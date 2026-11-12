package io.github.fogonapps.social

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.serialization.Serializable

/** Publica un archivo completo; un corte nunca deja un JSON a medias. */
internal fun escribirAtomico(archivo: File, bytes: ByteArray, temporal: File = File(archivo.parentFile, archivo.name + ".nuevo")) {
    check(archivo.parentFile!!.mkdirs() || archivo.parentFile!!.isDirectory)
    FileOutputStream(temporal).use { it.write(bytes); it.fd.sync() }
    if (!temporal.renameTo(archivo)) throw IOException("No se pudo publicar ${archivo.name}")
}

@Serializable
data class PerfilTelefono(val id: String, val apodo: String = "", val usuario: String = "", val miniatura: String = "", val provisional: Boolean = false, val idCuenta: String = "")

@Serializable
data class ListaPerfiles(val perfiles: List<PerfilTelefono> = listOf(PerfilTelefono("1")), val activo: String = "1", val siguiente: Long = 2, val pendientesDeBorrar: List<String> = emptyList())

/** El registro es independiente de Android para probar cortes reales con directorios temporales. */
class Perfiles(private val raiz: File) {
    private val archivo = File(raiz, "perfiles.json")
    @Volatile var lista: ListaPerfiles = if (archivo.exists()) JsonSocial.decodeFromString(ListaPerfiles.serializer(), archivo.readText()) else ListaPerfiles()
        private set
    init {
        require(lista.perfiles.size in 1..5 && lista.perfiles.map { it.id }.distinct().size == lista.perfiles.size)
        require(lista.perfiles.any { it.id == lista.activo })
        lista.perfiles.forEach { directorio(it.id) }
    }
    fun directorio(id: String): File {
        require(id.matches(Regex("[1-9][0-9]*")))
        return File(raiz, "perfiles/$id").apply { check(mkdirs() || isDirectory) }
    }
    @Synchronized private fun guardar(nueva: ListaPerfiles) {
        escribirAtomico(archivo, JsonSocial.encodeToString(ListaPerfiles.serializer(), nueva).encodeToByteArray())
        lista = nueva
    }
    @Synchronized fun cambiar(id: String) {
        require(lista.perfiles.any { it.id == id })
        guardar(lista.copy(activo = id))
    }
    /** El perfil sin cuenta (solo en este teléfono), si hay. */
    fun local(): PerfilTelefono? = lista.perfiles.firstOrNull { it.usuario.isBlank() && it.idCuenta.isBlank() }
    /** «Añadir otra cuenta»: solo puede haber un perfil local; si ya existe, se usa ese en vez de crear otro. */
    @Synchronized fun paraOtraCuenta(): PerfilTelefono? = local() ?: agregar()
    @Synchronized fun agregar(provisional: Boolean = true): PerfilTelefono? {
        if (lista.perfiles.size >= 5) return null
        val nuevo = PerfilTelefono(lista.siguiente.toString(), provisional = provisional)
        directorio(nuevo.id)
        guardar(lista.copy(perfiles = lista.perfiles + nuevo, siguiente = lista.siguiente + 1))
        return nuevo
    }
    @Synchronized fun actualizar(id: String, apodo: String, usuario: String, miniatura: String) {
        guardar(lista.copy(perfiles = lista.perfiles.map { if (it.id == id) it.copy(apodo = apodo, usuario = usuario, miniatura = miniatura, provisional = it.provisional && usuario.isBlank()) else it }))
    }
    /** Nunca se identifica una cuenta por el @: puede cambiar o pasar a otra persona. */
    fun duplicado(id: String, idCuenta: String): PerfilTelefono? = lista.perfiles.firstOrNull {
        it.id != id && idCuenta.isNotBlank() && it.idCuenta == idCuenta
    }
    /** Una respuesta sin id no borra la identidad; una respuesta de otra cuenta tampoco la sustituye. */
    @Synchronized fun completarIdCuenta(id: String, idCuenta: String): Boolean {
        if (idCuenta.isBlank()) return false
        val perfil = lista.perfiles.firstOrNull { it.id == id } ?: return false
        if (perfil.idCuenta == idCuenta) return true
        if (perfil.idCuenta.isNotBlank()) return false
        guardar(lista.copy(perfiles = lista.perfiles.map { if (it.id == id) it.copy(idCuenta = idCuenta) else it }))
        return true
    }
    /** El llamante detiene y cierra la sesión antes de borrar. Nunca reutilizamos un id. */
    @Synchronized fun quitar(id: String, borrarClaves: (String) -> Unit = {}) {
        require(id != lista.activo && lista.perfiles.any { it.id == id })
        guardar(lista.copy(perfiles = lista.perfiles.filterNot { it.id == id }, pendientesDeBorrar = lista.pendientesDeBorrar + id))
        limpiarPendientes(borrarClaves)
    }
    /** La intención persiste antes del borrado: un cierre a mitad no deja datos personales huérfanos. */
    @Synchronized fun limpiarPendientes(borrarClaves: (String) -> Unit = {}) {
        lista.pendientesDeBorrar.toList().forEach { id ->
            require(id.matches(Regex("[1-9][0-9]*")) && lista.perfiles.none { it.id == id })
            borrarClaves(id)
            val carpeta = File(raiz, "perfiles/$id")
            check(!carpeta.exists() || carpeta.deleteRecursively())
            guardar(lista.copy(pendientesDeBorrar = lista.pendientesDeBorrar - id))
        }
    }
}


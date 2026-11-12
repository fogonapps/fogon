package io.github.fogonapps.cajon.datos

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
    @Volatile var lista: ListaPerfiles = if (archivo.exists()) JsonCajon.decodeFromString(ListaPerfiles.serializer(), archivo.readText()) else ListaPerfiles()
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
        escribirAtomico(archivo, JsonCajon.encodeToString(ListaPerfiles.serializer(), nueva).encodeToByteArray())
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

/**
 * Migración sin abrir DataStore ni iniciar servicios. Cada fase se publica después de verificarla.
 * El respaldo guarda también una instantánea opaca de TODAS las preferencias de claves.
 */
class MigracionPerfiles(
    private val raiz: File,
    private val copiarClaves: () -> ByteArray,
    private val migrarClaves: (ByteArray) -> Unit,
    private val despues: (String) -> Unit = {},
) {
    private val respaldo = File(raiz, "migracion-perfiles-respaldo")
    private val progreso = File(raiz, "migracion-perfiles.json")
    private val destino = File(raiz, "perfiles/1")
    private val temporales = File(raiz, "migracion-perfiles-temporal")
    private val controles = setOf("perfiles", "perfiles.json", "perfiles.json.nuevo", "migracion-perfiles.json", "migracion-perfiles.json.nuevo", respaldo.name, temporales.name, "migracion-perfiles-preparando")
    private fun marcar(fase: String) {
        escribirAtomico(progreso, fase.encodeToByteArray())
        despues(fase)
    }
    fun ejecutar() {
        val fase = if (progreso.exists()) progreso.readText() else ""
        if (fase == "confirmada") {
            check(!respaldo.exists() || respaldo.deleteRecursively())
            return
        }
        if (fase == "terminada") return
        // Un registro sin marcador es una instalación nueva o una transferencia de Android.
        if (!progreso.exists() && File(raiz, "perfiles.json").exists()) return
        check(temporales.mkdirs() || temporales.isDirectory)
        if (!respaldo.exists()) {
            val preparando = File(raiz, "migracion-perfiles-preparando")
            check(preparando.mkdirs() || preparando.isDirectory)
            val archivos = File(preparando, "archivos").apply { mkdirs() }
            listar(raiz).filter { it.name !in controles }.forEach { copiarVerificado(it, File(archivos, it.name)) }
            escribirAtomico(File(preparando, "claves.json"), copiarClaves())
            if (!preparando.renameTo(respaldo)) throw IOException("No se pudo publicar el respaldo previo")
            despues("respaldo")
        }
        marcar("respaldada")
        // Un estado ilegible no detiene la migración (la app no arrancaría nunca): se mueve tal cual y el almacén
        // del perfil hace lo de siempre con un JSON dañado (guarda una copia «estado-dañado-…» y empieza vacío).
        val estadoAnterior = File(respaldo, "archivos/datastore/estado.json")
        if (estadoAnterior.exists() && runCatching { JsonCajon.decodeFromString(Estado.serializer(), estadoAnterior.readText()) }.isFailure)
            check(estadoAnterior.copyTo(File(respaldo, "estado-ilegible-al-migrar.json"), overwrite = true).exists())
        // Copiar es reanudable: un corte no elimina el origen ni modifica el respaldo publicado.
        val origen = File(respaldo, "archivos")
        listar(origen).forEach { copiarVerificado(it, File(destino, it.name)) }
        marcar("copiada")
        // Las imágenes elegidas se guardaban como file:///…/files/portadas/… en los JSON.
        // Solo reescribimos rutas de archivos que efectivamente están en el respaldo.
        val rutas = origen.walkTopDown().onFail { _, error -> throw error }.filter { it.isFile }.map { it.relativeTo(origen).path }.toList()
        destino.walkTopDown().onFail { _, error -> throw error }.filter { it.isFile && it.extension == "json" }.forEach { f ->
            val original = f.readText()
            var texto = original
            rutas.forEach { relativa -> texto = texto.replace(File(raiz, relativa).absolutePath, File(destino, relativa).absolutePath) }
            if (texto != original) escribirAtomico(f, texto.encodeToByteArray(), File(temporales, "reescritura"))
        }
        marcar("rutas")
        migrarClaves(File(respaldo, "claves.json").readBytes())
        marcar("claves")
        if (!File(raiz, "perfiles.json").exists()) {
            escribirAtomico(File(raiz, "perfiles.json"), JsonCajon.encodeToString(ListaPerfiles.serializer(), ListaPerfiles()).encodeToByteArray())
        }
        marcar("registrada")
        // El respaldo sigue intacto incluso después de retirar los originales verificados.
        listar(origen).forEach {
            check(File(raiz, it.name).let { f -> !f.exists() || f.deleteRecursively() })
            despues("retirada")
        }
        check(temporales.deleteRecursively())
        marcar("terminada")
    }
    /** Se llama solo tras cargar y guardar correctamente el perfil 1, en un arranque posterior. */
    fun confirmarArranque() {
        if (progreso.takeIf { it.exists() }?.readText() != "terminada") return
        marcar("confirmada")
        check(!respaldo.exists() || respaldo.deleteRecursively())
    }
    private fun listar(directorio: File): Array<File> = directorio.listFiles()
        ?: throw IOException("No se pudo leer el directorio ${directorio.name}")

    private fun copiarVerificado(origen: File, destino: File) {
        if (origen.isDirectory) {
            check(destino.mkdirs() || destino.isDirectory)
            listar(origen).forEach { copiarVerificado(it, File(destino, it.name)) }
        } else {
            check(destino.parentFile!!.mkdirs() || destino.parentFile!!.isDirectory)
            // Fuera del contenido: nunca colisiona con un archivo personal llamado «*.nuevo».
            val temporal = File(temporales, "archivo")
            val huella = java.security.MessageDigest.getInstance("SHA-256")
            FileOutputStream(temporal).use { salida ->
                origen.inputStream().use { entrada ->
                    val bloque = ByteArray(64 * 1024)
                    while (true) {
                        val leidos = entrada.read(bloque)
                        if (leidos < 0) break
                        salida.write(bloque, 0, leidos)
                        huella.update(bloque, 0, leidos)
                        despues("bloque")
                    }
                }
                salida.fd.sync()
            }
            val copia = java.security.MessageDigest.getInstance("SHA-256")
            temporal.inputStream().use { entrada ->
                val bloque = ByteArray(64 * 1024)
                while (true) {
                    val leidos = entrada.read(bloque)
                    if (leidos < 0) break
                    copia.update(bloque, 0, leidos)
                }
            }
            check(huella.digest().contentEquals(copia.digest())) { "Copia incompleta: ${origen.name}" }
            if (!temporal.renameTo(destino)) throw IOException("No se pudo publicar ${destino.name}")
            despues("archivo")
        }
    }
}

/** La generación retirada nunca vuelve a estar vigente, aunque se vuelva al mismo perfil y token. */
internal class VigenciaPerfil {
    private val activa = java.util.concurrent.atomic.AtomicBoolean(true)
    fun vigente() = activa.get()
    fun invalidar() { activa.set(false) }
}

/** Solo se descarta el perfil de entrada sin contenido, ajustes ni apariencia propios. */
internal fun perfilProvisionalVacio(estado: Estado?, escuchas: Oidos): Boolean = estado != null &&
    estado.copy(listo = false, apariencia = null) == Estado() &&
    (estado.apariencia == null || estado.apariencia.copy(cambio = 0) == AparienciaCuenta()) && escuchas == Oidos()

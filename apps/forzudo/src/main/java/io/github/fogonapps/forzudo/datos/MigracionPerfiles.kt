package io.github.fogonapps.forzudo.datos

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import io.github.fogonapps.social.ListaPerfiles
import io.github.fogonapps.social.JsonSocial

/** Publica un archivo completo; un corte nunca deja un JSON a medias. */
internal fun escribirAtomico(archivo: File, bytes: ByteArray, temporal: File = File(archivo.parentFile, archivo.name + ".nuevo")) {
    check(archivo.parentFile!!.mkdirs() || archivo.parentFile!!.isDirectory)
    FileOutputStream(temporal).use { it.write(bytes); it.fd.sync() }
    if (!temporal.renameTo(archivo)) throw IOException("No se pudo publicar ${archivo.name}")
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
        val estadoAnterior = File(respaldo, "archivos/datastore/estado.json")
        if (estadoAnterior.exists()) {
            try { JsonGym.decodeFromString(Estado.serializer(), estadoAnterior.readText()) }
            catch (x: Exception) { throw IOException("El cuaderno anterior es ilegible; se conserva el origen y el respaldo", x) }
        }
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
            escribirAtomico(File(raiz, "perfiles.json"), JsonSocial.encodeToString(ListaPerfiles.serializer(), ListaPerfiles()).encodeToByteArray())
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


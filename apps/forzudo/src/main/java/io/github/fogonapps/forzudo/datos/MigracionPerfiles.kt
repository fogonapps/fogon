package io.github.fogonapps.forzudo.datos

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Publica un archivo completo; un corte nunca deja un JSON a medias. */
internal fun escribirAtomico(archivo: File, bytes: ByteArray, temporal: File = File(archivo.parentFile, archivo.name + ".nuevo")) {
    check(archivo.parentFile!!.mkdirs() || archivo.parentFile!!.isDirectory)
    FileOutputStream(temporal).use { it.write(bytes); it.fd.sync() }
    if (!temporal.renameTo(archivo)) throw IOException("No se pudo publicar ${archivo.name}")
}

/**
 * Migración sin abrir DataStore ni iniciar servicios (`MigracionPerfilesTelefono` de core/social).
 * El respaldo guarda también una instantánea opaca de TODAS las preferencias de claves; el cuaderno anterior
 * tiene que poder leerse antes de copiarlo.
 */
class MigracionPerfiles(
    raiz: File,
    copiarClaves: () -> ByteArray,
    migrarClaves: (ByteArray) -> Unit,
    despues: (String) -> Unit = {},
) : io.github.fogonapps.social.MigracionPerfilesTelefono(raiz, copiarClaves, migrarClaves, despues,
    { JsonGym.decodeFromString(Estado.serializer(), it) })

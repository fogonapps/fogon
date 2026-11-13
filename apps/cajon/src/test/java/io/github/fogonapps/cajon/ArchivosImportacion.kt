package io.github.fogonapps.cajon

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Archivos sintéticos con los encabezados de las exportaciones, sin datos personales. */
internal fun zipImportacion(vararg archivos: Pair<String, String>): ByteArray = ByteArrayOutputStream().also { b ->
    ZipOutputStream(b).use { z -> archivos.forEach { (nombre, texto) ->
        z.putNextEntry(ZipEntry(nombre)); z.write(texto.toByteArray()); z.closeEntry()
    } }
}.toByteArray()

internal fun filaCsv(vararg campos: String) = campos.joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" } + "\r\n"

package io.github.fogonapps.cucha.datos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64

/*
 * Las fotos de las recetas en la copia cifrada de la cuenta (D-033): viajan dentro del mismo JSON, achicadas
 * ([LADO] px, JPEG [CALIDAD]) en el campo "fotosCopia" (id de receta → base64), hasta [TOPE] bytes en total para que
 * el sobre quepa en el servidor. Las versiones viejas de la app ignoran el campo.
 */
private const val LADO = 640
private const val CALIDAD = 75
private const val TOPE = 4_000_000

/** Achica una foto para la copia; null si no se puede leer. */
internal fun fotoParaCopia(archivo: File): ByteArray? = runCatching {
    val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(archivo.path, medidas)
    var muestra = 1
    while (maxOf(medidas.outWidth, medidas.outHeight) / (muestra * 2) >= LADO) muestra *= 2
    val b = BitmapFactory.decodeFile(archivo.path, BitmapFactory.Options().apply { inSampleSize = muestra }) ?: return null
    val escala = LADO.toFloat() / maxOf(b.width, b.height)
    val chica = if (escala < 1f) Bitmap.createScaledBitmap(b, (b.width * escala).toInt().coerceAtLeast(1), (b.height * escala).toInt().coerceAtLeast(1), true) else b
    ByteArrayOutputStream().also { chica.compress(Bitmap.CompressFormat.JPEG, CALIDAD, it) }.toByteArray()
}.getOrNull()

/** El JSON de [estado] con sus fotos de recetas adentro. [achicar] es [fotoParaCopia] (se cambia en las pruebas). */
internal fun conFotos(estadoJson: String, fotos: Map<String, String>, achicar: (File) -> ByteArray? = ::fotoParaCopia): String {
    if (fotos.isEmpty()) return estadoJson
    var total = 0
    val datos = buildMap {
        for ((id, ruta) in fotos.entries.sortedBy { it.key }) {
            val archivo = runCatching { File(java.net.URI(ruta)) }.getOrNull()?.takeIf { it.exists() } ?: continue
            val bytes = achicar(archivo) ?: continue
            if (total + bytes.size > TOPE) break
            total += bytes.size
            put(id, JsonPrimitive(Base64.getEncoder().encodeToString(bytes)))
        }
    }
    if (datos.isEmpty()) return estadoJson
    val o = JsonCucha.parseToJsonElement(estadoJson).jsonObject
    return JsonObject(o + ("fotosCopia" to JsonObject(datos))).toString()
}

/**
 * Saca las fotos de una copia que llegó de la cuenta: las escribe en [carpeta] y devuelve el JSON sin ellas y las
 * rutas nuevas (id → `file://…`), que reemplazan las de otro teléfono.
 */
internal fun sacarFotos(json: String, carpeta: File): Pair<String, Map<String, String>> {
    val o = runCatching { JsonCucha.parseToJsonElement(json).jsonObject }.getOrNull() ?: return json to emptyMap()
    val datos = o["fotosCopia"] as? JsonObject ?: return json to emptyMap()
    carpeta.mkdirs()
    val rutas = buildMap {
        for ((id, valor) in datos) {
            val bytes = runCatching { Base64.getDecoder().decode(valor.jsonPrimitive.content) }.getOrNull() ?: continue
            val archivo = File(carpeta, "${id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(60)}-copia.jpg")
            runCatching { archivo.writeBytes(bytes) }.onSuccess { put(id, archivo.toURI().toString()) }
        }
    }
    return JsonObject(o - "fotosCopia").toString() to rutas
}

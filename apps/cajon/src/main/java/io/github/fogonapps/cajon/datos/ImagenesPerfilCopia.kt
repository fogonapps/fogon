package io.github.fogonapps.cajon.datos

import java.io.File
import java.util.Base64

/*
 * La foto y la portada del perfil en la copia cifrada del cajón: en el teléfono son archivos (`file://…`) y a la
 * cuenta antes solo viajaba su ruta, que en otro teléfono (o al reinstalar) no existe. Ahora van adentro, en
 * [Perfil.fotoCopia] y [Perfil.portadaCopia] (base64), y al traer el cajón se vuelven a escribir como archivos.
 */
private const val MAXIMO = 1_500_000

private fun archivoLocal(ruta: String): File? =
    if (ruta.startsWith("file://")) File(ruta.removePrefix("file://")).takeIf { it.isFile && it.length() in 1..MAXIMO } else null

/** El cajón que sale a la cuenta, con la foto y la portada del teléfono adentro. */
fun conImagenesPerfil(e: Estado, leer: (File) -> ByteArray = File::readBytes): Estado {
    val p = e.perfil
    val foto = archivoLocal(p.foto)?.let { Base64.getEncoder().encodeToString(leer(it)) }.orEmpty()
    val portada = archivoLocal(p.portada)?.let { Base64.getEncoder().encodeToString(leer(it)) }.orEmpty()
    return if (foto.isEmpty() && portada.isEmpty()) e else e.copy(perfil = p.copy(fotoCopia = foto, portadaCopia = portada))
}

/**
 * El cajón que llegó de la cuenta: si trae las imágenes y aquí no existen, se escriben en [carpeta] y la ruta pasa
 * a ser la de este teléfono. Lo que ya está en el teléfono no se toca.
 */
fun restaurarImagenesPerfil(e: Estado, carpeta: File): Estado {
    val p = e.perfil
    if (p.fotoCopia.isEmpty() && p.portadaCopia.isEmpty()) return e
    fun escribir(datos: String, ruta: String, nombre: String): String {
        if (datos.isEmpty() || archivoLocal(ruta) != null || !ruta.startsWith("file://")) return ruta
        val bytes = runCatching { Base64.getDecoder().decode(datos) }.getOrNull()?.takeIf { it.size in 1..MAXIMO } ?: return ruta
        carpeta.mkdirs()
        val f = File(carpeta, "$nombre-${bytes.contentHashCode().toUInt()}.jpg")
        return runCatching { f.writeBytes(bytes); "file://" + f.absolutePath }.getOrDefault(ruta)
    }
    return e.copy(perfil = p.copy(foto = escribir(p.fotoCopia, p.foto, "perfil-foto"), portada = escribir(p.portadaCopia, p.portada, "perfil-portada"),
        fotoCopia = "", portadaCopia = ""))
}

/*
 * Lo mismo para todo el cajón: cualquier imagen del teléfono (`file://…`) que use (la foto y la portada del perfil,
 * las portadas y fondos elegidos desde la galería para una obra, la foto de un artista) viaja en el campo
 * «imagenesCopia» del JSON (ruta → base64), hasta [TOPE_TOTAL] bytes. Al traerlo, las que no existen en este
 * teléfono se escriben en la carpeta de la app y la ruta vieja se cambia por la nueva en todo el cajón.
 */
private const val TOPE_TOTAL = 4_500_000
private val RUTA = Regex("\"(file://[^\"\\\\]+)\"")

fun empacarImagenes(json: String): String {
    var total = 0
    val datos = mutableMapOf<String, String>()
    for (ruta in RUTA.findAll(json).map { it.groupValues[1] }.distinct()) {
        val f = archivoLocal(ruta) ?: continue
        if (total + f.length() > TOPE_TOTAL) continue
        total += f.length().toInt()
        datos[ruta] = Base64.getEncoder().encodeToString(f.readBytes())
    }
    if (datos.isEmpty()) return json
    val o = JsonCajon.parseToJsonElement(json) as? kotlinx.serialization.json.JsonObject ?: return json
    return kotlinx.serialization.json.JsonObject(o + ("imagenesCopia" to kotlinx.serialization.json.JsonObject(
        datos.mapValues { kotlinx.serialization.json.JsonPrimitive(it.value) }))).toString()
}

fun desempacarImagenes(json: String, carpeta: File): String {
    val o = runCatching { JsonCajon.parseToJsonElement(json) as? kotlinx.serialization.json.JsonObject }.getOrNull() ?: return json
    val datos = o["imagenesCopia"] as? kotlinx.serialization.json.JsonObject ?: return json
    var texto = kotlinx.serialization.json.JsonObject(o - "imagenesCopia").toString()
    for ((ruta, valor) in datos) {
        if (archivoLocal(ruta) != null) continue
        val bytes = runCatching { Base64.getDecoder().decode((valor as kotlinx.serialization.json.JsonPrimitive).content) }.getOrNull()?.takeIf { it.size in 1..MAXIMO } ?: continue
        carpeta.mkdirs()
        val nombre = ruta.substringAfterLast('/').filter { it.isLetterOrDigit() || it in "-_." }.take(60).ifBlank { "imagen.jpg" }
        val f = File(carpeta, nombre)
        if (runCatching { f.writeBytes(bytes) }.isFailure) continue
        texto = texto.replace("\"$ruta\"", "\"file://${f.absolutePath}\"")
    }
    return texto
}

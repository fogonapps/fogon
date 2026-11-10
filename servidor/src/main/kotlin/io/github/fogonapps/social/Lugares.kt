package io.github.fogonapps.social

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.text.Normalizer
import java.sql.Connection
import kotlin.math.round

@Serializable
data class Lugar(val id: Long, val nombre: String, val ciudad: String, val direccion: String?, val lat: Double?, val lon: Double?, val cuenta: Long?, val cuando: Long)

internal fun normalizarLugar(s: String): String = Normalizer.normalize(s.trim(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(java.util.Locale.ROOT).replace(Regex("\\s+"), " ")

internal fun Connection.lugar(id: Long): Lugar? = leer("SELECT * FROM lugares WHERE id = ? AND retirado = 0", id) {
    Lugar(it.getLong("id"), it.getString("nombre"), it.getString("ciudad"), it.getString("direccion"),
        (it.getObject("lat") as? Number)?.toDouble(), (it.getObject("lon") as? Number)?.toDouble(),
        (it.getObject("cuenta") as? Number)?.toLong(), it.getLong("cuando"))
}.singleOrNull()

internal fun validarLugar(d: JsonObject, edicion: Boolean = false): JsonObject {
    fun mal(): Nothing = throw Problema(400, "lugar_esquema")
    val claves = if (edicion) setOf("direccion", "lat", "lon") else setOf("nombre", "ciudad", "direccion", "lat", "lon")
    if (d.keys.any { it !in claves } || (edicion && d.isEmpty())) mal()
    fun texto(k: String, max: Int, obligatorio: Boolean) {
        if (!obligatorio && k !in d) return
        val v = d[k] as? JsonPrimitive ?: mal()
        if (!v.isString || v.content.trim().isEmpty() || v.content.trim().length > max) mal()
    }
    if (!edicion) { texto("nombre", 120, true); texto("ciudad", 80, true) }
    texto("direccion", 200, false)
    if (("lat" in d) != ("lon" in d)) mal()
    return JsonObject(d.mapValues { (k, v) ->
        if (k in setOf("lat", "lon")) {
            val n = (v as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull ?: mal()
            val max = if (k == "lat") 90.0 else 180.0
            if (!n.isFinite() || n < -max || n > max) mal()
            JsonPrimitive(round(n * 10000) / 10000)
        } else JsonPrimitive(v.jsonPrimitive.content.trim())
    })
}

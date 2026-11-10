package io.github.fogonapps.social

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

object AppsSocial {
    fun validar(app: String): String = app.also { if (it !in setOf("cajon", "forzudo", "cucha")) throw Problema(400, "app") }
    fun nombre(app: String): String = when (validar(app)) {
        "forzudo" -> "El Forzudo"
        "cucha" -> "La Cucha"
        else -> "El Cajón"
    }
}

@Serializable
data class IdentidadParcial(
    val nombre: String? = null, val bio: String? = null, val portada: String? = null,
    val enlaces: List<String>? = null, val privada: Boolean? = null,
)

@Serializable
data class PerfilApp(val revision: Long? = null, val identidad: IdentidadParcial? = null, val partes: Map<String, Parte>? = null)

/** El esquema cerrado impide enviar accidentalmente el cuaderno o datos corporales privados. */
fun validarLoteApp(app: String, c: CambiosPublicaciones) {
    if (c.lista.size > 2000) throw Problema(413, "muy_grande")
    if (c.lista.map { it.clave }.distinct().size != c.lista.size || c.quitar.any { it.isBlank() || it.length > 80 }) throw Problema(400, "clave")
    c.lista.forEach { p ->
        if (p.clave.isBlank() || p.clave.length > 80 || p.visibilidad !in Vis.SUBIBLES || p.cuando < 0) throw Problema(400, "publicacion")
        when (app) {
            "cajon" -> if (p.tipo !in setOf("diario", "resena", "lista", "foto")) throw Problema(400, "tipo_app")
            "forzudo" -> { if (p.tipo != "entreno") throw Problema(400, "tipo_app"); validarEntreno(p.datos) }
            else -> throw Problema(400, "tipo_app")
        }
    }
}

private fun validarEntreno(d: JsonObject) {
    fun mal(): Nothing = throw Problema(400, "entreno_esquema")
    fun campos(o: JsonObject, permitidos: Set<String>) { if (o.keys.any { it !in permitidos }) mal() }
    fun texto(o: JsonObject, k: String, limite: Int): String {
        val v = o[k] as? JsonPrimitive ?: mal()
        if (!v.isString || v.content.isBlank() || v.content.length > limite) mal()
        return v.content
    }
    fun numero(o: JsonObject, k: String, max: Double, entero: Boolean = false): Double {
        val v = o[k] as? JsonPrimitive ?: mal()
        val n = v.doubleOrNull ?: mal()
        if (v.isString || !n.isFinite() || n < 0 || n > max || (entero && n % 1 != 0.0)) mal()
        return n
    }
    campos(d, setOf("version", "titulo", "fecha", "duracion", "unidadDuracion", "ejercicios", "texto", "imagenes", "video", "poster"))
    if (numero(d, "version", 1.0, true) != 1.0) mal()
    texto(d, "titulo", 120)
    numero(d, "fecha", 253402300799999.0, true)
    numero(d, "duracion", 86400.0, true)
    if (texto(d, "unidadDuracion", 1) != "s") mal()
    if ("texto" in d && ((d["texto"] as? JsonPrimitive)?.let { !it.isString || it.content.length > 2000 } != false)) mal()
    val ejercicios = d["ejercicios"] as? JsonArray ?: mal()
    if (ejercicios.size !in 1..100) mal()
    ejercicios.forEach { elemento ->
        val e = elemento as? JsonObject ?: mal()
        campos(e, setOf("id", "nombre", "series"))
        texto(e, "id", 80); texto(e, "nombre", 120)
        val series = e["series"] as? JsonArray ?: mal()
        if (series.size !in 1..100) mal()
        series.forEach { elementoSerie ->
            val serie = elementoSerie as? JsonObject ?: mal()
            campos(serie, setOf("repeticiones", "peso", "unidadPeso", "duracion", "unidadDuracion"))
            if (serie.isEmpty() || serie.keys.none { it in setOf("repeticiones", "peso", "duracion") }) mal()
            if ("repeticiones" in serie) numero(serie, "repeticiones", 10000.0, true)
            if ("peso" in serie) {
                numero(serie, "peso", 10000.0)
                if (texto(serie, "unidadPeso", 2) !in setOf("kg", "lb")) mal()
            } else if ("unidadPeso" in serie) mal()
            if ("duracion" in serie) {
                numero(serie, "duracion", 86400.0, true)
                if (texto(serie, "unidadDuracion", 1) != "s") mal()
            } else if ("unidadDuracion" in serie) mal()
        }
    }
}

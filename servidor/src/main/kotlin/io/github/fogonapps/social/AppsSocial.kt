package io.github.fogonapps.social

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

object AppsSocial {
    fun validar(app: String): String = app.also { if (it !in setOf("cajon", "forzudo", "cucha", "alcancia")) throw Problema(400, "app") }
    fun nombre(app: String): String = when (validar(app)) {
        "forzudo" -> "El Forzudo"
        "cucha" -> "La Cucha"
        "alcancia" -> "La Alcancía"
        else -> "El Cajón"
    }
}

@Serializable
data class IdentidadParcial(
    val nombre: String? = null, val bio: String? = null, val portada: String? = null,
    val enlaces: List<String>? = null, val privada: Boolean? = null,
)

@Serializable
/**
 * [fusionar]: las [partes] se suman a las que ya hay (y se quitan las de [quitar]) en vez de reemplazarlas; así se
 * cambia una sola parte (la canción del perfil) sin borrar las demás.
 */
data class PerfilApp(val revision: Long? = null, val identidad: IdentidadParcial? = null, val partes: Map<String, Parte>? = null,
    val fusionar: Boolean = false, val quitar: List<String> = emptyList())

/** El esquema cerrado impide enviar accidentalmente el cuaderno o datos corporales privados. */
fun validarLoteApp(app: String, c: CambiosPublicaciones) {
    if (c.lista.size > 2000) throw Problema(413, "muy_grande")
    if (c.lista.map { it.clave }.distinct().size != c.lista.size || c.quitar.any { it.isBlank() || it.length > 80 }) throw Problema(400, "clave")
    c.lista.forEach { p ->
        if (p.clave.isBlank() || p.clave.length > 80 || p.visibilidad !in Vis.SUBIBLES || p.cuando < 0) throw Problema(400, "publicacion")
        when (app) {
            "cajon" -> if (p.tipo !in setOf("diario", "resena", "lista", "foto")) throw Problema(400, "tipo_app")
            "forzudo" -> { if (p.tipo != "entreno") throw Problema(400, "tipo_app"); validarEntreno(p.datos) }
            "cucha" -> validarComida(p.tipo, p.datos)
            "alcancia" -> { if (p.tipo != "logro") throw Problema(400, "tipo_app"); validarLogro(p.datos) }
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

internal fun validarComida(tipo: String, d: JsonObject) {
    fun mal(): Nothing = throw Problema(400, "cucha_esquema")
    fun campos(o: JsonObject, claves: Set<String>) { if (o.keys.any { it !in claves }) mal() }
    fun texto(o: JsonObject, k: String, max: Int, vacio: Boolean = false): String {
        val v = o[k] as? JsonPrimitive ?: mal()
        if (!v.isString || (!vacio && v.content.isBlank()) || v.content.length > max) mal()
        return v.content
    }
    fun numero(o: JsonObject, k: String, min: Double, max: Double, entero: Boolean = false): Double {
        val v = o[k] as? JsonPrimitive ?: mal()
        val n = v.doubleOrNull ?: mal()
        if (v.isString || !n.isFinite() || n < min || n > max || (entero && n % 1 != 0.0)) mal()
        return n
    }
    val comunes = setOf("version", "texto", "imagenes", "video", "poster")
    val propias = when (tipo) {
        "plato" -> setOf("nombre", "nota", "lugar", "nutricion")
        "receta" -> setOf("nombre", "porciones", "tiempo", "ingredientes", "pasos", "nutricion")
        "resena_restaurante" -> setOf("lugar", "nota", "plato")
        else -> throw Problema(400, "tipo_app")
    }
    campos(d, comunes + propias)
    numero(d, "version", 1.0, 1.0, true)
    if (tipo != "resena_restaurante") texto(d, "nombre", 120)
    if ("texto" in d) texto(d, "texto", 2000, true)
    if ("nota" in d || tipo == "resena_restaurante") numero(d, "nota", 1.0, 5.0, true)
    if ("lugar" in d || tipo == "resena_restaurante") numero(d, "lugar", 1.0, 9007199254740991.0, true)
    if ("plato" in d) texto(d, "plato", 120)
    if ("nutricion" in d) {
        val n = d["nutricion"] as? JsonObject ?: mal()
        campos(n, setOf("kcal", "proteina", "carbohidratos", "grasa"))
        numero(n, "kcal", 0.0, 10000.0)
        for (k in listOf("proteina", "carbohidratos", "grasa")) numero(n, k, 0.0, 1000.0)
    }
    if (tipo == "receta") {
        numero(d, "porciones", 1.0, 100.0, true)
        if ("tiempo" in d) numero(d, "tiempo", 1.0, 10080.0, true)
        val ingredientes = d["ingredientes"] as? JsonArray ?: mal()
        if (ingredientes.size !in 1..100) mal()
        ingredientes.forEach {
            val i = it as? JsonObject ?: mal()
            campos(i, setOf("nombre", "cantidad", "unidad"))
            texto(i, "nombre", 120); numero(i, "cantidad", 0.000001, 100000.0)
            if (texto(i, "unidad", 20) !in setOf("g", "kg", "ml", "l", "unidad", "cucharadita", "cucharada", "taza", "pizca")) mal()
        }
        val pasos = d["pasos"] as? JsonArray ?: mal()
        if (pasos.size !in 1..50) mal()
        pasos.forEach { texto(buildJsonObject { put("paso", it) }, "paso", 1000) }
    }
}

/** Los logros que La Alcancía puede compartir (D-032): nunca saldos, montos, cuentas, bancos ni movimientos. */
val LOGROS_ALCANCIA = setOf("meta_cumplida", "meta_avance", "primera_anotacion", "cierres")

/**
 * Un logro de La Alcancía con esquema cerrado: el código, el nombre de la meta (si aplica), su avance en
 * porcentaje entero o cuántos meses cerró. Ningún campo admite dinero.
 */
internal fun validarLogro(d: JsonObject) {
    fun mal(): Nothing = throw Problema(400, "alcancia_esquema")
    fun texto(k: String, max: Int, vacio: Boolean = false): String {
        val v = d[k] as? JsonPrimitive ?: mal()
        if (!v.isString || (!vacio && v.content.isBlank()) || v.content.length > max) mal()
        return v.content
    }
    fun entero(k: String, min: Long, max: Long): Long {
        val v = d[k] as? JsonPrimitive ?: mal()
        val n = v.doubleOrNull ?: mal()
        if (v.isString || !n.isFinite() || n % 1 != 0.0 || n < min || n > max) mal()
        return n.toLong()
    }
    if (d.keys.any { it !in setOf("version", "logro", "meta", "porcentaje", "cantidad", "texto", "imagenes", "video", "poster") }) mal()
    if (entero("version", 1, 1) != 1L) mal()
    val logro = texto("logro", 40)
    if (logro !in LOGROS_ALCANCIA) mal()
    val conMeta = logro == "meta_cumplida" || logro == "meta_avance"
    if (conMeta) texto("meta", 60) else if ("meta" in d) mal()
    if (logro == "meta_avance") entero("porcentaje", 0, 100) else if ("porcentaje" in d) mal()
    if (logro == "cierres") entero("cantidad", 1, 1200) else if ("cantidad" in d) mal()
    if ("texto" in d) texto("texto", 2000, vacio = true)
}

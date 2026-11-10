package io.github.fogonapps.cucha.datos

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.security.MessageDigest

/*
 * Lo que La Cucha publica: instantáneas elegidas, nunca el diario, las metas, el peso ni los datos personales
 * (D-030). La información nutricional solo va si quien publica la activa (D-031). Los esquemas son los del
 * servidor 0.33 (servidor/README.md, «La Cucha: publicaciones y lugares»).
 */

const val TIPO_PLATO = "plato"
const val TIPO_RECETA = "receta"
const val TIPO_RESENA = "resena_restaurante"
val TIPOS_CUCHA = setOf(TIPO_PLATO, TIPO_RECETA, TIPO_RESENA)

private fun huella(texto: String) = MessageDigest.getInstance("SHA-256").digest(texto.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

/** El prefijo de la clave de cada tipo (corto: el servidor admite claves de hasta 80 caracteres). */
fun prefijoClave(tipo: String) = when (tipo) { TIPO_RESENA -> "resena"; else -> tipo } + "-"

/** La clave estable de una publicación: la misma receta o el mismo plato anotado actualiza, no duplica. */
fun clavePublicacion(tipo: String, origen: String) = prefijoClave(tipo) + huella(origen)

/** Las calorías y los macros con el redondeo que se ve en la app (enteros en kcal, un decimal en gramos). */
private fun nutricion(n: Nutrientes) = buildJsonObject {
    fun g(x: Double) = (Math.round(x * 10) / 10.0).coerceIn(0.0, 1000.0)
    put("kcal", Math.round(n.kcal).toDouble().coerceIn(0.0, 10000.0))
    put("proteina", g(n.proteina)); put("carbohidratos", g(n.carbos)); put("grasa", g(n.grasa))
}

private fun nombreValido(n: String) = n.trim().also { require(it.length in 1..120) }
private fun textoValido(t: String) = t.trim().also { require(it.length <= 2000) }

/** Un plato: nombre, nota opcional (1 a 5), texto, restaurante opcional y, si se activa, su nutrición. */
fun instantaneaPlato(nombre: String, nota: Int?, texto: String, lugar: Long?, nutricion: Nutrientes?): JsonObject {
    require(nota == null || nota in 1..5)
    require(lugar == null || lugar > 0)
    return buildJsonObject {
        put("version", 1); put("nombre", nombreValido(nombre))
        if (nota != null) put("nota", nota)
        textoValido(texto).takeIf { it.isNotEmpty() }?.let { put("texto", it) }
        if (lugar != null) put("lugar", lugar)
        if (nutricion != null) put("nutricion", nutricion(nutricion))
    }
}

/** Un ingrediente que viaja: nombre, cantidad y unidad (los ids del catálogo local no significan nada afuera). */
data class IngredienteCompartido(val nombre: String, val cantidad: Double, val unidad: String = "g")

val UNIDADES_RECETA = setOf("g", "kg", "ml", "l", "unidad", "cucharadita", "cucharada", "taza", "pizca")

/** Los ingredientes de una receta con su nombre en [idioma]; los que no están en la base ni en los productos se omiten. */
fun ingredientesCompartidos(r: Receta, base: Base, productos: List<Producto>, idioma: String): List<IngredienteCompartido> =
    r.ingredientes.mapNotNull { i ->
        val nombre = base.alimento(i.alimento)?.nombre(idioma) ?: productos.firstOrNull { it.id == i.alimento }?.nombre
        nombre?.takeIf { it.isNotBlank() && i.gramos > 0 }?.let { IngredienteCompartido(it.trim().take(120), i.gramos) }
    }

/** Una receta propia como instantánea: porciones, tiempo, ingredientes, pasos y, si se activa, la nutrición por porción. */
fun instantaneaReceta(nombre: String, porciones: Int, minutos: Int, ingredientes: List<IngredienteCompartido>, pasos: List<String>,
                      texto: String, nutricionPorPorcion: Nutrientes?): JsonObject {
    require(porciones in 1..100)
    require(ingredientes.size in 1..100)
    val limpios = pasos.map { it.trim() }.filter { it.isNotEmpty() }
    require(limpios.size in 1..50 && limpios.all { it.length <= 1000 })
    return buildJsonObject {
        put("version", 1); put("nombre", nombreValido(nombre)); put("porciones", porciones)
        if (minutos in 1..10080) put("tiempo", minutos)
        putJsonArray("ingredientes") {
            ingredientes.forEach { i ->
                require(i.nombre.isNotBlank() && i.nombre.length <= 120 && i.unidad in UNIDADES_RECETA)
                require(i.cantidad.isFinite() && i.cantidad in 0.000001..100000.0)
                add(buildJsonObject { put("nombre", i.nombre); put("cantidad", i.cantidad); put("unidad", i.unidad) })
            }
        }
        putJsonArray("pasos") { limpios.forEach { add(JsonPrimitive(it)) } }
        textoValido(texto).takeIf { it.isNotEmpty() }?.let { put("texto", it) }
        if (nutricionPorPorcion != null) putJsonObject("nutricion") { nutricion(nutricionPorPorcion).forEach { (k, v) -> put(k, v) } }
    }
}

/** La reseña de un restaurante: el lugar (obligatorio), la nota (1 a 5), el texto y, si quiere, el plato que pidió. */
fun instantaneaResena(lugar: Long, nota: Int, texto: String, plato: String): JsonObject {
    require(lugar > 0 && nota in 1..5)
    return buildJsonObject {
        put("version", 1); put("lugar", lugar); put("nota", nota)
        textoValido(texto).takeIf { it.isNotEmpty() }?.let { put("texto", it) }
        plato.trim().takeIf { it.isNotEmpty() }?.let { put("plato", nombreValido(it)) }
    }
}

/** Añade fotos (hasta 10) o un vídeo con su póster: nunca las dos cosas. */
fun conMultimedia(datos: JsonObject, imagenes: List<String>, video: String, poster: String): JsonObject = JsonObject(datos + buildJsonObject {
    require(imagenes.size <= 10)
    if (video.isNotBlank() && poster.isNotBlank()) { require(imagenes.isEmpty()); put("video", video); put("poster", poster) }
    else if (imagenes.isNotEmpty()) put("imagenes", JsonArray(imagenes.map { JsonPrimitive(it) }))
})

/** El servidor añade el tipo antes de medir: hasta 100 000 caracteres. */
fun cabePublicacionCucha(datos: JsonObject): Boolean = runCatching {
    val tipo = when {
        "porciones" in datos -> TIPO_RECETA
        "nombre" in datos -> TIPO_PLATO
        else -> TIPO_RESENA
    }
    JsonObject(datos + ("tipo" to JsonPrimitive(tipo))).toString().length <= 100_000
}.getOrDefault(false)

/* ----- Partes voluntarias del perfil («Quién lo ve»): favoritos, nunca calorías ni peso ----- */

const val PARTE_PLATOS = "platos_favoritos"
const val PARTE_RESTAURANTES = "restaurantes_favoritos"
val VISIBILIDADES_PARTE = setOf("privado", "publica", "amigos")

/** Una lista de favoritos: hasta 20 nombres, sin repetir ni vacíos. */
fun listaFavoritos(nombres: List<String>): JsonObject = buildJsonObject {
    put("version", 1)
    putJsonArray("lista") { nombres.map { it.trim().take(120) }.filter { it.isNotEmpty() }.distinct().take(20).forEach { add(JsonPrimitive(it)) } }
}

/** Las partes que se suben: las privadas no viajan (el servidor las quita del perfil). */
fun partesCucha(platos: List<String>, visPlatos: String, restaurantes: List<String>, visRestaurantes: String): Map<String, io.github.fogonapps.social.ParteSubida> {
    require(visPlatos in VISIBILIDADES_PARTE && visRestaurantes in VISIBILIDADES_PARTE)
    return buildMap {
        if (visPlatos != "privado") put(PARTE_PLATOS, io.github.fogonapps.social.ParteSubida(visPlatos, listaFavoritos(platos)))
        if (visRestaurantes != "privado") put(PARTE_RESTAURANTES, io.github.fogonapps.social.ParteSubida(visRestaurantes, listaFavoritos(restaurantes)))
    }
}

/** Los nombres de una parte de favoritos recibida del servidor. */
fun nombresFavoritos(parte: kotlinx.serialization.json.JsonElement?): List<String> = runCatching {
    (parte as JsonObject)["lista"]!!.let { it as JsonArray }.map { (it as JsonPrimitive).content }
}.getOrDefault(emptyList())

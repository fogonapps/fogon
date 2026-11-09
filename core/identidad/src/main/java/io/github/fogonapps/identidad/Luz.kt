package io.github.fogonapps.identidad

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.json.*

data class LuzPropia(val color: Color, val segundo: Color? = null, val intensidad: Int = 100, val tamano: Int = 100)
data class TintasGuardadas(val nombre: String, val ajuste: AjustesEstilo)

/** Guarda colores resueltos, para que la combinación se pueda aplicar a otro tema. */
fun Apariencia.guardarTintas(nombre: String, paleta: Paleta): Apariencia =
    if (nombre.isBlank() || guardadas.size >= 12) this else copy(guardadas = guardadas + TintasGuardadas(
        nombre.trim().take(60), de().copy(colores = Ranura.entries.associateWith { color(paleta, it) })))

fun Apariencia.borrarTintas(indice: Int): Apariencia = copy(guardadas = guardadas.filterIndexed { i, _ -> i != indice })
fun Apariencia.aplicarTintas(t: TintasGuardadas): Apariencia = copy(ajustes = ajustes + (estilo to t.ajuste))

/** Recorre desde cero para no atravesar una franja ilegible al interpolar el degradado. */
fun LuzPropia.legible(colores: Colores): LuzPropia {
    val pedido = intensidad.coerceIn(0, 100)
    val limite = (0..pedido).firstOrNull { i ->
        listOfNotNull(color, segundo).any { c ->
            val fondo = c.copy(alpha = c.alpha * i / 100f).compositeOver(colores.fondo)
            listOf(colores.texto, colores.textoSuave).any { contraste(it, fondo) < 4.5 }
        }
    }?.let { (it - 1).coerceAtLeast(0) } ?: pedido
    return copy(intensidad = limite, tamano = tamano.coerceIn(50, 150))
}

data class PincelLuz(val colores: List<Color>, val radio: Float = 1.05f, val centroX: Float = 0.5f, val centroY: Float = -0.15f)
fun parametrosLuz(f: Fogon): PincelLuz = f.luz?.let { l ->
    val alfa = l.intensidad / 100f
    PincelLuz(listOf(l.color.copy(alpha = l.color.alpha * alfa), l.segundo?.let { it.copy(alpha = it.alpha * alfa) } ?: Color.Transparent, Color.Transparent), 1.05f * l.tamano / 100f)
} ?: PincelLuz(listOf(f.brillo, f.brillo.copy(alpha = 0f)))

fun escribirLuz(l: LuzPropia): String = buildJsonObject {
    put("color", l.color.toArgb()); l.segundo?.let { put("segundo", it.toArgb()) }
    put("intensidad", l.intensidad); put("tamano", l.tamano)
}.toString()
fun leerLuz(texto: String): LuzPropia? = runCatching {
    val j = Json.parseToJsonElement(texto).jsonObject
    LuzPropia(Color(j.getValue("color").jsonPrimitive.int), j["segundo"]?.jsonPrimitive?.intOrNull?.let { Color(it) },
        j["intensidad"]?.jsonPrimitive?.intOrNull?.coerceIn(0, 100) ?: 100,
        j["tamano"]?.jsonPrimitive?.intOrNull?.coerceIn(50, 150) ?: 100)
}.getOrNull()
fun escribirGuardadas(lista: List<TintasGuardadas>): String = buildJsonArray {
    lista.take(12).forEach { t -> add(buildJsonObject {
        put("nombre", t.nombre)
        put("colores", buildJsonObject { t.ajuste.colores.forEach { (r, c) -> put(r.name, c.toArgb()) } })
        t.ajuste.luz?.let { put("luz", Json.parseToJsonElement(escribirLuz(it))) }
        t.ajuste.adorno?.let { put("adorno", it.name) }
        t.ajuste.transparencia?.let { put("transparencia", it) }
    }) }
}.toString()
fun leerGuardadas(texto: String?): List<TintasGuardadas> = runCatching {
    Json.parseToJsonElement(texto ?: "[]").jsonArray.take(12).map { elemento ->
        val j = elemento.jsonObject
        TintasGuardadas(j.getValue("nombre").jsonPrimitive.content, AjustesEstilo(
            colores = j.getValue("colores").jsonObject.mapNotNull { (r, c) -> Ranura.entries.firstOrNull { it.name == r }?.let { it to Color(c.jsonPrimitive.int) } }.toMap(),
            transparencia = j["transparencia"]?.jsonPrimitive?.intOrNull,
            adorno = Decoracion.entries.firstOrNull { it.name == j["adorno"]?.jsonPrimitive?.content },
            luz = j["luz"]?.let { leerLuz(it.toString()) }))
    }
}.getOrDefault(emptyList())

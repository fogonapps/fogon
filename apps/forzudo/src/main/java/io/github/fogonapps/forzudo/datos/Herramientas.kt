package io.github.fogonapps.forzudo.datos

import java.time.LocalDate
import kotlin.math.floor
import kotlin.math.roundToInt

/* Herramientas del entreno: calentamiento, discos de la barra y medallas. Sin Android, para probarlas. */

/** Una serie de calentamiento: kilos y repeticiones. */
data class SerieCalentamiento(val kg: Double, val reps: Int)

/**
 * Series de calentamiento antes de la serie de trabajo de [kgTrabajo]: 40 % × 8, 60 % × 5 y 80 % × 3,
 * redondeadas a [paso] y sin repetir pesos. Con barra empiezan por la barra sola; con poco peso no hace falta.
 */
fun calentamiento(kgTrabajo: Double, barra: Double? = 20.0, paso: Double = 2.5): List<SerieCalentamiento> {
    if (kgTrabajo < 25) return emptyList()
    val series = mutableListOf<SerieCalentamiento>()
    if (barra != null && kgTrabajo > barra * 1.6) series += SerieCalentamiento(barra, 10)
    listOf(0.4 to 8, 0.6 to 5, 0.8 to 3).forEach { (p, r) ->
        val kg = (kgTrabajo * p / paso).roundToInt() * paso
        if (kg >= (barra ?: 0.0) && kg < kgTrabajo && series.none { it.kg == kg }) series += SerieCalentamiento(kg, r)
    }
    return series
}

/** Los discos de cada lado para llegar a [total] con una barra de [barra] kg, de los más grandes a los chicos. null si no se puede exacto. */
fun discos(total: Double, barra: Double = 20.0, disponibles: List<Double> = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)): List<Double>? {
    var lado = (total - barra) / 2
    if (lado < -1e-9) return null
    val usados = mutableListOf<Double>()
    for (d in disponibles.sortedDescending()) {
        val n = floor((lado + 1e-9) / d).toInt()
        repeat(n) { usados += d }
        lado -= n * d
    }
    return if (lado > 1e-6) null else usados
}

/** Una medalla y qué tan cerca está. [meta] y [actual] en la misma unidad (entrenos, semanas, kilos o nivel). */
data class Medalla(val id: String, val meta: Double, val actual: Double) {
    val lograda get() = actual >= meta
    val progreso get() = (actual / meta).coerceIn(0.0, 1.0)
}

/** Las medallas, en el orden en que se muestran. Solo necesitan cuántos entrenos, la mejor racha, el volumen total y el nivel del rango general. */
fun medallas(entrenos: Int, mejorRacha: Int, volumenTotal: Double, nivelRango: Int?): List<Medalla> =
    listOf(1, 10, 50, 100, 250).map { Medalla("entrenos_$it", it.toDouble(), entrenos.toDouble()) } +
        listOf(4, 12, 26, 52).map { Medalla("racha_$it", it.toDouble(), mejorRacha.toDouble()) } +
        listOf(100_000, 1_000_000).map { Medalla("volumen_${it / 1000}", it.toDouble(), volumenTotal) } +
        (2..6).map { Medalla("rango_$it", it.toDouble(), (nivelRango ?: 0).toDouble()) }

/** Las medallas del estado [e], calculadas con la lógica de la app. */
fun Logica.medallas(e: Estado, hoy: LocalDate): List<Medalla> {
    val dias = dias(e)
    return medallas(e.sessions.size, racha(e, dias, hoy).mejor, e.sessions.sumOf { volumen(it.entries) }, rangoGeneral(e)?.rango?.tier)
}

/** Las medallas que se ganaron con el último entreno: las que hay ahora y no había sin él. */
fun Logica.medallasNuevas(e: Estado, hoy: LocalDate): List<Medalla> {
    val ultimo = e.sessions.maxByOrNull { it.date } ?: return emptyList()
    val antes = medallas(e.copy(sessions = e.sessions - ultimo), hoy).filter { it.lograda }.map { it.id }.toSet()
    return medallas(e, hoy).filter { it.lograda && it.id !in antes }
}

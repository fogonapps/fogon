package io.github.fogonapps.forzudo.datos

import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneOffset

/** Solo recibe una sesión terminada: no tiene acceso al cuerpo, rangos ni cuaderno. */
fun claveEntreno(id: String): String = "entreno-" + MessageDigest.getInstance("SHA-256")
    .digest(id.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

fun instantaneaEntreno(sesion: Sesion, ejercicios: Map<String, Ejercicio>, unidad: String = "kg",
                       excluir: Set<String> = emptySet(), seriesExcluidas: Set<String> = emptySet()): JsonObject {
    require(unidad in setOf("kg", "lb"))
    val fecha = LocalDate.parse(sesion.date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    require(fecha in 0..253402300799999L)
    val lista = sesion.entries.filterKeys { it !in excluir }.mapNotNull { (id, originales) ->
        val series = originales.filterIndexed { indice, _ -> "$id:$indice" !in seriesExcluidas }
        if (series.isEmpty()) return@mapNotNull null
        val ejercicio = requireNotNull(ejercicios[id])
        require(id.length in 1..80 && ejercicio.n.length in 1..120)
        require(series.size in 1..100)
        buildJsonObject {
            put("id", id); put("nombre", ejercicio.n)
            putJsonArray("series") { series.forEach { serie ->
                add(buildJsonObject {
                    val repeticiones = serie.reps
                    val peso = serie.kg
                    if (repeticiones != null) {
                        val limite = if (ejercicio.kind == "s") 86400 else 10000
                        require(repeticiones.isFinite() && repeticiones in 0.0..limite.toDouble() && repeticiones % 1 == 0.0)
                        put(if (ejercicio.kind == "s") "duracion" else "repeticiones", repeticiones.toInt())
                        if (ejercicio.kind == "s") put("unidadDuracion", "s")
                    }
                    if (peso != null && ejercicio.kind == "w") {
                        val convertido = peso * if (unidad == "lb") 2.2046226218487757 else 1.0
                        require(convertido.isFinite() && convertido in 0.0..10000.0)
                        put("peso", convertido); put("unidadPeso", unidad)
                    }
                    require(repeticiones != null || (peso != null && ejercicio.kind == "w"))
                })
            } }
        }
    }
    require(lista.size in 1..100)
    val titulo = sesion.title?.takeIf { it.isNotBlank() } ?: sesion.day ?: sesion.date
    require(titulo.length in 1..120)
    val segundos = (sesion.ms ?: 0) / 1000
    require((sesion.ms ?: 0) >= 0 && segundos in 0..86400)
    return buildJsonObject {
        put("version", 1); put("titulo", titulo); put("fecha", fecha)
        put("duracion", segundos); put("unidadDuracion", "s"); put("ejercicios", JsonArray(lista))
    }.also { require(cabeInstantaneaEntreno(it)) }
}

/** El servidor añade el discriminador al JSON almacenado antes de aplicar su límite. */
fun cabeInstantaneaEntreno(datos: JsonObject): Boolean =
    JsonObject(datos + ("tipo" to JsonPrimitive("entreno"))).toString().length <= 20000

package io.github.fogonapps.forzudo.datos

import io.github.fogonapps.social.ParteSubida
import kotlinx.serialization.json.*
import java.time.LocalDate

/** Lista explícita de valores públicos: niveles e identificadores de medallas, sin cifras corporales. */
fun partesDeportivas(estado: Estado, logica: Logica, hoy: LocalDate, rangos: String, medallas: String): Map<String, ParteSubida> {
    require(rangos in setOf("privado", "publica", "amigos"))
    require(medallas in setOf("privado", "publica", "amigos"))
    return buildMap {
        if (rangos != "privado") put("rangos", ParteSubida(rangos, buildJsonObject {
            logica.rangoGeneral(estado)?.let { put("nivel", it.rango.tier) }
        }))
        if (medallas != "privado") put("medallas", ParteSubida(medallas, buildJsonObject {
            putJsonArray("logradas") { logica.medallas(estado, hoy).filter { it.lograda }.forEach { add(it.id) } }
        }))
    }
}

package io.github.fogonapps.social.ui

import io.github.fogonapps.social.PersonaSocial
import io.github.fogonapps.social.PublicacionSocial
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test

class CalificacionTest {
    @Test fun soloLasResenasSinTextoSePresentanComoCalificaciones() {
        val p = PublicacionSocial(1, PersonaSocial("persona"), "resena", 1, "publica",
            buildJsonObject { put("texto", ""); put("nota", 8) })
        assertTrue(esCalificacion(p))
        assertTrue(esCalificacion(p.copy(datos = buildJsonObject { put("texto", "  "); put("nota", 8) })))
        assertFalse(esCalificacion(p.copy(datos = buildJsonObject { put("texto", "Opinión") })))
        assertFalse(esCalificacion(p.copy(tipo = "diario")))
    }
}

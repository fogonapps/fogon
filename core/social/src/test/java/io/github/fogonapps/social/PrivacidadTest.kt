package io.github.fogonapps.social

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PrivacidadTest {
    @Test fun aceptacionDependeDeSesionYVersionDeLaCuenta() {
        assertFalse(debeAceptarPolitica(false, "", POLITICA_VIGENTE))
        assertTrue(debeAceptarPolitica(true, "2026-11-03", POLITICA_VIGENTE))
        assertTrue(debeAceptarPolitica(true, null, POLITICA_VIGENTE))
        assertFalse(debeAceptarPolitica(true, POLITICA_VIGENTE, POLITICA_VIGENTE))
        assertTrue(debeAceptarPolitica(true, POLITICA_VIGENTE, "otra-version"))
    }
    @Test fun gestionDeCuentaSigueDisponibleDuranteLaPausa() {
        listOf("/cuenta", "/cuenta/politica", "/cuenta/borrar", "/acceso/codigo", "/salir").forEach {
            assertTrue(rutaPermitidaSinPolitica(it))
        }
        listOf("/cajon", "/perfil", "/publicaciones", "/mensajes", "/avisos").forEach {
            assertFalse(rutaPermitidaSinPolitica(it))
        }
    }
    @Test fun revisaLaPrimeraVezYDespuesComoMuchoUnaVezPorMinuto() {
        org.junit.Assert.assertTrue(debeRevisarAlAbrir(0, 5_000))
        org.junit.Assert.assertFalse(debeRevisarAlAbrir(5_000, 30_000))
        org.junit.Assert.assertTrue(debeRevisarAlAbrir(5_000, 65_000))
    }
}

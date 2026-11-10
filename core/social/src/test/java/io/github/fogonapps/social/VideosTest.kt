package io.github.fogonapps.social

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class VideosTest {
    @Test fun recorteInicialConLimiteExacto() {
        assertEquals(TramoVideo(12_345, false), tramoVideo(12_345))
        assertEquals(TramoVideo(30_000, false), tramoVideo(30_000))
        assertEquals(TramoVideo(30_000, true), tramoVideo(30_001))
        assertEquals(TramoVideo(30_000, true), tramoVideo(Long.MAX_VALUE))
        assertThrows(IllegalArgumentException::class.java) { tramoVideo(0) }
    }
    @Test fun noHayPrecargaSoloUnaAccionExplicitaAbreElVideo() {
        // La política conservadora es idéntica en wifi, móvil y ahorro de datos.
        assertFalse(cargarVideo(solicitado = false))
        assertTrue(cargarVideo(solicitado = true))
    }
}

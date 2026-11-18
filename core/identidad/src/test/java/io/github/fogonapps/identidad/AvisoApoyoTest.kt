package io.github.fogonapps.identidad

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvisoApoyoTest {
    private val hoy = LocalDate.of(2025, 3, 1)

    @Test
    fun requiereCatorceDiasDeUso() {
        assertFalse(debeMostrarAvisoApoyo(13, hoy))
        assertTrue(debeMostrarAvisoApoyo(14, hoy))
        assertTrue(debeMostrarAvisoApoyo(15, hoy))
    }

    @Test
    fun esperaTreintaDiasDesdeElAviso() {
        assertFalse(debeMostrarAvisoApoyo(14, hoy, hoy))
        assertFalse(debeMostrarAvisoApoyo(14, hoy, hoy.minusDays(29)))
        assertTrue(debeMostrarAvisoApoyo(14, hoy, hoy.minusDays(30)))
        assertTrue(debeMostrarAvisoApoyo(14, hoy, hoy.minusDays(31)))
    }

    @Test
    fun esperaNoventaDiasSiTocoApoyar() {
        assertFalse(debeMostrarAvisoApoyo(14, hoy, hoy.minusDays(30), tocoApoyar = true))
        assertFalse(debeMostrarAvisoApoyo(14, hoy, hoy.minusDays(89), tocoApoyar = true))
        assertTrue(debeMostrarAvisoApoyo(14, hoy, hoy.minusDays(90), tocoApoyar = true))
        assertTrue(debeMostrarAvisoApoyo(14, hoy, hoy.minusDays(91), tocoApoyar = true))
    }

    @Test
    fun noMuestraElAvisoConUnaFechaAnteriorAlUltimoAviso() {
        assertFalse(debeMostrarAvisoApoyo(14, hoy, hoy.plusDays(1)))
    }

    @Test
    fun respetaLaPreferenciaYPermiteReactivar() {
        val ultimoAviso = hoy.minusDays(30)
        assertFalse(debeMostrarAvisoApoyo(14, hoy, ultimoAviso, apagado = true))
        assertTrue(debeMostrarAvisoApoyo(14, hoy, ultimoAviso, apagado = false))
    }

    @Test
    fun ocultaElAvisoEnPlay() {
        assertFalse(debeMostrarAvisoApoyo(14, hoy, tiendaPlay = true))
    }

    @Test
    fun ocultaElAvisoSiLaPersonaYaApoya() {
        assertFalse(debeMostrarAvisoApoyo(14, hoy, yaApoya = true))
    }

    @Test
    fun ocultaElAvisoDuranteElTutorial() {
        assertFalse(debeMostrarAvisoApoyo(14, hoy, enTutorial = true))
    }
}

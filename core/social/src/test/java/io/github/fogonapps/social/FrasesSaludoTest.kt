package io.github.fogonapps.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrasesSaludoTest {
    private val base = listOf(FraseBase("fz_1", "Uno"), FraseBase("fz_2", "Dos"), FraseBase("fz_3", "De noche", "noche"))

    @Test fun noSaleLoQueNoGusta() {
        repeat(50) { vuelta ->
            val f = elegirFraseSaludo(base, emptyList(), emptyMap(), emptySet(), setOf("fz_1"), dia = 10, hora = 9, vuelta = vuelta)!!
            assertNotEquals("fz_1", f.clave)
        }
    }

    @Test fun lasDeLaNocheSoloDeNocheYDeMadrugada() {
        repeat(50) { v -> assertNotEquals("fz_3", elegirFraseSaludo(base, emptyList(), emptyMap(), emptySet(), emptySet(), 10, 9, v)!!.clave) }
        val deNoche = (0 until 80).map { v -> elegirFraseSaludo(base, emptyList(), emptyMap(), emptySet(), emptySet(), 10, 2, v)!!.clave }
        assertTrue("fz_3" in deNoche)
    }

    @Test fun entranLasDeLaGenteYSuTexto() {
        val gente = listOf(FraseComunidad("c7", "Hoy toca pierna", "es", app = "forzudo"))
        val salidas = (0 until 80).map { v -> elegirFraseSaludo(base, gente, emptyMap(), emptySet(), setOf("fz_1", "fz_2"), 10, 9, v)!! }
        assertTrue(salidas.all { it.clave == "c7" && it.texto == "Hoy toca pierna" })
    }

    @Test fun sinNadaPosible() {
        assertNull(elegirFraseSaludo(emptyList(), emptyList(), emptyMap(), emptySet(), emptySet(), 1, 9))
        assertEquals("madrugada", franjaDelDia(3)); assertEquals("noche", franjaDelDia(22))
    }
}

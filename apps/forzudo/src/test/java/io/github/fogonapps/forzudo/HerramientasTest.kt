package io.github.fogonapps.forzudo

import io.github.fogonapps.forzudo.datos.SerieCalentamiento
import io.github.fogonapps.forzudo.datos.calentamiento
import io.github.fogonapps.forzudo.datos.discos
import io.github.fogonapps.forzudo.datos.medallas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HerramientasTest {
    @Test
    fun discosDeCadaLado() {
        assertEquals(listOf(25.0, 5.0, 2.5), discos(85.0))
        assertEquals(emptyList<Double>(), discos(20.0))
        assertEquals(listOf(1.25), discos(22.5))
        assertNull(discos(21.0))
        assertNull(discos(10.0))
    }

    @Test
    fun calentamientoConBarra() {
        assertEquals(
            listOf(SerieCalentamiento(20.0, 10), SerieCalentamiento(40.0, 8), SerieCalentamiento(60.0, 5), SerieCalentamiento(80.0, 3)),
            calentamiento(100.0),
        )
        assertTrue(calentamiento(20.0).isEmpty())
        // Con mancuernas no hay barra y se redondea a 2 kg.
        assertEquals(listOf(12.0, 18.0, 24.0), calentamiento(30.0, barra = null, paso = 2.0).map { it.kg })
    }

    @Test
    fun medallasPorProgreso() {
        val m = medallas(entrenos = 12, mejorRacha = 5, volumenTotal = 150_000.0, nivelRango = 3).associateBy { it.id }
        assertTrue(m.getValue("entrenos_10").lograda)
        assertTrue(!m.getValue("entrenos_50").lograda)
        assertEquals(12.0 / 50, m.getValue("entrenos_50").progreso, 1e-9)
        assertTrue(m.getValue("racha_4").lograda && !m.getValue("racha_12").lograda)
        assertTrue(m.getValue("volumen_100").lograda && !m.getValue("volumen_1000").lograda)
        assertTrue(m.getValue("rango_3").lograda && !m.getValue("rango_4").lograda)
    }
}

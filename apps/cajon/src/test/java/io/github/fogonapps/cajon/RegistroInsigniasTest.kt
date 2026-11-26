package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.Insignia
import io.github.fogonapps.cajon.datos.Insignias
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** El registro local de insignias ganadas: qué se celebra y qué no. */
class RegistroInsigniasTest {
    @Test fun laPrimeraVezSeAnotanSinCelebrarNiFecha() {
        val r = Insignias.anotar(null, listOf("cine", "lector", "reto"), "2026-11-25")
        assertTrue(r.celebrar.isEmpty())
        assertEquals(mapOf("cine" to "", "lector" to "", "reto" to ""), r.fechas)
    }

    @Test fun laNuevaSeCelebraConSuDia() {
        val r = Insignias.anotar(mapOf("cine" to ""), listOf("cine", "series"), "2026-11-25")
        assertEquals(listOf("series"), r.celebrar)
        assertEquals("2026-11-25", r.fechas["series"])
        assertEquals("", r.fechas["cine"])
    }

    @Test fun muchasDeGolpeSeAnotanSinCelebrar() {
        val r = Insignias.anotar(emptyMap(), listOf("cine", "series", "anime"), "2026-11-25")
        assertTrue(r.celebrar.isEmpty())
        assertEquals(3, r.fechas.size)
    }

    @Test fun elRetoPerdidoSeOlvidaYSeVuelveACelebrar() {
        val sinReto = Insignias.anotar(mapOf("reto" to "2026-03-01", "cine" to "2026-01-01"), listOf("cine"), "2027-01-02")
        assertEquals(setOf("cine"), sinReto.fechas.keys)
        val otraVez = Insignias.anotar(sinReto.fechas, listOf("cine", "reto"), "2027-06-01")
        assertEquals(listOf("reto"), otraVez.celebrar)
    }

    @Test fun lasMetasNoCambian() {
        assertEquals(listOf(50, 10, 10, 12, 30, 30, 10, 1), listOf("cine", "series", "anime", "lector", "discos", "constancia", "critico", "reto").map { Insignias.meta(it) })
    }

    @Test fun losNivelesApilanTalones() {
        assertEquals(listOf(50, 250, 500), Insignias.niveles("cine"))
        assertEquals(listOf(1), Insignias.niveles("reto"))
        assertEquals(1, Insignias.niveles("explorador").size)
        val cine = Insignia("cine", 260, 50, Insignias.niveles("cine"))
        assertEquals(2, cine.nivel)
        assertEquals(250, cine.alcanzada)
        assertEquals(500, cine.siguiente)
        val enCamino = Insignia("cine", 20, 50, Insignias.niveles("cine"))
        assertEquals(0, enCamino.nivel)
        assertEquals(50, enCamino.alcanzada)
    }

    @Test fun cadaNivelSeAnotaYSeCelebraAparte() {
        val claves = Insignias.clavesGanadas(listOf(Insignia("cine", 260, 50, Insignias.niveles("cine")), Insignia("reto", 1, 1)))
        assertEquals(listOf("cine", "cine:2", "reto"), claves)
        assertEquals("cine" to 2, Insignias.deClave("cine:2"))
        assertEquals("reto" to 1, Insignias.deClave("reto"))
        val r = Insignias.anotar(mapOf("cine" to "2026-01-01", "reto" to ""), claves, "2026-11-25")
        assertEquals(listOf("cine:2"), r.celebrar)
    }
}

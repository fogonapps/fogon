package io.github.fogonapps.forzudo

import io.github.fogonapps.forzudo.datos.Biblioteca
import io.github.fogonapps.forzudo.datos.Estado
import io.github.fogonapps.forzudo.datos.Logica
import io.github.fogonapps.forzudo.datos.PesoCorporal
import io.github.fogonapps.forzudo.datos.Serie
import io.github.fogonapps.forzudo.datos.Sesion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RangosTest {
    private val bib = Biblioteca.cargar { File("src/main/assets/datos/$it").readText() }
    private val L = Logica(bib)

    private fun estado(vararg series: Pair<String, List<Serie>>) = Estado(
        body = listOf(PesoCorporal("2026-10-01", 78.0)),
        sessions = listOf(Sesion("s1", "2026-10-01", entries = series.toMap())),
    )

    @Test
    fun masDe12RepeticionesNoInflanEl1RM() {
        // 92 × 14 no cuenta; 80 × 10 sí.
        val e = estado("remo-mancuerna" to listOf(Serie(92.0, 14.0, true), Serie(80.0, 10.0, true)))
        val m = L.mejorE1("remo-mancuerna", e.sessions)!!
        assertEquals(80.0, m.kg, 0.0)
        assertEquals(80 * (1 + 10 / 30.0), m.v, 1e-9)
    }

    @Test
    fun dominadasCuentanPorRepeticiones() {
        val r = L.rangoEjercicio(estado("dominadas" to listOf(Serie(null, 8.0, true), Serie(null, 6.0, true))), "dominadas")
        assertNotNull(r)
        assertEquals("Oro", bib.niveles[r!!.rango.tier].n)
        val meta = L.siguienteMeta("dominadas", r)!!
        assertEquals(0.0, meta.kg, 0.0)          // la meta es en repeticiones, no en kilos
        assertTrue(meta.reps in 9..12)
    }

    @Test
    fun remoConBarraYRemoEnTCuentan() {
        assertNotNull(bib.estandarDe("remo-barra")); assertNotNull(bib.estandarDe("remo-t")); assertNotNull(bib.estandarDe("dominadas"))
    }

    @Test
    fun elConsejoDelMusculoSaleDeLosDosMejores() {
        // Dos ejercicios de espalda muy altos y uno bajo: el bajo no sube el promedio, así que no es el consejo.
        val e = estado(
            "remo-mancuerna" to listOf(Serie(60.0, 8.0, true)),
            "remo-maquina" to listOf(Serie(150.0, 8.0, true)),
            "jalon-amplio" to listOf(Serie(40.0, 8.0, true)),
        )
        val m = bib.musculos.first { it.id == "espalda" }
        val r = L.rangoMusculo(e, m)!!
        assertTrue(r.consejo == null || r.consejo!!.id != "jalon-amplio")
        if (r.rango.s >= 5) assertNull(r.consejo)
    }
}

package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.Estados
import io.github.fogonapps.cajon.datos.Importar
import io.github.fogonapps.cajon.datos.Tipos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetflixTest {
    private var n = 0
    private val id = { p: String -> p + (n++) }

    @Test fun historialDeNetflix() {
        val csv = "Title,Date\n" +
            "\"Stranger Things: Temporada 4: Capítulo 1: El Club Fuego Infernal\",\"25/10/26\"\n" +
            "\"Stranger Things: Temporada 4: Capítulo 2: Vecna\",\"26/10/26\"\n" +
            "\"Dune: Parte Dos\",\"1/11/26\"\n" +
            "\"El Juego del Calamar: Temporada 2: Pan y lotería\",\"3/11/26\"\n"
        val r = Importar.netflix(csv, id, 0)!!
        assertEquals("Netflix", r.deDonde)
        val porTitulo = r.obras.associateBy { it.titulo }
        assertEquals(setOf("Stranger Things", "Dune: Parte Dos", "El Juego del Calamar"), porTitulo.keys)
        assertEquals(Tipos.SERIE, porTitulo.getValue("Stranger Things").tipo)
        assertEquals(Estados.EN_CURSO, porTitulo.getValue("Stranger Things").estado)
        assertEquals("2026-10-25", porTitulo.getValue("Stranger Things").empezada)
        val dune = porTitulo.getValue("Dune: Parte Dos")
        assertEquals(Tipos.PELICULA, dune.tipo)                        // "Parte Dos" sin ": " después del nombre: es una película
        assertEquals(listOf("2026-11-01"), dune.registros.map { it.fecha })
        assertNull(Importar.netflix("Name,Year\nMatrix,1999\n", id, 0))  // no es de Netflix
    }

    @Test fun fechasEstiloEstadosUnidos() {
        val r = Importar.netflix("Title,Date\nArcane: Season 2: Act 1,10/25/26\n", id, 0)!!
        assertEquals("2026-10-25", r.obras.single().empezada)
    }

    private val completa = "Profile Name,Start Time,Duration,Title,Supplemental Video Type\n" +
        "Ana,2026-01-02 00:10:00,01:30:00,Una película,\n" +
        "Luis,2026-01-03 23:50:00,00:40:00,Serie: Season 1: Episode 1,\n" +
        "Ana,2026-01-04 12:00:00,00:01:00,Avance,Trailer\n" +
        "Avances,2026-01-04 12:00:00,00:01:00,Otro avance,Teaser\n"

    @Test fun descargaCompletaFiltraPerfilesYAvances() {
        assertEquals(listOf("Ana", "Luis"), Importar.perfilesNetflix(completa))
        val ana = Importar.netflix(completa, id, 0, "Ana")!!.obras.single()
        assertEquals("Una película", ana.titulo)
        assertEquals("2026-01-02", ana.registros.single().fecha)
        val todas = Importar.netflix(completa, id, 0)!!.obras
        assertEquals(listOf("Una película", "Serie"), todas.map { it.titulo })
        assertEquals("2026-01-03", todas.last().empezada)
        assertNull(Importar.netflix(completa, id, 0, "Inexistente"))
    }

    @Test fun fechaCompletaEsUtcYNoAceptaFechasImposibles() {
        val zona = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Bogota"))
            assertEquals("2026-01-02", Importar.netflix(completa, id, 0, "Ana")!!.obras.single().terminada)
            val invalida = completa.replace("2026-01-02", "2026-02-30")
            assertNull(Importar.netflix(invalida, id, 0, "Ana")!!.obras.single().terminada)
        } finally { java.util.TimeZone.setDefault(zona) }
    }
}

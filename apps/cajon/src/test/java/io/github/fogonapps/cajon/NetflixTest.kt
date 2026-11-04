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
}

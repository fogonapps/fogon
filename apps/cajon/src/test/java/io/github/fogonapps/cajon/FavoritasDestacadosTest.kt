package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.Ajustes
import io.github.fogonapps.cajon.datos.Destacados
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Logica
import io.github.fogonapps.cajon.datos.Obra
import io.github.fogonapps.cajon.datos.Perfil
import io.github.fogonapps.cajon.datos.Resenas
import io.github.fogonapps.cajon.datos.Tipos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Una sola forma de marcar favoritos: los destacados de obras salen de las cuatro favoritas. */
class FavoritasDestacadosTest {
    private fun obra(id: String, tipo: String = Tipos.PELICULA) = Obra(id = id, tipo = tipo, titulo = id)
    private val obras = listOf(obra("a"), obra("b"), obra("c"), obra("d"), obra("e"), obra("s", Tipos.SERIE))

    @Test fun laQuintaFavoritaNoEntraNiReemplaza() {
        val e = Estado(obras = obras, ajustes = Ajustes(favoritas = listOf("a", "b", "c", "d")))
        assertEquals(listOf("a", "b", "c", "d"), Logica.favorita(e, "e", 1).ajustes.favoritas)
    }

    @Test fun soloSeEligenFavoritasDelTipoDelHueco() {
        val e = Estado(obras = obras, ajustes = Ajustes(favoritas = listOf("a", "s")))
        assertEquals(listOf("a"), Logica.favoritasPara(e, Tipos.PELICULA).map { it.id })
        assertEquals(listOf("s"), Logica.favoritasPara(e, Tipos.SERIE).map { it.id })
        assertTrue(Logica.favoritasPara(e, Tipos.LIBRO).isEmpty())
    }

    @Test fun quitarLaEstrellaVaciaSuDestacado() {
        val e = Estado(obras = obras, ajustes = Ajustes(favoritas = listOf("a")),
            perfil = Perfil(destacados = mapOf(Destacados.PELICULA to "a", Destacados.MARCO to "a")))
        val d = Logica.favorita(e, "a", 5).perfil.destacados
        assertFalse(Destacados.PELICULA in d)
        // "La obra que me marcó" no sale de las favoritas: se queda.
        assertEquals("a", d[Destacados.MARCO])
    }

    @Test fun losDestacadosViejosPasanAFavoritasSiHayLugar() {
        val e = Estado(obras = obras, ajustes = Ajustes(favoritas = listOf("a", "b", "c")),
            perfil = Perfil(destacados = mapOf(Destacados.PELICULA to "d", Destacados.SERIE to "s")))
        val m = Logica.favoritasDeDestacados(e)
        assertEquals(4, m.ajustes.favoritas.size)
        assertTrue("d" in m.ajustes.favoritas || "s" in m.ajustes.favoritas)
        // Sin lugar, el destacado se queda como estaba.
        assertEquals(e.perfil.destacados, m.perfil.destacados)
        assertEquals(m, Logica.favoritasDeDestacados(m))
    }

    @Test fun filtroDeResenasPorNota() {
        assertTrue(Resenas.pasaFiltroNota(8, Resenas.FILTRO_BUENAS))
        assertFalse(Resenas.pasaFiltroNota(6, Resenas.FILTRO_BUENAS))
        assertTrue(Resenas.pasaFiltroNota(3, Resenas.FILTRO_MALAS))
        assertFalse(Resenas.pasaFiltroNota(null, Resenas.FILTRO_MALAS))
        assertTrue(Resenas.pasaFiltroNota(null, Resenas.FILTRO_TODAS))
    }
}

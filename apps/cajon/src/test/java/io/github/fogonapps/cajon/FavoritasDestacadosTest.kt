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

/**
 * Favoritas y perfil por separado: la estrella marca favoritas sin tope y no las pone en el perfil; la vitrina del
 * perfil muestra las que la persona elige (varias del mismo tipo, obras o artistas, en orden).
 */
class FavoritasDestacadosTest {
    private fun obra(id: String, tipo: String = Tipos.PELICULA) = Obra(id = id, tipo = tipo, titulo = id)
    private val obras = listOf(obra("a"), obra("b"), obra("c"), obra("d"), obra("e"), obra("s", Tipos.SERIE))

    @Test fun lasFavoritasNoTienenTopeNiVanAlPerfil() {
        val e = Estado(obras = obras, ajustes = Ajustes(favoritas = listOf("a", "b", "c", "d")))
        val m = Logica.favorita(e, "e", 1)
        assertEquals(listOf("a", "b", "c", "d", "e"), m.ajustes.favoritas)
        assertTrue(Logica.vitrina(m).isEmpty())
    }

    @Test fun variasDelMismoTipoEnElPerfilYQuedanComoFavoritas() {
        var e = Estado(obras = obras)
        listOf("a", "b", "s").forEach { e = Logica.alternarEnVitrina(e, it, 2) }
        assertEquals(listOf("a", "b", "s"), Logica.vitrina(e))
        // Ponerla en el perfil la deja como favorita, sin marcarla antes.
        assertEquals(listOf("a", "b", "s"), e.ajustes.favoritas)
        // Quitarla del perfil no le quita la estrella.
        e = Logica.alternarEnVitrina(e, "b", 3)
        assertEquals(listOf("a", "s"), Logica.vitrina(e))
        assertTrue("b" in e.ajustes.favoritas)
        assertEquals(listOf("s", "a"), Logica.vitrina(Logica.moverEnVitrina(e, "s", -1, 4)))
    }

    @Test fun llenaNoEntraNada() {
        val muchas = (1..Logica.MAX_VITRINA + 1).map { obra("o$it") }
        var e = Estado(obras = muchas)
        muchas.forEach { e = Logica.alternarEnVitrina(e, it.id, 1) }
        assertEquals(Logica.MAX_VITRINA, Logica.vitrina(e).size)
        assertFalse("o${Logica.MAX_VITRINA + 1}" in Logica.vitrina(e))
    }

    @Test fun quitarLaEstrellaLaSacaDelPerfilPeroNoDeLaQueMeMarco() {
        val e = Estado(obras = obras, ajustes = Ajustes(favoritas = listOf("a")),
            perfil = Perfil(vitrina = listOf("a"), destacados = mapOf(Destacados.MARCO to "a")))
        val m = Logica.favorita(e, "a", 5)
        assertTrue(Logica.vitrina(m).isEmpty())
        assertEquals("a", m.perfil.destacados[Destacados.MARCO])
    }

    @Test fun sinVitrinaPropiaSeVenLosHuecosDeAntes() {
        val e = Estado(obras = obras, perfil = Perfil(destacados = mapOf(Destacados.SERIE to "s", Destacados.PELICULA to "a", Destacados.MARCO to "d")))
        // En el orden de los huecos de antes, sin «la que me marcó».
        assertEquals(listOf("a", "s"), Logica.vitrina(e))
        // Lo borrado no se muestra.
        assertEquals(listOf("s"), Logica.vitrina(e.copy(obras = obras.map { if (it.id == "a") it.copy(borrada = true) else it })))
    }

    @Test fun laRedRecibeLaVitrinaYLaPrimeraDeCadaTipoParaLasVersionesDeAntes() {
        var e = Estado(obras = obras)
        listOf("b", "a", "s").forEach { e = Logica.alternarEnVitrina(e, it, 1) }
        val subido = RedSocial.destacadosParaLaRed(e, e.vivas.associateBy { it.id })
        val vitrina = subido["vitrina"] as kotlinx.serialization.json.JsonArray
        assertEquals(listOf("b", "a", "s"), vitrina.map { (it as kotlinx.serialization.json.JsonObject)["titulo"].toString().trim('"') })
        assertEquals("\"b\"", (subido[Destacados.PELICULA] as kotlinx.serialization.json.JsonObject)["titulo"].toString())
        assertEquals("\"s\"", (subido[Destacados.SERIE] as kotlinx.serialization.json.JsonObject)["titulo"].toString())
    }

    @Test fun filtroDeResenasPorNota() {
        assertTrue(Resenas.pasaFiltroNota(8, Resenas.FILTRO_BUENAS))
        assertFalse(Resenas.pasaFiltroNota(6, Resenas.FILTRO_BUENAS))
        assertTrue(Resenas.pasaFiltroNota(3, Resenas.FILTRO_MALAS))
        assertFalse(Resenas.pasaFiltroNota(null, Resenas.FILTRO_MALAS))
        assertTrue(Resenas.pasaFiltroNota(null, Resenas.FILTRO_TODAS))
    }
}

package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.ArtistaEncontrado
import io.github.fogonapps.cajon.datos.Encontrado
import io.github.fogonapps.cajon.datos.OrdenBusqueda
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrdenBusquedaTest {
    private var n = 0
    private fun pelicula(titulo: String, votos: Int, original: String = "") =
        Encontrado("pelicula", titulo, null, "", "", "tmdb:movie:${n++}", votos = votos, original = original)
    private fun libro(titulo: String, votos: Int) = Encontrado("libro", titulo, null, "", "", "ol:OL${n++}W", votos = votos)
    private fun disco(titulo: String) = Encontrado("album", titulo, null, "", "", "mb:rg:${n++}")
    /** Un artista de MusicBrainz con los votos de sus etiquetas. */
    private fun artista(nombre: String, etiquetas: Int) = ArtistaEncontrado("mb${n++}", nombre, "", null, "", "", etiquetas = etiquetas)
    private fun escritor(nombre: String) = ArtistaEncontrado("", nombre, "", null, "autor", "", fuente = "ol:author:OL${n++}A")

    @Test fun inceptionDaLaPeliculaYNoUnoDeLosVariosGruposConEseNombre() {
        val grupos = listOf(artista("Inception", 1), artista("Inception", 0), artista("Inception", 0))
        val d = OrdenBusqueda.decidir("inception", listOf(listOf(pelicula("Inception", 37000))), grupos)
        assertNull(d.mejorPersona)
        assertTrue(d.obrasPrimero)
    }

    @Test fun artistasLatinosConPocasEtiquetasSiguenEnGrande() {
        for ((nombre, etiquetas) in listOf("Selena" to 19, "Karol G" to 9, "Morat" to 2, "Camilo" to 4, "Bad Bunny" to 19)) {
            val a = artista(nombre, etiquetas)
            val d = OrdenBusqueda.decidir(nombre.lowercase(), listOf(listOf(pelicula(nombre, 5000))), listOf(a, artista("$nombre Tributo", 0)))
            assertEquals(nombre, a, d.mejorPersona)
            assertTrue(nombre, d.obrasPrimero)
        }
    }

    @Test fun bandaConocidaSigueEnGrandeYLaPeliculaJustoDebajo() {
        for ((nombre, etiquetas) in listOf("The Doors" to 60, "Queen" to 180, "Rush" to 75, "Ghost" to 56)) {
            val banda = artista(nombre, etiquetas)
            val d = OrdenBusqueda.decidir(nombre.lowercase(), listOf(listOf(pelicula(nombre, 1500))), listOf(banda))
            assertEquals(nombre, banda, d.mejorPersona)
            assertTrue(nombre, d.obrasPrimero)
        }
    }

    @Test fun escritorSinPopularidadCedeAnteUnaPeliculaMuyConocida() {
        val d = OrdenBusqueda.decidir("joker", listOf(listOf(pelicula("Joker", 25000))), listOf(escritor("Joker")))
        assertNull(d.mejorPersona)
        assertTrue(d.obrasPrimero)
    }

    @Test fun escritorNoCedeAnteUnaPeliculaPocoVotada() {
        val autor = escritor("Joker")
        val d = OrdenBusqueda.decidir("joker", listOf(listOf(pelicula("Joker", 300))), listOf(autor))
        assertEquals(autor, d.mejorPersona)
    }

    @Test fun libroConocidoEnOpenLibraryConSuUmbral() {
        assertTrue(OrdenBusqueda.muyConocida(libro("Dune", 465)))
        assertFalse(OrdenBusqueda.muyConocida(libro("Dune", 40)))
        assertFalse(OrdenBusqueda.muyConocida(pelicula("Dune", 465)))
        assertFalse(OrdenBusqueda.muyConocida(disco("Dune")))
    }

    @Test fun libroConElTituloVaAntesQueEscritoresConEsaPalabra() {
        val d = OrdenBusqueda.decidir("Dune", listOf(listOf(libro("Dune", 2500), libro("Dune Messiah", 900))),
            listOf(escritor("Kyra Dune"), escritor("Dune Rats")))
        assertNull(d.mejorPersona)
        assertTrue(d.obrasPrimero)
    }

    @Test fun discoHomonimoNoLeQuitaElLugarAlArtista() {
        val a = artista("Taylor Swift", 241)
        assertEquals(a, OrdenBusqueda.decidir("taylor swift", listOf(listOf(disco("Taylor Swift"))), listOf(a)).mejorPersona)
        val poco = artista("Inception", 0)
        assertEquals(poco, OrdenBusqueda.decidir("inception", listOf(listOf(disco("Inception"))), listOf(poco)).mejorPersona)
    }

    @Test fun sinObraConEseTituloSiguePrimeroLaPersona() {
        val a = artista("Radiohead", 200)
        val d = OrdenBusqueda.decidir("radiohead", listOf(listOf(disco("OK Computer"))), listOf(a))
        assertEquals(a, d.mejorPersona)
        assertFalse(d.obrasPrimero)
    }

    @Test fun personaPorTodasLasPalabrasNoPierdeFrenteAUnaObraSinVotos() {
        val autor = escritor("Gabriel García Márquez")
        assertEquals(autor, OrdenBusqueda.decidir("garcia marquez", listOf(listOf(libro("García Márquez", 0))), listOf(autor)).mejorPersona)
        val sinObra = OrdenBusqueda.decidir("garcia marquez", listOf(listOf(libro("Cien años de soledad", 4000))), listOf(autor))
        assertEquals(autor, sinObra.mejorPersona)
        assertFalse(sinObra.obrasPrimero)
    }

    @Test fun soloCuentanLosPrimerosTresDeCadaTipo() {
        val lista = listOf(pelicula("A", 10), pelicula("B", 10), pelicula("C", 10), pelicula("Inception", 50000))
        assertFalse(OrdenBusqueda.decidir("inception", listOf(lista), listOf(artista("Inception", 1))).obrasPrimero)
    }

    @Test fun textoVacioNoDecideNada() {
        val d = OrdenBusqueda.decidir("  ", listOf(listOf(pelicula("Inception", 50000))), listOf(artista("Inception", 1)))
        assertNull(d.mejorPersona)
        assertFalse(d.obrasPrimero)
    }

    @Test fun inceptionEnEspanolSeReconocePorElTituloOriginal() {
        val grupos = listOf(artista("Inception", 1), artista("Inception", 0))
        val origen = listOf(pelicula("El origen", 37000, original = "Inception"))
        val d = OrdenBusqueda.decidir("inception", listOf(listOf(disco("Inception")), origen), grupos)
        assertNull(d.mejorPersona)
        assertTrue(d.obrasPrimero)
        // Y el grupo de las películas va antes que el de la banda sonora: se parecen igual y la película tiene más votos.
        assertEquals(3, OrdenBusqueda.parecido("inception", origen))
        assertTrue(OrdenBusqueda.votosExactos("inception", origen) > OrdenBusqueda.votosExactos("inception", listOf(disco("Inception"))))
    }

    @Test fun enLibrosDuneVaAntesQueLosEscritoresAunqueElTituloTraigaLaEdicion() {
        // Open Library da «Dune / Dune» (la edición bilingüe) y ningún escritor se llama «Dune».
        val d = OrdenBusqueda.decidir("dune", listOf(listOf(libro("Dune / Dune", 464), libro("El Mesias De Dune / Dune Messiah", 149))),
            listOf(escritor("Kyra Dune"), escritor("Heather Dune Macadam")))
        assertNull(d.mejorPersona)
        assertTrue(d.obrasPrimero)
        // Solo empezar así no basta: «taylor» sigue mostrando primero a los artistas aunque haya un disco «Taylor Swift».
        assertFalse(OrdenBusqueda.decidir("taylor", listOf(listOf(disco("Taylor Swift"))), listOf(artista("Taylor Swift", 241))).obrasPrimero)
    }

    @Test fun elEscritorVaPrimeroSoloSiSeLlamaComoLoBuscado() {
        val autor = escritor("Frank Herbert")
        val d = OrdenBusqueda.decidir("frank herbert", listOf(listOf(libro("Frank Herbert: a biography", 3))), listOf(autor))
        assertEquals(autor, d.mejorPersona)
    }

    @Test fun duneEnTodoDaLaObraYNoElGrupoAleman() {
        // El grupo alemán «Dune» tiene 13 etiquetas (más que el umbral de sin señal) y hay otro «Dune» británico.
        val grupos = listOf(artista("Dune", 13), artista("Dune", 0))
        val d = OrdenBusqueda.decidir("dune", listOf(listOf(disco("Dune")), listOf(pelicula("Dune", 13500)), listOf(libro("Dune / Dune", 464))), grupos)
        assertNull(d.mejorPersona)
        assertTrue(d.obrasPrimero)
    }

    @Test fun unaObraClasicaNoLeQuitaElLugarAUnArtistaConocido() {
        for ((nombre, etiquetas) in listOf("Selena" to 19, "Rush" to 75, "Ghost" to 56)) {
            val a = artista(nombre, etiquetas)
            assertEquals(nombre, a, OrdenBusqueda.decidir(nombre.lowercase(), listOf(listOf(pelicula(nombre, 20000))), listOf(a)).mejorPersona)
        }
    }

    @Test fun laPersonaEnGrandeNoSeRepiteEnLaLista() {
        val karol = artista("Karol G", 9)
        val otra = artista("KAROL G", 0)
        val directora = ArtistaEncontrado("", "Karol G", "", null, "director", "", fuente = "tmdb:person:1")
        val resto = artista("Karol G Tributo", 0)
        assertEquals(listOf(resto), OrdenBusqueda.sinLaMejor(listOf(karol, otra, resto), karol))
        assertTrue(OrdenBusqueda.sinLaMejor(listOf(directora), karol).isEmpty())
        assertEquals(listOf(karol, resto), OrdenBusqueda.sinLaMejor(listOf(karol, resto), null))
    }

    /**
     * «dune» en «Todo», con lo que traen las fuentes: la película (con su título en español y el original), las series,
     * los libros, los discos y el grupo alemán «Dune». La obra clásica va primero y Películas encabeza los grupos.
     */
    @Test fun duneEnTodoDaLaPeliculaPrimeroYNoElGrupoAleman() {
        val obras = mapOf(
            "pelicula" to listOf(pelicula("Duna", 16028, original = "Dune"), pelicula("Duna: Parte dos", 9045, original = "Dune: Part Two"), pelicula("Duna: Parte tres", 0)),
            "serie" to listOf(Encontrado("serie", "Dune: La profecía", null, "", "", "tmdb:tv:${n++}", votos = 900, original = "Dune: Prophecy")),
            "libro" to listOf(libro("Dune / Dune", 464), libro("El Mesias De Dune / Dune Messiah", 149), libro("Hijos De Dune/Children of Dune", 55)),
            "album" to listOf(disco("Dune"), disco("Dune: Part Two (Original Motion Picture Soundtrack)")),
        )
        val orden = listOf("album", "pelicula", "serie", "anime", "libro", "juego")
        val grupoAleman = artista("Dune", 13)
        val personas = listOf(grupoAleman, artista("Herman Dune", 2), escritor("Frank Herbert"))
        val d = OrdenBusqueda.decidir("dune", orden.map { obras[it].orEmpty() }, personas)
        assertNull(d.mejorPersona)
        assertTrue(d.obrasPrimero)
        val grupos = OrdenBusqueda.ordenGrupos("dune", orden, obras).filter { obras[it].orEmpty().isNotEmpty() }
        assertEquals("pelicula", grupos.first())
        assertTrue("serie" in grupos && "libro" in grupos && "album" in grupos)
        // Sin la película (TMDB aún no respondió), el grupo sí iría en grande: por eso la película no puede faltar.
        assertEquals(grupoAleman, OrdenBusqueda.decidir("dune", orden.map { if (it == "pelicula" || it == "serie") emptyList() else obras[it].orEmpty() }, personas).mejorPersona)
    }
}

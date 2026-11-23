package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.ImagenElegida
import io.github.fogonapps.cajon.datos.Imagenes
import io.github.fogonapps.cajon.datos.Estados
import io.github.fogonapps.cajon.datos.JsonCajon
import io.github.fogonapps.cajon.datos.Logica
import io.github.fogonapps.cajon.datos.Obra
import io.github.fogonapps.cajon.datos.Tipos
import io.github.fogonapps.cajon.datos.WikidataJuegos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los videojuegos en el modelo, en las secciones y en la barra de pestañas. */
class JuegosTest {
    @Test fun elTipoJuegoVaAlFinalYNoCambiaLosDeAntes() {
        assertEquals("juego", Tipos.JUEGO)
        assertEquals(listOf("pelicula", "serie", "anime", "libro", "album", "juego"), Tipos.TODOS)
        assertFalse(Tipos.porEpisodios(Tipos.JUEGO))
    }

    @Test fun unJuegoConPlataformasSeGuardaYSeLeeIgual() {
        val juego = Obra("o1", Tipos.JUEGO, "Hollow Knight", anio = 2017, creador = "Team Cherry", fuente = "wd:Q29300592",
            plataformas = listOf("PC", "Nintendo Switch"), estado = Estados.EN_CURSO)
        val e = Estado(obras = listOf(juego))
        val texto = JsonCajon.encodeToString(Estado.serializer(), e)
        assertTrue("\"plataformas\"" in texto)
        assertEquals(e, JsonCajon.decodeFromString(Estado.serializer(), texto))
    }

    @Test fun unaObraViejaSinPlataformasCargaIgual() {
        val viejo = """{"obras":[{"id":"o1","tipo":"pelicula","titulo":"Matrix","estado":"terminado"},{"id":"o2","tipo":"juego","titulo":"Celeste"}]}"""
        val e = JsonCajon.decodeFromString(Estado.serializer(), viejo)
        assertEquals(emptyList<String>(), e.obras[0].plataformas)
        assertEquals(Tipos.JUEGO, e.obras[1].tipo)
        assertEquals(Estados.QUIERO, e.obras[1].estado)
        // Sin plataformas no se escribe el campo: el formato de lo de antes no cambia.
        assertFalse("plataformas" in JsonCajon.encodeToString(Estado.serializer(), e))
    }

    @Test fun lasSeccionesDeJuegos() {
        assertEquals(Secciones.JUEGOS, Secciones.de(Tipos.JUEGO))
        assertEquals(listOf(Tipos.JUEGO), Secciones.tipos(Secciones.JUEGOS))
        assertEquals(Secciones.JUEGOS, Secciones.dePestana(Pestana.JUEGOS))
        assertTrue(Tipos.JUEGO in Secciones.tipos(Secciones.TODO))
        // Los de antes siguen donde estaban (y nada de juegos cae en películas).
        assertEquals(Secciones.PELICULAS, Secciones.de(Tipos.PELICULA))
        assertEquals(Secciones.MUSICA, Secciones.de(Tipos.ALBUM))
        assertFalse(Tipos.JUEGO in Secciones.tipos(Secciones.CINE))
        // Los filtros de siempre no traen Juegos: se suman solo a quien usa los juegos ([Secciones.busqueda]).
        assertFalse(Secciones.JUEGOS in Secciones.BUSQUEDA)
    }

    @Test fun laBarraDeSiempreNoTraeJuegos() {
        val deSiempre = Pestana.visibles(emptyList())
        assertFalse(Pestana.JUEGOS in deSiempre)
        assertEquals(listOf(Pestana.INICIO, Pestana.PARA_TI, Pestana.CINE, Pestana.MUSICA, Pestana.LIBROS), deSiempre)
        // Con el orden viejo tampoco.
        assertFalse(Pestana.JUEGOS in Pestana.visibles(emptyList(), listOf("LIBROS", "CINE")))
        // La barra que alguien ya eligió no cambia.
        val elegida = listOf("INICIO", "LIBROS", "MUSICA")
        assertEquals(listOf(Pestana.INICIO, Pestana.LIBROS, Pestana.MUSICA), Pestana.visibles(elegida))
        // Al completar las tres mínimas tampoco se cuela.
        assertFalse(Pestana.JUEGOS in Pestana.visibles(listOf("INICIO")))
    }

    @Test fun juegosSePoneYSeQuitaComoLasDemas() {
        val con = Pestana.conPestana(emptyList(), emptyList(), Pestana.JUEGOS, true)
        assertEquals(Pestana.JUEGOS, con.last())
        assertEquals(6, con.size)
        val nombres = con.map { it.name }
        assertEquals(con, Pestana.visibles(nombres))
        val sin = Pestana.conPestana(nombres, emptyList(), Pestana.JUEGOS, false)
        assertFalse(Pestana.JUEGOS in sin)
        assertEquals(5, sin.size)
        // En una barra elegida de tres, se suma al final y se puede volver a quitar.
        val tres = listOf("INICIO", "CINE", "LIBROS")
        val conJuegos = Pestana.conPestana(tres, emptyList(), Pestana.JUEGOS, true)
        assertEquals(listOf(Pestana.INICIO, Pestana.CINE, Pestana.LIBROS, Pestana.JUEGOS), conJuegos)
        assertEquals(listOf(Pestana.INICIO, Pestana.CINE, Pestana.LIBROS), Pestana.conPestana(conJuegos.map { it.name }, emptyList(), Pestana.JUEGOS, false))
        // Inicio nunca se quita y no se baja de tres.
        assertEquals(conJuegos, Pestana.conPestana(conJuegos.map { it.name }, emptyList(), Pestana.INICIO, false))
        assertEquals(listOf(Pestana.INICIO, Pestana.CINE, Pestana.JUEGOS),
            Pestana.conPestana(listOf("INICIO", "CINE", "JUEGOS"), emptyList(), Pestana.JUEGOS, false))
    }

    @Test fun losJuegosSalenEnCifrasYRetosSoloSiHayAlgoDeJuegos() {
        val sinJuegos = Estado(obras = listOf(Obra("o1", Tipos.PELICULA, "Matrix")))
        assertFalse(Tipos.JUEGO in Secciones.tiposVisibles(sinJuegos))
        assertEquals(Tipos.TODOS - Tipos.JUEGO, Secciones.tiposVisibles(sinJuegos))
        // Con la pestaña visible.
        val conPestana = sinJuegos.copy(ajustes = sinJuegos.ajustes.copy(barra = Pestana.conPestana(emptyList(), emptyList(), Pestana.JUEGOS, true).map { it.name }))
        assertEquals(Tipos.TODOS, Secciones.tiposVisibles(conPestana))
        // Con un juego vivo (uno borrado no cuenta).
        assertTrue(Tipos.JUEGO in Secciones.tiposVisibles(Estado(obras = listOf(Obra("o2", Tipos.JUEGO, "Celeste")))))
        assertFalse(Tipos.JUEGO in Secciones.tiposVisibles(Estado(obras = listOf(Obra("o2", Tipos.JUEGO, "Celeste", borrada = true)))))
        // Con un reto de juegos.
        assertTrue(Tipos.JUEGO in Secciones.tiposVisibles(sinJuegos.copy(ajustes = sinJuegos.ajustes.copy(retos = mapOf(Tipos.JUEGO to 5)))))
        assertFalse(Tipos.JUEGO in Secciones.tiposVisibles(sinJuegos.copy(ajustes = sinJuegos.ajustes.copy(retos = mapOf(Tipos.JUEGO to 0)))))
    }

    @Test fun lasPlataformasEscritasAMano() {
        assertEquals(listOf("PC", "Switch"), Logica.plataformas(" PC, Switch,, pc ,"))
        assertEquals(listOf("PS5", "Xbox"), Logica.plataformas("PS5 · Xbox"))
        assertEquals(emptyList<String>(), Logica.plataformas("  "))
        assertEquals(12, Logica.plataformas((1..20).joinToString(",") { "P$it" }).size)
    }

    @Test fun wikidataEntregaJuegos() {
        val j = WikidataJuegos.encontrado(WikidataJuegos.Juego("Q1", "Celeste", 2018, emptyList(), emptyList(), ""), emptyMap())
        assertEquals(Tipos.JUEGO, j.tipo)
        assertEquals(Secciones.JUEGOS, Secciones.de(j.tipo))
    }

    /* ----- Paso 3: buscador, ficha y crédito ----- */

    @Test fun juegosEnElBuscadorSoloSiSeUsan() {
        val sinJuegos = Estado(obras = listOf(Obra("o1", Tipos.PELICULA, "Matrix")))
        assertEquals(Secciones.BUSQUEDA, Secciones.busqueda(sinJuegos))
        val conJuego = Estado(obras = listOf(Obra("o2", Tipos.JUEGO, "Celeste")))
        val filtros = Secciones.busqueda(conJuego)
        assertTrue(Secciones.JUEGOS in filtros)
        // Antes de Personas, que sigue al final.
        assertEquals(listOf(Secciones.JUEGOS, Secciones.PERSONAS), filtros.takeLast(2))
        assertEquals(Secciones.BUSQUEDA.size + 1, filtros.size)
    }

    @Test fun elMasDeJuegosAbreElBuscadorDeJuegos() {
        assertEquals(Pantalla.Buscar(Secciones.JUEGOS), io.github.fogonapps.cajon.ui.agregarDesde(Pestana.JUEGOS))
        assertEquals(Pantalla.Buscar(Secciones.LIBROS), io.github.fogonapps.cajon.ui.agregarDesde(Pestana.LIBROS))
    }

    @Test fun elCreditoDeLaPortadaSeVeSoloSiEsLaDeCommonsYTieneAutor() {
        val url = "https://commons.wikimedia.org/wiki/Special:FilePath/Celeste.png?width=500"
        val i = ImagenElegida("wikimedia", url, "Ana", "CC BY 4.0")
        val o = Obra("o1", Tipos.JUEGO, "Celeste", portada = url, imagen = i)
        assertEquals("Ana · CC BY 4.0", io.github.fogonapps.cajon.ui.creditoImagen(o))
        // Sin portada (respaldo), con otra portada, sin autor o de otra fuente: nada.
        assertNull(io.github.fogonapps.cajon.ui.creditoImagen(o.copy(portada = "")))
        assertNull(io.github.fogonapps.cajon.ui.creditoImagen(o.copy(portada = "https://image.tmdb.org/t/p/w342/x.jpg")))
        assertNull(io.github.fogonapps.cajon.ui.creditoImagen(o.copy(imagen = i.copy(autor = null))))
        assertNull(io.github.fogonapps.cajon.ui.creditoImagen(o.copy(imagen = i.copy(fuente = "tmdb"))))
        assertNull(io.github.fogonapps.cajon.ui.creditoImagen(o.copy(imagen = null)))
    }

    @Test fun actualizarUnJuegoDeWikidataNoPierdeElCreditoNiLoPropio() {
        val url = "https://commons.wikimedia.org/wiki/Special:FilePath/Celeste.png?width=500"
        val i = ImagenElegida("wikimedia", url, "Ana", "CC BY 4.0")
        val guardado = Obra("o1", Tipos.JUEGO, "Celeste", portada = url, portadaRecomendada = url, imagen = i, fuente = "wd:Q1")
        // Llegó con su imagen y crédito: se usa la nueva.
        val nueva = i.copy(autor = "Ana María")
        assertEquals(nueva, Imagenes.alRenovar(guardado, Obra("", Tipos.JUEGO, "Celeste", portada = url, imagen = nueva)))
        // Commons falló (sin imagen en el detalle) y la portada sigue igual: el crédito se queda.
        assertEquals(i, Imagenes.alRenovar(guardado, Obra("", Tipos.JUEGO, "Celeste")))
        // Sin portada antes ni ahora: nada que acreditar.
        assertNull(Imagenes.alRenovar(Obra("o2", Tipos.JUEGO, "Otro", fuente = "wd:Q2"), Obra("", Tipos.JUEGO, "Otro")))
        // La que eligió la persona no se toca.
        val propia = ImagenElegida("local", "file:///x.jpg")
        assertEquals(propia, Imagenes.alRenovar(guardado.copy(portada = "file:///x.jpg", portadaPropia = true, imagen = propia),
            Obra("", Tipos.JUEGO, "Celeste", portada = url, imagen = i)))
    }
}

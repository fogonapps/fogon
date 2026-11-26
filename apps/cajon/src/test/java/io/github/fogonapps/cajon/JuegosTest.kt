package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.Ajustes
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
        assertTrue(Tipos.JUEGO in Secciones.tipos(Secciones.TODO))
        // Los de antes siguen donde estaban (y nada de juegos cae en películas).
        assertEquals(Secciones.PELICULAS, Secciones.de(Tipos.PELICULA))
        assertEquals(Secciones.MUSICA, Secciones.de(Tipos.ALBUM))
        assertFalse(Tipos.JUEGO in Secciones.tipos(Secciones.CINE))
        // El buscador trae Juegos siempre, como la barra, antes de Personas.
        assertEquals(listOf(Secciones.JUEGOS, Secciones.PERSONAS), Secciones.BUSQUEDA.takeLast(2))
    }

    @Test fun losJuegosSeVenSalvoQueLaBarraGuardadaLosQuitara() {
        assertTrue(Pestana.juegosVisibles(emptyList()))
        assertTrue(Pestana.juegosVisibles(listOf("INICIO", "JUEGOS")))
        assertFalse(Pestana.juegosVisibles(listOf("INICIO", "LIBROS", "MUSICA")))
    }

    @Test fun juegosLlegaUnaVezALaBarraElegida() {
        val elegida = Estado(ajustes = Ajustes(barra = listOf("INICIO", "LIBROS", "MUSICA"), cambio = 7))
        val migrada = Logica.normalizar(elegida)
        assertEquals(listOf("INICIO", "LIBROS", "MUSICA", "JUEGOS"), migrada.ajustes.barra)
        assertTrue(migrada.ajustes.juegosEnBarra)
        // Normalizar otra vez no la repite.
        assertEquals(migrada.ajustes, Logica.normalizar(migrada).ajustes)
        // Si la quita en Ajustes, no vuelve: ni al leer de nuevo ni tras pasar por el JSON guardado.
        val sin = migrada.ajustes.barra - "JUEGOS"
        val quitada = migrada.copy(ajustes = migrada.ajustes.copy(barra = sin, cambio = 8))
        val leida = Logica.normalizar(JsonCajon.decodeFromString(Estado.serializer(), JsonCajon.encodeToString(Estado.serializer(), quitada)))
        assertFalse("JUEGOS" in leida.ajustes.barra)
        assertFalse(Pestana.juegosVisibles(leida.ajustes.barra))
        // Quien ya la tenía o usa la barra de siempre queda igual, solo con la marca.
        val conJuegos = Ajustes(barra = listOf("INICIO", "JUEGOS", "CINE"))
        assertEquals(conJuegos.barra, Logica.conJuegosEnBarra(conJuegos).barra)
        assertEquals(emptyList<String>(), Logica.normalizar(Estado()).ajustes.barra)
        assertEquals(Estado(), Logica.normalizar(Estado()))   // la barra de siempre no se toca (ya trae Juegos)
        // Una barra elegida en Ajustes lleva la marca: sin Juegos, queda sin Juegos.
        val nueva = Ajustes(barra = listOf("INICIO", "CINE", "LIBROS"), juegosEnBarra = true)
        assertEquals(nueva, Logica.conJuegosEnBarra(nueva))
    }

    @Test fun juegosQuitadaNoVuelveAlUnirConUnaVersionVieja() {
        // A (3.2.1) quitó Juegos; B (3.2, no conoce la marca) cambió después otro ajuste y subió su copia.
        val a = Estado(ajustes = Ajustes(barra = listOf("INICIO", "CINE", "LIBROS"), juegosEnBarra = true, cambio = 1))
        val b = Estado(ajustes = Ajustes(nombre = "Caro", barra = listOf("INICIO", "CINE", "LIBROS"), cambio = 2))
        val unida = Logica.normalizar(Logica.unir(a, b))
        assertEquals("Caro", unida.ajustes.nombre)
        assertFalse("JUEGOS" in unida.ajustes.barra)
        assertTrue(unida.ajustes.juegosEnBarra)
    }

    @Test fun losJuegosSalenEnCifrasYRetosSoloSiHayAlgoDeJuegos() {
        // Con la barra de siempre (que trae Juegos) salen.
        assertEquals(Tipos.TODOS, Secciones.tiposVisibles(Estado(obras = listOf(Obra("o1", Tipos.PELICULA, "Matrix")))))
        // Con una barra elegida sin Juegos, no.
        val sinJuegos = Estado(ajustes = Ajustes(barra = listOf("INICIO", "CINE", "LIBROS"), juegosEnBarra = true), obras = listOf(Obra("o1", Tipos.PELICULA, "Matrix")))
        assertFalse(Tipos.JUEGO in Secciones.tiposVisibles(sinJuegos))
        assertEquals(Tipos.TODOS - Tipos.JUEGO, Secciones.tiposVisibles(sinJuegos))
        // Con un juego vivo (uno borrado no cuenta).
        assertTrue(Tipos.JUEGO in Secciones.tiposVisibles(sinJuegos.copy(obras = listOf(Obra("o2", Tipos.JUEGO, "Celeste")))))
        assertFalse(Tipos.JUEGO in Secciones.tiposVisibles(sinJuegos.copy(obras = listOf(Obra("o2", Tipos.JUEGO, "Celeste", borrada = true)))))
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

    @Test fun juegosEnElBuscadorSiempre() {
        assertTrue(Secciones.JUEGOS in Secciones.BUSQUEDA)
        assertEquals(listOf(Secciones.JUEGOS, Secciones.PERSONAS), Secciones.BUSQUEDA.takeLast(2))
    }

    @Test fun elMasBuscaEnElApartadoQueSeMiraEnLoTuyo() {
        assertEquals(Pantalla.Buscar(Secciones.JUEGOS), io.github.fogonapps.cajon.ui.agregarDesde(Pestana.LO_TUYO, Secciones.JUEGOS))
        assertEquals(Pantalla.Buscar(Secciones.PELICULAS), io.github.fogonapps.cajon.ui.agregarDesde(Pestana.LO_TUYO, Secciones.CINE))
        assertEquals(Pantalla.Buscar(Secciones.TODO), io.github.fogonapps.cajon.ui.agregarDesde(Pestana.INICIO, Secciones.LIBROS))
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

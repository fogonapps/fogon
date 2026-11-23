package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

/** Los juegos de IGDB: respuestas inventadas con la forma de la API (nombres, ids e imágenes de muestra). */
class IgdbTest {
    private fun json(s: String) = JsonCajon.parseToJsonElement(s).jsonObject

    private val ficha = json("""{
      "id": 900001, "name": "Cueva de Prueba", "slug": "cueva-de-prueba", "first_release_date": 1487894400,
      "cover": {"id": 1, "image_id": "co0abc"},
      "platforms": [{"id": 6, "name": "PC (Microsoft Windows)"}, {"id": 130, "name": "Nintendo Switch"}, {"id": 6, "name": "PC (Microsoft Windows)"}],
      "involved_companies": [
        {"id": 1, "company": {"id": 10, "name": "Editorial Grande, Inc."}, "developer": false, "publisher": true},
        {"id": 2, "company": {"id": 11, "name": "Estudio Chico"}, "developer": true, "publisher": false}],
      "genres": [{"id": 8, "name": "Platform"}, {"id": 32, "name": "Indie"}, {"id": 99, "name": "Genero Nuevo"}],
      "themes": [{"id": 1, "name": "Action"}, {"id": 17, "name": "Fantasy"}],
      "rating": 87.66, "rating_count": 1234, "aggregated_rating": 91.0, "aggregated_rating_count": 21,
      "summary": "A short invented summary.", "url": "https://www.igdb.com/games/cueva-de-prueba",
      "screenshots": [{"id": 1, "image_id": "sc0001"}, {"id": 2, "image_id": "sc0002"}],
      "artworks": [{"id": 3, "image_id": "ar0001"}],
      "websites": [
        {"id": 1, "url": "http://cueva.example", "type": 1},
        {"id": 2, "url": "https://twitter.com/cueva", "type": 5},
        {"id": 3, "url": "https://store.steampowered.com/app/123", "type": 13},
        {"id": 4, "url": "https://en.wikipedia.org/wiki/Cueva", "type": 3},
        {"id": 5, "url": "https://cueva.wiki.example/w/Cueva", "type": 2},
        {"id": 6, "url": "https://www.gog.com/game/cueva", "type": 17}]
    }""")

    private val nombres = GenerosJuego.Nombres(List(WikidataJuegos.GENEROS.size) { "g$it" }.toMutableList().also { it[0] = "Acción"; it[9] = "Plataformas" },
        listOf("Independiente", "Arcade"))

    @Test fun fichaComoObra() {
        val o = Igdb.obra(ficha)
        assertEquals("igdb:900001", o.fuente)
        assertEquals(Tipos.JUEGO, o.tipo)
        assertEquals("Cueva de Prueba", o.titulo)
        assertEquals(2017, o.anio)
        // Solo los que desarrollaron (sin la editorial), sin comas.
        assertEquals("Estudio Chico", o.creador)
        assertEquals(listOf("PC", "Nintendo Switch"), o.plataformas)
        // Guardados como claves (el tema Acción primero); uno que no está en ninguna tabla, con su nombre de IGDB.
        assertEquals(listOf("Q270948", "Q828322", "igdb-genero:indie", "Genero Nuevo"), o.generos)
        assertEquals(listOf("Acción", "Plataformas", "Independiente", "Genero Nuevo"), o.generos.map { GenerosJuego.nombre(it, nombres) })
        assertEquals(8.8, o.publica!!, 0.001)
        assertEquals(1234, o.votos)
        assertEquals(9.1, o.criticos!!, 0.001)
        assertEquals(21, o.votosCriticos)
        assertEquals("A short invented summary.", o.sinopsis)
        assertEquals("https://images.igdb.com/igdb/image/upload/t_cover_big_2x/co0abc.jpg", o.portada)
        assertEquals("https://images.igdb.com/igdb/image/upload/t_screenshot_big/ar0001.jpg", o.fondo)
    }

    @Test fun sinVotosNoHayNota() {
        val o = Igdb.obra(json("""{"id": 5, "name": "Sin notas", "rating": 70.0, "rating_count": 0}"""))
        assertNull(o.publica)
        assertNull(o.criticos)
        assertEquals("", o.portada)
        assertNull(o.anio)
    }

    @Test fun editorialSiNoHayDesarrollador() {
        val j = json("""{"id": 5, "name": "X", "involved_companies": [{"company": {"name": "Solo Edita"}, "developer": false, "publisher": true}]}""")
        assertEquals(listOf("Solo Edita"), Igdb.estudios(j))
    }

    @Test fun encontradoParaLaBusqueda() {
        val e = Igdb.encontrado(ficha)!!
        assertEquals("igdb:900001", e.fuente)
        assertEquals("Estudio Chico", e.creador)
        assertEquals("PC · Nintendo Switch", e.detalle)
        assertEquals("https://images.igdb.com/igdb/image/upload/t_cover_big/co0abc.jpg", e.portada)
        assertNull(Igdb.encontrado(json("""{"id": 7, "name": "  "}""")))
        assertNull(Igdb.encontrado(json("""{"name": "Sin id"}""")))
    }

    @Test fun ordenConVotosYSinRepetidosVacios() {
        val l = JsonCajon.parseToJsonElement("""[
          {"id": 1, "name": "Mina"},
          {"id": 2, "name": "Mina Torre"},
          {"id": 3, "name": "Mina: Edicion Java", "rating_count": 2000, "aggregated_rating_count": 14},
          {"id": 4, "name": "Mina", "rating_count": 0},
          {"id": 5, "name": "Ruina", "rating_count": 10},
          {"id": 6, "name": "Ruina", "rating_count": 900}
        ]""").jsonArray.map { it.jsonObject }
        val ids = Igdb.ordenar(l).map { it["id"].toString() }
        // El muy votado sube; el «Mina» repetido sin votos se va; dos «Ruina» con votos son juegos distintos.
        assertEquals(listOf("3", "6", "5", "1", "2"), ids)
    }

    /** Con datos de la forma que da IGDB: lo reciente y popular primero, y el nombre exacto arriba. */
    @Test fun ordenPorParecidoPopularidadYFecha() {
        fun j(id: Int, nombre: String, anio: Int?, votos: Int, hypes: Int = 0) = json(
            """{"id": $id, "name": "$nombre", "total_rating_count": $votos, "hypes": $hypes""" +
                (anio?.let { ", \"first_release_date\": ${java.time.LocalDate.of(it, 6, 1).toEpochDay() * 86400}" } ?: "") + "}")
        val fifa = listOf(j(1, "FIFA: Road to World Cup 98", 1997, 125), j(2, "FIFA Soccer 2005", 2004, 95), j(3, "FIFA 22", 2021, 114),
            j(4, "FIFA 21: Legacy Edition", 2020, 0), j(5, "FIFA 14", 2013, 281), j(6, "FIFA 21", 2020, 134), j(7, "FIFA 23", 2022, 112))
        val f = Igdb.ordenar(fifa, "FIFA", anio = 2026).map { it["id"].toString() }
        assertEquals(setOf("7", "3", "6"), f.take(3).toSet())
        assertTrue(f.indexOf("1") > f.indexOf("5"))
        val zelda = listOf(j(1, "The Legend of Zelda", 1986, 742), j(2, "The Legend of Zelda: A Link to the Past", 1991, 1746),
            j(3, "The Legend of Zelda: Ocarina of Time", 1998, 2197), j(4, "The Legend of Zelda: Breath of the Wild", 2017, 3600),
            j(5, "The Legend of Zelda: Tears of the Kingdom", 2023, 1400))
        assertEquals(listOf("4", "5"), Igdb.ordenar(zelda, "Zelda", anio = 2026).take(2).map { it["id"].toString() })
        // El nombre igual a lo buscado va primero aunque otro tenga más votos; uno sin salir sube por los que lo esperan.
        val mina = listOf(j(1, "Mina: Edicion Java", 2011, 2100), j(2, "Mina", 2016, 600), j(3, "Mina Sin Votos", 2015, 0),
            j(4, "Mina 2", null, 0, hypes = 400))
        val m = Igdb.ordenar(mina, "mina", anio = 2026).map { it["id"].toString() }
        assertEquals("2", m.first())
        assertTrue(m.indexOf("4") < m.indexOf("3"))
    }

    /** Lo que vio el tester: Celeste pasado a IGDB en inglés seguía en inglés tras «Actualizar» en español. */
    @Test fun alActualizarLaSinopsisSigueElIdioma() {
        val es = "https://es.wikipedia.org/wiki/Celeste_(videojuego)"
        val enIngles = Obra("1", Tipos.JUEGO, "Celeste", fuente = "igdb:26226", sinopsis = "Help Madeline survive.")
        val deWikipedia = enIngles.copy(sinopsis = "Ayuda a Madeline.", sinopsisFuente = es)
        // En español llega la de Wikipedia con su crédito.
        assertEquals("Ayuda a Madeline." to es, Logica.sinopsisAlRenovar(enIngles, deWikipedia, "es"))
        // Y de vuelta en inglés, la de IGDB (sin crédito de Wikipedia).
        assertEquals("Help Madeline survive." to "", Logica.sinopsisAlRenovar(deWikipedia, enIngles, "en"))
        // En español, si Wikipedia no respondió esta vez, no se cambia la suya por la de IGDB.
        assertEquals("Ayuda a Madeline." to es, Logica.sinopsisAlRenovar(deWikipedia, enIngles, "es"))
        // Sin Wikipedia en español: la de IGDB.
        assertEquals("Help Madeline survive." to "", Logica.sinopsisAlRenovar(enIngles.copy(sinopsis = ""), enIngles, "es"))
        // Una película conserva la suya.
        val peli = Obra("2", Tipos.PELICULA, "X", fuente = "tmdb:movie:1", sinopsis = "La de antes")
        assertEquals("La de antes" to "", Logica.sinopsisAlRenovar(peli, peli.copy(sinopsis = "Otra"), "es"))
    }

    @Test fun galeriaDeCapturasYArtes() {
        val g = Igdb.galeriaDe(ficha)
        assertEquals(3, g.size)
        assertEquals("https://images.igdb.com/igdb/image/upload/t_screenshot_med/sc0001.jpg" to
            "https://images.igdb.com/igdb/image/upload/t_1080p/sc0001.jpg", g.first())
        assertEquals("", Igdb.imagen("../malo", "1080p"))
    }

    @Test fun enlacesOrdenadosSinRedesYConRespaldo() {
        val propios = Igdb.enlacesDe(ficha)
        // La wiki lleva su sitio, para no confundirla con Wikipedia.
        assertEquals(listOf(Enlace.OFICIAL, "Steam", "GOG", "Wikipedia", Enlace.WIKI + "cueva.wiki.example"), propios.map { it.nombre })
        assertEquals("http://cueva.example", propios.first().url)
        val x = WikidataJuegos.Extra("Q42", oficial = "https://otro.example", steam = "999",
            wikipedia = mapOf("es" to "https://es.wikipedia.org/wiki/Cueva", "en" to "https://en.wikipedia.org/wiki/Cueva"))
        val juntos = Igdb.juntarEnlaces(propios, x, "es")
        // Lo de IGDB manda; Wikipedia pasa a la de español; al final, Wikidata.
        assertEquals("http://cueva.example", juntos.first { it.nombre == Enlace.OFICIAL }.url)
        assertEquals("https://store.steampowered.com/app/123", juntos.first { it.nombre == "Steam" }.url)
        assertEquals("https://es.wikipedia.org/wiki/Cueva", juntos.first { it.nombre == "Wikipedia" }.url)
        assertEquals("https://www.wikidata.org/wiki/Q42", juntos.last().url)
        // Sin sitios en IGDB, todo de Wikidata.
        val soloWd = Igdb.juntarEnlaces(emptyList(), x, "en")
        assertEquals(listOf(Enlace.OFICIAL, "Steam", "Wikipedia", "Wikidata"), soloWd.map { it.nombre })
        assertEquals("https://store.steampowered.com/app/999/", soloWd[1].url)
        assertEquals("https://en.wikipedia.org/wiki/Cueva", soloWd[2].url)
        assertEquals("https://www.igdb.com/games/cueva-de-prueba", Igdb.pagina(ficha))
        assertEquals(Igdb.SITIO, Igdb.pagina(json("""{"id": 1, "url": "https://malo.example/x"}""")))
    }

    @Test fun nombresDeSitios() {
        assertEquals("Epic Games Store", Igdb.nombreDeSitio("https://store.epicgames.com/p/x", null))
        assertEquals("itch.io", Igdb.nombreDeSitio("https://alguien.itch.io/x", 15))
        assertNull(Igdb.nombreDeSitio("https://www.youtube.com/@x", 9))
        assertEquals(Enlace.WIKI + "fandom.com", Igdb.nombreDeSitio("https://celeste.fandom.com/wiki/Celeste", 2))
        assertEquals(Enlace.WIKI + "wiki.gg", Igdb.nombreDeSitio("https://hollowknight.wiki.gg/", null))
        assertNull(Igdb.nombreDeSitio("https://sitio.example", 99))
        assertNull(Igdb.nombreDeSitio("no es una dirección", 1))
        // Un dominio que solo termina parecido no cuenta.
        assertNull(Igdb.nombreDeSitio("https://notgog.com.example/x", null))
    }

    @Test fun fuentesDeIgdb() {
        assertTrue(Igdb.esFuente("igdb:1942"))
        assertFalse(Igdb.esFuente("igdb:"))
        assertFalse(Igdb.esFuente("igdb:12a"))
        assertEquals(1942L, Igdb.idDe("igdb:1942"))
        assertNull(Igdb.idDe("wd:Q1"))
    }

    @Test fun imagenesDeIgdbCambianDeTamanoSoloLasPortadas() {
        val p = "https://images.igdb.com/igdb/image/upload/t_cover_big/co0abc.jpg"
        assertEquals("igdb", Imagenes.fuente(p))
        assertTrue(Imagenes.autorizada(p))
        assertEquals("https://images.igdb.com/igdb/image/upload/t_cover_big_2x/co0abc.jpg", Imagenes.url(p, Imagenes.Lugar.FICHA))
        assertEquals("https://images.igdb.com/igdb/image/upload/t_720p/co0abc.jpg", Imagenes.url(p, Imagenes.Lugar.VISOR))
        assertEquals("https://images.igdb.com/igdb/image/upload/t_cover_small/co0abc.jpg", Imagenes.url(p, Imagenes.Lugar.LISTA, ahorro = true))
        val fondo = "https://images.igdb.com/igdb/image/upload/t_screenshot_big/sc0001.jpg"
        assertEquals(fondo, Imagenes.url(fondo, Imagenes.Lugar.VISOR))
    }

    /** Wikidata: la ficha de IGDB de un juego (P5794 con el id numérico P9043 o solo el nombre corto) y lo que suma. */
    @Test fun igdbDesdeWikidata() {
        fun p5794(slug: String, numero: String?, rank: String = "normal") = """{"mainsnak":{"snaktype":"value","property":"P5794","datavalue":{"value":"$slug","type":"string"}},"type":"statement","rank":"$rank"""" +
            (numero?.let { ""","qualifiers":{"P9043":[{"snaktype":"value","property":"P9043","datavalue":{"value":"$it","type":"string"}}]}""" } ?: "") + "}"
        assertEquals("id:14", WikidataJuegos.igdbDe(json("""{"P5794":[${p5794("cueva", "14")}]}""")))
        assertEquals("slug:cueva", WikidataJuegos.igdbDe(json("""{"P5794":[${p5794("cueva", null)}]}""")))
        assertEquals("slug:nueva", WikidataJuegos.igdbDe(json("""{"P5794":[${p5794("vieja", "1", "deprecated")},${p5794("nueva", null)}]}""")))
        assertEquals("", WikidataJuegos.igdbDe(json("""{"P5794":[${p5794("Con Espacios", null)}]}""")))
        assertEquals("", WikidataJuegos.igdbDe(json("""{}""")))

        fun texto(p: String, v: String) = """{"mainsnak":{"snaktype":"value","property":"$p","datavalue":{"value":"$v","type":"string"}},"type":"statement","rank":"normal"}"""
        val x = WikidataJuegos.extraDe(json("""{"Q42":{"id":"Q42","claims":{"P856":[${texto("P856", "https://cueva.example")}],"P1733":[${texto("P1733", "123")}]},
            "sitelinks":{"eswiki":{"site":"eswiki","title":"Cueva","url":"https://es.wikipedia.org/wiki/Cueva"}}}}"""), "Q42")
        assertEquals("https://cueva.example", x.oficial)
        assertEquals("123", x.steam)
        assertEquals(mapOf("es" to "https://es.wikipedia.org/wiki/Cueva"), x.wikipedia)
        assertEquals(WikidataJuegos.Extra("Q7"), WikidataJuegos.extraDe(json("{}"), "Q7"))
    }

    /** Un juego de Wikidata que pasa a IGDB y otro igual ya agregado de IGDB quedan en uno. */
    @Test fun sinRepetidosAlPasarAIgdb() {
        val a = Obra("o1", Tipos.JUEGO, "Cueva", fuente = "igdb:900001", nota = 8, registros = listOf(Registro("r1", "2026-01-01")))
        val b = Obra("o2", Tipos.JUEGO, "Cueva", fuente = "igdb:900001", meGusta = true)
        val e = Logica.sinRepetidas(Estado(obras = listOf(a, b)))
        assertEquals(1, e.vivas.size)
        assertEquals("o1", e.vivas.single().id)
        assertTrue(e.vivas.single().meGusta)
    }

    /** Al juntar dos copias del mismo juego no se pierde nada de la persona, y todo lo que apuntaba a la borrada pasa a la que queda. */
    @Test fun juntarNoPierdeLoPersonal() {
        val a = Obra("o1", Tipos.JUEGO, "Cueva", fuente = "igdb:900001", nota = 8, estado = Estados.EN_CURSO, notas = "Jefe final difícil",
            etiquetas = listOf("plataformas"), empezada = "2026-02-01", registros = listOf(Registro("r1", "2026-01-01")), cambio = 10, agregada = 5)
        val b = Obra("o2", Tipos.JUEGO, "Cueva", fuente = "igdb:900001", meGusta = true, estado = Estados.TERMINADO, notas = "Lo terminé con amigos",
            etiquetas = listOf("cooperativo", "plataformas"), portada = "file://mia.jpg", portadaPropia = true, fondo = "file://fondo.jpg", fondoPropio = true,
            empezada = "2026-01-15", terminada = "2026-03-01", vistos = listOf("1x1"), resenaVisibilidad = Visibilidad.PUBLICA, cambio = 20, agregada = 3)
        val borrador = Resena("n:1", "o2", texto = "Borrador")
        val publicacion = PublicacionPropia("p1", 1, obra = "o2")
        val e = Logica.sinRepetidas(Estado(obras = listOf(a, b), borradoresResena = listOf(borrador), publicaciones = listOf(publicacion),
            perfil = Perfil(destacados = mapOf("juego" to "o2"))))
        val q = e.vivas.single()
        assertEquals("o1", q.id)
        assertEquals(Estados.TERMINADO, q.estado)
        assertEquals(8, q.nota)
        assertTrue(q.meGusta)
        assertEquals("Jefe final difícil\n\nLo terminé con amigos", q.notas)
        assertEquals(listOf("plataformas", "cooperativo"), q.etiquetas)
        assertEquals("file://mia.jpg", q.portada)
        assertTrue(q.portadaPropia)
        assertEquals("file://fondo.jpg", q.fondo)
        assertTrue(q.fondoPropio)
        assertEquals("2026-01-15", q.empezada)
        assertEquals("2026-03-01", q.terminada)
        assertEquals(listOf("1x1"), q.vistos)
        assertEquals(Visibilidad.PUBLICA, q.resenaVisibilidad)
        assertEquals(1, q.registros.size)
        assertEquals("o1", e.borradoresResena.single().obra)
        assertEquals("o1", e.publicaciones.single().obra)
        assertEquals("o1", e.perfil.destacados["juego"])
    }

    /** Dos juegos de Wikidata distintos con la misma ficha de IGDB (un error de Wikidata) no se juntan en silencio. */
    @Test fun noSeFundenJuegosDistintosPorUnErrorDeWikidata() {
        val ya = Obra("o1", Tipos.JUEGO, "Cueva de Prueba", fuente = "igdb:900001")
        val otro = Obra("o2", Tipos.JUEGO, "Montaña Lejana", fuente = "wd:Q2")
        val mismo = Obra("o3", Tipos.JUEGO, "Cueva de prueba", fuente = "wd:Q3")
        val e = Estado(obras = listOf(ya, otro, mismo))
        assertFalse(Logica.puedePasarAIgdb(e, otro, "igdb:900001", listOf("Cueva de Prueba")))
        assertTrue(Logica.puedePasarAIgdb(e, mismo, "igdb:900001", listOf("Cueva de Prueba")))
        // Con el título de IGDB, aunque la otra copia tenga otro nombre.
        assertTrue(Logica.puedePasarAIgdb(e, otro, "igdb:900001", listOf("Montaña Lejana")))
        // Si nadie tiene esa ficha, pasa la primera.
        assertTrue(Logica.puedePasarAIgdb(Estado(obras = listOf(otro, mismo)), otro, "igdb:900001", listOf("Cueva de Prueba")))
    }

    /** El resumen de Wikipedia: el título es un solo tramo de la dirección, aunque lleve «/». */
    @Test fun resumenConBarraEnElTitulo() {
        assertEquals("https://en.wikipedia.org/api/rest_v1/page/summary/Fate%2Fstay_night", Wikimedia.urlResumen("en", "Fate/stay_night").toString())
        assertEquals("https://es.wikipedia.org/api/rest_v1/page/summary/Pok%C3%A9mon_Rojo", Wikimedia.urlResumen("es", "Pok%C3%A9mon_Rojo").toString())
        assertEquals("https://en.wikipedia.org/api/rest_v1/page/summary/Fate%2Fzero", Wikimedia.urlResumen("en", "Fate%2Fzero").toString())
    }
}

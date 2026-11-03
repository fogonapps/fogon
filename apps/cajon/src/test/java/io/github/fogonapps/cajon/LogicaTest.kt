package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Gusto
import io.github.fogonapps.cajon.datos.Recomendar
import io.github.fogonapps.cajon.datos.Senales
import io.github.fogonapps.cajon.datos.Estados
import io.github.fogonapps.cajon.datos.Importar
import io.github.fogonapps.cajon.datos.JsonCajon
import io.github.fogonapps.cajon.datos.Logica
import io.github.fogonapps.cajon.datos.Obra
import io.github.fogonapps.cajon.datos.Orden
import io.github.fogonapps.cajon.datos.Registro
import io.github.fogonapps.cajon.datos.Temporada
import io.github.fogonapps.cajon.datos.Tipos
import io.github.fogonapps.cajon.datos.estrellas
import io.github.fogonapps.cajon.datos.leerCsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LogicaTest {
    private val hoy = LocalDate.of(2026, 10, 25)
    private var n = 0
    private val id: (String) -> String = { p -> p + (n++) }

    private fun peli(id: String, titulo: String, cambio: Long = 1, nota: Int = 0, estado: String = Estados.TERMINADO, fecha: String? = "2026-05-01") =
        Obra(id, Tipos.PELICULA, titulo, anio = 2000, minutos = 100, nota = nota, estado = estado, cambio = cambio,
            registros = listOfNotNull(fecha?.let { Registro("r$id", it, nota) }), terminada = fecha)

    @Test fun unirGanaLoMasReciente() {
        val a = Estado(listo = true, obras = listOf(peli("1", "Viejo", cambio = 10), peli("2", "Solo aquí", cambio = 5)))
        val b = Estado(obras = listOf(peli("1", "Nuevo", cambio = 20), peli("3", "Solo allá", cambio = 7)))
        val u = Logica.unir(a, b)
        assertEquals(setOf("1", "2", "3"), u.obras.map { it.id }.toSet())
        assertEquals("Nuevo", u.obras.first { it.id == "1" }.titulo)
        assertTrue(u.listo)
        // Lo mismo en el otro orden.
        assertEquals("Nuevo", Logica.unir(b, a).obras.first { it.id == "1" }.titulo)
    }

    @Test fun borrarSeSincroniza() {
        val aqui = Estado(obras = listOf(peli("1", "X", cambio = 10)))
        val borradaAlla = Estado(obras = listOf(peli("1", "X", cambio = 30).copy(borrada = true)))
        val u = Logica.unir(aqui, borradaAlla)
        assertTrue(u.obras.single().borrada)
        assertTrue(u.vivas.isEmpty())
        // Si aquí se tocó después de borrarla allá, vuelve.
        assertFalse(Logica.unir(Estado(obras = listOf(peli("1", "X", cambio = 40))), borradaAlla).obras.single().borrada)
    }

    @Test fun elJsonDaLaVuelta() {
        val e = Estado(listo = true, obras = listOf(peli("1", "Ñandú", nota = 7)))
        val texto = JsonCajon.encodeToString(Estado.serializer(), e)
        assertEquals(e, JsonCajon.decodeFromString(Estado.serializer(), texto))
    }

    @Test fun estrellasEnMedias() {
        assertEquals("★★★½", estrellas(7))
        assertEquals("★★★★★", estrellas(10))
        assertEquals("½", estrellas(1))
        assertEquals("", estrellas(0))
    }

    @Test fun filtrarYOrdenar() {
        val e = Estado(obras = listOf(peli("1", "Él", nota = 4), peli("2", "Amélie", nota = 9), peli("3", "Pendiente", estado = Estados.QUIERO, fecha = null),
            Obra("4", Tipos.LIBRO, "Cien años", creador = "García Márquez")))
        assertEquals(listOf("2", "1"), Logica.filtrar(e, Tipos.PELICULA, Estados.TERMINADO, "", Orden.NOTA).map { it.id })
        assertEquals(listOf("4"), Logica.filtrar(e, null, null, "garcia", Orden.TITULO).map { it.id })
        assertEquals(listOf("2"), Logica.filtrar(e, null, null, "amelie", Orden.TITULO).map { it.id })
    }

    @Test fun progresoSoloDeLibros() {
        assertEquals(0.5f, Logica.progreso(Obra("l", Tipos.LIBRO, "Libro", paginas = 200, pagina = 100)))
        // Las series ya no llevan episodios marcados: no tienen barra.
        assertNull(Logica.progreso(Obra("s", Tipos.SERIE, "Serie", temporadas = listOf(Temporada(1, 2)))))
        val terminada = Obra("s", Tipos.SERIE, "Serie", minutos = 30, estado = Estados.TERMINADO, terminada = "2026-02-01", temporadas = listOf(Temporada(1, 10), Temporada(2, 8)))
        assertEquals(540, Logica.cifras(Estado(obras = listOf(terminada)), 2026).minutosSeries)
    }

    @Test fun cifrasDelAnio() {
        val e = Estado(obras = listOf(
            peli("1", "A", nota = 8, fecha = "2026-01-10"), peli("2", "B", nota = 6, fecha = "2026-01-20"), peli("3", "C", nota = 10, fecha = "2025-12-31"),
            Obra("4", Tipos.LIBRO, "L", paginas = 300, estado = Estados.TERMINADO, terminada = "2026-03-02", generos = listOf("Novela")),
        ))
        val c = Logica.cifras(e, 2026)
        assertEquals(2, c.porTipo[Tipos.PELICULA])
        assertEquals(1, c.porTipo[Tipos.LIBRO])
        assertEquals(200, c.minutosCine)
        assertEquals(300, c.paginas)
        assertEquals(2, c.porMes[0])
        assertEquals(1, c.notas[8]); assertEquals(1, c.notas[6])
        assertEquals(3.5, c.notaMedia!!, 0.001)
        assertEquals(4, Logica.cifras(e, null).porTipo.values.sum())
    }

    @Test fun csvConComillas() {
        val f = leerCsv("Name,Year,Review\n\"Amores, perros\",2000,\"Dijo \"\"wow\"\"\nsegunda línea\"\n")
        assertEquals(1, f.size)
        assertEquals("Amores, perros", f[0]["Name"])
        assertEquals("Dijo \"wow\"\nsegunda línea", f[0]["Review"])
    }

    @Test fun importaLetterboxd() {
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use { z ->
            fun poner(n: String, t: String) { z.putNextEntry(ZipEntry(n)); z.write(t.encodeToByteArray()); z.closeEntry() }
            poner("diary.csv", "Date,Name,Year,Letterboxd URI,Rating,Rewatch,Tags,Watched Date\n2026-02-03,Paprika,2006,https://boxd.it/x,4.5,,,2026-02-02\n")
            poner("ratings.csv", "Date,Name,Year,Letterboxd URI,Rating\n2026-02-03,Paprika,2006,https://boxd.it/x,4.5\n")
            poner("watchlist.csv", "Date,Name,Year,Letterboxd URI\n2026-01-01,Perfect Blue,1997,https://boxd.it/y\n")
            poner("likes/films.csv", "Date,Name,Year,Letterboxd URI\n2026-02-03,Paprika,2006,https://boxd.it/x\n")
        }
        val r = Importar.letterboxd(zip.toByteArray(), "export.zip", id, 1)
        assertNotNull(r)
        val paprika = r!!.obras.first { it.titulo == "Paprika" }
        assertEquals(9, paprika.nota)
        assertTrue(paprika.meGusta)
        assertEquals(Estados.TERMINADO, paprika.estado)
        assertEquals("2026-02-02", paprika.registros.single().fecha)
        assertEquals(Estados.QUIERO, r.obras.first { it.titulo == "Perfect Blue" }.estado)
        // Un CSV que no es de Letterboxd no se toma.
        assertNull(Importar.letterboxd("a,b\n1,2\n".encodeToByteArray(), "x.csv", id, 1))
    }

    @Test fun importaGoodreadsYNoRepite() {
        val csv = "Book Id,Title,Author,Additional Authors,My Rating,Number of Pages,Original Publication Year,Date Read,Bookshelves,Exclusive Shelf,My Review\n" +
            "1,Cien años de soledad,Gabriel García Márquez,,5,432,1967,2025/08/14,favoritos,read,Una joya\n" +
            "2,Rayuela,Julio Cortázar,,0,600,1963,,,to-read,\n"
        val r = Importar.goodreads(csv, id, 1)!!
        val cien = r.obras.first { it.titulo.startsWith("Cien") }
        assertEquals(10, cien.nota); assertEquals(432, cien.paginas); assertEquals("2025-08-14", cien.terminada)
        assertEquals(listOf("favoritos"), cien.etiquetas)
        assertEquals(Estados.QUIERO, r.obras.first { it.titulo == "Rayuela" }.estado)
        // Sumar dos veces lo mismo no lo duplica.
        val (e1, nuevas1) = Importar.sumar(Estado(), r.obras, 1)
        val (e2, nuevas2) = Importar.sumar(e1, Importar.goodreads(csv, id, 2)!!.obras, 2)
        assertEquals(2, nuevas1); assertEquals(0, nuevas2); assertEquals(2, e2.obras.size)
    }

    @Test fun sinopsisSinMarkdown() {
        assertEquals("Cien años es una novela de García Márquez (ver Wikipedia).",
            io.github.fogonapps.cajon.datos.OpenLibrary.sinFormato("*Cien años* es una **novela** de García Márquez (ver [Wikipedia](https://es.wikipedia.org)).").replace("ver Wikipedia", "ver Wikipedia"))
    }

    @Test fun rachaDelDiario() {
        val e = Estado(obras = listOf(peli("1", "A", fecha = "2026-10-25"), peli("2", "B", fecha = "2026-10-24"), peli("3", "C", fecha = "2026-10-22")))
        assertEquals(2, Logica.racha(e, hoy))
    }

    private fun libro(clave: String, titulo: String, autor: String, ediciones: Int, portada: Int?, idioma: String? = "spa", tituloEd: String = titulo) =
        kotlinx.serialization.json.Json.parseToJsonElement("""{"key":"/works/$clave","title":"$titulo","author_name":["$autor"],"edition_count":$ediciones,""" +
            (if (portada != null) "\"cover_i\":$portada," else "") +
            """"editions":{"docs":[{"title":"$tituloEd"${if (idioma != null) ",\"language\":[\"$idioma\"]" else ""}}]}}""").let { it as kotlinx.serialization.json.JsonObject }

    @Test fun librosSinRepetidosNiEstudios() {
        val r = io.github.fogonapps.cajon.datos.OpenLibrary.ordenar(listOf(
            libro("A", "Cien años de soledad", "Gabriel García Márquez", 208, 1),
            libro("B", "Cien años de soledad", "Josefina Ludmer", 1, 2),               // un estudio: fuera
            libro("C", "Cien Años De Soledad", "Gabriel García Márquez", 1, null),     // la misma obra repetida: fuera
            libro("D", "Sto godina samoće", "Gabriel García Márquez", 3, 4, "hrv"),    // solo en otro idioma: fuera
            libro("E", "El amor en los tiempos del cólera", "Gabriel García Márquez", 130, 5),
        ), "es")
        assertEquals(listOf("ol:A", "ol:E"), r.map { it.fuente })
        assertEquals("https://covers.openlibrary.org/b/id/1-M.jpg", r[0].portada)   // en la lista, la portada de la obra
    }

    @Test fun laTraduccionBuscadaSaleConSuTitulo() {
        val r = io.github.fogonapps.cajon.datos.OpenLibrary.ordenar(listOf(
            libro("A", "L’étranger", "Albert Camus", 468, 1, "spa", "El extranjero")), "es", consulta = "el extranjero")
        assertEquals("El extranjero", r[0].titulo)
    }

    @Test fun edicionMalMarcadaNoCambiaElTitulo() {
        val r = io.github.fogonapps.cajon.datos.OpenLibrary.ordenar(listOf(
            libro("A", "Cien años de soledad", "Gabriel García Márquez", 208, 1, "spa", "One hundred years of solitude")), "es")
        assertEquals("Cien años de soledad", r[0].titulo)
    }

    @Test fun portadasSoloSiEsLaMisma() {
        fun enc(tipo: String, t: String, anio: Int?, creador: String = "", detalle: String = "") =
            io.github.fogonapps.cajon.datos.Encontrado(tipo, t, anio, creador, "", "x", detalle)
        val disco = Obra("1", Tipos.ALBUM, "Sé Morir", anio = 1999, creador = "Andrés Cepeda")
        assertEquals(true, Logica.esLaMisma(disco, enc(Tipos.ALBUM, "Se morir", 1999, "Andres Cepeda")))
        assertEquals(false, Logica.esLaMisma(disco, enc(Tipos.ALBUM, "Sé Morir", 1999, "Otro Artista")))
        assertEquals(false, Logica.esLaMisma(disco, enc(Tipos.ALBUM, "Sé Morir", 2010, "Andrés Cepeda")))
        val peli = Obra("2", Tipos.PELICULA, "The Basketball Diaries", anio = 1995)
        // Por el título original, aunque TMDB lo traiga traducido.
        assertEquals(true, Logica.esLaMisma(peli, enc(Tipos.PELICULA, "Diario de un rebelde", 1995, detalle = "The Basketball Diaries")))
        assertEquals(false, Logica.esLaMisma(peli, enc(Tipos.PELICULA, "Basketball", 1995)))
    }

    @Test fun titulosLatinosYDeEspana() {
        fun enc(t: String, anio: Int?, original: String = "") = io.github.fogonapps.cajon.datos.Encontrado(Tipos.PELICULA, t, anio, "", "", "x", original)
        // Escrita con el título latino; TMDB en España la llama distinto pero el original empieza igual.
        val coraline = Obra("1", Tipos.PELICULA, "Coraline y la puerta secreta", anio = 2009)
        assertEquals(true, Logica.esLaMisma(coraline, enc("Coraline y la puerta secreta", 2009, "Coraline")))
        assertEquals(true, Logica.esLaMisma(coraline, enc("Los mundos de Coraline", 2009, "Coraline")))
        val ponyo = Obra("2", Tipos.ANIME, "Ponyo", anio = 2008)
        assertEquals(true, Logica.esLaMisma(ponyo, io.github.fogonapps.cajon.datos.Encontrado(Tipos.ANIME, "Ponyo y el secreto de la sirenita", 2008, "", "", "x", "崖の上のポニョ")))
        // Otro año, u otro título que solo comparte el principio corto: no.
        assertEquals(false, Logica.esLaMisma(coraline, enc("Coraline", 2015)))
        assertEquals(false, Logica.esLaMisma(Obra("3", Tipos.PELICULA, "It", anio = 2017), enc("It Follows", 2017)))
    }

    @Test fun partesDelTituloYAutoresEnOtroAlfabeto() {
        assertEquals(listOf("La La Land: ciudad de sueños", "La La Land", "ciudad de sueños"), Logica.fragmentos("La La Land: ciudad de sueños"))
        assertEquals(listOf("Ponyo"), Logica.fragmentos("Ponyo"))
        fun peli(t: String, anio: Int, original: String = "") = io.github.fogonapps.cajon.datos.Encontrado(Tipos.PELICULA, t, anio, "", "", "x", original)
        // Con el subtítulo de las notas: por el título sin subtítulo y el mismo año.
        assertEquals(true, Logica.esLaMisma(Obra("1", Tipos.PELICULA, "La La Land: ciudad de sueños", anio = 2016), peli("La La Land: Una historia de amor", 2016, "La La Land")))
        assertEquals(true, Logica.esLaMisma(Obra("2", Tipos.PELICULA, "Jobs: el hombre que revolucionó al mundo", anio = 2013), peli("JOBS: La Vida De Steve Jobs", 2013, "Jobs")))
        assertEquals(false, Logica.esLaMisma(Obra("2", Tipos.PELICULA, "Jobs: el hombre que revolucionó al mundo", anio = 2013), peli("Steve Jobs", 2015)))
        fun libro(t: String, autor: String) = io.github.fogonapps.cajon.datos.Encontrado(Tipos.LIBRO, t, 2020, autor, "", "x")
        // Mismo autor y casi el mismo título (una parte).
        val kant = Obra("3", Tipos.LIBRO, "Respuesta a la pregunta: ¿Qué es Ilustración?", anio = 1784, creador = "Immanuel Kant.")
        assertEquals(true, Logica.esLaMisma(kant, libro("¿Qué es la Ilustración?", "Immanuel Kant")))
        assertEquals(false, Logica.esLaMisma(kant, libro("Crítica de la razón pura", "Immanuel Kant")))
        // Open Library guarda a Platón en griego: no se descarta por el autor.
        assertEquals(true, Logica.esLaMisma(Obra("4", Tipos.LIBRO, "Dialogos", creador = "Platon"), libro("Diálogos", "Πλάτων")))
        assertEquals(false, Logica.esLaMisma(Obra("4", Tipos.LIBRO, "Dialogos", creador = "Platon"), libro("Diálogos", "Otro Autor")))
    }

    @Test fun librosConElMismoTituloPeroOtroAutor() {
        fun enc(t: String, creador: String) = io.github.fogonapps.cajon.datos.Encontrado(Tipos.LIBRO, t, null, creador, "", "x")
        val libro = Obra("1", Tipos.LIBRO, "El extranjero", creador = "Albert Camus")
        assertTrue(Logica.esLaMisma(libro, enc("El Extranjero", "Albert Camus")))
        assertFalse(Logica.esLaMisma(libro, enc("El extranjero", "Otra Persona")))
        // Sin autor en el cajón, basta el título.
        assertTrue(Logica.esLaMisma(libro.copy(creador = ""), enc("El extranjero", "Albert Camus")))
        // Del mismo autor: un título que empieza por el otro, aunque el año sea el de una edición.
        assertTrue(Logica.esLaMisma(Obra("2", Tipos.LIBRO, "Frankenstein o el moderno Prometeo", anio = 1818, creador = "Mary Shelley"),
            io.github.fogonapps.cajon.datos.Encontrado(Tipos.LIBRO, "Frankenstein", 1994, "Mary Wollstonecraft Shelley", "", "x")))
        assertFalse(Logica.esLaMisma(libro, enc("El", "Albert Camus")))
    }

    @Test fun etiquetasIgualesParaTodo() {
        val a = Obra("1", Tipos.PELICULA, "A", generos = listOf("Drama"), etiquetas = listOf("Con amigos"))
        val b = Obra("2", Tipos.LIBRO, "B", generos = listOf("drama"))
        val e = Estado(obras = listOf(a, b))
        // Se juntan sin importar mayúsculas ni tildes, la más usada primero.
        assertEquals(listOf("Drama", "Con amigos"), Logica.etiquetasDelCajon(e))
        assertEquals(listOf("drama"), Logica.etiquetasDelCajon(e, listOf(Tipos.LIBRO)))
        assertTrue(Logica.tieneEtiqueta(a, "con AMIGOS"))
        assertTrue(Logica.tieneEtiqueta(b, "Drama"))
        assertFalse(Logica.tieneEtiqueta(b, "Con amigos"))
    }

    @Test fun losNuevosCamposViajanEnElRespaldo() {
        val o = Obra("1", Tipos.SERIE, "S", episodiosFavoritos = listOf("1x5|Piloto"), portadaPropia = true)
        val e = Estado(obras = listOf(o))
        assertEquals(e, JsonCajon.decodeFromString(Estado.serializer(), JsonCajon.encodeToString(Estado.serializer(), e)))
    }

    @Test fun recientesPorFechaDeAgregada() {
        val vieja = Obra("1", Tipos.PELICULA, "Vieja", agregada = 1000, cambio = 9000)   // se tocó hace poco, pero entró antes
        val nueva = Obra("2", Tipos.PELICULA, "Nueva", agregada = 5000, cambio = 5000)
        assertEquals(listOf("2", "1"), Logica.filtrar(Estado(obras = listOf(vieja, nueva)), null, null, "", Orden.RECIENTES).map { it.id })
    }

    @Test fun generosParaBuscar() {
        assertEquals("Boom Bap", Logica.genero("boom bap", listOf("Boom Bap", "Pop")))
        assertEquals("Rock Alternativo", Logica.genero("rock alternativo", emptyList()))   // de los comunes
        assertEquals(null, Logica.genero("mac miller", listOf("Boom Bap")))
        assertEquals("alternative rock", Logica.etiquetaMusicBrainz("Rock Alternativo"))
        assertEquals("musica rara", Logica.etiquetaMusicBrainz("Música Rara"))   // los que no conoce van tal cual, limpios
    }

    @Test fun artistasConOtroNombre() {
        fun mb(n: String, vararg alias: String) = io.github.fogonapps.cajon.datos.ArtistaEncontrado("x", n, "", null, "", "", alias = alias.toList())
        assertEquals(true, Logica.mismoArtista("Los Enanitos Verdes", mb("Enanitos Verdes")))
        assertEquals(true, Logica.mismoArtista("Lisa Ono", mb("小野リサ", "Lisa Ono")))
        assertEquals(true, Logica.mismoArtista("Andrés Cepeda", mb("Andres Cepeda")))
        assertEquals(false, Logica.mismoArtista("La Mosca", mb("Mosca Muerta")))
    }

    @Test fun cineConTitulosAlternativosYSinAnio() {
        fun enc(tipo: String, t: String, anio: Int?, original: String = "", alias: List<String> = emptyList(), portada: String = "p") =
            io.github.fogonapps.cajon.datos.Encontrado(tipo, t, anio, "", portada, "x", original, alias = alias)
        assertEquals(listOf("Road House (2024)", "Road House"), Logica.fragmentos("Road House (2024)"))
        assertEquals(listOf("Saga of Tanya the Evil (Youjo Senki)", "Saga of Tanya the Evil", "Youjo Senki"), Logica.fragmentos("Saga of Tanya the Evil (Youjo Senki)"))
        // Las entradas sin año de TMDB no se toman si la obra tiene año.
        val kite = Obra("1", Tipos.ANIME, "Kite", anio = 1998)
        assertEquals(false, Logica.esLaMisma(kite, enc(Tipos.PELICULA, "Kite", null)))
        assertEquals(true, Logica.esLaMisma(kite, enc(Tipos.ANIME, "Kite", 1998)))
        // Romaji: por sus títulos alternativos.
        val seraph = Obra("2", Tipos.ANIME, "Owari no Seraph", anio = 2015)
        assertEquals(true, Logica.esLaMisma(seraph, enc(Tipos.ANIME, "Serafín del Fín", 2015, "終わりのセラフ", listOf("Owari no Seraph", "Seraph of the End"))))
        // Un alternativo que solo es una parte del título no basta ("The Seven Deadly Sins" de otra película).
        val sins = Obra("3", Tipos.ANIME, "The Seven Deadly Sins: La ira imperial de los dioses", anio = 2019)
        assertEquals(false, Logica.esLaMisma(sins, enc(Tipos.PELICULA, "Sinful", 2019, alias = listOf("The Seven Deadly Sins"))))
        // Gana el del mismo año y tipo (el anime de 2021 por su alternativo, no la película de 2022 con el título igual).
        val bp = Obra("4", Tipos.ANIME, "Blue Period", anio = 2021)
        val anime = enc(Tipos.ANIME, "Periodo Azul", 2021, "ブルーピリオド", listOf("Blue Period"))
        val peli = enc(Tipos.PELICULA, "Blue Period", 2022)
        assertEquals(true, Logica.puntaje(bp, anime) > Logica.puntaje(bp, peli))
    }

    @Test fun escuchasPorAlbum() {
        val clave = io.github.fogonapps.cajon.datos.RegistroEscuchas.Companion::clave
        // La misma edición con otro nombre (deluxe, remasterizado) y el artista con invitados: el mismo álbum.
        assertEquals(clave("Soda Stereo", "Canción animal"), clave("Soda Stereo", "Canción Animal (Remastered 2007)"))
        assertEquals(clave("Bad Bunny", "Un Verano Sin Ti"), clave("Bad Bunny, Chencho Corleone", "Un Verano Sin Ti (Deluxe Edition)"))
        assertEquals(false, clave("Soda Stereo", "Signos") == clave("Soda Stereo", "Sueño Stereo"))
    }

    @Test fun historialDeSpotify() {
        val ampliado = """[{"ts":"2026-10-01T20:00:00Z","ms_played":200000,"master_metadata_track_name":"Signos","master_metadata_album_artist_name":"Soda Stereo","master_metadata_album_album_name":"Signos"},
            {"ts":"2026-10-01T20:05:00Z","ms_played":5000,"master_metadata_track_name":"Prófugos","master_metadata_album_artist_name":"Soda Stereo","master_metadata_album_album_name":"Signos"},
            {"ts":"2026-10-01T21:00:00Z","ms_played":900000,"master_metadata_track_name":null,"master_metadata_album_artist_name":null,"master_metadata_album_album_name":null,"episode_name":"Un podcast"}]"""
        val h = io.github.fogonapps.cajon.datos.Importar.spotify(ampliado.toByteArray(), "Streaming_History_Audio_2026.json")!!
        assertEquals(1, h.escuchas.size)   // la de 5 segundos y el podcast no cuentan
        assertEquals("Signos", h.escuchas[0].album)
        val basico = """[{"endTime":"2026-10-01 20:00","artistName":"Soda Stereo","trackName":"Signos","msPlayed":200000}]"""
        assertEquals(true, io.github.fogonapps.cajon.datos.Importar.spotify(basico.toByteArray(), "StreamingHistory_music_0.json")!!.sinAlbum)
        assertEquals(null, io.github.fogonapps.cajon.datos.Importar.spotify("hola".toByteArray(), "notas.txt"))
    }

    @Test fun recomendarDesdeLosGustos() {
        val R = io.github.fogonapps.cajon.datos.Recomendar
        val buena = Obra("1", Tipos.PELICULA, "A", nota = 10, generos = listOf("Drama", "Ciencia ficción"), estado = Estados.TERMINADO)
        val mala = Obra("2", Tipos.PELICULA, "B", nota = 3, generos = listOf("Comedia"), estado = Estados.TERMINADO)
        val conCorazon = Obra("3", Tipos.SERIE, "C", meGusta = true, generos = listOf("Drama"), estado = Estados.TERMINADO)
        val pendDrama = Obra("4", Tipos.PELICULA, "D", generos = listOf("drama"), estado = Estados.QUIERO)
        val pendComedia = Obra("5", Tipos.PELICULA, "E", generos = listOf("Comedia"), estado = Estados.QUIERO)
        val e = Estado(obras = listOf(buena, mala, conCorazon, pendDrama, pendComedia))
        assertEquals(1.0, R.peso(buena), 1e-9)
        assertEquals(0.0, R.peso(mala), 1e-9)                      // lo que no le gustó no recomienda nada
        assertEquals(listOf("1", "3"), R.semillas(e, listOf(Tipos.PELICULA, Tipos.SERIE), 5).map { it.first.id })
        assertEquals(listOf("4"), R.pendientes(e).map { it.id })   // el pendiente de drama sí; el de comedia no se parece
        // Lo que recomiendan varias semillas sube; lo que ya está se quita.
        val j = io.github.fogonapps.cajon.datos.Recomendar.Junta<String> { it }
        j.sumar(listOf("x", "y", "z"), 1.0, "A"); j.sumar(listOf("y", "w"), 1.0, "C")
        val mejores = j.mejores(3) { it == "w" }
        assertEquals("y", mejores.first().first)
        assertEquals(false, mejores.any { it.first == "w" })
    }

    @Test fun librosQueYaTieneEnOtroIdioma() {
        val R = io.github.fogonapps.cajon.datos.Recomendar
        assertEquals(true, R.yaLoTiene("Metamorphosis", listOf("La Metamorfosis", "El proceso")))
        assertEquals(true, R.yaLoTiene("Crime and Punishment", listOf("Crimen y castigo")))
        assertEquals(false, R.yaLoTiene("Das Schloß", listOf("La Metamorfosis", "El proceso")))
    }

    @Test fun perfilDeGustosYPreferenciasNegativas() {
        val ahora = 400L * 86_400_000
        val vieja = Obra("1", Tipos.PELICULA, "A", nota = 10, generos = listOf("Drama"), estado = Estados.TERMINADO, cambio = 0)
        val nueva = Obra("2", Tipos.PELICULA, "B", nota = 10, generos = listOf("Terror"), estado = Estados.TERMINADO, agregada = ahora - 86_400_000)
        val abandonada = Obra("3", Tipos.PELICULA, "C", generos = listOf("Comedia"), estado = Estados.ABANDONADO, agregada = ahora)
        var e = Estado(obras = listOf(vieja, nueva, abandonada))
        var p = Recomendar.perfil(e, null, ahora)
        // Lo reciente pesa más y es "de ahora"; lo abandonado resta; lo aprendido no es "dicho".
        assertTrue(p.pesoGenero("terror") > p.pesoGenero("Drama"))
        assertTrue(p.generos.first { it.nombre == "Terror" }.reciente)
        assertTrue(p.pesoGenero("Comedia") < 0)
        assertFalse(p.generos.first { it.nombre == "Drama" }.declarado)
        // Lo dicho gana: "no es así" quita lo aprendido; excluir saca todo lo de ese género.
        e = e.copy(gustos = listOf(Gusto("genero:terror", "Terror", Senales.QUITADO), Gusto("genero:drama", "Drama", Senales.EXCLUIR), Gusto("genero:accion", "Acción", Senales.GUSTA)))
        p = Recomendar.perfil(e, null, ahora)
        assertEquals(0.0, p.pesoGenero("Terror"), 1e-9)
        assertTrue(p.excluido(listOf("drama"), ""))
        assertEquals(1.0, p.pesoGenero("Accion"), 1e-9)
        // Elegir: lo excluido y lo que no quiere recomendado no salen; lo de su género sube; el último lugar explora.
        val c = listOf(
            Recomendar.Candidato("x", "obra:x", 1.0, "A", listOf("Drama")),
            Recomendar.Candidato("y", "obra:y", 0.2, "A", listOf("Acción")),
            Recomendar.Candidato("z", "obra:z", 0.9, "A", listOf("Comedia")),
            Recomendar.Candidato("w", "obra:w", 0.5, "B", listOf("Western")),
            Recomendar.Candidato("v", "obra:v", 0.8, "B", listOf("Acción")),
        )
        e = e.copy(gustos = e.gustos + Gusto("obra:v", "V", Senales.NO_RECOMENDAR))
        p = Recomendar.perfil(e, null, ahora)
        val elegidos = Recomendar.elegir(c, p, emptyMap(), 2, ahora)
        assertEquals(listOf("y", "w"), elegidos.map { it.first })
        assertEquals(Recomendar.Por.EXPLORAR, elegidos.last().second.tipo)
        // Lo que salió 6 veces sin abrirlo descansa; si lo abrió, no.
        val vistos = mapOf("obra:y" to Recomendar.Visto(6, ahora - 1000, false))
        assertFalse(Recomendar.elegir(c, p, vistos, 3, ahora, explorar = false).any { it.first == "y" })
        assertTrue(Recomendar.elegir(c, p, mapOf("obra:y" to Recomendar.Visto(6, ahora - 1000, true)), 3, ahora, explorar = false).any { it.first == "y" })
        // Sincronizar: gana lo más reciente de cada gusto.
        val a = Estado(gustos = listOf(Gusto("genero:x", "X", Senales.GUSTA, cambio = 1)))
        val b = Estado(gustos = listOf(Gusto("genero:x", "X", Senales.NO_GUSTA, cambio = 2)))
        assertEquals(Senales.NO_GUSTA, Logica.unir(a, b).gustos.single().senal)
    }

    @Test fun perfilCultural() {
        assertEquals("angel_salazar", Logica.usuarioLimpio("@Ángel Salazar!"))
        assertEquals("https://letterboxd.com/angel", Logica.enlaceLimpio("letterboxd.com/angel"))
        assertNull(Logica.enlaceLimpio("no es un enlace"))
        assertNull(Logica.enlaceLimpio("hola"))
        assertEquals("letterboxd.com/angel", Logica.enlaceCorto("https://www.letterboxd.com/angel/"))
        val a = Obra("1", Tipos.PELICULA, "A", nota = 10, generos = listOf("Drama"), registros = listOf(Registro("r1", "2025-03-01", nota = 10)))
        val b = Obra("2", Tipos.PELICULA, "B", nota = 6, generos = listOf("Drama"), registros = listOf(Registro("r2", "2025-05-01")))
        val c = Obra("3", Tipos.LIBRO, "C", meGusta = true, generos = listOf("Novela"), registros = listOf(Registro("r3", "2026-10-20")))
        val e = Estado(obras = listOf(a, b, c))
        val ev = Logica.evolucion(e)
        assertEquals(listOf(2026, 2025), ev.map { it.anio })
        assertEquals("1", ev[1].mejores.first().id)
        assertEquals("Drama", ev[1].genero)
        assertEquals(listOf("3"), Logica.descubrimientos(e, LocalDate.of(2026, 11, 2)).map { it.id })
        // Sincronizar: gana el perfil cambiado más tarde.
        val viejo = Estado(perfil = io.github.fogonapps.cajon.datos.Perfil(bio = "vieja", cambio = 1))
        val nuevo = Estado(perfil = io.github.fogonapps.cajon.datos.Perfil(bio = "nueva", cambio = 2))
        assertEquals("nueva", Logica.unir(viejo, nuevo).perfil.bio)
        assertEquals("nueva", Logica.unir(nuevo, viejo).perfil.bio)
    }

    /** 1.16: el nombre con letras de Unicode pegadas y el encuadre de la foto y la portada. */
    @Test fun nombreConLetrasYEncuadre() {
        val gotico = "\uD835\uDD04\uD835\uDD2B\uD835\uDD24\uD835\uDD22\uD835\uDD29" // 𝔄𝔫𝔤𝔢𝔩: dos unidades por letra
        assertEquals(gotico, Logica.recortar(gotico, 40))
        assertEquals("\uD835\uDD04\uD835\uDD2B", Logica.recortar(gotico, 2)) // corta entre letras, nunca a la mitad de una
        assertEquals("Ang", Logica.recortar("Angel", 3))
        val raro = io.github.fogonapps.cajon.datos.Encuadre(zoom = 9f, x = -3f, y = Float.NaN).limpio()
        assertEquals(io.github.fogonapps.cajon.datos.Encuadre(4f, -1f, 0f), raro)
        // Un perfil de la 1.15 (con la letra elegida) se sigue leyendo; el encuadre viaja con la sincronización.
        val viejo = JsonCajon.decodeFromString(Estado.serializer(), """{"perfil":{"bio":"hola","letraNombre":"cartel"}}""")
        assertEquals("hola", viejo.perfil.bio)
        assertEquals(1f, viejo.perfil.encuadreFoto.zoom)
        val con = Estado(perfil = io.github.fogonapps.cajon.datos.Perfil(encuadrePortada = io.github.fogonapps.cajon.datos.Encuadre(2f, 0.5f, -0.25f)))
        val ida = JsonCajon.decodeFromString(Estado.serializer(), JsonCajon.encodeToString(Estado.serializer(), con))
        assertEquals(io.github.fogonapps.cajon.datos.Encuadre(2f, 0.5f, -0.25f), ida.perfil.encuadrePortada)
    }
}

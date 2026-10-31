package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.Estado
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
        assertEquals("", r[0].portada)   // los libros van sin portada
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
}

package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** La exportación de Trakt: un ZIP de JSON con el formato de su API (elementos inventados). */
class TraktTest {
    private var n = 0
    private val id = { p: String -> p + n++ }
    private val utc = java.time.ZoneOffset.UTC

    private val rainMan = """{"title":"Rain Man","year":1988,"ids":{"trakt":304,"slug":"rain-man-1988","imdb":"tt0095953","tmdb":380}}"""
    private val dune = """{"title":"Dune","year":2021,"ids":{"trakt":287071,"slug":"dune-2021","imdb":"tt1160419","tmdb":438631}}"""
    private val sinTmdb = """{"title":"Corto raro","year":2010,"ids":{"trakt":99,"slug":"corto-raro","imdb":null,"tmdb":null}}"""
    private val bb = """{"title":"Breaking Bad","year":2008,"ids":{"trakt":1388,"slug":"breaking-bad","tvdb":81189,"imdb":"tt0903747","tmdb":1396}}"""
    private val dark = """{"title":"Dark","year":2017,"ids":{"trakt":125131,"slug":"dark","tvdb":334824,"imdb":"tt5753856","tmdb":70523}}"""
    private fun ep(t: Int, c: Int) = """{"season":$t,"number":$c,"title":"Ep","ids":{"trakt":${t * 100 + c},"tvdb":1}}"""

    private val historial = """[
      {"id":3,"watched_at":"2024-05-02T23:30:00.000Z","action":"watch","type":"movie","movie":$rainMan},
      {"id":2,"watched_at":"2021-03-22T06:33:24.000Z","action":"scrobble","type":"movie","movie":$rainMan},
      {"id":1,"watched_at":"2023-01-10T20:00:00.000Z","action":"checkin","type":"episode","episode":${ep(1, 2)},"show":$bb},
      {"id":0,"watched_at":"2023-01-09T20:00:00.000Z","action":"watch","type":"episode","episode":${ep(1, 1)},"show":$bb},
      {"id":9,"watched_at":"2023-01-08T20:00:00.000Z","action":"watch","type":"episode","episode":${ep(0, 1)},"show":$bb}
    ]"""
    private val vistasPelis = """[{"plays":1,"last_watched_at":"2022-12-31T23:59:00.000Z","last_updated_at":"2023-01-01T00:00:00.000Z","movie":$sinTmdb}]"""
    private val vistasSeries = """[{"plays":3,"last_watched_at":"2023-01-12T20:00:00.000Z","last_updated_at":"2023-01-12T20:00:00.000Z","show":$bb,
      "seasons":[{"number":1,"episodes":[{"number":1,"plays":1,"last_watched_at":"2023-01-09T20:00:00.000Z"},{"number":3,"plays":1,"last_watched_at":"2023-01-12T20:00:00.000Z"}]},
                 {"number":2,"episodes":[{"number":10,"plays":1,"last_watched_at":"2023-02-01T20:00:00.000Z"}]}]}]"""
    private val notas = """[
      {"rated_at":"2024-05-03T00:00:00.000Z","rating":9,"type":"movie","movie":$rainMan},
      {"rated_at":"2023-03-01T00:00:00.000Z","rating":10,"type":"show","show":$bb},
      {"rated_at":"2023-03-01T00:00:00.000Z","rating":2,"type":"episode","episode":${ep(1, 1)},"show":$dark}
    ]"""
    private val pendientes = """[{"rank":1,"id":5,"listed_at":"2024-01-01T00:00:00.000Z","notes":null,"type":"movie","movie":$dune},
      {"rank":2,"id":6,"listed_at":"2024-01-01T00:00:00.000Z","notes":null,"type":"show","show":$dark}]"""
    private val listaPropia = """[{"rank":1,"id":7,"listed_at":"2024-01-01T00:00:00.000Z","type":"movie","movie":{"title":"Otra","year":2000,"ids":{"trakt":5,"tmdb":5}}}]"""

    private fun zip(vararg archivos: Pair<String, String>): ByteArray {
        val b = ByteArrayOutputStream()
        ZipOutputStream(b).use { z -> archivos.forEach { (n, t) -> z.putNextEntry(ZipEntry(n)); z.write(t.encodeToByteArray()); z.closeEntry() } }
        return b.toByteArray()
    }

    private val todo get() = zip(
        "trakt-backup/watched/history-1.json" to historial, "trakt-backup/watched/watched-movies.json" to vistasPelis,
        "trakt-backup/watched/watched-shows.json" to vistasSeries, "trakt-backup/ratings/ratings.json" to notas,
        "trakt-backup/lists/watchlist.json" to pendientes, "trakt-backup/lists/mis-favoritas.json" to listaPropia,
        "trakt-backup/lists/favorites-movies.json" to listaPropia, "trakt-backup/comments/comments.json" to "[]",
        "trakt-backup/user/settings.json" to """{"user":{"username":"x"}}""",
    )

    private fun importar() = Importar.trakt(todo, "trakt-backup-2026.zip", id, 7, utc)!!
    private fun obra(r: Importado, titulo: String) = r.obras.single { it.titulo == titulo }

    @Test fun peliculaConHistorialYNota() {
        val r = importar()
        assertEquals("Trakt", r.deDonde)
        val o = obra(r, "Rain Man")
        assertEquals(Tipos.PELICULA, o.tipo); assertEquals(1988, o.anio)
        assertEquals("tmdb:movie:380", o.fuente)
        assertEquals(Estados.TERMINADO, o.estado); assertEquals(9, o.nota)
        assertEquals(listOf("2021-03-22", "2024-05-02"), o.registros.map { it.fecha })
        assertEquals(listOf(false, true), o.registros.map { it.otraVez })
        assertEquals(listOf(0, 9), o.registros.map { it.nota })
        assertEquals("2024-05-02", o.terminada)
    }

    @Test fun peliculaVistaSinHistorialNiTmdb() {
        val o = obra(importar(), "Corto raro")
        assertEquals("", o.fuente)
        assertEquals(Estados.TERMINADO, o.estado)
        assertEquals(listOf("2022-12-31"), o.registros.map { it.fecha })
    }

    @Test fun serieConEpisodiosVistos() {
        val o = obra(importar(), "Breaking Bad")
        assertEquals(Tipos.SERIE, o.tipo); assertEquals("tmdb:tv:1396", o.fuente)
        assertEquals(Estados.EN_CURSO, o.estado); assertEquals(10, o.nota)
        // Del historial y de watched-shows, sin repetir, en orden y sin los especiales (temporada 0).
        assertEquals(listOf("1x1", "1x2", "1x3", "2x10"), o.vistos)
        // El especial (temporada 0) no queda como episodio, pero sí cuenta como el día en que empezó.
        assertEquals("2023-01-08", o.empezada)
        assertTrue(o.registros.isEmpty())
    }

    @Test fun pendientesYListasPropias() {
        val r = importar()
        assertEquals(Estados.QUIERO, obra(r, "Dune").estado)
        assertEquals("tmdb:movie:438631", obra(r, "Dune").fuente)
        // Dark: pendiente; la nota de un episodio no es la de la serie.
        val dark = obra(r, "Dark")
        assertEquals(Estados.QUIERO, dark.estado); assertEquals(0, dark.nota); assertEquals(Tipos.SERIE, dark.tipo)
        // Lo que solo está en una lista propia o en favoritos no entra.
        assertTrue(r.obras.none { it.titulo == "Otra" })
        assertEquals(5, r.obras.size)
    }

    @Test fun unJsonSuelto() {
        val r = Importar.trakt(historial.encodeToByteArray(), "history-1.json", id, 7, utc)!!
        assertEquals(setOf("Rain Man", "Breaking Bad"), r.obras.map { it.titulo }.toSet())
        // Un JSON suelto sin nombre útil (lo que da el selector de archivos) también sirve.
        val w = Importar.trakt(pendientes.encodeToByteArray(), "msf:31", id, 7, utc)!!
        assertTrue(w.obras.all { it.estado == Estados.QUIERO })
        assertEquals(2, w.obras.size)
    }

    @Test fun loQueNoEsDeTrakt() {
        assertNull(Importar.trakt("""[{"ts":"2024-01-01T00:00:00Z","ms_played":40000}]""".encodeToByteArray(), "a.json", id, 7))
        assertNull(Importar.trakt("""{"obras":[]}""".encodeToByteArray(), "cajon.json", id, 7))
        assertNull(Importar.trakt("Title,Date\nAlgo,1/1/24\n".encodeToByteArray(), "x.csv", id, 7))
        assertNull(Importar.trakt(zip("diary.csv" to "Date,Name,Year,Letterboxd URI\n"), "letterboxd.zip", id, 7))
        assertNull(Importar.trakt(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4, 0, 0, 1), "roto.zip", id, 7))
        // Un ZIP de Letterboxd no se toma por uno de Trakt, ni al revés.
        assertNull(Importar.letterboxd(todo, "trakt-backup-2026.zip", id, 7))
    }

    @Test fun reimportarNoDuplicaYJuntaEpisodios() {
        val primero = Importar.sumar(Estado(), importar().obras, 8)
        assertEquals(5, primero.second)
        val segundo = Importar.sumar(primero.first, importar().obras, 9)
        assertEquals(0, segundo.second)
        val bb = segundo.first.obras.single { it.titulo == "Breaking Bad" }
        assertEquals(listOf("1x1", "1x2", "1x3", "2x10"), bb.vistos)
        assertEquals(2, segundo.first.obras.single { it.titulo == "Rain Man" }.registros.size)
    }

    @Test fun seJuntaConLoQueYaHabia() {
        // A mano, sin fuente: se reconoce por título y año, toma la fuente y suma lo visto.
        val antes = Estado(obras = listOf(
            Obra("a", Tipos.SERIE, "Breaking Bad", anio = 2008, estado = Estados.EN_CURSO, vistos = listOf("1x1", "3x1"), empezada = "2020-01-01"),
            // Otra película con el mismo nombre pero ya enlazada a otra cosa: no se mezcla.
            Obra("b", Tipos.PELICULA, "Dune", anio = 2021, fuente = "tmdb:movie:1"),
        ))
        val (e, nuevas) = Importar.sumar(antes, importar().obras, 8)
        val bb = e.obras.single { it.id == "a" }
        assertEquals("tmdb:tv:1396", bb.fuente)
        assertEquals(listOf("1x1", "3x1", "1x2", "1x3", "2x10"), bb.vistos)
        assertEquals("2020-01-01", bb.empezada)
        assertEquals(10, bb.nota)
        assertEquals(2, e.obras.count { it.titulo == "Dune" })
        assertEquals(4, nuevas)
    }

    @Test fun serieDeTvmazeSeJuntaConLaDeTrakt() {
        // Sin clave de TMDB la serie se guardó desde TVmaze: es la misma, no se repite.
        val antes = Estado(obras = listOf(Obra("a", Tipos.SERIE, "Breaking Bad", anio = 2008, fuente = "tvmaze:169")))
        val (e, _) = Importar.sumar(antes, importar().obras, 8)
        assertEquals(1, e.obras.count { it.titulo == "Breaking Bad" })
        assertEquals("tvmaze:169", e.obras.single { it.titulo == "Breaking Bad" }.fuente)
        assertEquals(listOf("1x1", "1x2", "1x3", "2x10"), e.obras.single { it.titulo == "Breaking Bad" }.vistos)
    }

    @Test fun seriesViejasQuedanTerminadas() {
        val septiembre2023 = java.time.LocalDate.of(2023, 9, 1).atStartOfDay(utc).toInstant().toEpochMilli()
        val o = Importar.trakt(todo, "t.zip", id, septiembre2023, utc)!!.obras.single { it.titulo == "Breaking Bad" }
        assertEquals(Estados.TERMINADO, o.estado); assertEquals("2023-02-01", o.terminada)
        val marzo2023 = java.time.LocalDate.of(2023, 3, 1).atStartOfDay(utc).toInstant().toEpochMilli()
        assertEquals(Estados.EN_CURSO, Importar.trakt(todo, "t.zip", id, marzo2023, utc)!!.obras.single { it.titulo == "Breaking Bad" }.estado)
    }

    @Test fun fechasEnLaZonaDeLaPersona() {
        // Visto en Bogotá el 2 de mayo a las 18:30 (23:30 en UTC): es del 2 de mayo; el de las 06:33 UTC es la 01:33 en Bogotá: sigue siendo el 22.
        val o = Importar.trakt(historial.encodeToByteArray(), "history-1.json", id, 7, java.time.ZoneId.of("America/Bogota"))!!
            .obras.single { it.titulo == "Rain Man" }
        assertEquals(listOf("2021-03-22", "2024-05-02"), o.registros.map { it.fecha })
        val tokio = Importar.trakt(historial.encodeToByteArray(), "history-1.json", id, 7, java.time.ZoneId.of("Asia/Tokyo"))!!
            .obras.single { it.titulo == "Rain Man" }
        assertEquals(listOf("2021-03-22", "2024-05-03"), tokio.registros.map { it.fecha })
    }

    @Test fun zipDemasiadoGrandeNoSeLee() {
        // 60 MB de ceros comprimen a casi nada; pasar el tope devuelve null en vez de llenar la memoria.
        val b = ByteArrayOutputStream()
        ZipOutputStream(b).use { z ->
            z.putNextEntry(ZipEntry("watched/history-1.json"))
            val ceros = ByteArray(1024 * 1024)
            repeat(60) { z.write(ceros) }
            z.closeEntry()
        }
        assertNull(Importar.trakt(b.toByteArray(), "t.zip", id, 7, utc))
    }
}

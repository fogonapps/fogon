package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** La exportación de datos de TV Time: un ZIP de CSV (datos inventados con las columnas de su formato). */
class TvTimeTest {
    private var n = 0
    private val id = { p: String -> p + n++ }
    private val utc = java.time.ZoneOffset.UTC
    /** 1 de enero de 2024: lo visto desde julio de 2023 sigue en curso. */
    private val ahora = java.time.LocalDate.of(2024, 1, 1).atStartOfDay(utc).toInstant().toEpochMilli()

    // Series (v2): un episodio por fila, una repetición, un especial, la fila de totales y las banderas de cada serie.
    private val series = """created_at,user_id,key,uuid,is_archived,is_followed,s_id,is_for_later,s_no,ep_no,ep_id,is_special,series_name,season_number,episode_number
2023-11-02 11:53:58,7,watch-episode-a-1,u1,,,81189,,1,1,349232,false,Breaking Bad,1,1
2023-11-03 23:30:00,7,watch-episode-a-2,u2,,,81189,,1,2,349233,false,Breaking Bad,1,2
2023-11-04 20:00:00,7,rewatch-episode-a-2,u3,,,81189,,1,2,349233,false,Breaking Bad,1,2
2023-11-01 20:00:00,7,watch-episode-a-0,u4,,,81189,,0,1,1,true,Breaking Bad,0,1
2022-01-10 20:00:00,7,watch-episode-b-1,u5,,,121361,,,,,false,Juego de tronos,8,6
2023-12-01 10:00:00,7,tracking-stats,u6,,,,,,,,,,,
2023-01-01 10:00:00,7,user-series-c,u7,false,true,334824,false,,,,,Dark,,
2023-01-01 10:00:00,7,user-series-d,u8,false,false,1,true,,,,,The Bear,,
2023-01-01 10:00:00,7,user-series-e,u9,true,true,2,false,,,,,Lost,,
2023-01-01 10:00:00,7,user-series-f,u10,false,false,3,false,,,,,Nada,,
2023-02-01 10:00:00,7,watch-episode-e-1,u11,,,2,,1,1,9,false,Lost,1,1
"""
    // Películas (v1): vistas, repetidas, pendientes y una fecha de estreno desconocida.
    private val peliculas = """uuid,type,entity_type,movie_name,release_date,created_at,series_name,season_number,episode_number,watch_count
p1,watch,movie,Rain Man,1988-12-16,2021-03-22 06:33:24,,,,2
p2,rewatch,movie,Rain Man,1988-12-16,2023-05-02 23:30:00,,,,2
p3,towatch,movie,Dune,2021-10-22,2023-01-01 00:00:00,,,,0
p4,watch,movie,Corto raro,0001-01-01,2020-06-01 12:00:00,,,,1
p5,count-movies,movie,,,2020-06-01 12:00:00,,,,
p6,follow,episode,,,2022-02-02 10:00:00,Severance,,,
"""
    private val seguidas = """user_id,tv_show_id,notification_type,created_at,updated_at,active,archived,tv_show_name
7,334824,1,2022-01-01 00:00:00,2022-01-01 00:00:00,1,0,Dark
7,5,1,2022-01-01 00:00:00,2022-01-01 00:00:00,1,1,Mad Men
"""
    // Lo que no se debe leer nunca, aunque venga en el ZIP.
    private val credenciales = "token,created_at,series_name\nsecreto,2023-01-01 00:00:00,Robada\n"

    private fun zip(vararg archivos: Pair<String, String>): ByteArray {
        val b = ByteArrayOutputStream()
        ZipOutputStream(b).use { z -> archivos.forEach { (n, t) -> z.putNextEntry(ZipEntry(n)); z.write(t.encodeToByteArray()); z.closeEntry() } }
        return b.toByteArray()
    }

    private val todo get() = zip(
        "gdpr-data/tracking-prod-records-v2.csv" to series, "gdpr-data/tracking-prod-records.csv" to peliculas,
        "gdpr-data/followed_tv_show.csv" to seguidas, "gdpr-data/access_token.csv" to credenciales,
        "gdpr-data/seen_episode_source.csv" to credenciales,
    )

    private fun importar(z: java.time.ZoneId = utc) = Importar.tvtime(todo, "tv-time-personal-data.zip", id, ahora, z)!!
    private fun obra(r: Importado, titulo: String) = r.obras.single { it.titulo == titulo }

    @Test fun serieConEpisodiosVistos() {
        val r = importar()
        assertEquals("TV Time", r.deDonde)
        val o = obra(r, "Breaking Bad")
        assertEquals(Tipos.SERIE, o.tipo); assertEquals("", o.fuente)
        assertEquals(Estados.EN_CURSO, o.estado)
        // Sin repetir la repetición y sin el especial, que sí cuenta como el día en que empezó.
        assertEquals(listOf("1x1", "1x2"), o.vistos)
        assertEquals("2023-11-01", o.empezada)
        assertNull(o.terminada)
    }

    @Test fun serieViejaQuedaTerminada() {
        val o = obra(importar(), "Juego de tronos")
        // Sin s_no/ep_no, con season_number/episode_number.
        assertEquals(listOf("8x6"), o.vistos)
        assertEquals(Estados.TERMINADO, o.estado); assertEquals("2022-01-10", o.terminada)
    }

    @Test fun banderasDeLasSeries() {
        val r = importar()
        assertEquals(Estados.QUIERO, obra(r, "Dark").estado)      // seguida, sin episodios (y repetida en followed_tv_show)
        assertEquals(Estados.QUIERO, obra(r, "The Bear").estado)  // para después
        assertEquals(Estados.ABANDONADO, obra(r, "Lost").estado)  // archivada, aunque tenga episodios
        assertEquals(listOf("1x1"), obra(r, "Lost").vistos)
        assertEquals(Estados.ABANDONADO, obra(r, "Mad Men").estado) // archivada en followed_tv_show
        assertEquals(Estados.QUIERO, obra(r, "Severance").estado) // "follow" en v1
        assertTrue(r.obras.none { it.titulo == "Nada" })          // ni seguida ni vista
    }

    @Test fun peliculasVistasRepetidasYPendientes() {
        val r = importar()
        val rm = obra(r, "Rain Man")
        assertEquals(Tipos.PELICULA, rm.tipo); assertEquals(1988, rm.anio); assertEquals(Estados.TERMINADO, rm.estado)
        assertEquals(listOf("2021-03-22", "2023-05-02"), rm.registros.map { it.fecha })
        assertEquals(listOf(false, true), rm.registros.map { it.otraVez })
        assertEquals("2023-05-02", rm.terminada)
        val dune = obra(r, "Dune")
        assertEquals(Estados.QUIERO, dune.estado); assertEquals(2021, dune.anio); assertTrue(dune.registros.isEmpty())
        assertNull(obra(r, "Corto raro").anio)  // 0001-01-01 = desconocida
    }

    @Test fun noLeeLosArchivosDeLaCuenta() {
        assertTrue(importar().obras.none { it.titulo == "Robada" })
        assertEquals(10, importar().obras.size)
    }

    @Test fun unCsvSueltoYLaExportacionVieja() {
        val r = Importar.tvtime(peliculas.encodeToByteArray(), "tracking-prod-records.csv", id, ahora, utc)!!
        assertEquals(setOf("Rain Man", "Dune", "Corto raro", "Severance"), r.obras.map { it.titulo }.toSet())
        // La exportación vieja: seen_episode.csv con la fecha en segundos.
        val vieja = "ts,show_name,episode_id,episode_season_number,episode_number,episode_name\n1700000000,Dark,1,2,3,Ep\n"
        val o = Importar.tvtime(vieja.encodeToByteArray(), "seen_episode.csv", id, ahora, utc)!!.obras.single()
        assertEquals("Dark", o.titulo); assertEquals(listOf("2x3"), o.vistos); assertEquals("2023-11-14", o.empezada)
    }

    @Test fun fechasEnLaZonaDeLaPersona() {
        // 23:30 UTC del 2 de mayo es el 2 en Bogotá y el 3 en Tokio.
        assertEquals("2023-05-02", obra(importar(java.time.ZoneId.of("America/Bogota")), "Rain Man").terminada)
        assertEquals("2023-05-03", obra(importar(java.time.ZoneId.of("Asia/Tokyo")), "Rain Man").terminada)
    }

    @Test fun loQueNoEsDeTvTime() {
        assertNull(Importar.tvtime("Title,Date\nAlgo,1/1/24\n".encodeToByteArray(), "x.csv", id, ahora, utc))
        assertNull(Importar.tvtime("Title,Author,Exclusive Shelf\nX,Y,read\n".encodeToByteArray(), "goodreads.csv", id, ahora, utc))
        assertNull(Importar.tvtime("""[{"watched_at":"2024-01-01T00:00:00Z"}]""".encodeToByteArray(), "history.json", id, ahora, utc))
        assertNull(Importar.tvtime(zip("diary.csv" to "Date,Name,Year,Letterboxd URI\n"), "letterboxd.zip", id, ahora, utc))
        assertNull(Importar.tvtime(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4, 0, 0, 1), "roto.zip", id, ahora, utc))
        // Y los demás importadores no lo toman por suyo.
        assertNull(Importar.letterboxd(todo, "tv-time.zip", id, ahora))
        assertNull(Importar.trakt(todo, "tv-time.zip", id, ahora, utc))
        assertNull(Importar.netflix(peliculas, id, ahora))
        assertNull(Importar.goodreads(series, id, ahora))
        assertNull(Importar.storygraph(series, id, ahora))
    }

    @Test fun reimportarNoDuplica() {
        val primero = Importar.sumar(Estado(), importar().obras, 8)
        assertEquals(10, primero.second)
        val segundo = Importar.sumar(primero.first, importar().obras, 9)
        assertEquals(0, segundo.second)
        assertEquals(listOf("1x1", "1x2"), segundo.first.obras.single { it.titulo == "Breaking Bad" }.vistos)
        assertEquals(2, segundo.first.obras.single { it.titulo == "Rain Man" }.registros.size)
    }

    @Test fun seJuntaConLoQueYaHabia() {
        // Guardada antes desde TMDB: se reconoce por el título y suma los episodios, sin perder su fuente.
        val antes = Estado(obras = listOf(Obra("a", Tipos.SERIE, "Breaking Bad", anio = 2008, fuente = "tmdb:tv:1396", vistos = listOf("3x1"))))
        val (e, _) = Importar.sumar(antes, importar().obras, 8)
        val bb = e.obras.single { it.titulo == "Breaking Bad" }
        assertEquals("tmdb:tv:1396", bb.fuente); assertEquals(listOf("3x1", "1x1", "1x2"), bb.vistos)
    }

    @Test fun zipDemasiadoGrandeNoSeLee() {
        val b = ByteArrayOutputStream()
        ZipOutputStream(b).use { z ->
            z.putNextEntry(ZipEntry("gdpr-data/tracking-prod-records-v2.csv"))
            val ceros = ByteArray(1024 * 1024)
            repeat(60) { z.write(ceros) }
            z.closeEntry()
        }
        assertNull(Importar.tvtime(b.toByteArray(), "t.zip", id, ahora, utc))
    }

    @Test fun casosDeLaRevision() {
        // Mismo título, otro año: dos películas. Dos visionados el mismo día: dos registros.
        val pelis = """uuid,type,entity_type,movie_name,release_date,created_at
a,watch,movie,Dune,1984-12-14,2020-01-01 10:00:00
b,watch,movie,Dune,2021-10-22,2022-01-01 10:00:00
c,watch,movie,Up,2009-05-29,2022-03-03 10:00:00
d,rewatch,movie,Up,2009-05-29,2022-03-03 21:00:00
"""
        val r = Importar.tvtime(pelis.encodeToByteArray(), "tracking-prod-records.csv", id, ahora, utc)!!
        assertEquals(setOf(1984, 2021), r.obras.filter { it.titulo == "Dune" }.map { it.anio }.toSet())
        assertEquals(listOf(false, true), obra(r, "Up").registros.map { it.otraVez })
        assertEquals(listOf("2022-03-03", "2022-03-03"), obra(r, "Up").registros.map { it.fecha })
        // Una fila v2 con otra clave (votos, emociones) no marca la serie.
        val raras = "created_at,key,series_name,season_number,episode_number\n2023-01-01 00:00:00,vote-x,Fantasma,,\n"
        assertTrue(Importar.tvtime(raras.encodeToByteArray(), "tracking-prod-records-v2.csv", id, ahora, utc)!!.obras.isEmpty())
        // Seguida y luego dejada (active=0): no entra. Un episodio con tv_show_id sigue siendo un episodio.
        val seg = "tv_show_id,created_at,active,archived,tv_show_name\n1,2022-01-01 00:00:00,0,0,Dejada\n"
        assertTrue(Importar.tvtime(seg.encodeToByteArray(), "followed_tv_show.csv", id, ahora, utc)!!.obras.isEmpty())
        val ep = "tv_show_id,tv_show_name,episode_season_number,episode_number,created_at\n9,Dark,1,4,2023-12-01 10:00:00\n"
        assertEquals(listOf("1x4"), Importar.tvtime(ep.encodeToByteArray(), "watched_on_episode.csv", id, ahora, utc)!!.obras.single().vistos)
        // Un archivo con otro prefijo no se lee.
        val conPrefijo = zip("gdpr-data/x_seen_episode.csv" to "ts,show_name,episode_season_number,episode_number\n1700000000,Dark,1,1\n")
        assertNull(Importar.tvtime(conPrefijo, "t.zip", id, ahora, utc))
    }

    @Test fun letterboxdSoloLeeSusCsvYConTope() {
        val diario = "Date,Name,Year,Letterboxd URI,Rating,Rewatch,Watched Date\n2024-01-01,Up,2009,https://boxd.it/x,4,,2024-01-01\n"
        // Un CSV ajeno con la columna de Letterboxd (como un archivo de cuenta) no se toma.
        val ajeno = "Date,Name,Year,Letterboxd URI\n2024-01-01,Robada,2000,https://boxd.it/y\n"
        // Lo borrado y las reseñas ajenas que gustaron tampoco, aunque vayan después en el ZIP y se llamen igual.
        val r = Importar.letterboxd(zip("l/diary.csv" to diario, "access_token.csv" to ajeno, "l/deleted/diary.csv" to ajeno,
            "l/orphaned/reviews.csv" to ajeno, "l/likes/reviews.csv" to ajeno), "l.zip", id, ahora)!!
        assertEquals(listOf("Up"), r.obras.map { it.titulo })
        val gustos = Importar.letterboxd(zip("l/likes/films.csv" to "Date,Name,Year,Letterboxd URI\n2024-01-01,Up,2009,https://boxd.it/x\n"), "l.zip", id, ahora)!!
        assertTrue(gustos.obras.single().meGusta)
        val b = ByteArrayOutputStream()
        ZipOutputStream(b).use { z -> z.putNextEntry(ZipEntry("diary.csv")); val ceros = ByteArray(1024 * 1024); repeat(60) { z.write(ceros) }; z.closeEntry() }
        assertNull(Importar.letterboxd(b.toByteArray(), "l.zip", id, ahora))
    }
}

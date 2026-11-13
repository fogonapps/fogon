package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import org.junit.Assert.*
import org.junit.Test

class HistorialSpotifyTest {
    private val extendido = """{"ts":"2024-02-03T12:30:00Z","platform":"Android OS","ms_played":30000,"conn_country":"CO","ip_addr":"0.0.0.0","master_metadata_track_name":"Canción, \"Niño\"","master_metadata_album_artist_name":"Ángel","master_metadata_album_album_name":"Mañana","spotify_track_uri":"spotify:track:ejemplo","episode_name":null,"episode_show_name":null,"spotify_episode_uri":null,"reason_start":"trackdone","reason_end":"trackdone","shuffle":false,"skipped":false,"offline":false,"offline_timestamp":null,"incognito_mode":false}"""
    private val basico = """[{"endTime":"2024-02-03 12:30","artistName":"Ángel","trackName":"Niño","msPlayed":180000}]"""
    private fun leer(texto: String) = Importar.spotify(texto.toByteArray(), "Streaming_History_Audio_2024_0.json")

    @Test fun extendidoSueltoYZipConVariosArchivos() {
        val escucha = leer("\uFEFF[$extendido]")!!.escuchas.single()
        assertEquals("Ángel", escucha.artista); assertEquals("Mañana", escucha.album)
        assertEquals("Canción, \"Niño\"", escucha.cancion)
        assertEquals(java.time.Instant.parse("2024-02-03T12:30:00Z").toEpochMilli(), escucha.cuando)
        val zip = zipImportacion("datos/Streaming_History_Audio_2024_0.json" to "[$extendido]", "datos/Streaming_History_Audio_2024_1.json" to "[$extendido]", "ajeno.json" to "[$extendido]")
        val r = Importar.spotify(zip, "datos.zip")!!
        assertEquals(2, r.escuchas.size); assertFalse(r.sinAlbum)
    }

    @Test fun basicoIndicaQueNoTieneAlbum() {
        val r = Importar.spotify(basico.toByteArray(), "StreamingHistory_music_0.json")!!
        assertTrue(r.sinAlbum); assertTrue(r.escuchas.isEmpty())
        assertTrue(Importar.spotify(zipImportacion("StreamingHistory_music_0.json" to basico), "datos.zip")!!.sinAlbum)
    }

    @Test fun filtraEscuchasCortasPodcastsYDatosInvalidos() {
        assertNull(leer("[" + extendido.replace("30000", "29999") + "]"))
        assertNull(leer("[" + extendido.replace("\"Mañana\"", "null") + "]"))
        assertNull(leer("")); assertNull(leer("[]")); assertNull(leer("[{\"Title\":\"Netflix\"}]"))
        assertNull(Importar.spotify(zipImportacion("otro.json" to "[$extendido]"), "x.zip"))
        assertNull(Importar.spotify(byteArrayOf(80, 75, 3, 4, 0), "x.zip"))
    }

    @Test fun reimportarHistorialNoIncrementaEscuchas() {
        val escuchas = leer("[$extendido]")!!.escuchas
        val primera = RegistroEscuchas.sumarA(Oidos(), escuchas)
        assertEquals(1, primera.albumes.single().veces)
        assertEquals(primera, RegistroEscuchas.sumarA(primera, escuchas))
    }

    @Test(timeout = 10000) fun cincoMilEscuchas() {
        assertEquals(5000, leer((1..5000).joinToString(",", "[", "]") { extendido })!!.escuchas.size)
    }
}

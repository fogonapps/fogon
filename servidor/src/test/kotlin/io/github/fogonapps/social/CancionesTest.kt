package io.github.fogonapps.social

import kotlinx.serialization.json.*
import kotlin.test.*

class CancionesTest {
    private fun cancion(titulo: String, id: Int, artista: String = "Bad Bunny", album: String = "Otro",
                         audio: String = "https://audio-ssl.itunes.apple.com/$id.m4a") = buildJsonObject {
        put("trackName", titulo)
        put("artistName", artista)
        put("artistId", 1126808565L)
        put("collectionName", album)
        put("artworkUrl100", "https://is1-ssl.mzstatic.com/$id/100x100bb.jpg")
        put("previewUrl", audio)
        put("trackViewUrl", "https://music.apple.com/co/song/$id")
    }

    private fun respuesta(vararg canciones: JsonObject) = buildJsonObject {
        put("results", JsonArray(canciones.toList()))
    }.toString()

    @Test fun respaldoPorArtistaYCacheCompartida() {
        val urls = mutableListOf<String>()
        val canciones = Canciones(pedir = { url ->
            urls.add(url)
            if ("/search?" in url) respuesta(cancion("Otra", 1))
            else respuesta(
                buildJsonObject { put("wrapperType", "artist"); put("artistName", "Bad Bunny") },
                cancion("NO ME QUIERO CASAR", 2),
                cancion("Neverita", 3)
            )
        })
        val lista = canciones.buscar("No me quiero casar Bad Bunny")
        assertEquals(listOf("NO ME QUIERO CASAR", "Otra"), lista.map { it.titulo })
        assertEquals("https://audio-ssl.itunes.apple.com/2.m4a", lista.first().audio)
        assertTrue(lista.first().portada.endsWith("300x300bb.jpg"))
        assertEquals(2, urls.size)
        assertTrue(urls.last().contains("lookup?id=1126808565&entity=song&limit=200&country=CO"))
        assertEquals(lista, canciones.buscar("No me quiero casar Bad Bunny"))
        assertEquals(2, urls.size)
        assertEquals("Neverita", canciones.buscar("neverita bad bunny").first().titulo)
        assertEquals(3, urls.size)
    }

    @Test fun sinArtistaOSinRestoNoConsultaCatalogo() {
        for ((consulta, artista) in listOf("no me quiero casar" to "Bad Bunny", "buena" to "a", "bad bunny" to "Bad Bunny")) {
            var pedidos = 0
            val canciones = Canciones(pedir = {
                pedidos++
                respuesta(cancion("Otra", 1, artista))
            })
            canciones.buscar(consulta)
            assertEquals(1, pedidos, consulta)
        }
    }

    @Test fun falloDelCatalogoConservaBusqueda() {
        var pedidos = 0
        val canciones = Canciones(pedir = {
            pedidos++
            if ("/search?" in it) respuesta(cancion("Otra", 1)) else null
        })
        assertEquals(listOf("Otra"), canciones.buscar("no me quiero casar bad bunny").map { it.titulo })
        assertEquals(2, pedidos)
    }

    @Test fun coincideConAlbumNormalizadoYFiltraDuplicadosEInseguros() {
        val canciones = Canciones(pedir = {
            if ("/search?" in it) respuesta(cancion("Otra", 1, "Bád Bunny"))
            else respuesta(
                cancion("Primera", 2, album = "Nadie sabe lo que va a pasar mañana"),
                cancion("PRÍMERA!", 3, album = "Nadie sabe lo que va a pasar mañana"),
                cancion("Insegura", 4, album = "Nadie sabe lo que va a pasar mañana", audio = "https://otro.example/audio"),
                cancion("No coincide", 5)
            )
        })
        assertEquals(listOf("Primera", "Otra"),
            canciones.buscar("nadie sabe lo que va a pasar manana BAD-BUNNY").map { it.titulo })
    }

    @Test fun coincidenciaExistenteEvitaRespaldo() {
        for (porAlbum in listOf(false, true)) {
            var pedidos = 0
            val canciones = Canciones(pedir = {
                pedidos++
                respuesta(cancion(if (porAlbum) "Otra" else "Neverita", 1,
                    album = if (porAlbum) "Neverita" else "Otro"))
            })
            canciones.buscar("neverita bad bunny")
            assertEquals(1, pedidos)
        }
    }

    @Test fun usaArtistaCrudoYLimitaLaRespuesta() {
        var pedidos = 0
        val canciones = Canciones(pedir = {
            pedidos++
            if ("/search?" in it) respuesta(cancion("Otra", 1, audio = ""))
            else respuesta(*(2..20).map { id -> cancion("Neverita $id", id) }.toTypedArray())
        })
        val lista = canciones.buscar("neverita bad bunny")
        assertEquals(15, lista.size)
        assertEquals("Neverita 2", lista.first().titulo)
        assertEquals(2, pedidos)
    }

    @Test fun catalogoVenceYSeSeparaPorPais() {
        var reloj = 0L
        var consultasCatalogo = 0
        val canciones = Canciones(ahora = { reloj }, pedir = {
            if ("/search?" in it) respuesta(cancion("Otra", 1))
            else {
                consultasCatalogo++
                respuesta(cancion("Neverita", 2))
            }
        })
        canciones.buscar("neverita bad bunny", "CO")
        canciones.buscar("neverita bad bunny", "MX")
        assertEquals(2, consultasCatalogo)
        reloj += 24 * 3600_000L
        canciones.buscar("neverita bad bunny", "CO")
        assertEquals(3, consultasCatalogo)
    }
}

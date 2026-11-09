package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class LastfmTest {
    private val pista = """{"artist":{"#text":"Artista"},"album":{"#text":"Disco"},"name":"Canción","date":{"uts":"1700000000"}}"""
    private fun pagina(pistas: String) = JsonCajon.parseToJsonElement("""{"recenttracks":{"@attr":{"totalPages":"2","total":"201"},"track":$pistas}}""").jsonObject

    @Test fun convierteSegundosYOmiteLoQueSuenaOSinAlbum() {
        val sinAlbum = pista.replace("Disco", "")
        val sonando = """{"@attr":{"nowplaying":"true"},"artist":{"#text":"Artista"}}"""
        val r = Lastfm.convertir(pagina("[$pista,$sinAlbum,$sonando]"))
        assertEquals(2, r.paginas)
        assertEquals(201L, r.total)
        assertEquals(listOf(Escucha("Artista", "Disco", "Canción", 1700000000000, "Last.fm")), r.escuchas)
        assertEquals(1, Lastfm.convertir(pagina(pista)).escuchas.size)
    }

    @Test fun repetirPaginasYReabrirNoDuplicaNiSpotify() {
        val x = Lastfm.convertir(pagina("[$pista,$pista]")).escuchas
        val a = RegistroEscuchas.sumarA(Oidos(), x)
        val guardado = JsonCajon.decodeFromString(Oidos.serializer(), JsonCajon.encodeToString(Oidos.serializer(), a))
        val b = RegistroEscuchas.sumarA(guardado, x + x.map { it.copy(app = "Spotify") })
        assertEquals(a, b)
        assertEquals(1, b.albumes.single().veces)
        val c = RegistroEscuchas.sumarA(b, listOf(x.first().copy(cuando = x.first().cuando + 120000)))
        assertEquals(2, c.albumes.single().veces)
    }
}

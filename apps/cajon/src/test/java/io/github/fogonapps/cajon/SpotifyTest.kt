package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.Spotify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyTest {
    @Test fun laAutorizacionLlevaPkceYState() {
        val u = Spotify.urlAutorizar("abc", "http://127.0.0.1:5555/callback", "reto", "e1")
        assertTrue(u.startsWith("https://accounts.spotify.com/authorize?"))
        assertTrue("code_challenge_method=S256" in u && "code_challenge=reto" in u && "state=e1" in u)
        assertTrue("redirect_uri=http%3A%2F%2F127.0.0.1%3A5555%2Fcallback" in u)
    }

    @Test fun laVueltaSeLeeSegunCodigoStateYError() {
        assertEquals(Spotify.Vuelta.Codigo("xyz"), Spotify.leerVuelta("GET /callback?code=xyz&state=e1 HTTP/1.1", "e1"))
        assertEquals(Spotify.Vuelta.Ajena, Spotify.leerVuelta("GET /callback?code=xyz&state=otro HTTP/1.1", "e1"))
        assertEquals(Spotify.Vuelta.Ajena, Spotify.leerVuelta("GET /favicon.ico HTTP/1.1", "e1"))
        assertEquals(Spotify.Vuelta.Cancelada, Spotify.leerVuelta("GET /callback?error=access_denied&state=e1 HTTP/1.1", "e1"))
        assertEquals(Spotify.Vuelta.Error("invalid_scope"), Spotify.leerVuelta("GET /callback?error=invalid_scope&state=e1 HTTP/1.1", "e1"))
    }
}

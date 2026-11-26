package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.ui.Marcas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Los enlaces del perfil reconocen la red por su dominio exacto y muestran el usuario. */
class MarcasTest {
    @Test fun reconoceLaRedYElUsuario() {
        assertEquals("@marina", Marcas.texto("https://www.instagram.com/marina/"))
        assertEquals("@marina", Marcas.texto("https://letterboxd.com/marina"))
        assertEquals("@cine.diario", Marcas.texto("https://www.tiktok.com/@cine.diario"))
        assertEquals("@canal", Marcas.texto("https://youtube.com/@canal"))
        assertEquals("@marina.bsky.social", Marcas.texto("https://bsky.app/profile/marina.bsky.social"))
        assertEquals("@marina", Marcas.texto("https://anilist.co/user/marina/"))
        // Sin usuario en la dirección, el nombre de la red.
        assertEquals("Spotify", Marcas.texto("https://open.spotify.com/user/31abc?si=x"))
        assertEquals("X", Marcas.texto("https://twitter.com/"))
    }

    @Test fun unDominioParecidoNoSeHacePasarPorLaRed() {
        assertNull(Marcas.de("https://instagram.com.estafa.io/marina"))
        assertNull(Marcas.de("https://falsoinstagram.com/marina"))
        assertNull(Marcas.texto("https://miblog.net"))
        assertNull(Marcas.de("no es un enlace"))
    }

    @Test fun todosLosLogosSeLeen() {
        Marcas.TODAS.forEach { androidx.compose.ui.graphics.vector.PathParser().parsePathString(it.trazo) }
    }
}

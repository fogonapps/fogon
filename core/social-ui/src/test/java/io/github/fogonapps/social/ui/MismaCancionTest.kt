package io.github.fogonapps.social.ui

import io.github.fogonapps.social.CancionSocial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MismaCancionTest {
    private fun c(t: String, a: String, audio: String = "https://audio-ssl.itunes.apple.com/x.m4a") = CancionSocial(t, a, audio = audio)

    @Test fun `elige el mismo titulo y artista aunque cambien tildes y mayusculas`() {
        val lista = listOf(c("La Nave del Olvido (En Vivo)", "Otro"), c("La nave del olvido", "Jose Jose"), c("Otra", "José José"))
        assertEquals("Jose Jose", mismaCancion("La nave del olvido", "José José", lista)?.artista)
    }

    @Test fun `sin el mismo titulo o sin avance no elige ninguna`() {
        assertNull(mismaCancion("La nave del olvido", "José José", listOf(c("Otra", "José José"), c("La nave del olvido", "José José", audio = ""))))
    }
}

package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import org.junit.Assert.*
import org.junit.Test

class EscuchasTest {
    private val album = AlbumOido("artista|album", "Artista", "Álbum")

    @Test fun jsonAnteriorSinPortada() {
        val o = JsonCajon.decodeFromString(Oidos.serializer(), """{"albumes":[{"clave":"artista|album","artista":"Artista","album":"Álbum"}]}""")
        assertEquals(album, o.albumes.single())
        assertEquals("", o.albumes.single().portada)
    }

    @Test fun unirConservaPortadaEnAmbosSentidos() {
        val con = Oidos(listOf(album.copy(portada = "https://ejemplo.org/portada.jpg")))
        val sin = Oidos(listOf(album.copy(canciones = listOf("una"), ultima = 20)))
        for ((a, b) in listOf(con to sin, sin to con)) {
            val unido = RegistroEscuchas.unirA(a, b).albumes.single()
            assertEquals(con.albumes.single().portada, unido.portada)
            assertEquals(listOf("una"), unido.canciones)
            assertEquals(20L, unido.ultima)
        }
    }

    @Test fun pendientesSoloSinObraNiDescartadosOrdenados() {
        val reciente = album.copy(clave = "reciente", ultima = 30, portada = "https://ejemplo.org/a.jpg")
        val o = Oidos(listOf(album, album.copy(clave = "agregado", obra = "obra"),
            album.copy(clave = "descartado", descartado = true), reciente))
        assertEquals(listOf(reciente, album), RegistroEscuchas.pendientes(o))
    }

    @Test fun sumarSoloAdmitePortadasHttpsYLasConserva() {
        fun escucha(url: String, cuando: Long) = Escucha("Artista", "Álbum", "Canción", cuando, "App", portada = url)
        val sin = RegistroEscuchas.sumarA(Oidos(), listOf(escucha("content://imagen", 1)))
        assertEquals("", sin.albumes.single().portada)
        val con = RegistroEscuchas.sumarA(sin, listOf(escucha("https://ejemplo.org/a.jpg", 60001)))
        assertEquals("https://ejemplo.org/a.jpg", con.albumes.single().portada)
        assertEquals(con.albumes.single().portada, RegistroEscuchas.sumarA(con, listOf(escucha("", 120001))).albumes.single().portada)
    }
}

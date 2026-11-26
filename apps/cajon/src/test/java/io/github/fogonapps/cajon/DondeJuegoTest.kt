package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import org.junit.Assert.*
import org.junit.Test

/** «Dónde lo juegas»: poner y quitar, sin repetir ni pasar el tope, unir dos teléfonos y las cifras de juegos. */
class DondeJuegoTest {
    private val juego = Obra(id = "j", tipo = Tipos.JUEGO, titulo = "Hollow Knight", plataformas = listOf("PC", "Nintendo Switch"))

    @Test fun seAlternaSinRepetir() {
        val a = Logica.alternarDondeJuego(juego, " Nintendo Switch ")
        assertEquals(listOf("Nintendo Switch"), a.dondeJuego)
        assertEquals(emptyList<String>(), Logica.alternarDondeJuego(a, "nintendo switch").dondeJuego)
        assertSame(juego, Logica.alternarDondeJuego(juego, "   "))
    }

    @Test fun tieneTope() {
        var o = juego
        (1..Logica.MAX_DONDE_JUEGO + 2).forEach { o = Logica.alternarDondeJuego(o, "P$it") }
        assertEquals(Logica.MAX_DONDE_JUEGO, o.dondeJuego.size)
    }

    @Test fun lasOpcionesSonDondeSaleOLasComunes() {
        assertEquals(listOf("PC", "Nintendo Switch"), Logica.opcionesDondeJuego(juego))
        val aMano = Obra(id = "m", tipo = Tipos.JUEGO, titulo = "Tetris", dondeJuego = listOf("Game Boy"))
        assertEquals(Logica.PLATAFORMAS_COMUNES + "Game Boy", Logica.opcionesDondeJuego(aMano))
        // Elegir una no la cambia de lugar.
        assertEquals(Logica.opcionesDondeJuego(juego), Logica.opcionesDondeJuego(Logica.alternarDondeJuego(juego, "Nintendo Switch")))
    }

    @Test fun alSincronizarGanaLoMasReciente() {
        // Quitar una plataforma en un teléfono tiene que llegar al otro: no se suman, gana la versión más nueva.
        val a = juego.copy(dondeJuego = listOf("PC", "Nintendo Switch"), cambio = 1)
        val b = juego.copy(dondeJuego = listOf("PC"), cambio = 2)
        assertEquals(listOf("PC"), Logica.unir(Estado(obras = listOf(a)), Estado(obras = listOf(b))).obras.single().dondeJuego)
    }

    @Test fun alJuntarRepetidasSeSuman() {
        val a = juego.copy(dondeJuego = listOf("PC"))
        val b = juego.copy(id = "k", dondeJuego = listOf("pc", "Nintendo Switch"))
        assertEquals(listOf("PC", "Nintendo Switch"), Logica.juntarPersonal(a, listOf(b)).dondeJuego)
    }

    @Test fun lasCifrasCuentanDondeSeJugoLoTerminado() {
        val e = Estado(obras = listOf(
            juego.copy(id = "1", estado = Estados.TERMINADO, dondeJuego = listOf("PC")),
            juego.copy(id = "2", titulo = "Celeste", estado = Estados.TERMINADO, dondeJuego = listOf("PC", "Nintendo Switch")),
            juego.copy(id = "3", titulo = "Hades", estado = Estados.QUIERO, dondeJuego = listOf("Nintendo Switch"))))
        assertEquals(listOf("PC" to 2, "Nintendo Switch" to 1), Logica.cifras(e, null).plataformas)
    }
}

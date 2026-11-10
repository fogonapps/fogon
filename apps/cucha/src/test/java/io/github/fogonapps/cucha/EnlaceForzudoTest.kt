package io.github.fogonapps.cucha

import io.github.fogonapps.cucha.datos.EnlaceForzudo
import io.github.fogonapps.cucha.datos.FORZUDO_NINGUNO
import io.github.fogonapps.cucha.datos.enlaceForzudo
import io.github.fogonapps.identidad.PerfilDeApp
import org.junit.Assert.assertEquals
import org.junit.Test

class EnlaceForzudoTest {
    private val ana = PerfilDeApp("1", "Ana", "ana", "c-ana")
    private val beto = PerfilDeApp("2", "Beto", "beto", "c-beto")
    private val sinCuenta = PerfilDeApp("3")

    @Test fun sinPerfilesYUnSoloPerfilSigueComoAntes() {
        assertEquals(EnlaceForzudo.Unico, enlaceForzudo(null, "", null, 1))
        assertEquals(EnlaceForzudo.Ninguno, enlaceForzudo(null, "", null, 2))
        assertEquals(EnlaceForzudo.Perfil("3"), enlaceForzudo(listOf(sinCuenta), "", null, 1))
    }

    @Test fun laMismaCuentaGanaAunqueElNumeroSeaOtro() {
        assertEquals(EnlaceForzudo.Perfil("2"), enlaceForzudo(listOf(ana, beto), "c-beto", "1", 2))
    }

    @Test fun nuncaSeAsumeElMismoNumero() {
        assertEquals(EnlaceForzudo.Elegir, enlaceForzudo(listOf(ana, beto), "", null, 2))
        assertEquals(EnlaceForzudo.Elegir, enlaceForzudo(listOf(ana), "", null, 2))
        assertEquals(EnlaceForzudo.Elegir, enlaceForzudo(listOf(ana, beto), "c-otra", null, 1))
    }

    @Test fun cuentasDistintasNoSeMezclan() {
        assertEquals(EnlaceForzudo.Ninguno, enlaceForzudo(listOf(ana), "c-otra", null, 1))
    }

    @Test fun loElegidoSeRespeta() {
        assertEquals(EnlaceForzudo.Perfil("1"), enlaceForzudo(listOf(ana, beto), "", "1", 2))
        assertEquals(EnlaceForzudo.Ninguno, enlaceForzudo(listOf(ana), "", FORZUDO_NINGUNO, 1))
        // Un perfil elegido que ya no existe vuelve a preguntar.
        assertEquals(EnlaceForzudo.Elegir, enlaceForzudo(listOf(ana, beto), "", "9", 1))
    }
}

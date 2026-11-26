package io.github.fogonapps.social.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagosExternosTest {
    @Test fun playNoOfrecePagosExternos() {
        assertFalse(ofrecePagosExternos(tiendaPlay = true))
    }

    @Test fun distribucionHabitualConservaPagosExternos() {
        assertTrue(ofrecePagosExternos(tiendaPlay = false))
    }

    @Test fun playVendeSoloConPasarela() {
        assertTrue(ofrecePagosPlay(tiendaPlay = true, hayPasarela = true))
        assertFalse(ofrecePagosPlay(tiendaPlay = true, hayPasarela = false))
        assertFalse(ofrecePagosPlay(tiendaPlay = false, hayPasarela = true))
    }

    @Test fun cadaErrorDePlayTieneSuTexto() {
        val motivos = listOf("cancelada", "no_disponible", "conexion", "rechazada", "sesion", "ya_tenia")
        assertEquals(motivos.size, motivos.map(::textoErrorPlay).toSet().size)
        assertFalse(textoErrorPlay("otro") in motivos.map(::textoErrorPlay))
    }
}

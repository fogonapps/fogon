package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.Steam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** «Entrar con Steam»: la página oficial de Steam y una vuelta que solo vale si la pidió este teléfono. */
class SteamEntrarTest {
    private val id = "76561197960287930"
    private val cuenta = "https://steamcommunity.com/openid/id/$id"

    @Test fun laEntradaEsLaPaginaOficialYVuelveAFogon() {
        val u = Steam.inicioSesion("abc123")
        assertTrue(u.startsWith("https://steamcommunity.com/openid/login?"))
        assertTrue("openid.mode=checkid_setup" in u)
        assertTrue("openid.return_to=" + java.net.URLEncoder.encode("https://fogonapps.duckdns.org/steam/vuelta?estado=abc123", "UTF-8") in u)
    }

    @Test fun soloSeAceptaLaVueltaPedidaAqui() {
        assertEquals(id, Steam.cuentaDeVuelta("abc", "id_res", cuenta, "abc"))
        // Un enlace ajeno (otro estado, o ninguno pedido) no trae juegos de otra cuenta.
        assertNull(Steam.cuentaDeVuelta("otro", "id_res", cuenta, "abc"))
        assertNull(Steam.cuentaDeVuelta("abc", "id_res", cuenta, null))
        assertNull(Steam.cuentaDeVuelta("abc", "cancel", cuenta, "abc"))
        assertNull(Steam.cuentaDeVuelta("abc", "id_res", "https://otro.com/openid/id/$id", "abc"))
        assertTrue(Steam.perfilValido(id))
    }
}

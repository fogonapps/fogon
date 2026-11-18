package io.github.fogonapps.perfil

import org.junit.Assert.*
import org.junit.Test

class BannerOficialTest {
    @Test fun soloLaInsigniaOficialHabilitaElBanner() {
        assertTrue("oficial" in bannersDisponibles(emptyList(), "fogon"))
        assertFalse("oficial" in bannersDisponibles(emptyList(), ""))
        assertFalse("oficial" in bannersDisponibles(listOf("banner:oficial"), "verificada"))
    }

    @Test fun tieneRecursoSinCatalogoNiPrecio() {
        assertEquals(R.drawable.banner_oficial, TemasPerfil.RECURSOS_BANNER["oficial"])
        assertFalse(TemasPerfil.BANNERS.containsKey("oficial"))
        assertFalse(TemasPerfil.PRECIOS.containsKey("banner:oficial"))
    }
}

package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import org.junit.Assert.*
import org.junit.Test

class GenerosPerfilTest {
    @Test fun `los generos de musica se separan de los de cine`() {
        assertTrue(GenerosPerfil.esMusical("hip hop")); assertTrue(GenerosPerfil.esMusical("latin rock")); assertTrue(GenerosPerfil.esMusical("electronica"))
        assertFalse(GenerosPerfil.esMusical("terror")); assertFalse(GenerosPerfil.esMusical("drama")); assertFalse(GenerosPerfil.esMusical("romance"))
        assertFalse(GenerosPerfil.esMusical("action adventure"))
    }
}

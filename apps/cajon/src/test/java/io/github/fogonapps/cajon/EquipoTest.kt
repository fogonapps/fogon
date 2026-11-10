package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class EquipoTest {
    @Test fun cuentaAntiguaNoConcedePermisos() {
        val c = Json.decodeFromString<MiCuenta>("""{"usuario":"prueba","admin":true}""")
        assertTrue(c.permisos.isEmpty())
        assertEquals("", c.rol)
        assertFalse(c.mfa)
        assertEquals(0L, c.elevadaHasta)
    }

    @Test fun elevacionVenceEnElLimiteYExigeFactorActivo() {
        val c = MiCuenta("prueba", mfa = true, elevadaHasta = 1000)
        assertFalse(necesitaElevar(c, 999))
        assertTrue(necesitaElevar(c, 1000))
        assertTrue(necesitaElevar(c, 1001))
        assertTrue(necesitaElevar(c.copy(mfa = false), 999))
        assertTrue(necesitaElevar(c.copy(elevadaHasta = 0), 999))
    }







    @Test fun estadoDelCasoYPermisoControlanRestauracion() {
        val caso = Json.decodeFromString<CasoModeracion>("""{"que":"publicacion","objeto":"1","estado":"retirado","retiradoHasta":123}""")
        assertEquals(123L, caso.retiradoHasta)
        assertTrue(puedeRestaurar(caso, setOf("gestionar_publicaciones")))
        assertFalse(puedeRestaurar(caso, emptySet()))
        for (estado in listOf("visible", "preservado")) assertFalse(puedeRestaurar(caso.copy(estado = estado), setOf("gestionar_publicaciones")))
        val antiguo = Json.decodeFromString<CasoModeracion>("""{"que":"comentario","objeto":"2"}""")
        assertFalse(puedeRestaurar(antiguo, setOf("gestionar_publicaciones")))
        assertEquals(9L, convertirRegistro("""{"entradas":[],"cadenaIntegra":true,"siguiente":9}""").siguiente)
        assertNull(convertirRegistro("""{"entradas":[],"cadenaIntegra":false,"siguiente":null}""").siguiente)
    }


}

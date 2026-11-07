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

    @Test fun accionesExigenTodosSusPermisos() {
        assertTrue(accionesModeracion(emptySet()).isEmpty())
        assertTrue(accionesModeracion(setOf("sancion_grave", "cerrar_cuentas")).isEmpty())
        assertEquals(setOf("sin_falta", "falta"), accionesModeracion(setOf("sancionar")))
        assertEquals(setOf("sin_falta", "falta", "falta_grave"), accionesModeracion(setOf("sancionar", "sancion_grave")))
        assertEquals(setOf("sin_falta", "falta", "cerrar"), accionesModeracion(setOf("sancionar", "cerrar_cuentas")))
    }

    @Test fun sancionesQueCierranNoAparecenSinPermisoDeCierre() {
        val p = setOf("sancionar", "sancion_grave")
        for (regla in listOf("menor_en_riesgo", "menor_de_edad", "evadir_sancion", "amenaza_real")) {
            assertEquals(setOf("sin_falta"), accionesModeracion(p, regla))
        }
        assertEquals(setOf("sin_falta", "falta"), accionesModeracion(p, faltas = 2))
        assertEquals(setOf("sin_falta"), accionesModeracion(p, faltas = 3))
        assertEquals(setOf("sin_falta", "falta", "falta_grave", "cerrar"), accionesModeracion(p + "cerrar_cuentas", faltas = 3))
    }

    @Test fun registroConvierteCamposSinPerderFechaNiOrden() {
        val lista = """[{"id":9,"actor":"servidor","accion":"equipo_dar","objeto":"42","detalle":"moderador","cuando":1234567890123,"sesion":"no mostrar","huella":"abc"}]"""
        val r = convertirRegistro(lista)
        assertEquals(EntradaRegistro(9, "servidor", "equipo_dar", "42", "moderador", 1234567890123), r.entradas.single())
        assertTrue(r.cadenaIntegra)
        assertFalse(convertirRegistro("""{"entradas":$lista,"cadenaIntegra":false}""").cadenaIntegra)
        assertTrue(convertirRegistro("[]").entradas.isEmpty())
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

    @Test fun uriDeInscripcionSeConservaComoLaEntregaElServidor() {
        val i = Json.decodeFromString<InscripcionMfa>("""{"secreto":"ABC234","uri":"otpauth://totp/Fogón:prueba?secret=ABC234&issuer=Fogón"}""")
        assertEquals("otpauth://totp/Fogón:prueba?secret=ABC234&issuer=Fogón", i.uri)
    }
}

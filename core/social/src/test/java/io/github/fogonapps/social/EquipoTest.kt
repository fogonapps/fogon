package io.github.fogonapps.social

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class EquipoTest {
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
    @Test fun uriDeInscripcionSeConservaComoLaEntregaElServidor() {
        val i = Json.decodeFromString<InscripcionMfa>("""{"secreto":"ABC234","uri":"otpauth://totp/Fogón:prueba?secret=ABC234&issuer=Fogón"}""")
        assertEquals("otpauth://totp/Fogón:prueba?secret=ABC234&issuer=Fogón", i.uri)
    }
}

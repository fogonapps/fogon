package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PrivacidadTest {
    @Test fun aceptacionDependeDeSesionYVersionDeLaCuenta() {
        assertFalse(debeAceptarPolitica(false, "", POLITICA_VIGENTE))
        assertTrue(debeAceptarPolitica(true, "2026-11-03", POLITICA_VIGENTE))
        assertTrue(debeAceptarPolitica(true, null, POLITICA_VIGENTE))
        assertFalse(debeAceptarPolitica(true, POLITICA_VIGENTE, POLITICA_VIGENTE))
        assertTrue(debeAceptarPolitica(true, POLITICA_VIGENTE, "otra-version"))
    }

    @Test fun posponerImpideSubirSincronizarYChatearAntesDeTocarLaRed() = runTest {
        val cliente = ClienteSocial("sesion-de-prueba", permiteRed = { false })
        val operaciones: List<suspend () -> Unit> = listOf(
            { cliente.subirPublicaciones(emptyList()) },
            { cliente.guardarCajon("sobre", 0) },
            { cliente.cajon() },
            { cliente.guardarLlaveCajon("llave") },
            { cliente.mandar("alguien", "sobre", "firma") },
            { cliente.sinLeer() },
        )
        for (operacion in operaciones) {
            try { operacion(); fail("La red debe permanecer en pausa") }
            catch (e: java.io.IOException) { assertEquals("politica_pendiente", e.message) }
        }
        // El estado local no depende de la autorización para la red.
        val local = Estado().copy(obras = listOf(Obra(id = "local", titulo = "Mi libro", tipo = Tipos.LIBRO)))
        assertEquals("Mi libro", local.obras.single().titulo)
    }

    @Test fun gestionDeCuentaSigueDisponibleDuranteLaPausa() {
        listOf("/cuenta", "/cuenta/politica", "/cuenta/borrar", "/acceso/codigo", "/salir").forEach {
            assertTrue(rutaPermitidaSinPolitica(it))
        }
        listOf("/cajon", "/perfil", "/publicaciones", "/mensajes", "/avisos").forEach {
            assertFalse(rutaPermitidaSinPolitica(it))
        }
    }

    @Test fun opcionesReflejanCredencialesPermisosSesionYPreferencias() {
        fun estado(spotify: Boolean = false, github: Boolean = false, lectura: Boolean = false,
            permiso: Boolean = false, avisos: Boolean = false, mensajes: Boolean = false,
            sesion: Boolean = false, llave: Boolean = false) =
            opcionesPrivacidad(spotify, github, lectura, permiso, avisos, mensajes, sesion, llave)
        assertEquals(OpcionesPrivacidad(false, false, false, false, false), estado())
        assertTrue(estado(spotify = true).spotify)
        assertTrue(estado(github = true).github)
        assertTrue(estado(lectura = true).leerNotificaciones)
        assertFalse(estado(permiso = true, avisos = true).avisos)
        assertFalse(estado(sesion = true, avisos = true).avisos)
        assertFalse(estado(sesion = true, permiso = true).avisos)
        assertTrue(estado(sesion = true, permiso = true, avisos = true).avisos)
        assertTrue(estado(sesion = true, permiso = true, mensajes = true).avisos)
        assertFalse(estado(llave = true).copiaCifrada)
        assertFalse(estado(sesion = true).copiaCifrada)
        assertTrue(estado(sesion = true, llave = true).copiaCifrada)
    }
}

class RevisarAlAbrirTest {
    @Test fun revisaLaPrimeraVezYDespuesComoMuchoUnaVezPorMinuto() {
        org.junit.Assert.assertTrue(io.github.fogonapps.cajon.datos.debeRevisarAlAbrir(0, 5_000))
        org.junit.Assert.assertFalse(io.github.fogonapps.cajon.datos.debeRevisarAlAbrir(5_000, 30_000))
        org.junit.Assert.assertTrue(io.github.fogonapps.cajon.datos.debeRevisarAlAbrir(5_000, 65_000))
    }
}

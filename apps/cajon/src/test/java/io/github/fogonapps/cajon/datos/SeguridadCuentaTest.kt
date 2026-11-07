package io.github.fogonapps.cajon.datos

import org.junit.Assert.*
import org.junit.Test

class SeguridadCuentaTest {
    @Test fun decisionDuranteGraciaAunqueFechaHayaPasado() {
        assertFalse(debePreguntarBorrado(0))
        assertTrue(debePreguntarBorrado(1))
        assertTrue(debePreguntarBorrado(Long.MAX_VALUE))
    }

    @Test fun formularioExigeLaCredencialCorrecta() {
        assertFalse(credencialBorradoValida(true, "", "123456", ""))
        assertTrue(credencialBorradoValida(true, "contraseña existente", "", "a".repeat(300)))
        assertFalse(credencialBorradoValida(true, "clave", "", "a".repeat(301)))
        assertFalse(credencialBorradoValida(false, "clave", "", ""))
        assertFalse(credencialBorradoValida(false, "", "12345x", ""))
        assertFalse(credencialBorradoValida(false, "", "１２３４５６", ""))
        assertTrue(credencialBorradoValida(false, "", "012345", ""))
    }

    @Test fun validaCorreoNuevo() {
        assertFalse(correoNuevoValido(" ANA@correo.co ", "ana@correo.co"))
        assertFalse(correoNuevoValido("nuevo", "ana@correo.co"))
        assertFalse(correoNuevoValido("a b@correo.co", "ana@correo.co"))
        assertTrue(correoNuevoValido("nuevo@correo.co", "ana@correo.co"))
    }
}

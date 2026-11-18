package io.github.fogonapps.social.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumeroRepisaTest {
    @Test fun aceptaVacioYCifrasConUnSeparadorDecimal() {
        assertEquals("", limpiarNumeroRepisa(""))
        assertEquals("0", limpiarNumeroRepisa("0"))
        assertEquals("12.5", limpiarNumeroRepisa("12,5"))
        assertEquals("12.", limpiarNumeroRepisa("12,"))
        assertEquals(".5", limpiarNumeroRepisa(".5"))
        assertEquals("999999999", limpiarNumeroRepisa("999999999"))
    }

    @Test fun rechazaLaEdicionCompletaSiContieneCaracteresInvalidos() {
        for (texto in listOf("1234567Pelis", "1.2,3", "1.2.3", "1,2,3", " 12 ", "1 234", "+1", "-1", "1e2", ".")) {
            assertNull(texto, limpiarNumeroRepisa(texto))
        }
    }

    @Test fun rechazaValoresMayoresQueElMaximoInclusoConDecimales() {
        assertNull(limpiarNumeroRepisa("1000000000"))
        assertNull(limpiarNumeroRepisa("999999999,00000001"))
    }
}

package io.github.fogonapps.social.ui

import io.github.fogonapps.identidad.formatearMiles
import io.github.fogonapps.identidad.gruposNumeroFormateado
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatoMilesTest {
    @Test fun agrupaSegunIdiomaInclusoSiLaRegionEsDistinta() {
        for (idioma in listOf("es", "es-CO", "es-ES", "es-MX")) {
            assertEquals("1.000", formatearMiles("1000", Locale.forLanguageTag(idioma)))
            assertEquals("1.000.000", formatearMiles("1000000", Locale.forLanguageTag(idioma)))
        }
        for (idioma in listOf("en", "en-US", "en-GB")) {
            assertEquals("1,000", formatearMiles("1000", Locale.forLanguageTag(idioma)))
            assertEquals("1,000,000", formatearMiles("1000000", Locale.forLanguageTag(idioma)))
        }
        assertEquals("0", formatearMiles("0", Locale.US))
        assertEquals("999", formatearMiles("999", Locale.US))
    }

    @Test fun muestraEnterosDecimalesYCeroDeLasRepisas() {
        val espanol = Locale.forLanguageTag("es")
        assertEquals("1.234.567", formatearMiles("1234567.0", espanol))
        assertEquals("1,234,567", formatearMiles("1234567.0", Locale.US))
        assertEquals("12,5", formatearMiles("12.500", espanol))
        assertEquals("12.5", formatearMiles("12.500", Locale.US))
        assertEquals("0,00000042", formatearMiles("4.2E-7", espanol))
        assertEquals("0.00000042", formatearMiles("4.2E-7", Locale.US))
        assertEquals("0", formatearMiles("-0.0", espanol))
        assertEquals("0", formatearMiles("0.0", Locale.US))
    }

    @Test fun conservaLosSeparadoresJuntoAlGrupoAnteriorEnEspanolEIngles() {
        val espanol = Locale.forLanguageTag("es")
        assertEquals(listOf("1.", "234.", "567"), gruposNumeroFormateado(formatearMiles("1234567", espanol)))
        assertEquals(listOf("1,", "234,", "567"), gruposNumeroFormateado(formatearMiles("1234567", Locale.US)))
        assertEquals(listOf("12,", "5"), gruposNumeroFormateado(formatearMiles("12.5", espanol)))
        assertEquals(listOf("12.", "5"), gruposNumeroFormateado(formatearMiles("12.5", Locale.US)))
        assertEquals(listOf("0"), gruposNumeroFormateado(formatearMiles("0.0", espanol)))
        assertEquals(listOf("0"), gruposNumeroFormateado(formatearMiles("0.0", Locale.US)))
    }

    @Test fun permiteEnvolverFraccionesLargasSinPerderCifras() {
        val espanol = Locale.forLanguageTag("es")
        assertEquals(
            listOf("1.", "234,", "000", "000", "42"),
            gruposNumeroFormateado(formatearMiles("1234.00000042", espanol)),
        )
    }
}

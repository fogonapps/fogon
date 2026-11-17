package io.github.fogonapps.social.ui

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatoMilesTest {
    @Test fun agrupaSegunIdiomaInclusoSiLaRegionEsDistinta() {
        for (idioma in listOf("es", "es-CO", "es-ES", "es-MX")) {
            assertEquals("1.000", formatearMiles(1000, Locale.forLanguageTag(idioma)))
            assertEquals("1.000.000", formatearMiles(1000000, Locale.forLanguageTag(idioma)))
        }
        for (idioma in listOf("en", "en-US", "en-GB")) {
            assertEquals("1,000", formatearMiles(1000, Locale.forLanguageTag(idioma)))
            assertEquals("1,000,000", formatearMiles(1000000, Locale.forLanguageTag(idioma)))
        }
        assertEquals("0", formatearMiles(0, Locale.US))
        assertEquals("999", formatearMiles(999, Locale.US))
    }
}

package io.github.fogonapps.social.ui

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

class CampoChatTest {
    @Test fun loQueVuelveAlCampoDejaElCursorAlFinal() {
        val campo = alFinal("Qq hola")
        assertEquals("Qq hola", campo.text)
        assertEquals(TextRange(7), campo.selection)
        assertEquals(TextRange(0), alFinal("").selection)
    }

    @Test fun soloSeVaciaSiElContadorSubio() {
        assertEquals(true, seOlvidoDesde(vista = 0, actual = 1))
        assertEquals(false, seOlvidoDesde(vista = 2, actual = 2))
        // Android cerró la app: el contador volvió a 0 pero lo visto quedó guardado.
        assertEquals(false, seOlvidoDesde(vista = 2, actual = 0))
    }
}

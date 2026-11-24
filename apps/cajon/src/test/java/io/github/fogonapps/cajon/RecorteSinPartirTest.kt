package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.Logica
import org.junit.Assert.assertEquals
import org.junit.Test

class RecorteSinPartirTest {
    @Test fun cortaEnElTopeSiNoHayNadaQuePartir() {
        assertEquals("abcde", Logica.recortarSinPartir("abcdefgh", 5))
        assertEquals("abc", Logica.recortarSinPartir("abc", 5))
    }

    @Test fun noParteUnEmojiDeDosUnidades() {
        val texto = "a".repeat(4999) + "😀" + "b"
        val r = Logica.recortarSinPartir(texto, 5000)
        assertEquals("a".repeat(4999), r)
        assertEquals(4999, r.length)
    }

    @Test fun dejaElEmojiSiCabeEntero() {
        val texto = "a".repeat(4998) + "😀" + "b"
        assertEquals("a".repeat(4998) + "😀", Logica.recortarSinPartir(texto, 5000))
    }
}

package io.github.fogonapps.cajon

import io.github.fogonapps.social.Frases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrasesTest {
    @Test fun pesosDeLaComunidadYDeLaPersona() {
        assertEquals(1.0, Frases.peso(1.0, null, gusta = false, noGusta = false), 1e-9)          // nueva: neutra
        assertTrue(Frases.peso(1.0, Frases.Votos(40, 0), false, false) > 3.5)                    // le gusta a todos: casi 4
        assertTrue(Frases.peso(1.0, Frases.Votos(0, 40), false, false) > 0.25)                   // no desaparece
        assertTrue(Frases.peso(1.0, Frases.Votos(1, 0), false, false) < 1.4)                     // un voto no la dispara
        assertEquals(2.5, Frases.peso(1.0, null, gusta = true, noGusta = false), 1e-9)
        assertEquals(0.0, Frases.peso(1.0, Frases.Votos(99, 0), gusta = true, noGusta = true), 1e-9)
    }

    @Test fun elSorteoRespetaLosPesos() {
        val candidatas = mapOf("a" to 1.0, "b" to 1.0)
        val votos = mapOf("a" to Frases.Votos(100, 0), "b" to Frases.Votos(0, 100))
        val salidas = (0 until 2000L).map { Frases.elegir(candidatas, votos, emptySet(), emptySet(), it) }
        val a = salidas.count { it == "a" }
        assertTrue("a salió $a de 2000", a in 1800..1990)                                       // ~94 %: casi siempre, no siempre
        assertEquals(setOf("a"), (0 until 50L).map { Frases.elegir(candidatas, votos, emptySet(), setOf("b"), it) }.toSet())
        assertNull(Frases.elegir(candidatas, emptyMap(), emptySet(), setOf("a", "b"), 1))
        assertEquals(Frases.elegir(candidatas, votos, emptySet(), emptySet(), 7), Frases.elegir(candidatas, votos, emptySet(), emptySet(), 7))   // misma hora, misma frase
    }
}

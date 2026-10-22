package io.github.fogonapps.identidad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Que ningún estilo venga con letras que no se leen, en ninguna app (ver docs/IDENTIDAD.md → Apariencia). */
class AparienciaTest {
    private val paletas = mapOf("Forzudo" to Paleta.Forzudo, "Ahorrador" to Paleta.Ahorrador)

    @Test
    fun cadaEstiloSeLee() {
        for ((app, p) in paletas) for (e in Estilo.entries) {
            val f = fogonDe(p, Apariencia(estilo = e))
            val donde = "$app · $e"
            assertTrue("$donde: letras sobre el fondo", contraste(f.texto, f.fondo) >= 4.5)
            assertTrue("$donde: letras suaves sobre el fondo", contraste(f.textoSuave, f.fondo) >= 4.5)
            assertTrue("$donde: letras sobre el acento", contraste(f.paleta.sobreTinta, f.paleta.tinta) >= 4.5)
            assertTrue("$donde: el acento como texto", contraste(f.acentoTexto, f.fondo) >= 4.5)
            assertTrue("$donde: la píldora", contraste(f.sobrePildora, f.pildora) >= 4.5)
            assertTrue("$donde: la tarjeta destacada", contraste(f.sobreDestacada, androidx.compose.ui.graphics.lerp(f.destacada[0], f.destacada[1].copy(alpha = 1f), 0.5f)) >= 4.5)
        }
    }

    @Test
    fun loQueCambiaLaPersonaGanaYSeRestablece() {
        val rojo = androidx.compose.ui.graphics.Color(0xFFE5332A)
        val a = Apariencia(ajustes = mapOf(Estilo.NOCHE to AjustesEstilo(colores = mapOf(Ranura.FONDO to rojo), transparencia = 10)))
        assertEquals(rojo, a.color(Paleta.Forzudo, Ranura.FONDO))
        assertEquals(10, a.transparencia())
        // Otro estilo no se entera.
        assertEquals(Estilo.RISO.colorBase(Paleta.Forzudo, Ranura.FONDO), a.color(Paleta.Forzudo, Ranura.FONDO, Estilo.RISO))
        assertEquals(Estilo.RISO.transparenciaBase, a.transparencia(Estilo.RISO))
        assertTrue(Apariencia().rectas(Estilo.REBELDE))
    }
}

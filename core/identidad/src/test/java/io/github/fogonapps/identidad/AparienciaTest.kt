package io.github.fogonapps.identidad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Que ningún estilo venga con letras que no se leen, en ninguna app (ver docs/IDENTIDAD.md → Apariencia). */
class AparienciaTest {
    private val paletas = mapOf("Forzudo" to Paleta.Forzudo, "Ahorrador" to Paleta.Ahorrador, "Minga" to Paleta.Minga)

    @Test
    fun cadaEstiloSeLee() {
        for ((app, p) in paletas) for (e in Estilo.entries) {
            val f = fogonDe(p, Apariencia(estilo = e))
            val donde = "$app · $e"
            assertTrue("$donde: letras sobre el fondo", contraste(f.texto, f.fondo) >= 4.5)
            assertTrue("$donde: letras suaves sobre el fondo", contraste(f.textoSuave, f.fondo) >= 4.5)
            assertTrue("$donde: letras sobre la primera tinta", contraste(f.sobreTinta1, f.tinta1) >= 4.5)
            assertTrue("$donde: letras sobre las tarjetas", contraste(f.texto, f.superficie) >= 4.5)
            assertTrue("$donde: el acento como texto", contraste(f.acentoTexto, f.fondo) >= 4.5)
            assertTrue("$donde: la píldora", contraste(f.sobrePildora, f.pildora) >= 4.5)
            assertTrue("$donde: el número gigante", contraste(f.numero, f.fondo) >= 3.0)
        }
    }

    @Test
    fun loQueCambiaLaPersonaGanaYSeRestablece() {
        val rojo = androidx.compose.ui.graphics.Color(0xFFE5332A)
        val a = Apariencia(estilo = Estilo.NOCHE, ajustes = mapOf(Estilo.NOCHE to AjustesEstilo(colores = mapOf(Ranura.FONDO to rojo), transparencia = 10, desfase = 5f)))
        assertEquals(rojo, a.color(Paleta.Forzudo, Ranura.FONDO))
        assertEquals(10, a.transparencia())
        assertEquals(5f, a.desfase())
        // Otro estilo no se entera.
        assertEquals(Estilo.RISO.colorBase(Paleta.Forzudo, Ranura.FONDO), a.color(Paleta.Forzudo, Ranura.FONDO, Estilo.RISO))
        assertEquals(0, a.transparencia(Estilo.RISO))
        assertEquals(Estilo.RISO.desfaseBase, a.desfase(Estilo.RISO))
        assertTrue(Apariencia().rectas(Estilo.REBELDE))
    }

    @Test
    fun lasAppsVienenEnNeutroSinAdornos() {
        val f = fogonDe(Paleta.Forzudo, Apariencia())
        assertEquals(Estilo.NEUTRO, f.estilo)
        assertEquals(0f, f.desfase.value)
        assertEquals(Decoracion.NADA, f.decoracion)
        assertEquals(0f, f.grano)
        assertEquals(Sencilla, f.titulos)
        // Neutro es igual en todas las apps.
        assertEquals(f.tinta1, fogonDe(Paleta.Ahorrador, Apariencia()).tinta1)
    }

    @Test
    fun cambiarLaPrimeraTintaNoDejaNadaIlegible() {
        val amarillo = androidx.compose.ui.graphics.Color(0xFFFFC93C)
        val a = Apariencia(estilo = Estilo.RISO, ajustes = mapOf(Estilo.RISO to AjustesEstilo(colores = mapOf(Ranura.TINTA_1 to amarillo))))
        val f = fogonDe(Paleta.Forzudo, a)
        assertEquals(amarillo, f.numero)
        assertTrue(contraste(f.sobreTinta1, f.tinta1) >= 4.5)
        // El amarillo no se lee como texto sobre el papel crema: el acento pasa a ser el color de las letras.
        assertEquals(f.texto, f.acentoTexto)
    }
}

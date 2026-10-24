package io.github.fogonapps.identidad

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Que ningún tema venga con letras que no se leen, en ninguna app (ver docs/IDENTIDAD.md → Vidrio y puntos). */
class AparienciaTest {
    private val paletas = mapOf("Forzudo" to Paleta.Forzudo, "Ahorrador" to Paleta.Ahorrador, "Minga" to Paleta.Minga)

    /** El vidrio es transparente: lo que importa es cómo queda sobre el papel. */
    private fun Fogon.tarjeta() = superficie.compositeOver(fondo)

    private fun comprobar(f: Fogon, donde: String) {
        assertTrue("$donde: letras sobre el fondo", contraste(f.texto, f.fondo) >= 4.5)
        assertTrue("$donde: letras suaves sobre el fondo", contraste(f.textoSuave, f.fondo) >= 4.5)
        assertTrue("$donde: letras sobre las tarjetas", contraste(f.texto, f.tarjeta()) >= 4.5)
        assertTrue("$donde: letras suaves sobre las tarjetas", contraste(f.textoSuave, f.tarjeta()) >= 4.5)
        assertTrue("$donde: letras sobre la primera tinta", contraste(f.sobreTinta1, f.tinta1) >= 4.5)
        assertTrue("$donde: el acento como texto", contraste(f.acentoTexto, f.fondo) >= 4.5)
        assertTrue("$donde: el número grande", contraste(f.numero, f.fondo) >= 3.0)
    }

    @Test
    fun cadaTemaSeLee() {
        for ((app, p) in paletas) for (e in Estilo.entries) comprobar(fogonDe(p, Apariencia(estilo = e)), "$app · $e")
    }

    @Test
    fun lasAppsVienenEnGrafito() {
        val f = fogonDe(Paleta.Forzudo, Apariencia())
        assertEquals(Estilo.GRAFITO, f.estilo)
        assertTrue(f.oscuro)
        assertEquals(Bricolage, f.titulos)
        assertEquals(Puntos, f.puntos)
        // Grafito es igual en todas las apps.
        assertEquals(f.fondo, fogonDe(Paleta.Ahorrador, Apariencia()).fondo)
        assertEquals(f.tinta1, fogonDe(Paleta.Ahorrador, Apariencia()).tinta1)
    }

    @Test
    fun loQueCambiaLaPersonaGanaYSeRestablece() {
        val rojo = Color(0xFFE5332A)
        val a = Apariencia(estilo = Estilo.MARCA, ajustes = mapOf(Estilo.MARCA to AjustesEstilo(colores = mapOf(Ranura.FONDO to rojo), transparencia = 40)))
        assertEquals(rojo, a.color(Paleta.Forzudo, Ranura.FONDO))
        assertEquals(40, a.transparencia())
        // Otro tema no se entera.
        assertEquals(Estilo.CLARO.colorBase(Paleta.Forzudo, Ranura.FONDO), a.color(Paleta.Forzudo, Ranura.FONDO, Estilo.CLARO))
        assertEquals(0, a.transparencia(Estilo.CLARO))
    }

    @Test
    fun cambiarLaPrimeraTintaNoDejaNadaIlegible() {
        val gris = Color(0xFF2A2A2E)
        val a = Apariencia(estilo = Estilo.GRAFITO, ajustes = mapOf(Estilo.GRAFITO to AjustesEstilo(colores = mapOf(Ranura.TINTA_1 to gris))))
        val f = fogonDe(Paleta.Forzudo, a)
        assertEquals(gris, f.tinta1)
        assertTrue(contraste(f.sobreTinta1, f.tinta1) >= 4.5)
        // El gris no se lee sobre el papel casi negro: el número y el acento pasan al color de las letras.
        assertEquals(f.texto, f.numero)
        assertEquals(f.texto, f.acentoTexto)
    }

    /** Cualquier papel y cualquier tinta que elija la persona, con cualquier vidrio: las letras se ajustan solas. */
    @Test
    fun cualquierColorSeLee() {
        val tonos = (0 until 360 step 30).flatMap { h -> listOf(0.15f, 0.35f, 0.5f, 0.65f, 0.9f).map { v -> Color.hsv(h.toFloat(), 0.6f, v) } } +
            listOf(0f, 0.2f, 0.45f, 0.5f, 0.55f, 0.8f, 1f).map { Color(it, it, it) }
        for (e in Estilo.entries) for (c in tonos) for (vidrio in listOf(0, 100)) {
            val a = Apariencia(estilo = e, ajustes = mapOf(e to AjustesEstilo(colores = mapOf(Ranura.FONDO to c, Ranura.TINTA_1 to c), transparencia = vidrio)))
            comprobar(fogonDe(Paleta.Forzudo, a), "$e · papel y tinta ${hex(c)} · vidrio $vidrio")
        }
    }
}

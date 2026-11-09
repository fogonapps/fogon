package io.github.fogonapps.identidad

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.*
import org.junit.Test

class PersonalizacionTest {
    @Test fun pincelPorDefectoConservaParametros() {
        listOf(Paleta.Forzudo, Paleta.Cajon, Paleta.Cucha, Paleta.Ahorrador).forEach { p ->
            Estilo.entries.forEach { e ->
                val f = fogonDe(p, Apariencia(estilo = e))
                assertEquals(PincelLuz(listOf(f.brillo, f.brillo.copy(alpha = 0f)), 1.05f, 0.5f, -0.15f), parametrosLuz(f))
            }
        }
    }
    @Test fun limitaLaLuzParaLeer() {
        val c = Apariencia().colores(Paleta.Cajon)
        val luz = LuzPropia(Color.White).legible(c)
        assertTrue(luz.intensidad < 100)
        val fondo = luz.color.copy(alpha = luz.intensidad / 100f).compositeOver(c.fondo)
        assertTrue(contraste(c.texto, fondo) >= 4.5)
        assertTrue(contraste(c.textoSuave, fondo) >= 4.5)
    }
    @Test fun guardadasTienenLimiteYConservanLuz() {
        var a = Apariencia(ajustes = mapOf(Estilo.GRAFITO to AjustesEstilo(luz = LuzPropia(Color.Red, Color.Blue, 23, 145))))
        repeat(15) { a = a.guardarTintas("Tinta $it", Paleta.Cajon) }
        assertEquals(12, a.guardadas.size)
        val aplicada = Apariencia(estilo = Estilo.CLARO).aplicarTintas(a.guardadas.first())
        assertEquals(a.guardadas.first().ajuste, aplicada.de())
        assertEquals(11, a.borrarTintas(0).guardadas.size)
        assertEquals("Tinta 1", a.borrarTintas(0).guardadas.first().nombre)
        assertEquals(a.guardadas, leerGuardadas(escribirGuardadas(a.guardadas)))
        assertEquals(emptyList<TintasGuardadas>(), leerGuardadas(null))
    }
    @Test fun sinAdornosYContrasteNoDibujanLuz() {
        listOf(Apariencia(estilo = Estilo.CONTRASTE, ajustes = mapOf(Estilo.CONTRASTE to AjustesEstilo(luz = LuzPropia(Color.White)))),
            Apariencia(ajustes = mapOf(Estilo.GRAFITO to AjustesEstilo(adorno = Decoracion.NADA, luz = LuzPropia(Color.White))))).forEach {
            val f = fogonDe(Paleta.Cajon, it)
            assertNull(f.luz); assertEquals(0f, f.brillo.alpha)
        }
    }
}

package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.ui.estadoDeNota
import io.github.fogonapps.cajon.ui.notaDeProgreso
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class AccesibilidadNotaTest {
    private fun cadena(carpeta: String, nombre: String): String {
        val documento = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/$carpeta/strings.xml"))
        val cadenas = documento.getElementsByTagName("string")
        return (0 until cadenas.length).map { cadenas.item(it) as Element }
            .first { it.getAttribute("name") == nombre }.textContent
    }

    @Test fun estadoEnAmbosIdiomasConCadenasReales() {
        for ((carpeta, idioma, sinNota) in listOf(
            Triple("values-es", Locale.forLanguageTag("es"), "Sin calificar"),
            Triple("values", Locale.ENGLISH, "Not rated"),
        )) {
            val sinCalificar = cadena(carpeta, "nota_sin_calificar")
            val formato = cadena(carpeta, "nota_estado_estrellas")
            assertEquals(sinNota, estadoDeNota(0, idioma, sinCalificar, formato))
            for (nota in 1..10) {
                val numero = if (nota % 2 == 0) "${nota / 2}" else "${nota / 2}${if (idioma.language == "es") "," else "."}5"
                val esperado = if (idioma.language == "es") "$numero de 5 estrellas" else "$numero of 5 stars"
                assertEquals(esperado, estadoDeNota(nota, idioma, sinCalificar, formato))
            }
        }
    }

    @Test fun progresoRedondeaYRespetaLimites() {
        for (nota in 0..10) assertEquals(nota, notaDeProgreso(nota.toFloat()))
        assertEquals(3, notaDeProgreso(3.49f))
        assertEquals(4, notaDeProgreso(3.5f))
        assertEquals(0, notaDeProgreso(-2f))
        assertEquals(10, notaDeProgreso(12f))
        assertEquals(0, notaDeProgreso(Float.NaN))
        assertEquals(0, notaDeProgreso(Float.NEGATIVE_INFINITY))
        assertEquals(10, notaDeProgreso(Float.POSITIVE_INFINITY))
    }
}

package io.github.fogonapps.forzudo

import io.github.fogonapps.forzudo.datos.Biblioteca
import io.github.fogonapps.forzudo.datos.Vocabulario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Que ningún texto quede en un solo idioma: cada strings.xml en inglés (values) tiene su par en
 * español (values-es) con los mismos datos variables (%1$s, %d…), y los datos fijos están traducidos.
 * Corre desde apps/forzudo (como las demás pruebas de Gradle).
 */
class TraduccionTest {
    private val carpetas = listOf("src/main/res", "../../core/identidad/src/main/res")

    /** nombre → lista de textos (uno para string, uno por cantidad o elemento para plurals y arreglos). */
    private fun leer(f: File): Map<String, List<String>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
        val raiz = doc.documentElement
        val out = mutableMapOf<String, List<String>>()
        val hijos = raiz.childNodes
        for (i in 0 until hijos.length) {
            val n = hijos.item(i) as? org.w3c.dom.Element ?: continue
            val clave = n.tagName + ":" + n.getAttribute("name")
            out[clave] = if (n.tagName == "string") listOf(n.textContent)
            else (0 until n.getElementsByTagName("item").length).map { n.getElementsByTagName("item").item(it).textContent }
        }
        return out
    }

    private fun marcas(s: String) = Regex("%(\\d+\\$)?[sd]").findAll(s).map { it.value }.toSortedSet()

    @Test
    fun mismosTextosEnLosDosIdiomas() {
        for (c in carpetas) {
            val en = leer(File("$c/values/strings.xml")).filterKeys { it != "string:app_name" }
            val es = leer(File("$c/values-es/strings.xml"))
            assertEquals("textos distintos entre idiomas en $c", en.keys, es.keys)
            for ((k, v) in en) {
                val w = es.getValue(k)
                if (k.startsWith("string-array:")) assertEquals("$k: distinta cantidad de elementos", v.size, w.size)
                v.zip(w).forEach { (a, b) -> assertEquals("$k: datos variables distintos", marcas(a), marcas(b)) }
            }
        }
    }

    @Test
    fun datosFijosTraducidos() {
        val leer = { n: String -> File("src/main/assets/datos/$n").readText() }
        val es = Biblioteca.cargar("es", leer)
        val en = Biblioteca.cargar("en", leer)
        assertEquals(es.ejercicios.map { it.id }, en.ejercicios.map { it.id })
        es.ejercicios.zip(en.ejercicios).forEach { (a, b) ->
            assertNotEquals("${a.id} sin técnica en inglés", a.cue, b.cue)
            assertTrue("${a.id} sin nombre", b.n.isNotBlank())
        }
        assertTrue(en.niveles.first().n == "Iron")
        assertTrue(en.musculos.none { m -> es.musculos.any { it.n == m.n && it.id != "abdomen" } })
        // La lógica sigue en español por defecto, igual que la web.
        assertEquals("Entreno", Vocabulario.ES.entreno)
    }
}

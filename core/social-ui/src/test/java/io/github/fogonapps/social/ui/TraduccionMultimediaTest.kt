package io.github.fogonapps.social.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class TraduccionMultimediaTest {
    private fun recursos(archivo: File): Map<String, String> {
        val nodos = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(archivo).documentElement.childNodes
        return (0 until nodos.length).mapNotNull { nodos.item(it) as? Element }
            .associate { it.getAttribute("name") to it.textContent }
    }
    @Test fun multimediaConservaLiteralmenteLosTextosDeCajonEnAmbosIdiomas() {
        val raiz = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "settings.gradle.kts").exists() }
        val nombres = mutableListOf<Set<String>>()
        for (idioma in listOf("values", "values-es")) {
            val comunes = recursos(File(raiz, "core/social-ui/src/main/res/$idioma/strings.xml"))
            val cajon = recursos(File(raiz, "apps/cajon/src/main/res/$idioma/strings.xml"))
            assertTrue(comunes.isNotEmpty())
            // Los textos que El Cajón también tiene deben decir lo mismo; los nuevos solo viven en lo común.
            comunes.forEach { (clave, texto) -> cajon[clave.removePrefix("social_")]?.let { assertEquals(clave, it, texto) } }
            nombres += comunes.keys
        }
        assertEquals(nombres[0], nombres[1])
    }
}

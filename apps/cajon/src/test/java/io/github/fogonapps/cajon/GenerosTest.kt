package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.Generos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Que cada género que el buscador ofrece para tocar (generos_cine, generos_anime y generos_libros, en los dos
 * idiomas) sepa cómo buscarse afuera (ver [Generos]). Corre desde apps/cajon.
 */
class GenerosTest {
    private fun arreglo(carpeta: String, nombre: String): List<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$carpeta/strings.xml"))
        val lista = doc.getElementsByTagName("string-array")
        for (i in 0 until lista.length) {
            val n = lista.item(i) as org.w3c.dom.Element
            if (n.getAttribute("name") == nombre) return (0 until n.getElementsByTagName("item").length).map { n.getElementsByTagName("item").item(it).textContent }
        }
        error("no está $nombre en $carpeta")
    }

    @Test
    fun cadaGeneroDelBuscadorSeBuscaAfuera() {
        for (carpeta in listOf("values", "values-es")) {
            (arreglo(carpeta, "generos_cine") + arreglo(carpeta, "generos_anime")).forEach { assertNotNull("$it ($carpeta) sin TMDB", Generos.cine(it)) }
            arreglo(carpeta, "generos_libros").forEach { assertNotNull("$it ($carpeta) sin tema", Generos.libro(it)) }
        }
    }

    @Test
    fun cadaGeneroTieneConQueBuscar() {
        Generos.CINE.forEach { g -> assertTrue(g.nombres.first(), (g.peliculas ?: g.clavePeliculas) != null && (g.series ?: g.claveSeries) != null) }
        assertTrue(Generos.CINE.filter { it.anime }.all { it.peliculas == null && it.clavePeliculas != null })
    }

    @Test
    fun reconoceSinTildesNiMayusculas() {
        assertEquals("878", Generos.cine("ciencia ficcion")?.peliculas)
        assertEquals("207826", Generos.cine("SHŌNEN")?.claveSeries)
        assertEquals("315058", Generos.cine("Terror")?.claveSeries)
        assertEquals("mystery", Generos.libro("policial")?.tema)
        assertNull(Generos.cine("El señor de los anillos"))
    }
}

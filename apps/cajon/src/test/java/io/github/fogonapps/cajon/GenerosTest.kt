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

    @Test
    fun traduceGenerosYTemasDeLibrosAlIdiomaActivo() {
        assertEquals("Fantasía", Generos.etiquetaLibro("Fantasy", "es"))
        assertEquals("Fantasy", Generos.etiquetaLibro("Fantasía", "en"))
        assertEquals("Invisibilidad", Generos.etiquetaLibro("invisibility", "es"))
        assertEquals("Batalla de los Cinco Ejércitos", Generos.etiquetaLibro("Battle of Five Armies", "es"))
        assertEquals("Arkenstone", Generos.etiquetaLibro("Arkenstone", "es"))
    }
    @Test
    fun traduceTemasFrecuentesYVariantesSinModificarElOriginal() {
        val temas = listOf(
            Triple("FANTASY   FICTION", "Ficción fantástica", "Fantasy fiction"),
            Triple("Wizards, fiction", "Magos", "Wizards"),
            Triple("Wizards -- Fiction", "Magos", "Wizards"),
            Triple("Good and evil -- Juvenile fiction", "El bien y el mal", "Good and evil"),
            Triple("Quests (Expeditions)", "Misiones y expediciones", "Quests (expeditions)"),
            Triple("  FRIENDSHIP  ", "Amistad", "Friendship"),
            Triple("Climate change", "Cambio climático", "Climate change"),
            Triple("short stories", "Cuentos", "Short stories"),
            Triple("graphic novels", "Novelas gráficas", "Graphic novels"),
        )
        temas.forEach { (original, es, en) ->
            assertEquals(original, es, Generos.etiquetaLibro(original, "es"))
            assertEquals(original, en, Generos.etiquetaLibro(original, "en"))
        }
        assertEquals("Ciencia ficción", Generos.etiquetaLibro("CIENCIA FICCION", "es"))
        listOf("Arkenstone", "Bilbo Baggins", "Bilbo Baggins, fiction", "Unknown magic").forEach { original ->
            listOf("es", "en").forEach { idioma ->
                assertEquals(original, Generos.etiquetaLibro(original, idioma))
            }
        }
        assertNull(Generos.libro("Wizards, fiction"))
        assertEquals("science fiction", Generos.libro("Ciencia ficción")?.tema)
    }


}

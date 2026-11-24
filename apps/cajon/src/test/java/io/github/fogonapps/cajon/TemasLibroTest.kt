package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.Generos
import io.github.fogonapps.cajon.datos.OpenLibrary
import io.github.fogonapps.cajon.datos.TEMAS_TRADUCIDOS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemasLibroTest {
    private fun queda(tema: String) = OpenLibrary.temas(listOf(tema)) == listOf(tema)

    @Test fun quitaLosTemasDeCatalogoYLasAclaracionesDeLugaresYPersonajes() {
        val dune = listOf(
            "Dune (Imaginary place)", "Fiction", "New York Times reviewed", "Science fiction", "Accessible book",
            "Protected DAISY", "In library", "nyt:mass_market_paperback=1965-08-01", "Paul Atreides (Fictitious character)",
            "Ecology", "Fiction, science fiction, general", "Award winner",
        )
        assertEquals(listOf("Fiction", "Science fiction", "Ecology"), OpenLibrary.temas(dune))
    }

    @Test fun ningunTemaTraducidoSePierdeSalvoLanguage() {
        assertEquals(269, TEMAS_TRADUCIDOS.size)
        val perdidos = (TEMAS_TRADUCIDOS.values.map { it.second } + TEMAS_TRADUCIDOS.keys).filterNot { queda(it) }
        assertEquals(listOf("Language", "language"), perdidos)
        assertTrue(queda("Juvenile fiction"))
        assertTrue(queda("Juvenile literature"))
        assertTrue(queda("Magic realism (Literature)"))
    }

    @Test fun quitaTraduccionesYAclaracionesEnCualquierIdioma() {
        for (t in listOf("Translations into French", "Translation into Spanish", "Macondo (Lugar imaginario)", "Hobbits (Personnages fictifs)",
            "Paris (Lieu imaginaire)", "Middle Earth (Tolkien, J.R.R.)", "open_syllabus_project", "Libraries", "Languages", "Awards",
            "Staff picks", "Juvenile fantasy fiction")) {
            assertTrue(t, OpenLibrary.temas(listOf(t)).isEmpty())
        }
    }

    @Test fun comparaPorPalabraEnteraNoPorPedazos() {
        for (t in listOf("Quests (expeditions)", "Imaginary voyages", "Imaginary places", "Loss (psychology)", "Identity (psychology)",
            "Expeditions", "Scientific expeditions", "Microscopy", "Spectroscopy", "Anything goes", "Imaginary wars and battles")) {
            assertTrue(t, queda(t))
        }
    }

    @Test fun hastaCuatroSinRepetir() {
        assertEquals(listOf("Fantasy", "Magic", "Dragons", "Wizards"),
            OpenLibrary.temas(listOf("Fantasy", "fantasy", "Magic", "Dragons", "Wizards", "Schools")))
    }

    @Test fun duneSinRepetidosYConLaLiteraturaTraducida() {
        val dune = listOf("Dune (Imaginary place)", "Fiction", "Fiction, science fiction, general", "Dune (imaginary place), fiction",
            "New York Times reviewed", "Science fiction", "Science-fiction", "American literature", "New York Times bestseller",
            "Hugo Award Winner", "American Science fiction", "Long Now Manual for Civilization", "Ecology", "Fantasy fiction", "Large type books")
        val temas = OpenLibrary.temas(dune)
        assertEquals(listOf("Fiction", "Science fiction", "American literature", "Ecology"), temas)
        assertEquals(listOf("Novela", "Ciencia ficción", "Literatura estadounidense", "Ecología"), temas.map { Generos.etiquetaLibro(it, "es") })
    }

    @Test fun cienAnosDeSoledadConRealismoMagicoYSinRepetirNovela() {
        val cien = listOf("Spanish language books", "Fiction", "Social conditions", "Macondo (Imaginary place)", "Latinamerican literature",
            "Condiciones sociales", "Novela", "Literatura épica", "Epic literature", "Macondo (Lugar imaginario)", "Ficción",
            "Spanish language materials", "Ficcion", "Translations into Arabic", "Latin American fiction", "Spanish fiction",
            "Colombian fiction", "Cliffs Notes", "Magic realism (Literature)", "Large type books", "Dorf", "Familie",
            "Conditions sociales", "Romance literature", "Fantasy", "Literature", "Magic Realism", "Novel")
        val temas = OpenLibrary.temas(cien).map { Generos.etiquetaLibro(it, "es") }
        assertEquals(listOf("Novela", "Literatura latinoamericana", "Realismo mágico", "Fantasía"), temas)
        assertEquals(listOf("Fiction", "Latin American literature", "Magic realism", "Fantasy"),
            OpenLibrary.temas(cien).map { Generos.etiquetaLibro(it, "en") })
    }

    @Test fun laLiteraturaDeUnPaisSeTraduceYLaDesconocidaSeDescarta() {
        assertEquals("Literatura latinoamericana", Generos.etiquetaLibro("Latin American literature", "es"))
        assertEquals("Literatura colombiana", Generos.etiquetaLibro("Colombian literature", "es"))
        assertTrue(OpenLibrary.temas(listOf("Romance literature", "Epic literature")).isEmpty())
        assertTrue(queda("Dystopias (Literature)"))
    }
}

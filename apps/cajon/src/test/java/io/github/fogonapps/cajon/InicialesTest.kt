package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.Iniciales
import io.github.fogonapps.cajon.datos.ObraInicial
import io.github.fogonapps.cajon.datos.Tipos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class InicialesTest {
    private val todas = Iniciales.leer(File("src/main/assets/datos/gustos.json").readText())
    private val porId = todas.associateBy { it.id }

    @Test fun titulosSiguenElIdiomaSinCambiarFuentesNiIds() {
        for ((id, ingles) in listOf("eterno_resplandor" to "Eternal Sunshine of the Spotless Mind", "the_office" to "The Office")) {
            val original = porId.getValue(id)
            val traducida = Iniciales.enIdioma(original, "en")
            assertEquals(ingles, traducida.titulo)
            assertEquals(original.fuente, traducida.fuente)
            assertEquals(original.id, traducida.id)
            assertEquals(original, Iniciales.enIdioma(original, "es"))
        }
        val artista = todas.first { it.tipo == CajonViewModel.TIPO_ARTISTA }
        assertEquals(artista, Iniciales.enIdioma(artista, "en"))
    }

    @Test fun cacheDeGustosSoloValeParaElIdiomaCargado() {
        val carga = CajonViewModel.ParaElegir(listo = true, idioma = "es")
        assertTrue(carga.vigente("es"))
        assertTrue(!carga.vigente("en"))
        assertTrue(!CajonViewModel.ParaElegir(idioma = "en").vigente("en"))
    }

    @Test fun laListaEstaBienArmada() {
        assertTrue(todas.size > 150)
        assertEquals(todas.size, porId.size)                                                   // ids sin repetir
        todas.forEach { o ->
            assertTrue(o.id, o.titulo.isNotBlank() && o.parecidos.isNotEmpty())
            assertTrue(o.id, o.parecidos.all { it in porId && it != o.id })                    // las parecidas existen
            assertTrue(o.id, o.fuente.matches(Regex("tmdb:(movie|tv):[0-9]+|ol:OL[0-9]+W|mb:artist:[0-9a-f-]{36}")))
            assertTrue(o.id, o.imagen.isEmpty() || o.imagen.startsWith("https://"))
        }
        listOf(Tipos.PELICULA, Tipos.SERIE, Tipos.ANIME, Tipos.LIBRO, CajonViewModel.TIPO_ARTISTA).forEach { t -> assertTrue(t, todas.count { it.tipo == t } >= 15) }
    }

    @Test fun alElegirSalenParecidasAlLado() {
        val fila = listOf("interstellar", "la_la_land", "coco")
        val nueva = Iniciales.alElegir(fila, "la_la_land", porId)
        assertEquals(listOf("interstellar", "la_la_land"), nueva.take(2))
        assertEquals("eterno_resplandor", nueva[2])                                            // la primera parecida, justo al lado
        assertEquals("coco", nueva.last())                                                     // lo demás se corre a la derecha
        val otraVez = Iniciales.alElegir(nueva, "la_la_land", porId)                            // elegirla de nuevo trae las siguientes
        assertEquals(otraVez.size, otraVez.toSet().size)                                       // sin repetir
    }

    @Test fun recargarDejaLasElegidas() {
        val fila = Iniciales.semilla(todas.filter { it.tipo == Tipos.PELICULA }, 8, Random(1))
        val otra = Iniciales.recargar(fila, setOf(fila[2]), todas.filter { it.tipo == Tipos.PELICULA }, 8, Random(2))
        assertEquals(fila[2], otra.first())
        assertTrue(otra.drop(1).none { it in fila })
    }

    @Test fun semillaNoRompeElOrdenamientoConMuchasObras() {
        // Con más de 32 obras, un azar sorteado en cada comparación hacía fallar TimSort al abrir el tutorial.
        val muchas = (1..200).map { ObraInicial("o$it", Tipos.PELICULA, "Obra $it", parecidos = listOf("o${(it % 7) + 1}")) }
        repeat(200) { s ->
            val fila = Iniciales.semilla(muchas, 8, Random(s))
            assertEquals(8, fila.size)
            assertEquals(8, fila.toSet().size)
        }
    }
}

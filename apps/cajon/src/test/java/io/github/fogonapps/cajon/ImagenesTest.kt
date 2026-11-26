package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import io.github.fogonapps.cajon.datos.Imagenes.Lugar
import org.junit.Assert.*
import org.junit.Test

class ImagenesTest {
    @Test fun tamanosPorFuenteYLugar() {
        assertEquals(listOf("w342", "w780", "original"), Lugar.entries.map { Imagenes.tamano("tmdb", it) })
        assertEquals(listOf("L", "L", "L"), Lugar.entries.map { Imagenes.tamano("ol", it) })
        assertEquals(listOf("500", "1200", "1200"), Lugar.entries.map { Imagenes.tamano("caa", it) })
        assertEquals(listOf("w185", "w342", "w780"), Lugar.entries.map { Imagenes.tamano("tmdb", it, true) })
        assertEquals(listOf("M", "L", "L"), Lugar.entries.map { Imagenes.tamano("ol", it, true) })
        assertEquals(listOf("250", "500", "500"), Lugar.entries.map { Imagenes.tamano("caa", it, true) })
        assertEquals("https://coverartarchive.org/release/x/front-1200", Imagenes.url("https://coverartarchive.org/release/x/front-500", Lugar.FICHA))
        assertEquals("https://covers.openlibrary.org/b/id/3-L.jpg", Imagenes.url("https://covers.openlibrary.org/b/id/3-M.jpg", Lugar.FICHA))
        assertEquals("/local/foto.jpg", Imagenes.url("/local/foto.jpg", Lugar.LISTA))
    }
    private fun ed(id: String, anio: Int?, idioma: String, portada: String = id) = EdicionLibro(id, portada, anio = anio, idiomas = listOf(idioma))
    @Test fun edicionPorIdiomaYAnioConPortada() {
        val lista = listOf(ed("vieja", 1990, "spa"), ed("nueva", 2020, "spa"), ed("ingles", 2024, "eng"), ed("sin", 2025, "spa", ""))
        assertEquals("nueva", Imagenes.edicion(lista, "es")?.id)
        assertEquals("ingles", Imagenes.edicion(lista, "fr")?.id)
        assertNull(Imagenes.edicion(listOf(ed("frances", 2026, "fre")), "es"))
        assertNull(Imagenes.edicion(emptyList(), "es"))
        assertEquals("fecha", Imagenes.edicion(listOf(ed("sinFecha", null, "spa"), ed("fecha", 2000, "spa")), "es")?.id)
    }
    @Test fun prioridadYBloqueo() {
        val o = Obra("1", "libro", "Libro", portada = "propia", portadaPropia = true, imagen = ImagenElegida("local", "propia"))
        val c = PortadasComunidad("comunidad")
        assertEquals("propia", Imagenes.elegir(o, c, "recomendada"))
        assertEquals("comunidad", Imagenes.elegir(o.copy(portadaPropia = false), c, "recomendada"))
        assertEquals("recomendada", Imagenes.elegir(o.copy(portadaPropia = false), PortadasComunidad(), "recomendada"))
        assertEquals("recomendada", Imagenes.elegir(o, c.copy(bloqueadas = listOf("propia", "comunidad")), "recomendada"))
        assertEquals("", Imagenes.elegir(o, c.copy(bloqueadas = listOf("propia", "comunidad", "recomendada")), "recomendada"))
    }
    @Test fun bloqueadasIncluyenOtrosTamanos() {
        val a = "https://image.tmdb.org/t/p/w342/a.jpg"
        val b = "https://image.tmdb.org/t/p/w780/a.jpg"
        assertEquals(listOf("otra"), Imagenes.filtrar(listOf(a, b, "otra"), listOf(b)))
        assertFalse(Imagenes.autorizada("https://image.tmdb.org.evil.invalid/a"))
        assertFalse(Imagenes.autorizada("http://image.tmdb.org/a"))
        assertTrue(Imagenes.autorizada(b))
    }
    @Test fun migracionIdempotenteConservaEleccion() {
        val o = Obra("1", "libro", "Libro", portada = "https://covers.openlibrary.org/b/id/3-L.jpg", portadaPropia = true, edicion = "OL1M", traida = 7)
        val m = Imagenes.migrar(o)
        assertEquals("ol", m.imagen?.fuente)
        assertEquals("OL1M", m.imagen?.edicion)
        assertEquals(7L, m.imagen?.cuando)
        assertEquals(m, Imagenes.migrar(m))
        assertEquals(o.portada, m.portada)
        assertTrue(m.portadaPropia)
        assertNull(Imagenes.migrar(o.copy(portada = "")).imagen)
        val propia = ImagenElegida("wikimedia", "foto", "Autor", "CC BY-SA")
        assertEquals(propia, Imagenes.migrar(o.copy(imagen = propia)).imagen)
    }

    @Test fun caratulaDeAlbumSegunLaRespuesta() {
        // Cover Art Archive: 307 a archive.org si alguna edición tiene frente; 404 si ninguna.
        assertEquals(true, Imagenes.hayCaratula(307))
        assertEquals(true, Imagenes.hayCaratula(200))
        assertEquals(false, Imagenes.hayCaratula(404))
        assertEquals(false, Imagenes.hayCaratula(400))
        assertNull(Imagenes.hayCaratula(503))
    }

    @Test fun albumesSinCaratulaQuedanVaciosYAlFinal() {
        fun a(id: String) = Encontrado(Tipos.ALBUM, id, null, "", "https://coverartarchive.org/release-group/$id/front-500", "mb:rg:$id")
        val l = Imagenes.conCaratulas(listOf(a("1"), a("2"), a("3"), a("4")), listOf(false, true, null, false))
        assertEquals(listOf("2", "3", "1", "4"), l.map { it.titulo })
        assertEquals(listOf(false, false, true, true), l.map { it.portada.isBlank() })
        // Si no se pudo saber, la carátula se conserva.
        assertEquals("https://coverartarchive.org/release-group/3/front-500", l[1].portada)
        assertEquals(listOf("1"), Imagenes.conCaratulas(listOf(a("1")), emptyList()).map { it.titulo })
    }

    @Test fun imagenVaciaDeOpenLibraryYReintentos() {
        assertTrue(Imagenes.vaciaDeOpenLibrary("covers.openlibrary.org", "image/gif"))
        assertTrue(Imagenes.vaciaDeOpenLibrary("covers.openlibrary.org", "Image/GIF; charset=binary"))
        assertFalse(Imagenes.vaciaDeOpenLibrary("covers.openlibrary.org", "image/jpeg"))
        assertFalse(Imagenes.vaciaDeOpenLibrary("covers.openlibrary.org", null))
        assertFalse(Imagenes.vaciaDeOpenLibrary("coverartarchive.org", "image/gif"))
        assertTrue(listOf(408, 429, 500, 502, 503, 504).all(Imagenes::reintentable))
        assertFalse(listOf(200, 304, 400, 404).any(Imagenes::reintentable))
    }
}

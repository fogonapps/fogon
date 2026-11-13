package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import org.junit.Assert.*
import org.junit.Test

class GoodreadsTest {
    private var n = 0
    private val id = { p: String -> p + n++ }
    private val cabecera = "Book Id,Title,Author,Author l-f,Additional Authors,ISBN,ISBN13,My Rating,Average Rating,Publisher,Binding,Number of Pages,Year Published,Original Publication Year,Date Read,Date Added,Bookshelves,Bookshelves with positions,Exclusive Shelf,My Review,Spoiler,Private Notes,Read Count,Owned Copies\r\n"
    private fun fila(estante: String, nota: String, titulo: String = "El niño, la niña") = filaCsv(
        "1", titulo, "Ángel Muñoz", "Muñoz, Ángel", "Ana Peña", "=\"1234567890\"", "=\"9781234567890\"", nota, "3.4", "Editorial", "Paperback", "321", "2020", "1990", "2024/02/03", "2024/01/01", "favoritos, español", "favoritos (#1)", estante, "Dijo \"sí\", mañana\nOtra línea<br/>Fin", "", "", "1", "0")

    @Test fun formatoRealEstadosNotasYResena() {
        for ((estante, estado) in listOf("to-read" to Estados.QUIERO, "currently-reading" to Estados.EN_CURSO, "read" to Estados.TERMINADO)) {
            for (nota in 0..5) {
                val o = Importar.goodreads("\uFEFF" + cabecera + fila(estante, "$nota"), id, 7)!!.obras.single()
                assertEquals(estado, o.estado); assertEquals(nota * 2, o.nota)
                assertEquals("El niño, la niña", o.titulo)
                assertEquals("Ángel Muñoz, Ana Peña", o.creador)
                assertEquals("2024-02-03", o.terminada)
                assertEquals(321, o.paginas); assertEquals(1990, o.anio)
                assertEquals(listOf("favoritos", "español"), o.etiquetas)
                assertEquals("Dijo \"sí\", mañana\nOtra línea\nFin", o.resena)
            }
        }
        assertEquals(0, Importar.goodreads(cabecera + fila("read", ""), id, 0)!!.obras.single().nota)
    }

    @Test fun reimportarNoDuplicaNiResenasNiRegistros() {
        fun leer() = Importar.goodreads(cabecera + fila("read", "5"), id, 0)!!.obras
        val primera = Importar.sumar(Estado(), leer(), 1).first
        val segunda = Importar.sumar(primera, leer(), 2)
        assertEquals(0, segunda.second)
        assertEquals(primera.obras, segunda.first.obras.map { it.copy(cambio = 0) })
        assertEquals(primera.resenas, segunda.first.resenas)
    }

    @Test fun rechazaVaciosYOtrosServicios() {
        assertNull(Importar.goodreads("", id, 0))
        assertNull(Importar.goodreads("Name,Year,Letterboxd URI\nNiño,2020,url", id, 0))
        assertTrue(Importar.goodreads(cabecera + fila("read", "5", ""), id, 0)!!.obras.isEmpty())
    }

    @Test(timeout = 10000) fun cincoMilFilas() {
        val csv = cabecera + (1..5000).joinToString("") { fila("read", "4", "Libro $it") }
        assertEquals(5000, Importar.goodreads(csv, id, 0)!!.obras.size)
    }
}

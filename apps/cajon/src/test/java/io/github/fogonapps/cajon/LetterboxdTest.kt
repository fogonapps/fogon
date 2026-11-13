package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import org.junit.Assert.*
import org.junit.Test

class LetterboxdTest {
    private var n = 0
    private val id = { p: String -> p + n++ }
    private val cabecera = "Date,Name,Year,Letterboxd URI"
    private val peli = "2024-02-03,El niño,2023,https://boxd.it/abc"
    private fun importar(texto: String, nombre: String) = Importar.letterboxd(texto.toByteArray(), nombre, id, 10)

    @Test fun zipCompletoConResenaMultilineaYRevisitas() {
        val bytes = zipImportacion(
            "export/diary.csv" to (cabecera + ",Rating,Rewatch,Tags,Watched Date\n" + peli + ",0.5,No,,2024-01-01\n" + peli + ",5,Yes,,2024-02-01\n"),
            "export/ratings.csv" to (cabecera + ",Rating\n" + peli + ",4.5\n"),
            "export/watched.csv" to (cabecera + "\n" + peli + "\n"),
            "export/watchlist.csv" to (cabecera + "\n2024-02-03,Pendiente,2020,https://boxd.it/def\n"),
            "export/reviews.csv" to ("\uFEFF" + cabecera + ",Rating,Rewatch,Review,Tags,Watched Date\r\n" +
                filaCsv("2024-02-03", "El niño", "2023", "https://boxd.it/abc", "4.5", "No", "Sí, dijo \"mañana\"\nOtra línea", "", "2024-01-01")))
        val obras = Importar.letterboxd(bytes, "export.zip", id, 10)!!.obras
        assertEquals(2, obras.size)
        val vista = obras.first()
        assertEquals(9, vista.nota)
        assertEquals(Estados.TERMINADO, vista.estado)
        assertEquals("2024-02-01", vista.terminada)
        assertEquals(listOf(1, 10), vista.registros.map { it.nota })
        assertTrue(vista.registros.last().otraVez)
        assertEquals("Sí, dijo \"mañana\"\nOtra línea", vista.resena)
        assertEquals(Estados.QUIERO, obras.last().estado)
        val primera = Importar.sumar(Estado(), obras, 20).first
        val segunda = Importar.sumar(primera, Importar.letterboxd(bytes, "export.zip", id, 30)!!.obras, 30)
        assertEquals(0, segunda.second)
        assertEquals(primera.obras.size, segunda.first.obras.size)
        assertEquals(primera.obras.first().registros, segunda.first.obras.first().registros)
        assertEquals(primera.resenas, segunda.first.resenas)
    }

    @Test fun csvSueltosNotasYFechaWatched() {
        for (nota in 1..10) assertEquals(nota, importar(cabecera + ",Rating\n" + peli + ",${nota / 2.0}\n", "ratings.csv")!!.obras.single().nota)
        val vista = importar("\uFEFF" + cabecera + "\n" + peli + "\n", "watched.csv")!!.obras.single()
        assertEquals(0, vista.nota)
        assertEquals("2024-02-03", vista.terminada)
        assertEquals(Estados.TERMINADO, vista.estado)
        assertEquals(Estados.QUIERO, importar(cabecera + "\n" + peli, "watchlist.csv")!!.obras.single().estado)
    }

    @Test fun resenasSueltasConservanNotaYFechaDelVisionado() {
        val csv = cabecera + ",Rating,Rewatch,Review,Tags,Watched Date\n" +
            filaCsv("2024-02-03", "El niño", "2023", "https://boxd.it/abc", "4", "Yes", "Sí, mañana", "", "2024-01-02")
        val obra = importar(csv, "reviews.csv")!!.obras.single()
        assertEquals(8, obra.nota)
        assertEquals("2024-01-02", obra.terminada)
        assertEquals(8, obra.registros.single().nota)
        assertTrue(obra.registros.single().otraVez)
        assertEquals("Sí, mañana", obra.resena)
    }

    @Test fun vaciosAjenosYZipDanado() {
        assertNull(importar("", "diary.csv"))
        assertNull(importar("Title,Date\nOtra,2024-01-01", "diary.csv"))
        assertNull(Importar.letterboxd(zipImportacion("ajeno.csv" to "a,b\n1,2"), "x.zip", id, 0))
        val roto = zipImportacion("diary.csv" to (cabecera + "\n" + peli)).copyOf(40)
        assertNull(Importar.letterboxd(roto, "x.zip", id, 0))
    }

    @Test(timeout = 10000) fun cincoMilFilas() {
        val csv = cabecera + ",Rating\n" + (1..5000).joinToString("\n") { "2024-01-01,Película $it,2020,https://boxd.it/$it,3.5" }
        assertEquals(5000, importar(csv, "ratings.csv")!!.obras.size)
    }
}

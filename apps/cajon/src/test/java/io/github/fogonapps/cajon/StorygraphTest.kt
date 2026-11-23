package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import org.junit.Assert.*
import org.junit.Test

/** El CSV de The StoryGraph, con su cabecera real y filas inventadas. */
class StorygraphTest {
    private var n = 0
    private val id = { p: String -> p + n++ }
    private val cabecera = "Title,Authors,Contributors,ISBN/UID,Format,Read Status,Date Added,Last Date Read,Dates Read,Read Count,Moods,Pace," +
        "Character- or Plot-Driven?,Strong Character Development?,Loveable Characters?,Diverse Characters?,Flawed Characters?,Star Rating," +
        "Review,Content Warnings,Content Warning Description,Tags,Owned?\n"
    private fun fila(estado: String, nota: String = "", ultima: String = "", fechas: String = "", titulo: String = "El niño, la niña",
                     resena: String = "", etiquetas: String = "") = filaCsv(
        titulo, "Ana Peña, Ángel Muñoz", "", "9781234567890", "paperback", estado, "2024/01/01", ultima, fechas, "1",
        "informative", "fast", "", "", "", "", "", nota, resena, "", "", etiquetas, "No")

    private fun una(csv: String) = Importar.storygraph(csv, id, 7)!!.obras.single()

    @Test fun estadosYNotas() {
        for ((s, e) in listOf("read" to Estados.TERMINADO, "currently-reading" to Estados.EN_CURSO, "paused" to Estados.EN_CURSO,
                "did-not-finish" to Estados.ABANDONADO, "to-read" to Estados.QUIERO, "otra-cosa" to Estados.QUIERO)) {
            assertEquals(s, e, una(cabecera + fila(s)).estado)
        }
        assertEquals(10, una(cabecera + fila("read", "5.0")).nota)
        assertEquals(9, una(cabecera + fila("read", "4.25")).nota)
        assertEquals(7, una(cabecera + fila("read", "3.5")).nota)
        assertEquals(0, una(cabecera + fila("read", "")).nota)
    }

    @Test fun datosDelLibro() {
        val o = una("﻿" + cabecera + fila("read", "4.0", "2024/02/03", "2024/01/20-2024/02/03",
            resena = "Dijo \"sí\", mañana<br/>Otra línea", etiquetas = "favoritos, español"))
        assertEquals(Tipos.LIBRO, o.tipo)
        assertEquals("El niño, la niña", o.titulo)
        assertEquals("Ana Peña, Ángel Muñoz", o.creador)
        assertEquals("Dijo \"sí\", mañana\nOtra línea", o.resena)
        assertEquals(listOf("favoritos", "español"), o.etiquetas)
        assertEquals("2024-01-20", o.empezada); assertEquals("2024-02-03", o.terminada)
        assertEquals(listOf("2024-02-03"), o.registros.map { it.fecha }); assertEquals(8, o.registros.single().nota)
    }

    @Test fun variasLecturas() {
        val o = una(cabecera + fila("read", "5.0", "2023/06/01", "2021/03/01-2021/03/09, 2023/05/20-2023/06/01"))
        assertEquals(listOf("2021-03-09", "2023-06-01"), o.registros.map { it.fecha })
        assertEquals(listOf(false, true), o.registros.map { it.otraVez })
        assertEquals("2023-05-20", o.empezada); assertEquals("2023-06-01", o.terminada)
        assertEquals(listOf(0, 10), o.registros.map { it.nota })
        val puntoYComa = una(cabecera + fila("read", "5.0", "", "2021/03/09; 2023/06/01"))
        assertEquals(listOf("2021-03-09", "2023-06-01"), puntoYComa.registros.map { it.fecha })
    }

    @Test fun relecturaEnCursoOAbandonadaNoCuentaComoTerminada() {
        val leyendo = una(cabecera + fila("currently-reading", "4.0", "2021/03/09", "2021/03/01-2021/03/09, 2024/05/01-"))
        assertEquals(Estados.EN_CURSO, leyendo.estado)
        assertEquals(listOf("2021-03-09"), leyendo.registros.map { it.fecha })
        assertEquals("2024-05-01", leyendo.empezada); assertNull(leyendo.terminada)
        val dejado = una(cabecera + fila("did-not-finish", "", "2021/03/09", "2021/03/01-2021/03/09, 2024/05/01-2024/05/10"))
        assertEquals(Estados.ABANDONADO, dejado.estado)
        assertEquals(listOf("2021-03-09"), dejado.registros.map { it.fecha }); assertEquals(listOf(false), dejado.registros.map { it.otraVez })
        assertEquals("2024-05-01", dejado.empezada); assertNull(dejado.terminada)
    }

    @Test fun sinTerminarSinDatesReadNoInventaLectura() {
        for (e in listOf("did-not-finish", "currently-reading", "paused")) {
            val o = una(cabecera + fila(e, ultima = "2024/05/10"))
            assertTrue(e, o.registros.isEmpty()); assertNull(e, o.terminada)
        }
    }

    @Test fun entidadEscritaSeConserva() {
        assertEquals("usa &lt; y <", una(cabecera + fila("read", resena = "usa &amp;lt; y &lt;")).resena)
    }

    @Test fun resenaConHtmlYSignos() {
        val o = una(cabecera + fila("read", resena = "<p>Uno &amp; dos</p><p>si a < b y c > d</p><br/><b>fin</b>"))
        assertEquals("Uno & dos\nsi a < b y c > d\nfin", o.resena)
    }

    @Test fun sinFechasNiLecturaNoInventaRegistros() {
        val o = una(cabecera + fila("read", "5.0"))
        assertTrue(o.registros.isEmpty()); assertNull(o.terminada)
        val leyendo = una(cabecera + fila("currently-reading", fechas = "2024/05/01-"))
        assertTrue(leyendo.registros.isEmpty()); assertEquals("2024-05-01", leyendo.empezada)
        assertNull(una(cabecera + fila("read", ultima = "2024/13/40")).terminada)
    }

    @Test fun reimportarNoDuplica() {
        fun leer() = Importar.storygraph(cabecera + fila("read", "4.5", "2024/02/03", resena = "Bueno"), id, 0)!!.obras
        val primera = Importar.sumar(Estado(), leer(), 1).first
        val segunda = Importar.sumar(primera, leer(), 2)
        assertEquals(0, segunda.second)
        assertEquals(primera.obras, segunda.first.obras.map { it.copy(cambio = 0) })
        assertEquals(primera.resenas, segunda.first.resenas)
    }

    @Test fun noSeConfundeConOtrosServicios() {
        assertNull(Importar.storygraph("", id, 0))
        assertNull(Importar.storygraph("Name,Year,Letterboxd URI\nNiño,2020,url", id, 0))
        val goodreads = "Book Id,Title,Author,My Rating,Exclusive Shelf\n1,Libro,Autor,5,read\n"
        assertNull(Importar.storygraph(goodreads, id, 0))
        assertNull(Importar.goodreads(cabecera + fila("read"), id, 0))
        assertNull(Importar.netflix(cabecera + fila("read"), id, 0))
        assertTrue(Importar.storygraph(cabecera + fila("read", titulo = ""), id, 0)!!.obras.isEmpty())
    }

    @Test(timeout = 10000) fun cincoMilFilas() {
        val csv = cabecera + (1..5000).joinToString("") { fila("read", "4.0", "2024/02/03", titulo = "Libro $it") }
        assertEquals(5000, Importar.storygraph(csv, id, 0)!!.obras.size)
    }
}

package io.github.fogonapps.ahorrador

import io.github.fogonapps.ahorrador.datos.FilaExtracto
import io.github.fogonapps.ahorrador.datos.leerExtracto
import io.github.fogonapps.ahorrador.datos.leerFecha
import io.github.fogonapps.ahorrador.datos.leerMonto
import io.github.fogonapps.ahorrador.datos.montoReto
import io.github.fogonapps.ahorrador.datos.totalReto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExtractoTest {
    @Test
    fun montosDeVariosFormatos() {
        assertEquals(1234568L, leerMonto("$ 1.234.567,89"))
        assertEquals(-1234L, leerMonto("-1,234.40"))
        assertEquals(-50000L, leerMonto("(50.000)"))
        assertEquals(86400L, leerMonto("86400"))
        assertEquals(3200L, leerMonto("3.200"))
        assertEquals(12L, leerMonto("12,4"))
        assertNull(leerMonto("COMPRA EXITO"))
    }

    @Test
    fun fechasConDiaPrimero() {
        assertEquals("2026-10-03", leerFecha("03/10/2026"))
        assertEquals("2026-10-03", leerFecha("2026-10-03"))
        assertEquals("2026-01-31", leerFecha("31-01-26"))
        assertNull(leerFecha("Saldo"))
    }

    @Test
    fun extractoConUnaColumnaDeValor() {
        val csv = "Fecha;Descripción;Valor;Saldo\n03/10/2026;COMPRA EN TIENDA;-86.400;1.200.000\n05/10/2026;PAGO NOMINA;1.850.000;3.050.000\n"
        assertEquals(listOf(FilaExtracto("2026-10-03", "COMPRA EN TIENDA", -86400), FilaExtracto("2026-10-05", "PAGO NOMINA", 1850000)), leerExtracto(csv))
    }

    @Test
    fun extractoConDebitosYCreditos() {
        val csv = "fecha,concepto,debito,credito,saldo\n2026-10-03,\"Tienda, la esquina\",86400,,1200000\n2026-10-05,Nomina,,1850000,3050000\n"
        assertEquals(listOf(FilaExtracto("2026-10-03", "Tienda, la esquina", -86400), FilaExtracto("2026-10-05", "Nomina", 1850000)), leerExtracto(csv))
    }

    @Test
    fun retoPorSemanas() {
        assertEquals(15000L, montoReto(5000, 3))
        assertEquals(30000L, totalReto(5000, 3))
        assertEquals(6890000L, totalReto(5000, 52))
    }

    @Test fun bomComillasTildesYSaltosDeLinea() {
        val csv = "\uFEFFFecha;Descripción;Valor;Saldo\r\n03/02/2024;\"Café, \"\"Niño\"\"\nMañana\";-1.234,50;8.000\r\n"
        assertEquals(listOf(FilaExtracto("2024-02-03", "Café, \"Niño\"\nMañana", -1235)), leerExtracto(csv))
    }

    @Test fun vaciosAjenosYFechasImposibles() {
        assertEquals(emptyList<FilaExtracto>(), leerExtracto(""))
        assertEquals(emptyList<FilaExtracto>(), leerExtracto("Title,Author,My Rating\nLibro,Ana,5"))
        assertEquals(emptyList<FilaExtracto>(), leerExtracto("Fecha;Descripción;Valor\n30/02/2024;Café;-100"))
    }

    @Test fun dosComprasIgualesSeConservanYNoSeReimportan() {
        var n = 0
        val filas = List(2) { FilaExtracto("2024-02-03", "Café", -1000) to "comida" }
        val original = io.github.fogonapps.ahorrador.datos.Estado()
        val primera = io.github.fogonapps.ahorrador.datos.importarFilasExtracto(original, filas, "c1") { "m${n++}" }
        assertEquals(2, primera.movimientos.size)
        assertEquals(primera, io.github.fogonapps.ahorrador.datos.importarFilasExtracto(primera, filas, "c1") { "m${n++}" })
    }

    @Test(timeout = 10000) fun cincoMilFilas() {
        val csv = "Fecha\tDescripción\tValor\n" + (1..5000).joinToString("\n") { "03/02/2024\tCompra $it\t-1000" }
        assertEquals(5000, leerExtracto(csv).size)
    }
}

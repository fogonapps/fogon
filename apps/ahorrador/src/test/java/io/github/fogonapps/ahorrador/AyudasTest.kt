package io.github.fogonapps.ahorrador

import io.github.fogonapps.ahorrador.datos.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AyudasTest {
    private val cats = listOf("comida", "transporte", "mercado", "casa", "sueldo", "otros").map {
        Categoria(it, it, 0, it, ingreso = it == "sueldo")
    }
    private val base = Estado(categorias = cats, cuentas = listOf(Cuenta("c", "Efectivo", TipoCuenta.EFECTIVO, saldoInicial = 300_000)), listo = true)

    @Test fun montosComoSeDicen() {
        mapOf("25 mil" to 25_000L, "12k" to 12_000L, "12.000" to 12_000L, "$12.000" to 12_000L, "2 lucas" to 2_000L,
            "1,5 millones" to 1_500_000L, "2 palos" to 2_000_000L, "9500" to 9_500L, "1.250.000" to 1_250_000L, "2,5 mil" to 2_500L)
            .forEach { (texto, valor) -> assertEquals(texto, valor, montoEnTexto(texto)?.first) }
        assertNull(montoEnTexto("almuerzo"))
    }

    @Test fun variosConCategoria() {
        val l = leerVarios("Almuerzo 25 mil + Uber 12k\nmercado en el D1 $84.300, sueldo 2 palos; sin valor", base)
        assertEquals(listOf("Almuerzo", "Uber", "mercado en el D1", "sueldo"), l.map { it.nota })
        assertEquals(listOf(25_000L, 12_000L, 84_300L, 2_000_000L), l.map { it.monto })
        assertEquals(listOf("comida", "transporte", "mercado", "sueldo"), l.map { it.categoria })
        assertEquals(TipoMovimiento.INGRESO, l[3].tipo)
        assertEquals(TipoMovimiento.GASTO, l[0].tipo)
    }

    @Test fun aprendeDeLoAnotado() {
        val e = base.copy(movimientos = listOf(Movimiento("m", "2026-11-01", TipoMovimiento.GASTO, 5_000, "c", categoria = "casa", nota = "Doña Rosa")))
        assertEquals("casa", leerVarios("doña rosa 7 mil", e).single().categoria)
        assertNull(leerVarios("algo raro 3 mil", e).single().categoria)
    }

    @Test fun meAlcanzaConFijosYRitmo() {
        val hoy = LocalDate.parse("2026-11-21") // quedan 10 días contando hoy
        val e = base.copy(sistema = Sistema.SOLO_ANOTAR, fijos = listOf(Fijo("f", "Internet", 100_000, "casa", "c", 25)))
        val a = meAlcanza(e, Logica(), 9_000, hoy)
        assertEquals(300_000L, a.tienes); assertEquals(100_000L, a.comprometido); assertEquals(200_000L, a.seguro)
        assertEquals(10, a.diasRestantes); assertEquals(20_000L, a.cupoDia)
        assertEquals(Veredicto.SI, a.veredicto)
        assertEquals(Veredicto.SE_COME_DIAS, meAlcanza(e, Logica(), 50_000, hoy).veredicto)
        assertEquals(2.5, meAlcanza(e, Logica(), 50_000, hoy).diasQueSeCome, 0.001)
        assertEquals(Veredicto.NO, meAlcanza(e, Logica(), 250_000, hoy).veredicto)
        // Un fijo ya pagado este mes no se cuenta dos veces.
        val pagado = e.copy(movimientos = listOf(Movimiento("p", "2026-11-05", TipoMovimiento.GASTO, 100_000, "c", categoria = "casa", fijo = "f")))
        assertEquals(0L, meAlcanza(pagado, Logica(), 0, hoy).comprometido)
    }

    @Test fun alertasEHistorial() {
        val e = base.copy(sistema = Sistema.SOBRES, sobres = mapOf("2026-10" to mapOf("comida" to 50_000L), "2026-11" to mapOf("comida" to 10_000L)),
            metas = listOf(Meta("v", "Viaje", 100_000), Meta("b", "Bici", 100_000)),
            movimientos = listOf(
                Movimiento("1", "2026-10-03", TipoMovimiento.GASTO, 40_000, "c", categoria = "comida"),
                Movimiento("2", "2026-11-03", TipoMovimiento.GASTO, 20_000, "c", categoria = "comida"),
                Movimiento("3", "2026-11-04", TipoMovimiento.AHORRO, 80_000, "c", meta = "v"),
                Movimiento("4", "2026-11-04", TipoMovimiento.AHORRO, 100_000, "c", meta = "b")))
        val a = alertas(e, Logica(), LocalDate.parse("2026-11-10"))
        assertEquals(listOf("comida"), a.sobresPasados.map { it.clave })
        assertEquals(listOf("v"), a.metasCerca.map { it.id }); assertEquals(listOf("b"), a.metasCumplidas.map { it.id })
        val h = historialSobres(e, Logica(), LocalDate.parse("2026-11-10"))
        assertEquals(listOf("2026-10"), h.map { it.first })
        assertTrue(h.single().second.single().respetado); assertEquals(10_000L, h.single().second.single().diferencia)
    }
}

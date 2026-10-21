package io.github.fogonapps.ahorrador

import io.github.fogonapps.ahorrador.datos.Abono
import io.github.fogonapps.ahorrador.datos.Categoria
import io.github.fogonapps.ahorrador.datos.Credito
import io.github.fogonapps.ahorrador.datos.Cuenta
import io.github.fogonapps.ahorrador.datos.Deuda
import io.github.fogonapps.ahorrador.datos.Estado
import io.github.fogonapps.ahorrador.datos.Fijo
import io.github.fogonapps.ahorrador.datos.Formato
import io.github.fogonapps.ahorrador.datos.Grupo
import io.github.fogonapps.ahorrador.datos.JsonAhorrador
import io.github.fogonapps.ahorrador.datos.Logica
import io.github.fogonapps.ahorrador.datos.Meta
import io.github.fogonapps.ahorrador.datos.Movimiento
import io.github.fogonapps.ahorrador.datos.Sistema
import io.github.fogonapps.ahorrador.datos.TipoCuenta
import io.github.fogonapps.ahorrador.datos.TipoMovimiento.AHORRO
import io.github.fogonapps.ahorrador.datos.TipoMovimiento.GASTO
import io.github.fogonapps.ahorrador.datos.TipoMovimiento.INGRESO
import io.github.fogonapps.ahorrador.datos.TipoMovimiento.TRASPASO
import io.github.fogonapps.ahorrador.datos.diaDelMes
import io.github.fogonapps.ahorrador.datos.gmf
import io.github.fogonapps.ahorrador.datos.proximaFecha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Las cuentas de la plata: si algo de aquí falla, la app le muestra a la persona un número equivocado. */
class LogicaTest {
    private val L = Logica()
    private val hoy = LocalDate.parse("2026-10-21")

    private val cats = listOf(
        Categoria("mercado", "Mercado", 0, "mercado", Grupo.NECESIDAD),
        Categoria("salidas", "Salidas", 0, "salidas", Grupo.GUSTO),
        Categoria("cultura", "Cultura", 0, "cultura", Grupo.CULTURA),
        Categoria("sueldo", "Sueldo", 0, "sueldo", ingreso = true),
    )
    private val base = Estado(
        cuentas = listOf(
            Cuenta("efectivo", "Efectivo", TipoCuenta.EFECTIVO, 100_000),
            Cuenta("banco", "Banco", TipoCuenta.BANCO, 1_000_000),
            Cuenta("tarjeta", "Tarjeta", TipoCuenta.CREDITO, -200_000, Credito(cupo = 2_000_000, diaCorte = 15, diaPago = 30, tasaMensual = 2.0, minimoPct = 5.0)),
        ),
        categorias = cats,
        listo = true,
    )

    private fun mov(fecha: String, tipo: io.github.fogonapps.ahorrador.datos.TipoMovimiento, monto: Long, cuenta: String, categoria: String? = null,
                    aCuenta: String? = null, cuotas: Int? = null, meta: String? = null, fijo: String? = null) =
        Movimiento(fecha + monto + tipo, fecha, tipo, monto, cuenta, aCuenta, categoria, cuotas = cuotas, meta = meta, fijo = fijo)

    @Test
    fun saldosYTraspasos() {
        val e = base.copy(movimientos = listOf(
            mov("2026-10-01", INGRESO, 3_000_000, "banco", "sueldo"),
            mov("2026-10-02", GASTO, 50_000, "efectivo", "mercado"),
            mov("2026-10-03", TRASPASO, 200_000, "banco", aCuenta = "efectivo"),
            mov("2026-10-04", TRASPASO, 200_000, "banco", aCuenta = "tarjeta"),
        ))
        assertEquals(250_000, L.saldo(e, "efectivo"))
        assertEquals(3_600_000, L.saldo(e, "banco"))
        // Pagar la tarjeta baja la deuda y no es un gasto.
        assertEquals(0, L.deuda(e, "tarjeta"))
        assertEquals(50_000, L.gastos(e, "2026-10"))
        // Los traspasos no cambian el total.
        assertEquals(100_000 + 1_000_000 - 200_000 + 3_000_000 - 50_000, L.patrimonio(e))
    }

    @Test
    fun disponibleDelMes() {
        val e = base.copy(movimientos = listOf(
            mov("2026-09-30", INGRESO, 999, "banco", "sueldo"),
            mov("2026-10-01", INGRESO, 2_000_000, "banco", "sueldo"),
            mov("2026-10-05", GASTO, 300_000, "banco", "mercado"),
            mov("2026-10-06", AHORRO, 200_000, "banco", meta = "viaje"),
        ))
        assertEquals(1_500_000, L.disponible(e, "2026-10"))
        assertEquals(2_400_999, L.porAsignar(e, "2026-10"))
        assertEquals(200_000, L.ahorradoEn(e, "viaje"))
    }

    @Test
    fun cuatroPorMil() {
        assertEquals(4_000, gmf(1_000_000))
        assertEquals(2, gmf(500))
        assertEquals(0, gmf(100))
    }

    @Test
    fun tarjetaMinimoInteresesYCuotas() {
        val e = base.copy(movimientos = listOf(
            mov("2026-08-10", GASTO, 1_200_000, "tarjeta", "salidas", cuotas = 12),
            mov("2026-10-10", GASTO, 100_000, "tarjeta", "mercado", cuotas = 3),
            mov("2026-10-11", GASTO, 50_000, "tarjeta", "mercado"),
        ))
        val t = L.tarjeta(e, "tarjeta", hoy)!!
        assertEquals(200_000 + 1_200_000 + 100_000 + 50_000, t.deuda)
        assertEquals(77_500, t.minimo)
        assertEquals(29_500, t.interesesSiMinimo)
        assertEquals(LocalDate.parse("2026-11-15"), t.corte)
        assertEquals(LocalDate.parse("2026-10-30"), t.pago)
        assertEquals(2_000_000 - 1_550_000, t.disponible)
        // Dos compras a cuotas; la de agosto lleva 2 cuotas pagadas.
        assertEquals(2, t.cuotas.size)
        assertEquals(2, t.cuotas[0].pagadas)
        assertEquals(100_000, t.cuotas[0].valor)
        assertEquals(1_000_000, t.cuotas[0].restante)
        assertEquals(0, t.cuotas[1].pagadas)
    }

    @Test
    fun fechasAFinDeMes() {
        assertEquals(LocalDate.parse("2026-02-28"), diaDelMes(YearMonth.parse("2026-02"), 31))
        assertEquals(LocalDate.parse("2026-10-21"), proximaFecha(21, hoy))
        assertEquals(LocalDate.parse("2026-11-20"), proximaFecha(20, hoy))
    }

    @Test
    fun sobresSegunElSistema() {
        val movs = listOf(
            mov("2026-10-01", INGRESO, 1_000_000, "banco", "sueldo"),
            mov("2026-10-02", GASTO, 120_000, "banco", "mercado"),
            mov("2026-10-03", GASTO, 80_000, "banco", "salidas"),
            mov("2026-10-04", GASTO, 30_000, "banco", "cultura"),
        )
        val sobres = base.copy(movimientos = movs, sobres = mapOf("2026-10" to mapOf("mercado" to 100_000L, "salidas" to 200_000L)))
        val s = L.sobres(sobres, "2026-10").associateBy { it.clave }
        assertTrue(s.getValue("mercado").pasado)
        assertEquals(-20_000L, s.getValue("mercado").queda)
        assertEquals(120_000L, s.getValue("salidas").queda)
        // Tiene 1.670.000; a salidas le quedan 120.000 (mercado se pasó y no resta).
        assertEquals(1_550_000, L.porAsignar(sobres, "2026-10"))

        val regla = L.sobres(base.copy(movimientos = movs, sistema = Sistema.REGLA_50_30_20), "2026-10").associateBy { it.clave }
        assertEquals(500_000L, regla.getValue("NECESIDAD").asignado)
        assertEquals(120_000, regla.getValue("NECESIDAD").gastado)
        // Cultura cuenta como gusto en el 50/30/20.
        assertEquals(110_000, regla.getValue("GUSTO").gastado)

        val kakeibo = L.sobres(base.copy(movimientos = movs, sistema = Sistema.KAKEIBO), "2026-10").associateBy { it.clave }
        assertEquals(4, kakeibo.size)
        assertEquals(30_000, kakeibo.getValue("CULTURA").gastado)
        assertNull(kakeibo.getValue("CULTURA").asignado)

        assertTrue(L.sobres(base.copy(sistema = Sistema.SOLO_ANOTAR), "2026-10").isEmpty())
    }

    @Test
    fun metas() {
        val m = Meta("viaje", "Viaje", 1_200_000, fecha = "2027-04-21")
        val e = base.copy(metas = listOf(m), movimientos = listOf(
            mov("2026-08-01", AHORRO, 100_000, "banco", meta = "viaje"),
            mov("2026-09-01", AHORRO, 100_000, "banco", meta = "viaje"),
            mov("2026-10-01", AHORRO, 100_000, "banco", meta = "viaje"),
        ))
        // Faltan 900.000 en 182 días: 12 quincenas de 75.000.
        assertEquals(75_000L, L.porQuincena(e, m, hoy))
        // A 100.000 por mes, faltan 9 meses.
        assertEquals(YearMonth.parse("2027-07"), L.llegaEn(e, m, hoy))
        assertEquals(0.25, L.progreso(e, m), 1e-9)
    }

    @Test
    fun deudasYFijos() {
        val d = Deuda("d", "Ana", meDeben = true, monto = 300_000, fecha = "2026-09-01", cuotas = 3, abonos = listOf(Abono("2026-10-01", 100_000)))
        assertEquals(200_000, L.pendiente(d))
        assertEquals(100_000L, L.cuotaDe(d))
        val e = base.copy(
            deudas = listOf(d, Deuda("p", "Banco", meDeben = false, monto = 1_000_000, fecha = "2026-01-01")),
            fijos = listOf(
                Fijo("arriendo", "Arriendo", 900_000, "mercado", "banco", dia = 5, automatico = true),
                Fijo("luz", "Luz", 80_000, "mercado", "banco", dia = 23),
                Fijo("internet", "Internet", 70_000, "mercado", "banco", dia = 28),
            ),
        )
        assertEquals(200_000, L.meDeben(e))
        // Lo que debo suma el préstamo y la tarjeta.
        assertEquals(1_200_000, L.debo(e))
        assertEquals(listOf("arriendo"), L.aAnotar(e, hoy).map { it.id })
        assertEquals(listOf("arriendo", "luz"), L.porVencer(e, hoy).map { it.first.id })
        val pagado = e.copy(movimientos = listOf(mov("2026-10-05", GASTO, 900_000, "banco", "mercado", fijo = "arriendo")))
        assertFalse(L.aAnotar(pagado, hoy).any { it.id == "arriendo" })
        assertEquals(listOf("luz", "internet"), L.proximos(pagado, hoy).map { it.first.id })
    }

    @Test
    fun respaldoIdaYVuelta() {
        val e = base.copy(movimientos = listOf(mov("2026-10-01", GASTO, 1, "efectivo", "mercado"), mov("2026-10-01", GASTO, 2, "borrada")))
        val texto = JsonAhorrador.encodeToString(Estado.serializer(), e)
        val leido = L.normalizar(JsonAhorrador.decodeFromString(Estado.serializer(), texto))
        // Se quita el movimiento de una cuenta que no existe; lo demás queda igual.
        assertEquals(1, leido.movimientos.size)
        assertEquals(e.cuentas, leido.cuentas)
    }

    @Test
    fun formatoDePesos() {
        Formato.idioma = "es"
        assertEquals("$ 842.300", Formato.pesos(842_300))
        assertEquals("− $ 5.000", Formato.pesos(-5_000))
        Formato.idioma = "en"
        assertEquals("$842,300", Formato.pesos(842_300))
        Formato.idioma = "es"
    }
}

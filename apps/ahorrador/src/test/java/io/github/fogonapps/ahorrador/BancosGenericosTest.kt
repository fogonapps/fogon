package io.github.fogonapps.ahorrador

import io.github.fogonapps.ahorrador.datos.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

/** Alertas escritas como las de esos bancos (no son reales): el lector general debe sacar lo esencial o callarse. */
class BancosGenericosTest {
    private val hora = LocalDateTime.of(2026, 11, 10, 18, 30, 42)
    private fun leer(b: Banco, titulo: String, texto: String) = reconocerGenerico(b, titulo, texto, hora)

    @Test fun gastosEIngresosComunes() {
        leer(Banco.NEQUI, "Nequi", "Pagaste $25.000 en TIENDA D1 con tu Nequi.")!!.let {
            assertEquals(TipoMovimiento.GASTO, it.tipo); assertEquals(25_000, it.monto); assertEquals("TIENDA D1", it.contraparte); assertEquals("2026-11-10T18:30", it.momento)
        }
        leer(Banco.NEQUI, "", "Recibiste $150.000 de MARIA PEREZ en tu Nequi")!!.let {
            assertEquals(TipoMovimiento.INGRESO, it.tipo); assertEquals(150_000, it.monto); assertEquals("MARIA PEREZ", it.contraparte)
        }
        leer(Banco.DAVIVIENDA, "Davivienda", "Compra por $ 84.300,00 en EXITO CALLE 80 con tu tarjeta terminada en 4321")!!.let {
            assertEquals(TipoMovimiento.GASTO, it.tipo); assertEquals(84_300, it.monto); assertEquals("*4321", it.cuenta); assertEquals("EXITO CALLE 80", it.contraparte)
        }
        leer(Banco.DAVIPLATA, "DaviPlata", "Te enviaron $50,000.00 a tu DaviPlata")!!.let { assertEquals(TipoMovimiento.INGRESO, it.tipo); assertEquals(50_000, it.monto) }
        leer(Banco.BBVA, "BBVA", "Retiro de COP 200.000 en cajero ATM CENTRO")!!.let { assertEquals(TipoMovimiento.GASTO, it.tipo); assertEquals(200_000, it.monto) }
    }

    @Test fun loQueNoEsUnMovimientoNoSeAnota() {
        assertNull(leer(Banco.NEQUI, "Nequi", "Tu código de verificación es 123456. No lo compartas."))
        assertNull(leer(Banco.DAVIVIENDA, "Davivienda", "Gana hasta $1.000.000 con tu crédito preaprobado"))
        assertNull(leer(Banco.BOGOTA, "Banco de Bogotá", "Iniciaste sesión desde un nuevo dispositivo"))
        assertNull(leer(Banco.NEQUI, "Nequi", "Tu saldo disponible es $40.000"))
    }

    @Test fun elPaqueteYElSmsDicenElBanco() {
        val todos = Banco.entries.toSet()
        assertEquals(Banco.NEQUI, bancoDelTexto("com.nequi.MobileApp", null, todos, "Pagaste $1.000"))
        assertNull(bancoDelTexto("com.nequi.MobileApp", null, setOf(Banco.NU), "Pagaste $1.000"))
        assertEquals(Banco.DAVIPLATA, bancoDelTexto("com.google.android.apps.messaging", null, todos, "DaviPlata (Davivienda): recibiste $10.000"))
        assertEquals(Banco.DAVIVIENDA, bancoDelTexto("com.google.android.apps.messaging", null, todos, "Davivienda: compra por $10.000"))
        assertNull(bancoDelTexto("com.whatsapp", null, todos, "Davivienda: compra por $10.000"))
        assertEquals(84_300L, montoFlexible("84.300,00")); assertEquals(1_234_568L, montoFlexible("1,234,567.89")); assertEquals(5_000L, montoFlexible("5000"))
    }
}

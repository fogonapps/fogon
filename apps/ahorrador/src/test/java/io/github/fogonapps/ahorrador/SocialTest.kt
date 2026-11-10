package io.github.fogonapps.ahorrador

import io.github.fogonapps.ahorrador.datos.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SocialTest {
    private val e = Estado(cuentas = listOf(Cuenta("c", "Efectivo", TipoCuenta.EFECTIVO, 1_000_000)),
        metas = listOf(Meta("v", "Viaje a Cartagena", 2_000_000), Meta("b", "Bici", 500_000)),
        movimientos = listOf(Movimiento("1", "2026-11-01", TipoMovimiento.AHORRO, 500_000, "c", meta = "b"),
            Movimiento("2", "2026-11-02", TipoMovimiento.AHORRO, 900_000, "c", meta = "v")),
        cierres = mapOf("2026-09" to "", "2026-10" to "Gastar menos"))

    private fun sinDinero(o: JsonElement) {
        when (o) {
            is JsonObject -> o.forEach { (k, v) ->
                assertFalse("Salió «$k»", k in setOf("monto", "objetivo", "ahorrado", "saldo", "cuenta", "banco", "movimientos"))
                sinDinero(v)
            }
            is JsonArray -> o.forEach(::sinDinero)
            is JsonPrimitive -> if (!o.isString) assertTrue("Cifra sospechosa: $o", (o.doubleOrNull ?: 0.0) <= 1200)
            else -> {}
        }
    }

    @Test fun logrosSinPlata() {
        val logros = logrosPosibles(e, Logica())
        assertEquals(listOf(Logro.META_AVANCE, Logro.META_CUMPLIDA, Logro.PRIMERA_ANOTACION, Logro.CIERRES), logros.map { it.logro })
        assertEquals(45, logros[0].porcentaje)
        logros.forEach { l ->
            val d = instantaneaLogro(l, " ¡Vamos! ")
            sinDinero(d)
            assertTrue(cabeLogro(d))
            assertEquals("¡Vamos!", d["texto"]!!.jsonPrimitive.content)
        }
        assertEquals(2, instantaneaLogro(logros[3], "")["cantidad"]!!.jsonPrimitive.int)
        assertNull(instantaneaLogro(logros[2], "")["meta"])
    }

    @Test fun clavesEstablesYCortas() {
        val l = logrosPosibles(e, Logica())
        assertEquals(l[0].clave, logrosPosibles(e, Logica())[0].clave)
        assertNotEquals(l[0].clave, l[1].clave)
        assertTrue(l.all { it.clave.length <= 80 })
    }

    @Test fun parteMetasEnPorcentaje() {
        val p = parteMetas(e, Logica())
        sinDinero(p)
        assertEquals(listOf("Viaje a Cartagena" to 45, "Bici" to 100), metasDeParte(p))
    }
}

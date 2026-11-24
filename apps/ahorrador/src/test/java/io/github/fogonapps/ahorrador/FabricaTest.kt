package io.github.fogonapps.ahorrador

import io.github.fogonapps.ahorrador.datos.Categoria
import io.github.fogonapps.ahorrador.datos.Cuenta
import io.github.fogonapps.ahorrador.datos.Estado
import io.github.fogonapps.ahorrador.datos.Fabrica
import io.github.fogonapps.ahorrador.datos.Logica
import io.github.fogonapps.ahorrador.datos.Meta
import io.github.fogonapps.ahorrador.datos.Reto
import io.github.fogonapps.ahorrador.datos.TextoFabrica
import io.github.fogonapps.ahorrador.datos.TipoCuenta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Lo de fábrica (categorías, cuentas iniciales, reto) sigue al idioma de la app; lo que escribió la persona, no. */
class FabricaTest {
    private val L = Logica()

    /** La fábrica con la app en español. */
    private val enEspanol = Fabrica(
        categorias = mapOf("mercado" to TextoFabrica(setOf("Mercado", "Groceries"), "Mercado")),
        cuentas = mapOf("efectivo" to TextoFabrica(setOf("Efectivo", "Cash"), "Efectivo")),
        reto = TextoFabrica(setOf("Reto de ahorro", "Savings challenge"), "Reto de ahorro"),
        ajusteSaldo = TextoFabrica(setOf("Ajuste de saldo", "Balance adjustment"), "Ajuste de saldo"),
    )

    private val enIngles = Estado(
        cuentas = listOf(Cuenta("efectivo", "Cash", TipoCuenta.EFECTIVO, notaSaldo = "Balance adjustment"), Cuenta("c2", "Cash", TipoCuenta.BANCO)),
        categorias = listOf(Categoria("mercado", "Groceries", 0, "mercado"), Categoria("mia", "Groceries", 0, "otros")),
        metas = listOf(Meta("t1", "Savings challenge", 1000), Meta("m2", "Savings challenge", 1000)),
        reto = Reto("2026-01-01", 1000, "t1"),
    )

    @Test fun loDeFabricaPasaAlIdiomaActual() {
        val e = L.traducirFabrica(enIngles, enEspanol)
        assertEquals("Mercado", e.categorias[0].nombre)
        assertEquals("Efectivo", e.cuentas[0].nombre)
        assertEquals("Ajuste de saldo", e.cuentas[0].notaSaldo)
        assertEquals("Reto de ahorro", e.metas[0].nombre)
    }

    @Test fun loDeLaPersonaNoSeToca() {
        val e = L.traducirFabrica(enIngles, enEspanol)
        // Otro id con el mismo texto es de la persona.
        assertEquals("Groceries", e.categorias[1].nombre)
        assertEquals("Cash", e.cuentas[1].nombre)
        assertEquals("Savings challenge", e.metas[1].nombre)
        // Un nombre cambiado no se traduce.
        val cambiado = enIngles.copy(categorias = listOf(Categoria("mercado", "Súper", 0, "mercado")))
        assertEquals("Súper", L.traducirFabrica(cambiado, enEspanol).categorias[0].nombre)
    }

    @Test fun sinCambiosDevuelveElMismoEstado() {
        val ya = L.traducirFabrica(enIngles, enEspanol)
        assertSame(ya, L.traducirFabrica(ya, enEspanol))
    }

    @Test fun unRespaldoViejoSeLeeYSeTraduce() {
        val json = """{"listo":true,"cuentas":[{"id":"efectivo","nombre":"Cash","tipo":"EFECTIVO"}],
            "categorias":[{"id":"mercado","nombre":"Groceries","color":0,"icono":"mercado"}]}"""
        val e = L.traducirFabrica(L.normalizar(leerRespaldoAhorrador(json)!!), enEspanol)
        assertEquals("Efectivo", e.cuentas.single().nombre)
        assertEquals("Mercado", e.categorias.single().nombre)
    }
}

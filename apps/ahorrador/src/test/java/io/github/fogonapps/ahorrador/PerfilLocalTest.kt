package io.github.fogonapps.ahorrador

import io.github.fogonapps.ahorrador.datos.Cuenta
import io.github.fogonapps.ahorrador.datos.TipoCuenta
import io.github.fogonapps.ahorrador.datos.Estado
import io.github.fogonapps.ahorrador.datos.JsonAhorrador
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Test

class PerfilLocalTest {
    @Test
    fun respaldoAnteriorConservaNombreYDatos() {
        val estado = JsonAhorrador.decodeFromString<Estado>("""{"nombre":"Ana","listo":true,"cierres":{"2026-10":"Revisar"}}""")
        assertEquals("Ana", estado.nombre)
        assertEquals(mapOf("2026-10" to "Revisar"), estado.cierres)
        assertEquals("", estado.bio)
        assertEquals("", estado.fotoPerfil)
        assertEquals("", estado.portadaPerfil)
    }

    @Test
    fun perfilViajaEnElRespaldoSinPerderOtrosDatos() {
        val estado = Estado(nombre = "Ana", bio = "Mis metas", fotoPerfil = "Zm90bw==", portadaPerfil = "cG9ydGFkYQ==",
            cuentas = listOf(Cuenta("efectivo", "Bolsillo", TipoCuenta.EFECTIVO, 12500)),
            listo = true, cierres = mapOf("2026-10" to "Revisar"))
        assertEquals(estado, leerRespaldoAhorrador(JsonAhorrador.encodeToString(estado)))
    }
}

package io.github.fogonapps.social

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.*
import kotlin.test.*
import java.nio.file.Files

class PagosWompiTest {
    private val configuracion = ConfiguracionWompi("pub_test_inventada", "integridad_inventada", "eventos_inventados")
    private fun escenario(prueba: (Base, Social, Long, PagosWompi) -> Unit) {
        val dir = Files.createTempDirectory("wompi").toFile()
        val base = Base(dir.resolve("base.db").path)
        try {
            val social = Social(base, dir.resolve("fotos"))
            val cuenta = social.cuentaDe(social.crearCuenta(NuevaCuenta("ana")).token)!!
            prueba(base, social, cuenta, PagosWompi(base, configuracion))
        } finally { base.cerrar(); dir.deleteRecursively() }
    }
    private fun evento(ref: String, monto: Long = 1000000, estado: String = "APPROVED", id: String = "tx_inventada"): JsonObject {
        // Construcción independiente: campos en un orden distinto al ejemplo de Wompi.
        val suma = java.security.MessageDigest.getInstance("SHA-256")
            .digest("${monto}${id}${estado}123eventos_inventados".toByteArray())
            .joinToString("") { "%02X".format(it) }
        return Json.parseToJsonElement("""{"event":"transaction.updated","environment":"test","timestamp":123,
            "data":{"transaction":{"id":"$id","reference":"$ref","status":"$estado","amount_in_cents":$monto,"currency":"COP"}},
            "signature":{"properties":["transaction.amount_in_cents","transaction.id","transaction.status"],"checksum":"$suma"}}""").jsonObject
    }
    @Test fun vectorIntegridad() {
        // Vector fijado mediante printf 'referencia1000000COPinventado' | sha256sum.
        assertEquals("e35e373d78b68ee50369834ed4ce224d3372fc0611b416e5c20d72ce5d552120", firmaWompi("referencia", 1000000, "inventado"))
    }
    @Test fun ordenYAprobacionIdempotente() = escenario { b, _, cuenta, p ->
        val orden = p.orden(cuenta, 1000)
        assertTrue(orden.url.startsWith("https://checkout.wompi.co/p/?public-key=pub_test_"))
        assertTrue(orden.url.contains("amount-in-cents=1000000"))
        assertTrue(orden.url.contains("redirect-url=https%3A%2F%2Ffogonapps.github.io%2F%23gracias"))
        assertEquals(1000000L, b.usar { leer("SELECT monto FROM ordenes_pago") { it.getLong(1) }.single() })
        repeat(2) { p.evento(evento(orden.referencia)) }
        assertEquals(listOf(1000), b.usar { leer("SELECT cantidad FROM chispas WHERE motivo='wompi:tx_inventada'") { it.getInt(1) } })
        p.evento(evento(orden.referencia, id = "otra_tx"))
        assertEquals(1, b.usar { leer("SELECT id FROM chispas WHERE motivo LIKE 'wompi:%'") { it.getLong(1) }.size })
    }
    @Test fun montoRechazoYFirma() = escenario { b, _, cuenta, p ->
        val orden = p.orden(cuenta, 1000)
        p.evento(evento(orden.referencia, monto = 999999))
        p.evento(evento(orden.referencia, estado = "DECLINED"))
        assertEquals("DECLINED", b.usar { leer("SELECT estado FROM ordenes_pago") { it.getString(1) }.single() })
        val e = evento(orden.referencia)
        val falso = JsonObject(e + ("timestamp" to JsonPrimitive(124)))
        assertEquals(401, assertFailsWith<Problema> { p.evento(falso) }.codigo)
        assertEquals(0, b.usar { leer("SELECT id FROM chispas WHERE motivo LIKE 'wompi:%'") { it.getLong(1) }.size })
        p.evento(evento("desconocida"))
        b.usar { cambiar("UPDATE ordenes_pago SET paquete=999") }
        p.evento(evento(orden.referencia))
        assertEquals(0, b.usar { leer("SELECT id FROM chispas WHERE motivo LIKE 'wompi:%'") { it.getLong(1) }.size })
    }
    @Test fun estadosFinalesNoDuplicanElAbono() = escenario { b, _, cuenta, p ->
        val orden = p.orden(cuenta, 1000)
        p.evento(evento(orden.referencia, estado = "ERROR"))
        p.evento(evento(orden.referencia))
        p.evento(evento(orden.referencia, estado = "VOIDED"))
        assertEquals("VOIDED", b.usar { leer("SELECT estado FROM ordenes_pago") { it.getString(1) }.single() })
        p.evento(evento(orden.referencia))
        assertEquals(listOf(1000), b.usar { leer("SELECT cantidad FROM chispas WHERE motivo LIKE 'wompi:%'") { it.getInt(1) } })
    }

    @Test fun paquetesLineales() = escenario { b, _, cuenta, p ->
        listOf(1000,2000,5000).forEach { paquete ->
            val orden = p.orden(cuenta, paquete)
            assertEquals(paquete.toLong()*1000, b.usar { leer("SELECT monto FROM ordenes_pago WHERE referencia=?", orden.referencia) { it.getLong(1) }.single() })
        }
        assertEquals(400, assertFailsWith<Problema> { p.orden(cuenta, 999) }.codigo)
    }
    @Test fun sinVariables() = escenario { b, _, _, _ ->
        assertNull(ConfiguracionWompi.entorno { null })
        assertNull(ConfiguracionWompi.entorno { if (it == "WOMPI_AMBIENTE") "prod" else "inventado" })
        assertEquals(503, assertFailsWith<Problema> { PagosWompi(b, null).comprobar() }.codigo)
        val sandbox = ConfiguracionWompi.entorno(mapOf("WOMPI_AMBIENTE" to "pruebas", "WOMPI_LLAVE_PUBLICA" to "pub_test_x",
            "WOMPI_SECRETO_INTEGRIDAD" to "i", "WOMPI_SECRETO_EVENTOS" to "e", "WOMPI_LLAVE_PRIVADA" to "prv_test_x")::get)
        assertNotNull(sandbox)
        val soloEquipo = PagosWompi(b, sandbox, delEquipo = { it == 1L })
        assertEquals(503, assertFailsWith<Problema> { soloEquipo.orden(2L, 1000) }.codigo)
        assertEquals(503, assertFailsWith<Problema> { soloEquipo.comprobar(2L) }.codigo)
    }
    @Test fun rutasConSesionYEventosFueraDelCupoGeneral() = escenario { b, social, _, pagos ->
        val token = social.crearCuenta(NuevaCuenta("beto")).token
        testApplication {
            application { fogonSocial(social, pagos = pagos) }
            val sinSesion = client.post("/v1/pagos/wompi/orden") {
                contentType(ContentType.Application.Json); setBody("""{"paquete":1000}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, sinSesion.status)
            val respuesta = client.post("/v1/pagos/wompi/orden") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody("""{"paquete":2000}""")
            }
            assertEquals(HttpStatusCode.OK, respuesta.status)
            val referencia = b.usar { leer("SELECT referencia FROM ordenes_pago WHERE paquete=2000") { it.getString(1) }.single() }
            // Agota el cupo de la IP anónima: el webhook debe seguir entrando.
            repeat(125) { client.get("/v1/no-existe") }
            assertEquals(HttpStatusCode.TooManyRequests, client.get("/v1/no-existe").status)
            repeat(2) {
                assertEquals(HttpStatusCode.OK, client.post("/v1/pagos/wompi/eventos") {
                    contentType(ContentType.Application.Json); setBody(evento(referencia, 2000000).toString())
                }.status)
            }
            assertEquals(listOf(2000), b.usar { leer("SELECT cantidad FROM chispas WHERE motivo LIKE 'wompi:%'") { it.getInt(1) } })
            val falso = JsonObject(evento(referencia, 2000000) + ("timestamp" to JsonPrimitive(999)))
            assertEquals(HttpStatusCode.Unauthorized, client.post("/v1/pagos/wompi/eventos") {
                contentType(ContentType.Application.Json); setBody(falso.toString())
            }.status)
        }
    }

    @Test fun rutasApagadasYFirmaInvalida() = escenario { b, social, _, pagos ->
        testApplication {
            application { fogonSocial(social, pagos = PagosWompi(b, null)) }
            for (ruta in listOf("orden", "eventos")) {
                val respuesta = client.post("/v1/pagos/wompi/$ruta") { contentType(ContentType.Application.Json); setBody("{}") }
                assertEquals(HttpStatusCode.ServiceUnavailable, respuesta.status)
            }
        }
        testApplication {
            application { fogonSocial(social, pagos = pagos) }
            val respuesta = client.post("/v1/pagos/wompi/eventos") { contentType(ContentType.Application.Json); setBody("{}") }
            assertEquals(HttpStatusCode.Unauthorized, respuesta.status)
        }
    }
}

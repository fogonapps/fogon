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
    private fun evento(ref: String, monto: Long = 500000, estado: String = "APPROVED", id: String = "tx_inventada"): JsonObject {
        // Construcción independiente: campos en un orden distinto al ejemplo de Wompi.
        val suma = java.security.MessageDigest.getInstance("SHA-256")
            .digest("${monto}${id}${estado}123eventos_inventados".toByteArray())
            .joinToString("") { "%02X".format(it) }
        return Json.parseToJsonElement("""{"event":"transaction.updated","environment":"test","timestamp":123,
            "data":{"transaction":{"id":"$id","reference":"$ref","status":"$estado","amount_in_cents":$monto,"currency":"COP"}},
            "signature":{"properties":["transaction.amount_in_cents","transaction.id","transaction.status"],"checksum":"$suma"}}""").jsonObject
    }
    @Test fun conciliaAprobacionYWebhookEnAmbosOrdenes() = escenario { b, _, cuenta, webhook ->
        for (webhookPrimero in listOf(false, true)) {
            val orden = webhook.orden(cuenta, 5000)
            val id = "tx_$webhookPrimero"
            val p = PagosWompi(b, configuracion, consultar = { ref ->
                listOf(TransaccionWompi(ref, id, "APPROVED", 5000000, "COP"))
            })
            if (webhookPrimero) webhook.evento(evento(orden.referencia, 5000000, id = id))
            p.conciliar(cuenta)
            assertEquals(listOf(5750), b.usar { leer("SELECT cantidad FROM chispas WHERE motivo=?", "wompi:$id") { it.getInt(1) } })
            webhook.evento(evento(orden.referencia, 5000000, id = id))
            assertEquals(listOf(5750), b.usar { leer("SELECT cantidad FROM chispas WHERE motivo=?", "wompi:$id") { it.getInt(1) } })
        }
    }

    @Test fun conciliacionValidaMontoMonedaYEstados() = escenario { b, _, cuenta, webhook ->
        for ((estado, monto, moneda) in listOf(Triple("APPROVED", 999L, "COP"), Triple("APPROVED", 500000L, "USD"),
            Triple("ERROR", 500000L, "COP"), Triple("DECLINED", 500000L, "COP"), Triple("VOIDED", 500000L, "COP"))) {
            val orden = webhook.orden(cuenta, 500)
            PagosWompi(b, configuracion, consultar = { ref ->
                if (ref == orden.referencia) listOf(TransaccionWompi(ref, "tx_$estado", estado, monto, moneda)) else emptyList()
            }).conciliar(cuenta)
            assertEquals(if (estado == "APPROVED") "pendiente" else estado,
                b.usar { leer("SELECT estado FROM ordenes_pago WHERE referencia=?", orden.referencia) { it.getString(1) }.single() })
        }
        assertTrue(b.usar { leer("SELECT cantidad FROM chispas WHERE motivo LIKE 'wompi:%'") { it.getInt(1) } }.isEmpty())
    }

    @Test fun conciliacionFiltraCuentaAntiguedadReferenciaYLimitaIntentos() = escenario { b, social, cuenta, webhook ->
        val otra = social.cuentaDe(social.crearCuenta(NuevaCuenta("otra")).token)!!
        webhook.orden(otra, 500)
        val vieja = webhook.orden(cuenta, 500)
        b.usar { cambiar("UPDATE ordenes_pago SET cuando=0 WHERE referencia=?", vieja.referencia) }
        val nueva = webhook.orden(cuenta, 500)
        val consultas = mutableListOf<String>()
        val p = PagosWompi(b, configuracion, consultar = { ref ->
            consultas.add(ref)
            listOf(TransaccionWompi(vieja.referencia, "ajena", "APPROVED", 500000, "COP"))
        })
        repeat(2) { p.conciliar(cuenta) }
        assertEquals(listOf(nueva.referencia), consultas)
        assertTrue(b.usar { leer("SELECT cantidad FROM chispas WHERE motivo LIKE 'wompi:%'") { it.getInt(1) } }.isEmpty())
        PagosWompi(b, configuracion, consultar = { throw java.io.IOException("sin conexión") }).conciliar(cuenta)
    }

    @Test fun ambientesDeWebhookYConfiguracion() = escenario { b, _, cuenta, _ ->
        for (ambiente in listOf("test", "prod")) {
            val c = ConfiguracionWompi.entorno(mapOf("WOMPI_AMBIENTE" to ambiente, "WOMPI_LLAVE_PUBLICA" to "pub_${ambiente}_x",
                "WOMPI_SECRETO_INTEGRIDAD" to "i", "WOMPI_SECRETO_EVENTOS" to "eventos_inventados", "WOMPI_LLAVE_PRIVADA" to "prv_${ambiente}_x")::get)!!
            assertEquals(ambiente, c.ambiente)
            assertTrue(c.api.contains(if (ambiente == "test") "sandbox" else "production"))
            val p = PagosWompi(b, c)
            val orden = p.orden(cuenta, 500)
            val e = evento(orden.referencia, id = "tx_$ambiente")
            p.evento(JsonObject(e + ("environment" to JsonPrimitive(if (ambiente == "test") "prod" else "test"))))
            assertEquals(0, b.usar { leer("SELECT acreditada FROM ordenes_pago WHERE referencia=?", orden.referencia) { it.getInt(1) }.single() })
            p.evento(JsonObject(e + ("environment" to JsonPrimitive(ambiente))))
            assertEquals(1, b.usar { leer("SELECT acreditada FROM ordenes_pago WHERE referencia=?", orden.referencia) { it.getInt(1) }.single() })
        }
    }

    @Test fun tiendaConciliaAntesDeResponderYToleraFalloDeRed() = escenario { b, social, _, _ ->
        val sesion = social.crearCuenta(NuevaCuenta("compradora"))
        val cuenta = social.cuentaDe(sesion.token)!!
        val pagos = PagosWompi(b, configuracion, consultar = { ref ->
            listOf(TransaccionWompi(ref, "tx_tienda", "APPROVED", 5000000, "COP"))
        })
        pagos.orden(cuenta, 5000)
        testApplication {
            application { fogonSocial(social, pagos = pagos) }
            assertEquals(HttpStatusCode.OK, client.get("/v1/tienda/temas") { bearerAuth(sesion.token) }.status)
            assertEquals(listOf(5750), b.usar { leer("SELECT cantidad FROM chispas WHERE motivo='wompi:tx_tienda'") { it.getInt(1) } })
        }
        val sinRed = PagosWompi(b, configuracion, consultar = { throw java.io.IOException("sin conexión") })
        sinRed.orden(cuenta, 500)
        testApplication {
            application { fogonSocial(social, pagos = sinRed) }
            assertEquals(HttpStatusCode.OK, client.get("/v1/tienda/temas") { bearerAuth(sesion.token) }.status)
        }
    }

    @Test fun vectorIntegridad() {
        // Vector fijado mediante printf 'referencia1000000COPinventado' | sha256sum.
        assertEquals("e35e373d78b68ee50369834ed4ce224d3372fc0611b416e5c20d72ce5d552120", firmaWompi("referencia", 1000000, "inventado"))
    }
    @Test fun ordenYAprobacionIdempotente() = escenario { b, _, cuenta, p ->
        val orden = p.orden(cuenta, 500)
        assertTrue(orden.url.startsWith("https://checkout.wompi.co/p/?public-key=pub_test_"))
        assertTrue(orden.url.contains("amount-in-cents=500000"))
        assertTrue(orden.url.contains("redirect-url=https%3A%2F%2Ffogonapps.github.io%2F%23gracias"))
        assertEquals(500000L, b.usar { leer("SELECT monto FROM ordenes_pago") { it.getLong(1) }.single() })
        repeat(2) { p.evento(evento(orden.referencia)) }
        assertEquals(listOf(500), b.usar { leer("SELECT cantidad FROM chispas WHERE motivo='wompi:tx_inventada'") { it.getInt(1) } })
        p.evento(evento(orden.referencia, id = "otra_tx"))
        assertEquals(1, b.usar { leer("SELECT id FROM chispas WHERE motivo LIKE 'wompi:%'") { it.getLong(1) }.size })
    }
    @Test fun montoRechazoYFirma() = escenario { b, _, cuenta, p ->
        val orden = p.orden(cuenta, 500)
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
        val orden = p.orden(cuenta, 500)
        p.evento(evento(orden.referencia, estado = "ERROR"))
        p.evento(evento(orden.referencia))
        p.evento(evento(orden.referencia, estado = "VOIDED"))
        assertEquals("VOIDED", b.usar { leer("SELECT estado FROM ordenes_pago") { it.getString(1) }.single() })
        p.evento(evento(orden.referencia))
        assertEquals(listOf(500), b.usar { leer("SELECT cantidad FROM chispas WHERE motivo LIKE 'wompi:%'") { it.getInt(1) } })
    }

    @Test fun paquetesLineales() = escenario { b, _, cuenta, p ->
        listOf(500,1000,2500,5000).forEach { paquete ->
            val orden = p.orden(cuenta, paquete)
            assertEquals(paquete.toLong()*1000, b.usar { leer("SELECT monto FROM ordenes_pago WHERE referencia=?", orden.referencia) { it.getLong(1) }.single() })
        }
        assertEquals(400, assertFailsWith<Problema> { p.orden(cuenta, 999) }.codigo)
    }
    /** JsonSocial omite valores por defecto: la oferta debe llevar la lista o las apps fallan al leerla (cierre de la tienda, 2026-11-16). */
    @Test fun ofertaSiempreLlevaPaquetesYRegalo() {
        val json = Json.parseToJsonElement(JsonSocial.encodeToString(PaquetesWompi.serializer(), ofertaWompi())).jsonObject
        assertEquals(listOf(500, 1000, 2500, 5000), json.getValue("paquetes").jsonArray.map { it.jsonPrimitive.int })
        assertEquals(750, json.getValue("regalo").jsonObject.getValue("5000").jsonPrimitive.int)
        // Los mismos paquetes y el mismo regalo que en Play.
        assertEquals(CATALOGO_PLAY.filterNot { it.unico }.map { it.chispas }, PAQUETES_WOMPI)
        CATALOGO_PLAY.filterNot { it.unico }.forEach { assertEquals(it.regalo, REGALO_WOMPI[it.chispas] ?: 0) }
    }
    @Test fun soloElEquipoEnPruebasYTodosEnProduccion() = escenario { b, _, cuenta, _ ->
        val pruebas = PagosWompi(b, ConfiguracionWompi("pub_test_x", "i", "e", "prv_test_x"), delEquipo = { false })
        assertEquals(503, assertFailsWith<Problema> { pruebas.comprobar(cuenta) }.codigo)
        val produccion = PagosWompi(b, ConfiguracionWompi("pub_prod_x", "i", "e", "prv_prod_x"), delEquipo = { false })
        assertEquals("prod", produccion.comprobar(cuenta).ambiente)
    }
    @Test fun losPaquetesGrandesTraenRegaloYLasOrdenesViejasSeAcreditan() = escenario { b, _, cuenta, p ->
        val orden = p.orden(cuenta, 5000)
        p.evento(evento(orden.referencia, monto = 5000L * 1000))
        assertEquals(listOf(5750), b.usar { leer("SELECT cantidad FROM chispas WHERE motivo LIKE 'wompi:%'") { it.getInt(1) } })
        assertEquals(400, assertFailsWith<Problema> { p.orden(cuenta, 10000) }.codigo)
        // Una orden de 10.000 hecha antes del cambio se acredita con su regalo de entonces.
        b.usar { cambiar("INSERT INTO ordenes_pago(referencia,cuenta,paquete,monto,cuando) VALUES(?,?,?,?,?)", "vieja", cuenta, 10000, 10000L * 1000, System.currentTimeMillis()) }
        p.evento(evento("vieja", id = "tx-vieja", monto = 10000L * 1000))
        assertEquals(listOf(5750, 11500), b.usar { leer("SELECT cantidad FROM chispas WHERE motivo LIKE 'wompi:%' ORDER BY id") { it.getInt(1) } })
        // Una de 5.000 de antes del cambio (sin regalo guardado) recibe el regalo de entonces (500), no el nuevo (750).
        b.usar { cambiar("INSERT INTO ordenes_pago(referencia,cuenta,paquete,monto,cuando) VALUES(?,?,?,?,?)", "vieja5000", cuenta, 5000, 5000L * 1000, System.currentTimeMillis()) }
        p.evento(evento("vieja5000", id = "tx-vieja5000", monto = 5000L * 1000))
        assertEquals(listOf(5750, 11500, 5500), b.usar { leer("SELECT cantidad FROM chispas WHERE motivo LIKE 'wompi:%' ORDER BY id") { it.getInt(1) } })
        assertEquals(750, b.usar { leer("SELECT regalo FROM ordenes_pago WHERE referencia = ?", orden.referencia) { it.getInt(1) }.single() })
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
                bearerAuth(token); contentType(ContentType.Application.Json); setBody("""{"paquete":500}""")
            }
            assertEquals(HttpStatusCode.OK, respuesta.status)
            val referencia = b.usar { leer("SELECT referencia FROM ordenes_pago WHERE paquete=500") { it.getString(1) }.single() }
            // Agota el cupo de la IP anónima: el webhook debe seguir entrando.
            repeat(125) { client.get("/v1/no-existe") }
            assertEquals(HttpStatusCode.TooManyRequests, client.get("/v1/no-existe").status)
            repeat(2) {
                assertEquals(HttpStatusCode.OK, client.post("/v1/pagos/wompi/eventos") {
                    contentType(ContentType.Application.Json); setBody(evento(referencia, 500000).toString())
                }.status)
            }
            assertEquals(listOf(500), b.usar { leer("SELECT cantidad FROM chispas WHERE motivo LIKE 'wompi:%'") { it.getInt(1) } })
            val falso = JsonObject(evento(referencia, 500000) + ("timestamp" to JsonPrimitive(999)))
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

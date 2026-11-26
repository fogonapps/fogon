package io.github.fogonapps.social

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.*
import kotlin.test.*
import java.nio.file.Files

class PagosPlayTest {
    private val paquete = "io.github.fogonapps.cajon"
    private val tokenCompra = "token-de-compra-inventado.1234567890"

    /** Google simulado: lo que dice de cada token, y cuántas veces se le preguntó. */
    private class GoogleFalso : VerificadorPlay {
        val compras = mutableMapOf<String, CompraGoogle>()
        var falla = false
        var consultas = 0
        override fun consultar(paquete: String, producto: String, token: String): CompraGoogle? {
            consultas++
            if (falla) throw java.io.IOException("sin conexión")
            return compras[token]
        }
    }

    private fun escenario(prueba: (Base, Social, Long, GoogleFalso, PagosPlay) -> Unit) {
        val dir = Files.createTempDirectory("play").toFile()
        val base = Base(dir.resolve("base.db").path)
        try {
            val social = Social(base, dir.resolve("fotos"))
            val cuenta = social.cuentaDe(social.crearCuenta(NuevaCuenta("ana")).token)!!
            val google = GoogleFalso()
            prueba(base, social, cuenta, google, PagosPlay(base, google, social::idPlay))
        } finally { base.cerrar(); dir.deleteRecursively() }
    }

    private fun Base.compradas(): List<Int> = usar { leer("SELECT cantidad FROM chispas WHERE motivo LIKE 'play%' ORDER BY id") { it.getInt(1) } }

    @Test fun acreditaUnaSolaVezPorToken() = escenario { b, social, cuenta, google, pagos ->
        google.compras[tokenCompra] = CompraGoogle(0, "GPA.1234-5678", social.idPlay(cuenta), prueba = false)
        assertEquals("acreditada", pagos.verificar(cuenta, PedidoPlay(paquete, "chispas_1000", tokenCompra)))
        // La app se cerró antes de consumir: al volver a mandarla, responde lo mismo sin acreditar otra vez.
        assertEquals("acreditada", pagos.verificar(cuenta, PedidoPlay(paquete, "chispas_1000", tokenCompra)))
        assertEquals(listOf(1050), b.compradas())
        assertEquals(1, google.consultas)
        assertEquals(listOf("play:GPA.1234-5678"), b.usar { leer("SELECT motivo FROM chispas WHERE motivo LIKE 'play%'") { it.getString(1) } })
    }

    @Test fun pendienteNoAcreditaHastaQueSeCobra() = escenario { b, social, cuenta, google, pagos ->
        google.compras[tokenCompra] = CompraGoogle(2, "", social.idPlay(cuenta), prueba = false)
        assertEquals("pendiente", pagos.verificar(cuenta, PedidoPlay(paquete, "chispas_500", tokenCompra)))
        assertTrue(b.compradas().isEmpty())
        google.compras[tokenCompra] = CompraGoogle(0, "GPA.9", social.idPlay(cuenta), prueba = false)
        assertEquals("acreditada", pagos.verificar(cuenta, PedidoPlay(paquete, "chispas_500", tokenCompra)))
        assertEquals(listOf(500), b.compradas())
    }

    @Test fun rechazaCanceladasDesconocidasYAjenas() = escenario { b, social, cuenta, google, pagos ->
        val otra = social.cuentaDe(social.crearCuenta(NuevaCuenta("beto")).token)!!
        google.compras["cancelada-0123456789"] = CompraGoogle(1, "GPA.1", social.idPlay(cuenta), false)
        assertEquals("compra_cancelada", assertFailsWith<Problema> { pagos.verificar(cuenta, PedidoPlay(paquete, "chispas_500", "cancelada-0123456789")) }.mensaje)
        assertEquals("compra_invalida", assertFailsWith<Problema> { pagos.verificar(cuenta, PedidoPlay(paquete, "chispas_500", "desconocida-0123456789")) }.mensaje)
        // Hecha desde otra cuenta de Fogón (el identificador opaco no coincide).
        google.compras[tokenCompra] = CompraGoogle(0, "GPA.2", social.idPlay(otra), false)
        assertEquals(403, assertFailsWith<Problema> { pagos.verificar(cuenta, PedidoPlay(paquete, "chispas_500", tokenCompra)) }.codigo)
        // La dueña la acredita; otra cuenta ya no puede reclamar el mismo token.
        assertEquals("acreditada", pagos.verificar(otra, PedidoPlay(paquete, "chispas_500", tokenCompra)))
        assertEquals(409, assertFailsWith<Problema> { pagos.verificar(cuenta, PedidoPlay(paquete, "chispas_500", tokenCompra)) }.codigo)
        assertEquals(listOf(500), b.compradas())
    }

    @Test fun validaProductoPaqueteYToken() = escenario { _, _, cuenta, google, pagos ->
        assertEquals("producto", assertFailsWith<Problema> { pagos.verificar(cuenta, PedidoPlay(paquete, "chispas_999999", tokenCompra)) }.mensaje)
        assertEquals("paquete", assertFailsWith<Problema> { pagos.verificar(cuenta, PedidoPlay("com.otra.app", "chispas_500", tokenCompra)) }.mensaje)
        assertEquals("token", assertFailsWith<Problema> { pagos.verificar(cuenta, PedidoPlay(paquete, "chispas_500", "corto")) }.mensaje)
        assertEquals("token", assertFailsWith<Problema> { pagos.verificar(cuenta, PedidoPlay(paquete, "chispas_500", "con espacio y /barras/")) }.mensaje)
        assertEquals(0, google.consultas)
    }

    @Test fun googleCaidoNoAcreditaYSePuedeReintentar() = escenario { b, social, cuenta, google, pagos ->
        google.compras[tokenCompra] = CompraGoogle(0, "GPA.3", social.idPlay(cuenta), false)
        google.falla = true
        assertEquals(502, assertFailsWith<Problema> { pagos.verificar(cuenta, PedidoPlay(paquete, "chispas_2500", tokenCompra)) }.codigo)
        assertTrue(b.compradas().isEmpty())
        google.falla = false
        assertEquals("acreditada", pagos.verificar(cuenta, PedidoPlay(paquete, "chispas_2500", tokenCompra)))
        assertEquals(listOf(2750), b.compradas())
    }

    @Test fun pruebasYCantidadYApoyo() = escenario { b, social, cuenta, google, pagos ->
        google.compras["prueba-0123456789"] = CompraGoogle(0, "GPA.4", social.idPlay(cuenta), prueba = true, cantidad = 3)
        pagos.verificar(cuenta, PedidoPlay(paquete, "chispas_500", "prueba-0123456789"))
        google.compras["apoyo-0123456789"] = CompraGoogle(0, "GPA.5", social.idPlay(cuenta), prueba = false, cantidad = 2)
        pagos.verificar(cuenta, PedidoPlay(paquete, "apoyo_fogon", "apoyo-0123456789"))
        assertEquals(listOf(1500, 5750), b.compradas())
        assertEquals(listOf("play_prueba:GPA.4", "play:GPA.5"), b.usar { leer("SELECT motivo FROM chispas WHERE motivo LIKE 'play%' ORDER BY id") { it.getString(1) } })
    }

    @Test fun sinIdentificadorDeCuentaSeRechaza() = escenario { b, _, cuenta, google, pagos ->
        google.compras[tokenCompra] = CompraGoogle(0, "GPA.8", "", false)
        val e = assertFailsWith<Problema> { pagos.verificar(cuenta, PedidoPlay(paquete, "chispas_500", tokenCompra)) }
        assertEquals(400, e.codigo); assertEquals("compra_sin_cuenta", e.mensaje)
        google.compras[tokenCompra] = CompraGoogle(0, "GPA.8", "   ", false)
        assertEquals("compra_sin_cuenta", assertFailsWith<Problema> { pagos.verificar(cuenta, PedidoPlay(paquete, "chispas_500", tokenCompra)) }.mensaje)
        assertTrue(b.compradas().isEmpty())
        assertTrue(b.usar { leer("SELECT 1 FROM compras_play") { 1 } }.isEmpty())
    }

    @Test fun cuentaBorradaNoLiberaElToken() = escenario { b, social, cuenta, google, pagos ->
        google.compras[tokenCompra] = CompraGoogle(0, "GPA.6", social.idPlay(cuenta), false)
        pagos.verificar(cuenta, PedidoPlay(paquete, "chispas_500", tokenCompra))
        b.usar { cambiar("DELETE FROM cuentas WHERE id=?", cuenta) }
        val otra = social.cuentaDe(social.crearCuenta(NuevaCuenta("beto")).token)!!
        assertEquals(409, assertFailsWith<Problema> { pagos.verificar(otra, PedidoPlay(paquete, "chispas_500", tokenCompra)) }.codigo)
    }

    @Test fun idPlayEsOpacoYEstable() = escenario { _, social, cuenta, _, _ ->
        val id = social.idPlay(cuenta)
        assertEquals(id, social.idPlay(cuenta))
        assertNotEquals(id, social.idPlay(cuenta + 1))
        assertTrue(id.length in 20..64 && !id.contains(cuenta.toString().padStart(3, 'x')))
    }

    @Test fun leeLaRespuestaDeGoogle() {
        val g = leerCompraGoogle(Json.parseToJsonElement("""{"kind":"androidpublisher#productPurchase","purchaseTimeMillis":"1","purchaseState":0,
            "consumptionState":0,"orderId":"GPA.7","purchaseType":0,"acknowledgementState":0,"obfuscatedExternalAccountId":"abc","regionCode":"CO"}""").jsonObject)
        assertEquals(CompraGoogle(0, "GPA.7", "abc", prueba = true, cantidad = 1), g)
        assertFalse(leerCompraGoogle(buildJsonObject { put("purchaseState", 0) }).prueba)
    }

    @Test fun cuentaDeServicioSeLeeSinRed() {
        val par = java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val pem = "-----BEGIN PRIVATE KEY-----\n" + java.util.Base64.getMimeEncoder().encodeToString(par.private.encoded) + "\n-----END PRIVATE KEY-----\n"
        val json = buildJsonObject { put("client_email", "inventada@ejemplo.iam.gserviceaccount.com"); put("private_key", pem) }.toString()
        GooglePlayApi(json)
    }

    @Test fun rutas() = escenario { b, social, _, google, pagos ->
        val token = social.crearCuenta(NuevaCuenta("carla")).token
        val carla = social.cuentaDe(token)!!
        google.compras[tokenCompra] = CompraGoogle(0, "GPA.8", social.idPlay(carla), false)
        testApplication {
            application { fogonSocial(social, pagosPlay = pagos) }
            assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/pagos/play/paquetes").status)
            val oferta = client.get("/v1/pagos/play/paquetes") { bearerAuth(token) }
            assertEquals(HttpStatusCode.OK, oferta.status)
            val json = Json.parseToJsonElement(oferta.bodyAsText()).jsonObject
            assertEquals(listOf("chispas_500", "chispas_1000", "chispas_2500", "chispas_5000", "apoyo_fogon"),
                json.getValue("productos").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content })
            assertEquals(social.idPlay(carla), json.getValue("cuenta").jsonPrimitive.content)
            // Todos los campos viajan aunque valgan 0 o false.
            assertEquals(setOf("id", "chispas", "regalo", "unico"), json.getValue("productos").jsonArray[0].jsonObject.keys)
            val pedido = """{"paquete":"$paquete","producto":"chispas_1000","token":"$tokenCompra"}"""
            assertEquals(HttpStatusCode.Unauthorized, client.post("/v1/pagos/play/verificar") { contentType(ContentType.Application.Json); setBody(pedido) }.status)
            val saldoAntes = social.chispas(carla)
            val r = client.post("/v1/pagos/play/verificar") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(pedido) }
            assertEquals(HttpStatusCode.OK, r.status)
            val cuerpo = Json.parseToJsonElement(r.bodyAsText()).jsonObject
            assertEquals("acreditada", cuerpo.getValue("estado").jsonPrimitive.content)
            assertEquals(saldoAntes + 1050, cuerpo.getValue("chispas").jsonPrimitive.int)
        }
        testApplication {
            application { fogonSocial(social, pagosPlay = PagosPlay(b, null, social::idPlay)) }
            assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/v1/pagos/play/paquetes") { bearerAuth(token) }.status)
            val r = client.post("/v1/pagos/play/verificar") { bearerAuth(token); contentType(ContentType.Application.Json)
                setBody("""{"paquete":"$paquete","producto":"chispas_500","token":"$tokenCompra"}""") }
            assertEquals(HttpStatusCode.ServiceUnavailable, r.status)
            assertTrue("pagos_apagados" in r.bodyAsText())
        }
    }

    /** Las compras que Google anuló, simuladas: cuántas veces se le preguntó y desde cuándo. */
    private class AnuladasFalsas : AnuladasPlay {
        val lista = mutableListOf<CompraAnulada>()
        val desdes = mutableListOf<Long>()
        override fun anuladas(paquete: String, desde: Long): List<CompraAnulada> { desdes += desde; return lista.toList() }
    }

    @Test fun unaCompraAnuladaDescuentaSinSaldoNegativoYDejaDeuda() = escenario { b, social, cuenta, google, _ ->
        val anuladas = AnuladasFalsas()
        var reloj = 2_000_000_000_000L
        val pagos = PagosPlay(b, google, social::idPlay, ahora = { reloj }, anuladas = anuladas)
        social.chispas(cuenta)                                                   // la bienvenida (500)
        google.compras[tokenCompra] = CompraGoogle(0, "GPA.10", social.idPlay(cuenta), false)
        pagos.verificar(cuenta, PedidoPlay(paquete, "chispas_5000", tokenCompra))
        assertEquals(500 + 5750, social.chispas(cuenta))
        // Gasta casi todo antes del reembolso.
        b.usar { cambiar("INSERT INTO chispas(cuenta,cantidad,motivo,cuando) VALUES(?,-6000,'compra:prueba',?)", cuenta, reloj) }
        assertEquals(250, social.chispas(cuenta))
        anuladas.lista += CompraAnulada(tokenCompra, "GPA.10")
        anuladas.lista += CompraAnulada("token-que-no-es-de-fogon-123", "GPA.X")
        assertEquals(1, pagos.revisarAnuladas())
        assertEquals(reloj - 30L * 24 * 3600_000 + 3600_000, anuladas.desdes.single())
        // El saldo queda en 0 (no negativo); el ajuste está en el historial y lo que faltó, en deuda.
        assertEquals(0, social.chispas(cuenta))
        assertEquals(listOf(-250L), b.usar { leer("SELECT cantidad FROM chispas WHERE motivo = 'play_anulada:GPA.10'") { it.getLong(1) } })
        assertEquals(5500L, b.usar { leer("SELECT cantidad FROM deudas_chispas WHERE cuenta = ?", cuenta) { it.getLong(1) }.single() })
        // Otra pasada no la vuelve a descontar.
        assertEquals(0, pagos.revisarAnuladas())
        // Con deuda no compra nada con chispas, aunque reciba algunas: primero saldan la deuda.
        b.usar { registrarChispas(cuenta, 5000, "aporte:prueba", reloj) }
        assertEquals(0, social.chispas(cuenta))
        assertEquals(500L, b.usar { leer("SELECT cantidad FROM deudas_chispas WHERE cuenta = ?", cuenta) { it.getLong(1) }.single() })
        assertEquals("deuda_chispas", assertFailsWith<Problema> { social.reclamarTema(cuenta, "marco:doble") }.mensaje)
        b.usar { registrarChispas(cuenta, 1500, "aporte:prueba2", reloj) }
        // Ya saldada (1500 − 500): vuelve a comprar.
        assertEquals(1000, social.chispas(cuenta))
        assertTrue(b.usar { leer("SELECT 1 FROM deudas_chispas WHERE cuenta = ?", cuenta) { 1 } }.isEmpty())
        social.reclamarTema(cuenta, "marco:doble")
        assertEquals(600, social.chispas(cuenta))
        assertEquals(listOf(-5000L, -500L), b.usar { leer("SELECT cantidad FROM chispas WHERE cuenta = ? AND motivo = 'deuda_play' ORDER BY id", cuenta) { it.getLong(1) } })
    }

    @Test fun unReembolsoParcialDescuentaEnProporcion() = escenario { b, social, cuenta, google, _ ->
        val anuladas = AnuladasFalsas()
        val pagos = PagosPlay(b, google, social::idPlay, ahora = { 2_000_000_000_000L }, anuladas = anuladas)
        social.chispas(cuenta)                                                   // la bienvenida (500)
        // 4 paquetes de 1000 (+50 de regalo cada uno): 4200 chispas.
        google.compras[tokenCompra] = CompraGoogle(0, "GPA.20", social.idPlay(cuenta), false, cantidad = 4)
        pagos.verificar(cuenta, PedidoPlay(paquete, "chispas_1000", tokenCompra))
        assertEquals(500 + 4200, social.chispas(cuenta))
        // Google reembolsa 1 de las 4: se descuenta una cuarta parte, una sola vez.
        anuladas.lista += CompraAnulada(tokenCompra, "GPA.20", cantidad = 1, cuando = 111)
        assertEquals(1, pagos.revisarAnuladas())
        assertEquals(500 + 3150, social.chispas(cuenta))
        assertEquals(0, pagos.revisarAnuladas())
        assertEquals(500 + 3150, social.chispas(cuenta))
        // Un segundo reembolso parcial (otro momento), de 2 unidades, descuenta dos cuartas partes.
        anuladas.lista += CompraAnulada(tokenCompra, "GPA.20", cantidad = 2, cuando = 222)
        assertEquals(1, pagos.revisarAnuladas())
        assertEquals(500 + 1050, social.chispas(cuenta))
        // Si después se anula entera, solo se descuenta lo que quedaba (nunca más que la compra).
        anuladas.lista += CompraAnulada(tokenCompra, "GPA.20", cuando = 333)
        assertEquals(1, pagos.revisarAnuladas())
        assertEquals(500, social.chispas(cuenta))
        assertEquals(0, pagos.revisarAnuladas())
        assertEquals(listOf(-1050L, -2100L, -1050L), b.usar { leer("SELECT cantidad FROM chispas WHERE motivo LIKE 'play_anulada:%' ORDER BY id") { it.getLong(1) } })
        // Google manda la cantidad y el momento de la anulación.
        assertEquals(CompraAnulada("t2", "GPA.2", 3, 5), leerAnuladas(Json.parseToJsonElement(
            """{"voidedPurchases":[{"purchaseToken":"t2","orderId":"GPA.2","voidedTimeMillis":"5","voidedQuantity":3}]}""").jsonObject).single())
    }

    @Test fun conDeudaNoSeCompraConChispas() = escenario { b, social, cuenta, _, _ ->
        social.chispas(cuenta)
        b.usar { cambiar("INSERT INTO chispas(cuenta,cantidad,motivo,cuando) VALUES(?,-500,'compra:prueba',1)", cuenta)
            cambiar("INSERT INTO deudas_chispas VALUES(?, 100, 1)", cuenta) }
        assertEquals("deuda_chispas", assertFailsWith<Problema> { social.reclamarTema(cuenta, "marco:doble") }.mensaje)
        social.crearCuenta(NuevaCuenta("beto"))
        assertEquals("deuda_chispas", assertFailsWith<Problema> { social.regalar(cuenta, Regalo("marco:doble", "beto")) }.mensaje)
    }

    @Test fun sinCuentaDeServicioLaRevisionQuedaApagada() = escenario { b, social, _, _, _ ->
        assertEquals(0, PagosPlay(b, null, social::idPlay).revisarAnuladas())
        // Una cuenta de servicio de verdad sirve para las dos cosas.
        val par = java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val pem = "-----BEGIN PRIVATE KEY-----\n" + java.util.Base64.getMimeEncoder().encodeToString(par.private.encoded) + "\n-----END PRIVATE KEY-----\n"
        val api: VerificadorPlay = GooglePlayApi(buildJsonObject { put("client_email", "x@ejemplo.iam.gserviceaccount.com"); put("private_key", pem) }.toString())
        assertTrue(api is AnuladasPlay)
    }

    @Test fun leeLasAnuladasDeGoogle() {
        val o = Json.parseToJsonElement("""{"pageInfo":{"totalResults":2},"tokenPagination":{"nextPageToken":"x"},"voidedPurchases":[
            {"kind":"androidpublisher#voidedPurchase","purchaseToken":"t1","purchaseTimeMillis":"1","voidedTimeMillis":"2","orderId":"GPA.1","voidedSource":0,"voidedReason":1},
            {"kind":"androidpublisher#voidedPurchase","orderId":"GPA.2"}]}""").jsonObject
        assertEquals(listOf(CompraAnulada("t1", "GPA.1", cuando = 2)), leerAnuladas(o))
        assertTrue(leerAnuladas(buildJsonObject {}).isEmpty())
    }

    @Test fun verificarTieneTopePorHora() = escenario { _, social, _, google, pagos ->
        val token = social.crearCuenta(NuevaCuenta("dani")).token
        testApplication {
            application { fogonSocial(social, pagosPlay = pagos) }
            val estados = (1..21).map {
                client.post("/v1/pagos/play/verificar") { bearerAuth(token); contentType(ContentType.Application.Json)
                    setBody("""{"paquete":"$paquete","producto":"chispas_500","token":"desconocida-0123456789-$it"}""") }.status
            }
            assertEquals(List(20) { HttpStatusCode.BadRequest }, estados.take(20))
            assertEquals(HttpStatusCode.TooManyRequests, estados.last())
            assertEquals(20, google.consultas)
        }
    }
}

package io.github.fogonapps.social

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.http.HttpRequest
import java.nio.ByteBuffer
import java.nio.file.Files
import java.util.concurrent.Flow
import kotlin.test.*

/** Steam e IGDB falsos: nada sale a internet. */
class SteamTest {
    private val clave = "CLAVE-SECRETA-DE-STEAM-123"
    private val numero = "76561197960287930"

    private fun cuerpo(p: HttpRequest): String {
        val publicador = p.bodyPublisher().orElse(null) ?: return ""
        val partes = StringBuilder()
        publicador.subscribe(object : Flow.Subscriber<ByteBuffer> {
            override fun onSubscribe(s: Flow.Subscription) { s.request(Long.MAX_VALUE) }
            override fun onNext(b: ByteBuffer) { val a = ByteArray(b.remaining()); b.get(a); partes.append(String(a)) }
            override fun onError(t: Throwable) {}
            override fun onComplete() {}
        })
        return partes.toString()
    }

    private fun parametros(p: HttpRequest): Map<String, String> = p.uri().rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }
        .associate { it.substringBefore('=') to java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8") }

    private inner class Falso(var juegos: Int = 3) {
        val pedidosSteam = mutableListOf<HttpRequest>()
        val consultasIgdb = mutableListOf<String>()
        var resolver: (String) -> Cine.Respuesta = { nombre ->
            if (nombre == "gabelogannewell") Cine.Respuesta(200, """{"response":{"steamid":"$numero","success":1}}""")
            else Cine.Respuesta(200, """{"response":{"success":42,"message":"No match"}}""")
        }
        var biblioteca: (String) -> Cine.Respuesta = { _ ->
            val lista = (1..juegos).joinToString(",") { n ->
                val dos = if (n == 1) ""","playtime_2weeks":30""" else ""
                """{"appid":${n * 10},"name":"Juego $n","playtime_forever":${(n - 1) * 60}$dos,"img_icon_url":"x"}"""
            }
            Cine.Respuesta(200, """{"response":{"game_count":$juegos,"games":[$lista]}}""")
        }
        var igdbResponde: (String) -> Cine.Respuesta = { consulta ->
            // Cada appid que termina en 0 y no en 50 tiene ficha (appid * 100); el 10 tiene dos filas (queda la más baja).
            val uids = Regex("\"([0-9]+)\"").findAll(consulta.substringAfter("uid = (")).map { it.groupValues[1].toLong() }.toList()
            val filas = uids.filter { it % 50 != 0L }.flatMap { u ->
                listOf("""{"id":${u + 1},"game":${u * 100},"uid":"$u"}""") + if (u == 10L) listOf("""{"id":9,"game":999,"uid":"10"}""") else emptyList()
            }
            Cine.Respuesta(200, filas.joinToString(",", "[", "]"))
        }
        /** El jugador existe (para distinguir un perfil privado de un número que no es de nadie). */
        var resumen: (String) -> Cine.Respuesta = { n -> Cine.Respuesta(200, """{"response":{"players":[{"steamid":"$n"}]}}""") }
        /** El reloj de IGDB avanza con cada espera de su fila, sin dormir de verdad. */
        val reloj = java.util.concurrent.atomic.AtomicLong(1_000_000L)
        val igdb = Igdb("cliente", "secreto", ahora = { reloj.get() }, esperar = { reloj.addAndGet(it) }) { p ->
            if (p.uri().host == "id.twitch.tv") Cine.Respuesta(200, """{"access_token":"tok","expires_in":99999}""")
            else { val c = cuerpo(p); consultasIgdb += c; igdbResponde(c) }
        }
        fun steam(k: String? = clave, igdbUsado: Igdb = igdb, maxBytes: Long = Steam.MAX_BYTES, tiempo: Long = Steam.TIEMPO,
                  esperaTurno: Long = Steam.ESPERA_TURNO) = Steam(k, igdbUsado, maxBytes = maxBytes, tiempo = tiempo, esperaTurno = esperaTurno) { p ->
            assertEquals("api.steampowered.com", p.uri().host)
            pedidosSteam += p
            val q = parametros(p)
            flujo(when {
                p.uri().path.endsWith("ResolveVanityURL/v1/") -> resolver(q.getValue("vanityurl"))
                p.uri().path.endsWith("GetOwnedGames/v1/") -> biblioteca(q.getValue("steamid"))
                p.uri().path.endsWith("GetPlayerSummaries/v2/") -> resumen(q.getValue("steamids"))
                else -> error("Ruta inesperada ${p.uri().path}")
            })
        }
    }

    private fun flujo(r: Cine.Respuesta) = Steam.Flujo(r.codigo, r.cuerpo.byteInputStream())

    private fun juegos(r: Cine.Respuesta): List<JsonObject> =
        (JsonSocial.parseToJsonElement(r.cuerpo).jsonObject["juegos"] as JsonArray).map { it.jsonObject }

    @Test fun formasDelPerfil() {
        assertEquals(Steam.Perfil(numero = numero), Steam.perfil("https://steamcommunity.com/profiles/$numero"))
        assertEquals(Steam.Perfil(numero = numero), Steam.perfil("  steamcommunity.com/profiles/$numero/  "))
        assertEquals(Steam.Perfil(numero = numero), Steam.perfil("http://www.steamcommunity.com/profiles/$numero/?l=spanish"))
        assertEquals(Steam.Perfil(numero = numero), Steam.perfil(numero))
        assertEquals(Steam.Perfil(nombre = "gabelogannewell"), Steam.perfil("https://steamcommunity.com/id/gabelogannewell/"))
        assertEquals(Steam.Perfil(nombre = "mi_nombre-2"), Steam.perfil("mi_nombre-2"))
        // Con lo que venga después del perfil: la página de juegos, otra subpágina o una barra al final.
        assertEquals(Steam.Perfil(nombre = "gabe"), Steam.perfil("https://steamcommunity.com/id/gabe/games/?tab=all"))
        assertEquals(Steam.Perfil(nombre = "gabe"), Steam.perfil("https://steamcommunity.com/id/gabe/games"))
        assertEquals(Steam.Perfil(numero = numero), Steam.perfil("https://steamcommunity.com/profiles/$numero/"))
        assertEquals(Steam.Perfil(numero = numero), Steam.perfil("https://steamcommunity.com/profiles/$numero/friends/#x"))
        listOf(null, "", " ", "a", "https://steamcommunity.com/profiles/123", "https://steamcommunity.com/id/con espacio",
            "https://otro.example/id/gabe", "https://steamcommunity.com.malo.example/id/gabe", "gabe\"; drop", "x".repeat(33),
            "https://steamcommunity.com/id/", "steamcommunity.com/profiles/76561197960287930x", "https://steamcommunity.com/id/gabe/games con espacio",
        ).forEach { assertNull(Steam.perfil(it), "perfil $it") }
    }

    @Test fun porEnlaceDeNombre() {
        val f = Falso()
        val r = f.steam().biblioteca("https://steamcommunity.com/id/gabelogannewell")
        assertEquals(200, r.codigo, r.cuerpo)
        assertEquals(2, f.pedidosSteam.size)
        val resolver = parametros(f.pedidosSteam[0])
        assertEquals(mapOf("vanityurl" to "gabelogannewell", "format" to "json", "key" to clave), resolver)
        val pedido = parametros(f.pedidosSteam[1])
        assertEquals(numero, pedido["steamid"])
        assertEquals("1", pedido["include_appinfo"])
        assertEquals("1", pedido["include_played_free_games"])
        assertEquals("0", pedido["skip_unvetted_apps"])
        assertEquals("json", pedido["format"])
        val j = juegos(r)
        assertEquals(3, j.size)
        assertEquals(setOf("appid", "nombre", "minutos", "minutos2semanas", "igdb"), j[0].keys)
        assertEquals("Juego 1", j[0]["nombre"]!!.jsonPrimitive.content)
        assertEquals(0L, j[0]["minutos"]!!.jsonPrimitive.long)
        assertEquals(30L, j[0]["minutos2semanas"]!!.jsonPrimitive.long)
        assertEquals(0L, j[1]["minutos2semanas"]!!.jsonPrimitive.long)
        assertEquals(120L, j[2]["minutos"]!!.jsonPrimitive.long)
        // Dos filas para el mismo appid: queda la ficha de id más bajo.
        assertEquals(999L, j[0]["igdb"]!!.jsonPrimitive.long)
        assertEquals(2000L, j[1]["igdb"]!!.jsonPrimitive.long)
        val consulta = f.consultasIgdb.single()
        assertEquals("fields game,uid; where external_game_source = 1 & uid = (\"10\",\"20\",\"30\"); limit 500;", consulta)
    }

    @Test fun porNumeroNoSeResuelve() {
        listOf("https://steamcommunity.com/profiles/$numero/", numero).forEach { t ->
            val f = Falso()
            assertEquals(200, f.steam().biblioteca(t).codigo)
            assertEquals(1, f.pedidosSteam.size)
            assertTrue(f.pedidosSteam[0].uri().path.endsWith("GetOwnedGames/v1/"))
        }
    }

    @Test fun juegoSinFichaVaConIgdbNull() {
        val f = Falso(juegos = 5)
        val j = juegos(f.steam().biblioteca(numero))
        assertEquals(JsonNull, j.first { it["appid"]!!.jsonPrimitive.long == 50L }["igdb"])
    }

    @Test fun bibliotecaPrivada() {
        val f = Falso()
        f.biblioteca = { Cine.Respuesta(200, """{"response":{}}""") }
        val r = f.steam().biblioteca(numero)
        assertEquals(403, r.codigo)
        assertTrue("biblioteca_privada" in r.cuerpo)
        assertTrue(f.consultasIgdb.isEmpty())
        // Pública y sin juegos: lista vacía, no privada.
        f.biblioteca = { Cine.Respuesta(200, """{"response":{"game_count":0}}""") }
        assertEquals("""{"juegos":[],"total":0}""", f.steam().biblioteca(numero).cuerpo)
    }

    @Test fun nombreQueNoExiste() {
        val f = Falso()
        val r = f.steam().biblioteca("https://steamcommunity.com/id/nadie-se-llama-asi")
        assertEquals(404, r.codigo)
        assertTrue("perfil_no_encontrado" in r.cuerpo)
        assertEquals(1, f.pedidosSteam.size)
    }

    @Test fun perfilInvalidoNoPide() {
        val f = Falso()
        val r = f.steam().biblioteca("https://otro.example/id/gabe")
        assertEquals(400, r.codigo)
        assertTrue("perfil_invalido" in r.cuerpo)
        assertTrue(f.pedidosSteam.isEmpty())
    }

    @Test fun sinClave() {
        val f = Falso()
        listOf(null, "", "  ").forEach { k ->
            val r = f.steam(k).biblioteca(numero)
            assertEquals(503, r.codigo)
            assertTrue("sin_steam" in r.cuerpo)
        }
        assertTrue(f.pedidosSteam.isEmpty())
        // Steam rechaza la clave: también sin_steam.
        f.biblioteca = { Cine.Respuesta(401, "<html>Unauthorized</html>") }
        assertTrue("sin_steam" in f.steam().biblioteca(numero).cuerpo)
    }

    @Test fun steamNoResponde() {
        val f = Falso()
        f.biblioteca = { Cine.Respuesta(500, "error") }
        assertEquals(502, f.steam().biblioteca(numero).codigo)
        f.biblioteca = { Cine.Respuesta(200, "esto no es json") }
        assertTrue("steam_no_responde" in f.steam().biblioteca(numero).cuerpo)
        f.biblioteca = { throw java.net.http.HttpTimeoutException("tiempo") }
        assertTrue("steam_no_responde" in f.steam().biblioteca(numero).cuerpo)
    }

    @Test fun igdbFallaOFalta() {
        val f = Falso()
        f.igdbResponde = { Cine.Respuesta(500, "x") }
        val r = f.steam().biblioteca(numero)
        assertEquals(502, r.codigo)
        assertTrue("\"igdb\"" in r.cuerpo)
        val sin = f.steam(igdbUsado = Igdb(null, null)).biblioteca(numero)
        assertEquals(503, sin.codigo)
        assertTrue("sin_igdb" in sin.cuerpo)
    }

    @Test fun loteDe250EnTresConsultas() {
        val f = Falso(juegos = 250)
        val r = f.steam().biblioteca(numero)
        assertEquals(200, r.codigo)
        assertEquals(250, juegos(r).size)
        assertEquals(3, f.consultasIgdb.size)
        assertEquals(listOf(100, 100, 50), f.consultasIgdb.map { c -> Regex("\"[0-9]+\"").findAll(c).count() })
        assertTrue(f.consultasIgdb.all { it.endsWith("limit 500;") })
        // Con ficha: los que no terminan en 50 (multiplos de 10 que no lo son de 50).
        assertEquals(200, juegos(r).count { it["igdb"] != JsonNull })
    }

    @Test fun nadaSeGuardaEnLaCache() {
        val f = Falso()
        val s = f.steam()
        repeat(2) { s.biblioteca(numero) }
        assertEquals(2, f.consultasIgdb.size)
        assertEquals(2, f.pedidosSteam.size)
    }

    @Test fun claveNoAparece() {
        val f = Falso()
        val salida = ByteArrayOutputStream()
        val err = System.err
        val out = System.out
        val respuestas = mutableListOf<Cine.Respuesta>()
        System.setErr(PrintStream(salida, true)); System.setOut(PrintStream(salida, true))
        try {
            val casos: List<(String) -> Cine.Respuesta> = listOf(
                { Cine.Respuesta(403, "Forbidden key=$clave") }, { Cine.Respuesta(500, "fallo con $clave") },
                { throw RuntimeException("https://api.steampowered.com/?key=$clave&steamid=$numero") },
            )
            casos.forEach { c -> f.biblioteca = c; respuestas += f.steam().biblioteca("https://steamcommunity.com/id/gabelogannewell") }
            f.resolver = { throw IllegalStateException("key=$clave vanityurl=gabelogannewell") }
            respuestas += f.steam().biblioteca("gabelogannewell")
        } finally { System.setErr(err); System.setOut(out) }
        val registro = salida.toString()
        respuestas.forEach { r ->
            assertFalse(clave in r.cuerpo, r.cuerpo)
            assertFalse(numero in r.cuerpo, r.cuerpo)
            assertFalse("gabelogannewell" in r.cuerpo, r.cuerpo)
        }
        assertFalse(clave in registro, registro)
        assertFalse(numero in registro, registro)
        assertFalse("gabelogannewell" in registro, registro)
    }

    @Test fun cupoDiarioDeTodoElServidor() {
        var ahora = 0L
        var pedidos = 0
        val s = Steam(clave, Falso().igdb, { ahora }, maxPorDia = 10) { pedidos++; flujo(Cine.Respuesta(200, """{"response":{"game_count":0}}""")) }
        repeat(10) { s.biblioteca(numero, ip = "ip$it") }
        val r = s.biblioteca(numero, ip = "otra")
        // Se acabó lo de hoy: «cupo_diario» (la app dice que se pruebe mañana), no «Steam no respondió».
        assertEquals(429, r.codigo)
        assertTrue("cupo_diario" in r.cuerpo, r.cuerpo)
        assertEquals(10, pedidos)
        ahora += 86_400_000
        assertEquals(200, s.biblioteca(numero, ip = "otra").codigo)
    }

    @Test fun cupoDiarioPorDireccion() {
        var ahora = 0L
        val f = Falso()
        val s = Steam(clave, f.igdb, { ahora }) { flujo(Cine.Respuesta(200, """{"response":{"game_count":0}}""")) }
        repeat(Steam.MAX_POR_IP_DIA) { assertEquals(200, s.biblioteca(numero, ip = "1.2.3.4").codigo) }
        val tope = s.biblioteca(numero, ip = "1.2.3.4")
        assertEquals(429, tope.codigo)
        assertTrue("cupo_diario" in tope.cuerpo)
        // Otra dirección sigue pudiendo; al día siguiente, la primera también.
        assertEquals(200, s.biblioteca(numero, ip = "5.6.7.8").codigo)
        ahora += 86_400_000
        assertEquals(200, s.biblioteca(numero, ip = "1.2.3.4").codigo)
    }

    @Test fun numeroQueNoExisteNoEsPrivada() {
        val f = Falso()
        f.biblioteca = { Cine.Respuesta(200, """{"response":{}}""") }
        f.resumen = { Cine.Respuesta(200, """{"response":{"players":[]}}""") }
        val r = f.steam().biblioteca(numero)
        assertEquals(404, r.codigo)
        assertTrue("perfil_no_encontrado" in r.cuerpo)
        // Si el resumen no responde, queda «privada» (lo más probable).
        f.resumen = { Cine.Respuesta(500, "x") }
        assertTrue("biblioteca_privada" in f.steam().biblioteca(numero).cuerpo)
    }

    @Test fun bibliotecaGrandeSeRecortaALasMasJugadas() {
        val f = Falso(juegos = 10_000)
        val r = f.steam().biblioteca(numero)
        assertEquals(200, r.codigo, r.cuerpo.take(200))
        val o = JsonSocial.parseToJsonElement(r.cuerpo).jsonObject
        assertEquals(10_000L, o["total"]!!.jsonPrimitive.long)
        assertEquals("true", o["recortada"]!!.jsonPrimitive.content)
        val j = juegos(r)
        assertEquals(Steam.MAX_JUEGOS, j.size)
        // Los más jugados: en el falso, más minutos cuanto más alto el número.
        assertEquals(9_999L * 60, j.first()["minutos"]!!.jsonPrimitive.long)
        assertTrue(j.all { it["minutos"]!!.jsonPrimitive.long >= 5_000L * 60 })
        // Recortada antes de IGDB: solo esos 5 000 appids, en lotes.
        val consultados = f.consultasIgdb.flatMap { c -> Regex("\"([0-9]+)\"").findAll(c).map { it.groupValues[1] }.toList() }
        assertEquals(Steam.MAX_JUEGOS, consultados.size)
        assertEquals(Steam.MAX_JUEGOS / Igdb.LOTE_STEAM, f.consultasIgdb.size)
        // Una sin recortar no lleva la marca.
        assertNull(JsonSocial.parseToJsonElement(Falso().steam().biblioteca(numero).cuerpo).jsonObject["recortada"])
    }

    @Test fun respuestaDemasiadoGrandeNoSeLee() {
        val f = Falso(juegos = 2_000)
        val r = f.steam(maxBytes = 10_000).biblioteca(numero)
        assertEquals(502, r.codigo)
        assertTrue("steam_no_responde" in r.cuerpo)
        assertTrue(f.consultasIgdb.isEmpty())
    }

    @Test fun cupoDeIgdbPorDireccion() {
        val f = Falso(juegos = 5_000)
        val s = f.steam()
        // 50 consultas por importación de 5 000 juegos: tres caben en la hora, la cuarta no.
        repeat(Steam.MAX_IGDB_POR_IP_HORA / 50) { assertEquals(200, s.biblioteca(numero, ip = "9.9.9.9").codigo) }
        val antes = f.consultasIgdb.size
        val tope = s.biblioteca(numero, ip = "9.9.9.9")
        assertEquals(429, tope.codigo)
        assertTrue("despacio" in tope.cuerpo)
        assertEquals(antes, f.consultasIgdb.size)
        assertEquals(200, s.biblioteca(numero, ip = "8.8.8.8").codigo)
    }

    @Test fun lasPaginasDeMasCuentanEnElCupo() {
        val f = Falso(juegos = 5_000)
        // Cada appid trae 6 filas: cada lote pide dos páginas, 100 consultas por importación.
        f.igdbResponde = { consulta ->
            val uids = Regex("\"([0-9]+)\"").findAll(consulta.substringAfter("uid = (")).map { it.groupValues[1].toLong() }.toList()
            val todas = uids.flatMap { u -> (0 until 6).map { k -> """{"id":${u * 10 + k},"game":${u * 100 + k},"uid":"$u"}""" } }
            val desde = Regex("offset ([0-9]+);").find(consulta)?.groupValues?.get(1)?.toInt() ?: 0
            Cine.Respuesta(200, todas.drop(desde).take(500).joinToString(",", "[", "]"))
        }
        val s = f.steam()
        assertEquals(200, s.biblioteca(numero, ip = "9.9.9.9").codigo)
        assertEquals(100, f.consultasIgdb.size)
        // La segunda aparta sus 50 primeras páginas, pero la segunda página del primer lote ya no cabe en la hora.
        val tope = s.biblioteca(numero, ip = "9.9.9.9")
        assertEquals(429, tope.codigo)
        assertTrue("despacio" in tope.cuerpo)
        assertEquals(101, f.consultasIgdb.size)
        // Lo apartado y no usado volvió: quedan 49 para esta dirección.
        f.igdbResponde = { Cine.Respuesta(200, "[]") }
        f.juegos = 4_900
        assertEquals(200, s.biblioteca(numero, ip = "9.9.9.9").codigo)
        f.juegos = 100
        assertEquals(429, s.biblioteca(numero, ip = "9.9.9.9").codigo)
    }

    @Test fun siIgdbFallaVuelveLoApartado() {
        val f = Falso(juegos = 5_000)
        val s = f.steam()
        f.igdbResponde = { Cine.Respuesta(500, "x") }
        assertEquals(502, s.biblioteca(numero, ip = "9.9.9.9").codigo)
        assertEquals(1, f.consultasIgdb.size)
        // Solo cuenta la consulta que salió: caben 149 más (dos de 50 y una de 49).
        f.igdbResponde = { Cine.Respuesta(200, "[]") }
        repeat(2) { assertEquals(200, s.biblioteca(numero, ip = "9.9.9.9").codigo) }
        f.juegos = 4_900
        assertEquals(200, s.biblioteca(numero, ip = "9.9.9.9").codigo)
        f.juegos = 100
        assertEquals(429, s.biblioteca(numero, ip = "9.9.9.9").codigo)
    }

    @Test fun paginaSiIgdbTraeMuchasFilas() {
        val f = Falso(juegos = 100)
        // Cada appid trae 6 filas: 600 para un lote de 100, más que una página de 500.
        f.igdbResponde = { consulta ->
            val uids = Regex("\"([0-9]+)\"").findAll(consulta.substringAfter("uid = (")).map { it.groupValues[1].toLong() }.toList()
            val todas = uids.flatMap { u -> (0 until 6).map { k -> """{"id":${u * 10 + k},"game":${u * 100 + k},"uid":"$u"}""" } }
            val desde = Regex("offset ([0-9]+);").find(consulta)?.groupValues?.get(1)?.toInt() ?: 0
            Cine.Respuesta(200, todas.drop(desde).take(500).joinToString(",", "[", "]"))
        }
        val j = juegos(f.steam().biblioteca(numero))
        assertEquals(2, f.consultasIgdb.size)
        assertTrue(f.consultasIgdb[1].endsWith("limit 500; offset 500;"), f.consultasIgdb[1])
        // El último appid solo aparece en la segunda página.
        assertEquals(100_000L, j.last()["igdb"]!!.jsonPrimitive.long)
    }

    @Test fun deAUnaYLasBusquedasSiguen() = kotlinx.coroutines.runBlocking {
        val f = Falso(juegos = 300)
        val adentro = java.util.concurrent.CountDownLatch(1)
        val soltar = java.util.concurrent.CountDownLatch(1)
        f.igdbResponde = { c ->
            if ("external_game_source" in c) { adentro.countDown(); soltar.await() }
            Cine.Respuesta(200, "[]")
        }
        val s = f.steam(esperaTurno = 200)
        val primera = async(kotlinx.coroutines.Dispatchers.IO) { s.importar(numero, "1.1.1.1") }
        assertTrue(adentro.await(5, java.util.concurrent.TimeUnit.SECONDS))
        // Con una importación en curso, la segunda no entra (ni gasta IGDB) y la búsqueda normal responde.
        val segunda = s.importar(numero, "2.2.2.2")
        assertEquals(503, segunda.codigo)
        assertTrue("ocupado" in segunda.cuerpo)
        assertEquals(200, f.igdb.buscar("hades").codigo)
        soltar.countDown()
        assertEquals(200, primera.await().codigo)
        assertEquals(3, f.consultasIgdb.count { "external_game_source" in it })
        assertEquals(200, s.importar(numero, "2.2.2.2").codigo)
    }

    @Test fun siLaAppCortaNoSigueGastando() = kotlinx.coroutines.runBlocking {
        val f = Falso(juegos = 3_000)
        val primera = java.util.concurrent.CountDownLatch(1)
        f.igdbResponde = { c ->
            if ("external_game_source" in c) { primera.countDown(); Thread.sleep(100) }
            Cine.Respuesta(200, "[]")
        }
        val s = f.steam()
        val trabajo = launch(kotlinx.coroutines.Dispatchers.IO) { s.importar(numero, "3.3.3.3") }
        assertTrue(primera.await(5, java.util.concurrent.TimeUnit.SECONDS))
        trabajo.cancelAndJoin()
        val hechas = f.consultasIgdb.size
        Thread.sleep(700)
        assertEquals(hechas, f.consultasIgdb.size)
        assertTrue(hechas < 30, "hechas $hechas")
        // El turno quedó libre.
        f.igdbResponde = { Cine.Respuesta(200, "[]") }
        assertEquals(200, s.importar(numero, "3.3.3.3").codigo)
    }

    @Test fun conTopeDeTiempo() = kotlinx.coroutines.runBlocking {
        val f = Falso(juegos = 3_000)
        f.igdbResponde = { Thread.sleep(100); Cine.Respuesta(200, "[]") }
        assertTrue(Steam.ESPERA_TURNO + Steam.TIEMPO < 120_000L, "menos que el tope de la app")
        val s = f.steam(tiempo = 300)
        val r = s.importar(numero, "4.4.4.4")
        assertEquals(502, r.codigo)
        val hechas = f.consultasIgdb.size
        Thread.sleep(500)
        assertEquals(hechas, f.consultasIgdb.size)
    }

    @Test fun rutaYLimitePorIp() = testApplication {
        val dir = Files.createTempDirectory("steam-http").toFile()
        val f = Falso()
        application { fogonSocial(Social(Base(java.io.File(dir, "s.db").path), java.io.File(dir, "fotos")), igdb = f.igdb, steam = f.steam()) }
        val r = client.get("/v1/steam/biblioteca?perfil=https%3A%2F%2Fsteamcommunity.com%2Fid%2Fgabelogannewell")
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals("no-store", r.headers["Cache-Control"])
        assertTrue("Juego 1" in r.bodyAsText())
        assertEquals(HttpStatusCode.NotFound, client.get("/v1/steam/biblioteca?perfil=nadie").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/steam/biblioteca").status)
        f.biblioteca = { Cine.Respuesta(200, """{"response":{}}""") }
        assertEquals(HttpStatusCode.Forbidden, client.get("/v1/steam/biblioteca?perfil=$numero").status)
        repeat(2) { client.get("/v1/steam/biblioteca?perfil=$numero") }
        val tope = client.get("/v1/steam/biblioteca?perfil=$numero")
        assertEquals(HttpStatusCode.TooManyRequests, tope.status)
        assertTrue("despacio" in tope.bodyAsText())
    }

    @Test fun rutaSinClave() = testApplication {
        val dir = Files.createTempDirectory("steam-sin").toFile()
        application { fogonSocial(Social(Base(java.io.File(dir, "s.db").path), java.io.File(dir, "fotos")), steam = Steam(null, Igdb(null, null))) }
        val r = client.get("/v1/steam/biblioteca?perfil=$numero")
        assertEquals(HttpStatusCode.ServiceUnavailable, r.status)
        assertTrue("sin_steam" in r.bodyAsText())
    }
}

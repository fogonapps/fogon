package io.github.fogonapps.social

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.net.http.HttpRequest
import java.nio.ByteBuffer
import java.nio.file.Files
import java.util.concurrent.Flow
import kotlin.test.*

/** IGDB falso: nada sale a internet; se mira qué pediría el servidor y qué haría con cada respuesta. */
class IgdbTest {
    /** El cuerpo de un pedido POST (la consulta de IGDB). */
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

    private class Falso {
        var ahora = 1_000_000L
        val esperas = mutableListOf<Long>()
        var tokens = 0
        var pedidos = mutableListOf<HttpRequest>()
        var duraToken: Long? = 3600L * 24 * 60
        val formularios = mutableListOf<HttpRequest>()
        var respuestaIgdb: (HttpRequest) -> Cine.Respuesta = { Cine.Respuesta(200, """[{"id":1,"name":"Juego de prueba"}]""") }
        fun igdb() = Igdb("cliente", "secreto", { ahora }, { esperas += it; ahora += it }) { p ->
            if (p.uri().host == "id.twitch.tv") {
                tokens++; formularios += p
                val dura = duraToken?.let { "\"expires_in\":$it," }.orEmpty()
                Cine.Respuesta(200, """{"access_token":"tok$tokens",$dura"token_type":"bearer"}""")
            }
            else { pedidos += p; respuestaIgdb(p) }
        }
    }

    @Test fun sinClaveNoPide() {
        val i = Igdb(null, null, enviar = { error("No debe consultar") })
        assertEquals(503, i.buscar("zelda").codigo)
        assertTrue("sin_igdb" in i.buscar("zelda").cuerpo)
        assertEquals(503, i.juego("1").codigo)
        assertEquals(503, i.juegos("1", null).codigo)
        assertEquals(503, Igdb("solo-id", "").buscar("zelda").codigo)
    }

    @Test fun entradaRevisada() {
        val f = Falso()
        val i = f.igdb()
        listOf(null, "", "   ", "\"\"", "x".repeat(81)).forEach { assertEquals(400, i.buscar(it).codigo, "buscar $it") }
        listOf(null, "", "abc", "0", "-3", "1,2", "1;fields *").forEach { assertEquals(400, i.juego(it).codigo, "juego $it") }
        assertEquals(400, i.juegos(null, null).codigo)
        assertEquals(400, i.juegos("1,x", null).codigo)
        assertEquals(400, i.juegos(null, "hollow-knight,\"; fields *").codigo)
        assertEquals(400, i.juegos(null, "Mayusculas").codigo)
        assertEquals(400, i.juegos((1..51).joinToString(","), null).codigo)
        assertEquals(400, i.juegos((1..30).joinToString(","), (1..30).joinToString(",") { "j$it" }).codigo)
        assertTrue(f.pedidos.isEmpty())
        assertEquals(0, f.tokens)
    }

    @Test fun consultasArmadasAqui() {
        val f = Falso()
        val i = f.igdb()
        assertEquals(200, i.buscar("  Hollow \"Knight\"\\; fields *  ").codigo)
        // Dos consultas: por parecido del nombre y los más votados cuyo nombre lo contiene.
        assertEquals(2, f.pedidos.size)
        val b = cuerpo(f.pedidos[0])
        assertTrue(b.startsWith("search \"Hollow Knight; fields *\"; fields "), b)
        assertTrue("where version_parent = null" in b)
        assertTrue("limit 20;" in b)
        assertTrue("total_rating_count,hypes" in b, b)
        val populares = cuerpo(f.pedidos[1])
        assertTrue("where name ~ *\"Hollow Knight; fields *\"* & version_parent = null" in populares, populares)
        assertTrue("sort total_rating_count desc; limit 20;" in populares, populares)
        val p = f.pedidos.last()
        assertEquals("api.igdb.com", p.uri().host)
        assertEquals("/v4/games", p.uri().path)
        assertEquals("POST", p.method())
        assertEquals("cliente", p.headers().firstValue("Client-ID").get())
        assertEquals("Bearer tok1", p.headers().firstValue("Authorization").get())

        i.juego("1942")
        assertTrue("where id = 1942;" in cuerpo(f.pedidos.last()))
        assertTrue("summary" in cuerpo(f.pedidos.last()) && "websites.url" in cuerpo(f.pedidos.last()))
        i.juegos("3, 1,2", "hollow-knight")
        assertTrue("where id = (1,2,3) | slug = (\"hollow-knight\");" in cuerpo(f.pedidos.last()), cuerpo(f.pedidos.last()))
        assertEquals(1, f.tokens)
    }

    @Test fun tokenSeRenuevaAntesDeVencer() {
        val f = Falso()
        f.duraToken = 3 * 3600
        val i = f.igdb()
        i.buscar("uno")
        assertEquals(1, f.tokens)
        f.ahora += 3600_000
        i.buscar("dos")
        assertEquals(1, f.tokens)
        // Falta menos de una hora para que venza: se pide otro antes.
        f.ahora += 3600_000 + 1
        i.buscar("tres")
        assertEquals(2, f.tokens)
        assertEquals("Bearer tok2", f.pedidos.last().headers().firstValue("Authorization").get())
    }

    @Test fun tokenRechazadoSePideOtroUnaVez() {
        val f = Falso()
        f.respuestaIgdb = { p -> if (p.headers().firstValue("Authorization").get() == "Bearer tok1") Cine.Respuesta(401, "{}") else Cine.Respuesta(200, "[]") }
        val i = f.igdb()
        assertEquals(200, i.buscar("zelda").codigo)
        assertEquals(2, f.tokens)
        assertEquals(3, f.pedidos.size)
        // Si el nuevo tampoco sirve, no queda dando vueltas.
        f.respuestaIgdb = { Cine.Respuesta(401, "{}") }
        assertEquals(502, i.buscar("mario").codigo)
        assertEquals(5, f.pedidos.size)
    }

    @Test fun claveEnElCuerpoNoEnLaDireccion() {
        val f = Falso()
        f.igdb().buscar("zelda")
        val p = f.formularios.single()
        assertNull(p.uri().query)
        assertEquals("application/x-www-form-urlencoded", p.headers().firstValue("Content-Type").get())
        assertEquals("client_id=cliente&client_secret=secreto&grant_type=client_credentials", cuerpo(p))
    }

    @Test fun tokenSinDuracionNoSePideCadaVez() {
        listOf(null, 0L, 30L).forEach { dura ->
            val f = Falso()
            f.duraToken = dura
            val i = f.igdb()
            i.buscar("uno")
            f.ahora += 60_000
            i.buscar("dos")
            f.ahora += 600_000
            i.buscar("tres")
            assertEquals(1, f.tokens, "expires_in $dura")
        }
    }

    @Test fun cacheConTopeYBarrido() {
        val f = Falso()
        f.respuestaIgdb = { Cine.Respuesta(200, "[]") }
        val i = f.igdb()
        // Muchas búsquedas distintas sin resultados: las llaves cuentan y hay tope de entradas.
        repeat(Igdb.MAX_ENTRADAS + 50) { n -> f.ahora += 1; i.buscar("juego que no existe $n") }
        assertTrue(i.enCache <= Igdb.MAX_ENTRADAS)
        assertTrue(i.caracteresEnCache <= Igdb.MAX_CACHE)
        assertTrue(i.enCache > 0 && i.caracteresEnCache > i.enCache * 300L, "las llaves también cuentan: ${i.enCache} ${i.caracteresEnCache}")
        // Pasadas las 6 horas, el barrido se lleva el texto buscado aunque nadie vuelva a pedirlo.
        f.ahora += Igdb.SEIS_HORAS
        i.barrer()
        assertEquals(0, i.enCache)
    }

    @Test fun sinTokenNoConsulta() {
        val i = Igdb("cliente", "malo", enviar = { p -> if (p.uri().host == "id.twitch.tv") Cine.Respuesta(400, "{}") else error("No debe consultar") })
        assertEquals(502, i.buscar("zelda").codigo)
    }

    @Test fun cacheSeisHorasYFallosNoSeGuardan() {
        val f = Falso()
        val i = f.igdb()
        repeat(3) { assertEquals(200, i.buscar("zelda").codigo) }
        assertEquals(2, f.pedidos.size)
        f.ahora += Igdb.SEIS_HORAS
        i.buscar("zelda")
        assertEquals(4, f.pedidos.size)
        f.respuestaIgdb = { Cine.Respuesta(500, "error interno") }
        // Si falla la primera, no se pide la segunda.
        repeat(2) { assertEquals(502, i.buscar("metroid").codigo) }
        assertEquals(6, f.pedidos.size)
        f.respuestaIgdb = { Cine.Respuesta(429, "{}") }
        assertEquals(429, i.buscar("kirby").codigo)
    }

    @Test fun filaDeCuatroPorSegundo() {
        val f = Falso()
        val i = f.igdb()
        // El reloj no avanza solo: cada pedido espera su turno, 250 ms después del anterior.
        listOf("a", "b", "c", "d", "e").forEach { i.buscar(it) }
        assertEquals(List(9) { 250L }, f.esperas)
        // Con la fila muy larga, se pide esperar en vez de colgar a la app.
        val g = Falso()
        val j = Igdb("cliente", "secreto", { g.ahora }, { }) { p ->
            if (p.uri().host == "id.twitch.tv") Cine.Respuesta(200, """{"access_token":"t","expires_in":99999}""") else Cine.Respuesta(200, "[]")
        }
        val codigos = (1..40).map { j.buscar("juego $it").codigo }
        assertTrue(429 in codigos)
        assertEquals(200, codigos.first())
    }

    @Test fun busquedaJuntaSinRepetir() {
        val f = Falso()
        f.respuestaIgdb = { p ->
            if (cuerpo(p).startsWith("search")) Cine.Respuesta(200, """[{"id":1,"name":"Uno"},{"id":2,"name":"Dos"}]""")
            else Cine.Respuesta(200, """[{"id":2,"name":"Dos"},{"id":3,"name":"Tres","total_rating_count":900}]""")
        }
        val r = f.igdb().buscar("algo")
        assertEquals(200, r.codigo)
        val ids = JsonSocial.parseToJsonElement(r.cuerpo).let { it as kotlinx.serialization.json.JsonArray }
            .map { (it as kotlinx.serialization.json.JsonObject)["id"].toString() }
        assertEquals(listOf("1", "2", "3"), ids)
        // Si la segunda falla, va la primera sola.
        val g = Falso()
        g.respuestaIgdb = { p -> if (cuerpo(p).startsWith("search")) Cine.Respuesta(200, """[{"id":1,"name":"Uno"}]""") else Cine.Respuesta(500, "x") }
        assertEquals("""[{"id":1,"name":"Uno"}]""", g.igdb().buscar("algo").cuerpo)
    }

    @Test fun rutasYLimitePorIp() = testApplication {
        val dir = Files.createTempDirectory("igdb-http").toFile()
        val f = Falso()
        application { fogonSocial(Social(Base(java.io.File(dir, "s.db").path), java.io.File(dir, "fotos")), igdb = f.igdb()) }
        val r = client.get("/v1/igdb/buscar?q=zelda")
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue("Juego de prueba" in r.bodyAsText())
        assertEquals(HttpStatusCode.OK, client.get("/v1/igdb/juegos/7346").status)
        assertEquals(HttpStatusCode.OK, client.get("/v1/igdb/juegos?ids=1,2&slugs=hollow-knight").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/igdb/juegos/abc").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/igdb/buscar").status)
        repeat(55) { client.get("/v1/igdb/juegos/1") }
        assertEquals(HttpStatusCode.TooManyRequests, client.get("/v1/igdb/juegos/1").status)
    }

    @Test fun rutaSinClave() = testApplication {
        val dir = Files.createTempDirectory("igdb-sin").toFile()
        application { fogonSocial(Social(Base(java.io.File(dir, "s.db").path), java.io.File(dir, "fotos")), igdb = Igdb(null, null)) }
        val r = client.get("/v1/igdb/buscar?q=zelda")
        assertEquals(HttpStatusCode.ServiceUnavailable, r.status)
        assertTrue("sin_igdb" in r.bodyAsText())
    }

    @Test fun portadasDeIgdbSonSeguras() {
        assertTrue(Social.portadaSegura("https://images.igdb.com/igdb/image/upload/t_cover_big/co1rgi.jpg"))
        assertFalse(Social.portadaSegura("https://images.igdb.com.malo.example/x.jpg"))
    }
}

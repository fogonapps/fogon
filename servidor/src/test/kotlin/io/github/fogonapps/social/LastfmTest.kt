package io.github.fogonapps.social

import kotlin.test.*
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.nio.file.Files

class LastfmTest {
    @Test fun rutaSinClaveYLimitePorIp() = testApplication {
        val dir = Files.createTempDirectory("lastfm-http").toFile()
        application { fogonSocial(Social(Base(java.io.File(dir, "s.db").path), java.io.File(dir, "fotos")), lastfm = Lastfm(null)) }
        repeat(60) {
            assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/v1/lastfm?method=user.getinfo&user=ana").status)
        }
        assertEquals(HttpStatusCode.TooManyRequests, client.get("/v1/lastfm?method=user.getinfo&user=ana").status)
    }

    @Test fun sinClaveYMetodosAjenos() {
        assertEquals(503, Lastfm(null).pedir(emptyMap()).codigo)
        val l = Lastfm("secreto", enviar = { error("No debe consultar") })
        listOf(mapOf("method" to "track.scrobble", "user" to "ana"),
            mapOf("method" to "user.getrecenttracks", "user" to "ana", "page" to "0"),
            mapOf("method" to "user.getinfo", "user" to "")).forEach { assertEquals(400, l.pedir(it).codigo) }
    }

    @Test fun parametrosControladosCabeceraYCacheCaduca() {
        var ahora = 0L
        var pedidos = 0
        val l = Lastfm("secreto", { ahora }) { p ->
            pedidos++
            assertEquals("FogonSocial/$VERSION (+https://fogonapps.duckdns.org)", p.headers().firstValue("User-Agent").get())
            assertEquals("ws.audioscrobbler.com", p.uri().host)
            assertTrue("api_key=secreto" in p.uri().query)
            assertTrue("limit=200" in p.uri().query)
            assertFalse("ajena" in p.uri().query)
            Cine.Respuesta(200, "{}")
        }
        val params = mapOf("method" to "user.getrecenttracks", "user" to "ana", "api_key" to "ajena", "limit" to "1")
        repeat(2) { assertEquals(200, l.pedir(params).codigo) }
        assertEquals(1, pedidos)
        ahora = 300_000
        l.pedir(params)
        assertEquals(2, pedidos)
    }

    @Test fun fallosNoSeGuardan() {
        var pedidos = 0
        val l = Lastfm("x", enviar = { pedidos++; Cine.Respuesta(503, "{}") })
        repeat(2) { l.pedir(mapOf("method" to "user.getinfo", "user" to "ana")) }
        assertEquals(2, pedidos)
    }
}

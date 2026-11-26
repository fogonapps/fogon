package io.github.fogonapps.social

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** «Juntos»: los votos de cada uno para ver con la otra persona, solo entre quienes se siguen los dos. */
class JuntosTest {
    private class Entorno : AutoCloseable {
        val dir: File = Files.createTempDirectory("juntos").toFile()
        val b = Base(File(dir, "s.db").path)
        val s = Social(b, File(dir, "fotos"))
        val tokens = mutableMapOf<String, String>()
        fun alta(nombre: String) = s.crearCuenta(NuevaCuenta(nombre)).token.let { tokens[nombre] = it; s.cuentaDe(it)!! }
        val ana = alta("ana"); val beto = alta("beto")
        fun votos(de: String) = b.usar { leer("SELECT COUNT(*) FROM juntos_votos WHERE cuenta = (SELECT id FROM cuentas WHERE usuario = ?)", de) { it.getInt(1) }.single() }
        override fun close() { b.cerrar(); dir.deleteRecursively() }
    }

    @Test fun soloEntreQuienesSeSiguenLosDos() = Entorno().use { e ->
        // Sin seguirse los dos, no hay votos compartidos.
        assertEquals(403, assertFailsWith<Problema> { e.s.votarJuntos(e.ana, "beto", VotoJuntos("tmdb:movie:1", "si")) }.codigo)
        e.s.seguir(e.ana, "beto")
        assertEquals(403, assertFailsWith<Problema> { e.s.votosJuntos(e.ana, "beto") }.codigo)
        e.s.seguir(e.beto, "ana")
        e.s.votarJuntos(e.ana, "beto", VotoJuntos("tmdb:movie:1", "si"))
        e.s.votarJuntos(e.ana, "beto", VotoJuntos("tmdb:movie:2", "no"))
        e.s.votarJuntos(e.beto, "ana", VotoJuntos("tmdb:movie:1", "si"))
        assertEquals(VotosJuntos(mapOf("tmdb:movie:1" to "si", "tmdb:movie:2" to "no"), mapOf("tmdb:movie:1" to "si")), e.s.votosJuntos(e.ana, "beto"))
        assertEquals(VotosJuntos(mapOf("tmdb:movie:1" to "si"), mapOf("tmdb:movie:1" to "si", "tmdb:movie:2" to "no")), e.s.votosJuntos(e.beto, "ana"))
        // Cambiar y quitar.
        e.s.votarJuntos(e.ana, "beto", VotoJuntos("tmdb:movie:2", "si"))
        e.s.votarJuntos(e.ana, "beto", VotoJuntos("tmdb:movie:1", null))
        assertEquals(mapOf("tmdb:movie:2" to "si"), e.s.votosJuntos(e.ana, "beto").mios)
        // Votos malos.
        assertEquals(400, assertFailsWith<Problema> { e.s.votarJuntos(e.ana, "beto", VotoJuntos("x", "quizas")) }.codigo)
        assertEquals(400, assertFailsWith<Problema> { e.s.votarJuntos(e.ana, "beto", VotoJuntos(" ", "si")) }.codigo)
        assertEquals(404, assertFailsWith<Problema> { e.s.votarJuntos(e.ana, "ana", VotoJuntos("x", "si")) }.codigo)
        assertEquals(404, assertFailsWith<Problema> { e.s.votosJuntos(e.ana, "ana") }.codigo)
    }

    @Test fun unBloqueoLosEscondeEnLosDosSentidos() = Entorno().use { e ->
        e.s.seguir(e.ana, "beto"); e.s.seguir(e.beto, "ana")
        e.s.votarJuntos(e.beto, "ana", VotoJuntos("tmdb:movie:1", "si"))
        e.s.bloquear(e.beto, "ana", true)
        // Aunque quede un «seguir» viejo, el bloqueo manda.
        e.b.usar { cambiar("INSERT OR IGNORE INTO seguir(de,a,cuando,aceptada) VALUES(?,?,1,1)", e.ana, e.beto); cambiar("INSERT OR IGNORE INTO seguir(de,a,cuando,aceptada) VALUES(?,?,1,1)", e.beto, e.ana) }
        assertEquals(404, assertFailsWith<Problema> { e.s.votosJuntos(e.ana, "beto") }.codigo)
        assertEquals(404, assertFailsWith<Problema> { e.s.votosJuntos(e.beto, "ana") }.codigo)
        assertEquals(404, assertFailsWith<Problema> { e.s.votarJuntos(e.ana, "beto", VotoJuntos("tmdb:movie:1", "si")) }.codigo)
    }

    @Test fun seBorranConLaCuenta() = Entorno().use { e ->
        e.s.seguir(e.ana, "beto"); e.s.seguir(e.beto, "ana")
        e.s.votarJuntos(e.ana, "beto", VotoJuntos("tmdb:movie:1", "si"))
        e.s.votarJuntos(e.beto, "ana", VotoJuntos("tmdb:movie:1", "no"))
        e.s.borrarCuenta(e.ana)
        assertEquals(0, e.b.usar { leer("SELECT COUNT(*) FROM juntos_votos") { it.getInt(1) }.single() })
    }

    @Test fun lasRutas() = Entorno().use { e ->
        testApplication {
            application { fogonSocial(e.s) }
            e.s.seguir(e.ana, "beto"); e.s.seguir(e.beto, "ana")
            fun auth(n: String) = "Bearer ${e.tokens.getValue(n)}"
            val puesto = client.put("/v1/juntos/beto/votos") { header("Authorization", auth("ana")); contentType(ContentType.Application.Json); setBody("""{"clave":"tmdb:movie:7","voto":"si"}""") }
            assertEquals(HttpStatusCode.NoContent, puesto.status)
            val leido = client.get("/v1/juntos/ana/votos") { header("Authorization", auth("beto")) }
            assertEquals(HttpStatusCode.OK, leido.status)
            assertTrue("\"suyos\":{\"tmdb:movie:7\":\"si\"}" in leido.bodyAsText())
            assertEquals(1, e.votos("ana"))
            val quitado = client.put("/v1/juntos/beto/votos") { header("Authorization", auth("ana")); contentType(ContentType.Application.Json); setBody("""{"clave":"tmdb:movie:7","voto":null}""") }
            assertEquals(HttpStatusCode.NoContent, quitado.status)
            assertEquals(0, e.votos("ana"))
            assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/juntos/ana/votos").status)
        }
    }
}

package io.github.fogonapps.social

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SocialTest {
    private fun nuevo(): Social {
        val dir = Files.createTempDirectory("social").toFile()
        return Social(Base(java.io.File(dir, "s.db").path), java.io.File(dir, "fotos"))
    }

    private fun pub(clave: String, vis: String, cuando: Long = 1) =
        PublicacionSubida(clave, vis, cuando, "diario", buildJsonObject { put("titulo", "Obra $clave") })

    @Test fun visibilidadAmigosYBloqueos() {
        val s = nuevo()
        val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("ana")).token)!!
        val beto = s.cuentaDe(s.crearCuenta(NuevaCuenta("beto")).token)!!
        val caro = s.cuentaDe(s.crearCuenta(NuevaCuenta("caro")).token)!!
        assertFailsWith<Problema> { s.crearCuenta(NuevaCuenta("ANA")) }         // el usuario ya existe
        assertFailsWith<Problema> { s.crearCuenta(NuevaCuenta("a b")) }         // no es un usuario válido

        s.subirPublicaciones(ana, listOf(pub("1", Vis.PUBLICA, 10), pub("2", Vis.AMIGOS, 20), pub("3", "privada", 30)))
        s.subirPerfil(ana, PerfilSubido(nombre = "Ana", partes = mapOf(
            "destacados" to Parte(Vis.PUBLICA, JsonPrimitive("d")), "diario" to Parte(Vis.AMIGOS, JsonPrimitive("x")))))
        // Sin ser amigos: solo lo público. Lo privado ni se guardó.
        var visto = s.verPerfil(beto, "ana")
        assertEquals(listOf("destacados"), visto.partes.keys.toList())
        assertEquals(1, visto.publicaciones.size)
        // Amigos (los dos lo pidieron): también lo de amigos, y sale en su actividad.
        s.pedirAmistad(beto, "ana")
        assertEquals("recibida", s.verPerfil(ana, "beto").relacion)
        s.pedirAmistad(ana, "beto")
        visto = s.verPerfil(beto, "ana")
        assertEquals("amigos", visto.relacion)
        assertEquals(2, visto.publicaciones.size)
        assertEquals(2, s.actividad(beto, null).size)
        assertEquals(0, s.actividad(caro, null).size)

        // Me gusta, comentarios y guardar.
        val id = s.actividad(beto, null).first().id
        s.meGusta(beto, id, true); s.guardar(beto, id, true)
        val c = s.comentar(beto, id, "¡Qué buena!")
        assertEquals(1, s.publicacion(ana, id)!!.megusta)
        assertEquals(1, s.guardados(beto).size)
        assertTrue(s.comentarios(ana, id).single().puedoBorrar)            // la dueña puede borrarlo
        assertFailsWith<Problema> { s.borrarComentario(caro, c) }          // otro no
        // Caro no puede comentar lo de amigos.
        assertFailsWith<Problema> { s.comentar(caro, id, "hola") }

        // Volver a subir sin la 2 (la volvió privada): se va, con sus me gusta.
        s.subirPublicaciones(ana, listOf(pub("1", Vis.PUBLICA, 10)))
        assertNull(s.publicacion(beto, id))

        // Bloquear: se acaba la amistad y no se ven.
        s.bloquear(ana, "beto", true)
        assertFailsWith<Problema> { s.verPerfil(beto, "ana") }
        assertEquals(0, s.amigos(ana).amigos.size)
        assertEquals(emptyList(), s.buscar(beto, "an"))

        // Borrar la cuenta: se va todo.
        s.borrarCuenta(ana)
        assertFailsWith<Problema> { s.verPerfil(caro, "ana") }
        assertNull(s.cuentaDe("token-que-no-existe"))
    }

    @Test fun fotoSoloJpegChico() {
        val s = nuevo()
        val yo = s.cuentaDe(s.crearCuenta(NuevaCuenta("dani")).token)!!
        assertFailsWith<Problema> { s.subirFoto(yo, "no es imagen".toByteArray()) }
        assertFailsWith<Problema> { s.subirFoto(yo, ByteArray(500_000) { 0xFF.toByte() }) }
        s.subirFoto(yo, byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3))
        assertEquals(5, s.foto(null, "dani").size)
        assertTrue(s.verPerfil(null, "dani").persona.foto)
    }

    @Test fun porHttp() = testApplication {
        val s = nuevo()
        application { fogonSocial(s) }
        val r = client.post("/v1/cuentas") { contentType(ContentType.Application.Json); setBody("""{"usuario":"eva","nombre":"Eva"}""") }
        assertEquals(HttpStatusCode.Created, r.status)
        val token = Regex("\"token\":\"([^\"]+)\"").find(r.bodyAsText())!!.groupValues[1]
        assertEquals(HttpStatusCode.Unauthorized, client.put("/v1/perfil") { contentType(ContentType.Application.Json); setBody("{}") }.status)
        assertEquals(HttpStatusCode.NoContent, client.put("/v1/perfil") {
            header("Authorization", "Bearer $token"); contentType(ContentType.Application.Json); setBody("""{"nombre":"Eva","bio":"Cine"}""")
        }.status)
        val perfil = client.get("/v1/perfiles/eva").bodyAsText()
        assertTrue("\"bio\":\"Cine\"" in perfil)
        assertEquals(HttpStatusCode.NotFound, client.get("/v1/perfiles/nadie").status)
        assertEquals(HttpStatusCode.BadRequest, client.post("/v1/cuentas") { contentType(ContentType.Application.Json); setBody("no es json") }.status)
    }

    @Test fun limiteDePeticiones() = testApplication {
        application { fogonSocial(nuevo()) }
        val estados = (1..125).map { client.get("/v1/estado") { header("Authorization", "Bearer x") }.status }
        assertEquals(120, estados.count { it == HttpStatusCode.OK })
        assertEquals(HttpStatusCode.TooManyRequests, estados.last())
    }

    /** Carga: 200 amigos con 50 publicaciones cada uno (10.000), con me gusta y comentarios; la actividad debe salir rápido. */
    @Test fun cargaDeActividad() {
        val s = nuevo()
        val yo = s.cuentaDe(s.crearCuenta(NuevaCuenta("centro")).token)!!
        val amigos = (1..200).map { i -> s.cuentaDe(s.crearCuenta(NuevaCuenta("amigo$i")).token)!! }
        amigos.forEachIndexed { i, a ->
            s.pedirAmistad(a, "centro"); s.pedirAmistad(yo, "amigo${i + 1}")
            s.subirPublicaciones(a, (1..50).map { k -> pub("$k", if (k % 2 == 0) Vis.AMIGOS else Vis.PUBLICA, (i * 50L + k)) })
        }
        val primeras = s.actividad(yo, null)
        primeras.take(20).forEach { s.meGusta(yo, it.id, true); s.comentar(yo, it.id, "bien") }
        val inicio = System.nanoTime()
        repeat(20) { s.actividad(yo, null) }
        val ms = (System.nanoTime() - inicio) / 1_000_000 / 20
        println("Actividad con 200 amigos y 10.000 publicaciones: $ms ms por carga")
        assertEquals(40, s.actividad(yo, null).size)
        assertEquals(1, s.actividad(yo, null).first().megusta)
        assertTrue(ms < 300, "la actividad tardó $ms ms")
    }
}

package io.github.fogonapps.social

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlinx.serialization.json.*
import kotlin.test.*

class PublicacionesTest {
    private fun escenario(prueba: (Social, Base, Long, Long, Long) -> Unit) {
        val dir = Files.createTempDirectory("publicaciones").toFile()
        val b = Base(java.io.File(dir, "s.db").path)
        try {
            val s = Social(b, java.io.File(dir, "fotos"))
            val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("ana")).token)!!
            val beto = s.cuentaDe(s.crearCuenta(NuevaCuenta("beto")).token)!!
            val cami = s.cuentaDe(s.crearCuenta(NuevaCuenta("cami")).token)!!
            s.subirPublicaciones(ana, listOf(pub("a"), pub("b")))
            prueba(s, b, ana, beto, cami)
        } finally { b.cerrar(); dir.deleteRecursively() }
    }
    private fun pub(clave: String, fotos: List<String>? = null) = PublicacionSubida(clave, Vis.PUBLICA, 1, "foto", buildJsonObject {
        put("texto", clave); fotos?.let { put("imagenes", JsonArray(it.map(::JsonPrimitive))) }
    })

    @Test fun respuestasRaizAvisosYBorrado() = escenario { s, b, ana, beto, cami ->
        val publicaciones = s.actividad(ana, null)
        val pub = publicaciones.first().id
        val raiz = s.comentar(beto, pub, "raíz")
        val respuesta = s.comentar(cami, pub, "respuesta", raiz)
        val segunda = s.comentar(ana, pub, "otra", respuesta)
        val lista = s.comentarios(beto, pub)
        assertEquals(2, lista.first { it.id == raiz }.respuestas)
        assertEquals(raiz, lista.first { it.id == segunda }.padre)
        assertEquals("padre", assertFailsWith<Problema> { s.comentar(beto, publicaciones.last().id, "error", raiz) }.mensaje)
        assertTrue(s.avisos(beto, null).any { it.tipo == "respuesta" && it.objeto == "$pub" })
        assertTrue(s.avisos(cami, null).any { it.tipo == "respuesta" })
        val antes = s.avisos(cami, null).size
        s.comentar(cami, pub, "a mí", respuesta)
        assertEquals(antes, s.avisos(cami, null).size)
        s.borrarComentario(beto, raiz)
        val borrado = s.comentarios(cami, pub).first { it.id == raiz }
        assertTrue(borrado.borrado)
        assertEquals("", borrado.texto)
        assertEquals("", borrado.autor.usuario)
        assertEquals(3, borrado.respuestas)
        val solo = s.comentar(beto, pub, "sin respuestas")
        s.borrarComentario(beto, solo)
        assertFalse(s.comentarios(beto, pub).any { it.id == solo })
        b.usar { cambiar("UPDATE comentarios SET oculto = 1 WHERE id = ?", respuesta) }
        assertEquals("padre", assertFailsWith<Problema> { s.comentar(beto, pub, "oculta", respuesta) }.mensaje)
    }

    @Test fun meGustaUnicoQuitableYVisibilidad() = escenario { s, b, ana, beto, cami ->
        val pub = s.actividad(ana, null).first().id
        val comentario = s.comentar(beto, pub, "hola")
        repeat(2) { s.meGustaComentario(cami, comentario, true) }
        assertEquals(1, s.comentarios(cami, pub).single().megusta)
        assertTrue(s.comentarios(cami, pub).single().yoMeGusta)
        s.meGustaComentario(cami, comentario, false)
        assertEquals(0, s.comentarios(cami, pub).single().megusta)
        s.bloquear(beto, "cami", true)
        assertEquals(404, assertFailsWith<Problema> { s.meGustaComentario(cami, comentario, true) }.codigo)
        assertTrue(s.comentarios(cami, pub).isEmpty())
        s.bloquear(beto, "cami", false)
        b.usar { cambiar("UPDATE comentarios SET oculto = 1 WHERE id = ?", comentario) }
        assertEquals(404, assertFailsWith<Problema> { s.meGustaComentario(cami, comentario, false) }.codigo)
        b.usar { cambiar("UPDATE comentarios SET oculto = 0 WHERE id = ?", comentario); cambiar("UPDATE publicaciones SET estado = 'retirado' WHERE id = ?", pub) }
        assertEquals(404, assertFailsWith<Problema> { s.meGustaComentario(cami, comentario, true) }.codigo)
    }

    @Test fun diezFotosOnceYAjenaConTransaccion() = escenario { s, b, ana, beto, _ ->
        val nombres = (1..11).map { "foto$it" }
        b.usar { nombres.forEach { cambiar("INSERT INTO imagenes(nombre, cuenta, cuando) VALUES(?,?,0)", it, ana) } }
        s.subirPublicaciones(ana, listOf(pub("fotos", nombres.take(10))))
        assertEquals(10, (s.actividad(ana, null).single().datos["imagenes"] as JsonArray).size)
        assertEquals(400, assertFailsWith<Problema> { s.subirPublicaciones(ana, listOf(pub("once", nombres))) }.codigo)
        assertEquals(400, assertFailsWith<Problema> { s.subirPublicaciones(beto, listOf(pub("ajena", nombres.take(1)))) }.codigo)
        assertEquals(10, (s.actividad(ana, null).single().datos["imagenes"] as JsonArray).size)
    }

    @Test fun moderacionDeRespuestasYContenedorRetenido() = escenario { s, b, ana, beto, cami ->
        val token = s.crearCuenta(NuevaCuenta("jefa")).token
        val jefa = s.cuentaDe(token)!!
        b.usar {
            cambiar("INSERT INTO roles VALUES(?, 'administrador', 'servidor', 0)", jefa)
            cambiar("INSERT INTO mfa VALUES(?, '', 1, 0, -1)", jefa)
            cambiar("UPDATE sesiones SET elevada_hasta = ? WHERE cuenta = ?", Long.MAX_VALUE, jefa)
        }
        val pub = s.actividad(ana, null).first().id
        val raiz = s.comentar(beto, pub, "raíz")
        val respuesta = s.comentar(cami, pub, "respuesta", raiz)
        s.reportar(ana, NuevoReporte("comentario", "$respuesta", Moderacion.MENOR_EN_RIESGO))
        assertEquals(0, s.comentarios(ana, pub).single().respuestas)
        assertEquals(1, s.comentarios(cami, pub).first { it.id == raiz }.respuestas)
        assertEquals("respuesta", s.casos(jefa, token).single().contenido)
        s.retirar(jefa, ContenidoEquipo("comentario", "$respuesta"), token)
        assertEquals("retirado", s.casos(jefa, token).single().estado)
        s.borrarComentario(beto, raiz)
        assertTrue(s.comentarios(ana, pub).isEmpty())
        // La retirada de una respuesta no se pierde al borrar su raíz.
        assertEquals("respuesta", s.casos(jefa, token).single().contenido)
        s.restaurar(jefa, ContenidoEquipo("comentario", "$respuesta"), token)
        assertEquals(2, s.comentarios(ana, pub).size)
        assertTrue(s.comentarios(ana, pub).first().borrado)
    }

    @Test fun rutasDeRespuestasYMeGusta() = escenario { s, _, ana, beto, _ ->
        val pub = s.actividad(ana, null).first().id
        val raiz = s.comentar(beto, pub, "raíz")
        val token = s.crearCuenta(NuevaCuenta("dani")).token
        testApplication {
            application { fogonSocial(s) }
            val respuesta = client.post("/v1/publicaciones/$pub/comentarios") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"texto":"hola","padre":$raiz}""")
            }
            assertEquals(HttpStatusCode.Created, respuesta.status)
            val id = JsonSocial.parseToJsonElement(respuesta.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.long
            assertEquals(HttpStatusCode.NoContent, client.post("/v1/comentarios/$id/megusta") { header(HttpHeaders.Authorization, "Bearer $token") }.status)
            assertEquals(HttpStatusCode.NoContent, client.delete("/v1/comentarios/$id/megusta") { header(HttpHeaders.Authorization, "Bearer $token") }.status)
            assertEquals(HttpStatusCode.BadRequest, client.post("/v1/publicaciones/$pub/comentarios") {
                header(HttpHeaders.Authorization, "Bearer $token"); contentType(ContentType.Application.Json)
                setBody("""{"texto":"error","padre":999999}""")
            }.status)
        }
    }

    @Test fun migracionIdempotente() {
        val dir = Files.createTempDirectory("migracion-publicaciones").toFile()
        val ruta = java.io.File(dir, "s.db").path
        try {
            Class.forName("org.sqlite.JDBC")
            java.sql.DriverManager.getConnection("jdbc:sqlite:$ruta").use { c ->
                c.createStatement().use { it.execute("""CREATE TABLE comentarios(
                    id INTEGER PRIMARY KEY, pub INTEGER NOT NULL REFERENCES publicaciones(id) ON DELETE CASCADE,
                    cuenta INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE, texto TEXT NOT NULL, cuando INTEGER NOT NULL)""") }
            }
            repeat(2) {
                val b = Base(ruta)
                try {
                    val columnas = b.usar { leer("PRAGMA table_info(comentarios)") { it.getString("name") } }
                    assertEquals(1, columnas.count { it == "padre" })
                    assertEquals(1, columnas.count { it == "borrado" })
                } finally { b.cerrar() }
            }
        } finally { dir.deleteRecursively() }
    }
}

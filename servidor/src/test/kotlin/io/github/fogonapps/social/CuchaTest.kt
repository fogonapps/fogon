package io.github.fogonapps.social

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.*
import java.nio.file.Files
import kotlin.test.*

class CuchaTest {
    private fun json(s: String) = JsonSocial.parseToJsonElement(s).jsonObject
    private val plato = """{"version":1,"nombre":"Sopa <rica>"}"""
    private val receta = """{"version":1,"nombre":"Sopa","porciones":2,"ingredientes":[{"nombre":"Agua","cantidad":250,"unidad":"ml"}],"pasos":["Hervir <agua>"]}"""
    private val resena = """{"version":1,"lugar":1,"nota":5,"texto":"Rico"}"""
    private fun pub(tipo: String, d: JsonObject, clave: String = tipo) = PublicacionSubida(clave, Vis.PUBLICA, 1, tipo, d)
    private class Entorno : AutoCloseable {
        val dir = Files.createTempDirectory("cucha-test").toFile()
        val b = Base(java.io.File(dir, "social.db").path)
        var reloj = 2_000_000_000_000L
        val s = Social(b, java.io.File(dir, "fotos"), { reloj })
        val token = s.crearCuenta(NuevaCuenta("ana")).token
        val yo = s.cuentaDe(token)!!
        override fun close() { b.cerrar(); dir.deleteRecursively() }
    }
    @Test fun esquemasCerradosLimitesNutricionYOrigen() {
        for ((tipo, texto) in listOf("plato" to plato, "receta" to receta, "resena_restaurante" to resena)) {
            val d = json(texto)
            validarComida(tipo, d)
            for ((k, v) in listOf("ubicacion" to JsonPrimitive("casa"), "version" to JsonPrimitive(2), "texto" to JsonPrimitive("x".repeat(2001)), "lat" to JsonPrimitive(2))) {
                assertFailsWith<Problema> { validarComida(tipo, JsonObject(d + (k to v))) }
            }
            for (app in listOf("cajon", "forzudo")) assertEquals("tipo_app", assertFailsWith<Problema> { validarLoteApp(app, CambiosPublicaciones(listOf(pub(tipo, d)))) }.mensaje)
            if (tipo != "resena_restaurante") {
                val n = json("""{"kcal":200,"proteina":5,"carbohidratos":30,"grasa":4}""")
                validarComida(tipo, JsonObject(d + ("nutricion" to n)))
                for (malo in listOf(JsonNull, JsonObject(n - "kcal"), JsonObject(n + ("grasa" to JsonPrimitive(-1))), JsonObject(n + ("kcal" to JsonPrimitive("NaN"))), JsonObject(n + ("unidad" to JsonPrimitive("g"))))) {
                    assertFailsWith<Problema> { validarComida(tipo, JsonObject(d + ("nutricion" to malo))) }
                }
            }
        }
        for (cambio in listOf("porciones" to JsonPrimitive(101), "tiempo" to JsonPrimitive(0), "pasos" to JsonArray(emptyList()), "ingredientes" to JsonArray(emptyList()))) {
            assertFailsWith<Problema> { validarComida("receta", JsonObject(json(receta) + cambio)) }
        }
        for (nota in listOf(0, 6)) assertFailsWith<Problema> { validarComida("resena_restaurante", JsonObject(json(resena) + ("nota" to JsonPrimitive(nota)))) }
        assertFailsWith<Problema> { validarLoteApp("cucha", CambiosPublicaciones(listOf(pub("entreno", json(plato))))) }
    }
    @Test fun extremosYObjetosAnidados() {
        validarComida("plato", json("""{"version":1,"nombre":"${"a".repeat(120)}","nota":1,"texto":"${"x".repeat(2000)}"}"""))
        for (nombre in listOf("", " ", "x".repeat(121))) assertFailsWith<Problema> { validarComida("plato", JsonObject(json(plato) + ("nombre" to JsonPrimitive(nombre)))) }
        val d = json(receta)
        for (ingrediente in listOf(
            """{"nombre":"a","cantidad":0,"unidad":"g"}""",
            """{"nombre":"a","cantidad":100001,"unidad":"g"}""",
            """{"nombre":"a","cantidad":"Infinity","unidad":"g"}""",
            """{"nombre":"a","cantidad":1,"unidad":"desconocida"}""",
            """{"nombre":"a","cantidad":1,"unidad":"g","id":"privado"}""")) {
            assertFailsWith<Problema> { validarComida("receta", JsonObject(d + ("ingredientes" to JsonArray(listOf(json(ingrediente)))))) }
        }
        for (pasos in listOf(List(51) { JsonPrimitive("a") }, listOf(JsonPrimitive("x".repeat(1001))), listOf(JsonPrimitive(" ")))) {
            assertFailsWith<Problema> { validarComida("receta", JsonObject(d + ("pasos" to JsonArray(pasos)))) }
        }
        assertFailsWith<Problema> { validarComida("receta", JsonObject(d + ("ingredientes" to JsonArray(List(101) { json("""{"nombre":"a","cantidad":1,"unidad":"g"}""") })))) }
    }
    @Test fun lugaresDuplicadosCoordenadasPermisosLimiteYReportes() = Entorno().use { e ->
        val l = e.s.crearLugar(e.yo, json("""{"nombre":"Café","ciudad":"Bogotá","direccion":"Calle <uno>","lat":4.123456,"lon":-74.123456}"""))
        assertEquals(4.1235, l.lat); assertEquals(-74.1235, l.lon)
        assertEquals(l.id, e.s.crearLugar(e.yo, json("""{"nombre":"CAFE","ciudad":"BOGOTA"}""")).id)
        assertEquals(l.id, e.s.buscarLugares("cafe", "bogota").single().id)
        val otroToken = e.s.crearCuenta(NuevaCuenta("beto")).token
        val otro = e.s.cuentaDe(otroToken)!!
        assertFailsWith<Problema> { e.s.corregirLugar(otro, l.id, json("""{"direccion":"otra"}"""), null) }
        assertEquals("otra", e.s.corregirLugar(e.yo, l.id, json("""{"direccion":"otra"}"""), null).direccion)
        for (d in listOf("""{"nombre":"a","ciudad":"b","lat":91,"lon":0}""", """{"nombre":"a","ciudad":"b","lat":0}""", """{"nombre":"a","ciudad":"b","extra":0}""")) assertFailsWith<Problema> { e.s.crearLugar(e.yo, json(d)) }
        repeat(9) { e.s.crearLugar(e.yo, json("""{"nombre":"Otro $it","ciudad":"Bogotá"}""")) }
        assertEquals(429, assertFailsWith<Problema> { e.s.crearLugar(e.yo, json("""{"nombre":"Once","ciudad":"Bogotá"}""")) }.codigo)
        e.s.reportar(otro, NuevoReporte("lugar", l.id.toString(), "otro: falso"))
        assertEquals(1, e.b.usar { leer("SELECT COUNT(*) FROM reportes WHERE que = 'lugar'") { it.getInt(1) }.single() })
        e.b.usar {
            cambiar("INSERT INTO roles VALUES(?,'administrador','servidor',0)", otro)
            cambiar("INSERT INTO mfa VALUES(?,'',1,0,-1)", otro)
            cambiar("UPDATE sesiones SET elevada_hasta = ? WHERE cuenta = ?", Long.MAX_VALUE, otro)
        }
        assertEquals("Equipo", e.s.corregirLugar(otro, l.id, json("""{"direccion":"Equipo"}"""), otroToken).direccion)
        e.s.retirar(otro, ContenidoEquipo("lugar", "${l.id}"), otroToken)
        assertTrue(e.s.buscarLugares("cafe", "bogota").isEmpty())
        assertNull(e.s.verLugar(l.id))
        assertEquals(409, assertFailsWith<Problema> { e.s.crearLugar(e.yo, json("""{"nombre":"CAFE","ciudad":"BOGOTA"}""")) }.codigo)
        e.reloj += 24 * 3600_000L + 1
        e.s.crearLugar(e.yo, json("""{"nombre":"Nuevo día","ciudad":"Bogotá"}"""))
    }
    @Test fun rutasYMedios() = Entorno().use { e ->
        testApplication {
            application { fogonSocial(e.s) }
            assertEquals(HttpStatusCode.Unauthorized, client.post("/v2/apps/cucha/lugares") { contentType(ContentType.Application.Json); setBody("{}") }.status)
            val creada = client.post("/v2/apps/cucha/lugares") { bearerAuth(e.token); contentType(ContentType.Application.Json); setBody("""{"nombre":"Café","ciudad":"Bogotá"}""") }
            assertEquals(HttpStatusCode.Created, creada.status)
            val id = json(creada.bodyAsText()).getValue("id").jsonPrimitive.long
            assertTrue(client.get("/v2/apps/cucha/lugares?nombre=CAFE&ciudad=BOGOTA").bodyAsText().contains("Café"))
            assertEquals(HttpStatusCode.OK, client.patch("/v2/apps/cucha/lugares/$id") { bearerAuth(e.token); contentType(ContentType.Application.Json); setBody("""{"lat":-90,"lon":180}""") }.status)
            for ((tipo, datos) in listOf("plato" to plato, "receta" to receta, "resena_restaurante" to resena)) {
                val carga = JsonSocial.encodeToString(CambiosPublicaciones.serializer(), CambiosPublicaciones(listOf(pub(tipo, json(datos)))))
                assertEquals(HttpStatusCode.NoContent, client.put("/v2/apps/cucha/publicaciones/cambios") { bearerAuth(e.token); contentType(ContentType.Application.Json); setBody(carga) }.status)
            }
            val resenaId = e.s.actividad(e.yo, null, app = "cucha").first { it.tipo == "resena_restaurante" }.id
            val token = e.s.crearCuenta(NuevaCuenta("beto")).token
            assertEquals(HttpStatusCode.NoContent, client.post("/v2/apps/cucha/publicaciones/$resenaId/util") { bearerAuth(token) }.status)
            assertEquals(HttpStatusCode.NoContent, client.delete("/v2/apps/cucha/publicaciones/$resenaId/util") { bearerAuth(token) }.status)
            assertEquals(HttpStatusCode.Created, client.post("/v1/reportes") { bearerAuth(token); contentType(ContentType.Application.Json); setBody("""{"que":"lugar","objeto":"$id","motivo":"otro: falso"}""") }.status)
            for (p in e.s.actividad(e.yo, null, app = "cucha")) {
                val web = client.get("/p/${p.id}").bodyAsText()
                assertTrue(web.contains(if (p.tipo == "plato") "Sopa &lt;rica&gt;" else if (p.tipo == "receta") "Hervir &lt;agua&gt;" else "Café"))
            }
        }
        for ((tipo, datos) in listOf("plato" to plato, "receta" to receta, "resena_restaurante" to resena)) {
            for (medios in listOf(
                mapOf("imagenes" to JsonArray(List(11) { JsonPrimitive("ajena") })),
                mapOf("imagenes" to JsonArray(listOf(JsonPrimitive("ajena")))),
                mapOf("imagenes" to JsonNull), mapOf("video" to JsonPrimitive("ajeno")),
                mapOf("poster" to JsonPrimitive("ajeno")))) {
                assertFailsWith<Problema> { e.s.subirCambios(e.yo, CambiosPublicaciones(listOf(pub(tipo, JsonObject(json(datos) + medios)))), "cucha") }
            }
        }
    }
    @Test fun loteAtomicoUtilYWebSinCoordenadas() = Entorno().use { e ->
        e.s.crearLugar(e.yo, json("""{"nombre":"Café <x>","ciudad":"Bogotá","direccion":"Calle <uno>","lat":4.123456,"lon":-74.123456}"""))
        e.s.subirCambios(e.yo, CambiosPublicaciones(listOf(pub("resena_restaurante", json(resena)), pub("receta", json(receta)), pub("plato", json(plato)))), "cucha")
        val id = e.s.actividad(e.yo, null, app = "cucha").first { it.tipo == "resena_restaurante" }.id
        val otro = e.s.cuentaDe(e.s.crearCuenta(NuevaCuenta("beto")).token)!!
        e.s.util(otro, id, true)
        assertEquals(1, e.s.publicacion(null, id)!!.utiles)
        assertFailsWith<Problema> { e.s.util(e.yo, id, true) }
        assertFailsWith<Problema> { e.s.subirCambios(e.yo, CambiosPublicaciones(listOf(pub("plato", json(plato), "nuevo"), pub("resena_restaurante", JsonObject(json(resena) + ("lugar" to JsonPrimitive(999))), "malo")), listOf("receta")), "cucha") }
        assertEquals(3, e.s.actividad(e.yo, null, app = "cucha").size)
        testApplication {
            application { fogonSocial(e.s) }
            val web = client.get("/p/$id").bodyAsText()
            assertTrue(web.contains("Café &lt;x&gt;")); assertTrue(web.contains("Calle &lt;uno&gt;"))
            assertFalse(web.contains("4.1235")); assertFalse(web.contains("74.1235"))
            e.b.usar { cambiar("UPDATE lugares SET retirado = 1 WHERE id = 1") }
            assertTrue(client.get("/p/$id").bodyAsText().contains("Lugar no disponible"))
            assertEquals(JsonNull, e.s.publicacion(null, id)!!.datos["lugarDetalle"])
        }
    }
}

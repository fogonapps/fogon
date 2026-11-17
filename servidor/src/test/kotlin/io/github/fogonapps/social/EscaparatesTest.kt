package io.github.fogonapps.social

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.*
import kotlin.test.*

class EscaparatesTest {
    private class Entorno : AutoCloseable {
        val dir = Files.createTempDirectory("escaparates").toFile()
        val ruta = File(dir, "s.db").path
        val b = Base(ruta)
        val s = Social(b, File(dir, "fotos"))
        val token = s.crearCuenta(NuevaCuenta("ana")).token
        val yo = s.cuentaDe(token)!!
        val otra = s.cuentaDe(s.crearCuenta(NuevaCuenta("beto")).token)!!
        fun foto(cuenta: Long = yo, visibilidad: String = Vis.PUBLICA): Pair<Long, String> {
            val nombre = s.subirImagen(cuenta, jpegDePrueba()).nombre
            s.subirCambios(cuenta, CambiosPublicaciones(listOf(PublicacionSubida(nombre, visibilidad, 1, "foto", buildJsonObject { put("imagen", nombre) }))))
            return s.actividad(cuenta, null).first { it.datos["imagen"]?.jsonPrimitive?.content == nombre }.id to nombre
        }
        override fun close() { b.cerrar(); dir.deleteRecursively() }
    }
    private fun texto(valor: String = "hola") = Escaparate("texto", buildJsonObject { put("texto", valor) })
    private fun galeria(vararg fotos: Pair<Long, String>) = Escaparate("galeria", buildJsonObject {
        put("fotos", JsonArray(fotos.map { (id, nombre) -> buildJsonObject { put("publicacion", id); put("nombre", nombre) } }))
    })
    private fun falla(mensaje: String, trabajo: () -> Unit) { assertEquals(mensaje, assertFailsWith<Problema>(block = trabajo).mensaje) }

    @Test fun rutasAditivasAutenticacionYCompatibilidad() = Entorno().use { e ->
        testApplication {
            application { fogonSocial(e.s) }
            assertEquals(HttpStatusCode.Unauthorized, client.put("/v1/cuenta/escaparates") {
                contentType(ContentType.Application.Json); setBody("[]")
            }.status)
            val cuerpo = """[{"tipo":"texto","datos":{"texto":"primero"}},{"tipo":"texto","datos":{"texto":"segundo"}}]"""
            assertEquals(HttpStatusCode.NoContent, client.put("/v1/cuenta/escaparates") {
                header(HttpHeaders.Authorization, "Bearer ${e.token}")
                header("X-Fogon-App", "forzudo")
                contentType(ContentType.Application.Json); setBody(cuerpo)
            }.status)
            val respuesta = client.get("/v1/perfil/ana/escaparates")
            assertEquals(HttpStatusCode.OK, respuesta.status)
            assertEquals(JsonSocial.parseToJsonElement(cuerpo), JsonSocial.parseToJsonElement(respuesta.bodyAsText()))
            assertEquals(2, JsonSocial.parseToJsonElement(client.get("/v1/cuenta/escaparates/cupo") {
                header(HttpHeaders.Authorization, "Bearer ${e.token}")
            }.bodyAsText()).jsonObject["total"]!!.jsonPrimitive.int)
            val antes = client.get("/v1/perfiles/ana").bodyAsText()
            assertFalse(JsonSocial.parseToJsonElement(antes).jsonObject.containsKey("escaparates"))
            assertEquals(HttpStatusCode.BadRequest, client.put("/v1/cuenta/escaparates") {
                header(HttpHeaders.Authorization, "Bearer ${e.token}")
                contentType(ContentType.Application.Json); setBody("""[{"tipo":"galeria","datos":{"fotos":"error"}}]""")
            }.status)
        }
    }

    @Test fun albumesConPortadaHttpsOSinPortadaSePuedenVolverAGuardar() = Entorno().use { e ->
        fun album(portada: String) = Escaparate("cajon:albumes", buildJsonObject {
            put("elementos", buildJsonArray {
                add(buildJsonObject { put("titulo", "Disco"); put("texto", "Artista"); put("portada", portada) })
            })
        })
        val repisas = listOf(album("https://ejemplo.invalid/disco.jpg"), album(""))
        e.s.escaparates.guardar(e.yo, repisas)
        e.s.escaparates.guardar(e.yo, e.s.escaparates.ver(e.yo, "ana"))
        assertEquals(repisas, e.s.escaparates.ver(null, "ana"))
        for (portada in listOf("id_suelto", "file:///privado.jpg", "content://album/1")) {
            falla("escaparate_datos") { e.s.escaparates.guardar(e.yo, listOf(album(portada))) }
            assertEquals(repisas, e.s.escaparates.ver(e.yo, "ana"))
        }
    }

    @Test fun cupoGratisNivelComprasYTope() = Entorno().use { e ->
        val x = e.s.escaparates
        assertEquals(CupoEscaparates(0, 2, 0, 2), x.cupo(e.yo))
        x.guardar(e.yo, List(2) { texto() })
        falla("escaparates_cupo") { x.guardar(e.yo, List(3) { texto() }) }
        e.s.subirPerfil(e.yo, PerfilSubido(partes = mapOf("insignias" to Parte(Vis.PUBLICA,
            JsonArray(listOf("cine", "series", "anime", "cine", "inventada").map(::JsonPrimitive))))))
        assertEquals(3, x.cupo(e.yo).nivel)
        x.guardar(e.yo, List(3) { texto() })
        e.b.usar { cambiar("INSERT INTO chispas(cuenta,cantidad,motivo,cuando) VALUES(?,10000,'prueba',1)", e.yo) }
        val saldo = e.s.chispas(e.yo)
        TemasPerfil.EXTRAS.forEach { e.s.reclamarTema(e.yo, it); e.s.reclamarTema(e.yo, it) }
        assertEquals(saldo - 3000, e.s.chispas(e.yo))
        assertEquals(6, x.cupo(e.yo).total)
        falla("no_existe") { e.s.reclamarTema(e.yo, "escaparate:extra4") }
        falla("item") { e.s.ponerTema(e.yo, "escaparate:extra1") }
        for (insignias in 0..30) for (extras in 0..5) {
            val c = Escaparates.calcularCupo(insignias, extras)
            assertEquals(minOf(6, 2 + insignias / 3) + minOf(extras, 3), c.total)
            assertTrue(c.total <= 9)
        }
        Base(e.ruta).cerrar()
        assertEquals(3, x.cupo(e.yo).comprados, "Las compras sobreviven al arranque y su limpieza de catálogo")
    }

    @Test fun validaTiposTamanosYGuardarEsAtomicoConOrden() = Entorno().use { e ->
        val x = e.s.escaparates
        val lista = listOf(texto("primero"), texto("segundo"))
        x.guardar(e.yo, lista)
        assertEquals(lista, x.ver(null, "ana"))
        falla("escaparate_tipo") { x.guardar(e.yo, listOf(texto(), Escaparate("inventado", buildJsonObject {}))) }
        falla("escaparate_datos") { x.guardar(e.yo, listOf(texto("a".repeat(301)))) }
        x.guardar(e.yo, listOf(texto("😀".repeat(300))))
        x.guardar(e.yo, lista.reversed())
        assertEquals(lista.reversed(), x.ver(null, "ana"))
        val meta = Escaparate("alcancia:meta", buildJsonObject { put("numero", 70); put("etiqueta", "%") })
        x.guardar(e.yo, listOf(meta))
        falla("escaparate_datos") { x.guardar(e.yo, listOf(meta.copy(datos = JsonObject(meta.datos + ("monto" to JsonPrimitive(1000)))))) }
        falla("escaparate_datos") { x.guardar(e.yo, listOf(meta.copy(datos = buildJsonObject { put("numero", 101); put("etiqueta", "%") }))) }
        assertEquals(listOf(meta), x.ver(null, "ana"))
        x.guardar(e.yo, emptyList()); assertTrue(x.ver(null, "ana").isEmpty())
    }

    @Test fun fotosAjenasSinPublicarRetiradasOcultasYBorradas() = Entorno().use { e ->
        val x = e.s.escaparates
        val propia = e.foto()
        val ajena = e.foto(e.otra)
        falla("escaparate_foto") { x.guardar(e.yo, listOf(galeria(ajena))) }
        val suelta = e.s.subirImagen(e.yo, jpegDePrueba()).nombre
        falla("escaparate_foto") { x.guardar(e.yo, listOf(galeria(propia.first to suelta))) }
        x.guardar(e.yo, listOf(galeria(propia)))
        assertEquals(listOf(galeria(propia)), x.ver(null, "ana"))
        e.b.usar { cambiar("UPDATE publicaciones SET oculta = 1 WHERE id = ?", propia.first) }
        assertEquals(listOf(galeria()), x.ver(e.yo, "ana"))
        falla("escaparate_foto") { x.guardar(e.yo, listOf(galeria(propia))) }
        e.b.usar { cambiar("UPDATE publicaciones SET oculta = 0, estado = 'retirado' WHERE id = ?", propia.first) }
        assertEquals(listOf(galeria()), x.ver(null, "ana"))
        falla("escaparate_foto") { x.guardar(e.yo, listOf(galeria(propia))) }
        e.b.usar { cambiar("UPDATE publicaciones SET estado = 'visible' WHERE id = ?", propia.first) }
        e.s.borrarPublicacion(e.yo, propia.first)
        assertEquals(listOf(galeria()), x.ver(null, "ana"))
    }

    @Test fun perfilPrivadoBloqueosYFotosSoloParaSeguidores() = Entorno().use { e ->
        val x = e.s.escaparates
        val foto = e.foto(visibilidad = Vis.SEGUIDORES)
        x.guardar(e.yo, listOf(texto(), galeria(foto)))
        assertEquals(listOf(texto(), galeria()), x.ver(null, "ana"))
        assertEquals(listOf(texto(), galeria(foto)), x.ver(e.yo, "ana"))
        e.s.ajustar(e.yo, Ajustes(true))
        assertTrue(x.ver(null, "ana").isEmpty())
        assertEquals(2, x.ver(e.yo, "ana").size)
        e.s.bloquear(e.yo, "beto", true)
        assertEquals(404, assertFailsWith<Problema> { x.ver(e.otra, "ana") }.codigo)
    }

    @Test fun galeriaLimitaSeisYRechazaEstructurasIncorrectas() = Entorno().use { e ->
        val fotos = List(7) { e.foto() }
        e.s.escaparates.guardar(e.yo, listOf(galeria(*fotos.take(6).toTypedArray())))
        falla("escaparate_datos") { e.s.escaparates.guardar(e.yo, listOf(galeria(*fotos.toTypedArray()))) }
        falla("escaparate_datos") { e.s.escaparates.guardar(e.yo, listOf(galeria(fotos[0], fotos[0]))) }
        falla("escaparate_datos") { e.s.escaparates.guardar(e.yo, listOf(Escaparate("galeria", buildJsonObject { put("fotos", "no es lista") }))) }
        assertEquals(7, e.s.escaparates.fotos(e.yo).size)
    }
    @Test fun fotosPropiasExigenDuenoYAccesoAlPerfil() = Entorno().use { e ->
        val nombre = e.s.subirImagen(e.yo, jpegDePrueba()).nombre
        fun propia(n: String) = Escaparate("galeria", buildJsonObject {
            put("fotos", buildJsonArray { add(buildJsonObject { put("nombre", n) }) })
        })
        val ajena = e.s.subirImagen(e.otra, jpegDePrueba()).nombre
        falla("escaparate_foto") { e.s.escaparates.guardar(e.yo, listOf(propia(ajena))) }
        e.s.escaparates.guardar(e.yo, listOf(propia(nombre)))
        assertEquals(listOf(propia(nombre)), e.s.escaparates.ver(null, "ana"))
        assertTrue(e.s.imagen(nombre, null).isNotEmpty())
        assertTrue(e.s.imagen(nombre, e.otra).isNotEmpty())
        e.s.ajustar(e.yo, Ajustes(true))
        assertEquals(404, assertFailsWith<Problema> { e.s.imagen(nombre, null) }.codigo)
        assertEquals(404, assertFailsWith<Problema> { e.s.imagen(nombre, e.otra) }.codigo)
        assertTrue(e.s.imagen(nombre, e.yo).isNotEmpty())
        e.s.seguir(e.otra, "ana")
        e.s.aceptar(e.yo, "beto")
        assertTrue(e.s.imagen(nombre, e.otra).isNotEmpty())
        e.s.ajustar(e.yo, Ajustes(false))
        e.s.bloquear(e.yo, "beto", true)
        assertEquals(404, assertFailsWith<Problema> { e.s.imagen(nombre, e.otra) }.codigo)
    }

    @Test fun limpiarHistoriaConservaFotoUsadaEnEscaparate() = Entorno().use { e ->
        val nombre = e.s.subirImagen(e.yo, jpegDePrueba()).nombre
        val historia = e.s.subirHistoria(e.yo, "cajon", HistoriaNueva(buildJsonObject { put("imagen", nombre) }))
        val galeria = Escaparate("galeria", buildJsonObject {
            put("fotos", buildJsonArray { add(buildJsonObject { put("nombre", nombre) }) })
        })
        e.s.escaparates.guardar(e.yo, listOf(galeria))
        e.s.borrarHistoria(e.yo, historia.id)
        assertTrue(e.s.imagen(nombre).isNotEmpty())
        e.s.escaparates.guardar(e.yo, emptyList())
        e.s.ajustar(e.yo, Ajustes(true))
        assertEquals(404, assertFailsWith<Problema> { e.s.imagen(nombre) }.codigo)
    }

    @Test fun perfilPublicoSerializaNivelInclusoCero() = Entorno().use { e ->
        testApplication {
            application { fogonSocial(e.s) }
            fun nivel(cuerpo: String) = JsonSocial.parseToJsonElement(cuerpo).jsonObject.getValue("nivel").jsonPrimitive.int
            val sinInsignias = client.get("/v1/perfiles/ana").bodyAsText()
            assertEquals(0, nivel(sinInsignias))
            assertEquals(JsonArray(emptyList()), JsonSocial.parseToJsonElement(sinInsignias).jsonObject.getValue("insigniasNivel"))
            e.s.subirPerfil(e.yo, PerfilSubido(partes = mapOf("insignias" to Parte(Vis.PUBLICA,
                JsonArray(listOf("cine", "series", "anime").map(::JsonPrimitive))))))
            val conInsignias = client.get("/v1/perfiles/ana").bodyAsText()
            assertEquals(3, nivel(conInsignias))
            assertEquals(setOf("cine", "series", "anime"), JsonSocial.parseToJsonElement(conInsignias).jsonObject.getValue("insigniasNivel").jsonArray.map { it.jsonPrimitive.content }.toSet())
        }
    }

}

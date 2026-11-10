package io.github.fogonapps.social

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.nio.file.Files
import kotlin.test.*

class VideosTest {
    private fun caja(tipo: String, vararg partes: ByteArray): ByteArray {
        val datos = partes.fold(byteArrayOf()) { a, b -> a + b }
        return ByteBuffer.allocate(8).putInt(datos.size + 8).put(tipo.toByteArray()).array() + datos
    }
    private fun entero(n: Int) = ByteBuffer.allocate(4).putInt(n).array()
    private fun mp4(segundos: Int = 30, ancho: Int = 1280, codec: String = "avc1", audio: String? = "mp4a", version: Int = 0): ByteArray {
        val mvhd = if (version == 0) ByteArray(12) + entero(1000) + entero(segundos * 1000)
            else byteArrayOf(1,0,0,0) + ByteArray(16) + entero(1000) + ByteBuffer.allocate(8).putLong(segundos * 1000L).array()
        fun pista(tipo: String, cod: String): ByteArray {
            val tkhd = ByteArray(76) + entero(ancho shl 16) + entero(720 shl 16)
            return caja("trak", caja("tkhd", tkhd), caja("mdia", caja("hdlr", ByteArray(8), tipo.toByteArray()),
                caja("minf", caja("stbl", caja("stsd", ByteArray(4), entero(1), caja(cod))))))
        }
        return caja("ftyp", "isom".toByteArray(), ByteArray(4), "mp42".toByteArray()) +
            caja("moov", caja("mvhd", mvhd), pista("vide", codec), audio?.let { pista("soun", it) } ?: byteArrayOf()) + caja("mdat", ByteArray(32))
    }
    private fun escenario(prueba: (Social, Base, Long, Long, String) -> Unit) {
        val dir = Files.createTempDirectory("videos").toFile()
        val b = Base(java.io.File(dir, "s.db").path)
        try {
            val s = Social(b, java.io.File(dir, "fotos"))
            val token = s.crearCuenta(NuevaCuenta("ana")).token
            val ana = s.cuentaDe(token)!!
            val beto = s.cuentaDe(s.crearCuenta(NuevaCuenta("beto")).token)!!
            b.usar { cambiar("INSERT INTO imagenes VALUES('poster', ?, 0)", ana) }
            prueba(s, b, ana, beto, token)
        } finally { b.cerrar(); dir.deleteRecursively() }
    }
    private fun pub(nombre: String, poster: String = "poster", vis: String = Vis.PUBLICA, fotos: Boolean = false) = PublicacionSubida("v", vis, 1, "foto", buildJsonObject {
        put("video", nombre); put("poster", poster)
        if (fotos) put("imagenes", JsonArray(listOf(JsonPrimitive(poster))))
    })
    @Test fun cajasDuracionTamanoYCodecs() {
        assertEquals(DatosVideo(30.0,1280,720), revisarVideo(mp4()))
        val normal = mp4()
        val tamFtyp = ByteBuffer.wrap(normal).int
        val extendido = entero(1) + "ftyp".toByteArray() + ByteBuffer.allocate(8).putLong(tamFtyp + 8L).array() + normal.sliceArray(8 until tamFtyp) + normal.drop(tamFtyp).toByteArray()
        assertEquals(revisarVideo(normal), revisarVideo(extendido))
        assertEquals(31.0, revisarVideo(mp4(31, audio = null, version = 1)).duracion)
        for ((datos, codigo) in listOf(
            mp4(0) to "video_formato", mp4(32) to "video_duracion", mp4(ancho = 1281) to "video_grande",
            mp4(codec = "hvc1") to "video_formato", mp4(audio = "ac-3") to "video_formato",
            byteArrayOf() to "video_formato", mp4().copyOf(40) to "video_formato",
            ByteArray(MAX_VIDEO + 1) to "video_grande", mp4().also { it[0] = 127 } to "video_formato"
        )) assertEquals(codigo, assertFailsWith<Problema> { revisarVideo(datos) }.mensaje)
    }
    @Test fun propiedadExclusionVisibilidadYBorrado() = escenario { s, b, ana, beto, _ ->
        val nombre = s.subirVideo(ana, mp4()).nombre
        assertTrue(s.video(nombre, ana, null).isFile)
        assertFailsWith<Problema> { s.video(nombre, null, null) }
        assertEquals("video_formato", assertFailsWith<Problema> { s.subirPublicaciones(beto, listOf(pub(nombre))) }.mensaje)
        assertEquals("poster", assertFailsWith<Problema> { s.subirPublicaciones(ana, listOf(pub(nombre, "ajena"))) }.mensaje)
        assertEquals("video_y_fotos", assertFailsWith<Problema> { s.subirPublicaciones(ana, listOf(pub(nombre, fotos = true))) }.mensaje)
        s.subirPublicaciones(ana, listOf(pub(nombre, vis = Vis.SEGUIDORES)))
        assertFailsWith<Problema> { s.video(nombre, beto, null) }
        s.seguir(beto, "ana")
        assertTrue(s.video(nombre, beto, null).isFile)
        s.bloquear(ana, "beto", true)
        assertFailsWith<Problema> { s.video(nombre, beto, null) }
        s.subirPublicaciones(ana, listOf(pub(nombre)))
        val archivo = s.video(nombre, null, null)
        b.usar { cambiar("UPDATE publicaciones SET oculta = 1") }
        assertFailsWith<Problema> { s.video(nombre, null, null) }
        assertTrue(s.video(nombre, ana, null).exists())
        b.usar { cambiar("UPDATE publicaciones SET oculta = 0") }
        s.borrarPublicacion(ana, s.actividad(ana, null).single().id)
        assertFailsWith<Problema> { s.video(nombre, null, null) }
        assertFalse(archivo.exists())
        val otra = s.subirVideo(ana, mp4()).nombre
        val archivoCuenta = s.video(otra, ana, null)
        s.borrarCuenta(ana)
        assertFalse(archivoCuenta.exists())
        assertFalse(archivo.exists())
        assertTrue(b.usar { leer("SELECT 1 FROM videos") { 1 } }.isEmpty())
    }
    @Test fun entrenoConVideoPropioExclusionYPrivacidad() = escenario { s, _, ana, beto, _ ->
        val nombre = s.subirVideo(ana, mp4()).nombre
        val datos = JsonSocial.parseToJsonElement("""{
            "version":1,"titulo":"Fuerza","fecha":1,"duracion":3600,"unidadDuracion":"s",
            "ejercicios":[{"id":"a","nombre":"Ejercicio","series":[{"repeticiones":10}]}]
        }""").jsonObject
        val entreno = PublicacionSubida("entreno", Vis.SEGUIDORES, 1, "entreno", JsonObject(datos + mapOf("video" to JsonPrimitive(nombre), "poster" to JsonPrimitive("poster"))))
        s.subirCambios(ana, CambiosPublicaciones(listOf(entreno)), "forzudo")
        assertFailsWith<Problema> { s.video(nombre, beto, null) }
        s.seguir(beto, "ana")
        assertTrue(s.video(nombre, beto, null).isFile)
        assertTrue(s.actividad(beto, null).isEmpty())
        val conFotos = entreno.copy(datos = JsonObject(entreno.datos + ("imagenes" to JsonArray(listOf(JsonPrimitive("poster"))))))
        assertEquals("video_y_fotos", assertFailsWith<Problema> { s.subirCambios(ana, CambiosPublicaciones(listOf(conFotos)), "forzudo") }.mensaje)
        val ajeno = entreno.copy(datos = JsonObject(entreno.datos + ("video" to JsonPrimitive("ajeno"))))
        assertEquals("video_formato", assertFailsWith<Problema> { s.subirCambios(ana, CambiosPublicaciones(listOf(ajeno)), "forzudo") }.mensaje)
        assertEquals("entreno", s.actividad(beto, null, app = "forzudo").single().tipo)
    }

    @Test fun moderacionPreservaYLaPurgaRespetaReferencias() = escenario { s, b, ana, _, _ ->
        val nombre = s.subirVideo(ana, mp4()).nombre
        s.subirPublicaciones(ana, listOf(pub(nombre)))
        val archivo = s.video(nombre, null, null)
        b.usar { cambiar("UPDATE publicaciones SET estado = 'preservado'") }
        assertFailsWith<Problema> { s.video(nombre, ana, null) }
        s.borrarCuenta(ana); s.limpiarSeguridad()
        assertTrue(archivo.exists())
        b.usar { cambiar("UPDATE publicaciones SET estado = 'retirado', retirado_hasta = ?", System.currentTimeMillis() + 100_000) }
        assertFailsWith<Problema> { s.video(nombre, null, null) }
        s.limpiarSeguridad(); assertTrue(archivo.exists())
        b.usar { cambiar("UPDATE publicaciones SET retirado_hasta = 0") }
        s.limpiarSeguridad(); assertFalse(archivo.exists())
    }
    @Test fun rutasSubidaYRange() = escenario { s, _, ana, _, token ->
        testApplication {
            application { fogonSocial(s) }
            val subido = client.put("/v1/videos") { bearerAuth(token); setBody(mp4()) }
            assertEquals(HttpStatusCode.Created, subido.status)
            val nombre = JsonSocial.parseToJsonElement(subido.bodyAsText()).jsonObject["nombre"]!!.jsonPrimitive.content
            s.subirPublicaciones(ana, listOf(pub(nombre)))
            val total = client.get("/v1/videos/$nombre")
            assertEquals(HttpStatusCode.OK, total.status)
            assertEquals("video/mp4", total.headers[HttpHeaders.ContentType])
            for ((rango, inicio, fin) in listOf(Triple("bytes=0-7", 0, 7), Triple("bytes=8-", 8, mp4().lastIndex), Triple("bytes=-5", mp4().size - 5, mp4().lastIndex))) {
                val r = client.get("/v1/videos/$nombre") { header(HttpHeaders.Range, rango) }
                assertEquals(HttpStatusCode.PartialContent, r.status)
                assertEquals("bytes $inicio-$fin/${mp4().size}", r.headers[HttpHeaders.ContentRange])
                assertContentEquals(mp4().sliceArray(inicio..fin), r.bodyAsBytes())
            }
            assertEquals(HttpStatusCode.RequestedRangeNotSatisfiable, client.get("/v1/videos/$nombre") { header(HttpHeaders.Range, "bytes=99999-") }.status)
            assertEquals(HttpStatusCode.BadRequest, client.put("/v1/videos") { bearerAuth(token); setBody(mp4(32)) }.status)
            assertEquals(HttpStatusCode.BadRequest, client.put("/v1/videos") { bearerAuth(token); setBody(ByteArray(MAX_VIDEO + 1)) }.status)
        }
    }
}

package io.github.fogonapps.social

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.test.*

class IdentidadCuentaTest {
    private class Entorno(secretoPrueba: ByteArray? = null) : AutoCloseable {
        val dir = Files.createTempDirectory("identidad-cuenta").toFile()
        val base = Base(File(dir, "s.db").path)
        var reloj = 2_000_000_000_000L
        private val cartas = mutableListOf<Pair<String, String>>()
        val s = Social(base, File(dir, "fotos"), { reloj }, Cartero { _, asunto, texto -> cartas += asunto to texto })
        init { secretoPrueba?.let { File(dir, "secreto").writeBytes(it) } }
        fun codigo() = Regex("[0-9]{6}").find(cartas.last { it.first.startsWith("Tu código") }.first)!!.value
        fun alta(usuario: String = "ana"): Acceso {
            s.pedirCodigo("$usuario@correo.co")
            return s.entrar(Entrada("$usuario@correo.co", codigo(), usuario, usuario, Social.POLITICA, true, "una frase bastante larga"))
        }
        fun entrar() = s.entrarConClave(EntradaClave("ana@correo.co", "una frase bastante larga"))
        override fun close() { base.cerrar(); dir.deleteRecursively() }
    }

    @Test fun estableEntreSesionesReaperturasYCambioDeUsuario() = Entorno().use { e ->
        val alta = e.alta()
        val yo = e.s.cuentaDe(alta.token)!!
        val segunda = e.entrar()
        assertNotEquals(alta.token, segunda.token)
        assertEquals(alta.idCuenta, segunda.idCuenta)
        assertEquals(alta.idCuenta, e.s.miCuenta(yo, alta.token).idCuenta)
        assertEquals(alta.idCuenta, e.s.miCuenta(yo, segunda.token).idCuenta)
        assertTrue(alta.idCuenta.matches(Regex("[A-Za-z0-9_-]{22}")))
        val secretoAntes = File(e.dir, "secreto").readBytes()
        e.s.cambiarUsuario(yo, CambioUsuario("ana_nueva"))
        assertEquals("ana_nueva", e.entrar().usuario)
        assertEquals(alta.idCuenta, e.entrar().idCuenta)
        val reabierta = Social(e.base, File(e.dir, "fotos"), { e.reloj })
        assertEquals(alta.idCuenta, reabierta.miCuenta(yo, segunda.token).idCuenta)
        assertContentEquals(secretoAntes, File(e.dir, "secreto").readBytes())
        assertNotEquals(alta.idCuenta, e.alta("beto").idCuenta)
    }

    @Test fun usaHmacConSecretoYSeparacionDeDominioSinExponerElNumero() {
        // Material sintético exclusivo de la prueba, nunca el secreto de una instalación.
        val secretoPrueba = ByteArray(32) { (it + 1).toByte() }
        Entorno(secretoPrueba).use { e ->
            val acceso = e.alta(); val yo = e.s.cuentaDe(acceso.token)!!
            val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secretoPrueba, "HmacSHA256")) }
            val esperado = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal("cuenta:$yo".toByteArray(Charsets.UTF_8))).take(22)
            assertEquals(esperado, acceso.idCuenta)
            val sinSecreto = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest("cuenta:$yo".toByteArray())).take(22)
            assertNotEquals(sinSecreto, acceso.idCuenta)
            assertNotEquals(yo.toString(), acceso.idCuenta)
            assertNull(e.s.cuentaDe(acceso.idCuenta)) // Es una identidad pública, no una credencial.
            Entorno(ByteArray(32) { (it + 2).toByte() }).use { otra ->
                val otraAlta = otra.alta()
                assertEquals(yo, otra.s.cuentaDe(otraAlta.token))
                assertNotEquals(acceso.idCuenta, otraAlta.idCuenta)
            }
        }
    }

    @Test fun borrarLaUltimaCuentaYReabrirNoReutilizaSuIdentidad() = Entorno().use { e ->
        val alta = e.alta(); val id = e.s.cuentaDe(alta.token)!!
        e.base.usar { cambiar("DELETE FROM cuentas WHERE id = ?", id) }
        Base(File(e.dir, "s.db").path).cerrar()
        val siguiente = e.alta("beto")
        assertTrue(e.s.cuentaDe(siguiente.token)!! > id)
        assertNotEquals(alta.idCuenta, siguiente.idCuenta)
    }

    @Test fun inicializaElAsignadorDesdeUnaBaseAnteriorSinCambiarCuentas() = Entorno().use { e ->
        val alta = e.alta(); val id = e.s.cuentaDe(alta.token)!!
        e.base.usar { cambiar("DROP TABLE secuencia_cuentas") }
        Base(File(e.dir, "s.db").path).cerrar()
        assertEquals(alta.idCuenta, e.s.miCuenta(id).idCuenta)
        assertEquals(id, e.s.cuentaDe(alta.token))
        val siguiente = e.alta("beto")
        assertTrue(e.s.cuentaDe(siguiente.token)!! > id)
        Base(File(e.dir, "s.db").path).cerrar()
        assertEquals(alta.idCuenta, e.s.miCuenta(id).idCuenta)
    }

    @Test fun secretoIncompletoNoSeRegeneraSilenciosamente() = Entorno().use { e ->
        val alta = e.alta(); val yo = e.s.cuentaDe(alta.token)!!
        File(e.dir, "secreto").writeBytes(byteArrayOf(1, 2, 3))
        val reabierta = Social(e.base, File(e.dir, "fotos"))
        assertFailsWith<IllegalStateException> { reabierta.miCuenta(yo) }
        assertContentEquals(byteArrayOf(1, 2, 3), File(e.dir, "secreto").readBytes())
    }

    @Serializable private data class AccesoAnterior(val usuario: String, val token: String, val nueva: Boolean, val borrarEl: Long)

    @Test fun todasLasRutasDeAccesoYCuentaPublicanLaMismaIdentidad() = Entorno().use { e ->
        testApplication {
            application { fogonSocial(e.s) }
            e.s.pedirCodigo("ana@correo.co")
            val alta = client.post("/v1/acceso") {
                contentType(ContentType.Application.Json)
                setBody("""{"correo":"ana@correo.co","codigo":"${e.codigo()}","usuario":"ana","nombre":"Ana","acepta":"${Social.POLITICA}","mayorDeEdad":true,"clave":"una frase bastante larga"}""")
            }
            assertEquals(HttpStatusCode.OK, alta.status)
            val respuesta = JsonSocial.parseToJsonElement(alta.bodyAsText()).jsonObject
            val identidad = respuesta.getValue("idCuenta").jsonPrimitive.content
            val token = respuesta.getValue("token").jsonPrimitive.content
            val anterior = Json { ignoreUnknownKeys = true }.decodeFromString(AccesoAnterior.serializer(), alta.bodyAsText())
            assertEquals(token, anterior.token)
            val porClave = client.post("/v1/acceso/clave") {
                contentType(ContentType.Application.Json)
                setBody("""{"correo":"ana@correo.co","clave":"una frase bastante larga"}""")
            }
            assertEquals(HttpStatusCode.OK, porClave.status)
            assertEquals(identidad, JsonSocial.parseToJsonElement(porClave.bodyAsText()).jsonObject["idCuenta"]!!.jsonPrimitive.content)
            e.reloj += 60_000; e.s.pedirCodigo("ana@correo.co")
            val porCodigo = client.post("/v1/acceso") {
                contentType(ContentType.Application.Json)
                setBody("""{"correo":"ana@correo.co","codigo":"${e.codigo()}"}""")
            }
            assertEquals(HttpStatusCode.OK, porCodigo.status)
            assertEquals(identidad, JsonSocial.parseToJsonElement(porCodigo.bodyAsText()).jsonObject["idCuenta"]!!.jsonPrimitive.content)
            val cuenta = client.get("/v1/cuenta") { header(HttpHeaders.Authorization, "Bearer $token") }
            assertEquals(HttpStatusCode.OK, cuenta.status)
            assertEquals(identidad, JsonSocial.parseToJsonElement(cuenta.bodyAsText()).jsonObject["idCuenta"]!!.jsonPrimitive.content)
        }
    }
}

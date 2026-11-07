package io.github.fogonapps.social

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.*
import java.nio.file.Files
import kotlin.test.*

class CuentasGraciaTest {
    private class Entorno : AutoCloseable {
        val dir = Files.createTempDirectory("cuentas-gracia").toFile()
        val base = Base(java.io.File(dir, "s.db").path)
        var reloj = 2_000_000_000_000L
        data class Carta(val para: String, val asunto: String, val texto: String)
        val buzon = mutableListOf<Carta>()
        val s = Social(base, java.io.File(dir, "fotos"), { reloj }, Cartero { para, asunto, texto -> buzon += Carta(para, asunto, texto) })
        val clave = "una frase bastante larga"
        fun codigo(correo: String) = Regex("[0-9]{6}").find(buzon.last { it.para == correo && it.asunto.startsWith("Tu código") }.asunto)!!.value
        fun alta(u: String = "ana", conClave: Boolean = true): Acceso {
            s.pedirCodigo("$u@correo.co")
            return s.entrar(Entrada("$u@correo.co", codigo("$u@correo.co"), u, u, Social.POLITICA, true, if (conClave) clave else ""))
        }
        fun id(a: Acceso) = s.cuentaDe(a.token)!!
        fun error(codigo: String, f: () -> Unit) = assertEquals(codigo, assertFailsWith<Problema>(block = f).mensaje)
        override fun close() { base.cerrar(); dir.deleteRecursively() }
    }

    @Test fun migracionDeBaseSinGraciaEsIdempotente() {
        val dir = Files.createTempDirectory("migracion-gracia").toFile()
        val ruta = java.io.File(dir, "s.db").path
        try {
            val anterior = Base(ruta)
            val s = Social(anterior, java.io.File(dir, "fotos"))
            val a = s.crearCuenta(NuevaCuenta("ana"))
            anterior.cerrar()
            java.sql.DriverManager.getConnection("jdbc:sqlite:$ruta").use { con ->
                con.createStatement().use {
                    it.execute("ALTER TABLE cuentas DROP COLUMN borrar_el")
                    it.execute("DROP TABLE motivos_borrado")
                    it.execute("DROP TABLE cambios_correo")
                }
            }
            repeat(2) {
                val base = Base(ruta)
                try {
                    val actual = Social(base, java.io.File(dir, "fotos"))
                    assertEquals(0, actual.borrarEl(actual.cuentaDe(a.token)!!))
                    assertTrue(actual.buscar(null, "ana").isNotEmpty())
                    assertEquals(0, base.usar { leer("SELECT COUNT(*) FROM motivos_borrado") { it.getInt(1) }.single() })
                } finally { base.cerrar() }
            }
        } finally { dir.deleteRecursively() }
    }

    @Test fun programarConClaveEntrarYConservar() = Entorno().use { e ->
        val a = e.alta(); val yo = e.id(a)
        val otra = e.s.entrarConClave(EntradaClave("ana@correo.co", e.clave))
        e.error("clave_mala") { e.s.programarBorrado(yo, a.token, Borrado(clave = "mala")) }
        assertEquals(0, e.s.borrarEl(yo))
        e.s.programarBorrado(yo, a.token, Borrado(clave = e.clave, motivo = "no_uso", motivoTexto = "No uso la red"))
        val fecha = e.reloj + 7 * 86400_000L
        assertEquals(fecha, e.s.borrarEl(yo))
        assertNull(e.s.cuentaDe(otra.token)); assertEquals(yo, e.id(a))
        assertTrue(e.buzon.last().texto.contains("Tu cuenta se borrará el"))
        assertEquals(fecha, e.s.entrarConClave(EntradaClave("ana@correo.co", e.clave)).borrarEl)
        e.reloj += 60_000
        e.s.pedirCodigo("ana@correo.co")
        assertEquals(fecha, e.s.entrar(Entrada("ana@correo.co", e.codigo("ana@correo.co"))).borrarEl)
        e.error("por_borrar") { e.s.revisarBorrado(yo) }
        e.s.conservarCuenta(yo)
        assertEquals(0, e.s.borrarEl(yo)); e.s.revisarBorrado(yo)
        assertEquals("Tu cuenta ya no se borrará", e.buzon.last().asunto)
        assertEquals(1, e.s.buscar(null, "ana").size)
        val columnas = e.base.usar { leer("PRAGMA table_info(motivos_borrado)") { it.getString("name") } }
        assertEquals(listOf("cuando", "motivo", "texto"), columnas)
    }

    @Test fun programarConCodigoYProtegerAdmin() = Entorno().use { e ->
        val a = e.alta(conClave = false); val yo = e.id(a)
        e.reloj += 60_000; e.s.pedirCodigo("ana@correo.co")
        e.error("codigo") { e.s.programarBorrado(yo, a.token, Borrado(codigo = "malo")) }
        e.s.programarBorrado(yo, a.token, Borrado(codigo = e.codigo("ana@correo.co")))
        assertTrue(e.s.borrarEl(yo) > 0)
        e.s.conservarCuenta(yo)
        e.error("sin_codigo") { e.s.programarBorrado(yo, a.token, Borrado(codigo = e.codigo("ana@correo.co"))) }
        e.base.usar { cambiar("INSERT INTO roles VALUES(?, 'administrador', 'servidor', 0)", yo) }
        e.error("cuenta_protegida") { e.s.programarBorrado(yo, a.token) }
        e.error("cuenta_protegida") { e.s.programarBorrado(yo, a.token, Borrado()) }
        assertEquals(0, e.s.borrarEl(yo))
    }

    @Test fun ocultacionCompleta() = Entorno().use { e ->
        val a = e.alta(); val b = e.alta("beto"); val yo = e.id(a); val otro = e.id(b)
        fun pub(clave: String) = PublicacionSubida(clave, Vis.PUBLICA, e.reloj, "diario", buildJsonObject { put("titulo", "Obra") })
        e.s.subirPublicaciones(yo, listOf(pub("a"))); e.s.subirPublicaciones(otro, listOf(pub("b")))
        e.s.seguir(otro, "ana"); e.s.seguir(yo, "beto")
        val propia = e.s.actividad(yo, null).first { it.autor.usuario == "ana" }.id
        val ajena = e.s.actividad(otro, null).first { it.autor.usuario == "beto" }.id
        e.s.comentar(yo, ajena, "comentario")
        e.s.programarBorrado(yo, a.token, Borrado(clave = e.clave))
        assertTrue(e.s.buscar(null, "ana").isEmpty())
        e.error("no_existe") { e.s.verPerfil(otro, "ana") }
        assertNull(e.s.publicacion(null, propia))
        assertTrue(e.s.actividad(otro, null).none { it.autor.usuario == "ana" })
        assertTrue(e.s.explorar(null, null).none { it.autor.usuario == "ana" })
        assertTrue(e.s.comentarios(otro, ajena).isEmpty())
        assertTrue(e.s.lista(otro, "beto", true).isEmpty())
        assertTrue(e.s.lista(otro, "beto", false).isEmpty())
        e.error("no_existe") { e.s.mandar(otro, "ana", SobreNuevo("sobre", "firma")) }
    }

    @Test fun limpiezaSoloVencidasYReserva() = Entorno().use { e ->
        val a = e.alta(); val b = e.alta("beto"); val c = e.alta("clara")
        val yo = e.id(a); val otro = e.id(b)
        e.s.guardarCajon(yo, CajonCifrado("cifrado"), 0)
        val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 1, 2)
        e.s.subirFoto(yo, jpeg)
        val imagen = e.s.subirImagen(yo, jpeg)
        e.s.programarBorrado(yo, a.token)
        e.reloj += 86400_000
        e.s.programarBorrado(otro, b.token)
        e.reloj += 6 * 86400_000L - 1
        e.s.limpiarSeguridad(); assertEquals(yo, e.s.cuentaDe(a.token))
        e.reloj++; e.s.limpiarSeguridad()
        assertNull(e.s.cuentaDe(a.token)); assertNotNull(e.s.cuentaDe(b.token)); assertNotNull(e.s.cuentaDe(c.token))
        assertEquals(0, e.base.usar { leer("SELECT COUNT(*) FROM cajones WHERE cuenta = ?", yo) { it.getInt(1) }.single() })
        assertFalse(java.io.File(e.dir, "fotos/$yo.jpg").exists())
        assertFalse(java.io.File(e.dir, "fotos/i/${imagen.nombre}.jpg").exists())
        assertFalse(e.s.usuarioLibre("ana"))
        val hasta = e.base.usar { leer("SELECT hasta FROM usuarios_viejos WHERE usuario = 'ana'") { it.getLong(1) }.single() }
        assertEquals(e.reloj + 30 * 86400_000L, hasta)
        e.reloj = hasta; assertTrue(e.s.usuarioLibre("ana"))
    }

    @Test fun cambioCorreoCompletoYAvisos() = Entorno().use { e ->
        val a = e.alta(); val yo = e.id(a); e.alta("beto")
        e.error("clave_mala") { e.s.cambiarCorreo(yo, CambioCorreo("nueva@correo.co", clave = "mala")) }
        e.s.cambiarCorreo(yo, CambioCorreo("nueva@correo.co", clave = e.clave))
        val codigo = e.codigo("nueva@correo.co")
        e.error("sin_codigo") { e.s.entrar(Entrada("nueva@correo.co", codigo)) }
        e.error("codigo") { e.s.confirmarCorreo(yo, CambioCorreo("nueva@correo.co", codigo = "malo")) }
        assertEquals("ana@correo.co", e.s.miCuenta(yo).correo)
        assertTrue(e.buzon.any { it.para == "ana@correo.co" && it.asunto == "Solicitud de cambio de correo" })
        e.s.confirmarCorreo(yo, CambioCorreo(" NUEVA@correo.co ", codigo = codigo))
        assertEquals("nueva@correo.co", e.s.miCuenta(yo).correo)
        assertEquals(setOf("ana@correo.co", "nueva@correo.co"), e.buzon.filter { it.asunto == "Tu correo de El Cajón cambió" }.map { it.para }.toSet())
        e.error("sin_codigo") { e.s.confirmarCorreo(yo, CambioCorreo("nueva@correo.co", codigo = codigo)) }
        e.reloj += 60_000
        e.s.cambiarCorreo(yo, CambioCorreo("beto@correo.co", clave = e.clave))
        e.error("correo_tomado") { e.s.confirmarCorreo(yo, CambioCorreo("beto@correo.co", codigo = e.codigo("beto@correo.co"))) }
        assertEquals("nueva@correo.co", e.s.miCuenta(yo).correo)
        e.error("correo") { e.s.vincularCorreo(yo, Entrada("otra@correo.co", "123456")) }
    }

    @Test fun cambioSinClaveIntentosVencimientoYLimites() = Entorno().use { e ->
        val a = e.alta(conClave = false); val yo = e.id(a)
        e.reloj += 60_000; e.s.pedirCodigo("ana@correo.co")
        e.s.cambiarCorreo(yo, CambioCorreo("nueva@correo.co", codigo = e.codigo("ana@correo.co")))
        val codigo = e.codigo("nueva@correo.co")
        repeat(5) { e.error("codigo") { e.s.confirmarCorreo(yo, CambioCorreo("nueva@correo.co", codigo = "mal")) } }
        e.error("codigo_vencido") { e.s.confirmarCorreo(yo, CambioCorreo("nueva@correo.co", codigo = codigo)) }
        e.reloj += 60_000; e.s.pedirCodigo("ana@correo.co")
        e.s.cambiarCorreo(yo, CambioCorreo("nueva@correo.co", codigo = e.codigo("ana@correo.co")))
        e.error("despacio") { e.s.pedirCodigo("nueva@correo.co") }
        e.reloj += 10 * 60_000
        e.error("codigo_vencido") { e.s.confirmarCorreo(yo, CambioCorreo("nueva@correo.co", codigo = e.codigo("nueva@correo.co"))) }
    }

    @Test fun rutasNuevasConCredencialesYCuentaProtegida() = Entorno().use { e -> testApplication {
        application { fogonSocial(e.s) }
        val a = e.alta(); val yo = e.id(a)
        suspend fun enviar(ruta: String, cuerpo: String) = client.post("/v1/$ruta") {
            header(HttpHeaders.Authorization, "Bearer ${a.token}")
            contentType(ContentType.Application.Json); setBody(cuerpo)
        }
        assertEquals(HttpStatusCode.BadRequest, enviar("cuenta/borrar", """{"clave":"mala"}""").status)
        assertEquals(HttpStatusCode.NoContent, enviar("cuenta/correo/cambiar", """{"nuevo":"nuevo@correo.co","clave":"${e.clave}"}""").status)
        assertEquals(HttpStatusCode.NoContent, enviar("cuenta/correo/confirmar", """{"nuevo":"nuevo@correo.co","codigo":"${e.codigo("nuevo@correo.co")}"}""").status)
        assertEquals(HttpStatusCode.NoContent, enviar("cuenta/borrar", """{"clave":"${e.clave}"}""").status)
        val acceso = client.post("/v1/acceso/clave") {
            contentType(ContentType.Application.Json); setBody("""{"correo":"nuevo@correo.co","clave":"${e.clave}"}""")
        }
        assertEquals(e.s.borrarEl(yo), Json.parseToJsonElement(acceso.bodyAsText()).jsonObject["borrarEl"]!!.jsonPrimitive.long)
        assertEquals(HttpStatusCode.NoContent, enviar("cuenta/conservar", "{}").status)
        e.base.usar { cambiar("INSERT INTO roles VALUES(?, 'administrador', 'servidor', 0)", yo) }
        assertEquals(HttpStatusCode.Forbidden, enviar("cuenta/borrar", "{}").status)
        assertEquals(HttpStatusCode.Forbidden, client.delete("/v1/cuenta") { header(HttpHeaders.Authorization, "Bearer ${a.token}") }.status)
    } }

    @Test fun rutasCompatibilidad148YBloqueo() = Entorno().use { e -> testApplication {
        application { fogonSocial(e.s) }
        val a = e.alta(); val yo = e.id(a)
        val otra = e.s.entrarConClave(EntradaClave("ana@correo.co", e.clave))
        suspend fun rechazarDeleteSinCambios() {
            val fecha = e.s.borrarEl(yo)
            val sesiones = e.s.sesiones(yo, a.token)
            val r = client.delete("/v1/cuenta") { header(HttpHeaders.Authorization, "Bearer ${a.token}") }
            assertEquals(HttpStatusCode.Forbidden, r.status)
            assertEquals("actualiza_app", Json.parseToJsonElement(r.bodyAsText()).jsonObject["error"]!!.jsonPrimitive.content)
            assertEquals(fecha, e.s.borrarEl(yo))
            assertEquals(sesiones, e.s.sesiones(yo, a.token))
            assertEquals(yo, e.s.cuentaDe(a.token))
        }
        rechazarDeleteSinCambios()
        assertEquals(0, e.s.borrarEl(yo))
        assertEquals(yo, e.s.cuentaDe(otra.token))
        assertEquals(HttpStatusCode.OK, client.get("/v1/actividad") { header(HttpHeaders.Authorization, "Bearer ${a.token}") }.status)
        assertEquals(HttpStatusCode.NoContent, client.post("/v1/cuenta/borrar") {
            header(HttpHeaders.Authorization, "Bearer ${a.token}")
            contentType(ContentType.Application.Json); setBody("""{"clave":"${e.clave}"}""")
        }.status)
        assertTrue(e.s.borrarEl(yo) > 0)
        assertNull(e.s.cuentaDe(otra.token))
        val duranteGracia = e.s.entrarConClave(EntradaClave("ana@correo.co", e.clave))
        rechazarDeleteSinCambios()
        assertEquals(yo, e.s.cuentaDe(duranteGracia.token))
        for (ruta in listOf("/v1/actividad", "/v1/sesiones", "/v1/buscar?q=ana", "/v1/explorar", "/v1/cuenta/estado")) {
            val respuesta = client.get(ruta) { header(HttpHeaders.Authorization, "Bearer ${a.token}") }
            assertEquals(HttpStatusCode.Forbidden, respuesta.status, ruta)
            assertEquals("por_borrar", Json.parseToJsonElement(respuesta.bodyAsText()).jsonObject["error"]!!.jsonPrimitive.content)
        }
        assertEquals(HttpStatusCode.OK, client.get("/v1/cuenta") { header(HttpHeaders.Authorization, "Bearer ${a.token}") }.status)
        assertEquals(HttpStatusCode.NoContent, client.post("/v1/cuenta/conservar") { header(HttpHeaders.Authorization, "Bearer ${a.token}") }.status)
        assertEquals(HttpStatusCode.OK, client.get("/v1/actividad") { header(HttpHeaders.Authorization, "Bearer ${a.token}") }.status)
    } }
}

package io.github.fogonapps.social

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.*

class EquipoTest {
    private class Entorno : AutoCloseable {
        val dir = Files.createTempDirectory("equipo").toFile()
        val ruta = File(dir, "social.db").path
        val b = Base(ruta)
        var reloj = 2_000_000_000_000L
        val correos = mutableListOf<Triple<String, String, String>>()
        val cartero = Cartero { a, t, c -> correos += Triple(a, t, c) }
        val s = Social(b, File(dir, "fotos"), { reloj }, cartero)
        data class Cuenta(val id: Long, val token: String, val usuario: String)
        fun alta(usuario: String): Cuenta {
            val a = s.crearCuenta(NuevaCuenta(usuario))
            return Cuenta(s.cuentaDe(a.token)!!, a.token, usuario)
        }
        fun rol(c: Cuenta, rol: String) = b.usar {
            cambiar("DELETE FROM roles WHERE cuenta = ?", c.id)
            if (rol.isNotEmpty()) cambiar("INSERT INTO roles VALUES(?,?, 'servidor', ?)", c.id, rol, reloj)
        }
        fun inscribir(c: Cuenta): Pair<ByteArray, Recuperacion> {
            val i = s.equipo.activar(c.id, Factor(), c.token)
            val bytes = Totp.decodificar(i.secreto)
            val codigos = s.equipo.confirmar(c.id, Factor(codigo = Totp.codigo(bytes, reloj / 30_000)), c.token)
            return bytes to codigos
        }
        fun siguiente(bytes: ByteArray): String { reloj += 30_000; return Totp.codigo(bytes, reloj / 30_000) }
        fun elevar(c: Cuenta, bytes: ByteArray) = s.equipo.elevar(c.id, Factor(codigo = siguiente(bytes)), c.token)
        fun pub(c: Cuenta, clave: String = "a", datos: JsonObject = buildJsonObject { put("texto", "Evidencia") }): Long {
            s.subirPublicaciones(c.id, listOf(PublicacionSubida(clave, Vis.PUBLICA, reloj, "diario", datos)))
            return b.usar { leer("SELECT id FROM publicaciones WHERE cuenta = ? AND clave = ?", c.id, clave) { it.getLong(1) }.single() }
        }
        override fun close() { b.cerrar(); dir.deleteRecursively() }
    }

    @Test fun vectoresRfc6238Sha1YBase32() {
        val secreto = "12345678901234567890".toByteArray()
        val vectores = listOf(59L to "94287082", 1111111109L to "07081804", 1111111111L to "14050471", 1234567890L to "89005924", 2000000000L to "69279037", 20000000000L to "65353130")
        for ((segundos, esperado) in vectores) {
            assertEquals(esperado, Totp.codigo(secreto, segundos / 30, 8))
            assertEquals(esperado.takeLast(6), Totp.codigo(secreto, segundos / 30))
        }
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", Totp.base32(secreto))
        assertContentEquals(secreto, Totp.decodificar(Totp.base32(secreto)))
    }

    @Test fun ventanaRepeticionRecuperacionYCaducidad() = Entorno().use { e ->
        val a = e.alta("ana"); e.rol(a, "administrador")
        val (secreto, codigos) = e.inscribir(a)
        assertEquals(10, codigos.codigos.toSet().size)
        assertFalse(e.b.usar { leer("SELECT secreto_cifrado FROM mfa") { it.getString(1) }.single() }.contains(Totp.base32(secreto)))
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(File(e.dir, "llave-mfa").toPath()))
        assertEquals("codigo_mfa", assertFailsWith<Problema> { e.s.equipo.elevar(a.id, Factor(codigo = Totp.codigo(secreto, e.reloj / 30_000)), a.token) }.mensaje)
        e.reloj += 60_000
        e.s.equipo.elevar(a.id, Factor(codigo = Totp.codigo(secreto, e.reloj / 30_000 - 1)), a.token)
        e.s.equipo.elevar(a.id, Factor(codigo = Totp.codigo(secreto, e.reloj / 30_000 + 1)), a.token)
        assertFailsWith<Problema> { e.s.equipo.elevar(a.id, Factor(codigo = Totp.codigo(secreto, e.reloj / 30_000 + 1)), a.token) }
        assertFailsWith<Problema> { e.s.equipo.elevar(a.id, Factor(codigo = Totp.codigo(secreto, e.reloj / 30_000 + 2)), a.token) }
        val hasta = e.s.equipo.elevar(a.id, Factor(recuperacion = codigos.codigos.first()), a.token).elevadaHasta
        assertEquals(e.reloj + Equipo.DURACION, hasta)
        assertFailsWith<Problema> { e.s.equipo.elevar(a.id, Factor(recuperacion = codigos.codigos.first()), a.token) }
        val huellas = e.b.usar { leer("SELECT huella FROM codigos_recuperacion") { it.getString(1) } }
        assertTrue(codigos.codigos.none { it in huellas })
        // La elevación pertenece a una sesión concreta.
        val token = Base.tokenNuevo()
        e.b.usar { cambiar("INSERT INTO sesiones(huella, cuenta, creada, ultimo_uso) VALUES(?,?,?,?)", Base.huella(token), a.id, e.reloj, e.reloj) }
        assertEquals(0, e.s.miCuenta(a.id, token).elevadaHasta)
        assertFailsWith<Problema> { e.s.equipo.requiere(a.id, "ver_reportes", token) }
        e.reloj = hasta - 1; e.s.equipo.requiere(a.id, "ver_reportes", a.token)
        e.reloj++; assertEquals("elevacion_requerida", assertFailsWith<Problema> { e.s.equipo.requiere(a.id, "ver_reportes", a.token) }.mensaje)
        val nueva = e.s.equipo.activar(a.id, Factor(recuperacion = codigos.codigos[1]), a.token)
        assertFalse(e.s.equipo.activa(a.id))
        assertNotEquals(Totp.base32(secreto), nueva.secreto)
        assertEquals(0, e.s.equipo.elevada(a.id, a.token))
        val nuevoSecreto = Totp.decodificar(nueva.secreto)
        e.s.equipo.confirmar(a.id, Factor(codigo = Totp.codigo(nuevoSecreto, e.reloj / 30_000)), a.token)
        val renovados = e.s.equipo.regenerar(a.id, Factor(codigo = e.siguiente(nuevoSecreto)), a.token)
        assertEquals(10, renovados.codigos.size)
        assertFailsWith<Problema> { e.s.equipo.elevar(a.id, Factor(recuperacion = codigos.codigos[2]), a.token) }
        e.s.equipo.desactivar(a.id, Factor(codigo = e.siguiente(nuevoSecreto)), a.token)
        assertFalse(e.s.miCuenta(a.id, a.token).mfa)
        assertTrue(e.s.equipo.verificarCadena())
    }

    @Test fun equipoSoloPuedeModificarDestinosInferiores() = Entorno().use { e ->
        val actor = e.alta("actora"); val par = e.alta("par"); val superior = e.alta("superior")
        e.rol(actor, "equipo"); e.rol(par, "equipo"); e.rol(superior, "moderador")
        e.b.usar { cambiar("INSERT INTO permisos VALUES(?, 'asignar_equipo', 'servidor', 0)", actor.id) }
        val secreto = e.inscribir(actor).first; e.elevar(actor, secreto)
        for (destino in listOf(actor, par, superior)) for (quitar in listOf(false, true)) {
            for (cambio in listOf(CambioEquipo(destino.usuario, rol = "equipo"), CambioEquipo(destino.usuario, permiso = "asignar_equipo"))) {
                val fallo = assertFailsWith<Problema> {
                    e.s.equipo.cambiarEquipo(actor.id, cambio.copy(codigo = e.siguiente(secreto)), quitar, actor.token)
                }
                assertEquals(403, fallo.codigo)
                assertEquals(if (destino == actor) "equipo_propio" else "nivel_protegido", fallo.mensaje)
            }
        }
        assertEquals("moderador", e.s.equipo.rol(superior.id))
        assertEquals("equipo", e.s.equipo.rol(par.id))
    }

    @Test fun fallosMfaPersistenEntreRutasSesionesYReaperturas() = Entorno().use { e ->
        val a = e.alta("actora"); val destino = e.alta("destino"); e.rol(a, "administrador")
        val (secreto, recuperacion) = e.inscribir(a); e.elevar(a, secreto)
        val otra = Base.tokenNuevo()
        e.b.usar { cambiar("INSERT INTO sesiones(huella, cuenta, creada, ultimo_uso) VALUES(?,?,?,?)", Base.huella(otra), a.id, e.reloj, e.reloj) }
        val operaciones = listOf<() -> Unit>(
            { e.s.equipo.elevar(a.id, Factor(recuperacion = "incorrecto"), otra); Unit },
            { e.s.equipo.desactivar(a.id, Factor(codigo = "invalido"), a.token) },
            { e.s.equipo.regenerar(a.id, Factor(codigo = "invalido"), otra); Unit },
            { e.s.equipo.cambiarEquipo(a.id, CambioEquipo(destino.usuario, "equipo", codigo = "invalido"), false, a.token) },
            { e.s.equipo.activar(a.id, Factor(recuperacion = "incorrecto"), otra); Unit }
        )
        operaciones.forEach { assertEquals(400, assertFailsWith<Problema> { it() }.codigo) }
        val valido = e.siguiente(secreto)
        assertEquals("mfa_bloqueada", assertFailsWith<Problema> { e.s.equipo.elevar(a.id, Factor(codigo = valido), otra) }.mensaje)
        assertEquals("mfa_bloqueada", assertFailsWith<Problema> { e.s.equipo.elevar(a.id, Factor(recuperacion = recuperacion.codigos.first()), a.token) }.mensaje)
        Base(e.ruta).useBase { base ->
            val equipo = Equipo(base, e.dir, { e.reloj }, e.cartero, null, configurarPrincipal = false)
            assertEquals("mfa_bloqueada", assertFailsWith<Problema> { equipo.regenerar(a.id, Factor(codigo = valido), a.token) }.mensaje)
        }
        e.reloj += 60_000
        repeat(5) { assertEquals(400, assertFailsWith<Problema> { e.s.equipo.elevar(a.id, Factor(recuperacion = "incorrecto"), otra) }.codigo) }
        val hasta = e.b.usar { leer("SELECT bloqueada_hasta FROM intentos_mfa WHERE cuenta = ?", a.id) { it.getLong(1) }.single() }
        assertEquals(e.reloj + 5 * 60_000, hasta)
        e.reloj = hasta
        e.s.equipo.elevar(a.id, Factor(recuperacion = recuperacion.codigos.first()), otra)
        assertTrue(e.b.usar { leer("SELECT cuenta FROM intentos_mfa WHERE cuenta = ?", a.id) { it.getLong(1) }.isEmpty() })
    }

    @Test fun limitesHttpYMfaNormalizanAuthorization() = Entorno().use { e ->
        val a = e.alta("actora"); val (secreto, _) = e.inscribir(a)
        testApplication {
            application { fogonSocial(e.s) }
            repeat(6) { n ->
                val respuesta = client.post("/v1/mfa/elevar") {
                    header(HttpHeaders.Authorization, "Bearer ${" ".repeat(n)}${a.token} ${" ".repeat(n)}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"codigo":"invalido"}""")
                }
                assertEquals(if (n < 5) HttpStatusCode.BadRequest else HttpStatusCode.TooManyRequests, respuesta.status)
            }
            val respuesta = client.post("/v1/mfa/elevar") {
                header(HttpHeaders.Authorization, "Bearer   ${a.token}  ")
                contentType(ContentType.Application.Json); setBody("""{"codigo":"${e.siguiente(secreto)}"}""")
            }
            assertEquals(HttpStatusCode.TooManyRequests, respuesta.status)
            assertTrue(respuesta.bodyAsText().contains("mfa_bloqueada"))
            repeat(113) { n ->
                assertEquals(HttpStatusCode.OK, client.get("/v1/cuenta") {
                    header(HttpHeaders.Authorization, "Bearer ${" ".repeat(n % 4)}${a.token} ")
                }.status)
            }
            assertEquals(HttpStatusCode.TooManyRequests, client.get("/v1/cuenta") {
                header(HttpHeaders.Authorization, "Bearer    ${a.token}   ")
            }.status)
        }
    }

    private data class Ruta(val metodo: HttpMethod, val ruta: String, val permiso: String, val cuerpo: (String) -> String = { "" })
    private val rutas = listOf(
        Ruta(HttpMethod.Get, "casos", "ver_reportes"),
        Ruta(HttpMethod.Post, "decidir", "sancionar", { """{"que":"cuenta","objeto":"destino","decision":"sin_falta"}""" }),
        Ruta(HttpMethod.Get, "frases", "gestionar_frases"),
        Ruta(HttpMethod.Put, "frases", "gestionar_frases", { """{"texto":"Una frase de prueba","idioma":"es"}""" }),
        Ruta(HttpMethod.Post, "frases", "gestionar_frases", { """{"clave":"c1","aprobar":true}""" }),
        Ruta(HttpMethod.Delete, "frases/prueba", "gestionar_frases"),
        Ruta(HttpMethod.Get, "equipo", "asignar_equipo"),
        Ruta(HttpMethod.Put, "equipo", "asignar_equipo", { """{"usuario":"destino","rol":"equipo","codigo":"$it"}""" }),
        Ruta(HttpMethod.Delete, "equipo", "asignar_equipo", { """{"usuario":"destino","rol":"equipo","codigo":"$it"}""" }),
        Ruta(HttpMethod.Get, "registro", "ver_registro"),
        Ruta(HttpMethod.Post, "publicaciones/retirar", "gestionar_publicaciones", { """{"que":"publicacion","objeto":"123"}""" }),
        Ruta(HttpMethod.Post, "comentarios/retirar", "moderar_comentarios", { """{"que":"comentario","objeto":"123"}""" }),
        Ruta(HttpMethod.Post, "restaurar", "gestionar_publicaciones", { """{"que":"publicacion","objeto":"123"}""" }),
        Ruta(HttpMethod.Post, "sanciones/levantar", "gestionar_usuarios", { """{"usuario":"destino","motivo":"Apelación aceptada"}""" }),
        Ruta(HttpMethod.Post, "imagenes/bloquear", "gestionar_publicaciones", { """{"url":"https://image.tmdb.org/t/p/original/a.jpg","motivo":"prueba"}""" }),
        Ruta(HttpMethod.Post, "imagenes/permitir", "gestionar_publicaciones", { """{"url":"https://image.tmdb.org/t/p/original/a.jpg"}""" }),
    )

    @Test fun matrizHttpDeTodasLasRutasRolesMfaYElevacion() {
        for (rol in listOf("principal", "administrador", "moderador", "equipo", "")) Entorno().use { e ->
            val a = e.alta("actora"); val destino = e.alta("destino"); e.rol(a, rol)
            val pub = e.pub(destino)
            e.b.usar {
                cambiar("UPDATE publicaciones SET id = 123 WHERE id = ?", pub)
                cambiar("INSERT INTO comentarios(id, pub, cuenta, texto, cuando) VALUES(123,123,?, 'Texto', ?)", destino.id, e.reloj)
            }
            if (rol == "equipo") e.b.usar { cambiar("INSERT INTO permisos VALUES(?, 'gestionar_frases', 'servidor', 0)", a.id) }
            testApplication {
                application { fogonSocial(e.s) }
                var secreto = byteArrayOf()
                for (estado in listOf("sin_mfa", "sin_elevar", "elevada", "caducada")) {
                    when (estado) {
                        "sin_elevar" -> secreto = e.inscribir(a).first
                        "elevada" -> e.elevar(a, secreto)
                        "caducada" -> e.reloj += Equipo.DURACION
                    }
                    for (r in rutas) {
                        if (r.ruta == "decidir") e.b.usar { cambiar("INSERT INTO reportes(de, que, objeto, motivo, cuando) VALUES(?, 'cuenta', 'destino', 'spam', ?)", destino.id, e.reloj) }
                        val codigo = if (estado == "elevada") e.siguiente(secreto) else "000000"
                        val respuesta = client.request("/v1/admin/${r.ruta}") {
                            method = r.metodo; header(HttpHeaders.Authorization, "Bearer ${a.token}")
                            contentType(ContentType.Application.Json); setBody(r.cuerpo(codigo))
                        }
                        val permitido = r.permiso in e.s.equipo.permisos(a.id) && estado == "elevada"
                        if (permitido) assertTrue(respuesta.status.value in 200..299, "$rol $estado ${r.ruta}: ${respuesta.status} ${respuesta.bodyAsText()}")
                        else {
                            assertEquals(HttpStatusCode.Forbidden, respuesta.status, "$rol $estado ${r.ruta}: ${respuesta.bodyAsText()}")
                            if (estado == "sin_mfa" && r.permiso in e.s.equipo.permisos(a.id)) assertTrue(respuesta.bodyAsText().contains("mfa_requerida"))
                        }
                    }
                }
                // La matriz no debe olvidar rutas al crecer la API.
                val fuente = File("src/main/kotlin/io/github/fogonapps/social/Principal.kt").readText()
                val declaradas = Regex("(get|put|post|delete)\\(\"/admin/([^\"]+)\"").findAll(fuente).map { it.groupValues[1].uppercase() to it.groupValues[2].replace("{clave}", "prueba") }.toSet()
                assertEquals(declaradas, rutas.map { it.metodo.value to it.ruta }.toSet())
            }
        }
    }

    @Test fun escalamientoYConflictosDeInteres() = Entorno().use { e ->
        val p = e.alta("fogon"); val a = e.alta("angel"); val m = e.alta("modera"); val u = e.alta("usuaria")
        e.rol(p, "principal"); e.rol(a, "administrador"); e.rol(m, "moderador")
        val sp = e.inscribir(p).first; e.elevar(p, sp)
        val sa = e.inscribir(a).first; e.elevar(a, sa)
        val sm = e.inscribir(m).first; e.elevar(m, sm)
        fun cambio(actor: Entorno.Cuenta, destino: String, rol: String = "", permiso: String = "", quitar: Boolean = false) = e.s.equipo.cambiarEquipo(actor.id, CambioEquipo(destino, rol, permiso, "000000"), quitar, actor.token)
        assertEquals("equipo_propio", assertFailsWith<Problema> { cambio(a, "angel", permiso = "ver_reportes") }.mensaje)
        for (quitar in listOf(false, true)) assertEquals("solo_principal", assertFailsWith<Problema> { cambio(a, "usuaria", "administrador", quitar = quitar) }.mensaje)
        assertFailsWith<Problema> { cambio(p, "usuaria", "principal") }
        e.b.usar { cambiar("INSERT INTO permisos VALUES(?, 'asignar_equipo', 'servidor', 0)", m.id) }
        assertEquals("sin_permiso", assertFailsWith<Problema> { cambio(m, "usuaria", permiso = "ver_registro") }.mensaje)
        assertFailsWith<Problema> { cambio(m, "usuaria", "administrador") }
        e.s.equipo.cambiarEquipo(p.id, CambioEquipo("usuaria", "administrador", codigo = e.siguiente(sp)), false, p.token)
        assertEquals("administrador", e.s.equipo.rol(u.id))
        e.s.equipo.cambiarEquipo(p.id, CambioEquipo("usuaria", "administrador", codigo = e.siguiente(sp)), true, p.token)
        assertEquals("", e.s.equipo.rol(u.id))
        assertFailsWith<Problema> { e.s.decidir(m.id, Decision("cuenta", "angel", "falta"), m.token) }
        assertFailsWith<Problema> { e.s.decidir(a.id, Decision("cuenta", "fogon", "cerrar"), a.token) }
        assertFailsWith<Problema> { e.s.decidir(a.id, Decision("cuenta", "angel", "sin_falta"), a.token) }
        val pub = e.pub(u)
        e.s.reportar(a.id, NuevoReporte("publicacion", "$pub", "spam"))
        assertEquals("reporte_propio", assertFailsWith<Problema> { e.s.decidir(a.id, Decision("publicacion", "$pub", "sin_falta"), a.token) }.mensaje)
        assertFailsWith<Problema> { e.s.decidir(m.id, Decision("cuenta", "usuaria", "cerrar"), m.token) }
        assertFailsWith<Problema> { e.s.levantarSancion(a.id, SancionEquipo("fogon", "Revisada"), a.token) }
        e.s.reportar(u.id, NuevoReporte("mensaje", "usuaria:1", "spam: texto privado"))
        val antes = e.b.usar { leer("SELECT COUNT(*) FROM registro_equipo WHERE accion = 'mensaje_abrir'") { it.getInt(1) }.single() }
        repeat(2) { assertTrue(e.s.casos(p.id, p.token).any { it.contenido.contains("texto privado") }) }
        assertEquals(antes + 2, e.b.usar { leer("SELECT COUNT(*) FROM registro_equipo WHERE accion = 'mensaje_abrir'") { it.getInt(1) }.single() })
        assertTrue(e.s.equipo.verificarCadena())
        val registro = e.s.equipo.registro(p.id, p.token, p.id.toString(), "mensaje_abrir", 0)
        assertEquals(2, registro.size)
        assertEquals(1, e.s.equipo.registro(p.id, p.token, p.id.toString(), "mensaje_abrir", registro.first().id).size)
        e.b.usar { cambiar("UPDATE registro_equipo SET detalle = 'alterado' WHERE id = (SELECT MAX(id) FROM registro_equipo)") }
        assertFalse(e.s.equipo.verificarCadena())
    }

    @Test fun contratosHttpDeCasosYRegistro() = Entorno().use { e ->
        val a = e.alta("angel"); e.rol(a, "administrador"); val secreto = e.inscribir(a).first; e.elevar(a, secreto)
        val u = e.alta("usuaria"); val pub = e.pub(u)
        e.s.reportar(u.id, NuevoReporte("publicacion", "$pub", "spam"))
        testApplication {
            application { fogonSocial(e.s) }
            suspend fun casos() = Json.parseToJsonElement(client.get("/v1/admin/casos") {
                header(HttpHeaders.Authorization, "Bearer ${a.token}")
            }.bodyAsText()).jsonArray.single().jsonObject
            assertEquals("visible", casos().getValue("estado").jsonPrimitive.content)
            e.b.usar { cambiar("UPDATE publicaciones SET estado = 'retirado', retirado_hasta = 123 WHERE id = ?", pub) }
            assertEquals("retirado", casos().getValue("estado").jsonPrimitive.content)
            assertEquals(123L, casos().getValue("retiradoHasta").jsonPrimitive.long)
            e.b.usar { cambiar("UPDATE publicaciones SET estado = 'preservado' WHERE id = ?", pub) }
            assertEquals("preservado", casos().getValue("estado").jsonPrimitive.content)
            repeat(101) { e.s.equipo.registrar(a.id.toString(), "prueba", "$it", "") }
            suspend fun registro(cursor: Long = 0) = Json.parseToJsonElement(client.get("/v1/admin/registro?accion=prueba&despues=$cursor") {
                header(HttpHeaders.Authorization, "Bearer ${a.token}")
            }.bodyAsText()).jsonObject
            val primera = registro()
            assertTrue(primera.getValue("cadenaIntegra").jsonPrimitive.boolean)
            assertEquals(100, primera.getValue("entradas").jsonArray.size)
            val segunda = registro(primera.getValue("siguiente").jsonPrimitive.long)
            assertEquals(1, segunda.getValue("entradas").jsonArray.size)
            assertEquals(JsonNull, segunda["siguiente"] ?: JsonNull)
            e.b.usar { cambiar("UPDATE registro_equipo SET detalle = 'alterado' WHERE id = 1") }
            assertFalse(registro().getValue("cadenaIntegra").jsonPrimitive.boolean)
        }
    }

    @Test fun retiradaRestauracionLimpiezaYPreservado() = Entorno().use { e ->
        val a = e.alta("angel"); e.rol(a, "administrador"); val sa = e.inscribir(a).first; e.elevar(a, sa)
        val u = e.alta("usuaria"); val otro = e.alta("otra")
        val pub = e.pub(u)
        val comentario = e.s.comentar(otro.id, pub, "Un comentario")
        e.s.reportar(u.id, NuevoReporte("comentario", "$comentario", "spam"))
        e.s.decidir(a.id, Decision("comentario", "$comentario", "falta"), a.token)
        assertTrue(e.s.comentarios(otro.id, pub).isEmpty())
        e.s.borrarComentario(otro.id, comentario)
        assertEquals("retirado", e.b.usar { leer("SELECT estado FROM comentarios WHERE id = ?", comentario) { it.getString(1) }.single() })
        e.s.restaurar(a.id, ContenidoEquipo("comentario", "$comentario"), a.token)
        assertEquals(1, e.s.comentarios(otro.id, pub).size)
        e.s.reportar(otro.id, NuevoReporte("publicacion", "$pub", "spam"))
        e.s.decidir(a.id, Decision("publicacion", "$pub", "falta"), a.token)
        val hasta = e.b.usar { leer("SELECT retirado_hasta FROM publicaciones WHERE id = ?", pub) { it.getLong(1) }.single() }
        assertEquals(e.reloj + 90L * 86400_000, hasta)
        assertNull(e.s.publicacion(null, pub)); assertNull(e.s.publicacion(u.id, pub))
        assertTrue(e.s.verPerfil(u.id, "usuaria").publicaciones.isEmpty())
        e.s.subirPublicaciones(u.id, emptyList())
        e.pub(u)
        assertEquals("retirado", e.b.usar { leer("SELECT estado FROM publicaciones WHERE id = ?", pub) { it.getString(1) }.single() })
        e.s.restaurar(a.id, ContenidoEquipo("publicacion", "$pub"), a.token)
        assertNotNull(e.s.publicacion(null, pub))
        e.s.reportar(otro.id, NuevoReporte("publicacion", "$pub", "spam"))
        e.s.decidir(a.id, Decision("publicacion", "$pub", "falta"), a.token)
        val preservado = e.pub(otro)
        e.b.usar { cambiar("UPDATE publicaciones SET estado = 'preservado', retirado_hasta = 1 WHERE id = ?", preservado) }
        e.s.reportar(u.id, NuevoReporte("publicacion", "$preservado", "spam"))
        assertTrue(e.s.casos(a.id, a.token).none { it.objeto == "$preservado" && it.contenido.contains("Evidencia") })
        e.s.restaurar(a.id, ContenidoEquipo("publicacion", "$preservado"), a.token)
        assertNull(e.s.publicacion(otro.id, preservado))
        e.reloj = hasta - 1; e.s.limpiarSeguridad()
        assertEquals(2, e.b.usar { leer("SELECT COUNT(*) FROM publicaciones") { it.getInt(1) }.single() })
        e.reloj++; e.s.limpiarSeguridad()
        assertEquals(listOf(preservado), e.b.usar { leer("SELECT id FROM publicaciones") { it.getLong(1) } })
    }

    @Test fun migracionPrincipalProteccionesYOrdenesLocales() = Entorno().use { e ->
        val f = e.alta("fogon"); val a = e.alta("angel")
        e.b.usar {
            cambiar("UPDATE cuentas SET admin = 1, correo = usuario || '@ejemplo.co' WHERE id IN (?,?)", f.id, a.id)
            createStatement().use { it.execute("DROP TABLE roles") }
        }
        Base(e.ruta).useBase { base ->
            val s = Social(base, File(e.dir, "fotos"), { e.reloj }, e.cartero, "fogon")
            assertEquals("principal", s.equipo.rol(f.id)); assertEquals("administrador", s.equipo.rol(a.id))
            assertTrue(s.miCuenta(f.id).admin); assertFalse(s.miCuenta(f.id).mfa)
            assertEquals("mfa_requerida", assertFailsWith<Problema> { s.casos(f.id, f.token) }.mensaje)
            assertEquals("cuenta_protegida", assertFailsWith<Problema> { s.cambiarUsuario(f.id, CambioUsuario("otro")) }.mensaje)
            assertEquals("cuenta_protegida", assertFailsWith<Problema> { s.borrarCuenta(f.id) }.mensaje)
            s.equipo.activar(f.id, Factor(), f.token)
            s.equipo.ordenLocal(arrayOf("restablecer-mfa", "fogon"))
            assertFalse(s.equipo.activa(f.id))
            s.equipo.ordenLocal(arrayOf("cambiar-correo", "fogon", "nuevo@ejemplo.co"))
            assertEquals("nuevo@ejemplo.co", s.miCuenta(f.id).correo)
            assertNull(s.cuentaDe(f.token))
            assertTrue(e.correos.any { it.first == "fogon@ejemplo.co" && it.second.contains("MFA") })
            assertTrue(e.correos.any { it.first == "nuevo@ejemplo.co" })
            assertEquals(listOf("servidor", "servidor"), base.usar { leer("SELECT actor FROM registro_equipo WHERE accion IN ('mfa_restablecer','correo_cambiar')") { it.getString(1) } })
            assertTrue(s.equipo.verificarCadena())
            assertFailsWith<IllegalArgumentException> { s.equipo.ordenLocal(arrayOf("cambiar-correo", "angel", "incorrecto")) }
        }
        Base(e.ruta).useBase { base ->
            val s = Social(base, File(e.dir, "fotos"), { e.reloj }, e.cartero, "angel")
            assertEquals("principal", s.equipo.rol(a.id)); assertEquals("administrador", s.equipo.rol(f.id))
            // La migración no se reaplica al volver a abrir.
        }
        Base(e.ruta).useBase { base ->
            val s = Social(base, File(e.dir, "fotos"), { e.reloj }, e.cartero)
            assertEquals("administrador", s.equipo.rol(a.id)); assertEquals("administrador", s.equipo.rol(f.id))
        }
    }
    @Test fun compatibilidad149FlujoNormalSinMfa() = Entorno().use { e ->
        val otra = e.alta("otra")
        testApplication {
            application { fogonSocial(e.s) }
            suspend fun peticion(metodo: HttpMethod, ruta: String, cuerpo: String = "", token: String = "") = client.request("/v1/$ruta") {
                method = metodo; if (token.isNotBlank()) header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json); if (cuerpo.isNotBlank()) setBody(cuerpo)
            }
            assertEquals(HttpStatusCode.NoContent, peticion(HttpMethod.Post, "acceso/codigo", """{"correo":"ana@ejemplo.co"}""").status)
            val codigo = Regex("[0-9]{6}").find(e.correos.last().second)!!.value
            val entrada = peticion(HttpMethod.Post, "acceso", """{"correo":"ana@ejemplo.co","codigo":"$codigo","usuario":"ana","nombre":"Ana","acepta":"${Social.POLITICA}","mayorDeEdad":true}""")
            assertEquals(HttpStatusCode.OK, entrada.status)
            val token = Json.parseToJsonElement(entrada.bodyAsText()).jsonObject.getValue("token").jsonPrimitive.content
            assertEquals(HttpStatusCode.OK, peticion(HttpMethod.Get, "cuenta", token = token).status)
            assertEquals(HttpStatusCode.NoContent, peticion(HttpMethod.Put, "perfil", """{"nombre":"Ana","bio":"Perfil normal"}""", token).status)
            assertEquals(HttpStatusCode.OK, peticion(HttpMethod.Get, "perfiles/ana").status)
            assertEquals(HttpStatusCode.OK, peticion(HttpMethod.Put, "cajon", """{"sobre":"cifrado","llave":"envuelta"}""", token).status)
            assertTrue(peticion(HttpMethod.Get, "cajon", token = token).bodyAsText().contains("cifrado"))
            assertEquals(HttpStatusCode.NoContent, peticion(HttpMethod.Put, "llaves", """{"cifrar":"${"a".repeat(25)}","firmar":"${"b".repeat(25)}"}""", token).status)
            assertEquals(HttpStatusCode.Created, peticion(HttpMethod.Post, "mensajes/otra", """{"sobre":"mensaje cifrado","firma":"firma"}""", token).status)
            assertTrue(peticion(HttpMethod.Get, "mensajes", token = otra.token).bodyAsText().contains("mensaje cifrado"))
            assertEquals(HttpStatusCode.NoContent, peticion(HttpMethod.Put, "publicaciones", """[{"clave":"a","visibilidad":"publica","cuando":${e.reloj},"tipo":"diario","datos":{"texto":"Hola"}}]""", token).status)
            val feed = peticion(HttpMethod.Get, "explorar").bodyAsText()
            val id = Json.parseToJsonElement(feed).jsonArray.single().jsonObject.getValue("id").jsonPrimitive.long
            assertEquals(HttpStatusCode.Created, peticion(HttpMethod.Post, "reportes", """{"que":"publicacion","objeto":"$id","motivo":"spam"}""", otra.token).status)
            assertEquals(HttpStatusCode.OK, peticion(HttpMethod.Get, "publicaciones/$id", token = token).status)
            assertEquals(HttpStatusCode.Forbidden, peticion(HttpMethod.Get, "admin/casos", token = token).status)
        }
    }

    @Test fun borrarOVolverPrivadaConComentariosConservados() {
        for (estado in listOf("retirado", "preservado")) for (privada in listOf(false, true)) Entorno().use { e ->
            val a = e.alta("angel"); e.rol(a, "administrador"); val secreto = e.inscribir(a).first; e.elevar(a, secreto)
            val u = e.alta("usuaria"); val otro = e.alta("otra")
            val pub = e.pub(u, datos = buildJsonObject { put("texto", "Evidencia #prueba") })
            e.s.seguir(otro.id, u.usuario)
            e.s.guardar(otro.id, pub, true)
            val comentario = e.s.comentar(otro.id, pub, "Comentario retenido")
            e.s.reportar(u.id, NuevoReporte("comentario", "$comentario", "spam"))
            e.s.retirar(a.id, ContenidoEquipo("comentario", "$comentario"), a.token)
            if (estado == "preservado") e.b.usar {
                cambiar("UPDATE comentarios SET estado = 'preservado' WHERE id = ?", comentario)
            }
            assertTrue(e.s.actividad(otro.id, null).any { it.id == pub })
            val cambios = if (privada) CambiosPublicaciones(listOf(
                PublicacionSubida("a", "privada", e.reloj, "diario", buildJsonObject { put("texto", "Privado") })
            )) else CambiosPublicaciones(emptyList(), quitar = listOf("a"))
            e.s.subirCambios(u.id, cambios)
            assertTrue(e.s.actividad(otro.id, null).isEmpty())
            assertTrue(e.s.actividad(u.id, null).isEmpty())
            assertNull(e.s.publicacion(null, pub))
            assertNull(e.s.publicacion(u.id, pub))
            assertTrue(e.s.explorar(null, null).isEmpty())
            assertTrue(e.s.verPerfil(null, u.usuario).publicaciones.isEmpty())
            assertTrue(e.s.guardados(otro.id).isEmpty())
            assertTrue(e.s.conEtiqueta(null, "prueba", null).isEmpty())
            val caso = e.s.casos(a.id, a.token).single { it.que == "comentario" && it.objeto == "$comentario" }
            assertEquals(if (estado == "retirado") "Comentario retenido" else "(ya no está)", caso.contenido)
            e.s.limpiarSeguridad()
            assertEquals(estado, e.b.usar { leer("SELECT estado FROM comentarios WHERE id = ?", comentario) { it.getString(1) }.single() })
            assertEquals("retirado", e.b.usar { leer("SELECT estado FROM publicaciones WHERE id = ?", pub) { it.getString(1) }.single() })
            // Al terminar la retención del comentario, la limpieza también purga su contenedora.
            e.b.usar { cambiar("UPDATE comentarios SET estado = 'retirado', retirado_hasta = ? WHERE id = ?", e.reloj, comentario) }
            e.s.limpiarSeguridad()
            assertTrue(e.b.usar { leer("SELECT id FROM publicaciones WHERE id = ?", pub) { it.getLong(1) }.isEmpty() })
        }
    }

    @Test fun imagenesRetiradasYConservacionSinCascadas() = Entorno().use { e ->
        val a = e.alta("angel"); e.rol(a, "administrador"); val secreto = e.inscribir(a).first; e.elevar(a, secreto)
        val u = e.alta("usuaria"); val otro = e.alta("otra")
        val nombre = "a".repeat(22)
        val archivo = File(e.dir, "fotos/i/$nombre.jpg").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
        e.b.usar { cambiar("INSERT INTO imagenes VALUES(?,?,?)", nombre, u.id, e.reloj) }
        val pub = e.pub(u, datos = buildJsonObject { put("imagen", nombre) })
        val comentario = e.s.comentar(otro.id, pub, "Preservado")
        e.b.usar { cambiar("UPDATE comentarios SET estado = 'preservado' WHERE id = ?", comentario) }
        e.s.subirPublicaciones(u.id, emptyList())
        assertNull(e.s.publicacion(null, pub))
        e.s.retirar(a.id, ContenidoEquipo("publicacion", "$pub", "Revisada"), a.token)
        assertFailsWith<Problema> { e.s.imagen(nombre) }
        assertContentEquals(byteArrayOf(1, 2, 3), e.s.imagen(nombre, a.id, a.token))
        e.s.borrarCuenta(u.id)
        assertTrue(archivo.exists())
        e.reloj += 90L * 86400_000
        e.s.limpiarSeguridad()
        assertEquals(1, e.b.usar { leer("SELECT COUNT(*) FROM publicaciones") { it.getInt(1) }.single() })
        assertEquals(1, e.b.usar { leer("SELECT COUNT(*) FROM comentarios") { it.getInt(1) }.single() })
        assertTrue(archivo.exists())
        e.b.usar { cambiar("UPDATE comentarios SET estado = 'retirado', retirado_hasta = ?", e.reloj) }
        e.s.limpiarSeguridad()
        assertFalse(archivo.exists())
        assertTrue(e.b.usar { leer("SELECT nombre FROM imagenes") { it.getString(1) }.isEmpty() })
    }

    @Test fun permisosDeDecisionYCierreAcumulado() = Entorno().use { e ->
        val m = e.alta("modera"); val u = e.alta("usuaria")
        e.rol(m, "moderador"); val secreto = e.inscribir(m).first; e.elevar(m, secreto)
        var pub = e.pub(u)
        e.s.reportar(u.id, NuevoReporte("publicacion", "$pub", "spam"))
        e.s.decidir(m.id, Decision("publicacion", "$pub", "falta_grave", "spam"), m.token)
        assertEquals(2, e.s.estadoCuenta(u.id).faltas.sumOf { it.puntos })
        pub = e.pub(u, "b")
        e.s.reportar(u.id, NuevoReporte("publicacion", "$pub", "spam"))
        val fallo = assertFailsWith<Problema> { e.s.decidir(m.id, Decision("publicacion", "$pub", "falta_grave", "spam"), m.token) }
        assertEquals("sin_permiso", fallo.mensaje)
        assertNotNull(e.s.publicacion(null, pub))
        assertEquals(2, e.s.estadoCuenta(u.id).faltas.sumOf { it.puntos })
        assertFailsWith<Problema> { e.s.decidir(m.id, Decision("publicacion", "$pub", "falta", Moderacion.MENOR_EN_RIESGO), m.token) }
        e.rol(m, "equipo")
        e.b.usar { cambiar("INSERT INTO permisos VALUES(?, 'sancionar', 'servidor', 0)", m.id) }
        assertFailsWith<Problema> { e.s.decidir(m.id, Decision("publicacion", "$pub", "falta_grave"), m.token) }
        e.s.decidir(m.id, Decision("publicacion", "$pub", "sin_falta"), m.token)
        assertEquals(2, e.s.estadoCuenta(u.id).faltas.sumOf { it.puntos })
    }

    @Test fun registroPaginadoYTransaccionNoDejaAccionesFallidas() = Entorno().use { e ->
        val a = e.alta("angel"); e.rol(a, "administrador"); val secreto = e.inscribir(a).first; e.elevar(a, secreto)
        repeat(205) { e.s.equipo.registrar(a.id.toString(), "comprobacion", "$it", "Sin contenido privado", a.token) }
        val primera = e.s.equipo.registro(a.id, a.token, a.id.toString(), "comprobacion", 0)
        val segunda = e.s.equipo.registro(a.id, a.token, a.id.toString(), "comprobacion", primera.last().id)
        val tercera = e.s.equipo.registro(a.id, a.token, a.id.toString(), "comprobacion", segunda.last().id)
        assertEquals(listOf(100, 100, 5), listOf(primera.size, segunda.size, tercera.size))
        assertEquals(205, (primera + segunda + tercera).map { it.id }.distinct().size)
        val antes = e.s.equipo.registro(a.id, a.token, "", "frase_agregar", 0)
        assertFailsWith<Problema> { e.s.agregarFrase(a.id, FraseNueva("x", "es"), a.token) }
        assertEquals(antes, e.s.equipo.registro(a.id, a.token, "", "frase_agregar", 0))
        assertTrue(e.s.equipo.verificarCadena())
    }

    @Test fun ordenLocalSinEntornoPrincipalNoCambiaLaPrincipal() = Entorno().use { e ->
        val a = e.alta("fogon"); e.rol(a, "principal")
        val inscripcion = e.inscribir(a).first; e.elevar(a, inscripcion)
        val local = Equipo(e.b, e.dir, { e.reloj }, e.cartero, null, configurarPrincipal = false)
        local.ordenLocal(arrayOf("restablecer-mfa", "fogon"))
        assertEquals("principal", e.s.equipo.rol(a.id))
        assertEquals(0, e.s.equipo.elevada(a.id, a.token))
        assertFalse(e.s.equipo.activa(a.id))
        assertTrue(e.s.equipo.verificarCadena())
    }

    @Test fun decisionesRepetidasYConcurrentesConsumenUnSoloCaso() = Entorno().use { e ->
        val admins = listOf(e.alta("adminuno"), e.alta("admindos"))
        admins.forEach { e.rol(it, "administrador"); e.elevar(it, e.inscribir(it).first) }
        val u = e.alta("usuaria"); val denunciante = e.alta("denuncia")
        for (concurrente in listOf(false, true)) {
            val pub = e.pub(u, if (concurrente) "b" else "a")
            e.s.reportar(denunciante.id, NuevoReporte("publicacion", "$pub", "spam"))
            e.s.reportar(u.id, NuevoReporte("publicacion", "$pub", "spam"))
            val inicio = java.util.concurrent.CountDownLatch(1)
            val ejecutor = java.util.concurrent.Executors.newFixedThreadPool(2)
            try {
                fun decidir(a: Entorno.Cuenta): String = try {
                    e.s.decidir(a.id, Decision("publicacion", "$pub", "falta", "spam"), a.token)
                    "ok"
                } catch (p: Problema) { p.mensaje }
                val resultados = if (concurrente) {
                    val futuros = admins.map { a -> ejecutor.submit<String> { inicio.await(); decidir(a) } }
                    inicio.countDown()
                    futuros.map { it.get(10, java.util.concurrent.TimeUnit.SECONDS) }
                } else admins.map(::decidir)
                assertEquals(listOf("ok", "ya_resuelto"), resultados.sorted())
                assertTrue(e.s.casos(admins.first().id, admins.first().token).none { it.objeto == "$pub" })
            } finally { ejecutor.shutdownNow() }
        }
        assertEquals(2, e.s.estadoCuenta(u.id).faltas.size)
        for (tabla in listOf("decisiones", "faltas")) assertEquals(2, e.b.usar { leer("SELECT COUNT(*) FROM $tabla") { it.getInt(1) }.single() })
        assertEquals(2, e.b.usar { leer("SELECT COUNT(*) FROM registro_equipo WHERE accion = 'sancion_dar'") { it.getInt(1) }.single() })
    }

    @Test fun marcaSobrevivePurgaYRestaurarPermiteSincronizar() = Entorno().use { e ->
        val a = e.alta("administra"); e.rol(a, "administrador"); val secreto = e.inscribir(a).first; e.elevar(a, secreto)
        val u = e.alta("usuaria")
        val pub = e.pub(u)
        e.s.retirar(a.id, ContenidoEquipo("publicacion", "$pub"), a.token)
        e.reloj += 90L * 86400_000
        e.b.usar { cambiar("UPDATE sesiones SET ultimo_uso = ? WHERE cuenta = ?", e.reloj, a.id) }
        e.s.limpiarSeguridad()
        val subida = PublicacionSubida("a", Vis.PUBLICA, e.reloj, "diario", buildJsonObject { put("texto", "Reenvío") })
        e.s.subirPublicaciones(u.id, listOf(subida))
        e.s.subirCambios(u.id, CambiosPublicaciones(listOf(subida)))
        assertTrue(e.s.verPerfil(null, "usuaria").publicaciones.isEmpty())
        assertEquals(0, e.b.usar { leer("SELECT COUNT(*) FROM publicaciones") { it.getInt(1) }.single() })
        assertEquals(1, e.b.usar { leer("SELECT COUNT(*) FROM retiradas") { it.getInt(1) }.single() })
        e.elevar(a, secreto)
        val otra = e.pub(u, "b")
        e.s.retirar(a.id, ContenidoEquipo("publicacion", "$otra"), a.token)
        e.s.restaurar(a.id, ContenidoEquipo("publicacion", "$otra"), a.token)
        e.s.subirPublicaciones(u.id, emptyList())
        e.pub(u, "b")
        assertEquals(1, e.s.verPerfil(null, "usuaria").publicaciones.size)
        e.b.usar { cambiar("DELETE FROM cuentas WHERE id = ?", u.id) }
        assertEquals(0, e.b.usar { leer("SELECT COUNT(*) FROM retiradas") { it.getInt(1) }.single() })
    }

    private fun Base.useBase(f: (Base) -> Unit) { try { f(this) } finally { cerrar() } }
}

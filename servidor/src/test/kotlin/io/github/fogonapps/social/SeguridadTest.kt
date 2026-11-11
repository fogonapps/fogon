package io.github.fogonapps.social

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.*
import java.nio.file.Files
import kotlin.test.*

class SeguridadTest {
    private class Entorno : AutoCloseable {
        val dir = Files.createTempDirectory("seguridad").toFile()
        val base = Base(java.io.File(dir, "s.db").path)
        var reloj = 2_000_000_000_000L
        val buzon = mutableListOf<Pair<String, String>>()
        val s = Social(base, java.io.File(dir, "fotos"), { reloj }, Cartero { _, asunto, texto -> buzon += asunto to texto })
        fun codigo() = Regex("[0-9]{6}").find(buzon.last { it.first.startsWith("Tu código") }.first)!!.value
        fun alta(usuario: String = "ana", dispositivo: String = ""): Acceso {
            s.pedirCodigo("$usuario@correo.co")
            return s.entrar(Entrada("$usuario@correo.co", codigo(), usuario, usuario, Social.POLITICA, true, "una frase bastante larga"), dispositivo)
        }
        fun entrar(dispositivo: String = "") = s.entrarConClave(EntradaClave("ana@correo.co", "una frase bastante larga"), dispositivo)
        override fun close() { base.cerrar(); dir.deleteRecursively() }
    }

    /* ----- Límites contra abusos ----- */

    @Test fun elLimiteNoSeSaltaConTokensInventadosNiLoCompartenLosVisitantes() = Entorno().use { e ->
        testApplication {
            application { fogonSocial(e.s) }
            // Un token distinto (inventado) en cada petición ya no estrena cupo: cuenta la dirección.
            var rechazadas = 0
            repeat(130) { i ->
                val r = client.get("/v1/estado") { header("Authorization", "Bearer inventado$i"); header("X-Forwarded-For", "203.0.113.7") }
                if (r.status == HttpStatusCode.TooManyRequests) rechazadas++
            }
            assertEquals(10, rechazadas)
            // Otra dirección no paga por la primera.
            assertEquals(HttpStatusCode.OK, client.get("/v1/estado") { header("X-Forwarded-For", "203.0.113.8") }.status)
            // Quien manda su propio X-Forwarded-For delante no engaña: vale la última dirección (la que pone el proxy).
            assertEquals(HttpStatusCode.TooManyRequests, client.get("/v1/estado") { header("X-Forwarded-For", "198.51.100.1, 203.0.113.7") }.status)
        }
    }

    @Test fun adivinarElCodigoTieneTopeDiarioPorCorreo() = Entorno().use { e ->
        e.alta()
        var fallos = 0
        // Pide códigos nuevos una y otra vez y prueba 5 por código: al llegar al tope del día, ni el bueno entra.
        while (fallos < Social.FALLOS_CODIGO_DIA) {
            e.reloj += 13 * 60_000L
            e.s.pedirCodigo("ana@correo.co")
            repeat(5) {
                if (fallos < Social.FALLOS_CODIGO_DIA) {
                    val malo = if (e.codigo() == "000000") "000001" else "000000"
                    assertEquals("codigo", assertFailsWith<Problema> { e.s.entrar(Entrada("ana@correo.co", malo)) }.mensaje); fallos++
                }
            }
        }
        e.reloj += 13 * 60_000L
        e.s.pedirCodigo("ana@correo.co")
        assertEquals(429, assertFailsWith<Problema> { e.s.entrar(Entrada("ana@correo.co", e.codigo())) }.codigo)
        // Al día siguiente vuelve a servir; la contraseña sirvió todo el tiempo.
        e.entrar()
        e.reloj += 24 * 3600_000L
        e.s.pedirCodigo("ana@correo.co")
        e.s.entrar(Entrada("ana@correo.co", e.codigo()))
    }

    @Test fun lasPortadasSoloVienenDeDominiosPermitidos() = Entorno().use { e ->
        val yo = e.s.cuentaDe(e.alta().token)!!
        assertTrue(Social.portadaSegura("https://image.tmdb.org/t/p/w500/a.jpg"))
        assertTrue(Social.portadaSegura("https://fogonapps.duckdns.org/v1/imagenes/abc"))
        assertFalse(Social.portadaSegura("https://image.tmdb.org.rastreo.example/a.jpg"))
        assertFalse(Social.portadaSegura("https://rastreo.example/p.gif?image.tmdb.org"))
        assertFalse(Social.portadaSegura("http://image.tmdb.org/a.jpg"))
        e.s.subirPublicaciones(yo, listOf(PublicacionSubida("a", Vis.PUBLICA, e.reloj, "diario",
            buildJsonObject { put("titulo", "Algo"); put("portada", "https://rastreo.example/p.gif"); put("fondo", "https://image.tmdb.org/t/p/w780/b.jpg") })))
        val datos = e.s.verPerfil(null, "ana").publicaciones.single().datos
        assertNull(datos["portada"])
        assertEquals("https://image.tmdb.org/t/p/w780/b.jpg", datos.getValue("fondo").jsonPrimitive.content)
        e.s.subirPerfil(yo, PerfilSubido(nombre = "ana", portada = "https://rastreo.example/banner.jpg"))
        assertEquals("", e.s.verPerfil(null, "ana").portada)
    }

    @Test fun verificacionEnDosPasosOpcional() = Entorno().use { e ->
        val yo = e.s.cuentaDe(e.alta().token)!!
        assertEquals(400, assertFailsWith<Problema> { e.s.ponerDosPasos(yo, DosPasos(true, "otra cosa larga")) }.codigo)
        e.s.ponerDosPasos(yo, DosPasos(true, "una frase bastante larga"))
        assertTrue(e.s.miCuenta(yo).dosPasos)
        // Con la contraseña buena, falta el código: llega al correo.
        e.reloj += 61_000
        assertEquals("codigo_requerido", assertFailsWith<Problema> { e.entrar() }.mensaje)
        val codigo = e.codigo()
        assertEquals("codigo", assertFailsWith<Problema> { e.s.entrarConClave(EntradaClave("ana@correo.co", "una frase bastante larga", codigo = if (codigo == "000000") "000001" else "000000")) }.mensaje)
        e.s.entrarConClave(EntradaClave("ana@correo.co", "una frase bastante larga", codigo = codigo))
        // Con la contraseña mala no se manda ningún código.
        val antes = e.buzon.size
        assertEquals("clave_mala", assertFailsWith<Problema> { e.s.entrarConClave(EntradaClave("ana@correo.co", "no es la clave")) }.mensaje)
        assertEquals(antes, e.buzon.size)
        e.s.ponerDosPasos(yo, DosPasos(false, "una frase bastante larga"))
        e.entrar()
    }

    @Test fun caducidadYUso() = Entorno().use { e ->
        val a = e.alta()
        val yo = e.s.cuentaDe(a.token)!!
        val creada = e.s.sesiones(yo, a.token).single().ultimo_uso
        e.reloj += 3599_999
        assertEquals(yo, e.s.cuentaDe(a.token))
        assertEquals(creada, e.s.sesiones(yo, a.token).single().ultimo_uso)
        e.reloj++
        assertEquals(yo, e.s.cuentaDe(a.token))
        assertEquals(e.reloj, e.s.sesiones(yo, a.token).single().ultimo_uso)
        e.reloj += 90L * 86400_000 - 1
        assertEquals(1, e.s.sesiones(yo, a.token).size)
        e.reloj++
        assertNull(e.s.cuentaDe(a.token))
        assertEquals(0, e.base.usar { leer("SELECT COUNT(*) FROM sesiones") { it.getInt(1) }.single() })
    }

    @Test fun sesionesPropiasOpacasYCierres() = Entorno().use { e ->
        val a = e.alta(dispositivo = "Modelo A")
        val yo = e.s.cuentaDe(a.token)!!
        val b = e.entrar("Modelo B")
        val c = e.alta("beto")
        val otro = e.s.cuentaDe(c.token)!!
        val lista = e.s.sesiones(yo, a.token)
        assertEquals(2, lista.size)
        assertEquals(1, lista.count { it.actual })
        assertTrue(lista.all { it.id.length == 32 && it.id != Base.huella(a.token) })
        lista.forEach { assertNull(e.s.cuentaDe(it.id)) }
        e.s.cerrarSesion(yo, e.s.sesiones(otro, c.token).single().id)
        assertEquals(otro, e.s.cuentaDe(c.token))
        e.s.cerrarSesion(yo, lista.single { !it.actual }.id)
        assertNull(e.s.cuentaDe(b.token))
        val d = e.entrar()
        e.s.cerrarOtras(yo, a.token)
        assertNull(e.s.cuentaDe(d.token))
        assertEquals(yo, e.s.cuentaDe(a.token))
        assertEquals(otro, e.s.cuentaDe(c.token))
    }

    @Test fun escaleraDeIntentosYCodigoDuranteEspera() = Entorno().use { e ->
        e.alta()
        val mala = EntradaClave("ana@correo.co", "incorrecta")
        for (minutos in listOf(1, 5, 15, 60)) {
            repeat(5) { assertEquals("clave_mala", assertFailsWith<Problema> { e.s.entrarConClave(mala) }.mensaje) }
            assertEquals("clave_mala", assertFailsWith<Problema> { e.entrar() }.mensaje)
            val hasta = e.base.usar { leer("SELECT bloqueada_hasta FROM intentos") { it.getLong(1) }.single() }
            assertEquals(e.reloj + minutos * 60_000, hasta)
            e.reloj = hasta - 1
            assertEquals("clave_mala", assertFailsWith<Problema> { e.entrar() }.mensaje)
            e.reloj++
        }
        repeat(5) { assertFailsWith<Problema> { e.s.entrarConClave(mala) } }
        e.s.pedirCodigo("ana@correo.co")
        val a = e.s.entrar(Entrada("ana@correo.co", e.codigo()))
        assertNotNull(e.s.cuentaDe(a.token))
        assertEquals(0, e.base.usar { leer("SELECT COUNT(*) FROM intentos") { it.getInt(1) }.single() })
        repeat(5) { assertFailsWith<Problema> { e.s.entrarConClave(mala) } }
        e.reloj += 24L * 3600_000
        assertFailsWith<Problema> { e.s.entrarConClave(mala) }
        assertEquals(0, e.base.usar { leer("SELECT nivel FROM intentos") { it.getInt(1) }.single() })
        e.entrar()
        assertEquals(0, e.base.usar { leer("SELECT COUNT(*) FROM intentos") { it.getInt(1) }.single() })
    }

    @Test fun ventanaDeFallosNoSeDesliza() = Entorno().use { e ->
        e.alta()
        repeat(4) {
            assertFailsWith<Problema> { e.s.entrarConClave(EntradaClave("ana@correo.co", "mala")) }
            e.reloj += 5 * 60_000
        }
        assertEquals("ana", e.entrar().usuario)
    }

    @Test fun codigosPorHoraEInvalidacion() = Entorno().use { e ->
        e.alta()
        e.reloj += 60_000
        e.s.pedirCodigo("ana@correo.co")
        val viejo = e.codigo()
        e.reloj += 60_000
        e.s.pedirCodigo("ANA@correo.co")
        assertEquals("codigo", assertFailsWith<Problema> { e.s.entrar(Entrada("ana@correo.co", viejo)) }.mensaje)
        repeat(2) { e.reloj += 60_000; e.s.pedirCodigo("ana@correo.co") }
        e.reloj += 60_000
        assertEquals("despacio", assertFailsWith<Problema> { e.s.pedirCodigo("ana@correo.co") }.mensaje)
        e.reloj += 3600_000
        e.s.pedirCodigo("ana@correo.co")
    }

    @Test fun cambioYRecuperacionCierranOtrasYAvisan() = Entorno().use { e ->
        val a = e.alta()
        val yo = e.s.cuentaDe(a.token)!!
        val b = e.entrar()
        e.buzon.clear()
        e.s.ponerClave(yo, CambioClave("otra frase bastante larga", "una frase bastante larga"), token = a.token)
        assertEquals(yo, e.s.cuentaDe(a.token))
        assertNull(e.s.cuentaDe(b.token))
        assertEquals("Tu contraseña de El Cajón cambió", e.buzon.single().first)
        assertFalse(e.buzon.single().second.contains("otra frase"))
        val c = e.s.entrarConClave(EntradaClave("ana@correo.co", "otra frase bastante larga"))
        e.reloj += 60_000
        e.s.pedirCodigo("ana@correo.co")
        val recuperada = e.s.entrar(Entrada("ana@correo.co", e.codigo(), clave = "frase nueva recuperada"))
        assertNull(e.s.cuentaDe(a.token))
        assertNull(e.s.cuentaDe(c.token))
        assertEquals(yo, e.s.cuentaDe(recuperada.token))
        assertEquals("Tu contraseña de El Cajón cambió", e.buzon.last().first)
        val d = e.s.entrarConClave(EntradaClave("ana@correo.co", "frase nueva recuperada"))
        e.s.ponerClave(yo, CambioClave("frase con codigo nueva"), conCodigo = true, token = recuperada.token)
        assertNull(e.s.cuentaDe(d.token))
        assertEquals(yo, e.s.cuentaDe(recuperada.token))
    }

    @Test fun clavesComunes() = Entorno().use { e ->
        val a = e.alta()
        val yo = e.s.cuentaDe(a.token)!!
        for (clave in listOf("password", "PASSWORD", "12345678", "password1")) {
            assertEquals("clave_comun", assertFailsWith<Problema> {
                e.s.ponerClave(yo, CambioClave(clave), conCodigo = true, token = a.token)
            }.mensaje)
        }
        assertEquals("clave_corta", assertFailsWith<Problema> { Claves.validar("123456") }.mensaje)
        Claves.validar("una frase poco habitual 42")
    }

    @Test fun avisoSoloDispositivoNuevo() = Entorno().use { e ->
        e.alta()
        assertFalse(e.buzon.any { it.first.startsWith("Se entró") })
        e.entrar()
        assertFalse(e.buzon.any { it.first.startsWith("Se entró") })
        e.entrar("Modelo A")
        assertEquals(1, e.buzon.count { it.first == "Se entró a tu cuenta desde Modelo A" })
        assertTrue(e.buzon.last().second.contains("UTC"))
        e.entrar("Modelo A")
        assertEquals(1, e.buzon.count { it.first.startsWith("Se entró") })
        val b = e.entrar("x".repeat(100))
        val yo = e.s.cuentaDe(b.token)!!
        assertEquals(60, e.s.sesiones(yo, b.token).single { it.actual }.dispositivo.length)
    }

    @Test fun usuarioYApodoCompatibles() = Entorno().use { e ->
        val a = e.alta()
        val yo = e.s.cuentaDe(a.token)!!
        e.s.cambiarUsuario(yo, CambioUsuario("anita"))
        val cambio = e.reloj
        assertEquals(cambio + 30L * 86400_000, e.s.miCuenta(yo).usuarioDesde)
        assertFalse(e.s.usuarioLibre("ana"))
        e.reloj += 14L * 86400_000 + 1
        assertTrue(e.s.usuarioLibre("ana"))
        assertEquals("usuario_espera", assertFailsWith<Problema> { e.s.cambiarUsuario(yo, CambioUsuario("anita2")) }.mensaje)
        e.reloj = cambio + 30L * 86400_000
        e.s.cambiarUsuario(yo, CambioUsuario("anita2"))
        e.s.cambiarApodo(yo, CambioApodo("Uno"))
        e.s.subirPerfil(yo, PerfilSubido(nombre = "Dos"))
        val espera = e.s.miCuenta(yo).apodoDesde
        assertEquals(e.reloj + 14L * 86400_000, espera)
        val problema = assertFailsWith<Problema> { e.s.cambiarApodo(yo, CambioApodo("Tres")) }
        assertEquals(429, problema.codigo)
        assertEquals("apodo_espera", problema.mensaje)
        assertEquals(espera, problema.desde)
        e.s.subirPerfil(yo, PerfilSubido(nombre = "Tres", bio = "Se sincroniza"))
        assertEquals("Dos", e.s.miCuenta(yo).nombre)
        assertEquals("Se sincroniza", e.s.verPerfil(yo, "anita2").bio)
        e.reloj = espera
        e.s.cambiarApodo(yo, CambioApodo("Tres"))
        assertEquals("Tres", e.s.miCuenta(yo).nombre)
    }

    @Test fun limpiezaDiaria() = Entorno().use { e ->
        e.alta()
        assertFailsWith<Problema> { e.s.entrarConClave(EntradaClave("ana@correo.co", "mala")) }
        e.reloj += 90L * 86400_000
        e.s.limpiarSeguridad()
        for (tabla in listOf("sesiones", "intentos", "envios_codigo"))
            assertEquals(0, e.base.usar { leer("SELECT COUNT(*) FROM $tabla") { it.getInt(1) }.single() })
    }

    @Test fun compatibilidad147YRutasNuevas() = Entorno().use { e ->
        testApplication {
            application { fogonSocial(e.s) }
            suspend fun post(ruta: String, cuerpo: String) = client.post("/v1/$ruta") {
                contentType(ContentType.Application.Json); setBody(cuerpo)
            }
            assertEquals(HttpStatusCode.NoContent, post("acceso/codigo", """{"correo":"ana@correo.co"}""").status)
            val alta = post("acceso", """{"correo":"ana@correo.co","codigo":"${e.codigo()}","usuario":"ana","nombre":"Ana","acepta":"${Social.POLITICA}","mayorDeEdad":true,"clave":"una frase bastante larga"}""")
            assertEquals(HttpStatusCode.OK, alta.status)
            val token = Json.parseToJsonElement(alta.bodyAsText()).jsonObject.getValue("token").jsonPrimitive.content
            val entrada = post("acceso/clave", """{"correo":"ana@correo.co","clave":"una frase bastante larga"}""")
            assertEquals(HttpStatusCode.OK, entrada.status)
            val conDispositivo = client.post("/v1/acceso/clave") {
                contentType(ContentType.Application.Json)
                header("X-Fogon-Dispositivo", "Teléfono de prueba")
                setBody("""{"correo":"ana@correo.co","clave":"una frase bastante larga"}""")
            }
            assertEquals(HttpStatusCode.OK, conDispositivo.status)
            assertTrue(e.buzon.any { it.first == "Se entró a tu cuenta desde Teléfono de prueba" })
            assertTrue(client.get("/v1/cuenta") { header("Authorization", "Bearer $token") }.bodyAsText().contains("\"apodoDesde\":0"))
            // La ruta que decía si un correo tenía cuenta ya no existe.
            assertEquals(HttpStatusCode.NotFound, post("acceso/correo", """{"correo":"ana@correo.co"}""").status)
            suspend fun perfil(nombre: String) = client.put("/v1/perfil") {
                header("Authorization", "Bearer $token"); contentType(ContentType.Application.Json)
                setBody("""{"nombre":"$nombre","bio":"Sincronizada"}""")
            }
            assertEquals(HttpStatusCode.NoContent, perfil("Uno").status)
            assertEquals(HttpStatusCode.NoContent, perfil("Dos").status)
            assertEquals(HttpStatusCode.NoContent, perfil("Tres").status)
            assertEquals("Dos", e.s.miCuenta(e.s.cuentaDe(token)!!).nombre)
            val apodo = client.put("/v1/cuenta/apodo") {
                header("Authorization", "Bearer $token"); contentType(ContentType.Application.Json); setBody("""{"nombre":"Tres"}""")
            }
            assertEquals(HttpStatusCode.TooManyRequests, apodo.status)
            assertTrue(apodo.bodyAsText().contains("desde"))
            val lista = client.get("/v1/sesiones") { header("Authorization", "Bearer $token") }
            assertEquals(HttpStatusCode.OK, lista.status)
            assertFalse(lista.bodyAsText().contains("huella"))
            assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/sesiones").status)
            val opaco = e.s.sesiones(e.s.cuentaDe(token)!!, token).first().id
            assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/cuenta") { header("Authorization", "Bearer $opaco") }.status)
            assertEquals(HttpStatusCode.NoContent, client.delete("/v1/sesiones/otras") { header("Authorization", "Bearer $token") }.status)
            val id = e.s.sesiones(e.s.cuentaDe(token)!!, token).single().id
            assertEquals(HttpStatusCode.NoContent, client.delete("/v1/sesiones/$id") { header("Authorization", "Bearer $token") }.status)
            assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/cuenta") { header("Authorization", "Bearer $token") }.status)
            assertEquals(HttpStatusCode.ServiceUnavailable, post("cuentas", "{}").status)
            assertEquals("""{"error":"sin_correo"}""", post("cuentas", "{}").bodyAsText())
        }
    }

    @Test fun migracionIdempotenteDeSesionesYTokensAnteriores() {
        val dir = Files.createTempDirectory("migracion-sesiones").toFile()
        val ruta = java.io.File(dir, "s.db").path
        val token = Base.tokenNuevo()
        val antiguo = Base.tokenNuevo()
        val creada = System.currentTimeMillis()
        try {
            Base(ruta).cerrar()
            java.sql.DriverManager.getConnection("jdbc:sqlite:$ruta").use { c ->
                c.createStatement().use { s ->
                    s.execute("DROP TABLE sesiones")
                    s.execute("CREATE TABLE sesiones(huella TEXT PRIMARY KEY, cuenta INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE, creada INTEGER NOT NULL)")
                }
                val id = c.insertar("INSERT INTO cuentas(usuario, token, creada, cambio) VALUES('ana', ?, ?, ?)", Base.huella(antiguo), creada, creada)
                c.cambiar("INSERT INTO sesiones VALUES(?,?,?)", Base.huella(token), id, creada)
            }
            repeat(2) {
                val b = Base(ruta)
                try {
                    val s = Social(b, java.io.File(dir, "fotos"))
                    val yo = s.cuentaDe(token)!!
                    assertEquals(yo, s.cuentaDe(antiguo))
                    assertEquals(2, s.sesiones(yo, token).size)
                    b.usar {
                        assertEquals(listOf(creada, creada), leer("SELECT ultimo_uso FROM sesiones") { it.getLong(1) })
                        assertEquals(listOf(0L, 0L), leer("SELECT elevada_hasta FROM sesiones") { it.getLong(1) })
                    }
                    if (it == 1) {
                        s.cerrarOtras(yo, token)
                        assertNull(s.cuentaDe(antiguo))
                    }
                } finally { b.cerrar() }
            }
            val b = Base(ruta)
            try { assertNull(Social(b, java.io.File(dir, "fotos")).cuentaDe(antiguo)) }
            finally { b.cerrar() }
        } finally { dir.deleteRecursively() }
    }
}

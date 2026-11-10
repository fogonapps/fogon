package io.github.fogonapps.social

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.*

class AppsTest {
    private class Entorno : AutoCloseable {
        val dir = Files.createTempDirectory("apps-social").toFile()
        val b = Base(java.io.File(dir, "social.db").path)
        var reloj = 2_000_000_000_000L
        val correos = mutableListOf<Pair<String, String>>()
        val s = Social(b, java.io.File(dir, "fotos"), { reloj }, Cartero { _, asunto, texto -> correos += asunto to texto })
        val token = s.crearCuenta(NuevaCuenta("ana")).token
        val yo = s.cuentaDe(token)!!
        fun codigo() = Regex("[0-9]{6}").find(correos.last { it.first.startsWith("Tu código") }.first)!!.value
        override fun close() { b.cerrar(); dir.deleteRecursively() }
    }
    private fun foto(clave: String, cuando: Long = 1) = PublicacionSubida(clave, Vis.PUBLICA, cuando, "foto", buildJsonObject { put("texto", "#prueba") })
    private fun entreno(clave: String = "igual", cuando: Long = 2) = PublicacionSubida(clave, Vis.PUBLICA, cuando, "entreno", JsonSocial.parseToJsonElement("""{
        "version":1,"titulo":"Fuerza <hoy>","fecha":1000,"duracion":3600,"unidadDuracion":"s",
        "ejercicios":[{"id":"sentadilla","nombre":"Sentadilla","series":[{"peso":40,"unidadPeso":"kg","repeticiones":10}]}]
    }""").jsonObject)

    @Test fun compatibilidad161CompletaIncrementalClavesRetiradasYRelaciones() = Entorno().use { e ->
        val s = e.s; val yo = e.yo
        s.subirPublicaciones(yo, listOf(foto("igual"), foto("otra")))
        val original = e.b.usar { leer("SELECT id FROM publicaciones WHERE cuenta = ? AND clave = 'igual'", yo) { it.getLong(1) }.single() }
        val otro = s.cuentaDe(s.crearCuenta(NuevaCuenta("beto")).token)!!
        val comentario = s.comentar(otro, original, "Se conserva")
        s.meGusta(otro, original, true)
        s.subirCambios(yo, CambiosPublicaciones(listOf(entreno())), "forzudo")
        val deportivo = s.actividad(yo, null, app = "forzudo").single().id
        s.subirPublicaciones(yo, listOf(foto("igual"), foto("otra")))
        s.subirCambios(yo, CambiosPublicaciones(listOf(foto("nueva")), listOf("otra")))
        assertEquals(deportivo, s.actividad(yo, null, app = "forzudo").single().id)
        assertEquals(comentario, s.comentarios(yo, original).single().id)
        assertEquals(1, s.publicacion(yo, original)!!.megusta)
        s.borrarPublicacion(yo, deportivo)
        s.subirPublicaciones(yo, listOf(foto("igual")))
        s.subirCambios(yo, CambiosPublicaciones(listOf(entreno())), "forzudo")
        assertTrue(s.actividad(yo, null, app = "forzudo").isEmpty())
        assertEquals(1, s.actividad(yo, null).size)
        assertEquals("forzudo", e.b.usar { leer("SELECT app FROM retiradas") { it.getString(1) }.single() })
    }

    @Test fun perfilHeredadoConservaPartesYParcialConservaIdentidad() = Entorno().use { e ->
        e.s.subirPerfil(e.yo, PerfilSubido(nombre = "Ana", bio = "Biografía", partes = mapOf("favoritas" to Parte(Vis.PUBLICA, JsonPrimitive("cine")))))
        e.s.subirPerfilApp(e.yo, "forzudo", PerfilApp(partes = mapOf("cifras" to Parte(Vis.PUBLICA, JsonPrimitive(3)))))
        assertEquals("Biografía", e.s.verPerfil(null, "ana", "forzudo").bio)
        e.s.subirPerfil(e.yo, PerfilSubido(nombre = "Ana", bio = "Nueva"))
        assertEquals(JsonPrimitive(3), e.s.verPerfil(null, "ana", "forzudo").partes["cifras"])
        assertTrue(e.s.verPerfil(null, "ana").partes.isEmpty())
        val revision = e.s.revisionPerfil(e.yo)
        e.s.subirPerfilApp(e.yo, "forzudo", PerfilApp(revision, IdentidadParcial(bio = "Parcial")))
        assertEquals("perfil_revision", assertFailsWith<Problema> { e.s.subirPerfilApp(e.yo, "forzudo", PerfilApp(revision, IdentidadParcial(bio = "Vieja"))) }.mensaje)
        assertEquals("Parcial", e.s.verPerfil(null, "ana").bio)
        assertEquals(1, e.s.verPerfil(null, "ana", "forzudo").partes.size)
    }

    @Test fun filtrosAntesDelLimiteYPublicacionPorIdGlobal() = Entorno().use { e ->
        e.s.subirPublicaciones(e.yo, listOf(foto("vieja")))
        e.s.subirCambios(e.yo, CambiosPublicaciones((1..70).map { entreno("e$it", it + 10L) }), "forzudo")
        assertEquals("foto", e.s.actividad(e.yo, null, n = 1).single().tipo)
        assertEquals("foto", e.s.explorar(null, null, n = 1).single().tipo)
        assertEquals("foto", e.s.verPerfil(null, "ana").publicaciones.single().tipo)
        assertEquals(1, e.s.verPerfil(null, "ana").cuantas)
        assertEquals(70, e.s.verPerfil(null, "ana", "forzudo").cuantas)
        assertEquals(1, e.s.conEtiqueta(null, "prueba", null).size)
        val id = e.s.actividad(e.yo, null, app = "forzudo").first().id
        e.s.guardar(e.yo, id, true)
        assertTrue(e.s.guardados(e.yo).isEmpty())
        assertEquals(id, e.s.guardados(e.yo, "forzudo").single().id)
        assertEquals("forzudo", e.s.publicacion(null, id)!!.app)
    }

    @Test fun loteInvalidoRevierteBajasYAltasYRechazaDatosCorporales() = Entorno().use { e ->
        e.s.subirCambios(e.yo, CambiosPublicaciones(listOf(entreno("queda"))), "forzudo")
        val invalido = entreno("malo").let { it.copy(datos = JsonObject(it.datos + ("pesoCorporal" to JsonPrimitive(80)))) }
        assertEquals("entreno_esquema", assertFailsWith<Problema> {
            e.s.subirCambios(e.yo, CambiosPublicaciones(listOf(entreno("nuevo"), invalido), listOf("queda")), "forzudo")
        }.mensaje)
        val medioAjeno = entreno("ajeno").let { it.copy(datos = JsonObject(it.datos + ("imagenes" to JsonArray(listOf(JsonPrimitive("ajena")))))) }
        assertEquals("imagenes", assertFailsWith<Problema> { e.s.subirCambios(e.yo, CambiosPublicaciones(listOf(entreno("nuevo"), medioAjeno), listOf("queda")), "forzudo") }.mensaje)
        assertEquals(1, e.s.actividad(e.yo, null, app = "forzudo").size)
        for (tipo in listOf("plato", "receta", "resena_restaurante", "entreno")) {
            assertEquals("tipo_app", assertFailsWith<Problema> { e.s.subirCambios(e.yo, CambiosPublicaciones(listOf(entreno().copy(tipo = tipo))), "cucha") }.mensaje)
        }
        assertEquals("tipo_app", assertFailsWith<Problema> { e.s.subirPublicaciones(e.yo, listOf(entreno())) }.mensaje)
    }

    @Test fun correosSesionesEIntentosGlobales() = Entorno().use { e ->
        e.s.pedirCodigo("ana@correo.co", "forzudo")
        assertTrue(e.correos.last().first.contains("El Forzudo"))
        assertTrue(e.correos.last().second.contains("El Forzudo"))
        assertEquals("despacio", assertFailsWith<Problema> { e.s.pedirCodigo("ana@correo.co", "cucha") }.mensaje)
        val codigo = e.codigo()
        repeat(5) { n ->
            assertEquals("codigo", assertFailsWith<Problema> { e.s.entrar(Entrada(correo = "ana@correo.co", codigo = "malo", app = if (n % 2 == 0) "cajon" else "forzudo")) }.mensaje)
        }
        assertEquals("codigo_vencido", assertFailsWith<Problema> { e.s.entrar(Entrada(correo = "ana@correo.co", codigo = codigo, app = "cucha")) }.mensaje)
        e.reloj += 60_001
        e.s.pedirCodigo("ana@correo.co", "forzudo")
        val acceso = e.s.entrar(Entrada("ana@correo.co", e.codigo(), "nueva", acepta = Social.POLITICA, mayorDeEdad = true, app = "forzudo"), "Teléfono")
        assertEquals("forzudo", e.s.sesiones(e.s.cuentaDe(acceso.token)!!, acceso.token).single().app)
        assertTrue(e.correos.last().first.contains("El Forzudo"))
        repeat(3) { e.reloj += 60_001; e.s.pedirCodigo("ana@correo.co", if (it % 2 == 0) "cajon" else "forzudo") }
        e.reloj += 60_001
        assertEquals("despacio", assertFailsWith<Problema> { e.s.pedirCodigo("ana@correo.co", "cucha") }.mensaje)
    }

    @Test fun avisosPorOrigenLecturaYSancionesGlobales() = Entorno().use { e ->
        val otro = e.s.cuentaDe(e.s.crearCuenta(NuevaCuenta("beto")).token)!!
        e.s.subirPublicaciones(e.yo, listOf(foto("igual")))
        e.s.subirCambios(e.yo, CambiosPublicaciones(listOf(entreno())), "forzudo")
        val cultural = e.s.actividad(e.yo, null).single().id
        val deportivo = e.s.actividad(e.yo, null, app = "forzudo").single().id
        e.s.meGusta(otro, cultural, true); e.s.comentar(otro, deportivo, "Bien")
        e.b.usar { cambiar("INSERT INTO avisos(para,de,tipo,cuando) VALUES(?,?,'sancion',?)", e.yo, otro, e.reloj) }
        assertEquals(2, e.s.avisos(e.yo, null).size)
        assertEquals("forzudo", e.s.avisos(e.yo, null, "forzudo").single { it.tipo == "comentario" }.app)
        e.s.leidos(e.yo)
        assertEquals(0, e.s.sinLeer(e.yo))
        assertEquals(1, e.s.sinLeer(e.yo, "forzudo"))
        e.s.borrarAvisos(e.yo, null)
        assertEquals(1, e.s.avisos(e.yo, null, "forzudo").size)
    }

    @Test fun apodoNoBorraPartesYRevisionNoRetrocede() = Entorno().use { e ->
        val parte = mapOf("cifras" to Parte(Vis.PUBLICA, JsonPrimitive(1)))
        e.s.subirPerfil(e.yo, PerfilSubido(nombre = "Ana", partes = parte))
        e.s.subirPerfilApp(e.yo, "forzudo", PerfilApp(partes = parte))
        val revision = e.s.revisionPerfil(e.yo)
        e.s.cambiarApodo(e.yo, CambioApodo("Anita"))
        assertEquals(parte.keys, e.s.verPerfil(null, "ana").partes.keys)
        assertEquals(parte.keys, e.s.verPerfil(null, "ana", "forzudo").partes.keys)
        assertTrue(e.s.revisionPerfil(e.yo) > revision)
        val nueva = e.s.revisionPerfil(e.yo)
        e.s.ajustar(e.yo, Ajustes(true))
        assertTrue(e.s.revisionPerfil(e.yo) > nueva)
    }

    @Test fun retiradaYRestauracionNoCruzanApps() = Entorno().use { e ->
        val token = e.s.crearCuenta(NuevaCuenta("jefa")).token
        val jefa = e.s.cuentaDe(token)!!
        e.b.usar {
            cambiar("INSERT INTO roles VALUES(?,'administrador','servidor',0)", jefa)
            cambiar("INSERT INTO mfa VALUES(?,'',1,0,-1)", jefa)
            cambiar("UPDATE sesiones SET elevada_hasta = ? WHERE cuenta = ?", Long.MAX_VALUE, jefa)
        }
        e.s.subirPublicaciones(e.yo, listOf(foto("igual")))
        e.s.subirCambios(e.yo, CambiosPublicaciones(listOf(entreno())), "forzudo")
        val cultural = e.s.actividad(e.yo, null).single().id
        val deportivo = e.s.actividad(e.yo, null, app = "forzudo").single().id
        e.s.retirar(jefa, ContenidoEquipo("publicacion", "$cultural"), token)
        e.s.retirar(jefa, ContenidoEquipo("publicacion", "$deportivo"), token)
        e.s.restaurar(jefa, ContenidoEquipo("publicacion", "$deportivo"), token)
        e.s.subirPublicaciones(e.yo, listOf(foto("igual")))
        assertTrue(e.s.actividad(e.yo, null).isEmpty())
        assertEquals(deportivo, e.s.actividad(e.yo, null, app = "forzudo").single().id)
        assertEquals("cajon", e.b.usar { leer("SELECT app FROM retiradas") { it.getString(1) }.single() })
    }

    @Test fun migracionViejaConReferenciasIdempotente() {
        val dir = Files.createTempDirectory("apps-migracion").toFile()
        val ruta = java.io.File(dir, "social.db").path
        try {
            DriverManager.getConnection("jdbc:sqlite:$ruta").use { c ->
                c.createStatement().use { st ->
                    st.execute("CREATE TABLE cuentas(id INTEGER PRIMARY KEY, usuario TEXT UNIQUE NOT NULL, token TEXT UNIQUE NOT NULL, nombre TEXT NOT NULL DEFAULT '', perfil TEXT NOT NULL DEFAULT '{}', foto INTEGER NOT NULL DEFAULT 0, creada INTEGER NOT NULL, cambio INTEGER NOT NULL)")
                    st.execute("INSERT INTO cuentas(id,usuario,token,creada,cambio,perfil) VALUES(7,'ana','retirado:x',1,1,'{\"bio\":\"Antes\",\"partes\":{\"cine\":{\"visibilidad\":\"publica\",\"datos\":1}}}')")
                    st.execute("CREATE TABLE publicaciones(id INTEGER PRIMARY KEY, cuenta INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE, clave TEXT NOT NULL, visibilidad TEXT NOT NULL, cuando INTEGER NOT NULL, datos TEXT NOT NULL, UNIQUE(cuenta, clave))")
                    st.execute("INSERT INTO publicaciones VALUES(42,7,'igual','publica',1,'{\"tipo\":\"foto\"}')")
                    st.execute("CREATE TABLE retiradas(cuenta INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE, clave TEXT NOT NULL, cuando INTEGER NOT NULL, PRIMARY KEY(cuenta, clave))")
                    st.execute("INSERT INTO retiradas VALUES(7,'retirada',1)")
                    st.execute("CREATE TABLE comentarios(id INTEGER PRIMARY KEY, pub INTEGER NOT NULL REFERENCES publicaciones(id) ON DELETE CASCADE, cuenta INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE, texto TEXT NOT NULL, cuando INTEGER NOT NULL)")
                    st.execute("INSERT INTO comentarios VALUES(9,42,7,'Intacto',1)")
                    st.execute("CREATE TABLE megusta(pub INTEGER NOT NULL REFERENCES publicaciones(id) ON DELETE CASCADE, cuenta INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE, PRIMARY KEY(pub,cuenta))")
                    st.execute("INSERT INTO megusta VALUES(42,7)")
                }
            }
            repeat(2) {
                val b = Base(ruta)
                try {
                    val s = Social(b, java.io.File(dir, "fotos"))
                    assertEquals(42, s.actividad(7, null).single().id)
                    assertEquals(9, s.comentarios(7, 42).single().id)
                    assertEquals(1, s.publicacion(7, 42)!!.megusta)
                    assertEquals(JsonPrimitive(1), s.verPerfil(null, "ana").partes["cine"])
                    b.usar {
                        assertTrue(leer("PRAGMA foreign_key_check") { it.getString(1) }.isEmpty())
                        assertEquals("cajon", leer("SELECT app FROM retiradas") { it.getString(1) }.single())
                    }
                    s.subirCambios(7, CambiosPublicaciones(listOf(entreno())), "forzudo")
                    assertEquals(1, s.actividad(7, null, app = "forzudo").size)
                } finally { b.cerrar() }
            }
        } finally { dir.deleteRecursively() }
    }

    @Test fun rutasCompatibilidadChatGraciaYWeb() = Entorno().use { e ->
        val forzudo = "token-forzudo"
        e.b.usar { cambiar("INSERT INTO sesiones(huella,cuenta,creada,ultimo_uso,app) VALUES(?,?,?,?,?)", Base.huella(forzudo), e.yo, e.reloj, e.reloj, "forzudo") }
        testApplication {
            application { fogonSocial(e.s) }
            val carga = JsonSocial.encodeToString(CambiosPublicaciones.serializer(), CambiosPublicaciones(listOf(entreno())))
            assertEquals(HttpStatusCode.NoContent, client.put("/v2/apps/forzudo/publicaciones/cambios") { bearerAuth(forzudo); contentType(ContentType.Application.Json); setBody(carga) }.status)
            assertEquals(HttpStatusCode.NoContent, client.put("/v1/publicaciones") { bearerAuth(e.token); contentType(ContentType.Application.Json); setBody("[]") }.status)
            assertEquals("[]", client.get("/v1/actividad") { bearerAuth(e.token) }.bodyAsText())
            assertTrue(client.get("/v2/apps/forzudo/actividad") { bearerAuth(forzudo) }.bodyAsText().contains("entreno"))
            for (ruta in listOf("/v1/mensajes", "/v1/mensajes/esperar", "/v1/mensajes/sin-leer", "/v1/llaves/ana", "/v1/cajon")) {
                assertEquals(HttpStatusCode.Forbidden, client.get(ruta) { bearerAuth(forzudo) }.status)
            }
            assertEquals(HttpStatusCode.Forbidden, client.put("/v1/llaves") { bearerAuth(forzudo); contentType(ContentType.Application.Json); setBody("{}") }.status)
            assertEquals(HttpStatusCode.OK, client.get("/v1/mensajes") { bearerAuth(e.token) }.status)
            val id = e.s.actividad(e.yo, null, app = "forzudo").single().id
            val web = client.get("/p/$id").bodyAsText()
            assertTrue(web.contains("Fuerza &lt;hoy&gt;"))
            assertEquals(HttpStatusCode.OK, client.get("/@ana").status)
            e.b.usar { cambiar("UPDATE cuentas SET borrar_el = ? WHERE id = ?", e.reloj + 1000, e.yo) }
            assertEquals(HttpStatusCode.Forbidden, client.put("/v2/apps/forzudo/publicaciones/cambios") { bearerAuth(forzudo); contentType(ContentType.Application.Json); setBody(carga) }.status)
        }
    }
}

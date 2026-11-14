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
import kotlinx.coroutines.async
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class SocialTest {
    private fun nuevo(): Social {
        val dir = Files.createTempDirectory("social").toFile()
        return Social(Base(java.io.File(dir, "s.db").path), java.io.File(dir, "fotos"))
    }

    private fun pub(clave: String, vis: String, cuando: Long = 1) =
        PublicacionSubida(clave, vis, cuando, "diario", buildJsonObject { put("titulo", "Obra $clave") })

    @Test fun resenasDeUnaObra() {
        val s = nuevo()
        val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("ana")).token)!!
        val beto = s.cuentaDe(s.crearCuenta(NuevaCuenta("beto")).token)!!
        fun resena(clave: String, vis: String, fuente: String) = PublicacionSubida(clave, vis, 1, "resena",
            buildJsonObject { put("titulo", "Matrix"); put("fuente", fuente); put("texto", "buenísima") })
        s.subirPublicaciones(ana, listOf(resena("o:1", Vis.PUBLICA, "tmdb:movie:603"), resena("o:2", Vis.PUBLICA, "tmdb:movie:604"),
            PublicacionSubida("r:1", Vis.PUBLICA, 2, "diario", buildJsonObject { put("fuente", "tmdb:movie:603") })))
        s.subirPublicaciones(beto, listOf(resena("o:9", Vis.SEGUIDORES, "tmdb:movie:603")))
        assertEquals(listOf("ana"), s.resenas(null, "tmdb:movie:603").map { it.autor.usuario })      // sin sesión: solo lo público, solo reseñas
        s.seguir(ana, "beto")
        assertEquals(setOf("ana", "beto"), s.resenas(ana, "tmdb:movie:603").map { it.autor.usuario }.toSet())   // y lo de quien sigue
        s.bloquear(beto, "ana", true)
        assertEquals(listOf("beto"), s.resenas(beto, "tmdb:movie:603").map { it.autor.usuario })     // sin las bloqueadas
    }

    @Test fun frasesPropuestas() {
        val dir = Files.createTempDirectory("social").toFile()
        val s = Social(Base(java.io.File(dir, "s.db").path), java.io.File(dir, "fotos"))
        val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("ana")).token)!!
        val tokenJefa = s.crearCuenta(NuevaCuenta("moderadora")).token
        val jefa = s.cuentaDe(tokenJefa)!!
        s.proponerFrase(ana, FraseNueva("¿Hoy toca maratón de terror?", "es"))
        assertFailsWith<Problema> { s.proponerFrase(ana, FraseNueva("mira https://x.co", "es")) }      // sin enlaces
        assertFailsWith<Problema> { s.proponerFrase(ana, FraseNueva("hola", "es")) }                   // muy corta
        assertEquals(0, s.frasesComunidad("es").size)                                                // no sale sin aprobar
        assertFailsWith<Problema> { s.frasesPendientes(ana) }                                        // solo el equipo
        Base(java.io.File(dir, "s.db").path).let { base ->
            base.usar {
                cambiar("INSERT INTO roles VALUES(?, 'administrador', 'servidor', 0)", jefa)
                cambiar("INSERT INTO mfa VALUES(?, '', 1, 0, -1)", jefa)
                cambiar("UPDATE sesiones SET elevada_hasta = ? WHERE cuenta = ?", Long.MAX_VALUE, jefa)
            }
            base.cerrar()
        }
        val pendiente = s.frasesPendientes(jefa, tokenJefa).single()
        assertEquals("ana", pendiente.de)
        s.decidirFrase(jefa, DecisionFrase(pendiente.clave, true), tokenJefa)
        assertEquals(listOf("¿Hoy toca maratón de terror?"), s.frasesComunidad("es").map { it.texto })
        assertEquals(0, s.frasesComunidad("en").size)
        repeat(2) { s.proponerFrase(ana, FraseNueva("Otra frase más $it", "es")) }
        assertFailsWith<Problema> { s.proponerFrase(ana, FraseNueva("La cuarta del día", "es")) }    // 3 al día
        // Cada app tiene sus frases de la gente: la de El Forzudo no sale en El Cajón, y el equipo ve de qué app es.
        val beto = s.cuentaDe(s.crearCuenta(NuevaCuenta("beto")).token)!!
        s.proponerFrase(beto, FraseNueva("Hoy toca pierna, sin excusas", "es"), "forzudo")
        val deForzudo = s.frasesPendientes(jefa, tokenJefa).first { it.texto.startsWith("Hoy toca pierna") }
        assertEquals("forzudo", deForzudo.app)
        s.decidirFrase(jefa, DecisionFrase(deForzudo.clave, true), tokenJefa)
        assertEquals(listOf("Hoy toca pierna, sin excusas"), s.frasesComunidad("es", "forzudo").map { it.texto })
        assertTrue(s.frasesComunidad("es").none { it.texto.startsWith("Hoy toca pierna") })
        val agregada = s.agregarFrase(jefa, FraseNueva("A cocinar sin afán", "es", app = "cucha"), tokenJefa)
        assertEquals("cucha", agregada.app)
        assertEquals(listOf("A cocinar sin afán"), s.frasesComunidad("es", "cucha").map { it.texto })
        assertFailsWith<Problema> { s.proponerFrase(beto, FraseNueva("Una app que no existe", "es"), "otra") }
    }

    @Test fun frasesDelSaludo() {
        val s = nuevo()
        val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("ana")).token)!!
        val beto = s.cuentaDe(s.crearCuenta(NuevaCuenta("beto")).token)!!
        s.votarFrase(ana, "frase_2", VotoFrase(1)); s.votarFrase(ana, "frase_2", VotoFrase(1))   // una por cuenta
        s.votarFrase(beto, "frase_2", VotoFrase(-1))
        assertEquals(ConteoFrase(1, 1), s.frases()["frase_2"])
        s.votarFrase(beto, "frase_2", VotoFrase(0))                                              // quita su voto
        assertEquals(ConteoFrase(1, 0), s.frases()["frase_2"])
        assertFailsWith<Problema> { s.votarFrase(ana, "Frase con espacios", VotoFrase(1)) }
        assertFailsWith<Problema> { s.votarFrase(ana, "frase_1", VotoFrase(5)) }
        s.borrarCuenta(ana)
        assertNull(s.frases()["frase_2"])                                                       // se va con la cuenta
    }

    @Test fun seguirVisibilidadYBloqueos() {
        val s = nuevo()
        val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("ana")).token)!!
        val beto = s.cuentaDe(s.crearCuenta(NuevaCuenta("beto")).token)!!
        val caro = s.cuentaDe(s.crearCuenta(NuevaCuenta("caro")).token)!!
        assertFailsWith<Problema> { s.crearCuenta(NuevaCuenta("ANA")) }         // el usuario ya existe
        assertFailsWith<Problema> { s.crearCuenta(NuevaCuenta("a b")) }         // no es un usuario válido
        assertTrue(!s.usuarioLibre("ana") && s.usuarioLibre("dani") && !s.usuarioLibre("admin"))
        assertTrue(s.usuarioLibre("fogon") && !s.usuarioLibre("soporte"))

        s.subirPublicaciones(ana, listOf(pub("1", Vis.PUBLICA, 10), pub("2", Vis.SEGUIDORES, 20), pub("3", "privada", 30)))
        s.subirPerfil(ana, PerfilSubido(nombre = "Ana", partes = mapOf(
            "destacados" to Parte(Vis.PUBLICA, JsonPrimitive("d")), "diario" to Parte(Vis.SEGUIDORES, JsonPrimitive("x")))))
        // Sin seguirla: solo lo público. Lo privado ni se guardó.
        var visto = s.verPerfil(beto, "ana")
        assertEquals(listOf("destacados"), visto.partes.keys.toList())
        assertEquals(1, visto.publicaciones.size)
        assertEquals(2, visto.cuantas)
        // Seguirla (cuenta pública: sin aprobar): también lo de seguidores, y sale en su feed; a ella le llega el aviso.
        assertEquals("sigo", s.seguir(beto, "ana"))
        visto = s.verPerfil(beto, "ana")
        assertEquals(2, visto.publicaciones.size)
        assertEquals(1, visto.seguidores)
        assertTrue(s.verPerfil(ana, "beto").meSigue)
        assertEquals(2, s.actividad(beto, null).size)
        assertEquals(0, s.actividad(caro, null).size)
        assertEquals("seguir", s.avisos(ana, null).single().tipo)
        assertEquals(1, s.explorar(caro, null).size)                        // caro descubre lo público de ana

        // Me gusta, comentarios y guardar, con sus avisos.
        val id = s.actividad(beto, null).first().id
        s.meGusta(beto, id, true); s.meGusta(beto, id, true); s.guardar(beto, id, true)
        val c = s.comentar(beto, id, "¡Qué buena!")
        assertEquals(1, s.publicacion(ana, id)!!.megusta)
        assertEquals(1, s.guardados(beto).size)
        assertEquals(listOf("comentario", "megusta", "seguir"), s.avisos(ana, null).map { it.tipo })
        assertEquals(3, s.sinLeer(ana)); s.leidos(ana); assertEquals(0, s.sinLeer(ana))
        assertTrue(s.comentarios(ana, id).single().puedoBorrar)            // la dueña puede borrarlo
        assertFailsWith<Problema> { s.borrarComentario(caro, c) }          // otro no
        assertFailsWith<Problema> { s.comentar(caro, id, "hola") }         // caro no sigue a ana: no ve lo de seguidores

        // Volver a subir sin la 2 (la volvió privada): se va, con sus me gusta.
        s.subirPublicaciones(ana, listOf(pub("1", Vis.PUBLICA, 10)))
        assertNull(s.publicacion(beto, id))

        // Bloquear: deja de seguir y no se ven.
        s.bloquear(ana, "beto", true)
        assertFailsWith<Problema> { s.verPerfil(beto, "ana") }
        assertEquals(0, s.verPerfil(ana, "ana").seguidores)
        assertEquals(emptyList(), s.buscar(beto, "an"))
        assertEquals(listOf("beto"), s.bloqueados(ana).map { it.usuario })

        // Borrar la cuenta: se va todo.
        s.borrarCuenta(ana)
        assertFailsWith<Problema> { s.verPerfil(caro, "ana") }
        assertNull(s.cuentaDe("token-que-no-existe"))
    }

    @Test fun cuentaPrivada() {
        val s = nuevo()
        val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("ana")).token)!!
        val beto = s.cuentaDe(s.crearCuenta(NuevaCuenta("beto")).token)!!
        s.ajustar(ana, Ajustes(privada = true))
        s.subirPublicaciones(ana, listOf(pub("1", Vis.PUBLICA, 10)))
        // Cerrada: ni lo público se ve, ni sus listas, ni sale en explorar.
        assertTrue(s.verPerfil(beto, "ana").cerrada)
        assertEquals(0, s.verPerfil(beto, "ana").publicaciones.size)
        assertFailsWith<Problema> { s.lista(beto, "ana", seguidores = true) }
        assertEquals(0, s.explorar(beto, null).size)
        assertNull(s.publicacion(null, s.verPerfil(ana, "ana").publicaciones.first().id))
        // Pide seguir: queda pendiente hasta que ana acepta.
        assertEquals("pendiente", s.seguir(beto, "ana"))
        assertEquals(listOf("beto"), s.solicitudes(ana).map { it.usuario })
        assertEquals(0, s.actividad(beto, null).size)
        s.aceptar(ana, "beto")
        assertEquals("aceptada", s.avisos(beto, null).single().tipo)
        assertEquals(1, s.actividad(beto, null).size)
        assertTrue(!s.verPerfil(beto, "ana").cerrada)
        // Quitar al seguidor: vuelve a estar cerrada para él.
        s.quitarSeguidor(ana, "beto")
        assertTrue(s.verPerfil(beto, "ana").cerrada)
    }

    @Test fun telefonoVacioNoBorraLoPublicado() {
        val s = nuevo()
        val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("ana")).token)!!
        s.subirCambios(ana, CambiosPublicaciones(listOf(pub("1", Vis.PUBLICA, 10), pub("2", Vis.PUBLICA, 11))))
        s.subirCambios(ana, CambiosPublicaciones(emptyList()))                       // un teléfono nuevo, sin nada
        assertEquals(2, s.explorar(null, null).size)
        s.subirCambios(ana, CambiosPublicaciones(emptyList(), quitar = listOf("1")))  // quitar es explícito
        assertEquals(listOf("Obra 2"), s.explorar(null, null).map { (it.datos["titulo"] as JsonPrimitive).content })
    }

    @Test fun mensajesAlInstante(): Unit = kotlinx.coroutines.runBlocking {
        val s = nuevo()
        val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("ana")).token)!!
        val beto = s.cuentaDe(s.crearCuenta(NuevaCuenta("beto")).token)!!
        val espera = async(kotlinx.coroutines.Dispatchers.Default) { s.esperarMensajes(beto, 0, 10_000) }
        kotlinx.coroutines.delay(300)
        val inicio = System.currentTimeMillis()
        s.mandar(ana, "beto", SobreNuevo("sobre", "firma"))
        assertEquals(1, espera.await().size)
        assertTrue(System.currentTimeMillis() - inicio < 2_000)              // llega al instante, no a los 10 s
        assertEquals(0, s.esperarMensajes(ana, 0, 200).size)               // sin nada, espera y vuelve vacío
    }

    @Test fun registroYOlvideLaClave() {
        val dir = Files.createTempDirectory("social").toFile()
        val buzon = mutableListOf<String>()
        var reloj = 1_000_000L
        val s = Social(Base(java.io.File(dir, "s.db").path), java.io.File(dir, "fotos"), ahora = { reloj }, cartero = { _, asunto, _ -> buzon += asunto })
        fun codigo() = Regex("[0-9]{6}").find(buzon.last())!!.value
        assertEquals(EstadoCorreo(false, false), s.estadoCorreo("bea@correo.co"))                   // no existe: se registra
        s.pedirCodigo("bea@correo.co")
        assertEquals("clave_corta", assertFailsWith<Problema> {
            s.entrar(Entrada("bea@correo.co", codigo(), usuario = "bea", acepta = Social.POLITICA, mayorDeEdad = true, clave = "corta")) }.mensaje)
        s.entrar(Entrada("bea@correo.co", codigo(), usuario = "bea", acepta = Social.POLITICA, mayorDeEdad = true, clave = "una frase larga"))
        assertEquals(EstadoCorreo(true, true), s.estadoCorreo("Bea@correo.co"))                     // existe y entra con contraseña
        s.entrarConClave(EntradaClave("bea@correo.co", "una frase larga"))
        // La olvidó: con un código nuevo pone otra, y la vieja deja de servir.
        reloj += 61_000; s.pedirCodigo("bea@correo.co")
        s.entrar(Entrada("bea@correo.co", codigo(), clave = "otra frase larga"))
        assertFailsWith<Problema> { s.entrarConClave(EntradaClave("bea@correo.co", "una frase larga")) }
        s.entrarConClave(EntradaClave("bea@correo.co", "otra frase larga"))
        assertFailsWith<Problema> { s.estadoCorreo("no-es-correo") }
    }

    @Test fun entrarConCorreo() {
        val dir = Files.createTempDirectory("social").toFile()
        val buzon = mutableListOf<Pair<String, String>>()
        var reloj = 1_000_000L
        val s = Social(Base(java.io.File(dir, "s.db").path), java.io.File(dir, "fotos"), ahora = { reloj }, cartero = { para, asunto, _ -> buzon += para to asunto })
        fun codigo() = Regex("[0-9]{6}").find(buzon.last().second)!!.value
        assertFailsWith<Problema> { s.pedirCodigo("no-es-correo") }
        s.pedirCodigo("Ana@Correo.co")
        assertEquals("ana@correo.co", buzon.single().first)
        assertFailsWith<Problema> { s.pedirCodigo("ana@correo.co") }                           // uno por minuto
        assertEquals("codigo", assertFailsWith<Problema> { s.entrar(Entrada("ana@correo.co", if (codigo() == "000000") "111111" else "000000")) }.mensaje)
        // Cuenta nueva: primero pide usuario, aceptación y edad, sin gastar el código.
        val e = assertFailsWith<Problema> { s.entrar(Entrada("ana@correo.co", codigo())) }
        assertEquals("cuenta_nueva", e.mensaje)
        assertFailsWith<Problema> { s.entrar(Entrada("ana@correo.co", codigo(), usuario = "ana", acepta = Social.POLITICA)) }   // sin 18
        val a = s.entrar(Entrada("ana@correo.co", codigo(), usuario = "ana", acepta = Social.POLITICA, mayorDeEdad = true))
        assertTrue(a.nueva)
        assertEquals("ana@correo.co", s.miCuenta(s.cuentaDe(a.token)!!).correo)
        assertFailsWith<Problema> { s.entrar(Entrada("ana@correo.co", codigo())) }            // el código ya se gastó
        // En otro teléfono, un minuto después: el mismo correo abre otra sesión de la misma cuenta.
        reloj += 61_000
        s.pedirCodigo("ana@correo.co")
        val b = s.entrar(Entrada("ana@correo.co", codigo()))
        assertTrue(!b.nueva); assertEquals(s.cuentaDe(a.token), s.cuentaDe(b.token))
        s.salir(b.token); assertNull(s.cuentaDe(b.token)); assertTrue(s.cuentaDe(a.token) != null)
        // El código vence a los 10 minutos.
        reloj += 61_000
        s.pedirCodigo("ana@correo.co"); val viejo = codigo()
        reloj += 11 * 60_000
        assertEquals("codigo_vencido", assertFailsWith<Problema> { s.entrar(Entrada("ana@correo.co", viejo)) }.mensaje)
        // Una cuenta sin correo ya existente puede vincular uno.
        val sin = s.crearCuenta(NuevaCuenta("beto"))
        assertEquals("", s.miCuenta(s.cuentaDe(sin.token)!!).correo)
        s.pedirCodigo("beto@correo.co")
        s.vincularCorreo(s.cuentaDe(sin.token)!!, Entrada("beto@correo.co", codigo()))
        assertEquals("beto@correo.co", s.miCuenta(s.cuentaDe(sin.token)!!).correo)
    }

    @Test fun chatNoReutilizaIdsAlConfirmar() {
        val dir = Files.createTempDirectory("chat").toFile()
        val b = Base(java.io.File(dir, "s.db").path)
        try {
            val s = Social(b, java.io.File(dir, "fotos"))
            val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("ana")).token)!!
            val beto = s.cuentaDe(s.crearCuenta(NuevaCuenta("beto")).token)!!
            var ultimo = 0L
            repeat(3) {
                val id = s.mandar(beto, "ana", SobreNuevo("sobre$it", "firma"))
                assertTrue(id > ultimo)
                assertEquals(listOf(id), s.recibir(ana, ultimo).map { it.id })
                ultimo = id
                assertTrue(s.recibir(ana, ultimo).isEmpty())
                assertEquals(0L, b.usar { leer("SELECT COUNT(*) FROM mensajes") { it.getLong(1) }.single() })
            }
        } finally { b.cerrar(); dir.deleteRecursively() }
    }

    @Test fun chatSoloSobres() {
        val s = nuevo()
        val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("ana")).token)!!
        val beto = s.cuentaDe(s.crearCuenta(NuevaCuenta("beto")).token)!!
        assertFailsWith<Problema> { s.llaves(beto, "ana") }                       // todavía no puso llaves
        s.ponerLlaves(ana, Llaves("c".repeat(40), "f".repeat(40)))
        assertEquals("c".repeat(40), s.llaves(beto, "ana").cifrar)
        val id = s.mandar(beto, "ana", SobreNuevo("cerrado", "firma"))
        val llegan = s.recibir(ana, 0)
        assertEquals(listOf("beto"), llegan.map { it.de.usuario })
        assertEquals("cerrado", llegan.single().sobre)
        assertEquals(0, s.recibir(ana, id).size)                                 // al confirmar, se borra
        assertEquals(0, s.recibir(ana, 0).size)
        s.bloquear(ana, "beto", true)
        assertFailsWith<Problema> { s.mandar(beto, "ana", SobreNuevo("x", "y")) }
    }

    @Test fun vistasNotasYAvisos() {
        val s = nuevo()
        val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("ana")).token)!!
        val beto = s.cuentaDe(s.crearCuenta(NuevaCuenta("beto")).token)!!
        s.subirPublicaciones(ana, listOf(pub("1", Vis.PUBLICA, 10)))
        val id = s.explorar(beto, null).single().id
        s.vio(beto, listOf(id, id)); s.vio(beto, listOf(id)); s.vio(ana, listOf(id))
        assertEquals(1, s.publicacion(null, id)!!.vistas)                        // una vez por cuenta, y la autora no cuenta
        s.seguir(beto, "ana")
        s.ponerNota(ana, NotaNueva("viendo Matrix"))
        assertEquals(listOf("ana"), s.notas(beto).map { it.persona.usuario })
        assertEquals(0, s.notas(ana).count { it.persona.usuario == "beto" })     // beto no tiene nota
        s.ponerNota(ana, NotaNueva(""))                                          // vacía: se quita
        assertEquals(0, s.notas(beto).size)
        assertEquals(1, s.avisos(ana, null).size)
        s.borrarAvisos(ana, s.avisos(ana, null).single().id)
        assertEquals(0, s.avisos(ana, null).size)
    }

    @Test fun etiquetasYGuardadas() {
        val s = nuevo()
        val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("ana")).token)!!
        val beto = s.cuentaDe(s.crearCuenta(NuevaCuenta("beto")).token)!!
        s.subirPublicaciones(ana, listOf(PublicacionSubida("1", Vis.PUBLICA, 10, "foto", buildJsonObject { put("texto", "Qué peli #Cine_Colombiano #matrix") })))
        assertEquals(1, s.conEtiqueta(beto, "#cine_colombiano", null).size)
        assertEquals(1, s.conEtiqueta(null, "MATRIX", null).size)
        assertEquals(0, s.conEtiqueta(null, "otra", null).size)
        val id = s.conEtiqueta(null, "matrix", null).single().id
        s.meGusta(beto, id, true); s.guardar(beto, id, true)
        assertEquals(1, s.verPerfil(null, "ana").megusta)
        // Las guardadas de beto: privadas hasta que él diga.
        assertFailsWith<Problema> { s.guardadasDe(ana, "beto") }
        s.subirPerfil(beto, PerfilSubido(partes = mapOf("guardadas" to Parte(Vis.PUBLICA, JsonPrimitive("")))))
        assertEquals(1, s.guardadasDe(ana, "beto").size)
    }

    @Test fun explorarConCalidad() {
        val s = nuevo()
        val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("ana")).token)!!
        val gente = (1..5).map { s.cuentaDe(s.crearCuenta(NuevaCuenta("persona$it")).token)!! }
        s.subirPublicaciones(ana, listOf(pub("buena", Vis.PUBLICA, System.currentTimeMillis() - 1000), pub("otra", Vis.PUBLICA, System.currentTimeMillis() - 2000)))
        val lista = s.explorar(gente[0], null)
        val buena = lista.first { it.datos["titulo"].toString().contains("buena") }.id
        gente.forEach { g -> s.vio(g, listOf(buena)); s.meGusta(g, buena, true); s.guardar(g, buena, true) }
        val otra = s.explorar(gente[0], null)
        assertTrue(otra.first { it.id == buena }.calidad > otra.first { it.id != buena }.calidad)
        assertTrue(otra.all { it.calidad in 0.0..3.0 })
    }

    @Test fun claveYUsuario() {
        val dir = Files.createTempDirectory("social").toFile()
        val buzon = mutableListOf<String>()
        var reloj = 1_000_000_000L
        val s = Social(Base(java.io.File(dir, "s.db").path), java.io.File(dir, "fotos"), ahora = { reloj }, cartero = { _, asunto, _ -> buzon += asunto })
        s.pedirCodigo("ana@correo.co")
        val a = s.entrar(Entrada("ana@correo.co", Regex("[0-9]{6}").find(buzon.last())!!.value, usuario = "ana", acepta = Social.POLITICA, mayorDeEdad = true))
        val ana = s.cuentaDe(a.token)!!
        assertFailsWith<Problema> { s.ponerClave(ana, CambioClave("corta")) }
        s.ponerClave(ana, CambioClave("una clave larga"), token = a.token)
        assertTrue(s.miCuenta(ana).conClave)
        assertFailsWith<Problema> { s.entrarConClave(EntradaClave("ana@correo.co", "otra cosa")) }
        assertEquals("ana", s.entrarConClave(EntradaClave("ANA@correo.co", "una clave larga")).usuario)
        assertFailsWith<Problema> { s.ponerClave(ana, CambioClave("nueva clave larga", actual = "mala")) }   // pide la actual
        s.ponerClave(ana, CambioClave("nueva clave larga", actual = "una clave larga"), token = a.token)
        // Usuario: primer cambio libre; el viejo queda reservado.
        reloj += 8L * 24 * 3600_000
        assertEquals("anita", s.cambiarUsuario(ana, CambioUsuario("anita")))
        assertFailsWith<Problema> { s.cambiarUsuario(ana, CambioUsuario("ana2")) }
        assertTrue(!s.usuarioLibre("ana"))
    }

    @Test fun avisoAlPublicar() {
        val s = nuevo()
        val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("ana")).token)!!
        val beto = s.cuentaDe(s.crearCuenta(NuevaCuenta("beto")).token)!!
        s.seguir(beto, "ana")
        s.subirPublicaciones(ana, listOf(pub("1", Vis.PUBLICA, System.currentTimeMillis())))
        s.subirPublicaciones(ana, listOf(pub("1", Vis.PUBLICA, System.currentTimeMillis()), pub("2", Vis.PUBLICA, System.currentTimeMillis())))
        assertEquals(1, s.avisos(beto, null).count { it.tipo == "publico" })     // una vez cada 6 horas
    }

    @Test fun moderacion() {
        val dir = Files.createTempDirectory("social").toFile()
        var reloj = 1_000_000_000_000L
        val s = Social(Base(java.io.File(dir, "s.db").path), java.io.File(dir, "fotos"), ahora = { reloj })
        val tokenAdmin = s.crearCuenta(NuevaCuenta("moderadora")).token
        val admin = s.cuentaDe(tokenAdmin)!!
        val malo = s.cuentaDe(s.crearCuenta(NuevaCuenta("malo")).token)!!
        val gente = (1..Moderacion.UMBRAL).map { s.cuentaDe(s.crearCuenta(NuevaCuenta("persona$it")).token)!! }
        val nueva = s.cuentaDe(s.crearCuenta(NuevaCuenta("recien")).token)
        reloj += 8L * 24 * 3600_000                                              // las de antes ya tienen más de 7 días
        val tardia = s.cuentaDe(s.crearCuenta(NuevaCuenta("tardia")).token)!!
        s.subirPublicaciones(malo, listOf(pub("1", Vis.PUBLICA, reloj)))
        val id = s.explorar(gente[0], null).single().id
        // Una cuenta recién hecha no tumba nada; UMBRAL que cuentan, sí (hasta revisar).
        s.reportar(tardia, NuevoReporte("publicacion", "$id", "spam"))
        gente.dropLast(1).forEachIndexed { i, c -> s.reportar(c, NuevoReporte("publicacion", "$id", if (i == 1) "acoso" else "spam")) }
        assertEquals(1, s.explorar(gente.last(), null).size)
        s.reportar(gente.last(), NuevoReporte("publicacion", "$id", "spam"))
        assertEquals(0, s.explorar(nueva, null).size)
        assertTrue(s.publicacion(malo, id) != null)                             // su autor la sigue viendo
        // Solo quien administra ve la cola y decide.
        assertFailsWith<Problema> { s.casos(gente[0]) }
        Base(java.io.File(dir, "s.db").path).let { base ->
            base.usar {
                cambiar("INSERT INTO roles VALUES(?, 'administrador', 'servidor', 0)", admin)
                cambiar("INSERT INTO mfa VALUES(?, '', 1, 0, -1)", admin)
                cambiar("UPDATE sesiones SET elevada_hasta = ? WHERE cuenta = ?", Long.MAX_VALUE, admin)
            }
            base.cerrar()
        }
        val caso = s.casos(admin, tokenAdmin).single()
        assertEquals(Moderacion.UMBRAL + 1, caso.personas); assertEquals(Moderacion.UMBRAL, caso.motivos["spam"])
        s.decidir(admin, Decision("publicacion", "$id", "falta", "spam"), tokenAdmin)
        assertNull(s.publicacion(malo, id))
        assertEquals("sancion", s.avisos(malo, null).first().tipo)
        assertEquals("fogon", s.avisos(malo, null).first().de.usuario)              // nunca quién moderó
        s.bloquear(malo, "moderadora", true)
        assertEquals("sancion", s.avisos(malo, null).first().tipo)                // bloquear a quien moderó no esconde la sanción
        s.bloquear(malo, "moderadora", false)
        // Escala: 2 puntos = 24 h sin escribir; 3 = 7 días suspendida; 4 = cerrada.
        repeat(1) { s.subirPublicaciones(malo, listOf(pub("2", Vis.PUBLICA, reloj))); val i = s.explorar(gente[0], null).single().id
            s.reportar(gente[0], NuevoReporte("publicacion", "$i", "spam"))
            s.decidir(admin, Decision("publicacion", "$i", "falta", "spam"), tokenAdmin) }
        assertFailsWith<Problema> { s.revisarSancion(malo, escribir = true) }
        s.revisarSancion(malo, escribir = false)                                // leer, sí
        assertEquals(2, s.estadoCuenta(malo).faltas.size)
        // Las faltas vencen a los 90 días.
        reloj += 91L * 24 * 3600_000
        assertEquals(0, s.estadoCuenta(malo).faltas.size)
        // Cierre a la primera por un menor en riesgo.
        s.subirPublicaciones(malo, listOf(pub("3", Vis.PUBLICA, reloj)))
        val i3 = s.explorar(gente[0], null).single().id
        s.reportar(gente[0], NuevoReporte("publicacion", "$i3", Moderacion.MENOR_EN_RIESGO))
        assertEquals(0, s.explorar(gente[1], null).size)                       // oculta al primer reporte
        s.decidir(admin, Decision("publicacion", "$i3", "falta", Moderacion.MENOR_EN_RIESGO), tokenAdmin)
        assertTrue(s.estadoCuenta(malo).cerrada)
        assertFailsWith<Problema> { s.verPerfil(null, "malo") }
    }

    @Test fun cajonGuardado() {
        val s = nuevo()
        val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("ana")).token)!!
        assertEquals(0, s.cajon(ana).cambio)
        val v1 = s.guardarCajon(ana, CajonCifrado("cifrado-1"), antes = 0)
        assertFailsWith<Problema> { s.guardarCajon(ana, CajonCifrado("viejo"), antes = 0) }   // otro teléfono subió antes
        val v2 = s.guardarCajon(ana, CajonCifrado("cifrado-2"), antes = v1)
        s.guardarLlave(ana, "llave-envuelta")
        assertEquals(CajonCifrado("cifrado-2", v2, "llave-envuelta"), s.cajon(ana))
    }

    @Test fun imagenes() {
        val s = nuevo()
        val yo = s.cuentaDe(s.crearCuenta(NuevaCuenta("dani")).token)!!
        assertFailsWith<Problema> { s.subirImagen(yo, "no es imagen".toByteArray()) }
        // Solo con la cabecera de JPEG no alcanza: tiene que ser una imagen de verdad.
        assertFailsWith<Problema> { s.subirImagen(yo, byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 9, 9)) }
        val img = s.subirImagen(yo, jpegDePrueba(conMetadatos = true))
        // Se guarda dibujada de nuevo: sin los metadatos (como la ubicación) que traía el archivo.
        val guardada = s.imagen(img.nombre)
        assertTrue(javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(guardada)) != null)
        assertFalse(String(guardada, Charsets.ISO_8859_1).contains("GPS-PRUEBA-UBICACION"))
        assertFailsWith<Problema> { s.imagen("../../etc/passwd") }
        s.borrarCuenta(yo)
        assertFailsWith<Problema> { s.imagen(img.nombre) }
    }

    @Test fun fotoSoloJpegChico() {
        val s = nuevo()
        val yo = s.cuentaDe(s.crearCuenta(NuevaCuenta("dani")).token)!!
        assertFailsWith<Problema> { s.subirFoto(yo, "no es imagen".toByteArray()) }
        assertFailsWith<Problema> { s.subirFoto(yo, ByteArray(500_000) { 0xFF.toByte() }) }
        s.subirFoto(yo, jpegDePrueba(conMetadatos = true))
        assertFalse(String(s.foto(null, "dani"), Charsets.ISO_8859_1).contains("GPS-PRUEBA-UBICACION"))
        assertTrue(s.verPerfil(null, "dani").persona.foto)
    }

    @Test fun porHttp() = testApplication {
        val s = nuevo()
        application { fogonSocial(s) }
        val r = client.post("/v1/cuentas") { contentType(ContentType.Application.Json); setBody("""{"correo":"","codigo":"","usuario":"eva","nombre":"Eva","acepta":"${Social.POLITICA}","mayorDeEdad":true}""") }
        assertEquals(HttpStatusCode.ServiceUnavailable, r.status)
        val token = s.crearCuenta(NuevaCuenta("eva")).token
        assertEquals(HttpStatusCode.Unauthorized, client.put("/v1/perfil") { contentType(ContentType.Application.Json); setBody("{}") }.status)
        assertEquals(HttpStatusCode.NoContent, client.put("/v1/perfil") {
            header("Authorization", "Bearer $token"); contentType(ContentType.Application.Json); setBody("""{"nombre":"Eva","bio":"Cine"}""")
        }.status)
        val perfil = client.get("/v1/perfiles/eva").bodyAsText()
        assertTrue("\"bio\":\"Cine\"" in perfil)
        assertEquals(HttpStatusCode.NotFound, client.get("/v1/perfiles/nadie").status)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.post("/v1/cuentas") { contentType(ContentType.Application.Json); setBody("no es json") }.status)
        // Las páginas web del enlace compartido y lo que necesita Android para abrir la app.
        val web = client.get("/@eva")
        assertEquals(HttpStatusCode.OK, web.status)
        assertTrue("@eva" in web.bodyAsText() && "Cine" in web.bodyAsText())
        assertEquals(HttpStatusCode.NotFound, client.get("/@nadie").status)
        assertTrue("io.github.fogonapps.cajon" in client.get("/.well-known/assetlinks.json").bodyAsText())
        assertEquals(HttpStatusCode.OK, client.get("/privacidad").status)
        // La política y los términos también en inglés, con el enlace al otro idioma.
        assertTrue("Privacy policy" in client.get("/privacy").bodyAsText() && "/privacidad" in client.get("/privacy").bodyAsText())
        assertTrue("Terms of use" in client.get("/terms").bodyAsText())
        assertTrue("/privacy" in client.get("/privacidad").bodyAsText())
    }

    @Test fun limiteDePeticiones() = testApplication {
        application { fogonSocial(nuevo()) }
        val estados = (1..125).map { client.get("/v1/estado") { header("Authorization", "Bearer x") }.status }
        assertEquals(120, estados.count { it == HttpStatusCode.OK })
        assertEquals(HttpStatusCode.TooManyRequests, estados.last())
    }

    /** Carga: sigue a 200 con 50 publicaciones cada uno (10.000), con me gusta y comentarios; la actividad debe salir rápido. */
    @Test fun cargaDeActividad() {
        val s = nuevo()
        val yo = s.cuentaDe(s.crearCuenta(NuevaCuenta("centro")).token)!!
        val amigos = (1..200).map { i -> s.cuentaDe(s.crearCuenta(NuevaCuenta("amigo$i")).token)!! }
        amigos.forEachIndexed { i, a ->
            s.seguir(a, "centro"); s.seguir(yo, "amigo${i + 1}")
            s.subirPublicaciones(a, (1..50).map { k -> pub("$k", if (k % 2 == 0) Vis.SEGUIDORES else Vis.PUBLICA, (i * 50L + k)) })
        }
        val primeras = s.actividad(yo, null)
        primeras.take(20).forEach { s.meGusta(yo, it.id, true) }
        primeras.take(10).forEach { s.comentar(yo, it.id, "bien") }   // una cuenta nueva: hasta 10 comentarios por hora
        val inicio = System.nanoTime()
        repeat(20) { s.actividad(yo, null) }
        val ms = (System.nanoTime() - inicio) / 1_000_000 / 20
        println("Feed siguiendo a 200 y 10.000 publicaciones: $ms ms por carga")
        assertEquals(40, s.actividad(yo, null).size)
        assertEquals(1, s.actividad(yo, null).first().megusta)
        assertTrue(ms < 300, "la actividad tardó $ms ms")
    }

    @Test fun pocasCuentasNuevasPorDiaDesdeLaMismaDireccion() {
        val l = LimiteDiario(10)
        val hoy = 1_000L * 86_400_000
        repeat(10) { assertTrue(l.pasa("nueva:1.2.3.4", hoy)) }
        assertFalse(l.pasa("nueva:1.2.3.4", hoy))                       // la undécima del día, no
        assertTrue(l.pasa("nueva:5.6.7.8", hoy))                        // otra dirección, sí
        assertTrue(l.pasa("nueva:1.2.3.4", hoy + 86_400_000))           // al día siguiente, de nuevo
    }
}

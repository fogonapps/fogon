package io.github.fogonapps.social

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class ClienteSocialTest {
    private val configuracion = ConfiguracionSocial("cajon", "Fabs", "ElCajon-Fogon")
    private fun cliente(
        token: String? = "credencial-de-prueba",
        permiso: () -> Boolean = { true },
        responder: (Request) -> Pair<Int, String>,
    ) = ClienteSocial(token, permiso, configuracion, OkHttpClient.Builder().addInterceptor { cadena ->
        val (codigo, texto) = responder(cadena.request())
        Response.Builder().request(cadena.request()).protocol(Protocol.HTTP_1_1)
            .code(codigo).message("prueba").body(texto.toResponseBody()).build()
    }.build(), { "Marca Modelo" })

    @Test fun escaparatesUsanRutasGlobalesYListaOrdenada() = runBlocking {
        val peticiones = mutableListOf<Request>()
        val c = cliente { p ->
            peticiones += p
            200 to when (p.url.encodedPath) {
                "/v1/cuenta/escaparates/cupo" -> """{"nivel":1,"gratis":3,"comprados":2,"total":5}"""
                "/v1/cuenta/escaparates/fotos" -> """[{"publicacion":42,"nombre":"foto"}]"""
                else -> """[{"tipo":"texto","datos":{"texto":"segundo"}},{"tipo":"texto","datos":{"texto":"primero"}}]"""
            }
        }
        val lista = c.escaparates("ana")
        assertEquals(5, c.cupoEscaparates().total)
        assertEquals(42L, c.fotosEscaparates().single().publicacion)
        c.guardarEscaparates(lista)
        assertEquals("/v1/perfil/ana/escaparates", peticiones.first().url.encodedPath)
        assertEquals("PUT", peticiones.last().method)
        assertEquals("/v1/cuenta/escaparates", peticiones.last().url.encodedPath)
        assertEquals(lista, JsonSocial.decodeFromString(kotlinx.serialization.builtins.ListSerializer(EscaparateSocial.serializer()), Buffer().also { peticiones.last().body!!.writeTo(it) }.readUtf8()))
    }

    @Test fun fotoPropiaSinPublicacionYNivelPublicoSonAditivos() {
        val foto = JsonSocial.decodeFromString(FotoEscaparate.serializer(), """{"nombre":"propia"}""")
        assertNull(foto.publicacion)
        val antiguo = """{"persona":{"usuario":"ana","nombre":"Ana"}}"""
        assertEquals(0, JsonSocial.decodeFromString(PerfilSocial.serializer(), antiguo).nivel)
        val actual = antiguo.dropLast(1) + ",\"nivel\":5}"
        assertEquals(5, JsonSocial.decodeFromString(PerfilSocial.serializer(), actual).nivel)
    }

    @Test fun pagosWompiUsanSesionYPropaganApagado() = runBlocking {
        val peticiones = mutableListOf<Request>()
        val c = cliente { peticion ->
            peticiones += peticion
            200 to if (peticion.method == "GET") """{"paquetes":[2000,5000,10000],"regalo":{"5000":500,"10000":1500}}"""
                else """{"referencia":"inventada","url":"https://checkout.wompi.co/p/?prueba"}"""
        }
        val oferta = c.paquetesWompi()
        assertEquals(listOf(2000,5000,10000), oferta.paquetes)
        assertEquals(500, oferta.regalo["5000"])
        assertEquals("https://checkout.wompi.co/p/?prueba", c.ordenWompi(2000))
        assertEquals("/v1/pagos/wompi/orden", peticiones[1].url.encodedPath)
        assertEquals("Bearer credencial-de-prueba", peticiones[1].header("Authorization"))
        assertEquals("""{"paquete":2000}""", Buffer().also { peticiones[1].body!!.writeTo(it) }.readUtf8())
        try {
            cliente { 503 to """{"error":"pagos_apagados"}""" }.paquetesWompi()
            fail("Debe propagar pagos apagados")
        } catch (e: ErrorSocial) { assertEquals(503, e.codigo); assertEquals("pagos_apagados", e.error) }
    }

    @Test fun conservaRutasCabecerasYCuerposDeAccesoYRespuestas() = runBlocking {
        val peticiones = mutableListOf<Request>()
        val cliente = cliente { peticion ->
            peticiones += peticion
            200 to if (peticion.url.encodedPath == "/v1/acceso") """{"usuario":"ana","token":"nuevo","idCuenta":"estable"}""" else ""
        }
        val acceso = cliente.entrar("ana@example.invalid", "123456", "ana", "Ana", "politica", "clave-de-prueba")
        cliente.comentar(42, "respuesta", 7)
        cliente.cerrarSesion("sesion con espacio")
        assertEquals("estable", acceso.idCuenta)
        val entrada = peticiones[0]
        assertEquals("/v1/acceso", entrada.url.encodedPath)
        assertEquals("POST", entrada.method)
        assertEquals("ElCajon-Fogon", entrada.header("User-Agent"))
        assertEquals("Marca Modelo", entrada.header("X-Fogon-Dispositivo"))
        assertEquals("Bearer credencial-de-prueba", entrada.header("Authorization"))
        assertNull(entrada.header("X-Fogon-App"))
        fun cuerpo(peticion: Request) = Buffer().also { peticion.body!!.writeTo(it) }.readUtf8()
        assertEquals("""{"correo":"ana@example.invalid","codigo":"123456","usuario":"ana","nombre":"Ana","acepta":"politica","mayorDeEdad":true,"clave":"clave-de-prueba"}""", cuerpo(entrada))
        assertEquals("/v1/publicaciones/42/comentarios", peticiones[1].url.encodedPath)
        assertEquals("""{"texto":"respuesta","padre":7}""", cuerpo(peticiones[1]))
        assertEquals("/v1/sesiones/sesion+con+espacio", peticiones[2].url.encodedPath)
        assertEquals("DELETE", peticiones[2].method)
    }

    @Test fun conservaErrorSemanticoYFechaDeBorrado() = runBlocking {
        val cliente = cliente { 403 to """{"error":"por_borrar","desde":123}""" }
        try {
            cliente.feed()
            fail("Debe propagar el error")
        } catch (error: ErrorSocial) {
            assertEquals(403, error.codigo)
            assertEquals("por_borrar", error.error)
            assertEquals(123L, error.desde)
        }
    }

    @Test fun politicaPendientePermiteGestionPeroNoFeed() = runBlocking {
        val rutas = mutableListOf<String>()
        val cliente = cliente(permiso = { false }) { peticion ->
            rutas += peticion.url.encodedPath
            200 to """{"usuario":"ana"}"""
        }
        cliente.miCuenta()
        try {
            cliente.feed()
            fail("Debe bloquear la red social")
        } catch (error: java.io.IOException) { assertEquals("politica_pendiente", error.message) }
        assertEquals(listOf("/v1/cuenta"), rutas)
    }

    @Test fun conservaDescriptorYCampoUltimoUsoDeSesion() {
        val sesion = JsonSocial.decodeFromString(SesionSocial.serializer(), """{"id":"s","ultimo_uso":456,"actual":true,"futuro":true}""")
        assertEquals(456L, sesion.ultimoUso)
        assertEquals("""{"id":"s","ultimo_uso":456,"actual":true}""", JsonSocial.encodeToString(SesionSocial.serializer(), sesion))
        assertEquals("io.github.fogonapps.cajon.datos.SesionSocial", SesionSocial.serializer().descriptor.serialName)
    }

    @Test fun dispositivoYContinuacionDeRegistroConservanReglas() {
        assertEquals("Samsung S24", nombreDispositivo("Samsung", "Samsung S24"))
        assertEquals("Marca Modelo", nombreDispositivo(" Marca\n", "Modelo\u0000"))
        assertEquals(60, nombreDispositivo("Marca", "x".repeat(80)).length)
        assertTrue(esCuentaNueva(ErrorSocial(409, "cuenta_nueva")))
        assertFalse(esCuentaNueva(ErrorSocial(400, "cuenta_nueva")))
        assertEquals(SesionMostrada("s", null, 1, 2, true), SesionSocial("s", " ", 1, 2, true).paraPantalla())
    }
}

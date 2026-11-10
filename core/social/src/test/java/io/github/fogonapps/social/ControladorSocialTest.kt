package io.github.fogonapps.social

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class ControladorSocialTest {
    private class RedPrueba(scope: CoroutineScope, private val transporte: ClienteSocial, sesion: SesionCuenta) :
        ControladorSocial(scope, sesion, "ana") {
        val errores = mutableListOf<java.io.IOException>()
        var olvidada = false
        override fun cliente() = transporte
        override suspend fun intentar(avisar: Boolean, marca: SesionCuenta.Marca, trabajo: suspend () -> Unit) =
            sesion.intentar(marca, trabajo) { errores += it }
        override fun avisar(evento: EventoSocial) {}
        override fun olvidarCuenta() { olvidada = true }
        override fun ponerCuenta(cuenta: MiCuenta) { _cuenta.value = cuenta }
        override fun alContarAvisos(cantidad: Int) {}
        override fun cargarCasos() = scope.launch {}
    }

    private fun cliente(responder: (Request) -> Pair<Int, String>) = ClienteSocial(
        "prueba", { true }, ConfiguracionSocial("cajon", "El Cajón", "ElCajon-Fogon"),
        OkHttpClient.Builder().addInterceptor { cadena ->
            val (codigo, cuerpo) = responder(cadena.request())
            Response.Builder().request(cadena.request()).protocol(Protocol.HTTP_1_1)
                .code(codigo).message("prueba").body(cuerpo.toResponseBody()).build()
        }.build(), { "Modelo" },
    )
    private val publicacion = """{"id":1,"autor":{"usuario":"ana"},"tipo":"foto","cuando":100,"visibilidad":"publica","datos":{}}"""

    @Test fun reaccionFallidaRevierteTodasLasListasYComentarioActualizaContador() = runTest {
        val rutas = mutableListOf<String>()
        val red = RedPrueba(this, cliente { peticion ->
            val ruta = peticion.url.encodedPath
            rutas += peticion.method + " " + ruta
            when {
                ruta.endsWith("/megusta") -> 500 to "{}"
                ruta.endsWith("/comentarios") && peticion.method == "GET" -> 200 to "[]"
                ruta.endsWith("/comentarios") -> 200 to ""
                else -> 200 to publicacion
            }
        }, SesionCuenta { "prueba" })
        red.cargarPublicacion(1).join()
        red.meGusta(red.buscarPublicacion(1)!!)
        assertTrue(red.vistas.value.getValue(1).yoMeGusta)
        coroutineContext[Job]!!.children.toList().joinAll()
        assertFalse(red.vistas.value.getValue(1).yoMeGusta)
        assertEquals(0, red.vistas.value.getValue(1).megusta)
        assertEquals(1, red.errores.size)
        red.comentar(1, " texto ", 9).join()
        assertEquals(1, red.vistas.value.getValue(1).comentarios)
        assertEquals(listOf("POST /v1/publicaciones/1/comentarios", "GET /v1/publicaciones/1/comentarios"), rutas.takeLast(2))
    }

    @Test fun respuestaTardiaNoCargaSesionesNiOlvidaLaSesionNueva() = runTest {
        val sesion = SesionCuenta { "prueba" }
        val red = RedPrueba(this, cliente { peticion ->
            sesion.invalidar()
            200 to if (peticion.method == "GET") """[{"id":"s","actual":true}]""" else ""
        }, sesion)
        red.cargarSesiones().join()
        assertNull(red.sesiones.value)
        red.cerrarSesion("s", actual = true).join()
        assertFalse(red.olvidada)
    }

    @Test fun equipoExcluyeOperacionesSimultaneasYDescartaResultadoInvalidado() = runTest {
        val sesion = SesionCuenta { "prueba" }
        val red = RedPrueba(this, cliente { 200 to "{}" }, sesion)
        val resultado = red.operarEquipo {
            assertNull(red.operarEquipo { "no debe ejecutarse" })
            sesion.invalidar()
            "respuesta antigua"
        }
        assertNull(resultado)
        assertFalse(red.equipoOcupado.value)
    }

    @Test fun avisosCarganSolicitudesAntesDeMarcarLeidos() = runTest {
        val rutas = mutableListOf<String>()
        val red = RedPrueba(this, cliente { peticion ->
            rutas += peticion.url.encodedPath
            200 to if (peticion.method == "GET") "[]" else ""
        }, SesionCuenta { "prueba" })
        red.cargarAvisos().join()
        assertEquals(listOf("/v1/avisos", "/v1/solicitudes", "/v1/avisos/leidos"), rutas)
        assertEquals(emptyList<AvisoSocial>(), red.avisos.value)
        assertEquals(0, red.sinLeer.value)
    }
}

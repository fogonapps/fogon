package io.github.fogonapps.social

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class ControladorCuentaSocialTest {
    private class Cuenta(scope: CoroutineScope, val transporte: ClienteSocial, val marca: SesionCuenta) :
        ControladorCuentaSocial(scope, marca, "ana") {
        val pasos = mutableListOf<String>()
        override fun revisarServidor() = scope.launch {}
        override fun permitirRed(permitir: Boolean) { pasos += "red:$permitir" }
        override fun guardarPolitica(version: String) { pasos += "politica" }
        override fun pausarPublicaciones() { pasos += "pausar" }
        override fun reanudarPublicaciones() { pasos += "reanudar" }
        override fun cliente() = transporte
        override fun clienteAcceso() = transporte
        override fun ahoraAcceso() = 100L
        override suspend fun intentar(avisar: Boolean, marca: SesionCuenta.Marca, trabajo: suspend () -> Unit) =
            sesion.intentar(marca, trabajo) { pasos += "error" }
        override fun avisar(evento: EventoSocial) { pasos += evento.name }
        override fun olvidarCuenta() { pasos += "olvidar" }
        override fun ponerCuenta(cuenta: MiCuenta) { pasos += "cuenta" }
        override fun alContarAvisos(cantidad: Int) {}
        override fun cargarCasos() = scope.launch {}
        override suspend fun entro(a: Acceso, nombreNuevo: String): Boolean { pasos += "entrar"; return true }
        override suspend fun llaveConClave(clave: String) { pasos += "abrir" }
        override suspend fun llaveNueva(clave: String) { pasos += "recuperar" }
    }
    private fun cliente(responder: (Request) -> Pair<Int, String>) = ClienteSocial(
        configuracion = ConfiguracionSocial("cajon", "El Cajón", "ElCajon-Fogon"),
        http = OkHttpClient.Builder().addInterceptor { cadena ->
            val (codigo, cuerpo) = responder(cadena.request())
            Response.Builder().request(cadena.request()).protocol(Protocol.HTTP_1_1)
                .code(codigo).message("prueba").body(cuerpo.toResponseBody()).build()
        }.build(), dispositivo = { "prueba" },
    )
    @Test fun codigoDeCuentaNuevaMantieneCorreoYNoAbreDatosPrivados() = runTest {
        val cuenta = Cuenta(this, cliente { 409 to """{"error":"cuenta_nueva"}""" }, SesionCuenta { "token" })
        cuenta.usarClave(" ANA@EXAMPLE.ORG ")
        cuenta.entrar(" 123456 ").join()
        assertEquals(PasoAcceso.USUARIO, cuenta.acceso.value.paso)
        assertEquals("ana@example.org", cuenta.acceso.value.correo)
        assertEquals("123456", cuenta.acceso.value.codigo)
        assertFalse(cuenta.acceso.value.ocupado)
        assertTrue(cuenta.pasos.isEmpty())
    }
    @Test fun respuestaTardiaNoCambiaCorreoPendiente() = runTest {
        val sesion = SesionCuenta { "token" }
        val cuenta = Cuenta(this, cliente { sesion.invalidar(); 200 to "" }, sesion)
        cuenta.cambiarCorreo("nuevo@example.org", "clave", "codigo").join()
        assertEquals("", cuenta.correoPendiente.value)
        assertFalse(cuenta.seguridadOcupada.value)
    }
    @Test fun borradoCierraSesionAntesDeOlvidarYAvisar() = runTest {
        val rutas = mutableListOf<String>()
        val cuenta = Cuenta(this, cliente { rutas += it.url.encodedPath; 200 to "" }, SesionCuenta { "token" })
        cuenta.borrarCuenta("clave", "codigo", "otro", "").join()
        assertEquals(listOf("/v1/cuenta/borrar", "/v1/salir"), rutas)
        assertEquals(listOf("olvidar", "CUENTA_BORRADA"), cuenta.pasos)
    }
    @Test fun falloDeBorradoConservaSesion() = runTest {
        val cuenta = Cuenta(this, cliente { 500 to "{}" }, SesionCuenta { "token" })
        cuenta.borrarCuenta("clave", "codigo", "otro", "").join()
        assertEquals(listOf("error"), cuenta.pasos)
    }
    @Test fun aceptarPoliticaGuardaAntesDeReanudarYRespuestaTardiaNoReactiva() = runTest {
        val sesion = SesionCuenta { "token" }
        var invalidar = false
        val cuenta = Cuenta(this, cliente { if (invalidar) sesion.invalidar(); 200 to "" }, sesion)
        cuenta.revisarPolitica()
        cuenta.posponerPolitica()
        assertEquals(listOf("red:false", "pausar"), cuenta.pasos)
        assertFalse(cuenta.hojaPolitica.value)
        cuenta.pasos.clear()
        cuenta.aceptarPolitica().join()
        assertEquals(listOf("politica", "red:true", "reanudar"), cuenta.pasos)
        cuenta.pasos.clear()
        invalidar = true
        cuenta.aceptarPolitica().join()
        assertTrue(cuenta.pasos.isEmpty())
    }
}

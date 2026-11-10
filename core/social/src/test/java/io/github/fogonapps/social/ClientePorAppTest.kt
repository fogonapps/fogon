package io.github.fogonapps.social

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class ClientePorAppTest {
    private fun cliente(app: String, peticiones: MutableList<Request>) = ClienteSocial(configuracion = ConfiguracionSocial(app, "App", "App-Fogon"),
        http = OkHttpClient.Builder().addInterceptor { cadena ->
            val p = cadena.request(); peticiones += p
            val texto = when {
                p.url.encodedPath.endsWith("/perfil") || p.url.encodedPath.endsWith("/revision") -> """{"revision":42}"""
                p.url.encodedPath.endsWith("/sin-leer") -> """{"n":0}"""
                p.url.encodedPath.endsWith("/acceso") || p.url.encodedPath.endsWith("/clave") -> """{"usuario":"ana","token":"prueba"}"""
                else -> "[]"
            }
            Response.Builder().request(p).protocol(Protocol.HTTP_1_1).code(200).message("prueba").body(texto.toResponseBody()).build()
        }.build(), dispositivo = { "Prueba" })
    private fun cuerpo(p: Request) = Buffer().also { p.body!!.writeTo(it) }.readUtf8()

    @Test fun forzudoAcotaContenidoYMantieneGestionGlobal() = runBlocking {
        val peticiones = mutableListOf<Request>(); val c = cliente("forzudo", peticiones)
        c.pedirCodigo("a@example.invalid"); c.entrar("a@example.invalid", "123456"); c.entrarConClave("a@example.invalid", "prueba")
        c.feed(); c.explorar(); c.guardados(); c.avisos(); c.sinLeer(); c.leidos(); c.comentarios(5)
        assertEquals(listOf("/v1/acceso/codigo", "/v1/acceso", "/v1/acceso/clave", "/v2/apps/forzudo/actividad",
            "/v2/apps/forzudo/explorar", "/v2/apps/forzudo/guardados", "/v2/apps/forzudo/avisos", "/v2/apps/forzudo/avisos/sin-leer", "/v2/apps/forzudo/avisos/leidos", "/v1/publicaciones/5/comentarios"), peticiones.map { it.url.encodedPath })
        peticiones.take(3).forEach { assertTrue(cuerpo(it).contains("\"app\":\"forzudo\"")) }
    }
    @Test fun cajonConservaPeticionesSinDeclararApp() = runBlocking {
        val peticiones = mutableListOf<Request>(); val c = cliente("cajon", peticiones)
        c.pedirCodigo("a@example.invalid"); c.feed(); c.subirPerfil(PerfilSubido())
        assertEquals("""{"correo":"a@example.invalid"}""", cuerpo(peticiones.first()))
        assertTrue(peticiones.all { it.url.encodedPath.startsWith("/v1/") && it.header("X-Fogon-App") == null })
    }
    @Test fun partesNoEnvianIdentidadLocalYEdicionExigeRevision() = runBlocking {
        val peticiones = mutableListOf<Request>(); val c = cliente("forzudo", peticiones)
        c.subirPerfilApp(CambioPerfilApp(partes = emptyMap()))
        assertEquals("""{"partes":{}}""", cuerpo(peticiones.last()))
        assertEquals(42L, c.revisionPerfil())
        c.subirPerfilApp(CambioPerfilApp(42, IdentidadPerfil(bio = "")))
        assertEquals("""{"revision":42,"identidad":{"bio":""}}""", cuerpo(peticiones.last()))
        try { c.subirPerfil(PerfilSubido()); fail("No reemplazar identidad desde otra app") } catch (_: IllegalStateException) { }
        try { c.subirPerfilApp(CambioPerfilApp(identidad = IdentidadPerfil(nombre = "Ana"))); fail("Falta revisión") } catch (_: IllegalArgumentException) { }
    }
}

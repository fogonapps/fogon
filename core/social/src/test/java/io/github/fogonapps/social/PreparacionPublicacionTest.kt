package io.github.fogonapps.social

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class PreparacionPublicacionTest {
    @Test fun recuperaPerfilAntesDelContenidoYRechazaSesionInvalidada() = runTest {
        val pasos = mutableListOf<String>()
        var pendiente = true
        var vigente = true
        val preparacion = PreparacionPublicacion { valida -> prepararSubidaPerfil(
            { pendiente },
            { pasos += "perfil"; pendiente = false; true },
            { pasos += "contenido"; vigente = false; true },
            valida,
        ) }
        assertFalse(preparacion.preparar { vigente })
        assertEquals(listOf("perfil", "contenido"), pasos)
        pasos.clear()
        assertFalse(preparacion.preparar { vigente })
        assertTrue(pasos.isEmpty())
    }

    @Test fun falloDePerfilImpideLeerContenido() = runTest {
        assertFalse(prepararSubidaPerfil({ true }, { false }, { error("No debe recuperar contenido") }, { true }))
    }

    @Test fun confirmaSoloTrasAmbasPeticionesYReintentaTrasInvalidacion() = runTest {
        var vigente = true
        var invalidar = true
        var confirmadas = setOf("antigua")
        val rutas = mutableListOf<String>()
        val http = OkHttpClient.Builder().addInterceptor { cadena ->
            rutas += cadena.request().url.encodedPath
            if (invalidar) vigente = false
            Response.Builder().request(cadena.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("prueba").body("".toResponseBody()).build()
        }.build()
        val cliente = ClienteSocial(configuracion = ConfiguracionSocial("cajon", "El Cajón", "ElCajon-Fogon"), http = http, dispositivo = { "prueba" })
        val aporte = object : AporteSocialApp {
            override suspend fun perfil() = PerfilSubido(nombre = "Ana")
            override fun publicaciones() = listOf(PublicacionSubida("nueva", "publica", 1, "foto", buildJsonObject {}))
        }
        val subidas = SubidasPerfil()
        suspend fun subir() = subirAporteSocial(aporte, subidas, { vigente }, { confirmadas }, { cliente }, { confirmadas = it })
        assertFalse(subir())
        assertEquals(listOf("/v1/perfil"), rutas)
        assertEquals(setOf("antigua"), confirmadas)
        vigente = true; invalidar = false
        assertTrue(subir())
        assertEquals(listOf("/v1/perfil", "/v1/perfil", "/v1/publicaciones/cambios"), rutas)
        assertEquals(setOf("nueva"), confirmadas)
        assertTrue(subir())
        assertEquals(3, rutas.size)
    }
}

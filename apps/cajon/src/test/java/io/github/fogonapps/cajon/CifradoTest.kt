package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.CajonCifrado
import io.github.fogonapps.cajon.datos.CajonIlegible
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.ErrorSocial
import io.github.fogonapps.cajon.datos.JsonCajon
import io.github.fogonapps.cajon.datos.Logica
import io.github.fogonapps.cajon.datos.Nube
import io.github.fogonapps.cajon.datos.Obra
import io.github.fogonapps.cajon.datos.huellaLlaveCajon
import io.github.fogonapps.cajon.datos.llaveParaRecuperacion
import io.github.fogonapps.cajon.datos.permiteReemplazarCajon
import io.github.fogonapps.cajon.datos.sincronizarCajonCifrado
import kotlinx.coroutines.test.runTest
import io.github.fogonapps.cajon.datos.Cifrado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NubeTest {
    @Test fun cajonCifradoConLaContrasena() {
        val llave = io.github.fogonapps.cajon.datos.Nube.llaveNueva()
        val envuelta = io.github.fogonapps.cajon.datos.Nube.envolver(llave, "una frase larga")
        assertEquals(llave, io.github.fogonapps.cajon.datos.Nube.desenvolver(envuelta, "una frase larga"))
        assertTrue(runCatching { io.github.fogonapps.cajon.datos.Nube.desenvolver(envuelta, "otra cosa") }.isFailure)
        val json = """{"obras":[{"id":"o1","tipo":"pelicula","titulo":"Matrix"}]}""".repeat(50)
        val sobre = io.github.fogonapps.cajon.datos.Nube.cerrar(json, llave)
        assertTrue("Matrix" !in sobre && sobre.length < json.length)          // cifrado y comprimido
        assertEquals(json, io.github.fogonapps.cajon.datos.Nube.abrir(sobre, llave))
        assertTrue(runCatching { io.github.fogonapps.cajon.datos.Nube.abrir(sobre, io.github.fogonapps.cajon.datos.Nube.llaveNueva()) }.isFailure)
    }

    @Test fun recuperarPorCodigoReemplazaElSobreViejoYDespuesSincronizaNormal() = runTest {
        val k1 = Nube.llaveNueva()
        val k2 = Nube.llaveNueva()
        val recuperada = llaveParaRecuperacion(null) { k2 }
        assertTrue(recuperada.reemplazar)
        val envuelta = Nube.envolver(recuperada.llave, "contraseña nueva de prueba")
        var servidor = CajonCifrado(
            Nube.cerrar(JsonCajon.encodeToString(Estado.serializer(), Estado(obras = listOf(Obra("vieja", "libro", "Anterior")))), k1),
            cambio = 10, llave = envuelta, // Guardar la llave en el servidor conserva el sobre cifrado con K1.
        )
        var autorizacion: String? = huellaLlaveCajon(recuperada.llave)
        var local = Estado(obras = listOf(Obra("local", "libro", "De este teléfono")))
        var uniones = 0
        var confirmaciones = 0
        val cambios = mutableListOf<Long>()
        suspend fun sincronizar() = sincronizarCajonCifrado(
            llave = recuperada.llave,
            autorizacion = { autorizacion },
            vigente = { true },
            descargar = { servidor },
            unir = { remoto -> uniones++; local = Logica.unir(local, remoto) },
            local = { local },
            guardar = { sobre, cambio ->
                cambios += cambio
                if (cambios.size == 1) {
                    assertTrue(permiteReemplazarCajon(k2, autorizacion))
                    servidor = servidor.copy(cambio = 11)
                    throw ErrorSocial(409, "cambio")
                }
                assertEquals(servidor.cambio, cambio)
                servidor = servidor.copy(sobre = sobre, cambio = cambio + 1)
            },
            confirmar = {
                if (permiteReemplazarCajon(k2, autorizacion)) { autorizacion = null; confirmaciones++ }
            },
        )
        assertTrue(sincronizar())
        assertEquals(listOf(10L, 11L), cambios)
        assertEquals(0, uniones)
        assertEquals(1, confirmaciones)
        assertEquals(null, autorizacion)
        val guardado = JsonCajon.decodeFromString(Estado.serializer(), Nube.abrir(servidor.sobre, k2))
        assertEquals(listOf("local"), guardado.obras.map { it.id })
        assertTrue(sincronizar())
        assertEquals(1, uniones)
        assertEquals(1, confirmaciones)
        assertEquals(listOf(10L, 11L, 12L), cambios)
    }

    @Test fun unSobreIlegibleSinAutorizacionParaLaLlaveActualNoSeSobrescribe() = runTest {
        val k1 = Nube.llaveNueva()
        val k2 = Nube.llaveNueva()
        val sobre = Nube.cerrar(JsonCajon.encodeToString(Estado.serializer(), Estado()), k1)
        for (autorizacion in listOf(null, huellaLlaveCajon(k1))) {
            var escrituras = 0
            var uniones = 0
            val resultado = runCatching {
                sincronizarCajonCifrado(
                    llave = k2,
                    autorizacion = { autorizacion },
                    vigente = { true },
                    descargar = { CajonCifrado(sobre, cambio = 3) },
                    unir = { uniones++ },
                    local = { Estado() },
                    guardar = { _, _ -> escrituras++ },
                    confirmar = { error("No debe consumir una autorización sin guardar") },
                )
            }
            assertTrue(resultado.exceptionOrNull() is CajonIlegible)
            assertEquals(0, escrituras)
            assertEquals(0, uniones)
        }
    }

    @Test fun unFalloDeRedConservaLaAutorizacionDeReemplazoHastaConfirmar() = runTest {
        val k1 = Nube.llaveNueva()
        val recuperada = llaveParaRecuperacion(null)
        var autorizacion: String? = huellaLlaveCajon(recuperada.llave)
        var servidor = CajonCifrado(Nube.cerrar(JsonCajon.encodeToString(Estado.serializer(), Estado()), k1), cambio = 4)
        var conRed = false
        suspend fun sincronizar() = sincronizarCajonCifrado(
            llave = recuperada.llave,
            autorizacion = { autorizacion },
            vigente = { true },
            descargar = { servidor },
            unir = { error("No se debe unir un sobre ilegible") },
            local = { Estado() },
            guardar = { sobre, cambio ->
                if (!conRed) throw java.io.IOException("sin red")
                servidor = servidor.copy(sobre = sobre, cambio = cambio + 1)
            },
            confirmar = { autorizacion = null },
        )
        assertTrue(runCatching { sincronizar() }.exceptionOrNull() is java.io.IOException)
        assertTrue(permiteReemplazarCajon(recuperada.llave, autorizacion))
        conRed = true
        assertTrue(sincronizar())
        assertEquals(null, autorizacion)
        assertTrue(runCatching { Nube.abrir(servidor.sobre, recuperada.llave) }.isSuccess)
    }

    @Test fun recuperarConLlaveLocalLaReutilizaSinAutorizarUnReemplazoNuevo() {
        val local = Nube.llaveNueva()
        val recuperada = llaveParaRecuperacion(local) { error("No debe crear otra llave") }
        assertTrue(local == recuperada.llave)
        assertEquals(false, recuperada.reemplazar)
        assertEquals(false, permiteReemplazarCajon(recuperada.llave, null))
    }

}

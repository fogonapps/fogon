package io.github.fogonapps.social

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SesionCuentaTest {
    @Test fun invalidacionYTokenDescartanResultadosAntiguos() = kotlinx.coroutines.test.runTest {
        var token: String? = "uno"
        val sesion = SesionCuenta { token }
        val marca = sesion.actual()
        assertTrue(sesion.vigente(marca))
        sesion.invalidar()
        assertFalse(sesion.vigente(marca))
        val otra = sesion.actual()
        token = "dos"
        assertFalse(sesion.vigente(otra))
        var errores = 0
        assertFalse(sesion.intentar(otra, { throw java.io.IOException() }, { errores++ }))
        assertEquals(0, errores)
    }
    @Test fun respuestaExitosaAntiguaTampocoContinuaLaPreparacionDeLaLlave() = runTest {
        var token = "sesión A de prueba"
        val sesiones = SesionCuenta { token }
        val respuesta = CompletableDeferred<Unit>()
        var preparaciones = 0
        val accesoA = async {
            val vigente = sesiones.intentar(trabajo = { respuesta.await() }, alError = { error("No debe fallar") })
            if (vigente) preparaciones++
        }
        runCurrent()
        sesiones.invalidar()
        token = "sesión B de prueba"
        respuesta.complete(Unit)
        accesoA.await()
        assertEquals(0, preparaciones)
        assertEquals("sesión B de prueba", token)
    }
}

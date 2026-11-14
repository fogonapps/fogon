package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EnlaceMasivoTest {
    @Test fun ocupadoEsperaHastaResponderSinCortarPorCincoFallos() = runTest {
        var pedidos = 0
        val resultado = reintentarOcupado {
            if (++pedidos <= 7) throw Ocupado()
            "enlazada"
        }
        assertEquals("enlazada", resultado)
        assertEquals(8, pedidos)
        assertEquals(255_000L, testScheduler.currentTime)
    }

    @Test fun respetaRetryAfter() = runTest {
        var pedidos = 0
        reintentarOcupado { if (++pedidos == 1) throw Ocupado(90) }
        assertEquals(90_000L, testScheduler.currentTime)
        assertEquals(90L, segundosDeEspera("90"))
        assertEquals(60L, segundosDeEspera("Thu, 01 Jan 1970 00:01:00 GMT", 0))
    }

    @Test fun sinRedYClaveMalaNoSeReintentan() = runTest {
        for (fallo in listOf(SinRed(), ClaveMala())) {
            var pedidos = 0
            try {
                reintentarOcupado { pedidos++; throw fallo }
                fail("Debía propagar el fallo")
            } catch (x: Exception) { assertSame(fallo, x) }
            assertEquals(1, pedidos)
        }
        assertEquals(0L, testScheduler.currentTime)
    }
    @Test fun ritmoMasivoYFalloPersistenAunqueUnaConsultaOpcionalLoOculte() = runTest {
        val enlace = EnlaceMasivo()
        kotlinx.coroutines.withContext(enlace) {
            repeat(6) { enlace.pausarTmdb() }
            assertEquals(2_100L, testScheduler.currentTime)
            val fallo = SinRed()
            enlace.fallar(fallo)
            try { enlace.comprobar(); fail("Debe detenerse") }
            catch (x: SinRed) { assertSame(fallo, x) }
        }
    }

}

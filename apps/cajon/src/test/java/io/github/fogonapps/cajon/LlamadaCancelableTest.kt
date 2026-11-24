package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.esperar
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Una consulta colgada (aquí, un DNS que no responde) no deja la búsqueda cargando: al vencer el tope de la fuente, la
 * corrutina se suelta enseguida y la búsqueda puede avisar y ofrecer volver a intentar.
 */
class LlamadaCancelableTest {
    @Test fun unDnsColgadoNoRetieneLaBusquedaMasAllaDeSuTope() = runBlocking {
        val suelto = CountDownLatch(1)
        val colgado = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> { suelto.await(10, TimeUnit.SECONDS); throw java.net.UnknownHostException(hostname) }
        }
        val cliente = OkHttpClient.Builder().dns(colgado).build()
        val inicio = System.nanoTime()
        val r = withTimeoutOrNull(300) { cliente.newCall(Request.Builder().url("https://servidor.invalid/v1/tmdb/search/movie").build()).esperar() }
        val ms = (System.nanoTime() - inicio) / 1_000_000
        suelto.countDown()
        assertNull(r)
        assertTrue("tardó $ms ms", ms < 2_000)
    }
}

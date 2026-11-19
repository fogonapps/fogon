package io.github.fogonapps.social

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.LocalDate
import java.util.concurrent.LinkedBlockingQueue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class EnviarContadoresTest {
    private class Telefono(app: String, clave: String) {
        var hoy = LocalDate.of(2026, 11, 16)
        val contadores = Contadores(app, { "3.0" }, { "" }, { true }, { hoy })
        init {
            repeat(3) { contadores.contar(clave) }
            hoy = hoy.plusDays(1)
        }
    }

    private data class Solicitud(val metodo: String, val ruta: String, val cabeceras: Map<String, List<String>>, val cuerpo: String)

    private val peticiones = LinkedBlockingQueue<Solicitud>()
    private val respuestas = LinkedBlockingQueue<Int>()
    private val servidor = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val aplicacion get() = RuntimeEnvironment.getApplication()

    @Before fun abrirServidor() {
        servidor.createContext("/") { intercambio ->
            intercambio.use {
                peticiones.add(Solicitud(it.requestMethod, it.requestURI.toString(), it.requestHeaders.toMap(),
                    it.requestBody.bufferedReader().readText()))
                // Cerrar sin respuesta simula una desconexión después de recibir el cuerpo.
                val codigo = respuestas.poll() ?: return@use
                it.sendResponseHeaders(codigo, -1)
            }
        }
        servidor.start()
    }

    @After fun cerrarServidor() {
        servidor.stop(0)
        ReflectionHelpers.setStaticField(Contadores::class.java, "instancia", null)
    }

    @Test fun aceptaYBorraTrasUnSoloPostAnonimo() {
        val origen = Telefono("cajon", "tienda.abrir")
        val esperado = origen.contadores.verSumas()
        respuestas.add(200)
        ejecutar(origen.contadores)
        assertEquals(esperado, comprobarPost("cajon", "tienda.abrir"))
        assertTrue(origen.contadores.verSumas().dias.isEmpty())
    }

    @Test fun desconexionConservaElCuerpoYReintentaConElMismoLoteAlDiaSiguiente() {
        val origen = Telefono("cajon", "tienda.abrir")
        ejecutar(origen.contadores)
        val primero = comprobarPost("cajon", "tienda.abrir")
        assertEquals(primero, origen.contadores.verSumas())
        ejecutar(origen.contadores)
        assertTrue(peticiones.isEmpty())
        origen.hoy = origen.hoy.plusDays(1)
        respuestas.add(200)
        ejecutar(origen.contadores)
        assertEquals(primero, comprobarPost("cajon", "tienda.abrir"))
        assertTrue(origen.contadores.verSumas().dias.isEmpty())
    }

    @Test fun descartaTras400() {
        val origen = Telefono("cajon", "tienda.abrir")
        respuestas.add(400)
        ejecutar(origen.contadores)
        comprobarPost("cajon", "tienda.abrir")
        origen.hoy = origen.hoy.plusDays(1)
        ejecutar(origen.contadores)
        assertTrue(origen.contadores.verSumas().dias.isEmpty())
        assertTrue(peticiones.isEmpty())
    }

    @Test fun alcanciaEnviaDirecto() {
        val alcancia = Telefono("alcancia", "alcancia.meta_creada").contadores
        respuestas.add(200)
        ejecutar(alcancia)
        comprobarPost("alcancia", "alcancia.meta_creada")
        assertTrue(alcancia.verSumas().dias.isEmpty())
    }

    @Test fun alcanciaApagadaNoEnvia() {
        val alcancia = Telefono("alcancia", "alcancia.meta_creada").contadores
        alcancia.permitir(false)
        ejecutar(alcancia)
        assertTrue(peticiones.isEmpty())
    }

    private fun ejecutar(contadores: Contadores) {
        ReflectionHelpers.setStaticField(Contadores::class.java, "instancia", contadores)
        val trabajo = TestListenableWorkerBuilder<EnviarContadores>(aplicacion)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                    EnviarContadores(aplicacion, workerParameters, "http://127.0.0.1:${servidor.address.port}")
            }).build()
        assertEquals(ListenableWorker.Result.success(), runBlocking { trabajo.doWork() })
    }

    private fun comprobarPost(app: String, clave: String): LoteContadores {
        val peticion = requireNotNull(peticiones.poll())
        assertTrue(peticiones.isEmpty())
        assertEquals("POST", peticion.metodo)
        assertEquals("/v1/contadores", peticion.ruta)
        assertFalse(peticion.cabeceras.keys.any { it.equals("Authorization", true) || it.equals("Cookie", true) })
        val lote = Json.decodeFromString<LoteContadores>(peticion.cuerpo)
        assertEquals(LoteContadores(lote.lote, app, "3.0", listOf(DiaContadores("2026-11-16", mapOf(clave to 3)))), lote)
        assertEquals(Json.encodeToString(lote), peticion.cuerpo)
        return lote
    }
}

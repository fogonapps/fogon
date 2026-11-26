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

class TiendaPlayTest {
    private val configuracion = ConfiguracionSocial("cajon", "Fabs", "ElCajon-Fogon")
    private val catalogo = """{"productos":[{"id":"chispas_500","chispas":500},{"id":"chispas_1000","chispas":1000},
        {"id":"apoyo_fogon","chispas":5000,"unico":true}],"cuenta":"opaco"}"""

    /** Play simulado: precios, compras sin cerrar y lo que se cerró (en orden, junto con lo que pidió el servidor). */
    private class PlayFalso(val registro: MutableList<String>) : PasarelaPlay {
        override val paquete = "io.github.fogonapps.cajon"
        var precios: Map<String, String>? = mapOf("chispas_500" to "COP 5.000", "apoyo_fogon" to "COP 50.000")
        val pendientes = mutableListOf<CompraPlay>()
        override fun escuchar(receptor: (ResultadoPlay) -> Unit) {}
        override suspend fun precios(ids: List<String>) = precios
        override suspend fun comprar(actividad: android.app.Activity, producto: String, cuenta: String): ResultadoPlay? = null
        override suspend fun sinCerrar() = pendientes.toList()
        override suspend fun cerrar(token: String, unico: Boolean): Boolean {
            registro += "cerrar:$token:$unico"
            pendientes.removeAll { it.token == token }
            return true
        }
    }

    private fun escenario(servidor: (Request) -> Pair<Int, String>, prueba: suspend (TiendaPlay, PlayFalso, MutableList<String>, MutableList<Int>) -> Unit) = runBlocking {
        val registro = mutableListOf<String>()
        val saldos = mutableListOf<Int>()
        val http = OkHttpClient.Builder().addInterceptor { cadena ->
            val p = cadena.request()
            if (p.method == "POST") registro += "servidor:" + Buffer().also { p.body!!.writeTo(it) }.readUtf8()
            val (codigo, texto) = servidor(p)
            Response.Builder().request(p).protocol(Protocol.HTTP_1_1).code(codigo).message("prueba").body(texto.toResponseBody()).build()
        }.build()
        val cliente = ClienteSocial("credencial", { true }, configuracion, http, { "Marca Modelo" })
        val play = PlayFalso(registro)
        prueba(TiendaPlay(this, play, { cliente }) { saldos += it }, play, registro, saldos)
    }

    private fun normal(p: Request): Pair<Int, String> =
        if (p.method == "GET") 200 to catalogo else 200 to """{"estado":"acreditada","chispas":2500}"""

    @Test fun cargaElCatalogoDelServidorConElPrecioDeGoogle() = escenario(::normal) { tienda, play, _, _ ->
        tienda.cargar().join()
        // chispas_1000 no tiene precio en Play (no creado en la consola): no se muestra.
        assertEquals(listOf(PaquetePlay("chispas_500", 500, 0, false, "COP 5.000"), PaquetePlay("apoyo_fogon", 5000, 0, true, "COP 50.000")),
            tienda.paquetes.value)
        play.precios = null
        tienda.cargar().join()
        assertEquals(emptyList<PaquetePlay>(), tienda.paquetes.value)
    }

    @Test fun servidorApagadoNoMuestraPaquetes() = escenario({ 503 to """{"error":"pagos_apagados"}""" }) { tienda, _, _, _ ->
        tienda.cargar().join()
        assertEquals(emptyList<PaquetePlay>(), tienda.paquetes.value)
    }

    @Test fun consumeSoloDespuesDeQueElServidorAcredita() = escenario(::normal) { tienda, _, registro, saldos ->
        tienda.recibir(ResultadoPlay.Compras(listOf(CompraPlay("chispas_500", "tok1", pendiente = false))))
        assertEquals(listOf("""servidor:{"paquete":"io.github.fogonapps.cajon","producto":"chispas_500","token":"tok1"}""", "cerrar:tok1:false"), registro)
        assertEquals(listOf(2500), saldos)
        assertEquals(EstadoPlay.Acreditada(2500), tienda.estado.value)
    }

    @Test fun apoyoSeReconoceEnVezDeConsumirse() = escenario(::normal) { tienda, _, registro, _ ->
        tienda.recibir(ResultadoPlay.Compras(listOf(CompraPlay("apoyo_fogon", "tok2", pendiente = false))))
        assertEquals("cerrar:tok2:true", registro.last())
    }

    @Test fun sinServidorNoConsumeYSeReintentaAlAbrir() {
        var caido = true
        escenario({ p -> if (caido && p.method == "POST") 500 to "{}" else normal(p) }) { tienda, play, registro, saldos ->
            val compra = CompraPlay("chispas_500", "tok3", pendiente = false)
            play.pendientes += compra
            tienda.recibir(ResultadoPlay.Compras(listOf(compra)))
            assertEquals(EstadoPlay.Error("conexion"), tienda.estado.value)
            assertTrue(registro.none { it.startsWith("cerrar") })
            assertTrue(saldos.isEmpty())
            caido = false
            tienda.revisar().join()
            assertEquals("cerrar:tok3:false", registro.last())
            assertEquals(listOf(2500), saldos)
            assertTrue(play.pendientes.isEmpty())
        }
    }

    @Test fun pendienteNoVaAlServidorNiSeConsume() = escenario(::normal) { tienda, _, registro, _ ->
        tienda.recibir(ResultadoPlay.Compras(listOf(CompraPlay("chispas_500", "tok4", pendiente = true))))
        assertEquals(EstadoPlay.Pendiente, tienda.estado.value)
        assertTrue(registro.isEmpty())
    }

    @Test fun rechazadaNoSeConsume() = escenario({ p -> if (p.method == "POST") 409 to """{"error":"compra_ajena"}""" else normal(p) }) { tienda, _, registro, _ ->
        tienda.recibir(ResultadoPlay.Compras(listOf(CompraPlay("chispas_500", "tok5", pendiente = false))))
        assertEquals(EstadoPlay.Error("rechazada"), tienda.estado.value)
        assertTrue(registro.none { it.startsWith("cerrar") })
    }

    @Test fun revisarSaltaElApoyoYaReconocido() = escenario(::normal) { tienda, play, registro, _ ->
        play.pendientes += CompraPlay("apoyo_fogon", "tok6", pendiente = false, reconocida = true)
        tienda.revisar().join()
        assertTrue(registro.isEmpty())
        tienda.recibir(ResultadoPlay.YaTenia)
        assertEquals(EstadoPlay.Error("ya_tenia"), tienda.estado.value)
    }

    @Test fun canceladaYFallosSeDicen() = escenario(::normal) { tienda, _, _, _ ->
        tienda.recibir(ResultadoPlay.Cancelada)
        assertEquals(EstadoPlay.Error("cancelada"), tienda.estado.value)
        tienda.recibir(ResultadoPlay.NoDisponible)
        assertEquals(EstadoPlay.Error("no_disponible"), tienda.estado.value)
        tienda.visto()
        assertEquals(EstadoPlay.Libre, tienda.estado.value)
    }
}

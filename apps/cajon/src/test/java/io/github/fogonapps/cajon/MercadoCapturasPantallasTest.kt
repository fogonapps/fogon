package io.github.fogonapps.cajon

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.res.stringResource
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.social.ui.TiendaTemas
import org.junit.Assert.assertEquals
import io.github.fogonapps.social.ui.R as SocialR
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import coil3.EventListener
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import io.github.fogonapps.cajon.ui.interfazSocial
import io.github.fogonapps.identidad.Apariencia
import io.github.fogonapps.identidad.Estilo
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.Paleta
import io.github.fogonapps.social.Acceso
import io.github.fogonapps.social.ClienteSocial
import io.github.fogonapps.social.ControladorCuentaSocial
import io.github.fogonapps.social.EventoSocial
import io.github.fogonapps.social.MiCuenta
import io.github.fogonapps.social.SesionCuenta
import io.github.fogonapps.social.ui.InterfazSocial
import io.github.fogonapps.social.ui.Mercado
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@OptIn(coil3.annotation.DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w400dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MercadoCapturasPantallasTest {
    @get:Rule val regla = createComposeRule()
    private val tareas = CoroutineScope(Job())

    @After fun cerrar() {
        tareas.cancel()
        SingletonImageLoader.reset()
    }

    @Test fun oscuro() = capturar(Estilo.GRAFITO, "oscuro")
    @Test fun claro() = capturar(Estilo.CLARO, "claro")

    @Test
    @Config(qualifiers = "en-w400dp-h800dp-xhdpi")
    fun tiendaDePerfilCubreLaVentana() {
        val app = ApplicationProvider.getApplicationContext<CajonApp>()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        val cuenta = Cuenta(tareas, ClienteSocial(configuracion = app.avisosConfiguracion, http = servidor()))
        val vm = CajonViewModel(app)
        val titulo = app.getString(SocialR.string.social_temas_tienda)
        regla.setContent {
            FogonTema(Paleta.Cajon, Apariencia(estilo = Estilo.GRAFITO)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    BotonRiso(stringResource(R.string.empezar), {})
                }
                val adaptador = interfazSocial(vm)
                TiendaTemas(object : InterfazSocial by adaptador {
                    override val red = cuenta
                }) {}
            }
        }
        regla.onNodeWithText(titulo).assertIsDisplayed()
        val ventana = regla.onNode(hasAnyDescendant(hasText("Start")) and androidx.compose.ui.test.isRoot()).fetchSemanticsNode().boundsInWindow
        val dialogo = regla.onNode(isDialog() and hasAnyDescendant(hasText(titulo))).fetchSemanticsNode().boundsInWindow
        assertEquals(ventana.width, dialogo.width, 1f)
        assertEquals(ventana.height, dialogo.height, 1f)
        guardar("cajon-tienda-perfil-sin-barra", "en", titulo)
    }

    private fun capturar(estilo: Estilo, variante: String) {
        val app = ApplicationProvider.getApplicationContext<CajonApp>()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        val clienteHttp = servidor()
        val cargadas = ConcurrentHashMap<String, Int>()
        SingletonImageLoader.setUnsafe(ImageLoader.Builder(app)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { clienteHttp })) }
            .eventListener(object : EventListener() {
                override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                    cargadas.merge(request.data.toString(), 1, Int::plus)
                }
            })
            .build())
        fun descargas(ruta: String) = cargadas[ClienteSocial.SERVIDOR + ruta] ?: 0
        val cuenta = Cuenta(tareas, ClienteSocial(configuracion = app.avisosConfiguracion, http = clienteHttp))
        val vm = CajonViewModel(app)
        regla.setContent {
            FogonTema(Paleta.Cajon, Apariencia(estilo = estilo)) {
                val adaptador = interfazSocial(vm)
                val interfaz = object : InterfazSocial by adaptador {
                    override val red = cuenta
                    override fun cabeceras(): Map<String, String> = emptyMap()
                }
                Mercado(interfaz) {}
            }
        }
        regla.waitUntil(10_000) {
            regla.onAllNodes(hasText("Destacados")).fetchSemanticsNodes().isNotEmpty() &&
                regla.onAllNodes(hasText("Órbita")).fetchSemanticsNodes().isNotEmpty() &&
                regla.onAllNodes(hasText("150 chispas")).fetchSemanticsNodes().isNotEmpty()
        }
        regla.onNodeWithText("Destacados").assertIsDisplayed()
        regla.waitUntil(10_000) { descargas("/v1/mercado/1/imagen") >= 2 && descargas("/v1/mercado/2/imagen") >= 1 }
        guardar("mercado-portada", variante, "Destacados")
        regla.onNodeWithText("Órbita").assertIsDisplayed().performClick()
        regla.onNodeWithText("Te faltan 450 chispas").assertIsDisplayed()
        regla.waitUntil("Marco cargado en la ficha", 10_000) { descargas("/v1/mercado/2/imagen") >= 2 }
        guardar("mercado-ficha", variante, "Te faltan 450 chispas")
        val faltante = regla.onNodeWithText("Te faltan 450 chispas")
        if (BuildConfig.TIENDA_PLAY) {
            faltante.assertHasNoClickAction()
        } else {
            faltante.performClick()
            regla.onNodeWithText("Conseguir chispas").assertIsDisplayed()
        }
    }

    private fun guardar(nombre: String, variante: String, texto: String) {
        val imagen = regla.onNode(isDialog() and hasAnyDescendant(hasText(texto))).captureToImage().asAndroidBitmap()
        val carpeta = File("build/reports/capturas-pantallas").apply { mkdirs() }
        val archivo = File(carpeta, "$nombre-$variante.png")
        archivo.outputStream().use { check(imagen.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    private fun servidor(): OkHttpClient {
        val aurora = """{"id":1,"creador":"fogon","nombre":"Aurora","tipo":"banner","precio":400,"estado":"aprobado","oficial":true}"""
        val orbita = """{"id":2,"creador":"luna","nombre":"Órbita","tipo":"marco","precio":600,"estado":"aprobado"}"""
        val banner = arte(Color.rgb(34, 49, 108), Color.rgb(244, 144, 100))
        val marco = arte(Color.TRANSPARENT, Color.rgb(71, 173, 154))
        return OkHttpClient.Builder().addInterceptor { cadena ->
            val solicitud = cadena.request()
            val ruta = solicitud.url.encodedPath
            val cuerpo = when (ruta) {
                "/v1/mercado" -> {
                    check(solicitud.url.queryParameter("tipo") == "")
                    val articulos = if (solicitud.url.queryParameter("oficial") == "true") aurora else "$aurora,$orbita"
                    """{"articulos":[$articulos]}""".toResponseBody()
                }
                "/v1/pagos/wompi/paquetes" -> """{"paquetes":[1000]}""".toResponseBody()
                "/v1/tienda/temas" -> """{"chispas":150}""".toResponseBody()
                "/v1/mercado/1/imagen" -> banner.toResponseBody("image/png".toMediaType())
                "/v1/mercado/2/imagen" -> marco.toResponseBody("image/png".toMediaType())
                else -> return@addInterceptor respuesta(cadena, 404).body("{}".toResponseBody()).build()
            }
            respuesta(cadena, 200).body(cuerpo).build()
        }.build()
    }

    private fun respuesta(cadena: Interceptor.Chain, codigo: Int) = Response.Builder()
        .request(cadena.request()).protocol(Protocol.HTTP_1_1).code(codigo).message("prueba")

    private fun arte(fondo: Int, tinta: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        val lienzo = Canvas(bitmap)
        val pintura = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tinta }
        lienzo.drawColor(fondo)
        if (fondo == Color.TRANSPARENT) {
            pintura.style = Paint.Style.STROKE
            pintura.strokeWidth = 20f
        }
        lienzo.drawCircle(96f, 96f, 81f, pintura)
        return ByteArrayOutputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            it.toByteArray()
        }
    }

    private class Cuenta(scope: CoroutineScope, private val transporte: ClienteSocial) :
        ControladorCuentaSocial(scope, SesionCuenta { null }, "ana") {
        override fun cliente() = transporte
        override fun clienteAcceso() = transporte
        override suspend fun intentar(avisar: Boolean, marca: SesionCuenta.Marca, trabajo: suspend () -> Unit) =
            sesion.intentar(marca, trabajo) {}
        override fun revisarServidor() = Job().apply { complete() }
        override fun cargarCasos() = Job().apply { complete() }
        override fun permitirRed(permitir: Boolean) {}
        override fun guardarPolitica(version: String) {}
        override fun pausarPublicaciones() {}
        override fun reanudarPublicaciones() {}
        override fun avisar(evento: EventoSocial) {}
        override fun olvidarCuenta() {}
        override fun ponerCuenta(cuenta: MiCuenta) {}
        override fun alContarAvisos(cantidad: Int) {}
        override suspend fun entro(a: Acceso, nombreNuevo: String) = false
        override suspend fun llaveConClave(clave: String) {}
        override suspend fun llaveNueva(clave: String) {}
    }
}

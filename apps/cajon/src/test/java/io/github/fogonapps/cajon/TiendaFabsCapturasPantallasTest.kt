package io.github.fogonapps.cajon

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.fogonapps.cajon.ui.Figtree
import io.github.fogonapps.cajon.ui.Fraunces
import io.github.fogonapps.cajon.ui.interfazSocial
import io.github.fogonapps.identidad.Apariencia
import io.github.fogonapps.identidad.Estilo
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.Paleta
import io.github.fogonapps.identidad.Trama
import io.github.fogonapps.perfil.BannerPerfil
import io.github.fogonapps.perfil.BannersGratis
import io.github.fogonapps.perfil.MuestraMarco
import io.github.fogonapps.perfil.PROPORCION_PORTADA
import io.github.fogonapps.perfil.Perfil
import io.github.fogonapps.perfil.PerfilVista
import io.github.fogonapps.perfil.TemasPerfil
import io.github.fogonapps.perfil.nombreBanner
import io.github.fogonapps.social.Acceso
import io.github.fogonapps.social.ClienteSocial
import io.github.fogonapps.social.ControladorCuentaSocial
import io.github.fogonapps.social.EventoSocial
import io.github.fogonapps.social.MiCuenta
import io.github.fogonapps.social.SesionCuenta
import io.github.fogonapps.social.ui.ContenidoLoQuiero
import io.github.fogonapps.social.ui.InterfazSocial
import io.github.fogonapps.social.ui.TiendaTemas
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * El catálogo oficial de Fabs: el perfil con cada colección, la tienda con su vitrina, el probador y «Lo quiero».
 * Deja las capturas en build/reports/capturas-pantallas/fabs-*.png.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w400dp-h860dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TiendaFabsCapturasPantallasTest {
    @get:Rule val regla = createComposeRule()
    private val tareas = CoroutineScope(Job())

    @After fun cerrar() { tareas.cancel() }

    @Composable
    private fun Tema(estilo: Estilo = Estilo.CLARO, contenido: @Composable () -> Unit) =
        FogonTema(Paleta.Cajon, Apariencia(estilo = estilo), letraTitulos = Fraunces, letraTexto = Figtree) { contenido() }

    @Test fun enFabsSoloSeOfreceElCatalogoNuevo() {
        assertTrue(Trama.apagada)
        assertTrue(TemasPerfil.seOfrece("vinilo-funda")); assertTrue(TemasPerfil.seOfrece("banner:fabs-papel"))
        assertFalse(TemasPerfil.seOfrece("nieve")); assertFalse(TemasPerfil.seOfrece("riso-rosa"))
        assertFalse(TemasPerfil.admiteColor("fabs-papel")); assertTrue(TemasPerfil.admiteColor("riso-rosa"))
        assertTrue(TemasPerfil.COLECCIONES.flatMap { it.articulos }.all { TemasPerfil.PRECIOS.getValue(it) > 0 })
    }

    @Test fun perfilConCadaColeccion() {
        var actual by androidx.compose.runtime.mutableStateOf(TemasPerfil.COLECCIONES.first())
        regla.setContent {
            val c = actual
            androidx.compose.runtime.key(c.id) {
                Tema {
                    val vista = PerfilVista("Valeria Ortiz", "valeria", "", "banner:${c.banners.first()}", "Cine de los cuarenta y discos de segunda.",
                        emptyList(), false, "yo", tema = c.fondo, marco = c.marco)
                    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Perfil(vista, 0, {}, portada = { BannerPerfil(c.banners.first(), Modifier.matchParentSize()) },
                            foto = { Box(Modifier.size(96.dp).background(androidx.compose.ui.graphics.Color(0xFF8A9A8C), androidx.compose.foundation.shape.CircleShape)) },
                            enlace = {}, acciones = { io.github.fogonapps.identidad.BotonRiso("Seguir", {}, Modifier.weight(1f)) },
                            despuesAcciones = {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    (c.banners + c.animado).forEach { BannerPerfil(it, Modifier.fillMaxWidth().aspectRatio(PROPORCION_PORTADA)) }
                                }
                            })
                    }
                }
            }
        }
        TemasPerfil.COLECCIONES.forEach { c ->
            actual = c
            regla.waitForIdle()
            regla.mainClock.advanceTimeBy(1_200)
            guardar(regla.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap(), "fabs-perfil-${c.id}")
        }
    }

    @Test fun bannersAnimadosYGratis() {
        regla.setContent {
            Tema {
                val f = FogonTema.actual
                Column(Modifier.fillMaxSize().background(f.fondo).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BannersGratis({}, emptyList(), portada = "banner:fabs-papel")
                    TemasPerfil.COLECCIONES.forEach { c ->
                        Text(nombreBanner(c.animado), color = f.texto)
                        BannerPerfil(c.animado, Modifier.fillMaxWidth().aspectRatio(PROPORCION_PORTADA))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TemasPerfil.COLECCIONES.forEach { MuestraMarco(it.marco, Modifier.weight(1f)) } }
                }
            }
        }
        regla.mainClock.advanceTimeBy(900)
        guardar(regla.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap(), "fabs-animados-y-gratis")
    }

    @Test fun tiendaVitrinaProbadorYLoQuiero() = tiendaVitrinaYProbador(Estilo.CLARO, "")
    @Test fun tiendaVitrinaYProbadorOscuro() = tiendaVitrinaYProbador(Estilo.GRAFITO, "-oscuro")

    private fun tiendaVitrinaYProbador(estilo: Estilo, sufijo: String) {
        val app = ApplicationProvider.getApplicationContext<CajonApp>()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        val cuenta = Cuenta(tareas, ClienteSocial(configuracion = app.avisosConfiguracion, http = servidor()))
        val vm = CajonViewModel(app)
        regla.setContent {
            Tema(estilo) {
                TiendaTemas(object : InterfazSocial by interfazSocial(vm) { override val red = cuenta }) {}
            }
        }
        regla.waitUntil(10_000) { regla.onAllNodes(hasText("700 chispas")).fetchSemanticsNodes().isNotEmpty() }
        regla.mainClock.advanceTimeBy(900)
        val vitrina = TemasPerfil.coleccionDeLaSemana()
        guardar(regla.onNode(isDialog() and hasAnyDescendant(hasText("COLECCIÓN DE LA SEMANA"))).captureToImage().asAndroidBitmap(), "fabs-tienda-vitrina$sufijo")
        regla.onNodeWithText("animados").performScrollTo().performClick()
        regla.onNodeWithText("Giradiscos").performScrollTo()
        regla.mainClock.advanceTimeBy(600)
        guardar(regla.onNode(isDialog() and hasAnyDescendant(hasText("COLECCIÓN DE LA SEMANA"))).captureToImage().asAndroidBitmap(), "fabs-tienda-catalogo$sufijo")
        regla.onNodeWithText("Giradiscos").performClick()
        regla.waitUntil(10_000) { regla.onAllNodes(hasText("Quitar")).fetchSemanticsNodes().isNotEmpty() }
        regla.mainClock.advanceTimeBy(900)
        guardar(regla.onNode(isDialog() and hasAnyDescendant(hasText("Quitar"))).captureToImage().asAndroidBitmap(), "fabs-probador-faltan$sufijo")
        assertTrue(vitrina.articulos.size == 5)
    }

    @Test fun loQuiero() = loQuiero(Estilo.CLARO, "")
    @Test fun loQuieroOscuro() = loQuiero(Estilo.GRAFITO, "-oscuro")

    private fun loQuiero(estilo: Estilo, sufijo: String) {
        val app = ApplicationProvider.getApplicationContext<CajonApp>()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        val cuenta = Cuenta(tareas, ClienteSocial(configuracion = app.avisosConfiguracion, http = servidor()))
        val vm = CajonViewModel(app)
        regla.setContent {
            // El contenido de la hoja (la hoja de abajo no se deja capturar en Robolectric).
            Tema(estilo) { Box(Modifier.fillMaxSize().background(FogonTema.actual.fondo), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
                ContenidoLoQuiero(object : InterfazSocial by interfazSocial(vm) { override val red = cuenta }, "banner:vinilo-sello", {}, {}) {}
            } }
        }
        regla.waitUntil(10_000) { regla.onAllNodes(hasText("Lo quiero")).fetchSemanticsNodes().isNotEmpty() }
        // Con «Regalar» abierto, la hoja crece: todo queda a la vista, desplazándose si no cabe.
        regla.onAllNodes(hasText("regalar", ignoreCase = true)).onFirst().performScrollTo().performClick()
        regla.mainClock.advanceTimeBy(900)
        regla.onAllNodes(hasText("regalar", ignoreCase = true)).onLast().performScrollTo().assertIsDisplayed()
        guardar(regla.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap(), "fabs-lo-quiero$sufijo")
    }

    private fun guardar(imagen: Bitmap, nombre: String) {
        val carpeta = File("build/reports/capturas-pantallas").apply { mkdirs() }
        File(carpeta, "$nombre.png").outputStream().use { check(imagen.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    private fun servidor(): OkHttpClient = OkHttpClient.Builder().addInterceptor { cadena ->
        val cuerpo = when (cadena.request().url.encodedPath) {
            "/v1/pagos/wompi/paquetes" -> """{"paquetes":[500,1000,2500,5000],"regalo":{"1000":50,"2500":250,"5000":750}}"""
            "/v1/tienda/temas" -> """{"chispas":700,"reclamados":["banner:negro-hotel"],"banner":"banner:negro-hotel"}"""
            "/v1/mercado" -> """{"articulos":[]}"""
            else -> null
        }
        Response.Builder().request(cadena.request()).protocol(Protocol.HTTP_1_1).code(if (cuerpo == null) 404 else 200).message("prueba")
            .body((cuerpo ?: "{}").toResponseBody()).build()
    }.build()

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

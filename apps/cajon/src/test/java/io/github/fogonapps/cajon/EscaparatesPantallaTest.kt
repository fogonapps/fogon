package io.github.fogonapps.cajon

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import io.github.fogonapps.identidad.*
import io.github.fogonapps.perfil.*
import io.github.fogonapps.revision.revisar
import io.github.fogonapps.cajon.datos.CajaFuerte
import io.github.fogonapps.cajon.ui.interfazSocial
import io.github.fogonapps.social.ui.InterfazSocial
import io.github.fogonapps.social.ui.TiendaEnEditarPerfil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Revisa el contenido nuevo con límites reales y sin fotos ni peticiones de red. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w360dp-h3000dp-xxhdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class EscaparatesPantallaTest {
    @get:Rule val regla = createComposeRule()

    @Test fun editorEnEspanolOscuro() = revisarEditor("es", Apariencia())

    @Test
    @Config(qualifiers = "en-w360dp-h3000dp-xxhdpi")
    fun editorEnInglesClaro() = revisarEditor("en", Apariencia(estilo = Estilo.CLARO))

    @Test fun cabeceraMuestraNivelYAbreExplicacionPropia() {
        var abierto = false
        regla.setContent {
            FogonTema(Paleta.Cajon, Apariencia()) {
                PerfilCabecera(PerfilVista("Ana", "ana", "", "", "", emptyList(), false, "yo",
                    nivel = 5, abrirNivel = { abierto = true }), portada = {}, foto = {})
            }
        }
        val pildora = regla.onNodeWithContentDescription("Nivel 5").assertExists()
        val limites = pildora.fetchSemanticsNode().boundsInRoot
        assertTrue(limites.width >= with(regla.density) { 48.dp.toPx() })
        assertTrue(limites.height >= with(regla.density) { 48.dp.toPx() })
        pildora.performClick()
        regla.runOnIdle { assertTrue(abierto) }
    }

    @Test fun nivelYBotonDeTiendaQuedanSeparadosConLetraGrande() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<CajonApp>()
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(app)
        val vm = CajonViewModel(app)
        val sinTareasDeRed = CoroutineScope(Job().apply { cancel() })
        val cuentaDePrueba = RedSocial(app, sinTareasDeRed, CajaFuerte(app, "nivel-prueba"), { null }, {}, {})
        (cuentaDePrueba.usuario as MutableStateFlow<String?>).value = "ana"
        regla.setContent {
            val densidad = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(densidad.density, 1.3f)) {
                FogonTema(Paleta.Cajon, Apariencia()) {
                    Column {
                        val adaptador = interfazSocial(vm)
                        val interfaz = object : InterfazSocial by adaptador {
                            override val red = cuentaDePrueba
                        }
                        NivelDetalle(5, listOf("cine"), propio = true)
                        TiendaEnEditarPerfil(interfaz)
                    }
                }
            }
        }
        val nivel = regla.onNodeWithContentDescription("cinéfilo, ganada").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val tienda = regla.onNodeWithText("Tienda del perfil").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(nivel.bottom < tienda.top)
    }

    @Test fun cabeceraOcultaNivelCero() {
        regla.setContent {
            FogonTema(Paleta.Cajon, Apariencia()) {
                PerfilCabecera(PerfilVista("Ana", "ana", "", "", "", emptyList(), false, "yo",
                    nivel = 0), portada = {}, foto = {})
            }
        }
        regla.onNodeWithContentDescription("Nivel 0").assertDoesNotExist()
        regla.onNodeWithText("Nivel 0").assertDoesNotExist()
    }

    @Test fun nivelAjenoAbreDetalleSinProgreso() {
        regla.setContent {
            FogonTema(Paleta.Cajon, Apariencia()) {
                PerfilCabecera(PerfilVista("Ana", "ana", "", "", "", emptyList(), false, "",
                    nivel = 5, insigniasNivel = listOf("cine")), portada = {}, foto = {})
            }
        }
        regla.onNodeWithContentDescription("Nivel 5").performClick()
        regla.onNodeWithText("Cómo subir").assertExists()
        regla.onNodeWithContentDescription("cinéfilo, ganada").assertExists()
        regla.onNodeWithText("Te falta 1 insignia para tu próxima repisa").assertDoesNotExist()
    }

    @Test fun detallePropioUsaSingularYExplicaCupo() {
        regla.setContent {
            FogonTema(Paleta.Cajon, Apariencia()) {
                NivelDetalle(5, listOf("cine"), propio = true, gratis = 3)
            }
        }
        regla.onNodeWithText("Te falta 1 insignia para tu próxima repisa").assertExists()
        regla.onNodeWithText("Repisas gratis: 3 de 6").assertExists()
    }

    @Test fun escaparatePropioPermiteAbrirEditor() {
        var abierto = false
        regla.setContent {
            FogonTema(Paleta.Cajon, Apariencia()) {
                Escaparates(listOf(EscaparateVista("texto", texto = "Mi escaparate")), alTocar = { abierto = true }) { _, _ -> }
            }
        }
        regla.onNodeWithText("Mi escaparate").performClick()
        regla.runOnIdle { assertTrue(abierto) }
    }

    @Test fun mantenerApretadaRepisaPropiaMuestraAccionesYAjenaNo() {
        var quitada = false
        regla.setContent {
            FogonTema(Paleta.Cajon, Apariencia()) {
                Column {
                    Escaparates(listOf(EscaparateVista("texto", texto = "Repisa propia")),
                        alTocar = {}, acciones = { listOf(CustomAccessibilityAction("Quitar") { quitada = true; true }) }) { _, _ -> }
                    Escaparates(listOf(EscaparateVista("texto", texto = "Repisa ajena"))) { _, _ -> }
                }
            }
        }
        val propia = regla.onNodeWithText("Repisa propia")
        val ajena = regla.onNodeWithText("Repisa ajena")
        ajena.performTouchInput { longClick() }
        regla.onNodeWithText("Quitar").assertDoesNotExist()
        assertEquals(listOf("Quitar"), propia.fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label })
        propia.performTouchInput { longClick() }
        regla.onNodeWithText("Quitar").assertIsDisplayed().performClick()
        regla.runOnIdle { assertTrue(quitada) }
    }

    @Test fun galeriaDeUnaFotoUsaTodoElAncho() = comprobarGaleria(1, 312.dp)

    @Test fun galeriaDeDosFotosReparteElAncho() = comprobarGaleria(2, 151.dp)

    @Test fun galeriaDeTresFotosMantieneLasMiniaturas() = comprobarGaleria(3, 112.dp)

    private fun comprobarGaleria(cantidad: Int, anchoFoto: androidx.compose.ui.unit.Dp) {
        val fotos = List(cantidad) { "foto-$it" }
        regla.setContent {
            FogonTema(Paleta.Cajon, Apariencia()) {
                Box(Modifier.width(360.dp)) {
                    Escaparates(listOf(EscaparateVista("galeria", fotos = fotos))) { foto, modifier ->
                        Box(modifier.testTag(foto))
                    }
                }
            }
        }
        fotos.take(2).forEach { foto ->
            val ancho = regla.onNodeWithTag(foto).fetchSemanticsNode().boundsInRoot.width
            assertEquals(with(regla.density) { anchoFoto.toPx() }, ancho, 1f)
        }
    }

    @Test
    @Config(qualifiers = "es-w360dp-h640dp-xxhdpi")
    fun tiendaAbreEnExtrasYSeAlineaSiLleganPaquetes() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<CajonApp>()
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(app)
        val vm = CajonViewModel(app)
        regla.setContent {
            FogonTema(Paleta.Cajon, Apariencia()) {
                io.github.fogonapps.social.ui.TiendaTemas(io.github.fogonapps.cajon.ui.interfazSocial(vm), escaparatesExtra = true) {}
            }
        }
        fun comprobarSeccion() {
            regla.waitForIdle()
            val seccion = regla.onNodeWithText("Más repisas").assertIsDisplayed().fetchSemanticsNode()
            assertTrue("La sección debe quedar junto a la cabecera", seccion.boundsInRoot.top < with(regla.density) { 120.dp.toPx() })
        }
        comprobarSeccion()
        regla.runOnIdle {
            @Suppress("UNCHECKED_CAST")
            val paquetes = io.github.fogonapps.social.ControladorSocial::class.java.getDeclaredField("_paquetesWompi").apply { isAccessible = true }.get(vm.red) as kotlinx.coroutines.flow.MutableStateFlow<List<Int>>
            paquetes.value = listOf(1000, 2000, 5000)
        }
        comprobarSeccion()
    }

    private fun revisarEditor(variante: String, apariencia: Apariencia) {
        val lista = listOf(
            EscaparateVista("galeria", fotos = (1..6).map { it.toString() }),
            EscaparateVista("numero", numero = "999999999", etiqueta = "Mi año"),
            EscaparateVista("texto", texto = "Una historia para compartir. ".repeat(11).take(300)),
            EscaparateVista("cajon:albumes", elementos = listOf(ElementoEscaparate("Un álbum", "Un artista"))),
            EscaparateVista("alcancia:meta", numero = "100", etiqueta = "%"),
            EscaparateVista("forzudo:records", elementos = listOf(ElementoEscaparate("Sentadilla", "110 kg · 8"))),
            EscaparateVista("cucha:racha", numero = "23", etiqueta = "días"),
            EscaparateVista("insignias", insignias = listOf("cine", "lector", "constancia")),
        )
        regla.setContent {
            FogonTema(Paleta.Cajon, apariencia) {
                Column(Modifier.fillMaxSize().fondoFogon(FogonTema.actual).verticalScroll(rememberScrollState())) {
                    EditorEscaparates(lista, 9, false, { _, _ -> }, {}, {}, {}, {}, {},
                        imagen = { nombre, m -> Box(m.tarjetaFogon()) { Text(nombre, color = FogonTema.actual.texto) } })
                }
            }
        }
        val hallazgos = regla.revisar("Escaparates $variante")
        assertTrue(hallazgos.joinToString("\n"), hallazgos.isEmpty())
        val salida = File("build/reports/escaparates/editor-$variante.png")
        salida.parentFile?.mkdirs()
        salida.outputStream().use { regla.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}

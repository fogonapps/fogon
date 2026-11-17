package io.github.fogonapps.cajon

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
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
        regla.onNodeWithContentDescription("Nivel 5").assertExists().performClick()
        regla.runOnIdle { assertTrue(abierto) }
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
            val seccion = regla.onNodeWithText("Escaparates").assertIsDisplayed().fetchSemanticsNode()
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

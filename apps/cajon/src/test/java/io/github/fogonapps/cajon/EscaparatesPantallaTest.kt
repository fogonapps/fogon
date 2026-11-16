package io.github.fogonapps.cajon

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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

    private fun revisarEditor(variante: String, apariencia: Apariencia) {
        val lista = listOf(
            EscaparateVista("galeria", fotos = (1..6).map { it.toString() }),
            EscaparateVista("numero", numero = "999999999", etiqueta = "Mi año"),
            EscaparateVista("texto", texto = "Una historia para compartir. ".repeat(11).take(300)),
            EscaparateVista("cajon:albumes", elementos = listOf(ElementoEscaparate("Un álbum", "Un artista"))),
            EscaparateVista("alcancia:meta", numero = "100", etiqueta = "%"),
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

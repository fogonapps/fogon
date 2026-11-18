package io.github.fogonapps.cajon

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import io.github.fogonapps.cajon.datos.Insignia
import io.github.fogonapps.cajon.datos.Insignias
import io.github.fogonapps.cajon.ui.FilaInsignias
import io.github.fogonapps.identidad.Apariencia
import io.github.fogonapps.identidad.Estilo
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.Paleta
import io.github.fogonapps.identidad.fondoFogon
import io.github.fogonapps.perfil.EscaparateVista
import io.github.fogonapps.perfil.Escaparates
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w360dp-h760dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MedallaInsigniaTest {
    @get:Rule val regla = createComposeRule()

    @Test fun insigniasEnOscuro() = capturar(Estilo.GRAFITO, "oscuro")

    @Test fun insigniasEnClaro() = capturar(Estilo.CLARO, "claro")

    private fun capturar(estilo: Estilo, tema: String) {
        regla.setContent {
            FogonTema(Paleta.Cajon, Apariencia(estilo = estilo)) {
                Column(Modifier.fillMaxWidth().fondoFogon(FogonTema.actual)) {
                    FilaInsignias(listOf(Insignia("cine", 10, 10), Insignia("series", 4, 10)), propias = true)
                    Escaparates(listOf(EscaparateVista("insignias", insignias = Insignias.IDS))) { _, _ -> }
                }
            }
        }
        val carpeta = File("build/reports/insignias").apply { mkdirs() }
        val salida = File(carpeta, "medallas-$tema.png")
        salida.outputStream().use {
            regla.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        regla.onNodeWithContentDescription("seriéfilo, lleva 4 de 10").assertIsDisplayed()
        regla.onNodeWithContentDescription("reto cumplido, ganada").assertIsDisplayed()
    }
}

package io.github.fogonapps.forzudo

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import io.github.fogonapps.identidad.Apariencia
import io.github.fogonapps.identidad.Desplegable
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.IconosFogon
import io.github.fogonapps.identidad.Paleta
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w360dp-h180dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PistaRespaldoTest {
    @get:Rule val regla = createComposeRule()

    @Test fun pistaCompletaEnEspanolConLetraGrande() {
        regla.setContent {
            FogonTema(Paleta.Forzudo, Apariencia(tamanoTexto = 3)) {
                Box(Modifier.background(FogonTema.actual.fondo)) {
                    Desplegable(IconosFogon.Chulo, stringResource(R.string.respaldo_titulo), stringResource(R.string.pista_respaldo)) {}
                }
            }
        }
        val resultados = mutableListOf<TextLayoutResult>()
        regla.onNodeWithText("Exporta una copia e impórtala cuando la necesites.", useUnmergedTree = true)
            .assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(resultados)) }
        assertFalse(resultados.single().hasVisualOverflow)
        val carpeta = File("build/reports/insignias").apply { mkdirs() }
        val salida = File(carpeta, "respaldo-es-letra-130.png")
        salida.outputStream().use {
            regla.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

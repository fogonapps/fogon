package io.github.fogonapps.social.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.fogonapps.identidad.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="es-w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorMercadoTest {
    @get:Rule val regla=createComposeRule()
    @Test fun bannerPermiteTextoYEntregaArteAlPublicador() {
        var resultado: Pair<String,ByteArray>?=null
        regla.setContent { FogonTema(Paleta.Cajon,Apariencia(menosMovimiento=true)) {
            EditorMercado("banner",null,{}, { tipo,bytes -> resultado=tipo to bytes })
        } }
        regla.waitUntil(10_000) { runCatching { regla.onNodeWithText("Siguiente: publicar").assertIsEnabled() }.isSuccess }
        regla.onNodeWithText("Texto",useUnmergedTree=true).performClick()
        regla.onNodeWithText("Añadir texto (máximo 3)").performScrollTo().performClick()
        regla.onNodeWithText("Tu texto").assertExists()
        captura("banner")
        regla.onNodeWithText("Siguiente: publicar").performClick()
        regla.runOnIdle { assertEquals("banner",resultado?.first); assertNotNull(resultado?.second) }
    }
    @Test @Config(qualifiers="en-w360dp-h800dp-xxhdpi")
    fun marcoAbreEnInglesConTemaClaroYExporta() {
        var formato=""
        regla.setContent { FogonTema(Paleta.Cajon,Apariencia(estilo=Estilo.CLARO,menosMovimiento=true)) {
            EditorMercado("marco",null,{}, { tipo,_ -> formato=tipo })
        } }
        regla.waitUntil(10_000) { runCatching { regla.onNodeWithText("Next: publish").assertIsEnabled() }.isSuccess }
        regla.onNodeWithText("Create frame").assertIsDisplayed()
        captura("marco")
        regla.onNodeWithText("Next: publish").performClick()
        regla.runOnIdle { assertEquals("marco",formato) }
    }
    private fun captura(tipo: String) {
        val bitmap=regla.onNode(isDialog()).captureToImage().asAndroidBitmap()
        java.io.File("build/reports/editor-$tipo.png").apply { parentFile?.mkdirs() }.outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }

}

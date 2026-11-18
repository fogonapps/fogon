package io.github.fogonapps.social.ui

import androidx.compose.ui.test.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Constraints
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
    @Test fun formasYCapasTienenEspacioYBarraFija() = revisarControles(false)

    @Test @Config(qualifiers="en-w360dp-h800dp-xxhdpi")
    fun formasYCapasEnInglesClaro() = revisarControles(true)

    private fun revisarControles(ingles: Boolean) {
        regla.setContent { FogonTema(Paleta.Cajon, Apariencia(estilo=if (ingles) Estilo.CLARO else Estilo.GRAFITO, menosMovimiento=true)) {
            EditorMercado("banner", null, {}, { _, _ -> })
        } }
        val siguiente = if (ingles) "Next: publish" else "Siguiente: publicar"
        regla.waitUntil(10_000) { runCatching { regla.onNodeWithText(siguiente).assertIsEnabled() }.isSuccess }
        regla.onNodeWithText(if (ingles) "Shapes" else "Formas").performScrollTo().performClick()
        val formas = if (ingles) listOf("Circle", "Pill", "Spark star") else listOf("Círculo", "Píldora", "Estrella chispa")
        regla.onNodeWithText(formas.first()).performScrollTo()
        captura(if (ingles) "formas-en" else "formas-es")
        comprobarFila(formas)
        regla.onNodeWithText(formas.first()).performClick()
        val capas = if (ingles) listOf("Backward", "Forward", "Delete layer") else listOf("Hacia atrás", "Hacia adelante", "Borrar capa")
        regla.onNodeWithText(capas.last()).performScrollTo()
        comprobarFila(capas)
        regla.onNodeWithText(if (ingles) "Undo" else "Deshacer").assertIsDisplayed()
        regla.onNodeWithText(siguiente).assertIsDisplayed()
        captura(if (ingles) "capas-en" else "capas-es")
        regla.onNodeWithText(if (ingles) "Templates" else "Plantillas").performScrollTo().performClick()
        captura(if (ingles) "plantillas-en" else "plantillas-es")
    }

    private fun comprobarFila(textos: List<String>) {
        val nodos = textos.map { regla.onNodeWithText(it).assertIsDisplayed().fetchSemanticsNode() }
        nodos.zipWithNext().forEach { (a, b) ->
            if (b.boundsInRoot.top >= a.boundsInRoot.bottom) {
                assertTrue("Separación de 8 dp entre filas", b.boundsInRoot.top - a.boundsInRoot.bottom >= with(regla.density) { 7.5.dp.toPx() })
            } else {
                assertTrue("Separación de 12 dp", b.boundsInRoot.left - a.boundsInRoot.right >= with(regla.density) { 11.5.dp.toPx() })
            }
        }
        textos.forEach { texto ->
            val resultados = mutableListOf<TextLayoutResult>()
            regla.onNodeWithText(texto, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(resultados) }
            assertTrue("Texto completo: $texto", resultados.none { r ->
                val medido = TextMeasurer(r.layoutInput.fontFamilyResolver, r.layoutInput.density, r.layoutInput.layoutDirection, cacheSize = 0).measure(
                    text = r.layoutInput.text, style = r.layoutInput.style,
                    constraints = Constraints(maxWidth = r.size.width, maxHeight = r.size.height))
                medido.didOverflowWidth || (medido.didOverflowHeight && medido.size.height < medido.multiParagraph.height)
            })
        }
    }

    @Test @Config(qualifiers="es-w360dp-h2400dp-xxhdpi")
    fun repisasMuestranLosTresEjemplosSinRed() {
        regla.setContent { FogonTema(Paleta.Cajon, Apariencia(menosMovimiento=true)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { repeat(3) { MuestraRepisa(it) } }
        } }
        regla.onNodeWithText("Lluvia en la terraza").assertExists()
        regla.onNodeWithText("Siempre alcanza para uno más.").assertExists()
        regla.onNodeWithText("128").assertExists()
        regla.onNodeWithText("películas y series este año").assertExists()
        val bitmap = regla.onRoot().captureToImage().asAndroidBitmap()
        java.io.File("build/reports/repisas-muestras.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    }

    private fun captura(tipo: String) {
        val bitmap=regla.onNode(isDialog()).captureToImage().asAndroidBitmap()
        java.io.File("build/reports/editor-$tipo.png").apply { parentFile?.mkdirs() }.outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }

}

package io.github.fogonapps.social.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.github.fogonapps.identidad.Apariencia
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.Paleta
import io.github.fogonapps.identidad.Estilo
import io.github.fogonapps.perfil.Perfil
import io.github.fogonapps.perfil.PerfilVista
import io.github.fogonapps.perfil.TarjetaPreguntaLena
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LenaPantallaTest {
    @get:Rule val regla = createComposeRule()

    @Test
    @Config(qualifiers = "en-w360dp-h800dp-xxhdpi")
    fun elDetalleEnLineaSeEnvuelveYDesplazaLasAccionesEnContraste() {
        val vista = PerfilVista(nombre = "Ana", usuario = "ana", foto = "", portada = "", bio = "",
            enlaces = emptyList(), privada = false, relacion = "nada", nivelLena = "brasa")
        regla.setContent {
            FogonTema(Paleta.Cajon, Apariencia(estilo = Estilo.CONTRASTE, tamanoTexto = 3, menosMovimiento = true)) {
                Column(Modifier.width(260.dp)) {
                    Perfil(vista, 0, {}, portada = {}, foto = {}, enlace = {}, acciones = { Text("Edit profile") })
                }
            }
        }
        val insignia = regla.onNodeWithContentDescription("Leña: Ember").assertIsDisplayed()
        val detalle = regla.onNodeWithText("Leña · Ember")
        val agradecimiento = regla.onNodeWithText("Thank you for supporting Fogón.")
        val accion = regla.onNodeWithText("Edit profile")
        val inicioAccion = accion.getUnclippedBoundsInRoot().top
        detalle.assertDoesNotExist()
        insignia.performClick()
        detalle.assertIsDisplayed()
        agradecimiento.assertIsDisplayed()

        val texto = mutableListOf<TextLayoutResult>()
        agradecimiento.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(texto) }
        assertTrue(texto.single().lineCount > 1)
        assertFalse(texto.single().hasVisualOverflow)
        val nuevaPosicion = accion.getUnclippedBoundsInRoot().top
        assertTrue(nuevaPosicion > inicioAccion)
        assertTrue(nuevaPosicion > agradecimiento.getUnclippedBoundsInRoot().bottom)

        insignia.performClick()
        detalle.assertDoesNotExist()
        agradecimiento.assertDoesNotExist()
        assertEquals(inicioAccion, accion.getUnclippedBoundsInRoot().top)
    }

    @Test fun lasDosOpcionesDeLaTarjetaEnvianLaEleccion() {
        val elecciones = mutableListOf<Boolean>()
        regla.setContent {
            FogonTema(Paleta.Cajon, Apariencia(menosMovimiento = true)) {
                TarjetaPreguntaLena(habilitada = true) { elecciones += it }
            }
        }
        regla.onNodeWithText("Mostrar").performClick()
        regla.onNodeWithText("Mantener oculta").performClick()
        regla.runOnIdle { assertEquals(listOf(true, false), elecciones) }
    }
}

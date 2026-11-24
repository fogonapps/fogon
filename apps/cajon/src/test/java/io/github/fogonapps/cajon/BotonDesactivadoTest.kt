package io.github.fogonapps.cajon

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.core.view.ViewCompat
import io.github.fogonapps.identidad.Apariencia
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.Paleta
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Un botón apagado se anuncia apagado al lector de pantalla (y no solo se ve gris). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BotonDesactivadoTest {
    @get:Rule val regla = createComposeRule()

    @Test fun botonesApagadosSeAnuncianApagados() {
        var vista: View? = null
        regla.setContent {
            vista = LocalView.current
            FogonTema(Paleta.Cajon, Apariencia()) {
                Column {
                    BotonRiso("Guardar", {}, habilitado = false)
                    BotonRiso("Seguir", {})
                    BotonSecundario("Quitar", {}, habilitado = false)
                }
            }
        }
        regla.onNodeWithText("Guardar").assertIsNotEnabled().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Role))
        regla.onNodeWithText("Seguir").assertIsEnabled()
        regla.onNodeWithText("Quitar").assertIsNotEnabled()

        // Lo que de verdad ve TalkBack: el nodo de accesibilidad de Android.
        val v = vista!!.let { it as? ViewGroup ?: it }
        val proveedor = ViewCompat.getAccessibilityDelegate(v)!!.getAccessibilityNodeProvider(v)!!
        val guardar = regla.onNodeWithText("Guardar").fetchSemanticsNode().id
        val seguir = regla.onNodeWithText("Seguir").fetchSemanticsNode().id
        assertFalse(proveedor.createAccessibilityNodeInfo(guardar)!!.isEnabled)
        assertTrue(proveedor.createAccessibilityNodeInfo(seguir)!!.isEnabled)
    }
}

package io.github.fogonapps.cajon

import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import io.github.fogonapps.identidad.Apariencia
import io.github.fogonapps.identidad.BarrasDelDialogo
import io.github.fogonapps.identidad.Estilo
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.Paleta
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Los diálogos a pantalla completa (tienda, probador, mercado, celebración) tienen su propia ventana: sus íconos de
 * la barra de estado van oscuros sobre el papel claro y claros sobre el oscuro.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BarrasDelDialogoTest {
    @get:Rule val regla = createComposeRule()

    @Test fun temaClaroIconosOscuros() = assertEquals(true, iconosOscuros(Estilo.CLARO))
    @Test fun temaOscuroIconosClaros() = assertEquals(false, iconosOscuros(Estilo.GRAFITO))

    /** Si la barra de estado del diálogo pide íconos oscuros («apariencia clara»). */
    private fun iconosOscuros(estilo: Estilo): Boolean {
        var vista: View? = null
        regla.setContent {
            FogonTema(Paleta.Cajon, Apariencia(estilo = estilo)) {
                Dialog({}, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
                    BarrasDelDialogo()
                    vista = LocalView.current
                }
            }
        }
        regla.waitForIdle()
        val v = checkNotNull(vista)
        val ventana = checkNotNull((v.parent as? DialogWindowProvider)?.window)
        return WindowCompat.getInsetsController(ventana, v).isAppearanceLightStatusBars
    }
}

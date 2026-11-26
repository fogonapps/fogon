package io.github.fogonapps.cajon

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.fogonapps.cajon.datos.*
import io.github.fogonapps.cajon.ui.Acciones
import io.github.fogonapps.cajon.ui.App
import io.github.fogonapps.cajon.ui.textoPestana
import io.github.fogonapps.identidad.Apariencia
import io.github.fogonapps.identidad.Estilo
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.Paleta
import io.github.fogonapps.revision.guardarCaptura
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** La barra de siempre (seis pestañas y el +) cabe en un teléfono angosto con la letra grande, sin cortarse ni encimarse. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BarraPestanasTest {
    @get:Rule val regla = createComposeRule()

    @Test fun seisPestanasCabenA360ConLetraGrande() = comprobar(2f, "360-letra-200")

    @Test
    @Config(qualifiers = "en-w360dp-h800dp-xhdpi")
    fun seisPestanasCabenA360EnIngles() = comprobar(1.3f, "360-letra-130-en")

    private fun comprobar(escala: Float, variante: String) {
        val app = ApplicationProvider.getApplicationContext<CajonApp>()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        app.getSharedPreferences("guia", Context.MODE_PRIVATE).edit().putInt("vista.cajon", 99).commit()
        val vm = CajonViewModel(app)
        regla.setContent {
            val densidad = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(densidad.density, escala)) {
                FogonTema(Paleta.Cajon, Apariencia(estilo = Estilo.GRAFITO)) { App(vm, Acciones({}, {}, {}, { _, _ -> })) }
            }
        }
        regla.waitUntil(20_000) { vm.estado.value != null }
        @Suppress("UNCHECKED_CAST")
        val estado = CajonViewModel::class.java.getDeclaredField("_estado").apply { isAccessible = true }
            .get(vm) as MutableStateFlow<Estado?>
        estado.value = estado.value!!.copy(listo = true, ajustes = Ajustes(nombre = "Marina", gustosListos = true))
        regla.runOnIdle { vm.irA(Pestana.INICIO) }
        regla.waitForIdle()

        val ancho = regla.onRoot().fetchSemanticsNode().boundsInRoot.width
        val nombres = Pestana.entries.map { app.getString(textoPestana(it)) } + app.getString(R.string.buscar_y_agregar)
        assertEquals(5, nombres.size)
        val cajas = nombres.map { n ->
            val nodos = regla.onAllNodesWithContentDescription(n).fetchSemanticsNodes()
            assertTrue("No está «$n» en la barra", nodos.isNotEmpty())
            nodos.maxBy { it.boundsInRoot.bottom }.boundsInRoot
        }
        cajas.forEachIndexed { i, c ->
            assertTrue("«${nombres[i]}» se sale de la pantalla: $c (ancho $ancho)", c.left >= 0f && c.right <= ancho)
            if (i > 0) assertTrue("«${nombres[i]}» se encima con «${nombres[i - 1]}»", c.left >= cajas[i - 1].right)
        }
        regla.guardarCaptura("barra", variante)
    }
}

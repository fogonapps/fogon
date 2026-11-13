package io.github.fogonapps.forzudo

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.fogonapps.forzudo.ui.App
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.Paleta
import io.github.fogonapps.revision.Hallazgo
import io.github.fogonapps.revision.pantallasDe
import io.github.fogonapps.revision.revisar
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * El recorrido de todas las pestañas y pantallas de la app (las encuentra solo, también las nuevas): cada una se
 * abre y se revisa (botones pegados o encimados, cosas difíciles de tocar o sin nombre para el lector de pantalla).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h3000dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecorridoPantallasTest {
    @get:Rule val regla = createComposeRule()

    @Test fun ningunaPantallaSeVeMal() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(app)
        // La guía de primeros pasos, como ya vista (se probaría encima de todo).
        app.getSharedPreferences("guia", android.content.Context.MODE_PRIVATE).edit().putInt("vista.forzudo", 99).commit()
        val vm = GymViewModel(app)
        regla.setContent { FogonTema(Paleta.Forzudo) { App(vm, {}, {}) } }
        regla.waitUntil(20_000) { vm.estado.value != null }
        vm.terminarTutorial()
        regla.waitForIdle()
        val hallazgos = mutableListOf<Hallazgo>()
        Pestana.entries.forEach { p -> vm.irA(p); hallazgos += regla.revisar("pestaña $p", barraAbajo = 140f) }
        pantallasDe(Pantalla::class).forEach { p ->
            vm.irA(Pestana.INICIO); vm.abrir(p)
            hallazgos += runCatching { regla.revisar(p.toString().take(60)) }.getOrElse { listOf(Hallazgo(p.toString().take(60), "falla", it.toString().take(200))) }
        }
        assertTrue("Pantallas con problemas:\n" + hallazgos.joinToString("\n"), hallazgos.isEmpty())
    }
}

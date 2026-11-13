package io.github.fogonapps.forzudo

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.fogonapps.forzudo.ui.App
import io.github.fogonapps.identidad.Apariencia
import io.github.fogonapps.identidad.Estilo
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
@Config(sdk = [35], qualifiers = "en-w400dp-h3000dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecorridoPantallasTest {
    val regla = createComposeRule()
    // La escala debe estar aplicada antes de que la regla de Compose cree la actividad.
    @get:Rule val entorno: org.junit.rules.TestRule = org.junit.rules.RuleChain.outerRule(
        org.junit.rules.TestRule { base, descripcion ->
            object : org.junit.runners.model.Statement() {
                override fun evaluate() {
                    org.robolectric.RuntimeEnvironment.setFontScale(
                        if (descripcion.methodName == "espanolChicoLetraGrande") 1.3f else 1f
                    )
                    try { base.evaluate() } finally { org.robolectric.RuntimeEnvironment.setFontScale(1f) }
                }
            }
        }
    ).around(regla)

    @Test fun ningunaPantallaSeVeMal() = recorrer("inglés, 400 dp, letra normal, oscuro")

    @Test
    @Config(qualifiers = "es-w360dp-h3000dp-xxhdpi")
    fun espanolChicoLetraGrande() = recorrer("español, 360 dp, letra 130 %, oscuro")

    @Test
    @Config(qualifiers = "en-w360dp-h3000dp-xxhdpi")
    fun inglesClaro() = recorrer("inglés, 360 dp, letra normal, claro", apariencia = Apariencia(estilo = Estilo.CLARO))

    private fun recorrer(variante: String, apariencia: Apariencia = Apariencia()) {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(app)
        // La guía de primeros pasos, como ya vista (se probaría encima de todo).
        app.getSharedPreferences("guia", android.content.Context.MODE_PRIVATE).edit().putInt("vista.forzudo", 99).commit()
        val vm = GymViewModel(app)
        regla.setContent { FogonTema(Paleta.Forzudo, apariencia) { App(vm, {}, {}) } }
        regla.waitUntil(20_000) { vm.estado.value != null }
        vm.terminarTutorial()
        regla.waitForIdle()
        val hallazgos = mutableListOf<Hallazgo>()
        Pestana.entries.forEach { p ->
            hallazgos += runCatching { vm.irA(p); regla.revisar("pestaña $p", barraAbajo = 140f) }
                .getOrElse { listOf(Hallazgo("pestaña $p", "falla", it.toString())) }
        }
        pantallasDe(Pantalla::class).forEach { p ->
            hallazgos += runCatching { vm.irA(Pestana.INICIO); vm.abrir(p); regla.revisar(p.toString().take(60)) }.getOrElse { listOf(Hallazgo(p.toString().take(60), "falla", it.toString().take(200))) }
        }
        println("=== forzudo | $variante ===\n" + hallazgos.joinToString("\n").ifEmpty { "Sin problemas" })
        val errores = hallazgos
        assertTrue("Pantallas con problemas ($variante):\n" + errores.joinToString("\n"), errores.isEmpty())
    }
}

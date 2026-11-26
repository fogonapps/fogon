package io.github.fogonapps.cajon

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import io.github.fogonapps.cajon.datos.Insignia
import io.github.fogonapps.cajon.datos.Insignias
import io.github.fogonapps.cajon.ui.CapaCelebracion
import io.github.fogonapps.cajon.ui.Figtree
import io.github.fogonapps.cajon.ui.FilaInsignias
import io.github.fogonapps.cajon.ui.Fraunces
import io.github.fogonapps.cajon.ui.HojaInsignia
import io.github.fogonapps.cajon.ui.TalonDe
import io.github.fogonapps.identidad.Apariencia
import io.github.fogonapps.identidad.Estilo
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.Paleta
import io.github.fogonapps.identidad.Trama
import io.github.fogonapps.identidad.fondoFogon
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Las insignias como talones de cine: la fila del perfil (72 dp), la hoja y los niveles, en claro y en oscuro. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w360dp-h760dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TalonesCapturasTest {
    @get:Rule val regla = createComposeRule()

    @Before fun sinTrama() { Trama.apagada = true }

    private fun ins(id: String, lleva: Int) = Insignia(id, lleva, Insignias.meta(id), Insignias.niveles(id))

    private val lasDelPerfil = listOf(ins("cine", 260), ins("series", 10), ins("lector", 12), ins("constancia", 30), ins("anime", 6),
        ins("discos", 18), ins("critico", 3), ins("explorador", 4), ins("reto", 0))

    @Test fun filaClaro() = fila(Estilo.CLARO, "claro")
    @Test fun filaOscuro() = fila(Estilo.GRAFITO, "oscuro")
    @Test fun hojaGanadaClaro() = hojaGanada(Estilo.CLARO, "claro")
    @Test fun hojaGanadaOscuro() = hojaGanada(Estilo.GRAFITO, "oscuro")
    @Test fun hojaPorGanarClaro() = hojaPorGanar(Estilo.CLARO, "claro")
    @Test fun hojaPorGanarOscuro() = hojaPorGanar(Estilo.GRAFITO, "oscuro")
    @Test fun nivelesClaro() = niveles(Estilo.CLARO, "claro")
    @Test fun nivelesOscuro() = niveles(Estilo.GRAFITO, "oscuro")
    @Test fun celebracionClaro() = celebracion(Estilo.CLARO, "claro")
    @Test fun celebracionOscuro() = celebracion(Estilo.GRAFITO, "oscuro")

    /** Con «menos movimiento», el talón aparece directo en su lugar y a su tamaño: nada se mueve después. */
    @Test fun celebracionConMenosMovimientoNoAnima() {
        val (alPrincipio, alFinal) = boundsDeLaCelebracion(menosMovimiento = true)
        assertEquals(alFinal, alPrincipio)
    }

    /** Sin «menos movimiento», el talón entra animado (la prueba de arriba de verdad mide algo). */
    @Test fun celebracionSinMenosMovimientoAnima() {
        val (alPrincipio, alFinal) = boundsDeLaCelebracion(menosMovimiento = false)
        assertNotEquals(alFinal, alPrincipio)
    }

    /** Con [hoja], sobre el papel liso, como en la hoja de abajo. */
    private fun conTema(estilo: Estilo, hoja: Boolean = false, contenido: @androidx.compose.runtime.Composable () -> Unit) = regla.setContent {
        FogonTema(Paleta.Cajon, Apariencia(estilo = estilo), letraTitulos = Fraunces, letraTexto = Figtree) {
            val f = FogonTema.actual
            Column(Modifier.fillMaxWidth().then(if (hoja) Modifier.background(f.fondo) else Modifier.fondoFogon(f))) { contenido() }
        }
    }

    private fun fila(estilo: Estilo, tema: String) {
        conTema(estilo) { FilaInsignias(lasDelPerfil, propias = true) }
        guardar("fila-$tema")
        regla.onNodeWithContentDescription("cinéfilo, ganada, nivel 2 de 3").assertIsDisplayed()
        regla.onNodeWithContentDescription("seriéfilo, ganada, nivel 1 de 3").assertIsDisplayed()
        regla.onNodeWithText("4 de 9").assertIsDisplayed()
    }

    private fun hojaGanada(estilo: Estilo, tema: String) {
        conTema(estilo, hoja = true) { HojaInsignia(ins("cine", 260), propias = true, fecha = "2026-11-12", capa = null, compartir = {}) }
        guardar("hoja-ganada-$tema")
        regla.onNodeWithText("Nivel 2 de 3 · el próximo, a los 500").assertIsDisplayed()
        regla.onNodeWithContentDescription("cinéfilo, ganada, nivel 2 de 3").assertIsDisplayed()
    }

    private fun hojaPorGanar(estilo: Estilo, tema: String) {
        conTema(estilo, hoja = true) { HojaInsignia(ins("discos", 18), propias = true, fecha = null, capa = null, compartir = null) }
        guardar("hoja-porganar-$tema")
        regla.onNodeWithText("Te faltan 12").assertIsDisplayed()
        regla.onNodeWithContentDescription("melómano, lleva 18 de 30").assertIsDisplayed()
    }

    private fun niveles(estilo: Estilo, tema: String) {
        conTema(estilo) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(50, 250, 500).forEach { TalonDe(ins("cine", it), 108.dp) }
            }
        }
        guardar("niveles-$tema")
    }

    /** La celebración sobre la app (el perfil con sus talones detrás), ya quieta. */
    private fun celebracion(estilo: Estilo, tema: String) {
        conTema(estilo) {
            FilaInsignias(lasDelPerfil, propias = true)
            CapaCelebracion(ins("series", 10), 1) {}
        }
        regla.mainClock.advanceTimeBy(3_000)
        regla.onNodeWithText("Nueva insignia").assertIsDisplayed()
        guardarNodo(regla.onNode(isDialog()), "celebracion-$tema")
    }

    /** Dónde está el talón en el primer cuadro y cuando todo terminó. */
    private fun boundsDeLaCelebracion(menosMovimiento: Boolean): Pair<androidx.compose.ui.geometry.Rect, androidx.compose.ui.geometry.Rect> {
        regla.mainClock.autoAdvance = false
        regla.setContent {
            FogonTema(Paleta.Cajon, Apariencia(estilo = Estilo.CLARO, menosMovimiento = menosMovimiento), letraTitulos = Fraunces, letraTexto = Figtree) {
                CapaCelebracion(ins("series", 10), 1) {}
            }
        }
        regla.mainClock.advanceTimeByFrame()
        regla.mainClock.advanceTimeByFrame()
        val talon = regla.onNodeWithContentDescription("seriéfilo", substring = true, ignoreCase = true, useUnmergedTree = true)
        val alPrincipio = talon.fetchSemanticsNode().boundsInRoot
        regla.mainClock.advanceTimeBy(3_000)
        return alPrincipio to talon.fetchSemanticsNode().boundsInRoot
    }

    private fun guardarNodo(nodo: androidx.compose.ui.test.SemanticsNodeInteraction, nombre: String) {
        val carpeta = File("build/reports/insignias").apply { mkdirs() }
        File(carpeta, "talones-$nombre.png").outputStream().use {
            nodo.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun guardar(nombre: String) {
        val carpeta = File("build/reports/insignias").apply { mkdirs() }
        File(carpeta, "talones-$nombre.png").outputStream().use {
            regla.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

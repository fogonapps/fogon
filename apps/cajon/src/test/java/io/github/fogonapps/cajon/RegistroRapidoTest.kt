package io.github.fogonapps.cajon

import android.content.Context
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.fogonapps.cajon.datos.*
import io.github.fogonapps.cajon.ui.Acciones
import io.github.fogonapps.cajon.ui.App
import io.github.fogonapps.cajon.ui.RegistroRapido
import io.github.fogonapps.cajon.ui.notaDeArrastre
import io.github.fogonapps.identidad.Apariencia
import io.github.fogonapps.identidad.Estilo
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.Paleta
import io.github.fogonapps.revision.guardarCaptura
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * El registro en dos toques (mantener apretada una portada), el diario en cuadrícula y los Ajustes agrupados de Fabs:
 * las reglas son las de siempre y las pantallas quedan en capturas (build/reports/capturas-pantallas).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w400dp-h1100dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RegistroRapidoTest {
    @get:Rule val regla = createComposeRule()

    private val pelicula = Obra("p", Tipos.PELICULA, "Cuaderno del río", estado = Estados.QUIERO, anio = 2021)
    private val serie = Obra("s", Tipos.SERIE, "La casa del faro", estado = Estados.EN_CURSO)

    private fun preparar(obras: List<Obra>, contenido: (@androidx.compose.runtime.Composable (CajonViewModel) -> Unit)? = null): Pair<CajonViewModel, MutableStateFlow<Estado?>> {
        val app = ApplicationProvider.getApplicationContext<CajonApp>()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        app.getSharedPreferences("guia", Context.MODE_PRIVATE).edit().putInt("vista.cajon", 99).commit()
        val vm = CajonViewModel(app)
        // La app entera (así carga el estado); la hoja, si hay, encima cuando el estado ya está.
        val encima = androidx.compose.runtime.mutableStateOf(false)
        regla.setContent { FogonTema(Paleta.Cajon, Apariencia(estilo = Estilo.CLARO)) {
            App(vm, Acciones({}, {}, {}, { _, _ -> }))
            if (encima.value) contenido?.invoke(vm)
        } }
        regla.waitUntil(20_000) { vm.estado.value != null }
        @Suppress("UNCHECKED_CAST")
        val estado = CajonViewModel::class.java.getDeclaredField("_estado").apply { isAccessible = true }.get(vm) as MutableStateFlow<Estado?>
        estado.value = estado.value!!.copy(listo = true, ajustes = Ajustes(nombre = "Marina", gustosListos = true), obras = obras)
        regla.runOnIdle { encima.value = true }
        regla.waitForIdle()
        return vm to estado
    }

    @Test
    fun notaDelDedoVaDeMediaEnMedia() {
        assertEquals(0, notaDeArrastre(-5f, 200f))
        assertEquals(1, notaDeArrastre(3f, 200f))
        assertEquals(5, notaDeArrastre(100f, 200f))
        assertEquals(7, notaDeArrastre(121f, 200f))
        assertEquals(10, notaDeArrastre(260f, 200f))
        assertEquals(0, notaDeArrastre(50f, 0f))
    }

    @Test
    fun terminadaHoyAnotaUnaVezYNoCierraLaPantalla() {
        val (vm, estado) = preparar(listOf(pelicula, serie))
        regla.runOnIdle { vm.abrir(Pantalla.Diario) }
        regla.runOnIdle { vm.registroRapido("p", 7, terminadaHoy = true, otraVez = false, gusta = true) }
        regla.runOnIdle {
            val p = estado.value!!.vivas.single { it.id == "p" }
            assertEquals(Estados.TERMINADO, p.estado)
            assertEquals(7, p.nota)
            assertTrue(p.meGusta)
            assertEquals(1, p.registros.size)
            assertEquals(7, p.registros.single().nota)
            assertEquals(vm.hoy().toString(), p.registros.single().fecha)
            // Se anotó desde una hoja: la pantalla de abajo sigue abierta.
            assertEquals(Pantalla.Diario, vm.pila.value.lastOrNull())
        }
        // Una serie también queda terminada, con una sola entrada de hoy (ponerEstado no repite el registro).
        regla.runOnIdle { vm.registroRapido("s", 8, terminadaHoy = true, otraVez = true, gusta = false) }
        regla.runOnIdle {
            val s = estado.value!!.vivas.single { it.id == "s" }
            assertEquals(Estados.TERMINADO, s.estado)
            assertEquals(1, s.registros.size)
            assertTrue(s.registros.single().otraVez)
            assertFalse(s.meGusta)
        }
        // Sin «Terminé hoy» solo cambia la nota (y el me gusta): el diario no suma nada.
        regla.runOnIdle { vm.registroRapido("p", 9, terminadaHoy = false, otraVez = false, gusta = false) }
        regla.runOnIdle {
            val p = estado.value!!.vivas.single { it.id == "p" }
            assertEquals(9, p.nota)
            assertFalse(p.meGusta)
            assertEquals(1, p.registros.size)
        }
    }

    @Test
    fun laHojaSeVeYGuarda() {
        val (_, estado) = preparar(listOf(pelicula)) { vm -> RegistroRapido(vm, pelicula, onElegirVarias = {}) {} }
        val app = ApplicationProvider.getApplicationContext<CajonApp>()
        regla.onNodeWithText(app.getString(R.string.rapido_termine_hoy)).assertExists()
        regla.onNodeWithText(app.getString(R.string.rapido_elegir_varias)).assertExists()
        regla.onNodeWithText(app.getString(R.string.me_gusta)).performClick()
        regla.onNodeWithText(app.getString(R.string.guardar)).performClick()
        regla.runOnIdle {
            val p = estado.value!!.vivas.single()
            assertTrue(p.meGusta)
            assertEquals(Estados.TERMINADO, p.estado)
            assertEquals(1, p.registros.size)
        }
    }

    @Test
    fun diarioEnCuadriculaYEnLista() {
        val hoy = java.time.LocalDate.now()
        val obras = (1..6).map { i ->
            Obra("o$i", if (i % 2 == 0) Tipos.LIBRO else Tipos.PELICULA, "Obra de muestra $i", estado = Estados.TERMINADO, meGusta = i == 2,
                registros = listOf(Registro("g$i", hoy.minusDays(i * 9L).toString(), nota = i + 3, otraVez = i == 3)))
        }
        val (vm, _) = preparar(obras)
        val app = ApplicationProvider.getApplicationContext<CajonApp>()
        regla.runOnIdle { vm.diarioEnCuadricula = true; vm.abrir(Pantalla.Diario) }
        regla.waitForIdle()
        regla.guardarCaptura("diario", "cuadricula")
        regla.onNodeWithText(app.getString(R.string.diario_lista)).performClick()
        regla.waitForIdle()
        assertFalse(vm.diarioEnCuadricula)
        assertTrue(regla.onAllNodesWithText("Obra de muestra 1").fetchSemanticsNodes().isNotEmpty())
        regla.guardarCaptura("diario", "lista")
        regla.runOnIdle { vm.diarioEnCuadricula = true }
    }

    @Test
    fun ajustesEnCincoGrupos() {
        val (vm, _) = preparar(listOf(pelicula))
        val app = ApplicationProvider.getApplicationContext<CajonApp>()
        regla.runOnIdle { vm.abrir(Pantalla.Ajustes) }
        regla.waitForIdle()
        listOf(R.string.aj_grupo_cuenta, R.string.aj_grupo_coleccion, R.string.aj_grupo_apariencia, R.string.aj_grupo_privacidad, R.string.acerca_de_corto)
            .forEach { regla.onNode(hasText(app.getString(it)) and isHeading()).assertExists() }
        regla.onNodeWithText(app.getString(R.string.licencias)).assertExists()
        regla.guardarCaptura("ajustes", "grupos")
    }
}

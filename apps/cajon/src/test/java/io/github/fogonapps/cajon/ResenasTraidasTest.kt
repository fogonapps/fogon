package io.github.fogonapps.cajon

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.fogonapps.cajon.datos.*
import io.github.fogonapps.cajon.ui.Acciones
import io.github.fogonapps.cajon.ui.App
import io.github.fogonapps.identidad.Apariencia
import io.github.fogonapps.identidad.Estilo
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.Paleta
import io.github.fogonapps.revision.guardarCaptura
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Las reseñas traídas de otra app entran privadas; con cuenta se ofrece compartirlas una vez, todas juntas. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w400dp-h900dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ResenasTraidasTest {
    @get:Rule val regla = createComposeRule()

    private val csv = "Date,Name,Year,Letterboxd URI,Rating,Rewatch,Review,Tags,Watched Date\n" +
        "2024-02-03,El niño,2023,https://boxd.it/abc,4.5,No,Me encantó,,2024-01-01\n" +
        "2024-02-04,Otra,2021,https://boxd.it/def,3,No,Buena,,2024-01-02\n"

    private fun preparar(conCuenta: Boolean): Pair<CajonViewModel, MutableStateFlow<Estado?>> {
        val app = ApplicationProvider.getApplicationContext<CajonApp>()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        app.getSharedPreferences("guia", Context.MODE_PRIVATE).edit().putInt("vista.cajon", 99).commit()
        val vm = CajonViewModel(app)
        regla.setContent { FogonTema(Paleta.Cajon, Apariencia(estilo = Estilo.CLARO)) { App(vm, Acciones({}, {}, {}, { _, _ -> })) } }
        regla.waitUntil(20_000) { vm.estado.value != null }
        @Suppress("UNCHECKED_CAST")
        val estado = CajonViewModel::class.java.getDeclaredField("_estado").apply { isAccessible = true }
            .get(vm) as MutableStateFlow<Estado?>
        estado.value = estado.value!!.copy(listo = true, ajustes = Ajustes(nombre = "Marina", gustosListos = true))
        regla.runOnIdle { vm.importarDeOtraApp(csv.toByteArray(), "reviews.csv") }
        regla.waitUntil(20_000) { vm.resenasTraidas.value != null }
        // La sesión se pone al final (la app la lee al arrancar): también es el caso de quien entra después de importar.
        if (conCuenta) regla.runOnIdle { (vm.red.usuario as MutableStateFlow<String?>).value = "marina" }
        regla.waitForIdle()
        return vm to estado
    }

    @Test fun conCuentaSePregunta() {
        preparar(conCuenta = true)
        regla.waitUntil(20_000) { regla.onAllNodesWithText("Trajiste 2 reseñas de Letterboxd").fetchSemanticsNodes().isNotEmpty() }
        regla.guardarCaptura("resenas-traidas", "claro")
    }

    @Test fun compartirSoloTocaLasTraidasQueSiguenPrivadas() {
        val o = Obra("o", Tipos.PELICULA, "El niño")
        val e = Estado(obras = listOf(o), resenas = listOf(
            Resena("a", "o", texto = "Traída"), Resena("b", "o", texto = "Traída, ya de amigos", visibilidad = Visibilidad.AMIGOS),
            Resena("c", "o", texto = "Mía, no traída")))
        val x = Resenas.compartir(e, setOf("a", "b"), Visibilidad.PUBLICA, 5)
        assertEquals(mapOf("a" to Visibilidad.PUBLICA, "b" to Visibilidad.AMIGOS, "c" to Visibilidad.PRIVADA),
            x.resenas.associate { it.id to Visibilidad.de(it.visibilidad) })
    }

    @Test fun dejarlasPrivadasNoCambiaNada() {
        val (vm, estado) = preparar(conCuenta = true)
        regla.runOnIdle { vm.compartirResenasTraidas(null) }
        regla.waitForIdle()
        assertEquals(setOf(Visibilidad.PRIVADA), estado.value!!.resenas.filter { !it.borrada }.map { Visibilidad.de(it.visibilidad) }.toSet())
        assertNull(vm.resenasTraidas.value)
    }

    @Test fun sinCuentaNoSePreguntaYQuedanPrivadas() {
        val (vm, estado) = preparar(conCuenta = false)
        regla.onNodeWithText("Trajiste 2 reseñas de Letterboxd").assertDoesNotExist()
        assertEquals(setOf(Visibilidad.PRIVADA), estado.value!!.resenas.filter { !it.borrada }.map { Visibilidad.de(it.visibilidad) }.toSet())
        // Espera a que la persona entre con su cuenta.
        assertEquals(2, vm.resenasTraidas.value?.ids?.size)
        // Y sigue esperando aunque la app se cierre (otro ViewModel, el mismo perfil).
        val otro = CajonViewModel(ApplicationProvider.getApplicationContext<CajonApp>())
        assertEquals(vm.resenasTraidas.value, otro.resenasTraidas.value)
    }
}

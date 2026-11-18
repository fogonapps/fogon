package io.github.fogonapps.cajon

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.Density
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
import io.github.fogonapps.social.PerfilSocial
import io.github.fogonapps.social.PersonaSocial
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w400dp-h1100dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CapturasPantallasTest {
    @get:Rule val regla = createComposeRule()

    @Test fun oscuro() = capturar(Estilo.GRAFITO, "oscuro")
    @Test fun claro() = capturar(Estilo.CLARO, "claro")

    @Test
    @Config(qualifiers = "es-w360dp-h1100dp-xhdpi")
    fun perfilesOscurosConLetraGrande() = capturar(Estilo.GRAFITO, "oscuro-360-letra-130", 1.3f)

    @Test
    @Config(qualifiers = "es-w360dp-h1100dp-xhdpi")
    fun perfilesClarosConLetraGrande() = capturar(Estilo.CLARO, "claro-360-letra-130", 1.3f)

    private fun capturar(estilo: Estilo, variante: String, escala: Float = 1f) {
        val app = ApplicationProvider.getApplicationContext<CajonApp>()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        app.getSharedPreferences("guia", Context.MODE_PRIVATE).edit().putInt("vista.cajon", 99).commit()
        val vm = CajonViewModel(app)
        regla.setContent {
            val densidad = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(densidad.density, escala)) {
                FogonTema(Paleta.Cajon, Apariencia(estilo = estilo)) { App(vm, Acciones({}, {}, {}, { _, _ -> })) }
            }
        }
        regla.waitUntil(20_000) { vm.estado.value != null }
        @Suppress("UNCHECKED_CAST")
        val estado = CajonViewModel::class.java.getDeclaredField("_estado").apply { isAccessible = true }
            .get(vm) as MutableStateFlow<Estado?>
        estado.value = estado.value!!.copy(
            listo = true,
            ajustes = Ajustes(nombre = "Marina", gustosListos = true),
            perfil = Perfil(usuario = "marina", bio = "Historias para compartir."),
            obras = listOf(Obra("lectura", Tipos.LIBRO, "Cuaderno del bosque",
                estado = Estados.EN_CURSO, paginas = 240, pagina = 78)),
        )
        val insignias = listOf("series", "anime", "lector")
        val perfil = PerfilSocial(PersonaSocial("ines", "Inés de muestra"), bio = "Una libreta llena de ideas.",
            nivel = insignias.size, insigniasNivel = insignias, seguidores = 12, siguiendo = 8)
        (vm.red.perfiles as MutableStateFlow).value = mapOf("ines" to perfil)

        fun abrir(pantalla: Pantalla) = regla.runOnIdle {
            vm.irA(Pestana.INICIO)
            vm.abrir(pantalla)
        }

        if (escala == 1f) {
            regla.runOnIdle { vm.irA(Pestana.INICIO) }
            regla.guardarCaptura("inicio", variante)
            abrir(Pantalla.Ajustes)
            regla.guardarCaptura("ajustes", variante)
            abrir(Pantalla.Privacidad)
            regla.guardarCaptura("privacidad", variante)
            abrir(Pantalla.EditarPerfil)
            regla.guardarCaptura("editar-perfil", variante)
        }
        abrir(Pantalla.Perfil)
        regla.waitUntil(5_000) { regla.onAllNodesWithText("Marina").fetchSemanticsNodes().isNotEmpty() }
        regla.guardarCaptura("perfil-propio", variante)
        abrir(Pantalla.PerfilRed("ines"))
        regla.waitUntil(5_000) { regla.onAllNodesWithText("Inés de muestra").fetchSemanticsNodes().isNotEmpty() }
        regla.guardarCaptura("perfil-ajeno", variante)
    }
}

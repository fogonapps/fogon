package io.github.fogonapps.cajon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.fogonapps.identidad.Apariencia
import io.github.fogonapps.identidad.Estilo
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.Paleta
import io.github.fogonapps.perfil.BannerPerfil
import io.github.fogonapps.perfil.BannersGratis
import io.github.fogonapps.perfil.TemasPerfil
import io.github.fogonapps.perfil.nombreBanner
import io.github.fogonapps.revision.guardarCaptura
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Capturas de los banners: muestras de color, banners teñidos y un cuadro de cada banner con movimiento. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w400dp-h1600dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BannersCapturasPantallasTest {
    @get:Rule val regla = createComposeRule()

    @Test fun oscuro() = capturar(Estilo.GRAFITO, "oscuro")
    @Test fun claro() = capturar(Estilo.CLARO, "claro")

    private fun capturar(estilo: Estilo, variante: String) {
        regla.setContent {
            FogonTema(Paleta.Cajon, Apariencia(estilo = estilo)) {
                val f = FogonTema.actual
                Column(Modifier.fillMaxSize().background(f.fondo).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf("chispa", "brasa", "fogata", "fogon").forEach { io.github.fogonapps.perfil.MedallaLena(it, 26.dp) }
                        listOf("chispa", "fogon").forEach { io.github.fogonapps.perfil.MedallaLena(it, 52.dp) }
                    }
                    BannersGratis({}, TemasPerfil.BANNERS_ANIMADOS.map { "banner:$it" }, portada = "banner:papel~azul")
                    listOf("papel~azul", "riso-rosa~verde", "puntos~naranja").forEach { id ->
                        BannerPerfil(id, Modifier.fillMaxWidth().height(90.dp))
                    }
                    TemasPerfil.BANNERS_ANIMADOS.forEach { id ->
                        Text(nombreBanner(id), color = f.texto)
                        BannerPerfil("$id~violeta".takeIf { id == "estelar" } ?: id, Modifier.fillMaxWidth().height(90.dp))
                    }
                }
            }
        }
        regla.mainClock.advanceTimeBy(1_500)
        regla.guardarCaptura("banners", variante)
    }
}

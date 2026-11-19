package io.github.fogonapps.cajon

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.github.fogonapps.identidad.Apariencia
import io.github.fogonapps.identidad.Estilo
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.Paleta
import io.github.fogonapps.social.Contadores
import io.github.fogonapps.social.PermisoContadores
import io.github.fogonapps.social.ui.AjustesContadores
import io.github.fogonapps.social.ui.R as SocialR
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContadoresPantallaTest {
    @get:Rule val regla = createComposeRule()

    @Test fun estadisticasEncendidasYSePuedenApagar() {
        val app = ApplicationProvider.getApplicationContext<CajonApp>()
        File(app.noBackupFilesDir, "contadores.json").delete()
        ReflectionHelpers.setStaticField(Contadores::class.java, "instancia", null)
        regla.setContent {
            FogonTema(Paleta.Cajon, Apariencia(estilo = Estilo.CLARO)) { Column { Column { AjustesContadores() } } }
        }
        val interruptor = regla.onNode(isToggleable()).assertIsOn()
        regla.onNodeWithText(app.getString(SocialR.string.contadores_pista), useUnmergedTree = true).assertIsDisplayed()
        interruptor.performClick().assertIsOff()
        check(Contadores.de(app).permiso.value == PermisoContadores.NO)
    }

    @Test fun datosLegiblesEnEspanol() = conDatos("es")

    @Test
    @Config(qualifiers = "en-w360dp-h640dp-xhdpi")
    fun datosLegiblesEnIngles() = conDatos("en")

    private fun conDatos(idioma: String) {
        val app = ApplicationProvider.getApplicationContext<CajonApp>()
        val hoy = LocalDate.now()
        val dia = hoy.minusDays(1)
        File(app.noBackupFilesDir, "contadores.json").writeText(
            """{"dias":{"$dia":{"tienda.abrir":3,"futura.clave":1},"$hoy":{"tienda.abrir":12}}}""",
        )
        ReflectionHelpers.setStaticField(Contadores::class.java, "instancia", null)
        regla.setContent {
            FogonTema(Paleta.Cajon, Apariencia(estilo = Estilo.CLARO)) { Column { AjustesContadores() } }
        }
        val vista = app.getString(SocialR.string.contadores_ver)
        regla.onNodeWithText(vista).performClick()
        val fecha = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
            .withLocale(app.resources.configuration.locales[0])
        regla.onNodeWithText(dia.format(fecha)).assertIsDisplayed()
        regla.onNodeWithText(hoy.format(fecha)).assertDoesNotExist()
        regla.onNodeWithText(if (idioma == "es") "Abriste la tienda de chispas · 3" else "Opened the spark shop · 3").assertIsDisplayed()
        regla.onNodeWithText("futura.clave · 1").assertIsDisplayed()
        val exactos = regla.onNodeWithText(app.getString(SocialR.string.contadores_exactos))
        val enviar = regla.onNodeWithText(app.getString(SocialR.string.contadores_enviar_ahora))
        val bordeExactos = exactos.assertIsDisplayed().fetchSemanticsNode().boundsInRoot.bottom
        val bordeEnviar = enviar.assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
        assertTrue(bordeEnviar - bordeExactos in 0f..24f)
        guardar("sumas-contadores-con-dias", idioma, vista)

        regla.onNodeWithText(app.getString(SocialR.string.contadores_exactos)).performClick()
        val json = regla.onNodeWithText("\"app\":\"cajon\"", substring = true)
        json.performScrollTo().assertIsDisplayed()
        regla.onNodeWithText("\"tienda.abrir\":12", substring = true).assertDoesNotExist()
    }

    private fun guardar(nombre: String, variante: String, texto: String) {
        val imagen = regla.onNode(isDialog() and hasAnyDescendant(hasText(texto))).captureToImage().asAndroidBitmap()
        val carpeta = File("build/reports/capturas-pantallas").apply { mkdirs() }
        File(carpeta, "$nombre-$variante.png").outputStream().use {
            check(imagen.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}

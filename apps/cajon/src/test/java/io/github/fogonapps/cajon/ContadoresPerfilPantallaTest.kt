package io.github.fogonapps.cajon

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import io.github.fogonapps.identidad.*
import io.github.fogonapps.perfil.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class ContadoresPerfilPantallaTest {
    @get:Rule val regla = createComposeRule()

    @Test fun inglesGrafito() = comprobar(Estilo.GRAFITO, true)
    @Test fun inglesMarca() = comprobar(Estilo.MARCA, true)
    @Test fun inglesClaro() = comprobar(Estilo.CLARO, true)
    @Test fun inglesContraste() = comprobar(Estilo.CONTRASTE, true)
    @Test fun espanolGrafito() = comprobar(Estilo.GRAFITO, false)
    @Test fun espanolMarca() = comprobar(Estilo.MARCA, false)
    @Test fun espanolClaro() = comprobar(Estilo.CLARO, false)
    @Test fun espanolContraste() = comprobar(Estilo.CONTRASTE, false)

    private fun comprobar(estilo: Estilo, ingles: Boolean) {
        val etiquetas = if (ingles) listOf("following", "followers", "in your cajón")
            else listOf("siguiendo", "seguidores", "en tu cajón")
        regla.setContent {
            val densidad = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(densidad.density, 1.3f)) {
                FogonTema(Paleta.Cajon, Apariencia(estilo = estilo)) {
                    PerfilCabecera(PerfilVista("Prueba", "prueba", "", "", "", emptyList(), false, "yo",
                        contadores = etiquetas.map { ContadorPerfil("32", it) }), portada = {}, foto = {})
                }
            }
        }
        etiquetas.forEach { etiqueta ->
            regla.onNodeWithText(etiqueta).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { accion ->
                val resultados = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                assertTrue(accion(resultados))
                resultados.forEach { resultado ->
                    assertFalse("Altura insuficiente: $etiqueta", resultado.didOverflowHeight)
                    // El tamaño entero puede redondear hacia abajo el ancho tipográfico fraccionario.
                    for (linea in 0 until resultado.lineCount) {
                        assertTrue("Texto cortado: $etiqueta (${resultado.getLineRight(linea)} > ${resultado.size.width})",
                            resultado.getLineRight(linea) <= resultado.size.width + 1f)
                    }
                    for (linea in 0 until resultado.lineCount) assertFalse(resultado.isLineEllipsized(linea))
                }
            }
        }
    }
}

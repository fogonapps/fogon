package io.github.fogonapps.revision

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w400dp-h3000dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RevisionTest {
    @get:Rule val regla = createComposeRule()
    private val texto = "Un texto demasiado largo para caber en una caja angosta"

    @Test fun textoQueCabeNoSeMarcaComoCortado() {
        regla.setContent { BasicText("Hola") }
        assertTrue(regla.revisar("ejemplo").isEmpty())
    }

    @Test fun etiquetaCortaEnDesplazableNoSeMarcaComoCortada() {
        regla.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) { BasicText("Hola") }
        }
        assertTrue(regla.revisar("ejemplo").isEmpty())
    }

    @Test fun textoQueDesbordaUnaAlturaFinitaSeMarcaComoCortado() {
        regla.setContent { BasicText(texto, Modifier.width(100.dp).height(8.dp)) }
        assertEquals(listOf(Hallazgo("ejemplo", "cortado", "«$texto»")), regla.revisar("ejemplo"))
    }

    @Test fun textoSinElipsisSeMarcaComoCortado() {
        regla.setContent {
            BasicText(texto, Modifier.width(40.dp), maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
        }
        assertEquals(listOf(Hallazgo("ejemplo", "cortado", "«$texto»")), regla.revisar("ejemplo"))
    }

    @Test fun textoConElipsisNoSeMarcaComoCortado() {
        regla.setContent {
            BasicText(texto, Modifier.width(40.dp), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
        assertTrue(regla.revisar("ejemplo").isEmpty())
    }
}

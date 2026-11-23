package io.github.fogonapps.cajon

import androidx.compose.ui.graphics.Color
import io.github.fogonapps.cajon.datos.*
import io.github.fogonapps.identidad.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test

class PersonalizacionTest {
    private val json = Json { ignoreUnknownKeys = true }
    @Test fun aparienciaViajaConLaCuenta() {
        val a = Apariencia(ajustes = mapOf(Estilo.GRAFITO to AjustesEstilo(luz = LuzPropia(Color.Red, Color.Blue, 25, 150))))
            .guardarTintas("Mi tinta", Paleta.Cajon)
        assertEquals(a, json.decodeFromString<AparienciaCuenta>(json.encodeToString(a.paraCuenta(2))).paraTelefono())
    }
    @Test fun jsonViejoMantieneValoresPorDefecto() {
        val a = json.decodeFromString<AparienciaCuenta>("""{"estilo":"CLARO","ajustes":{"CLARO":{"transparencia":30}}}""").paraTelefono()
        assertNull(a.de().luz); assertTrue(a.guardadas.isEmpty()); assertEquals(30, a.transparencia())
    }
    @Test fun barraConservaInicioYMinimoTres() {
        assertEquals(3, Pestana.visibles(listOf("MUSICA")).size)
        assertTrue(Pestana.INICIO in Pestana.visibles(listOf("CINE", "LIBROS", "MUSICA")))
        assertEquals(Pestana.entries, Pestana.visibles(emptyList()))
        assertEquals(listOf(Pestana.MUSICA, Pestana.INICIO, Pestana.CINE), Pestana.visibles(listOf("MUSICA", "INICIO", "CINE", "CINE", "desconocida")))
    }
}

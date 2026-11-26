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
    @Test fun laBarraTieneCuatroDestinosFijos() {
        // Las guías piden de tres a cinco destinos; los tipos de obra pasaron a «Lo tuyo».
        assertEquals(listOf(Pestana.INICIO, Pestana.LO_TUYO, Pestana.DESCUBRIR, Pestana.GENTE), Pestana.entries)
    }
}

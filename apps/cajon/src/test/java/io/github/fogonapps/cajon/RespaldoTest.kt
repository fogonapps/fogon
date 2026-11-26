package io.github.fogonapps.cajon

import android.content.Context
import io.github.fogonapps.cajon.datos.*
import io.github.fogonapps.identidad.RespaldoFogon
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = CajonApp::class)
class RespaldoTest {
    private val original = Estado(listo = true, obras = listOf(Obra("o1", Tipos.LIBRO, "El niño, mañana", registros = listOf(Registro("g1", "2024-02-03", 8)), etiquetas = listOf("español"))))

    @Test fun limpiarClavesRetiradasConservaLasPreferenciasActuales() {
        val contexto = RuntimeEnvironment.getApplication()
        val prefs = contexto.getSharedPreferences("claves", Context.MODE_PRIVATE)
        val conservadas = mapOf(
            "p1.social" to "sesión de prueba",
            "p2.tmdb" to "clave de prueba",
            "p2.ultima_copia" to 456L,
            "p1.usuario_social" to "prueba",
        )
        prefs.edit()
            .putString("github", "cifrado de prueba")
            .putString("github.iv", "iv de prueba")
            .putString("p1.github", "cifrado de prueba")
            .putString("p1.github.iv", "iv de prueba")
            .putString("p1.gist", "copia anterior")
            .putString("p2.gist", "copia sin token")
            .putString("p1.usuario", "cuenta anterior")
            .putLong("p1.ultima", 123L)
            .putString("p1.social", "sesión de prueba")
            .putString("p2.tmdb", "clave de prueba")
            .putLong("p2.ultima_copia", 456L)
            .putString("p1.usuario_social", "prueba")
            .apply()

        limpiarClavesRetiradas(contexto)

        assertEquals(conservadas, prefs.all)
    }

    @Test fun idaYVueltaConDatosYCamposDesconocidos() {
        val texto = JsonCajon.encodeToString(Estado.serializer(), original)
        assertEquals(original, leerRespaldoCajon(texto))
        assertEquals(original, leerRespaldoCajon(texto.dropLast(1) + ",\"campoFuturo\":true}"))
    }

    @Test fun respaldoTodoFogonConservaLaParteDeLaApp() {
        val texto = JsonCajon.encodeToString(Estado.serializer(), original)
        val partes = RespaldoFogon.partes("{\"fogon\":1,\"apps\":{\"cajon\":$texto}}")!!
        assertEquals(original, leerRespaldoCajon(partes.getValue("cajon")))
        assertNull(RespaldoFogon.partes("{\"fogon\":2,\"apps\":{\"cajon\":$texto}}"))
    }

    @Test fun respaldoDanadoNoSeLee() {
        for (texto in listOf("", "{", "[]", "null", "{}", "{\"Title\":\"Netflix\"}", JsonCajon.encodeToString(Estado.serializer(), original).dropLast(5)))
            assertNull(leerRespaldoCajon(texto))
    }
}

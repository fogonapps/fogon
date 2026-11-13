package io.github.fogonapps.cajon

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

    @Test fun proveedorRechazaDanadosSinBorrarDatos() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as CajonApp
        val almacen = app.recursos().almacen
        almacen.updateData { Logica.normalizar(original) }
        val proveedor = Robolectric.buildContentProvider(DatosCajon::class.java).create().get()
        val antes = almacen.data.first()
        for (texto in listOf("", "{", "[]", "null", "{}", "{\"Title\":\"Netflix\"}", JsonCajon.encodeToString(Estado.serializer(), original).dropLast(5))) {
            assertNull(leerRespaldoCajon(texto))
            assertFalse(proveedor.restaurar(texto))
            assertEquals(antes, almacen.data.first())
        }
        val exportado = leerRespaldoCajon(proveedor.leer())!!
        assertEquals(original.obras, exportado.obras)
        assertTrue(proveedor.restaurar(proveedor.leer()))
        val restaurado = leerRespaldoCajon(proveedor.leer())!!
        assertEquals(exportado, restaurado)
    }
}

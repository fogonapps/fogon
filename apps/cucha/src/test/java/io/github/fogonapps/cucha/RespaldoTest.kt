package io.github.fogonapps.cucha

import io.github.fogonapps.cucha.datos.*
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
@Config(sdk = [35], application = CuchaApp::class)
class RespaldoTest {
    private val original = Estado(listo = true, nombre = "Ángel", recetas = listOf(Receta("r1", "Piña, café", pasosEs = listOf("Lavar\nCocinar"), propia = true)), plan = mapOf("A|1|almuerzo" to "r1"), mercadoExtra = listOf("Ñame"))

    @Test fun idaYVueltaConDatosYCamposDesconocidos() {
        val texto = JsonCucha.encodeToString(Estado.serializer(), original)
        assertEquals(original, leerRespaldoCucha(texto))
        assertEquals(original, leerRespaldoCucha(texto.dropLast(1) + ",\"campoFuturo\":true}"))
    }

    @Test fun respaldoTodoFogonConservaLaParteDeLaApp() {
        val texto = JsonCucha.encodeToString(Estado.serializer(), original)
        val partes = RespaldoFogon.partes("{\"fogon\":1,\"apps\":{\"cucha\":$texto}}")!!
        assertEquals(original, leerRespaldoCucha(partes.getValue("cucha")))
        assertNull(RespaldoFogon.partes("{\"fogon\":2,\"apps\":{\"cucha\":$texto}}"))
    }

    @Test fun proveedorRechazaDanadosSinBorrarDatos() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as CuchaApp
        val almacen = app.almacen
        almacen.updateData { app.logica.normalizar(original) }
        val proveedor = Robolectric.buildContentProvider(DatosCucha::class.java).create().get()
        val antes = almacen.data.first()
        for (texto in listOf("", "{", "[]", "null", "{}", "{\"Title\":\"Netflix\"}", JsonCucha.encodeToString(Estado.serializer(), original).dropLast(5))) {
            assertNull(leerRespaldoCucha(texto))
            assertFalse(proveedor.restaurar(texto))
            assertEquals(antes, almacen.data.first())
        }
        val exportado = leerRespaldoCucha(proveedor.leer())!!
        assertEquals(original.recetas, exportado.recetas)
        assertTrue(proveedor.restaurar(proveedor.leer()))
        val restaurado = leerRespaldoCucha(proveedor.leer())!!
        assertEquals(exportado, restaurado.copy(updatedAt = exportado.updatedAt))
    }
}

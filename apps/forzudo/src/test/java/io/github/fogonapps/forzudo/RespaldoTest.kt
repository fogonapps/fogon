package io.github.fogonapps.forzudo

import io.github.fogonapps.forzudo.datos.*
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
@Config(sdk = [35], application = ForzudoApp::class)
class RespaldoTest {
    private val original = Estado(nombre = "Ángel Muñoz", sessions = listOf(Sesion("s1", "2024-02-03", title = "Pierna, fuerza\nMañana", entries = mapOf("sentadilla" to listOf(Serie(42.5, 8.0, true))))), body = listOf(PesoCorporal("2024-02-03", 70.5)))

    @Test fun idaYVueltaConDatosYCamposDesconocidos() {
        val texto = JsonGym.encodeToString(Estado.serializer(), original)
        assertEquals(original, leerRespaldoGym(texto))
        assertEquals(original, leerRespaldoGym(texto.dropLast(1) + ",\"campoFuturo\":true}"))
    }

    @Test fun respaldoTodoFogonConservaLaParteDeLaApp() {
        val texto = JsonGym.encodeToString(Estado.serializer(), original)
        val partes = RespaldoFogon.partes("{\"fogon\":1,\"apps\":{\"forzudo\":$texto}}")!!
        assertEquals(original, leerRespaldoGym(partes.getValue("forzudo")))
        assertNull(RespaldoFogon.partes("{\"fogon\":2,\"apps\":{\"forzudo\":$texto}}"))
    }

    @Test fun proveedorRechazaDanadosSinBorrarDatos() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as ForzudoApp
        val almacen = app.almacen
        almacen.updateData { app.logica.normalizar(original) }
        val proveedor = Robolectric.buildContentProvider(DatosForzudo::class.java).create().get()
        val antes = almacen.data.first()
        for (texto in listOf("", "{", "[]", "null", "{}", "{\"Title\":\"Netflix\"}", JsonGym.encodeToString(Estado.serializer(), original).dropLast(5))) {
            assertNull(leerRespaldoGym(texto))
            assertFalse(proveedor.restaurar(texto))
            assertEquals(antes, almacen.data.first())
        }
        val exportado = leerRespaldoGym(proveedor.leer())!!
        assertEquals(original.sessions, exportado.sessions)
        assertTrue(proveedor.restaurar(proveedor.leer()))
        val restaurado = leerRespaldoGym(proveedor.leer())!!
        assertEquals(exportado, restaurado.copy(updatedAt = exportado.updatedAt))
    }
}

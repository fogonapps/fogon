package io.github.fogonapps.ahorrador

import io.github.fogonapps.ahorrador.datos.*
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
@Config(sdk = [35], application = AhorradorApp::class)
class RespaldoTest {
    private val original = Estado(nombre = "Ángel", cuentas = listOf(Cuenta("c1", "Ahorro, mañana", TipoCuenta.BANCO, 123456)), movimientos = listOf(Movimiento("m1", "2024-02-03", TipoMovimiento.GASTO, 1234, "c1", nota = "Niño\nPan, café")))

    @Test fun idaYVueltaConDatosYCamposDesconocidos() {
        val texto = JsonAhorrador.encodeToString(Estado.serializer(), original)
        assertEquals(original, leerRespaldoAhorrador(texto))
        assertEquals(original, leerRespaldoAhorrador(texto.dropLast(1) + ",\"campoFuturo\":true}"))
    }

    @Test fun respaldoTodoFogonConservaLaParteDeLaApp() {
        val texto = JsonAhorrador.encodeToString(Estado.serializer(), original)
        val partes = RespaldoFogon.partes("{\"fogon\":1,\"apps\":{\"ahorrador\":$texto}}")!!
        assertEquals(original, leerRespaldoAhorrador(partes.getValue("ahorrador")))
        assertNull(RespaldoFogon.partes("{\"fogon\":2,\"apps\":{\"ahorrador\":$texto}}"))
    }

    @Test fun proveedorRechazaDanadosSinBorrarDatos() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as AhorradorApp
        val almacen = app.almacen
        almacen.updateData { app.logica.normalizar(original) }
        val proveedor = Robolectric.buildContentProvider(DatosAhorrador::class.java).create().get()
        val antes = almacen.data.first()
        for (texto in listOf("", "{", "[]", "null", "{}", "{\"Title\":\"Netflix\"}", JsonAhorrador.encodeToString(Estado.serializer(), original).dropLast(5))) {
            assertNull(leerRespaldoAhorrador(texto))
            assertFalse(proveedor.restaurar(texto))
            assertEquals(antes, almacen.data.first())
        }
        val exportado = leerRespaldoAhorrador(proveedor.leer())!!
        assertEquals(original.movimientos, exportado.movimientos)
        assertTrue(proveedor.restaurar(proveedor.leer()))
        val restaurado = leerRespaldoAhorrador(proveedor.leer())!!
        assertEquals(exportado, restaurado.copy(updatedAt = exportado.updatedAt))
    }
}

package io.github.fogonapps.ahorrador

import android.os.Looper
import io.github.fogonapps.ahorrador.datos.Categoria
import io.github.fogonapps.ahorrador.datos.Cuenta
import io.github.fogonapps.ahorrador.datos.Estado
import io.github.fogonapps.ahorrador.datos.Fijo
import io.github.fogonapps.ahorrador.datos.Movimiento
import io.github.fogonapps.ahorrador.datos.TipoCuenta
import io.github.fogonapps.ahorrador.datos.TipoMovimiento
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Al abrir, los fijos automáticos vencidos se anotan una sola vez, después de traer la cuenta: el que otro teléfono ya
 * anotó este mes (mismo `fijo`) no se repite, y abrir y volver a la app a la vez no los duplica.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AhorradorApp::class, qualifiers = "es")
class FijosAlAbrirTest {
    @Test fun seAnotanUnaVezYNoRepitenLoDeOtroTelefono() {
        val app = RuntimeEnvironment.getApplication() as AhorradorApp
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(app)
        val almacen = app.almacenPerfil(app.perfiles.lista.activo)
        val mes = LocalDate.now().toString().take(7)
        runBlocking {
            almacen.updateData {
                Estado(
                    cuentas = listOf(Cuenta("banco", "Banco", TipoCuenta.BANCO)),
                    categorias = listOf(Categoria("casa", "Casa", 0, "casa")),
                    fijos = listOf(
                        Fijo("arriendo", "Arriendo", 900_000, "casa", "banco", dia = 1, automatico = true),
                        Fijo("luz", "Luz", 80_000, "casa", "banco", dia = 1, automatico = true),
                    ),
                    // La luz ya la anotó el otro teléfono (llegó con la copia de la cuenta).
                    movimientos = listOf(Movimiento("otro", "$mes-01", TipoMovimiento.GASTO, 80_000, "banco", categoria = "casa", nota = "Luz", fijo = "luz")),
                    listo = true,
                )
            }
        }

        val vm = AhorradorViewModel(app)
        // Como onStart, a la vez que el arranque.
        vm.anotarFijos(); vm.anotarFijos()
        var e = runBlocking { almacen.data.first() }
        repeat(200) {
            if (e.movimientos.any { it.fijo == "arriendo" }) return@repeat
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(25)
            e = runBlocking { almacen.data.first() }
        }
        repeat(20) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(25) }
        e = runBlocking { almacen.data.first() }

        assertEquals(1, e.movimientos.count { it.fijo == "arriendo" })
        assertEquals(1, e.movimientos.count { it.fijo == "luz" })
    }
}

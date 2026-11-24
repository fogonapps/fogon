package io.github.fogonapps.ahorrador

import android.content.Context
import android.os.Looper
import io.github.fogonapps.ahorrador.datos.Categoria
import io.github.fogonapps.ahorrador.datos.Estado
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Traducir lo de fábrica al abrir no es un cambio de la persona: no toca `updatedAt` ni deja la copia de la cuenta
 * como pendiente. Si lo hiciera, un teléfono que solo tradujo nombres subiría su cuaderno viejo encima del de la
 * cuenta y se perderían los movimientos anotados en otro teléfono.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AhorradorApp::class, qualifiers = "es")
class TraducirSinSubirTest {
    @Test fun traducirNoMarcaCambioNiCambiaLaFecha() {
        val app = RuntimeEnvironment.getApplication() as AhorradorApp
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(app)
        val id = app.perfiles.lista.activo
        val almacen = app.almacenPerfil(id)
        runBlocking { almacen.updateData { Estado(categorias = listOf(Categoria("mercado", "Groceries", 0, "mercado")), updatedAt = 123L, listo = true) } }

        AhorradorViewModel(app)
        var e: Estado = runBlocking { almacen.data.first() }
        repeat(200) {
            if (e.categorias.first().nombre == "Mercado") return@repeat
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(25)
            e = runBlocking { almacen.data.first() }
        }

        assertEquals("Mercado", e.categorias.first().nombre)
        assertEquals(123L, e.updatedAt)
        assertFalse(app.getSharedPreferences("copia-alcancia-p$id", Context.MODE_PRIVATE).getBoolean("sucio", false))
    }
}

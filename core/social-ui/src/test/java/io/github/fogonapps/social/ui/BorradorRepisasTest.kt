package io.github.fogonapps.social.ui

import androidx.compose.runtime.saveable.SaverScope
import io.github.fogonapps.social.EscaparateSocial
import io.github.fogonapps.social.FotoEscaparate
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** El borrador del editor de repisas sobrevive a que Android recree la pantalla (girar, cambiar la letra). */
class BorradorRepisasTest {
    private val alcance = SaverScope { true }

    @Test fun elBorradorDeRepisasVuelveIgual() {
        val borrador = listOf(
            EscaparateSocial("numero", buildJsonObject { put("numero", JsonPrimitive("1234567")); put("etiqueta", JsonPrimitive("Pelis")) }),
            EscaparateSocial("texto", buildJsonObject { put("texto", JsonPrimitive("Siempre cabe uno más")) }),
        )
        val guardado = with(guardaEscaparates) { alcance.save(borrador) }!!
        assertEquals(borrador, guardaEscaparates.restore(guardado))
    }

    @Test fun sinBorradorNoSeGuardaNada() {
        assertNull(with(guardaEscaparates) { alcance.save(null) })
    }

    @Test fun lasFotosElegidasVuelvenIgual() {
        val fotos = listOf(FotoEscaparate(12, "a.jpg"), FotoEscaparate(null, "b.jpg"))
        val guardado = with(guardaFotos) { alcance.save(fotos) }!!
        assertEquals(fotos, guardaFotos.restore(guardado))
    }

    @Test fun unGuardadoDanadoNoRompeLaPantalla() {
        assertNull(guardaEscaparates.restore("no es json"))
        assertNull(guardaFotos.restore("[{"))
    }
}

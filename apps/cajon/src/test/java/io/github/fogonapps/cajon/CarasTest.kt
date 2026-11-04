package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.Caras
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Estados
import io.github.fogonapps.cajon.datos.Obra
import io.github.fogonapps.cajon.datos.Tipos
import org.junit.Assert.assertEquals
import org.junit.Test

class CarasTest {
    private fun obra(id: String, vararg g: String, tipo: String = Tipos.PELICULA, cambio: Long = 0, estado: String = Estados.TERMINADO) =
        Obra(id = id, tipo = tipo, titulo = id, generos = g.toList(), estado = estado, cambio = cambio)

    @Test fun lasCarasSiguenAlGenero() {
        assertEquals("miedo", Caras.de(obra("a", "Terror")))
        assertEquals("enamorado", Caras.de(obra("b", "Romance")))
        assertEquals("feliz", Caras.de(obra("c", "Comedy")))
        assertEquals("decidido", Caras.de(obra("d", "Rock", tipo = Tipos.ALBUM)))
        assertEquals("guino", Caras.de(obra("e", tipo = Tipos.ALBUM)))
        assertEquals("normal", Caras.de(null))
    }

    @Test fun mandaLaObraDeLaFraseYSiNoLaUltima() {
        val e = Estado(obras = listOf(obra("vieja", "Terror", cambio = 1), obra("nueva", "Comedia", cambio = 5), obra("quiero", "Drama", cambio = 9, estado = Estados.QUIERO)))
        assertEquals("nueva", Caras.obra(e, null)?.id)
        assertEquals("vieja", Caras.obra(e, "vieja")?.id)
    }
}

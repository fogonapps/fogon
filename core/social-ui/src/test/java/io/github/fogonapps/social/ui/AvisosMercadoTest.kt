package io.github.fogonapps.social.ui

import io.github.fogonapps.social.AvisoSocial
import io.github.fogonapps.social.PersonaSocial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-rCO")
class AvisosMercadoTest {
    @Test fun textosConNombreYExtra() {
        comprobar("regalo", "banner:m7|Cometa", """@ana te regaló "Cometa"""")
        comprobar("mercado_venta", "marco:m7|Cometa|600", """@ana compró "Cometa" · +600 chispas""")
        comprobar("mercado_aprobado", "banner:m7|Cometa|", """"Cometa" ya está a la venta""")
        comprobar("mercado_rechazado", "marco:m7|Cometa|Falta alfa | revisar", """"Cometa" no se aprobó: Falta alfa | revisar · te devolvimos lo que pagaste por publicarlo""")
    }

    @Test fun objetosSinSeparadores() {
        comprobar("regalo", "banner:m7", "@ana te regaló banner:m7")
        comprobar("mercado_venta", "marco:m7", """@ana compró "marco:m7"""")
        comprobar("mercado_aprobado", "banner:m7", """"banner:m7" ya está a la venta""")
        comprobar("mercado_rechazado", "marco:m7", """"marco:m7" no se aprobó · te devolvimos lo que pagaste por publicarlo""")
        val regaloTienda = aviso("regalo", "marco:luna")
        assertNull(articuloDelAviso(regaloTienda))
        assertEquals("@ana te regaló marco:luna", textoAvisoMercado(RuntimeEnvironment.getApplication(), regaloTienda))
    }

    private fun comprobar(tipo: String, objeto: String, esperado: String) {
        val aviso = aviso(tipo, objeto)
        assertEquals(esperado, textoAvisoMercado(RuntimeEnvironment.getApplication(), aviso))
        assertEquals(7L, articuloDelAviso(aviso))
    }

    private fun aviso(tipo: String, objeto: String) =
        AvisoSocial(1, tipo, PersonaSocial("ana"), objeto, cuando = 0)
}

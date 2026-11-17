package io.github.fogonapps.perfil

import androidx.compose.ui.graphics.Color
import io.github.fogonapps.identidad.Tintas
import org.junit.Assert.assertEquals
import org.junit.Test

class NivelPerfilTest {
    @Test fun coloresPorDecenaYReinicio() {
        val colores = listOf(Color.Gray, Tintas.Rojo, Tintas.Naranja, Tintas.Amarillo, Tintas.Verde,
            Tintas.Azul, Tintas.Violeta, Tintas.Rosa, Tintas.Cafe, Tintas.Dorado)
        colores.forEachIndexed { decena, color ->
            assertEquals(color, tintaNivel(decena * 10, Color.Gray))
            assertEquals(color, tintaNivel(decena * 10 + 9, Color.Gray))
            assertEquals(color, tintaNivel(decena * 10 + 100, Color.Gray))
        }
        assertEquals(Color.Gray, tintaNivel(-1, Color.Gray))
    }
    @Test fun formaPorCentena() {
        listOf(0, 99, 200, 299).forEach { assertEquals(0, formaNivel(it)) }
        listOf(100, 199, 300, 399).forEach { assertEquals(1, formaNivel(it)) }
        assertEquals(0, formaNivel(-1))
    }
}

package io.github.fogonapps.identidad

import org.junit.Assert.assertEquals
import org.junit.Test

/** El lector de pantalla nombra los colores de Apariencia en vez de leer su código. */
class NombreColorTest {
    @Test fun papelesYTintasConNombre() {
        assertEquals(FamiliaColor.NEGRO to LuzColor.MEDIO, describirColor(0x0E0E10))
        assertEquals(FamiliaColor.BLANCO to LuzColor.MEDIO, describirColor(0xF3F2EE))
        assertEquals(FamiliaColor.ROJO, describirColor(0xE53935).first)
        assertEquals(FamiliaColor.AZUL to LuzColor.MUY_OSCURO, describirColor(0x0B0F2A))
        assertEquals(FamiliaColor.GRIS, describirColor(0x808080).first)
        assertEquals(FamiliaColor.NARANJA, describirColor(0xFF7A1A).first)
        assertEquals(FamiliaColor.VERDE, describirColor(0x2E7D32).first)
    }
}

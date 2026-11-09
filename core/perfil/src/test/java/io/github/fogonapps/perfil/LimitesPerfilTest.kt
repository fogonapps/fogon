package io.github.fogonapps.perfil

import org.junit.Assert.assertEquals
import org.junit.Test

class LimitesPerfilTest {
    @Test fun conservaNombresCortosYVacios() {
        listOf("", "Ángel", "𝔄𝔫𝔤𝔢𝔩").forEach { assertEquals(it, recortarNombrePerfil(it)) }
    }

    @Test fun recortaCuarentaLetrasSinPartirParesSustitutos() {
        val nombre = "𝓐".repeat(40)
        assertEquals(nombre, recortarNombrePerfil(nombre + "𝓑"))
        assertEquals(40, recortarNombrePerfil("a".repeat(41)).length)
    }

    @Test fun conservaElLimiteExactoConLetrasMezcladas() {
        val nombre = "a𝓐".repeat(20)
        assertEquals(nombre, recortarNombrePerfil(nombre))
        assertEquals(nombre, recortarNombrePerfil(nombre + "z"))
    }

    @Test fun bioUsaElLimiteDeDoscientasUnidadesDelEditor() {
        assertEquals("", recortarBioPerfil(""))
        assertEquals("a".repeat(200), recortarBioPerfil("a".repeat(201)))
        assertEquals("a".repeat(200), recortarBioPerfil("a".repeat(200)))
    }
}

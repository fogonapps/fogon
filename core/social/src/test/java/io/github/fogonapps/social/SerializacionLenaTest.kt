package io.github.fogonapps.social

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class SerializacionLenaTest {
    @Test fun cuentaRecibeNivelYPreferencias() {
        val base = """{"usuario":"prueba"}"""
        val anterior = JsonSocial.decodeFromString<MiCuenta>(base)
        assertEquals("", anterior.nivelLena)
        assertFalse(anterior.lenaVisible)
        assertFalse(anterior.lenaPreguntada)

        val nuevo = """{"usuario":"prueba","nivelLena":"brasa","lena_visible":true,"lena_preguntada":true}"""
        val cuenta = JsonSocial.decodeFromString<MiCuenta>(nuevo)
        assertEquals("brasa", cuenta.nivelLena)
        assertTrue(cuenta.lenaVisible)
        assertTrue(cuenta.lenaPreguntada)
        assertEquals(JsonSocial.parseToJsonElement(nuevo), JsonSocial.parseToJsonElement(JsonSocial.encodeToString(MiCuenta.serializer(), cuenta)))
        assertEquals(JsonSocial.parseToJsonElement(base), JsonSocial.parseToJsonElement(JsonSocial.encodeToString(MiCuenta.serializer(), anterior)))
    }

    @Test fun perfilRecibeSoloElNivelVisible() {
        val base = """{"persona":{"usuario":"prueba"}}"""
        val anterior = JsonSocial.decodeFromString<PerfilSocial>(base)
        assertEquals("", anterior.nivelLena)
        assertFalse(JsonSocial.parseToJsonElement(JsonSocial.encodeToString(PerfilSocial.serializer(), anterior)).jsonObject.containsKey("nivelLena"))

        val nuevo = """{"persona":{"usuario":"prueba"},"nivelLena":"fogata"}"""
        val perfil = JsonSocial.decodeFromString<PerfilSocial>(nuevo)
        assertEquals("fogata", perfil.nivelLena)
        assertEquals(JsonSocial.parseToJsonElement(nuevo), JsonSocial.parseToJsonElement(JsonSocial.encodeToString(PerfilSocial.serializer(), perfil)))
    }
}

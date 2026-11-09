package io.github.fogonapps.cucha

import io.github.fogonapps.cucha.datos.*
import org.junit.Assert.*
import org.junit.Test

class PerfilLocalTest {
    @Test fun respaldoAnteriorConservaNombreDatosMetasYComidas() {
        val anterior = """{"listo":true,"nombre":"Ana","perfil":{"edad":42,"pesoKg":68.0},"metas":{"kcal":2100,"manual":true},"dias":{"2026-11-01":{"aguaMl":500}}}"""
        val estado = requireNotNull(leerRespaldoCucha(anterior))
        assertEquals("Ana", estado.nombre)
        assertEquals(42, estado.perfil.edad)
        assertEquals(68.0, estado.perfil.pesoKg, 0.0)
        assertEquals(2100, estado.metas.kcal)
        assertTrue(estado.metas.manual)
        assertEquals(500, estado.dias.getValue("2026-11-01").aguaMl)
        assertEquals("", estado.bio)
        assertEquals("", estado.fotoPerfil)
        assertEquals("", estado.portadaPerfil)
    }

    @Test fun respaldoIncluyePresentacionSinCambiarDatosNutricionales() {
        val original = Estado(listo = true, nombre = "Ana", bio = "Mi cocina", fotoPerfil = "Zm90bw==", portadaPerfil = "cG9ydGFkYQ==",
            perfil = Perfil(edad = 42), metas = Metas(kcal = 2100, manual = true))
        val restaurado = leerRespaldoCucha(JsonCucha.encodeToString(Estado.serializer(), original))
        assertEquals(original, restaurado)
    }
}

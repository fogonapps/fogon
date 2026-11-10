package io.github.fogonapps.social

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CompatibilidadTest {
    @Test fun descriptoresConservanNombresAnterioresYCamposJson() {
        val contratos: List<Pair<String, KSerializer<*>>> = listOf(
            "FactorMfa" to FactorMfa.serializer(), "InscripcionMfa" to InscripcionMfa.serializer(),
            "CodigosMfa" to CodigosMfa.serializer(), "ElevacionMfa" to ElevacionMfa.serializer(),
            "MiembroEquipo" to MiembroEquipo.serializer(), "ContenidoEquipo" to ContenidoEquipo.serializer(),
            "SancionEquipo" to SancionEquipo.serializer(), "CambioEquipo" to CambioEquipo.serializer(),
            "EntradaRegistro" to EntradaRegistro.serializer(), "ValorClave" to ValorClave.serializer(),
            "Cifrado.Propias" to Cifrado.Propias.serializer(),
        )
        contratos.forEach { (nombre, serializador) ->
            assertEquals("io.github.fogonapps.cajon.datos.$nombre", serializador.descriptor.serialName)
        }
        assertEquals("""{"codigo":"123456","recuperacion":"alternativo"}""",
            Json.encodeToString(FactorMfa.serializer(), FactorMfa("123456", "alternativo")))
        assertEquals("""{"cifrar":"c","firmar":"f"}""",
            Json.encodeToString(Cifrado.Propias.serializer(), Cifrado.Propias("c", "f")))
    }

    @Test fun instantaneaLeeFormatoAnteriorYConservaBytes() {
        val anterior = """{"social":{"tipo":"texto","texto":"cifrado"},"social.iv":{"tipo":"texto","texto":"iv"},"pendiente":{"tipo":"booleano","texto":"true"},"conjunto":{"tipo":"conjunto","conjunto":["a","b"]}}""".encodeToByteArray()
        val valores = abrirPreferenciasClaves(anterior)
        assertEquals("iv", valores["social.iv"])
        assertEquals(true, valores["pendiente"])
        assertEquals(setOf("a", "b"), valores["conjunto"])
        assertArrayEquals(anterior, copiarPreferenciasClaves(valores))
    }

    @Test fun permisosYVencimientoConservanLimites() {
        assertFalse(necesitaElevar(true, 1000, 999))
        assertTrue(necesitaElevar(true, 1000, 1000))
        assertTrue(necesitaElevar(false, 1000, 999))
        assertTrue(puedeRestaurar("retirado", setOf("gestionar_publicaciones")))
        assertFalse(puedeRestaurar("visible", setOf("gestionar_publicaciones")))
        assertFalse(puedeRestaurar("retirado", emptySet()))
    }
}

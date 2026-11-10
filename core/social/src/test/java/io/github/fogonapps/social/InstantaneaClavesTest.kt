package io.github.fogonapps.social

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class InstantaneaClavesTest {
    @Test fun instantaneaDeClavesConservaTodosLosTiposYValoresCifrados() {
        val valores = mapOf<String, Any>(
            "social" to "texto-cifrado", "social.iv" to "iv-cifrado", "llave_cajon" to "llave-cifrada",
            "chat_llaves" to "chat-cifrado", "ultima" to 1234567890123L, "version_cine" to 2,
            "perfil_red_pendiente" to true, "desconocida" to 1.5f,
            "portadas_revisadas" to setOf("uno", "dos"), "vacio" to emptySet<String>(),
        )
        assertEquals(valores, abrirPreferenciasClaves(copiarPreferenciasClaves(valores)))
    }
}

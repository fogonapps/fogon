package io.github.fogonapps.ahorrador

import io.github.fogonapps.ahorrador.datos.Estado
import io.github.fogonapps.ahorrador.datos.Meta
import io.github.fogonapps.ahorrador.ui.escaparatesMetas
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class EscaparatesTest {
    @Test fun metasNoCompartenMontosNombresNiIdentificadores() {
        val e = Estado(metas = listOf(Meta("id-privado", "Nombre privado", 123456789)))
        val escaparate = escaparatesMetas(e) { 0.423 }.single()
        assertEquals("alcancia:meta", escaparate.tipo)
        assertEquals(setOf("numero", "etiqueta"), escaparate.datos.keys)
        assertEquals(42, escaparate.datos["numero"]!!.jsonPrimitive.int)
        val json = escaparate.datos.toString()
        assertFalse(json.contains("privado")); assertFalse(json.contains("123456789"))
        assertEquals(100, escaparatesMetas(e) { 5.0 }.single().datos["numero"]!!.jsonPrimitive.int)
        assertEquals(0, escaparatesMetas(e) { -1.0 }.single().datos["numero"]!!.jsonPrimitive.int)
    }
}

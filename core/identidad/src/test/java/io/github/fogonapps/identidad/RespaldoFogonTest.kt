package io.github.fogonapps.identidad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RespaldoFogonTest {
    @Test fun leeLasPartesDeCadaApp() {
        val p = RespaldoFogon.partes("""{"fogon":1,"fecha":"2026-10-24","apps":{"forzudo":{"v":3,"sessions":[]},"ahorrador":{"cuentas":[{"id":"a"}]}}}""")!!
        assertEquals(setOf("forzudo", "ahorrador"), p.keys)
        assertEquals("""{"v":3,"sessions":[]}""", p["forzudo"])
    }

    @Test fun rechazaLoQueNoEsUnRespaldoDeTodoFogon() {
        assertNull(RespaldoFogon.partes("""{"v":3,"sessions":[]}"""))
        assertNull(RespaldoFogon.partes("""{"fogon":2,"apps":{"forzudo":{}}}"""))
        assertNull(RespaldoFogon.partes("""{"fogon":1,"apps":{}}"""))
        assertNull(RespaldoFogon.partes("no es json"))
    }
}

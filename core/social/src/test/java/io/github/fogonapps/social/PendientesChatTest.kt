package io.github.fogonapps.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendientesChatTest {
    @Test fun dosQueFallanVuelvenEnElOrdenEnQueSeEscribieron() {
        val p = PendientesChat()
        // B falla primero, pero A se escribió antes.
        p.guardar("ana", "yo", escrito = 2, texto = "B", responde = null)
        p.guardar("ana", "yo", escrito = 1, texto = "A", responde = null)
        assertEquals("A B", p.tomar("ana", "yo")?.texto)
        assertNull(p.tomar("ana", "yo"))
    }

    @Test fun conEnviosEnCursoEsperaYVuelveTodoJuntoEnOrden() {
        val p = PendientesChat()
        p.empezar("ana"); p.empezar("ana")
        // A falla mientras B sigue enviándose: todavía no vuelve.
        p.guardar("ana", "yo", 1, "A", null); p.terminar("ana")
        assertNull(p.estado.value["ana"])
        assertNull(p.tomar("ana", "yo"))
        // B también falla: vuelven los dos, A antes que B.
        p.guardar("ana", "yo", 2, "B", null); p.terminar("ana")
        assertEquals("A B", p.estado.value["ana"]?.texto)
        assertEquals("A B", p.tomar("ana", "yo")?.texto)
    }

    @Test fun noDevuelveLoDeOtraCuentaNiSinSesion() {
        val p = PendientesChat()
        p.guardar("ana", "yo", 1, "hola", null)
        assertNull(p.tomar("ana", "otra"))
        assertNull(p.tomar("ana", "yo"))
        p.guardar("ana", "yo", 1, "hola", null)
        assertNull(p.tomar("ana", null))
    }

    @Test fun otraCuentaReemplazaLoAnterior() {
        val p = PendientesChat()
        p.guardar("ana", "yo", 1, "de antes", null)
        p.guardar("ana", "otra", 2, "nuevo", null)
        assertEquals("nuevo", p.tomar("ana", "otra")?.texto)
    }

    @Test fun seOlvidaAlBorrarOBloquearYNoGuardaVacios() {
        val p = PendientesChat()
        p.guardar("ana", "yo", 1, "hola", null)
        p.guardar("beto", "yo", 1, "chao", null)
        p.guardar("caro", "yo", 1, "   ", null)
        p.olvidar("ana")
        assertNull(p.tomar("ana", "yo"))
        assertNull(p.tomar("caro", "yo"))
        p.olvidarTodo()
        assertNull(p.tomar("beto", "yo"))
    }

    @Test fun alBloquearOBorrarCambiaLaCuentaDeOlvidosAunqueNoHayaNadaGuardado() {
        // Lo escrito ya volvió al campo (no queda nada guardado): igual la pantalla debe enterarse para vaciarlo.
        val p = PendientesChat()
        p.guardar("ana", "yo", 1, "Qq", null)
        assertEquals("Qq", p.tomar("ana", "yo")?.texto)
        assertEquals(0, p.vecesOlvidada("ana"))
        p.olvidar("ana")
        assertEquals(1, p.vecesOlvidada("ana"))
        assertEquals(0, p.vecesOlvidada("beto"))
        p.olvidarTodo()
        assertEquals(2, p.vecesOlvidada("ana"))
        assertEquals(1, p.vecesOlvidada("beto"))
    }
}

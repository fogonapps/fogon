package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.Cifrado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CifradoTest {
    private val ana = Cifrado.nuevas()
    private val beto = Cifrado.nuevas()
    private val caro = Cifrado.nuevas()

    @Test fun abreSoloQuienDebe() {
        val (sobre, firma) = Cifrado.cerrar("hola, ¿viste Matrix?".toByteArray(), "ana", "beto", Cifrado.publicas(beto), ana)
        assertTrue("Matrix" !in sobre)                                   // el servidor no ve el texto
        assertEquals("hola, ¿viste Matrix?", String(Cifrado.abrir(sobre, firma, "ana", "beto", Cifrado.publicas(ana), beto)))
        // Caro no lo abre, ni con el sobre en la mano.
        assertTrue(runCatching { Cifrado.abrir(sobre, firma, "ana", "beto", Cifrado.publicas(ana), caro) }.isFailure)
        // Reenviado como si fuera de otra persona, o para otra: no pasa (el contexto ata a los dos).
        assertTrue(runCatching { Cifrado.abrir(sobre, firma, "caro", "beto", Cifrado.publicas(ana), beto) }.isFailure)
        // Con la firma de otro, tampoco.
        val (_, otraFirma) = Cifrado.cerrar("x".toByteArray(), "caro", "beto", Cifrado.publicas(beto), caro)
        assertTrue(runCatching { Cifrado.abrir(sobre, otraFirma, "ana", "beto", Cifrado.publicas(ana), beto) }.isFailure)
    }

    @Test fun numeroDeSeguridadIgualEnLosDos() {
        val a = Cifrado.numeroSeguridad(Cifrado.publicas(ana), Cifrado.publicas(beto))
        assertEquals(a, Cifrado.numeroSeguridad(Cifrado.publicas(beto), Cifrado.publicas(ana)))
        assertNotEquals(a, Cifrado.numeroSeguridad(Cifrado.publicas(ana), Cifrado.publicas(caro)))
        assertEquals(6, a.split(" ").size)
    }
}

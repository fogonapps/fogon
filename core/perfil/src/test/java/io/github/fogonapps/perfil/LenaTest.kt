package io.github.fogonapps.perfil

import org.junit.Assert.*
import org.junit.Test

class LenaTest {
    @Test fun cadaNivelTieneSuNombreYSilueta() {
        val esperados = mapOf(
            "chispa" to (R.string.lena_chispa to FormaLena.CHISPA),
            "brasa" to (R.string.lena_brasa to FormaLena.BRASA),
            "fogata" to (R.string.lena_fogata to FormaLena.FOGATA),
            "fogon" to (R.string.lena_fogon to FormaLena.FOGON),
        )
        assertEquals(esperados, esperados.keys.associateWith { codigo ->
            nivelLena(codigo)!!.let { it.nombre to it.forma }
        })
        assertEquals(4, esperados.keys.map { nivelLena(it)?.nombre }.toSet().size)
        assertEquals(4, esperados.keys.map { nivelLena(it)?.forma }.toSet().size)
        assertNull(nivelLena(""))
        assertNull(nivelLena("desconocido"))
    }

    @Test fun preguntaSoloConNivelYSinRespuestaPrevia() {
        NivelLena.entries.forEach {
            assertTrue(debePreguntarLena(it.codigo, preguntada = false))
            assertFalse(debePreguntarLena(it.codigo, preguntada = true))
        }
        assertFalse(debePreguntarLena("", preguntada = false))
        assertFalse(debePreguntarLena("", preguntada = true))
    }
}

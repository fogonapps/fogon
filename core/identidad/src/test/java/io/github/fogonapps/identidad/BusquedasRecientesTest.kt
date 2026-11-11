package io.github.fogonapps.identidad

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class BusquedasRecientesTest {
    @Test fun laMasNuevaPrimeroSinRepetirYConLimite() {
        val dir = Files.createTempDirectory("rec").toFile()
        val r = BusquedasRecientes(java.io.File(dir, "busquedas.txt"), maximo = 3)
        listOf("dune", "a", "  Blade   Runner ", "Dune", "solaris", "alien").forEach(r::anotar)
        assertEquals(listOf("alien", "solaris", "Dune"), r.lista.value)
        // Se conserva al volver a abrir.
        assertEquals(listOf("alien", "solaris", "Dune"), BusquedasRecientes(java.io.File(dir, "busquedas.txt"), maximo = 3).lista.value)
        r.quitar("solaris"); assertEquals(listOf("alien", "Dune"), r.lista.value)
        r.borrarTodas(); assertEquals(emptyList<String>(), BusquedasRecientes(java.io.File(dir, "busquedas.txt")).lista.value)
    }
}

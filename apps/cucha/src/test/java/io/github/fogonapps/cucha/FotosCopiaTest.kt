package io.github.fogonapps.cucha

import io.github.fogonapps.cucha.datos.Estado
import io.github.fogonapps.cucha.datos.JsonCucha
import io.github.fogonapps.cucha.datos.conFotos
import io.github.fogonapps.cucha.datos.sacarFotos
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FotosCopiaTest {
    @Test fun lasFotosDeLasRecetasViajanEnLaCopiaYVuelvenAlOtroTelefono() {
        val origen = Files.createTempDirectory("origen").toFile()
        val destino = Files.createTempDirectory("destino").toFile()
        try {
            val foto = File(origen, "r1.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            val e = Estado(fotosRecetas = mapOf("r1" to foto.toURI().toString(), "r2" to File(origen, "no-existe.jpg").toURI().toString()))
            val json = conFotos(JsonCucha.encodeToString(Estado.serializer(), e), e.fotosRecetas) { it.readBytes() }
            assertTrue("fotosCopia" in json)
            val (limpio, rutas) = sacarFotos(json, File(destino, "recetas"))
            assertFalse("fotosCopia" in limpio)
            assertEquals(setOf("r1"), rutas.keys)
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), File(java.net.URI(rutas.getValue("r1"))).readBytes())
            // El JSON sin fotos se sigue leyendo como antes.
            JsonCucha.decodeFromString(Estado.serializer(), limpio)
            JsonCucha.decodeFromString(Estado.serializer(), json)
        } finally { origen.deleteRecursively(); destino.deleteRecursively() }
    }
}

package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ImagenesPerfilCopiaTest {
    @Test fun laFotoYLaPortadaVuelvenAlReinstalar() {
        val antes = Files.createTempDirectory("antes").toFile()
        val despues = Files.createTempDirectory("despues").toFile()
        try {
            val foto = File(antes, "perfil-foto-1.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val portada = File(antes, "perfil-portada-1.jpg").apply { writeBytes(byteArrayOf(4, 5)) }
            val e = Estado(perfil = Perfil(foto = "file://" + foto.absolutePath, portada = "file://" + portada.absolutePath))
            // Lo que sale a la cuenta (y pasa por JSON, como el sobre cifrado).
            val viaje = JsonCajon.decodeFromString(Estado.serializer(), JsonCajon.encodeToString(Estado.serializer(), conImagenesPerfil(e)))
            antes.deleteRecursively() // reinstalar: los archivos ya no existen
            val r = restaurarImagenesPerfil(viaje, despues)
            assertArrayEquals(byteArrayOf(1, 2, 3), File(r.perfil.foto.removePrefix("file://")).readBytes())
            assertArrayEquals(byteArrayOf(4, 5), File(r.perfil.portada.removePrefix("file://")).readBytes())
            assertEquals("", r.perfil.fotoCopia); assertEquals("", r.perfil.portadaCopia)
            // Una portada de internet no se copia: va su dirección.
            val web = Estado(perfil = Perfil(portada = "https://image.tmdb.org/t/p/w780/a.jpg"))
            assertEquals(web, conImagenesPerfil(web))
        } finally { antes.deleteRecursively(); despues.deleteRecursively() }
    }
}

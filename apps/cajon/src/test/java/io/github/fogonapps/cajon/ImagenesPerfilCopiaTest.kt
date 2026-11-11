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

    @Test fun todaImagenDelTelefonoViajaEnElJson() {
        val antes = Files.createTempDirectory("antes").toFile()
        val despues = Files.createTempDirectory("despues").toFile()
        try {
            val portada = File(antes, "obra1-1.jpg").apply { writeBytes(byteArrayOf(9, 8, 7)) }
            val ruta = "file://" + portada.absolutePath
            val e = Estado(obras = listOf(Obra("o1", Tipos.LIBRO, "Algo", portada = ruta, portadaPropia = true, fondo = ruta)))
            val json = empacarImagenes(JsonCajon.encodeToString(Estado.serializer(), e))
            assertTrue("imagenesCopia" in json)
            antes.deleteRecursively()
            val vuelto = JsonCajon.decodeFromString(Estado.serializer(), desempacarImagenes(json, despues))
            val nueva = vuelto.obras.single().portada
            assertNotEquals(ruta, nueva)
            assertEquals(nueva, vuelto.obras.single().fondo)
            assertArrayEquals(byteArrayOf(9, 8, 7), File(nueva.removePrefix("file://")).readBytes())
            // Sin imágenes, el JSON queda igual.
            val sin = JsonCajon.encodeToString(Estado.serializer(), Estado())
            assertEquals(sin, empacarImagenes(sin))
        } finally { antes.deleteRecursively(); despues.deleteRecursively() }
    }
}

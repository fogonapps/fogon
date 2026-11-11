package io.github.fogonapps.social

import java.net.URI
import java.nio.file.Files
import kotlin.test.*

class PortadasTest {
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)

    @Test fun traeSigueAArchiveYGuarda() {
        val dir = Files.createTempDirectory("portadas").toFile()
        val pedidas = mutableListOf<String>()
        val p = Portadas(dir, traer = { u ->
            pedidas += u.toString()
            if (u.host == "covers.openlibrary.org") 302 to Triple(null, "https://archive.org/download/x/y.jpg", null)
            else if (u.host == "archive.org") 302 to Triple(null, "https://ia800.us.archive.org/x/y.jpg", null)
            else 200 to Triple("image/jpeg", null, jpeg)
        })
        try {
            val (b, tipo) = p.obtener("https://covers.openlibrary.org/b/id/1-M.jpg")!!
            assertContentEquals(jpeg, b); assertEquals("image/jpeg", tipo)
            assertEquals(3, pedidas.size)
            p.obtener("https://covers.openlibrary.org/b/id/1-M.jpg")
            assertEquals(3, pedidas.size) // la segunda vez, de la caché
        } finally { dir.deleteRecursively() }
    }

    @Test fun soloOrigenesConocidosYSoloImagenes() {
        val dir = Files.createTempDirectory("portadas").toFile()
        val p = Portadas(dir, traer = { u ->
            if (u.host == "coverartarchive.org") 302 to Triple(null, "https://169.254.169.254/latest/meta-data", null)
            else 200 to Triple("text/html", null, "<html>".toByteArray())
        })
        try {
            assertEquals(400, assertFailsWith<Problema> { p.obtener("https://rastreo.example/a.jpg") }.codigo)
            assertEquals(400, assertFailsWith<Problema> { p.obtener("http://covers.openlibrary.org/b/id/1-M.jpg") }.codigo)
            assertEquals(400, assertFailsWith<Problema> { p.obtener("https://covers.openlibrary.org:8443/b/id/1-M.jpg") }.codigo)
            // Una redirección a otro lado se corta; una página que no es imagen no se guarda.
            assertNull(p.obtener("https://coverartarchive.org/release-group/x/front-250"))
            assertNull(p.obtener("https://covers.openlibrary.org/b/id/2-M.jpg"))
            assertFalse(Portadas.destinoValido(URI("https://archive.org.rastreo.example/a")))
        } finally { dir.deleteRecursively() }
    }
}

package io.github.fogonapps.social

import java.io.File
import java.nio.file.Files
import kotlin.test.*

/** Banners: color gratis, animados pagos, foto propia (500) y banner animado propio (5000). */
class BannersTest {
    private fun conSocial(prueba: (Social, Base, Long) -> Unit) {
        val dir = Files.createTempDirectory("banners").toFile()
        val b = Base(File(dir, "social.db").path)
        try {
            val s = Social(b, File(dir, "fotos"), { 2_000_000_000_000L })
            val yo = s.cuentaDe(s.crearCuenta(NuevaCuenta("bannerista")).token)!!
            b.usar { cambiar("INSERT INTO chispas(cuenta,cantidad,motivo,cuando) VALUES(?,20000,'prueba',1)", yo) }
            prueba(s, b, yo)
        } finally { b.cerrar(); dir.deleteRecursively() }
    }

    @Test fun preciosDeLaFotoYDelAnimadoPropio() {
        assertEquals(2500, TemasPerfil.precio("portada:propia"))
        assertEquals(5000, TemasPerfil.precio("portada:animada"))
        TemasPerfil.BANNERS_ANIMADOS.forEach { assertEquals(600, TemasPerfil.precio("banner:$it")) }
    }

    @Test fun colorGratisSeGuardaYSeValida() = conSocial { s, _, yo ->
        s.ponerTema(yo, "banner:papel~azul")
        assertEquals("banner:papel", s.temas(yo).banner)
        assertEquals("azul", s.temas(yo).colorBanner)
        // Un color fuera de la lista o un banner inexistente no pasan.
        assertEquals(400, assertFailsWith<Problema> { s.ponerTema(yo, "banner:papel~dorado-brillante") }.codigo)
        assertEquals(400, assertFailsWith<Problema> { s.ponerTema(yo, "banner:inventado~azul") }.codigo)
        // «original» quita el color.
        s.ponerTema(yo, "banner:papel~original")
        assertEquals("", s.temas(yo).colorBanner)
    }

    @Test fun perfilViejoSinColorSigueIgual() = conSocial { s, _, yo ->
        s.ponerTema(yo, "banner:papel")
        assertEquals("banner:papel", s.temas(yo).banner)
        assertEquals("", s.temas(yo).colorBanner)
    }

    @Test fun bannerAnimadoSoloSiSeCompro() = conSocial { s, _, yo ->
        val animado = "banner:" + TemasPerfil.BANNERS_ANIMADOS.first()
        assertFails { s.ponerTema(yo, animado) }
        s.reclamarTema(yo, animado)
        s.ponerTema(yo, "$animado~verde")
        assertEquals(animado, s.temas(yo).banner)
        assertEquals("verde", s.temas(yo).colorBanner)
    }

    @Test fun animadoPropioExigeCompraYArchivoValido() = conSocial { s, _, yo ->
        val gif = byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61) + ByteArray(20)
        assertEquals(403, assertFailsWith<Problema> { s.subirPortadaAnimada(yo, gif) }.codigo)
        s.reclamarTema(yo, "portada:animada")
        // Una foto JPEG no es una portada animada; un archivo enorme tampoco pasa.
        assertEquals(400, assertFailsWith<Problema> { s.subirPortadaAnimada(yo, byteArrayOf(-1, -40, -1, -32) + ByteArray(40)) }.codigo)
        assertEquals(413, assertFailsWith<Problema> { s.subirPortadaAnimada(yo, ByteArray(MAX_PORTADA_ANIMADA + 1)) }.codigo)
    }

    @Test fun videoCortoComoBannerAnimado() = conSocial { s, _, yo ->
        s.reclamarTema(yo, "portada:animada")
        val corto = VideosTest().mp4(segundos = 6, audio = null)
        val nombre = s.subirPortadaAnimada(yo, corto).nombre
        assertEquals(nombre, s.temas(yo).banner)
        assertEquals("video_duracion", assertFailsWith<Problema> { s.subirPortadaAnimada(yo, VideosTest().mp4(segundos = 20)) }.mensaje)
    }
}

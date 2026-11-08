package io.github.fogonapps.social

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PrivacidadTest {
    private val dia = 24 * 3600_000L

    @Test fun laCuentaGuardaLaVersionAceptadaYSoloAceptaLaVigente() {
        val dir = Files.createTempDirectory("privacidad").toFile()
        val s = Social(Base(java.io.File(dir, "s.db").path), java.io.File(dir, "fotos"))
        val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("ana")).token)!!
        assertEquals("", s.miCuenta(ana).politica)
        assertEquals("politica", assertFailsWith<Problema> { s.aceptarPolitica(ana, "2026-11-03") }.mensaje)
        s.aceptarPolitica(ana, Social.POLITICA)
        assertEquals(Social.POLITICA, s.miCuenta(ana).politica)
    }

    @Test fun losReportesResueltosYLasDecisionesSeBorranAlAnioYLoAbiertoNo() {
        val dir = Files.createTempDirectory("privacidad").toFile()
        val base = Base(java.io.File(dir, "s.db").path)
        var hoy = 1_000 * dia
        val s = Social(base, java.io.File(dir, "fotos"), { hoy })
        base.usar {
            cambiar("INSERT INTO reportes(de, que, objeto, motivo, cuando, estado) VALUES(NULL, 'cuenta', '1', 'spam', ?, 'resuelto')", hoy)
            cambiar("INSERT INTO reportes(de, que, objeto, motivo, cuando, estado) VALUES(NULL, 'cuenta', '2', 'spam', ?, 'abierto')", hoy)
            cambiar("INSERT INTO decisiones(admin, que, objeto, decision, regla, cuando) VALUES(1, 'cuenta', '1', 'nada', '', ?)", hoy)
        }
        hoy += 364 * dia
        s.limpiarSeguridad()
        assertEquals(2, base.usar { leer("SELECT COUNT(*) FROM reportes") { it.getInt(1) }.first() })
        hoy += 2 * dia
        s.limpiarSeguridad()
        assertEquals(listOf("abierto"), base.usar { leer("SELECT estado FROM reportes") { it.getString(1) } })
        assertEquals(0, base.usar { leer("SELECT COUNT(*) FROM decisiones") { it.getInt(1) }.first() })
    }
}

class RecuperacionTest {
    @Test fun losCodigosNuevosSonFacilesDeEscribirYSeNormalizan() {
        val c = codigoRecuperacion()
        kotlin.test.assertTrue(Regex("[0-9A-HJKMNP-TV-Z]{4}(-[0-9A-HJKMNP-TV-Z]{4}){3}").matches(c), c)
        assertEquals(normalizarRecuperacion(c), normalizarRecuperacion(c.lowercase().replace("-", " ")))
        assertEquals("0111ABCD", normalizarRecuperacion("oIl1-abcd"))
    }
}

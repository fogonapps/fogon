package io.github.fogonapps.social

import java.nio.file.Files
import kotlin.test.*

class RespaldoTest {
    @Test fun copiaCifradaPorAppConLaLlaveComun() {
        val dir = Files.createTempDirectory("respaldo-test").toFile()
        val b = Base(java.io.File(dir, "social.db").path)
        try {
            var reloj = 2_000_000_000_000L
            val s = Social(b, java.io.File(dir, "fotos"), { reloj })
            val yo = s.cuentaDe(s.crearCuenta(NuevaCuenta("ana")).token)!!
            assertEquals("", s.respaldo(yo, "forzudo").sobre)
            // La llave es la misma del cajón: la ponga quien la ponga, todas las apps la ven.
            s.guardarLlave(yo, "envuelta")
            assertEquals("envuelta", s.respaldo(yo, "cucha").llave)
            val v1 = s.guardarRespaldo(yo, "forzudo", CajonCifrado("sobre-1"), 0)
            assertEquals("sobre-1", s.respaldo(yo, "forzudo").sobre)
            assertEquals("", s.respaldo(yo, "alcancia").sobre)
            // Otro teléfono con una versión vieja no pisa la copia.
            assertEquals(409, assertFailsWith<Problema> { s.guardarRespaldo(yo, "forzudo", CajonCifrado("viejo"), 0) }.codigo)
            reloj += 1000
            val v2 = s.guardarRespaldo(yo, "forzudo", CajonCifrado("sobre-2"), v1)
            assertTrue(v2 > v1)
            assertFailsWith<IllegalArgumentException> { s.respaldo(yo, "cajon") }
            assertEquals(413, assertFailsWith<Problema> { s.guardarRespaldo(yo, "cucha", CajonCifrado("x".repeat(12_000_001)), 0) }.codigo)
        } finally { b.cerrar(); dir.deleteRecursively() }
    }
}

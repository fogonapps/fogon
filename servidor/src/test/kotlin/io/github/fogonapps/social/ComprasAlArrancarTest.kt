package io.github.fogonapps.social

import java.io.File
import java.nio.file.Files
import kotlin.test.*

/** Reiniciar el servidor nunca borra compras vigentes (portadas propias, banners, artículos del mercado). */
class ComprasAlArrancarTest {
    @Test fun reiniciarConservaLasCompras() {
        val dir = Files.createTempDirectory("arranque").toFile()
        val ruta = File(dir, "social.db").path
        val comprado = listOf("portada:propia", "portada:animada", "banner:deriva", "banner:m7", "marco:m9", "nieve", "marco:doble")
        var b = Base(ruta)
        val yo: Long
        try {
            val s = Social(b, File(dir, "fotos"), { 2_000_000_000_000L })
            yo = s.cuentaDe(s.crearCuenta(NuevaCuenta("compradora")).token)!!
            b.transaccion {
                comprado.forEach { cambiar("INSERT INTO temas(cuenta,tema,cuando) VALUES(?,?,1)", yo, it) }
                cambiar("INSERT INTO temas(cuenta,tema,cuando) VALUES(?,?,1)", yo, "tema-viejo-retirado")
                cambiar("UPDATE cuentas SET marco='m9' WHERE id=?", yo)
            }
        } finally { b.cerrar() }
        b = Base(ruta)
        try {
            val temas = b.usar { leer("SELECT tema FROM temas WHERE cuenta=?", yo) { it.getString(1) } }.toSet()
            assertEquals(comprado.toSet(), temas)
            assertEquals("m9", b.usar { leer("SELECT marco FROM cuentas WHERE id=?", yo) { it.getString(1) }.single() })
        } finally { b.cerrar(); dir.deleteRecursively() }
    }
}

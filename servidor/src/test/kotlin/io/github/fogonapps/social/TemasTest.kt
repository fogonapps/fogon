package io.github.fogonapps.social

import java.io.File
import java.nio.file.Files
import kotlin.test.*

class TemasTest {
    @Test fun reclamarYPonerseUnTemaQueVenLosDemas() {
        val dir = Files.createTempDirectory("temas").toFile()
        val b = Base(File(dir, "social.db").path)
        try {
            val s = Social(b, File(dir, "fotos"), { 2_000_000_000_000L })
            val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("anabel")).token)!!
            // Sin reclamarlo no se puede poner; uno que no existe no se reclama.
            assertEquals(403, assertFailsWith<Problema> { s.ponerTema(ana, "neon") }.codigo)
            assertEquals(404, assertFailsWith<Problema> { s.reclamarTema(ana, "inventado") }.codigo)
            s.reclamarTema(ana, "neon"); s.reclamarTema(ana, "neon")
            assertEquals(TemasCuenta(listOf("neon"), ""), s.temas(ana))
            s.ponerTema(ana, "neon")
            assertEquals("neon", s.temas(ana).puesto)
            assertEquals("neon", s.buscar(null, "anabel").first().tema)
            s.ponerTema(ana, "")
            assertEquals("", s.temas(ana).puesto)
        } finally { b.cerrar(); dir.deleteRecursively() }
    }
}

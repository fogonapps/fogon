package io.github.fogonapps.social

import java.io.File
import java.nio.file.Files
import kotlin.test.*

class TemasTest {
    @Test fun chispasCompraYTemaQueVenLosDemas() {
        val dir = Files.createTempDirectory("temas").toFile()
        val b = Base(File(dir, "social.db").path)
        var reloj = 2_000_000_000_000L
        try {
            val s = Social(b, File(dir, "fotos"), { reloj })
            val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("anabel")).token)!!
            // La bienvenida una sola vez; la del día, una vez por día.
            assertEquals(TemasPerfil.BIENVENIDA, s.temas(ana).chispas)
            assertTrue(s.temas(ana).diario)
            s.cobrarDiario(ana); s.cobrarDiario(ana)
            assertEquals(TemasPerfil.BIENVENIDA + TemasPerfil.DIARIO, s.temas(ana).chispas)
            assertFalse(s.temas(ana).diario)
            reloj += 24 * 3600_000L
            assertTrue(s.temas(ana).diario)
            // Sin tenerlo no se pone; lo que no existe no se compra; comprar descuenta (una sola vez).
            assertEquals(403, assertFailsWith<Problema> { s.ponerTema(ana, "nieve") }.codigo)
            assertEquals(404, assertFailsWith<Problema> { s.reclamarTema(ana, "inventado") }.codigo)
            s.reclamarTema(ana, "nieve"); s.reclamarTema(ana, "nieve")
            assertEquals(TemasPerfil.BIENVENIDA + TemasPerfil.DIARIO - TemasPerfil.FONDOS.getValue("nieve"), s.temas(ana).chispas)
            // Lo que no alcanza no se compra.
            assertEquals(402, assertFailsWith<Problema> { s.reclamarTema(ana, "ciudad") }.codigo)
            s.ponerTema(ana, "nieve")
            // Con las chispas de un día más alcanza para el marco.
            assertEquals(402, assertFailsWith<Problema> { s.reclamarTema(ana, "marco:doble") }.codigo)
            reloj += 24 * 3600_000L; s.cobrarDiario(ana)
            s.reclamarTema(ana, "marco:doble"); s.ponerTema(ana, "marco:doble")
            assertEquals(0, s.temas(ana).chispas)
            val p = s.buscar(null, "anabel").first()
            assertEquals("nieve", p.tema); assertEquals("doble", p.marco)
            s.ponerTema(ana, ""); s.ponerTema(ana, "marco:")
            assertEquals("", s.temas(ana).puesto); assertEquals("", s.temas(ana).marco)
        } finally { b.cerrar(); dir.deleteRecursively() }
    }
}

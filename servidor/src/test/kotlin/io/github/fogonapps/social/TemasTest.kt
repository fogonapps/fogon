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
            // La bienvenida se conserva y la ruta diaria nunca suma.
            assertEquals(TemasPerfil.BIENVENIDA, s.temas(ana).chispas)
            assertFalse(s.temas(ana).diario)
            s.cobrarDiario(ana); s.cobrarDiario(ana)
            assertEquals(TemasPerfil.BIENVENIDA, s.temas(ana).chispas)
            assertFalse(s.temas(ana).diario)
            reloj += 24 * 3600_000L
            assertFalse(s.temas(ana).diario)
            // Con la bienvenida de 500 no alcanza un fondo: se suman 1.000 de prueba (como la bienvenida de antes).
            b.usar { cambiar("INSERT INTO chispas(cuenta,cantidad,motivo,cuando) VALUES(?,1000,'prueba',1)", ana) }
            // Sin tenerlo no se pone; lo que no existe no se compra; comprar descuenta (una sola vez).
            assertEquals(403, assertFailsWith<Problema> { s.ponerTema(ana, "nieve") }.codigo)
            assertEquals(404, assertFailsWith<Problema> { s.reclamarTema(ana, "inventado") }.codigo)
            s.reclamarTema(ana, "nieve"); s.reclamarTema(ana, "nieve")
            assertEquals(TemasPerfil.BIENVENIDA + 1000 - TemasPerfil.FONDOS.getValue("nieve"), s.temas(ana).chispas)
            // Lo que no alcanza no se compra.
            assertEquals(402, assertFailsWith<Problema> { s.reclamarTema(ana, "ciudad") }.codigo)
            s.ponerTema(ana, "nieve")
            // Los días siguientes tampoco conceden chispas.
            assertEquals(402, assertFailsWith<Problema> { s.reclamarTema(ana, "marco:doble") }.codigo)
            reloj += 24 * 3600_000L; s.cobrarDiario(ana)
            assertEquals(402, assertFailsWith<Problema> { s.reclamarTema(ana, "marco:doble") }.codigo)
            assertEquals(300, s.temas(ana).chispas)
            val p = s.buscar(null, "anabel").first()
            assertEquals("nieve", p.tema); assertEquals("", p.marco)
            s.ponerTema(ana, ""); s.ponerTema(ana, "marco:")
            assertEquals("", s.temas(ana).puesto); assertEquals("", s.temas(ana).marco)
        } finally { b.cerrar(); dir.deleteRecursively() }
    }
}

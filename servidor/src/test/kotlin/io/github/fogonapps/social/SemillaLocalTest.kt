package io.github.fogonapps.social

import java.io.File
import java.nio.file.Files
import kotlin.test.*

class SemillaLocalTest {
    @Test fun rechazaDatosNormalesSinAbrirLaBase() {
        val dir = Files.createTempDirectory("semilla-rechazada").toFile()
        try {
            assertFailsWith<IllegalArgumentException> { sembrarSocialLocal(dir) }
            assertFalse(File(dir, "social.db").exists())
        } finally { dir.deleteRecursively() }
    }

    @Test fun semillaRepetibleYCuentaUtilizable() {
        val raiz = Files.createTempDirectory("semilla-local").toFile()
        val dir = File(raiz, "Fogon-local/social-pruebas")
        try {
            val token = sembrarSocialLocal(dir)
            assertEquals(token, sembrarSocialLocal(dir))
            val b = Base(File(dir, "social.db").path)
            try {
                val s = Social(b, File(dir, "fotos"))
                val yo = s.cuentaDe(token)!!
                assertEquals("prueba", s.miCuenta(yo).usuario)
                assertEquals(Social.POLITICA, s.miCuenta(yo).politica)
                assertEquals(3, s.escaparates.cupo(yo).nivel)
                val repisas = s.escaparates.ver(yo, "prueba")
                assertEquals(1, repisas.size)
                s.escaparates.guardar(yo, repisas)
                assertEquals(repisas, s.escaparates.ver(yo, "prueba"))
                val nuevas = repisas + Escaparate("texto", kotlinx.serialization.json.buildJsonObject {
                    put("texto", kotlinx.serialization.json.JsonPrimitive("Otra repisa"))
                })
                s.escaparates.guardar(yo, nuevas)
                assertEquals(nuevas, s.escaparates.ver(yo, "prueba"))
                assertEquals(2, s.mercado.listar(yo).articulos.size)
                b.usar {
                    assertEquals(50000, leer("SELECT SUM(cantidad) FROM chispas WHERE cuenta=?", yo) { it.getInt(1) }.single())
                    assertEquals(2, leer("SELECT COUNT(*) FROM seguir WHERE de=?", yo) { it.getInt(1) }.single())
                }
            } finally { b.cerrar() }
        } finally { raiz.deleteRecursively() }
    }

    @Test fun rechazaBaseExistenteSinMarca() {
        val raiz = Files.createTempDirectory("semilla-sin-marca").toFile()
        val dir = File(raiz, "Fogon-local/social-pruebas").apply { mkdirs() }
        try {
            File(dir, "social.db").writeText("no tocar")
            assertFailsWith<IllegalArgumentException> { sembrarSocialLocal(dir) }
            assertEquals("no tocar", File(dir, "social.db").readText())
        } finally { raiz.deleteRecursively() }
    }
}

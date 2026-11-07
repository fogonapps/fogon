package io.github.fogonapps.social

import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BaseTest {
    @Test fun migraMensajesViejosSinPerderFilasNiRepetirSecuencia() {
        val dir = Files.createTempDirectory("migracion-chat").toFile()
        val ruta = java.io.File(dir, "s.db").path
        try {
            val inicial = Base(ruta)
            val social = Social(inicial, java.io.File(dir, "fotos"))
            val ana = social.cuentaDe(social.crearCuenta(NuevaCuenta("ana")).token)!!
            val beto = social.cuentaDe(social.crearCuenta(NuevaCuenta("beto")).token)!!
            inicial.cerrar()
            DriverManager.getConnection("jdbc:sqlite:$ruta").use { c ->
                c.createStatement().use { s ->
                    s.execute("DROP TABLE mensajes")
                    s.execute("""CREATE TABLE mensajes(id INTEGER PRIMARY KEY,
                        de INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE,
                        para INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE,
                        cuando INTEGER NOT NULL, sobre TEXT NOT NULL, firma TEXT NOT NULL)""")
                    s.execute("CREATE INDEX mensajes_para ON mensajes(para, id)")
                }
                // Insertados fuera de orden: la migración debe respetar el id original.
                for (id in listOf(7, 1, 4)) {
                    c.cambiar("INSERT INTO mensajes VALUES(?, ?, ?, 123, ?, 'firma')", id, beto, ana, "sobre$id")
                }
            }
            val antes = System.currentTimeMillis()
            val migrada = Base(ruta)
            val secuencia = try {
                migrada.usar {
                    assertEquals(listOf(1, 4, 7).map { "$beto:$ana:123:sobre$it:firma" }, leer("SELECT * FROM mensajes ORDER BY id") {
                        "${it.getLong("de")}:${it.getLong("para")}:${it.getLong("cuando")}:${it.getString("sobre")}:${it.getString("firma")}"
                    })
                    assertTrue(leer("SELECT sql FROM sqlite_master WHERE name = 'mensajes'") { it.getString(1) }.single().contains("AUTOINCREMENT"))
                    assertEquals(listOf("para", "id"), leer("PRAGMA index_info(mensajes_para)") { it.getString("name") })
                    leer("SELECT seq FROM sqlite_sequence WHERE name = 'mensajes'") { it.getLong(1) }.single().also {
                        assertTrue(it >= antes + 3 && it > 9)
                        assertEquals(listOf(it - 2, it - 1, it), leer("SELECT id FROM mensajes ORDER BY id") { fila -> fila.getLong(1) })
                    }
                }.also {
                    val llegan = Social(migrada, java.io.File(dir, "fotos"), ahora = { 123L }).recibir(ana, 9)
                    assertEquals(listOf("sobre1", "sobre4", "sobre7"), llegan.map { sobre -> sobre.sobre })
                    assertTrue(llegan.all { sobre -> sobre.id > antes })
                    assertEquals(listOf(it - 2, it - 1, it), llegan.map { sobre -> sobre.id })
                }
            } finally { migrada.cerrar() }
            val reabierta = Base(ruta)
            try {
                reabierta.usar {
                    assertEquals(secuencia, leer("SELECT seq FROM sqlite_sequence WHERE name = 'mensajes'") { it.getLong(1) }.single())
                    assertTrue(insertar("INSERT INTO mensajes(de, para, cuando, sobre, firma) VALUES(?, ?, 124, 'otro', 'firma')", beto, ana) > secuencia)
                    assertTrue(leer("PRAGMA foreign_key_check") { it.getString(1) }.isEmpty())
                }
            } finally { reabierta.cerrar() }
        } finally { dir.deleteRecursively() }
    }
}

package io.github.fogonapps.cucha

import io.github.fogonapps.cucha.datos.*
import io.github.fogonapps.social.MigracionPerfilesTelefono
import io.github.fogonapps.social.Perfiles
import io.github.fogonapps.social.copiarPreferenciasClaves
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MigracionPerfilesTest {
    private fun migracion(raiz: File, corte: String?, cortado: BooleanArray) = MigracionPerfilesTelefono(raiz,
        { copiarPreferenciasClaves(emptyMap<String, Any>()) }, {},
        { fase -> if (corte != null && !cortado[0] && fase == corte) { cortado[0] = true; throw java.io.IOException("cierre: $fase") } },
        { JsonCucha.decodeFromString(Estado.serializer(), it) })

    private fun escenario(corte: String?) {
        val raiz = Files.createTempDirectory("cucha-perfiles").toFile()
        try {
            val foto = File(raiz, "recetas/r1-1.jpg").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
            val estado = Estado(listo = true, nombre = "Ana", dias = mapOf("2026-11-09" to Dia(listOf(Anotado("n1", "almuerzo", "a:papa", "Papa", 100.0, n = Nutrientes(77.0))))),
                recetas = listOf(Receta("r1", "Sudado", propia = true)), pesos = listOf(Peso("2026-11-09", 70.0)), fotosRecetas = mapOf("r1" to foto.toURI().toString()))
            val texto = JsonCucha.encodeToString(Estado.serializer(), estado)
            File(raiz, "datastore").mkdirs(); File(raiz, "datastore/estado.json").writeText(texto)
            val cortado = booleanArrayOf(false)
            if (corte != null) {
                try { migracion(raiz, corte, cortado).ejecutar(); fail("No se alcanzó $corte") } catch (x: java.io.IOException) { assertEquals("cierre: $corte", x.message) }
            }
            migracion(raiz, null, cortado).ejecutar(); migracion(raiz, null, cortado).ejecutar()
            val migrado = JsonCucha.decodeFromString(Estado.serializer(), File(raiz, "perfiles/1/datastore/estado.json").readText())
            // Nada se pierde: diario, recetas, peso y la foto de la receta (con su ruta nueva).
            assertEquals(estado.dias, migrado.dias); assertEquals(estado.recetas, migrado.recetas); assertEquals(estado.pesos, migrado.pesos)
            val ruta = File(java.net.URI(migrado.fotosRecetas.getValue("r1")))
            assertEquals(File(raiz, "perfiles/1/recetas/r1-1.jpg").absolutePath, ruta.absolutePath)
            assertArrayEquals(byteArrayOf(1, 2, 3), ruta.readBytes())
            assertFalse(File(raiz, "datastore").exists())
            assertEquals(texto, File(raiz, "migracion-perfiles-respaldo/archivos/datastore/estado.json").readText())
            val perfiles = Perfiles(raiz)
            assertEquals("1", perfiles.lista.activo)
            MigracionPerfilesTelefono(raiz, { ByteArray(0) }, {}).confirmarArranque()
            assertFalse(File(raiz, "migracion-perfiles-respaldo").exists())
        } finally { raiz.deleteRecursively() }
    }

    @Test fun sinCortes() = escenario(null)
    @Test fun cortesEnCadaFase() = listOf("respaldo", "respaldada", "bloque", "archivo", "copiada", "rutas", "claves", "registrada", "retirada")
        .forEach { escenario(it) }

    @Test fun estadoIlegibleConservaElOrigen() {
        val raiz = Files.createTempDirectory("cucha-perfiles").toFile()
        try {
            File(raiz, "datastore").mkdirs(); File(raiz, "datastore/estado.json").writeText("{roto")
            assertThrows(java.io.IOException::class.java) { migracion(raiz, null, booleanArrayOf(false)).ejecutar() }
            assertEquals("{roto", File(raiz, "datastore/estado.json").readText())
        } finally { raiz.deleteRecursively() }
    }

    @Test fun instalacionNuevaNoMigra() {
        val raiz = Files.createTempDirectory("cucha-perfiles").toFile()
        try {
            migracion(raiz, null, booleanArrayOf(false)).ejecutar()
            assertTrue(File(raiz, "perfiles.json").exists())
            assertEquals("1", Perfiles(raiz).lista.activo)
        } finally { raiz.deleteRecursively() }
    }
}

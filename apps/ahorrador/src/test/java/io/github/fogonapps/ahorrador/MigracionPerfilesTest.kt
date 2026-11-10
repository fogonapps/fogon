package io.github.fogonapps.ahorrador

import io.github.fogonapps.ahorrador.datos.*
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
        { JsonAhorrador.decodeFromString(Estado.serializer(), it) })

    private fun escenario(corte: String?) {
        val raiz = Files.createTempDirectory("alcancia-perfiles").toFile()
        try {
            val estado = Estado(listo = true, nombre = "Ana", cuentas = listOf(Cuenta("c", "Efectivo", TipoCuenta.EFECTIVO, 300_000)),
                movimientos = listOf(Movimiento("m", "2026-11-09", TipoMovimiento.GASTO, 25_000, "c", categoria = "comida")),
                metas = listOf(Meta("v", "Viaje", 1_000_000)))
            val texto = JsonAhorrador.encodeToString(Estado.serializer(), estado)
            File(raiz, "datastore").mkdirs(); File(raiz, "datastore/estado.json").writeText(texto)
            val cortado = booleanArrayOf(false)
            if (corte != null) {
                try { migracion(raiz, corte, cortado).ejecutar(); fail("No se alcanzó $corte") } catch (x: java.io.IOException) { assertEquals("cierre: $corte", x.message) }
            }
            migracion(raiz, null, cortado).ejecutar(); migracion(raiz, null, cortado).ejecutar()
            assertEquals(texto, File(raiz, "perfiles/1/datastore/estado.json").readText())
            assertFalse(File(raiz, "datastore").exists())
            assertEquals("1", Perfiles(raiz).lista.activo)
        } finally { raiz.deleteRecursively() }
    }

    @Test fun sinCortes() = escenario(null)
    @Test fun cortesEnCadaFase() = listOf("respaldo", "respaldada", "bloque", "archivo", "copiada", "rutas", "claves", "registrada", "retirada").forEach { escenario(it) }
}

package io.github.fogonapps.forzudo

import io.github.fogonapps.forzudo.datos.*
import io.github.fogonapps.social.*
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class MigracionPerfilesTest {
    private fun escenario(corte: String?) {
        val raiz = Files.createTempDirectory("forzudo-perfiles").toFile()
        try {
            val estado = Estado(active = EntrenoActivo("activo", "2026-11-09", "lunes", entries = mapOf("press-banca" to listOf(Serie(40.0, 8.0, true)))),
                sessions = listOf(Sesion("anterior", "2026-11-08")), body = listOf(PesoCorporal("2026-11-08", 70.0)))
            val texto = JsonGym.encodeToString(Estado.serializer(), estado)
            File(raiz, "datastore").mkdirs(); File(raiz, "datastore/estado.json").writeText(texto)
            val antiguo = """{"v":2,"sessions":[{"id":"viejo","date":"2020-01-01"}],"weeks":{"2020-01-01":[true]}}"""
            File(raiz, "respaldo-viejo.json").writeText(antiguo)
            var claves = mapOf<String, Any>("token" to "cifrado-de-prueba", "iv" to "iv-de-prueba", "gist" to "gist-de-prueba", "ultima" to 123L)
            val originales = claves
            var destino: Map<String, Any> = emptyMap()
            var cortado = false
            fun migracion(cortar: Boolean) = MigracionPerfiles(raiz, { copiarPreferenciasClaves(claves) }, {
                destino = abrirPreferenciasClaves(it); claves = emptyMap()
            }, { fase -> if (cortar && !cortado && fase == corte) { cortado = true; throw java.io.IOException("cierre: $fase") } })
            if (corte != null) {
                try { migracion(true).ejecutar(); fail("No se alcanzó $corte") } catch (x: java.io.IOException) { assertEquals("cierre: $corte", x.message) }
            }
            migracion(false).ejecutar(); migracion(false).ejecutar()
            assertEquals(texto, File(raiz, "perfiles/1/datastore/estado.json").readText())
            assertEquals(antiguo, File(raiz, "perfiles/1/respaldo-viejo.json").readText())
            assertNotNull(JsonGym.decodeFromString(Estado.serializer(), File(raiz, "perfiles/1/datastore/estado.json").readText()).active)
            assertEquals(originales, destino)
            assertEquals(texto, File(raiz, "migracion-perfiles-respaldo/archivos/datastore/estado.json").readText())
            val perfiles = Perfiles(raiz)
            val otro = perfiles.agregar()!!
            assertFalse(File(perfiles.directorio(otro.id), "datastore/estado.json").exists())
            assertEquals("1", perfiles.lista.activo)
        } finally { raiz.deleteRecursively() }
    }
    @Test fun conservaActivoGistYRespaldoViejo() = escenario(null)
    @Test fun recuperaCierreEnTodasLasFases() {
        listOf("bloque", "archivo", "respaldo", "respaldada", "copiada", "rutas", "claves", "registrada", "retirada", "terminada").forEach(::escenario)
    }
}

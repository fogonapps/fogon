package io.github.fogonapps.social

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Un bloqueo, en cualquiera de los dos sentidos, saca a la otra persona de todos los listados. */
class BloqueosTest {
    private class Entorno : AutoCloseable {
        val dir: File = Files.createTempDirectory("bloqueos").toFile()
        val b = Base(File(dir, "s.db").path)
        val s = Social(b, File(dir, "fotos"))
        fun alta(nombre: String) = s.cuentaDe(s.crearCuenta(NuevaCuenta(nombre)).token)!!
        val ana = alta("ana"); val beto = alta("beto"); val carla = alta("carla")
        /** Como en la semilla local: un «seguir» que quedó aunque haya bloqueo. */
        fun seguirAFuerza(de: Long, a: Long) = b.usar { cambiar("INSERT OR IGNORE INTO seguir(de,a,cuando,aceptada) VALUES(?,?,1,1)", de, a) }
        override fun close() { b.cerrar(); dir.deleteRecursively() }
    }

    private fun resena(clave: String, cuando: Long = 1) = PublicacionSubida(clave, Vis.PUBLICA, cuando, "resena",
        buildJsonObject { put("titulo", "Matrix"); put("fuente", "tmdb:movie:603"); put("texto", "buenísima") })

    @Test fun elFeedNoMuestraANadieBloqueadoAunqueQuedeElSeguir() = Entorno().use { e ->
        e.s.seguir(e.ana, "beto")
        e.s.subirPublicaciones(e.beto, listOf(resena("r:1")))
        e.s.subirPublicaciones(e.ana, listOf(resena("r:2", 2)))
        assertEquals(setOf("ana", "beto"), e.s.actividad(e.ana, null).map { it.autor.usuario }.toSet())
        // Ana bloquea a Beto y el «seguir» vuelve a aparecer (semilla vieja, carrera): igual no sale.
        e.s.bloquear(e.ana, "beto", true); e.seguirAFuerza(e.ana, e.beto)
        assertEquals(listOf("ana"), e.s.actividad(e.ana, null).map { it.autor.usuario })
        assertEquals(listOf("ana"), e.s.actividad(e.ana, null, app = "cajon").map { it.autor.usuario })
        // Al revés: Beto bloquea a Ana.
        e.s.bloquear(e.ana, "beto", false); e.s.bloquear(e.beto, "ana", true); e.seguirAFuerza(e.ana, e.beto)
        assertEquals(listOf("ana"), e.s.actividad(e.ana, null).map { it.autor.usuario })
        e.s.bloquear(e.beto, "ana", false)
        assertEquals(setOf("ana", "beto"), e.s.actividad(e.ana, null).map { it.autor.usuario }.toSet())
    }

    @Test fun comentariosBusquedaResenasYAvisosEnLosDosSentidos() = Entorno().use { e ->
        e.s.subirPublicaciones(e.carla, listOf(resena("c:1")))
        val pub = e.s.explorar(null, null).single().id
        e.s.comentar(e.beto, pub, "me encantó")
        e.s.subirPublicaciones(e.beto, listOf(resena("b:1")))
        e.s.subirPublicaciones(e.ana, listOf(resena("a:1")))
        val deAna = e.s.actividad(e.ana, null).single().id
        e.s.meGusta(e.beto, deAna, true)
        assertEquals(listOf("beto"), e.s.comentarios(e.ana, pub).map { it.autor.usuario })
        assertTrue(e.s.buscar(e.ana, "beto").any { it.usuario == "beto" })
        assertTrue(e.s.resenas(e.ana, "tmdb:movie:603").any { it.autor.usuario == "beto" })
        assertTrue(e.s.avisos(e.ana, null).any { it.de.usuario == "beto" })
        for ((quien, otro) in listOf(e.ana to "beto", e.beto to "ana")) {
            e.s.bloquear(quien, otro, true)
            assertTrue(e.s.comentarios(e.ana, pub).isEmpty())
            assertTrue(e.s.buscar(e.ana, "beto").none { it.usuario == "beto" })
            assertTrue(e.s.resenas(e.ana, "tmdb:movie:603").none { it.autor.usuario == "beto" })
            assertTrue(e.s.explorar(e.ana, null).none { it.autor.usuario == "beto" })
            assertTrue(e.s.avisos(e.ana, null).none { it.de.usuario == "beto" })
            e.s.bloquear(quien, otro, false)
        }
    }
}

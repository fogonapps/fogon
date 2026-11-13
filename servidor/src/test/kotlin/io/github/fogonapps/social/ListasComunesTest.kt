package io.github.fogonapps.social

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import kotlin.test.*

class ListasComunesTest {
    private fun nuevo(): Social {
        val dir = Files.createTempDirectory("social").toFile()
        return Social(Base(java.io.File(dir, "s.db").path), java.io.File(dir, "fotos"))
    }

    private fun obra(titulo: String, fuente: String? = null) = buildJsonObject {
        put("titulo", titulo); put("tipoObra", "pelicula"); fuente?.let { put("fuente", it) }
        put("portada", "http://inseguro.example/x.jpg"); put("otra", "no se guarda")
    }

    @Test fun soloAmigosAgregarVotarYSalir() {
        val s = nuevo()
        val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("ana")).token)!!
        val beto = s.cuentaDe(s.crearCuenta(NuevaCuenta("beto")).token)!!
        val caro = s.cuentaDe(s.crearCuenta(NuevaCuenta("caro")).token)!!
        s.seguir(ana, "beto"); s.seguir(beto, "ana")          // amigos
        s.seguir(ana, "caro")                                 // caro no la sigue: no es amiga
        val l = s.crearListaComun(ana, NuevaListaComun("Viernes de cine", listOf("beto", "caro")))
        assertEquals(setOf("ana", "beto"), l.miembros.map { it.usuario }.toSet())
        assertFailsWith<Problema> { s.detalleListaComun(caro, l.id) }

        s.agregarAListaComun(beto, l.id, obra("Matrix", "tmdb:movie:603"))
        s.agregarAListaComun(ana, l.id, obra("Matrix", "tmdb:movie:603"))   // repetida: no se duplica
        s.agregarAListaComun(ana, l.id, obra("Amélie"))
        val d = s.detalleListaComun(ana, l.id)
        assertEquals(2, d.obras.size)
        assertNull(d.obras.first().datos["portada"])                     // solo https
        assertNull(d.obras.first().datos["otra"])                        // solo lo necesario

        val amelie = d.obras.first { it.datos["titulo"].toString().contains("Amélie") }
        s.votarListaComun(beto, l.id, amelie.id, true)
        assertEquals("Amélie", s.detalleListaComun(beto, l.id).obras.first().datos["titulo"]!!.toString().trim('"'))   // la más votada primero
        assertTrue(s.detalleListaComun(beto, l.id).obras.first().yoVoto)

        // Beto no puede quitar lo que agregó Ana; Ana (que la armó) sí puede quitar lo de Beto.
        val matrix = d.obras.first { it.datos["titulo"].toString().contains("Matrix") }
        assertFailsWith<Problema> { s.quitarDeListaComun(beto, l.id, amelie.id) }
        s.quitarDeListaComun(ana, l.id, matrix.id)
        assertEquals(1, s.detalleListaComun(ana, l.id).obras.size)

        // Si sale quien la armó, la lista se borra para todos.
        s.salirDeListaComun(ana, l.id)
        assertTrue(s.listasComunes(beto).isEmpty())
    }

    @Test fun losBloqueosCortanLasListasEnComun() {
        val s = nuevo()
        val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("ana")).token)!!
        val beto = s.cuentaDe(s.crearCuenta(NuevaCuenta("beto")).token)!!
        val caro = s.cuentaDe(s.crearCuenta(NuevaCuenta("caro")).token)!!
        for ((x, y) in listOf(ana to "beto", beto to "ana", ana to "caro", caro to "ana", beto to "caro", caro to "beto")) s.seguir(x, y)
        val l = s.crearListaComun(ana, NuevaListaComun("Cine", listOf("beto")))
        // Beto bloquea a Ana (que armó la lista): sale Beto, y no ve más lo que se agrega.
        s.bloquear(beto, "ana", true)
        assertFailsWith<Problema> { s.detalleListaComun(beto, l.id) }
        assertEquals(listOf("ana"), s.detalleListaComun(ana, l.id).lista.miembros.map { it.usuario })
        // Una lista de Caro con Ana: Caro no puede sumar a Beto, que bloqueó a Ana.
        val l2 = s.crearListaComun(caro, NuevaListaComun("Series", listOf("ana")))
        assertFailsWith<Problema> { s.sumarAListaComun(caro, l2.id, "beto") }
    }
}

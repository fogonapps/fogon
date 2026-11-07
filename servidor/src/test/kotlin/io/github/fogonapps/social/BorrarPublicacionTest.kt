package io.github.fogonapps.social

import java.nio.file.Files
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BorrarPublicacionTest {
    private fun nuevo(): Social {
        val dir = Files.createTempDirectory("borrar").toFile()
        return Social(Base(java.io.File(dir, "s.db").path), java.io.File(dir, "fotos"))
    }
    private fun foto(clave: String) = PublicacionSubida(clave, Vis.PUBLICA, 1, "foto", buildJsonObject { put("texto", "hola") })

    @Test fun elAutorBorraLaSuyaYUnaSincronizacionViejaNoLaRecrea() {
        val s = nuevo()
        val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("ana")).token)!!
        s.subirPublicaciones(ana, listOf(foto("f:1")))
        val id = s.actividad(ana, null).single().id
        s.borrarPublicacion(ana, id)
        assertNull(s.publicacion(ana, id))
        s.subirPublicaciones(ana, listOf(foto("f:1")))      // un teléfono viejo la vuelve a mandar
        assertTrue(s.actividad(ana, null).isEmpty())
    }

    @Test fun nadieBorraLaAjena() {
        val s = nuevo()
        val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("ana")).token)!!
        val beto = s.cuentaDe(s.crearCuenta(NuevaCuenta("beto")).token)!!
        s.subirPublicaciones(ana, listOf(foto("f:1")))
        val id = s.actividad(ana, null).single().id
        assertEquals("no_existe", assertFailsWith<Problema> { s.borrarPublicacion(beto, id) }.mensaje)
        assertEquals(1, s.actividad(ana, null).size)
    }
}

package io.github.fogonapps.social

import java.nio.file.Files
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResenasTest {
    private fun nuevo(): Social {
        val dir = Files.createTempDirectory("resenas").toFile()
        return Social(Base(java.io.File(dir, "s.db").path), java.io.File(dir, "fotos"))
    }
    private fun resena(clave: String, parte: String = "", cuando: Long = 1, nota: Int? = null) = PublicacionSubida(clave, Vis.PUBLICA, cuando, "resena",
        buildJsonObject { put("titulo", "Serie"); put("fuente", "tmdb:tv:1"); put("texto", "texto $clave"); if (parte.isNotBlank()) put("parte", parte); nota?.let { put("nota", it) } })

    @Test fun utilUnVotoPorCuentaQuitableYNoPropio() {
        val s = nuevo()
        val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("ana")).token)!!
        val beto = s.cuentaDe(s.crearCuenta(NuevaCuenta("beto")).token)!!
        s.subirPublicaciones(ana, listOf(resena("r1")))
        val id = s.resenas(null, "tmdb:tv:1").single().id
        s.util(beto, id, true); s.util(beto, id, true)
        assertEquals(1, s.resenas(beto, "tmdb:tv:1").single().utiles)
        assertTrue(s.resenas(beto, "tmdb:tv:1").single().yoUtil)
        assertFalse(s.resenas(ana, "tmdb:tv:1").single().yoUtil)
        s.util(beto, id, false)
        assertEquals(0, s.resenas(beto, "tmdb:tv:1").single().utiles)
        assertEquals("propia", assertFailsWith<Problema> { s.util(ana, id, true) }.mensaje)
    }

    @Test fun utilSoloEnResenas() {
        val s = nuevo()
        val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("ana")).token)!!
        val beto = s.cuentaDe(s.crearCuenta(NuevaCuenta("beto")).token)!!
        s.subirPublicaciones(ana, listOf(PublicacionSubida("d1", Vis.PUBLICA, 1, "diario", buildJsonObject { put("fuente", "tmdb:tv:1") })))
        val id = s.actividad(ana, null).single().id
        assertEquals("no_es_resena", assertFailsWith<Problema> { s.util(beto, id, true) }.mensaje)
    }

    @Test fun parteYOrden() {
        val s = nuevo()
        val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("ana")).token)!!
        val beto = s.cuentaDe(s.crearCuenta(NuevaCuenta("beto")).token)!!
        val cami = s.cuentaDe(s.crearCuenta(NuevaCuenta("cami")).token)!!
        s.subirPublicaciones(ana, listOf(resena("toda", cuando = 3), resena("ep", parte = "ep:S1E3", cuando = 1)))
        assertEquals(listOf("texto ep"), s.resenas(null, "tmdb:tv:1", parte = "ep:S1E3").map { (it.datos["texto"] as JsonPrimitive).content })
        // Sin orden: las más nuevas. Con «utiles», la votada primero.
        assertEquals(listOf("texto toda", "texto ep"), s.resenas(null, "tmdb:tv:1").map { (it.datos["texto"] as JsonPrimitive).content })
        val ep = s.resenas(null, "tmdb:tv:1", parte = "ep:S1E3").single().id
        s.util(beto, ep, true); s.util(cami, ep, true)
        assertEquals("texto ep", (s.resenas(null, "tmdb:tv:1", orden = "utiles").first().datos["texto"] as JsonPrimitive).content)
    }

    @Test fun validarResena() {
        val mias = setOf("a", "b", "c", "d", "e")
        fun d(texto: String = "x", nota: Int? = null, fotos: List<String> = emptyList()) = buildJsonObject {
            put("texto", texto); nota?.let { put("nota", it) }; put("fotos", JsonArray(fotos.map { JsonPrimitive(it) }))
        }
        assertTrue(resenaValida(d(), mias))
        assertTrue(resenaValida(d(nota = 7, fotos = listOf("a", "b", "c", "d")), mias))
        assertFalse(resenaValida(d(texto = "x".repeat(5001)), mias))
        assertFalse(resenaValida(d(nota = 11), mias))
        assertFalse(resenaValida(d(fotos = listOf("a", "b", "c", "d", "e")), mias))
        assertFalse(resenaValida(d(fotos = listOf("ajena")), mias))
    }

    @Test fun resenaInvalidaNoSeSube() {
        val s = nuevo()
        val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("ana")).token)!!
        s.subirPublicaciones(ana, listOf(PublicacionSubida("mala", Vis.PUBLICA, 1, "resena",
            buildJsonObject { put("fuente", "tmdb:tv:1"); put("fotos", JsonArray(listOf(JsonPrimitive("de-otra-cuenta")))) })))
        assertTrue(s.resenas(null, "tmdb:tv:1").isEmpty())
    }
}

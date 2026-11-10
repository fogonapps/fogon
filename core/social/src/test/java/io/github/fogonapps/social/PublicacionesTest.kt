package io.github.fogonapps.social

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PublicacionesTest {
    @Test fun lecturaViejaNuevaYResena() {
        assertEquals(listOf("a"), imagenesPublicacion(buildJsonObject { put("imagen", "a") }))
        assertEquals(listOf("b", "c"), imagenesPublicacion(buildJsonObject {
            put("imagen", "a"); put("imagenes", JsonArray(listOf(JsonPrimitive("b"), JsonPrimitive("c"))))
        }))
        assertEquals(listOf("r"), imagenesPublicacion(buildJsonObject { put("fotos", JsonArray(listOf(JsonPrimitive("r")))) }))
    }
    @Test fun arbolOrdenaRaicesRespuestasYConservaBorrados() {
        data class Comentario(val id: Long, val texto: String, val cuando: Long, val padre: Long? = null, val borrado: Boolean = false)
        fun arbolComentarios(lista: List<Comentario>) = io.github.fogonapps.social.arbolComentarios(lista, { it.id }, { it.padre }, { it.cuando })
        val raiz = Comentario(1, "", 1, borrado = true)
        val respuesta = Comentario(2, "hola", 2, padre = 1)
        val otra = Comentario(3, "otra", 3)
        val arbol = arbolComentarios(listOf(otra, respuesta, raiz))
        assertEquals(listOf(raiz, otra), arbol.map { it.first })
        assertEquals(listOf(respuesta), arbol.first().second)
        assertTrue(arbol.first().first.borrado)
        assertEquals(listOf(respuesta), arbolComentarios(listOf(respuesta)).map { it.first })
    }

    @Test fun bloquearRetiraDeListasCargadasYFeedSinModificarLosOriginales() {
        val ana = PersonaSocial("ana")
        val beto = PersonaSocial("beto")
        val gente = mapOf("seguidores:yo" to listOf(ana, beto), "siguiendo:yo" to listOf(beto),
            "seguidores:otra" to listOf(beto), "bloqueados" to listOf(beto))
        val limpia = quitarCuentaBloqueada(gente, "beto")
        assertEquals(listOf(ana), limpia["seguidores:yo"])
        assertEquals(emptyList<PersonaSocial>(), limpia["siguiendo:yo"])
        assertTrue(limpia.getValue("seguidores:otra").isEmpty())
        assertEquals(listOf(beto), limpia["bloqueados"])
        assertEquals(gente.keys, limpia.keys)
        assertEquals(2, gente.getValue("seguidores:yo").size)
        val publicacion = PublicacionSocial(1, beto, "texto", 0, "publica", kotlinx.serialization.json.JsonObject(emptyMap()))
        val propia = publicacion.copy(id = 2, autor = ana)
        assertEquals(listOf(propia), quitarPublicacionesBloqueadas(listOf(publicacion, propia), "beto"))
        assertEquals(null, quitarPublicacionesBloqueadas(null, "beto"))
        assertEquals(gente, quitarCuentaBloqueada(gente, "nadie"))
    }

}

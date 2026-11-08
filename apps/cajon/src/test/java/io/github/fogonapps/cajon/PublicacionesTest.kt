package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PublicacionesTest {
    @Test fun migracionIdempotenteYRespaldo() {
        val vieja = PublicacionPropia("p", 1, imagen = "vieja")
        val e = Logica.normalizar(Estado(publicaciones = listOf(vieja)))
        assertEquals(listOf("vieja"), e.publicaciones.single().imagenes)
        assertEquals("vieja", e.publicaciones.single().imagen)
        assertEquals(e, Logica.normalizar(e))
        val nueva = vieja.copy(imagenes = listOf("nueva", "otra"))
        assertEquals(nueva, Logica.normalizar(Estado(publicaciones = listOf(nueva))).publicaciones.single())
    }
    @Test fun subidaCompatibleYOrden() {
        val p = PublicacionPropia("p", 1, imagenes = listOf("segunda", "primera"))
        val subida = RedSocial.publicacionesDe(Estado(publicaciones = listOf(p))).single().datos
        assertEquals("segunda", subida["imagen"]!!.jsonPrimitive.content)
        assertEquals(listOf("segunda", "primera"), imagenesPublicacion(subida))
        assertEquals("", p.imagen)
        val vieja = RedSocial.publicacionesDe(Estado(publicaciones = listOf(p.copy(imagenes = emptyList(), imagen = "vieja")))).single().datos
        assertEquals(listOf("vieja"), imagenesPublicacion(vieja))
    }
    @Test fun lecturaViejaNuevaYResena() {
        assertEquals(listOf("a"), imagenesPublicacion(buildJsonObject { put("imagen", "a") }))
        assertEquals(listOf("b", "c"), imagenesPublicacion(buildJsonObject {
            put("imagen", "a"); put("imagenes", JsonArray(listOf(JsonPrimitive("b"), JsonPrimitive("c"))))
        }))
        assertEquals(listOf("r"), imagenesPublicacion(buildJsonObject { put("fotos", JsonArray(listOf(JsonPrimitive("r")))) }))
    }
    @Test fun arbolOrdenaRaicesRespuestasYConservaBorrados() {
        val persona = PersonaSocial("ana", "Ana", false)
        val raiz = ComentarioSocial(1, persona, "", 1, borrado = true)
        val respuesta = ComentarioSocial(2, persona, "hola", 2, padre = 1)
        val otra = ComentarioSocial(3, persona, "otra", 3)
        val arbol = arbolComentarios(listOf(otra, respuesta, raiz))
        assertEquals(listOf(raiz, otra), arbol.map { it.first })
        assertEquals(listOf(respuesta), arbol.first().second)
        assertTrue(arbol.first().first.borrado)
        assertEquals(listOf(respuesta), arbolComentarios(listOf(respuesta)).map { it.first })
    }
}

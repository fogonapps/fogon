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


}

package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class VideosTest {


    @Test fun videoSeRespaldaYPublicaSinConvertirPosterEnFoto() {
        val propia = PublicacionPropia("v", 1, texto = "vídeo", video = "mp4", poster = "jpeg", duracionVideo = 30)
        val recuperada = JsonCajon.decodeFromString<PublicacionPropia>(JsonCajon.encodeToString(PublicacionPropia.serializer(), propia))
        assertEquals(propia, recuperada)
        val datos = RedSocial.publicacionesDe(Estado(publicaciones = listOf(recuperada))).single().datos
        assertEquals("mp4", datos["video"]!!.jsonPrimitive.content)
        assertEquals("jpeg", datos["poster"]!!.jsonPrimitive.content)
        assertEquals(30, datos["duracion"]!!.jsonPrimitive.int)
        assertTrue(imagenesPublicacion(datos).isEmpty())
        assertFalse(datos.containsKey("imagen"))
        val antigua = JsonCajon.decodeFromString<PublicacionPropia>("""{"clave":"p","cuando":1,"imagen":"foto"}""")
        assertEquals("", antigua.video)
        assertEquals(listOf("foto"), imagenesPublicacion(RedSocial.publicacionesDe(Estado(publicaciones = listOf(antigua))).single().datos))
    }
}

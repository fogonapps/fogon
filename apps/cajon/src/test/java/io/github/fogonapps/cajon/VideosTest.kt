package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class VideosTest {
    @Test fun recorteInicialConLimiteExacto() {
        assertEquals(TramoVideo(12_345, false), tramoVideo(12_345))
        assertEquals(TramoVideo(30_000, false), tramoVideo(30_000))
        assertEquals(TramoVideo(30_000, true), tramoVideo(30_001))
        assertEquals(TramoVideo(30_000, true), tramoVideo(Long.MAX_VALUE))
        assertThrows(IllegalArgumentException::class.java) { tramoVideo(0) }
    }
    @Test fun noHayPrecargaSoloUnaAccionExplicitaAbreElVideo() {
        // La política conservadora es idéntica en wifi, móvil y ahorro de datos.
        assertFalse(cargarVideo(solicitado = false))
        assertTrue(cargarVideo(solicitado = true))
    }
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

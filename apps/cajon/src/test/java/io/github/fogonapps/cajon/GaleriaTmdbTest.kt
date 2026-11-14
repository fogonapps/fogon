package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.Tmdb
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class GaleriaTmdbTest {
    @Test fun fondosSinTextoPrimeroConTamanosYLimite() {
        val datos = buildJsonObject {
            putJsonArray("backdrops") {
                addJsonObject { put("file_path", "/es.jpg"); put("iso_639_1", "es"); put("vote_average", 10) }
                addJsonObject { put("file_path", ""); put("iso_639_1", JsonNull) }
                repeat(35) { indice ->
                    addJsonObject {
                        put("file_path", "/$indice.jpg")
                        put("iso_639_1", JsonNull)
                        put("vote_average", indice)
                    }
                }
            }
        }
        val fotos = Tmdb.galeriaDe(datos, "es")
        assertEquals(30, fotos.size)
        assertEquals("https://image.tmdb.org/t/p/w300/34.jpg" to "https://image.tmdb.org/t/p/w1280/34.jpg", fotos.first())
        assertTrue(fotos.none { it.first.endsWith("es.jpg") })
        assertEquals("https://image.tmdb.org/t/p/w300/5.jpg", fotos.last().first)
    }

    @Test fun idiomasVaciosDuplicadosYRespuestaSinFondos() {
        val datos = Json.parseToJsonElement("""{"backdrops":[
            {"file_path":"/en.jpg","iso_639_1":"en"},
            {"file_path":"/sin.jpg","iso_639_1":""},
            {"file_path":"/sin.jpg","iso_639_1":null},
            {"file_path":"/es.jpg","iso_639_1":"es"}] }""").jsonObject
        assertEquals(listOf("sin.jpg", "es.jpg", "en.jpg"), Tmdb.galeriaDe(datos, "es").map { it.first.substringAfterLast('/') })
        assertTrue(Tmdb.galeriaDe(buildJsonObject {}, "es").isEmpty())
    }

    @Test fun soloPeliculasYSeriesTmdb() {
        listOf("tmdb:movie:123", "tmdb:tv:456").forEach { assertTrue(Tmdb.esFuenteGaleria(it)) }
        listOf("", "ol:123", "tmdb:person:12", "tmdb:movie:", "tmdb:tv:abc", "otra:movie:123").forEach {
            assertFalse(Tmdb.esFuenteGaleria(it))
        }
    }
}

package io.github.fogonapps.social

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CineTest {
    @Test fun soloLasRutasDeLaApp() {
        val c = Cine("x")
        listOf("search/multi", "movie/157336", "tv/1396/season/2", "tv/1396/recommendations", "person/525/combined_credits",
            "discover/tv", "trending/movie/week", "watch/providers/movie", "genre/tv/list").forEach { assertTrue(c.permitida(it), it) }
        listOf("account", "authentication/token/new", "movie/1/rating", "list/1", "../etc/passwd", "movie/abc").forEach { assertFalse(c.permitida(it), it) }
    }

    @Test fun sinClaveNoPide() {
        assertEquals(503, Cine(null).pedir("movie/157336", emptyMap()).codigo)
        assertEquals(404, Cine("x").pedir("account", emptyMap()).codigo)
    }

    /** Contra TMDB de verdad (solo si se pide: TMDB_API_KEY en el entorno). */
    @Test fun enVivo() {
        val clave = System.getenv("TMDB_API_KEY") ?: return
        val r = Cine(clave).pedir("movie/157336", mapOf("language" to "es-MX", "api_key" to "ajena"))
        assertEquals(200, r.codigo)
        assertTrue("Interestelar" in r.cuerpo)
    }
}

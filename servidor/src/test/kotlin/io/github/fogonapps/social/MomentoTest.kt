package io.github.fogonapps.social

import kotlin.test.Test
import kotlin.test.assertEquals

class MomentoTest {
    @Test fun titulosLimpios() {
        assertEquals("Coco", Momento.limpiar("Coco_(película)"))
        assertEquals("Lupin", Momento.limpiar("Lupin (serie de televisión)"))
        assertEquals("It - bienvenidos a Derry", Momento.limpiar("It - bienvenidos a Derry"))
        assertEquals("La Odisea", Momento.limpiar("La Odisea (película de 2026)"))
    }

    /** Contra Wikimedia de verdad (solo si se pide: MOMENTO_EN_VIVO=1), para revisar la consulta. */
    @Test fun enVivo() {
        if (System.getenv("MOMENTO_EN_VIVO") != "1") return
        val m = Momento.calcular("es")
        println("${m.dia}: ${m.peliculas.take(5)}\n${m.series.take(5)}\n${m.anime.take(5)}")
        assert(m.peliculas.isNotEmpty())
    }
}

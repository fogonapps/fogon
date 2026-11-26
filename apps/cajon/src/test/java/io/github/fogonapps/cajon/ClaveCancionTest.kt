package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.ui.claveCancion
import io.github.fogonapps.social.CancionSocial
import org.junit.Assert.assertEquals
import org.junit.Test

/** Dos canciones iguales (mismo avance y título, o sin avance) no pueden dar la misma clave en la lista del buscador. */
class ClaveCancionTest {
    @Test fun cancionesRepetidasTienenClavesDistintas() {
        val repetida = CancionSocial("Canción", "Artista", audio = "https://audio/1.m4a")
        val sinAvance = CancionSocial("Otra", "Artista")
        val lista = listOf(repetida, repetida, sinAvance, sinAvance)
        val claves = lista.mapIndexed { i, c -> claveCancion(i, c) }
        assertEquals(lista.size, claves.toSet().size)
    }
}

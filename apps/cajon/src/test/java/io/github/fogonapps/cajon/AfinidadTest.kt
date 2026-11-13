package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import org.junit.Assert.*
import org.junit.Test

class AfinidadTest {
    private fun obra(titulo: String, tipo: String = Tipos.PELICULA, fuente: String = "") = Obra(id = titulo, tipo = tipo, titulo = titulo, fuente = fuente)

    @Test fun `sin nada que comparar no hay afinidad`() {
        assertNull(Afinidades.calcular(emptyList(), emptyList(), emptyList(), emptyList(), listOf("Zoé"), listOf("rock"), emptyList()))
    }

    @Test fun `gustos iguales dan cien y lo comun sale limitado`() {
        val a = Afinidades.calcular(listOf("Zoé", "Café Tacvba", "Enjambre", "Mon Laferte"), listOf("rock", "indie"), listOf(obra("Interstellar")), listOf(obra("Interstellar")),
            listOf("Zoe", "Cafe Tacvba", "Enjambre", "Mon Laferte"), listOf("Rock", "Indie"), listOf(ObraDestacada("Interstellar", Tipos.PELICULA)))!!
        assertEquals(100, a.porcentaje)
        assertEquals(Afinidades.MOSTRAR_ARTISTAS, a.artistas.size)
        assertEquals("Interstellar", a.favoritaDeLosDos?.titulo)
    }

    @Test fun `la misma obra se reconoce por su fuente aunque cambie el titulo`() {
        val a = Afinidades.calcular(emptyList(), emptyList(), listOf(obra("El viaje de Chihiro", fuente = "tmdb:129")), emptyList(),
            emptyList(), emptyList(), listOf(ObraDestacada("Spirited Away", Tipos.PELICULA, fuente = "tmdb:129")))!!
        assertEquals(100, a.porcentaje)
        assertNull(a.favoritaDeLosDos)
    }

    @Test fun `poco en comun da un porcentaje bajo`() {
        val a = Afinidades.calcular(listOf("A", "B", "C", "D"), listOf("pop"), listOf(obra("X")), emptyList(),
            listOf("A", "E", "F", "G"), listOf("metal"), listOf(ObraDestacada("Y", Tipos.PELICULA)))!!
        assertTrue(a.porcentaje in 1..50)
        // Con poco en común no hay número: se invita a descubrir lo suyo que no tienes.
        assertFalse(a.mostrarPorcentaje)
        assertEquals(listOf("Y"), a.paraDescubrir.map { it.titulo })
    }

    @Test fun `los generos de musica se separan de los de cine`() {
        assertTrue(GenerosPerfil.esMusical("hip hop")); assertTrue(GenerosPerfil.esMusical("latin rock")); assertTrue(GenerosPerfil.esMusical("electronica"))
        assertFalse(GenerosPerfil.esMusical("terror")); assertFalse(GenerosPerfil.esMusical("drama")); assertFalse(GenerosPerfil.esMusical("romance"))
        assertFalse(GenerosPerfil.esMusical("action adventure"))
    }
}

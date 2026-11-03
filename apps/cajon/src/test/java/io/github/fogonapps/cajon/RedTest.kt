package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.Ajustes
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Lista
import io.github.fogonapps.cajon.datos.Logica
import io.github.fogonapps.cajon.datos.Obra
import io.github.fogonapps.cajon.datos.PartesPerfil
import io.github.fogonapps.cajon.datos.Perfil
import io.github.fogonapps.cajon.datos.PublicacionPropia
import io.github.fogonapps.cajon.datos.Registro
import io.github.fogonapps.cajon.datos.Visibilidad
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lo que El Cajón sube a la red: solo lo que la persona marcó como público o para sus seguidores. */
class RedTest {
    private val matrix = Obra("o1", "pelicula", "Matrix", portada = "https://img/matrix.jpg", registros = listOf(
        Registro("r1", "2026-11-01", nota = 9, texto = "Una joya"),
        Registro("r2", "2026-10-01", nota = 6, visibilidad = Visibilidad.PUBLICA),
        Registro("r3", "2026-09-01", visibilidad = Visibilidad.PRIVADA),
    ))
    private val libro = Obra("o2", "libro", "Rayuela", portada = "file:///local.jpg", registros = listOf(Registro("r4", "2026-08-01", visibilidad = Visibilidad.AMIGOS)))

    @Test fun soloLoCompartido() {
        // Sin decir nada en el perfil: el diario es privado y solo sube lo marcado una por una.
        val e = Estado(obras = listOf(matrix, libro), listas = listOf(Lista("l1", "Pendientes"), Lista("l2", "Top", obras = listOf("o1"), visibilidad = Visibilidad.PUBLICA)),
            publicaciones = listOf(PublicacionPropia("p1", 5, "¡Qué día!", imagen = "x".repeat(22)), PublicacionPropia("p2", 6, "borrada", borrada = true)))
        val subidas = RedSocial.publicacionesDe(e)
        assertEquals(setOf("r:o1:r2", "r:o2:r4", "l:l2", "f:p1"), subidas.map { it.clave }.toSet())
        assertEquals(Visibilidad.AMIGOS, subidas.first { it.clave == "r:o2:r4" }.visibilidad)
        // Las portadas locales (de este teléfono) no viajan.
        assertEquals("", (subidas.first { it.clave == "r:o2:r4" }.datos["portada"] as JsonPrimitive).content)
        assertEquals(3.0, (subidas.first { it.clave == "r:o1:r2" }.datos["estrellas"] as JsonPrimitive).content.toDouble(), 0.0)

        // Con el diario público en el perfil, también lo que no dice nada (r1, una reseña); lo privado nunca.
        val publico = e.copy(perfil = Perfil(visibilidad = mapOf(PartesPerfil.DIARIO to Visibilidad.PUBLICA)))
        val claves = RedSocial.publicacionesDe(publico).map { it.clave }
        assertTrue("r:o1:r1" in claves && "r:o1:r3" !in claves)
        assertEquals("resena", RedSocial.publicacionesDe(publico).first { it.clave == "r:o1:r1" }.tipo)
    }

    @Test fun perfilConSusPartes() {
        val e = Estado(obras = listOf(matrix), ajustes = Ajustes(nombre = "Ana", favoritas = listOf("o1")),
            perfil = Perfil(bio = "Cine", portada = "file:///mia.jpg", visibilidad = mapOf(PartesPerfil.FAVORITAS to Visibilidad.PUBLICA)))
        val p = RedSocial.perfilDe(e)
        assertEquals("Ana", p.nombre)
        assertEquals("", p.portada)                          // la portada local no sube
        assertEquals(setOf(PartesPerfil.FAVORITAS), p.partes.keys) // los destacados siguen privados
    }

    @Test fun lasPublicacionesPropiasSeSincronizan() {
        val a = Estado(publicaciones = listOf(PublicacionPropia("p1", 1, "hola", cambio = 1)))
        val b = Estado(publicaciones = listOf(PublicacionPropia("p1", 1, "hola", cambio = 5, borrada = true), PublicacionPropia("p2", 2, "otra", cambio = 2)))
        val unido = Logica.unir(a, b)
        assertEquals(2, unido.publicaciones.size)
        assertTrue(unido.publicaciones.first { it.clave == "p1" }.borrada)
    }
}

class OrdenTest {
    private val ahora = 1_800_000_000_000L
    private fun pub(id: Long, autor: String, horas: Int, vistas: Int = 100, calidad: Double = 1.0, fuente: String = "", creador: String = "") =
        io.github.fogonapps.cajon.datos.PublicacionSocial(id, io.github.fogonapps.cajon.datos.PersonaSocial(autor), "foto", ahora - horas * 3_600_000L, "publica",
            kotlinx.serialization.json.buildJsonObject { if (fuente.isNotBlank()) put("fuente", kotlinx.serialization.json.JsonPrimitive(fuente)); if (creador.isNotBlank()) put("creador", kotlinx.serialization.json.JsonPrimitive(creador)) },
            vistas = vistas, calidad = calidad)

    @Test fun afinidadDiversidadYNuevas() {
        val e = Estado(obras = listOf(Obra("o1", "pelicula", "Matrix", fuente = "tmdb:movie:603", meGusta = true)),
            gustos = listOf(io.github.fogonapps.cajon.datos.Gusto("artista:spam", "Spam", io.github.fogonapps.cajon.datos.Senales.NO_INTERESA)))
        val lista = listOf(
            pub(1, "ana", 100), pub(2, "ana", 100), pub(3, "ana", 100),
            pub(4, "beto", 200, fuente = "tmdb:movie:603"),          // vieja pero de algo que te gusta
            pub(5, "caro", 100), pub(6, "dani", 100), pub(7, "eva", 100),
            pub(8, "nuevo", 2, vistas = 3),                          // nueva, pocas vistas
            pub(9, "x", 1, creador = "spam"),                        // no te interesa
        )
        val orden = io.github.fogonapps.cajon.RedSocial.ordenarPorGustos(lista, e, ahora)
        assertEquals(4L, orden.first().first.id)                     // lo tuyo primero
        assertTrue(orden.first().second is io.github.fogonapps.cajon.RedSocial.Companion.Motivo.TienesLaObra)
        assertTrue(orden.none { it.first.id == 9L })
        assertEquals(8L, orden[4].first.id)                          // la nueva, en el quinto lugar
        orden.zipWithNext().forEach { (a, b) -> assertTrue(a.first.autor.usuario != b.first.autor.usuario) }
    }
}

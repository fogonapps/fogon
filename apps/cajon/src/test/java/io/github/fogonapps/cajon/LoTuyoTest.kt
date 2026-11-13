package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import io.github.fogonapps.social.PersonaSocial
import io.github.fogonapps.social.PublicacionSocial
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test

class LoTuyoTest {
    private fun peli(id: String, fecha: String?) = Obra(id = id, tipo = Tipos.PELICULA, titulo = "Peli $id", estado = Estados.TERMINADO, terminada = fecha)

    @Test fun `las insignias cuentan a lo sumo tres obras por dia y nada sin fecha`() {
        // 60 películas el mismo día (como al importar de golpe) + 10 sin fecha: cuentan solo 3.
        val e = Estado(obras = (1..60).map { peli("$it", "2026-01-05") } + (61..70).map { peli("$it", null) })
        val cine = Insignias.de(e, 2026).first { it.id == "cine" }
        assertEquals(Insignias.POR_DIA, cine.lleva)
        assertFalse(cine.ganada)
        // 50 en días distintos sí la ganan.
        val e2 = Estado(obras = (1..50).map { peli("$it", java.time.LocalDate.of(2026, 1, 1).plusDays(it.toLong()).toString()) })
        assertTrue(Insignias.de(e2, 2026).first { it.id == "cine" }.ganada)
    }

    @Test fun `por donde vas en una serie y en un libro`() {
        assertEquals("T2 · E5", RedSocial.avanceDe(Obra(id = "s", tipo = Tipos.SERIE, titulo = "S", vistos = listOf("1x9", "2x5", "2x3"))))
        assertEquals("p. 120 / 300", RedSocial.avanceDe(Obra(id = "l", tipo = Tipos.LIBRO, titulo = "L", pagina = 120, paginas = 300)))
        assertEquals("", RedSocial.avanceDe(Obra(id = "p", tipo = Tipos.PELICULA, titulo = "P")))
    }

    private fun pub(id: Long, quien: String, titulo: String, hace: Long, ahora: Long) = PublicacionSocial(id, PersonaSocial(quien), "diario", ahora - hace, "publica",
        buildJsonObject { put("titulo", titulo); put("tipoObra", "pelicula"); put("fuente", "tmdb:$titulo") })

    @Test fun `esta semana en tu red junta por obra y deja afuera lo viejo`() {
        val ahora = 100L * 86_400_000L
        val feed = listOf(pub(1, "ana", "Dune", 1000, ahora), pub(2, "beto", "Dune", 2000, ahora), pub(3, "caro", "Amélie", 3000, ahora),
            pub(4, "ana", "Vieja", 9L * 86_400_000L, ahora))
        val semana = RedSocial.semanaEnTuRed(feed, ahora)
        assertEquals(listOf("tmdb:Dune", "tmdb:Amélie"), semana.map { (it.datos["fuente"] as kotlinx.serialization.json.JsonPrimitive).content })
        assertEquals(2, semana.first().quienes.size)
        assertTrue(RedSocial.semanaEnTuRed(feed.take(1), ahora).isEmpty())   // con una sola, nada
    }

    @Test fun `gente con tus gustos sin quienes ya sigues ni tu`() {
        val ahora = 10L * 86_400_000L
        val e = Estado(obras = listOf(Obra(id = "d", tipo = Tipos.PELICULA, titulo = "Dune", fuente = "tmdb:Dune", estado = Estados.TERMINADO)))
        val explorar = listOf(pub(1, "ana", "Dune", 1, ahora), pub(2, "beto", "Dune", 1, ahora), pub(3, "yo", "Dune", 1, ahora), pub(4, "caro", "Otra", 1, ahora))
        val gente = RedSocial.genteParecida(explorar, setOf("beto"), "yo", e)
        assertEquals(listOf("ana"), gente.map { it.persona.usuario })
        assertEquals(listOf("Dune"), gente.first().enComun)
    }

    @Test fun `lo que ves y lo que tienes por ver no se publica hasta elegirlo`() {
        val obras = listOf(Obra(id = "s", tipo = Tipos.SERIE, titulo = "Dark", estado = Estados.EN_CURSO), Obra(id = "q", tipo = Tipos.PELICULA, titulo = "Up", estado = Estados.QUIERO))
        val publico = Estado(obras = obras, ajustes = Ajustes(retos = mapOf(Tipos.PELICULA to 10)),
            perfil = Perfil(visibilidad = mapOf(PartesPerfil.DESTACADOS to Visibilidad.PUBLICA)))
        val partes = RedSocial.partesDeLoTuyo(publico)
        assertTrue(PartesPerfil.RETO in partes)                 // el reto va como los destacados
        assertFalse(PartesPerfil.VIENDO in partes)              // esto no, hasta que lo elija
        assertFalse(PartesPerfil.POR_VER in partes)
        val elegido = publico.copy(perfil = publico.perfil.copy(visibilidad = publico.perfil.visibilidad + (PartesPerfil.VIENDO to Visibilidad.AMIGOS) + (PartesPerfil.POR_VER to Visibilidad.AMIGOS)))
        assertEquals(Visibilidad.AMIGOS, RedSocial.partesDeLoTuyo(elegido)[PartesPerfil.VIENDO]?.visibilidad)
        assertTrue(PartesPerfil.POR_VER in RedSocial.partesDeLoTuyo(elegido))
    }
}

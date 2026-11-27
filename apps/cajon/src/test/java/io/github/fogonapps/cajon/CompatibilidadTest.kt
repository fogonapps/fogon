package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import io.github.fogonapps.cajon.ui.Compatibilidad
import io.github.fogonapps.social.PersonaSocial
import io.github.fogonapps.social.PublicacionSocial
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test

/** La compatibilidad con otra persona: solo con lo que comparte, sin porcentaje con muy poco, y lo que te puede gustar. */
class CompatibilidadTest {
    private fun pub(id: Long, tipo: String, titulo: String, fuente: String, estrellas: Double? = null) =
        PublicacionSocial(id, PersonaSocial("ana"), tipo, 0, "publica", buildJsonObject {
            put("titulo", titulo); put("tipoObra", Tipos.PELICULA); put("fuente", fuente); estrellas?.let { put("estrellas", it) }
        })

    private fun mia(id: String, titulo: String, fuente: String, nota: Int = 0, estado: String = Estados.TERMINADO) =
        Obra(id, Tipos.PELICULA, titulo, fuente = fuente, nota = nota, estado = estado)

    private fun perfil(vararg p: PublicacionSocial, musica: List<String> = emptyList()) = PerfilSocial(PersonaSocial("ana"), publicaciones = p.toList(),
        partes = if (musica.isEmpty()) emptyMap() else mapOf("musica" to JsonArray(musica.map { n -> buildJsonObject { put("nombre", n) } })))

    @Test fun conMuyPocoNoHayPorcentaje() {
        val e = Estado(obras = listOf(mia("1", "Alien", "tmdb:movie:1", 8)))
        val c = Compatibilidad.calcular(e, perfil(pub(1, "diario", "Alien", "tmdb:movie:1", 4.0)))
        assertNull(c.porcentaje)
        assertEquals(1, c.enComun.size)
    }

    @Test fun pensarParecidoSubeYOpuestoBaja() {
        val e = Estado(obras = (1..5).map { mia("$it", "P$it", "tmdb:movie:$it", 9) })
        val iguales = Compatibilidad.calcular(e, perfil(*(1..5).map { pub(it.toLong(), "resena", "P$it", "tmdb:movie:$it", 4.5) }.toTypedArray()))
        val opuestos = Compatibilidad.calcular(e, perfil(*(1..5).map { pub(it.toLong(), "resena", "P$it", "tmdb:movie:$it", 0.5) }.toTypedArray()))
        assertTrue("${iguales.porcentaje} > ${opuestos.porcentaje}", iguales.porcentaje!! > opuestos.porcentaje!! + 30)
        assertTrue(iguales.porcentaje!! in 1..99)
    }

    @Test fun laMismaObraVariasVecesCuentaUnaYGanaLaNotaMasAlta() {
        val s = Compatibilidad.suyas(perfil(pub(1, "diario", "Alien", "tmdb:movie:1", 3.0), pub(2, "resena", "Alien", "tmdb:movie:1", 4.5)))
        assertEquals(1, s.size)
        assertEquals(9, s.single().nota)
    }

    @Test fun teGustariaLoQueLeEncantaYNoTienes() {
        val borrada = mia("b", "Borrada", "tmdb:movie:9").copy(borrada = true)
        val e = Estado(obras = listOf(mia("1", "Alien", "tmdb:movie:1"), borrada))
        val c = Compatibilidad.calcular(e, perfil(
            pub(1, "resena", "Alien", "tmdb:movie:1", 5.0), pub(2, "resena", "Ran", "tmdb:movie:2", 4.5),
            pub(3, "resena", "Meh", "tmdb:movie:3", 2.0), pub(4, "resena", "Borrada", "tmdb:movie:9", 5.0)))
        assertEquals(listOf("Ran"), c.teGustarian.map { it.obra.titulo })
    }

    @Test fun sinPublicacionesUsaSuColeccion() {
        val e = Estado(obras = (1..4).map { mia("$it", "P$it", "tmdb:movie:$it", 8) })
        val coleccion = (1..4).map { io.github.fogonapps.social.ObraColeccion("tmdb:movie:$it", Tipos.PELICULA, "P$it", 8, true) } +
            io.github.fogonapps.social.ObraColeccion("tmdb:movie:9", Tipos.PELICULA, "Nueva", 10, true, "https://imagen/x.jpg")
        val c = Compatibilidad.calcular(e, perfil(), coleccion)
        assertEquals(4, c.enComun.size)
        assertTrue(c.porcentaje!! >= 70)
        assertEquals("https://imagen/x.jpg", c.teGustarian.single().obra.portada)
    }

    @Test fun laColeccionQueSeSubeNoLlevaLoQueSoloQuieresVer() {
        val e = Estado(obras = listOf(mia("1", "Vista", "tmdb:movie:1", 9).copy(portada = "https://p/1.jpg"), mia("2", "Por ver", "tmdb:movie:2", estado = Estados.QUIERO),
            mia("3", "Sin nota", "tmdb:movie:3", estado = Estados.TERMINADO)))
        val c = RedSocial.coleccionDe(e)
        assertEquals(listOf("Vista", "Sin nota"), c.map { it.titulo })
        assertTrue(c[0].encanta); assertEquals("https://p/1.jpg", c[0].portada)
        assertFalse(c[1].encanta); assertEquals("", c[1].portada)
    }

    @Test fun losArtistasEnComunCuentan() {
        val e = Estado(obras = listOf(mia("1", "Alien", "tmdb:movie:1", 8), mia("2", "Ran", "tmdb:movie:2", 8)),
            artistas = listOf(Artista("a", "Björk"), Artista("c", "Caruso")))
        val c = Compatibilidad.calcular(e, perfil(pub(1, "diario", "Alien", "tmdb:movie:1", 4.0), pub(2, "diario", "Ran", "tmdb:movie:2", 4.0),
            musica = listOf("björk", "Otro")))
        assertEquals(listOf("björk"), c.artistas)
        assertNotNull(c.porcentaje)
    }
}

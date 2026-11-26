package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import io.github.fogonapps.cajon.ui.DuracionJuntos
import io.github.fogonapps.cajon.ui.Juntos
import io.github.fogonapps.cajon.ui.ObraParte
import io.github.fogonapps.cajon.ui.Propuesta
import io.github.fogonapps.cajon.ui.VotoJuntos
import io.github.fogonapps.cajon.ui.resenasDeLaComunidad
import io.github.fogonapps.social.PersonaSocial
import io.github.fogonapps.social.PublicacionSocial
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test

/** «Juntos»: ¿qué vemos? (unión, duración y orden), lo que van viendo y lo que terminaron los dos; y las reseñas de Gente. */
class JuntosTest {
    private fun suya(titulo: String, tipo: String = Tipos.PELICULA, fuente: String = "", extra: Map<String, Any> = emptyMap()) =
        ObraParte(titulo, tipo, "", fuente, null, "", JsonObject(extra.mapValues { (_, v) -> if (v is Number) JsonPrimitive(v) else JsonPrimitive(v.toString()) }))

    private fun mia(id: String, titulo: String, tipo: String = Tipos.PELICULA, fuente: String = "", minutos: Int? = null, agregada: Long = 0,
                    temporadas: List<Temporada> = emptyList(), estado: String = Estados.QUIERO) =
        Obra(id = id, tipo = tipo, titulo = titulo, fuente = fuente, minutos = minutos, agregada = agregada, temporadas = temporadas, estado = estado)

    @Test fun `junta lo de los dos sin repetir y marca lo que comparten`() {
        val mias = listOf(mia("1", "Dune", fuente = "tmdb:1", agregada = 2), mia("2", "Amélie", agregada = 1),
            mia("3", "Un disco", tipo = Tipos.ALBUM), mia("4", "Vista", estado = Estados.TERMINADO))
        val suyas = listOf(suya("Dune", fuente = "tmdb:1"), suya("amelie"), suya("Coco"), suya("Coco"))
        val l = Juntos.queVemos(mias, suyas)
        assertEquals(listOf("Dune", "Amélie", "Coco"), l.map { it.titulo })
        assertTrue(l[0].deLosDos)
        // Por título limpio también es la misma.
        assertTrue(l[1].deLosDos)
        assertFalse(l[2].mia)
        assertTrue(l[2].suya)
    }

    @Test fun `filtra por duracion con lo que se sabe`() {
        val serieCorta = mia("s", "Corta", tipo = Tipos.SERIE, minutos = 25, temporadas = listOf(Temporada(1, 8)))
        val serieLarga = mia("l", "Larga", tipo = Tipos.SERIE, minutos = 45, temporadas = listOf(Temporada(1, 22), Temporada(2, 22)))
        val mias = listOf(mia("p", "Corta peli", minutos = 95), mia("q", "Larga peli", minutos = 170), serieCorta, serieLarga, mia("x", "Sin datos"))
        val suyas = listOf(suya("Suya", extra = mapOf("minutos" to 110)), suya("Suya serie", tipo = Tipos.SERIE, extra = mapOf("minutos" to 20)))
        val l = Juntos.queVemos(mias, suyas)
        fun con(d: DuracionJuntos) = l.filter { Juntos.cabe(it, d) }.map { it.titulo }.toSet()
        assertEquals(l.size, con(DuracionJuntos.TODO).size)
        assertEquals(setOf("Corta peli", "Suya"), con(DuracionJuntos.MENOS_DE_2H))
        // Una tarde: hasta 5 h (la serie corta dura 8 × 25 = 200 min).
        assertEquals(setOf("Corta peli", "Larga peli", "Corta", "Suya"), con(DuracionJuntos.UNA_TARDE))
        // Los minutos de una serie suya son por episodio: no se usan como total.
        assertEquals(setOf("Corta"), con(DuracionJuntos.TEMPORADA_CORTA))
    }

    private fun p(clave: String, mia: Boolean, suya: Boolean) = Propuesta(clave, clave, Tipos.PELICULA, "", "", null, "", null, null, mia, suya)

    @Test fun `sin sus votos ordena por los tuyos y al final lo que se paso`() {
        val l = listOf(p("solo", true, false), p("pasada", true, true), p("ambos", true, true), p("apuntada", true, false), p("suya", false, true), p("ambosSi", true, true))
        val votos = mapOf("pasada" to VotoJuntos.PASO, "apuntada" to VotoJuntos.ME_APUNTO, "ambosSi" to VotoJuntos.ME_APUNTO)
        // Sin saber qué eligió la otra persona: lo de los dos al que te apuntaste, lo tuyo al que te apuntaste, lo de los dos.
        assertEquals(listOf("ambosSi", "apuntada", "ambos", "solo", "suya", "pasada"), Juntos.ordenar(l, votos).map { it.clave })
        // La grande es la primera sin voto; con todo votado, la primera del orden.
        assertEquals("ambos", Juntos.propuesta(Juntos.ordenar(l, votos), votos)?.clave)
        val todos = l.associate { it.clave to VotoJuntos.ME_APUNTO }
        assertEquals("pasada", Juntos.propuesta(Juntos.ordenar(l, todos), todos)?.clave)
    }

    @Test fun `primero a lo que se apuntaron los dos de verdad`() {
        val l = listOf(p("nada", true, false), p("ambosLista", true, true), p("yoSi", true, false), p("ellaSi", false, true),
            p("losDosSi", true, false), p("ellaNo", true, true), p("yoNo", true, true), p("listaYEllaSi", true, true))
        val mios = mapOf("yoSi" to VotoJuntos.ME_APUNTO, "losDosSi" to VotoJuntos.ME_APUNTO, "yoNo" to VotoJuntos.PASO)
        val suyos = mapOf("ellaSi" to VotoJuntos.ME_APUNTO, "losDosSi" to VotoJuntos.ME_APUNTO, "ellaNo" to VotoJuntos.PASO,
            "listaYEllaSi" to VotoJuntos.ME_APUNTO, "yoNo" to VotoJuntos.ME_APUNTO)
        val orden = Juntos.ordenar(l, mios, suyos)
        assertEquals(listOf("losDosSi", "listaYEllaSi", "yoSi", "ellaSi", "ambosLista", "nada", "ellaNo", "yoNo"), orden.map { it.clave })
        assertTrue(Juntos.losDosSeApuntaron("losDosSi", mios, suyos))
        assertFalse(Juntos.losDosSeApuntaron("ellaSi", mios, suyos))
        // La grande es la de los dos, aunque ya la votaste; sin ninguna así, la primera sin tu voto.
        assertEquals("losDosSi", Juntos.propuesta(orden, mios, suyos)?.clave)
        assertEquals("listaYEllaSi", Juntos.propuesta(orden, mios, suyos - "losDosSi")?.clave)
    }

    @Test fun `los votos van y vuelven del servidor sin perder lo que no se pudo mandar`() {
        assertEquals("si", Juntos.aServidor(VotoJuntos.ME_APUNTO))
        assertEquals("no", Juntos.aServidor(VotoJuntos.PASO))
        assertNull(Juntos.aServidor(null))
        assertEquals(mapOf("a" to VotoJuntos.ME_APUNTO, "b" to VotoJuntos.PASO), Juntos.deServidor(mapOf("a" to "si", "b" to "no", "c" to "raro")))
        // Lo pendiente (cambiado sin red) gana, también si lo quitaste; lo demás viene del servidor (quizá de otro teléfono).
        val locales = mapOf("a" to VotoJuntos.PASO, "viejo" to VotoJuntos.ME_APUNTO)
        val servidor = mapOf("a" to VotoJuntos.ME_APUNTO, "b" to VotoJuntos.ME_APUNTO, "quitado" to VotoJuntos.ME_APUNTO)
        assertEquals(mapOf("a" to VotoJuntos.PASO, "b" to VotoJuntos.ME_APUNTO), Juntos.fusionar(locales, setOf("a", "quitado"), servidor))
        assertEquals(servidor, Juntos.fusionar(locales, emptySet(), servidor))
    }

    @Test fun `van juntos dice por donde va cada uno`() {
        val serie = Obra(id = "s", tipo = Tipos.SERIE, titulo = "Dark", estado = Estados.EN_CURSO, vistos = listOf("2x5"))
        val l = Juntos.enCurso(listOf(serie), listOf(suya("Dark", tipo = Tipos.SERIE, extra = mapOf("avance" to "T2 · E3")), suya("Otra")))
        assertEquals(1, l.size)
        assertEquals("T2 · E5", l[0].miAvance)
        assertEquals("T2 · E3", l[0].suAvance)
    }

    private fun diario(id: Long, titulo: String, fecha: String) = PublicacionSocial(id, PersonaSocial("ana"), "diario", 0, "publica",
        buildJsonObject { put("titulo", titulo); put("tipoObra", "pelicula"); put("fecha", fecha) })

    @Test fun `su maraton cuenta lo que terminaron los dos este anio y cuando coincidieron`() {
        val mias = listOf(Obra(id = "1", tipo = Tipos.PELICULA, titulo = "Dune", estado = Estados.TERMINADO, terminada = "2026-03-02"),
            Obra(id = "2", tipo = Tipos.PELICULA, titulo = "Coco", estado = Estados.TERMINADO, terminada = "2025-12-30"),
            Obra(id = "3", tipo = Tipos.PELICULA, titulo = "Solo mía", estado = Estados.TERMINADO, terminada = "2026-01-01"))
        val suyas = listOf(diario(1, "Dune", "2026-03-20"), diario(2, "Dune", "2026-01-01"), diario(3, "Coco", "2026-06-01"), diario(4, "Solo suya", "2026-02-02"))
        val l = Juntos.terminadasEnComun(mias, suyas)
        assertEquals(listOf("Coco", "Dune"), l.map { it.obra.titulo })
        assertEquals(mapOf(Tipos.PELICULA to 1), Juntos.delAnio(l, 2026))
        assertEquals(java.time.LocalDate.parse("2026-03-20"), Juntos.coincidieron(l.first { it.obra.titulo == "Dune" }))
        assertNull(Juntos.coincidieron(l.first { it.obra.titulo == "Coco" }))
        val perfil = io.github.fogonapps.social.PerfilSocial(PersonaSocial("ana"), publicaciones = suyas)
        assertEquals(mapOf(Tipos.PELICULA to 1), Juntos.enComunDelAnio(Estado(obras = mias), perfil, 2026))
    }

    private fun resena(id: Long, quien: String, megusta: Int, hace: Long, ahora: Long, texto: String = "Muy buena") = PublicacionSocial(id, PersonaSocial(quien), "resena",
        ahora - hace, "publica", buildJsonObject { put("titulo", "T$id"); put("texto", texto) }, megusta = megusta)

    @Test fun `las resenas de la comunidad van con palabras, recientes y con me gusta`() {
        val ahora = 100L * 86_400_000L
        val dia = 86_400_000L
        val l = listOf(resena(1, "ana", 2, dia, ahora), resena(2, "beto", 9, 2 * dia, ahora), resena(3, "caro", 2, 3 * dia, ahora),
            resena(4, "yo", 50, dia, ahora), resena(5, "dani", 99, 60 * dia, ahora), resena(6, "eva", 40, dia, ahora, texto = ""), resena(2, "beto", 9, 2 * dia, ahora))
        assertEquals(listOf(2L, 1L, 3L), resenasDeLaComunidad(l, "yo", ahora).map { it.id })
        // Con menos de tres del último mes, entran las de antes.
        assertEquals(listOf(5L, 2L), resenasDeLaComunidad(l.filter { it.id in setOf(2L, 5L) }, null, ahora).map { it.id })
    }
}

package io.github.fogonapps.forzudo

import io.github.fogonapps.forzudo.datos.Biblioteca
import io.github.fogonapps.forzudo.datos.Estado
import io.github.fogonapps.forzudo.datos.JsonGym
import io.github.fogonapps.forzudo.datos.Logica
import io.github.fogonapps.forzudo.datos.Serie
import io.github.fogonapps.forzudo.datos.duracion
import io.github.fogonapps.forzudo.datos.duracionPalabras
import io.github.fogonapps.forzudo.datos.etiquetaSemana
import io.github.fogonapps.forzudo.datos.fk
import io.github.fogonapps.forzudo.datos.fmtVol
import io.github.fogonapps.forzudo.datos.inicioSemana
import io.github.fogonapps.forzudo.datos.semanasDelMes
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Genera las mismas salidas que tools/web-salida.js (la lógica original de la web)
 * para compararlas. Solo corre si se pasa la ruta de los datos de prueba:
 *   ./gradlew :apps:forzudo:testDebugUnitTest -Pprueba=/ruta/prueba.json -Psalida=/ruta/salida-kotlin.json
 */
class EquivalenciaWebTest {

    private fun n(v: Double?): JsonElement = if (v == null) JsonNull else JsonPrimitive(v)
    private fun series(l: List<Serie>?): JsonElement = if (l == null) JsonNull else buildJsonArray {
        l.forEach { s -> add(buildJsonObject { put("kg", n(s.kg)); put("reps", n(s.reps)); put("done", s.done) }) }
    }

    @Test
    fun generarSalida() {
        val ruta = System.getProperty("prueba")
        assumeTrue("Sin datos de prueba", !ruta.isNullOrBlank())
        val bib = Biblioteca.cargar { File("src/main/assets/datos/$it").readText() }
        val L = Logica(bib)
        val raiz = JsonGym.parseToJsonElement(File(ruta!!).readText()).jsonObject
        // Mi Gym Fogón ya no usa marcas manuales de semana: se comparan sin ellas.
        val e = L.normalizar(JsonGym.decodeFromJsonElement<Estado>(raiz.getValue("estado"))).copy(weekMarks = emptyMap())
        val nube = JsonGym.decodeFromJsonElement<Estado>(raiz.getValue("nube"))
        val hoy = LocalDate.of(2026, 10, 11)
        val dias = L.dias(e)

        val o = buildJsonObject {
            val r = L.racha(e, dias, hoy)
            put("streaks", buildJsonObject { put("cur", r.actual); put("best", r.mejor) })
            put("diaHoy", L.diaDeHoy(dias, hoy)?.let { JsonPrimitive(it) } ?: JsonNull)
            val ws = (e.sessions.map { inicioSemana(it.date) } + e.weekMarks.keys).toSortedSet()
            put("semanas", buildJsonObject { ws.forEach { put(it, L.semanaHecha(e, it, dias)) } })
            put("weekMarks", buildJsonObject { e.weekMarks.forEach { (k, v) -> put(k, v) } })
            put("titulos", buildJsonArray { e.sessions.forEach { add(it.title?.let { t -> JsonPrimitive(t) } ?: JsonNull) } })
            put("weekStart", buildJsonArray { listOf("2026-10-11", "2026-10-05", "2026-10-06", "2026-03-01", "2026-01-01").forEach { add(inicioSemana(it)) } })
            put("monthWeeks", buildJsonArray { listOf("2026-02", "2026-03", "2026-10", "2027-01").forEach { m -> add(buildJsonArray { semanasDelMes(m).forEach { add(it) } }) } })
            put("weekLabel", buildJsonArray { listOf("2026-09-28", "2026-10-05").forEach { add(etiquetaSemana(it)) } })
            put("fk", buildJsonArray { listOf(20.0, 12.5, 7.25, 0.333333, 1234.5, null, 80.20000001).forEach { add(fk(it)) } })
            put("fmtVol", buildJsonArray { listOf(1234.4, 0.0, 98765.6).forEach { add(fmtVol(it)) } })
            put("dur", buildJsonArray { listOf(0L, 65000L, 3725000L).forEach { add(duracion(it)) } })
            put("durWords", buildJsonArray { listOf(1800000L, 3900000L).forEach { add(duracionPalabras(it)) } })
            put("ej", buildJsonObject {
                bib.ejercicios.forEach { ej ->
                    val id = ej.id
                    val rk = L.rangoEjercicio(e, id)
                    val nt = L.siguienteMeta(id, rk)
                    val (meta, consejo) = L.meta(e, bib.cfg(io.github.fogonapps.forzudo.datos.Item(id, 3)))
                    val alt = L.alternativas(id)
                    put(id, buildJsonObject {
                        put("prev", series(L.anterior(e, id)))
                        put("maxKg", n(L.maxKg(e, id))); put("maxReps", n(L.maxReps(e, id)))
                        put("goal", meta + (consejo?.let { ": <b>$it</b>" } ?: ""))
                        put("stalled", L.estancado(e, id))
                        put("alts", buildJsonArray {
                            add(buildJsonArray { alt.variantes.forEach { add(it.id) } })
                            add(buildJsonArray { alt.relacionados.forEach { add(it.id) } })
                        })
                        put("rank", if (rk == null) JsonNull else buildJsonObject {
                            put("s", rk.rango.s); put("name", rk.rango.nombre); put("pct", rk.pct); put("e1", rk.e1)
                        })
                        put("next", if (nt == null) JsonNull else buildJsonObject {
                            put("kg", nt.kg); put("reps", nt.reps); put("rank", nt.rango)
                        })
                    })
                }
            })
            put("musculos", buildJsonObject {
                bib.musculos.forEach { m ->
                    val r = L.rangoMusculo(e, m)
                    put(m.id, if (r == null) JsonNull else buildJsonObject {
                        put("s", r.rango.s); put("name", r.rango.nombre)
                        put("list", buildJsonArray { r.lista.forEach { add(it.first) } })
                        put("tip", r.consejo?.let { t ->
                            buildJsonObject { put("kg", t.kg); put("reps", t.reps); put("rank", t.rango); put("id", t.id) }
                        } ?: JsonNull)
                    })
                }
            })
            val g = L.rangoGeneral(e)
            put("general", if (g == null) JsonNull else buildJsonObject { put("s", g.rango.s); put("name", g.rango.nombre); put("count", g.cuenta) })
            put("seriesSemana", buildJsonObject { L.seriesSemana(e, hoy).forEach { (k, v) -> put(k, v) } })
            put("peso", n(L.pesoCorporal(e)))
            put("vol", buildJsonArray { e.sessions.forEach { add(L.volumen(it.entries)) } })
            put("sets", buildJsonArray { e.sessions.forEach { add(L.series(it.entries)) } })
            put("rangos", buildJsonArray {
                listOf(0.0, 0.5, 1.0, 1.34, 2.99, 3.66, 4.0, 5.2, 5.99).forEach { s ->
                    val r = L.rango(s)
                    add(buildJsonObject {
                        put("tier", r.tier); put("div", r.div); put("name", r.nombre); put("color", r.color); put("s", r.s)
                        put("dp", L.progresoDivision(s)); put("nb", n(L.siguienteLimite(s)))
                    })
                }
            })
            val u = L.unir(e, nube)
            put("union", buildJsonObject {
                put("ids", buildJsonArray { u.sessions.forEach { add(it.id) } })
                put("body", buildJsonArray { u.body.forEach { add(buildJsonObject { put("date", it.date); put("kg", it.kg) }) } })
                put("weekMarks", buildJsonObject { u.weekMarks.forEach { (k, v) -> put(k, v) } })
                put("deleted", buildJsonArray { u.deleted.forEach { add(it) } })
                put("program", u.program?.let { JsonPrimitive(it) } ?: JsonNull)
                put("updatedAt", u.updatedAt)
                put("active", u.active?.id?.let { JsonPrimitive(it) } ?: JsonNull)
            })
        }
        File(System.getProperty("salida")!!).writeText(o.toString())
    }

}

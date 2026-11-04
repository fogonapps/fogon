package io.github.fogonapps.social

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

/** Una película, serie o anime que la gente está buscando: su título, cuántas visitas tuvo su artículo y sus ids. */
@Serializable data class Buscado(val titulo: String, val vistas: Long, val wikidata: String, val tmdb: String = "")

/** Lo más buscado de un día en la Wikipedia de un idioma, por sección. */
@Serializable data class DelMomento(val dia: String = "", val peliculas: List<Buscado> = emptyList(), val series: List<Buscado> = emptyList(), val anime: List<Buscado> = emptyList())

/**
 * Lo del momento: los artículos más visitados de Wikipedia el día anterior (datos abiertos de Wikimedia) que son
 * películas, series o anime según Wikidata. Es lo que la gente está buscando de verdad (estrenos, fechas como el Día
 * de Muertos, lo que se volvió tema). Se calcula aquí cada 12 horas para que los teléfonos no tengan que bajar la
 * lista entera; la app lo junta con las tendencias de TMDB, ListenBrainz y Open Library.
 */
object Momento {
    private const val AGENTE = "FogonSocial/1.0 (https://github.com/fogonapps/fogon; fogonapps@gmail.com)"
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NORMAL).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val guardado = ConcurrentHashMap<String, DelMomento>()
    val IDIOMAS = listOf("es", "en")

    private val PELICULAS = setOf("Q11424", "Q202866", "Q24862")                       // película, película animada, cortometraje
    private val SERIES = setOf("Q5398426", "Q581714", "Q526877", "Q1259759", "Q15416")   // serie, serie animada, web, miniserie, programa de TV
    private val ANIME = setOf("Q63952888", "Q1107", "Q20650540", "Q100269041")           // serie anime, anime, película anime, ONA

    fun de(idioma: String): DelMomento = guardado[if (idioma in IDIOMAS) idioma else "es"] ?: DelMomento()

    /** Actualiza los idiomas cada 12 horas, en un hilo aparte (si falla, se queda con lo anterior y vuelve a intentar en 1 hora). */
    fun empezar() {
        Thread({
            while (true) {
                var bien = true
                for (l in IDIOMAS) runCatching { guardado[l] = calcular(l) }.onFailure { bien = false; System.err.println("momento $l: ${it.message}") }
                // El anime casi no tiene artículo propio en español: si hay pocos, se suman los más buscados en inglés.
                val es = guardado["es"]; val en = guardado["en"]
                if (es != null && en != null && es.anime.size < 6) guardado["es"] = es.copy(anime = (es.anime + en.anime).distinctBy { it.wikidata }.take(20))
                Thread.sleep(if (bien) 12 * 3600_000L else 3600_000L)
            }
        }, "momento").apply { isDaemon = true }.start()
    }

    private fun pedir(url: String, cuerpo: String? = null): String {
        val r = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(60)).header("User-Agent", AGENTE).header("Accept", "application/json")
            .apply { if (cuerpo != null) header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(cuerpo)) else GET() }.build()
        val res = http.send(r, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() != 200) throw IllegalStateException("$url → ${res.statusCode()}")
        return res.body()
    }

    /** "Coco (película)" → "Coco". */
    fun limpiar(titulo: String) = titulo.replace('_', ' ').replace(Regex("\\s*\\([^)]*\\)\\s*$"), "").trim()

    fun calcular(idioma: String): DelMomento {
        // El día anterior (en UTC); si todavía no está, el de antes.
        val (dia, top) = (1L..2L).firstNotNullOf { atras ->
            val d = LocalDate.now(ZoneOffset.UTC).minusDays(atras)
            runCatching { d to pedir("https://wikimedia.org/api/rest_v1/metrics/pageviews/top/$idioma.wikipedia/all-access/${d.year}/${"%02d".format(d.monthValue)}/${"%02d".format(d.dayOfMonth)}") }.getOrNull()
        }
        val articulos = json.parseToJsonElement(top).jsonObject["items"]!!.jsonArray[0].jsonObject["articles"]!!.jsonArray
            .map { it.jsonObject }.map { it["article"]!!.jsonPrimitive.content to (it["views"]?.jsonPrimitive?.longOrNull ?: 0) }
            .filter { (a, _) -> ':' !in a && a != "Main_Page" && a != "Wikipedia:Portada" }.take(1000)
        val vistas = articulos.associate { (a, v) -> a.replace('_', ' ') to v }
        val valores = vistas.keys.joinToString(" ") { "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\"@$idioma" }
        val clases = (PELICULAS + SERIES + ANIME).joinToString(" ") { "wd:$it" }
        val consulta = """SELECT ?t ?i ?c ?pm ?pt WHERE { VALUES ?t { $valores } ?a schema:name ?t ; schema:about ?i ; schema:isPartOf <https://$idioma.wikipedia.org/> .
            ?i wdt:P31 ?c . VALUES ?c { $clases } OPTIONAL { ?i wdt:P4947 ?pm } OPTIONAL { ?i wdt:P4983 ?pt } }"""
        val filas = json.parseToJsonElement(pedir("https://query.wikidata.org/sparql", "format=json&query=" + URLEncoder.encode(consulta, "UTF-8")))
            .jsonObject["results"]!!.jsonObject["bindings"] as JsonArray
        fun JsonObject.v(k: String) = (this[k] as? JsonObject)?.get("value")?.jsonPrimitive?.content.orEmpty()
        data class Fila(val t: String, val i: String, val c: String, val tmdb: String)
        val todas = filas.map { it.jsonObject }.map { f ->
            Fila(f.v("t"), f.v("i").substringAfterLast('/'), f.v("c").substringAfterLast('/'),
                f.v("pm").takeIf { it.isNotBlank() }?.let { "tmdb:movie:$it" } ?: f.v("pt").takeIf { it.isNotBlank() }?.let { "tmdb:tv:$it" } ?: "")
        }
        // Una obra puede tener varias clases: anime gana a serie y a película.
        val porObra = todas.groupBy { it.i }.map { (_, l) ->
            val clase = when { l.any { it.c in ANIME } -> "anime"; l.any { it.c in SERIES } -> "serie"; else -> "pelicula" }
            val f = l.first()
            clase to Buscado(limpiar(f.t), vistas[f.t] ?: 0, f.i, l.firstOrNull { it.tmdb.isNotBlank() }?.tmdb.orEmpty())
        }
        fun de(c: String) = porObra.filter { it.first == c }.map { it.second }.sortedByDescending { it.vistas }.distinctBy { it.titulo.lowercase() }.take(20)
        return DelMomento(dia.toString(), de("pelicula"), de("serie"), de("anime"))
    }
}

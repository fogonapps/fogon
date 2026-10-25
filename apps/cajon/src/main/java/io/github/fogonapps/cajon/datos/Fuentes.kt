package io.github.fogonapps.cajon.datos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/*
 * De dónde salen los datos de las obras (verificado el 25-10-2026):
 *  - TMDB (películas, series, animes): con la clave de la propia persona (licencia gratis no comercial,
 *    personal e intransferible). Pide su logo y un aviso, y no guardar sus datos más de 6 meses sin renovarlos.
 *  - Open Library (libros): datos abiertos del Internet Archive, sin clave.
 *  - MusicBrainz (álbumes y artistas): datos CC0, sin clave; pide un User-Agent con contacto y como mucho
 *    una consulta por segundo. Las carátulas vienen del Cover Art Archive.
 * Solo se consulta cuando la persona busca o abre una obra; nada se manda de vuelta.
 */

/** Algo encontrado al buscar, antes de agregarlo. */
data class Encontrado(
    val tipo: String,
    val titulo: String,
    val anio: Int?,
    val creador: String,
    val portada: String,
    val fuente: String,
    /** Una línea extra (el título original, "EP", el país…). */
    val detalle: String = "",
    /** La nota del público, si la fuente la da en la búsqueda (ver [Obra.publica]). */
    val publica: Double? = null,
    val votos: Int = 0,
)

/** Un artista encontrado en MusicBrainz ([foto] la pone después [Wikimedia]). */
data class ArtistaEncontrado(val mbid: String, val nombre: String, val pais: String, val desde: Int?, val tipo: String, val detalle: String, val foto: String = "")

class SinRed : IOException()
class ClaveMala : IOException()
/** El servicio pidió esperar (503 o 429): se reintenta. */
class Ocupado : IOException()

private const val AGENTE = "ElCajon-Fogon/1.0 (fogonapps@gmail.com)"
private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

private suspend fun pedirJson(url: HttpUrl, cabeceras: Map<String, String> = emptyMap()): JsonElement = withContext(Dispatchers.IO) {
    val req = Request.Builder().url(url).header("User-Agent", AGENTE).header("Accept", "application/json")
        .apply { cabeceras.forEach { (k, v) -> header(k, v) } }.build()
    try {
        http.newCall(req).execute().use { r ->
            if (r.code == 401) throw ClaveMala()
            if (r.code == 503 || r.code == 429) throw Ocupado()
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            JsonCajon.parseToJsonElement(r.body.string())
        }
    } catch (e: ClaveMala) { throw e } catch (e: Ocupado) { throw e } catch (e: IOException) { throw SinRed() }
}

private fun JsonElement?.texto(): String = (this as? JsonPrimitive)?.contentOrNull ?: ""
private fun JsonElement?.entero(): Int? = (this as? JsonPrimitive)?.intOrNull
private fun JsonElement?.decimal(): Double? = (this as? JsonPrimitive)?.doubleOrNull
private fun JsonObject.arr(k: String): JsonArray = (this[k] as? JsonArray) ?: JsonArray(emptyList())
private fun anioDe(fecha: String) = fecha.take(4).toIntOrNull()

/* =================== TMDB =================== */

class Tmdb(private val clave: String) {
    private val base = "https://api.themoviedb.org/3".toHttpUrl()

    // Sirven las dos claves de TMDB: la "API Key" (v3, va en la dirección) y el token de lectura (v4, en la cabecera).
    private val esToken = clave.startsWith("eyJ")

    private suspend fun pedir(ruta: String, vararg params: Pair<String, String>): JsonObject {
        val u = base.newBuilder().addPathSegments(ruta.trimStart('/')).apply {
            params.forEach { (k, v) -> addQueryParameter(k, v) }
            if (!esToken) addQueryParameter("api_key", clave)
        }.build()
        return pedirJson(u, if (esToken) mapOf("Authorization" to "Bearer $clave") else emptyMap()).jsonObject
    }

    /** Comprueba la clave (para guardarla solo si sirve). */
    suspend fun probar() { pedir("configuration") }

    /**
     * Busca películas, series o animes. Un anime es una serie o película animada (género 16) en japonés;
     * al buscar "anime" se miran las dos y se dejan solo esas.
     */
    suspend fun buscar(texto: String, tipo: String, idioma: String): List<Encontrado> {
        val lang = if (idioma == "en") "en-US" else "es-ES"
        val pelis = if (tipo == Tipos.PELICULA || tipo == Tipos.ANIME) pedir("search/movie", "query" to texto, "language" to lang).arr("results") else JsonArray(emptyList())
        val series = if (tipo == Tipos.SERIE || tipo == Tipos.ANIME) pedir("search/tv", "query" to texto, "language" to lang).arr("results") else JsonArray(emptyList())
        fun esAnime(o: JsonObject) = o.arr("genre_ids").any { it.entero() == 16 } && o["original_language"].texto() == "ja"
        val r = mutableListOf<Encontrado>()
        series.map { it.jsonObject }.filter { tipo != Tipos.ANIME || esAnime(it) }.forEach { o ->
            r += Encontrado(if (esAnime(o)) Tipos.ANIME else Tipos.SERIE, o["name"].texto(), anioDe(o["first_air_date"].texto()), "",
                imagen(o["poster_path"].texto(), "w342"), "tmdb:tv:${o["id"].entero()}", o["original_name"].texto().takeIf { it != o["name"].texto() } ?: "",
                publica(o), votos(o))
        }
        pelis.map { it.jsonObject }.filter { tipo != Tipos.ANIME || esAnime(it) }.forEach { o ->
            r += Encontrado(if (esAnime(o)) Tipos.ANIME else Tipos.PELICULA, o["title"].texto(), anioDe(o["release_date"].texto()), "",
                imagen(o["poster_path"].texto(), "w342"), "tmdb:movie:${o["id"].entero()}", o["original_title"].texto().takeIf { it != o["title"].texto() } ?: "",
                publica(o), votos(o))
        }
        return r
    }

    /** Los datos completos de una película o serie para guardarla. Si no hay sinopsis en el idioma, la trae en inglés. */
    suspend fun detalles(fuente: String, tipoPedido: String, idioma: String): Obra {
        val (_, clase, id) = fuente.split(":")
        val lang = if (idioma == "en") "en-US" else "es-ES"
        val o = pedir("$clase/$id", "language" to lang, "append_to_response" to "credits")
        var sinopsis = o["overview"].texto()
        if (sinopsis.isBlank() && lang != "en-US") sinopsis = pedir("$clase/$id", "language" to "en-US")["overview"].texto()
        val generos = o.arr("genres").map { it.jsonObject["name"].texto() }.filter { it.isNotBlank() }
        val ahora = System.currentTimeMillis()
        return if (clase == "movie") {
            val director = (o["credits"] as? JsonObject)?.arr("crew")?.map { it.jsonObject }?.filter { it["job"].texto() == "Director" }?.joinToString(", ") { it["name"].texto() } ?: ""
            Obra(
                id = "", tipo = tipoPedido, titulo = o["title"].texto(), original = o["original_title"].texto().takeIf { it != o["title"].texto() } ?: "",
                anio = anioDe(o["release_date"].texto()), creador = director, generos = generos, sinopsis = sinopsis,
                portada = imagen(o["poster_path"].texto(), "w500"), fondo = imagen(o["backdrop_path"].texto(), "w780"),
                fuente = fuente, minutos = o["runtime"].entero()?.takeIf { it > 0 }, traida = ahora, publica = publica(o), votos = votos(o),
            )
        } else {
            val creadores = o.arr("created_by").joinToString(", ") { it.jsonObject["name"].texto() }
            val temporadas = o.arr("seasons").map { it.jsonObject }.filter { (it["season_number"].entero() ?: 0) > 0 && (it["episode_count"].entero() ?: 0) > 0 }
                .map { Temporada(it["season_number"].entero()!!, it["episode_count"].entero()!!, it["name"].texto()) }
            Obra(
                id = "", tipo = tipoPedido, titulo = o["name"].texto(), original = o["original_name"].texto().takeIf { it != o["name"].texto() } ?: "",
                anio = anioDe(o["first_air_date"].texto()), creador = creadores, generos = generos, sinopsis = sinopsis,
                portada = imagen(o["poster_path"].texto(), "w500"), fondo = imagen(o["backdrop_path"].texto(), "w780"),
                fuente = fuente, minutos = o.arr("episode_run_time").firstOrNull()?.entero(), temporadas = temporadas, traida = ahora,
                publica = publica(o), votos = votos(o),
            )
        }
    }

    // La nota del público (de 0 a 10); sin votos no dice nada.
    private fun votos(o: JsonObject) = o["vote_count"].entero() ?: 0
    private fun publica(o: JsonObject) = o["vote_average"].decimal()?.takeIf { votos(o) > 0 && it > 0 }

    companion object {
        const val URL_CLAVE = "https://www.themoviedb.org/settings/api"
        fun imagen(ruta: String, tamano: String) = if (ruta.isBlank()) "" else "https://image.tmdb.org/t/p/$tamano$ruta"
    }
}

/* =================== Open Library =================== */

object OpenLibrary {
    private val base = "https://openlibrary.org".toHttpUrl()

    suspend fun buscar(texto: String): List<Encontrado> {
        val u = base.newBuilder().addPathSegment("search.json").addQueryParameter("q", texto)
            .addQueryParameter("fields", "key,title,author_name,first_publish_year,cover_i,number_of_pages_median,ratings_average,ratings_count")
            .addQueryParameter("limit", "25").build()
        return pedirJson(u).jsonObject.arr("docs").map { it.jsonObject }.map { d ->
            Encontrado(
                Tipos.LIBRO, d["title"].texto(), d["first_publish_year"].entero(), d.arr("author_name").take(2).joinToString(", ") { it.texto() },
                portada(d["cover_i"].entero(), "M"), "ol:" + d["key"].texto().removePrefix("/works/"),
                d["number_of_pages_median"].entero()?.let { "$it p." } ?: "",
                d["ratings_average"].decimal()?.takeIf { (d["ratings_count"].entero() ?: 0) > 0 }, d["ratings_count"].entero() ?: 0,
            )
        }
    }

    /** La obra completa: sinopsis, temas y la portada grande. Lo de la búsqueda llega en [e]. */
    suspend fun detalles(e: Encontrado): Obra {
        val id = e.fuente.removePrefix("ol:")
        val w = pedirJson(base.newBuilder().addPathSegments("works/$id.json").build()).jsonObject
        val desc = sinFormato(w["description"].let { d -> if (d is JsonObject) d["value"].texto() else d.texto() }
            .substringBefore("\n----").substringBefore("([source]").trim())
        val temas = w.arr("subjects").map { it.texto() }.filter { t -> t.length in 3..30 && !t.contains(":") && RUIDO.none { it in t.lowercase() } }.take(4)
        val cubierta = w.arr("covers").firstOrNull()?.entero()?.takeIf { it > 0 }
        // La nota de los lectores de Open Library (de 1 a 5); si no se puede traer, la de la búsqueda.
        val notas = runCatching { pedirJson(base.newBuilder().addPathSegments("works/$id/ratings.json").build()).jsonObject["summary"] as? JsonObject }.getOrNull()
        val votos = notas?.get("count").entero() ?: e.votos
        return Obra(
            id = "", tipo = Tipos.LIBRO, titulo = w["title"].texto().ifBlank { e.titulo }, anio = e.anio, creador = e.creador,
            generos = temas, sinopsis = desc, portada = cubierta?.let { portada(it, "L") } ?: e.portada.replace("-M.jpg", "-L.jpg"),
            fuente = e.fuente, paginas = e.detalle.removeSuffix(" p.").toIntOrNull(), traida = System.currentTimeMillis(),
            publica = (notas?.get("average").decimal() ?: e.publica)?.takeIf { votos > 0 }, votos = votos,
        )
    }

    /** Temas de Open Library que no son géneros (de bibliotecas, idiomas, formatos). */
    private val RUIDO = listOf("language", "translation", "accessible", "daisy", "library", "lending", "reading level", "large type",
        "juvenile", "social conditions", "materials", "in spanish", "long now", "open_syllabus", "staff picks", "protected")

    /** Quita el formato de Markdown que traen algunas descripciones (*cursiva*, **negrita**, [texto](enlace)). */
    fun sinFormato(t: String) = t.replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1").replace(Regex("(\\*{1,2}|_{1,2})([^*_\\n]+)\\1"), "$2")

    private fun portada(id: Int?, t: String) = if (id == null || id <= 0) "" else "https://covers.openlibrary.org/b/id/$id-$t.jpg"
}

/* =================== MusicBrainz =================== */

object MusicBrainz {
    private val base = "https://musicbrainz.org/ws/2".toHttpUrl()
    // MusicBrainz pide como mucho una consulta por segundo desde cada aplicación.
    private val turno = Mutex()
    private var ultima = 0L

    private suspend fun pedir(ruta: String, vararg params: Pair<String, String>): JsonObject = turno.withLock {
        val u = base.newBuilder().addPathSegments(ruta).apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            .addQueryParameter("fmt", "json").build()
        // Si dice que está ocupado (cuando le llegan muchas consultas, también de otras apps), se espera y se reintenta.
        for (intento in 0..3) {
            val espera = (1100 + intento * 1500) - (System.currentTimeMillis() - ultima)
            if (espera > 0) delay(espera)
            try { return@withLock pedirJson(u).jsonObject } catch (_: Ocupado) {
            } finally { ultima = System.currentTimeMillis() }
        }
        throw SinRed()
    }

    fun caratula(mbid: String, t: Int = 500) = "https://coverartarchive.org/release-group/$mbid/front-$t"

    private fun deGrupo(g: JsonObject): Encontrado {
        val artistas = g.arr("artist-credit").joinToString("") { (it.jsonObject["name"].texto()) + it.jsonObject["joinphrase"].texto() }
        val tipos = listOf(g["primary-type"].texto()) + g.arr("secondary-types").map { it.texto() }
        return Encontrado(Tipos.ALBUM, g["title"].texto(), anioDe(g["first-release-date"].texto()), artistas,
            caratula(g["id"].texto(), 250), "mb:rg:" + g["id"].texto(), tipos.filter { it.isNotBlank() }.joinToString(" · "))
    }

    /** Ordena por relevancia; entre los igual de relevantes, los álbumes antes que los sencillos y lo conocido (más ediciones) primero. */
    suspend fun buscarAlbumes(texto: String): List<Encontrado> =
        pedir("release-group", "query" to texto, "limit" to "40").arr("release-groups").map { it.jsonObject }
            .sortedWith(compareByDescending<JsonObject> { (it["score"].entero() ?: 0) / 10 }
                .thenBy { when (it["primary-type"].texto()) { "Album" -> 0; "EP" -> 1; "Single" -> 3; else -> 2 } }
                .thenByDescending { it["count"].entero() ?: 0 })
            .take(25).map { deGrupo(it) }

    /** Los artistas parecidos de verdad (MusicBrainz da un puntaje de 0 a 100; los de menos de 70 casi nunca son). */
    suspend fun buscarArtistas(texto: String): List<ArtistaEncontrado> =
        pedir("artist", "query" to texto, "limit" to "20").arr("artists").map { it.jsonObject }.filter { (it["score"].entero() ?: 0) >= 70 }.map { a ->
            ArtistaEncontrado(a["id"].texto(), a["name"].texto(), a["country"].texto(),
                anioDe((a["life-span"] as? JsonObject)?.get("begin").texto()), a["type"].texto(), a["disambiguation"].texto())
        }

    /** Los álbumes y EP de un artista (su discografía), del más nuevo al más viejo. */
    suspend fun discografia(mbid: String): List<Encontrado> =
        pedir("release-group", "artist" to mbid, "type" to "album|ep", "limit" to "100").arr("release-groups")
            .map { deGrupo(it.jsonObject) }.sortedByDescending { it.anio ?: 0 }

    /** El álbum completo: géneros y la lista de canciones de su primera edición oficial. */
    suspend fun detalles(e: Encontrado): Obra {
        val mbid = e.fuente.removePrefix("mb:rg:")
        val g = pedir("release-group/$mbid", "inc" to "genres+artist-credits+ratings")
        val generos = g.arr("genres").map { it.jsonObject }.sortedByDescending { it["count"].entero() ?: 0 }.map { it["name"].texto() }.take(5)
        val artista = g.arr("artist-credit").firstOrNull()?.jsonObject?.get("artist")?.jsonObject
        // La nota de quienes usan MusicBrainz (de 0 a 5).
        val nota = g["rating"] as? JsonObject
        val votos = nota?.get("votes-count").entero() ?: 0
        val ediciones = pedir("release", "release-group" to mbid, "inc" to "recordings", "status" to "official", "limit" to "10").arr("releases").map { it.jsonObject }
        // La edición más vieja (la original), no una reedición con extras.
        val edicion = ediciones.minByOrNull { it["date"].texto().ifBlank { "9999" } }
        val canciones = edicion?.arr("media")?.flatMap { m -> m.jsonObject.arr("tracks").map { t ->
            Cancion(t.jsonObject["title"].texto(), ((t.jsonObject["length"].entero() ?: 0) / 1000))
        } } ?: emptyList()
        return Obra(
            id = "", tipo = Tipos.ALBUM, titulo = g["title"].texto().ifBlank { e.titulo }, anio = e.anio,
            creador = e.creador, generos = generos, portada = caratula(mbid, 500), fuente = e.fuente,
            canciones = canciones, formato = e.detalle, minutos = canciones.sumOf { it.segundos }.takeIf { it > 0 }?.let { (it + 59) / 60 },
            traida = System.currentTimeMillis(), publica = nota?.get("value").decimal()?.takeIf { votos > 0 }, votos = votos,
        )
        // De paso, el artista (la app lo suma a Artistas con su fuente: "mb:artist:<id>").
        .copy(artista = artista?.let { "mb:artist:" + it["id"].texto() })
    }
}

/* =================== Wikimedia (fotos de artistas) =================== */

/**
 * Las fotos de los artistas: Wikidata une el artista de MusicBrainz (propiedad P434) con su imagen (P18) en
 * Wikimedia Commons, donde cada foto tiene licencia libre (CC BY, CC BY-SA, dominio público…) y su autor.
 * El crédito se guarda con la foto y se muestra en la ficha del artista.
 */
object Wikimedia {
    private val sparql = "https://query.wikidata.org/sparql".toHttpUrl()
    private val commons = "https://commons.wikimedia.org/w/api.php".toHttpUrl()

    /** mbid → dirección de la foto (400 px de ancho). Una sola consulta para varios artistas. */
    suspend fun fotos(mbids: List<String>): Map<String, String> {
        val ids = mbids.filter { it.matches(Regex("[0-9a-f-]{36}")) }.distinct().take(50)
        if (ids.isEmpty()) return emptyMap()
        val q = "SELECT ?mbid ?img WHERE { VALUES ?mbid { ${ids.joinToString(" ") { "\"$it\"" }} } ?a wdt:P434 ?mbid ; wdt:P18 ?img . }"
        val u = sparql.newBuilder().addQueryParameter("query", q).addQueryParameter("format", "json").build()
        val r = pedirJson(u, mapOf("Accept" to "application/sparql-results+json")).jsonObject
        val filas = (r["results"] as? JsonObject)?.arr("bindings") ?: return emptyMap()
        val m = LinkedHashMap<String, String>()
        filas.map { it.jsonObject }.forEach { b ->
            val id = (b["mbid"] as? JsonObject)?.get("value").texto()
            val img = (b["img"] as? JsonObject)?.get("value").texto()
            if (id.isNotBlank() && img.isNotBlank() && id !in m) m[id] = img.replace("http://", "https://") + "?width=400"
        }
        return m
    }

    /** El crédito de una foto de Commons: "Autor · Licencia · Wikimedia Commons" (sin HTML). */
    suspend fun credito(foto: String): String {
        val archivo = java.net.URLDecoder.decode(foto.substringAfter("Special:FilePath/").substringBefore("?"), "UTF-8")
        val u = commons.newBuilder().addQueryParameter("action", "query").addQueryParameter("format", "json")
            .addQueryParameter("prop", "imageinfo").addQueryParameter("iiprop", "extmetadata").addQueryParameter("titles", "File:$archivo").build()
        val paginas = pedirJson(u).jsonObject["query"]?.jsonObject?.get("pages") as? JsonObject ?: return ""
        val meta = paginas.values.firstOrNull()?.jsonObject?.arr("imageinfo")?.firstOrNull()?.jsonObject?.get("extmetadata") as? JsonObject ?: return ""
        fun v(k: String) = (meta[k] as? JsonObject)?.get("value").texto().replace(Regex("<[^>]+>"), "").replace("&amp;", "&").trim()
        return listOf(v("Artist"), v("LicenseShortName"), "Wikimedia Commons").filter { it.isNotBlank() }.joinToString(" · ")
    }
}

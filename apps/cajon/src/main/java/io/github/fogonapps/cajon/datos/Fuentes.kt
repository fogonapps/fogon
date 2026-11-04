package io.github.fogonapps.cajon.datos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
 * De dónde salen los datos de las obras:
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
    /** Sus otros títulos (TMDB: romaji, inglés, de otros países); solo se piden para comparar, ver [Logica.esLaMisma]. */
    val alias: List<String> = emptyList(),
    /** Sus géneros, si la fuente los da en la lista (TMDB en recomendaciones y tendencias). */
    val generos: List<String> = emptyList(),
)

/**
 * Una persona encontrada afuera: un artista de MusicBrainz, un escritor de Open Library o un director de TMDB.
 * [tipo] es el de MusicBrainz (Person, Group…) o [Personas.AUTOR] / [Personas.DIRECTOR]; [foto] la pone después
 * [Wikimedia] (o viene de TMDB). [fuente]: "mb:artist:<mbid>", "ol:author:OL…A", "tmdb:person:<id>".
 */
data class ArtistaEncontrado(
    val mbid: String, val nombre: String, val pais: String, val desde: Int?, val tipo: String, val detalle: String, val foto: String = "",
    val fuente: String = "mb:artist:$mbid",
    /** Otros nombres con que lo conoce MusicBrainz ("Los Enanitos Verdes" para "Enanitos Verdes"). */
    val alias: List<String> = emptyList(),
)

/** Lo que una fuente sabe de una persona (director o escritor): foto, biografía y su dirección, nacimiento y país. */
data class PersonaDeAfuera(val foto: String, val bio: String, val bioFuente: String, val desde: Int?, val pais: String)

/** Un enlace a otro servicio desde una ficha o un perfil ("Spotify", "Wikipedia"…). */
data class Enlace(val nombre: String, val url: String)

class SinRed : IOException()
class ClaveMala : IOException()
/** El servicio pidió esperar (503 o 429): se reintenta. */
class Ocupado : IOException()

private const val AGENTE = "ElCajon-Fogon/1.0 (fogonapps@gmail.com)"
private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

/** Los bytes de una imagen (para revisarla antes de usarla, ver [Fotos]). Null si no se pudo bajar. */
suspend fun bajarImagen(url: String, tope: Int = 4_000_000): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        http.newCall(Request.Builder().url(url).header("User-Agent", AGENTE).build()).execute().use { r ->
            if (!r.isSuccessful) null else r.body.bytes().takeIf { it.size in 1..tope }
        }
    }.getOrNull()
}

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
    /**
     * El idioma que se le pide a TMDB. En español, el latino (es-MX): los títulos de España ("¡Olvídate de mí!",
     * "Los mundos de Coraline") no son los que usa casi nadie en América. [ESPANA] es el de España, para los
     * títulos que no tienen versión latina y para buscar lo que alguien escribió con el título de allá.
     */
    private fun lengua(idioma: String) = if (idioma == "en") "en-US" else LATINO

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
    suspend fun buscar(texto: String, tipo: String, idioma: String, lang: String = lengua(idioma), anio: Int? = null): List<Encontrado> {
        // Con [anio], solo de ese año: un título común ("La noche") tiene tantos resultados que el de 1961 no sale entre los primeros.
        val pelis = if (tipo == Tipos.PELICULA || tipo == Tipos.ANIME) pedir("search/movie", "query" to texto, "language" to lang,
            *(anio?.let { arrayOf("year" to "$it") } ?: emptyArray())).arr("results") else JsonArray(emptyList())
        val series = if (tipo == Tipos.SERIE || tipo == Tipos.ANIME) pedir("search/tv", "query" to texto, "language" to lang,
            *(anio?.let { arrayOf("first_air_date_year" to "$it") } ?: emptyArray())).arr("results") else JsonArray(emptyList())
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

    /**
     * El nombre de una persona de los créditos en letras latinas: TMDB tiene a muchos en su escritura ("新海誠");
     * entonces se toma el primero de sus otros nombres que esté en letras latinas ("Makoto Shinkai").
     */
    private suspend fun enLatinas(p: JsonObject): String {
        val n = p["name"].texto()
        if (n.none { it.isLetter() && it.code > 0x24F }) return n
        val id = p["id"].entero() ?: return n
        return runCatching { pedir("person/$id").arr("also_known_as").map { it.texto() }
            .firstOrNull { a -> a.isNotBlank() && a.none { it.isLetter() && it.code > 0x24F } } }.getOrNull() ?: n
    }

    /** Los nombres de los géneros de TMDB en el idioma de la app (id → nombre), de películas y de series. */
    suspend fun generos(idioma: String): Map<Int, String> {
        val lang = lengua(idioma)
        GENEROS[lang]?.let { return it }
        val m = (pedir("genre/movie/list", "language" to lang).arr("genres") + pedir("genre/tv/list", "language" to lang).arr("genres"))
            .map { it.jsonObject }.mapNotNull { g -> g["id"].entero()?.let { it to g["name"].texto() } }.filter { it.second.isNotBlank() }.toMap()
        GENEROS[lang] = m
        return m
    }

    /** Una lista de TMDB (recomendaciones, tendencias) a [Encontrado], con sus géneros; [clase] null = la dice cada una. */
    private suspend fun lista(r: JsonObject, clase: String?, idioma: String): List<Encontrado> {
        val nombres = runCatching { generos(idioma) }.getOrDefault(emptyMap())
        fun esAnime(o: JsonObject) = o.arr("genre_ids").any { it.entero() == 16 } && o["original_language"].texto() == "ja"
        return r.arr("results").map { it.jsonObject }.mapNotNull { o ->
            val c = clase ?: when (o["media_type"].texto()) { "movie" -> "movie"; "tv" -> "tv"; else -> return@mapNotNull null }
            val serie = c == "tv"
            val titulo = (if (serie) o["name"] else o["title"]).texto().ifBlank { return@mapNotNull null }
            val original = (if (serie) o["original_name"] else o["original_title"]).texto()
            Encontrado(if (esAnime(o)) Tipos.ANIME else if (serie) Tipos.SERIE else Tipos.PELICULA, titulo,
                anioDe((if (serie) o["first_air_date"] else o["release_date"]).texto()), "", imagen(o["poster_path"].texto(), "w342"),
                "tmdb:$c:${o["id"].entero()}", original.takeIf { it != titulo } ?: "", publica(o), votos(o),
                generos = o.arr("genre_ids").mapNotNull { it.entero()?.let { id -> nombres[id] } })
        }
    }

    /** Lo que TMDB recomienda a quien le gustó esta película o serie (en el idioma de la app). */
    suspend fun recomendaciones(fuente: String, idioma: String): List<Encontrado> {
        val (_, clase, id) = fuente.split(":")
        return lista(pedir("$clase/$id/recommendations", "language" to lengua(idioma)), clase, idioma)
    }

    /**
     * Lo más visto de un género: `discover/movie` o `discover/tv` ([clase]) por popularidad, con su
     * género ([generos]) o su palabra clave ([palabras]); con [anime], solo animación japonesa (género 16, en japonés).
     */
    suspend fun descubrir(clase: String, generos: String?, palabras: String?, anime: Boolean, idioma: String): List<Encontrado> {
        val conGenero = listOfNotNull(if (anime) "16" else null, generos).joinToString(",").ifEmpty { null }
        if (conGenero == null && palabras == null) return emptyList()
        val params = listOfNotNull("language" to lengua(idioma), "sort_by" to "popularity.desc", "include_adult" to "false",
            conGenero?.let { "with_genres" to it }, palabras?.let { "with_keywords" to it }, if (anime) "with_original_language" to "ja" else null)
        return lista(pedir("discover/$clase", *params.toTypedArray()), clase, idioma)
    }

    /** Lo que más se está viendo esta semana (películas y series), para elegir gustos y para explorar. */
    suspend fun tendencias(idioma: String): List<Encontrado> = lista(pedir("trending/all/week", "language" to lengua(idioma)), null, idioma)

    /**
     * Las plataformas de un país (nombre → id), de lo que TMDB sabe por JustWatch (hay que darle el crédito a JustWatch
     * donde se muestre).
     */
    suspend fun plataformas(region: String, idioma: String): Map<String, Int> =
        listOf("movie", "tv").flatMap { c -> pedir("watch/providers/$c", "watch_region" to region, "language" to lengua(idioma)).arr("results") }
            .mapNotNull { it as? JsonObject }.mapNotNull { p -> p["provider_id"].entero()?.let { p["provider_name"].texto() to it } }.toMap()

    /**
     * Lo que está de moda en una plataforma de [region]: lo más popular que se puede ver ahí, solo lo reciente (películas
     * de los últimos dos años; series y anime con capítulos en los últimos dos meses).
     */
    suspend fun popularEn(proveedor: Int, clase: String, anime: Boolean, region: String, idioma: String): List<Encontrado> {
        val hoy = java.time.LocalDate.now()
        val params = listOfNotNull("language" to lengua(idioma), "sort_by" to "popularity.desc", "include_adult" to "false",
            "with_watch_providers" to "$proveedor", "watch_region" to region,
            if (clase == "movie") "primary_release_date.gte" to hoy.minusYears(2).toString() else "air_date.gte" to hoy.minusDays(60).toString(),
            if (anime) "with_genres" to "16" else null, if (anime) "with_original_language" to "ja" else null)
        return lista(pedir("discover/$clase", *params.toTypedArray()), clase, idioma)
    }

    /** El anime que está al aire (con capítulos en el último mes), lo más popular primero. */
    suspend fun animeAlAire(idioma: String): List<Encontrado> {
        val desde = java.time.LocalDate.now().minusDays(30).toString()
        return lista(pedir("discover/tv", "language" to lengua(idioma), "sort_by" to "popularity.desc", "with_genres" to "16",
            "with_original_language" to "ja", "air_date.gte" to desde, "include_adult" to "false"), "tv", idioma)
    }

    /** Lo más visto de la semana de una sola clase ("movie" o "tv"); el anime sale de [descubrir] con anime = true. */
    suspend fun tendenciasDe(clase: String, idioma: String): List<Encontrado> = lista(pedir("trending/$clase/week", "language" to lengua(idioma)), clase, idioma)

    /** Los otros títulos de una película o serie (romaji, inglés, de otros países): "Owari no Seraph" para "終わりのセラフ". */
    suspend fun titulosAlternativos(fuente: String): List<String> {
        val (_, clase, id) = fuente.split(":")
        val j = pedir("$clase/$id/alternative_titles")
        return (j.arr("titles") + j.arr("results")).map { it.jsonObject["title"].texto() }.filter { it.isNotBlank() }
    }

    /** Los datos completos de una película o serie para guardarla. Si no hay sinopsis en el idioma, la trae en inglés. */
    suspend fun detalles(fuente: String, tipoPedido: String, idioma: String): Obra {
        val (_, clase, id) = fuente.split(":")
        val lang = lengua(idioma)
        var o = pedir("$clase/$id", "language" to lang, "append_to_response" to "credits")
        var sinopsis = o["overview"].texto()
        if (lang == LATINO) {
            // Sin traducción latina, TMDB da el título original: si hay uno de España, mejor ese que el original
            // en japonés o coreano. Lo mismo con la sinopsis.
            val titulo = o["title"].texto().ifBlank { o["name"].texto() }
            val original = o["original_title"].texto().ifBlank { o["original_name"].texto() }
            if ((titulo == original && o["original_language"].texto() !in setOf("es", "en")) || sinopsis.isBlank()) {
                val es = pedir("$clase/$id", "language" to ESPANA)
                if (titulo == original && o["original_language"].texto() !in setOf("es", "en")) {
                    val t = es["title"].texto().ifBlank { es["name"].texto() }
                    val k = if (clase == "movie") "title" else "name"
                    if (t.isNotBlank()) o = JsonObject(o + (k to kotlinx.serialization.json.JsonPrimitive(t)))
                }
                if (sinopsis.isBlank()) sinopsis = es["overview"].texto()
            }
        }
        if (sinopsis.isBlank() && lang != "en-US") sinopsis = pedir("$clase/$id", "language" to "en-US")["overview"].texto()
        val generos = o.arr("genres").map { it.jsonObject["name"].texto() }.filter { it.isNotBlank() }
        val ahora = System.currentTimeMillis()
        return if (clase == "movie") {
            val director = ((o["credits"] as? JsonObject)?.arr("crew")?.map { it.jsonObject }?.filter { it["job"].texto() == "Director" } ?: emptyList())
                .map { enLatinas(it) }.joinToString(", ")
            Obra(
                id = "", tipo = tipoPedido, titulo = o["title"].texto(), original = o["original_title"].texto().takeIf { it != o["title"].texto() } ?: "",
                anio = anioDe(o["release_date"].texto()), creador = director, generos = generos, sinopsis = sinopsis,
                portada = imagen(o["poster_path"].texto(), "w500"), fondo = imagen(o["backdrop_path"].texto(), "w780"),
                fuente = fuente, minutos = o["runtime"].entero()?.takeIf { it > 0 }, traida = ahora, publica = publica(o), votos = votos(o),
            )
        } else {
            val creadores = o.arr("created_by").map { enLatinas(it.jsonObject) }.joinToString(", ")
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

    /** Directores (los que TMDB conoce por dirigir), con su foto. */
    suspend fun buscarDirectores(texto: String, idioma: String): List<ArtistaEncontrado> =
        pedir("search/person", "query" to texto, "language" to lengua(idioma)).arr("results").map { it.jsonObject }
            .filter { it["known_for_department"].texto() == "Directing" }.take(8).map { p ->
                ArtistaEncontrado("", p["name"].texto(), "", null, Personas.DIRECTOR,
                    p.arr("known_for").take(2).joinToString(", ") { k -> k.jsonObject["title"].texto().ifBlank { k.jsonObject["name"].texto() } },
                    imagen(p["profile_path"].texto(), "w185"), "tmdb:person:${p["id"].entero()}")
            }

    /**
     * Personas con ese nombre en cualquier oficio (creadores de series y guionistas también), para enlazar las del
     * cajón: primero las que dirigen.
     */
    suspend fun buscarPersonas(texto: String, idioma: String): List<ArtistaEncontrado> =
        pedir("search/person", "query" to texto, "language" to lengua(idioma)).arr("results").map { it.jsonObject }
            .sortedByDescending { if (it["known_for_department"].texto() == "Directing") 1 else 0 }.take(10).map { p ->
                ArtistaEncontrado("", p["name"].texto(), "", null, Personas.DIRECTOR, p["known_for_department"].texto(),
                    imagen(p["profile_path"].texto(), "w342"), "tmdb:person:${p["id"].entero()}")
            }

    /** Foto, biografía (en el idioma de la app o en inglés), año y lugar de nacimiento de una persona. */
    suspend fun persona(id: String, idioma: String): PersonaDeAfuera {
        val p = pedir("person/$id", "language" to lengua(idioma))
        val bio = p["biography"].texto().ifBlank { if (idioma != "en") pedir("person/$id", "language" to "en-US")["biography"].texto() else "" }
        return PersonaDeAfuera(imagen(p["profile_path"].texto(), "w342"), bio.trim(), "https://www.themoviedb.org/person/$id",
            anioDe(p["birthday"].texto()), p["place_of_birth"].texto().substringAfterLast(",").trim())
    }

    /** Lo que dirigió una persona (películas, series y animes), de lo más nuevo a lo más viejo. */
    suspend fun filmografia(id: String, idioma: String): List<Encontrado> {
        val c = pedir("person/$id/combined_credits", "language" to lengua(idioma))
        fun esAnime(o: JsonObject) = o.arr("genre_ids").any { it.entero() == 16 } && o["original_language"].texto() == "ja"
        return c.arr("crew").map { it.jsonObject }.filter { it["job"].texto() == "Director" }.mapNotNull { o ->
            val serie = o["media_type"].texto() == "tv"
            val titulo = (if (serie) o["name"] else o["title"]).texto().ifBlank { return@mapNotNull null }
            Encontrado(if (esAnime(o)) Tipos.ANIME else if (serie) Tipos.SERIE else Tipos.PELICULA, titulo,
                anioDe((if (serie) o["first_air_date"] else o["release_date"]).texto()), "", imagen(o["poster_path"].texto(), "w342"),
                "tmdb:${if (serie) "tv" else "movie"}:${o["id"].entero()}", "", publica(o), votos(o))
        }.distinctBy { it.fuente }.sortedByDescending { it.anio ?: 0 }
    }

    /** Los pósteres de una película o serie, para "Cambiar portada" (los del idioma de la app, en inglés y sin texto). */
    suspend fun posteres(fuente: String, idioma: String): List<String> = imagenes(fuente, "posters", idioma, "w500")

    /** Las imágenes de fondo de una película o serie, para elegir otra: primero las sin texto. */
    suspend fun fondos(fuente: String, idioma: String): List<String> = imagenes(fuente, "backdrops", idioma, "w780")

    /** Las fotos de una persona en TMDB (retratos), para elegir otra. */
    suspend fun fotosDePersona(id: String): List<String> =
        pedir("person/$id/images").arr("profiles").mapNotNull { (it as? JsonObject)?.get("file_path").texto().takeIf { r -> r.isNotBlank() } }
            .take(30).map { imagen(it, "w342") }

    /**
     * Todas las imágenes de un tipo (en todos los idiomas): primero las del idioma de la app, después
     * las sin texto, las en inglés y las demás; dentro de cada grupo, las mejor votadas.
     */
    private suspend fun imagenes(fuente: String, cuales: String, idioma: String, tamano: String): List<String> {
        val (_, clase, id) = fuente.split(":")
        val mio = if (idioma == "en") "en" else "es"
        fun orden(l: String) = when (l) { mio -> 0; "" -> if (cuales == "backdrops") -1 else 1; "en" -> 2; else -> 3 }
        return pedir("$clase/$id/images").arr(cuales).map { it.jsonObject }
            .sortedWith(compareBy<JsonObject> { orden(it["iso_639_1"].texto()) }.thenByDescending { it["vote_average"].decimal() ?: 0.0 })
            .mapNotNull { it["file_path"].texto().takeIf { r -> r.isNotBlank() } }.take(60).map { imagen(it, tamano) }
    }

    /** Los episodios de una temporada (número y nombre), para elegir los favoritos. */
    suspend fun episodios(fuente: String, temporada: Int, idioma: String): List<Pair<Int, String>> {
        val id = fuente.split(":").last()
        return pedir("tv/$id/season/$temporada", "language" to lengua(idioma)).arr("episodes").map { it.jsonObject }
            .mapNotNull { e -> e["episode_number"].entero()?.let { it to e["name"].texto() } }
    }

    // La nota del público (de 0 a 10); sin votos no dice nada.
    private fun votos(o: JsonObject) = o["vote_count"].entero() ?: 0
    private fun publica(o: JsonObject) = o["vote_average"].decimal()?.takeIf { votos(o) > 0 && it > 0 }

    companion object {
        /** Los nombres de géneros ya pedidos, por idioma (no cambian: una vez por sesión). */
        private val GENEROS = java.util.concurrent.ConcurrentHashMap<String, Map<Int, String>>()
        const val LATINO = "es-MX"
        const val ESPANA = "es-ES"
        const val INGLES = "en-US"
        const val URL_CLAVE = "https://www.themoviedb.org/settings/api"
        fun imagen(ruta: String, tamano: String) = if (ruta.isBlank()) "" else "https://image.tmdb.org/t/p/$tamano$ruta"
    }
}

/* =================== Open Library =================== */

object OpenLibrary {
    private val base = "https://openlibrary.org".toHttpUrl()

    /** El código de idioma de las ediciones de Open Library (MARC): "spa", "eng". */
    private fun marc(idioma: String) = if (idioma == "en") "eng" else "spa"

    /** Todas las fotos de un escritor en Open Library, para elegir. */
    suspend fun fotosDeAutor(olid: String): List<String> =
        pedirJson(base.newBuilder().addPathSegments("authors/$olid.json").build()).jsonObject.arr("photos")
            .mapNotNull { it.entero()?.takeIf { n -> n > 0 } }.map { "https://covers.openlibrary.org/a/id/$it-L.jpg" }

    /** La foto (de Open Library), la biografía y el nacimiento de un escritor. */
    suspend fun autor(olid: String): PersonaDeAfuera {
        val a = pedirJson(base.newBuilder().addPathSegments("authors/$olid.json").build()).jsonObject
        val bio = when (val b = a["bio"]) { is JsonObject -> b["value"].texto(); else -> b.texto() }
        val foto = a.arr("photos").map { it.entero() ?: -1 }.firstOrNull { it > 0 }?.let { "https://covers.openlibrary.org/a/id/$it-M.jpg" } ?: ""
        return PersonaDeAfuera(foto, sinFormato(bio).trim(), "https://openlibrary.org/authors/$olid", anioDe(a["birth_date"].texto().takeLast(4)), "")
    }

    /** Escritores: los que tienen obras de verdad (no los registros sueltos de bibliotecas), los más conocidos primero. */
    suspend fun buscarAutores(texto: String): List<ArtistaEncontrado> {
        val u = base.newBuilder().addPathSegments("search/authors.json").addQueryParameter("q", texto).addQueryParameter("limit", "20").build()
        return pedirJson(u).jsonObject.arr("docs").map { it.jsonObject }
            .filter { (it["work_count"].entero() ?: 0) >= 3 }
            .sortedByDescending { it["work_count"].entero() ?: 0 }
            // Open Library tiene al mismo autor repetido (registros de bibliotecas): queda el de más obras.
            .distinctBy { normal(it["name"].texto()).trim() }.take(6).map { a ->
                ArtistaEncontrado("", a["name"].texto(), "", anioDe(a["birth_date"].texto().takeLast(4)), Personas.AUTOR,
                    a["top_work"].texto(), fuente = "ol:author:" + a["key"].texto(), alias = a.arr("alternate_names").map { it.texto() })
            }
    }

    /** Las obras de un escritor, las más editadas primero, en el idioma de la app (como en [buscar]). */
    /** Los libros más buscados del año en Open Library (para elegir gustos), con el título en el idioma de la app si lo hay. */
    suspend fun populares(idioma: String): List<Encontrado> {
        val u = base.newBuilder().addPathSegments("trending/yearly.json").addQueryParameter("limit", "60").build()
        return ordenar(pedirJson(u).jsonObject.arr("works").map { it.jsonObject }, idioma, tope = 40)
    }

    /** Los libros de este año y el anterior que más se están buscando en Open Library (lo de ahora, no los clásicos de siempre). */
    suspend fun tendencias(idioma: String): List<Encontrado> {
        val anio = java.time.LocalDate.now().year
        val u = base.newBuilder().addPathSegment("search.json").addQueryParameter("q", "first_publish_year:[${anio - 1} TO $anio]")
            .addQueryParameter("sort", "trending").addQueryParameter("fields", CAMPOS).addQueryParameter("limit", "40").build()
        return ordenar(pedirJson(u).jsonObject.arr("docs").map { it.jsonObject }, idioma, tope = 24)
    }

    /** Las obras de un autor por su nombre (cuando no se tiene su id de Open Library), las más editadas primero. */
    suspend fun obrasDeNombre(nombre: String, idioma: String): List<Encontrado> {
        val u = base.newBuilder().addPathSegment("search.json").addQueryParameter("author", nombre).addQueryParameter("sort", "editions")
            .addQueryParameter("lang", if (idioma == "en") "en" else "es").addQueryParameter("fields", CAMPOS).addQueryParameter("limit", "40").build()
        return ordenar(pedirJson(u).jsonObject.arr("docs").map { it.jsonObject }, idioma, tope = 40)
    }

    /** Los libros de un tema (un género), los más leídos por quienes usan Open Library primero. */
    suspend fun porTema(tema: String, idioma: String): List<Encontrado> {
        val u = base.newBuilder().addPathSegment("search.json").addQueryParameter("subject", tema).addQueryParameter("sort", "readinglog")
            .addQueryParameter("lang", if (idioma == "en") "en" else "es").addQueryParameter("fields", CAMPOS).addQueryParameter("limit", "40").build()
        return ordenar(pedirJson(u).jsonObject.arr("docs").map { it.jsonObject }, idioma, tope = 40)
    }

    suspend fun obrasDe(autor: String, idioma: String): List<Encontrado> {
        val u = base.newBuilder().addPathSegment("search.json").addQueryParameter("author_key", autor).addQueryParameter("sort", "editions")
            .addQueryParameter("lang", if (idioma == "en") "en" else "es").addQueryParameter("fields", CAMPOS).addQueryParameter("limit", "60").build()
        return ordenar(pedirJson(u).jsonObject.arr("docs").map { it.jsonObject }, idioma, tope = 60)
    }

    private const val CAMPOS = "key,title,author_name,first_publish_year,cover_i,number_of_pages_median,ratings_average," +
        "ratings_count,edition_count,editions,editions.title,editions.cover_i,editions.language"

    /** Dos títulos se parecen si comparten una palabra (así no se cuela una edición mal marcada de otro idioma). */
    fun parecidos(a: String, b: String): Boolean {
        fun palabras(s: String) = normal(s).split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }.toSet()
        return palabras(a).intersect(palabras(b)).isNotEmpty() || normal(a) == normal(b)
    }

    /**
     * Busca libros. Open Library devuelve obras; con `lang` trae de cada una su mejor edición en el idioma de la
     * app, y de ahí salen el título y la portada (así sale "Cien años de soledad" con una portada en español).
     */
    /** Busca libros; con [autor], solo de ese autor (así el título que se compara es solo el título). */
    suspend fun buscar(texto: String, idioma: String, autor: String = ""): List<Encontrado> {
        val u = base.newBuilder().addPathSegment("search.json").addQueryParameter("q", texto)
            .apply { if (autor.isNotBlank()) addQueryParameter("author", autor) }
            .addQueryParameter("lang", if (idioma == "en") "en" else "es")
            .addQueryParameter("fields", CAMPOS).addQueryParameter("limit", "40").build()
        return ordenar(pedirJson(u).jsonObject.arr("docs").map { it.jsonObject }, idioma, consulta = texto)
    }

    /**
     * De los resultados crudos a la lista: sin la misma obra repetida (mismo título y autor; queda la de más
     * ediciones), sin las que solo existen en otro idioma, y lo conocido (muchas ediciones) un poco más arriba sin
     * perder el orden de relevancia; lo de una sola edición (guías, estudios, copias) va al final.
     */
    fun ordenar(docs: List<JsonObject>, idioma: String, tope: Int = 25, consulta: String = ""): List<Encontrado> {
        data class Libro(val e: Encontrado, val ediciones: Int, val puesto: Int, val clave: String)
        val mio = marc(idioma)
        val libros = docs.mapIndexedNotNull { i, d ->
            val ed = (d["editions"] as? JsonObject)?.arr("docs")?.firstOrNull() as? JsonObject
            val lenguas = ed?.arr("language")?.map { it.texto() } ?: emptyList()
            // Solo en otro idioma (ni el de la app ni inglés): es una traducción que no se buscó.
            if (lenguas.isNotEmpty() && mio !in lenguas && "eng" !in lenguas) return@mapIndexedNotNull null
            // El título de la edición en el idioma de la app, si se parece al de la obra o a lo que se buscó
            // ("El extranjero" para "L'étranger"); si no, puede ser una edición mal marcada de otro idioma.
            val tituloEd = ed?.get("title").texto()
            val enMiIdioma = mio in lenguas && (parecidos(tituloEd, d["title"].texto()) || consulta.isNotBlank() && parecidos(tituloEd, consulta))
            val titulo = (if (enMiIdioma) ed?.get("title").texto() else "").ifBlank { d["title"].texto() }
            val autores = d.arr("author_name").take(2).joinToString(", ") { it.texto() }
            val e = Encontrado(
                // En la lista, la portada de la obra (suele ser la carátula de verdad); la ficha elige la más linda.
                Tipos.LIBRO, titulo, d["first_publish_year"].entero(), autores, portada(d["cover_i"].entero(), "M"),
                "ol:" + d["key"].texto().removePrefix("/works/"), d["number_of_pages_median"].entero()?.let { "$it p." } ?: "",
                d["ratings_average"].decimal()?.takeIf { (d["ratings_count"].entero() ?: 0) > 0 }, d["ratings_count"].entero() ?: 0,
            )
            Libro(e, d["edition_count"].entero() ?: 1, i, normal(titulo).trim() + "|" + normal(autores.substringBefore(",")).trim())
        }
        val unicos0 = libros.groupBy { it.clave }.values.map { g -> g.maxBy { it.ediciones }.copy(puesto = g.minOf { it.puesto }) }
        // Si hay una obra muy editada, los libros de otros autores con ese título y casi sin ediciones son estudios
        // y guías sobre ella ("Cien años de soledad" de un crítico): se quitan.
        val fuertes = unicos0.filter { it.ediciones >= 20 }
        val unicos = unicos0.filter { l ->
            fuertes.none { g -> g !== l && l.ediciones <= 3 && normal(g.e.titulo).trim() in normal(l.e.titulo) && normal(g.e.creador) != normal(l.e.creador) }
        }
        return unicos.sortedWith(compareBy<Libro> { it.ediciones <= 1 }
            .thenBy { it.puesto - 4 * kotlin.math.ln(it.ediciones.toDouble()) / kotlin.math.ln(2.0) })
            .take(tope).map { it.e }
    }

    /**
     * Una obra por su id. Open Library junta obras repetidas y la vieja queda como redirección a la nueva (sus
     * ediciones y notas dan 404): se sigue. Devuelve el id de verdad y la obra.
     */
    private suspend fun obra(id0: String): Pair<String, JsonObject> {
        var id = id0
        var w = pedirJson(base.newBuilder().addPathSegments("works/$id.json").build()).jsonObject
        repeat(2) {
            val destino = w["location"].texto().removePrefix("/works/")
            if ((w["type"] as? JsonObject)?.get("key").texto() == "/type/redirect" && destino.isNotBlank()) {
                id = destino
                w = pedirJson(base.newBuilder().addPathSegments("works/$id.json").build()).jsonObject
            }
        }
        return id to w
    }

    /** La obra completa: sinopsis, temas, la nota de los lectores y la mejor portada. Lo de la búsqueda llega en [e]. */
    suspend fun detalles(e: Encontrado, idioma: String = "es"): Obra {
        val (id, w) = obra(e.fuente.removePrefix("ol:"))
        val desc = sinFormato(w["description"].let { d -> if (d is JsonObject) d["value"].texto() else d.texto() }
            .substringBefore("\n----").substringBefore("([source]").trim())
        val temas = w.arr("subjects").map { it.texto() }.filter { t -> t.length in 3..30 && !t.contains(":") && RUIDO.none { it in t.lowercase() } }.take(4)
        // La nota de los lectores de Open Library (de 1 a 5); si no se puede traer, la de la búsqueda.
        val notas = runCatching { pedirJson(base.newBuilder().addPathSegments("works/$id/ratings.json").build()).jsonObject["summary"] as? JsonObject }.getOrNull()
        val votos = notas?.get("count").entero() ?: e.votos
        // La portada más linda: de las ediciones en el idioma de la app y de la obra, sin escaneos de la página del título.
        val ediciones = runCatching {
            enIdioma(pedirJson(base.newBuilder().addPathSegments("works/$id/editions.json").addQueryParameter("limit", "200").build())
                .jsonObject.arr("entries").map { it.jsonObject }, idioma, w["title"].texto())
        }.getOrDefault(emptyList())
        val tapa = runCatching { mejorPortada(ediciones.mapNotNull { it.arr("covers").firstOrNull()?.entero() }, w.arr("covers").mapNotNull { it.entero()?.takeIf { n -> n > 0 } }) }.getOrNull()
        return Obra(
            id = "", tipo = Tipos.LIBRO, titulo = e.titulo.ifBlank { w["title"].texto() }, anio = e.anio, creador = e.creador,
            portada = tapa?.let { portada(it, "L") } ?: e.portada.replace("-M.jpg", "-L.jpg"),
            generos = temas, sinopsis = desc, fuente = e.fuente, paginas = e.detalle.removeSuffix(" p.").toIntOrNull() ?: ediciones.firstNotNullOfOrNull { it["number_of_pages"].entero() },
            traida = System.currentTimeMillis(),
            publica = (notas?.get("average").decimal() ?: e.publica)?.takeIf { votos > 0 }, votos = votos,
        )
    }

    /**
     * Las ediciones en [idioma] con portada, la subida más reciente primero, sin guías, estudios ni ediciones
     * bilingües, y con un título parecido al de la obra (hay ediciones mal marcadas de otro idioma).
     */
    fun enIdioma(ediciones: List<JsonObject>, idioma: String, tituloObra: String = ""): List<JsonObject> {
        val mio = "/languages/" + marc(idioma)
        val ruido = listOf("estudio", "guia", "study", "guide", "summary", "resumen", "analisis", "analysis", " / ")
        return ediciones.filter { ed ->
            ed.arr("languages").map { (it as? JsonObject)?.get("key").texto() } == listOf(mio) &&
                (ed.arr("covers").firstOrNull()?.entero() ?: 0) > 0 && ruido.none { it in normal(ed["title"].texto()) } &&
                (tituloObra.isBlank() || parecidos(ed["title"].texto(), tituloObra))
        }.sortedByDescending { it.arr("covers").firstOrNull()?.entero() ?: 0 }
    }

    /** Lo que se sabe de una portada: si es un escaneo de la página del título (de archive.org) y su alto. */
    data class Tapa(val id: Int, val alto: Int, val escaneo: Boolean)

    /**
     * La portada más linda: Open Library guarda de cada una de dónde salió y su tamaño. Las que son escaneos de la
     * página del título (salen de archive.org) son las feas; de las demás, la más grande, primero las de [delIdioma]
     * (ediciones en el idioma de la app) y después las de la obra. Se miran a la vez, hasta 10.
     */
    suspend fun mejorPortada(delIdioma: List<Int>, deLaObra: List<Int>): Int? = kotlinx.coroutines.coroutineScope {
        val todas = (delIdioma.take(6) + deLaObra.take(4)).distinct()
        val tapas = todas.map { id ->
            async { runCatching {
                val j = pedirJson("https://covers.openlibrary.org/b/id/$id.json".toHttpUrl()).jsonObject
                Tapa(id, j["height"].entero() ?: 0, "archive.org/download" in j["source_url"].texto())
            }.getOrNull() }
        }.awaitAll().filterNotNull()
        elegirPortada(tapas, delIdioma.toSet()) ?: todas.firstOrNull()
    }

    /** La regla de [mejorPortada], aparte para probarla: sin escaneos y grande (600 px o más), primero del idioma. */
    fun elegirPortada(tapas: List<Tapa>, delIdioma: Set<Int>): Int? {
        val buenas = tapas.filter { !it.escaneo && it.alto >= 600 }
        return (buenas.filter { it.id in delIdioma }.maxByOrNull { it.alto } ?: buenas.maxByOrNull { it.alto }
            ?: tapas.filter { !it.escaneo }.maxByOrNull { it.alto })?.id
    }

    /** Portadas para elegir (las de todas las ediciones y las de la obra), para "Cambiar portada". */
    private fun portada(id: Int?, t: String) = if (id == null || id <= 0) "" else "https://covers.openlibrary.org/b/id/$id-$t.jpg"

    suspend fun portadasParaElegir(fuente: String): List<String> {
        val (id, w) = obra(fuente.removePrefix("ol:"))
        val obra = w.arr("covers")
        val eds = runCatching { pedirJson(base.newBuilder().addPathSegments("works/$id/editions.json").addQueryParameter("limit", "200").build()).jsonObject.arr("entries") }.getOrNull()
        val ids = (obra.orEmpty().mapNotNull { it.entero() } + eds.orEmpty().mapNotNull { (it as? JsonObject)?.arr("covers")?.firstOrNull()?.entero() })
            .filter { it > 0 }.distinct().take(40)
        return ids.map { portada(it, "L") }
    }

    /** Temas de Open Library que no son géneros (de bibliotecas, idiomas, formatos). */
    private val RUIDO = listOf("language", "translation", "accessible", "daisy", "library", "lending", "reading level", "large type",
        "juvenile", "social conditions", "materials", "in spanish", "long now", "open_syllabus", "staff picks", "protected")

    /** Quita el formato de Markdown que traen algunas descripciones (*cursiva*, **negrita**, [texto](enlace)). */
    fun sinFormato(t: String) = t.replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1").replace(Regex("(\\*{1,2}|_{1,2})([^*_\\n]+)\\1"), "$2")

}

/* =================== ListenBrainz =================== */

/**
 * Artistas parecidos, de ListenBrainz (MetaBrainz, los mismos de MusicBrainz): salen de lo que escucha la gente que
 * usa ListenBrainz (datos CC0). No pide cuenta ni clave.
 */
object ListenBrainz {
    private const val ALGORITMO = "session_based_days_7500_session_300_contribution_5_threshold_10_limit_100_filter_True_skip_30"

    /** Los artistas más escuchados de siempre en ListenBrainz (datos abiertos), para elegir gustos. */
    suspend fun populares(n: Int = 60): List<ArtistaEncontrado> {
        val u = "https://api.listenbrainz.org/1/stats/sitewide/artists".toHttpUrl().newBuilder()
            .addQueryParameter("count", "$n").addQueryParameter("range", "all_time").build()
        val lista = ((pedirJson(u) as? JsonObject)?.get("payload") as? JsonObject)?.arr("artists") ?: return emptyList()
        return lista.mapNotNull { it as? JsonObject }.filter { it["artist_mbid"].texto().matches(Regex("[0-9a-f-]{36}")) }
            .distinctBy { it["artist_mbid"].texto() }.map { a -> ArtistaEncontrado(a["artist_mbid"].texto(), a["artist_name"].texto(), "", null, "", "") }
    }

    /** Los discos más escuchados de la semana en ListenBrainz (datos abiertos), con su carátula del Cover Art Archive. */
    suspend fun discosDeLaSemana(n: Int = 40): List<Encontrado> {
        val u = "https://api.listenbrainz.org/1/stats/sitewide/release-groups".toHttpUrl().newBuilder()
            .addQueryParameter("count", "$n").addQueryParameter("range", "week").build()
        val lista = ((pedirJson(u) as? JsonObject)?.get("payload") as? JsonObject)?.arr("release_groups") ?: return emptyList()
        return lista.mapNotNull { it as? JsonObject }.filter { it["release_group_mbid"].texto().matches(Regex("[0-9a-f-]{36}")) }
            .distinctBy { it["release_group_mbid"].texto() }.map { d ->
                val caratula = d["caa_release_mbid"].texto().takeIf { it.isNotBlank() }?.let { "https://coverartarchive.org/release/$it/front-250" }
                    ?: MusicBrainz.caratula(d["release_group_mbid"].texto(), 250)
                Encontrado(Tipos.ALBUM, d["release_group_name"].texto(), null, d["artist_name"].texto(), caratula, "mb:rg:${d["release_group_mbid"].texto()}")
            }
    }

    /** Los artistas más escuchados de la semana. */
    suspend fun artistasDeLaSemana(n: Int = 30): List<ArtistaEncontrado> {
        val u = "https://api.listenbrainz.org/1/stats/sitewide/artists".toHttpUrl().newBuilder()
            .addQueryParameter("count", "$n").addQueryParameter("range", "week").build()
        val lista = ((pedirJson(u) as? JsonObject)?.get("payload") as? JsonObject)?.arr("artists") ?: return emptyList()
        return lista.mapNotNull { it as? JsonObject }.filter { it["artist_mbid"].texto().matches(Regex("[0-9a-f-]{36}")) }
            .distinctBy { it["artist_mbid"].texto() }.map { a -> ArtistaEncontrado(a["artist_mbid"].texto(), a["artist_name"].texto(), "", null, "", "") }
    }

    suspend fun parecidos(mbid: String): List<ArtistaEncontrado> {
        if (!mbid.matches(Regex("[0-9a-f-]{36}"))) return emptyList()
        val u = "https://labs.api.listenbrainz.org/similar-artists/json".toHttpUrl().newBuilder()
            .addQueryParameter("artist_mbids", mbid).addQueryParameter("algorithm", ALGORITMO).build()
        val r = pedirJson(u)
        val lista = (r as? JsonArray) ?: return emptyList()
        return lista.mapNotNull { it as? JsonObject }.filter { it["artist_mbid"].texto().isNotBlank() }.take(25).map { a ->
            ArtistaEncontrado(a["artist_mbid"].texto(), a["name"].texto(), "", null, a["type"].texto(), a["comment"].texto())
        }
    }
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

    /** "Various Artists": así firma MusicBrainz las recopilaciones; no es un artista. */
    private const val VARIOS = "89ad4ac3-39f7-470e-963a-56509c546377"

    fun caratula(mbid: String, t: Int = 500) = "https://coverartarchive.org/release-group/$mbid/front-$t"

    /** Las carátulas de las ediciones de un álbum (las que tienen), para elegir otra. */
    suspend fun caratulasDeAlbum(rg: String): List<String> =
        pedir("release", "release-group" to rg, "limit" to "100").arr("releases").map { it.jsonObject }
            .filter { (it["cover-art-archive"] as? JsonObject)?.get("front")?.texto() == "true" }
            .map { "https://coverartarchive.org/release/${it["id"].texto()}/front-500" }.distinct().take(40)

    private fun deGrupo(g: JsonObject): Encontrado {
        val artistas = g.arr("artist-credit").joinToString("") { (it.jsonObject["name"].texto()) + it.jsonObject["joinphrase"].texto() }
        val tipos = listOf(g["primary-type"].texto()) + g.arr("secondary-types").map { it.texto() }
        return Encontrado(Tipos.ALBUM, g["title"].texto(), anioDe(g["first-release-date"].texto()), artistas,
            caratula(g["id"].texto(), 250), "mb:rg:" + g["id"].texto(), tipos.filter { it.isNotBlank() }.joinToString(" · "))
    }

    /** Artistas de un género (MusicBrainz los etiqueta: `tag:`), los más etiquetados primero. */
    suspend fun artistasPorGenero(genero: String): List<ArtistaEncontrado> =
        pedir("artist", "query" to "tag:\"${genero.replace("\"", " ")}\"", "limit" to "20").arr("artists").map { it.jsonObject }
            .filter { it["id"].texto() != VARIOS }.map { a ->
            ArtistaEncontrado(a["id"].texto(), a["name"].texto(), a["country"].texto(),
                anioDe((a["life-span"] as? JsonObject)?.get("begin").texto()), a["type"].texto(), a["disambiguation"].texto())
        }

    /** Álbumes de un género. */
    suspend fun albumesPorGenero(genero: String): List<Encontrado> =
        pedir("release-group", "query" to "tag:\"${genero.replace("\"", " ")}\" AND primarytype:album", "limit" to "25")
            .arr("release-groups").map { deGrupo(it.jsonObject) }

    /** Un álbum por su título y su artista (para completar lo que llegó sin fuente): los candidatos más parecidos. */
    suspend fun buscarAlbum(titulo: String, artista: String): List<Encontrado> {
        fun limpio(s: String) = s.replace(Regex("[\"\\\\]"), " ").trim()
        val q = "releasegroup:\"${limpio(titulo)}\"" + if (artista.isNotBlank()) " AND artist:\"${limpio(artista)}\"" else ""
        return pedir("release-group", "query" to q, "limit" to "5").arr("release-groups").map { it.jsonObject }
            .filter { (it["score"].entero() ?: 0) >= 80 }.map { deGrupo(it) }
    }

    /** Ordena por relevancia; entre los igual de relevantes, los álbumes antes que los sencillos y lo conocido (más ediciones) primero. */
    suspend fun buscarAlbumes(texto: String): List<Encontrado> =
        pedir("release-group", "query" to texto, "limit" to "40").arr("release-groups").map { it.jsonObject }
            .sortedWith(compareByDescending<JsonObject> { (it["score"].entero() ?: 0) / 10 }
                .thenBy { when (it["primary-type"].texto()) { "Album" -> 0; "EP" -> 1; "Single" -> 3; else -> 2 } }
                .thenByDescending { it["count"].entero() ?: 0 })
            .take(25).map { deGrupo(it) }

    /** Una canción encontrada (para la canción del perfil): con su álbum y la carátula del álbum. */
    data class CancionEncontrada(val titulo: String, val artista: String, val album: String, val portada: String)

    /** Canciones por título (y artista, si se escribe), con la carátula de su álbum (Cover Art Archive). */
    suspend fun buscarCanciones(texto: String): List<CancionEncontrada> =
        pedir("recording", "query" to texto, "limit" to "25").arr("recordings").map { it.jsonObject }
            .filter { (it["score"].entero() ?: 0) >= 60 }.mapNotNull { r ->
                val artista = r.arr("artist-credit").joinToString("") { c -> c.jsonObject["name"].texto() + c.jsonObject["joinphrase"].texto() }
                val rel = r.arr("releases").map { it.jsonObject }.firstOrNull { (it["release-group"] as? JsonObject)?.get("primary-type").texto() == "Album" }
                    ?: r.arr("releases").firstOrNull()?.jsonObject
                val rg = (rel?.get("release-group") as? JsonObject)?.get("id").texto()
                CancionEncontrada(r["title"].texto(), artista, rel?.get("title").texto(), if (rg.isBlank()) "" else caratula(rg, 250))
            }.distinctBy { (it.titulo + "|" + it.artista).lowercase() }.take(20)

    /** Los artistas parecidos de verdad (MusicBrainz da un puntaje de 0 a 100; los de menos de 70 casi nunca son). */
    suspend fun buscarArtistas(texto: String): List<ArtistaEncontrado> =
        pedir("artist", "query" to texto, "limit" to "20").arr("artists").map { it.jsonObject }.filter { (it["score"].entero() ?: 0) >= 70 }.map { a ->
            ArtistaEncontrado(a["id"].texto(), a["name"].texto(), a["country"].texto(),
                anioDe((a["life-span"] as? JsonObject)?.get("begin").texto()), a["type"].texto(), a["disambiguation"].texto(),
                alias = a.arr("aliases").map { it.jsonObject["name"].texto() }.filter { it.isNotBlank() })
        }

    /** Los álbumes y EP de un artista (su discografía), del más nuevo al más viejo. */
    suspend fun discografia(mbid: String): List<Encontrado> =
        pedir("release-group", "artist" to mbid, "type" to "album|ep", "limit" to "100").arr("release-groups")
            .map { deGrupo(it.jsonObject) }.sortedByDescending { it.anio ?: 0 }

    /** El álbum completo: géneros y la lista de canciones de su primera edición oficial. */
    suspend fun detalles(e: Encontrado): Obra = detallesConArtistas(e).first

    /**
     * Como [detalles], y además todos los artistas que firman el álbum (su "artist-credit": "A & B", "A feat. C"),
     * para mostrarlos en la previa antes de guardar nada. La obra solo guarda el primero ([Obra.artista]).
     */
    suspend fun detallesConArtistas(e: Encontrado): Pair<Obra, List<ArtistaEncontrado>> {
        val mbid = e.fuente.removePrefix("mb:rg:")
        val g = pedir("release-group/$mbid", "inc" to "genres+artist-credits+ratings")
        val firman = g.arr("artist-credit").mapNotNull { c -> (c.jsonObject["artist"] as? JsonObject)?.let { a ->
            ArtistaEncontrado(a["id"].texto(), a["name"].texto(), a["country"].texto(), null, a["type"].texto(), a["disambiguation"].texto())
        } }.filter { it.mbid.isNotBlank() && it.mbid != VARIOS }.distinctBy { it.mbid }
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
        .copy(artista = artista?.let { "mb:artist:" + it["id"].texto() }) to firman
    }
}

/* =================== Wikimedia (fotos de artistas) =================== */

/**
 * Las fotos de los artistas: Wikidata une el artista de MusicBrainz (propiedad P434) con su imagen (P18) en
 * Wikimedia Commons, donde cada foto tiene licencia libre (CC BY, CC BY-SA, dominio público…) y su autor.
 * El crédito se guarda con la foto y se muestra en la ficha del artista.
 */
object Wikimedia {
    private val api = "https://www.wikidata.org/w/api.php".toHttpUrl()

    /**
     * Un escritor por su nombre ("León Tolstói", "Platón"): la persona de Wikidata con ese nombre que tiene id de Open
     * Library (P648), que es el registro principal (Open Library tiene muchos repetidos, sin foto ni obras). Devuelve
     * ese id, o null.
     */
    suspend fun escritorPorNombre(nombre: String, idioma: String): String? {
        val b = api.newBuilder().addQueryParameter("action", "wbsearchentities").addQueryParameter("format", "json")
            .addQueryParameter("language", if (idioma == "en") "en" else "es").addQueryParameter("type", "item")
            .addQueryParameter("limit", "5").addQueryParameter("search", nombre).build()
        val ids = pedirJson(b).jsonObject.arr("search").map { it.jsonObject["id"].texto() }.filter { it.isNotBlank() }
        if (ids.isEmpty()) return null
        val e = pedirJson(api.newBuilder().addQueryParameter("action", "wbgetentities").addQueryParameter("format", "json")
            .addQueryParameter("props", "claims|labels|aliases").addQueryParameter("languages", "es|en")
            .addQueryParameter("ids", ids.joinToString("|")).build()).jsonObject["entities"] as? JsonObject ?: return null
        fun limpio(s: String) = normal(s).replace(Regex("[^a-z0-9]+"), " ").trim()
        val buscado = limpio(nombre)
        for (id in ids) {
            val x = e[id] as? JsonObject ?: continue
            // Que sea esa persona y no otra con nombre parecido ("Alexandre Dumas" para "Alexandre Dumas hijo"): su
            // nombre o uno de sus otros nombres, igual (también con las palabras en otro orden).
            val nombres = (x["labels"] as? JsonObject)?.values?.map { (it as JsonObject)["value"].texto() }.orEmpty() +
                (x["aliases"] as? JsonObject)?.values?.flatMap { l -> (l as JsonArray).map { (it as JsonObject)["value"].texto() } }.orEmpty()
            if (nombres.none { limpio(it) == buscado || limpio(it).split(" ").toSet() == buscado.split(" ").toSet() }) continue
            val claims = x["claims"] as? JsonObject ?: continue
            val ol = claims.arr("P648").firstNotNullOfOrNull { c ->
                ((c.jsonObject["mainsnak"] as? JsonObject)?.get("datavalue") as? JsonObject)?.get("value").texto().takeIf { it.matches(Regex("OL[0-9]+A")) }
            }
            if (ol != null) return ol
        }
        return null
    }

    /**
     * Fotos de una persona para elegir: las suyas en Wikidata (P18) y las de su categoría de Wikimedia Commons
     * (P373), todas libres. [propiedad] como en [fotos].
     */
    suspend fun fotosParaElegir(id: String, propiedad: String): List<String> {
        if (!id.matches(formaDe(propiedad))) return emptyList()
        val q = "SELECT ?img ?cat WHERE { ?a wdt:$propiedad \"$id\" . OPTIONAL { ?a wdt:P18 ?img } OPTIONAL { ?a wdt:P373 ?cat } }"
        val u = sparql.newBuilder().addQueryParameter("query", q).addQueryParameter("format", "json").build()
        val filas = ((pedirJson(u, mapOf("Accept" to "application/sparql-results+json")).jsonObject["results"] as? JsonObject)?.arr("bindings") ?: return emptyList()).map { it.jsonObject }
        val propias = filas.mapNotNull { (it["img"] as? JsonObject)?.get("value").texto().takeIf { s -> s.isNotBlank() } }.map { it.replace("http://", "https://") + "?width=400" }
        val categoria = filas.firstNotNullOfOrNull { (it["cat"] as? JsonObject)?.get("value").texto().takeIf { s -> s.isNotBlank() } }
        val deCategoria = if (categoria == null) emptyList() else runCatching {
            val c = commons.newBuilder().addQueryParameter("action", "query").addQueryParameter("format", "json").addQueryParameter("list", "categorymembers")
                .addQueryParameter("cmtitle", "Category:$categoria").addQueryParameter("cmtype", "file").addQueryParameter("cmlimit", "40").build()
            (pedirJson(c).jsonObject["query"]?.jsonObject?.arr("categorymembers") ?: JsonArray(emptyList())).map { it.jsonObject["title"].texto().removePrefix("File:") }
                .filter { t -> t.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp") && "logo" !in t.lowercase() }
                .map { t -> "https://commons.wikimedia.org/wiki/Special:FilePath/" + java.net.URLEncoder.encode(t, "UTF-8").replace("+", "%20") + "?width=400" }
        }.getOrDefault(emptyList())
        return (propias + deCategoria).distinct()
    }

    /**
     * Los enlaces de algo enlazado a otros servicios, por Wikidata: la página de su fuente, Wikipedia (en el idioma de
     * la app o en inglés), Spotify, Apple Music, Deezer, IMDb, Letterboxd y Goodreads, los que tenga. Escalable: otro
     * servicio es otra línea en [SERVICIOS].
     */
    suspend fun enlaces(fuente: String, idioma: String): List<Enlace> {
        val partes = fuente.split(":")
        val id = partes.last()
        val (prop, propia) = when {
            fuente.startsWith("mb:artist:") -> "P434" to "https://musicbrainz.org/artist/$id"
            fuente.startsWith("mb:rg:") -> "P436" to "https://musicbrainz.org/release-group/$id"
            fuente.startsWith("ol:author:") -> "P648" to "https://openlibrary.org/authors/$id"
            fuente.startsWith("ol:") -> "P648" to "https://openlibrary.org/works/$id"
            fuente.startsWith("tmdb:person:") -> "P4985" to "https://www.themoviedb.org/person/$id"
            fuente.startsWith("tmdb:movie:") -> "P4947" to "https://www.themoviedb.org/movie/$id"
            fuente.startsWith("tmdb:tv:") -> "P4983" to "https://www.themoviedb.org/tv/$id"
            else -> return emptyList()
        }
        val nombreFuente = when { fuente.startsWith("mb:") -> "MusicBrainz"; fuente.startsWith("ol:") -> "Open Library"; else -> "TMDB" }
        val r = mutableListOf(Enlace(nombreFuente, propia))
        if (!id.matches(Regex("[A-Za-z0-9-]+"))) return r
        val mio = if (idioma == "en") "en" else "es"
        val opcionales = SERVICIOS.mapIndexed { i, s -> "OPTIONAL { ?a wdt:${s.second} ?v$i }" }.joinToString(" ")
        val q = "SELECT * WHERE { ?a wdt:$prop \"$id\" . $opcionales " +
            "OPTIONAL { ?w1 schema:about ?a ; schema:isPartOf <https://$mio.wikipedia.org/> } " +
            "OPTIONAL { ?w2 schema:about ?a ; schema:isPartOf <https://en.wikipedia.org/> } } LIMIT 1"
        val u = sparql.newBuilder().addQueryParameter("query", q).addQueryParameter("format", "json").build()
        val b = ((pedirJson(u, mapOf("Accept" to "application/sparql-results+json")).jsonObject["results"] as? JsonObject)
            ?.arr("bindings")?.firstOrNull() as? JsonObject) ?: return r
        fun v(k: String) = (b[k] as? JsonObject)?.get("value").texto()
        (v("w1").ifBlank { v("w2") }).takeIf { it.isNotBlank() }?.let { r += Enlace("Wikipedia", it) }
        SERVICIOS.forEachIndexed { i, (nombre, _, url) -> v("v$i").takeIf { it.isNotBlank() }?.let { x -> r += Enlace(nombre, url(x)) } }
        v("a").takeIf { it.isNotBlank() }?.let { r += Enlace("Wikidata", it.replace("http://", "https://")) }
        return r.distinctBy { it.url }.distinctBy { it.nombre }
    }

    /** Servicio, propiedad de Wikidata y cómo se arma la dirección con el id. */
    private val SERVICIOS: List<Triple<String, String, (String) -> String>> = listOf(
        Triple("Spotify", "P1902", { x: String -> "https://open.spotify.com/artist/$x" }),
        Triple("Spotify", "P2205", { x: String -> "https://open.spotify.com/album/$x" }),
        Triple("Apple Music", "P2850", { x: String -> "https://music.apple.com/artist/$x" }),
        Triple("Apple Music", "P2281", { x: String -> "https://music.apple.com/album/$x" }),
        Triple("Deezer", "P2722", { x: String -> "https://www.deezer.com/artist/$x" }),
        Triple("Deezer", "P2723", { x: String -> "https://www.deezer.com/album/$x" }),
        Triple("IMDb", "P345", { x: String -> if (x.startsWith("nm")) "https://www.imdb.com/name/$x" else "https://www.imdb.com/title/$x" }),
        Triple("Letterboxd", "P6127", { x: String -> "https://letterboxd.com/film/$x/" }),
        Triple("Goodreads", "P8383", { x: String -> "https://www.goodreads.com/work/show/$x" }),
        Triple("Goodreads", "P2963", { x: String -> "https://www.goodreads.com/author/show/$x" }),
    )

    /** Cómo es un id según la propiedad de Wikidata: P434 MusicBrainz, P648 Open Library, P4985 persona de TMDB. */
    private fun formaDe(propiedad: String) = when (propiedad) { "P648" -> Regex("OL[0-9]+A"); "P4985" -> Regex("[0-9]+"); else -> Regex("[0-9a-f-]{36}") }

    private val sparql = "https://query.wikidata.org/sparql".toHttpUrl()
    private val commons = "https://commons.wikimedia.org/w/api.php".toHttpUrl()

    /**
     * Lo que alguien compartió desde una plataforma, por su id en ella: Netflix (P1874) o un álbum de Spotify (P2205).
     * Wikidata lo une con su id de TMDB o de MusicBrainz; devuelve la obra para abrirla, o null si no la conoce.
     */
    suspend fun deLaPlataforma(propiedad: String, id: String, idioma: String): Encontrado? {
        if (propiedad !in setOf("P1874", "P2205") || !id.matches(Regex("[A-Za-z0-9]{4,40}"))) return null
        val q = "SELECT ?l ?le ?pm ?pt ?rg WHERE { ?i wdt:$propiedad \"$id\" . OPTIONAL { ?i rdfs:label ?l FILTER(lang(?l) = \"$idioma\") } " +
            "OPTIONAL { ?i rdfs:label ?le FILTER(lang(?le) = \"en\") } OPTIONAL { ?i wdt:P4947 ?pm } OPTIONAL { ?i wdt:P4983 ?pt } OPTIONAL { ?i wdt:P436 ?rg } } LIMIT 1"
        val u = sparql.newBuilder().addQueryParameter("query", q).addQueryParameter("format", "json").build()
        val b = ((pedirJson(u, mapOf("Accept" to "application/sparql-results+json")).jsonObject["results"] as? JsonObject)?.arr("bindings")?.firstOrNull() as? JsonObject) ?: return null
        fun v(k: String) = (b[k] as? JsonObject)?.get("value").texto()
        val titulo = v("l").ifBlank { v("le") }.ifBlank { return null }
        return when {
            v("pm").isNotBlank() -> Encontrado(Tipos.PELICULA, titulo, null, "", "", "tmdb:movie:${v("pm")}")
            v("pt").isNotBlank() -> Encontrado(Tipos.SERIE, titulo, null, "", "", "tmdb:tv:${v("pt")}")
            v("rg").isNotBlank() -> Encontrado(Tipos.ALBUM, titulo, null, "", MusicBrainz.caratula(v("rg"), 500), "mb:rg:${v("rg")}")
            else -> Encontrado(if (propiedad == "P2205") Tipos.ALBUM else Tipos.PELICULA, titulo, null, "", "", "")
        }
    }

    /**
     * id → dirección de la foto (400 px de ancho). Una sola consulta para varias personas. [propiedad] es cómo
     * Wikidata guarda el id: P434 (MusicBrainz, artistas) o P648 (Open Library, escritores).
     */
    suspend fun fotos(ids0: List<String>, propiedad: String = "P434"): Map<String, String> {
        val forma = formaDe(propiedad)
        val ids = ids0.filter { it.matches(forma) }.distinct().take(50)
        if (ids.isEmpty()) return emptyMap()
        val q = "SELECT ?mbid ?img WHERE { VALUES ?mbid { ${ids.joinToString(" ") { "\"$it\"" }} } ?a wdt:$propiedad ?mbid ; wdt:P18 ?img . }"
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

    /**
     * Si Wikidata no tiene foto (P18): la imagen principal de su artículo de Wikipedia (en el idioma de la app o en
     * inglés), solo si es libre (`pilicense=free`) y no es un logo. id → dirección, como [fotos].
     */
    suspend fun fotosDeWikipedia(mbids: List<String>, idioma: String, propiedad: String = "P434"): Map<String, String> {
        val ids = mbids.filter { it.matches(formaDe(propiedad)) }.distinct().take(50)
        if (ids.isEmpty()) return emptyMap()
        val r = mutableMapOf<String, String>()
        for (lengua in listOf(if (idioma == "en") "en" else "es", "en").distinct()) {
            val faltan = ids.filter { it !in r }
            if (faltan.isEmpty()) break
            val q = "SELECT ?mbid ?art WHERE { VALUES ?mbid { ${faltan.joinToString(" ") { "\"$it\"" }} } ?a wdt:$propiedad ?mbid . " +
                "?art schema:about ?a ; schema:isPartOf <https://$lengua.wikipedia.org/> . }"
            val u = sparql.newBuilder().addQueryParameter("query", q).addQueryParameter("format", "json").build()
            val filas = ((pedirJson(u, mapOf("Accept" to "application/sparql-results+json")).jsonObject["results"] as? JsonObject)?.arr("bindings") ?: continue)
                .map { it.jsonObject }
            val articulo = filas.associate { (it["mbid"] as JsonObject)["value"].texto() to java.net.URLDecoder.decode((it["art"] as JsonObject)["value"].texto().substringAfter("/wiki/"), "UTF-8") }
            // Hasta 50 títulos por consulta a la API de Wikipedia.
            articulo.entries.chunked(50).forEach { tanda ->
                val api = "https://$lengua.wikipedia.org/w/api.php".toHttpUrl().newBuilder().addQueryParameter("action", "query").addQueryParameter("format", "json")
                    .addQueryParameter("prop", "pageimages").addQueryParameter("piprop", "name").addQueryParameter("pilicense", "free")
                    .addQueryParameter("redirects", "1").addQueryParameter("titles", tanda.joinToString("|") { it.value.replace('_', ' ') }).build()
                val q2 = pedirJson(api).jsonObject["query"]?.jsonObject ?: return@forEach
                // Wikipedia normaliza los títulos ("_" → " ") y sigue las redirecciones: se vuelve al título pedido.
                val vuelta = mutableMapOf<String, String>()
                (q2.arr("normalized") + q2.arr("redirects")).map { it.jsonObject }.forEach { vuelta[it["to"].texto()] = vuelta[it["from"].texto()] ?: it["from"].texto() }
                (q2["pages"] as? JsonObject)?.values?.map { it.jsonObject }?.forEach { p ->
                    val img = p["pageimage"].texto()
                    if (img.isBlank() || "logo" in img.lowercase()) return@forEach
                    val titulo = p["title"].texto(); val pedido = vuelta[titulo] ?: titulo
                    val mbid = tanda.firstOrNull { it.value.replace('_', ' ') == pedido || it.value.replace('_', ' ') == titulo }?.key ?: return@forEach
                    r[mbid] = "https://commons.wikimedia.org/wiki/Special:FilePath/" + java.net.URLEncoder.encode(img, "UTF-8").replace("+", "%20") + "?width=400"
                }
            }
        }
        return r
    }

    /**
     * La biografía breve de un artista: la introducción de su artículo de Wikipedia en el idioma de la app (o en
     * inglés si no hay), encontrado por Wikidata (P434, el id de MusicBrainz). Devuelve el texto y la dirección del
     * artículo; el texto es CC BY-SA 4.0 y se muestra con su crédito.
     */
    suspend fun bio(mbid: String, idioma: String, propiedad: String = "P434"): Pair<String, String>? {
        if (!mbid.matches(formaDe(propiedad))) return null
        for (lengua in listOf(if (idioma == "en") "en" else "es", "en").distinct()) {
            val q = "SELECT ?art WHERE { ?a wdt:$propiedad \"$mbid\" . ?art schema:about ?a ; schema:isPartOf <https://$lengua.wikipedia.org/> . }"
            val u = sparql.newBuilder().addQueryParameter("query", q).addQueryParameter("format", "json").build()
            val art = ((pedirJson(u, mapOf("Accept" to "application/sparql-results+json")).jsonObject["results"] as? JsonObject)
                ?.arr("bindings")?.firstOrNull() as? JsonObject)?.let { (it["art"] as? JsonObject)?.get("value").texto() } ?: continue
            val titulo = art.substringAfter("/wiki/")
            val resumen = pedirJson("https://$lengua.wikipedia.org/api/rest_v1/page/summary/$titulo".toHttpUrl()).jsonObject
            val texto = resumen["extract"].texto().trim()
            if (texto.isNotEmpty()) return texto to art
        }
        return null
    }

    /** El crédito de una foto de Commons: "Autor · Licencia · Wikimedia Commons" (sin HTML). */
    suspend fun credito(foto: String): String {
        val archivo = java.net.URLDecoder.decode(foto.substringAfter("Special:FilePath/").substringBefore("?"), "UTF-8")
        val u = commons.newBuilder().addQueryParameter("action", "query").addQueryParameter("format", "json")
            .addQueryParameter("prop", "imageinfo").addQueryParameter("iiprop", "extmetadata").addQueryParameter("titles", "File:$archivo").build()
        val paginas = pedirJson(u).jsonObject["query"]?.jsonObject?.get("pages") as? JsonObject ?: return ""
        val meta = paginas.values.firstOrNull()?.jsonObject?.arr("imageinfo")?.firstOrNull()?.jsonObject?.get("extmetadata") as? JsonObject ?: return ""
        // Commons repite a veces el texto en una etiqueta escondida (`<span style="display: none;">Unknown author</span>`):
        // lo escondido se quita antes que las etiquetas, para que no salga "Unknown authorUnknown author".
        fun v(k: String) = (meta[k] as? JsonObject)?.get("value").texto()
            .replace(Regex("<(\\w+)[^>]*display:\\s*none[^>]*>.*?</\\1>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<[^>]+>"), "").replace("&amp;", "&").replace(Regex("\\s+"), " ").trim()
        return listOf(v("Artist"), v("LicenseShortName"), "Wikimedia Commons").filter { it.isNotBlank() }.joinToString(" · ")
    }
}

/**
 * TVmaze (datos CC BY-SA 4.0, sin clave): lo que se estrena hoy en las plataformas, para "lo del momento" de las series
 * cuando no hay clave de TMDB. Se le da el crédito con un enlace en la pantalla.
 */
object TvMaze {
    const val SITIO = "https://www.tvmaze.com"

    suspend fun estrenosDeHoy(): List<Encontrado> {
        val u = "https://api.tvmaze.com/schedule/web".toHttpUrl().newBuilder().addQueryParameter("date", java.time.LocalDate.now().toString()).build()
        return (pedirJson(u) as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.get("_embedded") as? JsonObject }
            .mapNotNull { it["show"] as? JsonObject }.distinctBy { it["id"].entero() }
            // Las más seguidas primero ("weight" es lo popular que es en TVmaze), en los idiomas que más se ven aquí.
            .sortedByDescending { it["weight"].entero() ?: 0 }
            .mapNotNull { s ->
                val foto = (s["image"] as? JsonObject)?.get("medium").texto().replace("http://", "https://")
                if (foto.isBlank() || s["language"].texto() !in setOf("English", "Spanish", "Japanese", "Korean")) return@mapNotNull null
                // Solo series nuevas (de este año o el anterior), no las de toda la vida que estrenan capítulo.
                if ((anioDe(s["premiered"].texto()) ?: 0) < java.time.LocalDate.now().year - 1) return@mapNotNull null
                Encontrado(Tipos.SERIE, s["name"].texto(), anioDe(s["premiered"].texto()), "", foto, "tvmaze:${s["id"].entero()}")
            }.take(24)
    }
}

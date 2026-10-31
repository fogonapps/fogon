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

/**
 * Una persona encontrada afuera: un artista de MusicBrainz, un escritor de Open Library o un director de TMDB.
 * [tipo] es el de MusicBrainz (Person, Group…) o [Personas.AUTOR] / [Personas.DIRECTOR]; [foto] la pone después
 * [Wikimedia] (o viene de TMDB). [fuente]: "mb:artist:<mbid>", "ol:author:OL…A", "tmdb:person:<id>".
 */
data class ArtistaEncontrado(
    val mbid: String, val nombre: String, val pais: String, val desde: Int?, val tipo: String, val detalle: String, val foto: String = "",
    val fuente: String = "mb:artist:$mbid",
)

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

    /** Directores (los que TMDB conoce por dirigir), con su foto. */
    suspend fun buscarDirectores(texto: String, idioma: String): List<ArtistaEncontrado> =
        pedir("search/person", "query" to texto, "language" to if (idioma == "en") "en-US" else "es-ES").arr("results").map { it.jsonObject }
            .filter { it["known_for_department"].texto() == "Directing" }.take(8).map { p ->
                ArtistaEncontrado("", p["name"].texto(), "", null, Personas.DIRECTOR,
                    p.arr("known_for").take(2).joinToString(", ") { k -> k.jsonObject["title"].texto().ifBlank { k.jsonObject["name"].texto() } },
                    imagen(p["profile_path"].texto(), "w185"), "tmdb:person:${p["id"].entero()}")
            }

    /** Lo que dirigió una persona (películas, series y animes), de lo más nuevo a lo más viejo. */
    suspend fun filmografia(id: String, idioma: String): List<Encontrado> {
        val c = pedir("person/$id/combined_credits", "language" to if (idioma == "en") "en-US" else "es-ES")
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
    suspend fun posteres(fuente: String, idioma: String): List<String> {
        val (_, clase, id) = fuente.split(":")
        return pedir("$clase/$id/images", "include_image_language" to (if (idioma == "en") "en,null" else "es,en,null")).arr("posters")
            .mapNotNull { (it as? JsonObject)?.get("file_path").texto().takeIf { r -> r.isNotBlank() } }.take(40).map { imagen(it, "w500") }
    }

    /** Los episodios de una temporada (número y nombre), para elegir los favoritos. */
    suspend fun episodios(fuente: String, temporada: Int, idioma: String): List<Pair<Int, String>> {
        val id = fuente.split(":").last()
        return pedir("tv/$id/season/$temporada", "language" to if (idioma == "en") "en-US" else "es-ES").arr("episodes").map { it.jsonObject }
            .mapNotNull { e -> e["episode_number"].entero()?.let { it to e["name"].texto() } }
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

    /** El código de idioma de las ediciones de Open Library (MARC): "spa", "eng". */
    private fun marc(idioma: String) = if (idioma == "en") "eng" else "spa"

    /** Escritores: los que tienen obras de verdad (no los registros sueltos de bibliotecas), los más conocidos primero. */
    suspend fun buscarAutores(texto: String): List<ArtistaEncontrado> {
        val u = base.newBuilder().addPathSegments("search/authors.json").addQueryParameter("q", texto).addQueryParameter("limit", "20").build()
        return pedirJson(u).jsonObject.arr("docs").map { it.jsonObject }
            .filter { (it["work_count"].entero() ?: 0) >= 3 }
            .sortedByDescending { it["work_count"].entero() ?: 0 }
            // Open Library tiene al mismo autor repetido (registros de bibliotecas): queda el de más obras.
            .distinctBy { normal(it["name"].texto()).trim() }.take(6).map { a ->
                ArtistaEncontrado("", a["name"].texto(), "", anioDe(a["birth_date"].texto().takeLast(4)), Personas.AUTOR,
                    a["top_work"].texto(), fuente = "ol:author:" + a["key"].texto())
            }
    }

    /** Las obras de un escritor, las más editadas primero, en el idioma de la app (como en [buscar]). */
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

    /**
     * id → dirección de la foto (400 px de ancho). Una sola consulta para varias personas. [propiedad] es cómo
     * Wikidata guarda el id: P434 (MusicBrainz, artistas) o P648 (Open Library, escritores).
     */
    suspend fun fotos(ids0: List<String>, propiedad: String = "P434"): Map<String, String> {
        val forma = if (propiedad == "P648") Regex("OL[0-9]+A") else Regex("[0-9a-f-]{36}")
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
     * La biografía breve de un artista: la introducción de su artículo de Wikipedia en el idioma de la app (o en
     * inglés si no hay), encontrado por Wikidata (P434, el id de MusicBrainz). Devuelve el texto y la dirección del
     * artículo; el texto es CC BY-SA 4.0 y se muestra con su crédito.
     */
    suspend fun bio(mbid: String, idioma: String): Pair<String, String>? {
        if (!mbid.matches(Regex("[0-9a-f-]{36}"))) return null
        for (lengua in listOf(if (idioma == "en") "en" else "es", "en").distinct()) {
            val q = "SELECT ?art WHERE { ?a wdt:P434 \"$mbid\" . ?art schema:about ?a ; schema:isPartOf <https://$lengua.wikipedia.org/> . }"
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
        fun v(k: String) = (meta[k] as? JsonObject)?.get("value").texto().replace(Regex("<[^>]+>"), "").replace("&amp;", "&").trim()
        return listOf(v("Artist"), v("LicenseShortName"), "Wikimedia Commons").filter { it.isNotBlank() }.joinToString(" · ")
    }
}

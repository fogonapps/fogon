package io.github.fogonapps.cajon.datos

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * Videojuegos de IGDB, por el servidor de Fogón (`/v1/igdb/…`, que pone la clave): buscar por nombre, la ficha de uno
 * y varios por id o por nombre corto. Las portadas, fondos y capturas llegan directo de images.igdb.com (sin clave).
 * Lo que IGDB no da se completa con Wikidata, que une cada juego con su ficha de IGDB (P5794): el sitio oficial y
 * Steam de respaldo, y el resumen de Wikipedia en español. Si el servidor no tiene IGDB ([SinIgdb]) o no responde,
 * quien llama sigue con [WikidataJuegos].
 */
object Igdb {
    private val base get() = "${ClienteSocial.SERVIDOR}/v1/igdb".toHttpUrl()

    /** La página de IGDB, para el crédito. */
    const val SITIO = "https://www.igdb.com"

    fun esFuente(fuente: String) = Regex("igdb:[0-9]{1,10}").matches(fuente)
    fun idDe(fuente: String): Long? = if (esFuente(fuente)) fuente.removePrefix("igdb:").toLongOrNull() else null

    /** Una imagen de IGDB por su id y tamaño ("cover_big", "screenshot_big", "1080p"); vacío si el id no es válido. */
    fun imagen(id: String, tamano: String) =
        if (!id.matches(Regex("[A-Za-z0-9_-]{1,64}"))) "" else "https://images.igdb.com/igdb/image/upload/t_$tamano/$id.jpg"

    /**
     * Los géneros y temas de IGDB que son uno de los de [WikidataJuegos.GENEROS] (por su nombre en IGDB, en inglés): se
     * guardan con ese elemento y se muestran con el mismo nombre corto, en el idioma de la app, y se pueden buscar.
     */
    internal val COMO_GENERO: Map<String, String> = mapOf(
        "Action" to 0, "Adventure" to 1, "Role-playing (RPG)" to 3, "Strategy" to 5, "Real Time Strategy (RTS)" to 6,
        "Turn-based strategy (TBS)" to 7, "Simulator" to 8, "Platform" to 9, "Puzzle" to 12, "Racing" to 13, "Sport" to 14,
        "Fighting" to 15, "Shooter" to 16, "Horror" to 19, "Visual Novel" to 20, "Music" to 21, "Sandbox" to 23,
        "Survival" to 24, "Stealth" to 25, "Comedy" to 28, "Hack and slash/Beat 'em up" to 29,
    ).mapValues { WikidataJuegos.GENEROS[it.value] }

    /** Los géneros de IGDB que no están en Wikidata, en el orden de [GenerosJuego.CLAVES_IGDB] y del `string-array` `generos_igdb`. */
    internal val OTROS_GENEROS = listOf("Indie", "Arcade", "Tactical", "Point-and-click", "Card & Board Game", "MOBA", "Quiz/Trivia", "Pinball")

    /* ----- Pedidos al servidor ----- */

    suspend fun buscar(texto: String): List<Encontrado> {
        if (texto.isBlank()) return emptyList()
        val u = base.newBuilder().addPathSegment("buscar").addQueryParameter("q", texto.trim().take(80)).build()
        return ordenar(lista(pedirJson(u)), texto).take(MAX_RESULTADOS).mapNotNull { encontrado(it) }
    }

    private const val MAX_RESULTADOS = 20

    /**
     * El orden de una búsqueda, para que salga primero lo que más se busca: el parecido del nombre (igual a lo buscado,
     * o que empieza por eso), la popularidad (cuántos votaron y, si todavía no salió, cuántos lo esperan) y lo reciente;
     * el orden de IGDB pesa poco. Así «FIFA» trae primero los de los últimos años y no el de 1997, y «Zelda» los más
     * jugados. De dos con el mismo nombre (IGDB tiene entradas repetidas, «Hollow Knight Silksong» y «Hollow Knight:
     * Silksong»), queda el más votado (si el otro no tiene votos). [anio]: el de hoy.
     */
    internal fun ordenar(juegos: List<JsonObject>, texto: String = "", anio: Int = java.time.Year.now(java.time.ZoneOffset.UTC).value): List<JsonObject> {
        val buscado = Logica.tituloLimpio(texto)
        fun entero(j: JsonObject, k: String) = (j[k] as? JsonPrimitive)?.intOrNull ?: 0
        fun votos(j: JsonObject) = maxOf(entero(j, "total_rating_count"), entero(j, "rating_count") + entero(j, "aggregated_rating_count"))
        fun puntaje(i: Int, j: JsonObject): Double {
            val nombre = Logica.tituloLimpio(j["name"].texto())
            val parecido = when {
                buscado.isEmpty() -> 0.0
                nombre == buscado -> 2.0
                nombre.startsWith("$buscado ") -> 0.3
                else -> 0.0
            }
            // Sin fecha, como si fuera muy viejo: suelen ser entradas incompletas.
            val edad = anio(j)?.let { (anio - it).coerceIn(0, 30) } ?: 30
            return parecido + 0.8 * kotlin.math.ln(1.0 + votos(j) + entero(j, "hypes")) - 0.1 * edad - 0.03 * i
        }
        val vistos = mutableSetOf<String>()
        val unicos = juegos.filter { j -> j["id"].largo().let { it == null || vistos.add("id:$it") } }
        vistos.clear()
        // Dos con el mismo nombre y votos los dos (como «Doom» de 1993 y de 2016) son juegos distintos: quedan ambos.
        return unicos.withIndex().sortedByDescending { (i, j) -> puntaje(i, j) }.map { it.value }
            .filter { j -> vistos.add(Logica.tituloLimpio(j["name"].texto())) || votos(j) > 0 }
    }

    /** Las fichas ya pedidas en esta sesión (búsqueda, previa, ficha y enlaces piden la misma). */
    private val fichas = ConcurrentHashMap<Long, Pair<Long, JsonObject>>()
    private const val MEDIA_HORA = 30 * 60_000L
    private const val MAX_FICHAS = 300

    suspend fun juego(id: Long): JsonObject {
        fichas[id]?.let { (cuando, j) -> if (System.currentTimeMillis() - cuando < MEDIA_HORA) return j }
        val u = base.newBuilder().addPathSegment("juegos").addPathSegment("$id").build()
        val j = lista(pedirJson(u)).firstOrNull { it["id"].largo() == id } ?: throw WikidataJuegos.SinDatos("igdb:$id")
        recordar(j)
        return j
    }

    private val porSlug = ConcurrentHashMap<String, Long>()

    /** La fuente ("igdb:…") de una ficha; null si no trae id. */
    fun fuenteDe(j: JsonObject): String? = j["id"].largo()?.let { "igdb:$it" }

    /**
     * Guarda una ficha pedida. Con más de [MAX_FICHAS] se van primero las vencidas y después las más viejas (no todas:
     * al pasar muchos juegos, la tanda que se está usando no se pierde y no hay que pedirla otra vez).
     */
    private fun recordar(j: JsonObject) {
        val id = j["id"].largo() ?: return
        if (fichas.size >= MAX_FICHAS) {
            val ya = System.currentTimeMillis()
            fichas.entries.removeIf { ya - it.value.first >= MEDIA_HORA }
            if (fichas.size >= MAX_FICHAS) fichas.entries.sortedBy { it.value.first }.take(fichas.size - MAX_FICHAS + 50).forEach { fichas.remove(it.key) }
            porSlug.entries.removeIf { !fichas.containsKey(it.value) }
        }
        fichas[id] = System.currentTimeMillis() to j
        j["slug"].texto().takeIf { it.isNotBlank() }?.let { porSlug[it] = id }
    }

    /** La ficha ya pedida de una referencia ("id:…" o "slug:…"), si sigue fresca. */
    private fun recordada(ref: String): JsonObject? {
        val id = if (ref.startsWith("id:")) ref.removePrefix("id:").toLongOrNull() else porSlug[ref.removePrefix("slug:")]
        return id?.let { fichas[it] }?.takeIf { System.currentTimeMillis() - it.first < MEDIA_HORA }?.second
    }

    /** Varias fichas por sus referencias de Wikidata ("id:…" o "slug:…"), de a 50: referencia → ficha. */
    suspend fun porReferencias(refs: List<String>): Map<String, JsonObject> {
        val r = mutableMapOf<String, JsonObject>()
        refs.distinct().forEach { ref -> recordada(ref)?.let { r[ref] = it } }
        refs.distinct().filter { (it.startsWith("id:") || it.startsWith("slug:")) && it !in r }.chunked(50).forEach { tanda ->
            val ids = tanda.filter { it.startsWith("id:") }.map { it.removePrefix("id:") }
            val slugs = tanda.filter { it.startsWith("slug:") }.map { it.removePrefix("slug:") }
            val u = base.newBuilder().addPathSegment("juegos").apply {
                if (ids.isNotEmpty()) addQueryParameter("ids", ids.joinToString(","))
                if (slugs.isNotEmpty()) addQueryParameter("slugs", slugs.joinToString(","))
            }.build()
            lista(pedirJson(u)).forEach { j ->
                recordar(j)
                j["id"].largo()?.let { r["id:$it"] = j }
                j["slug"].texto().takeIf { it.isNotBlank() }?.let { r["slug:$it"] = j }
            }
        }
        return refs.mapNotNull { ref -> r[ref]?.let { ref to it } }.toMap()
    }

    /**
     * Los juegos de Wikidata que tienen ficha en IGDB, con la de IGDB (portada, nota, fuente "igdb:…"); los demás quedan
     * como vinieron. Si IGDB no responde, la lista queda igual.
     */
    suspend fun desdeWikidata(lista: List<Encontrado>): List<Encontrado> {
        val refs = lista.map { it.igdb }.filter { it.isNotEmpty() }
        if (refs.isEmpty()) return lista
        val fichas = try { porReferencias(refs) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { return lista }
        return lista.map { e -> fichas[e.igdb]?.let { encontrado(it) } ?: e }.distinctBy { it.fuente }
    }

    /** Lo que sabe Wikidata de un juego de IGDB (enlaces de respaldo y Wikipedia); null si no lo conoce o falla. */
    private val extras = ConcurrentHashMap<String, WikidataJuegos.Extra>()

    private suspend fun extra(j: JsonObject, qid: String? = null): WikidataJuegos.Extra? {
        val slug = j["slug"].texto()
        val clave = qid ?: "slug:$slug"
        extras[clave]?.let { return it }
        return try {
            val q = qid ?: if (slug.isBlank()) return null else WikidataJuegos.qidsDeIgdb(listOf("slug:$slug"))["slug:$slug"] ?: return null
            WikidataJuegos.extra(q).also { extras[clave] = it }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
    }

    /**
     * Todo lo de un juego para guardarlo. El resumen: el de IGDB (en inglés) en inglés; en español, el de Wikipedia en
     * español si lo hay, con su crédito. Si IGDB no tiene resumen, el de Wikipedia en inglés. [qid]: su elemento de
     * Wikidata, si ya se sabe (un juego que se guardó desde Wikidata).
     */
    suspend fun detalles(fuente: String, idioma: String, qid: String? = null): Obra {
        val id = idDe(fuente) ?: throw IllegalArgumentException("No es un juego de IGDB: $fuente")
        val j = juego(id)
        return conResumen(obra(j), j, idioma, qid)
    }

    /** [o] con el resumen de Wikipedia si hace falta (ver [detalles]); si Wikipedia falla, queda el de IGDB. */
    internal suspend fun conResumen(o: Obra, j: JsonObject, idioma: String, qid: String? = null): Obra {
        val propio = o.sinopsis
        if (idioma == "en" && propio.isNotBlank()) return o
        val x = extra(j, qid) ?: return o
        val articulo = (if (idioma == "en") listOf("en") else if (propio.isBlank()) listOf("es", "en") else listOf("es"))
            .firstNotNullOfOrNull { x.wikipedia[it] } ?: return o
        val texto = try { Wikimedia.resumen(articulo) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { "" }
        return if (texto.isBlank()) o else o.copy(sinopsis = texto, sinopsisFuente = articulo)
    }

    /** Las capturas y los artes de un juego para la galería: miniatura y grande. */
    suspend fun galeria(fuente: String): List<Pair<String, String>> = idDe(fuente)?.let { galeriaDe(juego(it)) }.orEmpty()

    /**
     * Los enlaces de un juego: su sitio oficial, tiendas (Steam, GOG, Epic, itch.io…), wikis y Wikipedia de IGDB; lo que
     * falte, de Wikidata (sitio oficial, Steam y Wikipedia); al final, su página en IGDB. El segundo valor es false si algo
     * falló (se puede volver a pedir).
     */
    suspend fun enlaces(fuente: String, idioma: String): Pair<List<Enlace>, Boolean> {
        val id = idDe(fuente) ?: return emptyList<Enlace>() to true
        val j = juego(id)
        val x = extra(j)
        return juntarEnlaces(enlacesDe(j), x, idioma) to true
    }

    /* ----- Lectura (sin red: se prueban con respuestas inventadas) ----- */

    private fun lista(e: JsonElement): List<JsonObject> = (e as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

    /** Lo que muestra la búsqueda: título, año, estudio, plataformas, portada y la nota del público. */
    internal fun encontrado(j: JsonObject): Encontrado? {
        val id = j["id"].largo() ?: return null
        val titulo = j["name"].texto().trim().ifBlank { return null }
        val (nota, votos) = nota(j, "rating")
        return Encontrado(
            tipo = Tipos.JUEGO, titulo = titulo, anio = anio(j), creador = estudios(j).firstOrNull().orEmpty(),
            portada = portada(j, Imagenes.Lugar.LISTA), fuente = "igdb:$id", detalle = plataformas(j).joinToString(" · "),
            publica = nota, votos = votos,
        )
    }

    /** La ficha de IGDB como obra del cajón (sin el resumen de Wikipedia, ver [conResumen]). */
    internal fun obra(j: JsonObject): Obra {
        val id = j["id"].largo() ?: throw IllegalArgumentException("Ficha de IGDB sin id")
        val (publica, votos) = nota(j, "rating")
        val (criticos, votosCriticos) = nota(j, "aggregated_rating")
        val fondo = (j.lista("artworks") + j.lista("screenshots")).firstNotNullOfOrNull { it["image_id"].texto().takeIf(String::isNotBlank) }
        return Obra(
            id = "", tipo = Tipos.JUEGO, titulo = j["name"].texto().trim(), anio = anio(j),
            creador = estudios(j).take(3).joinToString(", "), generos = generos(j),
            sinopsis = j["summary"].texto().trim(), plataformas = plataformas(j),
            portada = portada(j, Imagenes.Lugar.FICHA), fondo = fondo?.let { imagen(it, "screenshot_big") }.orEmpty(),
            fuente = "igdb:$id", traida = System.currentTimeMillis(),
            publica = publica, votos = votos, criticos = criticos, votosCriticos = votosCriticos,
        )
    }

    private fun portada(j: JsonObject, lugar: Imagenes.Lugar): String =
        (j["cover"] as? JsonObject)?.get("image_id").texto().takeIf { it.isNotBlank() }
            ?.let { imagen(it, "cover_big") }?.takeIf { it.isNotBlank() }?.let { Imagenes.url(it, lugar) }.orEmpty()

    /** El año de su primera salida (segundos desde 1970, en UTC). */
    private fun anio(j: JsonObject): Int? = j["first_release_date"].largo()?.takeIf { it > 0 }
        ?.let { java.time.Instant.ofEpochSecond(it).atZone(java.time.ZoneOffset.UTC).year }

    /** Una nota de IGDB (de 0 a 100) pasada a 0-10, con cuántos la dieron; sin votos, nada. */
    private fun nota(j: JsonObject, campo: String): Pair<Double?, Int> {
        val n = (j["${campo}_count"] as? JsonPrimitive)?.intOrNull ?: 0
        val v = (j[campo] as? JsonPrimitive)?.doubleOrNull?.takeIf { n > 0 && it > 0 } ?: return null to 0
        return Math.round(v.coerceAtMost(100.0)) / 10.0 to n
    }

    /** Los estudios que lo hicieron; si IGDB no marca ninguno como desarrollador, los que lo publicaron. */
    internal fun estudios(j: JsonObject): List<String> {
        val c = j.lista("involved_companies")
        fun con(marca: String) = c.filter { (it[marca] as? JsonPrimitive)?.booleanOrNull == true }
            .mapNotNull { (it["company"] as? JsonObject)?.get("name").texto().takeIf(String::isNotBlank) }
        return con("developer").ifEmpty { con("publisher") }.map { WikidataJuegos.sinComas(it) }.filter { it.isNotEmpty() }.distinct()
    }

    /** Las plataformas con su nombre ("PC (Microsoft Windows)" queda «PC»). */
    internal fun plataformas(j: JsonObject): List<String> = j.lista("platforms").mapNotNull { p ->
        val n = p["name"].texto().trim()
        if (n == "PC (Microsoft Windows)") "PC" else n.takeIf { it.isNotEmpty() }
    }.distinct()

    /**
     * Los géneros (y temas conocidos) como claves que no dependen del idioma ([GenerosJuego]): los de Wikidata por su
     * elemento, los otros de IGDB por su clave; uno que no está en ninguna tabla, con el nombre de IGDB. Hasta 6.
     */
    internal fun generos(j: JsonObject): List<String> {
        fun clave(n: String): String = GenerosJuego.clave(n) ?: n
        val generos = j.lista("genres").mapNotNull { g -> g["name"].texto().trim().takeIf { it.isNotEmpty() }?.let(::clave) }
        // De los temas (fantasía, ciencia ficción…), solo los que en Wikidata son géneros de juego («Acción», «Terror»).
        val temas = j.lista("themes").mapNotNull { t -> COMO_GENERO[t["name"].texto().trim()] }
        return (temas.take(1) + generos + temas.drop(1)).distinctBy { Logica.tituloLimpio(it) }.take(6)
    }

    /** Capturas y artes: miniatura (569 px) para la fila y grande (1080p) para el visor; hasta 30, sin repetir. */
    internal fun galeriaDe(j: JsonObject): List<Pair<String, String>> =
        (j.lista("screenshots") + j.lista("artworks")).mapNotNull { it["image_id"].texto().takeIf(String::isNotBlank) }.distinct().take(30)
            .map { imagen(it, "screenshot_med") to imagen(it, "1080p") }.filter { it.first.isNotBlank() }

    /** Sitio oficial ([Enlace.OFICIAL]), tiendas y wikis de los sitios de IGDB, en ese orden; las redes sociales no. */
    internal fun enlacesDe(j: JsonObject): List<Enlace> {
        val sitios = j.lista("websites").mapNotNull { w ->
            val url = w["url"].texto().trim().takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return@mapNotNull null
            nombreDeSitio(url, (w["type"] as? JsonPrimitive)?.intOrNull)?.let { it to url }
        }
        // Las wikis van al final, después de Wikipedia.
        return sitios.sortedBy { (n, _) -> ORDEN.indexOf(n).let { if (it < 0) ORDEN.size else it } }
            .map { (n, u) -> Enlace(n, u) }.distinctBy { it.nombre }
    }

    /** Sus enlaces de IGDB, lo que falte de Wikidata ([x]) y al final su página en IGDB. */
    internal fun juntarEnlaces(propios: List<Enlace>, x: WikidataJuegos.Extra?, idioma: String): List<Enlace> {
        val r = propios.toMutableList()
        fun falta(n: String) = r.none { it.nombre == n }
        if (x != null) {
            if (falta(Enlace.OFICIAL)) x.oficial?.let { r.add(0, Enlace(Enlace.OFICIAL, it)) }
            if (falta("Steam")) x.steam?.let { r += Enlace("Steam", "https://store.steampowered.com/app/$it/") }
            // Wikipedia en el idioma de la app (IGDB solo trae la de inglés).
            (x.wikipedia[if (idioma == "en") "en" else "es"] ?: x.wikipedia["en"])?.let { w ->
                val i = r.indexOfFirst { it.nombre == "Wikipedia" }
                if (i >= 0) r[i] = Enlace("Wikipedia", w) else r += Enlace("Wikipedia", w)
            }
            r += Enlace("Wikidata", "https://www.wikidata.org/wiki/${x.qid}")
        }
        return r.distinctBy { it.url }
    }

    /** La página del juego en IGDB (de su ficha), para el crédito con enlace. */
    internal fun pagina(j: JsonObject): String = j["url"].texto().takeIf { it.startsWith("https://www.igdb.com/") } ?: SITIO

    suspend fun paginaDe(fuente: String): String = idDe(fuente)?.let { runCatching { pagina(juego(it)) }.getOrNull() } ?: SITIO

    private val ORDEN = listOf(Enlace.OFICIAL, "Steam", "GOG", "Epic Games Store", "itch.io", "PlayStation Store", "Nintendo eShop",
        "Xbox", "App Store", "Google Play", "Wikipedia")

    /**
     * El nombre de un sitio por su dirección (o por su tipo en IGDB: 1 es el oficial, 2 una wiki); null si no se muestra.
     * Una wiki lleva su sitio ([Enlace.WIKI] + "fandom.com", "wiki.gg" o el dominio), para no confundirla con Wikipedia.
     */
    internal fun nombreDeSitio(url: String, tipo: Int?): String? {
        val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull()?.removePrefix("www.") ?: return null
        fun es(vararg dominios: String) = dominios.any { host == it || host.endsWith(".$it") }
        return when {
            es("steampowered.com", "steamcommunity.com") -> "Steam"
            es("gog.com") -> "GOG"
            es("epicgames.com") -> "Epic Games Store"
            es("itch.io") -> "itch.io"
            es("playstation.com") -> "PlayStation Store"
            es("nintendo.com", "nintendo.co.jp", "nintendo.co.uk") -> "Nintendo eShop"
            es("xbox.com") -> "Xbox"
            es("apps.apple.com", "itunes.apple.com") -> "App Store"
            es("play.google.com") -> "Google Play"
            es("wikipedia.org") -> "Wikipedia"
            es("fandom.com") -> Enlace.WIKI + "fandom.com"
            es("wikia.com") -> Enlace.WIKI + "wikia.com"
            es("wiki.gg") -> Enlace.WIKI + "wiki.gg"
            es("facebook.com", "twitter.com", "x.com", "instagram.com", "youtube.com", "twitch.tv", "reddit.com", "discord.gg",
                "discord.com", "bsky.app", "tiktok.com") -> null
            tipo == 1 -> Enlace.OFICIAL
            tipo == 2 -> Enlace.WIKI + host
            else -> null
        }
    }

    private fun JsonElement?.texto(): String = (this as? JsonPrimitive)?.contentOrNull ?: ""
    private fun JsonElement?.largo(): Long? = (this as? JsonPrimitive)?.longOrNull
    private fun JsonObject.lista(k: String): List<JsonObject> = (this[k] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
}

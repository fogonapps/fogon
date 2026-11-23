package io.github.fogonapps.cajon.datos

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt

/*
 * Traer lo que la persona tenía en otras apps, desde los archivos que esas apps dejan exportar:
 *  - Letterboxd: Ajustes → Import & Export → Export your data (un ZIP con diary.csv, ratings.csv,
 *    watchlist.csv, reviews.csv…). Sirve el ZIP o cualquiera de sus CSV.
 *  - Goodreads: My Books → Import and export → Export library (un CSV).
 *  - The StoryGraph: Manage Account → Export StoryGraph Library (un CSV).
 *  - Trakt: Settings → Data → Export (un ZIP de JSON con el formato de su API). Sirve el ZIP o uno de sus JSON.
 *  - TV Time: el ZIP de datos personales (CSV). Sirve el ZIP o uno de sus CSV.
 * Las columnas se buscan por su nombre. Lo importado queda sin portada ni sinopsis (no se consulta nada
 * afuera); desde la ficha se puede completar con "Buscar los datos".
 */

/** Lee un CSV con comillas (campos con comas y saltos de línea adentro). */
fun leerCsv(texto: String): List<Map<String, String>> {
    val filas = mutableListOf<List<String>>()
    var campo = StringBuilder(); var fila = mutableListOf<String>(); var comillas = false; var i = 0
    val t = texto.removePrefix("\uFEFF")
    while (i < t.length) {
        val c = t[i]
        when {
            comillas && c == '"' && i + 1 < t.length && t[i + 1] == '"' -> { campo.append('"'); i++ }
            c == '"' -> comillas = !comillas
            !comillas && c == ',' -> { fila.add(campo.toString()); campo = StringBuilder() }
            !comillas && (c == '\n' || c == '\r') -> {
                if (c == '\r' && i + 1 < t.length && t[i + 1] == '\n') i++
                fila.add(campo.toString()); campo = StringBuilder()
                if (fila.any { it.isNotEmpty() }) filas.add(fila)
                fila = mutableListOf()
            }
            else -> campo.append(c)
        }
        i++
    }
    fila.add(campo.toString()); if (fila.any { it.isNotEmpty() }) filas.add(fila)
    if (filas.isEmpty()) return emptyList()
    val cab = filas.first().map { it.trim() }
    return filas.drop(1).map { f -> cab.mapIndexed { k, n -> n to (f.getOrNull(k) ?: "").trim() }.toMap() }
}

/** Lo que salió de un archivo de otra app, listo para sumar al cajón. */
data class Importado(val obras: List<Obra>, val deDonde: String)

object Importar {

    /**
     * El historial de Netflix (Cuenta → Perfil → Actividad → Descargar todo: NetflixViewingHistory.csv, con "Title" y
     * "Date"). Lo que tiene "Temporada", "Capítulo"… es una serie (se junta por su nombre, en curso, con un registro por
     * día); lo demás, una película vista. Null si no parece de Netflix.
     */
    /** Perfiles con actividad principal; los avances no cuentan como historial. */
    fun perfilesNetflix(texto: String): List<String> = leerCsv(texto)
        .filter { it["Title"] != null && it["Start Time"] != null && it["Supplemental Video Type"].isNullOrBlank() }
        .mapNotNull { it["Profile Name"] }.distinct().sorted()

    fun netflix(texto: String, nuevoId: (String) -> String, ahora: Long, perfil: String? = null): Importado? {
        val filas = leerCsv(texto)
        if (filas.isEmpty()) return null
        val completa = filas.first().keys.containsAll(setOf("Profile Name", "Start Time", "Duration", "Title"))
        if (!completa && filas.first().keys.map { it.lowercase() }.toSet() != setOf("title", "date")) return null
        val partes = Regex("(?i)^(temporada|season|parte|part|volumen|volume|libro|book|capítulo|capitulo|episodio|episode|chapter|serie limitada|limited series|miniserie|miniseries|colección|collection)\\b")
        // La fecha viene según el país: d/m/aa o m/d/aa; si alguna tiene un día mayor que 12, se sabe cuál es.
        val fechas = filas.mapNotNull { it["Date"] ?: it["date"] }.map { it.split('/', '-', '.') }
        val diaPrimero = fechas.any { (it.getOrNull(0)?.toIntOrNull() ?: 0) > 12 } || fechas.none { (it.getOrNull(1)?.toIntOrNull() ?: 0) > 12 }
        fun fecha(s: String): String? {
            val p = s.split('/', '-', '.').mapNotNull { it.trim().toIntOrNull() }
            if (p.size != 3) return null
            if (s.length >= 10 && p[0] > 31) return "%04d-%02d-%02d".format(p[0], p[1], p[2])
            val (d, m) = if (diaPrimero) p[0] to p[1] else p[1] to p[0]
            val a = if (p[2] < 100) 2000 + p[2] else p[2]
            return runCatching { java.time.LocalDate.of(a, m, d).toString() }.getOrNull()
        }
        data class Vista(val titulo: String, val serie: Boolean, val fechas: MutableSet<String> = sortedSetOf())
        val vistas = LinkedHashMap<String, Vista>()
        filas.forEach { f ->
            if (completa && (!f["Supplemental Video Type"].isNullOrBlank() || perfil != null && f["Profile Name"] != perfil)) return@forEach
            val t = (f["Title"] ?: f["title"]).orEmpty().trim().ifBlank { return@forEach }
            val trozos = t.split(": ")
            val corte = trozos.indexOfFirst { it.trim().contains(partes) }
            // Serie solo si después de la temporada viene el capítulo ("Dune: Parte Dos" es una película).
            val (nombre, serie) = if (corte > 0 && corte < trozos.size - 1) trozos.take(corte).joinToString(": ") to true else t to false
            val v = vistas.getOrPut(nombre.lowercase()) { Vista(nombre, serie) }
            val dia = if (completa) runCatching {
                java.time.LocalDateTime.parse(f["Start Time"], java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
                    .withResolverStyle(java.time.format.ResolverStyle.STRICT))
                    .toInstant(java.time.ZoneOffset.UTC).atOffset(java.time.ZoneOffset.UTC).toLocalDate().toString()
            }.getOrNull() else fecha((f["Date"] ?: f["date"]).orEmpty())
            dia?.let { v.fechas += it }
        }
        if (vistas.isEmpty()) return null
        val obras = vistas.values.map { v ->
            Obra(
                id = nuevoId("o"), tipo = if (v.serie) Tipos.SERIE else Tipos.PELICULA, titulo = v.titulo,
                estado = if (v.serie) Estados.EN_CURSO else Estados.TERMINADO,
                registros = if (v.serie) emptyList() else v.fechas.map { Registro(nuevoId("g"), it) },
                terminada = if (v.serie) null else v.fechas.maxOrNull(), empezada = v.fechas.minOrNull(), agregada = ahora, cambio = ahora,
            )
        }
        return Importado(obras, "Netflix")
    }

    /** El ZIP de Letterboxd o uno de sus CSV. Null si no parece de Letterboxd. */
    fun letterboxd(bytes: ByteArray, nombre: String, nuevoId: (String) -> String, ahora: Long): Importado? {
        val archivos: Map<String, String> = if (nombre.endsWith(".zip", true) || bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            // Solo sus CSV y con tope: un ZIP de otra app (con archivos de la cuenta) o inflado no se lee entero. Lo borrado
            // (deleted/, orphaned/) no entra, y de likes/ solo films.csv (likes/reviews.csv son reseñas ajenas).
            (leerZip(bytes, Regex("(?i)^(?!(.*/)?(deleted|orphaned)/)(?!(.*/)?likes/(?!films\\.csv$))(.*/)?(diary|ratings|watched|watchlist|reviews|films|likes)[^/]*\\.csv$")) ?: return null)
                .associate { (n, t) -> n.substringAfterLast('/') to t }
        } else mapOf(nombre.substringAfterLast('/') to bytes.decodeToString())
        // Cada película por su enlace de Letterboxd (o nombre y año).
        data class Peli(var titulo: String, var anio: Int?, var nota: Int = 0, var vista: Boolean = false, var meGusta: Boolean = false,
                        var resena: String = "", var fechaVista: String? = null, val registros: MutableList<Registro> = mutableListOf())
        val pelis = LinkedHashMap<String, Peli>()
        fun clave(f: Map<String, String>) = f["Name"].orEmpty() + "|" + f["Year"].orEmpty()
        fun de(f: Map<String, String>): Peli? {
            val n = f["Name"]?.takeIf { it.isNotBlank() } ?: return null
            return pelis.getOrPut(clave(f)) { Peli(n, f["Year"]?.toIntOrNull()) }
        }
        fun nota(t: String?) = t?.toDoubleOrNull()?.let { (it * 2).roundToInt().coerceIn(0, 10) } ?: 0
        var algo = false
        archivos.forEach { (n, contenido) ->
            val filas = leerCsv(contenido)
            if (filas.isEmpty() || filas.first()["Letterboxd URI"] == null) return@forEach
            algo = true
            when {
                n.startsWith("diary") -> filas.forEach { f ->
                    val p = de(f) ?: return@forEach
                    p.vista = true
                    if (nota(f["Rating"]) > 0) p.nota = nota(f["Rating"])
                    val fecha = f["Watched Date"].orEmpty().ifBlank { f["Date"].orEmpty() }
                    if (fecha.length == 10) p.registros += Registro(nuevoId("g"), fecha, nota(f["Rating"]), f["Rewatch"].equals("Yes", true))
                }
                n.startsWith("ratings") -> filas.forEach { f -> de(f)?.let { it.vista = true; it.nota = nota(f["Rating"]) } }
                n.startsWith("watched") -> filas.forEach { f -> de(f)?.let {
                    it.vista = true
                    it.fechaVista = runCatching { java.time.LocalDate.parse(f["Date"]).toString() }.getOrNull()
                } }
                n.startsWith("reviews") -> filas.forEach { f -> de(f)?.let { p ->
                    p.vista = true
                    if (p.nota == 0) p.nota = nota(f["Rating"])
                    if (f["Review"].orEmpty().isNotBlank()) p.resena = f["Review"].orEmpty()
                    val fecha = runCatching { java.time.LocalDate.parse(f["Watched Date"]).toString() }.getOrNull()
                    val otraVez = f["Rewatch"].equals("Yes", true)
                    if (fecha != null && p.registros.none { it.fecha == fecha && it.otraVez == otraVez }) {
                        p.registros += Registro(nuevoId("g"), fecha, nota(f["Rating"]), otraVez)
                    }
                } }
                n.startsWith("watchlist") -> filas.forEach { f -> de(f) }
                n.startsWith("films") || n.startsWith("likes") -> filas.forEach { f -> de(f)?.meGusta = true }
            }
        }
        if (!algo) return null
        val obras = pelis.values.map { p ->
            // watched.csv guarda cuándo se marcó vista; el diario tiene las fechas precisas de cada visionado.
            if (p.registros.isEmpty()) p.fechaVista?.let { p.registros += Registro(nuevoId("g"), it) }
            Obra(
                id = nuevoId("o"), tipo = Tipos.PELICULA, titulo = p.titulo, anio = p.anio,
                estado = if (p.vista || p.registros.isNotEmpty()) Estados.TERMINADO else Estados.QUIERO,
                nota = p.nota, meGusta = p.meGusta, resena = p.resena, registros = p.registros.distinctBy { it.fecha + it.otraVez },
                terminada = p.registros.maxOfOrNull { it.fecha }, agregada = ahora, cambio = ahora,
            )
        }
        return Importado(obras, "Letterboxd")
    }

    /** El CSV de Goodreads. Null si no parece de Goodreads. */
    /**
     * El historial que se descarga de Spotify (Cuenta → Privacidad → Descargar tus datos): el ZIP o sus .json.
     * Del "historial ampliado" (`Streaming_History_Audio_*.json`) salen las canciones con su álbum (las de al menos
     * 30 segundos); el historial básico (`StreamingHistory_music_*.json`) no trae el álbum, así que da [sinAlbum].
     * Null si no es un historial de Spotify.
     */
    data class HistorialSpotify(val escuchas: List<Escucha>, val sinAlbum: Boolean)

    fun spotify(bytes: ByteArray, nombre: String): HistorialSpotify? {
        val archivos = mutableListOf<Pair<String, String>>()
        if (nombre.endsWith(".zip", true) || bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            runCatching {
                ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
                    while (true) {
                        val e = z.nextEntry ?: break
                        val n = e.name.substringAfterLast('/')
                        if (n.endsWith(".json") && (n.startsWith("Streaming_History_Audio") || n.startsWith("StreamingHistory"))) archivos += n to z.readBytes().decodeToString()
                    }
                }
            }
        } else if (nombre.endsWith(".json", true)) archivos += nombre to bytes.decodeToString()
        if (archivos.isEmpty()) return null
        val r = mutableListOf<Escucha>()
        var basico = false
        for ((_, texto) in archivos) {
            val lista = runCatching { JsonCajon.parseToJsonElement(texto.removePrefix("\uFEFF")) as? kotlinx.serialization.json.JsonArray }.getOrNull() ?: continue
            for (x in lista) {
                val o = x as? kotlinx.serialization.json.JsonObject ?: continue
                fun t(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
                fun ms(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull() ?: 0L
                if ("artistName" in o) { basico = true; continue }
                if (ms("ms_played") < 30_000) continue
                val album = t("master_metadata_album_album_name"); val artista = t("master_metadata_album_artist_name")
                if (album.isBlank() || artista.isBlank()) continue
                val cuando = runCatching { java.time.Instant.parse(t("ts")).toEpochMilli() }.getOrDefault(0L)
                r += Escucha(artista, album, t("master_metadata_track_name"), cuando, "Spotify")
            }
        }
        if (r.isEmpty() && !basico) return null
        return HistorialSpotify(r, sinAlbum = r.isEmpty() && basico)
    }

    fun goodreads(texto: String, nuevoId: (String) -> String, ahora: Long): Importado? {
        val filas = leerCsv(texto)
        if (filas.isEmpty() || filas.first()["Exclusive Shelf"] == null) return null
        fun fecha(t: String?) = t?.trim()?.takeIf { it.length == 10 }?.replace('/', '-')
        val obras = filas.mapNotNull { f ->
            val titulo = f["Title"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val estante = f["Exclusive Shelf"].orEmpty()
            val leido = fecha(f["Date Read"])
            val nota = (f["My Rating"]?.toIntOrNull() ?: 0) * 2
            Obra(
                id = nuevoId("o"), tipo = Tipos.LIBRO, titulo = titulo,
                creador = listOf(f["Author"].orEmpty(), f["Additional Authors"].orEmpty()).filter { it.isNotBlank() }.joinToString(", "),
                anio = f["Original Publication Year"]?.toIntOrNull() ?: f["Year Published"]?.toIntOrNull(),
                paginas = f["Number of Pages"]?.toIntOrNull(),
                estado = when (estante) { "read" -> Estados.TERMINADO; "currently-reading" -> Estados.EN_CURSO; else -> Estados.QUIERO },
                nota = nota, resena = f["My Review"].orEmpty().replace("<br/>", "\n"),
                etiquetas = f["Bookshelves"].orEmpty().split(",").map { it.trim() }.filter { it.isNotBlank() && it !in listOf("read", "to-read", "currently-reading") },
                terminada = leido, registros = listOfNotNull(leido?.let { Registro(nuevoId("g"), it, nota) }),
                agregada = ahora, cambio = ahora,
            )
        }
        return Importado(obras, "Goodreads")
    }

    /**
     * El CSV de The StoryGraph (Manage Account → Export StoryGraph Library). Columnas por nombre: Title, Authors,
     * Read Status (read, currently-reading, did-not-finish, paused, to-read), Last Date Read y Dates Read (AAAA/MM/DD,
     * una o varias lecturas separadas por coma, punto y coma o barra; cada una como rango inicio-fin, inicio- o una
     * fecha), Star Rating (0 a 5 en cuartos), Review (texto o HTML sencillo) y Tags.
     * Null si no parece de StoryGraph.
     */
    fun storygraph(texto: String, nuevoId: (String) -> String, ahora: Long): Importado? {
        val filas = leerCsv(texto)
        if (filas.isEmpty() || !filas.first().keys.containsAll(setOf("Title", "Read Status", "Star Rating", "ISBN/UID"))) return null
        val dia = Regex("(\\d{4})[/-](\\d{1,2})[/-](\\d{1,2})")
        val separador = Regex("[,;|]")
        val saltos = Regex("(?i)<br\\s*/?>|</p>\\s*<p>")
        // Solo etiquetas HTML comunes: un "a < b" escrito en la reseña se conserva.
        val etiquetasHtml = Regex("(?i)</?(p|br|b|i|u|em|strong|span|div|a|ul|ol|li|blockquote)(\\s[^>]*)?/?>")
        // "&amp;" al final: así un "&amp;lt;" escrito en la reseña queda como "&lt;".
        val entidades = mapOf("&quot;" to "\"", "&#39;" to "'", "&apos;" to "'", "&lt;" to "<", "&gt;" to ">", "&nbsp;" to " ", "&amp;" to "&")
        fun fechas(t: String?) = dia.findAll(t.orEmpty()).mapNotNull { m ->
            runCatching { java.time.LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()).toString() }.getOrNull()
        }.toList()
        /** Una lectura de "Dates Read": "inicio-fin", "inicio-" (sin terminar) o una sola fecha (el día que se terminó). */
        data class Lectura(val inicio: String?, val fin: String?)
        val obras = filas.mapNotNull { f ->
            val titulo = f["Title"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val estado = when (f["Read Status"].orEmpty().trim().lowercase()) {
                "read" -> Estados.TERMINADO
                "currently-reading", "paused" -> Estados.EN_CURSO
                "did-not-finish" -> Estados.ABANDONADO
                else -> Estados.QUIERO
            }
            val nota = f["Star Rating"]?.replace(',', '.')?.toDoubleOrNull()?.let { (it * 2).roundToInt().coerceIn(0, 10) } ?: 0
            val lecturas = f["Dates Read"].orEmpty().split(separador).mapNotNull { trozo ->
                val d = fechas(trozo)
                when {
                    d.size >= 2 -> Lectura(d.first(), d.last())
                    d.size == 1 && trozo.trim().endsWith("-") -> Lectura(d.first(), null)
                    d.size == 1 -> Lectura(null, d.first())
                    else -> null
                }
            }
            // Si no está terminado, la última lectura es la de ahora (o la abandonada): no es un registro, da el inicio.
            val actual = lecturas.lastOrNull()?.takeIf { estado == Estados.EN_CURSO || estado == Estados.ABANDONADO }
            val inicioActual = actual?.inicio ?: actual?.fin
            val anteriores = if (actual != null) lecturas.dropLast(1) else lecturas
            val ultima = fechas(f["Last Date Read"]).lastOrNull()
                // Sin terminar y sin "Dates Read" no se sabe si esa fecha fue una lectura completa: no se cuenta.
                ?.takeIf { estado == Estados.TERMINADO || inicioActual != null && it < inicioActual || estado == Estados.QUIERO }
            val fin = (anteriores.mapNotNull { it.fin } + listOfNotNull(ultima)).distinct().sorted()
            val empezada = when (estado) {
                Estados.QUIERO -> null
                Estados.TERMINADO -> lecturas.lastOrNull { it.fin == fin.lastOrNull() }?.inicio ?: lecturas.lastOrNull()?.inicio
                else -> inicioActual
            }
            val resena = entidades.entries.fold(f["Review"].orEmpty().replace(saltos, "\n").replace(etiquetasHtml, "")) { t, (a, b) -> t.replace(a, b) }
            Obra(
                id = nuevoId("o"), tipo = Tipos.LIBRO, titulo = titulo.trim(),
                creador = f["Authors"].orEmpty().trim(),
                estado = estado, nota = nota, resena = resena.trim(),
                etiquetas = f["Tags"].orEmpty().split(",").map { it.trim() }.filter { it.isNotBlank() },
                registros = fin.mapIndexed { i, d -> Registro(nuevoId("g"), d, if (i == fin.lastIndex && estado == Estados.TERMINADO) nota else 0, otraVez = i > 0) },
                empezada = empezada,
                terminada = fin.lastOrNull()?.takeIf { estado == Estados.TERMINADO }, agregada = ahora, cambio = ahora,
            )
        }
        return Importado(obras, "The StoryGraph")
    }

    /**
     * La exportación de Trakt (Settings → Data → Export: un ZIP de JSON con el formato de su API) o uno de sus JSON.
     * Cada elemento se reconoce por lo que trae, no por el nombre del archivo:
     *  - historial (`history-*.json`): `watched_at` y `type` movie o episode; cada vista de una película es un registro y
     *    cada episodio ("1x3") queda visto en su serie;
     *  - vistos (`watched-movies.json`, `watched-shows.json`): `plays` y `last_watched_at`; en series, `seasons` con sus episodios;
     *  - notas (`ratings-*.json`): `rating` de 1 a 10, como las medias estrellas del cajón; las de temporadas y episodios no se usan;
     *  - pendientes (`watchlist-*.json`): `listed_at`; los de las listas propias (`lists/…`) y la colección no se traen.
     * Con el id de TMDB la obra queda enlazada ("tmdb:movie:603", "tmdb:tv:1396") y la app trae portada y datos después.
     * Una serie vista en los últimos 6 meses queda en curso; las de antes, terminadas (Trakt no dice si se dejó).
     * Las fechas se pasan al día de [zona]. Null si no parece de Trakt.
     */
    fun trakt(bytes: ByteArray, nombre: String, nuevoId: (String) -> String, ahora: Long,
              zona: java.time.ZoneId = java.time.ZoneId.systemDefault()): Importado? {
        val archivos = mutableListOf<Pair<String, String>>()
        val esZip = nombre.endsWith(".zip", true) || bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        if (esZip) archivos += leerZip(bytes, Regex("(?i)(history|watched|ratings|watchlist)[^/]*\\.json$")) ?: return null
        else archivos += nombre to bytes.decodeToString()
        fun texto(o: JsonObject?, k: String) = (o?.get(k) as? JsonPrimitive)?.takeIf { it.isString }?.content
        fun numero(o: JsonObject?, k: String) = (o?.get(k) as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()?.toInt()
        fun objeto(o: JsonObject?, k: String) = o?.get(k) as? JsonObject
        fun dia(t: String?) = t?.let { runCatching { java.time.Instant.parse(it).atZone(zona).toLocalDate().toString() }.getOrNull() }

        class Item(val serie: Boolean, val titulo: String, val anio: Int?, val fuente: String) {
            var nota = 0; var quiero = false; var vista = false; var ultima: String? = null
            val vistas = mutableListOf<String>(); val episodios = sortedSetOf<String>(); val dias = sortedSetOf<String>()
        }
        val items = LinkedHashMap<String, Item>()
        /** La película o la serie de un elemento ([serie] elige cuál); null si no trae título. */
        fun de(x: JsonObject?, serie: Boolean): Item? {
            val titulo = texto(x, "title")?.trim()?.takeIf { it.isNotBlank() } ?: return null
            val anio = numero(x, "year")
            val ids = objeto(x, "ids")
            val tmdb = numero(ids, "tmdb")
            val clave = (if (serie) "s:" else "m:") + (numero(ids, "trakt")?.toString() ?: (titulo.lowercase() + "|" + anio))
            return items.getOrPut(clave) {
                Item(serie, titulo, anio, tmdb?.let { if (serie) "tmdb:tv:$it" else "tmdb:movie:$it" }.orEmpty())
            }
        }
        var esTrakt = false
        for ((ruta, contenido) in archivos) {
            val lista = runCatching { JsonCajon.parseToJsonElement(contenido.removePrefix("\uFEFF")) as? JsonArray }.getOrNull() ?: continue
            val n = ruta.substringAfterLast('/').lowercase()
            // Pendiente es solo lo de la watchlist: las listas propias, favoritos y recomendaciones también traen listed_at.
            // Un JSON suelto sin nombre útil (el selector puede dar "msf:31") se toma como watchlist.
            val watchlist = "watchlist" in n || !esZip && !n.endsWith(".json")
            for (e in lista) {
                val o = e as? JsonObject ?: continue
                val peli = objeto(o, "movie"); val show = objeto(o, "show")
                if (objeto(peli ?: show ?: objeto(o, "episode"), "ids")?.containsKey("trakt") != true) continue
                esTrakt = true
                val tipo = texto(o, "type")
                when {
                    "watched_at" in o -> {
                        val d = dia(texto(o, "watched_at"))
                        if (tipo == "movie" || tipo == null && peli != null) de(peli, false)?.let { i -> i.vista = true; d?.let { i.vistas += it } }
                        else if (tipo == "episode") de(show, true)?.let { i ->
                            i.vista = true; d?.let { i.dias += it }
                            val ep = objeto(o, "episode")
                            val t = numero(ep, "season"); val c = numero(ep, "number")
                            if (t != null && c != null && t > 0) i.episodios += "${t}x$c"
                        }
                    }
                    "plays" in o || "last_watched_at" in o -> {
                        val d = dia(texto(o, "last_watched_at"))
                        if (peli != null) de(peli, false)?.let { i -> i.vista = true; if (d != null && (i.ultima == null || d > i.ultima!!)) i.ultima = d }
                        else de(show, true)?.let { i ->
                            i.vista = true
                            (o["seasons"] as? JsonArray)?.forEach { s ->
                                val t = numero(s as? JsonObject, "number") ?: return@forEach
                                if (t > 0) ((s as JsonObject)["episodes"] as? JsonArray)?.forEach { ep ->
                                    numero(ep as? JsonObject, "number")?.let { c -> i.episodios += "${t}x$c" }
                                    dia(texto(ep as? JsonObject, "last_watched_at"))?.let { i.dias += it }
                                }
                            }
                        }
                    }
                    "rating" in o -> {
                        val nota = (numero(o, "rating") ?: 0).coerceIn(0, 10)
                        when (tipo ?: if (peli != null) "movie" else "show") {
                            "movie" -> de(peli, false)?.let { if (nota > 0) it.nota = nota }
                            "show" -> de(show, true)?.let { if (nota > 0) it.nota = nota }
                        }
                    }
                    "listed_at" in o && watchlist -> when (tipo ?: if (peli != null) "movie" else "show") {
                        "movie" -> de(peli, false)?.quiero = true
                        "show" -> de(show, true)?.quiero = true
                    }
                }
            }
        }
        if (!esTrakt) return null
        val hace6Meses = java.time.Instant.ofEpochMilli(ahora).atZone(zona).toLocalDate().minusMonths(6).toString()
        val obras = items.values.mapNotNull { i ->
            // Lo calificado se vio; lo que solo está en otra lista (y nada más) no entra.
            val vista = i.vista || i.nota > 0
            if (!vista && !i.quiero) return@mapNotNull null
            if (i.serie) Obra(
                id = nuevoId("o"), tipo = Tipos.SERIE, titulo = i.titulo, anio = i.anio, fuente = i.fuente,
                estado = when {
                    !vista -> Estados.QUIERO
                    i.dias.lastOrNull()?.let { it < hace6Meses } == true -> Estados.TERMINADO
                    else -> Estados.EN_CURSO
                },
                terminada = i.dias.lastOrNull()?.takeIf { it < hace6Meses }, nota = i.nota,
                vistos = i.episodios.sortedWith(compareBy({ it.substringBefore('x').toInt() }, { it.substringAfter('x').toInt() })),
                empezada = i.dias.firstOrNull(), agregada = ahora, cambio = ahora,
            ) else {
                val fechas = i.vistas.sorted().ifEmpty { listOfNotNull(i.ultima) }
                Obra(
                    id = nuevoId("o"), tipo = Tipos.PELICULA, titulo = i.titulo, anio = i.anio, fuente = i.fuente,
                    estado = if (vista) Estados.TERMINADO else Estados.QUIERO, nota = i.nota,
                    registros = fechas.mapIndexed { k, d -> Registro(nuevoId("g"), d, if (k == fechas.lastIndex) i.nota else 0, otraVez = k > 0) },
                    terminada = fechas.lastOrNull(), agregada = ahora, cambio = ahora,
                )
            }
        }
        return Importado(obras, "Trakt")
    }

    /** Los archivos de un ZIP cuyo nombre encaja con [utiles], con tope de 50 MB en total (un ZIP inflado no llena la memoria). Null si está dañado o se pasa. */
    private fun leerZip(bytes: ByteArray, utiles: Regex): List<Pair<String, String>>? {
        val archivos = mutableListOf<Pair<String, String>>()
        var quedan = 50L * 1024 * 1024
        return runCatching {
            ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
                val trozo = ByteArray(64 * 1024)
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.isDirectory || !utiles.containsMatchIn(e.name)) continue
                    val b = java.io.ByteArrayOutputStream()
                    while (true) {
                        val leidos = z.read(trozo)
                        if (leidos < 0) break
                        quedan -= leidos
                        if (quedan < 0) return null
                        b.write(trozo, 0, leidos)
                    }
                    archivos += e.name to b.toByteArray().decodeToString()
                }
            }
            archivos
        }.getOrNull()
    }

    /**
     * La exportación de datos de TV Time: el ZIP de datos personales (carpeta `gdpr-data/`) o uno
     * de sus CSV. Se leen solo estos, por el nombre de sus columnas (nunca los de la cuenta, como tokens o direcciones IP):
     *  - `tracking-prod-records-v2.csv`: un episodio visto por fila (`series_name`, `season_number`/`s_no`,
     *    `episode_number`/`ep_no`, `created_at`); las filas `user-series-…` dicen si la serie se sigue (`is_followed`),
     *    se dejó para después (`is_for_later`) o se archivó (`is_archived`);
     *  - `tracking-prod-records.csv`: películas (`entity_type` movie, `movie_name`, `release_date`) vistas (`type` watch o
     *    rewatch) o pendientes (towatch); también episodios;
     *  - `followed_tv_show.csv` y `user_tv_show_data.csv`: las series seguidas (`tv_show_name`, `archived`);
     *  - las exportaciones viejas `seen_episode.csv` y `watched_on_episode.csv` (`show_name`/`tv_show_name`,
     *    `episode_season_number`, `episode_number`, `created_at` o `ts`).
     * TV Time no da ids de TMDB: lo importado queda sin fuente y la app lo busca por título. Las fechas ("AAAA-MM-DD hh:mm:ss",
     * en UTC) se pasan al día de [zona]. Una serie seguida sin episodios queda como pendiente; archivada, abandonada; con
     * episodios, en curso si se vio en los últimos 6 meses y terminada si no. Las notas no se traen (su formato no está claro).
     * Null si no parece de TV Time.
     */
    fun tvtime(bytes: ByteArray, nombre: String, nuevoId: (String) -> String, ahora: Long,
               zona: java.time.ZoneId = java.time.ZoneId.systemDefault()): Importado? {
        val esZip = nombre.endsWith(".zip", true) || bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        val archivos = if (esZip) leerZip(bytes, Regex("(?i)(^|/)(tracking-prod-records(-v2)?|followed_tv_show|user_tv_show_data|seen_episode|watched_on_episode)\\.csv$")) ?: return null
            else listOf(nombre to bytes.decodeToString())
        val hora = java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(java.time.format.ResolverStyle.STRICT)
        fun si(t: String?) = t?.trim()?.lowercase() in setOf("true", "1", "yes")
        fun numero(f: Map<String, String>, vararg k: String) = k.firstNotNullOfOrNull { f[it]?.trim()?.toIntOrNull() }

        class Item(val serie: Boolean, val titulo: String) {
            var anio: Int? = null; var sigue = false; var despues = false; var archivada = false; var quiero = false
            val vistas = sortedSetOf<java.time.Instant>(); var vistaSinFecha = false; val episodios = sortedSetOf<String>(); val dias = sortedSetOf<String>()
        }
        val items = LinkedHashMap<String, Item>()
        fun de(titulo: String?, serie: Boolean, anio: Int? = null): Item? {
            val t = titulo?.trim()?.takeIf { it.isNotBlank() } ?: return null
            // Películas por título y año (si se sabe): "Dune" de 1984 y la de 2021 son dos.
            return items.getOrPut((if (serie) "s:" else "m:") + t.lowercase() + (anio?.let { "|$it" } ?: "")) { Item(serie, t).also { it.anio = anio } }
        }
        /** El momento exacto de la fila (para no juntar dos visionados del mismo día). */
        fun momento(f: Map<String, String>): java.time.Instant? {
            val t = f["created_at"].orEmpty().trim()
            val instante = if (t.isNotEmpty()) runCatching { java.time.LocalDateTime.parse(t.take(19), hora).toInstant(java.time.ZoneOffset.UTC) }.getOrNull()
                ?: runCatching { java.time.Instant.parse(t) }.getOrNull()
            else f["ts"]?.trim()?.toLongOrNull()?.let { java.time.Instant.ofEpochSecond(it) }
            return instante
        }
        var esTvtime = false
        for ((_, contenido) in archivos) {
            val filas = leerCsv(contenido)
            val cab = filas.firstOrNull()?.keys ?: continue
            val nombres = setOf("series_name", "movie_name", "tv_show_name", "show_name")
            // Las columnas de TV Time, con fecha (o el id de la serie, en la lista de seguidas): un CSV cualquiera no entra.
            if (cab.none { it in nombres } || cab.none { it in setOf("created_at", "ts", "tv_show_id", "s_id") }) continue
            esTvtime = true
            for (f in filas) {
                val serie = f["series_name"] ?: f["tv_show_name"] ?: f["show_name"]
                val llave = f["key"].orEmpty()
                val tipo = f["type"].orEmpty().trim().lowercase()
                if (llave.startsWith("tracking-stats") || tipo.startsWith("count")) continue
                when {
                    // Las banderas de una serie: seguida, para después o archivada.
                    llave.startsWith("user-series") || llave.isEmpty() && ("tv_show_id" in f || "is_followed" in f) && numero(f, "episode_number", "ep_no") == null -> de(serie, true)?.let { i ->
                        val activa = f["active"]?.trim()?.let { it.isEmpty() || si(it) } ?: true
                        if (si(f["is_followed"]) || "tv_show_id" in f && "is_followed" !in f && activa) i.sigue = true
                        if (si(f["is_for_later"])) i.despues = true
                        if (si(f["is_archived"]) || si(f["archived"])) i.archivada = true
                    }
                    f["entity_type"].equals("movie", true) || serie.isNullOrBlank() && !f["movie_name"].isNullOrBlank() ->
                        de(f["movie_name"], false, f["release_date"]?.take(4)?.toIntOrNull()?.takeIf { it > 1800 })?.let { i ->
                            when {
                                tipo == "towatch" -> i.quiero = true
                                tipo in setOf("watch", "rewatch") || tipo.isEmpty() && (llave.isEmpty() || llave.startsWith("watch-") || llave.startsWith("rewatch-")) ->
                                    momento(f)?.let { i.vistas += it } ?: run { i.vistaSinFecha = true }
                            }
                        }
                    // Un episodio visto: en v2 solo las claves watch-/rewatch- (otras filas, como votos, no cuentan).
                    tipo.isEmpty() && (llave.isEmpty() || llave.startsWith("watch-") || llave.startsWith("rewatch-")) || tipo in setOf("watch", "rewatch") -> de(serie, true)?.let { i ->
                        val t = numero(f, "season_number", "s_no", "episode_season_number")
                        val c = numero(f, "episode_number", "ep_no") ?: return@let
                        momento(f)?.let { i.dias += it.atZone(zona).toLocalDate().toString() }
                        // Los especiales (temporada 0) cuentan como días vistos, no como episodios.
                        if (t != null && t > 0 && !si(f["is_special"])) i.episodios += "${t}x$c"
                    }
                    tipo == "towatch" || tipo == "follow" -> de(serie, true)?.sigue = true
                }
            }
        }
        if (!esTvtime) return null
        val hace6Meses = java.time.Instant.ofEpochMilli(ahora).atZone(zona).toLocalDate().minusMonths(6).toString()
        val obras = items.values.mapNotNull { i ->
            if (i.serie) {
                val vista = i.episodios.isNotEmpty() || i.dias.isNotEmpty()
                if (!vista && !i.sigue && !i.despues && !i.archivada) return@mapNotNull null
                val vieja = i.dias.lastOrNull()?.let { it < hace6Meses } == true
                Obra(
                    id = nuevoId("o"), tipo = Tipos.SERIE, titulo = i.titulo,
                    estado = when {
                        i.archivada -> Estados.ABANDONADO
                        !vista -> Estados.QUIERO
                        vieja -> Estados.TERMINADO
                        else -> Estados.EN_CURSO
                    },
                    terminada = i.dias.lastOrNull()?.takeIf { vieja && !i.archivada },
                    vistos = i.episodios.sortedWith(compareBy({ it.substringBefore('x').toInt() }, { it.substringAfter('x').toInt() })),
                    empezada = i.dias.firstOrNull(), agregada = ahora, cambio = ahora,
                )
            } else {
                // Cada visionado por su hora, pasado al día de la persona (dos el mismo día son dos registros).
                val fechas = i.vistas.map { it.atZone(zona).toLocalDate().toString() }
                val vista = i.vistas.isNotEmpty() || i.vistaSinFecha
                if (!vista && !i.quiero) return@mapNotNull null
                Obra(
                    id = nuevoId("o"), tipo = Tipos.PELICULA, titulo = i.titulo, anio = i.anio,
                    estado = if (vista) Estados.TERMINADO else Estados.QUIERO,
                    registros = fechas.mapIndexed { k, d -> Registro(nuevoId("g"), d, otraVez = k > 0) },
                    terminada = fechas.lastOrNull(), agregada = ahora, cambio = ahora,
                )
            }
        }
        return Importado(obras, "TV Time")
    }

    /**
     * Suma lo importado sin repetir: si ya hay una obra con la misma fuente, o del mismo tipo con el mismo título y año
     * (y sin otra fuente), se le suman los registros y los episodios vistos y se completa lo que le falte; si no, entra nueva.
     */
    fun sumar(e: Estado, nuevas: List<Obra>, ahora: Long): Pair<Estado, Int> {
        val obras = e.obras.toMutableList()
        val resenas = Resenas.migrar(e).resenas.toMutableList()
        val importadas = Resenas.migrar(Estado(obras = nuevas)).resenas
        var nuevasN = 0
        nuevas.forEach { n ->
            val i = obras.indexOfFirst { !it.borrada && n.fuente.isNotEmpty() && it.fuente == n.fuente }.takeIf { it >= 0 }
                ?: obras.indexOfFirst { !it.borrada && it.tipo == n.tipo && normal(it.titulo) == normal(n.titulo) && (it.anio == null || n.anio == null || it.anio == n.anio) &&
                    // Enlazadas a la misma fuente con otro id son obras distintas; de fuentes distintas (TVmaze y TMDB) pueden ser la misma.
                    (it.fuente.substringBeforeLast(':') != n.fuente.substringBeforeLast(':') || it.fuente == n.fuente) }
            val destino = if (i < 0) n.id else obras[i].id
            val registros = if (i < 0) emptyList() else obras[i].registros
            importadas.filter { it.obra == n.id }.forEach { r ->
                val registro = r.registro?.let { id ->
                    val original = n.registros.firstOrNull { it.id == id }
                    registros.firstOrNull { it.fecha == original?.fecha && it.otraVez == original.otraVez }?.id ?: id
                }
                if (resenas.none { it.obra == destino && it.registro == registro && it.texto == r.texto }) {
                    resenas += r.copy(id = "n:" + java.util.UUID.randomUUID(), obra = destino, registro = registro, cambio = ahora)
                }
            }
            if (i < 0) { obras += n.copy(resena = "", registros = n.registros.map { it.copy(texto = "") }); nuevasN++ } else {
                val o = obras[i]
                obras[i] = o.copy(
                    nota = if (o.nota == 0) n.nota else o.nota, meGusta = o.meGusta || n.meGusta,
                    registros = (o.registros + n.registros.map { it.copy(texto = "") }).distinctBy { it.fecha + it.otraVez },
                    estado = if (o.estado == Estados.QUIERO) n.estado else o.estado, terminada = o.terminada ?: n.terminada,
                    paginas = o.paginas ?: n.paginas, creador = o.creador.ifBlank { n.creador }, anio = o.anio ?: n.anio,
                    fuente = o.fuente.ifEmpty { n.fuente }, vistos = (o.vistos + n.vistos).distinct(), empezada = o.empezada ?: n.empezada, cambio = ahora,
                )
            }
        }
        return e.copy(obras = obras, resenas = resenas) to nuevasN
    }
}

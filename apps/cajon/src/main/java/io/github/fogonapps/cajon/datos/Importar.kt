package io.github.fogonapps.cajon.datos

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt

/*
 * Traer lo que la persona tenía en otras apps, desde los archivos que esas apps dejan exportar:
 *  - Letterboxd: Ajustes → Import & Export → Export your data (un ZIP con diary.csv, ratings.csv,
 *    watchlist.csv, reviews.csv…). Sirve el ZIP o cualquiera de sus CSV.
 *  - Goodreads: My Books → Import and export → Export library (un CSV).
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
        val archivos: Map<String, String> = if (bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            val m = mutableMapOf<String, String>()
            ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    if (!e.isDirectory && e.name.endsWith(".csv")) m[e.name.substringAfterLast('/')] = z.readBytes().decodeToString()
                    e = z.nextEntry
                }
            }
            m
        } else mapOf(nombre.substringAfterLast('/') to bytes.decodeToString())
        // Cada película por su enlace de Letterboxd (o nombre y año).
        data class Peli(var titulo: String, var anio: Int?, var nota: Int = 0, var vista: Boolean = false, var meGusta: Boolean = false,
                        var resena: String = "", val registros: MutableList<Registro> = mutableListOf())
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
                n.startsWith("watched") -> filas.forEach { f -> de(f)?.vista = true }
                n.startsWith("reviews") -> filas.forEach { f -> de(f)?.let { it.vista = true; if (f["Review"].orEmpty().isNotBlank()) it.resena = f["Review"].orEmpty() } }
                n.startsWith("watchlist") -> filas.forEach { f -> de(f) }
                n.startsWith("films") || n.startsWith("likes") -> filas.forEach { f -> de(f)?.meGusta = true }
            }
        }
        if (!algo) return null
        val obras = pelis.values.map { p ->
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
            val lista = runCatching { JsonCajon.parseToJsonElement(texto) as? kotlinx.serialization.json.JsonArray }.getOrNull() ?: continue
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
     * Suma lo importado sin repetir: si ya hay una obra del mismo tipo con el mismo título y año, se le suman
     * los registros y se completa lo que le falte; si no, entra nueva.
     */
    fun sumar(e: Estado, nuevas: List<Obra>, ahora: Long): Pair<Estado, Int> {
        val obras = e.obras.toMutableList()
        val resenas = Resenas.migrar(e).resenas.toMutableList()
        val importadas = Resenas.migrar(Estado(obras = nuevas)).resenas
        var nuevasN = 0
        nuevas.forEach { n ->
            val i = obras.indexOfFirst { !it.borrada && it.tipo == n.tipo && normal(it.titulo) == normal(n.titulo) && (it.anio == null || n.anio == null || it.anio == n.anio) }
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
                    paginas = o.paginas ?: n.paginas, creador = o.creador.ifBlank { n.creador }, cambio = ahora,
                )
            }
        }
        return e.copy(obras = obras, resenas = resenas) to nuevasN
    }
}

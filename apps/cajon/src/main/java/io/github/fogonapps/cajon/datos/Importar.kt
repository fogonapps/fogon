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
        var nuevasN = 0
        nuevas.forEach { n ->
            val i = obras.indexOfFirst { !it.borrada && it.tipo == n.tipo && normal(it.titulo) == normal(n.titulo) && (it.anio == null || n.anio == null || it.anio == n.anio) }
            if (i < 0) { obras += n; nuevasN++ } else {
                val o = obras[i]
                obras[i] = o.copy(
                    nota = if (o.nota == 0) n.nota else o.nota, meGusta = o.meGusta || n.meGusta,
                    resena = o.resena.ifBlank { n.resena }, registros = (o.registros + n.registros).distinctBy { it.fecha + it.otraVez },
                    estado = if (o.estado == Estados.QUIERO) n.estado else o.estado, terminada = o.terminada ?: n.terminada,
                    paginas = o.paginas ?: n.paginas, creador = o.creador.ifBlank { n.creador }, cambio = ahora,
                )
            }
        }
        return e.copy(obras = obras) to nuevasN
    }
}

package io.github.fogonapps.cajon.datos

import java.text.Normalizer
import java.time.LocalDate

/** Para buscar sin importar tildes ni mayúsculas. */
fun normal(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

/** Las estrellas de una nota en medias estrellas (0 a 10): "★★★½". Vacío si no tiene nota. */
fun estrellas(nota: Int): String {
    if (nota <= 0) return ""
    return "★".repeat(nota / 2) + if (nota % 2 == 1) "½" else ""
}

/** Cómo ordenar el cajón. */
enum class Orden { RECIENTES, NOTA, ANIO, TITULO }

/** Una entrada del diario con su obra, para mostrar. */
data class EntradaDiario(val obra: Obra, val registro: Registro)

/** Las cifras de un año (o de siempre, si [anio] es null). */
data class Cifras(
    val anio: Int?,
    /** Obras terminadas por tipo. */
    val porTipo: Map<String, Int>,
    /** Veces que se registró algo en el diario (cuenta volver a ver). */
    val registros: Int,
    val minutosCine: Int,
    val episodios: Int,
    val paginas: Int,
    val canciones: Int,
    /** Cuántas notas hay de cada media estrella (índice 1 a 10). */
    val notas: IntArray,
    val generos: List<Pair<String, Int>>,
    val creadores: List<Pair<String, Int>>,
    /** Registros por mes (índice 0 = enero). */
    val porMes: IntArray,
    val notaMedia: Double?,
)

object Logica {

    /* ----- Sincronizar: unir dos estados ----- */

    /**
     * Une lo de este aparato con lo del Gist (u otro aparato): por cada obra, artista y lista gana la versión
     * con el [Obra.cambio] más reciente; lo borrado sigue borrado si su borrado es lo más reciente.
     */
    fun unir(a: Estado, b: Estado): Estado = Estado(
        v = maxOf(a.v, b.v),
        listo = a.listo || b.listo,
        ajustes = if (b.ajustes.cambio > a.ajustes.cambio) b.ajustes else a.ajustes,
        obras = unirPor(a.obras, b.obras, { it.id }, { it.cambio }),
        artistas = unirPor(a.artistas, b.artistas, { it.id }, { it.cambio }),
        listas = unirPor(a.listas, b.listas, { it.id }, { it.cambio }),
    )

    private fun <T> unirPor(a: List<T>, b: List<T>, id: (T) -> String, cambio: (T) -> Long): List<T> {
        val m = LinkedHashMap<String, T>()
        a.forEach { m[id(it)] = it }
        b.forEach { x -> val y = m[id(x)]; if (y == null || cambio(x) > cambio(y)) m[id(x)] = x }
        return m.values.toList()
    }

    /** Ordena y quita lo repetido (por si un respaldo viejo trae duplicados). */
    fun normalizar(e: Estado): Estado = e.copy(
        obras = e.obras.distinctBy { it.id },
        artistas = e.artistas.distinctBy { it.id },
        listas = e.listas.distinctBy { it.id },
        ajustes = e.ajustes.copy(favoritas = e.ajustes.favoritas.distinct().take(4)),
    )

    /* ----- El cajón ----- */

    fun filtrar(e: Estado, tipo: String?, estado: String?, texto: String, orden: Orden): List<Obra> {
        val palabras = normal(texto).split(" ").filter { it.isNotBlank() }
        val lista = e.vivas.filter { o ->
            (tipo == null || o.tipo == tipo) && (estado == null || o.estado == estado) &&
                (palabras.isEmpty() || normal("${o.titulo} ${o.original} ${o.creador} ${o.generos.joinToString(" ")} ${o.etiquetas.joinToString(" ")}").let { t -> palabras.all { it in t } })
        }
        return when (orden) {
            // Lo último que se tocó (agregar, registrar, calificar) primero.
            Orden.RECIENTES -> lista.sortedByDescending { maxOf(it.cambio, it.agregada) }
            Orden.NOTA -> lista.sortedWith(compareByDescending<Obra> { it.nota }.thenBy { normal(it.titulo) })
            Orden.ANIO -> lista.sortedWith(compareByDescending<Obra> { it.anio ?: 0 }.thenBy { normal(it.titulo) })
            Orden.TITULO -> lista.sortedBy { normal(it.titulo) }
        }
    }

    /** La fecha del último registro del diario (o cuándo se terminó). */
    fun ultimaVez(o: Obra): String? = (o.registros.map { it.fecha } + listOfNotNull(o.terminada)).maxOrNull()

    /** Ya está en el cajón algo de esa fuente (para no agregarlo dos veces). */
    fun porFuente(e: Estado, fuente: String): Obra? = if (fuente.isEmpty()) null else e.vivas.firstOrNull { it.fuente == fuente }

    /* ----- Diario ----- */

    fun diario(e: Estado): List<EntradaDiario> =
        e.vivas.flatMap { o -> o.registros.map { EntradaDiario(o, it) } }
            .sortedWith(compareByDescending<EntradaDiario> { it.registro.fecha }.thenByDescending { it.registro.id })

    /* ----- Progreso ----- */

    fun totalEpisodios(o: Obra) = o.temporadas.sumOf { it.episodios }

    /** De 0 a 1, o null si no hay cómo medirlo. */
    fun progreso(o: Obra): Float? = when {
        Tipos.porEpisodios(o.tipo) && totalEpisodios(o) > 0 -> o.vistos.size.toFloat() / totalEpisodios(o)
        o.tipo == Tipos.LIBRO && (o.paginas ?: 0) > 0 -> o.pagina.toFloat() / o.paginas!!
        else -> null
    }

    /** El siguiente episodio por ver ("1x4"), o null si los vio todos. */
    fun siguienteEpisodio(o: Obra): Pair<Int, Int>? {
        for (t in o.temporadas.sortedBy { it.numero }) for (ep in 1..t.episodios) if ("${t.numero}x$ep" !in o.vistos) return t.numero to ep
        return null
    }

    /** Marca o desmarca un episodio; si con eso los vio todos, la serie queda terminada (y al revés). */
    fun marcarEpisodio(o: Obra, temporada: Int, episodio: Int, hoy: LocalDate): Obra {
        val k = "${temporada}x$episodio"
        val vistos = if (k in o.vistos) o.vistos - k else o.vistos + k
        var r = o.copy(vistos = vistos)
        if (vistos.isNotEmpty() && o.estado == Estados.QUIERO) r = r.copy(estado = Estados.EN_CURSO, empezada = o.empezada ?: hoy.toString())
        val total = totalEpisodios(o)
        if (total > 0 && vistos.size >= total && o.estado != Estados.TERMINADO) r = r.copy(estado = Estados.TERMINADO, terminada = hoy.toString())
        else if (total > 0 && vistos.size < total && o.estado == Estados.TERMINADO) r = r.copy(estado = Estados.EN_CURSO, terminada = null)
        return r
    }

    /** Marca toda una temporada (o la desmarca si ya estaba completa). */
    fun marcarTemporada(o: Obra, temporada: Int, hoy: LocalDate): Obra {
        val t = o.temporadas.firstOrNull { it.numero == temporada } ?: return o
        val todos = (1..t.episodios).map { "${temporada}x$it" }
        val completa = todos.all { it in o.vistos }
        var r = o.copy(vistos = if (completa) o.vistos - todos.toSet() else (o.vistos + todos).distinct())
        val total = totalEpisodios(o)
        if (!completa && r.vistos.size >= total) r = r.copy(estado = Estados.TERMINADO, terminada = hoy.toString(), empezada = o.empezada ?: hoy.toString())
        else if (!completa && o.estado == Estados.QUIERO) r = r.copy(estado = Estados.EN_CURSO, empezada = o.empezada ?: hoy.toString())
        else if (completa && o.estado == Estados.TERMINADO) r = r.copy(estado = Estados.EN_CURSO, terminada = null)
        return r
    }

    /** Cambia el estado y anota las fechas de empezar y terminar. */
    fun cambiarEstado(o: Obra, estado: String, hoy: LocalDate): Obra = when (estado) {
        Estados.EN_CURSO -> o.copy(estado = estado, empezada = o.empezada ?: hoy.toString(), terminada = null)
        Estados.TERMINADO -> o.copy(
            estado = estado, empezada = o.empezada ?: hoy.toString(), terminada = o.terminada ?: hoy.toString(),
            pagina = o.paginas ?: o.pagina,
        )
        else -> o.copy(estado = estado)
    }

    /* ----- Estadísticas ----- */

    /** El año de una obra terminada (por su último registro o su fecha de terminada). */
    private fun anioDe(fecha: String?) = fecha?.take(4)?.toIntOrNull()

    fun cifras(e: Estado, anio: Int?): Cifras {
        val obras = e.vivas
        val terminadas = obras.filter { o ->
            o.estado == Estados.TERMINADO && (anio == null || anioDe(ultimaVez(o)) == anio)
        }
        val registros = obras.flatMap { o -> o.registros.filter { anio == null || anioDe(it.fecha) == anio }.map { o to it } }
        val notas = IntArray(11)
        terminadas.forEach { if (it.nota in 1..10) notas[it.nota]++ }
        val porMes = IntArray(12)
        registros.forEach { (_, r) -> r.fecha.substring(5, 7).toIntOrNull()?.let { m -> if (m in 1..12) porMes[m - 1]++ } }
        // Para las cifras de cine, cada vez que se vio cuenta (volver a ver suma minutos).
        val minutos = registros.filter { it.first.tipo == Tipos.PELICULA }.sumOf { it.first.minutos ?: 0 } +
            terminadas.filter { it.tipo == Tipos.PELICULA && it.registros.isEmpty() }.sumOf { it.minutos ?: 0 }
        val conNota = terminadas.filter { it.nota > 0 }
        fun top(sel: (Obra) -> List<String>) = terminadas.flatMap(sel).filter { it.isNotBlank() }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5).map { it.key to it.value }
        return Cifras(
            anio = anio,
            porTipo = Tipos.TODOS.associateWith { t -> terminadas.count { it.tipo == t } },
            registros = registros.size,
            minutosCine = minutos,
            episodios = obras.filter { Tipos.porEpisodios(it.tipo) }.sumOf { o -> if (anio == null || anioDe(o.terminada ?: o.empezada) == anio) o.vistos.size else 0 },
            paginas = terminadas.filter { it.tipo == Tipos.LIBRO }.sumOf { it.paginas ?: 0 },
            canciones = terminadas.filter { it.tipo == Tipos.ALBUM }.sumOf { it.canciones.size },
            notas = notas,
            generos = top { it.generos },
            creadores = top { listOf(it.creador) },
            porMes = porMes,
            notaMedia = if (conNota.isEmpty()) null else conNota.sumOf { it.nota } / 2.0 / conNota.size,
        )
    }

    /** Días seguidos (hasta hoy o ayer) con algo en el diario. */
    fun racha(e: Estado, hoy: LocalDate): Int {
        val dias = e.vivas.flatMap { o -> o.registros.map { it.fecha } }.toSet()
        var f = if (hoy.toString() in dias) hoy else hoy.minusDays(1)
        var n = 0
        while (f.toString() in dias) { n++; f = f.minusDays(1) }
        return n
    }

    /** Lo que lleva del reto del año para un tipo. */
    fun avanceReto(e: Estado, tipo: String, anio: Int): Int = cifras(e, anio).porTipo[tipo] ?: 0
}

package io.github.fogonapps.cajon.datos

import java.time.LocalDate

/** Las láminas del resumen del año, en el orden en que se ven. Cada una sale solo si tiene datos. */
enum class Lamina { TOTAL, TIPOS, TIEMPO, MEJORES, GUSTOS, JUEGOS, MES, RACHA, FAVORITAS, CIERRE }

/**
 * Lo que cuenta el resumen de un año: sale de [Logica.cifras] y de lo terminado en ese año (con el mismo criterio que
 * las cifras: el año de la última vez). No inventa nada; lo que no hay no tiene lámina.
 */
data class ResumenAnio(
    val anio: Int,
    /** Obras terminadas en el año. */
    val total: Int,
    /** Veces anotadas en el diario en el año. */
    val registros: Int,
    /** Terminadas por tipo, solo los tipos con alguna, de más a menos. */
    val porTipo: List<Pair<String, Int>>,
    /** Minutos de películas, series y animes. */
    val minutos: Int,
    val paginas: Int,
    val canciones: Int,
    /** Lo mejor calificado, hasta tres. */
    val mejores: List<Obra>,
    val genero: Pair<String, Int>?,
    val creador: Pair<String, Int>?,
    /** El mes con más actividad (1 a 12) y cuántas veces. */
    val mes: Int?,
    val enMes: Int,
    /** Actividad por mes (índice 0 = enero): el diario o, si no hay, lo terminado. */
    val porMes: IntArray,
    /** Los días seguidos con algo en el diario: la racha más larga del año. */
    val racha: Int,
    /** Lo terminado en el año con corazón, sin repetir lo mejor calificado; hasta seis. */
    val favoritas: List<Obra>,
    /** Lo último terminado, para el abanico de portadas; hasta siete. */
    val recientes: List<Obra>,
    /** Los juegos terminados en el año, los mejor calificados primero. */
    val juegos: List<Obra> = emptyList(),
    /** Dónde más se jugó lo terminado ([Obra.dondeJuego]) y el estudio que más se repite entre esos juegos. */
    val plataforma: Pair<String, Int>? = null,
    val estudio: Pair<String, Int>? = null,
    /** Las horas en Steam de esos juegos (las de siempre, no solo las del año: Steam no las da por año). */
    val horasSteam: Int = 0,
) {
    /** Con menos de cinco cosas en el año, el resumen sería pobre: mejor invitar a traer el historial. */
    val pocosDatos: Boolean get() = maxOf(total, registros) < MINIMO

    /** Las láminas que tienen algo que contar. */
    val laminas: List<Lamina> get() = buildList {
        add(Lamina.TOTAL)
        if (porTipo.size >= 2) add(Lamina.TIPOS)
        if (minutos >= 60 || paginas > 0 || canciones > 0) add(Lamina.TIEMPO)
        if (mejores.isNotEmpty()) add(Lamina.MEJORES)
        if (genero != null || creador != null) add(Lamina.GUSTOS)
        if (juegos.isNotEmpty()) add(Lamina.JUEGOS)
        if (mes != null) add(Lamina.MES)
        if (racha >= 2) add(Lamina.RACHA)
        if (favoritas.isNotEmpty()) add(Lamina.FAVORITAS)
        add(Lamina.CIERRE)
    }

    override fun equals(other: Any?) = other is ResumenAnio && anio == other.anio && total == other.total && registros == other.registros &&
        porTipo == other.porTipo && minutos == other.minutos && paginas == other.paginas && canciones == other.canciones &&
        mejores == other.mejores && genero == other.genero && creador == other.creador && mes == other.mes && enMes == other.enMes &&
        porMes.contentEquals(other.porMes) && racha == other.racha && favoritas == other.favoritas && recientes == other.recientes &&
        juegos == other.juegos && plataforma == other.plataforma && estudio == other.estudio && horasSteam == other.horasSteam
    override fun hashCode() = 31 * anio + total * 17 + registros + porMes.contentHashCode()

    companion object {
        const val MINIMO = 5

        /** El año del resumen: en enero todavía se mira el que acaba de terminar. */
        fun anioDelResumen(hoy: LocalDate): Int = if (hoy.monthValue == 1) hoy.year - 1 else hoy.year

        /** Si el Inicio destaca el resumen: en diciembre y en enero. */
        fun esTemporada(hoy: LocalDate): Boolean = hoy.monthValue == 12 || hoy.monthValue == 1

        fun de(e: Estado, anio: Int): ResumenAnio {
            val c = Logica.cifras(e, anio)
            fun delAnio(fecha: String?) = fecha?.take(4)?.toIntOrNull() == anio
            val terminadas = e.vivas.filter { it.estado == Estados.TERMINADO && delAnio(Logica.ultimaVez(it)) }
            val mejores = terminadas.filter { it.nota > 0 }
                .sortedWith(compareByDescending<Obra> { it.nota }.thenByDescending { Logica.ultimaVez(it) }).take(3)
            val favoritas = terminadas.filter { it.meGusta && it !in mejores }
                .sortedWith(compareByDescending<Obra> { it.nota }.thenByDescending { Logica.ultimaVez(it) }).take(6)
            // El mes: por el diario; si no hay diario (algo importado solo con su fecha de terminada), por lo terminado.
            val porMes = if (c.porMes.sum() > 0) c.porMes.copyOf() else IntArray(12).also { m ->
                terminadas.forEach { o -> Logica.ultimaVez(o)?.substring(5, 7)?.toIntOrNull()?.let { if (it in 1..12) m[it - 1]++ } }
            }
            val juegos = terminadas.filter { it.tipo == Tipos.JUEGO }
                .sortedWith(compareByDescending<Obra> { it.nota }.thenByDescending { Logica.ultimaVez(it) })
            // Un juego guarda hasta tres estudios separados por comas: cada uno cuenta aparte, como en las cifras.
            val estudio = juegos.flatMap { o -> o.creador.split(',').map { it.trim() } }.filter { it.isNotEmpty() }
                .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.firstOrNull()?.toPair()
            val maximo = porMes.max()
            val mes = if (maximo > 0) porMes.indexOfFirst { it == maximo } + 1 else null
            return ResumenAnio(
                anio = anio,
                total = terminadas.size,
                registros = c.registros,
                porTipo = c.porTipo.filterValues { it > 0 }.entries.sortedByDescending { it.value }.map { it.key to it.value },
                minutos = c.minutosCine + c.minutosSeries,
                paginas = c.paginas,
                canciones = c.canciones,
                mejores = mejores,
                genero = c.generos.firstOrNull(),
                creador = c.creadores.firstOrNull(),
                mes = mes,
                enMes = maximo,
                porMes = porMes,
                racha = rachaMasLarga(e, anio),
                favoritas = favoritas,
                recientes = terminadas.sortedByDescending { Logica.ultimaVez(it) }.take(7),
                juegos = juegos,
                plataforma = c.plataformas.firstOrNull(),
                estudio = estudio,
                horasSteam = juegos.sumOf { it.minutosSteam ?: 0 } / 60,
            )
        }

        /** La racha más larga del año: los días seguidos con algo anotado en el diario. */
        fun rachaMasLarga(e: Estado, anio: Int): Int {
            val dias = e.vivas.flatMap { o -> o.registros.map { it.fecha.take(10) } }
                .filter { it.take(4).toIntOrNull() == anio }
                .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSortedSet()
            var mejor = 0; var actual = 0; var antes: LocalDate? = null
            for (d in dias) {
                actual = if (antes != null && antes.plusDays(1) == d) actual + 1 else 1
                mejor = maxOf(mejor, actual); antes = d
            }
            return mejor
        }
    }
}

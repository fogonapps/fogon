package io.github.fogonapps.cajon.datos

/*
 * Las insignias del perfil: logros que cuestan tiempo de verdad. Para que no se puedan «farmear»:
 *  - no dan chispas (ni nada que se canjee): no hay premio por hacer trampa;
 *  - cuentan días reales: a lo sumo [POR_DIA] obras terminadas por día (agregar 200 de golpe no sirve) y días
 *    distintos con registro;
 *  - lo que no tiene fecha (lo traído de un respaldo sin fechas) no cuenta.
 */

/** Una insignia con cuánto lleva y cuánto pide. */
data class Insignia(val id: String, val lleva: Int, val meta: Int) {
    val ganada get() = lleva >= meta
}

object Insignias {
    /** Cuántas obras terminadas cuentan por día como máximo. */
    const val POR_DIA = 3

    /** Las insignias, en el orden en que se muestran. */
    val IDS = listOf("cine", "series", "anime", "lector", "discos", "constancia", "critico", "explorador", "reto")

    /** Cuántas cuentan: por cada día, a lo sumo [POR_DIA]. Solo las que tienen fecha. */
    private fun contadas(fechas: List<String>) = fechas.filter { it.length >= 10 }.groupingBy { it.take(10) }.eachCount().values.sumOf { minOf(it, POR_DIA) }

    fun de(e: Estado, anio: Int): List<Insignia> {
        val vivas = e.vivas
        fun terminadas(tipo: String) = contadas(vivas.filter { it.tipo == tipo && it.estado == Estados.TERMINADO }.mapNotNull { Logica.ultimaVez(it) })
        // Un disco cuenta si lo calificaste un día (su registro), con el mismo tope por día.
        val discos = contadas(vivas.filter { it.tipo == Tipos.ALBUM && it.nota > 0 }.mapNotNull { o -> o.registros.minOfOrNull { it.fecha } ?: o.terminada })
        val dias = vivas.flatMap { o -> o.registros.map { it.fecha.take(10) } + listOfNotNull(o.terminada?.take(10), o.empezada?.take(10)) }.filter { it.length == 10 }.toSet().size
        val criticas = e.resenas.count { !it.borrada && it.texto.trim().length >= 280 }
        val tipos = vivas.filter { it.estado == Estados.TERMINADO && Logica.ultimaVez(it) != null }.map { it.tipo }.toSet().size
        val c = Logica.cifras(e, anio)
        val retoCumplido = e.ajustes.retos.isNotEmpty() && e.ajustes.retos.all { (t, meta) -> meta > 0 && (c.porTipo[t] ?: 0) >= meta }
        return listOf(
            Insignia("cine", terminadas(Tipos.PELICULA), 50),
            Insignia("series", terminadas(Tipos.SERIE), 10),
            Insignia("anime", terminadas(Tipos.ANIME), 10),
            Insignia("lector", terminadas(Tipos.LIBRO), 12),
            Insignia("discos", discos, 30),
            Insignia("constancia", dias, 30),
            Insignia("critico", criticas, 10),
            Insignia("explorador", tipos, Tipos.TODOS.size.coerceAtMost(5)),
            Insignia("reto", if (retoCumplido) 1 else 0, 1),
        )
    }
}

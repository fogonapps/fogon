package io.github.fogonapps.cajon.datos

/*
 * Las insignias del perfil: logros que cuestan tiempo de verdad. Para que no se puedan «farmear»:
 *  - no dan chispas (ni nada que se canjee): no hay premio por hacer trampa;
 *  - cuentan días reales: a lo sumo [POR_DIA] obras terminadas por día (agregar 200 de golpe no sirve) y días
 *    distintos con registro;
 *  - lo que no tiene fecha (lo traído de un respaldo sin fechas) no cuenta.
 */

/**
 * Una insignia con cuánto lleva y cuánto pide. [niveles] son las metas de cada nivel (la primera es [meta]): se gana
 * al llegar a la primera y cada nivel más apila otro talón.
 */
data class Insignia(val id: String, val lleva: Int, val meta: Int, val niveles: List<Int> = listOf(meta)) {
    val ganada get() = lleva >= meta

    /** Cuántos niveles lleva: 0 si aún no la gana. */
    val nivel get() = niveles.count { lleva >= it }

    /** La meta del nivel más alto alcanzado ([meta] si aún no la gana). */
    val alcanzada get() = niveles.getOrNull(nivel - 1) ?: meta

    /** La meta del nivel que sigue, o null si ya tiene todos. */
    val siguiente get() = niveles.getOrNull(nivel)
}

object Insignias {
    /** Cuántas obras terminadas cuentan por día como máximo. */
    const val POR_DIA = 3

    /** Las insignias, en el orden en que se muestran. */
    val IDS = listOf("cine", "series", "anime", "lector", "discos", "constancia", "critico", "explorador", "reto")

    /** Cuánto pide cada insignia (también para describir las de otro perfil, que llegan sin cifras). */
    fun meta(id: String): Int = when (id) {
        "cine" -> 50
        "series", "anime", "critico" -> 10
        "lector" -> 12
        "discos", "constancia" -> 30
        "explorador" -> Tipos.TODOS.size.coerceAtMost(5)
        else -> 1
    }

    /** Cada nivel pide la meta por estas veces (como 10, 50 y 100). */
    val VECES_NIVEL = listOf(1, 5, 10)

    /** Las metas de los niveles. El explorador (tipos de obra) y el reto no tienen más de uno. */
    fun niveles(id: String): List<Int> = meta(id).let { m -> if (id == "explorador" || id == "reto") listOf(m) else VECES_NIVEL.map { it * m } }

    /** La clave de un nivel en el registro local: el primero es el id solo («cine»); los demás, «cine:2». */
    fun claveNivel(id: String, nivel: Int) = if (nivel <= 1) id else "$id:$nivel"

    /** Las claves de todos los niveles ganados. */
    fun clavesGanadas(lista: List<Insignia>): List<String> = lista.flatMap { i -> (1..i.nivel).map { claveNivel(i.id, it) } }

    /** De una clave del registro, el id y el nivel. */
    fun deClave(clave: String): Pair<String, Int> = clave.substringBefore(':') to (clave.substringAfter(':', "1").toIntOrNull() ?: 1)

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
            Insignia("cine", terminadas(Tipos.PELICULA), meta("cine"), niveles("cine")),
            Insignia("series", terminadas(Tipos.SERIE), meta("series"), niveles("series")),
            Insignia("anime", terminadas(Tipos.ANIME), meta("anime"), niveles("anime")),
            Insignia("lector", terminadas(Tipos.LIBRO), meta("lector"), niveles("lector")),
            Insignia("discos", discos, meta("discos"), niveles("discos")),
            Insignia("constancia", dias, meta("constancia"), niveles("constancia")),
            Insignia("critico", criticas, meta("critico"), niveles("critico")),
            Insignia("explorador", tipos, meta("explorador"), niveles("explorador")),
            Insignia("reto", if (retoCumplido) 1 else 0, meta("reto"), niveles("reto")),
        )
    }

    /** Lo anotado de las insignias ganadas ([fechas]: id → día en que se vio ganada, vacío si no se sabe) y las que hay que celebrar. */
    data class Anotadas(val fechas: Map<String, String>, val celebrar: List<String>)

    /** Si llegan más que estas de golpe, es un respaldo o una importación: se anotan sin celebrar. */
    const val MAX_CELEBRAR = 2

    /**
     * Anota las insignias ganadas (sus claves de nivel, ver [claveNivel]) en el registro local. Sin registro previo ([antes] nulo: la app recién se
     * actualiza o se instala) se anotan todas sin fecha y sin celebrar, para que no salten las que ya estaban.
     * Las nuevas llevan el día [hoy] y se celebran, salvo que lleguen muchas juntas. El reto es del año: si se
     * pierde (año nuevo) se olvida, y al cumplirlo de nuevo se vuelve a celebrar.
     */
    fun anotar(antes: Map<String, String>?, ganadas: List<String>, hoy: String): Anotadas {
        if (antes == null) return Anotadas(ganadas.associateWith { "" }, emptyList())
        val vigentes = antes.filterKeys { it != "reto" || it in ganadas }
        val nuevas = ganadas.filter { it !in vigentes }
        return Anotadas(vigentes + nuevas.associateWith { hoy }, if (nuevas.size <= MAX_CELEBRAR) nuevas else emptyList())
    }
}

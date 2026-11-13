package io.github.fogonapps.cajon.datos

/*
 * La afinidad con otra persona (como las fusiones de las apps de música): cuánto se parecen sus gustos, con lo que
 * más comparten. Se calcula en tu teléfono con tu cajón y lo que esa persona deja ver en su perfil; no sube nada.
 */

/** Una obra que esa persona destaca en su perfil (lo que llega en sus partes). */
data class ObraDestacada(val titulo: String, val tipo: String, val portada: String = "", val fuente: String = "")

/** Lo que tienen en común: el porcentaje y solo lo necesario (unos pocos artistas, géneros y obras). */
data class Afinidad(val porcentaje: Int, val artistas: List<String>, val generos: List<String>, val obras: List<ObraDestacada>, val favoritaDeLosDos: ObraDestacada?,
                    /** Lo que esa persona destaca y tú no tienes: para descubrir (sobre todo cuando comparten poco). */
                    val paraDescubrir: List<ObraDestacada> = emptyList()) {
    /** El número solo se muestra cuando es alto: con poco en común, la tarjeta invita a descubrir, no a comparar. */
    val mostrarPorcentaje get() = porcentaje >= Afinidades.MOSTRAR_DESDE
}

object Afinidades {
    /** Cuántos de cada cosa se muestran en la tarjeta; el resto queda en «Ver todo». */
    const val MOSTRAR_ARTISTAS = 3
    const val MOSTRAR_GENEROS = 4
    const val MOSTRAR_OBRAS = 3
    /** Desde cuánto se muestra el porcentaje (debajo, nada de números: no es para descartar a nadie). */
    const val MOSTRAR_DESDE = 50

    private fun limpio(s: String) = Logica.tituloLimpio(s)

    /**
     * [misObras] son las obras de tu cajón y [misFavoritas] las que destacas (favoritas y destacados). Devuelve null si
     * no hay con qué comparar (sin nada en común que medir de ningún lado).
     */
    fun calcular(
        misArtistas: List<String>, misGeneros: List<String>, misObras: List<Obra>, misFavoritas: List<Obra>,
        susArtistas: List<String>, susGeneros: List<String>, susObras: List<ObraDestacada>,
    ): Afinidad? {
        fun clave(titulo: String, tipo: String) = "$tipo|${limpio(titulo)}"
        fun mismaObra(o: Obra, d: ObraDestacada) = (o.fuente.isNotBlank() && o.fuente == d.fuente) ||
            (o.tipo == d.tipo && (limpio(o.titulo) == limpio(d.titulo) || (o.original.isNotBlank() && limpio(o.original) == limpio(d.titulo))))

        val misA = misArtistas.map(::limpio).filter { it.isNotEmpty() }.toSet()
        val artistas = susArtistas.distinctBy(::limpio).filter { limpio(it) in misA }
        val misG = misGeneros.map(::limpio).filter { it.isNotEmpty() }.toSet()
        val generos = susGeneros.distinctBy(::limpio).filter { limpio(it) in misG }
        val susO = susObras.distinctBy { clave(it.titulo, it.tipo) }
        val obras = susO.filter { d -> misObras.any { mismaObra(it, d) } }

        // Cada parte cuenta solo si los dos tienen algo que comparar; lo compartido sobre lo que tiene el que menos tiene.
        val partes = buildList {
            if (misA.isNotEmpty() && susArtistas.isNotEmpty()) add(0.4 to artistas.size.toDouble() / minOf(misA.size, susArtistas.size))
            if (misG.isNotEmpty() && susGeneros.isNotEmpty()) add(0.3 to generos.size.toDouble() / minOf(misG.size, susGeneros.size))
            if (misObras.isNotEmpty() && susO.isNotEmpty()) add(0.3 to obras.size.toDouble() / susO.size)
        }
        if (partes.isEmpty()) return null
        val parecido = partes.sumOf { it.first * it.second.coerceIn(0.0, 1.0) } / partes.sumOf { it.first }
        // La raíz suaviza: con poco en común igual se nota, y el 100 % queda para gustos casi iguales.
        val porcentaje = (Math.sqrt(parecido) * 100).toInt().coerceIn(0, 100)

        val favorita = obras.firstOrNull { d -> misFavoritas.any { mismaObra(it, d) } }
        // Primero lo que destacan los dos; luego, una película y una serie para que no sea todo del mismo tipo.
        val ordenadas = (listOfNotNull(favorita) + obras.filter { it.tipo == Tipos.PELICULA }.take(1) +
            obras.filter { it.tipo == Tipos.SERIE || it.tipo == Tipos.ANIME }.take(1) + obras).distinct()
        val descubrir = susO.filter { d -> misObras.none { mismaObra(it, d) } }.take(MOSTRAR_OBRAS)
        return Afinidad(porcentaje, artistas.take(MOSTRAR_ARTISTAS), generos.take(MOSTRAR_GENEROS), ordenadas.take(MOSTRAR_OBRAS), favorita, descubrir)
    }
}

/** Para ordenar los géneros de un perfil: los de música aparte de los de cine, series y libros. */
object GenerosPerfil {
    private val musicales by lazy { Logica.GENEROS_COMUNES.flatMap { (k, v) -> listOf(Logica.tituloLimpio(k), Logica.tituloLimpio(v)) }.toSet() }
    private val raices = listOf("rock", "pop", "hip hop", "rap", "jazz", "metal", "punk", "electr", "house", "techno", "reggae", "salsa", "cumbia",
        "trap", "soul", "funk", "blues", "folk", "musica", "bolero", "balada", "ballad", "ska", "indie", "lo fi", "r b", "rnb", "k pop", "j pop")

    fun esMusical(genero: String): Boolean {
        val g = Logica.tituloLimpio(genero)
        return g in musicales || raices.any { g == it || g.startsWith("$it ") || g.endsWith(" $it") || g.contains(it) && it.length >= 5 }
    }
}

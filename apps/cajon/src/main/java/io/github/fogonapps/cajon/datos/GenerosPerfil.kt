package io.github.fogonapps.cajon.datos

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

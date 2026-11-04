package io.github.fogonapps.cajon.datos

/**
 * La cara de El Cajón según lo que estás viendo, leyendo o escuchando: se mira la obra de la frase del saludo (si
 * habla de una) o, si no, lo último que tocaste en curso o terminado. Devuelve el nombre de la cara (`normal`,
 * `feliz`, `enamorado`, `sorpresa`, `triste`, `miedo`, `decidido`, `guino`), y la pantalla la convierte en imagen.
 */
object Caras {
    /** Géneros (en español y en inglés, como vienen de TMDB, Open Library y MusicBrainz) y la cara que ponen. */
    private val PORGENERO: List<Pair<List<String>, String>> = listOf(
        listOf("terror", "horror", "suspense", "suspenso", "thriller", "gore") to "miedo",
        listOf("romance", "romántic", "romantic", "amor", "love") to "enamorado",
        listOf("drama", "bélica", "guerra", "war", "tragedia", "tragedy", "historia", "history") to "triste",
        listOf("acción", "accion", "action", "aventura", "adventure", "crimen", "crime", "western", "deporte", "sport",
            "rock", "metal", "punk", "hip hop", "hip-hop", "rap", "trap") to "decidido",
        listOf("ciencia ficción", "science fiction", "sci-fi", "fantasía", "fantasy", "misterio", "mystery",
            "documental", "documentary") to "sorpresa",
        listOf("comedia", "comedy", "animación", "animation", "familia", "family", "infantil", "kids", "musical",
            "pop", "reggaeton", "dance", "salsa", "cumbia", "funk", "disco") to "feliz",
        listOf("jazz", "clásica", "classical", "ambient", "lo-fi", "lofi", "soul", "bossa", "poesía", "poetry") to "guino",
    )

    /** La cara para una obra: por su primer género que se reconozca; si te encantó, enamorado. */
    fun de(o: Obra?): String {
        if (o == null) return "normal"
        if (o.meGusta || o.nota >= 9) return "enamorado"
        for (g in o.generos.map { it.lowercase() }) {
            PORGENERO.firstOrNull { (claves, _) -> claves.any { it in g } }?.let { return it.second }
        }
        return when (o.tipo) { Tipos.ALBUM -> "guino"; else -> "normal" }
    }

    /** La obra de la que habla la frase (por su id) o, si no hay, la última en curso o terminada. */
    fun obra(e: Estado, obraDeLaFrase: String?): Obra? =
        obraDeLaFrase?.let { id -> e.obras.firstOrNull { it.id == id } }
            ?: e.obras.filter { it.estado == Estados.EN_CURSO || it.estado == Estados.TERMINADO }.maxByOrNull { it.cambio }
}

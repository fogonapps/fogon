package io.github.fogonapps.cajon.datos

/**
 * Los géneros del buscador de cine y de libros (los de la música siguen en [Logica.GENEROS_COMUNES])
 * y cómo se buscan afuera: en TMDB por género (`with_genres`) o, si TMDB no lo tiene como género de series (o es
 * propio del anime), por palabra clave (`with_keywords`); en Open Library por tema (`subject`).
 *
 * Los nombres son los de `generos_cine`, `generos_anime` y `generos_libros` (strings.xml) en los dos idiomas, más
 * los que TMDB pone a las series ("Acción y aventura"). Los ids de palabras clave se revisaron en themoviedb.org
 * (cuántas obras tiene cada una y que fueran las de verdad): no cambian, pero si TMDB borra una, ese
 * género solo trae lo del cajón.
 */
object Generos {
    /**
     * Un género de cine. [peliculas] y [series]: ids de género de TMDB ("28", o "878|14" = uno u otro), o null si no
     * lo tiene; [clavePeliculas] y [claveSeries]: la palabra clave cuando no hay género. [anime]: propio del anime
     * (sale en la sección Animes).
     */
    data class Cine(
        val nombres: List<String>, val peliculas: String?, val series: String?,
        val clavePeliculas: String? = null, val claveSeries: String? = null, val anime: Boolean = false,
    )

    /** Un género de libros, sus alias de búsqueda, el tema de Open Library y sus etiquetas traducidas. */
    data class Libro(val nombres: List<String>, val tema: String, val es: String, val en: String) {
        fun etiqueta(idioma: String) = if (idioma == "en") en else es
    }

    val CINE = listOf(
        Cine(listOf("Acción", "Action"), "28", "10759"),
        Cine(listOf("Aventura", "Adventure"), "12", "10759"),
        Cine(listOf("Acción y aventura", "Action & Adventure"), "28|12", "10759"),
        Cine(listOf("Animación", "Animation"), "16", "16"),
        Cine(listOf("Comedia", "Comedy"), "35", "35"),
        Cine(listOf("Crimen", "Crime"), "80", "80"),
        Cine(listOf("Documental", "Documentary"), "99", "99"),
        Cine(listOf("Drama"), "18", "18"),
        Cine(listOf("Fantasía", "Fantasy"), "14", "10765"),
        Cine(listOf("Terror", "Horror"), "27", null, claveSeries = "315058"),
        Cine(listOf("Misterio", "Mystery"), "9648", "9648"),
        Cine(listOf("Romance"), "10749", null, claveSeries = "9840"),
        Cine(listOf("Ciencia ficción", "Science Fiction"), "878", "10765"),
        Cine(listOf("Ciencia ficción y fantasía", "Sci-Fi & Fantasy"), "878|14", "10765"),
        Cine(listOf("Suspenso", "Thriller", "Suspense"), "53", null, claveSeries = "316362"),
        Cine(listOf("Bélica", "War"), "10752", "10768"),
        Cine(listOf("Bélica y política", "War & Politics"), "10752", "10768"),
        Cine(listOf("Western"), "37", "37"),
        Cine(listOf("Familia", "Family", "Familiar"), "10751", "10751"),
        Cine(listOf("Historia", "History"), "36", null, claveSeries = "192772"),
        Cine(listOf("Música", "Music"), "10402", null, claveSeries = "283297"),
        // Los del anime: palabras clave de TMDB (shounen 207826, seinen 195668, shoujo 206437, josei 229074,
        // isekai 237451, mecha 10046, slice of life 9914, magical girl 292887).
        Cine(listOf("Shonen", "Shōnen", "Shounen"), null, null, "207826", "207826", anime = true),
        Cine(listOf("Seinen"), null, null, "195668", "195668", anime = true),
        Cine(listOf("Shojo", "Shōjo", "Shoujo"), null, null, "206437", "206437", anime = true),
        Cine(listOf("Josei"), null, null, "229074", "229074", anime = true),
        Cine(listOf("Isekai"), null, null, "237451", "237451", anime = true),
        Cine(listOf("Mecha"), null, null, "10046", "10046", anime = true),
        Cine(listOf("Recuentos de la vida", "Slice of life"), null, null, "9914", "9914", anime = true),
        Cine(listOf("Chicas mágicas", "Magical girl"), null, null, "292887", "292887", anime = true),
    )

    val LIBROS = listOf(
        Libro(listOf("Novela", "Fiction", "Ficción"), "fiction", "Novela", "Fiction"),
        Libro(listOf("Ciencia ficción", "Science Fiction"), "science fiction", "Ciencia ficción", "Science fiction"),
        Libro(listOf("Fantasía", "Fantasy"), "fantasy", "Fantasía", "Fantasy"),
        Libro(listOf("Terror", "Horror"), "horror", "Terror", "Horror"),
        Libro(listOf("Policial", "Mystery", "Misterio"), "mystery", "Policial", "Mystery"),
        Libro(listOf("Romance"), "romance", "Romance", "Romance"),
        Libro(listOf("Historia", "History"), "history", "Historia", "History"),
        Libro(listOf("Biografía", "Biography"), "biography", "Biografía", "Biography"),
        Libro(listOf("Ensayo", "Essay", "Essays"), "essays", "Ensayo", "Essay"),
        Libro(listOf("Poesía", "Poetry"), "poetry", "Poesía", "Poetry"),
        Libro(listOf("Filosofía", "Philosophy"), "philosophy", "Filosofía", "Philosophy"),
        Libro(listOf("Psicología", "Psychology"), "psychology", "Psicología", "Psychology"),
        Libro(listOf("Autoayuda", "Self-help"), "self-help", "Autoayuda", "Self-help"),
        Libro(listOf("Clásicos", "Classics", "Clásico"), "classic literature", "Clásicos", "Classics"),
        Libro(listOf("Juvenil", "Young adult"), "young adult fiction", "Juvenil", "Young adult"),
        Libro(listOf("Cómic", "Comics"), "comics & graphic novels", "Cómic", "Comics"),
    )

    private fun igual(a: String, b: String) = Logica.tituloLimpio(a) == Logica.tituloLimpio(b)

    /** El género de cine que es [texto] (sin importar tildes ni mayúsculas), o null. */
    fun cine(texto: String): Cine? = CINE.firstOrNull { g -> g.nombres.any { igual(it, texto) } }

    /** El género de libros que es [texto], o null. */
    fun libro(texto: String): Libro? = LIBROS.firstOrNull { g -> g.nombres.any { igual(it, texto) } }

    /** Etiqueta para mostrar un género o tema de Open Library en el idioma activo, sin cambiar el dato guardado. */
    fun etiquetaLibro(texto: String, idioma: String): String {
        libro(texto)?.let { return it.etiqueta(idioma) }
        val clave = Logica.tituloLimpio(texto)
        val base = clave.replace(Regex(" (?:juvenile )?fiction$"), "")
        val tema = TEMAS_TRADUCIDOS[clave] ?: TEMAS_TRADUCIDOS[base]
        return tema?.let { if (idioma == "en") it.second else it.first } ?: texto
    }
}

package io.github.fogonapps.cajon.datos

/**
 * Los géneros de un juego con fuente (IGDB o Wikidata) se guardan como claves que no dependen del idioma y se muestran
 * con su nombre en el idioma de la app:
 * - los de [WikidataJuegos.GENEROS], por su elemento de Wikidata ("Q828322"), con su nombre de `generos_juegos`;
 * - los de IGDB que no están ahí ([Igdb.OTROS_GENEROS]), por [CLAVES_IGDB] ("igdb-genero:indie"), con `generos_igdb`.
 * Los demás (un género de Wikidata sin nombre corto) quedan como texto. Sin Android: los nombres los pone la app
 * ([configurar]); sin ellos, las claves se reconocen igual y se muestran tal cual.
 */
object GenerosJuego {
    const val PREFIJO_IGDB = "igdb-genero:"

    /** Las claves de los géneros de IGDB que no están en Wikidata, en el orden de [Igdb.OTROS_GENEROS]. */
    val CLAVES_IGDB: List<String> = listOf("indie", "arcade", "tactical", "point-and-click", "card-board", "moba", "quiz", "pinball")
        .map { PREFIJO_IGDB + it }

    /** Los nombres de un idioma: [wikidata] en el orden de [WikidataJuegos.GENEROS] e [igdb] en el de [CLAVES_IGDB]. */
    data class Nombres(val wikidata: List<String>, val igdb: List<String>)

    /**
     * La clave de un género guardado: él mismo si ya es una clave; si no, la del género que se llama así en IGDB (en
     * inglés) o en alguno de los [idiomas] (sin importar mayúsculas ni tildes). Null si no es ninguno de los conocidos.
     */
    fun clave(guardado: String, vararg idiomas: Nombres): String? {
        val t = guardado.trim()
        if (t in WikidataJuegos.GENEROS || t in CLAVES_IGDB) return t
        val n = Logica.tituloLimpio(t)
        if (n.isEmpty()) return null
        fun en(l: List<String>) = l.indexOfFirst { Logica.tituloLimpio(it) == n }.takeIf { it >= 0 }
        Igdb.COMO_GENERO.entries.firstOrNull { Logica.tituloLimpio(it.key) == n }?.value?.let { return it }
        en(Igdb.OTROS_GENEROS)?.let { return CLAVES_IGDB[it] }
        for (i in idiomas) {
            en(i.wikidata)?.let { idx -> WikidataJuegos.GENEROS.getOrNull(idx)?.let { return it } }
            en(i.igdb)?.let { idx -> CLAVES_IGDB.getOrNull(idx)?.let { return it } }
        }
        return null
    }

    /** Cómo se muestra un género guardado con los nombres de [actual]; uno que no es conocido, como se guardó. */
    fun nombre(guardado: String, actual: Nombres, vararg idiomas: Nombres): String {
        val c = clave(guardado, *idiomas) ?: return guardado
        val i = WikidataJuegos.GENEROS.indexOf(c)
        val n = if (i >= 0) actual.wikidata.getOrNull(i) else actual.igdb.getOrNull(CLAVES_IGDB.indexOf(c))
        return n?.takeIf { it.isNotBlank() } ?: guardado
    }

    /**
     * Los géneros de un juego con fuente pasados a su clave (los guardados con su nombre, en español o en inglés,
     * antes de que hubiera claves), sin repetir. Uno a mano no cambia: sus géneros son lo que escribió la persona.
     */
    fun migrar(o: Obra, vararg idiomas: Nombres): Obra {
        if (o.tipo != Tipos.JUEGO || o.fuente.isEmpty() || o.generos.isEmpty()) return o
        val nuevos = o.generos.map { clave(it, *idiomas) ?: it }.distinct()
        return if (nuevos == o.generos) o else o.copy(generos = nuevos)
    }

    /* ----- Los nombres de la app (los pone CajonApp al arrancar) ----- */

    @Volatile private var es: Nombres? = null
    @Volatile private var en: Nombres? = null
    @Volatile private var lengua: () -> String = { "es" }

    fun configurar(es: Nombres, en: Nombres, lengua: () -> String) {
        this.es = es; this.en = en; this.lengua = lengua
    }

    private fun idiomas(): Array<Nombres> = listOfNotNull(es, en).toTypedArray()

    /** Un género de juego en el idioma de la app. */
    fun visible(g: String): String {
        val actual = (if (lengua() == "en") en else es) ?: return g
        return nombre(g, actual, *idiomas())
    }

    /** Los géneros de [o] como se muestran: los de un juego en el idioma de la app; los demás, como están. */
    fun visibles(o: Obra): List<String> = if (o.tipo != Tipos.JUEGO || o.generos.isEmpty()) o.generos else o.generos.map(::visible).distinct()

    /** [o] con sus géneros de juego pasados a su clave, con los nombres de la app ([migrar]). */
    fun migrar(o: Obra): Obra = migrar(o, *idiomas())
}

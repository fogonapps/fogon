package io.github.fogonapps.cajon.datos

/**
 * Qué va primero en los resultados de una búsqueda: las obras o las personas (artistas, directores, escritores), y
 * cuál es el mejor resultado. Sin interfaz, para poder probarlo.
 *
 * - Si una persona se llama como lo buscado, va en grande, y si además una obra se llama igual, las obras van justo
 *   debajo, antes que las demás personas («Queen», «Selena», «Morat»: la persona y después la obra).
 * - La obra le quita el lugar grande solo si es muy conocida ([muyConocida]) y la persona casi no tiene señal
 *   ([sinSenal]): «Inception» da la película, no uno de los varios grupos con ese nombre.
 * - Una obra clásica ([clasica]: decenas de miles de votos) le quita el lugar también a una persona con poca señal
 *   ([senalMedia]): «Dune» da la película y la novela, no el grupo alemán «Dune».
 * - Si nadie se llama así pero una obra sí (o casi: «Dune / Dune», «Dune: edición de lujo»), las obras
 *   van antes que las personas («Dune»: el libro antes que Kyra Dune).
 * - El título se compara también en su idioma original: en español, «Inception» es «El origen».
 */
object OrdenBusqueda {
    /** Votos de TMDB desde los que una película o serie es muy conocida. */
    const val VOTOS_TMDB = 1000

    /**
     * Calificaciones de Open Library (y de IGDB) desde las que un libro (o un juego) es muy conocido. Proporcional a
     * TMDB: ahí se vota mucho menos (Dune, el libro, tiene unas 460 calificaciones; la película, decenas de miles).
     */
    const val VOTOS_OPEN_LIBRARY = 100

    /**
     * Votos de etiquetas de MusicBrainz hasta los que un artista casi no tiene señal. Es bajo a propósito: en
     * MusicBrainz se etiqueta poco la música latina (Morat tiene 2, Camilo 4, Karol G 9, Selena 19).
     */
    const val ETIQUETAS_SIN_SENAL = 2

    /** Votos de TMDB desde los que una película o serie es clásica (Dune, Avatar, Coco). */
    const val VOTOS_TMDB_CLASICA = 10000

    /** Calificaciones de Open Library (o IGDB) desde las que un libro (o juego) es clásico. */
    const val VOTOS_OPEN_LIBRARY_CLASICA = 1000

    /**
     * Votos de etiquetas de MusicBrainz hasta los que un artista pierde frente a una obra clásica. Por debajo de
     * Selena y Bad Bunny (19), que no comparten nombre con ninguna obra clásica; el grupo alemán «Dune» tiene 13.
     */
    const val ETIQUETAS_SENAL_MEDIA = 15

    /** El orden decidido: [mejorPersona] va en grande arriba (si no es null) y [obrasPrimero] pone las obras antes que las personas. */
    data class Decision(val mejorPersona: ArtistaEncontrado?, val obrasPrimero: Boolean)

    /**
     * [obras]: los resultados por tipo, cada lista de la más a la menos conocida; [personas]: las de cada sección,
     * en el mismo orden. Solo se miran los tres primeros de cada tipo (lo de más abajo ya no es lo buscado).
     */
    fun decidir(texto: String, obras: Collection<List<Encontrado>>, personas: List<ArtistaEncontrado>): Decision {
        val buscado = limpio(texto)
        if (buscado.isEmpty()) return Decision(null, false)
        val palabras = buscado.split(" ").filter { it.isNotBlank() }
        val persona = personas.firstOrNull { limpio(it.nombre) == buscado }
            ?: personas.firstOrNull { a -> palabras.size >= 2 && palabras.all { it in limpio(a.nombre) } }
        val primeras = obras.flatMap { it.take(3) }
        val exactas = primeras.filter { coincide(it, buscado) }
        if (persona == null) return Decision(null, exactas.isNotEmpty())
        if (exactas.isEmpty()) return Decision(persona, false)
        val ganaObra = exactas.any { muyConocida(it) } && sinSenal(persona, personas) ||
            exactas.any { clasica(it) } && senalMedia(persona)
        return Decision(if (ganaObra) null else persona, true)
    }

    /**
     * Cuánto se parece lo mejor de [lista] (sus tres primeros) a lo buscado: 3 si el título es igual (también el
     * original o sin lo que va tras «/», «:» o «(»), 2 si empieza así, 1 si lo contiene, 0 si no; -1 si está vacía.
     */
    fun parecido(texto: String, lista: List<Encontrado>): Int {
        val buscado = limpio(texto)
        return lista.take(3).maxOfOrNull { r ->
            val n = titulos(r).map { limpio(it) }
            when {
                coincide(r, buscado) -> 3
                n.any { it.startsWith(buscado) } -> 2
                n.any { buscado in it } -> 1
                else -> 0
            }
        } ?: -1
    }

    /**
     * En «Todo», el orden de los grupos de obras ([orden]: tipos de obra): primero el que más se parece a lo buscado
     * ([parecido]) y, si dos se parecen igual (la película «Inception» y su banda sonora), el de más votos.
     */
    fun ordenGrupos(texto: String, orden: List<String>, obras: Map<String, List<Encontrado>>): List<String> =
        orden.sortedWith(compareByDescending<String> { parecido(texto, obras[it].orEmpty()) }.thenByDescending { votosExactos(texto, obras[it].orEmpty()) })

    /** Los votos de la obra más votada entre las tres primeras de [lista] que se llaman como lo buscado. */
    fun votosExactos(texto: String, lista: List<Encontrado>): Int {
        val buscado = limpio(texto)
        return lista.take(3).filter { coincide(it, buscado) }.maxOfOrNull { it.votos } ?: 0
    }

    /**
     * Las personas de una sección sin la que ya va en grande ([mejor]): ni ella ni otra con el mismo nombre (la misma
     * artista repetida en la fuente).
     */
    fun sinLaMejor(lista: List<ArtistaEncontrado>, mejor: ArtistaEncontrado?): List<ArtistaEncontrado> =
        if (mejor == null) lista else lista.filter { it.fuente != mejor.fuente && limpio(it.nombre) != limpio(mejor.nombre) }

    /** Si [o] se llama como lo buscado: su título o el original, enteros o sin lo que va tras «/», «:» o «(». */
    private fun coincide(o: Encontrado, buscado: String) = titulos(o).any { limpio(it) == buscado || limpio(base(it)) == buscado }

    private fun titulos(o: Encontrado) = listOf(o.titulo, o.original).filter { it.isNotBlank() }

    /** El título sin su segunda parte: «Dune / Dune» (edición bilingüe), «Dune: edición de lujo», «Dune (Saga)». */
    private fun base(s: String) = s.split(" / ", ":", "(").first()

    /** Si la obra es clásica: muchísimos votos en su fuente (ver [VOTOS_TMDB_CLASICA]). */
    fun clasica(o: Encontrado): Boolean = when {
        o.fuente.startsWith("tmdb:") -> o.votos >= VOTOS_TMDB_CLASICA
        o.fuente.startsWith("ol:") || o.fuente.startsWith("igdb:") -> o.votos >= VOTOS_OPEN_LIBRARY_CLASICA
        else -> false
    }

    /** Si [a] tiene poca señal: ninguna (escritores, directores) o pocas etiquetas en MusicBrainz. */
    fun senalMedia(a: ArtistaEncontrado): Boolean = (a.etiquetas ?: 0) <= ETIQUETAS_SENAL_MEDIA

    /** Si la obra es muy conocida según los votos de su fuente; los discos no traen votos y nunca lo son. */
    fun muyConocida(o: Encontrado): Boolean = when {
        o.fuente.startsWith("tmdb:") -> o.votos >= VOTOS_TMDB
        o.fuente.startsWith("ol:") || o.fuente.startsWith("igdb:") -> o.votos >= VOTOS_OPEN_LIBRARY
        else -> false
    }

    /**
     * Si casi no hay señal de que [a] sea a quien se busca. Escritores y directores no traen popularidad: no tienen
     * señal. Un artista de MusicBrainz, si tiene muy pocas etiquetas y además no es el primero de su fuente o comparte
     * el nombre con otro de su fuente (hay cuatro «Inception»); el primero y único con ese nombre (Morat) sí la tiene.
     */
    fun sinSenal(a: ArtistaEncontrado, personas: List<ArtistaEncontrado>): Boolean {
        val etiquetas = a.etiquetas ?: return true
        if (etiquetas > ETIQUETAS_SIN_SENAL) return false
        val deSuFuente = personas.filter { it.fuente.substringBeforeLast(':') == a.fuente.substringBeforeLast(':') }
        return deSuFuente.firstOrNull() != a || deSuFuente.count { limpio(it.nombre) == limpio(a.nombre) } > 1
    }

    private fun limpio(s: String) = normal(s).trim().replace(Regex("\\s+"), " ")
}

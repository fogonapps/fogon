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

/** Un año en la evolución del perfil: cuánto entró al diario, lo mejor de ese año y su género más visto. */
data class AnioCultural(val anio: Int, val registros: Int, val mejores: List<Obra>, val genero: String?)

/** Las cifras de un año (o de siempre, si [anio] es null). */
data class Cifras(
    val anio: Int?,
    /** Obras terminadas por tipo. */
    val porTipo: Map<String, Int>,
    /** Veces que se registró algo en el diario (cuenta volver a ver). */
    val registros: Int,
    val minutosCine: Int,
    /** Minutos de series y animes terminados (duración del episodio por la cantidad de episodios). */
    val minutosSeries: Int,
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
    fun unir(a: Estado, b: Estado): Estado = unirMigrados(
        Resenas.migrar(a, b.resenas.map { it.id }.toSet()),
        Resenas.migrar(b, a.resenas.map { it.id }.toSet()),
    )

    private fun unirMigrados(a: Estado, b: Estado): Estado = Estado(
        v = maxOf(a.v, b.v),
        listo = a.listo || b.listo,
        apariencia = unirApariencia(a.apariencia, b.apariencia),
        ajustes = if (b.ajustes.cambio > a.ajustes.cambio) b.ajustes else a.ajustes,
        obras = unirPor(a.obras, b.obras, { it.id }, { it.cambio }),
        artistas = unirPor(a.artistas, b.artistas, { it.id }, { it.cambio }),
        listas = unirPor(a.listas, b.listas, { it.id }, { it.cambio }),
        gustos = unirPor(a.gustos, b.gustos, { it.clave }, { it.cambio }),
        perfil = if (b.perfil.cambio > a.perfil.cambio) b.perfil else a.perfil,
        publicaciones = unirPor(a.publicaciones, b.publicaciones, { it.clave }, { it.cambio }),
        resenas = unirPor(a.resenas, b.resenas, { it.id }, { it.cambio }),
        borradoresResena = unirPor(a.borradoresResena, b.borradoresResena, { it.id }, { it.cambio }),
    )

    private fun <T> unirPor(a: List<T>, b: List<T>, id: (T) -> String, cambio: (T) -> Long): List<T> {
        val m = LinkedHashMap<String, T>()
        a.forEach { m[id(it)] = it }
        b.forEach { x -> val y = m[id(x)]; if (y == null || cambio(x) > cambio(y)) m[id(x)] = x }
        return m.values.toList()
    }

    /**
     * Cada obra de afuera va una sola vez en el cajón. Si quedaron dos con la misma fuente (agregada en dos teléfonos,
     * o antes de recuperar la copia), se juntan en la más completa: suma los registros del diario y el me gusta, y
     * las listas y reseñas pasan a ella. La otra queda borrada (así el otro aparato también la quita). La elección no
     * depende del teléfono, para que los dos lleguen a lo mismo.
     */
    fun sinRepetidas(e: Estado): Estado {
        val grupos = e.obras.filter { !it.borrada && it.fuente.isNotEmpty() }.groupBy { it.fuente }.values.filter { it.size > 1 }
        if (grupos.isEmpty()) return e
        val pasa = HashMap<String, String>()
        val nuevas = HashMap<String, Obra>()
        for (g in grupos) {
            val queda = g.sortedWith(compareByDescending<Obra> { it.registros.size }.thenByDescending { it.nota }
                .thenBy { if (it.agregada > 0) it.agregada else Long.MAX_VALUE }.thenBy { it.id }).first()
            val otras = g.filter { it.id != queda.id }
            val marca = g.maxOf { it.cambio } + 1
            nuevas[queda.id] = queda.copy(
                registros = (queda.registros + otras.flatMap { it.registros }).distinctBy { it.id },
                meGusta = g.any { it.meGusta }, nota = if (queda.nota > 0) queda.nota else g.maxOf { it.nota }, cambio = marca)
            otras.forEach { o -> pasa[o.id] = queda.id; nuevas[o.id] = o.copy(borrada = true, cambio = marca) }
        }
        return e.copy(
            obras = e.obras.map { nuevas[it.id] ?: it },
            listas = e.listas.map { l -> if (l.obras.none { it in pasa } && l.portada !in pasa) l else l.copy(obras = l.obras.map { pasa[it] ?: it }.distinct(), portada = pasa[l.portada] ?: l.portada, cambio = maxOf(l.cambio, grupos.maxOf { g -> g.maxOf { it.cambio } } + 1)) },
            resenas = e.resenas.map { r -> pasa[r.obra]?.let { r.copy(obra = it, cambio = r.cambio + 1) } ?: r },
            ajustes = if (e.ajustes.favoritas.none { it in pasa }) e.ajustes else e.ajustes.copy(favoritas = e.ajustes.favoritas.map { pasa[it] ?: it }.distinct()),
        )
    }

    /** Ordena y quita lo repetido (por si un respaldo viejo trae duplicados, o la misma obra entró dos veces). */
    fun normalizar(e: Estado): Estado = Resenas.migrar(sinRepetidas(e).let { e -> e.copy(
        obras = e.obras.distinctBy { it.id }.map(Imagenes::migrar),
        artistas = e.artistas.distinctBy { it.id },
        listas = e.listas.distinctBy { it.id },
        gustos = e.gustos.distinctBy { it.clave },
        publicaciones = e.publicaciones.distinctBy { it.clave }.map { p ->
            if (p.imagenes.isEmpty() && p.imagen.isNotBlank()) p.copy(imagenes = listOf(p.imagen)) else p
        },
        resenas = e.resenas.groupBy { it.id }.values.map { xs -> xs.maxBy { it.cambio } },
        borradoresResena = e.borradoresResena.groupBy { it.id }.values.map { xs -> xs.maxBy { it.cambio } },
        ajustes = e.ajustes.copy(favoritas = e.ajustes.favoritas.distinct().take(4)),
    ) })

    /* ----- El cajón ----- */

    fun filtrar(e: Estado, tipo: String?, estado: String?, texto: String, orden: Orden): List<Obra> {
        val palabras = normal(texto).split(" ").filter { it.isNotBlank() }
        val lista = e.vivas.filter { o ->
            (tipo == null || o.tipo == tipo) && (estado == null || o.estado == estado) &&
                (palabras.isEmpty() || normal("${o.titulo} ${o.original} ${o.creador} ${o.generos.joinToString(" ")} ${o.etiquetas.joinToString(" ")}").let { t -> palabras.all { it in t } })
        }
        return when (orden) {
            // Lo agregado más recientemente primero (por la fecha en que entró al cajón, no por la última vez que se tocó).
            Orden.RECIENTES -> lista.sortedWith(compareByDescending<Obra> { if (it.agregada > 0) it.agregada else it.cambio }.thenBy { normal(it.titulo) })
            Orden.NOTA -> lista.sortedWith(compareByDescending<Obra> { it.nota }.thenBy { normal(it.titulo) })
            Orden.ANIO -> lista.sortedWith(compareByDescending<Obra> { it.anio ?: 0 }.thenBy { normal(it.titulo) })
            Orden.TITULO -> lista.sortedBy { normal(it.titulo) }
        }
    }

    /** La fecha del último registro del diario (o cuándo se terminó). */
    fun ultimaVez(o: Obra): String? = (o.registros.map { it.fecha } + listOfNotNull(o.terminada)).maxOrNull()

    /** Todas las etiquetas del cajón (géneros y etiquetas propias de obras y artistas), las más usadas primero. */
    fun etiquetasDelCajon(e: Estado, tipos: Collection<String>? = null): List<String> =
        (e.vivas.filter { tipos == null || it.tipo in tipos }.flatMap { it.generos + it.etiquetas } +
            (if (tipos == null || Tipos.ALBUM in tipos) e.musicosVivos.flatMap { it.generos } else emptyList()))
            .map { it.trim() }.filter { it.isNotEmpty() }
            .groupBy { tituloLimpio(it) }.values.sortedByDescending { it.size }.map { it.first() }

    /** Si una obra o un artista lleva esa etiqueta (como género o como etiqueta propia). */
    fun tieneEtiqueta(o: Obra, etiqueta: String) = tituloLimpio(etiqueta).let { t -> (o.generos + o.etiquetas).any { tituloLimpio(it) == t } }

    /**
     * Si lo buscado es un género: uno de los del cajón ([delCajon]) o de los comunes ([GENEROS_COMUNES]).
     * Devuelve el género tal como se muestra, o null.
     */
    fun genero(texto: String, delCajon: List<String>): String? {
        val t = tituloLimpio(texto)
        if (t.length < 2) return null
        return (delCajon + GENEROS_COMUNES.keys).firstOrNull { tituloLimpio(it) == t }
    }

    /** Cómo se llama el género en las etiquetas de MusicBrainz (en inglés y en minúsculas). */
    fun etiquetaMusicBrainz(genero: String): String = GENEROS_COMUNES.entries.firstOrNull { tituloLimpio(it.key) == tituloLimpio(genero) }?.value
        ?: tituloLimpio(genero)

    /** Géneros comunes en español y su etiqueta de MusicBrainz. */
    val GENEROS_COMUNES = mapOf(
        "Pop" to "pop", "Rock" to "rock", "Hip Hop" to "hip hop", "Rap" to "hip hop", "R&B" to "rnb", "Jazz" to "jazz",
        "Reggaetón" to "reggaeton", "Trap" to "trap", "Salsa" to "salsa", "Cumbia" to "cumbia", "Bachata" to "bachata",
        "Merengue" to "merengue", "Vallenato" to "vallenato", "Boleros" to "bolero", "Bolero" to "bolero", "Baladas" to "ballad",
        "Pop Latino" to "latin pop", "Rock Latino" to "latin rock", "Rock Alternativo" to "alternative rock", "Pop Rock" to "pop rock",
        "Indie Pop" to "indie pop", "Indie Rock" to "indie rock", "Indie" to "indie", "Electrónica" to "electronic", "House" to "house",
        "Techno" to "techno", "Soul" to "soul", "Neo-Soul" to "neo soul", "Funk" to "funk", "Disco" to "disco", "Reggae" to "reggae",
        "Ska" to "ska", "Punk Rock" to "punk rock", "Punk" to "punk", "Heavy Metal" to "heavy metal", "Metal" to "metal", "Hard Rock" to "hard rock",
        "Blues" to "blues", "Folk" to "folk", "Country" to "country", "K-pop" to "k-pop", "Lo-fi" to "lo-fi", "Trip Hop" to "trip hop",
        "Boom Bap" to "boom bap", "Rap Alternativo" to "alternative hip hop", "Rap Underground" to "underground hip hop",
        "Jazz Rap" to "jazz rap", "Urbano Latino" to "latin urban", "Bossa Nova" to "bossa nova", "Dream Pop" to "dream pop",
        "Shoegaze" to "shoegaze", "Ranchera" to "ranchera", "Regional Mexicano" to "regional mexicano", "Corridos Tumbados" to "corridos tumbados",
        "Música Clásica" to "classical", "Clásica" to "classical", "Soundtrack" to "soundtrack", "Gospel" to "gospel", "Dancehall" to "dancehall",
        "Afrobeats" to "afrobeats", "Drill" to "drill", "Emo" to "emo", "Grunge" to "grunge", "Synthpop" to "synth-pop", "Art Pop" to "art pop",
        "Electropop" to "electropop", "Bedroom Pop" to "bedroom pop", "City Pop" to "city pop", "J-pop" to "j-pop",
    )

    /** Un título para comparar: sin tildes, mayúsculas, signos ni espacios de más. */
    fun tituloLimpio(s: String) = normal(s).replace(Regex("[^a-z0-9]+"), " ").trim()

    /**
     * Si algo encontrado afuera es de verdad esta obra (para completar portadas sin equivocarse): el mismo título
     * (o el original), el año a lo sumo uno de diferencia si los dos lo tienen, en los álbumes el mismo artista y en los
     * libros un autor en común (entonces el año no importa y basta con que un título empiece por el otro).
     */
    /**
     * El título y sus partes, para buscar y comparar: "La La Land: ciudad de sueños" → también "La La Land" y
     * "ciudad de sueños"; "Respuesta a la pregunta: ¿Qué es Ilustración?" → también "¿Qué es Ilustración?". Cada país
     * (y cada quien en sus notas) le pone o le quita el subtítulo. Las partes sueltas, de 4 letras o más.
     */
    fun fragmentos(titulo: String): List<String> {
        val t = titulo.trim()
        val partes = Regex("\\s*(?::| - | – | — )\\s*").split(t).map { it.trim() }.filter { tituloLimpio(it).length >= 4 }
        // Lo que va entre paréntesis: "Road House (2024)" → "Road House"; "Saga of Tanya the Evil (Youjo Senki)" → las dos.
        val sinParentesis = sinParentesis(t).takeIf { it != t && tituloLimpio(it).length >= 4 }
        val dentro = Regex("\\(([^)]*)\\)").findAll(t).map { it.groupValues[1].trim() }.filter { tituloLimpio(it).length >= 4 && !it.all { c -> c.isDigit() } }
        return (listOf(t) + (if (partes.size > 1) partes else emptyList()) + listOfNotNull(sinParentesis) + dentro).distinct()
    }

    private fun sinParentesis(t: String) = t.replace(Regex("\\s*\\([^)]*\\)\\s*"), " ").trim()

    /** El título tal cual, o sin lo que va entre paréntesis: con eso se comparan los títulos alternativos (no con las partes sueltas). */
    private fun completos(t: String) = listOf(tituloLimpio(t), tituloLimpio(sinParentesis(t))).distinct()

    /** Coincide el título (o una parte) con el de afuera o su original, o el título completo con uno de sus alternativos. */
    fun mismoTitulo(o: Obra, r: Encontrado): Boolean {
        val ts = fragmentos(o.titulo).map { tituloLimpio(it) }
        val rt = tituloLimpio(r.titulo); val ro = tituloLimpio(r.detalle)
        return ts.any { it.isNotEmpty() && (it == rt || it == ro) } || r.alias.map { tituloLimpio(it) }.any { it.isNotEmpty() && it in completos(o.titulo) }
    }

    /**
     * Cuál de los que son la misma obra ([esLaMisma]) se elige: título igual, mismo año, mismo tipo y con portada
     * (TMDB tiene entradas sueltas sin año ni póster que repiten el título de otra).
     */
    fun puntaje(o: Obra, r: Encontrado): Int {
        var p = if (mismoTitulo(o, r)) 4 else 2
        if (o.anio != null && r.anio == o.anio) p += 3 else if (o.anio != null && r.anio != null && kotlin.math.abs(r.anio - o.anio) == 1) p += 1
        if (r.tipo == o.tipo) p += 2
        if (r.portada.isNotBlank()) p += 1
        return p
    }

    /**
     * El mismo artista: el nombre igual (sin tildes ni mayúsculas), o igual a uno de sus otros nombres en MusicBrainz,
     * o igual sin el artículo del principio ("Los Enanitos Verdes" y "Enanitos Verdes").
     */
    fun mismoArtista(nombre: String, a: io.github.fogonapps.cajon.datos.ArtistaEncontrado): Boolean {
        fun sinArticulo(s: String) = tituloLimpio(s).replace(Regex("^(los|las|la|el|the|les|le) "), "")
        val n = tituloLimpio(nombre)
        if (n.isEmpty()) return false
        // También con las palabras en otro orden ("Dazai Osamu" y "Osamu Dazai"; "Montaigne, Michel de").
        val palabras = n.split(" ").toSet()
        return (listOf(a.nombre) + a.alias).any { tituloLimpio(it) == n || sinArticulo(it) == sinArticulo(nombre) ||
            palabras.size >= 2 && tituloLimpio(it).split(" ").toSet() == palabras }
    }

    /** Un nombre escrito en otro alfabeto ("Πλάτων", "村上春樹"): no se puede comparar letra por letra. */
    fun otroAlfabeto(s: String) = s.any { it.isLetter() && it.code > 0x24F }

    fun esLaMisma(o: Obra, r0: Encontrado): Boolean {
        val ts = fragmentos(o.titulo).map { tituloLimpio(it) }.filter { it.isNotEmpty() }
        if (ts.isEmpty()) return false
        // Un autor en otro alfabeto no se compara (Open Library guarda a Platón como "Πλάτων"): se toma como sin autor.
        val r = if (otroAlfabeto(r0.creador)) r0.copy(creador = "") else r0
        val mismoAutor = o.tipo == Tipos.LIBRO && autorEnComun(o.creador, r.creador)
        val rt = tituloLimpio(r.titulo)
        val ro = tituloLimpio(r.detalle)
        fun prefijo(a: String, b: String) = minOf(a.length, b.length) >= 4 && (a.startsWith(b) || b.startsWith(a))
        // Casi el mismo título: comparten 3 de cada 4 palabras ("Qué es Ilustración" y "Qué es la Ilustración").
        fun casi(a: String, b: String): Boolean {
            val x = a.split(" ").toSet(); val y = b.split(" ").toSet()
            return x.size >= 2 && x.intersect(y).size.toDouble() / x.union(y).size >= 0.75
        }
        // Cine: si la obra tiene año, lo de afuera también (las entradas sin año de TMDB suelen ser basura).
        if (o.tipo in setOf(Tipos.PELICULA, Tipos.SERIE, Tipos.ANIME) && o.anio != null && r.anio == null) return false
        val exacto = mismoTitulo(o, r)
        // Libros del mismo autor: basta con que un título empiece por el otro ("Frankenstein" y "Frankenstein o el moderno Prometeo") o casi igual.
        val parecido = mismoAutor && ts.any { prefijo(it, rt) || casi(it, rt) }
        // Cine del mismo año: también si un título empieza por el otro o por el original ("Ponyo" y "Ponyo y el
        // secreto de la sirenita"; "Coraline y la puerta secreta" y "Coraline"). Cada país le pone su subtítulo.
        val cine = o.tipo in setOf(Tipos.PELICULA, Tipos.SERIE, Tipos.ANIME) && o.anio != null && o.anio == r.anio &&
            ts.any { t -> listOf(rt, ro).any { x -> prefijo(t, x) } }
        if (!exacto && !parecido && !cine) return false
        // El año, menos en libros del mismo autor (Open Library a veces da el de una edición, no el de la obra).
        if (!mismoAutor && o.anio != null && r.anio != null && kotlin.math.abs(o.anio - r.anio) > 1) return false
        if (o.tipo == Tipos.ALBUM && o.creador.isNotBlank()) {
            val a = tituloLimpio(o.creador); val b = tituloLimpio(r.creador)
            if (b.isEmpty() || !(a in b || b in a)) return false
        }
        // Libros: si los dos traen autor, que compartan al menos un apellido (hay muchos libros con el mismo título).
        if (o.tipo == Tipos.LIBRO && o.creador.isNotBlank() && r.creador.isNotBlank() && !mismoAutor) return false
        return true
    }

    private fun autorEnComun(a: String, b: String): Boolean {
        val x = tituloLimpio(a).split(" ").filter { it.length > 2 }.toSet()
        return x.isNotEmpty() && x.intersect(tituloLimpio(b).split(" ").toSet()).isNotEmpty()
    }

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
        o.tipo == Tipos.LIBRO && (o.paginas ?: 0) > 0 -> o.pagina.toFloat() / o.paginas!!
        else -> null
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
            minutosSeries = terminadas.filter { Tipos.porEpisodios(it.tipo) }.sumOf { (it.minutos ?: 0) * totalEpisodios(it) },
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

    /* ----- El perfil ----- */

    /**
     * Las primeras [n] letras de [t] contando caracteres de verdad y no unidades de UTF-16: un nombre pegado con letras
     * de Unicode (𝔄𝔫𝔤𝔢𝔩, 𝓐𝓷𝓰𝓮𝓵) usa dos unidades por letra, y cortarlo a la mitad dejaría un carácter roto.
     */
    fun recortar(t: String, n: Int): String =
        if (t.codePointCount(0, t.length) <= n) t else t.substring(0, t.offsetByCodePoints(0, n))

    /** El nombre de usuario limpio: minúsculas, letras, números, punto y guion bajo, hasta 30. */
    fun usuarioLimpio(t: String): String = normal(t.trimStart().removePrefix("@")).replace(' ', '_').filter { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '_' }.take(30)

    /** Un enlace del perfil: con "https://" si no lo trae; null si no parece una dirección. */
    fun enlaceLimpio(t: String): String? {
        val x = t.trim().take(200)
        if (x.isEmpty() || ' ' in x) return null
        val u = if (x.startsWith("http://") || x.startsWith("https://")) x else "https://$x"
        val host = u.substringAfter("://").substringBefore('/').substringBefore('?')
        return u.takeIf { '.' in host && host.length >= 4 && !host.startsWith('.') && !host.endsWith('.') }
    }

    /** Cómo se muestra un enlace: sin "https://", sin "www." ni la barra final. */
    fun enlaceCorto(u: String) = u.substringAfter("://").removePrefix("www.").trimEnd('/').take(40)

    /**
     * La evolución: por año (del más reciente al más viejo, hasta [n]), cuántas veces anotó algo en el diario, lo
     * mejor de ese año (por la nota de ese día, después la de la obra y el corazón) y el género que más se repitió.
     */
    fun evolucion(e: Estado, n: Int = 6): List<AnioCultural> {
        val entradas = e.vivas.flatMap { o -> o.registros.mapNotNull { r -> r.fecha.take(4).toIntOrNull()?.let { Triple(it, o, r) } } }
        return entradas.groupBy { it.first }.entries.sortedByDescending { it.key }.take(n).map { (anio, l) ->
            val mejores = l.sortedWith(compareByDescending<Triple<Int, Obra, Registro>> { maxOf(it.third.nota, it.second.nota) }
                .thenByDescending { it.second.meGusta }).map { it.second }.distinctBy { it.id }.take(3)
            val genero = l.flatMap { it.second.generos }.filter { it.isNotBlank() }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            AnioCultural(anio, l.size, mejores, genero)
        }
    }

    /** Descubrimientos recientes: lo que entró a su diario por primera vez en los últimos [dias] y le gustó (nota 8+ o corazón). */
    fun descubrimientos(e: Estado, hoy: LocalDate, dias: Long = 120, n: Int = 8): List<Obra> {
        val desde = hoy.minusDays(dias).toString()
        return e.vivas.filter { o -> (o.nota >= 8 || o.meGusta) && o.registros.isNotEmpty() && o.registros.minOf { it.fecha } >= desde }
            .sortedByDescending { o -> o.registros.minOf { it.fecha } }.take(n)
    }
}

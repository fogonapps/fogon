package io.github.fogonapps.cajon.datos

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Lo que sirve para recomendar, sin internet: cuánto le gustó cada cosa a la persona (su nota, el corazón, sus
 * cuatro favoritas, lo que escucha) y qué géneros le gustan. Las recomendaciones de afuera (TMDB, ListenBrainz,
 * Open Library) se piden desde estos gustos en `CajonViewModel.cargarRecomendaciones`.
 */
object Recomendar {
    /** De 0 (nada) a ~2: la nota (7 o más de 10 medias estrellas), el corazón, las favoritas y lo terminado. */
    fun peso(o: Obra, favoritas: Collection<String> = emptyList()): Double {
        var p = 0.0
        if (o.nota >= 7) p += (o.nota - 6) / 4.0
        if (o.meGusta) p += 0.6
        if (o.id in favoritas) p += 0.6
        if (p == 0.0 && o.estado == Estados.TERMINADO && o.nota == 0) p = 0.15
        if (o.estado == Estados.ABANDONADO || o.nota in 1..4) p = 0.0
        return p
    }

    /** Lo que más le gustó de esos [tipos], lo mejor primero (y a igual gusto, lo más reciente). */
    fun semillas(e: Estado, tipos: Collection<String>, n: Int): List<Pair<Obra, Double>> =
        e.vivas.filter { it.tipo in tipos }.map { it to peso(it, e.ajustes.favoritas) }.filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Obra, Double>> { it.second }.thenByDescending { it.first.cambio }).take(n)

    /**
     * Sus pendientes, de lo más parecido a lo que le gusta (por el perfil: géneros y creadores) a lo menos; sin
     * parecido, no salen, y lo de algo excluido o que no le gusta tampoco.
     */
    fun pendientes(e: Estado, perfil: PerfilGustos = perfil(e, null), n: Int = 12): List<Obra> =
        e.vivas.filter { it.estado == Estados.QUIERO && "obra:${it.fuente}" !in perfil.noRecomendar }.mapNotNull { o ->
            val gs = (o.generos + o.etiquetas).filter { it.isNotBlank() }.distinct()
            if (perfil.excluido(gs, o.creador)) return@mapNotNull null
            val pesos = gs.map { perfil.pesoGenero(it) }
            val x = pesos.sum() / sqrt(gs.size.coerceAtLeast(1).toDouble()) + 0.5 * perfil.pesoPersona(o.creador)
            o to x
        }.filter { it.second > 0 }.sortedByDescending { it.second }.take(n).map { it.first }

    /* ----- El perfil de gustos (desde la 1.13) ----- */

    fun claveGenero(nombre: String) = "genero:" + Logica.tituloLimpio(nombre)
    fun clavePersona(nombre: String) = "artista:" + Logica.tituloLimpio(nombre)

    /** Lo de hace [dias] pesa la mitad cada año, y nunca menos de un cuarto: lo de siempre sigue contando. */
    fun vigencia(dias: Double): Double = 0.5.pow(dias.coerceAtLeast(0.0) / 365.0).coerceAtLeast(0.25)

    private const val DIA = 86_400_000.0
    /** Lo de los últimos 90 días es "de ahora". */
    private const val RECIENTE = 90

    /**
     * El perfil: lo que la persona dijo ([Estado.gustos], declarado) junto con lo que se aprende de su cajón
     * (aprendido: sus notas, corazones, favoritas, lo terminado, lo que escucha; lo de nota baja o abandonado resta),
     * con lo reciente pesando más. Cada gusto dice cuánto pesa (de -1 a ~2), qué tan seguro es (cuántas cosas lo
     * respaldan) y si es de ahora. Lo excluido y lo que no quiere que se le recomiende va aparte y siempre gana.
     */
    fun perfil(e: Estado, oidos: Oidos?, ahora: Long = System.currentTimeMillis()): PerfilGustos {
        class Suma(var nombre: String) { var mas = 0.0; var menos = 0.0; var n = 0; var reciente = 0.0 }
        val generos = HashMap<String, Suma>()
        val personas = HashMap<String, Suma>()
        fun sumar(m: HashMap<String, Suma>, clave: String, nombre: String, x: Double, cuando: Long) {
            if (clave.substringAfter(":").isEmpty() || x == 0.0) return
            val s = m.getOrPut(clave) { Suma(nombre) }
            if (x > 0) s.mas += x else s.menos -= x
            s.n++
            if ((ahora - cuando) / DIA <= RECIENTE && x > 0) s.reciente += x
        }
        // Cuándo fue de verdad: la última fecha del diario, cuándo la terminó o empezó; si no tiene, cuándo la agregó,
        // salvo que ese día haya entrado de golpe (una importación: más de 25 cosas el mismo día), que no dice nada.
        val porDia = e.vivas.filter { it.agregada > 0 }.groupingBy { it.agregada / DIA.toLong() }.eachCount()
        fun fecha(t: String?) = runCatching { java.time.LocalDate.parse(t).toEpochDay() * DIA.toLong() }.getOrNull()
        fun cuandoDe(o: Obra): Long =
            (o.registros.mapNotNull { fecha(it.fecha) } + listOfNotNull(fecha(o.terminada), fecha(o.empezada))).maxOrNull()
                ?: o.agregada.takeIf { it > 0 && (porDia[it / DIA.toLong()] ?: 0) <= 25 } ?: 0L
        e.vivas.forEach { o ->
            val cuando = cuandoDe(o)
            val v = if (cuando > 0) vigencia((ahora - cuando) / DIA) else 0.6
            val malo = o.estado == Estados.ABANDONADO || o.nota in 1..4
            val x = if (malo) -0.6 * v else peso(o, e.ajustes.favoritas) * v
            (o.generos + o.etiquetas).filter { it.isNotBlank() }.distinctBy { Logica.tituloLimpio(it) }.forEach { sumar(generos, claveGenero(it), it, x, cuando) }
            if (o.tipo != Tipos.ALBUM && o.creador.isNotBlank()) o.creador.split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(2)
                .forEach { sumar(personas, clavePersona(it), it, x * 0.6, cuando) }
        }
        // Los músicos: lo de ahora es lo que escucha (la última vez que sonó); sin escuchas, pesa parejo.
        val ultimaVez = oidos?.albumes?.groupBy { it.clave.substringBefore("|") }?.mapValues { (_, l) -> l.maxOf { it.ultima } } ?: emptyMap()
        e.musicosVivos.forEach { a ->
            val cuando = ultimaVez[Logica.tituloLimpio(a.nombre)] ?: 0L
            val x = if (a.nota in 1..4) -0.6 else pesoArtista(a, e, oidos) * (if (cuando > 0) vigencia((ahora - cuando) / DIA) else 0.6)
            sumar(personas, clavePersona(a.nombre), a.nombre, x, cuando)
            a.generos.filter { it.isNotBlank() }.forEach { sumar(generos, claveGenero(it), it, x * 0.5, cuando) }
        }
        // Lo que dijo de obras sueltas: si no le gustó, sus géneros bajan un poco (si no le interesa, menos).
        e.gustosVivos.filter { it.clave.startsWith("obra:") }.forEach { g ->
            val x = when (g.senal) { Senales.NO_GUSTA -> -0.5; Senales.NO_INTERESA -> -0.15; Senales.GUSTA -> 0.8; else -> 0.0 }
            g.generos.filter { it.isNotBlank() }.forEach { sumar(generos, claveGenero(it), it, x, g.cambio) }
            if (g.creador.isNotBlank() && x != 0.0) sumar(personas, clavePersona(g.creador.split(",").first().trim()), g.creador.split(",").first().trim(), x * 0.6, g.cambio)
        }
        val dichos = e.gustosVivos.associateBy { it.clave }
        fun armar(m: HashMap<String, Suma>, prefijo: (String) -> Boolean): List<PesoGusto> {
            val tope = m.values.maxOfOrNull { it.mas - it.menos }?.takeIf { it > 0 } ?: 1.0
            val claves = m.keys + dichos.keys.filter(prefijo)
            return claves.distinct().mapNotNull { k ->
                val s = m[k]
                val dicho = dichos[k]
                val quitado = dicho?.senal == Senales.QUITADO
                val aprendido = if (s == null || quitado) 0.0 else ((s.mas - s.menos) / tope).coerceIn(-1.0, 1.0)
                val declarado = when (dicho?.senal) {
                    Senales.GUSTA -> 1.0; Senales.NO_GUSTA -> -1.0; Senales.NO_INTERESA -> -0.3; Senales.EXCLUIR -> -1.0
                    else -> 0.0
                }
                if (aprendido == 0.0 && declarado == 0.0 && !quitado) return@mapNotNull null
                PesoGusto(k, dicho?.nombre?.takeIf { it.isNotBlank() } ?: s?.nombre ?: k.substringAfter(":"),
                    (declarado + aprendido).coerceIn(-1.0, 2.0),
                    confianza = if (declarado != 0.0) 1.0 else (s?.n ?: 0).let { it / (it + 3.0) },
                    declarado = declarado != 0.0, aprendido = aprendido != 0.0, quitado = quitado,
                    reciente = s != null && s.mas > 0 && s.reciente >= s.mas * 0.4)
            }.sortedByDescending { it.peso }
        }
        val gs = armar(generos) { it.startsWith("genero:") }
        val ps = armar(personas) { it.startsWith("artista:") }
        val vivos = e.gustosVivos
        return PerfilGustos(gs, ps,
            excluidos = vivos.filter { it.senal == Senales.EXCLUIR }.map { it.clave }.toSet(),
            noRecomendar = vivos.filter { it.clave.startsWith("obra:") && it.senal in Senales.NEGATIVAS }.map { it.clave }.toSet(),
            semillas = vivos.filter { it.clave.startsWith("obra:") && it.senal == Senales.GUSTA },
            artistasDichos = vivos.filter { it.clave.startsWith("artista:") && it.senal == Senales.GUSTA },
            categorias = e.ajustes.categorias)
    }

    /** Lo que se recomendó antes: cuántas veces salió, la última vez y si la persona lo abrió (ver `HistorialParaTi`). */
    data class Visto(val veces: Int = 0, val ultima: Long = 0, val abierta: Boolean = false)

    /** Un candidato para "Para ti": [base] es lo que sumó de sus semillas (0 si vino de tendencias o populares). */
    data class Candidato<T>(val cosa: T, val clave: String, val base: Double, val porque: String,
                            val generos: List<String> = emptyList(), val persona: String = "")

    /** Por qué se recomienda algo, para decirlo en la tarjeta. */
    data class Por(val tipo: String, val texto: String = "") {
        companion object {
            const val SEMILLA = "semilla"; const val GENERO = "genero"; const val EXPLORAR = "explorar"; const val POPULAR = "popular"
        }
    }

    /**
     * Elige [n] de los [candidatos]: fuera lo excluido y lo que no quiere; el puntaje suma lo que aportaron sus
     * semillas con sus géneros y su creador según el perfil (un género que no le gusta lo baja mucho); lo que ya
     * salió varias veces sin que lo abriera baja, y si salió 6 veces, descansa 30 días. Después, variedad: cada uno más
     * de la misma semilla vale la mitad, y más de dos del mismo género, un poco menos; y el último lugar es para explorar
     * (lo mejor de un género que todavía no salió), si hay.
     */
    fun <T> elegir(candidatos: List<Candidato<T>>, perfil: PerfilGustos, vistos: Map<String, Visto>, n: Int,
                   ahora: Long = System.currentTimeMillis(), explorar: Boolean = true): List<Pair<T, Por>> {
        val tope = candidatos.maxOfOrNull { it.base }?.takeIf { it > 0 } ?: 1.0
        data class P<T>(val c: Candidato<T>, val puntos: Double)
        val puntuados = candidatos.distinctBy { it.clave }.mapNotNull { c ->
            if (c.clave in perfil.noRecomendar || perfil.excluido(c.generos, c.persona)) return@mapNotNull null
            var x = 0.3 + c.base / tope
            val pesos = c.generos.map { perfil.pesoGenero(it) }
            pesos.filter { it > 0 }.maxOrNull()?.let { x *= 1 + 0.6 * it }
            pesos.filter { it < 0 }.minOrNull()?.let { x *= (1 + it).coerceAtLeast(0.15) }
            val pp = perfil.pesoPersona(c.persona)
            if (pp < 0) x *= 0.3 else if (pp > 0) x *= 1 + 0.3 * pp
            vistos[c.clave]?.let { v ->
                if (!v.abierta && v.veces >= 6 && ahora - v.ultima < 30 * DIA) return@mapNotNull null
                if (!v.abierta && v.veces >= 3) x *= 0.5
            }
            P(c, x)
        }.sortedByDescending { it.puntos }
        val elegidos = mutableListOf<P<T>>()
        val resto = puntuados.toMutableList()
        val lugares = if (explorar && puntuados.size > n) n - 1 else n
        while (elegidos.size < lugares && resto.isNotEmpty()) {
            val mejor = resto.maxBy { p ->
                val misma = elegidos.count { it.c.porque.isNotEmpty() && it.c.porque == p.c.porque }
                val genero = p.c.generos.firstOrNull()?.let { g -> elegidos.count { it.c.generos.firstOrNull() == g } } ?: 0
                p.puntos * 0.5.pow(misma) * 0.85.pow(maxOf(0, genero - 2))
            }
            elegidos += mejor; resto -= mejor
        }
        val vistosGeneros = elegidos.flatMap { it.c.generos }.map { Logica.tituloLimpio(it) }.toSet()
        val exploracion = if (lugares < n) resto.firstOrNull { p ->
            p.c.generos.isNotEmpty() && p.c.generos.none { Logica.tituloLimpio(it) in vistosGeneros } && p.c.generos.none { perfil.pesoGenero(it) < 0 }
        } else null
        return elegidos.map { p ->
            p.c.cosa to when {
                p.c.porque.isNotEmpty() -> Por(Por.SEMILLA, p.c.porque)
                else -> p.c.generos.maxByOrNull { perfil.pesoGenero(it) }?.takeIf { perfil.pesoGenero(it) > 0 }?.let { Por(Por.GENERO, it) } ?: Por(Por.POPULAR)
            }
        } + listOfNotNull(exploracion?.let { it.c.cosa to Por(Por.EXPLORAR) })
    }

    /**
     * Cuánto le gusta un músico: tenerlo guardado ya es un gusto (0,3); más si es favorito, por su nota, sus álbumes
     * bien calificados y lo que lo escucha. Con nota baja (1 a 4 de 10), nada.
     */
    fun pesoArtista(a: Artista, e: Estado, oidos: Oidos?): Double {
        if (a.nota in 1..4) return 0.0
        var p = 0.3
        if (a.favorito) p += 1.0
        if (a.nota >= 7) p += (a.nota - 6) / 4.0
        p += e.vivas.filter { it.artista == a.id }.sumOf { peso(it, e.ajustes.favoritas) } * 0.5
        val clave = Logica.tituloLimpio(a.nombre)
        val veces = oidos?.albumes?.filter { it.clave.substringBefore("|") == clave }?.sumOf { it.veces } ?: 0
        if (veces > 0) p += ln(1.0 + veces) / 3
        return p
    }

    /**
     * Si un libro recomendado de un autor parece uno que ya tiene de ese autor en otro idioma ("Metamorphosis" y "La
     * metamorfosis", "Crime and Punishment" y "Crimen y castigo"): comparten una palabra de 5 letras o más con la
     * misma raíz (las primeras 5). Open Library tiene a veces la misma obra repetida, así que el id no basta.
     */
    fun yaLoTiene(titulo: String, suyos: List<String>): Boolean {
        fun raices(t: String) = Logica.tituloLimpio(t).split(" ").filter { it.length >= 5 }.map { it.take(5) }.toSet()
        val r = raices(titulo)
        return r.isNotEmpty() && suyos.any { raices(it).intersect(r).isNotEmpty() }
    }

    /**
     * Junta lo recomendado desde varias semillas: cada candidato suma el gusto de la semilla por lo arriba que salió
     * (el primero de 20 vale casi el gusto entero), y guarda la semilla que más aportó, para decir "porque te gustó…".
     */
    class Junta<T>(private val clave: (T) -> String) {
        private data class Uno<T>(val cosa: T, var puntos: Double, var porque: String, var mejor: Double)
        private val m = LinkedHashMap<String, Uno<T>>()
        fun sumar(lista: List<T>, peso: Double, porque: String) {
            lista.forEachIndexed { i, x ->
                val aporte = peso * (1.0 - i / (lista.size + 1.0))
                val u = m.getOrPut(clave(x)) { Uno(x, 0.0, porque, 0.0) }
                u.puntos += aporte
                if (aporte > u.mejor) { u.mejor = aporte; u.porque = porque }
            }
        }
        fun mejores(n: Int, fuera: (T) -> Boolean): List<Pair<T, String>> =
            m.values.filter { !fuera(it.cosa) }.sortedByDescending { it.puntos }.take(n).map { it.cosa to it.porque }
        /** Todo lo juntado, con sus puntos, para [elegir]. */
        fun candidatos(clave: (T) -> String, generos: (T) -> List<String> = { emptyList() }, persona: (T) -> String = { "" }, fuera: (T) -> Boolean): List<Candidato<T>> =
            m.values.filter { !fuera(it.cosa) }.map { Candidato(it.cosa, clave(it.cosa), it.puntos, it.porque, generos(it.cosa), persona(it.cosa)) }
    }
}

/** Un gusto del perfil (ver [Recomendar.perfil]). */
data class PesoGusto(
    val clave: String, val nombre: String,
    /** De -1 (no le gusta) a 2 (lo dijo y además se nota). */
    val peso: Double,
    /** De 0 a 1: lo dicho es seguro; lo aprendido, según cuántas cosas lo respaldan. */
    val confianza: Double,
    val declarado: Boolean, val aprendido: Boolean,
    /** Lo aprendido que la persona quitó ("no es así"): no cuenta, pero se muestra para poder deshacerlo. */
    val quitado: Boolean = false,
    /** Buena parte de lo que lo respalda es de los últimos 90 días. */
    val reciente: Boolean = false,
)

/** El perfil de gustos: géneros y personas (músicos, directores, autores) con su peso, y lo que nunca se recomienda. */
data class PerfilGustos(
    val generos: List<PesoGusto> = emptyList(),
    val personas: List<PesoGusto> = emptyList(),
    /** Claves excluidas ("genero:terror", "artista:…"): nada de eso sale. */
    val excluidos: Set<String> = emptySet(),
    /** Obras que no se recomiendan ("obra:<fuente>"). */
    val noRecomendar: Set<String> = emptySet(),
    /** Obras y artistas que dijo que le gustan (al elegir sus gustos): se piden sus parecidos. */
    val semillas: List<Gusto> = emptyList(),
    val artistasDichos: List<Gusto> = emptyList(),
    val categorias: List<String> = emptyList(),
) {
    private val porGenero = generos.filter { !it.quitado }.associate { it.clave to it.peso }
    private val porPersona = personas.filter { !it.quitado }.associate { it.clave to it.peso }
    fun pesoGenero(nombre: String) = porGenero[Recomendar.claveGenero(nombre)] ?: 0.0
    fun pesoPersona(nombre: String) = nombre.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        .maxOfOrNull { porPersona[Recomendar.clavePersona(it)] ?: 0.0 } ?: 0.0
    fun excluido(generos: List<String>, persona: String) =
        generos.any { Recomendar.claveGenero(it) in excluidos } ||
            persona.split(",").map { it.trim() }.filter { it.isNotEmpty() }.any { Recomendar.clavePersona(it) in excluidos }
    /** Si quiere esa categoría en "Para ti" (sin elegir ninguna, todas). */
    fun quiere(tipo: String) = categorias.isEmpty() || tipo in categorias
}

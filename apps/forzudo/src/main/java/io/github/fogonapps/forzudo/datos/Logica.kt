package io.github.fogonapps.forzudo.datos

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/*
 * Lógica de El Forzudo (antes Mi Gym), traducida función por función de la versión web (index.html).
 * Todo es puro: recibe el estado y la fecha de hoy, y no toca la pantalla ni el disco.
 */

/* ----- Fechas ----- */

fun fecha(s: String): LocalDate = LocalDate.parse(s)
fun iso(d: LocalDate): String = d.toString()

/** Lunes de la semana de esa fecha. */
fun inicioSemana(s: String): String {
    val d = fecha(s)
    return iso(d.minusDays((d.dayOfWeek.value - 1).toLong()))
}

/** Los lunes de un mes "YYYY-MM". */
fun semanasDelMes(mes: String): List<String> {
    var d = LocalDate.parse("$mes-01")
    while (d.dayOfWeek.value != 1) d = d.plusDays(1)
    val out = mutableListOf<String>()
    while (iso(d).startsWith(mes)) { out += iso(d); d = d.plusDays(7) }
    return out
}

fun etiquetaSemana(ws: String): String {
    val a = fecha(ws); val b = a.plusDays(6)
    return "${a.dayOfMonth}–${b.dayOfMonth} ${Vocabulario.actual.meses[b.monthValue - 1]}"
}

fun fechaCorta(s: String): String { val d = fecha(s); return "${d.dayOfMonth} ${Vocabulario.actual.meses[d.monthValue - 1]}" }

/* ----- Formatos ----- */

/** Número con máximo 2 decimales y el separador decimal del idioma (como fk() en la web, que usa coma). */
fun fk(n: Double?): String {
    if (n == null) return "–"
    val r = Math.round(n * 100) / 100.0
    val s = if (r == floor(r)) r.toLong().toString() else r.toString()
    return s.replace('.', Vocabulario.actual.decimal)
}

fun fmtVol(n: Double): String = "%,d kg".format(Vocabulario.actual.numeros, n.roundToLong())

fun duracion(ms: Long): String {
    val s = max(0, ms / 1000); val h = s / 3600; val m = s % 3600 / 60; val x = s % 60
    return if (h > 0) "${h}h ${m}min" else "$m:${x.toString().padStart(2, '0')}"
}

fun duracionPalabras(ms: Long): String {
    val m = Math.round(ms / 60000.0)
    return if (m >= 60) "${m / 60}h ${m % 60}min" else "$m min"
}

/** Lee un número escrito con coma o punto. */
fun numero(t: String?): Double? = t?.trim()?.takeIf { it.isNotEmpty() }?.replace(",", ".")?.toDoubleOrNull()

/** Máximo estimado de una repetición (Epley). */
fun e1rmDe(kg: Double, reps: Double) = kg * (1 + reps / 30)

/* ----- Rangos ----- */

data class Rango(val tier: Int, val div: String, val nombre: String, val color: String, val s: Double)

data class MejorSerie(val v: Double, val kg: Double, val reps: Double)

data class RangoEjercicio(
    val rango: Rango, val e1: Double, val mejor: MejorSerie, val T: List<Double>, val pct: Double, val std: Estandar,
)

data class Meta(val kg: Double, val reps: Int, val rango: String, val id: String = "", val inc: Double = 0.0)

data class RangoMusculo(val rango: Rango, val lista: List<Pair<String, RangoEjercicio>>, val consejo: Meta?)

data class RangoGeneral(val rango: Rango, val cuenta: Int)

data class Racha(val actual: Int, val mejor: Int)

data class Alternativas(val variantes: List<Ejercicio>, val relacionados: List<Ejercicio>)

class Logica(val b: Biblioteca) {

    fun dias(e: Estado): List<Dia> = b.dias(e.routines ?: b.programas.first().routines)

    fun diaDeHoy(dias: List<Dia>, hoy: LocalDate): String? {
        val g = hoy.dayOfWeek.value % 7 // domingo = 0, como getDay()
        return dias.firstOrNull { it.dow == g }?.id
    }

    fun tituloRutina(s: Sesion, dias: List<Dia>): String =
        s.title ?: dias.firstOrNull { it.id == s.day }?.title ?: Vocabulario.actual.entreno

    /* Semanas y constancia */

    fun semanaAuto(e: Estado, ws: String, dias: List<Dia>): Boolean =
        e.sessions.filter { inicioSemana(it.date) == ws }.map { it.day }.toSet().size >= max(1, dias.size)

    /**
     * Una semana cuenta solo si de verdad completaste todas las rutinas del programa.
     * (La versión web dejaba marcarlas a mano; esas marcas se conservan en los datos, pero ya no cuentan.)
     */
    fun semanaHecha(e: Estado, ws: String, dias: List<Dia>): Boolean = semanaAuto(e, ws, dias)

    fun racha(e: Estado, dias: List<Dia>, hoy: LocalDate): Racha {
        if (e.sessions.isEmpty()) return Racha(0, 0)
        val primero = inicioSemana(e.sessions.minOf { it.date })
        val ahora = inicioSemana(iso(hoy))
        val semanas = mutableListOf<String>()
        var d = fecha(primero)
        while (iso(d) <= ahora) { semanas += iso(d); d = d.plusDays(7) }
        var mejor = 0; var corrida = 0
        semanas.forEach { w ->
            if (semanaHecha(e, w, dias)) { corrida++; mejor = max(mejor, corrida) } else if (w != ahora) corrida = 0
        }
        var actual = 0
        for (i in semanas.indices.reversed()) {
            val w = semanas[i]
            if (semanaHecha(e, w, dias)) actual++ else if (w == ahora) continue else break
        }
        return Racha(actual, mejor)
    }

    /* Historial por ejercicio */

    fun anterior(e: Estado, id: String): List<Serie>? =
        e.sessions.filter { s -> s.entries[id]?.any { it.kg != null || it.reps != null } == true }
            .sortedByDescending { it.date }.firstOrNull()?.entries?.get(id)

    fun maxKg(e: Estado, id: String): Double? =
        e.sessions.flatMap { it.entries[id].orEmpty() }.mapNotNull { it.kg }.maxOrNull()

    fun maxReps(e: Estado, id: String): Double? =
        e.sessions.flatMap { it.entries[id].orEmpty() }.mapNotNull { it.reps }.maxOrNull()

    fun unidad(ej: Ejercicio) = if (ej.kind == "s") Vocabulario.actual.seg else Vocabulario.actual.reps

    /** Meta de la serie y el consejo (lo que va en negrita en la web). */
    fun meta(e: Estado, c: EjercicioCfg): Pair<String, String?> {
        val p = anterior(e, c.id)
        val r = "${c.lo}" + if (c.hi != c.lo) "–${c.hi}" else ""
        val v = Vocabulario.actual
        var t: String? = null
        if (p != null) {
            val reps = p.mapNotNull { it.reps }
            val kg = p.firstOrNull { it.kg != null }?.kg
            t = when {
                reps.isEmpty() -> v.mismoPeso
                reps.size >= c.sets && reps.all { it >= c.hi } -> when (c.e.kind) {
                    "w" -> v.subePeso
                    "s" -> v.mas10s
                    else -> v.masLento
                }
                reps.any { it < c.lo } -> v.manten.format(fk(kg))
                else -> v.masUnaRep
            }
        }
        return v.meta.format(c.sets, r, unidad(c.e)) to t
    }

    /* Rangos de fuerza */

    fun e1rm(kg: Double, reps: Double) = kg * (1 + reps / 30)

    fun pesoCorporal(e: Estado): Double? = e.body.sortedBy { it.date }.lastOrNull()?.kg

    fun mejorE1(id: String, sesiones: List<Sesion>): MejorSerie? {
        var mejor: MejorSerie? = null
        sesiones.forEach { s ->
            s.entries[id].orEmpty().forEach { x ->
                val kg = x.kg; val reps = x.reps
                if (kg != null && reps != null && kg > 0 && reps > 0 && reps <= 20) {
                    val v = e1rm(kg, reps)
                    if (mejor == null || v > mejor!!.v) mejor = MejorSerie(v, kg, reps)
                }
            }
        }
        return mejor
    }

    fun umbrales(std: Estandar, bw: Double) = std.t.map { it * bw / b.estandares.refBw }

    fun puntaje(x: Double, T: List<Double>): Double {
        if (x < T[0]) return max(0.0, x / T[0])
        for (k in 0 until 4) if (x < T[k + 1]) return 1 + k + (x - T[k]) / (T[k + 1] - T[k])
        return min(5.99, 5 + (x - T[4]) / (T[4] - T[3]))
    }

    fun xDePuntaje(s: Double, T: List<Double>): Double {
        if (s <= 1) return s * T[0]
        val j = floor(s).toInt()
        if (j >= 5) return T[4]
        return T[j - 1] + (s - j) * (T[j] - T[j - 1])
    }

    fun percentil(x: Double, T: List<Double>): Double {
        val p = listOf(0.0 to 0.0, T[0] to 5.0, T[1] to 20.0, T[2] to 50.0, T[3] to 80.0, T[4] to 95.0)
        if (x >= T[4]) return min(99.0, 95 + 4 * (x - T[4]) / (T[4] - T[3]))
        for (i in 1 until p.size) {
            if (x < p[i].first) return p[i - 1].second + (x - p[i - 1].first) / (p[i].first - p[i - 1].first) * (p[i].second - p[i - 1].second)
        }
        return 95.0
    }

    fun rango(s: Double): Rango {
        val tier = min(5, floor(s).toInt())
        val div = if (tier >= 5) "" else listOf("III", "II", "I")[min(2, floor((s - tier) * 3).toInt())]
        val n = b.niveles[tier]
        return Rango(tier, div, n.n + if (div.isNotEmpty()) " $div" else "", n.c, s)
    }

    fun progresoDivision(s: Double): Double {
        if (s >= 5) return 1.0
        val f = (s - floor(s)) * 3
        return f - floor(f)
    }

    fun siguienteLimite(s: Double): Double? {
        if (s >= 5) return null
        val t = floor(s); val f = s - t
        for (st in listOf(1.0 / 3, 2.0 / 3, 1.0)) if (f < st - 1e-9) return t + st
        return t + 1
    }

    fun rangoEjercicio(e: Estado, id: String, sesiones: List<Sesion> = e.sessions): RangoEjercicio? {
        val std = b.estandarDe(id) ?: return null
        val bw = pesoCorporal(e) ?: return null
        val m = mejorE1(id, sesiones) ?: return null
        val T = umbrales(std, bw)
        val s = puntaje(m.v, T)
        return RangoEjercicio(rango(s), m.v, m, T, percentil(m.v, T), std)
    }

    fun redondearArriba(w: Double, mancuerna: Boolean): Double {
        val st = if (mancuerna) 0.5 else 2.5
        return ceil((w - 1e-9) / st) * st
    }

    fun siguienteMeta(id: String, r: RangoEjercicio?): Meta? {
        if (r == null) return null
        val nb = siguienteLimite(r.rango.s) ?: return null
        val need = xDePuntaje(nb, r.T)
        val reps = b.ex.getValue(id).lo
        return Meta(redondearArriba(need / (1 + reps / 30.0), r.std.mancuerna), reps, rango(nb + 1e-6).nombre)
    }

    fun rangoMusculo(e: Estado, m: MusculoCfg, sesiones: List<Sesion> = e.sessions): RangoMusculo? {
        val visto = LinkedHashMap<String, Pair<String, RangoEjercicio>>()
        m.ejercicios.forEach { id ->
            val r = rangoEjercicio(e, id, sesiones) ?: return@forEach
            val k = b.ex[id]?.std ?: id
            val prev = visto[k]
            if (prev == null || r.rango.s > prev.second.rango.s) visto[k] = id to r
        }
        val lista = visto.values.sortedByDescending { it.second.rango.s }
        if (lista.isEmpty()) return null
        val top = lista.take(2)
        val s = top.sumOf { it.second.rango.s } / top.size
        var consejo: Meta? = null
        lista.forEach { (id, r) ->
            val nt = siguienteMeta(id, r) ?: return@forEach
            val inc = nt.kg / (r.mejor.kg.takeIf { it != 0.0 } ?: 1.0)
            if (consejo == null || inc < consejo.inc) consejo = nt.copy(id = id, inc = inc)
        }
        return RangoMusculo(rango(s), lista, consejo)
    }

    fun rangoGeneral(e: Estado, sesiones: List<Sesion> = e.sessions): RangoGeneral? {
        val ms = b.musculos.mapNotNull { rangoMusculo(e, it, sesiones) }
        if (ms.isEmpty()) return null
        return RangoGeneral(rango(ms.sumOf { it.rango.s } / ms.size), ms.size)
    }

    fun seriesSemana(e: Estado, hoy: LocalDate): Map<String, Int> {
        val ws = inicioSemana(iso(hoy)); val c = mutableMapOf<String, Int>()
        fun suma(id: String, n: Int) {
            val m = b.musculos.firstOrNull { id in it.ejercicios } ?: return
            if (n > 0) c[m.id] = (c[m.id] ?: 0) + n
        }
        e.sessions.filter { inicioSemana(it.date) == ws }.forEach { s -> s.entries.forEach { (k, a) -> suma(k, a.size) } }
        e.active?.let { a -> if (inicioSemana(a.date) == ws) a.entries.forEach { (k, l) -> suma(k, l.count { it.done }) } }
        return c
    }

    /* Estancamiento y alternativas */

    private fun e1Sesion(s: Sesion, id: String): Double {
        var mejor = 0.0
        s.entries[id].orEmpty().forEach { x ->
            val kg = x.kg ?: 0.0; val reps = x.reps ?: 0.0
            if (kg > 0 && reps > 0) mejor = max(mejor, e1rm(kg, reps)) else if (reps > 0) mejor = max(mejor, reps)
        }
        return mejor
    }

    fun estancado(e: Estado, id: String): Boolean {
        val ss = e.sessions.filter { it.entries.containsKey(id) }.sortedBy { it.date }.map { e1Sesion(it, id) }.filter { it > 0 }
        if (ss.size < 4) return false
        val antes = ss.dropLast(3).max()
        return ss.takeLast(3).max() <= antes * 1.005
    }

    fun alternativas(id: String): Alternativas {
        val e = b.ex.getValue(id)
        val todos = b.ejercicios.filter { it.id != id && it.id != "press-banca-ligero" }
        return Alternativas(todos.filter { it.f == e.f }, todos.filter { it.m == e.m && it.f != e.f })
    }

    /* Volumen */

    fun volumen(entries: Map<String, List<Serie>>): Double =
        entries.values.sumOf { a -> a.filter { it.done && it.kg != null && it.reps != null }.sumOf { it.kg!! * it.reps!! } }

    fun series(entries: Map<String, List<Serie>>): Int = entries.values.sumOf { a -> a.count { it.done } }

    /* Carga, migraciones y unión con la nube */

    /** Arregla un estado recién leído (disco, Gist o respaldo). Solo agrega, nunca borra datos. */
    fun normalizar(e0: Estado): Estado {
        var e = e0
        if (e.active != null && e.active.start == 0L) e = e.copy(active = null)
        if (e.routines == null) e = e.copy(routines = b.programas.first().routines, program = b.programas.first().key)
        val rutinas = e.routines!!
        e = e.copy(sessions = e.sessions.map { s ->
            if (s.title == null) rutinas.firstOrNull { it.id == s.day }?.let { s.copy(title = it.title) } ?: s else s
        })
        e.weeks?.let { viejas ->
            val marcas = e.weekMarks.toMutableMap()
            viejas.forEach { (mk, arr) ->
                val w = semanasDelMes(mk)
                arr.forEachIndexed { i, v -> if (v == true && i < w.size && !marcas.containsKey(w[i])) marcas[w[i]] = true }
            }
            e = e.copy(weekMarks = marcas, weeks = null)
        }
        return e
    }

    /** Une el estado del teléfono (a) con el de la nube (b), igual que mergeState() en la web. */
    fun unir(a: Estado, bb: Estado): Estado {
        val borrados = (a.deleted + bb.deleted).toCollection(LinkedHashSet())
        val nuevo = if (a.updatedAt >= bb.updatedAt) a else bb
        val viejo = if (nuevo === a) bb else a
        val ses = LinkedHashMap<String, Sesion>()
        (viejo.sessions + nuevo.sessions).forEach { if (it.id !in borrados) ses[it.id] = it }
        val cuerpo = LinkedHashMap<String, PesoCorporal>()
        (viejo.body + nuevo.body).forEach { cuerpo[it.date] = it }
        return nuevo.copy(
            v = 2,
            sessions = ses.values.toList(),
            body = cuerpo.values.toList(),
            weekMarks = viejo.weekMarks + nuevo.weekMarks,
            deleted = borrados.toList(),
            active = a.active,
            updatedAt = max(a.updatedAt, bb.updatedAt),
            nombre = a.nombre ?: bb.nombre,
        )
    }

    /** Lo que se sube a la nube o se exporta: todo menos el entreno en curso. */
    fun paraNube(e: Estado): Estado = e.copy(active = null)

    fun diasEntre(a: String, b: String) = ChronoUnit.DAYS.between(fecha(a), fecha(b))
}

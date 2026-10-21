package io.github.fogonapps.forzudo.datos

import kotlinx.serialization.decodeFromString

/** Ejercicio ya combinado con la configuración de la rutina (series, reps, descanso). */
data class EjercicioCfg(val e: Ejercicio, val sets: Int, val lo: Int, val hi: Int, val rest: Int) {
    val id get() = e.id
}

/** Rutina con día y ejercicios resueltos. */
data class Dia(val rutina: Rutina, val nombreDia: String, val ejercicios: List<EjercicioCfg>) {
    val id get() = rutina.id
    val title get() = rutina.title
    val dow get() = rutina.dow
}

data class MusculoCfg(val id: String, val n: String, val ejercicios: List<String>)

class Biblioteca(
    val ejercicios: List<Ejercicio>,
    val programas: List<Programa>,
    val estandares: Estandares,
    val niveles: List<Nivel>,
    musculos: List<Musculo>,
    val cuerpo: ModeloCuerpo,
    val laminas: Set<String>,
) {
    val ex: Map<String, Ejercicio> = ejercicios.associateBy { it.id }
    val musculos: List<MusculoCfg> = musculos.map { m -> MusculoCfg(m.id, m.n, ejercicios.filter { it.m == m.id }.map { it.id }) }

    fun estandarDe(id: String): Estandar? = ex[id]?.std?.let { estandares.std[it] }
    fun nombreMusculo(id: String): String = musculos.firstOrNull { it.id == id }?.n ?: ""

    fun cfg(it: Item): EjercicioCfg {
        val b = ex[it.id] ?: Ejercicio(it.id, it.id, "pecho", "", "", "", 8, 12, 90, "", "w")
        return EjercicioCfg(b, it.sets ?: 3, it.lo ?: b.lo, it.hi ?: b.hi, it.rest ?: b.rest)
    }

    fun dias(rutinas: List<Rutina>): List<Dia> =
        rutinas.map { r -> Dia(r, r.dow?.let { Vocabulario.actual.diasLargos[it] } ?: "", r.items.map(::cfg)) }

    /** Ruta de la lámina (i = 0 inicio, 1 final) dentro de assets, o null si el ejercicio no tiene. */
    fun foto(id: String, i: Int): String? = if (id in laminas) "laminas/$id-${i + 1}.webp" else null

    companion object {
        /**
         * Carga la biblioteca a partir de una función que lee archivos de assets/datos.
         * Con [idioma] distinto de "es" pone encima las traducciones de `datos/<idioma>/textos.json`
         * (nombres, técnica, equipo, músculos, rangos y programas); lo que no esté traducido queda en español.
         */
        fun cargar(idioma: String = "es", leer: (String) -> String): Biblioteca {
            var ejercicios = JsonGym.decodeFromString<List<Ejercicio>>(leer("ejercicios.json"))
            var programas = JsonGym.decodeFromString<List<Programa>>(leer("programas.json"))
            var niveles = JsonGym.decodeFromString<List<Nivel>>(leer("rangos.json"))
            var musculos = JsonGym.decodeFromString<List<Musculo>>(leer("musculos.json"))
            val t = if (idioma == "es") null else runCatching { JsonGym.decodeFromString<Traduccion>(leer("$idioma/textos.json")) }.getOrNull()
            if (t != null) {
                ejercicios = ejercicios.map { e ->
                    val x = t.ejercicios[e.id]
                    e.copy(n = x?.n ?: e.n, cue = x?.cue ?: e.cue, eq = t.equipos[e.eq] ?: e.eq)
                }
                programas = programas.map { p ->
                    val x = t.programas[p.key] ?: return@map p
                    p.copy(name = x.name ?: p.name, level = x.level ?: p.level, goal = x.goal ?: p.goal, desc = x.desc ?: p.desc,
                        routines = p.routines.map { r -> r.copy(title = x.rutinas[r.id] ?: r.title) })
                }
                niveles = niveles.mapIndexed { i, n -> n.copy(n = t.rangos.getOrNull(i) ?: n.n) }
                musculos = musculos.map { m -> m.copy(n = t.musculos[m.id] ?: m.n) }
            }
            return Biblioteca(
                ejercicios = ejercicios,
                programas = programas,
                estandares = JsonGym.decodeFromString(leer("estandares.json")),
                niveles = niveles,
                musculos = musculos,
                cuerpo = JsonGym.decodeFromString(leer("modelo-cuerpo.json")),
                laminas = JsonGym.decodeFromString<List<String>>(leer("laminas.json")).toSet(),
            )
        }
    }
}

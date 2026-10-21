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
        rutinas.map { r -> Dia(r, r.dow?.let { DIAS_LARGOS[it] } ?: "", r.items.map(::cfg)) }

    /** Ruta de la lámina (i = 0 inicio, 1 final) dentro de assets, o null si el ejercicio no tiene. */
    fun foto(id: String, i: Int): String? = if (id in laminas) "laminas/$id-${i + 1}.webp" else null

    companion object {
        val DIAS_CORTOS = listOf("D", "L", "M", "X", "J", "V", "S")
        val DIAS_LARGOS = listOf("Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado")
        val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
        val MESES_LARGOS = listOf(
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
        )

        /** Carga la biblioteca a partir de una función que lee archivos de assets/datos. */
        fun cargar(leer: (String) -> String): Biblioteca = Biblioteca(
            ejercicios = JsonGym.decodeFromString(leer("ejercicios.json")),
            programas = JsonGym.decodeFromString(leer("programas.json")),
            estandares = JsonGym.decodeFromString(leer("estandares.json")),
            niveles = JsonGym.decodeFromString(leer("rangos.json")),
            musculos = JsonGym.decodeFromString(leer("musculos.json")),
            cuerpo = JsonGym.decodeFromString(leer("modelo-cuerpo.json")),
            laminas = JsonGym.decodeFromString<List<String>>(leer("laminas.json")).toSet(),
        )
    }
}

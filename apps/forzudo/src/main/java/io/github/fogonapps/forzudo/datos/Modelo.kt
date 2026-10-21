package io.github.fogonapps.forzudo.datos

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * Modelo de datos de El Forzudo. Es el mismo formato JSON (v2) de la versión web,
 * así el Gist y los respaldos viejos se pueden leer sin conversión.
 * Regla: nunca renombrar ni borrar ids de ejercicios; las migraciones son solo aditivas.
 */

@Serializable
data class Serie(val kg: Double? = null, val reps: Double? = null, val done: Boolean = false)

@Serializable
data class Sesion(
    val id: String,
    val date: String,
    val day: String? = null,
    val title: String? = null,
    val ms: Long? = null,
    val entries: Map<String, List<Serie>> = emptyMap(),
)

@Serializable
data class PesoCorporal(val date: String, val kg: Double)

@Serializable
data class Item(val id: String, val sets: Int? = null, val lo: Int? = null, val hi: Int? = null, val rest: Int? = null)

@Serializable
data class Rutina(
    val id: String,
    val title: String,
    val dow: Int? = null,
    val light: Boolean? = null,
    val items: List<Item> = emptyList(),
)

@Serializable
data class EntrenoActivo(
    val id: String,
    val date: String,
    val day: String,
    val title: String? = null,
    val start: Long = 0,
    val items: List<Item> = emptyList(),
    val entries: Map<String, List<Serie>> = emptyMap(),
)

@Serializable
data class Estado(
    val v: Int = 2,
    val updatedAt: Long = 0,
    val sessions: List<Sesion> = emptyList(),
    val body: List<PesoCorporal> = emptyList(),
    val weekMarks: Map<String, Boolean> = emptyMap(),
    val deleted: List<String> = emptyList(),
    val routines: List<Rutina>? = null,
    val program: String? = null,
    val progEdited: Boolean = false,
    val active: EntrenoActivo? = null,
    /** Formato viejo (antes de weekMarks). Se convierte al cargar. */
    val weeks: Map<String, List<Boolean?>>? = null,
    /** Nombre de la persona. Nuevo en la versión Fogón; la web no lo tenía. */
    val nombre: String? = null,
)

val JsonGym = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
}

/* ----- Biblioteca (assets/datos) ----- */

@Serializable
data class Ejercicio(
    val id: String,
    val n: String,
    val g: String,
    val m: String,
    val f: String,
    val eq: String,
    val lo: Int,
    val hi: Int,
    val rest: Int,
    val cue: String,
    val kind: String,
    val std: String? = null,
)

@Serializable
data class Programa(
    val key: String,
    val name: String,
    val level: String,
    val goal: String,
    val desc: String,
    val routines: List<Rutina>,
)

@Serializable
data class Estandar(val t: List<Double>, val db: Int? = null, val m: Int? = null, val ap: Int? = null) {
    val mancuerna get() = db == 1
    val maquina get() = m == 1
    val aproximado get() = ap == 1
}

@Serializable
data class Estandares(val refBw: Double, val std: Map<String, Estandar>)

@Serializable
data class Nivel(val n: String, val c: String)

@Serializable
data class Musculo(val id: String, val n: String)

@Serializable
data class ParteCuerpo(val g: String? = null, val p: List<String>)

@Serializable
data class ModeloCuerpo(val front: List<ParteCuerpo>, val back: List<ParteCuerpo>)

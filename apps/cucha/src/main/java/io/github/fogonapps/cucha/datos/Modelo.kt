package io.github.fogonapps.cucha.datos

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * Modelo de datos de La Cucha. Todo se guarda en un JSON (datastore/estado.json).
 * Regla: las migraciones son solo aditivas (campos nuevos con valor por defecto); los ids de alimentos
 * de la base (assets/datos/alimentos.json) no se cambian, porque lo anotado los usa.
 */

/** Calorías y macros. En la base van por 100 g; en lo anotado, por lo que se comió. */
@Serializable
data class Nutrientes(val kcal: Double = 0.0, val proteina: Double = 0.0, val carbos: Double = 0.0, val grasa: Double = 0.0) {
    operator fun plus(o: Nutrientes) = Nutrientes(kcal + o.kcal, proteina + o.proteina, carbos + o.carbos, grasa + o.grasa)
    operator fun times(f: Double) = Nutrientes(kcal * f, proteina * f, carbos * f, grasa * f)
    operator fun div(f: Double) = if (f == 0.0) this else times(1 / f)
}

fun List<Nutrientes>.suma() = fold(Nutrientes()) { a, b -> a + b }

/** Una porción casera: "Taza", "Arepa mediana"… y cuántos gramos pesa (aproximado). */
@Serializable
data class Porcion(val es: String, val en: String, val gramos: Double) {
    fun nombre(idioma: String) = if (idioma == "en") en else es
}

/** Un alimento de la base (assets/datos/alimentos.json), con sus valores de USDA por 100 g. */
@Serializable
data class Alimento(
    val id: String,
    val es: String,
    val en: String,
    val grupo: String,
    val fdc: Long,
    val usda: String,
    val aprox: Boolean = false,
    val k: Double,
    val p: Double,
    val c: Double,
    val g: Double,
    val fi: Double = 0.0,
    val porciones: List<Porcion> = emptyList(),
) {
    val por100 get() = Nutrientes(k, p, c, g)
    fun nombre(idioma: String) = if (idioma == "en") en else es
}

/** Un producto propio: escaneado en Open Food Facts o escrito a mano. Valores por 100 g. */
@Serializable
data class Producto(
    val id: String,
    val nombre: String,
    val por100: Nutrientes,
    val codigo: String? = null,
    val porcionGramos: Double? = null,
    /** "off" si vino de Open Food Facts (se muestra el crédito), "propio" si lo escribió la persona. */
    val fuente: String = "propio",
)

@Serializable
data class Ingrediente(val alimento: String, val gramos: Double)

/**
 * Una receta. Las de ejemplo vienen en assets/datos/recetas.json; las propias se guardan en el estado
 * ([propia]). Los valores salen de los ingredientes; [porPorcion] los fija a mano (para recetas
 * importadas que solo traen las calorías y la proteína).
 */
@Serializable
data class Receta(
    val id: String,
    val es: String,
    val en: String = "",
    val comidas: List<String> = emptyList(),
    val porciones: Int = 1,
    val minutos: Int = 0,
    val ingredientes: List<Ingrediente> = emptyList(),
    val pasosEs: List<String> = emptyList(),
    val pasosEn: List<String> = emptyList(),
    val propia: Boolean = false,
    val porPorcion: Nutrientes? = null,
    val nota: String = "",
) {
    fun nombre(idioma: String) = if (idioma == "en" && en.isNotBlank()) en else es
    fun pasos(idioma: String) = if (idioma == "en" && pasosEn.isNotEmpty()) pasosEn else pasosEs
}

/**
 * Algo que se comió. [ref] dice de dónde salió: "a:<alimento>", "r:<receta>" o "p:<producto>".
 * [cantidad] son gramos (alimentos y productos) o porciones (recetas). Los valores quedan guardados tal
 * cual, para que el historial no cambie si después se corrige la base o la receta.
 */
@Serializable
data class Anotado(
    val id: String,
    val comida: String,
    val ref: String,
    val nombre: String,
    val cantidad: Double,
    val detalle: String = "",
    val n: Nutrientes,
)

@Serializable
data class Dia(val anotados: List<Anotado> = emptyList(), val aguaMl: Int = 0)

/** Los datos para calcular las metas (Mifflin-St Jeor). */
@Serializable
data class Perfil(
    val sexo: String = "m",
    val edad: Int = 25,
    val alturaCm: Double = 170.0,
    val pesoKg: Double = 70.0,
    /** 0 sentado · 1 poco · 2 moderado · 3 mucho · 4 muchísimo. */
    val actividad: Int = 2,
    /** -1 bajar · 0 mantener · 1 subir. */
    val objetivo: Int = 0,
    /** Kilos por semana que se quieren bajar o subir. */
    val ritmo: Double = 0.25,
)

@Serializable
data class Metas(
    val kcal: Int = 2200,
    val proteina: Int = 120,
    val carbos: Int = 260,
    val grasa: Int = 70,
    val aguaMl: Int = 2500,
    /** true si la persona puso las metas a mano (no se recalculan al cambiar el peso). */
    val manual: Boolean = false,
)

@Serializable
data class Peso(val fecha: String, val kg: Double)

/** Las cinco comidas del día, en orden. Sus nombres están en strings.xml (comida_<id>). */
val COMIDAS = listOf("desayuno", "media_manana", "almuerzo", "algo", "cena")

@Serializable
data class Estado(
    val v: Int = 1,
    val updatedAt: Long = 0,
    val listo: Boolean = false,
    val nombre: String = "",
    val perfil: Perfil = Perfil(),
    val metas: Metas = Metas(),
    /** Lo anotado, por fecha (AAAA-MM-DD). */
    val dias: Map<String, Dia> = emptyMap(),
    val recetas: List<Receta> = emptyList(),
    val productos: List<Producto> = emptyList(),
    /** El menú de la semana: "A|1|almuerzo" → id de receta (1 = lunes … 7 = domingo). */
    val plan: Map<String, String> = emptyMap(),
    /** Semana A y semana B que se turnan; si es false, siempre la A. */
    val alternar: Boolean = false,
    /** Lo marcado en la lista de mercado (por nombre) y lo agregado a mano. */
    val mercadoHecho: List<String> = emptyList(),
    val mercadoExtra: List<String> = emptyList(),
    /** El peso, si no está El Forzudo (con El Forzudo se lee de allá). */
    val pesos: List<Peso> = emptyList(),
    /** Buscar productos por código de barras en Open Food Facts (usa internet). */
    val codigoBarras: Boolean = false,
    /** Ids de alimentos y recetas favoritos ("a:arroz_blanco", "r:ajiaco"…). */
    val favoritos: List<String> = emptyList(),
)

val JsonCucha = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

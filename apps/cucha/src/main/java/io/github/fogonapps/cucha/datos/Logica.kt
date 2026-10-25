package io.github.fogonapps.cucha.datos

import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import kotlin.math.roundToInt

/** La base de alimentos y las recetas de ejemplo (assets/datos), ya leídas. */
class Base(val alimentos: List<Alimento>, val recetas: List<Receta>) {
    private val porId = alimentos.associateBy { it.id }
    private val recetaPorId = recetas.associateBy { it.id }
    fun alimento(id: String) = porId[id]
    fun receta(id: String) = recetaPorId[id]

    companion object {
        fun leer(alimentos: String, recetas: String) = Base(
            JsonCucha.decodeFromString<List<Alimento>>(alimentos),
            JsonCucha.decodeFromString<List<Receta>>(recetas),
        )
    }
}

/** Sin tildes y en minúscula, para buscar "arepa" y encontrar "Arepa"; "frijol" encuentra "Fríjoles". */
fun normal(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

/** Algo que se puede anotar: un alimento de la base, una receta o un producto propio. */
sealed class Comible {
    abstract val ref: String
    abstract fun nombre(idioma: String): String
    data class DeBase(val a: Alimento) : Comible() { override val ref = "a:${a.id}"; override fun nombre(idioma: String) = a.nombre(idioma) }
    data class DeReceta(val r: Receta) : Comible() { override val ref = "r:${r.id}"; override fun nombre(idioma: String) = r.nombre(idioma) }
    data class DeProducto(val p: Producto) : Comible() { override val ref = "p:${p.id}"; override fun nombre(idioma: String) = p.nombre }
}

class Logica(val base: Base) {

    /* ----- Metas ----- */

    /**
     * Calorías del día con la fórmula de Mifflin-St Jeor por el nivel de actividad, más o menos lo que hace
     * falta para bajar o subir [Perfil.ritmo] kg por semana (unas 7700 kcal por kilo). Proteína: 1,8 g por
     * kilo (2,0 al bajar); grasa: el 25 % de las calorías; carbohidratos: el resto. Agua: 35 ml por kilo.
     */
    fun metasDe(p: Perfil, antes: Metas = Metas()): Metas {
        val basal = 10 * p.pesoKg + 6.25 * p.alturaCm - 5 * p.edad + if (p.sexo == "f") -161 else 5
        val factor = listOf(1.2, 1.375, 1.55, 1.725, 1.9)[p.actividad.coerceIn(0, 4)]
        val ajuste = p.objetivo.coerceIn(-1, 1) * p.ritmo * 7700 / 7
        val kcal = ((basal * factor + ajuste) / 10).roundToInt().times(10).coerceAtLeast(1200)
        val proteina = (p.pesoKg * if (p.objetivo < 0) 2.0 else 1.8).roundToInt()
        val grasa = (kcal * 0.25 / 9).roundToInt()
        val carbos = ((kcal - proteina * 4 - grasa * 9) / 4.0).roundToInt().coerceAtLeast(0)
        val agua = ((p.pesoKg * 35) / 100).roundToInt() * 100
        return antes.copy(kcal = kcal, proteina = proteina, carbos = carbos, grasa = grasa, aguaMl = agua, manual = false)
    }

    /* ----- Valores ----- */

    fun deAlimento(a: Alimento, gramos: Double) = a.por100 * (gramos / 100)
    fun deProducto(p: Producto, gramos: Double) = p.por100 * (gramos / 100)

    /** Lo de una porción de la receta: de sus ingredientes (o lo fijado a mano). */
    fun porPorcion(r: Receta): Nutrientes = r.porPorcion
        ?: r.ingredientes.mapNotNull { i -> base.alimento(i.alimento)?.let { deAlimento(it, i.gramos) } }.suma() / r.porciones.coerceAtLeast(1).toDouble()

    fun totalDia(d: Dia?) = d?.anotados?.map { it.n }?.suma() ?: Nutrientes()
    fun totalComida(d: Dia?, comida: String) = d?.anotados?.filter { it.comida == comida }?.map { it.n }?.suma() ?: Nutrientes()

    /* ----- Buscar ----- */

    fun receta(e: Estado, id: String) = e.recetas.firstOrNull { it.id == id } ?: base.receta(id)
    fun recetas(e: Estado) = e.recetas + base.recetas.filter { b -> e.recetas.none { it.id == b.id } }

    fun comible(e: Estado, ref: String): Comible? {
        val (tipo, id) = ref.split(":", limit = 2).let { if (it.size == 2) it[0] to it[1] else return null }
        return when (tipo) {
            "a" -> base.alimento(id)?.let { Comible.DeBase(it) }
            "r" -> receta(e, id)?.let { Comible.DeReceta(it) }
            "p" -> e.productos.firstOrNull { it.id == id }?.let { Comible.DeProducto(it) }
            else -> null
        }
    }

    /**
     * Busca en recetas, productos propios y la base. Cada palabra tiene que aparecer en el nombre; primero lo
     * que empieza por lo buscado y lo más corto. Sin texto, devuelve los favoritos y lo anotado hace poco.
     */
    fun buscar(e: Estado, texto: String, idioma: String, max: Int = 40): List<Comible> {
        val palabras = normal(texto).split(" ").filter { it.isNotBlank() }
        if (palabras.isEmpty()) return recientes(e)
        val todo: List<Comible> = recetas(e).map { Comible.DeReceta(it) } + e.productos.map { Comible.DeProducto(it) } +
            base.alimentos.map { Comible.DeBase(it) }
        return todo.mapNotNull { c ->
            val n = normal(c.nombre(idioma))
            // En la base se busca también por el otro idioma (quien escribe "rice" con la app en español).
            val otro = (c as? Comible.DeBase)?.a?.let { normal(it.nombre(if (idioma == "en") "es" else "en")) } ?: ""
            if (palabras.all { it in n || it in otro }) c to (if (n.startsWith(palabras[0])) 0 else 1) * 1000 + n.length else null
        }.sortedBy { it.second }.take(max).map { it.first }
    }

    /** Favoritos primero y luego lo último anotado, sin repetir. */
    fun recientes(e: Estado, max: Int = 20): List<Comible> {
        val refs = e.favoritos + e.dias.toSortedMap().values.reversed().flatMap { d -> d.anotados.reversed().map { it.ref } }
        return refs.distinct().mapNotNull { comible(e, it) }.take(max)
    }

    /* ----- Plan y mercado ----- */

    /** "A" o "B": con [alternar], las semanas pares del año son A y las impares B. */
    fun letra(fecha: LocalDate, alternar: Boolean) =
        if (alternar && fecha.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) % 2 == 1) "B" else "A"

    fun clavePlan(letra: String, dia: Int, comida: String) = "$letra|$dia|$comida"

    /** El menú de un día: comida → receta. */
    fun menuDe(e: Estado, fecha: LocalDate): Map<String, Receta> {
        val l = letra(fecha, e.alternar)
        return COMIDAS.mapNotNull { c -> e.plan[clavePlan(l, fecha.dayOfWeek.value, c)]?.let { id -> receta(e, id)?.let { c to it } } }.toMap()
    }

    /**
     * La lista de mercado de la semana que empieza el lunes [lunes]: los ingredientes de cada receta del
     * menú, una porción por comida, sumados por alimento y en el orden de los grupos (verduras juntas…).
     */
    fun mercado(e: Estado, lunes: LocalDate): List<Pair<Alimento, Double>> {
        val gramos = mutableMapOf<String, Double>()
        for (d in 0L..6L) for (r in menuDe(e, lunes.plusDays(d)).values) {
            val factor = 1.0 / r.porciones.coerceAtLeast(1)
            for (i in r.ingredientes) gramos.merge(i.alimento, i.gramos * factor, Double::plus)
        }
        val orden = listOf("verduras", "frutas", "tuberculos", "carnes", "pescados", "lacteos", "granos", "legumbres", "grasas", "dulces", "bebidas", "hechas")
        return gramos.mapNotNull { (id, g) -> base.alimento(id)?.let { it to g } }
            .sortedWith(compareBy({ orden.indexOf(it.first.grupo) }, { it.first.es }))
    }

    /* ----- Progreso ----- */

    /** Días seguidos con algo anotado, hasta hoy (o hasta ayer, si hoy todavía no se ha anotado nada). */
    fun racha(e: Estado, hoy: LocalDate): Int {
        fun anotado(f: LocalDate) = e.dias[f.toString()]?.anotados?.isNotEmpty() == true
        var f = if (anotado(hoy)) hoy else hoy.minusDays(1)
        var n = 0
        while (anotado(f)) { n++; f = f.minusDays(1) }
        return n
    }

    /** Promedio por día de los últimos [dias] días que tienen algo anotado (null si no hay ninguno). */
    fun promedio(e: Estado, hoy: LocalDate, dias: Int = 7): Nutrientes? {
        val con = (0 until dias).mapNotNull { e.dias[hoy.minusDays(it.toLong()).toString()] }.filter { it.anotados.isNotEmpty() }
        return if (con.isEmpty()) null else con.map { totalDia(it) }.suma() / con.size.toDouble()
    }

    /** El lunes de la semana de [f]. */
    fun lunes(f: LocalDate): LocalDate = f.with(DayOfWeek.MONDAY)

    fun normalizar(e: Estado): Estado = e.copy(
        favoritos = e.favoritos.distinct(),
        mercadoHecho = e.mercadoHecho.distinct(),
    )
}

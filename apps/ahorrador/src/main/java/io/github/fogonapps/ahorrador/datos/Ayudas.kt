package io.github.fogonapps.ahorrador.datos

import java.text.Normalizer
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.max
import kotlin.math.roundToLong

/*
 * Ayudas de AL-04, inspiradas en otras apps de finanzas pero sin servicios externos: todo se calcula en el teléfono.
 * - Anotar varios con texto: «almuerzo 25 mil + uber 12k» → dos gastos, para revisar antes de guardar.
 * - «¿Me alcanza?»: lo seguro para gastar, el cupo por día y cuántos días de ritmo se come un gasto.
 * - Alertas del mes e historial de sobres y metas.
 */

private fun limpio(t: String) = Normalizer.normalize(t.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").trim()

/* ----- Anotar varios con texto ----- */

/** Un movimiento leído del texto, todavía sin guardar: la persona lo revisa y corrige. */
data class Borrador(val nota: String, val monto: Long, val tipo: TipoMovimiento, val categoria: String?)

/** Palabras que suelen ir con cada categoría inicial (en español e inglés). Lo que la persona ya anotó pesa más. */
private val PALABRAS = mapOf(
    "comida" to listOf("almuerzo", "desayuno", "cena", "comida", "onces", "cafe", "restaurante", "corrientazo", "pizza", "hamburguesa", "empanada", "domicilio", "rappi", "lunch", "dinner", "breakfast", "coffee", "food"),
    "transporte" to listOf("uber", "taxi", "didi", "bus", "transmilenio", "metro", "sitp", "gasolina", "parqueadero", "peaje", "pasaje", "cabify", "indriver", "gas", "parking"),
    "mercado" to listOf("mercado", "supermercado", "exito", "d1", "ara", "olimpica", "carulla", "jumbo", "tienda", "fruver", "groceries"),
    "servicios" to listOf("luz", "agua", "gas natural", "internet", "celular", "plan", "energia", "factura", "claro", "movistar", "tigo", "etb", "phone", "electricity"),
    "casa" to listOf("arriendo", "administracion", "renta", "rent", "arreglo"),
    "salud" to listOf("droga", "drogueria", "farmacia", "medico", "cita", "eps", "medicina", "pharmacy", "doctor"),
    "salidas" to listOf("cine", "cerveza", "trago", "fiesta", "bar", "rumba", "concierto", "beer", "movie"),
    "compras" to listOf("ropa", "zapatos", "regalo", "amazon", "temu", "shein", "clothes", "gift"),
    "cultura" to listOf("libro", "netflix", "spotify", "disney", "museo", "book", "suscripcion", "subscription"),
    "estudio" to listOf("curso", "universidad", "colegio", "matricula", "fotocopias", "course", "school"),
    "deudas" to listOf("cuota", "credito", "prestamo", "tarjeta", "loan"),
    "impuestos" to listOf("impuesto", "predial", "dian", "tax"),
    "sueldo" to listOf("sueldo", "salario", "nomina", "quincena", "salary", "paycheck"),
    "prima" to listOf("prima", "bonus"),
    "extra" to listOf("venta", "vendi", "freelance", "me pagaron", "me pago", "propina", "sold"),
)
private val INGRESOS = setOf("sueldo", "prima", "cesantias", "extra")

/** Montos como se dicen en Colombia: 25000, 25.000, $25.000, 25 mil, 12k, 2 lucas, 1,5 millones, 2 palos. */
private val MONTO = Regex(
    """\$?\s*(\d+(?:[.,]\d{3})+|\d+(?:[.,]\d+)?)\s*(millones|millon|palos?|m\b|mil|k\b|lucas?|luks?)?""",
    RegexOption.IGNORE_CASE,
)

/**
 * Lee el valor en pesos de un trozo de texto, o null si no hay ninguno. Si hay varios números («D1 $84.300»),
 * gana el que parece plata ($, mil, k, puntos de miles); si ninguno lo parece, el más grande.
 */
fun montoEnTexto(texto: String): Pair<Long, IntRange>? {
    val candidatos = MONTO.findAll(texto).mapNotNull { m ->
        val crudo = m.groupValues[1]
        val sufijo = m.groupValues[2].lowercase()
        val miles = Regex("""\d+(?:[.,]\d{3})+""").matches(crudo)
        val base = if (miles) crudo.filter(Char::isDigit).toDouble() else crudo.replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
        val factor = when {
            sufijo.startsWith("mill") || sufijo.startsWith("palo") || sufijo == "m" -> 1_000_000.0
            sufijo == "mil" || sufijo == "k" || sufijo.startsWith("luca") || sufijo.startsWith("luk") -> 1_000.0
            else -> 1.0
        }
        val monto = (base * factor).roundToLong()
        val pareceplata = sufijo.isNotEmpty() || miles || m.value.trimStart().startsWith("$")
        if (monto in 1..999_999_999_999) Triple(monto, m.range, pareceplata) else null
    }.toList()
    val elegido = candidatos.lastOrNull { it.third } ?: candidatos.maxByOrNull { it.first } ?: return null
    return elegido.first to elegido.second
}

/**
 * Parte el texto en movimientos (por «+», comas, punto y coma o renglones) y adivina la categoría: primero por lo
 * que la persona ya anotó con esa misma nota, después por palabras. Lo que no tiene valor se ignora.
 */
fun leerVarios(texto: String, e: Estado): List<Borrador> {
    val ids = e.categorias.map { it.id }.toSet()
    val porNota = e.movimientos.filter { it.categoria != null && it.nota.isNotBlank() }
        .associate { limpio(it.nota) to (it.categoria!! to it.tipo) }
    return texto.split(Regex("""\s*(?:\+|;|\n|,(?!\d{3}\b))\s*""")).mapNotNull { trozo ->
        val (monto, rango) = montoEnTexto(trozo) ?: return@mapNotNull null
        val nota = trozo.removeRange(rango).replace(Regex("""\s+"""), " ").trim(' ', '-', ':', '.', '$').take(60)
        val clave = limpio(nota)
        val aprendida = porNota[clave]?.takeIf { it.first in ids }
        val palabra = PALABRAS.entries.firstOrNull { (cat, palabras) -> cat in ids && palabras.any { p -> Regex("""\b${Regex.escape(p)}\b""").containsMatchIn(clave) } }?.key
        val categoria = aprendida?.first ?: palabra
        val tipo = aprendida?.second?.takeIf { it == TipoMovimiento.INGRESO || it == TipoMovimiento.GASTO }
            ?: if (categoria in INGRESOS) TipoMovimiento.INGRESO else TipoMovimiento.GASTO
        Borrador(nota, monto, tipo, categoria)
    }
}

/* ----- ¿Me alcanza? ----- */

enum class Veredicto { SI, SE_COME_DIAS, NO }

/** La cuenta de «¿Me alcanza?» para un [gasto] de hoy. Todo en pesos; [diasQueSeCome] en días de tu ritmo. */
data class Alcance(
    val tienes: Long,
    val comprometido: Long,
    val seguro: Long,
    val diasRestantes: Int,
    val cupoDia: Long,
    val gastadoHoy: Long,
    val gasto: Long,
    val teQuedarian: Long,
    val ritmoDespues: Long,
    val diasQueSeCome: Double,
    val veredicto: Veredicto,
) {
    val quedaHoy get() = cupoDia - gastadoHoy
}

/**
 * Lo seguro para gastar es lo que tienes (cuentas menos tarjetas) menos lo comprometido del mes: los pagos fijos
 * que faltan y, con sobres, lo que queda reservado en ellos. Se reparte entre los días que quedan del mes.
 */
fun meAlcanza(e: Estado, L: Logica, gasto: Long, hoy: LocalDate): Alcance {
    val mes = mesDe(hoy)
    val tienes = L.patrimonio(e)
    val fijos = e.fijos.filter { it.activo && !L.pagadoEnMes(e, it, mes) && !L.vence(it, mes).isBefore(hoy) }.sumOf { it.monto }
    val sobres = if (e.sistema == Sistema.SOBRES) L.sobres(e, mes).sumOf { max(0, it.queda ?: 0) } else 0
    val comprometido = fijos + sobres
    val seguro = max(0, tienes - comprometido)
    val diasRestantes = YearMonth.from(hoy).lengthOfMonth() - hoy.dayOfMonth + 1
    val gastadoHoy = e.movimientos.filter { it.fecha == hoy.toString() && it.tipo == TipoMovimiento.GASTO }.sumOf { it.monto }
    // El cupo de hoy cuenta también lo que ya gastaste hoy.
    val cupoDia = (seguro + gastadoHoy) / diasRestantes
    val teQuedarian = seguro - gasto
    val ritmoDespues = if (diasRestantes > 1) max(0, teQuedarian) / (diasRestantes - 1) else max(0, teQuedarian)
    val diasQueSeCome = if (cupoDia > 0) gasto.toDouble() / cupoDia else Double.POSITIVE_INFINITY
    val veredicto = when {
        gasto > seguro -> Veredicto.NO
        gasto + gastadoHoy > cupoDia -> Veredicto.SE_COME_DIAS
        else -> Veredicto.SI
    }
    return Alcance(tienes, comprometido, seguro, diasRestantes, cupoDia, gastadoHoy, gasto, teQuedarian, ritmoDespues, diasQueSeCome, veredicto)
}

/* ----- Alertas del mes e historial ----- */

/** Lo que conviene ver en Inicio: sobres pasados, metas cerca o cumplidas. */
data class Alertas(val sobresPasados: List<Sobre>, val metasCerca: List<Meta>, val metasCumplidas: List<Meta>) {
    val vacias get() = sobresPasados.isEmpty() && metasCerca.isEmpty() && metasCumplidas.isEmpty()
}

fun alertas(e: Estado, L: Logica, hoy: LocalDate): Alertas {
    val mes = mesDe(hoy)
    return Alertas(
        L.sobres(e, mes).filter { it.pasado },
        e.metas.filter { L.progreso(e, it) in 0.75..0.9999 },
        e.metas.filter { L.progreso(e, it) >= 1.0 },
    )
}

/** Cómo le fue a un sobre en un mes terminado: respetado (con lo que sobró) o pasado (por cuánto). */
data class ResultadoSobre(val mes: String, val sobre: Sobre) {
    val respetado get() = !sobre.pasado
    val diferencia get() = (sobre.asignado ?: 0) - sobre.gastado
}

/** Los meses ya terminados con sobres (los más nuevos primero), hasta [meses]. */
fun historialSobres(e: Estado, L: Logica, hoy: LocalDate, meses: Int = 12): List<Pair<String, List<ResultadoSobre>>> {
    val actual = mesDe(hoy)
    val conMovimientos = e.movimientos.map { mesDe(it.fecha) }.toSet() + e.sobres.keys
    return conMovimientos.filter { it < actual }.sortedDescending().take(meses).map { mes ->
        mes to L.sobres(e, mes).filter { it.asignado != null && it.asignado > 0 }.map { ResultadoSobre(mes, it) }
    }.filter { it.second.isNotEmpty() }
}

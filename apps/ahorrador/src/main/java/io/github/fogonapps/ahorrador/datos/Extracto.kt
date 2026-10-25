package io.github.fogonapps.ahorrador.datos

import java.time.LocalDate
import kotlin.math.roundToLong

/*
 * Leer el extracto de un banco en CSV (lo que dejan bajar casi todos desde su página). Cada banco lo arma a su
 * modo, así que se adivina: el separador (; , o tabulación), la columna de la fecha, la de la descripción y la del
 * monto (una sola, con signo, o dos: débitos y créditos). Los montos pueden venir como "$ 1.234.567,89" o
 * "-1,234.56"; en pesos no se usan centavos y se redondea.
 */

/** Una fila del extracto: [monto] negativo es plata que salió. */
data class FilaExtracto(val fecha: String, val descripcion: String, val monto: Long)

private val FECHAS = listOf(
    Regex("""^(\d{4})[-/](\d{1,2})[-/](\d{1,2})""") to { m: MatchResult -> Triple(m.groupValues[1], m.groupValues[2], m.groupValues[3]) },
    Regex("""^(\d{1,2})[-/.](\d{1,2})[-/.](\d{4})""") to { m: MatchResult -> Triple(m.groupValues[3], m.groupValues[2], m.groupValues[1]) },
    Regex("""^(\d{1,2})[-/.](\d{1,2})[-/.](\d{2})$""") to { m: MatchResult -> Triple("20" + m.groupValues[3], m.groupValues[2], m.groupValues[1]) },
)

/** La fecha de una celda como AAAA-MM-DD (día primero, como en Colombia), o null si no es fecha. */
fun leerFecha(s: String): String? {
    val t = s.trim()
    for ((r, partes) in FECHAS) {
        val m = r.find(t) ?: continue
        val (a, mes, d) = partes(m)
        return runCatching { LocalDate.of(a.toInt(), mes.toInt(), d.toInt()).toString() }.getOrNull()
    }
    return null
}

/** Un monto de una celda, en pesos enteros, o null si no es un número. */
fun leerMonto(s: String): Long? {
    var t = s.trim().replace("$", "").replace("COP", "").replace(" ", "").replace(" ", "")
    if (t.isEmpty()) return null
    var negativo = false
    if (t.startsWith("(") && t.endsWith(")")) { negativo = true; t = t.drop(1).dropLast(1) }
    if (t.startsWith("-")) { negativo = true; t = t.drop(1) }
    if (t.endsWith("-")) { negativo = true; t = t.dropLast(1) }
    if (t.isEmpty() || !t.all { it.isDigit() || it == '.' || it == ',' }) return null
    val punto = t.lastIndexOf('.'); val coma = t.lastIndexOf(',')
    val decimal = when {
        punto >= 0 && coma >= 0 -> maxOf(punto, coma)
        punto >= 0 -> if (t.count { it == '.' } == 1 && t.length - punto - 1 != 3) punto else -1
        coma >= 0 -> if (t.count { it == ',' } == 1 && t.length - coma - 1 != 3) coma else -1
        else -> -1
    }
    val entero = (if (decimal >= 0) t.substring(0, decimal) else t).filter { it.isDigit() }
    val fraccion = if (decimal >= 0) t.substring(decimal + 1).filter { it.isDigit() } else ""
    if (entero.isEmpty() && fraccion.isEmpty()) return null
    val v = ("${entero.ifEmpty { "0" }}.${fraccion.ifEmpty { "0" }}").toDouble().roundToLong()
    return if (negativo) -v else v
}

/** Parte una línea de CSV respetando las comillas. */
private fun celdas(linea: String, sep: Char): List<String> {
    val r = mutableListOf<String>(); val b = StringBuilder(); var comillas = false
    var i = 0
    while (i < linea.length) {
        val c = linea[i]
        when {
            c == '"' && comillas && i + 1 < linea.length && linea[i + 1] == '"' -> { b.append('"'); i++ }
            c == '"' -> comillas = !comillas
            c == sep && !comillas -> { r += b.toString().trim(); b.clear() }
            else -> b.append(c)
        }
        i++
    }
    r += b.toString().trim()
    return r
}

private fun normal(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

/** Las filas con fecha y monto del extracto [texto]. Las demás (encabezados, totales, saldos) se saltan. */
fun leerExtracto(texto: String): List<FilaExtracto> {
    val lineas = texto.removePrefix("\uFEFF").lines().filter { it.isNotBlank() }
    if (lineas.isEmpty()) return emptyList()
    val sep = listOf(';', '\t', ',').maxBy { s -> lineas.take(10).sumOf { l -> celdas(l, s).size } }
    val tabla = lineas.map { celdas(it, sep) }
    // Encabezado: la primera fila sin fecha, si sus nombres dicen algo.
    val enc = tabla.firstOrNull { f -> f.none { leerFecha(it) != null } }?.map(::normal)
    fun col(vararg nombres: String) = enc?.indexOfFirst { c -> nombres.any { c.contains(it) } }?.takeIf { it >= 0 }
    val cDebito = col("debito", "cargo", "retiro", "egreso", "salida")
    val cCredito = col("credito", "abono", "deposito", "ingreso", "entrada")
    val cMonto = col("valor", "monto", "importe", "amount", "transaccion")
    val cDesc = col("descripcion", "concepto", "detalle", "referencia", "description")
    val cSaldo = col("saldo", "balance")
    return tabla.mapNotNull { f ->
        val iFecha = f.indexOfFirst { leerFecha(it) != null }.takeIf { it >= 0 } ?: return@mapNotNull null
        val monto = when {
            cDebito != null && cCredito != null && cDebito != cCredito -> {
                val d = f.getOrNull(cDebito)?.let(::leerMonto)?.let { -kotlin.math.abs(it) } ?: 0
                val c = f.getOrNull(cCredito)?.let(::leerMonto)?.let { kotlin.math.abs(it) } ?: 0
                (d + c).takeIf { it != 0L }
            }
            cMonto != null -> f.getOrNull(cMonto)?.let(::leerMonto)
            // Sin encabezado: el primer número que no sea la fecha ni el saldo (casi siempre la última columna).
            else -> f.indices.filter { it != iFecha && it != cSaldo }.firstNotNullOfOrNull { i -> f[i].takeIf { leerFecha(it) == null }?.let(::leerMonto) }
        } ?: return@mapNotNull null
        val desc = (cDesc?.let { f.getOrNull(it) } ?: f.filterIndexed { i, c -> i != iFecha && leerMonto(c) == null }.maxByOrNull { it.length }).orEmpty()
        FilaExtracto(leerFecha(f[iFecha])!!, desc.trim(), monto)
    }
}

/** Cuánto se guarda en la semana [semana] (desde 1) del reto que empieza con [base]. */
fun montoReto(base: Long, semana: Int) = base * semana

/** Lo que se lleva ahorrado si se cumplió hasta la semana [semana]. */
fun totalReto(base: Long, semana: Int) = base * semana * (semana + 1) / 2

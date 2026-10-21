package io.github.fogonapps.ahorrador.datos

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/*
 * Cálculos de El Ahorrador. Todo es puro: recibe el estado y la fecha de hoy y no toca la pantalla
 * ni el disco, así que se prueba en la JVM (ver src/test).
 */

fun mesDe(fecha: String): String = fecha.take(7)
fun mesDe(d: LocalDate): String = YearMonth.from(d).toString()
fun mesAnterior(mes: String): String = YearMonth.parse(mes).minusMonths(1).toString()

/** El día [dia] del mes [ym], o el último si el mes es más corto (un pago del 31 en febrero cae el 28). */
fun diaDelMes(ym: YearMonth, dia: Int): LocalDate = ym.atDay(min(max(dia, 1), ym.lengthOfMonth()))

/** La próxima vez que llega el día [dia]: este mes si todavía no pasó, si no el mes que viene. */
fun proximaFecha(dia: Int, hoy: LocalDate): LocalDate {
    val este = diaDelMes(YearMonth.from(hoy), dia)
    return if (!este.isBefore(hoy)) este else diaDelMes(YearMonth.from(hoy).plusMonths(1), dia)
}

/** Meses enteros de diferencia entre dos meses "AAAA-MM". */
fun mesesEntre(desde: String, hasta: String): Int =
    ChronoUnit.MONTHS.between(YearMonth.parse(desde), YearMonth.parse(hasta)).toInt()

/** El 4×1000: cuatro pesos por cada mil que salen de una cuenta de banco. */
fun gmf(monto: Long): Long = (monto * 4.0 / 1000).roundToLong()

/** Redondea a la centena, como se cobra en la práctica. */
fun aCentena(n: Double): Long = (n / 100).roundToLong() * 100

data class Sobre(val clave: String, val nombre: String, val color: Long, val icono: String, val asignado: Long?, val gastado: Long) {
    val queda get() = asignado?.let { it - gastado }
    val pasado get() = asignado != null && gastado > asignado
}

data class Cuota(val movimiento: Movimiento, val valor: Long, val pagadas: Int) {
    val total get() = movimiento.cuotas ?: 1
    val restante get() = valor * (total - pagadas)
}

data class Tarjeta(
    val deuda: Long,
    val cupo: Long,
    val corte: LocalDate,
    val pago: LocalDate,
    val minimo: Long,
    val interesesSiMinimo: Long,
    val cuotas: List<Cuota>,
) {
    val disponible get() = max(0, cupo - deuda)
    val usado get() = if (cupo <= 0) 0.0 else min(1.0, deuda.toDouble() / cupo)
}

data class Cierre(val ingresos: Long, val gastos: Long, val ahorroPlaneado: Long?, val nota: String) {
    val ahorrado get() = ingresos - gastos
}

class Logica {

    /* ----- Cuentas ----- */

    fun saldo(e: Estado, id: String): Long {
        val c = e.cuentas.firstOrNull { it.id == id } ?: return 0
        var s = c.saldoInicial
        for (m in e.movimientos) {
            when (m.tipo) {
                TipoMovimiento.INGRESO -> if (m.cuenta == id) s += m.monto
                TipoMovimiento.GASTO, TipoMovimiento.AHORRO -> if (m.cuenta == id) s -= m.monto
                TipoMovimiento.TRASPASO -> {
                    if (m.cuenta == id) s -= m.monto
                    if (m.aCuenta == id) s += m.monto
                }
            }
        }
        return s
    }

    /** Lo que se debe en una tarjeta de crédito (su saldo en negativo). */
    fun deuda(e: Estado, id: String): Long = max(0, -saldo(e, id))

    fun tarjeta(e: Estado, id: String, hoy: LocalDate): Tarjeta? {
        val c = e.cuentas.firstOrNull { it.id == id } ?: return null
        val cr = c.credito ?: return null
        val deuda = deuda(e, id)
        val minimo = min(deuda, aCentena(deuda * cr.minimoPct / 100))
        return Tarjeta(
            deuda = deuda,
            cupo = cr.cupo,
            corte = proximaFecha(cr.diaCorte, hoy),
            pago = proximaFecha(cr.diaPago, hoy),
            minimo = minimo,
            interesesSiMinimo = aCentena((deuda - minimo) * cr.tasaMensual / 100),
            cuotas = cuotas(e, id, hoy),
        )
    }

    /** Compras a cuotas de una tarjeta que todavía no se terminan de pagar. La primera cuota llega el mes siguiente. */
    fun cuotas(e: Estado, id: String, hoy: LocalDate): List<Cuota> =
        e.movimientos.filter { it.tipo == TipoMovimiento.GASTO && it.cuenta == id && (it.cuotas ?: 1) > 1 }
            .map { m ->
                val n = m.cuotas!!
                Cuota(m, ceil(m.monto.toDouble() / n).toLong(), min(n, max(0, mesesEntre(mesDe(m.fecha), mesDe(hoy)))))
            }
            .filter { it.pagadas < it.total }
            .sortedBy { it.movimiento.fecha }

    /** Plata real que tiene la persona: todas las cuentas menos lo que debe en tarjetas. */
    fun patrimonio(e: Estado): Long = e.cuentas.filter { !it.archivada }.sumOf { saldo(e, it.id) }

    /* ----- El mes ----- */

    fun delMes(e: Estado, mes: String) = e.movimientos.filter { mesDe(it.fecha) == mes }

    fun ingresos(e: Estado, mes: String): Long = delMes(e, mes).filter { it.tipo == TipoMovimiento.INGRESO }.sumOf { it.monto }
    fun gastos(e: Estado, mes: String): Long = delMes(e, mes).filter { it.tipo == TipoMovimiento.GASTO }.sumOf { it.monto }
    fun ahorrado(e: Estado, mes: String): Long = delMes(e, mes).filter { it.tipo == TipoMovimiento.AHORRO }.sumOf { it.monto }

    /** Lo que queda para gastar este mes: lo que entró, menos lo que se gastó y lo que se guardó. */
    fun disponible(e: Estado, mes: String): Long = ingresos(e, mes) - gastos(e, mes) - ahorrado(e, mes)

    fun gastadoEn(e: Estado, mes: String, categoria: String): Long =
        delMes(e, mes).filter { it.tipo == TipoMovimiento.GASTO && it.categoria == categoria }.sumOf { it.monto }

    /** Gastos del mes por categoría, de mayor a menor. */
    fun porCategoria(e: Estado, mes: String): List<Pair<Categoria, Long>> =
        e.categorias.filter { !it.ingreso }.map { it to gastadoEn(e, mes, it.id) }.filter { it.second > 0 }.sortedByDescending { it.second }

    /** Cuánto cambiaron los gastos frente al mes anterior, en %. Null si el mes anterior no tiene gastos. */
    fun cambioGastos(e: Estado, mes: String): Int? {
        val antes = gastos(e, mesAnterior(mes))
        if (antes == 0L) return null
        return ((gastos(e, mes) - antes) * 100.0 / antes).roundToLong().toInt()
    }

    /* ----- Sobres según el sistema ----- */

    fun asignado(e: Estado, mes: String): Long = e.sobres[mes].orEmpty().values.sum()

    /**
     * Sistema Sobres: la plata que tienes (cuentas menos tarjetas) que todavía no está en un sobre del mes.
     * Un sobre cuenta solo por lo que le queda: lo gastado ya salió de las cuentas. Lo que sobra de un mes
     * vuelve a estar por asignar el siguiente. Una compra con tarjeta baja el sobre y sube la deuda, así que no cambia.
     */
    fun porAsignar(e: Estado, mes: String): Long {
        val a = e.sobres[mes].orEmpty()
        return patrimonio(e) - a.entries.sumOf { (cat, n) -> max(0, n - gastadoEn(e, mes, cat)) }
    }

    fun sobres(e: Estado, mes: String): List<Sobre> = when (e.sistema) {
        Sistema.SOBRES -> {
            val a = e.sobres[mes].orEmpty()
            e.categorias.filter { !it.ingreso }
                .map { Sobre(it.id, it.nombre, it.color, it.icono, a[it.id] ?: 0, gastadoEn(e, mes, it.id)) }
                .filter { (it.asignado ?: 0) > 0 || it.gastado > 0 }
        }
        Sistema.REGLA_50_30_20 -> {
            val ing = ingresos(e, mes)
            val (pn, pg, pa) = e.reparto
            fun gastoDe(vararg g: Grupo) = e.categorias.filter { !it.ingreso && it.grupo in g }.sumOf { gastadoEn(e, mes, it.id) }
            listOf(
                Sobre("NECESIDAD", "", 0xFF7B4DFF, "casa", ing * pn / 100, gastoDe(Grupo.NECESIDAD, Grupo.IMPREVISTO)),
                Sobre("GUSTO", "", 0xFFFF4FA3, "salidas", ing * pg / 100, gastoDe(Grupo.GUSTO, Grupo.CULTURA)),
                Sobre("AHORRO", "", 0xFF1FB45A, "moneda", ing * pa / 100, ahorrado(e, mes) + gastoDe(Grupo.AHORRO)),
            )
        }
        Sistema.KAKEIBO -> listOf(
            Sobre("NECESIDAD", "", 0xFF7B4DFF, "casa", null, 0),
            Sobre("GUSTO", "", 0xFFFF4FA3, "salidas", null, 0),
            Sobre("CULTURA", "", 0xFF2D5BFF, "cultura", null, 0),
            Sobre("IMPREVISTO", "", 0xFFE5332A, "imprevisto", null, 0),
        ).map { s -> s.copy(gastado = e.categorias.filter { !it.ingreso && it.grupo.name == s.clave }.sumOf { gastadoEn(e, mes, it.id) }) }
        Sistema.SOLO_ANOTAR -> emptyList()
    }

    /* ----- Metas ----- */

    fun ahorradoEn(e: Estado, meta: String): Long = e.movimientos.filter { it.tipo == TipoMovimiento.AHORRO && it.meta == meta }.sumOf { it.monto }

    fun progreso(e: Estado, m: Meta): Double = if (m.objetivo <= 0) 1.0 else min(1.0, ahorradoEn(e, m.id).toDouble() / m.objetivo)

    /** Cuánto guardar cada quincena para llegar a la fecha de la meta. Null si no tiene fecha o ya se cumplió. */
    fun porQuincena(e: Estado, m: Meta, hoy: LocalDate): Long? {
        val falta = m.objetivo - ahorradoEn(e, m.id)
        val fecha = m.fecha?.let { LocalDate.parse(it) } ?: return null
        if (falta <= 0) return null
        val quincenas = max(1, ChronoUnit.DAYS.between(hoy, fecha).toInt() / 15)
        return aCentena(falta.toDouble() / quincenas)
    }

    /** A este paso (lo abonado en los últimos 3 meses), en qué mes se llega. Null si no ha abonado. */
    fun llegaEn(e: Estado, m: Meta, hoy: LocalDate): YearMonth? {
        val falta = m.objetivo - ahorradoEn(e, m.id)
        if (falta <= 0) return YearMonth.from(hoy)
        val desde = hoy.minusMonths(3).toString()
        val reciente = e.movimientos.filter { it.tipo == TipoMovimiento.AHORRO && it.meta == m.id && it.fecha > desde }.sumOf { it.monto }
        if (reciente <= 0) return null
        val porMes = reciente / 3.0
        return YearMonth.from(hoy).plusMonths(ceil(falta / porMes).toLong())
    }

    /* ----- Me deben / debo ----- */

    fun pendiente(d: Deuda): Long = max(0, d.monto - d.abonos.sumOf { it.monto })

    fun meDeben(e: Estado): Long = e.deudas.filter { it.meDeben }.sumOf { pendiente(it) }

    /** Lo que debo: préstamos y créditos, más lo que se debe en las tarjetas. */
    fun debo(e: Estado): Long = e.deudas.filter { !it.meDeben }.sumOf { pendiente(it) } +
        e.cuentas.filter { it.tipo == TipoCuenta.CREDITO && !it.archivada }.sumOf { deuda(e, it.id) }

    /** Cuota de un crédito y cuántas van pagadas (abonos hechos). */
    fun cuotaDe(d: Deuda): Long? = d.cuotas?.let { ceil(d.monto.toDouble() / it).toLong() }

    /* ----- Pagos fijos ----- */

    fun vence(f: Fijo, mes: String): LocalDate = diaDelMes(YearMonth.parse(mes), f.dia)

    fun pagadoEnMes(e: Estado, f: Fijo, mes: String): Boolean = delMes(e, mes).any { it.fijo == f.id }

    /** Fijos de este mes que no se han pagado y vencen en los próximos [dias] días (o ya vencieron). */
    fun porVencer(e: Estado, hoy: LocalDate, dias: Int = 3): List<Pair<Fijo, LocalDate>> {
        val mes = mesDe(hoy)
        return e.fijos.filter { it.activo && !pagadoEnMes(e, it, mes) }
            .map { it to vence(it, mes) }
            .filter { (_, d) -> !d.isAfter(hoy.plusDays(dias.toLong())) }
            .sortedBy { it.second }
    }

    /** Próximos pagos para la pantalla de inicio: los de este mes que faltan, por fecha. */
    fun proximos(e: Estado, hoy: LocalDate): List<Pair<Fijo, LocalDate>> {
        val mes = mesDe(hoy)
        return e.fijos.filter { it.activo && !pagadoEnMes(e, it, mes) }.map { it to vence(it, mes) }.sortedBy { it.second }
    }

    /** Fijos automáticos que ya vencieron este mes y todavía no se anotaron: la app los anota sola. */
    fun aAnotar(e: Estado, hoy: LocalDate): List<Fijo> {
        val mes = mesDe(hoy)
        return e.fijos.filter { it.activo && it.automatico && !pagadoEnMes(e, it, mes) && !vence(it, mes).isAfter(hoy) }
    }

    /* ----- Cierre del mes (kakeibo) ----- */

    fun cierre(e: Estado, mes: String) = Cierre(ingresos(e, mes), gastos(e, mes), e.metaAhorroMes, e.cierres[mes].orEmpty())

    /* ----- Respaldo ----- */

    /** Arregla un estado recién leído de un respaldo sin borrar nada: quita movimientos de cuentas que no existen. */
    fun normalizar(e: Estado): Estado {
        val ids = e.cuentas.map { it.id }.toSet()
        return e.copy(movimientos = e.movimientos.filter { it.cuenta in ids && (it.aCuenta == null || it.aCuenta in ids) }, listo = true)
    }
}

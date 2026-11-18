package io.github.fogonapps.social

import kotlinx.serialization.Serializable

@Serializable internal data class VisibilidadLena(val visible: Boolean)
@Serializable internal data class AporteNuevo(val usuario: String, val monto: Int, val referencia: String, val nota: String? = null)

internal fun nivelLena(pesos: Long): String = when {
    pesos >= 300_000 -> "fogon"
    pesos >= 100_000 -> "fogata"
    pesos >= 25_000 -> "brasa"
    pesos > 0 -> "chispa"
    else -> ""
}

internal class Lena(private val b: Base, private val equipo: Equipo, private val ahora: () -> Long) {
    fun nivel(cuenta: Long): String = b.usar {
        // Wompi guarda el importe cobrado en centavos de COP.
        val pesos = leer("""SELECT
            COALESCE((SELECT SUM(monto) FROM ordenes_pago WHERE cuenta = ? AND estado = 'APPROVED'), 0) / 100
            + COALESCE((SELECT SUM(monto) FROM aportes WHERE cuenta = ? AND anulado = 0), 0)""",
            cuenta, cuenta) { it.getLong(1) }.single()
        nivelLena(pesos)
    }

    fun nivelVisible(cuenta: Long): String = b.usar {
        if (leer("SELECT lena_visible FROM cuentas WHERE id = ?", cuenta) { it.getBoolean(1) }.single()) nivel(cuenta) else ""
    }

    fun ponerVisibilidad(cuenta: Long, visible: Boolean) = b.usar {
        cambiar("UPDATE cuentas SET lena_visible = ?, lena_preguntada = 1 WHERE id = ?", if (visible) 1 else 0, cuenta)
    }

    fun requierePrincipal(yo: Long, token: String?) {
        if (equipo.rol(yo) != "principal") throw Problema(403, "solo_principal")
        equipo.requiere(yo, "asignar_equipo", token)
    }

    fun registrar(yo: Long, token: String?, aporte: AporteNuevo): Unit = b.transaccion {
        requierePrincipal(yo, token)
        val referencia = aporte.referencia.trim()
        if (referencia.isEmpty()) throw Problema(400, "referencia")
        if (aporte.monto <= 0) throw Problema(400, "monto")
        if (leer("SELECT 1 FROM aportes WHERE referencia = ?", referencia) { 1 }.isNotEmpty()) throw Problema(409, "aporte_duplicado")
        val cuenta = leer("SELECT id FROM cuentas WHERE usuario = ? AND cerrada = 0 AND borrar_el = 0",
            aporte.usuario.trim().removePrefix("@").lowercase()) { it.getLong(1) }.singleOrNull() ?: throw Problema(404, "no_existe")
        val chispas = (aporte.monto / TemasPerfil.PESOS_POR_CHISPA).toLong()
        val cuando = ahora()
        cambiar("INSERT INTO aportes(referencia, cuenta, monto, nota, cuando) VALUES(?,?,?,?,?)",
            referencia, cuenta, aporte.monto, aporte.nota, cuando)
        if (chispas > 0) registrarChispas(cuenta, chispas, "aporte:$referencia", cuando)
        equipo.registrar(yo.toString(), "aporte_registrar", cuenta.toString(), "Referencia $referencia. Chispas acreditadas: $chispas", token)
    }

    fun anular(yo: Long, token: String?, referencia: String): Unit = b.transaccion {
        requierePrincipal(yo, token)
        val ref = referencia.trim()
        val (cuenta, monto, anulado) = leer("SELECT cuenta, monto, anulado FROM aportes WHERE referencia = ?", ref) {
            val cuenta = it.getLong(1).let { id -> if (it.wasNull()) null else id }
            Triple(cuenta, it.getInt(2), it.getBoolean(3))
        }.singleOrNull() ?: throw Problema(404, "no_existe")
        if (anulado) return@transaccion
        val acreditadas = (monto / TemasPerfil.PESOS_POR_CHISPA).toLong()
        val saldo = cuenta?.let {
            leer("SELECT COALESCE(SUM(cantidad), 0) FROM chispas WHERE cuenta = ?", it) { fila -> fila.getLong(1) }.single()
        } ?: 0L
        val descontadas = minOf(acreditadas, saldo.coerceAtLeast(0))
        cambiar("UPDATE aportes SET anulado = 1 WHERE referencia = ?", ref)
        if (cuenta != null && descontadas > 0) registrarChispas(cuenta, -descontadas, "aporte_anulado:$ref", ahora())
        equipo.registrar(yo.toString(), "aporte_anular", cuenta?.toString().orEmpty(),
            "Referencia $ref. Chispas descontadas: $descontadas. Chispas no recuperadas: ${acreditadas - descontadas}", token)
    }
}

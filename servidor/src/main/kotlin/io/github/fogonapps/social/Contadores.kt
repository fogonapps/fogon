package io.github.fogonapps.social

import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.serialization.Serializable

@Serializable
internal data class DiaContadores(val dia: String, val contadores: Map<String, Int>)

@Serializable
internal data class LoteContadores(val lote: String, val app: String, val version: String, val dias: List<DiaContadores>)

@Serializable
internal data class TotalContador(val dia: String, val app: String, val nombre: String, val total: Long)

@Serializable
internal data class InformeContadores(val totales: List<TotalContador>, val embudo: Map<String, Long>)

internal class Contadores(private val base: Base, private val hoy: () -> LocalDate = { LocalDate.now(ZoneOffset.UTC) }) {
    private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    private val version = Regex("[0-9]+(?:\\.[0-9]+){0,3}")
    private val etapas = listOf("tienda.abrir", "tienda.paquete_elegido", "tienda.pago_iniciado", "tienda.pago_vuelto", "tienda.tema_comprado")

    fun sumar(lote: LoteContadores) {
        AppsSocial.validar(lote.app)
        if (!uuid.matches(lote.lote)) throw Problema(400, "lote")
        if (lote.version.length > 24 || !version.matches(lote.version)) throw Problema(400, "version")
        if (lote.dias.size !in 1..31 || lote.dias.distinctBy { it.dia }.size != lote.dias.size) throw Problema(400, "dias")
        val hoy = hoy()
        base.transaccion {
            prepareStatement("INSERT OR IGNORE INTO lotes_contadores(lote, recibido_dia) VALUES (?, ?)").use {
                it.setString(1, lote.lote)
                it.setString(2, hoy.toString())
                if (it.executeUpdate() == 0) return@transaccion
            }
            prepareStatement("""
                INSERT INTO contadores(dia, app, version, nombre, total) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(dia, app, version, nombre) DO UPDATE SET total = total + excluded.total
            """.trimIndent()).use { guardar ->
                for (entrada in lote.dias) {
                    if (fecha(entrada.dia) !in hoy.minusDays(30)..hoy) throw Problema(400, "dia")
                    for ((nombre, cantidad) in entrada.contadores) {
                        if (nombre !in nombresContadores) continue
                        if (cantidad !in 1..10_000) throw Problema(400, "cantidad")
                        guardar.setString(1, entrada.dia)
                        guardar.setString(2, lote.app)
                        guardar.setString(3, lote.version)
                        guardar.setString(4, nombre)
                        guardar.setInt(5, cantidad)
                        guardar.addBatch()
                    }
                }
                guardar.executeBatch()
            }
        }
    }

    fun limpiarLotes() {
        base.usar {
            prepareStatement("DELETE FROM lotes_contadores WHERE recibido_dia < ?").use {
                it.setString(1, hoy().minusDays(35).toString())
                it.executeUpdate()
            }
        }
    }

    fun consultar(desde: String?, hasta: String?, app: String?): InformeContadores {
        val hoy = hoy()
        val inicio = desde?.let(::fecha) ?: hoy.minusDays(30)
        val fin = hasta?.let(::fecha) ?: hoy
        if (inicio > fin) throw Problema(400, "dia")
        app?.let(AppsSocial::validar)
        val totales = base.usar {
            prepareStatement("""
                SELECT dia, app, nombre, SUM(total) FROM contadores
                WHERE dia BETWEEN ? AND ? AND (? IS NULL OR app = ?)
                GROUP BY dia, app, nombre ORDER BY dia, app, nombre
            """.trimIndent()).use {
                it.setString(1, inicio.toString())
                it.setString(2, fin.toString())
                it.setString(3, app)
                it.setString(4, app)
                it.executeQuery().use { filas ->
                    buildList {
                        while (filas.next()) add(TotalContador(filas.getString(1), filas.getString(2), filas.getString(3), filas.getLong(4)))
                    }
                }
            }
        }
        return InformeContadores(totales, etapas.associateWith { etapa -> totales.filter { it.nombre == etapa }.sumOf { it.total } })
    }

    private fun fecha(texto: String): LocalDate =
        runCatching { LocalDate.parse(texto) }.getOrNull()?.takeIf { texto.length == 10 && it.toString() == texto }
            ?: throw Problema(400, "dia")
}

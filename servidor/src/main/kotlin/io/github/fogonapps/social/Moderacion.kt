package io.github.fogonapps.social

import kotlinx.serialization.Serializable

/**
 * La moderación de Fogón Social: la hace una persona (la cuenta con `admin`) sobre los reportes; no se vigila todo lo
 * que se publica. Lo que reportan varias cuentas se oculta mientras se revisa; las faltas suben de a una (las graves,
 * de a dos), vencen a los 90 días y traen: aviso, 24 h sin publicar, 7 días de suspensión y, a la cuarta, cierre.
 * Hay casos que cierran a la primera (ver [Moderacion.CIERRE_DIRECTO]). Cada decisión queda en `decisiones`.
 */
object Moderacion {
    /** Los motivos de reporte (los mismos para cuentas, publicaciones, comentarios y mensajes). */
    const val MENOR_EN_RIESGO = "menor_en_riesgo"
    val MOTIVOS = setOf(MENOR_EN_RIESGO, "desnudos", "acoso", "odio", "autolesion", "spam", "menor_de_edad", "derechos", "falso", "otro")
    /** Con cuántas cuentas distintas (de más de 7 días y sin sanción) se oculta algo mientras se revisa. */
    const val UMBRAL = 3
    const val VENCE = 90L * 24 * 3600_000
    val CIERRE_DIRECTO = setOf(MENOR_EN_RIESGO, "menor_de_edad", "evadir_sancion", "amenaza_real")
}

@Serializable data class Caso(
    val que: String, val objeto: String, val motivos: Map<String, Int>, val personas: Int, val oculto: Boolean,
    /** Lo reportado, para verlo: el texto o los datos de la publicación, o el texto que mandó quien reportó un mensaje. */
    val contenido: String, val autor: Persona?, val faltasAutor: Int,
)
@Serializable data class Decision(val que: String, val objeto: String, val decision: String, val regla: String = "")
@Serializable data class Falta(val regla: String, val puntos: Int, val cuando: Long, val vence: Long)
@Serializable data class EstadoCuenta(
    val faltas: List<Falta>, val restringidaHasta: Long, val suspendidaHasta: Long, val cerrada: Boolean, val admin: Boolean,
)

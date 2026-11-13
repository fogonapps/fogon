package io.github.fogonapps.social

import kotlinx.serialization.Serializable

/**
 * La moderación de Fogón Social: la hace una persona (el equipo con permiso, MFA y sesión elevada) sobre los reportes; no se vigila todo lo
 * que se publica. Lo que reportan varias cuentas se oculta mientras se revisa; las faltas suben de a una (las graves,
 * de a dos), vencen a los 90 días y traen: aviso, 24 h sin publicar, 7 días de suspensión y, a la cuarta, cierre.
 * Hay casos que cierran a la primera (ver [Moderacion.CIERRE_DIRECTO]). Cada decisión queda en `decisiones` y en el registro encadenado del equipo.
 */
object Moderacion {
    /** Los motivos de reporte (los mismos para cuentas, publicaciones, comentarios y mensajes). */
    const val MENOR_EN_RIESGO = "menor_en_riesgo"
    /** Cuánto se conserva oculto lo retirado por poner a un menor en riesgo, para entregarlo a las autoridades: un año. */
    const val CONSERVAR_GRAVE = 365L * 86400_000
    val MOTIVOS = setOf(MENOR_EN_RIESGO, "desnudos", "acoso", "odio", "autolesion", "spam", "menor_de_edad", "derechos", "falso", "otro")
    /** Con cuántas cuentas distintas (de más de 7 días y sin sanción) se oculta algo mientras se revisa. */
    const val UMBRAL = 10
    const val VENCE = 90L * 24 * 3600_000
    val CIERRE_DIRECTO = setOf(MENOR_EN_RIESGO, "menor_de_edad", "evadir_sancion", "amenaza_real")
    val DECISIONES = setOf("advertencia", "falta", "falta_grave", "cerrar")
    /** Las reglas con las que se puede sancionar: los motivos de reporte y los de cierre directo. */
    val REGLAS = MOTIVOS + CIERRE_DIRECTO
    /** Hasta cuándo se puede apelar una sanción. */
    const val PLAZO_APELAR = 30L * 24 * 3600_000
    /** Dónde están las normas (los términos, sección «Lo que no se permite»). */
    const val NORMAS = "https://fogonapps.duckdns.org/terminos#normas"

    /** El nombre de una regla para el correo a la persona. */
    fun nombreRegla(regla: String) = when (regla) {
        MENOR_EN_RIESGO -> "poner a un menor en riesgo"
        "desnudos" -> "desnudos o contenido sexual"
        "acoso" -> "acoso o amenazas"
        "odio" -> "odio o discriminación"
        "autolesion" -> "promover la autolesión"
        "spam" -> "spam o estafas"
        "menor_de_edad" -> "ser menor de 18 años"
        "derechos" -> "violar derechos de autor o marcas"
        "falso" -> "información falsa o suplantación"
        "evadir_sancion" -> "abrir una cuenta para evadir una sanción"
        "amenaza_real" -> "amenazas reales"
        else -> "incumplir los términos"
    }

    /** El mensaje del equipo, limpio: sin caracteres de control (salvo saltos de línea) y de hasta 500 caracteres. */
    fun mensajeLimpio(m: String) = m.filter { it == '\n' || !it.isISOControl() }.trim().take(500)
}

@Serializable data class Caso(
    val que: String, val objeto: String, val motivos: Map<String, Int>, val personas: Int, val oculto: Boolean,
    /** Lo reportado, para verlo: el texto o los datos de la publicación, o el texto que mandó quien reportó un mensaje. */
    val contenido: String, val autor: Persona?, val faltasAutor: Int,
    val estado: String, val retiradoHasta: Long?,
    /** Lo reportó quien mira la cola: no lo puede decidir (lo decide otra persona del equipo). */
    val propio: Boolean = false,
)
/**
 * [decision]: "sin_falta", "advertencia" (se quita lo reportado, sin puntos: un aviso con el motivo), "falta",
 * "falta_grave" o "cerrar". [mensaje]: lo que el equipo le explica a la persona (lo ve en el estado de su cuenta).
 */
@Serializable data class Decision(val que: String, val objeto: String, val decision: String, val regla: String = "", val mensaje: String = "")
@Serializable data class Falta(val regla: String, val puntos: Int, val cuando: Long, val vence: Long, val mensaje: String = "", val decision: String = "")
/** Sancionar una cuenta directo desde Moderación (sin reporte de por medio). */
@Serializable data class SancionDirecta(val usuario: String, val decision: String, val regla: String, val mensaje: String = "")
/** Una apelación: la persona sancionada explica; el equipo la acepta (se levanta la sanción) o la rechaza, con respuesta. */
@Serializable data class Apelacion(val id: Long, val usuario: String, val texto: String, val cuando: Long, val estado: String,
    val respuesta: String = "", val faltas: List<Falta> = emptyList(), val suspendidaHasta: Long = 0, val cerrada: Boolean = false)
@Serializable data class NuevaApelacion(val texto: String, val correo: String = "", val clave: String = "")
@Serializable data class DecisionApelacion(val id: Long, val aceptar: Boolean, val respuesta: String)
/** Una sanción vigente o reciente, para la lista del equipo. */
@Serializable data class SancionReciente(val usuario: String, val nombre: String, val regla: String, val mensaje: String, val decision: String,
    val cuando: Long, val restringidaHasta: Long, val suspendidaHasta: Long, val cerrada: Boolean, val puntos: Int)
/** Lo que ve en la pantalla de baneo quien entra con una cuenta suspendida o cerrada. */
@Serializable data class EstadoSancion(val estado: EstadoCuenta, val apelacion: Apelacion? = null)
@Serializable data class AccesoSancion(val correo: String, val clave: String)
@Serializable data class EstadoCuenta(
    val faltas: List<Falta>, val restringidaHasta: Long, val suspendidaHasta: Long, val cerrada: Boolean, val admin: Boolean,
    /** La apelación más reciente, si hay. */
    val apelacion: Apelacion? = null,
)

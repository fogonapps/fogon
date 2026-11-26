package io.github.fogonapps.social

import kotlinx.serialization.Serializable

/**
 * La moderación de Fogón Social: la hace una persona (el equipo con permiso, MFA y sesión elevada) sobre los reportes; no se vigila todo lo
 * que se publica (solo se mira si las fotos subidas parecen desnudos, ver [Desnudos]). Lo que reportan [UMBRAL] cuentas que cuentan se
 * oculta mientras se revisa, y lo grave ([GRAVES]) con [UMBRAL_GRAVE] o con una de confianza; las faltas suben de a una (las graves,
 * de a dos), vencen a los 90 días y traen: aviso, 24 h sin publicar, 7 días de suspensión y, a la cuarta, cierre.
 * Hay casos que cierran a la primera (ver [Moderacion.CIERRE_DIRECTO]). Cada decisión queda en `decisiones` y en el registro encadenado del equipo.
 */
object Moderacion {
    /** Los motivos de reporte (los mismos para cuentas, publicaciones, comentarios y mensajes). */
    const val MENOR_EN_RIESGO = "menor_en_riesgo"
    /** Material sexual con menores: el primero de la lista en la app. */
    const val MASI = "masi"
    /**
     * Lo que va primero en la cola, avisa al equipo en el acto, se oculta con menos reportes ([UMBRAL_GRAVE]) y, si el equipo lo confirma, queda «preservado» (no se borra: ver
     * [Social.decidir]) y cierra la cuenta.
     */
    val GRAVES = setOf(MASI, MENOR_EN_RIESGO)
    val MOTIVOS = setOf(MASI, MENOR_EN_RIESGO, "desnudos", "acoso", "odio", "autolesion", "spam", "menor_de_edad", "derechos", "falso", "otro")
    /**
     * Con cuántas cuentas distintas que cuentan se oculta algo mientras se revisa (ver Social.reportar): una cuenta
     * cuenta si tiene al menos [ANTIGUEDAD_REPORTE], no está sancionada ni bloqueada por quien publicó, no es quien
     * publicó y no le descartaron [DESCARTADOS_MAX] reportes en [VENTANA_DESCARTES]. Las creadas desde la misma
     * dirección con menos de [MISMA_DIRECCION] de diferencia cuentan como una.
     */
    const val UMBRAL = 3
    /** Lo grave se oculta con 2 cuentas que cuentan, o con 1 de confianza ([ANTIGUEDAD_CONFIANZA], sin faltas ni descartes en 90 días). */
    const val UMBRAL_GRAVE = 2
    const val ANTIGUEDAD_REPORTE = 7L * 24 * 3600_000
    const val ANTIGUEDAD_CONFIANZA = 30L * 24 * 3600_000
    const val MISMA_DIRECCION = 24L * 3600_000
    const val VENTANA_DESCARTES = 90L * 24 * 3600_000
    const val DESCARTADOS_MAX = 3
    /** Reportes por cuenta al día. */
    const val REPORTES_DIA = 10
    const val VENCE = 90L * 24 * 3600_000
    val CIERRE_DIRECTO = setOf(MASI, MENOR_EN_RIESGO, "menor_de_edad", "evadir_sancion", "amenaza_real")
    /** Si el motivo de un reporte ("masi: …") es grave. */
    fun esGrave(motivo: String) = motivo.substringBefore(':').trim() in GRAVES
    val DECISIONES = setOf("advertencia", "falta", "falta_grave", "cerrar")
    /** Las reglas con las que se puede sancionar: los motivos de reporte y los de cierre directo. */
    val REGLAS = MOTIVOS + CIERRE_DIRECTO
    /** Hasta cuándo se puede apelar una sanción. */
    const val PLAZO_APELAR = 30L * 24 * 3600_000
    /** Dónde están las normas (los términos, sección «Lo que no se permite»). */
    const val NORMAS = "https://fogonapps.duckdns.org/terminos#normas"

    /** El nombre de una regla para el correo a la persona. */
    fun nombreRegla(regla: String) = when (regla) {
        MASI -> "material sexual con menores"
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
    /** El número de la denuncia ante las autoridades, si el equipo ya lo anotó (casos graves). */
    val radicado: String = "",
    /** Lo marcó la revisión automática de fotos (no una persona). */
    val automatico: Boolean = false,
)
/** El número de la denuncia de un caso (lo anota el equipo después de denunciar). */
@Serializable data class Radicado(val que: String, val objeto: String, val radicado: String)
/**
 * Lo preservado para las autoridades (material sexual con menores o un menor en riesgo): solo lo ve la cuenta
 * principal, que lo puede exportar o, cuando ya no haga falta, liberar (borrar).
 */
@Serializable data class Preservado(val id: Long, val que: String, val objeto: String, val usuario: String, val regla: String,
    val cuando: Long, val radicado: String, val decididoPor: String)
/** Por qué la cuenta principal libera (borra) o exporta un preservado (p. ej., el número del requerimiento de la autoridad). */
@Serializable data class Liberacion(val motivo: String)
/**
 * Lo que se cifra en la base al preservar: el contenido, los reportes y la [lista] de archivos, que van cifrados por
 * partes en `preservados/<id>/` de la carpeta de datos. [archivos] (base64 dentro del sobre) es el formato de antes.
 */
@Serializable data class SobrePreservado(val contenido: String, val reportes: List<String>, val archivos: Map<String, String> = emptyMap(),
    val lista: List<String> = emptyList())
/**
 * El paquete para una autoridad: un archivo age cifrado con [frase] (al azar, se muestra una sola vez y no se guarda),
 * que se descarga en `/v1/admin/paquetes/{[archivo]}` durante 24 horas. El servidor nunca entrega el contenido en claro.
 */
@Serializable data class PaqueteAutoridad(val archivo: String, val frase: String, val bytes: Long, val sha256: String, val vence: Long)
/** Lo que va en `caso.json` dentro del paquete. */
@Serializable data class CasoExportado(val caso: Preservado, val contenido: String, val reportes: List<String>, val archivos: List<String>,
    val exportado: Long, val motivo: String)
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

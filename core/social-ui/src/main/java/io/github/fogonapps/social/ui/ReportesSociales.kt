package io.github.fogonapps.social.ui
import io.github.fogonapps.social.FaltaSocial
val MOTIVOS_REPORTE = listOf(
    "menor_en_riesgo" to R.string.social_rep_menor, "desnudos" to R.string.social_rep_desnudos, "acoso" to R.string.social_rep_acoso, "odio" to R.string.social_rep_odio,
    "autolesion" to R.string.social_rep_autolesion, "spam" to R.string.social_rep_spam, "menor_de_edad" to R.string.social_rep_menor_de_edad,
    "derechos" to R.string.social_rep_autor, "falso" to R.string.social_rep_falso, "otro" to R.string.social_rep_otro,
)

/** Presentación traducida de una falta y los puntos que suma. */
fun textoFalta(falta: FaltaSocial, traducir: (Int) -> String): String {
    val regla = MOTIVOS_REPORTE.firstOrNull { it.first == falta.regla }?.second ?: when (falta.regla) {
        "evadir_sancion" -> R.string.social_mod_evadir_sancion
        "amenaza_real" -> R.string.social_rep_violencia
        else -> R.string.social_rep_otro
    }
    if (falta.decision == "advertencia") return "${traducir(regla)} · ${traducir(R.string.sancion_advertencia)}"
    val puntos = if (falta.puntos == 2) traducir(R.string.social_mod_grave_puntos)
        else if (falta.puntos == 1) traducir(R.string.social_mod_un_punto)
        else if (falta.puntos >= 4) traducir(R.string.social_mod_cierre)
        else String.format(traducir(R.string.social_mod_puntos), falta.puntos)
    return "${traducir(regla)} · $puntos"
}

/** El servidor une los reportes de mensajes con un separador; cada uno lleva su motivo. */
fun limpiarReporteMensaje(contenido: String): String = contenido.split("\n---\n").joinToString("\n---\n") { reporte ->
    val cabecera = reporte.substringBefore('\n')
    if ('\n' in reporte && MOTIVOS_REPORTE.any { cabecera.startsWith("${it.first}:") })
        reporte.substringAfter('\n')
    else reporte
}


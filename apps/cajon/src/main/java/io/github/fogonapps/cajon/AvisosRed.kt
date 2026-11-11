package io.github.fogonapps.cajon

import android.content.Context
import androidx.work.WorkerParameters
import io.github.fogonapps.social.ui.Avisos
import io.github.fogonapps.social.ui.AvisosFondo

/**
 * Los avisos de la red con la app cerrada: la revisión común de las apps (ver [AvisosFondo]), cada 15 minutos o al
 * instante con un distribuidor UnifiedPush. La clase conserva su nombre porque WorkManager guarda el trabajo ya
 * programado con él.
 */
class AvisosRed(contexto: Context, params: WorkerParameters) : AvisosFondo(contexto, params) {
    companion object {
        const val PREFS = Avisos.PREFS
        const val AVISOS = Avisos.AVISOS
        const val MENSAJES = Avisos.MENSAJES
        fun activo(prefs: android.content.SharedPreferences, perfil: String, tipo: String) = Avisos.activo(prefs, perfil, tipo)
        fun programar(c: Context) = Avisos.programar(c, AvisosRed::class.java)
        fun vistoAvisos(c: Context, avisos: Int, perfil: String) = Avisos.visto(c, perfil, avisos = avisos)
        fun visto(c: Context, avisos: Int, mensajes: Int, perfil: String) = Avisos.visto(c, perfil, avisos, mensajes)
    }
}

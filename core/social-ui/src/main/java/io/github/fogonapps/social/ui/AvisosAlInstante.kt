package io.github.fogonapps.social.ui

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.github.fogonapps.social.ClienteSocial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/*
 * Avisos al instante (X-10) por Firebase Cloud Messaging: el servidor manda un toque vacío (sin texto ni de quién) y
 * la app hace la misma revisión de [AvisosFondo]. Firebase se inicia a mano (sin el complemento de Google ni
 * Analytics) y solo pide su token si hay una cuenta con avisos activos. Sin Google Play Services, sigue la revisión
 * de cada 15 minutos.
 */

/** El proyecto de Firebase de Fogón (estos datos van dentro de cada APK; no son secretos). */
object ProyectoFirebase {
    const val PROYECTO = "fogon-6a56b"
    const val REMITENTE = "252547803157"
    const val CLAVE = "AIzaSyCysFb46cFuI3tSZ-CopDcW-Rx3dPDyyp8"
}

object AvisosAlInstante {
    private val alcance = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Inicia Firebase con el id de la app ([AppConAvisos.avisosFirebase]); null si la app no lo tiene o falla. */
    private fun firebase(c: Context): FirebaseApp? {
        val app = c.applicationContext as? AppConAvisos ?: return null
        val id = app.avisosFirebase ?: return null
        return runCatching {
            FirebaseApp.getApps(c).firstOrNull() ?: FirebaseApp.initializeApp(c.applicationContext,
                FirebaseOptions.Builder().setApplicationId(id).setApiKey(ProyectoFirebase.CLAVE)
                    .setProjectId(ProyectoFirebase.PROYECTO).setGcmSenderId(ProyectoFirebase.REMITENTE).build())
        }.getOrNull()
    }

    /**
     * Le da (o le quita) el token al servidor por cada cuenta del teléfono, según quiera avisos. Sin ninguna cuenta
     * con avisos, el token se borra de Firebase.
     */
    fun sincronizar(c: Context, token: String? = null) {
        val app = c.applicationContext as? AppConAvisos ?: return
        val prefs = c.getSharedPreferences(Avisos.PREFS, Context.MODE_PRIVATE)
        val cuentas = app.avisosCuentas()
        val quieren = cuentas.filter { Avisos.activo(prefs, it.perfil, Avisos.AVISOS) || (app.avisosConMensajes && Avisos.activo(prefs, it.perfil, Avisos.MENSAJES)) }
        firebase(c) ?: return
        val fm = runCatching { FirebaseMessaging.getInstance() }.getOrNull() ?: return
        if (quieren.isEmpty()) {
            val viejo = prefs.getString("fcm", null)
            fm.isAutoInitEnabled = false
            if (viejo != null) {
                alcance.launch { cuentas.forEach { runCatching { ClienteSocial(it.token, { it.vigente() }, app.avisosConfiguracion).quitarEmpuje(viejo) } } }
                runCatching { fm.deleteToken() }
                prefs.edit().remove("fcm").apply()
            }
            return
        }
        fun enviar(t: String) {
            prefs.edit().putString("fcm", t).apply()
            alcance.launch {
                for (cuenta in cuentas) runCatching {
                    val cliente = ClienteSocial(cuenta.token, { cuenta.vigente() }, app.avisosConfiguracion)
                    if (cuenta in quieren) cliente.registrarEmpuje(t) else cliente.quitarEmpuje(t)
                }
            }
        }
        if (token != null) enviar(token)
        else runCatching { fm.token.addOnSuccessListener { t -> if (!t.isNullOrBlank()) enviar(t) } }
    }
}

/** Recibe el toque y el token nuevo de Firebase. El toque no trae nada: solo despierta la revisión. */
class ServicioAvisos : FirebaseMessagingService() {
    override fun onNewToken(token: String) { AvisosAlInstante.sincronizar(this, token) }
    override fun onMessageReceived(message: RemoteMessage) {
        val app = applicationContext as? AppConTrabajador
        Avisos.revisarYa(this, app?.trabajadorAvisos ?: AvisosFondo::class.java)
    }
}

package io.github.fogonapps.social.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.github.fogonapps.social.ClienteSocial
import io.github.fogonapps.social.ConfiguracionSocial
import io.github.fogonapps.social.debeAceptarPolitica
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

/*
 * Los avisos de la red con la app cerrada, iguales en las cuatro apps (X-10): al instante con el toque de
 * [AvisosAlInstante] y, de respaldo, cada 15 minutos con WorkManager (lo que deja Android), la app pregunta con su propia sesión cuántos avisos y mensajes nuevos hay ([AvisosFondo]). La
 * notificación solo dice cuántos, nunca qué ni de quién.
 */

/** Una cuenta con sesión en este teléfono. [vigente] dice si la sesión sigue siendo la misma mientras se revisa. */
data class CuentaAvisos(val perfil: String, val token: String, val usuario: String, val vigente: () -> Boolean = { true })

/** Lo que pone cada app (su Application). */
interface AppConAvisos {
    val avisosConfiguracion: ConfiguracionSocial
    /** Solo El Cajón tiene chat. */
    val avisosConMensajes: Boolean get() = false
    val avisosIcono: Int
    fun avisosCuentas(): List<CuentaAvisos>
    fun avisosAbrir(perfil: String): Intent
    /** El id de la app en el proyecto de Firebase (el `mobilesdk_app_id`), o null sin avisos al instante. */
    val avisosFirebase: String? get() = null
    /** El perfil del teléfono que está en uso (los ajustes de avisos son por perfil). */
    fun avisosPerfilActivo(): String
}

open class AvisosFondo(contexto: Context, params: WorkerParameters) : Worker(contexto, params) {
    override fun doWork(): Result {
        val app = applicationContext as? AppConAvisos ?: return Result.success()
        val c = applicationContext
        val prefs = c.getSharedPreferences(Avisos.PREFS, Context.MODE_PRIVATE)
        var reintentar = false
        for (cuenta in app.avisosCuentas()) {
            val resultado = runCatching { runBlocking {
                var permitido = false
                val cliente = ClienteSocial(cuenta.token, { permitido && cuenta.vigente() }, app.avisosConfiguracion)
                val vigente = cliente.info().politica
                permitido = vigente.isNotBlank() && !debeAceptarPolitica(true, cliente.miCuenta().politica, vigente)
                if (!permitido || !cuenta.vigente()) return@runBlocking null
                cliente.sinLeer() to (if (app.avisosConMensajes) cliente.mensajesSinLeer() else 0)
            } }.getOrNull()
            if (resultado == null) { reintentar = true; continue }
            if (!cuenta.vigente()) continue
            val (avisos, mensajes) = resultado
            val p = cuenta.perfil
            val antes = prefs.getInt("p$p.avisos", 0) to prefs.getInt("p$p.mensajes", 0)
            prefs.edit().putInt("p$p.avisos", avisos).putInt("p$p.mensajes", mensajes).apply()
            val nuevosAvisos = avisos > antes.first && Avisos.activo(prefs, p, Avisos.AVISOS)
            val nuevosMensajes = app.avisosConMensajes && mensajes > antes.second && Avisos.activo(prefs, p, Avisos.MENSAJES)
            if (!nuevosAvisos && !nuevosMensajes) continue
            val texto = listOfNotNull(
                if (nuevosAvisos) c.resources.getQuantityString(R.plurals.fondo_avisos, avisos, avisos) else null,
                if (nuevosMensajes) c.resources.getQuantityString(R.plurals.fondo_mensajes, mensajes, mensajes) else null,
            ).joinToString(" · ")
            Avisos.notificar(c, app, texto, p, cuenta.usuario)
        }
        return if (reintentar) Result.retry() else Result.success()
    }
}

object Avisos {
    const val PREFS = "avisos_red"
    const val AVISOS = "avisos_activos"
    const val MENSAJES = "mensajes_activos"
    private const val CANAL = "red"
    private const val TRABAJO = "avisos-red"
    private const val YA = "avisos-red-ya"

    fun activo(prefs: SharedPreferences, perfil: String, tipo: String): Boolean =
        prefs.getBoolean("p$perfil.$tipo", if (perfil == "1") prefs.getBoolean(tipo, true) else true)

    private fun algoActivo(c: Context): Boolean {
        val app = c.applicationContext as? AppConAvisos ?: return false
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return app.avisosCuentas().any { activo(prefs, it.perfil, AVISOS) || (app.avisosConMensajes && activo(prefs, it.perfil, MENSAJES)) }
    }

    /**
     * Prende o apaga la revisión cada 15 minutos (prendida si hay cuenta y algo activo). [trabajador] es la clase de
     * la revisión (El Cajón conserva la suya).
     */
    fun programar(c: Context, trabajador: Class<out AvisosFondo> = AvisosFondo::class.java) {
        val wm = WorkManager.getInstance(c)
        AvisosAlInstante.sincronizar(c)
        if (!algoActivo(c)) { wm.cancelUniqueWork(TRABAJO); return }
        val req = PeriodicWorkRequest.Builder(trabajador, 15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        wm.enqueueUniquePeriodicWork(TRABAJO, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Revisar ya (por ejemplo, cuando llega un toque del servidor). */
    fun revisarYa(c: Context, trabajador: Class<out AvisosFondo> = AvisosFondo::class.java) {
        val req = OneTimeWorkRequest.Builder(trabajador)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(c).enqueueUniqueWork(YA, ExistingWorkPolicy.REPLACE, req)
    }

    /** Al abrir la app ya se vio todo: lo de ahora no vuelve a avisar. */
    fun visto(c: Context, perfil: String, avisos: Int? = null, mensajes: Int? = null) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            avisos?.let { putInt("p$perfil.avisos", it) }
            mensajes?.let { putInt("p$perfil.mensajes", it) }
        }.apply()
    }

    internal fun notificar(c: Context, app: AppConAvisos, texto: String, perfil: String, usuario: String) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, c.getString(R.string.fondo_canal), NotificationManager.IMPORTANCE_DEFAULT))
        val abrir = PendingIntent.getActivity(c, perfil.toIntOrNull() ?: 0, app.avisosAbrir(perfil).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(c, CANAL).setSmallIcon(app.avisosIcono)
            .setContentTitle(app.avisosConfiguracion.nombreVisible + " · @" + usuario)
            .setContentText(texto).setContentIntent(abrir).setAutoCancel(true).build()
        runCatching { nm.notify(perfil, 7, n) }
    }
}

/** Para El Cajón, que conserva el nombre de su revisión (WorkManager guarda la clase de los trabajos ya programados). */
interface AppConTrabajador { val trabajadorAvisos: Class<out AvisosFondo> }

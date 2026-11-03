package io.github.fogonapps.cajon

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.github.fogonapps.cajon.datos.ClienteSocial
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

/**
 * Los avisos de la red con la app cerrada: cada 15 minutos (lo mínimo que deja Android, y solo con internet) pregunta
 * cuántos avisos y mensajes nuevos hay y, si subieron, lo dice en una notificación. La notificación no dice qué ni de
 * quién: solo cuántos (lo demás se ve al abrir la app). Sin servicios de Google.
 */
class AvisosRed(contexto: Context, params: WorkerParameters) : Worker(contexto, params) {
    override fun doWork(): Result {
        val app = applicationContext as CajonApp
        val token = app.caja.tokenSocial ?: return Result.success()
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val (avisos, mensajes) = runCatching { runBlocking { ClienteSocial(token).let { it.sinLeer() to it.mensajesSinLeer() } } }.getOrNull() ?: return Result.retry()
        val antes = prefs.getInt("avisos", 0) to prefs.getInt("mensajes", 0)
        prefs.edit().putInt("avisos", avisos).putInt("mensajes", mensajes).apply()
        val nuevosAvisos = avisos > antes.first && prefs.getBoolean(AVISOS, true)
        val nuevosMensajes = mensajes > antes.second && prefs.getBoolean(MENSAJES, true)
        if (!nuevosAvisos && !nuevosMensajes) return Result.success()
        val texto = listOfNotNull(
            if (nuevosAvisos) app.resources.getQuantityString(R.plurals.notif_avisos, avisos, avisos) else null,
            if (nuevosMensajes) app.resources.getQuantityString(R.plurals.notif_mensajes, mensajes, mensajes) else null,
        ).joinToString(" · ")
        notificar(app, texto)
        return Result.success()
    }

    companion object {
        const val PREFS = "avisos_red"
        const val AVISOS = "avisos_activos"
        const val MENSAJES = "mensajes_activos"
        private const val CANAL = "red"
        private const val TRABAJO = "avisos-red"

        /** Prende o apaga la revisión periódica (prendida si hay cuenta y algo activo). */
        fun programar(c: Context) {
            val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val algo = prefs.getBoolean(AVISOS, true) || prefs.getBoolean(MENSAJES, true)
            val wm = WorkManager.getInstance(c)
            if ((c.applicationContext as CajonApp).caja.tokenSocial == null || !algo) { wm.cancelUniqueWork(TRABAJO); return }
            val req = PeriodicWorkRequestBuilder<AvisosRed>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            wm.enqueueUniquePeriodicWork(TRABAJO, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun vistoAvisos(c: Context, avisos: Int) { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("avisos", avisos).apply() }

        /** Al abrir la app ya se vio todo: lo de ahora no vuelve a avisar. */
        fun visto(c: Context, avisos: Int, mensajes: Int) {
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("avisos", avisos).putInt("mensajes", mensajes).apply()
        }

        private fun notificar(c: Context, texto: String) {
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CANAL, c.getString(R.string.notif_canal), NotificationManager.IMPORTANCE_DEFAULT))
            val abrir = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(c, CANAL).setSmallIcon(R.drawable.ic_notificacion).setContentTitle(c.getString(R.string.notif_titulo))
                .setContentText(texto).setContentIntent(abrir).setAutoCancel(true).build()
            runCatching { nm.notify(7, n) }
        }
    }
}

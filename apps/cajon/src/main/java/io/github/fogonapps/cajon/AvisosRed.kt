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
        var reintentar = false
        for (perfil in app.perfiles.lista.perfiles) {
            val caja = runCatching { app.recursos(perfil.id).caja }.getOrNull() ?: continue
            val token = caja.tokenSocial ?: continue
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val resultado = runCatching { runBlocking { ClienteSocial(token).let { it.sinLeer() to it.mensajesSinLeer() } } }.getOrNull()
            if (resultado == null) { reintentar = true; continue }
            if (caja.tokenSocial != token || app.perfiles.lista.perfiles.none { it.id == perfil.id }) continue
            val (avisos, mensajes) = resultado
            val antes = prefs.getInt("p${perfil.id}.avisos", 0) to prefs.getInt("p${perfil.id}.mensajes", 0)
            prefs.edit().putInt("p${perfil.id}.avisos", avisos).putInt("p${perfil.id}.mensajes", mensajes).apply()
            val nuevosAvisos = avisos > antes.first && activo(prefs, perfil.id, AVISOS)
            val nuevosMensajes = mensajes > antes.second && activo(prefs, perfil.id, MENSAJES)
            if (!nuevosAvisos && !nuevosMensajes) continue
            val texto = listOfNotNull(
                if (nuevosAvisos) app.resources.getQuantityString(R.plurals.notif_avisos, avisos, avisos) else null,
                if (nuevosMensajes) app.resources.getQuantityString(R.plurals.notif_mensajes, mensajes, mensajes) else null,
            ).joinToString(" · ")
            notificar(app, texto, perfil.id, caja.usuarioSocial.orEmpty())
        }
        return if (reintentar) Result.retry() else Result.success()
    }

    companion object {
        const val PREFS = "avisos_red"
        const val AVISOS = "avisos_activos"
        const val MENSAJES = "mensajes_activos"
        private const val CANAL = "red"
        private const val TRABAJO = "avisos-red"

        fun activo(prefs: android.content.SharedPreferences, perfil: String, tipo: String): Boolean =
            prefs.getBoolean("p$perfil.$tipo", if (perfil == "1") prefs.getBoolean(tipo, true) else true)

        /** Prende o apaga la revisión periódica (prendida si hay cuenta y algo activo). */
        fun programar(c: Context) {
            val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val app = c.applicationContext as CajonApp
            val algo = app.perfiles.lista.perfiles.any { perfil ->
                runCatching { app.recursos(perfil.id).caja.tokenSocial }.getOrNull() != null &&
                    (activo(prefs, perfil.id, AVISOS) || activo(prefs, perfil.id, MENSAJES))
            }
            val wm = WorkManager.getInstance(c)
            if (!algo) { wm.cancelUniqueWork(TRABAJO); return }
            val req = PeriodicWorkRequestBuilder<AvisosRed>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            wm.enqueueUniquePeriodicWork(TRABAJO, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun vistoAvisos(c: Context, avisos: Int, perfil: String) { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("p$perfil.avisos", avisos).apply() }

        /** Al abrir la app ya se vio todo: lo de ahora no vuelve a avisar. */
        fun visto(c: Context, avisos: Int, mensajes: Int, perfil: String) {
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("p$perfil.avisos", avisos).putInt("p$perfil.mensajes", mensajes).apply()
        }

        private fun notificar(c: Context, texto: String, perfil: String, usuario: String) {
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CANAL, c.getString(R.string.notif_canal), NotificationManager.IMPORTANCE_DEFAULT))
            val abrir = PendingIntent.getActivity(c, perfil.toInt(), Intent(c, MainActivity::class.java).putExtra("perfil", perfil).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(c, CANAL).setSmallIcon(R.drawable.ic_notificacion).setContentTitle(c.getString(R.string.notif_titulo) + " · @$usuario")
                .setContentText(texto).setContentIntent(abrir).setAutoCancel(true).build()
            runCatching { nm.notify(perfil, 7, n) }
        }
    }
}

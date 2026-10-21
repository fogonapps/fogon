package io.github.fogonapps.forzudo

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Notificación de "descanso terminado" y vibración. */
object Avisos {
    private const val CANAL = "descanso"
    private const val ID = 1

    fun crearCanal(c: Context) {
        val canal = NotificationChannel(CANAL, "Fin del descanso", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Avisa cuando termina el descanso entre series"
            enableVibration(true)
        }
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    private fun pendiente(c: Context) = PendingIntent.getBroadcast(
        c, ID, Intent(c, AvisoDescanso::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Programa el aviso. Si el sistema no permite alarmas exactas, usa una aproximada. */
    fun programar(c: Context, cuando: Long) {
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = pendiente(c)
        val exacta = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exacta) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando, pi)
    }

    fun cancelar(c: Context) {
        c.getSystemService(AlarmManager::class.java).cancel(pendiente(c))
        NotificationManagerCompat.from(c).cancel(ID)
    }

    fun mostrar(c: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val abrir = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(c, CANAL)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle("Descanso terminado")
            .setContentText("¡A la siguiente serie!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(c).notify(ID, n)
    }

    fun vibrar(c: Context, ms: Long) {
        val v = if (Build.VERSION.SDK_INT >= 31) c.getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") c.getSystemService(Vibrator::class.java)
        runCatching { v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }
}

class AvisoDescanso : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Avisos.mostrar(context)
        Avisos.vibrar(context, 400)
    }
}

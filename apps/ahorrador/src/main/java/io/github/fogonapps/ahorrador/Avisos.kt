package io.github.fogonapps.ahorrador

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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.fogonapps.ahorrador.datos.Formato
import io.github.fogonapps.identidad.Idioma
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * El aviso de pagos fijos por vencer. Una alarma aproximada cada mañana (9:00) revisa los fijos
 * de los próximos 3 días que no se han pagado y, si hay, muestra una notificación.
 */
object Avisos {
    private const val CANAL = "pagos"
    private const val ID = 1

    private const val CANAL_RECORDATORIO = "recordatorio"
    private const val ID_RECORDATORIO = 2

    fun crearCanal(c: Context) {
        val canal = NotificationChannel(CANAL, c.getString(R.string.canal_pagos), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = c.getString(R.string.canal_pagos_detalle)
        }
        val recordatorio = NotificationChannel(CANAL_RECORDATORIO, c.getString(R.string.canal_recordatorio), NotificationManager.IMPORTANCE_LOW).apply {
            description = c.getString(R.string.canal_recordatorio_detalle)
        }
        c.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(canal, recordatorio))
    }

    /** El recordatorio diario para anotar los gastos, a la [hora] (0–23); con -1 se apaga. */
    fun programarRecordatorio(c: Context, hora: Int) {
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(c, 1, Intent(c, RecordatorioGastos::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.cancel(pi)
        if (hora !in 0..23) return
        var cuando = LocalDate.now().atTime(LocalTime.of(hora, 0))
        if (cuando.isBefore(java.time.LocalDateTime.now())) cuando = cuando.plusDays(1)
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cuando.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), AlarmManager.INTERVAL_DAY, pi)
    }

    fun mostrarRecordatorio(c0: Context) {
        val c = Idioma.envolver(c0)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val abrir = PendingIntent.getActivity(c, 1, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(c, CANAL_RECORDATORIO)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(c.getString(R.string.recordatorio_titulo))
            .setContentText(c.getString(R.string.recordatorio_texto))
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .build()
        c.getSystemService(NotificationManager::class.java).notify(ID_RECORDATORIO, n)
    }

    fun programar(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(c, 0, Intent(c, AvisoPagos::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        var cuando = LocalDate.now().atTime(LocalTime.of(9, 0))
        if (cuando.isBefore(java.time.LocalDateTime.now())) cuando = cuando.plusDays(1)
        val ms = cuando.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, ms, AlarmManager.INTERVAL_DAY, pi)
    }

    fun mostrar(c0: Context, texto: String) {
        val c = Idioma.envolver(c0)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val abrir = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(c, CANAL)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(c.getString(R.string.aviso_pagos_titulo))
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .build()
        c.getSystemService(NotificationManager::class.java).notify(ID, n)
    }
}

/** Revisa cada mañana los pagos fijos por vencer. */
class AvisoPagos : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as AhorradorApp
        val pendiente = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val e = app.almacen.data.first()
                val lista = app.logica.porVencer(e, LocalDate.now())
                if (lista.isNotEmpty()) {
                    val c = Idioma.envolver(context)
                    val texto = lista.joinToString(" · ") { (f, d) -> c.getString(R.string.aviso_pago_linea, f.nombre, Formato.pesos(f.monto), Formato.fechaCorta(d)) }
                    Avisos.mostrar(context, texto)
                }
            } finally {
                pendiente.finish()
            }
        }
    }
}

/** El recordatorio diario: solo avisa si hoy no se anotó nada. */
class RecordatorioGastos : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as AhorradorApp
        val pendiente = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val e = app.almacen.data.first()
                if (e.listo && e.movimientos.none { it.fecha == LocalDate.now().toString() }) Avisos.mostrarRecordatorio(context)
            } finally {
                pendiente.finish()
            }
        }
    }
}

/** Al reiniciar el teléfono, las alarmas se borran: se vuelven a programar los avisos. */
class ArranqueTelefono : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Avisos.programar(context)
            Avisos.programarRecordatorio(context, context.getSharedPreferences("ui", Context.MODE_PRIVATE).getInt("recordatorio", -1))
        }
    }
}

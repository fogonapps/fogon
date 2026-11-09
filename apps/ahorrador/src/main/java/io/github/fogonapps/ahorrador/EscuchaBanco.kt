package io.github.fogonapps.ahorrador

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.fogonapps.ahorrador.datos.*
import io.github.fogonapps.identidad.Idioma
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** Solo conserva los campos de una regla reconocida; nunca el SMS ni los extras originales. */
class EscuchaBanco : NotificationListenerService() {
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    override fun onListenerConnected() {
        super.onListenerConnected()
        BancoAvisos.conexion(this, true)
    }
    override fun onListenerDisconnected() {
        BancoAvisos.conexion(this, false)
        super.onListenerDisconnected()
    }
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val desde = BancoAvisos.desde(this)
        val cuando = momentoAlerta(desde, sbn.postTime, sbn.notification.`when`) ?: return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val sms = Telephony.Sms.getDefaultSmsPackage(this)
        if (!paqueteAdmitido(sbn.packageName, sms, setOf(Banco.BANCOLOMBIA, Banco.NU))) return
        // DataStore es la fuente de verdad también en la primera alerta, antes de cualquier collect.
        val bancos = runBlocking(Dispatchers.IO) { (application as AhorradorApp).almacen.data.first().notificaciones.elegidos }
        if (!paqueteAdmitido(sbn.packageName, sms, bancos)) return
        val extras = sbn.notification.extras
        val textos = listOf(Notification.EXTRA_BIG_TEXT, Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT)
            .map { normalizarAlerta(extras.getCharSequence(it)?.toString().orEmpty()) } +
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty().map { normalizarAlerta(it.toString()) }
        val banco = textos.firstNotNullOfOrNull { bancoDelTexto(sbn.packageName, sms, bancos, it) } ?: return
        val titulos = listOf(Notification.EXTRA_TITLE_BIG, Notification.EXTRA_TITLE)
            .map { normalizarAlerta(extras.getCharSequence(it)?.toString().orEmpty()) }
        val a = reconocerExtrasBanco(banco, titulos, textos, Instant.ofEpochMilli(cuando).atZone(ZoneId.systemDefault()).toLocalDateTime())
        BancoAvisos.contar(this, banco, a != null)
        // Solo los datos estructurados cruzan al trabajo asíncrono.
        alcance.launch {
            val almacen = (application as AhorradorApp).almacen
            if (a == null) {
                var avisar = false
                almacen.updateData { e ->
                    if (banco !in e.notificaciones.elegidos || BancoAvisos.desde(this@EscuchaBanco) != desde || banco in e.notificaciones.noEntendidos) e
                    else { avisar = true; e.copy(notificaciones = e.notificaciones.copy(noEntendidos = e.notificaciones.noEntendidos + banco)) }
                }
                if (avisar) BancoAvisos.noEntendido(this@EscuchaBanco, banco)
            } else {
                var nuevo = false
                val e = almacen.updateData { anterior ->
                    if (BancoAvisos.desde(this@EscuchaBanco) != desde) anterior else {
                        val recibido = recibirBanco(anterior, a)
                        nuevo = recibido != anterior
                        val resultado = if (nuevo && a.banco in recibido.notificaciones.solos) confirmarBanco(recibido, a.huella, automatico = true) else recibido
                        if (resultado != anterior) resultado.copy(updatedAt = System.currentTimeMillis()) else anterior
                    }
                }
                if (nuevo) { BancoAvisos.actualizar(this@EscuchaBanco, e); WidgetPlata.actualizar(this@EscuchaBanco) }
            }
        }
    }
    override fun onDestroy() { BancoAvisos.conexion(this, false); alcance.cancel(); super.onDestroy() }
}

object BancoAvisos {
    // La conexión pertenece al proceso: un cierre abrupto no debe dejar un «conectado» persistente.
    @Volatile var conectado = false
        private set
    const val EXTRA_BANCO = "banco"
    const val EXTRA_EDITAR = "editar_banco"
    private const val CANAL = "banco"
    fun desde(c: Context) = c.getSharedPreferences("banco", Context.MODE_PRIVATE).getLong("desde", 0)
    fun activar(c: Context, si: Boolean) {
        c.getSharedPreferences("banco", Context.MODE_PRIVATE).edit { putLong("desde", if (si) System.currentTimeMillis() else 0) }
    }
    fun conexion(c: Context, conectado: Boolean) {
        this.conectado = conectado
        c.getSharedPreferences("banco", Context.MODE_PRIVATE).edit {
            putBoolean("conectado", conectado); putLong("conexion", System.currentTimeMillis())
        }
    }
    @Synchronized
    fun contar(c: Context, banco: Banco, entendida: Boolean) {
        val p = c.getSharedPreferences("banco", Context.MODE_PRIVATE)
        p.edit {
            putLong("vistas:$banco", p.getLong("vistas:$banco", 0) + 1)
            putLong("entendidas:$banco", p.getLong("entendidas:$banco", 0) + if (entendida) 1 else 0)
            putLong("ultima:$banco", System.currentTimeMillis())
        }
    }
    fun conPermiso(c: Context) = NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)
    private fun id(huella: String) = "banco:$huella"
    fun cancelar(c: Context, huella: String) = c.getSystemService(NotificationManager::class.java).cancel(id(huella), 0)
    private fun abrir(c: Context, huella: String, editar: Boolean = false): PendingIntent = PendingIntent.getActivity(c, 0,
        Intent(c, MainActivity::class.java).setAction("banco:$huella:$editar").putExtra(EXTRA_BANCO, huella).putExtra(EXTRA_EDITAR, editar),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun accion(c: Context, huella: String, accion: String): PendingIntent = PendingIntent.getBroadcast(c, 0,
        Intent(c, AccionBanco::class.java).setAction(accion).setData("alcancia://banco/$huella".toUri()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun base(c: Context): NotificationCompat.Builder {
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CANAL, c.getString(R.string.banco_titulo), NotificationManager.IMPORTANCE_DEFAULT))
        return NotificationCompat.Builder(c, CANAL).setSmallIcon(R.drawable.ic_notificacion)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setAutoCancel(true)
    }
    @android.annotation.SuppressLint("MissingPermission")
    private fun mostrar(c: Context, tag: String, n: Notification) {
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        c.getSystemService(NotificationManager::class.java).notify(tag, 0, n)
    }
    fun noEntendido(c0: Context, banco: Banco) {
        val c = Idioma.envolver(c0)
        mostrar(c, "no-entendido:$banco", base(c).setContentTitle(c.getString(R.string.banco_no_entendido, c.getString(banco.nombreBanco)))
            .setContentText(c.getString(R.string.banco_manual)).setContentIntent(abrir(c, "")).build())
    }
    fun actualizar(c0: Context, e: Estado) {
        val c = Idioma.envolver(c0)
        e.notificaciones.registros.forEach { r ->
            val h = r.aviso.huella
            if (r.descartado || r.movimiento != null || desde(c) == 0L || r.aviso.banco !in e.notificaciones.elegidos) { cancelar(c, h); return@forEach }
            val m = e.propuestaBanco(h)
            val n = base(c).setContentTitle(c.getString(R.string.banco_aviso, c.getString(when (m?.tipo ?: r.aviso.tipo) {
                TipoMovimiento.INGRESO -> R.string.ingreso
                TipoMovimiento.TRASPASO -> R.string.pasar
                else -> R.string.gasto
            }), Formato.pesos(r.aviso.monto), c.getString(r.aviso.banco.nombreBanco)))
                .setContentText(if (m == null) c.getString(R.string.banco_enlazar) else r.aviso.texto)
                .setOnlyAlertOnce(true).setContentIntent(abrir(c, h))
            if (m != null) {
                n.addAction(0, c.getString(R.string.anotar), accion(c, h, "anotar"))
                n.addAction(0, c.getString(R.string.editar), abrir(c, h, true))
            }
            n.addAction(0, c.getString(R.string.banco_no), accion(c, h, "no"))
            mostrar(c, id(h), n.build())
        }
    }
}

/** Las acciones solo aceptan huellas de pendientes guardados, nunca datos de movimientos en el Intent. */
class AccionBanco : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val huella = intent.data?.lastPathSegment ?: return
        val accion = intent.action
        if (accion !in setOf("anotar", "no")) return
        val pendiente = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val e = (context.applicationContext as AhorradorApp).almacen.updateData {
                    if (accion == "no") descartarBanco(it, huella)
                    else if (BancoAvisos.desde(context) != 0L) confirmarBanco(it, huella) else it
                }
                BancoAvisos.actualizar(context, e)
                WidgetPlata.actualizar(context)
            } finally { pendiente.finish() }
        }
    }
}

val Banco.nombreBanco: Int get() = when (this) {
    Banco.BANCOLOMBIA -> R.string.banco_bancolombia
    Banco.NU -> R.string.banco_nu
    Banco.NEQUI -> R.string.banco_nequi
    Banco.DAVIVIENDA -> R.string.banco_davivienda
    Banco.DAVIPLATA -> R.string.banco_daviplata
    Banco.BOGOTA -> R.string.banco_bogota
    Banco.BBVA -> R.string.banco_bbva
    Banco.OTRO -> R.string.banco_otro
}

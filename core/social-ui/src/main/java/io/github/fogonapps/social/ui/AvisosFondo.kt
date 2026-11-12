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
 * [AvisosAlInstante] y, de respaldo, cada 15 minutos con WorkManager (lo que deja Android), la app pregunta con su
 * propia sesión qué hay de nuevo ([AvisosFondo]). La notificación dice quién y qué («@pedro empezó a seguirte»,
 * «@ana te escribió»), nunca el texto de un mensaje; en la pantalla bloqueada, solo cuántos.
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
                val conteo = cliente.sinLeer() to (if (app.avisosConMensajes) cliente.mensajesSinLeer() else 0)
                // Quién y qué, solo si hay algo nuevo (para no pedir de más).
                val p = cuenta.perfil
                val ultimos = if (conteo.first > prefs.getInt("p$p.avisos", 0)) runCatching { cliente.avisos().filter { !it.leido } }.getOrDefault(emptyList()) else emptyList()
                // Los chats silenciados (los guarda la app en "p<perfil>.silenciados") no avisan.
                val silenciados = prefs.getStringSet("p$p.silenciados", emptySet()).orEmpty()
                val todos = if (app.avisosConMensajes && conteo.second > prefs.getInt("p$p.mensajes", 0)) cliente.remitentesConUsuario() else emptyList()
                val remitentes = todos.filter { it.first !in silenciados }.map { it.second }
                if (todos.isNotEmpty() && remitentes.isEmpty()) prefs.edit().putInt("p$p.mensajes", conteo.second).apply()
                Triple(conteo, ultimos, remitentes)
            } }.getOrNull()
            if (resultado == null) { reintentar = true; continue }
            if (!cuenta.vigente()) continue
            val (conteo, ultimos, remitentes) = resultado
            val (avisos, mensajes) = conteo
            val p = cuenta.perfil
            val antes = prefs.getInt("p$p.avisos", 0) to prefs.getInt("p$p.mensajes", 0)
            prefs.edit().putInt("p$p.avisos", avisos).putInt("p$p.mensajes", mensajes).apply()
            val nuevosAvisos = avisos > antes.first && Avisos.activo(prefs, p, Avisos.AVISOS)
            val nuevosMensajes = app.avisosConMensajes && mensajes > antes.second && Avisos.activo(prefs, p, Avisos.MENSAJES)
            if (!nuevosAvisos && !nuevosMensajes) continue
            // En la pantalla bloqueada solo se ve cuántos; desbloqueado, quién y qué (nunca el texto de un mensaje).
            val cuantos = listOfNotNull(
                if (nuevosAvisos) c.resources.getQuantityString(R.plurals.fondo_avisos, avisos, avisos) else null,
                if (nuevosMensajes) c.resources.getQuantityString(R.plurals.fondo_mensajes, mensajes, mensajes) else null,
            ).joinToString(" · ")
            val detalle = listOfNotNull(
                if (nuevosAvisos) ultimos.maxByOrNull { it.cuando }?.let { a -> Avisos.frase(c, a).let { f -> if (avisos > 1) c.getString(R.string.fondo_y_mas, f, avisos - 1) else f } } else null,
                if (nuevosMensajes) remitentes.takeIf { it.isNotEmpty() }?.let { r ->
                    if (r.size == 1) c.getString(R.string.fondo_te_escribio, r[0]) else c.getString(R.string.fondo_te_escribieron, r.take(3).joinToString(", "))
                } else null,
            ).joinToString(" · ").ifBlank { cuantos }
            Avisos.notificar(c, app, detalle, p, cuenta.usuario, publico = cuantos)
        }
        // Una vez al día por cuenta: los cumpleaños de los amigos.
        val hoy = java.time.LocalDate.now()
        val clave = "%02d-%02d".format(hoy.monthValue, hoy.dayOfMonth)
        for (cuenta in app.avisosCuentas()) {
            val p = cuenta.perfil
            if (prefs.getString("p$p.cumples", "") == hoy.toString() || !Avisos.activo(prefs, p, Avisos.AVISOS)) continue
            val cumples = runCatching { runBlocking { ClienteSocial(cuenta.token, { cuenta.vigente() }, app.avisosConfiguracion).cumplesAmigos() } }.getOrNull() ?: continue
            prefs.edit().putString("p$p.cumples", hoy.toString()).apply()
            Avisos.cumplesDeHoy(cumples, clave).forEachIndexed { i, a ->
                Avisos.notificar(c, app, c.getString(R.string.cumple_hoy_aviso, a.persona.nombre.ifBlank { a.persona.usuario }), p, cuenta.usuario, id = 100 + i)
            }
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

    /** Un aviso en palabras, con el nombre de la persona (no su @): «Pedro empezó a seguirte». */
    internal fun frase(c: Context, a: io.github.fogonapps.social.AvisoSocial): String {
        if (a.tipo == "sancion") return c.getString(R.string.social_red_aviso_sancion)
        val que = c.getString(when (a.tipo) {
            "respuesta" -> R.string.social_publicacion_aviso_respuesta; "megusta" -> R.string.social_red_aviso_megusta
            "comentario" -> R.string.social_red_aviso_comentario; "seguir" -> R.string.social_red_aviso_seguir
            "solicitud" -> R.string.social_red_aviso_solicitud; "aceptada" -> R.string.social_red_aviso_aceptada
            "publico" -> R.string.social_red_aviso_publico; "historia_megusta" -> R.string.social_red_aviso_historia; else -> R.string.social_red_aviso_otro
        })
        return "${a.de.nombre.ifBlank { a.de.usuario }} $que"
    }

    /** Los amigos que cumplen años en [hoy] ("MM-DD"). */
    fun cumplesDeHoy(l: List<io.github.fogonapps.social.CumpleAmigo>, hoy: String) = l.filter { it.fecha == hoy }

    internal fun notificar(c: Context, app: AppConAvisos, texto: String, perfil: String, usuario: String, publico: String = texto, id: Int = 7) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, c.getString(R.string.fondo_canal), NotificationManager.IMPORTANCE_DEFAULT))
        val abrir = PendingIntent.getActivity(c, perfil.toIntOrNull() ?: 0, app.avisosAbrir(perfil).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        // Con una sola cuenta basta el nombre de la app; con varias, se dice a cuál le llegó.
        val titulo = app.avisosConfiguracion.nombreVisible + if (app.avisosCuentas().size > 1 && usuario.isNotBlank()) " · @$usuario" else ""
        val publica = NotificationCompat.Builder(c, CANAL).setSmallIcon(app.avisosIcono).setContentTitle(titulo).setContentText(publico).build()
        val n = NotificationCompat.Builder(c, CANAL).setSmallIcon(app.avisosIcono)
            .setContentTitle(titulo).setContentText(texto).setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(publica)
            .setContentIntent(abrir).setAutoCancel(true).build()
        runCatching { nm.notify(perfil, id, n) }
    }
}

/** Para El Cajón, que conserva el nombre de su revisión (WorkManager guarda la clase de los trabajos ya programados). */
interface AppConTrabajador { val trabajadorAvisos: Class<out AvisosFondo> }

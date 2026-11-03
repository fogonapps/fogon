package io.github.fogonapps.cajon

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import androidx.core.content.edit
import io.github.fogonapps.cajon.datos.Escucha

/**
 * Anota lo que suena en cualquier app de música (Spotify, YouTube Music, el reproductor del teléfono…): Android deja
 * leer la canción de las sesiones de reproducción a las apps con "acceso a notificaciones". No lee ninguna
 * notificación ni manda nada a internet: solo artista, álbum y canción, y solo si la persona lo activó en Ajustes
 * ([activado]). Cuenta una canción si sonó al menos 30 segundos (o la mitad, si es más corta).
 */
class EscuchaMusica : NotificationListenerService() {
    private val principal = Handler(Looper.getMainLooper())
    private var sesiones: MediaSessionManager? = null
    private val seguidas = mutableMapOf<MediaController, Seguimiento>()

    /** Lo que suena en una app: la canción, cuánto lleva sonando y desde cuándo (si está sonando ahora). */
    private inner class Seguimiento(val c: MediaController) : MediaController.Callback() {
        var meta: MediaMetadata? = c.metadata
        var acumulado = 0L
        var desde: Long? = if (c.playbackState?.state == PlaybackState.STATE_PLAYING) System.currentTimeMillis() else null

        fun cerrar() {
            val m = meta ?: return
            val total = acumulado + (desde?.let { System.currentTimeMillis() - it } ?: 0L)
            val duracion = m.getLong(MediaMetadata.METADATA_KEY_DURATION)
            val minimo = if (duracion in 1..59_999) duracion / 2 else 30_000L
            if (total >= minimo) anotar(c.packageName, m)
            acumulado = 0; desde = if (c.playbackState?.state == PlaybackState.STATE_PLAYING) System.currentTimeMillis() else null
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            if (metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) == meta?.getString(MediaMetadata.METADATA_KEY_TITLE) &&
                metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) == meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)) { meta = metadata; return }
            cerrar(); meta = metadata
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            val sonando = state?.state == PlaybackState.STATE_PLAYING
            if (sonando && desde == null) desde = System.currentTimeMillis()
            if (!sonando && desde != null) { acumulado += System.currentTimeMillis() - desde!!; desde = null }
        }

        override fun onSessionDestroyed() { cerrar(); seguidas.remove(c); c.unregisterCallback(this) }
    }

    private val alCambiar = MediaSessionManager.OnActiveSessionsChangedListener { lista -> seguir(lista.orEmpty()) }

    private fun seguir(lista: List<MediaController>) {
        val token = lista.map { it.sessionToken }.toSet()
        seguidas.keys.filter { it.sessionToken !in token }.forEach { c -> seguidas.remove(c)?.let { it.cerrar(); c.unregisterCallback(it) } }
        lista.filter { c -> seguidas.keys.none { it.sessionToken == c.sessionToken } }.forEach { c ->
            val s = Seguimiento(c); seguidas[c] = s; c.registerCallback(s, principal)
        }
    }

    override fun onListenerConnected() {
        val m = getSystemService(MediaSessionManager::class.java) ?: return
        sesiones = m
        val yo = ComponentName(this, EscuchaMusica::class.java)
        runCatching {
            m.addOnActiveSessionsChangedListener(alCambiar, yo, principal)
            seguir(m.getActiveSessions(yo))
        }
    }

    override fun onListenerDisconnected() {
        runCatching { sesiones?.removeOnActiveSessionsChangedListener(alCambiar) }
        seguidas.forEach { (c, s) -> s.cerrar(); c.unregisterCallback(s) }
        seguidas.clear()
    }

    private fun anotar(paquete: String, m: MediaMetadata) {
        if (!activado(this) || paquete == packageName) return
        val artista = (m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: m.getString(MediaMetadata.METADATA_KEY_ARTIST)).orEmpty()
        val album = m.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
        val cancion = m.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        // Sin álbum (un video, un podcast mal etiquetado) no se anota.
        if (artista.isBlank() || album.isBlank() || cancion.isBlank()) return
        val app = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(paquete, 0)).toString() }.getOrDefault(paquete)
        (application as CajonApp).escuchas.sumar(listOf(Escucha(artista, album, cancion, System.currentTimeMillis(), app, paquete)))
    }

    companion object {
        private const val PREFS = "musica"
        fun activado(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("anotar", false)
        fun activar(c: Context, si: Boolean) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean("anotar", si) }
        fun agregarSolos(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("solos", false)
        fun ponerAgregarSolos(c: Context, si: Boolean) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean("solos", si) }
        /** Si la persona le dio a El Cajón el acceso a notificaciones (lo que permite leer lo que suena). */
        fun conPermiso(c: Context): Boolean =
            androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)
    }
}

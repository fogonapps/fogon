package io.github.fogonapps.social.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import io.github.fogonapps.social.ClienteSocial
import io.github.fogonapps.social.cargarVideo
import io.github.fogonapps.identidad.*
import kotlinx.coroutines.delay

/** Solo la portada usa la red del feed; el reproductor nace al tocar. */
@Composable
fun VideoPublicacion(cabecerasVideo: () -> Map<String, String>, nombre: String, poster: String, segundos: Int, descripcionAccesible: String? = null) {
    val contexto = LocalContext.current
    val cabeceras = runCatching { cabecerasVideo() }.getOrNull()
    val solicitud = remember(poster, cabeceras) {
        cabeceras?.let { valores ->
            ImageRequest.Builder(contexto).data(ClienteSocial.imagen(poster))
                .httpHeaders(NetworkHeaders.Builder().apply { valores.forEach { (clave, valor) -> set(clave, valor) } }.build())
                .memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build()
        }
    }
    var abierto by remember(nombre) { mutableStateOf(false) }
    val descripcion = descripcionAccesible ?: pluralStringResource(R.plurals.social_video_reproducir, segundos, segundos)
    Box(Modifier.fillMaxWidth().aspectRatio(1f).clickable { abierto = true }.semantics { contentDescription = descripcion }) {
        AsyncImage(solicitud, null, Modifier.fillMaxSize())
        Box(Modifier.align(Alignment.Center).size(52.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .background(FogonTema.actual.fondo.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
            IconoRiso(IconosMultimedia.Reproducir, null, tamano = 28.dp, tinta = FogonTema.actual.texto)
        }
    }
    if (cargarVideo(abierto)) ReproductorVideo(cabecerasVideo, nombre) { abierto = false }
}

@OptIn(UnstableApi::class)
@Composable
fun ReproductorVideo(cabecerasVideo: () -> Map<String, String>, nombre: String, cerrar: () -> Unit) {
    val contexto = LocalContext.current
    val f = FogonTema.actual
    val propietario = LocalLifecycleOwner.current
    val etiquetaBarra = stringResource(R.string.social_video_barra)
    var error by remember(nombre) { mutableStateOf(false) }
    val reproductor = remember(nombre) {
        runCatching {
            val fuente = DefaultHttpDataSource.Factory().setDefaultRequestProperties(cabecerasVideo())
            ExoPlayer.Builder(contexto).setMediaSourceFactory(DefaultMediaSourceFactory(contexto).setDataSourceFactory(fuente)).build().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
                setHandleAudioBecomingNoisy(true)
                setMediaItem(MediaItem.fromUri(ClienteSocial.video(nombre)))
                addListener(object : Player.Listener {
                    override fun onPlayerError(e: androidx.media3.common.PlaybackException) { error = true }
                })
                prepare(); playWhenReady = true
            }
        }.getOrElse { error = true; null }
    }
    var posicion by remember { mutableLongStateOf(0) }
    var duracion by remember { mutableLongStateOf(0) }
    var sonando by remember { mutableStateOf(true) }
    LaunchedEffect(reproductor) {
        while (true) {
            posicion = reproductor?.currentPosition ?: 0
            duracion = (reproductor?.duration ?: 0).coerceAtLeast(0)
            sonando = reproductor?.playWhenReady == true && reproductor.playbackState != Player.STATE_ENDED
            delay(250)
        }
    }
    DisposableEffect(reproductor, propietario) {
        val observador = LifecycleEventObserver { _, evento -> if (evento == Lifecycle.Event.ON_STOP) reproductor?.pause() }
        propietario.lifecycle.addObserver(observador)
        onDispose { propietario.lifecycle.removeObserver(observador); reproductor?.release() }
    }
    Dialog(cerrar, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(f.fondo).systemBarsPadding().padding(16.dp)) {
            BotonSecundario(stringResource(R.string.social_video_cerrar), cerrar)
            if (error) Text(stringResource(R.string.social_video_error), color = f.texto)
            AndroidView(factory = { PlayerView(it).apply { useController = false; /* sin foco: Atrás llega al diálogo y lo cierra */ isFocusable = false; isFocusableInTouchMode = false; player = reproductor; setShutterBackgroundColor(androidx.compose.ui.graphics.Color.Transparent.toArgb()) } },
                modifier = Modifier.fillMaxWidth().weight(1f), onRelease = { it.player = null })
            Text(pluralStringResource(R.plurals.social_video_tiempo, (duracion / 1000).toInt(), posicion / 1000, duracion / 1000), color = f.texto)
            Slider(value = posicion.coerceAtMost(duracion).toFloat(), onValueChange = { reproductor?.seekTo(it.toLong()) },
                valueRange = 0f..duracion.coerceAtLeast(1).toFloat(), enabled = duracion > 0,
                modifier = Modifier.semantics { contentDescription = etiquetaBarra },
                colors = SliderDefaults.colors(thumbColor = f.tinta1, activeTrackColor = f.tinta1, inactiveTrackColor = f.linea))
            BotonRiso(stringResource(if (sonando) R.string.social_video_pausa else R.string.social_video_play), {
                if (reproductor?.playbackState == Player.STATE_ENDED) { reproductor.seekTo(0); reproductor.play() }
                else if (reproductor?.playWhenReady == true) reproductor.pause() else reproductor?.play()
            }, Modifier.fillMaxWidth(), habilitado = reproductor != null && !error)
        }
    }
}

package io.github.fogonapps.social.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.perfil.menosMovimientoBanner
import io.github.fogonapps.perfil.visibilidadBanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

private const val MAX_PORTADA_ANIMADA = 3_000_000
private const val MAX_PORTADA_VIDEO = 6_000_000

/** Un MP4 lleva «ftyp» a partir del cuarto byte. */
internal fun esVideoPortada(bytes: ByteArray): Boolean =
    bytes.size > 12 && String(bytes, 4, 4, Charsets.US_ASCII) == "ftyp"

@Composable
internal fun ElegirPortadaAnimada(vm: InterfazSocial) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    var pendiente by remember { mutableStateOf<ByteArray?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var video by remember { mutableStateOf<android.net.Uri?>(null) }
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        // Un video se recorta antes de subirlo; GIF y WebP van tal cual.
        if (uri != null && contexto.contentResolver.getType(uri)?.startsWith("video/") == true) { error = false; video = uri }
        else if (uri != null) alcance.launch {
            ocupado = true
            error = false
            try {
                pendiente = withContext(Dispatchers.IO) {
                    contexto.contentResolver.openInputStream(uri)?.use { entrada ->
                        val salida = java.io.ByteArrayOutputStream()
                        val bloque = ByteArray(8192)
                        while (true) {
                            val leidos = entrada.read(bloque)
                            if (leidos < 0) break
                            check(salida.size() + leidos <= MAX_PORTADA_VIDEO)
                            salida.write(bloque, 0, leidos)
                        }
                        salida.toByteArray().also {
                            if (esVideoPortada(it)) Unit
                            else {
                                check(it.size <= MAX_PORTADA_ANIMADA)
                                if (Build.VERSION.SDK_INT >= 28) validarAnimada(it)
                            }
                        }
                    } ?: error("imagen")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = true
            } finally {
                ocupado = false
            }
        }
    }
    BotonSecundario(stringResource(R.string.social_portada_elegir_animada),
        { selector.launch(arrayOf("image/gif", "image/webp", "video/mp4")) }, habilitado = !ocupado)
    if (error) Text(stringResource(R.string.social_portada_animada_error))
    video?.let { uri -> RecortarVideoPortada(uri, { video = null }) { bytes -> video = null; pendiente = bytes } }
    pendiente?.let { bytes ->
        AlertDialog(
            onDismissRequest = { if (!ocupado) pendiente = null },
            title = { Text(stringResource(R.string.social_portada_elegir_animada)) },
            text = {
                androidx.compose.foundation.layout.Column {
                    DibujarPortadaSubida(bytes, Modifier.fillMaxWidth().aspectRatio(3.4f))
                    if (error) Text(stringResource(R.string.social_portada_animada_error))
                }
            },
            confirmButton = {
                TextButton(enabled = !ocupado, onClick = {
                    ocupado = true
                    error = false
                    alcance.launch {
                        try {
                            vm.red.equiparPortadaAnimada(bytes)
                            pendiente = null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            error = true
                        } finally {
                            ocupado = false
                        }
                    }
                }) { Text(stringResource(R.string.social_temas_usar)) }
            },
            dismissButton = {
                TextButton(enabled = !ocupado, onClick = { pendiente = null }) {
                    Text(stringResource(R.string.social_cancelar))
                }
            },
        )
    }
}

@RequiresApi(28)
private fun validarAnimada(bytes: ByteArray) {
    ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { _, info, _ ->
        check(info.isAnimated && info.size.width in 1..2048 && info.size.height in 1..2048)
    }
}

@Composable
internal fun PortadaSubida(nombre: String, modifier: Modifier, vm: InterfazSocial) {
    val cuenta by vm.red.usuario.collectAsState()
    val bytes by produceState<ByteArray?>(null, nombre, cuenta) {
        value = null
        try {
            value = vm.red.imagenPortada(nombre)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Las portadas retiradas o privadas dejan el fondo del perfil a la vista.
        }
    }
    bytes?.let { DibujarPortadaSubida(it, modifier) }
        ?: androidx.compose.foundation.layout.Box(modifier)
}

@Composable
private fun DibujarPortadaSubida(bytes: ByteArray, modifier: Modifier) {
    val quieta = menosMovimientoBanner()
    if (esVideoPortada(bytes)) VideoPortada(bytes, quieta, modifier)
    else if (Build.VERSION.SDK_INT >= 28) DibujarPortadaAndroid(bytes, quieta, modifier)
    else {
        val mapa by produceState<Bitmap?>(null, bytes) {
            value = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
        }
        mapa?.let { Image(it.asImageBitmap(), null, modifier, contentScale = ContentScale.Crop) }
    }
}

@RequiresApi(28)
@Composable
private fun DibujarPortadaAndroid(bytes: ByteArray, quieta: Boolean, modifier: Modifier) {
    val contexto = LocalContext.current
    val dibujo by produceState<Drawable?>(null, bytes, quieta) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val fuente = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
                if (quieta) android.graphics.drawable.BitmapDrawable(contexto.resources, ImageDecoder.decodeBitmap(fuente))
                else ImageDecoder.decodeDrawable(fuente)
            }.getOrNull()
        }
    }
    var visible by remember { mutableStateOf(false) }
    val ciclo by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    AndroidView(
        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
        modifier = modifier.visibilidadBanner { visible = it },
        update = { if (it.drawable !== dibujo) it.setImageDrawable(dibujo) },
    )
    val animacion = dibujo as? AnimatedImageDrawable
    val reproducir = !quieta && visible && ciclo.isAtLeast(Lifecycle.State.RESUMED)
    DisposableEffect(animacion, reproducir) {
        if (reproducir) {
            animacion?.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
            animacion?.start()
        }
        onDispose { animacion?.stop() }
    }
}

/** Video corto como portada: sin sonido, en bucle, solo mientras se ve; con «reducir movimiento», quieto. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoPortada(bytes: ByteArray, quieta: Boolean, modifier: Modifier) {
    val contexto = LocalContext.current
    val archivo by produceState<java.io.File?>(null, bytes) {
        value = withContext(Dispatchers.IO) {
            java.io.File(contexto.cacheDir, "portadas").apply { mkdirs() }
                .resolve("portada-${bytes.contentHashCode()}.mp4").also { if (!it.exists()) it.writeBytes(bytes) }
        }
    }
    val reproductor = remember(archivo) {
        archivo?.let { a ->
            androidx.media3.exoplayer.ExoPlayer.Builder(contexto).build().apply {
                setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(a)))
                volume = 0f
                repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
                prepare()
            }
        }
    }
    DisposableEffect(reproductor) { onDispose { reproductor?.release() } }
    var visible by remember { mutableStateOf(false) }
    val ciclo by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val reproducir = !quieta && visible && ciclo.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(reproductor, reproducir) { reproductor?.playWhenReady = reproducir }
    AndroidView(
        factory = {
            androidx.media3.ui.PlayerView(it).apply {
                useController = false; isFocusable = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        modifier = modifier.visibilidadBanner { visible = it },
        update = { it.player = reproductor },
    )
}

/** Elegir el tramo de 8 segundos de un video y dejarlo listo (sin sonido, reducido) para la portada. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun RecortarVideoPortada(uri: android.net.Uri, cerrar: () -> Unit, listo: (ByteArray) -> Unit) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val duracion by produceState(0L, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val lector = android.media.MediaMetadataRetriever()
                try { lector.setDataSource(contexto, uri); lector.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L }
                finally { lector.release() }
            }.getOrDefault(0L)
        }
    }
    var inicio by remember { mutableFloatStateOf(0f) }
    var progreso by remember { mutableStateOf<Float?>(null) }
    var fallo by remember { mutableStateOf(false) }
    val maximo = (duracion - SEGUNDOS_PORTADA_MS).coerceAtLeast(0L).toFloat()
    val reproductor = remember(uri) {
        androidx.media3.exoplayer.ExoPlayer.Builder(contexto).build().apply {
            volume = 0f; repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE; playWhenReady = true
        }
    }
    DisposableEffect(reproductor) { onDispose { reproductor.release() } }
    // La vista previa repite solo el tramo elegido.
    LaunchedEffect(uri, inicio.toLong() / 250) {
        val desde = inicio.toLong()
        reproductor.setMediaItem(androidx.media3.common.MediaItem.Builder().setUri(uri).setClippingConfiguration(
            androidx.media3.common.MediaItem.ClippingConfiguration.Builder().setStartPositionMs(desde)
                .setEndPositionMs(desde + SEGUNDOS_PORTADA_MS).build()).build())
        reproductor.prepare()
    }
    AlertDialog(
        onDismissRequest = { if (progreso == null) cerrar() },
        title = { Text(stringResource(R.string.social_portada_recortar_titulo)) },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                AndroidView(
                    factory = { androidx.media3.ui.PlayerView(it).apply {
                        useController = false; isFocusable = false; player = reproductor
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } },
                    modifier = Modifier.fillMaxWidth().aspectRatio(3.4f),
                )
                if (maximo > 0f) {
                    Text(stringResource(R.string.social_portada_recortar_desde, "%.1f".format(inicio / 1000f)))
                    androidx.compose.material3.Slider(inicio, { inicio = it }, valueRange = 0f..maximo, enabled = progreso == null)
                }
                progreso?.let { androidx.compose.material3.LinearProgressIndicator({ it }, Modifier.fillMaxWidth()) }
                if (fallo) Text(stringResource(R.string.social_portada_animada_error))
            }
        },
        confirmButton = {
            TextButton(enabled = progreso == null && duracion > 0, onClick = {
                progreso = 0f; fallo = false
                alcance.launch {
                    try {
                        val hecho = io.github.fogonapps.social.prepararVideo(contexto, uri, { progreso = it }, {},
                            maximoMs = SEGUNDOS_PORTADA_MS, inicioMs = inicio.toLong(), sinAudio = true)
                        val bytes = withContext(Dispatchers.IO) { hecho.archivo.readBytes().also { hecho.archivo.delete() } }
                        check(bytes.size <= MAX_PORTADA_VIDEO)
                        listo(bytes)
                    } catch (e: CancellationException) { throw e } catch (_: Exception) { fallo = true }
                    finally { progreso = null }
                }
            }) { Text(stringResource(R.string.social_temas_usar)) }
        },
        dismissButton = { TextButton(enabled = progreso == null, onClick = cerrar) { Text(stringResource(R.string.social_cancelar)) } },
    )
}

private const val SEGUNDOS_PORTADA_MS = 8_000L

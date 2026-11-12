package io.github.fogonapps.social

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.*
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class VideoPreparado(val archivo: File, val poster: ByteArray, val segundos: Int)

/** Solo archivos temporales privados; cancelar también detiene los codificadores. */
@OptIn(UnstableApi::class)
suspend fun prepararVideo(contexto: Context, uri: Uri, progreso: (Float) -> Unit, recorte: () -> Unit, maximoMs: Long = 30_000): VideoPreparado = withContext(Dispatchers.Main.immediate) {
    val metadatos = withContext(Dispatchers.IO) {
        conMetadatos { lector ->
            lector.setDataSource(contexto, uri)
            val duracion = lector.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: error("video_formato")
            val ancho = lector.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: error("video_formato")
            val alto = lector.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: error("video_formato")
            val rotacion = lector.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotacion % 180 == 0) Triple(tramoVideo(duracion, maximoMs), ancho, alto) else Triple(tramoVideo(duracion, maximoMs), alto, ancho)
        }
    }
    val (tramo, ancho, alto) = metadatos
    require(ancho > 0 && alto > 0) { "video_formato" }
    if (tramo.recortado) recorte()
    val archivo = File.createTempFile("video-", ".mp4", contexto.cacheDir)
    // Transformer crea el archivo de salida.
    archivo.delete()
    try {
        val escala = minOf(1.0, 1280.0 / maxOf(ancho, alto), 720.0 / minOf(ancho, alto))
        val w = (ancho * escala).toInt().coerceAtLeast(2) / 2 * 2
        val h = (alto * escala).toInt().coerceAtLeast(2) / 2 * 2
        val item = MediaItem.Builder().setUri(uri).setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder().setEndPositionMs(tramo.finMs).build()).build()
        val editado = EditedMediaItem.Builder(item).setEffects(Effects(emptyList(),
            listOf(Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT)))).build()
        val codificador = DefaultEncoderFactory.Builder(contexto)
            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(2_500_000).build())
            .setEnableFallback(false).build()
        // No copiar muestras del original: también se reduce un AVC que ya tenga el tamaño final.
        val conversion = object : Codec.EncoderFactory by codificador {
            override fun videoNeedsEncoding() = true
            override fun audioNeedsEncoding() = true
        }
        coroutineScope {
            var observador: Job? = null
            try {
                suspendCancellableCoroutine<Unit> { continua ->
                    val transformer = Transformer.Builder(contexto)
                        .setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .setEncoderFactory(conversion)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                if (continua.isActive) continua.resume(Unit)
                            }
                            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                if (continua.isActive) continua.resumeWithException(exportException)
                            }
                        }).build()
                    continua.invokeOnCancellation { transformer.cancel() }
                    transformer.start(editado, archivo.path)
                    observador = launch {
                        val estado = ProgressHolder()
                        while (isActive) {
                            if (transformer.getProgress(estado) == Transformer.PROGRESS_STATE_AVAILABLE) progreso(estado.progress / 100f)
                            delay(200)
                        }
                    }
                }
            } finally { observador?.cancel() }
        }
        require(archivo.length() in 1..15_000_000) { "video_grande" }
        val poster = withContext(Dispatchers.IO) {
            conMetadatos { lector ->
                lector.setDataSource(archivo.path)
                val cuadro = lector.getFrameAtTime(0) ?: error("video_formato")
                try {
                    var calidad = 85
                    var jpeg: ByteArray
                    do {
                        jpeg = ByteArrayOutputStream().use { salida ->
                            check(cuadro.compress(Bitmap.CompressFormat.JPEG, calidad, salida))
                            salida.toByteArray()
                        }
                        calidad -= 10
                    } while (jpeg.size > 900_000 && calidad >= 35)
                    require(jpeg.size <= 900_000) { "video_grande" }
                    jpeg
                } finally { cuadro.recycle() }
            }
        }
        progreso(1f)
        VideoPreparado(archivo, poster, ((tramo.finMs + 999) / 1000).toInt())
    } catch (x: Throwable) { archivo.delete(); throw x }
}

/** AutoCloseable llegó en API 29; release también funciona desde la mínima de la app (26). */
private inline fun <T> conMetadatos(trabajo: (MediaMetadataRetriever) -> T): T {
    val lector = MediaMetadataRetriever()
    try { return trabajo(lector) } finally { lector.release() }
}

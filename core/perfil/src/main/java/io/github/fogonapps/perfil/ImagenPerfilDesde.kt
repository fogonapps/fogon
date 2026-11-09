package io.github.fogonapps.perfil

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reduce y comprime en segundo plano; el respaldo conserva los bytes, nunca el URI temporal. */
suspend fun imagenPerfilDesde(contexto: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        val mapa = if (android.os.Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contexto.contentResolver, uri)) { decoder, info, _ ->
                val escala = minOf(1.0, 768.0 / maxOf(info.size.width, info.size.height))
                decoder.setTargetSize(maxOf(1, (info.size.width * escala).toInt()), maxOf(1, (info.size.height * escala).toInt()))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val limites = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contexto.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, limites) }
            require(limites.outWidth > 0 && limites.outHeight > 0)
            val opciones = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = 1
                while (maxOf(limites.outWidth, limites.outHeight) / inSampleSize > 768) inSampleSize *= 2
            }
            contexto.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opciones) }
                ?: error("imagen")
        }
        try {
            ByteArrayOutputStream().use { salida ->
                check(mapa.compress(Bitmap.CompressFormat.JPEG, 85, salida))
                Base64.encodeToString(salida.toByteArray(), Base64.NO_WRAP)
            }
        } finally { mapa.recycle() }
    }.getOrNull()
}

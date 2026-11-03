package io.github.fogonapps.cajon.datos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.FaceDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Revisa una foto de una persona antes de usarla: que tenga buen tamaño (al menos 250 px por lado) y que sea un
 * retrato: claramente vertical (así son los retratos), o, si es cuadrada (como las carátulas) u horizontal
 * (escenarios, grupos a lo lejos), que se vea una cara de buen tamaño. Las carátulas de Cover Art Archive no cuentan. La cara la busca el detector que trae Android
 * (`android.media.FaceDetector`), sin internet ni servicios de Google; con lentes oscuros o gorra a veces no la ve,
 * por eso no se le pide a los retratos verticales (probado con los 144 músicos de Angel el 01-11-2026). Si ninguna
 * pasa, no hay foto: mejor sin foto que una fea; la persona siempre puede elegir otra.
 */
object Fotos {
    suspend fun buena(url: String): Boolean {
        // Una carátula de disco nunca es la foto de una persona.
        if (url.isBlank() || "coverartarchive.org" in url) return false
        val bytes = bajarImagen(url) ?: return false
        return withContext(Dispatchers.Default) { tieneCara(bytes) }
    }

    fun tieneCara(bytes: ByteArray): Boolean {
        val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, medidas)
        if (medidas.outWidth < 250 || medidas.outHeight < 250) return false
        // Claramente vertical: un retrato. Las cuadradas (como las carátulas) y las horizontales necesitan una cara.
        if (medidas.outHeight >= medidas.outWidth * 1.08f) return true
        var muestra = 1
        while (maxOf(medidas.outWidth, medidas.outHeight) / (muestra * 2) >= 500) muestra *= 2
        val opciones = BitmapFactory.Options().apply { inSampleSize = muestra; inPreferredConfig = Bitmap.Config.RGB_565 }
        val b0 = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opciones) ?: return false
        // El detector pide un ancho par.
        val ancho = b0.width and 1.inv()
        val b = if (ancho != b0.width) Bitmap.createBitmap(b0, 0, 0, ancho, b0.height) else b0
        val caras = arrayOfNulls<FaceDetector.Face>(5)
        val n = runCatching { FaceDetector(b.width, b.height, caras.size).findFaces(b, caras) }.getOrDefault(0)
        // Una cara que se vea: los ojos separados al menos el 5 % del ancho (no una persona diminuta al fondo).
        return (0 until n).any { i -> (caras[i]?.eyesDistance() ?: 0f) >= b.width * 0.05f && (caras[i]?.confidence() ?: 0f) >= 0.3f }
    }
}

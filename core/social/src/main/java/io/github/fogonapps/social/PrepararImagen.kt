package io.github.fogonapps.social

/** Preparación común de fotos de publicaciones y escaparates, fuera del hilo de interfaz. */
suspend fun prepararImagen(ctx: android.content.Context, uri: android.net.Uri, lado: Int = 1600, maximo: Int = 900_000): ByteArray? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    runCatching {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
        val medidas = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, medidas)
        var muestra = 1
        while (maxOf(medidas.outWidth, medidas.outHeight) / (muestra * 2) >= lado) muestra *= 2
        val original = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = muestra }) ?: return@runCatching null
        val escala = minOf(1f, lado.toFloat() / maxOf(original.width, original.height))
        val chica = android.graphics.Bitmap.createScaledBitmap(original, (original.width * escala).toInt().coerceAtLeast(1), (original.height * escala).toInt().coerceAtLeast(1), true)
        var calidad = 88
        var salida: ByteArray
        do {
            salida = java.io.ByteArrayOutputStream().also { chica.compress(android.graphics.Bitmap.CompressFormat.JPEG, calidad, it) }.toByteArray()
            calidad -= 12
        } while (salida.size > maximo && calidad > 30)
        salida.takeIf { it.size <= maximo }
    }.getOrNull()
}

package io.github.fogonapps.social

import kotlinx.serialization.Serializable

@Serializable data class PublicarMercado(val clave: String, val imagen: String, val nombre: String, val tipo: String, val precio: Int)
@Serializable data class DecisionMercado(val accion: String, val motivo: String = "")
@Serializable data class CompraMercado(val clave: String)
@Serializable data class ArticuloMercado(val id: Long = 0, val creador: String = "", val nombre: String = "", val tipo: String = "banner", val precio: Int = 100,
    val oficial: Boolean = false, val estado: String = "revision", val motivo: String = "", val ventas: Int = 0, val cuando: Long = 0, val propio: Boolean = false, val adquirido: Boolean = false) {
    val item get() = "$tipo:m$id"
}
@Serializable data class PaginaMercado(val articulos: List<ArticuloMercado> = emptyList(), val siguiente: Int? = null)

/** Los marcos se suben como PNG sin perder alfa; los banners usan el preparador JPEG común. */
suspend fun prepararArteMercado(ctx: android.content.Context, uri: android.net.Uri, marco: Boolean): ByteArray? {
    if (!marco) return prepararImagen(ctx, uri)
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { entrada ->
                val salida = java.io.ByteArrayOutputStream()
                val bloque = ByteArray(8192)
                while (true) { val n = entrada.read(bloque); if (n < 0) break; salida.write(bloque, 0, n); require(salida.size() <= 10_000_000) }
                salida.toByteArray()
            } ?: return@runCatching null
            val opciones = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,opciones)
            require(opciones.outWidth in 1..2048 && opciones.outWidth == opciones.outHeight)
            val imagen = android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size) ?: return@runCatching null
            require(imagen.hasAlpha())
            java.io.ByteArrayOutputStream().also { imagen.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }.toByteArray().takeIf { it.size <= 900_000 }
        }.getOrNull()
    }
}

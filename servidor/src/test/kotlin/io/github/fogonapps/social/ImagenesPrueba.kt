package io.github.fogonapps.social

/** Un JPEG de verdad para las pruebas (el servidor ahora vuelve a dibujar cada foto). [conMetadatos]: con un bloque EXIF falso. */
internal fun jpegDePrueba(conMetadatos: Boolean = false): ByteArray {
    val img = java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB)
    val salida = java.io.ByteArrayOutputStream()
    javax.imageio.ImageIO.write(img, "jpeg", salida)
    val b = salida.toByteArray()
    if (!conMetadatos) return b
    // Un segmento APP1 («Exif») con un texto que no debe sobrevivir, justo después del inicio (FF D8).
    val texto = "Exif\u0000\u0000GPS-PRUEBA-UBICACION".toByteArray()
    val app1 = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), ((texto.size + 2) shr 8).toByte(), ((texto.size + 2) and 0xFF).toByte()) + texto
    return b.copyOfRange(0, 2) + app1 + b.copyOfRange(2, b.size)
}

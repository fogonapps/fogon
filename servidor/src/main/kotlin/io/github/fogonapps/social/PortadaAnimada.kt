package io.github.fogonapps.social

import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

internal const val MAX_PORTADA_ANIMADA = 3_000_000
private const val MAX_LADO = 2048
private const val MAX_CUADROS = 300

internal fun formatoPortadaAnimada(bytes: ByteArray): String? = when {
    bytes.size >= 6 && (bytes.ascii(0, "GIF89a") || bytes.ascii(0, "GIF87a")) -> "gif"
    bytes.size >= 12 && bytes.ascii(0, "RIFF") && bytes.ascii(8, "WEBP") -> "webp"
    else -> null
}

/** Verifica el contenedor y retira metadatos que no hacen falta para reproducir la animación. */
internal fun revisarPortadaAnimada(bytes: ByteArray): Pair<String, ByteArray> {
    if (bytes.size > MAX_PORTADA_ANIMADA) throw Problema(413, "muy_grande")
    val formato = formatoPortadaAnimada(bytes) ?: throw Problema(400, "imagen")
    return try {
        formato to if (formato == "gif") limpiarGif(bytes) else limpiarWebp(bytes)
    } catch (_: java.io.IOException) {
        throw Problema(400, "imagen")
    } catch (_: IllegalArgumentException) {
        throw Problema(400, "imagen")
    } catch (_: IndexOutOfBoundsException) {
        throw Problema(400, "imagen")
    }
}

private fun ByteArray.ascii(pos: Int, texto: String): Boolean =
    pos >= 0 && pos <= size - texto.length && texto.indices.all { this[pos + it].toInt() == texto[it].code }

private fun ByteArray.numero(pos: Int, largo: Int): Int =
    (0 until largo).fold(0) { valor, i -> valor or ((this[pos + i].toInt() and 255) shl (8 * i)) }

private fun limpiarGif(datos: ByteArray): ByteArray {
    val ancho = datos.numero(6, 2)
    val alto = datos.numero(8, 2)
    require(ancho in 1..MAX_LADO && alto in 1..MAX_LADO)
    fun largoTabla(opciones: Int): Int = if (opciones and 128 != 0) 3 * (2 shl (opciones and 7)) else 0
    var pos = 13 + largoTabla(datos.numero(10, 1))
    fun bloques(): Int {
        while (true) {
            val largo = datos.numero(pos++, 1)
            if (largo == 0) return pos
            pos += largo
            require(pos < datos.size)
        }
    }
    val salida = ByteArrayOutputStream().apply { write(datos, 0, pos) }
    var cuadros = 0
    while (pos < datos.lastIndex) {
        val inicio = pos
        val guardar = when (datos.numero(pos++, 1)) {
            0x2C -> {
                val x = datos.numero(pos, 2)
                val y = datos.numero(pos + 2, 2)
                val w = datos.numero(pos + 4, 2)
                val h = datos.numero(pos + 6, 2)
                require(w > 0 && h > 0 && x + w <= ancho && y + h <= alto)
                require(++cuadros <= MAX_CUADROS)
                pos += 10 + largoTabla(datos.numero(pos + 8, 1))
                bloques()
                true
            }
            0x21 -> {
                val etiqueta = datos.numero(pos++, 1)
                val bucle = etiqueta == 0xFF && datos.numero(pos, 1) == 11 && datos.ascii(pos + 1, "NETSCAPE2.0")
                val fin = bloques()
                if (etiqueta == 0xF9) require(fin - inicio == 8 && datos.numero(inicio + 2, 1) == 4)
                etiqueta == 0xF9 || (bucle && fin - inicio == 19 && datos.numero(inicio + 14, 2) == 0x0103)
            }
            else -> throw IllegalArgumentException()
        }
        if (guardar) salida.write(datos, inicio, pos - inicio)
    }
    require(pos == datos.lastIndex && datos.numero(pos, 1) == 0x3B && cuadros >= 2)
    salida.write(0x3B)
    val limpia = salida.toByteArray()
    val lector = ImageIO.getImageReadersByFormatName("gif").next()
    try {
        javax.imageio.stream.MemoryCacheImageInputStream(limpia.inputStream()).use { entrada ->
            lector.input = entrada
            repeat(cuadros) { lector.read(it).flush() }
        }
    } finally {
        lector.dispose()
    }
    return limpia
}

private fun limpiarWebp(datos: ByteArray): ByteArray {
    require(datos.numero(4, 4) == datos.size - 8 && datos.ascii(12, "VP8X") && datos.numero(16, 4) == 10)
    val banderas = datos.numero(20, 1)
    require(banderas and 0x02 != 0 && banderas and 0xC1 == 0 && datos.numero(21, 3) == 0)
    val ancho = 1 + datos.numero(24, 3)
    val alto = 1 + datos.numero(27, 3)
    require(ancho in 1..MAX_LADO && alto in 1..MAX_LADO)
    val salida = ByteArrayOutputStream().apply { write(datos, 0, 30) }
    fun finBloque(pos: Int, limite: Int): Int {
        require(pos <= limite - 8)
        val largo = datos.numero(pos + 4, 4)
        require(largo in 0..(limite - pos - 8))
        return (pos + 8 + largo + (largo and 1)).also { require(it <= limite) }
    }
    var pos = 30
    var animacion = false
    var cuadros = 0
    while (pos < datos.size) {
        val fin = finBloque(pos, datos.size)
        val conservar = when {
            datos.ascii(pos, "ANIM") -> {
                require(!animacion && datos.numero(pos + 4, 4) == 6)
                animacion = true
                true
            }
            datos.ascii(pos, "ANMF") -> {
                require(animacion && ++cuadros <= MAX_CUADROS)
                val inicio = pos + 8
                val limite = inicio + datos.numero(pos + 4, 4)
                require(limite - inicio >= 24)
                val w = datos.numero(inicio + 6, 3) + 1
                val h = datos.numero(inicio + 9, 3) + 1
                require(2 * datos.numero(inicio, 3) + w <= ancho && 2 * datos.numero(inicio + 3, 3) + h <= alto)
                require(datos.numero(inicio + 15, 1) and 0xFC == 0)
                val primerBloque = inicio + 16
                val tieneAlfa = datos.ascii(primerBloque, "ALPH")
                val imagen = if (tieneAlfa) finBloque(primerBloque, limite) else primerBloque
                require(finBloque(imagen, limite) == limite)
                val largo = datos.numero(imagen + 4, 4)
                val carga = imagen + 8
                when {
                    datos.ascii(imagen, "VP8 ") -> {
                        require(largo > 10 && datos.numero(carga, 1) and 1 == 0 && datos.numero(carga + 3, 3) == 0x2A019D)
                        require(datos.numero(carga + 6, 2) and 0x3FFF == w && datos.numero(carga + 8, 2) and 0x3FFF == h)
                    }
                    datos.ascii(imagen, "VP8L") -> {
                        require(!tieneAlfa && largo > 5 && datos.numero(carga, 1) == 0x2F)
                        val medidas = datos.numero(carga + 1, 4)
                        require(medidas and 0x3FFF == w - 1 && (medidas ushr 14) and 0x3FFF == h - 1 && medidas ushr 29 == 0)
                    }
                    else -> throw IllegalArgumentException()
                }
                true
            }
            else -> false
        }
        if (conservar) salida.write(datos, pos, fin - pos)
        pos = fin
    }
    require(cuadros >= 2)
    return salida.toByteArray().apply {
        this[20] = (banderas and 0x12).toByte()
        repeat(4) { this[4 + it] = ((size - 8) ushr (8 * it)).toByte() }
    }
}

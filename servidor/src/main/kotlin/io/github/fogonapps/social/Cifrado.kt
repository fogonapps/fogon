package io.github.fogonapps.social

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private val azarCifrado = SecureRandom()
private fun alAzar(n: Int) = ByteArray(n).also { azarCifrado.nextBytes(it) }

private fun hmac(llave: ByteArray, datos: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
    // HMAC rellena la llave con ceros: una vacía equivale a un solo cero (SecretKeySpec no acepta vacías).
    init(SecretKeySpec(if (llave.isEmpty()) ByteArray(1) else llave, "HmacSHA256"))
    doFinal(datos)
}

/** HKDF-SHA256 (RFC 5869) para salidas de hasta 32 bytes. */
internal fun hkdf(ikm: ByteArray, sal: ByteArray, info: String, largo: Int = 32): ByteArray {
    require(largo <= 32)
    val prk = hmac(if (sal.isEmpty()) ByteArray(32) else sal, ikm)
    return hmac(prk, info.toByteArray(Charsets.US_ASCII) + byteArrayOf(1)).copyOf(largo)
}

/** Lee hasta llenar [buf] o hasta el final; devuelve cuántos bytes leyó. */
private fun leerLleno(entrada: InputStream, buf: ByteArray): Int {
    var n = 0
    while (n < buf.size) { val r = entrada.read(buf, n, buf.size - n); if (r < 0) break; n += r }
    return n
}

/**
 * Cifrado de archivos por partes, para lo preservado: cada archivo se cifra de a 64 KiB con AES-256-GCM (memoria fija,
 * aunque sea un video). Es la construcción STREAM (la de Tink y age): una llave por archivo (HMAC de la llave maestra con
 * una sal al azar), y el nonce de cada parte lleva un prefijo al azar, su número y si es la última, así que no se pueden
 * quitar, cambiar de orden ni cortar partes sin que falle. La cabecera va como dato asociado en todas.
 */
internal object CifradoPorPartes {
    private val MAGIA = "FGP1".toByteArray(Charsets.US_ASCII)
    private const val PARTE = 64 * 1024
    private const val ETIQUETA = 16
    private const val CABECERA = 4 + 32 + 7

    private fun llave(maestra: ByteArray, sal: ByteArray) = SecretKeySpec(hmac(maestra, "preservado-archivo".toByteArray() + sal), "AES")

    private fun nonce(prefijo: ByteArray, i: Int, ultima: Boolean) = ByteArray(12).also {
        System.arraycopy(prefijo, 0, it, 0, 7)
        it[7] = (i ushr 24).toByte(); it[8] = (i ushr 16).toByte(); it[9] = (i ushr 8).toByte(); it[10] = i.toByte()
        it[11] = if (ultima) 1 else 0
    }

    private fun parte(modo: Int, k: SecretKeySpec, nonce: ByteArray, cabecera: ByteArray, datos: ByteArray, n: Int): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(modo, k, GCMParameterSpec(128, nonce))
            updateAAD(cabecera)
            doFinal(datos, 0, n)
        }

    fun cifrar(maestra: ByteArray, entrada: InputStream, salida: OutputStream) {
        val sal = alAzar(32); val prefijo = alAzar(7)
        val cabecera = MAGIA + sal + prefijo
        salida.write(cabecera)
        val k = llave(maestra, sal)
        var buf = ByteArray(PARTE); var siguiente = ByteArray(PARTE)
        var n = leerLleno(entrada, buf)
        var i = 0
        while (true) {
            // Se lee la parte siguiente antes de cifrar esta: así se sabe si es la última.
            val m = if (n < PARTE) 0 else leerLleno(entrada, siguiente)
            salida.write(parte(Cipher.ENCRYPT_MODE, k, nonce(prefijo, i, m == 0), cabecera, buf, n))
            if (m == 0) return
            check(i < Int.MAX_VALUE) { "archivo demasiado grande" }
            buf = siguiente.also { siguiente = buf }; n = m; i++
        }
    }

    fun descifrar(maestra: ByteArray, entrada: InputStream, salida: OutputStream) {
        val cabecera = ByteArray(CABECERA)
        if (leerLleno(entrada, cabecera) != CABECERA || !cabecera.copyOf(4).contentEquals(MAGIA)) throw java.io.IOException("cabecera inválida")
        val k = llave(maestra, cabecera.copyOfRange(4, 36))
        val prefijo = cabecera.copyOfRange(36, 43)
        var buf = ByteArray(PARTE + ETIQUETA); var siguiente = ByteArray(PARTE + ETIQUETA)
        var n = leerLleno(entrada, buf)
        var i = 0
        while (true) {
            val m = if (n < buf.size) 0 else leerLleno(entrada, siguiente)
            if (n < ETIQUETA) throw java.io.IOException("archivo cortado")
            salida.write(parte(Cipher.DECRYPT_MODE, k, nonce(prefijo, i, m == 0), cabecera, buf, n))
            if (m == 0) return
            buf = siguiente.also { siguiente = buf }; n = m; i++
        }
    }
}

/**
 * Un archivo en el formato abierto age v1 (https://age-encryption.org/v1), cifrado con una frase de paso (scrypt): se
 * abre con la herramienta libre `age -d` (o rage) y la frase. Escribe por partes de 64 KiB con ChaCha20-Poly1305, en
 * memoria fija. Al cerrarlo se escribe la última parte y se cierra [salida].
 */
internal class EscritorAge(frase: String, private val salida: OutputStream, logN: Int = 15) : OutputStream() {
    private val clave: SecretKeySpec
    private val buf = ByteArray(PARTE)
    private var n = 0
    private var contador = 0L
    private var cerrado = false

    init {
        require(logN in 10..20)
        val archivo = alAzar(16)
        val sal = alAzar(16)
        val k = scrypt(frase.toByteArray(Charsets.UTF_8), "age-encryption.org/v1/scrypt".toByteArray(Charsets.US_ASCII) + sal, 1 shl logN, 8, 1, 32)
        val envuelta = Cipher.getInstance("ChaCha20-Poly1305").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(k, "ChaCha20"), IvParameterSpec(ByteArray(12)))
            doFinal(archivo)
        }
        val cabecera = "age-encryption.org/v1\n-> scrypt ${b64(sal)} $logN\n${b64(envuelta)}\n---"
        val mac = hmac(hkdf(archivo, ByteArray(0), "header"), cabecera.toByteArray(Charsets.US_ASCII))
        salida.write("$cabecera ${b64(mac)}\n".toByteArray(Charsets.US_ASCII))
        val nonce = alAzar(16)
        salida.write(nonce)
        clave = SecretKeySpec(hkdf(archivo, nonce, "payload"), "ChaCha20")
    }

    override fun write(b: Int) {
        if (n == PARTE) sacar(false)
        buf[n++] = b.toByte()
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        var desde = off; var falta = len
        while (falta > 0) {
            // La parte llena se cifra solo cuando llega algo más: la última puede ir llena.
            if (n == PARTE) sacar(false)
            val c = minOf(falta, PARTE - n)
            System.arraycopy(b, desde, buf, n, c)
            n += c; desde += c; falta -= c
        }
    }

    private fun sacar(ultima: Boolean) {
        check(!cerrado) { "cerrado" }
        val nonce = ByteArray(12)
        var c = contador
        for (i in 10 downTo 0) { nonce[i] = c.toByte(); c = c ushr 8 }
        nonce[11] = if (ultima) 1 else 0
        salida.write(Cipher.getInstance("ChaCha20-Poly1305").run {
            init(Cipher.ENCRYPT_MODE, clave, IvParameterSpec(nonce))
            doFinal(buf, 0, n)
        })
        contador++; n = 0
    }

    override fun flush() = salida.flush()

    override fun close() {
        if (cerrado) return
        sacar(true)
        cerrado = true
        salida.close()
    }

    companion object {
        const val PARTE = 64 * 1024
        fun b64(x: ByteArray): String = java.util.Base64.getEncoder().withoutPadding().encodeToString(x)

        /**
         * Una frase de paso al azar: 6 grupos de 5 letras y números sin los que se confunden (150 bits). Se le muestra
         * una sola vez a quien genera el paquete; el servidor no la guarda.
         */
        fun frase(): String {
            val letras = "abcdefghijkmnpqrstuvwxyz23456789"
            return (1..6).joinToString("-") { (1..5).map { letras[azarCifrado.nextInt(letras.length)] }.joinToString("") }
        }
    }
}

/** scrypt (RFC 7914). [n] potencia de 2; usa 128·r·n bytes de memoria. */
internal fun scrypt(clave: ByteArray, sal: ByteArray, n: Int, r: Int, p: Int, largo: Int): ByteArray {
    require(n > 1 && n and (n - 1) == 0)
    val b = pbkdf2(clave, sal, p * 128 * r)
    val x = IntArray(32 * r)
    val v = IntArray(32 * r * n)
    val y = IntArray(32 * r)
    for (i in 0 until p) {
        val o = i * 128 * r
        for (k in 0 until 32 * r) x[k] = (b[o + 4 * k].toInt() and 255) or ((b[o + 4 * k + 1].toInt() and 255) shl 8) or
            ((b[o + 4 * k + 2].toInt() and 255) shl 16) or ((b[o + 4 * k + 3].toInt() and 255) shl 24)
        for (j in 0 until n) { System.arraycopy(x, 0, v, j * 32 * r, 32 * r); mezclarBloques(x, y, r) }
        for (j in 0 until n) {
            val k = x[(2 * r - 1) * 16] and (n - 1)
            for (t in 0 until 32 * r) x[t] = x[t] xor v[k * 32 * r + t]
            mezclarBloques(x, y, r)
        }
        for (k in 0 until 32 * r) {
            b[o + 4 * k] = x[k].toByte(); b[o + 4 * k + 1] = (x[k] ushr 8).toByte()
            b[o + 4 * k + 2] = (x[k] ushr 16).toByte(); b[o + 4 * k + 3] = (x[k] ushr 24).toByte()
        }
    }
    return pbkdf2(clave, b, largo)
}

/** PBKDF2-HMAC-SHA256 con una sola iteración (lo que usa scrypt). */
private fun pbkdf2(clave: ByteArray, sal: ByteArray, largo: Int): ByteArray {
    val salida = ByteArray(largo)
    var i = 1; var o = 0
    while (o < largo) {
        val t = hmac(clave, sal + byteArrayOf((i ushr 24).toByte(), (i ushr 16).toByte(), (i ushr 8).toByte(), i.toByte()))
        System.arraycopy(t, 0, salida, o, minOf(32, largo - o))
        o += 32; i++
    }
    return salida
}

/** scryptBlockMix: [b] (32·r enteros) queda mezclado; [y] es espacio de trabajo. */
private fun mezclarBloques(b: IntArray, y: IntArray, r: Int) {
    val x = b.copyOfRange((2 * r - 1) * 16, 2 * r * 16)
    for (i in 0 until 2 * r) {
        for (k in 0 until 16) x[k] = x[k] xor b[i * 16 + k]
        salsa8(x)
        System.arraycopy(x, 0, y, i * 16, 16)
    }
    for (i in 0 until r) {
        System.arraycopy(y, 2 * i * 16, b, i * 16, 16)
        System.arraycopy(y, (2 * i + 1) * 16, b, (i + r) * 16, 16)
    }
}

/** Salsa20/8 sobre 16 enteros. */
private fun salsa8(b: IntArray) {
    val x = b.copyOf()
    fun R(a: Int, n: Int) = (a shl n) or (a ushr (32 - n))
    repeat(4) {
        x[4] = x[4] xor R(x[0] + x[12], 7); x[8] = x[8] xor R(x[4] + x[0], 9)
        x[12] = x[12] xor R(x[8] + x[4], 13); x[0] = x[0] xor R(x[12] + x[8], 18)
        x[9] = x[9] xor R(x[5] + x[1], 7); x[13] = x[13] xor R(x[9] + x[5], 9)
        x[1] = x[1] xor R(x[13] + x[9], 13); x[5] = x[5] xor R(x[1] + x[13], 18)
        x[14] = x[14] xor R(x[10] + x[6], 7); x[2] = x[2] xor R(x[14] + x[10], 9)
        x[6] = x[6] xor R(x[2] + x[14], 13); x[10] = x[10] xor R(x[6] + x[2], 18)
        x[3] = x[3] xor R(x[15] + x[11], 7); x[7] = x[7] xor R(x[3] + x[15], 9)
        x[11] = x[11] xor R(x[7] + x[3], 13); x[15] = x[15] xor R(x[11] + x[7], 18)
        x[1] = x[1] xor R(x[0] + x[3], 7); x[2] = x[2] xor R(x[1] + x[0], 9)
        x[3] = x[3] xor R(x[2] + x[1], 13); x[0] = x[0] xor R(x[3] + x[2], 18)
        x[6] = x[6] xor R(x[5] + x[4], 7); x[7] = x[7] xor R(x[6] + x[5], 9)
        x[4] = x[4] xor R(x[7] + x[6], 13); x[5] = x[5] xor R(x[4] + x[7], 18)
        x[11] = x[11] xor R(x[10] + x[9], 7); x[8] = x[8] xor R(x[11] + x[10], 9)
        x[9] = x[9] xor R(x[8] + x[11], 13); x[10] = x[10] xor R(x[9] + x[8], 18)
        x[12] = x[12] xor R(x[15] + x[14], 7); x[13] = x[13] xor R(x[12] + x[15], 9)
        x[14] = x[14] xor R(x[13] + x[12], 13); x[15] = x[15] xor R(x[14] + x[13], 18)
    }
    for (i in 0 until 16) b[i] += x[i]
}

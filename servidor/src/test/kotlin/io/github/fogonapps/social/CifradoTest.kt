package io.github.fogonapps.social

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.*

/** El cifrado de lo preservado (por partes) y el paquete age para una autoridad. */
class CifradoTest {
    private fun hex(x: ByteArray) = x.joinToString("") { "%02x".format(it) }

    @Test fun scryptCumpleLosVectoresDelRfc7914() {
        assertEquals("77d6576238657b203b19ca42c18a0497f16b4844e3074ae8dfdffa3fede21442fcd0069ded0948f8326a753a0fc81f17e8d3e0fb2e0d3628cf35e20c38d18906",
            hex(scrypt(ByteArray(0), ByteArray(0), 16, 1, 1, 64)))
        assertEquals("fdbabe1c9d3472007856e7190d01e9fe7c6ad7cbc8237830e77376634b3731622eaf30d92e22a3886ff109279d9830dac727afb94a83ee6d8360cbdfa2cc0640",
            hex(scrypt("password".toByteArray(), "NaCl".toByteArray(), 1024, 8, 16, 64)))
        assertEquals("7023bdcb3afd7348461c06cd81fd38ebfda8fbba904f8e3ea9b543f6545da1f2d5432955613f0fcf62d49705242a9af9e61e85dc0d651e40dfcf017b45575887",
            hex(scrypt("pleaseletmein".toByteArray(), "SodiumChloride".toByteArray(), 16384, 8, 1, 64)))
    }

    private val llave = ByteArray(32) { it.toByte() }

    private fun cifrar(datos: ByteArray) = ByteArrayOutputStream().also { CifradoPorPartes.cifrar(llave, ByteArrayInputStream(datos), it) }.toByteArray()
    private fun descifrar(datos: ByteArray, k: ByteArray = llave) = ByteArrayOutputStream().also { CifradoPorPartes.descifrar(k, ByteArrayInputStream(datos), it) }.toByteArray()

    @Test fun porPartesIdaYVueltaEnTodosLosTamanos() {
        val azar = java.util.Random(7)
        for (n in listOf(0, 1, 65535, 65536, 65537, 3 * 65536, 200_001)) {
            val datos = ByteArray(n).also(azar::nextBytes)
            val c = cifrar(datos)
            assertContentEquals(datos, descifrar(c), "tamaño $n")
            assertEquals(43 + n + 16 * maxOf(1, (n + 65535) / 65536), c.size, "tamaño $n")
        }
    }

    @Test fun porPartesNoSeDejaCambiarCortarNiAbrirConOtraLlave() {
        val datos = ByteArray(3 * 65536 + 10) { (it % 251).toByte() }
        val c = cifrar(datos)
        assertFails { descifrar(c.copyOf().also { it[100] = (it[100] + 1).toByte() }) }
        // Quitar la última parte entera: la que queda no está marcada como última.
        assertFails { descifrar(c.copyOf(43 + 3 * (65536 + 16))) }
        // Cambiar el orden de dos partes.
        val p = 65536 + 16
        val cambiado = c.copyOf().also { System.arraycopy(c, 43 + p, it, 43, p); System.arraycopy(c, 43, it, 43 + p, p) }
        assertFails { descifrar(cambiado) }
        assertFails { descifrar(c, ByteArray(32)) }
    }

    @Test fun elPaqueteAgeSeAbreConLaFraseYNoConOtra() {
        val frase = EscritorAge.frase()
        assertTrue(Regex("([a-z2-9]{5}-){5}[a-z2-9]{5}").matches(frase))
        assertNotEquals(frase, EscritorAge.frase())
        for (n in listOf(0, 10, 65536, 65537, 150_000)) {
            val datos = ByteArray(n) { (it * 7).toByte() }
            val salida = ByteArrayOutputStream()
            EscritorAge(frase, salida, logN = 10).use { it.write(datos) }
            val c = salida.toByteArray()
            assertTrue(String(c, 0, 22, Charsets.US_ASCII) == "age-encryption.org/v1\n")
            assertContentEquals(datos, abrirAge(frase, c), "tamaño $n")
            assertFails { abrirAge("otra-frase", c) }
        }
    }

    companion object {
        /** Abre un archivo age con frase de paso (para las pruebas; quien lo recibe usa la herramienta `age -d`). */
        fun abrirAge(frase: String, archivo: ByteArray): ByteArray {
            val fin = String(archivo, Charsets.ISO_8859_1).indexOf("\n---") + 4
            val lineas = String(archivo, 0, fin, Charsets.US_ASCII).split("\n")
            check(lineas[0] == "age-encryption.org/v1")
            val (_, tipo, sal64, logN) = lineas[1].split(" ")
            check(tipo == "scrypt")
            val dec = java.util.Base64.getDecoder()
            val k = scrypt(frase.toByteArray(), "age-encryption.org/v1/scrypt".toByteArray() + dec.decode(sal64), 1 shl logN.toInt(), 8, 1, 32)
            val clave = Cipher.getInstance("ChaCha20-Poly1305").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(k, "ChaCha20"), IvParameterSpec(ByteArray(12))); doFinal(dec.decode(lineas[2]))
            }
            val finMac = String(archivo, Charsets.ISO_8859_1).indexOf('\n', fin)
            val mac = dec.decode(String(archivo, fin + 1, finMac - fin - 1, Charsets.US_ASCII))
            val esperado = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(hkdf(clave, ByteArray(0), "header"), "HmacSHA256")); doFinal(archivo.copyOf(fin)) }
            check(mac.contentEquals(esperado)) { "MAC de la cabecera" }
            val nonce = archivo.copyOfRange(finMac + 1, finMac + 17)
            val payload = SecretKeySpec(hkdf(clave, nonce, "payload"), "ChaCha20")
            val salida = ByteArrayOutputStream()
            var o = finMac + 17; var i = 0L
            while (true) {
                val largo = minOf(65536 + 16, archivo.size - o)
                val ultima = o + largo == archivo.size
                val n = ByteArray(12).also { var c = i; for (j in 10 downTo 0) { it[j] = c.toByte(); c = c ushr 8 }; it[11] = if (ultima) 1 else 0 }
                salida.write(Cipher.getInstance("ChaCha20-Poly1305").run { init(Cipher.DECRYPT_MODE, payload, IvParameterSpec(n)); doFinal(archivo, o, largo) })
                o += largo; i++
                if (ultima) return salida.toByteArray()
            }
        }

        /** Las entradas del ZIP de un paquete. */
        fun entradasZip(zip: ByteArray): Map<String, ByteArray> = buildMap {
            java.util.zip.ZipInputStream(ByteArrayInputStream(zip)).use { z ->
                while (true) { val e = z.nextEntry ?: break; put(e.name, z.readBytes()) }
            }
        }
    }
}

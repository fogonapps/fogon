package io.github.fogonapps.social

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Las contraseñas: PBKDF2 con HMAC-SHA256, 600 000 iteraciones (lo que recomienda OWASP para PBKDF2-SHA256) y una sal
 * al azar de 16 bytes por cuenta. Se guarda "iteraciones:sal:huella"; la contraseña nunca.
 */
object Claves {
    private const val ITERACIONES = 600_000
    private val azar = SecureRandom()

    private fun derivar(clave: String, sal: ByteArray, iteraciones: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(clave.toCharArray(), sal, iteraciones, 256)).encoded

    fun huella(clave: String): String {
        val sal = ByteArray(16).also { azar.nextBytes(it) }
        val b = Base64.getEncoder()
        return "$ITERACIONES:${b.encodeToString(sal)}:${b.encodeToString(derivar(clave, sal, ITERACIONES))}"
    }

    fun comprobar(clave: String, guardada: String): Boolean {
        val (n, sal, h) = guardada.split(":").takeIf { it.size == 3 } ?: return false
        val d = Base64.getDecoder()
        return MessageDigest.isEqual(derivar(clave, d.decode(sal), n.toIntOrNull() ?: return false), d.decode(h))
    }
}

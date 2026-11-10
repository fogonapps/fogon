package io.github.fogonapps.social

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Lo de una app guardado en la cuenta (D-033), cifrado de extremo a extremo, igual que el cajón de El Cajón: el teléfono lo cifra con una llave al azar (AES-256-GCM,
 * comprimido antes) y esa llave viaja envuelta con la contraseña de la persona (PBKDF2-SHA256, 600 000 vueltas, sal
 * propia). El servidor guarda los dos sobres y no puede abrir ninguno: sin la contraseña, nadie lee el cajón.
 * Si la persona olvida la contraseña, lo guardado en el servidor no se puede recuperar (lo del teléfono sigue ahí).
 */
object NubeCifrada {
    private val azar = SecureRandom()
    private const val VUELTAS = 600_000
    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)
    private fun de64(s: String) = Base64.getDecoder().decode(s)

    /** Una llave nueva para el cajón (32 bytes al azar, en base64). */
    fun llaveNueva(): String = b64(ByteArray(32).also { azar.nextBytes(it) })

    private fun cifrar(llave: ByteArray, datos: ByteArray): ByteArray {
        val iv = ByteArray(12).also { azar.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(llave, "AES"), GCMParameterSpec(128, iv)) }
        return iv + c.doFinal(datos)
    }

    private fun descifrar(llave: ByteArray, sobre: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(llave, "AES"), GCMParameterSpec(128, sobre, 0, 12)) }
        return c.doFinal(sobre, 12, sobre.size - 12)
    }

    private fun deClave(clave: String, sal: ByteArray, vueltas: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(clave.toCharArray(), sal, vueltas, 256)).encoded

    /** Envuelve la llave del cajón con la contraseña: "vueltas:sal:sobre". */
    fun envolver(llave: String, clave: String): String {
        val sal = ByteArray(16).also { azar.nextBytes(it) }
        return "$VUELTAS:${b64(sal)}:${b64(cifrar(deClave(clave, sal, VUELTAS), de64(llave)))}"
    }

    /** Abre la llave envuelta con la contraseña (falla si la contraseña no es). */
    fun desenvolver(envuelta: String, clave: String): String {
        val (vueltas, sal, sobre) = envuelta.split(":").takeIf { it.size == 3 } ?: error("llave")
        return b64(descifrar(deClave(clave, de64(sal), vueltas.toInt()), de64(sobre)))
    }

    /** El cajón (su JSON) comprimido y cifrado, en base64. */
    fun cerrar(json: String, llave: String): String {
        val comprimido = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(json.toByteArray()) } }.toByteArray()
        return b64(cifrar(de64(llave), comprimido))
    }

    fun abrir(sobre: String, llave: String): String =
        GZIPInputStream(ByteArrayInputStream(descifrar(de64(llave), de64(sobre)))).use { String(it.readBytes()) }
}

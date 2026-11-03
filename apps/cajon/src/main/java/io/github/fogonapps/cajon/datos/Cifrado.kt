package io.github.fogonapps.cajon.datos

import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.PublicKeyVerify
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkJsonProtoKeysetFormat
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.signature.SignatureConfig
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.util.Base64

/**
 * El cifrado de extremo a extremo del chat, con Tink (no con criptografía propia): cada teléfono tiene un par para
 * cifrar (HPKE: X25519, HKDF-SHA256 y AES-256-GCM) y uno para firmar (Ed25519). Un mensaje se cifra con la llave
 * pública de quien lo recibe y se firma con la propia; el "contexto" ata el sobre a quién lo manda y a quién va, para
 * que no se pueda reenviar como si fuera de otro. El servidor solo ve sobres cerrados.
 *
 * Límite conocido: no hay secreto hacia adelante (si alguien saca la llave privada de un teléfono, podría abrir los
 * sobres viejos que hubiera guardado). Los sobres se borran del servidor al entregarlos.
 */
object Cifrado {
    init { HybridConfig.register(); SignatureConfig.register() }

    /** Las llaves de este teléfono: las privadas (en JSON de Tink, con su secreto) para guardar en la caja fuerte. */
    @Serializable data class Propias(val cifrar: String, val firmar: String)

    fun nuevas(): Propias {
        val c = KeysetHandle.generateNew(KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM"))
        val f = KeysetHandle.generateNew(KeyTemplates.get("ED25519"))
        return Propias(TinkJsonProtoKeysetFormat.serializeKeyset(c, InsecureSecretKeyAccess.get()), TinkJsonProtoKeysetFormat.serializeKeyset(f, InsecureSecretKeyAccess.get()))
    }

    private fun privada(json: String) = TinkJsonProtoKeysetFormat.parseKeyset(json, InsecureSecretKeyAccess.get())
    private fun publica(b64: String) = TinkJsonProtoKeysetFormat.parseKeysetWithoutSecret(String(Base64.getDecoder().decode(b64)))
    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)

    /** Las públicas, para subirlas (base64 del JSON de Tink, sin secretos). */
    fun publicas(p: Propias) = LlavesPublicas(
        b64(TinkJsonProtoKeysetFormat.serializeKeysetWithoutSecret(privada(p.cifrar).publicKeysetHandle).toByteArray()),
        b64(TinkJsonProtoKeysetFormat.serializeKeysetWithoutSecret(privada(p.firmar).publicKeysetHandle).toByteArray()),
    )

    private fun contexto(de: String, para: String) = "elcajon-chat:v1:$de>$para".toByteArray()

    /** Cierra un mensaje de [de] para [para]: el sobre (base64) y su firma (base64). */
    fun cerrar(texto: ByteArray, de: String, para: String, suyas: LlavesPublicas, mias: Propias): Pair<String, String> {
        val ctx = contexto(de, para)
        val sobre = publica(suyas.cifrar).getPrimitive(RegistryConfiguration.get(), HybridEncrypt::class.java).encrypt(texto, ctx)
        val firma = privada(mias.firmar).getPrimitive(RegistryConfiguration.get(), PublicKeySign::class.java).sign(sobre + ctx)
        return b64(sobre) to b64(firma)
    }

    /** Abre un sobre de [de] para [para] (este teléfono): comprueba la firma con las llaves públicas de quien lo mandó. */
    fun abrir(sobreB64: String, firmaB64: String, de: String, para: String, suyas: LlavesPublicas, mias: Propias): ByteArray {
        val ctx = contexto(de, para)
        val sobre = Base64.getDecoder().decode(sobreB64)
        publica(suyas.firmar).getPrimitive(RegistryConfiguration.get(), PublicKeyVerify::class.java).verify(Base64.getDecoder().decode(firmaB64), sobre + ctx)
        return privada(mias.cifrar).getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java).decrypt(sobre, ctx)
    }

    /**
     * El número de seguridad de una conversación: sale de las llaves públicas de las dos personas (en orden, para que
     * los dos vean el mismo). Si coincide en los dos teléfonos, nadie se metió en el medio.
     */
    fun numeroSeguridad(a: LlavesPublicas, b: LlavesPublicas): String {
        val (x, y) = listOf(a.cifrar + a.firmar, b.cifrar + b.firmar).sorted()
        val h = MessageDigest.getInstance("SHA-256").digest((x + "|" + y).toByteArray())
        return (0 until 6).joinToString(" ") { i -> "%05d".format(((h[i * 3].toInt() and 0xFF shl 16) or (h[i * 3 + 1].toInt() and 0xFF shl 8) or (h[i * 3 + 2].toInt() and 0xFF)) % 100000) }
    }
}

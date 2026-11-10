package io.github.fogonapps.social

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Un token cifrado con una llave del Keystore de Android (AES-GCM), como la caja de El Forzudo.
 * La llave nunca sale del hardware seguro; en las preferencias [preferencias] solo queda el texto cifrado.
 */
class CajaToken(context: Context, preferencias: String, private val alias: String) {
    private val prefs = context.getSharedPreferences(preferencias, Context.MODE_PRIVATE)

    private fun llave(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    var token: String?
        get() {
            val datos = prefs.getString("token", null) ?: return null
            val iv = prefs.getString("iv", null) ?: return null
            return runCatching {
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.DECRYPT_MODE, llave(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
                c.doFinal(Base64.decode(datos, Base64.NO_WRAP)).decodeToString()
            }.getOrNull()
        }
        set(v) {
            if (v == null) { check(prefs.edit().remove("token").remove("iv").commit()); return }
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, llave())
            check(prefs.edit()
                .putString("token", Base64.encodeToString(c.doFinal(v.encodeToByteArray()), Base64.NO_WRAP))
                .putString("iv", Base64.encodeToString(c.iv, Base64.NO_WRAP))
                .commit())
        }
}

/**
 * La sesión social de un perfil del teléfono: token cifrado, usuario, política aceptada y «Quién lo ve»
 * de las partes del perfil. [app] separa las preferencias y la llave de cada app.
 */
class SesionApp(
    contexto: Context, private val perfiles: Perfiles, val perfil: String, app: String,
    /** Dónde vive el token cifrado: El Forzudo conserva su caja de antes para no cerrar sesiones al actualizar. */
    preferenciasToken: String = "sesion-$app-p$perfil", aliasToken: String = "$app-sesion",
) : AlmacenSesionSocial {
    private val caja = CajaToken(contexto, preferenciasToken, aliasToken)
    private val preferencias = contexto.getSharedPreferences("social-p$perfil", Context.MODE_PRIVATE)
    override var token: String?
        get() = caja.token
        set(valor) { caja.token = valor }
    var usuario: String?
        get() = preferencias.getString("usuario", null)
        set(valor) { check(preferencias.edit().putString("usuario", valor).commit()) }
    override var politicaAceptada: String?
        get() = preferencias.getString("politica", null)
        set(valor) { check(preferencias.edit().putString("politica", valor).commit()) }
    fun visibilidad(clave: String) = preferencias.getString("visibilidad.$clave", "privado") ?: "privado"
    fun guardarVisibilidad(clave: String, valor: String) { check(preferencias.edit().putString("visibilidad.$clave", valor).commit()) }
    /** Un dato propio de la app para este perfil (por ejemplo, el perfil de El Forzudo elegido). */
    fun dato(clave: String): String? = preferencias.getString("dato.$clave", null)
    fun guardarDato(clave: String, valor: String?) { check(preferencias.edit().putString("dato.$clave", valor).commit()) }
    override val idCuenta get() = perfiles.lista.perfiles.first { it.id == perfil }.idCuenta
    override val sesion = SesionCuenta { token }
    override fun guardarIdCuenta(idCuenta: String) { check(perfiles.completarIdCuenta(perfil, idCuenta)) }

    companion object {
        /** Las preferencias de un perfil que se borran al quitarlo del teléfono. */
        fun preferenciasDe(app: String, perfil: String) = listOf("sesion-$app-p$perfil", "social-p$perfil")
    }
}

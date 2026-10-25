package io.github.fogonapps.cajon.datos

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Guarda el estado completo en un archivo JSON con escrituras atómicas (DataStore). */
object SerializadorEstado : Serializer<Estado> {
    override val defaultValue = Estado()
    override suspend fun readFrom(input: InputStream): Estado = try {
        JsonCajon.decodeFromString(Estado.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Estado ilegible", e)
    }

    override suspend fun writeTo(t: Estado, output: OutputStream) {
        output.write(JsonCajon.encodeToString(Estado.serializer(), t).encodeToByteArray())
    }
}

fun crearAlmacen(context: Context): DataStore<Estado> = DataStoreFactory.create(
    serializer = SerializadorEstado,
    // Si el archivo se daña, se conserva una copia antes de empezar de cero.
    corruptionHandler = ReplaceFileCorruptionHandler { _ ->
        val f = File(context.filesDir, "datastore/estado.json")
        runCatching { f.copyTo(File(context.filesDir, "estado-dañado-${System.currentTimeMillis()}.json")) }
        Estado()
    },
    produceFile = { File(context.filesDir, "datastore/estado.json") },
)

/**
 * Las claves de la persona (el token de GitHub y la clave de TMDB), cifradas con una llave del Keystore de
 * Android (AES-GCM). La llave nunca sale del hardware seguro del teléfono; en el disco solo queda el texto
 * cifrado. No van en el respaldo ni en el Gist.
 */
class CajaFuerte(context: Context) {
    private val prefs = context.getSharedPreferences("claves", Context.MODE_PRIVATE)
    private val alias = "cajon-claves"

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

    private fun leer(nombre: String): String? {
        val datos = prefs.getString(nombre, null) ?: return null
        val iv = prefs.getString("$nombre.iv", null) ?: return null
        return runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, llave(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            c.doFinal(Base64.decode(datos, Base64.NO_WRAP)).decodeToString()
        }.getOrNull()
    }

    private fun escribir(nombre: String, v: String?) {
        if (v == null) { prefs.edit { remove(nombre); remove("$nombre.iv") }; return }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, llave())
        val cifrado = c.doFinal(v.encodeToByteArray())
        prefs.edit {
            putString(nombre, Base64.encodeToString(cifrado, Base64.NO_WRAP))
            putString("$nombre.iv", Base64.encodeToString(c.iv, Base64.NO_WRAP))
        }
    }

    var tokenGithub: String?
        get() = leer("github")
        set(v) = escribir("github", v)

    var claveTmdb: String?
        get() = leer("tmdb")
        set(v) = escribir("tmdb", v)

    /** Lo de la sincronización que no es secreto: el usuario, el Gist y la última vez. */
    var usuario: String?
        get() = prefs.getString("usuario", null)
        set(v) = prefs.edit { if (v == null) remove("usuario") else putString("usuario", v) }
    var gist: String?
        get() = prefs.getString("gist", null)
        set(v) = prefs.edit { if (v == null) remove("gist") else putString("gist", v) }
    var ultima: Long
        get() = prefs.getLong("ultima", 0)
        set(v) = prefs.edit { putLong("ultima", v) }

    fun desconectarGithub() { tokenGithub = null; usuario = null; gist = null; ultima = 0 }
}

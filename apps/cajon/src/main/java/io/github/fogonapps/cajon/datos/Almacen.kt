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

    /** Spotify (opcional): el Client ID de la app que la persona creó en Spotify y el token para renovar el acceso. */
    var spotifyCliente: String?
        get() = leer("spotify_cliente")
        set(v) = escribir("spotify_cliente", v)
    var spotifyRenovar: String?
        get() = leer("spotify_renovar")
        set(v) = escribir("spotify_renovar", v)

    /** La sesión en Fogón Social (la red de El Cajón): el token, cifrado; y el @usuario, que no es secreto. */
    var tokenSocial: String?
        get() = leer("social")
        set(v) = escribir("social", v)
    var usuarioSocial: String?
        get() = prefs.getString("usuario_social", null)
        set(v) = prefs.edit { if (v == null) remove("usuario_social") else putString("usuario_social", v) }
    /** Las llaves del chat de este teléfono (keysets de Tink en JSON, con su parte secreta): solo aquí, cifradas. */
    var chatLlaves: String?
        get() = leer("chat_llaves")
        set(v) = escribir("chat_llaves", v)

    /** La versión de la política que aceptó (si cambia, se vuelve a pedir). */
    var politicaSocial: String?
        get() = prefs.getString("politica_social", null)
        set(v) = prefs.edit { if (v == null) remove("politica_social") else putString("politica_social", v) }

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

    /**
     * Las obras que "Buscar portadas" ya revisó sin encontrar (para no repetir cientos de consultas cada vez que se
     * abre la app). Se olvidan a los 30 días, por si MusicBrainz o TMDB las agregan después. Solo en este teléfono.
     */
    var portadasRevisadas: Set<String>
        get() {
            if (System.currentTimeMillis() - prefs.getLong("portadas_desde", 0) > 30L * 24 * 3600 * 1000) return emptySet()
            return prefs.getStringSet("portadas_revisadas", emptySet()) ?: emptySet()
        }
        set(v) = prefs.edit {
            if (prefs.getStringSet("portadas_revisadas", emptySet()).isNullOrEmpty() || v.isEmpty()) putLong("portadas_desde", System.currentTimeMillis())
            putStringSet("portadas_revisadas", v)
        }

    /** 2 = los títulos de TMDB ya están en español latino (ver `CajonViewModel.pasarALatino`). Solo en este teléfono. */
    var versionCine: Int
        get() = prefs.getInt("version_cine", 0)
        set(v) = prefs.edit { putInt("version_cine", v) }

    /** 1 = lo no enlazado se volvió a buscar con las reglas de la 1.9.3. Solo en este teléfono. */
    var versionEnlace: Int
        get() = prefs.getInt("version_enlace", 0)
        set(v) = prefs.edit { putInt("version_enlace", v) }

    fun desconectarGithub() { tokenGithub = null; usuario = null; gist = null; ultima = 0 }
}

package io.github.fogonapps.cajon.datos

import android.content.SharedPreferences
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
        Logica.normalizar(JsonCajon.decodeFromString(Estado.serializer(), input.readBytes().decodeToString()))
    } catch (e: SerializationException) {
        throw CorruptionException("Estado ilegible", e)
    }

    override suspend fun writeTo(t: Estado, output: OutputStream) {
        output.write(JsonCajon.encodeToString(Estado.serializer(), t).encodeToByteArray())
    }
}

fun crearAlmacen(context: Context, directorio: File = context.filesDir, scope: kotlinx.coroutines.CoroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())): DataStore<Estado> = crearAlmacenPerfil(directorio, scope)

/** La misma fábrica que usa Android, comprobable con un sistema de archivos temporal. */
internal fun crearAlmacenPerfil(directorio: File, scope: kotlinx.coroutines.CoroutineScope): DataStore<Estado> = DataStoreFactory.create(
    serializer = SerializadorEstado,
    scope = scope,
    // Si el archivo se daña, se conserva una copia antes de empezar de cero.
    corruptionHandler = ReplaceFileCorruptionHandler { error ->
        // Durante la migración no se sustituye el cajón por uno vacío, ni siquiera ante corrupción.
        if (File(directorio.parentFile?.parentFile, "migracion-perfiles-respaldo").exists()) throw error
        val f = File(directorio, "datastore/estado.json")
        f.copyTo(File(directorio, "estado-dañado-${System.currentTimeMillis()}.json"))
        Estado()
    },
    produceFile = { File(directorio, "datastore/estado.json") },
)

/**
 * Las claves de la persona (el token de GitHub y la clave de TMDB), cifradas con una llave del Keystore de
 * Android (AES-GCM). La llave nunca sale del hardware seguro del teléfono; en el disco solo queda el texto
 * cifrado. No van en el respaldo ni en el Gist.
 */
class CajaFuerte(context: Context, val perfilId: String) {
    private val _permisoRed = kotlinx.coroutines.flow.MutableStateFlow(false)
    val permisoRed: kotlinx.coroutines.flow.StateFlow<Boolean> = _permisoRed
    var redPermitida: Boolean
        get() = _permisoRed.value
        set(valor) { _permisoRed.value = valor }
    fun clienteSocial(token: String? = tokenSocial) = ClienteSocial(token) { redPermitida && token == tokenSocial }
    private val prefijo = "p$perfilId."
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

    private fun leer(clave: String): String? {
        val nombre = prefijo + clave
        val datos = prefs.getString(nombre, null) ?: return null
        val iv = prefs.getString("$nombre.iv", null) ?: return null
        return runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, llave(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            c.doFinal(Base64.decode(datos, Base64.NO_WRAP)).decodeToString()
        }.getOrNull()
    }

    private fun escribir(clave: String, v: String?, confirmar: Boolean = false, extras: SharedPreferences.Editor.() -> Unit = {}) {
        val nombre = prefijo + clave
        if (v == null) { prefs.edit(commit = confirmar) { remove(nombre); remove("$nombre.iv"); extras() }; return }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, llave())
        val cifrado = c.doFinal(v.encodeToByteArray())
        prefs.edit(commit = confirmar) {
            putString(nombre, Base64.encodeToString(cifrado, Base64.NO_WRAP))
            putString("$nombre.iv", Base64.encodeToString(c.iv, Base64.NO_WRAP))
            extras()
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

    /** Las claves de lo que este teléfono ya publicó (para saber qué quitó; lo que nunca subió no se borra). */
    var publicacionesSubidas: Set<String>
        get() = prefs.getStringSet(prefijo + "publicaciones_subidas", emptySet()).orEmpty()
        set(v) = prefs.edit { putStringSet(prefijo + "publicaciones_subidas", v) }

    /** La portada del perfil ya subida a la red: "archivo local|dirección en el servidor" (para no subirla otra vez). */
    var portadaSubida: String?
        get() = prefs.getString(prefijo + "portada_subida", null)
        set(v) = prefs.edit { if (v == null) remove(prefijo + "portada_subida") else putString(prefijo + "portada_subida", v) }

    /** La frase del saludo de esta franja del día ("franja\tclave\tobra"): no cambia hasta la próxima franja. */
    var fraseGuardada: String?
        get() = prefs.getString(prefijo + "frase_guardada", null)
        set(v) = prefs.edit { if (v == null) remove(prefijo + "frase_guardada") else putString(prefijo + "frase_guardada", v) }

    /** La sesión en Fogón Social (la red de El Cajón): el token, cifrado; y el @usuario, que no es secreto. */
    var tokenSocial: String?
        get() = leer("social")
        set(v) = escribir("social", v, confirmar = true) { putBoolean(prefijo + "apariencia_cuenta_pendiente", v != null) }
    /** Al entrar, la primera apariencia descifrada tiene prioridad; persiste hasta recuperarla. */
    var aparienciaCuentaPendiente: Boolean
        get() = prefs.getBoolean(prefijo + "apariencia_cuenta_pendiente", false)
        set(v) = prefs.edit { putBoolean(prefijo + "apariencia_cuenta_pendiente", v) }
    var usuarioSocial: String?
        get() = prefs.getString(prefijo + "usuario_social", null)
        set(v) = prefs.edit { if (v == null) remove(prefijo + "usuario_social") else putString(prefijo + "usuario_social", v) }
    /** Recuperación pendiente al entrar; persiste si se cierra la app sin red. */
    var perfilRedPendiente: Boolean
        get() = prefs.getBoolean(prefijo + "perfil_red_pendiente", false)
        set(v) = prefs.edit { putBoolean(prefijo + "perfil_red_pendiente", v) }
    /** Migra también las sesiones abiertas antes de recuperar la identidad al entrar. Escritura atómica. */
    fun migrarPerfilRed() {
        val migrada = prefs.getBoolean(prefijo + "perfil_red_migrado", false)
        if (migrada) return
        val pendiente = perfilPendienteTrasMigrar(tokenSocial != null, migrada, perfilRedPendiente)
        prefs.edit(commit = true) {
            putBoolean(prefijo + "perfil_red_pendiente", pendiente)
            putBoolean(prefijo + "perfil_red_migrado", true)
        }
    }

    /** Solo la acción explícita de quitar la foto permite borrarla de la cuenta. */
    var borrarFotoRed: Boolean
        get() = prefs.getBoolean(prefijo + "borrar_foto_red", false)
        set(v) = prefs.edit { putBoolean(prefijo + "borrar_foto_red", v) }

    /** La llave del cajón guardado en la cuenta (abierta, solo en este teléfono y cifrada aquí). Ver `Nube`. */
    var llaveCajon: String?
        get() = leer("llave_cajon")
        set(v) = escribir("llave_cajon", v, confirmar = true) {
            if (v == null || !permiteReemplazarCajon(v, reemplazarCajonRemoto)) remove(prefijo + "reemplazar_cajon_remoto")
        }

    /** Huella de la única llave autorizada para reemplazar un cajón anterior ilegible; null normalmente. */
    var reemplazarCajonRemoto: String?
        get() = prefs.getString(prefijo + "reemplazar_cajon_remoto", null)
        set(v) = prefs.edit(commit = true) {
            if (v == null) remove(prefijo + "reemplazar_cajon_remoto") else putString(prefijo + "reemplazar_cajon_remoto", v)
        }

    /** La llave cifrada y su autorización se guardan juntas: nunca queda la llave nueva sin su transición. */
    internal fun guardarLlaveRecuperada(recuperada: LlaveRecuperada) {
        if (!recuperada.reemplazar) { llaveCajon = recuperada.llave; return }
        escribir("llave_cajon", recuperada.llave, confirmar = true) {
            putString(prefijo + "reemplazar_cajon_remoto", huellaLlaveCajon(recuperada.llave))
        }
    }

    /** Las llaves del chat de este teléfono (keysets de Tink en JSON, con su parte secreta): solo aquí, cifradas. */
    var chatLlaves: String?
        get() = leer("chat_llaves")
        set(v) = escribir("chat_llaves", v)

    /** Ya se pusieron a la vista las partes del perfil de la red (una vez, al crear la cuenta o la primera vez que se abre). */
    var partesRedPuestas: Boolean
        get() = prefs.getBoolean(prefijo + "partes_red_puestas", false)
        set(v) = prefs.edit { putBoolean(prefijo + "partes_red_puestas", v) }

    /** La versión de la política que aceptó (si cambia, se vuelve a pedir). */
    var politicaSocial: String?
        get() = prefs.getString(prefijo + "politica_social", null)
        set(v) = prefs.edit { if (v == null) remove(prefijo + "politica_social") else putString(prefijo + "politica_social", v) }

    /** Lo de la sincronización que no es secreto: el usuario, el Gist y la última vez. */
    var usuario: String?
        get() = prefs.getString(prefijo + "usuario", null)
        set(v) = prefs.edit { if (v == null) remove(prefijo + "usuario") else putString(prefijo + "usuario", v) }
    var gist: String?
        get() = prefs.getString(prefijo + "gist", null)
        set(v) = prefs.edit { if (v == null) remove(prefijo + "gist") else putString(prefijo + "gist", v) }
    var ultima: Long
        get() = prefs.getLong(prefijo + "ultima", 0)
        set(v) = prefs.edit { putLong(prefijo + "ultima", v) }

    /**
     * Las obras que "Buscar portadas" ya revisó sin encontrar (para no repetir cientos de consultas cada vez que se
     * abre la app). Se olvidan a los 30 días, por si MusicBrainz o TMDB las agregan después. Solo en este teléfono.
     */
    var portadasRevisadas: Set<String>
        get() {
            if (System.currentTimeMillis() - prefs.getLong(prefijo + "portadas_desde", 0) > 30L * 24 * 3600 * 1000) return emptySet()
            return prefs.getStringSet(prefijo + "portadas_revisadas", emptySet()) ?: emptySet()
        }
        set(v) = prefs.edit {
            if (prefs.getStringSet(prefijo + "portadas_revisadas", emptySet()).isNullOrEmpty() || v.isEmpty()) putLong(prefijo + "portadas_desde", System.currentTimeMillis())
            putStringSet(prefijo + "portadas_revisadas", v)
        }

    /** 2 = los títulos de TMDB ya están en español latino (ver `CajonViewModel.pasarALatino`). Solo en este teléfono. */
    var versionCine: Int
        get() = prefs.getInt(prefijo + "version_cine", 0)
        set(v) = prefs.edit { putInt(prefijo + "version_cine", v) }

    /** 1 = lo no enlazado se volvió a buscar con las reglas de la 1.9.3. Solo en este teléfono. */
    var versionEnlace: Int
        get() = prefs.getInt(prefijo + "version_enlace", 0)
        set(v) = prefs.edit { putInt(prefijo + "version_enlace", v) }

    fun desconectarGithub() { tokenGithub = null; usuario = null; gist = null; ultima = 0 }
}

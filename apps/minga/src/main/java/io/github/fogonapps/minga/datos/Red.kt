package io.github.fogonapps.minga.datos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Lo único que La Minga hace con internet: leer el catálogo y las versiones, y bajar los APK. */
class Red(private val cliente: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()) {

    class ErrorRed(val codigo: Int, mensaje: String) : IOException(mensaje)

    companion object {
        const val URL_CATALOGO = "https://raw.githubusercontent.com/fogonapps/fogon/main/catalogo.json"
        const val URL_VERSIONES = "https://api.github.com/repos/fogonapps/fogon/releases?per_page=100"
        /** El tope de un APK: los de Fogón pesan menos de 15 MB. */
        const val MAX_APK = 80L * 1024 * 1024
    }

    private fun texto(url: String, aceptar: String): String {
        val r = Request.Builder().url(url).header("Accept", aceptar).header("User-Agent", "LaMinga-Fogon").build()
        cliente.newCall(r).execute().use { res ->
            if (!res.isSuccessful) throw ErrorRed(res.code, "HTTP ${res.code}")
            return res.body.string()
        }
    }

    suspend fun catalogo(): Catalogo = withContext(Dispatchers.IO) {
        JsonMinga.decodeFromString(Catalogo.serializer(), texto(URL_CATALOGO, "application/json"))
    }

    /** El JSON tal cual, para guardarlo y mostrar algo sin internet. */
    suspend fun versionesJson(): String = withContext(Dispatchers.IO) { texto(URL_VERSIONES, "application/vnd.github+json") }

    /** Baja un APK a [destino], avisando el avance (0 a 1). Devuelve su SHA-256 en hexadecimal. */
    suspend fun bajar(url: String, destino: File, avance: (Float) -> Unit): String = withContext(Dispatchers.IO) {
        val r = Request.Builder().url(url).header("User-Agent", "LaMinga-Fogon").build()
        cliente.newCall(r).execute().use { res ->
            if (!res.isSuccessful) throw ErrorRed(res.code, "HTTP ${res.code}")
            val total = res.body.contentLength()
            if (total > MAX_APK) throw IOException("APK demasiado grande")
            val md = MessageDigest.getInstance("SHA-256")
            destino.parentFile?.mkdirs()
            res.body.byteStream().use { ent ->
                destino.outputStream().use { sal ->
                    val buf = ByteArray(64 * 1024); var leidos = 0L
                    while (true) {
                        val n = ent.read(buf); if (n < 0) break
                        leidos += n
                        if (leidos > MAX_APK) throw IOException("APK demasiado grande")
                        sal.write(buf, 0, n); md.update(buf, 0, n)
                        if (total > 0) avance(leidos.toFloat() / total)
                    }
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

package io.github.fogonapps.cajon.datos

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Spotify (opcional): cada persona crea gratis su propia "app" en developer.spotify.com (Spotify pide que el dueño
 * tenga Premium) y pega aquí su Client ID; nunca va una clave en el código. El inicio de sesión es OAuth con PKCE
 * (sin secreto): se abre el navegador y Spotify vuelve a `http://127.0.0.1:<puerto>/callback`, que recibe un mini
 * servidor local solo mientras dura ([esperarCodigo]). Solo se lee: lo escuchado hace poco, los álbumes guardados y
 * los artistas que sigue. Nada de Spotify se guarda fuera de lo que la persona agrega a su cajón.
 */
class Spotify(private val cliente: String) {
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

    /** Lo que hace falta para iniciar sesión: la dirección a abrir, el verificador y el puerto local. */
    data class Inicio(val url: String, val verificador: String, val socket: ServerSocket) {
        val redireccion get() = "http://127.0.0.1:${socket.localPort}/callback"
    }

    fun iniciar(): Inicio {
        val bytes = ByteArray(48).also { SecureRandom().nextBytes(it) }
        val verificador = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val reto = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verificador.toByteArray()), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val socket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 5 * 60 * 1000 }
        val redireccion = "http://127.0.0.1:${socket.localPort}/callback"
        fun c(s: String) = URLEncoder.encode(s, "UTF-8")
        val url = "https://accounts.spotify.com/authorize?client_id=${c(cliente)}&response_type=code&redirect_uri=${c(redireccion)}" +
            "&code_challenge_method=S256&code_challenge=$reto&scope=${c(ALCANCE)}"
        return Inicio(url, verificador, socket)
    }

    /** Espera a que Spotify vuelva (hasta 5 minutos) y devuelve el código, o null si la persona no aceptó. */
    suspend fun esperarCodigo(i: Inicio): String? = withContext(Dispatchers.IO) {
        i.socket.use { s ->
            s.accept().use { con ->
                val linea = con.getInputStream().bufferedReader().readLine().orEmpty()
                val ruta = linea.split(" ").getOrNull(1).orEmpty()
                val params = ruta.substringAfter("?", "").split("&").filter { "=" in it }
                    .associate { it.substringBefore("=") to URLDecoder.decode(it.substringAfter("="), "UTF-8") }
                val ok = params["code"] != null
                val html = "<html><body style='font-family:sans-serif;text-align:center;padding:40px'><h2>" +
                    (if (ok) "Listo. Vuelve a El Cajón." else "No se conectó.") + "</h2></body></html>"
                con.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nConnection: close\r\n\r\n$html").toByteArray())
                params["code"]
            }
        }
    }

    /** Cambia el código por el acceso; devuelve (acceso, para renovar). */
    suspend fun canjear(i: Inicio, codigo: String): Pair<String, String> = token(FormBody.Builder().add("grant_type", "authorization_code")
        .add("code", codigo).add("redirect_uri", i.redireccion).add("client_id", cliente).add("code_verifier", i.verificador).build())

    suspend fun renovar(renovar: String): Pair<String, String> =
        token(FormBody.Builder().add("grant_type", "refresh_token").add("refresh_token", renovar).add("client_id", cliente).build(), renovar)

    private suspend fun token(cuerpo: FormBody, anterior: String = ""): Pair<String, String> = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url("https://accounts.spotify.com/api/token").post(cuerpo).build()).execute().use { r ->
            if (r.code == 400 || r.code == 401) throw ClaveMala()
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val j = JsonCajon.parseToJsonElement(r.body.string()).jsonObject
            j.getValue("access_token").jsonPrimitive.content to (j["refresh_token"]?.jsonPrimitive?.content ?: anterior)
        }
    }

    /** Spotify no deja usar su API a esta cuenta (sin Premium, o no es la dueña de la app). */
    class SinPermiso : IOException()

    /** Comprueba que la cuenta puede usar la API (la primera lectura después de conectar). */
    suspend fun probar(acceso: String) { pedir(acceso, "https://api.spotify.com/v1/me") }

    private suspend fun pedir(acceso: String, url: String): JsonObject = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).header("Authorization", "Bearer $acceso").build()).execute().use { r ->
            if (r.code == 401) throw ClaveMala()
            if (r.code == 403) throw SinPermiso()
            if (r.code == 429) throw Ocupado()
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            JsonCajon.parseToJsonElement(r.body.string()).jsonObject
        }
    }

    private fun JsonObject.texto(k: String) = this[k]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }.orEmpty()
    private fun artistas(o: JsonObject) = o["artists"]?.jsonArray?.joinToString(", ") { it.jsonObject.texto("name") }.orEmpty()

    /** Lo escuchado hace poco (las últimas 50 canciones, después de [desde]). */
    suspend fun recientes(acceso: String, desde: Long): List<Escucha> {
        val j = pedir(acceso, "https://api.spotify.com/v1/me/player/recently-played?limit=50" + if (desde > 0) "&after=$desde" else "")
        return j["items"]?.jsonArray.orEmpty().mapNotNull { e ->
            val o = e.jsonObject; val t = o["track"]?.jsonObject ?: return@mapNotNull null
            val al = t["album"]?.jsonObject ?: return@mapNotNull null
            val cuando = runCatching { java.time.Instant.parse(o.texto("played_at")).toEpochMilli() }.getOrDefault(System.currentTimeMillis())
            Escucha(artistas(al).substringBefore(","), al.texto("name"), t.texto("name"), cuando, "Spotify")
        }
    }

    /** Un álbum guardado o un artista seguido: lo justo para agregarlo al cajón (después se enlaza con MusicBrainz). */
    data class Guardado(val titulo: String, val artista: String, val anio: Int?)

    suspend fun albumesGuardados(acceso: String): List<Guardado> {
        val r = mutableListOf<Guardado>()
        var url: String? = "https://api.spotify.com/v1/me/albums?limit=50"
        while (url != null && r.size < 2000) {
            val j = pedir(acceso, url)
            j["items"]?.jsonArray.orEmpty().forEach { e ->
                val al = e.jsonObject["album"]?.jsonObject ?: return@forEach
                r += Guardado(al.texto("name"), artistas(al).substringBefore(","), al.texto("release_date").take(4).toIntOrNull())
            }
            url = j.texto("next").ifBlank { null }
        }
        return r
    }

    suspend fun artistasSeguidos(acceso: String): List<String> {
        val r = mutableListOf<String>()
        var url: String? = "https://api.spotify.com/v1/me/following?type=artist&limit=50"
        while (url != null && r.size < 2000) {
            val a = pedir(acceso, url)["artists"]?.jsonObject ?: break
            a["items"]?.jsonArray.orEmpty().forEach { r += it.jsonObject.texto("name") }
            url = a.texto("next").ifBlank { null }
        }
        return r.filter { it.isNotBlank() }
    }

    companion object {
        const val ALCANCE = "user-read-recently-played user-library-read user-follow-read"
        /** Crear una app directamente. */
        const val CREAR = "https://developer.spotify.com/dashboard/create"
        /** Un Client ID de Spotify: 32 letras (a-f) y números. */
        val FORMA_CLIENTE = Regex("[0-9a-fA-F]{32}")
        /** La dirección de vuelta que se registra en la app de Spotify (sin puerto: Spotify acepta el puerto que se elija). */
        const val VUELTA = "http://127.0.0.1/callback"
    }
}

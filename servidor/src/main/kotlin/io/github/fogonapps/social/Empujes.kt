package io.github.fogonapps.social

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** El token de Firebase Cloud Messaging de un teléfono para una app. */
@Serializable data class EmpujeNuevo(val token: String, val app: String = "cajon")

/** Manda un toque a [token] y devuelve el código HTTP (o -1 si no se pudo). */
fun interface Mensajero { fun enviar(token: String): Int }

/**
 * Avisos al instante con la app cerrada (X-10), por Firebase Cloud Messaging: cada teléfono registra su token y,
 * cuando a esa cuenta le llega un aviso o un mensaje, el servidor le manda un toque **vacío** (sin texto, sin de
 * quién); la app despierta y pregunta con su propia sesión. Google solo ve que hubo un toque. Varios seguidos se
 * juntan en uno cada [espera] milisegundos por token. Sin [mensajero] (sin credenciales de FCM) no se manda nada.
 */
class Empujes(
    private val b: Base,
    private val ahora: () -> Long,
    private val mensajero: Mensajero?,
    private val espera: Long = 15_000,
    private val hilo: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { Thread(it, "empujes").apply { isDaemon = true } },
) {
    companion object {
        const val MAXIMO = 10
        private val TOKEN = Regex("[A-Za-z0-9_:\\-]{20,4096}")
        fun valido(token: String) = TOKEN.matches(token)
    }

    private val ultimo = ConcurrentHashMap<String, Long>()
    private val pendiente = ConcurrentHashMap.newKeySet<String>()

    /** [sesion]: la sesión con que se registró; al cerrarla (salir, cerrar sesiones), el token deja de servir y se borra. */
    fun registrar(yo: Long, e: EmpujeNuevo, sesion: String? = null) {
        val app = AppsSocial.validar(e.app)
        if (!valido(e.token)) throw Problema(400, "token")
        val huella = sesion?.let { Base.huella(it) }
        b.transaccion {
            cambiar("INSERT INTO empujes(endpoint, cuenta, app, cuando, sesion) VALUES(?,?,?,?,?) ON CONFLICT(endpoint) DO UPDATE SET cuenta = excluded.cuenta, app = excluded.app, cuando = excluded.cuando, sesion = excluded.sesion",
                e.token, yo, app, ahora(), huella)
            cambiar("DELETE FROM empujes WHERE cuenta = ? AND endpoint NOT IN (SELECT endpoint FROM empujes WHERE cuenta = ? ORDER BY cuando DESC LIMIT $MAXIMO)", yo, yo)
        }
    }

    fun quitar(yo: Long, token: String) { b.usar { cambiar("DELETE FROM empujes WHERE cuenta = ? AND endpoint = ?", yo, token) } }

    /** Toca los teléfonos de [cuenta] en [app] (o en todas sus apps si es null). No espera: va en su propio hilo. */
    fun tocar(cuenta: Long, app: String? = null) {
        if (mensajero == null) return
        val lista = b.usar {
            // Los de sesiones ya cerradas no se tocan y se borran.
            cambiar("DELETE FROM empujes WHERE cuenta = ? AND sesion IS NOT NULL AND sesion NOT IN (SELECT huella FROM sesiones WHERE cuenta = ?)", cuenta, cuenta)
            if (app == null) leer("SELECT endpoint FROM empujes WHERE cuenta = ?", cuenta) { it.getString(1) }
            else leer("SELECT endpoint FROM empujes WHERE cuenta = ? AND app = ?", cuenta, app) { it.getString(1) }
        }
        lista.forEach(::programar)
    }

    private fun programar(token: String) {
        val m = mensajero ?: return
        if (!pendiente.add(token)) return
        val falta = ((ultimo[token] ?: 0L) + espera - ahora()).coerceAtLeast(0)
        hilo.schedule({
            pendiente.remove(token)
            ultimo[token] = ahora()
            // 404: el token ya no existe (la app se desinstaló o se borraron sus datos).
            if (m.enviar(token) == 404) runCatching { b.usar { cambiar("DELETE FROM empujes WHERE endpoint = ?", token) } }
        }, falta, TimeUnit.MILLISECONDS)
    }
}

/**
 * El envío por la API HTTP v1 de FCM con una cuenta de servicio de Google (el JSON que se descarga de Firebase;
 * vive en el servidor, nunca en el repo). El toque es un mensaje de datos con prioridad alta y sin texto.
 */
class Fcm(json: String) : Mensajero {
    private val proyecto: String
    private val correo: String
    private val llave: java.security.PrivateKey
    init {
        val o = Json.parseToJsonElement(json).jsonObject
        proyecto = o.getValue("project_id").jsonPrimitive.content
        correo = o.getValue("client_email").jsonPrimitive.content
        val pem = o.getValue("private_key").jsonPrimitive.content
            .replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replace(Regex("\\s"), "")
        llave = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)))
    }

    @Volatile private var acceso: Pair<String, Long>? = null

    private fun b64(x: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(x)

    @Synchronized private fun accesoVigente(): String {
        acceso?.let { (t, hasta) -> if (System.currentTimeMillis() < hasta) return t }
        val ahora = System.currentTimeMillis() / 1000
        val cabeza = b64("""{"alg":"RS256","typ":"JWT"}""".toByteArray())
        val cuerpo = b64("""{"iss":"$correo","scope":"https://www.googleapis.com/auth/firebase.messaging","aud":"https://oauth2.googleapis.com/token","iat":$ahora,"exp":${ahora + 3600}}""".toByteArray())
        val firma = Signature.getInstance("SHA256withRSA").run { initSign(llave); update("$cabeza.$cuerpo".toByteArray()); sign() }
        val jwt = "$cabeza.$cuerpo.${b64(firma)}"
        val respuesta = pedir("https://oauth2.googleapis.com/token", "application/x-www-form-urlencoded",
            "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", "UTF-8") + "&assertion=" + jwt, null)
        check(respuesta.first == 200) { "FCM: no se pudo obtener el acceso (${respuesta.first})" }
        val token = Json.parseToJsonElement(respuesta.second).jsonObject.getValue("access_token").jsonPrimitive.content
        acceso = token to (System.currentTimeMillis() + 50 * 60_000)
        return token
    }

    override fun enviar(token: String): Int = try {
        val cuerpo = """{"message":{"token":"$token","android":{"priority":"high","ttl":"86400s"},"data":{"t":"1"}}}"""
        val r = pedir("https://fcm.googleapis.com/v1/projects/$proyecto/messages:send", "application/json", cuerpo, accesoVigente())
        if (r.first == 401) { acceso = null }
        // UNREGISTERED llega como 404; un token mal formado, como 400 INVALID_ARGUMENT: los dos se olvidan.
        if (r.first == 400 && "INVALID_ARGUMENT" in r.second) 404 else r.first
    } catch (_: Exception) { -1 }

    private fun pedir(url: String, tipo: String, cuerpo: String, portador: String?): Pair<Int, String> =
        (URI(url).toURL().openConnection() as HttpURLConnection).run {
            connectTimeout = 8000; readTimeout = 8000; requestMethod = "POST"; doOutput = true
            setRequestProperty("Content-Type", tipo)
            portador?.let { setRequestProperty("Authorization", "Bearer $it") }
            outputStream.use { it.write(cuerpo.toByteArray()) }
            val codigo = responseCode
            val texto = (if (codigo < 400) inputStream else errorStream)?.use { it.readBytes().decodeToString() }.orEmpty()
            disconnect()
            codigo to texto
        }
}

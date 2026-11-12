package io.github.fogonapps.social

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/** Una canción para una nota o una historia: su avance de 30 segundos y el enlace a Apple Music (lo piden sus condiciones). */
@Serializable data class Cancion(val titulo: String, val artista: String, val portada: String, val audio: String, val enlace: String, val album: String = "")

/**
 * Buscar canciones en la búsqueda pública de Apple (iTunes Search API, sin clave). Pasa por el servidor para que el
 * teléfono no le diga a Apple qué busca; los resultados se guardan un día (Apple pide unas 20 consultas por minuto).
 * El avance se usa solo para mostrar la canción, junto al botón «Escuchar en Apple Music».
 */
class Canciones(private val ahora: () -> Long = System::currentTimeMillis,
                private val pedir: (String) -> String? = ::pedirApple) {
    private val guardadas = ConcurrentHashMap<String, Pair<Long, List<Cancion>>>()

    fun buscar(consulta: String, pais: String = "CO"): List<Cancion> {
        val q = consulta.trim().take(80)
        if (q.length < 2) return emptyList()
        val p = pais.uppercase().takeIf { Regex("[A-Z]{2}").matches(it) } ?: "CO"
        val llave = "$p:${q.lowercase()}"
        guardadas[llave]?.let { (cuando, l) -> if (ahora() - cuando < DIA) return l }
        val url = "https://itunes.apple.com/search?media=music&entity=song&limit=15&country=$p&term=" + URLEncoder.encode(q, "UTF-8")
        val cuerpo = pedir(url) ?: return emptyList()
        val lista = leer(cuerpo)
        if (guardadas.size > 5000) guardadas.clear()
        guardadas[llave] = ahora() to lista
        return lista
    }

    companion object {
        private const val DIA = 24 * 3600_000L
        private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
        private fun pedirApple(url: String): String? = runCatching {
            val r = http.send(HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(15)).header("User-Agent", "FogonSocial/$VERSION (+https://fogonapps.duckdns.org)").GET().build(),
                HttpResponse.BodyHandlers.ofString())
            r.body().takeIf { r.statusCode() == 200 }
        }.getOrNull()

        /** Solo canciones con avance, de los dominios de Apple (los mismos que acepta [Adjuntos]). */
        fun leer(cuerpo: String): List<Cancion> = runCatching {
            (JsonSocial.parseToJsonElement(cuerpo).jsonObject["results"] as? JsonArray).orEmpty().mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                fun t(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val c = Cancion(t("trackName").take(120), t("artistName").take(120), t("artworkUrl100").replace("100x100bb", "300x300bb"), t("previewUrl"), t("trackViewUrl"), t("collectionName").take(120))
                c.takeIf { it.titulo.isNotBlank() && Adjuntos.audioSeguro(it.audio) && Adjuntos.enlaceSeguro(it.enlace) && Adjuntos.imagenApple(it.portada) }
            }
        }.getOrDefault(emptyList())
    }
}

/**
 * Lo que acompaña una nota o una historia: una canción, una obra, un entreno, un plato, una meta… Cada app arma el
 * suyo; el servidor solo deja pasar campos conocidos, textos cortos y direcciones de sitios permitidos.
 */
object Adjuntos {
    val TIPOS = setOf("cancion", "obra", "entreno", "ejercicio", "logro", "plato", "receta", "meta", "ahorro", "racha", "lugar")
    private val HOSTS_AUDIO = Regex("""https://audio-ssl\.itunes\.apple\.com/[^\s]{1,400}""")
    private val HOSTS_ENLACE = setOf("music.apple.com", "www.youtube.com", "youtube.com", "m.youtube.com", "www.themoviedb.org", "fogonapps.duckdns.org")

    fun audioSeguro(u: String) = HOSTS_AUDIO.matches(u)
    fun enlaceSeguro(u: String) = u.startsWith("https://") && u.length <= 400 && u.removePrefix("https://").substringBefore('/').substringBefore('?').lowercase() in HOSTS_ENLACE
    fun imagenApple(u: String) = Regex("""https://is[0-9]-ssl\.mzstatic\.com/[^\s]{1,400}""").matches(u)
    private fun imagenSegura(u: String) = imagenApple(u) || Social.portadaSegura(u) || Regex("[A-Za-z0-9_-]{22}").matches(u)

    /** El adjunto limpio, o null si no sirve. */
    fun limpio(o: JsonObject?): JsonObject? {
        o ?: return null
        fun t(k: String, max: Int) = (o[k] as? JsonPrimitive)?.contentOrNull?.filter { !it.isISOControl() }?.trim()?.take(max).orEmpty()
        val tipo = t("tipo", 20).takeIf { it in TIPOS } ?: return null
        val titulo = t("titulo", 120).ifBlank { return null }
        return buildJsonObject {
            put("tipo", tipo); put("titulo", titulo)
            t("detalle", 160).takeIf { it.isNotBlank() }?.let { put("detalle", it) }
            t("cifra", 40).takeIf { it.isNotBlank() }?.let { put("cifra", it) }
            t("fuente", 80).takeIf { it.isNotBlank() }?.let { put("fuente", it) }
            t("tipoObra", 20).takeIf { it.isNotBlank() }?.let { put("tipoObra", it) }
            t("imagen", 400).takeIf { imagenSegura(it) }?.let { put("imagen", it) }
            t("audio", 400).takeIf { audioSeguro(it) }?.let { put("audio", it) }
            t("enlace", 400).takeIf { enlaceSeguro(it) }?.let { put("enlace", it) }
            t("escena", 20).takeIf { Regex("[A-Za-z0-9_-]{6,20}").matches(it) }?.let { put("escena", it) }
            (o["segundo"] as? JsonPrimitive)?.intOrNull?.takeIf { it in 0..36_000 }?.let { put("segundo", it) }
        }
    }
}

package io.github.fogonapps.social

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.text.Normalizer
import java.time.Duration
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/** Una canción para una nota o una historia: su avance de 30 segundos y el enlace a Apple Music (lo piden sus condiciones). */
@Serializable data class Cancion(val titulo: String, val artista: String, val portada: String, val audio: String, val enlace: String, val album: String = "")

/**
 * Buscar canciones en la búsqueda pública de Apple (iTunes Search API, sin clave). Pasa por el servidor para que el
 * teléfono no le diga a Apple qué busca; los resultados se guardan un día (Apple pide unas 20 consultas por minuto) en
 * una [CacheTemporal]: lo vencido se barre cada minuto, así que ni las búsquedas ni sus resultados viven más de un día.
 * El avance se usa solo para mostrar la canción, junto al botón «Escuchar en Apple Music».
 */
class Canciones(private val ahora: () -> Long = System::currentTimeMillis,
                private val pedir: (String) -> String? = ::pedirApple) {
    private val guardadas = CacheTemporal<List<Cancion>>(DIA, MAX_ENTRADAS, MAX_CACHE, ahora) { l -> l.sumOf { peso(it) } }

    private val catalogos = CacheTemporal<List<Resultado>>(DIA, MAX_ENTRADAS, MAX_CACHE, ahora) { l -> l.sumOf { peso(it.cancion) } }

    /** Borra lo vencido (lo llama el servidor cada minuto). */
    fun barrer() {
        guardadas.barrer()
        catalogos.barrer()
    }

    /** Cuántas búsquedas y catálogos hay en memoria (para las pruebas). */
    internal val guardadasEnMemoria: Int get() = guardadas.cantidad + catalogos.cantidad

    private data class Resultado(val artistaId: Long?, val cancion: Cancion) {
        fun coincide(palabras: List<String>): Boolean =
            listOf(cancion.titulo, cancion.album).any { texto ->
                val normalizado = " ${normalizar(texto)} "
                palabras.all { " $it " in normalizado }
            }
    }

    private fun catalogo(artistaId: Long, pais: String): List<Resultado> {
        val llave = "$pais:$artistaId"
        catalogos.leer(llave)?.let { return it }
        val cuerpo = pedir("https://itunes.apple.com/lookup?id=$artistaId&entity=song&limit=200&country=$pais")
            ?: return emptyList()
        val lista = leerCrudo(cuerpo)
        catalogos.guardar(llave, lista)
        return lista
    }

    fun buscar(consulta: String, pais: String = "CO"): List<Cancion> {
        val q = consulta.trim().take(80)
        if (q.length < 2) return emptyList()
        val p = pais.uppercase().takeIf { Regex("[A-Z]{2}").matches(it) } ?: "CO"
        val llave = "$p:${q.lowercase()}"
        guardadas.leer(llave)?.let { return it }
        val url = "https://itunes.apple.com/search?media=music&entity=song&limit=15&country=$p&term=" + URLEncoder.encode(q, "UTF-8")
        val cuerpo = pedir(url) ?: return emptyList()
        val resultados = leerCrudo(cuerpo)
        val normalizada = " ${normalizar(q)} "
        var respaldo = emptyList<Resultado>()
        for (resultado in resultados.distinctBy { it.artistaId }) {
            val artistaId = resultado.artistaId ?: continue
            val artista = normalizar(resultado.cancion.artista)
            if (artista.isEmpty() || " $artista " !in normalizada) continue
            val resto = normalizada.replace(" $artista ", " ").trim()
            if (resto.isEmpty()) continue
            val palabras = resto.split(Regex("\\s+"))
            if (resultados.any { it.artistaId == artistaId && it.coincide(palabras) }) continue
            respaldo = catalogo(artistaId, p).filter { it.coincide(palabras) }
            break
        }
        val enlaces = mutableSetOf<String>()
        val titulos = mutableSetOf<Pair<String, String>>()
        val lista = (respaldo + resultados).map { it.cancion }.filter { c ->
            val titulo = normalizar(c.titulo) to normalizar(c.artista)
            if (c.titulo.isBlank() || !Adjuntos.audioSeguro(c.audio) ||
                !Adjuntos.enlaceSeguro(c.enlace) || !Adjuntos.imagenApple(c.portada) ||
                c.enlace in enlaces || titulo in titulos) false
            else {
                enlaces.add(c.enlace)
                titulos.add(titulo)
                true
            }
        }.take(15)
        guardadas.guardar(llave, lista)
        return lista
    }

    companion object {
        private const val DIA = 24 * 3600_000L
        private const val MAX_ENTRADAS = 5_000
        /** Caracteres por caché (unos 16 MB cada una): un catálogo de 200 canciones pesa unos 90 000. */
        private const val MAX_CACHE = 8_000_000L

        private fun peso(c: Cancion): Long =
            (c.titulo.length + c.artista.length + c.portada.length + c.audio.length + c.enlace.length + c.album.length).toLong()
        private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
        private fun pedirApple(url: String): String? = runCatching {
            val r = http.send(HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(15)).header("User-Agent", "FogonSocial/$VERSION (+https://fogonapps.duckdns.org)").GET().build(),
                HttpResponse.BodyHandlers.ofString())
            r.body().takeIf { r.statusCode() == 200 }
        }.getOrNull()

        private fun normalizar(texto: String): String =
            Normalizer.normalize(texto.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

        /** Solo canciones con avance, de los dominios de Apple (los mismos que acepta [Adjuntos]). */
        fun leer(cuerpo: String): List<Cancion> = leerCrudo(cuerpo).map { it.cancion }.filter {
            it.titulo.isNotBlank() && Adjuntos.audioSeguro(it.audio) &&
                Adjuntos.enlaceSeguro(it.enlace) && Adjuntos.imagenApple(it.portada)
        }

        private fun leerCrudo(cuerpo: String): List<Resultado> = runCatching {
            (JsonSocial.parseToJsonElement(cuerpo).jsonObject["results"] as? JsonArray).orEmpty().mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                fun t(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
                if (t("wrapperType") == "artist") return@mapNotNull null
                val c = Cancion(t("trackName").take(120), t("artistName").take(120), t("artworkUrl100").replace("100x100bb", "300x300bb"), t("previewUrl"), t("trackViewUrl"), t("collectionName").take(120))
                Resultado(t("artistId").toLongOrNull()?.takeIf { it > 0 }, c)
            }
        }.getOrDefault(emptyList())
    }
}

/**
 * Lo que acompaña una nota o una historia: una canción, una obra, un entreno, un plato, una meta… Cada app arma el
 * suyo; el servidor solo deja pasar campos conocidos, textos cortos y direcciones de sitios permitidos.
 */
object Adjuntos {
    val TIPOS = setOf("cancion", "artista", "publicacion", "obra", "entreno", "ejercicio", "logro", "plato", "receta", "meta", "ahorro", "racha", "lugar")
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

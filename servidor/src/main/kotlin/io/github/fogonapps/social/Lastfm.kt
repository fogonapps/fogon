package io.github.fogonapps.social

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/** Intermediario de perfiles públicos; nunca acepta destinos ni claves del cliente. */
class Lastfm(
    private val clave: String?,
    private val ahora: () -> Long = System::currentTimeMillis,
    private val enviar: (HttpRequest) -> Cine.Respuesta = { pedido ->
        val r = http.send(pedido, HttpResponse.BodyHandlers.ofString())
        Cine.Respuesta(r.statusCode(), r.body())
    },
) {
    companion object {
        private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
    }

    private val cache = ConcurrentHashMap<String, Pair<Long, Cine.Respuesta>>()

    fun pedir(parametros: Map<String, String>): Cine.Respuesta {
        if (clave.isNullOrBlank()) return Cine.Respuesta(503, """{"error":"sin_lastfm"}""")
        val metodo = parametros["method"]
        val usuario = parametros["user"].orEmpty()
        val numeros = parametros.filterKeys { it in setOf("page", "from", "to") }
        if (metodo !in setOf("user.getrecenttracks", "user.getinfo") || usuario.isBlank() || usuario.length > 100 ||
            numeros.any { (k, v) -> v.toLongOrNull()?.let { it < if (k == "page") 1 else 0 } != false })
            return Cine.Respuesta(400, """{"error":"parametros"}""")
        val consulta = (mapOf("method" to metodo!!, "user" to usuario, "format" to "json") +
            if (metodo == "user.getrecenttracks") numeros + ("limit" to "200") else emptyMap()).toSortedMap()
        fun codificar(m: Map<String, String>) = m.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        val llave = codificar(consulta)
        cache[llave]?.let { if (ahora() - it.first < 300_000) return it.second }
        val pedido = HttpRequest.newBuilder(URI("https://ws.audioscrobbler.com/2.0/?" + codificar(consulta + ("api_key" to clave))))
            .timeout(Duration.ofSeconds(20)).header("User-Agent", "FogonSocial/$VERSION (+https://fogonapps.duckdns.org)")
            .header("Accept", "application/json").GET().build()
        val r = try { enviar(pedido) } catch (_: Exception) { return Cine.Respuesta(502, """{"error":"lastfm"}""") }
        if (r.codigo == 200) {
            if (cache.size >= 2000) cache.clear()
            cache[llave] = ahora() to r
        }
        return r
    }
}

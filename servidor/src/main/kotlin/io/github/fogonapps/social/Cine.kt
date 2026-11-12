package io.github.fogonapps.social

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * TMDB a través del servidor: la app pide aquí lo mismo que le pediría a TMDB y el servidor le suma la clave de
 * Fogón (`TMDB_API_KEY`, solo en el servidor). Solo pasan las rutas que usa El Cajón; las respuestas se guardan 6
 * horas (menos pedidos a TMDB y más rápido); las imágenes siguen yendo directo a image.tmdb.org (no piden clave).
 */
class Cine(private val clave: String?, private val ahora: () -> Long = System::currentTimeMillis) {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
    private val guardadas = ConcurrentHashMap<String, Pair<Long, Respuesta>>()
    private val ocupado = java.util.concurrent.atomic.AtomicLong()

    data class Respuesta(val codigo: Int, val cuerpo: String)

    val lista get() = !clave.isNullOrBlank()

    /** Las rutas que El Cajón usa (búsquedas, fichas, tendencias, plataformas, personas, géneros, episodios). */
    fun permitida(ruta: String): Boolean = Regex(
        "(search/(multi|movie|tv|person)|(movie|tv)/[0-9]+(/(recommendations|alternative_titles|credits|external_ids|images|videos|season/[0-9]+|watch/providers))?|" +
            "person/[0-9]+(/(combined_credits|movie_credits|tv_credits|external_ids))?|discover/(movie|tv)|trending/(all|movie|tv)/(day|week)|" +
            "genre/(movie|tv)/list|watch/providers/(movie|tv)|configuration)"
    ).matches(ruta)

    fun pedir(ruta: String, parametros: Map<String, String>): Respuesta {
        if (!lista) return Respuesta(503, """{"error":"sin_tmdb"}""")
        if (!permitida(ruta)) return Respuesta(404, """{"error":"ruta"}""")
        val consulta = parametros.filterKeys { it != "api_key" }.toSortedMap()
            .entries.joinToString("&") { (k, v) -> URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8") }
        val llave = "$ruta?$consulta"
        guardadas[llave]?.let { (cuando, r) -> if (ahora() - cuando < SEIS_HORAS) return r }
        val url = "https://api.themoviedb.org/3/$ruta?" + (if (consulta.isEmpty()) "" else "$consulta&") + "api_key=" + URLEncoder.encode(clave, "UTF-8")
        val res = http.send(HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(20)).header("Accept", "application/json").GET().build(),
            HttpResponse.BodyHandlers.ofString())
        val r = Respuesta(res.statusCode(), res.body())
        // La caché se acota por tamaño (no por cantidad): con búsquedas distintas sin parar se llenaba la memoria.
        if (r.codigo == 200 && r.cuerpo.length <= 400_000) {
            if (ocupado.get() + r.cuerpo.length > MAX_CACHE) {
                guardadas.entries.removeIf { ahora() - it.value.first >= SEIS_HORAS }
                ocupado.set(guardadas.values.sumOf { it.second.cuerpo.length.toLong() })
                if (ocupado.get() + r.cuerpo.length > MAX_CACHE) { guardadas.clear(); ocupado.set(0) }
            }
            val anterior = guardadas.put(llave, ahora() to r)
            ocupado.addAndGet(r.cuerpo.length.toLong() - (anterior?.second?.cuerpo?.length ?: 0))
        }
        return r
    }

    companion object { const val SEIS_HORAS = 6 * 3600_000L; const val MAX_CACHE = 24_000_000L }
}

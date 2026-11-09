package io.github.fogonapps.cajon.datos

import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlin.coroutines.coroutineContext

/** Importación voluntaria de un perfil público, sin credenciales en el teléfono. */
class Lastfm {
    private val http = OkHttpClient.Builder().callTimeout(25, java.util.concurrent.TimeUnit.SECONDS).build()

    data class Resumen(val escuchas: Long, val desde: Int)
    data class Pagina(val escuchas: List<Escucha>, val paginas: Int, val total: Long)
    class Fallo(val codigo: Int) : IOException()

    private suspend fun pedir(metodo: String, usuario: String, parametros: Map<String, String> = emptyMap()): JsonObject = suspendCancellableCoroutine { espera ->
        val url = (ClienteSocial.SERVIDOR + "/v1/lastfm").toHttpUrl().newBuilder()
            .addQueryParameter("method", metodo).addQueryParameter("user", usuario)
        parametros.forEach { (k, v) -> url.addQueryParameter(k, v) }
        val llamada = http.newCall(Request.Builder().url(url.build()).build())
        espera.invokeOnCancellation { llamada.cancel() }
        llamada.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                if (espera.isActive) espera.resumeWith(Result.failure(e))
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val resultado = runCatching {
                    response.use { r ->
                        if (!r.isSuccessful) throw Fallo(r.code)
                        val j = JsonCajon.parseToJsonElement(r.body.string()).jsonObject
                        j["error"]?.jsonPrimitive?.intOrNull?.let { throw Fallo(it) }
                        j
                    }
                }
                if (espera.isActive) espera.resumeWith(resultado)
            }
        })
    }

    suspend fun resumen(usuario: String): Resumen {
        val u = pedir("user.getinfo", usuario).getValue("user").jsonObject
        val fecha = u.getValue("registered").jsonObject.getValue("unixtime").jsonPrimitive.content.toLong()
        return Resumen(u.getValue("playcount").jsonPrimitive.content.toLong(),
            java.time.Instant.ofEpochSecond(fecha).atZone(java.time.ZoneOffset.UTC).year)
    }

    /** Fija el extremo superior para que nuevas escuchas no desplacen las páginas. Una petición cada 1,1 s. */
    suspend fun importar(usuario: String, desde: Long, avance: suspend (Pagina, Int) -> Unit) {
        val hasta = System.currentTimeMillis() / 1000
        var pagina = 1
        do {
            coroutineContext.ensureActive()
            val j = pedir("user.getrecenttracks", usuario, mapOf("page" to pagina.toString(), "from" to desde.toString(), "to" to hasta.toString()))
            val p = convertir(j)
            avance(p, pagina)
            if (pagina >= p.paginas) break
            pagina++
            delay(1100)
        } while (true)
    }

    companion object {
        /** Ignora lo que está sonando y entradas sin fecha, artista, álbum o canción. */
        fun convertir(j: JsonObject): Pagina {
            val r = j.getValue("recenttracks").jsonObject
            val a = r.getValue("@attr").jsonObject
            val pistas = when (val t = r["track"]) { is JsonArray -> t.toList(); is JsonObject -> listOf(t); else -> emptyList() }
            val escuchas = pistas.mapNotNull { e ->
                val t = e.jsonObject
                if (t["@attr"]?.jsonObject?.get("nowplaying")?.jsonPrimitive?.content == "true") return@mapNotNull null
                val fecha = t["date"]?.jsonObject?.get("uts")?.jsonPrimitive?.content?.toLongOrNull()
                    ?.takeIf { it > 0 && it <= Long.MAX_VALUE / 1000 } ?: return@mapNotNull null
                val artista = t["artist"]?.jsonObject?.get("#text")?.jsonPrimitive?.content.orEmpty().trim()
                val album = t["album"]?.jsonObject?.get("#text")?.jsonPrimitive?.content.orEmpty().trim()
                val cancion = t["name"]?.jsonPrimitive?.content.orEmpty().trim()
                if (artista.isBlank() || album.isBlank() || cancion.isBlank()) return@mapNotNull null
                Escucha(artista, album, cancion, fecha * 1000, "Last.fm")
            }
            return Pagina(escuchas, a["totalPages"]?.jsonPrimitive?.content?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                a["total"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0)
        }
    }
}

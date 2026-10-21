package io.github.fogonapps.forzudo.datos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ErrorGitHub(val codigo: Int) : IOException("HTTP $codigo")

/**
 * Sincronización con un Gist secreto de la cuenta de GitHub de la persona,
 * igual que la versión web: mismo archivo y mismo formato.
 */
class ClienteGist(private val token: String) {

    private fun base(url: String) = Request.Builder().url(url)
        .header("Accept", "application/vnd.github+json")
        .header("Authorization", "Bearer $token")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("User-Agent", "ElForzudo-Fogon")
        .header("Cache-Control", "no-store")

    private suspend fun pedir(ruta: String, metodo: String = "GET", cuerpo: JsonElement? = null): JsonElement? =
        withContext(Dispatchers.IO) {
            val body = cuerpo?.toString()?.toRequestBody(JSON)
            val req = base(API + ruta).method(metodo, body).build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw ErrorGitHub(r.code)
                if (r.code == 204) null else JsonGym.parseToJsonElement(r.body.string())
            }
        }

    suspend fun usuario(): String = pedir("/user")!!.jsonObject.getValue("login").jsonPrimitive.content

    /** Busca el Gist de la app; si no existe, lo crea con el contenido dado. Devuelve su id. */
    suspend fun buscarOCrear(inicial: String): String {
        val lista = pedir("/gists?per_page=100") as? JsonArray ?: JsonArray(emptyList())
        lista.firstOrNull { (it.jsonObject["files"] as? JsonObject)?.containsKey(ARCHIVO) == true }
            ?.let { return it.jsonObject.getValue("id").jsonPrimitive.content }
        val nuevo = pedir("/gists", "POST", buildJsonObject {
            put("description", "El Forzudo: datos de entreno (no borrar)")
            put("public", false)
            put("files", buildJsonObject { put(ARCHIVO, buildJsonObject { put("content", inicial) }) })
        })
        return nuevo!!.jsonObject.getValue("id").jsonPrimitive.content
    }

    /** Lee el archivo del Gist. Null si el Gist no lo tiene. */
    suspend fun leer(gist: String): String? {
        val g = pedir("/gists/$gist")!!.jsonObject
        val f = (g["files"] as? JsonObject)?.get(ARCHIVO)?.jsonObject ?: return null
        val truncado = (f["truncated"] as? JsonPrimitive)?.boolean == true
        if (!truncado) return f["content"]?.jsonPrimitive?.content
        val url = f.getValue("raw_url").jsonPrimitive.content
        return withContext(Dispatchers.IO) {
            http.newCall(base(url).build()).execute().use { r ->
                if (!r.isSuccessful) throw ErrorGitHub(r.code)
                r.body.string()
            }
        }
    }

    suspend fun escribir(gist: String, contenido: String) {
        pedir("/gists/$gist", "PATCH", buildJsonObject {
            put("files", buildJsonObject { put(ARCHIVO, buildJsonObject { put("content", contenido) }) })
        })
    }

    companion object {
        // Mismo nombre que la versión web y que Mi Gym, para seguir encontrando los datos que ya existen.
        const val ARCHIVO = "mi-gym-datos.json"
        const val URL_TOKEN = "https://github.com/settings/tokens/new?scopes=gist&description=El%20Forzudo%20app"
        private const val API = "https://api.github.com"
        private val JSON = "application/json".toMediaType()
        private val http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

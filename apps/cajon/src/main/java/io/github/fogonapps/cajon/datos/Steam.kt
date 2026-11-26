package io.github.fogonapps.cajon.datos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * La biblioteca de Steam de la persona, por el servidor de Fogón (`/v1/steam/biblioteca`, que pone la clave y no guarda
 * nada). El perfil sale del teléfono solo al tocar «Importar». Cada juego llega con su id de IGDB, si tiene ficha:
 * entra como `igdb:<id>`; si no, con su nombre, como un juego sin ficha (igual que lo que traen los otros importadores
 * sin fuente). Las horas jugadas quedan en [Obra.minutosSteam], con su propia hora ([Obra.minutosSteamCuando]).
 */
object Steam {
    /** La página de privacidad del perfil en Steam: ahí se ponen públicos los detalles de juego. */
    const val PRIVACIDAD = "https://steamcommunity.com/my/edit/settings"

    /* Los errores del servidor. */
    const val PERFIL_INVALIDO = "perfil_invalido"
    const val NO_ENCONTRADO = "perfil_no_encontrado"
    const val PRIVADA = "biblioteca_privada"
    const val SIN_STEAM = "sin_steam"
    const val NO_RESPONDE = "steam_no_responde"
    /** Se acabó lo de hoy: mañana se puede otra vez. */
    const val CUPO_DIARIO = "cupo_diario"
    /** Muchos pedidos seguidos: en un rato. */
    const val DESPACIO = "despacio"
    /** El servidor no pudo reconocer los juegos (IGDB falló). */
    const val IGDB = "igdb"
    /** Otra importación en curso en el servidor. */
    const val OCUPADO = "ocupado"
    /** Sin internet (no lo dice el servidor). */
    const val SIN_RED = "sin_red"

    data class Juego(val appid: Long, val nombre: String, val minutos: Int, val minutos2semanas: Int, val igdb: Long?)

    /** Lo que trajo el servidor: los juegos y, si la biblioteca era muy grande, que se recortó a los más jugados ([total]). */
    data class Biblioteca(val juegos: List<Juego>, val recortada: Boolean = false, val total: Int = juegos.size)

    /** Lo que respondió el servidor cuando no es la lista: su `error` («biblioteca_privada»…). */
    class Fallo(val error: String) : IOException()

    private val NUMERO = Regex("7656119[0-9]{10}")
    private val NOMBRE = Regex("[A-Za-z0-9_-]{2,32}")
    /** El enlace del perfil, también con lo que venga después: `/games/?tab=all`, `/` o `?l=spanish`. */
    private val ENLACE = Regex("(?:https?://)?(?:www\\.)?steamcommunity\\.com/(id|profiles)/([^/?#\\s]{1,64})(?:/[^?#\\s]*)?(?:[?#]\\S*)?", RegexOption.IGNORE_CASE)

    /** La vuelta de «Entrar con Steam», en el servidor de Fogón: Android la abre en Fabs (assetlinks). */
    const val RUTA_VUELTA = "/steam/vuelta"
    private const val SITIO = "https://fogonapps.duckdns.org"
    private val CUENTA_OPENID = Regex("https://steamcommunity\\.com/openid/id/(7656119[0-9]{10})")

    /**
     * «Entrar con Steam»: la página oficial de Steam (OpenID 2.0). La persona entra allá, con su contraseña y su
     * verificación, y Steam solo le dice a Fabs el número de su cuenta. [estado] es un valor al azar de este teléfono:
     * la vuelta se acepta solo si lo trae, para que un enlace ajeno no meta juegos de otra cuenta.
     */
    fun inicioSesion(estado: String): String {
        fun c(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
        val ns = "http://specs.openid.net/auth/2.0"
        return "https://steamcommunity.com/openid/login?" + listOf(
            "openid.ns" to ns, "openid.mode" to "checkid_setup", "openid.return_to" to "$SITIO$RUTA_VUELTA?estado=$estado",
            "openid.realm" to SITIO, "openid.identity" to "$ns/identifier_select", "openid.claimed_id" to "$ns/identifier_select",
        ).joinToString("&") { (k, v) -> "$k=${c(v)}" }
    }

    /** El número de la cuenta que volvió de Steam, o null si no se completó o no es la vuelta que pidió este teléfono. */
    fun cuentaDeVuelta(estado: String?, modo: String?, cuenta: String?, esperado: String?): String? {
        if (esperado.isNullOrBlank() || estado != esperado || modo != "id_res") return null
        return CUENTA_OPENID.matchEntire(cuenta.orEmpty())?.groupValues?.get(1)
    }

    /** Si [texto] tiene la forma de un perfil (el enlace, el número de 17 cifras o el nombre), como lo revisa el servidor. */
    fun perfilValido(texto: String): Boolean {
        val t = texto.trim()
        if (t.length !in 2..200) return false
        ENLACE.matchEntire(t)?.let { m ->
            val v = m.groupValues[2]
            return if (m.groupValues[1].equals("profiles", true)) NUMERO.matches(v) else NOMBRE.matches(v)
        }
        return NUMERO.matches(t) || NOMBRE.matches(t)
    }

    /** Más que lo que el servidor puede tardar (espera de turno más la importación): así responde él, no se corta aquí. */
    const val TIEMPO_SEGUNDOS = 120L
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).callTimeout(TIEMPO_SEGUNDOS, TimeUnit.SECONDS).build()

    /**
     * La biblioteca de [perfil]. [Fallo] con el error del servidor, o [SIN_RED] si no hubo respuesta. Si la corrutina se
     * cancela, se cancela la llamada (y el servidor deja de trabajar en ella).
     */
    suspend fun biblioteca(perfil: String): Biblioteca = withContext(Dispatchers.IO) {
        val url = (ClienteSocial.SERVIDOR + "/v1/steam/biblioteca").toHttpUrl().newBuilder().addQueryParameter("perfil", perfil.trim()).build()
        val llamada = http.newCall(Request.Builder().url(url).header("Accept", "application/json").build())
        val respuesta = try {
            kotlinx.coroutines.runInterruptible { llamada.execute() }
        } catch (x: kotlinx.coroutines.CancellationException) { llamada.cancel(); throw x
        } catch (_: IOException) { throw Fallo(SIN_RED) }
        respuesta.use { r ->
            val cuerpo = try { r.body.string() } catch (_: IOException) { throw Fallo(SIN_RED) }
            if (!r.isSuccessful) throw Fallo(errorDe(cuerpo) ?: NO_RESPONDE)
            convertir(cuerpo) ?: throw Fallo(NO_RESPONDE)
        }
    }

    /** El `error` de una respuesta de error del servidor. */
    internal fun errorDe(cuerpo: String): String? = runCatching {
        ((JsonCajon.parseToJsonElement(cuerpo) as? JsonObject)?.get("error") as? JsonPrimitive)?.contentOrNull
    }.getOrNull()?.takeIf { it.matches(Regex("[a-z_]{1,40}")) }

    /**
     * La respuesta `{"juegos":[…], "total": n, "recortada": true}`; null si no tiene esa forma. Se descartan los juegos
     * sin appid.
     */
    internal fun convertir(cuerpo: String): Biblioteca? {
        val raiz = runCatching { JsonCajon.parseToJsonElement(cuerpo) as? JsonObject }.getOrNull() ?: return null
        val lista = raiz["juegos"] as? JsonArray ?: return null
        val juegos = lista.mapNotNull { e ->
            val j = e as? JsonObject ?: return@mapNotNull null
            fun largo(k: String) = (j[k] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
            fun minutos(k: String) = ((j[k] as? JsonPrimitive)?.intOrNull ?: 0).coerceAtLeast(0)
            val appid = largo("appid")?.takeIf { it > 0 } ?: return@mapNotNull null
            Juego(appid, (j["nombre"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty(), minutos("minutos"), minutos("minutos2semanas"),
                largo("igdb")?.takeIf { it > 0 })
        }
        val recortada = (raiz["recortada"] as? JsonPrimitive)?.contentOrNull == "true"
        val total = (raiz["total"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull?.coerceAtLeast(juegos.size) ?: juegos.size
        return Biblioteca(juegos, recortada, total)
    }

    /** Lo que Steam dice del juego: jugado estas dos semanas, «Jugando»; nunca jugado, «Quiero jugar»; si no, sin estado. */
    fun estadoDe(minutos: Int, minutos2semanas: Int): String = when {
        minutos2semanas > 0 -> Estados.EN_CURSO
        minutos == 0 -> Estados.QUIERO
        else -> Estados.SIN_ESTADO
    }

    /** Las horas que muestra la ficha («120 h en Steam»), redondeadas hacia abajo; null si no hay o no llega a una hora. */
    fun horas(minutos: Int?): Int? = minutos?.takeIf { it >= 60 }?.div(60)

    /** Cuántos juegos entraron con ficha, cuántos ya estaban (se les pusieron las horas) y cuántos entraron sin ficha. */
    data class Resultado(val nuevos: Int, val actualizados: Int, val sinFicha: Int)

    /**
     * Suma [juegos] al cajón sin repetir. Un juego con ficha es el que tiene la misma `igdb:`, el `wd:` que ya apunta a
     * esa ficha ([wdConIgdb]: id de IGDB → id de la obra) o, si no, el juego sin fuente con el mismo nombre (escrito a
     * mano o que entró sin ficha en otra importación): ese pasa a la ficha (`igdb:`, con los datos por traer) y conserva
     * lo suyo. Uno sin ficha es el juego con el mismo nombre. Al que ya está solo se le ponen las horas, con su propia
     * hora y sin tocar [Obra.cambio] (así reimportar no pisa lo hecho en otro teléfono ni lo sube en «En curso»);
     * estado, nota, reseña y notas no se tocan. Un juego que la persona borró no vuelve. Los nuevos llevan la ficha de
     * [fichas] (id de IGDB → obra de [Igdb.obra]) o, si no llegó, solo el nombre (los datos se traen después), y entran
     * en silencio ([Obra.silenciosa]): no van a lo que se ve en la red hasta que la persona los toque. Con [renovar],
     * los datos se vuelven a traer después (el resumen en español). Dos juegos de Steam con la misma ficha (ediciones)
     * suman sus minutos.
     */
    fun sumar(e: Estado, juegos: List<Juego>, fichas: Map<Long, Obra>, wdConIgdb: Map<Long, String>, nuevoId: (String) -> String,
              ahora: Long, renovar: Boolean = false): Pair<Estado, Resultado> {
        val obras = e.obras.toMutableList()
        var nuevos = 0; var actualizados = 0; var sinFicha = 0
        fun nombre(t: String) = normal(t).replace(Regex("\\s+"), " ").trim()
        fun clave(j: Juego) = j.igdb?.let { "igdb:$it" } ?: ("nombre:" + nombre(j.nombre))
        val grupos = juegos.distinctBy { it.appid }.filter { it.igdb != null || nombre(it.nombre).isNotEmpty() }.groupBy(::clave)
        // Los juegos del cajón por nombre: así una biblioteca de miles no recorre el cajón entero por cada uno.
        val porNombre = HashMap<String, MutableList<Int>>()
        obras.forEachIndexed { i, o -> if (o.tipo == Tipos.JUEGO) porNombre.getOrPut(nombre(o.titulo)) { mutableListOf() } += i }
        val porFuente = HashMap<String, Int>()
        obras.forEachIndexed { i, o -> if (!o.borrada && o.fuente.isNotEmpty()) porFuente.putIfAbsent(o.fuente, i) }
        val porId = obras.withIndex().filter { !it.value.borrada }.associate { it.value.id to it.index }
        val fuentesBorradas = obras.filter { it.borrada && it.fuente.isNotEmpty() }.map { it.fuente }.toSet()
        for (grupo in grupos.values) {
            val j = grupo.first()
            val minutos = grupo.sumOf { it.minutos.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val dos = grupo.sumOf { it.minutos2semanas.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val fuente = j.igdb?.let { "igdb:$it" }
            val mismoNombre = grupo.flatMap { porNombre[nombre(it.nombre)].orEmpty() }.distinct()
            val i = if (fuente != null) {
                porFuente[fuente] ?: wdConIgdb[j.igdb]?.let { porId[it] }
                    ?: mismoNombre.firstOrNull { !obras[it].borrada && obras[it].fuente.isEmpty() }
            } else mismoNombre.firstOrNull { !obras[it].borrada }
            if (i != null) {
                val o = obras[i]
                var x = o
                // Pasa a la ficha: lo suyo se queda; los datos de afuera se traen después.
                if (fuente != null && o.fuente.isEmpty()) {
                    x = x.copy(fuente = fuente, traida = 0L, cambio = ahora)
                    porFuente[fuente] = i
                }
                if (x.minutosSteam != minutos) x = x.copy(minutosSteam = minutos, minutosSteamCuando = ahora)
                if (x != o) obras[i] = x
                actualizados++
                continue
            }
            // Lo que la persona borró no vuelve.
            if (fuente != null && fuente in fuentesBorradas || mismoNombre.any { obras[it].borrada }) continue
            val base = j.igdb?.let { fichas[it] }
                ?: Obra(id = "", tipo = Tipos.JUEGO, titulo = j.nombre, fuente = fuente.orEmpty())
            obras += base.copy(id = nuevoId("o"), estado = estadoDe(minutos, dos), minutosSteam = minutos, minutosSteamCuando = ahora,
                agregada = ahora, cambio = ahora, silenciosa = true, traida = if (renovar) 0L else base.traida)
            val n = obras.size - 1
            if (fuente != null) porFuente[fuente] = n
            porNombre.getOrPut(nombre(obras[n].titulo)) { mutableListOf() } += n
            if (j.igdb != null) nuevos++ else sinFicha++
        }
        return e.copy(obras = obras) to Resultado(nuevos, actualizados, sinFicha)
    }
}

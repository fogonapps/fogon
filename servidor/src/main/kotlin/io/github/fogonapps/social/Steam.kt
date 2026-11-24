package io.github.fogonapps.social

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * La biblioteca de Steam de una persona, para importarla a El Cajón. La clave de Fogón (`STEAM_API_KEY`) vive solo
 * aquí. Con el enlace del perfil (o su nombre o su número), se pide a Steam la lista de juegos y a IGDB la ficha de
 * cada uno ([Igdb.deSteam]); la respuesta va directo a la app. No se guarda nada: ni la lista, ni el perfil, ni el
 * número de Steam, y nada de eso (ni la clave) se escribe en los registros.
 *
 * Topes, para que nadie agote lo de todos: Steam deja 100 000 llamadas por día y aquí se corta antes ([MAX_POR_DIA]);
 * cada dirección importa hasta [MAX_POR_IP_DIA] veces por día y gasta hasta [MAX_IGDB_POR_IP_HORA] consultas de IGDB por
 * hora; se importa de a [A_LA_VEZ] en todo el servidor, con una pausa de [PAUSA_IGDB] entre consultas a IGDB (así las
 * búsquedas siguen teniendo su lugar en la fila), y una biblioteca de más de [MAX_JUEGOS] juegos se recorta a los más
 * jugados.
 */
class Steam(
    private val clave: String?,
    private val igdb: Igdb,
    private val ahora: () -> Long = System::currentTimeMillis,
    private val maxPorDia: Int = MAX_POR_DIA,
    private val maxBytes: Long = MAX_BYTES,
    private val tiempo: Long = TIEMPO,
    private val esperaTurno: Long = ESPERA_TURNO,
    private val enviar: (HttpRequest) -> Flujo = { pedido ->
        val r = http.send(pedido, HttpResponse.BodyHandlers.ofInputStream())
        Flujo(r.statusCode(), r.body())
    },
) {
    companion object {
        private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
        private const val API = "https://api.steampowered.com"
        /** Llamadas a Steam por día (UTC) para todo el servidor, por debajo de las 100 000 que deja Steam. */
        const val MAX_POR_DIA = 90_000
        /** Importaciones por día y por dirección (una casa o un colegio caben). */
        const val MAX_POR_IP_DIA = 20
        /** Consultas a IGDB por hora y por dirección para importar (una biblioteca de [MAX_JUEGOS] gasta 50). */
        const val MAX_IGDB_POR_IP_HORA = 150
        /** Una biblioteca más grande se recorta a los juegos más jugados (las de verdad casi nunca pasan de unos miles). */
        const val MAX_JUEGOS = 5_000
        /** Lo más que se lee de una respuesta de Steam (una biblioteca de 10 000 juegos ronda los 3 MB). */
        const val MAX_BYTES = 16L * 1024 * 1024
        /** Importaciones a la vez en todo el servidor. */
        const val A_LA_VEZ = 1
        /** Entre una consulta a IGDB de la importación y la siguiente: como mucho la mitad de la fila de IGDB. */
        const val PAUSA_IGDB = 500L
        /** Cuánto se espera el turno de importar y cuánto puede durar una importación: juntos, menos que los 120 s de la app. */
        const val ESPERA_TURNO = 20_000L
        const val TIEMPO = 80_000L

        private val NUMERO = Regex("7656119[0-9]{10}")
        private val NOMBRE = Regex("[A-Za-z0-9_-]{2,32}")
        /** El enlace del perfil, también con lo que venga después: `/games/?tab=all`, `/` o `?l=spanish`. */
        private val ENLACE = Regex("(?:https?://)?(?:www\\.)?steamcommunity\\.com/(id|profiles)/([^/?#\\s]{1,64})(?:/[^?#\\s]*)?(?:[?#]\\S*)?", RegexOption.IGNORE_CASE)

        private fun error(codigo: Int, nombre: String) = Cine.Respuesta(codigo, """{"error":"$nombre"}""")
        private val SIN_CLAVE = error(503, "sin_steam")
        private val INVALIDO = error(400, "perfil_invalido")
        private val NO_ENCONTRADO = error(404, "perfil_no_encontrado")
        private val PRIVADA = error(403, "biblioteca_privada")
        private val NO_RESPONDE = error(502, "steam_no_responde")
        private val SIN_IGDB = error(503, "sin_igdb")
        private val FALLA_IGDB = error(502, "igdb")
        /** Se acabó lo de hoy (de esta dirección o de todo el servidor): mañana se puede otra vez. */
        private val CUPO_DIARIO = error(429, "cupo_diario")
        /** Demasiadas consultas de IGDB de esta dirección en la última hora. */
        private val DESPACIO = error(429, "despacio")
        /** Hay otra importación en curso y no terminó a tiempo. */
        private val OCUPADO = error(503, "ocupado")

        /**
         * Lo que la persona pegó: el enlace a su perfil (`…/id/<nombre>` o `…/profiles/<número>`, con o sin lo que venga
         * después), el número de 17 cifras o el nombre solo. Null si no tiene ninguna de esas formas.
         */
        internal fun perfil(texto: String?): Perfil? {
            val t = texto?.trim()?.takeIf { it.length in 2..200 } ?: return null
            ENLACE.matchEntire(t)?.let { m ->
                val valor = m.groupValues[2]
                return if (m.groupValues[1].equals("profiles", true)) valor.takeIf { NUMERO.matches(it) }?.let { Perfil(numero = it) }
                else valor.takeIf { NOMBRE.matches(it) }?.let { Perfil(nombre = it) }
            }
            if (NUMERO.matches(t)) return Perfil(numero = t)
            if (NOMBRE.matches(t)) return Perfil(nombre = t)
            return null
        }
    }

    /** Una respuesta de Steam sin leer todavía: se lee en flujo y con tope ([maxBytes]). */
    class Flujo(val codigo: Int, val cuerpo: InputStream)

    /** Un perfil por su número (steamid64) o por su nombre (el de `/id/<nombre>`), uno de los dos. */
    internal data class Perfil(val numero: String? = null, val nombre: String? = null)

    val lista get() = !clave.isNullOrBlank()

    private val candado = Any()
    private var dia = -1L
    private var llamadas = 0
    private val turno = Semaphore(A_LA_VEZ)
    private val porIpDia = LimiteDiario(MAX_POR_IP_DIA)
    /** Consultas de IGDB gastadas por cada dirección en la hora: dirección → (hora, cuántas). */
    private val igdbPorIp = ConcurrentHashMap<String, Pair<Long, Int>>()

    /** Si queda cupo de Steam hoy; cuenta la llamada. */
    private fun cupo(): Boolean = synchronized(candado) {
        val hoy = ahora() / 86_400_000
        if (hoy != dia) { dia = hoy; llamadas = 0 }
        if (llamadas >= maxPorDia) false else { llamadas++; true }
    }

    /** Aparta [n] consultas de IGDB para [ip] en esta hora; false si no le alcanzan. */
    private fun apartarIgdb(ip: String, n: Int): Boolean {
        val hora = ahora() / 3_600_000
        var paso = false
        igdbPorIp.compute(ip) { _, v ->
            val usadas = if (v == null || v.first != hora) 0 else v.second
            paso = usadas + n <= MAX_IGDB_POR_IP_HORA
            hora to if (paso) usadas + n else usadas
        }
        if (igdbPorIp.size > 50_000) igdbPorIp.entries.removeIf { it.value.first != hora }
        return paso
    }

    /** Devuelve a [ip] [n] consultas de IGDB apartadas en esta hora que no se usaron. */
    private fun devolverIgdb(ip: String, n: Int) {
        if (n <= 0) return
        val hora = ahora() / 3_600_000
        igdbPorIp.computeIfPresent(ip) { _, v -> if (v.first != hora) v else hora to (v.second - n).coerceAtLeast(0) }
    }

    /**
     * La importación desde la ruta: espera su turno (hay [A_LA_VEZ] en todo el servidor) y dura como mucho [tiempo].
     * Si la llamada se cancela (la app cortó o se pasó el tiempo), el trabajo se interrumpe y no sigue gastando cupo.
     */
    suspend fun importar(texto: String?, ip: String): Cine.Respuesta {
        if (!lista) return SIN_CLAVE
        if (perfil(texto) == null) return INVALIDO
        withTimeoutOrNull(esperaTurno) { turno.acquire() } ?: return OCUPADO
        try {
            return withTimeoutOrNull(tiempo) {
                val trabajo = coroutineContext.job
                runInterruptible(Dispatchers.IO) { biblioteca(texto, ip) { trabajo.isActive } }
            } ?: NO_RESPONDE
        } finally { turno.release() }
    }

    /**
     * La biblioteca de [texto] como `{"juegos":[{appid, nombre, minutos, minutos2semanas, igdb}], "total": n}` (igdb: el
     * id del juego en IGDB o null; total: cuántos tiene la biblioteca; con `"recortada": true` si eran más de
     * [MAX_JUEGOS] y van los más jugados). Errores: `perfil_invalido`, `perfil_no_encontrado`, `biblioteca_privada`
     * (Steam no la muestra: los detalles de juego no son públicos), `cupo_diario`, `despacio`, `sin_steam`,
     * `steam_no_responde`, `sin_igdb` e `igdb`. [sigue] dice si la importación sigue en pie (se pregunta entre consultas).
     */
    fun biblioteca(texto: String?, ip: String = "", sigue: () -> Boolean = { true }): Cine.Respuesta {
        if (!lista) return SIN_CLAVE
        val p = perfil(texto) ?: return INVALIDO
        if (!porIpDia.pasa("steam:$ip", ahora())) return CUPO_DIARIO
        return try {
            val numero = p.numero ?: when (val r = resolver(p.nombre!!)) { is String -> r; else -> return r as Cine.Respuesta }
            juegos(numero, ip, sigue)
        } catch (_: SinCupo) {
            CUPO_DIARIO
        } catch (x: Falla) {
            x.respuesta
        } catch (x: InterruptedException) {
            Thread.currentThread().interrupt(); NO_RESPONDE
        } catch (_: Exception) {
            // Sin el mensaje: podría traer la dirección, con la clave y el perfil.
            NO_RESPONDE
        }
    }

    private class SinCupo : Exception()

    @Serializable private class Envoltura<T>(val response: T? = null)
    @Serializable private class Resuelto(val steamid: String? = null, val success: Int? = null)
    @Serializable private class Resumenes(val players: List<JsonObject>? = null)
    @Serializable private class Propios(@SerialName("game_count") val cuantos: Int? = null, val games: List<Propio> = emptyList())
    @Serializable private class Propio(
        val appid: Long = 0, val name: String = "",
        @SerialName("playtime_forever") val minutos: Long = 0, @SerialName("playtime_2weeks") val dos: Long = 0,
    )

    /** El número de un nombre (`ResolveVanityURL`), o la respuesta de error. */
    private fun resolver(nombre: String): Any {
        val res = pedir("ISteamUser/ResolveVanityURL/v1/", mapOf("vanityurl" to nombre), Resuelto.serializer(), "ResolveVanityURL")
            ?: return NO_RESPONDE
        val numero = res.steamid
        return if (res.success == 1 && numero != null && NUMERO.matches(numero)) numero else NO_ENCONTRADO
    }

    /**
     * Steam responde igual a un perfil con los detalles de juego privados que a un número que no es de nadie. El
     * resumen público (`GetPlayerSummaries`) los distingue: solo se mira si trae al jugador, y nada de él se usa.
     */
    private fun existe(numero: String): Boolean? {
        val res = runCatching { pedir("ISteamUser/GetPlayerSummaries/v2/", mapOf("steamids" to numero), Resumenes.serializer(), "GetPlayerSummaries") }
            .getOrNull() ?: return null
        return res.players.orEmpty().isNotEmpty()
    }

    private fun juegos(numero: String, ip: String, sigue: () -> Boolean): Cine.Respuesta {
        val res = pedir("IPlayerService/GetOwnedGames/v1/", mapOf("steamid" to numero, "include_appinfo" to "1",
            "include_played_free_games" to "1", "skip_unvetted_apps" to "0"), Propios.serializer(), "GetOwnedGames") ?: return NO_RESPONDE
        // Con los detalles de juego privados (o un número que no es de nadie), Steam responde 200 con `response` vacío.
        if (res.cuantos == null) return if (existe(numero) == false) NO_ENCONTRADO else PRIVADA
        val todos = res.games.mapNotNull { g ->
            val appid = g.appid.takeIf { it in 1..9_999_999_999 } ?: return@mapNotNull null
            val nombre = g.name.filter { !it.isISOControl() }.trim().take(200)
            fun minutos(m: Long) = m.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            Juego(appid, nombre, minutos(g.minutos), minutos(g.dos))
        }.distinctBy { it.appid }
        val recortada = todos.size > MAX_JUEGOS
        // Recortada antes de preguntar a IGDB: van los más jugados.
        val lista = if (recortada) todos.sortedWith(compareByDescending<Juego> { it.minutos }.thenBy { it.appid }).take(MAX_JUEGOS) else todos
        if (!igdb.lista) return SIN_IGDB
        val consultas = (lista.size + Igdb.LOTE_STEAM - 1) / Igdb.LOTE_STEAM
        // Se aparta la primera página de cada lote; las de más, cuando hacen falta. Al final se devuelve lo apartado
        // que no se llegó a consultar (si IGDB falló o la importación se cortó).
        if (consultas > 0 && !apartarIgdb(ip, consultas)) return DESPACIO
        var apartadas = consultas
        var hechas = 0
        var sinCupo = false
        val ids = try {
            if (lista.isEmpty()) emptyMap() else igdb.deSteam(lista.map { it.appid }, PAUSA_IGDB, sigue,
                otraPagina = { apartarIgdb(ip, 1).also { if (it) apartadas++ else sinCupo = true } },
                alConsultar = { hechas++ })
        } finally {
            devolverIgdb(ip, apartadas - hechas)
        } ?: return if (sinCupo) DESPACIO else FALLA_IGDB
        val juegos = JsonArray(lista.map { j ->
            JsonObject(mapOf("appid" to JsonPrimitive(j.appid), "nombre" to JsonPrimitive(j.nombre), "minutos" to JsonPrimitive(j.minutos),
                "minutos2semanas" to JsonPrimitive(j.minutos2semanas), "igdb" to (ids[j.appid]?.let { JsonPrimitive(it) } ?: JsonNull)))
        })
        val cuerpo = mapOf("juegos" to juegos, "total" to JsonPrimitive(todos.size)) +
            (if (recortada) mapOf("recortada" to JsonPrimitive(true)) else emptyMap())
        return Cine.Respuesta(200, JsonObject(cuerpo).toString())
    }

    private data class Juego(val appid: Long, val nombre: String, val minutos: Int, val minutos2semanas: Int)

    /** Un error de Steam: si rechaza la clave, es como no tenerla; lo demás, que no responde. Solo el código al registro. */
    private class Falla(val respuesta: Cine.Respuesta) : Exception()

    /** Lo que corta la lectura cuando la respuesta pasa de [tope] bytes. */
    private class ConTope(entrada: InputStream, private val tope: Long) : FilterInputStream(entrada) {
        private var leidos = 0L
        private fun contar(n: Int): Int { if (n > 0) { leidos += n; if (leidos > tope) throw IOException("demasiado grande") }; return n }
        override fun read(): Int = super.read().also { if (it >= 0) contar(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = contar(super.read(b, off, len))
    }

    /**
     * Un pedido a Steam con la clave, leído en flujo (sin armar el árbol entero del JSON) y con tope de bytes: el
     * `response` de la respuesta, o null si no tiene esa forma. [SinCupo] si ya no queda cupo hoy.
     */
    @OptIn(ExperimentalSerializationApi::class)
    private fun <T> pedir(metodo: String, parametros: Map<String, String>, forma: KSerializer<T>, nombre: String): T? {
        if (!cupo()) throw SinCupo()
        fun cod(s: String) = URLEncoder.encode(s, "UTF-8")
        val consulta = (parametros + ("format" to "json") + ("key" to clave!!)).entries.joinToString("&") { (k, v) -> "$k=${cod(v)}" }
        val pedido = HttpRequest.newBuilder(URI("$API/$metodo?$consulta")).timeout(Duration.ofSeconds(20))
            .header("Accept", "application/json").header("User-Agent", "FogonSocial/$VERSION (+https://fogonapps.duckdns.org)")
            .GET().build()
        val r = enviar(pedido)
        r.cuerpo.use { cuerpo ->
            if (r.codigo != 200) {
                System.err.println("[steam] $nombre respondió ${r.codigo}")
                throw Falla(if (r.codigo == 401 || r.codigo == 403) SIN_CLAVE else NO_RESPONDE)
            }
            return runCatching { JsonSocial.decodeFromStream(Envoltura.serializer(forma), ConTope(cuerpo, maxBytes)).response }.getOrNull()
        }
    }
}

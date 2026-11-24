package io.github.fogonapps.social

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * IGDB (videojuegos) a través del servidor: la clave de Fogón (`IGDB_CLIENT_ID` e `IGDB_CLIENT_SECRET`) vive solo aquí.
 * Con ella se pide a Twitch el token de la app, que se guarda en memoria y se renueva antes de que venza (o si IGDB lo
 * rechaza). La app no manda consultas de IGDB: pide una de tres cosas ya armadas aquí, con la entrada revisada
 * (buscar por texto, un juego por su id, varios por id o por nombre corto). Los pedidos a IGDB salen en fila, como
 * mucho 4 por segundo y 8 abiertos a la vez, que es lo que IGDB permite; las respuestas se guardan 6 horas
 * ([CacheTemporal]). Las
 * imágenes van directo a images.igdb.com, que no pide clave.
 */
class Igdb(
    private val clienteId: String?,
    private val secreto: String?,
    private val ahora: () -> Long = System::currentTimeMillis,
    private val esperar: (Long) -> Unit = { Thread.sleep(it) },
    private val enviar: (HttpRequest) -> Cine.Respuesta = { pedido ->
        val r = http.send(pedido, HttpResponse.BodyHandlers.ofString())
        Cine.Respuesta(r.statusCode(), r.body())
    },
) {
    companion object {
        private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
        const val SEIS_HORAS = 6 * 3600_000L
        const val MAX_CACHE = 16_000_000L
        const val MAX_ENTRADAS = 20_000
        /** Si Twitch no dice cuánto dura el token (o dice menos de un minuto), se toma una hora. */
        const val DURA_MINIMA = 3600L
        /** Entre un pedido y el siguiente: 4 por segundo. */
        const val PASO = 250L
        /** Si la fila ya pasa de esto, se responde «despacio» en vez de dejar a la app esperando. */
        const val MAX_ESPERA = 8_000L
        /** El token se renueva una hora antes de vencer (o a la mitad de su vida, si dura menos de dos). */
        const val MARGEN = 3600_000L
        const val MAX_IDS = 50
        /** Cuántos juegos de Steam van en cada consulta a `external_games` (un appid puede traer varias filas). */
        const val LOTE_STEAM = 100
        /** Filas por página de `external_games` (lo más que deja IGDB) y cuántas páginas, como mucho, por lote. */
        const val FILAS_STEAM = 500
        const val PAGINAS_STEAM = 4
        private val SLUG = Regex("[a-z0-9][a-z0-9$~-]{0,150}")

        /** Lo que llega en una búsqueda o en una lista: lo justo para mostrar el juego y elegirlo. */
        const val CAMPOS_LISTA = "name,slug,first_release_date,cover.image_id,platforms.name,platforms.abbreviation," +
            "involved_companies.company.name,involved_companies.developer,involved_companies.publisher,genres.name,themes.name," +
            "rating,rating_count,aggregated_rating,aggregated_rating_count,total_rating_count,hypes"
        /** La ficha: además, el resumen, las imágenes, los sitios y la página en IGDB. */
        const val CAMPOS_FICHA = "$CAMPOS_LISTA,summary,url,screenshots.image_id,artworks.image_id,websites.url,websites.type"
        /**
         * Sin ediciones (version_parent), contenido descargable, paquetes, mods, episodios, temporadas, packs ni
         * actualizaciones: en una búsqueda tapan al juego («Hollow Knight» traía seis mods con el mismo nombre).
         */
        private const val SOLO_JUEGOS = "version_parent = null & game_type != (1,3,5,6,7,13,14)"

        private val SIN_CLAVE = Cine.Respuesta(503, """{"error":"sin_igdb"}""")
        private val MALA = Cine.Respuesta(400, """{"error":"parametros"}""")
        private val DESPACIO = Cine.Respuesta(429, """{"error":"despacio"}""")
        private val FALLO = Cine.Respuesta(502, """{"error":"igdb"}""")

        /**
         * El texto de una búsqueda, listo para ir entre comillas: sin comillas, barras ni caracteres de control, con los
         * espacios juntos y de 1 a 80 caracteres. Null si no queda nada.
         */
        internal fun textoLimpio(texto: String?): String? {
            val t = texto.orEmpty().filter { it != '"' && it != '\\' && !it.isISOControl() }.replace(Regex("\\s+"), " ").trim()
            return t.takeIf { it.isNotEmpty() && it.length <= 80 }
        }

        /** Ids de IGDB separados por comas (de 1 a [MAX_IDS], solo números); null si alguno no lo es. */
        internal fun ids(texto: String?): List<Long>? {
            if (texto.isNullOrBlank()) return emptyList()
            val l = texto.split(',').map { it.trim() }
            if (l.size > MAX_IDS) return null
            return l.map { s -> s.toLongOrNull()?.takeIf { it in 1..9_999_999_999 } ?: return null }.distinct().sorted()
        }

        /** Nombres cortos de IGDB («hollow-knight») separados por comas; null si alguno no tiene esa forma. */
        internal fun slugs(texto: String?): List<String>? {
            if (texto.isNullOrBlank()) return emptyList()
            val l = texto.split(',').map { it.trim() }
            if (l.size > MAX_IDS || l.any { !SLUG.matches(it) }) return null
            return l.distinct().sorted()
        }
    }

    val lista get() = !clienteId.isNullOrBlank() && !secreto.isNullOrBlank()

    private val guardadas = CacheTemporal(SEIS_HORAS, MAX_ENTRADAS, MAX_CACHE, ahora)
    private val fila = Any()
    private var proximo = 0L
    private val abiertos = Semaphore(8)
    private val candado = Any()
    /** El token de la app y cuándo hay que renovarlo. */
    @Volatile private var token: Pair<String, Long>? = null

    /**
     * Juegos por su nombre, los que se juegan solos: los 20 que IGDB da por parecido del nombre y, sin repetir, los 20
     * más votados cuyo nombre lo contiene (la búsqueda de IGDB no ordena por popularidad: con «Zelda», los más jugados
     * no llegaban). La app los ordena. Si falla lo segundo, va lo primero solo.
     */
    fun buscar(texto: String?): Cine.Respuesta {
        if (!lista) return SIN_CLAVE
        val t = textoLimpio(texto) ?: return MALA
        val r = consultar("games", "search \"$t\"; fields $CAMPOS_LISTA; where $SOLO_JUEGOS; limit 20;")
        if (r.codigo != 200) return r
        val populares = consultar("games", "fields $CAMPOS_LISTA; where name ~ *\"$t\"* & $SOLO_JUEGOS & total_rating_count > 0; " +
            "sort total_rating_count desc; limit 20;")
        return if (populares.codigo == 200) juntar(r, populares) else r
    }

    /** Dos listas de juegos en una, sin repetir el mismo id (queda la primera vez); si alguna no se entiende, la primera. */
    internal fun juntar(a: Cine.Respuesta, b: Cine.Respuesta): Cine.Respuesta {
        fun lista(r: Cine.Respuesta) = runCatching { JsonSocial.parseToJsonElement(r.cuerpo) as? kotlinx.serialization.json.JsonArray }.getOrNull()
        val primera = lista(a) ?: return a
        val segunda = lista(b) ?: return a
        val vistos = mutableSetOf<String>()
        val juntos = (primera + segunda).filter { j -> ((j as? JsonObject)?.get("id") as? JsonPrimitive)?.content?.let(vistos::add) ?: true }
        return Cine.Respuesta(200, kotlinx.serialization.json.JsonArray(juntos).toString())
    }

    /** Un juego por su id, con todo lo de la ficha. */
    fun juego(id: String?): Cine.Respuesta {
        if (!lista) return SIN_CLAVE
        val n = ids(id)?.singleOrNull() ?: return MALA
        return consultar("games", "fields $CAMPOS_FICHA; where id = $n; limit 1;")
    }

    /** Varios juegos (hasta [MAX_IDS] en total) por sus ids o sus nombres cortos, con todo lo de la ficha. */
    fun juegos(ids: String?, slugs: String?): Cine.Respuesta {
        if (!lista) return SIN_CLAVE
        val numeros = ids(ids) ?: return MALA
        val nombres = slugs(slugs) ?: return MALA
        if (numeros.isEmpty() && nombres.isEmpty() || numeros.size + nombres.size > MAX_IDS) return MALA
        val donde = listOfNotNull(
            numeros.takeIf { it.isNotEmpty() }?.let { "id = (${it.joinToString(",")})" },
            nombres.takeIf { it.isNotEmpty() }?.let { l -> "slug = (${l.joinToString(",") { "\"$it\"" }})" },
        ).joinToString(" | ")
        return consultar("games", "fields $CAMPOS_FICHA; where $donde; limit $MAX_IDS;")
    }

    /**
     * Los ids de IGDB de unos juegos de Steam ([appids]): appid → id de IGDB. Se pregunta a `external_games` (fuente
     * 1 = Steam) en lotes de [LOTE_STEAM]; si un lote trae [FILAS_STEAM] filas (un appid puede tener varias), se pide
     * la página siguiente, hasta [PAGINAS_STEAM]. Si un appid tiene varias filas, queda el id más bajo. No se guarda en
     * la caché: la lista es la biblioteca de una persona. Entre una consulta y la siguiente se esperan [pausa] ms, para
     * que una importación no ocupe toda la fila; antes de cada una se pregunta si [sigue] (la importación pudo
     * cancelarse). Cada página de más se pide antes a [otraPagina] (el cupo de quien importa) y cada consulta que
     * sale se avisa a [alConsultar]. Null si IGDB no está, un lote falla, ya no sigue o no hay cupo para otra página.
     */
    fun deSteam(appids: List<Long>, pausa: Long = 0, sigue: () -> Boolean = { true }, otraPagina: () -> Boolean = { true },
                alConsultar: () -> Unit = {}): Map<Long, Long>? {
        if (!lista) return null
        val r = HashMap<Long, Long>()
        var primera = true
        for (lote in appids.filter { it > 0 }.distinct().chunked(LOTE_STEAM)) {
            for (pagina in 0 until PAGINAS_STEAM) {
                if (!primera && pausa > 0) esperar(pausa)
                primera = false
                if (!sigue()) return null
                if (pagina > 0 && !otraPagina()) return null
                alConsultar()
                val desde = if (pagina == 0) "" else " offset ${pagina * FILAS_STEAM};"
                val res = consultar("external_games", "fields game,uid; where external_game_source = 1 & uid = (" +
                    lote.joinToString(",") { "\"$it\"" } + "); limit $FILAS_STEAM;$desde", guardar = false)
                if (res.codigo != 200) return null
                val filas = runCatching { JsonSocial.parseToJsonElement(res.cuerpo) as? kotlinx.serialization.json.JsonArray }.getOrNull() ?: return null
                filas.forEach { f ->
                    val o = f as? JsonObject ?: return@forEach
                    val uid = (o["uid"] as? JsonPrimitive)?.content?.toLongOrNull() ?: return@forEach
                    val juego = when (val g = o["game"]) {
                        is JsonPrimitive -> g.longOrNull
                        is JsonObject -> (g["id"] as? JsonPrimitive)?.longOrNull
                        else -> null
                    }?.takeIf { it > 0 } ?: return@forEach
                    r[uid] = r[uid]?.let { minOf(it, juego) } ?: juego
                }
                if (filas.size < FILAS_STEAM) break
            }
        }
        return r
    }

    private fun consultar(ruta: String, cuerpo: String, guardar: Boolean = true): Cine.Respuesta {
        val llave = "$ruta|$cuerpo"
        if (guardar) guardadas.leer(llave)?.let { return it }
        val acceso = token(forzar = false) ?: return FALLO
        var r = pedir(ruta, cuerpo, acceso) ?: return DESPACIO
        // Un token que IGDB ya no acepta (revocado o vencido antes de tiempo): uno nuevo y otra vez, una sola vez.
        if (r.codigo == 401) r = pedir(ruta, cuerpo, token(forzar = true) ?: return FALLO) ?: return DESPACIO
        if (guardar && r.codigo == 200 && r.cuerpo.length <= 600_000) guardadas.guardar(llave, r)
        return when (r.codigo) { 200, 429 -> r; 400 -> MALA; else -> FALLO }
    }

    /** Borra de la caché lo que ya venció (el servidor lo llama cada minuto). */
    fun barrer() = guardadas.barrer()

    internal val enCache get() = guardadas.cantidad
    internal val caracteresEnCache get() = guardadas.caracteres

    /** Un pedido a IGDB en su turno de la fila; null si la fila está muy larga o no se pudo abrir otro pedido. */
    private fun pedir(ruta: String, cuerpo: String, acceso: String): Cine.Respuesta? {
        val espera = synchronized(fila) {
            val ya = ahora()
            val turno = maxOf(ya, proximo)
            if (turno - ya > MAX_ESPERA) return null
            proximo = turno + PASO
            turno - ya
        }
        if (espera > 0) esperar(espera)
        if (!abiertos.tryAcquire(20, TimeUnit.SECONDS)) return null
        try {
            val pedido = HttpRequest.newBuilder(URI("https://api.igdb.com/v4/$ruta")).timeout(Duration.ofSeconds(20))
                .header("Client-ID", clienteId!!).header("Authorization", "Bearer $acceso").header("Accept", "application/json")
                .header("User-Agent", "FogonSocial/$VERSION (+https://fogonapps.duckdns.org)")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build()
            return try { enviar(pedido) } catch (_: Exception) { FALLO }
        } finally { abiertos.release() }
    }

    /** El token de la app: el guardado si todavía sirve; si no (o si se pide [forzar]), uno nuevo de Twitch. */
    private fun token(forzar: Boolean): String? { synchronized(candado) {
        val guardado = token
        if (!forzar && guardado != null && ahora() < guardado.second) return guardado.first
        fun cod(s: String) = URLEncoder.encode(s, "UTF-8")
        // La clave va en el cuerpo del formulario, no en la dirección (que queda en más registros).
        val formulario = "client_id=${cod(clienteId!!)}&client_secret=${cod(secreto!!)}&grant_type=client_credentials"
        val pedido = HttpRequest.newBuilder(URI("https://id.twitch.tv/oauth2/token")).timeout(Duration.ofSeconds(20))
            .header("Accept", "application/json").header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(formulario)).build()
        val r = try { enviar(pedido) } catch (_: Exception) { return null }
        if (r.codigo != 200) return null
        val j = runCatching { JsonSocial.parseToJsonElement(r.cuerpo) as? JsonObject }.getOrNull() ?: return null
        val acceso = (j["access_token"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return null
        val dura = ((j["expires_in"] as? JsonPrimitive)?.longOrNull ?: 0L).let { if (it < 60) DURA_MINIMA else it } * 1000
        token = acceso to ahora() + dura - minOf(MARGEN, dura / 2)
        return acceso
    } }
}

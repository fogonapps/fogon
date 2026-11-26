package io.github.fogonapps.social

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.util.Base64

/**
 * Un producto de Google Play. [unico]: se compra una sola vez por cuenta de Google (se reconoce, no se consume).
 * Sin valores por defecto: JsonSocial los omitiría y la app no sabría qué da cada uno.
 */
@Serializable data class ProductoPlay(val id: String, val chispas: Int, val regalo: Int, val unico: Boolean)
/** Lo que la app ofrece en la versión de Play. [cuenta]: el identificador opaco que viaja a Google con la compra. */
@Serializable data class OfertaPlay(val productos: List<ProductoPlay>, val cuenta: String)
@Serializable data class PedidoPlay(val paquete: String, val producto: String, val token: String)
/** [estado]: "acreditada" (ya se puede consumir) o "pendiente" (Google aún no cobra; no se consume). */
@Serializable data class RespuestaPlay(val estado: String, val chispas: Int)

/**
 * Lo que se vende en Play: el único lugar donde se cambian los paquetes (la app los pide al servidor). Los precios los
 * pone Fogón en Play Console; aquí solo cuántas chispas da cada producto. Con Play no hay cobro fijo por pago, así que
 * también hay paquetes chicos.
 */
val CATALOGO_PLAY = listOf(
    // Regalo escalonado (5 %, 10 % y 15 %), el mismo de los paquetes de Wompi.
    ProductoPlay("chispas_500", 500, 0, false),
    ProductoPlay("chispas_1000", 1000, 50, false),
    ProductoPlay("chispas_2500", 2500, 250, false),
    ProductoPlay("chispas_5000", 5000, 750, false),
    // Apoya a Fogón: pago único voluntario al precio del paquete de 5.000 y con su mismo regalo, para que apoyar no salga peor que comprar (la insignia de apoyo está pendiente de diseño).
    ProductoPlay("apoyo_fogon", 5000, 750, true),
)
/** Productos que ya no se ofrecen pero que se siguen acreditando (compras viejas sin confirmar). */
val RETIRADOS_PLAY = emptyList<ProductoPlay>()
/** Apps que venden con Play (applicationId). */
val APPS_PLAY = setOf("io.github.fogonapps.cajon")

/** Lo que Google dice de una compra (purchases.products.get de la Android Publisher API v3). */
data class CompraGoogle(
    /** 0 comprada, 1 cancelada, 2 pendiente. */
    val estado: Int,
    val orden: String,
    val cuentaOfuscada: String,
    /** purchaseType 0: compra con una licencia de prueba (no cobra). */
    val prueba: Boolean,
    val cantidad: Int = 1,
)

/** Consulta una compra a Google. Devuelve null si Google no la conoce; lanza una excepción si no responde. */
fun interface VerificadorPlay { fun consultar(paquete: String, producto: String, token: String): CompraGoogle? }

/**
 * Una compra que Google anuló (reembolso, contracargo o cancelación): su token, su orden y, en un reembolso parcial
 * por cantidad, cuántas unidades se anularon ([cantidad], null si se anuló entera) y cuándo ([cuando], para no
 * descontar dos veces el mismo reembolso parcial).
 */
data class CompraAnulada(val token: String, val orden: String, val cantidad: Int? = null, val cuando: Long = 0)

/**
 * Las compras anuladas de una app desde [desde] (purchases.voidedpurchases.list de la Android Publisher API v3; Google
 * guarda las de los últimos 30 días). Lanza una excepción si Google no responde.
 */
fun interface AnuladasPlay { fun anuladas(paquete: String, desde: Long): List<CompraAnulada> }

/**
 * Verifica compras de Google Play con la cuenta de servicio de Fogón (su JSON vive en el servidor, nunca en el repo;
 * la ruta va en PLAY_CUENTA). Firma el JWT con la llave de la cuenta y pide un token de acceso, como el envío de FCM.
 */
class GooglePlayApi(json: String) : VerificadorPlay, AnuladasPlay {
    private val correo: String
    private val llave: java.security.PrivateKey
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()
    init {
        val o = Json.parseToJsonElement(json).jsonObject
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
        val cuerpo = b64("""{"iss":"$correo","scope":"https://www.googleapis.com/auth/androidpublisher","aud":"https://oauth2.googleapis.com/token","iat":$ahora,"exp":${ahora + 3600}}""".toByteArray())
        val firma = Signature.getInstance("SHA256withRSA").run { initSign(llave); update("$cabeza.$cuerpo".toByteArray()); sign() }
        val peticion = HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token")).timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", Charsets.UTF_8) +
                "&assertion=$cabeza.$cuerpo.${b64(firma)}")).build()
        val r = http.send(peticion, HttpResponse.BodyHandlers.ofString())
        check(r.statusCode() == 200) { "Play: no se pudo obtener el acceso (${r.statusCode()})" }
        val token = Json.parseToJsonElement(r.body()).jsonObject.getValue("access_token").jsonPrimitive.content
        acceso = token to (System.currentTimeMillis() + 50 * 60_000)
        return token
    }

    override fun consultar(paquete: String, producto: String, token: String): CompraGoogle? {
        fun cod(x: String) = URLEncoder.encode(x, Charsets.UTF_8)
        val url = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${cod(paquete)}/purchases/products/${cod(producto)}/tokens/${cod(token)}"
        val r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
            .header("Authorization", "Bearer ${accesoVigente()}").GET().build(), HttpResponse.BodyHandlers.ofString())
        if (r.statusCode() == 401) acceso = null
        // 400 (token mal formado), 404 y 410 (no existe o ya venció): Google no la conoce.
        if (r.statusCode() in setOf(400, 404, 410)) return null
        check(r.statusCode() == 200) { "Play: respuesta ${r.statusCode()}" }
        return leerCompraGoogle(Json.parseToJsonElement(r.body()).jsonObject)
    }

    override fun anuladas(paquete: String, desde: Long): List<CompraAnulada> {
        fun cod(x: String) = URLEncoder.encode(x, Charsets.UTF_8)
        val todas = mutableListOf<CompraAnulada>()
        var pagina: String? = null
        // Como mucho 20 páginas de 1000: de sobra para Fogón, y sin bucles si Google repitiera la página.
        repeat(20) {
            val url = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${cod(paquete)}/purchases/voidedpurchases" +
                "?startTime=$desde&maxResults=1000&type=0&includeQuantityBasedPartialRefund=true" + (pagina?.let { "&token=${cod(it)}" } ?: "")
            val r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer ${accesoVigente()}").GET().build(), HttpResponse.BodyHandlers.ofString())
            if (r.statusCode() == 401) acceso = null
            check(r.statusCode() == 200) { "Play: respuesta ${r.statusCode()} al pedir las compras anuladas" }
            val o = Json.parseToJsonElement(r.body()).jsonObject
            todas += leerAnuladas(o)
            pagina = ((o["tokenPagination"] as? JsonObject)?.get("nextPageToken") as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
            if (pagina.isNullOrEmpty()) return todas
        }
        return todas
    }
}

internal fun leerAnuladas(o: JsonObject): List<CompraAnulada> =
    (o["voidedPurchases"] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { e ->
        val v = e as? JsonObject ?: return@mapNotNull null
        val token = (v["purchaseToken"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        fun numero(clave: String) = (v[clave] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.toLongOrNull()
        CompraAnulada(token, (v["orderId"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty(),
            numero("voidedQuantity")?.toInt()?.takeIf { it > 0 }, numero("voidedTimeMillis") ?: 0)
    }

internal fun leerCompraGoogle(o: JsonObject): CompraGoogle {
    fun texto(clave: String) = (o[clave] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty()
    fun numero(clave: String) = (o[clave] as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull
    return CompraGoogle(numero("purchaseState") ?: -1, texto("orderId"), texto("obfuscatedExternalAccountId"),
        numero("purchaseType") == 0, (numero("quantity") ?: 1).coerceAtLeast(1))
}

/**
 * Compras con Google Play Billing (solo la versión de Play de las apps). La app manda el token de la compra; el
 * servidor la confirma con Google, acredita las chispas y guarda el token en la misma transacción. Solo después la
 * app consume la compra: si algo falla en medio, la compra queda sin consumir y la app la vuelve a mandar al abrirse.
 * Sin [verificador] (sin cuenta de servicio configurada) los pagos de Play están apagados (503).
 */
class PagosPlay(
    private val base: Base,
    private val verificador: VerificadorPlay? = PLAY_CUENTA_ENTORNO(),
    private val idCuenta: (Long) -> String,
    private val ahora: () -> Long = System::currentTimeMillis,
    /** Las compras anuladas: con la misma cuenta de servicio; sin ella, la revisión diaria queda apagada. */
    private val anuladas: AnuladasPlay? = verificador as? AnuladasPlay,
) {
    companion object {
        private val TOKEN = Regex("[A-Za-z0-9._:\\-]{10,4096}")
        /** La cuenta de servicio de PLAY_CUENTA (la ruta del JSON). Si falta o no se puede leer, null. */
        val PLAY_CUENTA_ENTORNO: () -> VerificadorPlay? = {
            System.getenv("PLAY_CUENTA")?.takeIf { it.isNotBlank() }?.let { ruta ->
                runCatching { GooglePlayApi(java.io.File(ruta).readText()) }
                    .onFailure { System.err.println("[play] PLAY_CUENTA no se pudo leer: pagos de Play apagados.") }.getOrNull()
            }
        }
        fun producto(id: String): ProductoPlay? = (CATALOGO_PLAY + RETIRADOS_PLAY).firstOrNull { it.id == id }
    }

    fun comprobar(): VerificadorPlay = verificador ?: throw Problema(503, "pagos_apagados")

    fun oferta(cuenta: Long): OfertaPlay { comprobar(); return OfertaPlay(CATALOGO_PLAY, idCuenta(cuenta)) }

    /** Devuelve "acreditada" o "pendiente". Idempotente: repetir el mismo token no acredita otra vez. */
    fun verificar(cuenta: Long, pedido: PedidoPlay): String {
        val v = comprobar()
        val producto = producto(pedido.producto) ?: throw Problema(400, "producto")
        if (pedido.paquete !in APPS_PLAY) throw Problema(400, "paquete")
        if (!TOKEN.matches(pedido.token)) throw Problema(400, "token")
        // Ya acreditada: la app puede haberse cerrado antes de consumirla.
        yaAcreditada(cuenta, pedido.token)?.let { return it }
        val g = try { v.consultar(pedido.paquete, producto.id, pedido.token) }
            catch (e: Exception) { System.err.println("[play] Google no respondió: ${e.javaClass.simpleName}"); throw Problema(502, "play_no_responde") }
            ?: throw Problema(400, "compra_invalida")
        when (g.estado) {
            0 -> {}
            2 -> return "pendiente"
            1 -> throw Problema(400, "compra_cancelada")
            else -> throw Problema(400, "compra_invalida")
        }
        // La app siempre manda el identificador opaco de la cuenta: sin él no se sabe de quién es la compra.
        if (g.cuentaOfuscada.isBlank()) throw Problema(400, "compra_sin_cuenta")
        if (g.cuentaOfuscada != idCuenta(cuenta)) throw Problema(403, "compra_ajena")
        return base.transaccion {
            yaAcreditada(cuenta, pedido.token)?.let { return@transaccion it }
            val chispas = (producto.chispas + producto.regalo).toLong() * (if (producto.unico) 1 else g.cantidad)
            val orden = g.orden.ifBlank { "token:" + Base.huella(pedido.token).take(24) }
            cambiar("INSERT INTO compras_play(token,cuenta,producto,orden,chispas,prueba,cuando) VALUES(?,?,?,?,?,?,?)",
                pedido.token, cuenta, producto.id, orden, chispas, if (g.prueba) 1 else 0, ahora())
            registrarChispas(cuenta, chispas, (if (g.prueba) "play_prueba:" else "play:") + orden, ahora())
            "acreditada"
        }
    }

    /**
     * Una vez al día: las compras que Google anuló (reembolsos y contracargos de los últimos 30 días). Por cada una ya
     * acreditada y sin anular, se descuentan sus chispas hasta dejar el saldo en 0 (nunca negativo) y lo que falte queda
     * como deuda ([deudaChispas]): mientras haya deuda, la cuenta no compra nada con chispas, y las que reciba la
     * saldan primero. Todo queda en el historial de chispas. Devuelve cuántas compras se anularon.
     */
    fun revisarAnuladas(): Int {
        val a = anuladas ?: return 0
        val desde = ahora() - 30L * 24 * 3600_000 + 3600_000
        var n = 0
        for (paquete in APPS_PLAY) {
            val lista = try { a.anuladas(paquete, desde) }
                catch (e: Exception) { System.err.println("[play] No se pudieron pedir las compras anuladas: ${e.javaClass.simpleName}: ${e.message}"); continue }
            for (c in lista) if (anular(c)) n++
        }
        return n
    }

    /**
     * Anula [c]. Si Google dice que se anularon menos unidades de las compradas (reembolso parcial por cantidad), se
     * descuenta solo esa parte y la compra sigue viva para un reembolso posterior; cada reembolso parcial se descuenta
     * una vez (su motivo en el historial lleva el momento de la anulación). Nunca se descuenta más que la compra.
     */
    private fun anular(c: CompraAnulada): Boolean = base.transaccion {
        data class Fila(val cuenta: Long?, val chispas: Long, val orden: String, val producto: String, val yaDescontadas: Long)
        val f = leer("SELECT cuenta, chispas, orden, producto, anulado_chispas FROM compras_play WHERE token = ? AND anulada = 0", c.token) {
            Fila(it.getLong(1).let { id -> if (it.wasNull()) null else id }, it.getLong(2), it.getString(3), it.getString(4), it.getLong(5))
        }.firstOrNull() ?: return@transaccion false
        val porUnidad = producto(f.producto)?.takeIf { !it.unico }?.let { (it.chispas + it.regalo).toLong() }
        val compradas = if (porUnidad != null && porUnidad > 0) (f.chispas / porUnidad).coerceAtLeast(1) else 1L
        val parcial = c.cantidad != null && c.cantidad < compradas
        val motivo = if (parcial) "play_anulada:${f.orden}:parcial:${c.cuando}" else "play_anulada:${f.orden}"
        if (parcial && leer("SELECT 1 FROM chispas WHERE motivo = ?", motivo) { 1 }.isNotEmpty()) return@transaccion false
        val pendiente = (f.chispas - f.yaDescontadas).coerceAtLeast(0)
        val chispas = if (parcial) minOf(pendiente, f.chispas * c.cantidad / compradas) else pendiente
        if (parcial) cambiar("UPDATE compras_play SET anulado_chispas = anulado_chispas + ? WHERE token = ?", chispas, c.token)
        else cambiar("UPDATE compras_play SET anulada = 1, anulado_chispas = chispas WHERE token = ?", c.token)
        val cuenta = f.cuenta ?: return@transaccion true
        val saldo = leer("SELECT COALESCE(SUM(cantidad), 0) FROM chispas WHERE cuenta = ?", cuenta) { it.getLong(1) }.single()
        val descontadas = minOf(chispas, saldo.coerceAtLeast(0))
        // El ajuste queda en el historial aunque sea 0 (todo se había gastado).
        registrarChispas(cuenta, -descontadas, motivo, ahora())
        val deuda = chispas - descontadas
        if (deuda > 0) cambiar("""INSERT INTO deudas_chispas(cuenta, cantidad, cuando) VALUES(?,?,?)
            ON CONFLICT(cuenta) DO UPDATE SET cantidad = cantidad + excluded.cantidad, cuando = excluded.cuando""", cuenta, deuda, ahora())
        System.err.println("[play] Compra anulada: $descontadas chispas descontadas, $deuda en deuda.")
        true
    }

    private fun yaAcreditada(cuenta: Long, token: String): String? = base.usar {
        leer("SELECT cuenta FROM compras_play WHERE token=?", token) { it.getLong(1).let { c -> if (it.wasNull()) null else c } }
    }.let { filas ->
        if (filas.isEmpty()) null
        else if (filas.single() == cuenta) "acreditada"
        else throw Problema(409, "compra_ajena")
    }
}

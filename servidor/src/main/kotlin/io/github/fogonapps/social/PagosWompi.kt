package io.github.fogonapps.social

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.net.URLEncoder
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.security.MessageDigest

@Serializable data class PedidoWompi(val paquete: Int)
@Serializable data class OrdenWompi(val referencia: String, val url: String)
/** Paquetes de chispas a la venta. Sin valor por defecto: JsonSocial omite los valores por defecto y la app recibiría `{}`. */
@Serializable data class PaquetesWompi(val paquetes: List<Int>)
val PAQUETES_WOMPI = listOf(1000, 2000, 5000)

class ConfiguracionWompi(val publica: String, val integridad: String, val eventos: String, val privada: String = "") {
    val ambiente = if (publica.startsWith("pub_prod_")) "prod" else "test"
    val api = if (ambiente == "prod") "https://production.wompi.co/v1" else "https://sandbox.wompi.co/v1"
    companion object {
        fun entorno(leer: (String) -> String? = System::getenv): ConfiguracionWompi? {
            val ambiente = leer("WOMPI_AMBIENTE")
            val publica = leer("WOMPI_LLAVE_PUBLICA").orEmpty()
            val integridad = leer("WOMPI_SECRETO_INTEGRIDAD").orEmpty()
            val eventos = leer("WOMPI_SECRETO_EVENTOS").orEmpty()
            val privada = leer("WOMPI_LLAVE_PRIVADA").orEmpty()
            return if (((ambiente in setOf("test", "pruebas") && publica.startsWith("pub_test_")) ||
                (ambiente == "prod" && publica.startsWith("pub_prod_"))) &&
                integridad.isNotBlank() && eventos.isNotBlank() && privada.isNotBlank())
                ConfiguracionWompi(publica, integridad, eventos, privada) else null
        }
    }
}

internal fun shaWompi(texto: String): String = MessageDigest.getInstance("SHA-256")
    .digest(texto.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
internal fun firmaWompi(referencia: String, monto: Long, secreto: String) = shaWompi("${referencia}${monto}COP$secreto")

data class TransaccionWompi(val referencia: String, val id: String, val estado: String, val montoCentavos: Long, val moneda: String)

/**
 * [delEquipo]: mientras el cobro esté en Sandbox, solo el equipo de Fogón puede comprar (con datos de prueba saldrían
 * chispas gratis); para los demás los pagos siguen «apagados» y la app no muestra la sección.
 */
class PagosWompi(private val base: Base, private val configuracion: ConfiguracionWompi? = ConfiguracionWompi.entorno(),
                 private val delEquipo: (Long) -> Boolean = { true },
                 private val consultar: ((String) -> List<TransaccionWompi>)? = null) {
    private val ultimas = ConcurrentHashMap<Long, Long>()
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    /** Una sola ventana de cinco segundos para todas las consultas de la cuenta. */
    fun conciliar(cuenta: Long) {
        if (configuracion == null) return
        val ahora = System.currentTimeMillis()
        var permitida = false
        ultimas.compute(cuenta) { _, anterior ->
            if (anterior == null || ahora - anterior >= 20_000) { permitida = true; ahora } else anterior
        }
        if (!permitida) return
        val limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        val referencias = base.usar { leer("SELECT referencia FROM ordenes_pago WHERE cuenta=? AND acreditada=0 AND estado='pendiente' AND cuando>=?",
            cuenta, ahora - 48 * 60 * 60 * 1000L) { it.getString(1) } }
        for (referencia in referencias) {
            val restante = limite - System.nanoTime()
            if (restante <= 0) break
            try {
                val transacciones = consultar?.invoke(referencia) ?: consultarApi(referencia, restante)
                for (t in transacciones) {
                    if (t.referencia != referencia || t.moneda != "COP") continue
                    if (aplicar(t.referencia, t.id, t.estado, t.montoCentavos))
                        System.err.println("[wompi] conciliada $referencia ${t.estado}")
                }
            } catch (_: Exception) {
                // La orden sigue pendiente para el próximo intento.
            }
        }
    }

    private fun consultarApi(referencia: String, restante: Long): List<TransaccionWompi> {
        val c = comprobar()
        val peticion = HttpRequest.newBuilder(URI.create("${c.api}/transactions?reference=${URLEncoder.encode(referencia, Charsets.UTF_8)}"))
            .timeout(Duration.ofNanos(restante)).header("Authorization", "Bearer ${c.privada}").GET().build()
        val respuesta = http.sendAsync(peticion, HttpResponse.BodyHandlers.ofString())
        val cuerpo = try { respuesta.get(restante, TimeUnit.NANOSECONDS) }
            finally { if (!respuesta.isDone) respuesta.cancel(true) }
        check(cuerpo.statusCode() == 200)
        return Json.parseToJsonElement(cuerpo.body()).jsonObject.getValue("data").jsonArray.map { elemento ->
            val t = elemento.jsonObject
            TransaccionWompi(t.getValue("reference").jsonPrimitive.content, t.getValue("id").jsonPrimitive.content,
                t.getValue("status").jsonPrimitive.content, t.getValue("amount_in_cents").jsonPrimitive.long, t.getValue("currency").jsonPrimitive.content)
        }
    }

    private fun rechazar(motivo: String): Boolean {
        System.err.println("[wompi] evento rechazado: $motivo")
        return false
    }
    fun comprobar(): ConfiguracionWompi = configuracion ?: throw Problema(503, "pagos_apagados")

    /** Lo mismo que [comprobar], pero para una cuenta: en Sandbox solo el equipo. */
    /** En pruebas (Sandbox) solo el equipo puede comprar; en producción, todas las cuentas. */
    fun comprobar(cuenta: Long): ConfiguracionWompi = comprobar().also { if (it.ambiente == "test" && !delEquipo(cuenta)) throw Problema(503, "pagos_apagados") }

    fun orden(cuenta: Long, paquete: Int): OrdenWompi {
        val c = comprobar(cuenta)
        if (paquete !in PAQUETES_WOMPI) throw Problema(400, "paquete")
        val monto = paquete.toLong() * TemasPerfil.PESOS_POR_CHISPA * 100
        val referencia = java.util.UUID.randomUUID().toString()
        base.usar { cambiar("INSERT INTO ordenes_pago(referencia,cuenta,paquete,monto,cuando) VALUES(?,?,?,?,?)",
            referencia, cuenta, paquete, monto, System.currentTimeMillis()) }
        val parametros = listOf("public-key" to c.publica, "currency" to "COP", "amount-in-cents" to "$monto",
            "reference" to referencia, "signature:integrity" to firmaWompi(referencia, monto, c.integridad),
            "redirect-url" to "https://fogonapps.github.io/#gracias")
        return OrdenWompi(referencia, "https://checkout.wompi.co/p/?" + parametros.joinToString("&") {
            (clave, valor) -> "$clave=${URLEncoder.encode(valor, Charsets.UTF_8)}"
        })
    }

    fun evento(e: JsonObject) {
        val c = configuracion ?: run { rechazar("pagos apagados"); throw Problema(503, "pagos_apagados") }
        fun invalido(): Nothing { rechazar("firma inválida"); throw Problema(401, "firma_invalida") }
        val firma = e["signature"] as? JsonObject ?: invalido()
        val propiedades = firma["properties"] as? JsonArray ?: invalido()
        if (propiedades.isEmpty()) invalido()
        val datos = e["data"] as? JsonObject ?: invalido()
        val valores = propiedades.joinToString("") { propiedad ->
            val ruta = (propiedad as? JsonPrimitive)?.contentOrNull ?: invalido()
            val valor = ruta.split('.').fold(datos as JsonElement) { actual, parte ->
                (actual as? JsonObject)?.get(parte) ?: invalido()
            }
            (valor as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content ?: invalido()
        }
        val timestamp = (e["timestamp"] as? JsonPrimitive)?.longOrNull ?: invalido()
        val checksum = (firma["checksum"] as? JsonPrimitive)?.contentOrNull ?: invalido()
        val esperado = shaWompi(valores + timestamp + c.eventos)
        if (!MessageDigest.isEqual(esperado.toByteArray(), checksum.lowercase(java.util.Locale.ROOT).toByteArray())) invalido()
        if ((e["event"] as? JsonPrimitive)?.contentOrNull != "transaction.updated") { rechazar("tipo de evento distinto"); return }
        if ((e["environment"] as? JsonPrimitive)?.contentOrNull != c.ambiente) { rechazar("ambiente distinto"); return }
        val t = datos["transaction"] as? JsonObject ?: run { rechazar("transacción ausente"); return }
        val id = (t["id"] as? JsonPrimitive)?.contentOrNull ?: run { rechazar("id ausente"); return }
        val referencia = (t["reference"] as? JsonPrimitive)?.contentOrNull ?: run { rechazar("referencia ausente"); return }
        val estado = (t["status"] as? JsonPrimitive)?.contentOrNull ?: run { rechazar("estado ausente"); return }
        if ((t["currency"] as? JsonPrimitive)?.contentOrNull != "COP") { rechazar("moneda distinta"); return }
        val monto = (t["amount_in_cents"] as? JsonPrimitive)?.longOrNull ?: run { rechazar("monto inválido"); return }
        aplicar(referencia, id, estado, monto)
    }

    /** Devuelve true solo cuando se crea el abono; webhook y conciliación comparten la misma transacción. */
    private fun aplicar(referencia: String, id: String, estado: String, monto: Long): Boolean {
        if (id.isBlank()) return rechazar("id vacío")
        if (estado !in setOf("APPROVED", "DECLINED", "VOIDED", "ERROR")) return rechazar("estado no final")
        return base.transaccion {
            val orden = leer("SELECT cuenta,paquete,monto,acreditada,transaccion FROM ordenes_pago WHERE referencia=?", referencia) {
                Triple(it.getLong(1), it.getInt(2), it.getLong(3)) to ((it.getInt(4) == 1) to it.getString(5))
            }.firstOrNull() ?: return@transaccion rechazar("orden desconocida")
            val (compra, registro) = orden
            val (acreditada, transaccion) = registro
            val (cuenta, paquete, guardado) = compra
            if (paquete !in PAQUETES_WOMPI || guardado != paquete.toLong() * TemasPerfil.PESOS_POR_CHISPA * 100 || monto != guardado) return@transaccion rechazar("monto distinto")
            if (acreditada && transaccion != id) return@transaccion rechazar("transacción distinta")
            if (leer("SELECT referencia FROM ordenes_pago WHERE transaccion=? AND referencia<>?", id, referencia) { it.getString(1) }.isNotEmpty()) return@transaccion rechazar("transacción ya utilizada")
            cambiar("UPDATE ordenes_pago SET estado=?,transaccion=?,acreditada=? WHERE referencia=?",
                estado, id, if (acreditada || estado == "APPROVED") 1 else 0, referencia)
            if (estado == "APPROVED" && !acreditada) cambiar("INSERT INTO chispas(cuenta,cantidad,motivo,cuando) VALUES(?,?,?,?)",
                cuenta, paquete, "wompi:$id", System.currentTimeMillis())
            estado == "APPROVED" && !acreditada
        }
    }
}

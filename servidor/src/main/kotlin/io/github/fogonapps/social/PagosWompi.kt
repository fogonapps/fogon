package io.github.fogonapps.social

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.net.URLEncoder
import java.security.MessageDigest

@Serializable data class PedidoWompi(val paquete: Int)
@Serializable data class OrdenWompi(val referencia: String, val url: String)
@Serializable data class PaquetesWompi(val paquetes: List<Int> = listOf(1000, 2000, 5000))

/** Solo Sandbox: la llave privada se reserva para futuras consultas al API. */
class ConfiguracionWompi(val publica: String, val integridad: String, val eventos: String) {
    companion object {
        fun entorno(leer: (String) -> String? = System::getenv): ConfiguracionWompi? {
            val ambiente = leer("WOMPI_AMBIENTE")
            val publica = leer("WOMPI_LLAVE_PUBLICA").orEmpty()
            val integridad = leer("WOMPI_SECRETO_INTEGRIDAD").orEmpty()
            val eventos = leer("WOMPI_SECRETO_EVENTOS").orEmpty()
            val privada = leer("WOMPI_LLAVE_PRIVADA").orEmpty()
            return if (ambiente in setOf("test", "pruebas") && publica.startsWith("pub_test_") &&
                integridad.isNotBlank() && eventos.isNotBlank() && privada.isNotBlank())
                ConfiguracionWompi(publica, integridad, eventos) else null
        }
    }
}

internal fun shaWompi(texto: String): String = MessageDigest.getInstance("SHA-256")
    .digest(texto.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
internal fun firmaWompi(referencia: String, monto: Long, secreto: String) = shaWompi("${referencia}${monto}COP$secreto")

/**
 * [delEquipo]: mientras el cobro esté en Sandbox, solo el equipo de Fogón puede comprar (con datos de prueba saldrían
 * chispas gratis); para los demás los pagos siguen «apagados» y la app no muestra la sección.
 */
class PagosWompi(private val base: Base, private val configuracion: ConfiguracionWompi? = ConfiguracionWompi.entorno(),
                 private val delEquipo: (Long) -> Boolean = { true }) {
    fun comprobar(): ConfiguracionWompi = configuracion ?: throw Problema(503, "pagos_apagados")

    /** Lo mismo que [comprobar], pero para una cuenta: en Sandbox solo el equipo. */
    fun comprobar(cuenta: Long): ConfiguracionWompi = comprobar().also { if (!delEquipo(cuenta)) throw Problema(503, "pagos_apagados") }

    fun orden(cuenta: Long, paquete: Int): OrdenWompi {
        val c = comprobar(cuenta)
        if (paquete !in PaquetesWompi().paquetes) throw Problema(400, "paquete")
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
        val c = comprobar()
        fun invalido(): Nothing = throw Problema(401, "firma_invalida")
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
        if (e["event"]?.jsonPrimitive?.content != "transaction.updated" || e["environment"]?.jsonPrimitive?.content != "test") return
        val t = datos["transaction"] as? JsonObject ?: return
        val id = (t["id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return
        val referencia = (t["reference"] as? JsonPrimitive)?.contentOrNull ?: return
        val estado = (t["status"] as? JsonPrimitive)?.contentOrNull ?: return
        if (estado !in setOf("APPROVED", "DECLINED", "VOIDED", "ERROR")) return
        if ((t["currency"] as? JsonPrimitive)?.contentOrNull != "COP") return
        val monto = (t["amount_in_cents"] as? JsonPrimitive)?.longOrNull ?: return
        base.transaccion {
            val orden = leer("SELECT cuenta,paquete,monto,acreditada,transaccion FROM ordenes_pago WHERE referencia=?", referencia) {
                Triple(it.getLong(1), it.getInt(2), it.getLong(3)) to ((it.getInt(4) == 1) to it.getString(5))
            }.firstOrNull() ?: return@transaccion
            val (compra, registro) = orden
            val (acreditada, transaccion) = registro
            val (cuenta, paquete, guardado) = compra
            if (paquete !in PaquetesWompi().paquetes || guardado != paquete.toLong() * TemasPerfil.PESOS_POR_CHISPA * 100 || monto != guardado) return@transaccion
            if (acreditada && transaccion != id) return@transaccion
            if (leer("SELECT referencia FROM ordenes_pago WHERE transaccion=? AND referencia<>?", id, referencia) { it.getString(1) }.isNotEmpty()) return@transaccion
            cambiar("UPDATE ordenes_pago SET estado=?,transaccion=?,acreditada=? WHERE referencia=?",
                estado, id, if (acreditada || estado == "APPROVED") 1 else 0, referencia)
            if (estado == "APPROVED" && !acreditada) cambiar("INSERT INTO chispas(cuenta,cantidad,motivo,cuando) VALUES(?,?,?,?)",
                cuenta, paquete, "wompi:$id", System.currentTimeMillis())
        }
    }
}

package io.github.fogonapps.ahorrador.datos

import kotlinx.serialization.json.*
import java.security.MessageDigest
import kotlin.math.roundToInt

/*
 * Lo que La Alcancía publica en Fogón Social (D-032): solo logros elegidos y metas en porcentaje.
 * Nunca saldos, montos, cuentas, bancos ni movimientos. El esquema es el del servidor (`validarLogro`).
 */

const val TIPO_LOGRO = "logro"
const val PARTE_METAS = "metas"

enum class Logro(val codigo: String) { META_CUMPLIDA("meta_cumplida"), META_AVANCE("meta_avance"), PRIMERA_ANOTACION("primera_anotacion"), CIERRES("cierres") }

private fun huella(t: String) = MessageDigest.getInstance("SHA-256").digest(t.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

/** La clave estable de un logro: el mismo logro de la misma meta actualiza, no duplica. */
fun claveLogro(logro: Logro, origen: String = "") = "logro-" + huella(logro.codigo + ":" + origen)

/** Un logro que se puede compartir, con lo justo para mostrarlo. */
data class LogroPosible(val logro: Logro, val meta: Meta? = null, val porcentaje: Int? = null, val cantidad: Int? = null) {
    val clave get() = claveLogro(logro, meta?.id ?: "")
}

/** Los logros que la persona tiene hoy: metas cumplidas, el avance de las demás, la primera anotación y los meses cerrados. */
fun logrosPosibles(e: Estado, L: Logica): List<LogroPosible> = buildList {
    e.metas.forEach { m ->
        val p = L.progreso(e, m)
        if (p >= 1.0) add(LogroPosible(Logro.META_CUMPLIDA, m))
        else add(LogroPosible(Logro.META_AVANCE, m, porcentaje = (p * 100).toInt().coerceIn(0, 99)))
    }
    if (e.movimientos.isNotEmpty()) add(LogroPosible(Logro.PRIMERA_ANOTACION))
    if (e.cierres.isNotEmpty()) add(LogroPosible(Logro.CIERRES, cantidad = e.cierres.size.coerceAtMost(1200)))
}

/** La instantánea de un logro: el nombre de la meta va recortado a 60; nunca su objetivo ni lo ahorrado. */
fun instantaneaLogro(l: LogroPosible, texto: String): JsonObject = buildJsonObject {
    put("version", 1); put("logro", l.logro.codigo)
    when (l.logro) {
        Logro.META_CUMPLIDA -> put("meta", requireNotNull(l.meta).nombre.trim().take(60).ifBlank { "—" })
        Logro.META_AVANCE -> {
            put("meta", requireNotNull(l.meta).nombre.trim().take(60).ifBlank { "—" })
            put("porcentaje", requireNotNull(l.porcentaje).coerceIn(0, 100))
        }
        Logro.CIERRES -> put("cantidad", requireNotNull(l.cantidad).coerceIn(1, 1200))
        Logro.PRIMERA_ANOTACION -> {}
    }
    texto.trim().take(2000).takeIf { it.isNotEmpty() }?.let { put("texto", it) }
}

/** La parte «metas» del perfil: nombre y porcentaje entero de cada meta (hasta 20), sin dinero. */
fun parteMetas(e: Estado, L: Logica): JsonObject = buildJsonObject {
    put("version", 1)
    putJsonArray("lista") {
        e.metas.take(20).forEach { m ->
            add(buildJsonObject { put("nombre", m.nombre.trim().take(60)); put("porcentaje", (L.progreso(e, m) * 100).roundToInt().coerceIn(0, 100)) })
        }
    }
}

/** Lee la parte «metas» de un perfil ajeno. */
fun metasDeParte(parte: JsonElement?): List<Pair<String, Int>> = runCatching {
    (parte as JsonObject)["lista"]!!.jsonArray.map { val o = it.jsonObject; o["nombre"]!!.jsonPrimitive.content to o["porcentaje"]!!.jsonPrimitive.int }
}.getOrDefault(emptyList())

/** El servidor añade el tipo antes de medir (20 000 caracteres para La Alcancía). */
fun cabeLogro(d: JsonObject): Boolean = JsonObject(d + ("tipo" to JsonPrimitive(TIPO_LOGRO))).toString().length <= 20_000

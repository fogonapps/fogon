package io.github.fogonapps.social

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.FactorMfa")
data class FactorMfa(val codigo: String = "", val recuperacion: String = "")
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.InscripcionMfa")
data class InscripcionMfa(val secreto: String, val uri: String)
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.CodigosMfa")
data class CodigosMfa(val codigos: List<String>)
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.ElevacionMfa")
data class ElevacionMfa(val elevadaHasta: Long)
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.MiembroEquipo")
data class MiembroEquipo(val cuenta: Long, val usuario: String, val rol: String, val permisos: Set<String> = emptySet())
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.ContenidoEquipo")
data class ContenidoEquipo(val que: String, val objeto: String, val motivo: String = "")
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.SancionEquipo")
data class SancionEquipo(val usuario: String, val motivo: String)
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.CambioEquipo")
data class CambioEquipo(val usuario: String, val rol: String = "", val permiso: String = "", val codigo: String)
@Serializable
@kotlinx.serialization.SerialName("io.github.fogonapps.cajon.datos.EntradaRegistro")
data class EntradaRegistro(val id: Long, val actor: String, val accion: String, val objeto: String, val detalle: String, val cuando: Long)
data class RegistroEquipo(val entradas: List<EntradaRegistro>, val cadenaIntegra: Boolean = true, val siguiente: Long? = null)
private val formatoEquipo = Json { ignoreUnknownKeys = true }
/** Admite el sobre actual y la lista de servidores anteriores. */
fun convertirRegistro(texto: String): RegistroEquipo {
    val raiz = formatoEquipo.parseToJsonElement(texto)
    if (raiz is JsonArray) return RegistroEquipo(formatoEquipo.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(EntradaRegistro.serializer()), raiz), siguiente = raiz.lastOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.longOrNull?.takeIf { raiz.size == 100 })
    val sobre = raiz.jsonObject
    return RegistroEquipo(formatoEquipo.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(EntradaRegistro.serializer()), sobre.getValue("entradas")),
        sobre["cadenaIntegra"]?.jsonPrimitive?.booleanOrNull ?: true, sobre["siguiente"]?.jsonPrimitive?.longOrNull)
}
fun puedeRestaurar(estado: String, permisos: Set<String>) = estado == "retirado" && "gestionar_publicaciones" in permisos
fun necesitaElevar(mfa: Boolean, elevadaHasta: Long, ahora: Long) = !mfa || elevadaHasta <= ahora
fun accionesModeracion(permisos: Set<String>, regla: String = "", faltas: Int = 0): Set<String> = buildSet {
    if ("sancionar" in permisos) {
        add("sin_falta")
        val cierreDirecto = regla in setOf("menor_en_riesgo", "menor_de_edad", "evadir_sancion", "amenaza_real")
        if ((!cierreDirecto && faltas + 1 < 4) || "cerrar_cuentas" in permisos) add("falta")
        if ("sancion_grave" in permisos && ((!cierreDirecto && faltas + 2 < 4) || "cerrar_cuentas" in permisos)) add("falta_grave")
        if ("cerrar_cuentas" in permisos) add("cerrar")
    }
}

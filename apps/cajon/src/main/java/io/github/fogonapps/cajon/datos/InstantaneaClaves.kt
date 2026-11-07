package io.github.fogonapps.cajon.datos

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

@Serializable
internal data class ValorClave(val tipo: String, val texto: String = "", val conjunto: Set<String> = emptySet())

/** Conserva los tipos de SharedPreferences, incluidos IV, conjuntos y preferencias todavía desconocidas. */
internal fun copiarPreferenciasClaves(valores: Map<String, *>): ByteArray {
    val mapa = valores.mapValues { (_, valor) ->
        when (valor) {
            is String -> ValorClave("texto", valor)
            is Boolean -> ValorClave("booleano", valor.toString())
            is Int -> ValorClave("entero", valor.toString())
            is Long -> ValorClave("largo", valor.toString())
            is Float -> ValorClave("decimal", valor.toString())
            is Set<*> -> {
                require(valor.all { it is String })
                ValorClave("conjunto", conjunto = valor.filterIsInstance<String>().toSet())
            }
            else -> error("Tipo de preferencia desconocido")
        }
    }
    return JsonCajon.encodeToString(MapSerializer(String.serializer(), ValorClave.serializer()), mapa).encodeToByteArray()
}

internal fun abrirPreferenciasClaves(bytes: ByteArray): Map<String, Any> =
    JsonCajon.decodeFromString(MapSerializer(String.serializer(), ValorClave.serializer()), bytes.decodeToString()).mapValues { (_, valor) ->
        when (valor.tipo) {
            "texto" -> valor.texto
            "booleano" -> valor.texto.toBooleanStrict()
            "entero" -> valor.texto.toInt()
            "largo" -> valor.texto.toLong()
            "decimal" -> valor.texto.toFloat()
            "conjunto" -> valor.conjunto
            else -> error("Tipo de preferencia desconocido")
        }
    }

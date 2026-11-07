package io.github.fogonapps.cajon.datos

import android.content.Context
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Lo que "Para ti" ya mostró: cuántas veces salió cada cosa, cuándo, y si la persona la abrió. Sirve
 * para no repetir siempre lo mismo (ver `Recomendar.elegir`). Solo en este teléfono (`files/para_ti.json`): es un
 * detalle de cómo se muestra, no un gusto, así que no va en la sincronización. Hasta 600 cosas, las más recientes.
 */
class HistorialParaTi(context: Context, directorio: File = context.filesDir) {
    @Serializable
    private data class Archivo(val vistos: Map<String, Visto> = emptyMap())

    @Serializable
    private data class Visto(val veces: Int = 0, val ultima: Long = 0, val abierta: Boolean = false)

    private val archivo = File(directorio, "para_ti.json")
    private var vistos: Map<String, Visto> = runCatching {
        JsonCajon.decodeFromString(Archivo.serializer(), archivo.readText()).vistos
    }.getOrDefault(emptyMap())

    @Synchronized
    fun todo(): Map<String, Recomendar.Visto> = vistos.mapValues { (_, v) -> Recomendar.Visto(v.veces, v.ultima, v.abierta) }

    /** Suma una vez a lo que se mostró en esta carga. */
    @Synchronized
    fun mostrado(claves: Collection<String>, ahora: Long = System.currentTimeMillis()) {
        if (claves.isEmpty()) return
        val m = vistos.toMutableMap()
        claves.forEach { k -> val v = m[k] ?: Visto(); m[k] = v.copy(veces = v.veces + 1, ultima = ahora) }
        guardar(m)
    }

    /** La persona la abrió: ya no cuenta como "salió y no le interesó". */
    @Synchronized
    fun abierta(clave: String) {
        val v = vistos[clave] ?: return
        if (!v.abierta) guardar(vistos + (clave to v.copy(abierta = true)))
    }

    @Synchronized
    fun borrar() = guardar(emptyMap())

    private fun guardar(m: Map<String, Visto>) {
        vistos = if (m.size > 600) m.entries.sortedByDescending { it.value.ultima }.take(600).associate { it.key to it.value } else m
        runCatching {
            val tmp = File(archivo.parentFile, "para_ti.json.tmp")
            tmp.writeText(JsonCajon.encodeToString(Archivo.serializer(), Archivo(vistos)))
            tmp.renameTo(archivo)
        }
    }
}

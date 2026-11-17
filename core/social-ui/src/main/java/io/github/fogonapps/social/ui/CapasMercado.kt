package io.github.fogonapps.social.ui

import kotlinx.serialization.json.*

/** Coordenadas normalizadas; tamaño en fracción del lado menor. Nunca hay rotación. */
data class CapaMercado(
    val id: Int, val clase: String, val x: Float = .5f, val y: Float = .5f,
    val tamano: Float = .35f, val color: Int = 1, val contenido: String = "",
)
data class LienzoMercado(
    val tipo: String = "banner", val fondo: Int = 0, val segundo: Int = 1,
    val degradado: Boolean = false, val trama: Float = 0f, val paso: Float = 24f,
    val capas: List<CapaMercado> = emptyList(),
) {
    val ancho get() = if (tipo == "marco") 512 else 1360
    val alto get() = if (tipo == "marco") 512 else 400
    fun agregar(clase: String, contenido: String = "", color: Int = 1): LienzoMercado {
        if (capas.size >= 40 || (clase == "texto" && capas.count { it.clase == "texto" } >= 3)) return this
        return copy(capas = capas + CapaMercado((capas.maxOfOrNull { it.id } ?: 0) + 1, clase, color = color, contenido = contenido))
    }
    fun cambiar(capa: CapaMercado) = copy(capas = capas.map { if (it.id == capa.id) capa else it })
    fun borrar(id: Int) = copy(capas = capas.filterNot { it.id == id })
    fun ordenar(id: Int, adelante: Boolean): LienzoMercado {
        val lista = capas.toMutableList(); val i = lista.indexOfFirst { it.id == id }
        val destino = i + if (adelante) 1 else -1
        if (i < 0 || destino !in lista.indices) return this
        java.util.Collections.swap(lista, i, destino)
        return copy(capas = lista)
    }
    fun serializar(): String = buildJsonObject {
        put("version", 1); put("tipo", tipo); put("fondo", fondo); put("segundo", segundo)
        put("degradado", degradado); put("trama", trama); put("paso", paso)
        put("capas", buildJsonArray { capas.forEach { c -> add(buildJsonObject {
            put("id", c.id); put("clase", c.clase); put("x", c.x); put("y", c.y)
            put("tamano", c.tamano); put("color", c.color); put("contenido", c.contenido)
        }) } })
    }.toString()
    companion object {
        fun leer(texto: String): LienzoMercado {
            val j = Json.parseToJsonElement(texto).jsonObject
            require(j.getValue("version").jsonPrimitive.int == 1)
            val tipo = j.getValue("tipo").jsonPrimitive.content; require(tipo in listOf("banner", "marco"))
            fun numero(k: String) = j.getValue(k).jsonPrimitive.int.also { require(it in 0..5) }
            fun finito(v: Float, rango: ClosedFloatingPointRange<Float>) = v.also { require(it.isFinite() && it in rango) }
            val capas = j.getValue("capas").jsonArray.map { e -> val c = e.jsonObject
                CapaMercado(c.getValue("id").jsonPrimitive.int, c.getValue("clase").jsonPrimitive.content,
                    finito(c.getValue("x").jsonPrimitive.float, 0f..1f), finito(c.getValue("y").jsonPrimitive.float, 0f..1f),
                    finito(c.getValue("tamano").jsonPrimitive.float, .05f..3f), c.getValue("color").jsonPrimitive.int.also { require(it in 0..5) },
                    c.getValue("contenido").jsonPrimitive.content.also { require(it.length <= 300) })
            }
            require(capas.size <= 40 && capas.count { it.clase == "texto" } <= 3 && capas.map { it.id }.distinct().size == capas.size)
            require(capas.all { it.clase in listOf("texto", "personaje", "imagen", "circulo", "pildora", "estrella") })
            return LienzoMercado(tipo, numero("fondo"), numero("segundo"), j.getValue("degradado").jsonPrimitive.boolean,
                finito(j.getValue("trama").jsonPrimitive.float, 0f..1f), finito(j.getValue("paso").jsonPrimitive.float, 8f..64f), capas)
        }
    }
}

/** Una transformación continua cuenta como un paso, aunque tenga muchos eventos del dedo. */
class HistorialMercado(inicial: LienzoMercado) {
    var actual = inicial; private set
    private val anteriores = ArrayDeque<LienzoMercado>()
    val puedeDeshacer get() = anteriores.isNotEmpty()
    fun aplicar(nuevo: LienzoMercado) {
        if (nuevo == actual) return
        recordar(actual); actual = nuevo
    }
    fun transformar(nuevo: LienzoMercado) { actual = nuevo }
    fun terminarGesto(inicio: LienzoMercado) { if (inicio != actual) recordar(inicio) }
    private fun recordar(valor: LienzoMercado) { anteriores.addLast(valor); if (anteriores.size > 30) anteriores.removeFirst() }
    fun deshacer(): LienzoMercado { if (anteriores.isNotEmpty()) actual = anteriores.removeLast(); return actual }
}

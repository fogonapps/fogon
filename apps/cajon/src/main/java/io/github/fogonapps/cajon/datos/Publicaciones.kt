package io.github.fogonapps.cajon.datos

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Compatibilidad con publicaciones de una foto y con las fotos de las reseñas. */
fun imagenesPublicacion(datos: JsonObject): List<String> {
    val lista = datos["imagenes"] ?: datos["fotos"]
    if (lista is JsonArray) return lista.mapNotNull { (it as? JsonPrimitive)?.content?.takeIf(String::isNotBlank) }
    return listOfNotNull((datos["imagen"] as? JsonPrimitive)?.content?.takeIf(String::isNotBlank))
}

/** Una respuesta cuyo padre no llega se conserva como raíz para clientes antiguos. */
fun arbolComentarios(lista: List<ComentarioSocial>): List<Pair<ComentarioSocial, List<ComentarioSocial>>> {
    val ids = lista.map { it.id }.toSet()
    return lista.filter { it.padre == null || it.padre !in ids }.sortedWith(compareBy({ it.cuando }, { it.id })).map { raiz ->
        raiz to lista.filter { it.padre == raiz.id }.sortedWith(compareBy({ it.cuando }, { it.id }))
    }
}

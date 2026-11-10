package io.github.fogonapps.social

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
fun <T> arbolComentarios(lista: List<T>, id: (T) -> Long, padre: (T) -> Long?, cuando: (T) -> Long): List<Pair<T, List<T>>> {
    val ids = lista.map { id(it) }.toSet()
    return lista.filter { padre(it) == null || padre(it) !in ids }.sortedWith(compareBy({ cuando(it) }, { id(it) })).map { raiz ->
        raiz to lista.filter { padre(it) == id(raiz) }.sortedWith(compareBy({ cuando(it) }, { id(it) }))
    }
}

/** Retira la cuenta de las listas de seguidores y seguidos que estén cargadas. */
fun quitarCuentaBloqueada(gente: Map<String, List<PersonaSocial>>, usuario: String): Map<String, List<PersonaSocial>> =
    gente.mapValues { (clave, personas) ->
        if (clave.startsWith("seguidores:") || clave.startsWith("siguiendo:")) personas.filterNot { it.usuario == usuario }
        else personas
    }

fun quitarPublicacionesBloqueadas(feed: List<PublicacionSocial>?, usuario: String): List<PublicacionSocial>? =
    feed?.filterNot { it.autor.usuario == usuario }

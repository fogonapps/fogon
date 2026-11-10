package io.github.fogonapps.cajon.datos

fun imagenesPublicacion(datos: kotlinx.serialization.json.JsonObject) = io.github.fogonapps.social.imagenesPublicacion(datos)
fun arbolComentarios(lista: List<ComentarioSocial>) = io.github.fogonapps.social.arbolComentarios(lista, { it.id }, { it.padre }, { it.cuando })

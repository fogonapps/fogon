package io.github.fogonapps.cajon.datos

internal typealias VideoPreparado = io.github.fogonapps.social.VideoPreparado

internal suspend fun prepararVideo(contexto: android.content.Context, uri: android.net.Uri, progreso: (Float) -> Unit, recorte: () -> Unit) =
    io.github.fogonapps.social.prepararVideo(contexto, uri, progreso, recorte)

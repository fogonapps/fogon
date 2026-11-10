package io.github.fogonapps.cajon.ui

import androidx.compose.runtime.Composable
import io.github.fogonapps.cajon.CajonViewModel

@Composable
fun VideoPublicacion(vm: CajonViewModel, nombre: String, poster: String, segundos: Int) =
    io.github.fogonapps.social.ui.VideoPublicacion({ vm.red.cabecerasVideo() }, nombre, poster, segundos)

@Composable
internal fun ReproductorVideo(vm: CajonViewModel, nombre: String, cerrar: () -> Unit) =
    io.github.fogonapps.social.ui.ReproductorVideo({ vm.red.cabecerasVideo() }, nombre, cerrar)

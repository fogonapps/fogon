package io.github.fogonapps.cajon.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun CarruselFotos(fotos: List<String>, modifier: Modifier = Modifier) =
    io.github.fogonapps.social.ui.CarruselFotos(fotos, modifier)

@Composable
fun VisorFotos(fotos: List<String>, inicial: Int, cerrar: () -> Unit) =
    io.github.fogonapps.social.ui.VisorFotos(fotos, inicial, cerrar)

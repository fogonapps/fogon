package io.github.fogonapps.social.ui
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
internal fun Cabecera(titulo: String, volver: () -> Unit, derecha: @Composable () -> Unit = {}) =
    io.github.fogonapps.identidad.CabeceraSocial(titulo, volver, stringResource(R.string.social_volver), derecha = derecha)

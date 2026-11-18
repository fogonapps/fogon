package io.github.fogonapps.social.ui

import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.perfil.TarjetaPreguntaLena
import io.github.fogonapps.perfil.debePreguntarLena

@Composable
fun PreguntaLenaEnPerfil(vm: InterfazSocial) {
    val red = vm.red
    val yo by red.usuario.collectAsStateWithLifecycle()
    val cuenta by red.cuenta.collectAsStateWithLifecycle()
    val guardando by red.guardandoLena.collectAsStateWithLifecycle()
    LaunchedEffect(red, yo) { if (yo != null) red.cargarPreferenciaLena() }
    val actual = cuenta?.takeIf { it.usuario == yo } ?: return
    if (debePreguntarLena(actual.nivelLena, actual.lenaPreguntada)) {
        TarjetaPreguntaLena(!guardando, red::cambiarVisibilidadLena)
    }
}

@Composable
fun LenaEnEditarPerfil(vm: InterfazSocial) {
    val red = vm.red
    val yo by red.usuario.collectAsStateWithLifecycle()
    val cuenta by red.cuenta.collectAsStateWithLifecycle()
    val guardando by red.guardandoLena.collectAsStateWithLifecycle()
    LaunchedEffect(red, yo) { if (yo != null) red.cargarPreferenciaLena() }
    val actual = cuenta?.takeIf { it.usuario == yo && it.nivelLena.isNotBlank() } ?: return
    Row(Modifier.fillMaxWidth().toggleable(value = actual.lenaVisible, enabled = !guardando, role = Role.Switch) {
        red.cambiarVisibilidadLena(it)
    }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.lena_visibilidad), Modifier.weight(1f), color = FogonTema.actual.texto)
        Switch(checked = actual.lenaVisible, onCheckedChange = null, enabled = !guardando)
    }
}

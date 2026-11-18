package io.github.fogonapps.social.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.perfil.PerfilVista
import kotlinx.coroutines.CancellationException

/** El nivel procede de las mismas insignias compartidas que determinan el cupo. */
@Composable
private fun cupoPropio(vm: InterfazSocial): io.github.fogonapps.social.CupoEscaparates {
    val yo by vm.red.usuario.collectAsStateWithLifecycle()
    val revision by vm.red.revisionEscaparates.collectAsStateWithLifecycle()
    var cupo by remember(yo) { mutableStateOf(io.github.fogonapps.social.CupoEscaparates()) }
    LaunchedEffect(yo, revision) {
        if (yo != null) try { cupo = vm.red.cupoEscaparates() }
        catch (e: CancellationException) { throw e } catch (_: Exception) { /* Conserva el último dato. */ }
    }
    return cupo
}

@Composable
fun conNivelPropio(vista: PerfilVista, vm: InterfazSocial): PerfilVista {
    val cupo = cupoPropio(vm)
    val cuenta by vm.red.cuenta.collectAsStateWithLifecycle()
    val nivelLena = cuenta?.takeIf { it.usuario == vista.usuario && it.lenaVisible }?.nivelLena.orEmpty()
    return vista.copy(nivel = cupo.nivel, insigniasNivel = cupo.insignias, nivelLena = nivelLena)
}

@Composable
fun NivelEnEditarPerfil(vm: InterfazSocial, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    val yo by vm.red.usuario.collectAsStateWithLifecycle()
    if (yo == null) return
    val cupo = cupoPropio(vm)
    androidx.compose.foundation.layout.Column(modifier) {
        io.github.fogonapps.identidad.Titulo(androidx.compose.ui.res.stringResource(io.github.fogonapps.perfil.R.string.perfil_nivel_titulo),
            androidx.compose.ui.Modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 8.dp))
        io.github.fogonapps.perfil.NivelDetalle(cupo.nivel, cupo.insignias, propio = true, gratis = cupo.gratis,
            modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp))
    }
}

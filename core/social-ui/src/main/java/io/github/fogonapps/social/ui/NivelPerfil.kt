package io.github.fogonapps.social.ui

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.perfil.PerfilVista
import kotlinx.coroutines.CancellationException

/** El nivel procede de las mismas insignias compartidas que determinan el cupo. */
@Composable
fun conNivelPropio(vista: PerfilVista, vm: InterfazSocial): PerfilVista {
    val yo by vm.red.usuario.collectAsStateWithLifecycle()
    val revision by vm.red.revisionEscaparates.collectAsStateWithLifecycle()
    var cupo by remember(yo) { mutableStateOf(io.github.fogonapps.social.CupoEscaparates()) }
    LaunchedEffect(yo, revision) {
        if (yo != null) try { cupo = vm.red.cupoEscaparates() }
        catch (e: CancellationException) { throw e } catch (_: Exception) { /* Conserva el último dato. */ }
    }
    return vista.copy(nivel = cupo.nivel, insigniasNivel = cupo.insignias)
}

package io.github.fogonapps.social.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.perfil.PerfilVista
import io.github.fogonapps.perfil.R as RP
import kotlinx.coroutines.CancellationException

/** El nivel procede de las mismas insignias compartidas que determinan el cupo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun conNivelPropio(vista: PerfilVista, vm: InterfazSocial): PerfilVista {
    val yo by vm.red.usuario.collectAsStateWithLifecycle()
    val revision by vm.red.revisionEscaparates.collectAsStateWithLifecycle()
    var nivel by remember(yo) { mutableIntStateOf(0) }
    var abierto by remember(yo) { mutableStateOf(false) }
    LaunchedEffect(yo, revision) {
        if (yo != null) try { nivel = vm.red.cupoEscaparates().nivel }
        catch (e: CancellationException) { throw e } catch (_: Exception) { /* Se conserva el último dato recibido. */ }
    }
    if (abierto) ModalBottomSheet(onDismissRequest = { abierto = false }) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(RP.string.perfil_nivel, nivel), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(RP.string.perfil_nivel_explica))
            if (nivel < 12) {
                LinearProgressIndicator(progress = { (nivel % 3) / 3f }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(RP.string.perfil_nivel_faltan, 3 - nivel % 3))
            } else Text(stringResource(RP.string.perfil_nivel_maximo))
            Spacer(Modifier.height(16.dp))
        }
    }
    return vista.copy(nivel = nivel, abrirNivel = { abierto = true })
}

package io.github.fogonapps.cajon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.R
import io.github.fogonapps.identidad.BotonSecundario

/** Solo ofrece borrar el activo: la exportación usa exactamente el cajón visible. */
@Composable
fun SelectorPerfiles(vm: CajonViewModel) {
    var abierto by remember { mutableStateOf(false) }
    var quitar by remember { mutableStateOf(false) }
    val acciones = LocalAcciones.current
    BotonSecundario(stringResource(R.string.perfiles_cambiar), { abierto = true }, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
    if (abierto) AlertDialog(
        onDismissRequest = { abierto = false },
        title = { Text(stringResource(R.string.perfiles_cambiar)) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val lista = vm.perfilesTelefono()
                lista.perfiles.forEach { perfil ->
                    TextButton(onClick = { abierto = false; vm.cambiarPerfil(perfil.id) }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FotoPerfil(perfil.miniatura, 40.dp)
                            Column {
                                Text(perfil.apodo.ifBlank { stringResource(R.string.perfiles_local) })
                                if (perfil.usuario.isNotBlank()) Text("@${perfil.usuario}")
                                if (perfil.id == lista.activo) Text(stringResource(R.string.perfiles_activo))
                            }
                        }
                    }
                }
                TextButton(onClick = { abierto = false; vm.agregarPerfil() }) { Text(stringResource(R.string.perfiles_anadir)) }
                // Solo se quita el perfil activo (la copia JSON es la de su cajón): se dice cuál, para no confundirse.
                val activo = lista.perfiles.first { it.id == lista.activo }
                val nombreActivo = activo.apodo.ifBlank { stringResource(R.string.perfiles_local) }
                TextButton(onClick = { quitar = true; abierto = false }) { Text(stringResource(R.string.perfiles_quitar_cual, nombreActivo)) }
            }
        },
        confirmButton = { TextButton(onClick = { abierto = false }) { Text(stringResource(R.string.cancelar)) } },
    )
    if (quitar) AlertDialog(
        onDismissRequest = { quitar = false },
        title = {
            val lista = vm.perfilesTelefono()
            val activo = lista.perfiles.first { it.id == lista.activo }
            val nombre = activo.apodo.ifBlank { stringResource(R.string.perfiles_local) } + if (activo.usuario.isNotBlank()) " (@${activo.usuario})" else ""
            Text(stringResource(R.string.perfiles_quitar_cual, nombre))
        },
        text = {
            Column {
                Text(stringResource(R.string.perfiles_confirmar))
                TextButton(onClick = { acciones?.exportar?.invoke() }) { Text(stringResource(R.string.perfiles_copia)) }
            }
        },
        confirmButton = { TextButton(onClick = { quitar = false; vm.quitarPerfil() }) { Text(stringResource(R.string.perfiles_quitar_si)) } },
        dismissButton = { TextButton(onClick = { quitar = false }) { Text(stringResource(R.string.cancelar)) } },
    )
}

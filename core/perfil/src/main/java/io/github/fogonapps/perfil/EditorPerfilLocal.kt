package io.github.fogonapps.perfil

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.fogonapps.identidad.*

/** Formulario local sin estado persistente ni dependencia de una aplicación. */
@Composable
fun EditorPerfilLocal(
    nombre: String, bio: String, foto: String, portada: String,
    onNombre: (String) -> Unit, onBio: (String) -> Unit,
    onElegirFoto: () -> Unit, onQuitarFoto: () -> Unit,
    onElegirPortada: () -> Unit, onQuitarPortada: () -> Unit,
    onGuardar: () -> Unit, habilitado: Boolean = true,
) {
    val f = FogonTema.actual
    TituloEditor(stringResource(R.string.perfil_local_foto_portada))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FotoPerfilLocal(foto, tamano = 72.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonSecundario(stringResource(R.string.perfil_local_elegir_foto), onElegirFoto, Modifier.fillMaxWidth(), habilitado)
                if (foto.isNotBlank()) BotonSecundario(stringResource(R.string.perfil_local_quitar_foto), onQuitarFoto, Modifier.fillMaxWidth(), habilitado)
            }
        }
        Box(Modifier.fillMaxWidth().height(110.dp).clip(forma(24.dp))
            .tarjetaFogon(forma(24.dp), sombra = false).haloPuntos(f.tinta2.copy(alpha = 0.35f))) {
            ImagenLocal(portada, Modifier.matchParentSize())
        }
        BotonSecundario(stringResource(R.string.perfil_local_subir_imagen), onElegirPortada, Modifier.fillMaxWidth(), habilitado)
        if (portada.isNotBlank()) BotonSecundario(stringResource(R.string.perfil_local_quitar_portada), onQuitarPortada, Modifier.fillMaxWidth(), habilitado)
    }
    TituloEditor(stringResource(R.string.perfil_local_quien_eres))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(nombre, { onNombre(recortarNombrePerfil(it)) }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.perfil_local_nombre)) })
        OutlinedTextField(bio, { onBio(recortarBioPerfil(it)) }, Modifier.fillMaxWidth(), minLines = 3,
            label = { Text(stringResource(R.string.perfil_local_bio)) },
            supportingText = { Text(stringResource(R.string.perfil_local_contador, bio.length, LIMITE_BIO_PERFIL)) })
        BotonRiso(stringResource(R.string.perfil_local_guardar), onGuardar, Modifier.fillMaxWidth(), habilitado = habilitado)
    }
}

@Composable
private fun TituloEditor(titulo: String) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(titulo, style = MaterialTheme.typography.titleLarge, color = FogonTema.actual.texto,
            modifier = Modifier.weight(1f).semantics { heading() })
    }
}

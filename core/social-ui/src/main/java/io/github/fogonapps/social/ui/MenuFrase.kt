package io.github.fogonapps.social.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fogonapps.identidad.BotonIconoSocial
import io.github.fogonapps.identidad.FilaVidrioSocial
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.IconosSociales
import io.github.fogonapps.identidad.forma

/**
 * Lo que se puede hacer con la frase del saludo al mantenerla pulsada, igual en todas las apps: decir que gusta (o que
 * ya no), compartirla, no verla más y proponer una propia. [onProponer] recibe el texto; si es null (la app no tiene
 * red), no se ofrece proponer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuFrase(
    texto: String, gusta: Boolean, cerrar: () -> Unit, onGusta: () -> Unit, onNoMostrar: () -> Unit,
    onCompartido: () -> Unit = {}, onProponer: ((String) -> Unit)? = null,
) {
    val f = FogonTema.actual
    val contexto = LocalContext.current
    ModalBottomSheet(cerrar, containerColor = f.fondo) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(texto, style = MaterialTheme.typography.titleMedium, color = f.texto, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            FilaVidrioSocial({ onGusta(); cerrar() }) {
                IconoRiso(IconosSociales.Corazon, null, tamano = 20.dp, tinta = f.acentoTexto)
                Text(stringResource(if (gusta) R.string.social_frase_ya_no_gusta else R.string.social_frase_me_gusta), color = f.texto, modifier = Modifier.weight(1f))
            }
            FilaVidrioSocial({
                onCompartido()
                contexto.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND)
                    .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, texto), null))
                cerrar()
            }) {
                IconoRiso(IconosSociales.Compartir, null, tamano = 20.dp, tinta = f.texto)
                Text(stringResource(R.string.social_compartir), color = f.texto, modifier = Modifier.weight(1f))
            }
            FilaVidrioSocial({ onNoMostrar(); cerrar() }) {
                IconoRiso(IconosSociales.Cerrar, null, tamano = 20.dp, tinta = f.texto)
                Text(stringResource(R.string.social_frase_no_mostrar), color = f.texto, modifier = Modifier.weight(1f))
            }
            // Proponer una frase propia: el equipo la revisa y, si va, sale para todos en esta app.
            if (onProponer != null) {
                var propia by rememberSaveable { mutableStateOf("") }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(propia, { propia = it.take(90) }, Modifier.weight(1f), singleLine = true, shape = forma(22.dp),
                        placeholder = { Text(stringResource(R.string.social_frase_proponer)) })
                    BotonIconoSocial(IconosSociales.Enviar, stringResource(R.string.social_frase_proponer), activo = propia.trim().length >= 6, lleno = true) {
                        onProponer(propia); propia = ""; cerrar()
                    }
                }
            }
        }
    }
}

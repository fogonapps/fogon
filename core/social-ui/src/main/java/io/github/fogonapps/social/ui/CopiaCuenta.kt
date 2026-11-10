package io.github.fogonapps.social.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.identidad.*
import io.github.fogonapps.social.RedApp

/**
 * D-033: «Tus datos en tu cuenta». Con la llave en el teléfono, dice que se guardan solos y cuándo fue la última
 * copia; si la cuenta ya tiene datos y este teléfono no la abrió, pide la contraseña. [soloSiFalta] la muestra
 * únicamente cuando hay que hacer algo (para ponerla arriba en «Tu gente»).
 */
@Composable
fun CopiaCuenta(red: RedApp, modifier: Modifier = Modifier, soloSiFalta: Boolean = false) {
    val f = FogonTema.actual
    val usuario by red.usuario.collectAsStateWithLifecycle()
    val lista by red.copiaLista.collectAsStateWithLifecycle()
    val bloqueada by red.copiaBloqueada.collectAsStateWithLifecycle()
    val ultima by red.ultimaCopia.collectAsStateWithLifecycle()
    val cuenta by red.cuenta.collectAsStateWithLifecycle()
    LaunchedEffect(usuario) { if (usuario != null) red.revisarCopia() }
    if (usuario == null) return
    val sinClave = !lista && !bloqueada && cuenta?.conClave == false
    if (soloSiFalta && !bloqueada && !sinClave) return
    var clave by remember { mutableStateOf("") }
    var mala by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).tarjetaFogon().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.social_copia_titulo), style = MaterialTheme.typography.titleSmall, color = f.texto)
        when {
            bloqueada -> {
                Text(stringResource(R.string.social_copia_bloqueada), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                OutlinedTextField(clave, { clave = it; mala = false }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.social_copia_clave)) },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                if (mala) Text(stringResource(R.string.social_copia_mala), color = MaterialTheme.colorScheme.error)
                BotonRiso(stringResource(R.string.social_copia_abrir), { red.desbloquearCopia(clave) { mala = true } }, Modifier.fillMaxWidth(), habilitado = clave.length >= 8)
            }
            sinClave -> Text(stringResource(R.string.social_copia_sin_clave), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            else -> {
                Text(stringResource(R.string.social_copia_lista), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                if (ultima > 0) Text(stringResource(R.string.social_copia_ultima,
                    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(ultima))),
                    style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
        }
    }
}

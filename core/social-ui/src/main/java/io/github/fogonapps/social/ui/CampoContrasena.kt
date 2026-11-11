package io.github.fogonapps.social.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.fogonapps.identidad.*

/** Un ojo abierto y uno tachado, para ver u ocultar la contraseña. */
val IconoOjo = dibujo("ojo", trazo("M2.5 12c2.2-4.2 5.6-6.4 9.5-6.4s7.3 2.2 9.5 6.4c-2.2 4.2-5.6 6.4-9.5 6.4S4.7 16.2 2.5 12z", 2.2f),
    relleno("M12 8.8a3.2 3.2 0 1 1 0 6.4 3.2 3.2 0 0 1 0-6.4z"))
val IconoOjoTachado = dibujo("ojo-tachado", trazo("M2.5 12c2.2-4.2 5.6-6.4 9.5-6.4s7.3 2.2 9.5 6.4c-2.2 4.2-5.6 6.4-9.5 6.4S4.7 16.2 2.5 12zM4 3.5l16 17", 2.2f))

/** El campo de la contraseña con el ojito para verla. */
@Composable
fun CampoContrasena(valor: String, cambiar: (String) -> Unit, etiqueta: String, modifier: Modifier = Modifier,
    ayuda: String? = null, alListo: (() -> Unit)? = null) {
    var ver by rememberSaveable { mutableStateOf(false) }
    val f = FogonTema.actual
    OutlinedTextField(valor, { cambiar(it.take(200)) }, modifier.fillMaxWidth(), label = { Text(etiqueta) }, singleLine = true, shape = forma(18.dp),
        visualTransformation = if (ver) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (alListo != null) ImeAction.Done else ImeAction.Default),
        keyboardActions = KeyboardActions(onDone = { alListo?.invoke() }),
        trailingIcon = {
            BotonIconoSocial(if (ver) IconoOjoTachado else IconoOjo,
                stringResource(if (ver) R.string.acceso_ocultar_clave else R.string.acceso_ver_clave), tinta = f.textoSuave) { ver = !ver }
        },
        supportingText = ayuda?.let { { Text(it) } })
}

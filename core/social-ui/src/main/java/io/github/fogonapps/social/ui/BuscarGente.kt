package io.github.fogonapps.social.ui

import io.github.fogonapps.identidad.PistaSocial as Pista
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.fogonapps.identidad.*
import io.github.fogonapps.social.PersonaSocial
import kotlinx.coroutines.delay
import java.io.File

/** La lupa (la misma de El Cajón). */
val IconoBuscar = dibujo("buscar", trazo("M10.6 4.4a6.2 6.2 0 1 1 0 12.4 6.2 6.2 0 0 1 0-12.4zM15.2 15.2l4.6 4.6", 2.8f))

/**
 * Buscar personas en la red (X-07) para las apps sin buscador propio: por nombre o @usuario, con las búsquedas
 * recientes de este perfil del teléfono ([recientes], solo en el teléfono). Tocar una persona abre su perfil.
 */
@Composable
fun BuscarGente(vm: InterfazSocial, recientes: File, volver: () -> Unit) {
    val f = FogonTema.actual
    val guardadas = remember(recientes) { BusquedasRecientes(recientes) }
    val lista by guardadas.lista.collectAsState()
    var texto by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var resultados by remember { mutableStateOf<List<PersonaSocial>?>(null) }
    var error by remember { mutableStateOf(false) }
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
    // Busca medio segundo después de dejar de escribir.
    LaunchedEffect(texto) {
        val q = texto.trim().removePrefix("@")
        if (q.length < 2) { resultados = null; error = false; return@LaunchedEffect }
        delay(400)
        val r = runCatching { vm.red.buscarPersonas(q) }
        error = r.isFailure
        resultados = r.getOrNull()
    }
    DisposableEffect(Unit) { onDispose { if (!resultados.isNullOrEmpty()) guardadas.anotar(texto) } }
    Cabecera(stringResource(R.string.gente_buscar), volver)
    OutlinedTextField(texto, { texto = it.take(60) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(foco),
        singleLine = true, shape = forma(18.dp), placeholder = { Text(stringResource(R.string.gente_pista)) },
        leadingIcon = { IconoRiso(IconoBuscar, null, tamano = 20.dp, tinta = f.textoSuave) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { guardadas.anotar(texto) }))
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        val r = resultados
        when {
            texto.trim().removePrefix("@").length < 2 -> if (lista.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.gente_recientes), style = MaterialTheme.typography.titleMedium, color = f.texto, modifier = Modifier.weight(1f))
                    TextButton({ guardadas.borrarTodas() }) { Text(stringResource(R.string.gente_borrar), color = f.textoSuave) }
                }
                lista.forEach { t ->
                    Row(Modifier.fillMaxWidth().clickable { texto = t }.padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconoRiso(IconoBuscar, null, tamano = 18.dp, tinta = f.textoSuave)
                        Text(t, color = f.texto, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                        BotonIconoSocial(io.github.fogonapps.identidad.IconosSociales.Cerrar, stringResource(R.string.gente_quitar, t), tinta = f.textoSuave) { guardadas.quitar(t) }
                    }
                }
            } else Pista(stringResource(R.string.gente_vacio), Modifier.padding(16.dp))
            error -> Pista(stringResource(R.string.gente_error), Modifier.padding(16.dp))
            r == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.tinta1, trackColor = f.linea)
            r.isEmpty() -> Pista(stringResource(R.string.gente_sin_resultados), Modifier.padding(16.dp))
            else -> r.forEach { p -> FilaPersona(p, { guardadas.anotar(texto); vm.navegacion.perfil(p.usuario) }) }
        }
    }
}

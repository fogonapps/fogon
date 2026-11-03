package io.github.fogonapps.cajon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.Logica
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FormaPildora

/**
 * Etiquetas como píldoras, igual para géneros y etiquetas propias, en obras y artistas. Tocar una llama a [onTocar]
 * (buscarla); con "Editar" se quita con un toque y se agrega escribiendo, con sugerencias de las que ya hay en el
 * cajón ([sugerencias]). Nada de escribir con comas. [clave] distingue el estado de cada editor en la pantalla.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorEtiquetas(
    clave: String,
    titulo: String,
    etiquetas: List<String>,
    sugerencias: () -> List<String>,
    onCambio: (List<String>) -> Unit,
    onTocar: ((String) -> Unit)?,
    textoAgregar: String,
    modifier: Modifier = Modifier,
) {
    var editando by rememberSaveable(clave) { mutableStateOf(false) }
    if (etiquetas.isEmpty() && !editando) {
        Box(modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { Pildora(textoAgregar, Iconos.Mas) { editando = true } }
        return
    }
    Column(modifier) {
        Seccion(titulo) { Pildora(stringResource(if (editando) R.string.listo else R.string.editar)) { editando = !editando } }
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            etiquetas.forEach { g ->
                if (editando) Pildora(g, Iconos.Cerrar) { onCambio(etiquetas - g) }
                else Pildora(g, onClick = { onTocar?.invoke(g) })
            }
        }
        if (editando) {
            var nuevo by rememberSaveable(clave + "n") { mutableStateOf("") }
            fun agregar(t: String) { if (t.isNotBlank()) { onCambio(etiquetas + t.trim()); nuevo = "" } }
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(nuevo, { nuevo = it.take(30) }, Modifier.weight(1f), singleLine = true, shape = FormaPildora,
                    placeholder = { Text(textoAgregar) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { agregar(nuevo) }))
                BotonSecundario(stringResource(R.string.agregar), { agregar(nuevo) })
            }
            // Sugerencias: las que empiezan (o contienen) lo escrito; sin escribir, las más usadas. Sin las que ya tiene.
            val ya = etiquetas.map { Logica.tituloLimpio(it) }.toSet()
            val t = Logica.tituloLimpio(nuevo)
            val lista = sugerencias().filter { Logica.tituloLimpio(it) !in ya && (t.isEmpty() || t in Logica.tituloLimpio(it)) }.take(12)
            if (lista.isNotEmpty()) FlowRow(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lista.forEach { s -> Pildora(s, Iconos.Mas) { agregar(s) } }
            }
        }
    }
}

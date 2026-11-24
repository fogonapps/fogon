package io.github.fogonapps.identidad

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

private val flechaArriba = dibujo("arriba", trazo("M5.4 14.8 12 8.2l6.6 6.6", 3.4f))

/** Encabezado de sección, como una frase. */
@Composable
fun SeccionAjustes(titulo: String, modifier: Modifier = Modifier, derecha: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(titulo, style = MaterialTheme.typography.titleLarge, color = FogonTema.actual.texto,
            modifier = Modifier.weight(1f).semantics { heading() })
        derecha()
    }
}

/** Una fila de Ajustes que abre otra vista. */
@Composable
fun FilaAjuste(icono: Dibujo, titulo: String, pista: String?, onClick: () -> Unit) {
    ContenidoFilaAjuste(icono, titulo, pista, Modifier.clickable(onClick = onClick)) {
        IconoRiso(IconosFogon.Siguiente, null, tamano = 18.dp, tinta = FogonTema.actual.textoSuave)
    }
}

/** Una fila de Ajustes que cambia un valor directamente. */
@Composable
fun FilaAjuste(icono: Dibujo, titulo: String, valor: Boolean, onCambio: (Boolean) -> Unit) {
    FilaAjuste(icono, titulo, null, valor, onCambio)
}

@Composable
fun FilaAjuste(icono: Dibujo, titulo: String, pista: String?, valor: Boolean, onCambio: (Boolean) -> Unit) {
    ContenidoFilaAjuste(
        icono, titulo, pista,
        Modifier.toggleable(valor, role = Role.Switch, onValueChange = onCambio),
        maxLineasPista = Int.MAX_VALUE,
    ) {
        Switch(valor, onCheckedChange = null)
    }
}

@Composable
private fun ContenidoFilaAjuste(
    icono: Dibujo,
    titulo: String,
    pista: String?,
    interaccion: Modifier,
    maxLineasPista: Int = 2,
    aLaDerecha: @Composable () -> Unit,
) {
    val f = FogonTema.actual
    Row(Modifier.fillMaxWidth().clip(forma(16.dp)).then(interaccion).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        IconoRiso(icono, null, tamano = 22.dp, tinta = f.texto)
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge, color = f.texto)
            pista?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = maxLineasPista) }
        }
        aLaDerecha()
    }
}

/** Un grupo de ajustes que se abre y se cierra en el mismo lugar. */
@Composable
fun Desplegable(icono: io.github.fogonapps.identidad.Dibujo, titulo: String, pista: String?, contenido: @Composable () -> Unit) {
    val f = FogonTema.actual
    var abierto by rememberSaveable(titulo) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clip(io.github.fogonapps.identidad.forma(16.dp)).clickable { abierto = !abierto }.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        IconoRiso(icono, null, tamano = 22.dp, tinta = f.texto)
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge, color = f.texto)
            // Cerrado, la pista va en dos líneas; abierto, entera.
            pista?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = if (abierto) Int.MAX_VALUE else 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
        }
        IconoRiso(if (abierto) flechaArriba else IconosFogon.Abajo, null, tamano = 18.dp, tinta = f.textoSuave)
    }
    androidx.compose.animation.AnimatedVisibility(abierto) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { contenido() }
    }
}


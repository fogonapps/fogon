package io.github.fogonapps.identidad

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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

/** Una fila de los ajustes: ícono, título, una pista y la flecha. */
@Composable
fun FilaAjuste(icono: io.github.fogonapps.identidad.Dibujo, titulo: String, pista: String?, onClick: () -> Unit) {
    val f = FogonTema.actual
    Row(Modifier.fillMaxWidth().clip(io.github.fogonapps.identidad.forma(16.dp)).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        IconoRiso(icono, null, tamano = 22.dp, tinta = f.texto)
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge, color = f.texto)
            pista?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 2) }
        }
        IconoRiso(IconosFogon.Siguiente, null, tamano = 18.dp, tinta = f.textoSuave)
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
            pista?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 2) }
        }
        IconoRiso(if (abierto) flechaArriba else IconosFogon.Abajo, null, tamano = 18.dp, tinta = f.textoSuave)
    }
    androidx.compose.animation.AnimatedVisibility(abierto) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { contenido() }
    }
}


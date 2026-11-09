package io.github.fogonapps.identidad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

/** Botón redondo de la cabecera, como el ⚙ de El Cajón. */
@Composable
fun BotonCirculoPerfil(icono: io.github.fogonapps.identidad.Dibujo, descripcion: String, onClick: () -> Unit) {
    val f = FogonTema.actual
    Box(Modifier.size(46.dp).clip(CircleShape).background(f.superficie).border(f.borde, f.linea, CircleShape).clickable(onClick = onClick)
        .semantics { contentDescription = descripcion; role = Role.Button }, contentAlignment = Alignment.Center) {
        IconoRiso(icono, null, tamano = 22.dp, tinta = f.texto)
    }
}

package io.github.fogonapps.social.ui
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.Dibujo
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.PistaSocial
import io.github.fogonapps.identidad.Trama

@Composable
internal fun Cabecera(titulo: String, volver: () -> Unit, derecha: @Composable () -> Unit = {}) =
    io.github.fogonapps.identidad.CabeceraSocial(titulo, volver, stringResource(R.string.social_volver), derecha = derecha)

/**
 * Una lista vacía. Con el diseño nuevo (trama apagada): un ícono, una frase en la letra de los títulos, lo que falta y,
 * si hay, un botón. Sin él, la pista de siempre con [texto].
 */
@Composable
fun EstadoVacioSocial(icono: Dibujo, titulo: String, texto: String, boton: String? = null, accion: (() -> Unit)? = null) {
    if (!Trama.apagada) {
        PistaSocial(texto, Modifier.padding(16.dp))
        if (boton != null && accion != null) BotonRiso(boton, accion, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        return
    }
    val f = FogonTema.actual
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        IconoRiso(icono, null, tamano = 40.dp, tinta = f.texto)
        Text(titulo, fontFamily = f.titulos, fontSize = 22.sp, lineHeight = 28.sp, color = f.texto, textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() })
        Text(texto, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp), color = f.textoSuave, textAlign = TextAlign.Center)
        if (boton != null && accion != null) BotonRiso(boton, accion, Modifier.padding(top = 8.dp))
    }
}

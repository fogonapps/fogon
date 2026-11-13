package io.github.fogonapps.perfil

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.forma

/**
 * Una cifra de la cabecera de un perfil (seguidores, seguidos, lo del cajón…), repartida a lo ancho de la fila.
 * Sin [onClick], no se puede tocar.
 */
@Composable
fun RowScope.CifraPerfil(valor: String, etiqueta: String, onClick: (() -> Unit)? = null) {
    val f = FogonTema.actual
    Column(Modifier.weight(1f).clip(forma(14.dp)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valor, fontFamily = f.cifras, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = f.texto)
        Text(etiqueta, style = MaterialTheme.typography.bodySmall, color = f.textoSuave, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/**
 * El saludo de Fogón: el nombre grande y debajo la frase, igual en todas las apps. [onMantener] abre lo que cada app
 * ofrece sobre la frase (me gusta, otra, ocultarla); [adorno] va al lado de la frase (por ejemplo, un corazón).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FraseFogon(nombre: String, frase: String, modifier: Modifier = Modifier, onMantener: () -> Unit = {}, adorno: @Composable () -> Unit = {}) {
    val f = FogonTema.actual
    Column(modifier) {
        Text(nombre, fontFamily = f.titulos, fontSize = 30.sp, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(frase, style = MaterialTheme.typography.bodyMedium, color = f.textoSuave, maxLines = 2,
                modifier = Modifier.weight(1f, fill = false).clip(forma(8.dp)).combinedClickable(onClick = {}, onLongClick = onMantener).heightIn(min = 32.dp).wrapContentHeight(Alignment.CenterVertically))
            adorno()
        }
    }
}

/**
 * La cabecera del inicio, igual en todas las apps (X-07): el nombre (o «¡Hola!») en grande con la frase debajo; a la
 * derecha las acciones de la app y, siempre la última, el perfil ([perfil], normalmente `BotonPerfilMascota`).
 * Lo propio de cada app (fecha, semana, día) no va aquí: va en su contenido.
 */
@Composable
fun CabeceraInicio(
    nombre: String, frase: String, perfil: @Composable () -> Unit, modifier: Modifier = Modifier,
    onMantener: () -> Unit = {}, adorno: @Composable () -> Unit = {}, acciones: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FraseFogon(nombre, frase, Modifier.weight(1f), onMantener, adorno)
        acciones()
        perfil()
    }
}

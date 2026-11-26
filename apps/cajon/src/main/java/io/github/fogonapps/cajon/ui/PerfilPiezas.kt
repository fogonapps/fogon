package io.github.fogonapps.cajon.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fogonapps.cajon.R
import io.github.fogonapps.identidad.Dibujo
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.FormaPildora
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.forma
import io.github.fogonapps.identidad.rebote

/*
 * Las piezas del perfil, iguales en el tuyo y en el de otra persona: títulos de sección, los huecos punteados de
 * las favoritas, el reto, las reseñas y las cifras. Todo
 * sobre el papel, en la tinta del texto: el color lo ponen las portadas.
 */

/** El gris apenas más hondo que el papel: lo que todavía no tiene imagen. */
@Composable
@ReadOnlyComposable
internal fun tonoPapel(): Color {
    val f = FogonTema.actual
    return lerp(f.fondo, f.texto, if (f.oscuro) 0.08f else 0.05f)
}

/** Un borde punteado y fino, para los huecos que esperan algo. */
internal fun Modifier.bordePunteado(color: Color, esquina: Dp): Modifier = drawBehind {
    val ancho = 1.2.dp.toPx()
    drawRoundRect(color, cornerRadius = CornerRadius(esquina.toPx()),
        style = Stroke(ancho, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
}

/** El título de una sección del perfil, en la letra de los títulos; [derecha] para un «Ver todos» o un lápiz. */
@Composable
internal fun TituloPerfil(texto: String, modifier: Modifier = Modifier, derecha: @Composable RowScope.() -> Unit = {}) {
    val f = FogonTema.actual
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(texto, fontFamily = f.titulos, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 26.sp, color = f.texto,
            modifier = Modifier.weight(1f).semantics { heading() })
        derecha()
    }
}

/** Un hueco que espera una obra: el marco de póster punteado, el ícono de su tipo y su nombre. */
@Composable
internal fun HuecoVacio(icono: Dibujo, nombre: String?, modifier: Modifier = Modifier, esquina: Dp = 12.dp) {
    val f = FogonTema.actual
    Box(modifier.aspectRatio(2f / 3f).clip(forma(esquina)).bordePunteado(f.textoSuave.copy(alpha = 0.6f), esquina), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            IconoRiso(icono, null, tamano = 24.dp, tinta = f.textoSuave)
            if (nombre != null) Text(nombre, style = MaterialTheme.typography.labelSmall, color = f.textoSuave, textAlign = TextAlign.Center,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
            IconoRiso(Iconos.Mas, null, tamano = 14.dp, tinta = f.textoSuave)
        }
    }
}

/** Una favorita lista para el perfil: de qué hueco es, su título, su imagen y qué hace al tocarla. */
internal class Favorita(
    val hueco: String, val titulo: String, val onClick: () -> Unit, val onLongClick: (() -> Unit)? = null,
    val imagen: @Composable (Modifier) -> Unit,
)

/**
 * Las favoritas, protagonistas del perfil (como las cuatro de Letterboxd): cuatro portadas grandes en una fila,
 * con su título y de qué son. Si hay más de cuatro, «Ver todos» las muestra todas. Sin ninguna, nada (o, en el tuyo,
 * una sola invitación discreta con [invitar]).
 */
@Composable
internal fun FavoritasDelPerfil(favoritas: List<Favorita>, invitar: (() -> Unit)? = null) {
    val f = FogonTema.actual
    if (favoritas.isEmpty()) {
        if (invitar == null) return
        // Una sola invitación: cuatro huecos punteados y una frase; tocar lleva a elegirlas.
        val titulo = stringResource(R.string.perfil_elegir_favoritas)
        Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = titulo, onClick = invitar)
            .semantics(mergeDescendants = true) {}.padding(vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(Iconos.Pelicula, Iconos.Serie, Iconos.Disco, Iconos.Libro).forEach { HuecoVacio(it, null, Modifier.weight(1f), esquina = 10.dp) }
            }
            Text(titulo, fontFamily = f.titulos, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = f.texto,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp))
            Text(stringResource(R.string.perfil_elegir_favoritas_pista), style = MaterialTheme.typography.bodySmall, color = f.textoSuave,
                modifier = Modifier.padding(horizontal = 20.dp))
        }
        return
    }
    var todas by rememberSaveable { mutableStateOf(false) }
    TituloPerfil(stringResource(R.string.perfil_favoritas)) {
        if (favoritas.size > 4) Text(stringResource(if (todas) R.string.lotuyo_ver_menos else R.string.lotuyo_ver_todos, favoritas.size),
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = f.texto,
            modifier = Modifier.clip(forma(8.dp)).clickable(role = Role.Button) { todas = !todas }.heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 14.dp))
    }
    (if (todas) favoritas else favoritas.take(4)).chunked(4).forEach { fila ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            fila.forEach { fav -> CeldaFavorita(fav, Modifier.weight(1f)) }
            repeat(4 - fila.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CeldaFavorita(fav: Favorita, modifier: Modifier) {
    val f = FogonTema.actual
    val cambiar = stringResource(R.string.perfil_cambiar)
    Column(modifier.clip(forma(10.dp)).combinedClickable(onClick = fav.onClick, onLongClick = fav.onLongClick,
        onLongClickLabel = if (fav.onLongClick != null) cambiar else null)
        .semantics(mergeDescendants = true) { contentDescription = "${fav.hueco}: ${fav.titulo}"; role = Role.Button },
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        fav.imagen(Modifier.fillMaxWidth())
        Text(fav.titulo, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = f.texto, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Text(fav.hueco, style = MaterialTheme.typography.labelSmall, color = f.textoSuave, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** La foto de una persona favorita en el mismo marco de póster que las obras: redonda, al centro. */
@Composable
internal fun FotoEnMarco(foto: String, nombre: String, tipo: String, modifier: Modifier) {
    BoxWithConstraints(modifier.aspectRatio(2f / 3f).clip(forma(10.dp)).background(tonoPapel()), contentAlignment = Alignment.Center) {
        FotoArtista(foto, nombre, maxWidth * 0.86f, tipo)
    }
}

/**
 * «La que me marcó» o «Mi descubrimiento», como una lámina: la portada a un lado, el nombre del hueco, el título
 * grande y sus palabras entre comillas.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DestacadoConPalabras(
    hueco: String, titulo: String, porQue: String?, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, portada: @Composable (Modifier) -> Unit,
) {
    val f = FogonTema.actual
    val cambiar = stringResource(R.string.perfil_cambiar)
    Row(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = if (onLongClick != null) cambiar else null)
        .semantics(mergeDescendants = true) {}.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        portada(Modifier.width(88.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(hueco, style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
            Text(titulo, fontFamily = f.titulos, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 25.sp, color = f.texto,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (!porQue.isNullOrBlank()) Text("“${porQue.trim()}”", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic,
                color = f.texto, maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** El reto del año: cada tipo con su cuenta y una barra fina en la tinta del texto. [onEditar] solo en el tuyo. */
@Composable
internal fun RetoDelPerfil(titulo: String, metas: List<Triple<String, Int, Int>>, onEditar: (() -> Unit)?) {
    val f = FogonTema.actual
    if (metas.isEmpty()) return
    val cumplido = metas.all { (_, lleva, meta) -> lleva >= meta }
    TituloPerfil(titulo) {
        if (onEditar != null) BotonIcono(Iconos.Lapiz, stringResource(R.string.editar), onClick = onEditar)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (cumplido) Text(stringResource(R.string.reto_cumplido), fontFamily = f.titulos, fontSize = 18.sp, color = f.texto)
        metas.forEach { (t, lleva, meta) ->
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconoRiso(Iconos.de(t), null, tamano = 16.dp, tinta = f.textoSuave)
                    Text(nombreTipo(t), style = MaterialTheme.typography.bodyMedium, color = f.texto, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    Text("$lleva / $meta", fontFamily = f.titulos, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = f.texto)
                }
                val avance = (lleva.toFloat() / meta.coerceAtLeast(1)).coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth().height(4.dp).clip(FormaPildora).background(tonoPapel())) {
                    Box(Modifier.fillMaxWidth(io.github.fogonapps.identidad.progresoAnimado(avance)).fillMaxHeight().clip(FormaPildora).background(f.texto))
                }
            }
        }
    }
}

/**
 * Una reseña en el perfil: la portada, el título en la letra de los títulos, las estrellas y un pedazo del texto (si
 * tiene spoilers, tapado hasta que se toque). Sin caja: una raya fina la separa de la siguiente.
 */
@Composable
internal fun FilaResenaPerfil(
    titulo: String, parte: String, nota: Int?, texto: String, abajo: String, spoiler: Boolean, destapada: Boolean,
    onDestapar: () -> Unit, onClick: () -> Unit, portada: @Composable (Modifier) -> Unit,
) {
    val f = FogonTema.actual
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        portada(Modifier.width(72.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(titulo, fontFamily = f.titulos, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp, color = f.texto,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (parte.isNotBlank()) Text(parte, style = MaterialTheme.typography.labelMedium, color = f.textoSuave, maxLines = 1, overflow = TextOverflow.Ellipsis)
            nota?.let { NotaResena(it) }
            if (spoiler && !destapada) Text(stringResource(R.string.red_spoiler_tocar), style = MaterialTheme.typography.labelLarge, color = f.texto,
                modifier = Modifier.clip(forma(8.dp)).clickable(role = Role.Button, onClick = onDestapar).heightIn(min = 48.dp).padding(vertical = 14.dp))
            else if (texto.isNotBlank()) Text(texto, style = MaterialTheme.typography.bodyMedium, color = f.texto, maxLines = 4, overflow = TextOverflow.Ellipsis)
            if (abajo.isNotBlank()) Text(abajo, style = MaterialTheme.typography.labelSmall, color = f.textoSuave)
        }
    }
}

/** Las cifras del perfil en dos columnas: el número grande en la letra de los títulos y su nombre debajo, sin caja. */
@Composable
internal fun CifrasEnRejilla(cifras: List<Pair<String, String>>, onClick: (() -> Unit)? = null) {
    val f = FogonTema.actual
    cifras.chunked(2).forEach { fila ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            fila.forEach { (valor, nombre) ->
                Column(Modifier.weight(1f).clip(forma(12.dp)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                    .semantics(mergeDescendants = true) {}.padding(horizontal = 8.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(valor, fontFamily = f.titulos, fontWeight = FontWeight.Black, fontSize = 32.sp, lineHeight = 36.sp, color = f.texto, maxLines = 1)
                    Text(nombre, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = f.textoSuave)
                }
            }
            if (fila.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** Una opción que se elige: llena en la tinta del texto si está elegida, con borde fino si no. */
@Composable
internal fun ChipPerfil(texto: String, elegido: Boolean, modifier: Modifier = Modifier, icono: Dibujo? = null, onClick: () -> Unit) {
    val f = FogonTema.actual
    Row(modifier.rebote().heightIn(min = 40.dp).clip(FormaPildora)
        .then(if (elegido) Modifier.background(f.texto) else Modifier.border(1.dp, f.linea, FormaPildora))
        .clickable(role = Role.RadioButton, onClick = onClick).semantics { selected = elegido }
        .padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (icono != null) IconoRiso(icono, null, tamano = 16.dp, tinta = if (elegido) f.fondo else f.texto)
        Text(texto, style = MaterialTheme.typography.labelLarge, color = if (elegido) f.fondo else f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Un enlace del perfil, sin caja: de una red conocida, su logo y el usuario («@marina»); de cualquier otra, el ícono
 * de enlace y la dirección corta, para que se vea a dónde lleva.
 */
@Composable
internal fun EnlacePerfil(url: String, onClick: () -> Unit) {
    val f = FogonTema.actual
    val marca = Marcas.de(url)
    val texto = Marcas.texto(url) ?: io.github.fogonapps.cajon.datos.Logica.enlaceCorto(url)
    Row(Modifier.clip(forma(8.dp)).clickable(role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) { if (marca != null) contentDescription = "${marca.nombre}: $texto" }
        .heightIn(min = 48.dp).padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (marca != null) LogoMarca(marca, 18.dp, f.texto) else IconoRiso(Iconos.Enlace, null, tamano = 16.dp, tinta = f.texto)
        Text(texto, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** El logo de una red, relleno y en un solo color (sin el contorno de los íconos de la app, que lo emborronaría). */
@Composable
internal fun LogoMarca(marca: Marca, tamano: androidx.compose.ui.unit.Dp, tinta: Color) {
    val camino = androidx.compose.runtime.remember(marca.clave) { androidx.compose.ui.graphics.vector.PathParser().parsePathString(marca.trazo).toPath() }
    androidx.compose.foundation.Canvas(Modifier.size(tamano)) {
        val escala = size.minDimension / 24f
        scale(escala, escala, pivot = androidx.compose.ui.geometry.Offset.Zero) { drawPath(camino, tinta) }
    }
}

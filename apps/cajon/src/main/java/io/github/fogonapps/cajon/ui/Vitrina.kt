package io.github.fogonapps.cajon.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Logica
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.forma

/** Lo que se puede poner en la vitrina: una obra o un artista, con lo que hace falta para dibujarlo. */
private class Candidata(val id: String, val titulo: String, val esArtista: Boolean, val imagen: @Composable (Modifier) -> Unit)

private const val TODO = "todo"
private const val ARTISTAS = "artistas"

/**
 * Las favoritas del perfil: arriba, las que ya salen (en orden; tocar una la quita, mantenerla presionada la mueve
 * a la izquierda); abajo, todo el cajón para sumar más, primero lo que ya marcaste con la estrella. Ser favorita no
 * la pone en el perfil, y ponerla en el perfil la deja como favorita (sin pasos previos).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditarVitrina(vm: CajonViewModel, e: Estado) {
    val f = FogonTema.actual
    val elegidas = Logica.vitrina(e)
    var filtro by rememberSaveable { mutableStateOf(TODO) }
    var texto by rememberSaveable { mutableStateOf("") }
    fun candidata(id: String): Candidata? = e.vivas.firstOrNull { it.id == id }?.let { o ->
        Candidata(o.id, o.titulo, false) { m -> Portada(o.portada, o.tipo, o.titulo, m, esquina = 10.dp, conTitulo = false) }
    } ?: e.artistasVivos.firstOrNull { it.id == id }?.let { a ->
        Candidata(a.id, a.nombre, true) { m -> FotoEnMarco(a.foto, a.nombre, a.tipo, m) }
    }

    Cabecera(stringResource(R.string.vitrina_titulo), vm::cerrar)
    Pista(stringResource(R.string.vitrina_pista, Logica.MAX_VITRINA), Modifier.padding(horizontal = 16.dp))

    Seccion(stringResource(R.string.vitrina_elegidas, elegidas.size, Logica.MAX_VITRINA))
    if (elegidas.isEmpty()) Pista(stringResource(R.string.vitrina_vacia), Modifier.padding(horizontal = 16.dp))
    val quitar = stringResource(R.string.vitrina_quitar)
    val mover = stringResource(R.string.vitrina_mover)
    elegidas.mapNotNull(::candidata).chunked(3).forEach { fila ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            fila.forEach { c ->
                Column(Modifier.weight(1f).clip(forma(10.dp))
                    .combinedClickable(role = Role.Button, onClickLabel = quitar, onLongClickLabel = mover,
                        onLongClick = { vm.moverVitrina(c.id, -1) }) { vm.alternarVitrina(c.id) }
                    .semantics(mergeDescendants = true) { contentDescription = c.titulo },
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box {
                        c.imagen(Modifier.fillMaxWidth())
                        Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).clip(forma(12.dp)).background(f.fondo),
                            contentAlignment = Alignment.Center) { IconoRiso(Iconos.Cerrar, null, tamano = 14.dp, tinta = f.texto) }
                    }
                    Text(c.titulo, style = MaterialTheme.typography.labelMedium, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            repeat(3 - fila.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    if (elegidas.size > 1) Pista(stringResource(R.string.vitrina_como_ordenar), Modifier.padding(horizontal = 16.dp))

    Seccion(stringResource(R.string.vitrina_agregar))
    val tipos = io.github.fogonapps.cajon.Secciones.tiposVisibles(e)
    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (listOf(TODO) + tipos + ARTISTAS).forEach { t ->
            ChipPerfil(if (t == TODO) stringResource(R.string.vitrina_todo) else nombreTipo(if (t == ARTISTAS) "artista" else t), filtro == t) { filtro = t }
        }
    }
    CampoTexto(texto, { texto = it }, stringResource(R.string.buscar_en_tu_cajon), Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    val q = Logica.tituloLimpio(texto)
    val favoritas = e.ajustes.favoritas.toSet()
    // El orden se fija al cambiar el filtro o la búsqueda: si se rehiciera al tocar (poner una la vuelve favorita, y
    // las favoritas van primero), la cuadrícula saltaría bajo el dedo.
    val lista: List<Candidata> = androidx.compose.runtime.remember(filtro, q) { if (filtro == ARTISTAS) {
        e.artistasVivos.filter { q.isEmpty() || q in Logica.tituloLimpio(it.nombre) }
            .sortedWith(compareByDescending<io.github.fogonapps.cajon.datos.Artista> { it.favorito }.thenByDescending { it.nota }.thenBy { it.nombre })
            .take(60).map { a -> Candidata(a.id, a.nombre, true) { m -> FotoEnMarco(a.foto, a.nombre, a.tipo, m) } }
    } else {
        e.vivas.filter { (filtro == TODO || it.tipo == filtro) && (q.isEmpty() || q in Logica.tituloLimpio(it.titulo + " " + it.creador)) }
            .sortedWith(compareByDescending<io.github.fogonapps.cajon.datos.Obra> { it.id in favoritas }.thenByDescending { it.meGusta }
                .thenByDescending { it.nota }.thenBy { it.titulo })
            .take(60).map { o -> Candidata(o.id, o.titulo, false) { m -> Portada(o.portada, o.tipo, o.titulo, m, esquina = 10.dp, conTitulo = false) } }
    } }
    if (lista.isEmpty()) Pista(stringResource(R.string.sin_resultados), Modifier.padding(horizontal = 16.dp))
    lista.chunked(3).forEach { fila ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            fila.forEach { c ->
                val puesta = c.id in elegidas
                Column(Modifier.weight(1f).clip(forma(12.dp)).clickable(role = Role.Checkbox) { vm.alternarVitrina(c.id) }
                    .semantics(mergeDescendants = true) { selected = puesta },
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box {
                        c.imagen(Modifier.fillMaxWidth())
                        if (puesta) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp).clip(forma(14.dp)).background(f.texto),
                            contentAlignment = Alignment.Center) { IconoRiso(Iconos.Chulo, null, tamano = 16.dp, tinta = f.fondo) }
                        else if (!c.esArtista && c.id in favoritas) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp).clip(forma(14.dp))
                            .background(f.fondo), contentAlignment = Alignment.Center) { IconoRiso(Iconos.Favorita, null, tamano = 16.dp, tinta = f.texto) }
                    }
                    Text(c.titulo, style = MaterialTheme.typography.labelLarge, color = f.texto, fontWeight = if (puesta) FontWeight.Bold else null,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            repeat(3 - fila.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    Espacio(40.dp)
}

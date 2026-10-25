package io.github.fogonapps.cajon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.Pantalla
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Estados
import io.github.fogonapps.cajon.datos.Obra
import io.github.fogonapps.cajon.datos.Orden
import io.github.fogonapps.cajon.datos.Tipos
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.FormaPildora
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.aparecer
import io.github.fogonapps.identidad.forma

@Composable
fun Cajon(vm: CajonViewModel, e: Estado) {
    val f = FogonTema.actual
    var tipo by rememberSaveable { mutableStateOf<String?>(null) }
    var estado by rememberSaveable { mutableStateOf<String?>(null) }
    var texto by rememberSaveable { mutableStateOf("") }
    var orden by rememberSaveable { mutableStateOf(Orden.RECIENTES) }
    val obras = remember(e, tipo, estado, texto, orden) { vm.L.filtrar(e, tipo, estado, texto, orden) }

    Seccion(stringResource(R.string.pestana_cajon), Modifier.padding(top = 12.dp)) {
        Pildora(stringResource(R.string.tipo_artistas), Iconos.Artista) { vm.abrir(Pantalla.Artistas) }
    }

    OutlinedTextField(
        texto, { texto = it.take(40) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        placeholder = { Text(stringResource(R.string.buscar_en_cajon)) }, singleLine = true, shape = FormaPildora,
        leadingIcon = { IconoRiso(Iconos.Buscar, null, tamano = 20.dp, tinta = f.textoSuave) },
    )

    // Filtros: tipo (en una fila que se desliza) y estado.
    Chips(listOf<Pair<String?, String>>(null to stringResource(R.string.todo)) + Tipos.TODOS.map { it to nombreTipo(it) }, tipo) { tipo = it }
    Chips(listOf<Pair<String?, String>>(null to stringResource(R.string.todo)) + Estados.TODOS.map { it to nombreEstado(it, tipo) }, estado) { estado = it }

    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(pluralStringResource(R.plurals.obras_n, obras.size, obras.size), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, modifier = Modifier.weight(1f))
        val siguiente = Orden.entries[(orden.ordinal + 1) % Orden.entries.size]
        Pildora(stringResource(nombreOrden(orden)), Iconos.Ordenar) { orden = siguiente }
    }

    if (obras.isEmpty()) {
        Pista(stringResource(if (e.vivas.isEmpty()) R.string.cajon_vacio else R.string.cajon_nada), centro = true, modifier = Modifier.padding(top = 24.dp))
        return
    }
    Cuadricula(obras, vm)
}

/** Las portadas en cuadrícula de tres, con sus estrellas y el corazón. */
@Composable
fun Cuadricula(obras: List<Obra>, vm: CajonViewModel, numerada: Boolean = false) {
    val f = FogonTema.actual
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        obras.chunked(3).forEachIndexed { fila, grupo ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                grupo.forEachIndexed { k, o ->
                    val n = fila * 3 + k
                    Column(Modifier.weight(1f).aparecer(n.coerceAtMost(12)).clip(forma(12.dp)).clickable { vm.abrir(Pantalla.Ficha(o.id)) },
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Todas en el mismo marco vertical; los discos (cuadrados) se apoyan abajo, así los títulos quedan parejos.
                        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f), contentAlignment = Alignment.BottomCenter) {
                            Portada(o.portada, o.tipo, o.titulo, Modifier.fillMaxWidth(), conTitulo = false)
                            if (numerada) Text("${n + 1}", fontFamily = f.cifras, fontSize = 15.sp, color = f.sobrePildora,
                                modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(FormaPildora).background(f.pildora).padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                        Text(o.titulo, style = MaterialTheme.typography.labelMedium, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Estrellas(o.nota, 12.sp)
                            if (o.meGusta) IconoRiso(Iconos.Corazon, null, tamano = 12.dp, tinta = f.acentoTexto, relleno = f.acentoTexto)
                            if (o.nota == 0 && !o.meGusta) Text(nombreEstado(o.estado, o.tipo), style = MaterialTheme.typography.labelSmall, color = f.textoSuave, maxLines = 1)
                        }
                    }
                }
                repeat(3 - grupo.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Píldoras para filtrar, en una fila que se desliza. */
@Composable
fun <T> Chips(opciones: List<Pair<T, String>>, elegido: T, onElegir: (T) -> Unit) {
    val f = FogonTema.actual
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opciones.forEach { (v, t) ->
            FilterChip(v == elegido, { onElegir(v) }, { Text(t) }, shape = FormaPildora,
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = f.paleta.tinta, selectedLabelColor = f.paleta.sobreTinta))
        }
    }
}

fun nombreOrden(o: Orden) = when (o) {
    Orden.RECIENTES -> R.string.orden_recientes
    Orden.NOTA -> R.string.orden_nota
    Orden.ANIO -> R.string.orden_anio
    Orden.TITULO -> R.string.orden_titulo
}

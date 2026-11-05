package io.github.fogonapps.cajon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Iniciales
import io.github.fogonapps.cajon.datos.ObraInicial
import io.github.fogonapps.cajon.datos.Senales
import io.github.fogonapps.cajon.datos.Tipos
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.FormaPildora
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.forma
import io.github.fogonapps.identidad.haloPuntos
import kotlinx.coroutines.launch

/** Cuántas salen en cada fila al empezar (y al recargar). */
private const val POR_FILA = 8

/**
 * Elegir gustos con obras y artistas concretos: una fila por tipo. Tocar una dice que te gusta y, a su lado, aparecen
 * parecidas (la fila se corre a la derecha); tocarla otra vez lo quita. Cada fila se puede recargar.
 */
@Composable
fun FilasDeGustos(vm: CajonViewModel, e: Estado) {
    listOf(
        Tipos.PELICULA to R.string.seccion_peliculas, Tipos.SERIE to R.string.seccion_series, Tipos.ANIME to R.string.seccion_animes,
        Tipos.LIBRO to R.string.seccion_libros, CajonViewModel.TIPO_ARTISTA to R.string.artistas,
    ).forEach { (tipo, nombre) -> FilaDeGustos(vm, e, tipo, stringResource(nombre)) }
}

/** Cuántas de la lista para empezar dijo que le gustan. */
fun elegidasIniciales(vm: CajonViewModel, e: Estado): Int {
    val dichos = e.gustosVivos.filter { it.senal == Senales.GUSTA }.map { it.clave }.toSet()
    return vm.iniciales.count { vm.claveInicial(it) in dichos }
}

@Composable
private fun FilaDeGustos(vm: CajonViewModel, e: Estado, tipo: String, titulo: String) {
    val f = FogonTema.actual
    val todas = remember(tipo) { vm.iniciales.filter { it.tipo == tipo } }
    if (todas.isEmpty()) return
    val porId = remember(todas) { todas.associateBy { it.id } }
    val dichos = e.gustosVivos.filter { it.senal == Senales.GUSTA }.map { it.clave }.toSet()
    val elegidas = todas.filter { vm.claveInicial(it) in dichos }.map { it.id }.toSet()
    var mostradas by rememberSaveable(tipo) { mutableStateOf(Iniciales.semilla(todas, POR_FILA, kotlin.random.Random(System.nanoTime()))) }
    val imagenes by vm.imagenesIniciales.collectAsStateWithLifecycle()
    LaunchedEffect(mostradas) { vm.cargarImagenesIniciales(mostradas) }
    val estado = rememberLazyListState()
    val alcance = rememberCoroutineScope()

    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(titulo, style = MaterialTheme.typography.titleMedium, color = f.texto, modifier = Modifier.weight(1f))
        BotonIcono(Iconos.Repetir, stringResource(R.string.gustos_recargar)) {
            mostradas = Iniciales.recargar(mostradas, elegidas, todas, POR_FILA, kotlin.random.Random(System.nanoTime()))
            alcance.launch { estado.animateScrollToItem(0) }
        }
    }
    LazyRow(state = estado, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(mostradas, key = { it }) { id ->
            val o = porId[id] ?: return@items
            val si = id in elegidas
            TarjetaInicial(o, o.imagen.ifBlank { imagenes[id].orEmpty() }, si, Modifier.animateItem()) {
                if (si) vm.gustaInicial(o, false)
                else {
                    vm.gustaInicial(o, true)
                    // La fila no salta: la elegida se queda donde está y las parecidas entran a su derecha.
                    mostradas = Iniciales.alElegir(mostradas, id, porId)
                }
            }
        }
    }
}

/** Una obra o artista para elegir: su portada (o, sin portada, una tarjeta con el título en la trama de puntos). */
@Composable
private fun TarjetaInicial(o: ObraInicial, imagen: String, elegida: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val f = FogonTema.actual
    val artista = o.tipo == CajonViewModel.TIPO_ARTISTA
    Column(modifier.width(if (artista) 100.dp else 112.dp).clip(forma(12.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box {
            if (artista) FotoArtista(imagen, o.titulo, 92.dp)
            else Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(forma(12.dp)).background(f.superficie)
                .then(if (elegida) Modifier.border(3.dp, f.tinta1, forma(12.dp)) else Modifier.border(f.borde, f.linea, forma(12.dp))),
                contentAlignment = Alignment.Center) {
                if (imagen.isNotBlank()) coil3.compose.AsyncImage(imagen, o.titulo, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Box(Modifier.fillMaxSize().haloPuntos(f.tinta2.copy(alpha = 0.35f)).padding(10.dp), contentAlignment = Alignment.Center) {
                    Text(o.titulo, style = MaterialTheme.typography.titleSmall, color = f.texto, textAlign = TextAlign.Center, maxLines = 5, overflow = TextOverflow.Ellipsis)
                }
            }
            if (elegida) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp).clip(FormaPildora).background(f.tinta1), contentAlignment = Alignment.Center) {
                IconoRiso(Iconos.Chulo, null, tamano = 16.dp, tinta = f.sobreTinta1)
            }
        }
        // Sin portada, el título ya va en la tarjeta: abajo no se repite.
        if (artista || imagen.isNotBlank()) Text(o.titulo, style = MaterialTheme.typography.labelLarge, color = if (elegida) f.acentoTexto else f.texto, maxLines = 2,
            overflow = TextOverflow.Ellipsis, textAlign = if (artista) TextAlign.Center else TextAlign.Start, modifier = Modifier.fillMaxWidth())
        o.anio?.let { Text("$it", style = MaterialTheme.typography.labelSmall, color = f.textoSuave, modifier = Modifier.fillMaxWidth()) }
    }
}

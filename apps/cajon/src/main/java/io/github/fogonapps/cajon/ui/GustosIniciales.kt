package io.github.fogonapps.cajon.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import io.github.fogonapps.cajon.datos.Catalogo
import io.github.fogonapps.cajon.datos.SeccionCatalogo
import io.github.fogonapps.cajon.datos.ObraInicial
import io.github.fogonapps.cajon.datos.Senales
import io.github.fogonapps.cajon.datos.Tipos
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.FormaPildora
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.forma
import io.github.fogonapps.identidad.haloPuntos
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material3.CircularProgressIndicator

/**
 * Elegir gustos con obras y artistas concretos: una fila por tipo. Tocar una dice que te gusta y, a su lado, aparecen
 * parecidas (la fila se corre a la derecha); tocarla otra vez lo quita. Cada fila tiene secciones y carga progresiva.
 */
@Composable
fun FilasDeGustos(vm: CajonViewModel, e: Estado) {
    listOf(
        Tipos.PELICULA to R.string.seccion_peliculas, Tipos.SERIE to R.string.seccion_series, Tipos.ANIME to R.string.seccion_animes,
        Tipos.LIBRO to R.string.seccion_libros, Tipos.ALBUM to R.string.tipo_albumes, CajonViewModel.TIPO_ARTISTA to R.string.artistas,
    ).forEach { (tipo, nombre) -> FilaDeGustos(vm, e, tipo, stringResource(nombre)) }
}

/** Cuántas de la lista para empezar dijo que le gustan. */
fun elegidasIniciales(e: Estado): Int = Catalogo.cantidadElegida(e)

@Composable
private fun FilaDeGustos(vm: CajonViewModel, e: Estado, tipo: String, titulo: String) {
    val f = FogonTema.actual
    val idioma = vm.idioma()
    var seccion by rememberSaveable(tipo) { mutableStateOf(SeccionCatalogo.ICONICAS) }
    var mostradas by remember(tipo, idioma) { mutableStateOf<List<ObraInicial>>(emptyList()) }
    var cargando by remember { mutableStateOf(false) }
    var sinInternet by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val elegidas = e.gustosVivos.filter { it.senal == Senales.GUSTA }.map { it.fuente }.toSet()
    val imagenes by vm.imagenesIniciales.collectAsStateWithLifecycle()
    val estado = rememberLazyListState()
    val alcance = rememberCoroutineScope()

    LaunchedEffect(seccion, idioma) {
        mostradas = emptyList()
        error = false
        var pagina by mutableIntStateOf(1)
        var fin = false
        estado.scrollToItem(0)
        suspend fun cargar() {
            cargando = true
            try {
                val (resultado, respaldo) = vm.paginaCatalogo(seccion, tipo, idioma, pagina)
                if (respaldo) {
                    sinInternet = true
                    seccion = SeccionCatalogo.ICONICAS
                } else {
                    mostradas = Catalogo.sinRepetir(mostradas + resultado.obras, emptySet())
                    fin = resultado.fin
                    pagina++
                }
            } catch (cancelada: CancellationException) { throw cancelada }
              catch (_: Exception) { error = true; fin = true }
            finally { cargando = false }
        }
        cargar()
        snapshotFlow { Triple(estado.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1, mostradas.size, pagina) }
            .distinctUntilChanged().collect { (ultimo, cantidad, _) ->
                if (!fin && !error && (cantidad == 0 || ultimo >= cantidad - 6)) cargar()
            }
    }
    LaunchedEffect(estado, mostradas) {
        snapshotFlow { estado.layoutInfo.visibleItemsInfo.mapNotNull { mostradas.getOrNull(it.index)?.fuente } }
            .distinctUntilChanged().collect { vm.cargarImagenesIniciales(it) }
    }
    Text(titulo, style = MaterialTheme.typography.titleMedium, color = f.texto,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp))
    // Una sola línea que se desliza: con seis filas, dos líneas de píldoras por fila alargaban demasiado la pantalla.
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            SeccionCatalogo.ICONICAS to stringResource(R.string.catalogo_iconicas),
            SeccionCatalogo.TENDENCIAS to stringResource(R.string.catalogo_tendencias),
            SeccionCatalogo.PREMIADAS to stringResource(R.string.catalogo_premiadas),
            SeccionCatalogo.QUERIDAS to stringResource(R.string.catalogo_queridas),
            SeccionCatalogo.PARA_TI to stringResource(R.string.catalogo_para_ti),
        ).forEach { (valor, texto) ->
            Pildora(texto, icono = if (valor == seccion) Iconos.Chulo else null, acento = valor == seccion,
                modifier = Modifier.heightIn(min = 48.dp).semantics { selected = valor == seccion; role = Role.Tab }) { sinInternet = false; seccion = valor }
        }
    }
    if (sinInternet) Text(stringResource(R.string.catalogo_necesita_internet), color = f.textoSuave,
        modifier = Modifier.padding(horizontal = 20.dp))
    if (error) Text(stringResource(R.string.catalogo_error), color = f.textoSuave, modifier = Modifier.padding(horizontal = 20.dp))
    if (!cargando && !error && mostradas.isEmpty()) Text(stringResource(if (seccion == SeccionCatalogo.PARA_TI) R.string.catalogo_elige_primero else R.string.catalogo_vacio),
        color = f.textoSuave, modifier = Modifier.padding(horizontal = 20.dp))
    LazyRow(state = estado, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(mostradas, key = { it.fuente }) { o ->
            val si = o.fuente in elegidas
            val visible by remember(o.fuente, estado) { derivedStateOf { estado.layoutInfo.visibleItemsInfo.any { it.key == o.fuente } } }
            TarjetaInicial(o, if (visible) o.imagen.ifBlank { imagenes[o.fuente].orEmpty() } else "", si, Modifier.animateItem()) {
                vm.gustaInicial(o, !si)
                val seccionElegida = seccion
                if (!si) alcance.launch {
                    try {
                        val nuevas = vm.parecidasCatalogo(o)
                        // Una consulta lenta no debe insertar obras en otra sección.
                        if (seccion == seccionElegida && mostradas.any { it.fuente == o.fuente }) mostradas = Catalogo.insertarParecidas(mostradas, o, nuevas, vm.excluidasCatalogo(e) + o.fuente)
                    } catch (cancelada: CancellationException) { throw cancelada }
                      catch (_: Exception) { /* La selección se conserva aunque la fuente no responda. */ }
                }
            }
        }
        if (cargando) item { Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = f.tinta1, modifier = Modifier.size(28.dp))
        } }
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
            else Box(Modifier.fillMaxWidth().aspectRatio(if (o.tipo == Tipos.ALBUM) 1f else 2f / 3f).clip(forma(12.dp)).background(f.superficie)
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

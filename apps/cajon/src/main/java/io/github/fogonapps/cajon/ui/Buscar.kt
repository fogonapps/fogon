package io.github.fogonapps.cajon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.Pantalla
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Tipos
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.FormaPildora
import io.github.fogonapps.identidad.IconoRiso

@Composable
fun Buscar(vm: CajonViewModel, e: Estado, p: Pantalla.Buscar) {
    val f = FogonTema.actual
    val b by vm.busqueda.collectAsStateWithLifecycle()
    val trayendo by vm.trayendo.collectAsStateWithLifecycle()
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { foco.requestFocus() }
    val artistas = b.tipo == CajonViewModel.TIPO_ARTISTA

    Column(Modifier.fillMaxSize()) {
        Cabecera(stringResource(R.string.agregar), vm::cerrar)
        OutlinedTextField(
            b.texto, { vm.buscar(it.take(80)) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(foco),
            placeholder = { Text(stringResource(R.string.buscar_pista)) }, singleLine = true, shape = FormaPildora,
            leadingIcon = { IconoRiso(Iconos.Buscar, null, tamano = 20.dp, tinta = f.textoSuave) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        Chips(Tipos.TODOS.map { it to nombreTipo(it) } + (CajonViewModel.TIPO_ARTISTA to nombreTipo(CajonViewModel.TIPO_ARTISTA)), b.tipo) { vm.cambiarTipoBusqueda(it) }

        when {
            b.cargando -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = f.tinta1) }
            b.error != null -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Pista(stringResource(b.error!!), adentro = true)
                if (b.error == R.string.buscar_falta_tmdb || b.error == R.string.buscar_tmdb_mala)
                    BotonRiso(stringResource(R.string.poner_clave_tmdb), { vm.abrir(Pantalla.ClaveTmdb) }, Modifier.fillMaxWidth())
                BotonSecundario(stringResource(R.string.agregar_a_mano), { vm.reemplazar(Pantalla.EditarObra(null, b.tipo.takeIf { it in Tipos.TODOS } ?: Tipos.PELICULA)) }, Modifier.fillMaxWidth())
            }
            b.texto.trim().length < 2 -> Column(Modifier.padding(horizontal = 4.dp)) {
                Pista(stringResource(fuenteDe(b.tipo)))
                Pista(stringResource(R.string.buscar_o_a_mano))
                Box(Modifier.padding(16.dp)) {
                    if (artistas) BotonSecundario(stringResource(R.string.artista_a_mano), { vm.reemplazar(Pantalla.Artistas) }, Modifier.fillMaxWidth())
                    else BotonSecundario(stringResource(R.string.agregar_a_mano), { vm.reemplazar(Pantalla.EditarObra(null, b.tipo)) }, Modifier.fillMaxWidth())
                }
            }
            artistas && b.artistas.isEmpty() || !artistas && b.obras.isEmpty() -> Column {
                Pista(stringResource(R.string.buscar_nada, b.texto.trim()))
                Box(Modifier.padding(16.dp)) {
                    if (!artistas) BotonSecundario(stringResource(R.string.agregar_a_mano), { vm.reemplazar(Pantalla.EditarObra(null, b.tipo)) }, Modifier.fillMaxWidth())
                }
            }
            artistas -> LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(b.artistas, key = { it.mbid }) { a ->
                    val ya = e.artistasVivos.any { it.fuente == "mb:artist:${a.mbid}" }
                    FilaVidrio({ vm.abrir(Pantalla.PreviaArtista(a)) }, Modifier.animateItem()) {
                        Ficha(f.tinta2, Iconos.Artista)
                        Column(Modifier.weight(1f)) {
                            Text(a.nombre, style = MaterialTheme.typography.bodyLarge, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOf(a.tipo, a.pais, a.desde?.toString() ?: "", a.detalle).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (ya) IconoRiso(Iconos.Chulo, stringResource(R.string.ya_en_cajon), tamano = 18.dp, tinta = f.acentoTexto)
                    }
                }
                item { Espacio(40.dp) }
            }
            else -> LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(b.obras, key = { it.fuente }) { r ->
                    val ya = vm.L.porFuente(e, r.fuente) != null
                    FilaVidrio({ vm.abrirEncontrado(r) }, Modifier.animateItem()) {
                        Portada(r.portada, r.tipo, r.titulo, Modifier.width(46.dp), esquina = 6.dp)
                        Column(Modifier.weight(1f)) {
                            Text(r.titulo, style = MaterialTheme.typography.bodyLarge, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(listOf(r.anio?.toString() ?: "", r.creador, r.detalle, if (b.tipo == Tipos.ANIME) nombreTipo(r.tipo, false) else "").filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        when {
                            trayendo == r.fuente -> CircularProgressIndicator(Modifier.size(20.dp), color = f.tinta1, strokeWidth = 2.dp)
                            ya -> IconoRiso(Iconos.Chulo, stringResource(R.string.ya_en_cajon), tamano = 18.dp, tinta = f.acentoTexto)
                        }
                    }
                }
                item {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BotonSecundario(stringResource(R.string.no_esta_a_mano), { vm.reemplazar(Pantalla.EditarObra(null, b.tipo)) }, Modifier.fillMaxWidth())
                        Pista(stringResource(fuenteDe(b.tipo)), adentro = true)
                    }
                    Espacio(40.dp)
                }
            }
        }
    }
}

/** De dónde salen los datos de cada tipo (se muestra como crédito). */
fun fuenteDe(tipo: String) = when (tipo) {
    Tipos.LIBRO -> R.string.fuente_open_library
    Tipos.ALBUM, CajonViewModel.TIPO_ARTISTA -> R.string.fuente_musicbrainz
    else -> R.string.fuente_tmdb
}

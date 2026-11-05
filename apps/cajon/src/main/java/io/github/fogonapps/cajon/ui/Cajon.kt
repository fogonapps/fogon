package io.github.fogonapps.cajon.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import io.github.fogonapps.cajon.datos.Personas
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import io.github.fogonapps.identidad.tarjetaFogon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.Pantalla
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.Secciones
import io.github.fogonapps.cajon.datos.Artista
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Estados
import io.github.fogonapps.cajon.datos.Obra
import io.github.fogonapps.cajon.datos.Orden
import io.github.fogonapps.cajon.datos.Tipos
import io.github.fogonapps.cajon.datos.normal
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.FormaPildora
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.NumeroRiso
import io.github.fogonapps.identidad.aparecer
import io.github.fogonapps.identidad.forma
import io.github.fogonapps.identidad.haloPuntos

/** Un apartado de la app: Cine, Música o Libros, con sus cifras, lo que está en curso y su cajón. */
@Composable
fun Apartado(vm: CajonViewModel, e: Estado, seccion: String) {
    val f = FogonTema.actual
    val tipos = Secciones.tipos(seccion)
    val musica = seccion == Secciones.MUSICA
    // Las personas de este apartado: artistas en Música, directores en Cine y escritores en Libros.
    val tipoPersona = when (seccion) { Secciones.LIBROS -> Personas.AUTOR; Secciones.CINE -> Personas.DIRECTOR; else -> CajonViewModel.TIPO_ARTISTA }
    val esDeAqui: (Artista) -> Boolean = { a -> if (musica) Personas.esMusico(a) else a.tipo == tipoPersona }
    // En Cine se elige entre películas, series, animes y directores; en Música, entre álbumes y artistas; en Libros, entre libros y escritores.
    var tipo by rememberSaveable(seccion) { mutableStateOf<String?>(null) }
    var artistas by rememberSaveable(seccion) { mutableStateOf(false) }
    var estado by rememberSaveable(seccion) { mutableStateOf<String?>(null) }
    val texto = ""
    var orden by rememberSaveable(seccion) { mutableStateOf(Orden.RECIENTES) }
    LaunchedEffect(seccion) { vm.completarFotos() }

    Seccion(nombreSeccion(seccion), Modifier.padding(top = 12.dp))

    // Las cifras del apartado: lo terminado este año en grande, y lo demás al lado.
    val anio = vm.hoy().year
    val delAnio = vm.L.cifras(e, anio)
    val obras = e.vivas.filter { it.tipo in tipos }
    val terminadasAnio = tipos.sumOf { delAnio.porTipo[it] ?: 0 }
    Row(Modifier.aparecer(0).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).vidrio(forma(24.dp))
        .haloPuntos(f.tinta2.copy(alpha = 0.3f), centro = androidx.compose.ui.geometry.Offset(0.2f, 0.5f))
        .clickable { vm.abrir(Pantalla.CifrasDe(seccion)) }.padding(18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            NumeroRiso("$terminadasAnio", 48.sp)
            Text(stringResource(when (seccion) { Secciones.MUSICA -> R.string.escuchados_en; Secciones.LIBROS -> R.string.leidos_en; else -> R.string.vistas_en }, anio),
                style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniDato(stringResource(R.string.en_curso), obras.count { it.estado == Estados.EN_CURSO })
            MiniDato(stringResource(R.string.pendientes), obras.count { it.estado == Estados.QUIERO })
            if (musica) MiniDato(nombreTipo(CajonViewModel.TIPO_ARTISTA), e.musicosVivos.size)
            else MiniDato(stringResource(R.string.siempre), obras.count { it.estado == Estados.TERMINADO })
        }
    }


    // En Música: los álbumes que sonaron en tus apps y todavía no están en el cajón.
    if (musica) EscuchadoHacePoco(vm)

    val enCurso = obras.filter { it.estado == Estados.EN_CURSO }.sortedByDescending { it.cambio }
    if (enCurso.isNotEmpty()) {
        Seccion(stringResource(R.string.en_curso))
        Carrusel(enCurso, vm, conProgreso = true)
    }

    Seccion(stringResource(R.string.tu_cajon))
    if (seccion == Secciones.CINE) {
        // Las obras por tipo y, al final, los directores.
        val opciones = listOf<Pair<String?, String>>(null to stringResource(R.string.todo)) + tipos.map { it to nombreTipo(it) } + (Personas.DIRECTOR to nombreTipo(Personas.DIRECTOR))
        Chips(opciones, if (artistas) Personas.DIRECTOR else tipo) { if (it == Personas.DIRECTOR) artistas = true else { artistas = false; tipo = it } }
    } else Chips(listOf(false to nombreTipo(tipos.first()), true to nombreTipo(tipoPersona)), artistas) { artistas = it }

    if (artistas) { CajonArtistas(vm, e, texto, esDeAqui, musica); return }

    val tipoEstados = tipo ?: tipos.first()
    Chips(listOf<Pair<String?, String>>(null to stringResource(R.string.todo)) + Estados.TODOS.map { it to nombreEstado(it, tipoEstados) }, estado) { estado = it }
    val lista = remember(e, tipos, tipo, estado, texto, orden) {
        vm.L.filtrar(e, null, estado, texto, orden).filter { it.tipo in tipos && (tipo == null || it.tipo == tipo) }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(pluralStringResource(R.plurals.obras_n, lista.size, lista.size), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, modifier = Modifier.weight(1f))
        val siguiente = Orden.entries[(orden.ordinal + 1) % Orden.entries.size]
        Pildora(stringResource(nombreOrden(orden)), Iconos.Ordenar) { orden = siguiente }
    }
    if (lista.isEmpty()) {
        Pista(stringResource(if (obras.isEmpty()) R.string.apartado_vacio else R.string.cajon_nada), centro = true, modifier = Modifier.padding(top = 16.dp))
        return
    }
    BarraSeleccion(vm, e, tipoEstados, deObras = true)
    Cuadricula(lista, vm, conSeleccion = true)
}

/**
 * Lo que se puede hacer con varios elegidos: borrar (con confirmación), y en obras cambiar el estado o sumarlas a
 * una lista. Sin nada elegido, una pista de cómo elegir.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BarraSeleccion(vm: CajonViewModel, e: Estado, tipoEstados: String, deObras: Boolean) {
    val f = FogonTema.actual
    val confirmar = LocalConfirmar.current
    val elegidas by vm.seleccion.collectAsStateWithLifecycle()
    // Solo las de este apartado (si se cambió de apartado con algo elegido, no cuenta).
    val aqui = elegidas.filter { id -> if (deObras) e.vivas.any { it.id == id } else e.artistasVivos.any { it.id == id } }
    if (aqui.isEmpty()) return
    // Atrás cancela la selección antes de salir del apartado.
    androidx.activity.compose.BackHandler { vm.limpiarSeleccion() }
    var que by rememberSaveable { mutableStateOf("") }
    // Flotando abajo, sobre la barra de navegación: así la cuadrícula no se corre al elegir (y el siguiente toque cae
    // donde se quería). Sin foco propio: lo de atrás se sigue tocando.
    val alto = with(androidx.compose.ui.platform.LocalDensity.current) { 22.dp.roundToPx() }
    // Medido desde el borde de abajo de la ventana (no del contenido, que sigue más abajo al hacer scroll).
    val posicion = remember(alto) {
        object : androidx.compose.ui.window.PopupPositionProvider {
            override fun calculatePosition(anchorBounds: androidx.compose.ui.unit.IntRect, windowSize: androidx.compose.ui.unit.IntSize,
                layoutDirection: androidx.compose.ui.unit.LayoutDirection, popupContentSize: androidx.compose.ui.unit.IntSize) =
                androidx.compose.ui.unit.IntOffset((windowSize.width - popupContentSize.width) / 2, windowSize.height - popupContentSize.height - alto)
        }
    }
    androidx.compose.ui.window.Popup(popupPositionProvider = posicion) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(forma(22.dp)).background(f.fondo)
        .border(f.borde, f.linea, forma(22.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.elegidas_n, aqui.size), style = MaterialTheme.typography.titleSmall, color = f.texto, modifier = Modifier.weight(1f))
            BotonIcono(Iconos.Cerrar, stringResource(R.string.cancelar)) { vm.limpiarSeleccion(); que = "" }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (deObras) {
                Pildora(stringResource(R.string.cambiar_estado), Iconos.Chulo) { que = if (que == "estado") "" else "estado" }
                if (e.listas.isNotEmpty()) Pildora(stringResource(R.string.a_una_lista), Iconos.Mas) { que = if (que == "lista") "" else "lista" }
            }
            Pildora(stringResource(R.string.borrar_n, aqui.size), Iconos.Cerrar) {
                confirmar(vm.tx(R.string.borrar_n_pregunta, aqui.size), vm.tx(R.string.borrar_n_texto)) { vm.borrarSeleccion(); que = "" }
            }
        }
        if (que == "estado") FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Estados.TODOS.forEach { s -> Pildora(nombreEstado(s, tipoEstados)) { vm.estadoSeleccion(s); que = "" } }
        }
        if (que == "lista") FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            e.listas.filter { !it.borrada }.forEach { l -> Pildora(l.nombre) { vm.seleccionALista(l.id); que = "" } }
        }
    }
    }
}

@Composable
private fun MiniDato(etiqueta: String, n: Int) {
    val f = FogonTema.actual
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$n", fontFamily = f.cifras, fontSize = 16.sp, color = f.texto)
        Text(etiqueta, style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
    }
}

/** Las personas del apartado (artistas, directores o escritores) en cuadrícula, con su foto redonda y su nota. */
@Composable
private fun CajonArtistas(vm: CajonViewModel, e: Estado, texto: String, esDeAqui: (Artista) -> Boolean, musica: Boolean) {
    val f = FogonTema.actual
    val lista = e.artistasVivos.filter { esDeAqui(it) && (texto.isBlank() || normal(texto) in normal(it.nombre) || it.generos.any { g -> normal(texto) in normal(g) }) }
        .sortedWith(compareByDescending<Artista> { it.favorito }.thenByDescending { it.nota }.thenBy { normal(it.nombre) })
    if (lista.isEmpty()) {
        Pista(stringResource(if (musica) R.string.artistas_vacio else R.string.personas_vacio), centro = true, modifier = Modifier.padding(top = 16.dp))
        return
    }
    val elegidas by vm.seleccion.collectAsStateWithLifecycle()
    BarraSeleccion(vm, e, "", deObras = false)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        lista.chunked(3).forEachIndexed { fila, grupo ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                grupo.forEachIndexed { k, a ->
                    val albumes = e.vivas.count { it.artista == a.id || !musica && normal(a.nombre) in normal(it.creador) }
                    val elegido = a.id in elegidas
                    Column(Modifier.weight(1f).aparecer((fila * 3 + k).coerceAtMost(12)).clip(forma(12.dp))
                        .combinedClickable(onLongClick = { vm.alternarSeleccion(a.id) }) {
                            if (elegidas.isNotEmpty()) vm.alternarSeleccion(a.id) else vm.abrir(Pantalla.VerArtista(a.id))
                        },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                            FotoArtista(a.foto, a.nombre, 96.dp, a.tipo)
                            if (elegido) Box(Modifier.align(Alignment.TopEnd).size(26.dp).clip(FormaPildora).background(f.tinta1),
                                contentAlignment = Alignment.Center) { IconoRiso(Iconos.Chulo, null, tamano = 16.dp, tinta = f.sobreTinta1) }
                        }
                        Text(a.nombre, style = MaterialTheme.typography.labelLarge, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        if (a.nota > 0) Estrellas(a.nota, 12.sp)
                        else Text(pluralStringResource(if (musica) R.plurals.albumes_n else R.plurals.obras_n, albumes, albumes), style = MaterialTheme.typography.labelSmall, color = f.textoSuave)
                    }
                }
                repeat(3 - grupo.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Las portadas en cuadrícula de tres, con sus estrellas y el corazón. */
@Composable
fun Cuadricula(obras: List<Obra>, vm: CajonViewModel, numerada: Boolean = false, conSeleccion: Boolean = false) {
    val f = FogonTema.actual
    val elegidas by vm.seleccion.collectAsStateWithLifecycle()
    // Si son solo discos, marcos cuadrados; si hay de todo, verticales (los discos se apoyan abajo).
    val marco = if (obras.all { it.tipo == Tipos.ALBUM }) 1f else 2f / 3f
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        obras.chunked(3).forEachIndexed { fila, grupo ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                grupo.forEachIndexed { k, o ->
                    val n = fila * 3 + k
                    // Mantener presionado elige (para borrar o cambiar varios); con algo elegido, tocar también elige.
                    val elegida = o.id in elegidas
                    Column(Modifier.weight(1f).aparecer(n.coerceAtMost(12)).clip(forma(12.dp))
                        .combinedClickable(onLongClick = if (conSeleccion) ({ vm.alternarSeleccion(o.id) }) else null) {
                            if (conSeleccion && elegidas.isNotEmpty()) vm.alternarSeleccion(o.id) else vm.abrir(Pantalla.Ficha(o.id))
                        }
                        .then(if (elegida) Modifier.border(2.dp, f.tinta1, forma(12.dp)) else Modifier),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Todas en el mismo marco vertical; los discos (cuadrados) se apoyan abajo, así los títulos quedan parejos.
                        Box(Modifier.fillMaxWidth().aspectRatio(marco), contentAlignment = Alignment.BottomCenter) {
                            Portada(o.portada, o.tipo, o.titulo, Modifier.fillMaxWidth(), conTitulo = false)
                            if (elegida) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp).clip(FormaPildora).background(f.tinta1),
                                contentAlignment = Alignment.Center) { IconoRiso(Iconos.Chulo, null, tamano = 16.dp, tinta = f.sobreTinta1) }
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


/**
 * "Escuchado hace poco": lo que sonó en Spotify, YouTube Music o cualquier app (ver [io.github.fogonapps.cajon.EscuchaMusica])
 * y todavía no está en el cajón: agregar con un toque o descartar. Sin nada anotado, una pista de cómo activarlo.
 */
@Composable
private fun EscuchadoHacePoco(vm: CajonViewModel) {
    val f = FogonTema.actual
    val oidos by vm.oidos.collectAsStateWithLifecycle()
    val lista = vm.oidosPendientes(oidos)
    val c = androidx.compose.ui.platform.LocalContext.current
    if (lista.isEmpty()) {
        if (oidos.albumes.isEmpty() && !io.github.fogonapps.cajon.EscuchaMusica.activado(c)) {
            Seccion(stringResource(R.string.escuchado_poco))
            Pista(stringResource(R.string.escuchado_vacio))
        }
        return
    }
    Seccion(stringResource(R.string.escuchado_poco))
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lista.take(8).forEach { a ->
            Row(Modifier.fillMaxWidth().vidrio().padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconoRiso(Iconos.Disco, null, tamano = 28.dp, tinta = f.acentoTexto)
                Column(Modifier.weight(1f)) {
                    Text(a.album, style = MaterialTheme.typography.bodyLarge, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(a.artista + " · " + androidx.compose.ui.res.pluralStringResource(R.plurals.canciones_app, a.canciones.size, a.canciones.size, a.app), style = MaterialTheme.typography.bodySmall,
                        color = f.textoSuave, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Pildora(stringResource(R.string.agregar), Iconos.Mas, acento = true) { vm.agregarOido(a.clave) }
                BotonIcono(Iconos.Cerrar, stringResource(R.string.descartar)) { vm.descartarOido(a.clave) }
            }
        }
    }
}

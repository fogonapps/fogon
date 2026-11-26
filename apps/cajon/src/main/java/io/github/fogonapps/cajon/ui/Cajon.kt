package io.github.fogonapps.cajon.ui

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.foundation.layout.height
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
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
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.FormaPildora
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.aparecer
import io.github.fogonapps.identidad.forma

/** Un apartado de la app: Cine, Música, Libros o Juegos, con sus cifras, lo que está en curso y su cajón. */
@Composable
fun Apartado(vm: CajonViewModel, e: Estado, seccion: String, conTitulo: Boolean = true) {
    val f = FogonTema.actual
    val tipos = Secciones.tipos(seccion)
    val musica = seccion == Secciones.MUSICA
    // Los juegos no tienen personas (los estudios no se guardan aparte).
    val juegos = seccion == Secciones.JUEGOS
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

    AvanceEnlace(vm)
    if (conTitulo) Seccion(nombreSeccion(seccion), Modifier.padding(top = 12.dp))

    // Las cifras del apartado: lo terminado este año en grande, y lo demás al lado.
    val anio = vm.hoy().year
    val delAnio = vm.L.cifras(e, anio)
    val obras = e.vivas.filter { it.tipo in tipos }
    val terminadasAnio = tipos.sumOf { delAnio.porTipo[it] ?: 0 }
    Row(Modifier.aparecer(0).fillMaxWidth().clip(forma(20.dp))
        .clickable { vm.abrir(Pantalla.CifrasDe(seccion)) }.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text("$terminadasAnio", fontFamily = f.titulos, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, fontSize = 54.sp, lineHeight = 56.sp, color = f.texto)
            Text(stringResource(when (seccion) {
                Secciones.MUSICA -> R.string.escuchados_en; Secciones.LIBROS -> R.string.leidos_en; Secciones.JUEGOS -> R.string.jugados_en; else -> R.string.vistas_en
            }, anio),
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

    // «Todo lo tuyo» solo hace falta como título si arriba hay un carrusel «En curso» del que separarlo.
    if (enCurso.isNotEmpty()) Seccion(stringResource(R.string.tu_cajon)) else Spacer(Modifier.height(4.dp))
    if (seccion == Secciones.CINE) {
        // Las obras por tipo y, al final, los directores.
        val opciones = listOf<Pair<String?, String>>(null to stringResource(R.string.todo)) + tipos.map { it to nombreTipo(it) } + (Personas.DIRECTOR to nombreTipo(Personas.DIRECTOR))
        Chips(opciones, if (artistas) Personas.DIRECTOR else tipo) { if (it == Personas.DIRECTOR) artistas = true else { artistas = false; tipo = it } }
    } else if (!juegos) Chips(listOf(false to nombreTipo(tipos.first()), true to nombreTipo(tipoPersona)), artistas) { artistas = it }

    if (artistas && !juegos) { CajonArtistas(vm, e, texto, esDeAqui, musica); return }

    val tipoEstados = tipo ?: tipos.first()
    // «Sin estado» (juegos traídos de Steam que ya se jugaron) aparece solo si hay alguno; si ya no queda, vuelve a todo.
    val haySinEstado = obras.any { it.estado == Estados.SIN_ESTADO && (tipo == null || it.tipo == tipo) }
    val estadoVisto = estado.takeIf { it != Estados.SIN_ESTADO || haySinEstado }
    Chips(listOf<Pair<String?, String>>(null to stringResource(R.string.todo)) + Estados.TODOS.map { it to nombreEstado(it, tipoEstados) } +
        listOfNotNull(if (haySinEstado) Estados.SIN_ESTADO to nombreEstado(Estados.SIN_ESTADO, tipoEstados) else null), estadoVisto) { estado = it }
    val lista = remember(e, tipos, tipo, estadoVisto, texto, orden) {
        vm.L.filtrar(e, null, estadoVisto, texto, orden).filter { it.tipo in tipos && (tipo == null || it.tipo == tipo) }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(pluralStringResource(R.plurals.obras_n, lista.size, lista.size), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, modifier = Modifier.weight(1f))
        val siguiente = Orden.entries[(orden.ordinal + 1) % Orden.entries.size]
        Pildora(stringResource(nombreOrden(orden)), Iconos.Ordenar) { orden = siguiente }
    }
    if (lista.isEmpty()) {
        if (obras.isNotEmpty()) Pista(stringResource(R.string.cajon_nada), centro = true, modifier = Modifier.padding(top = 16.dp))
        else Vacio(Iconos.de(tipos.first()), stringResource(R.string.vacio_apartado_titulo), stringResource(R.string.vacio_apartado_texto),
            boton = stringResource(R.string.buscar)) { vm.abrir(Pantalla.Buscar(Secciones.paraBuscar(seccion))) }
        return
    }
    BarraSeleccion(vm, e, tipoEstados, deObras = true)
    Cuadricula(lista, vm, conSeleccion = true, porTandas = true)
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
                            if (elegido) Box(Modifier.align(Alignment.TopEnd).size(26.dp).clip(FormaPildora).background(f.pildora),
                                contentAlignment = Alignment.Center) { IconoRiso(Iconos.Chulo, null, tamano = 16.dp, tinta = f.sobrePildora) }
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

/**
 * Las portadas en cuadrícula de tres, con sus estrellas y el corazón. Con [porTandas] (el cajón entero, que puede tener
 * miles), se arman de a [TANDA] y se suman más cuando el final se acerca a la pantalla: la pestaña tiene un solo scroll
 * (el de `App.kt`), así que no puede ser una cuadrícula perezosa adentro sin romperlo.
 */
@Composable
fun Cuadricula(obras: List<Obra>, vm: CajonViewModel, numerada: Boolean = false, conSeleccion: Boolean = false, porTandas: Boolean = false) {
    val f = FogonTema.actual
    val elegidas by vm.seleccion.collectAsStateWithLifecycle()
    // Si son solo discos, marcos cuadrados; si hay de todo, verticales (los discos se apoyan abajo).
    val marco = if (obras.all { it.tipo == Tipos.ALBUM }) 1f else 2f / 3f
    var tope by rememberSaveable { mutableIntStateOf(TANDA) }
    val visibles = if (porTandas) obras.take(tope) else obras
    val altoPantalla = with(androidx.compose.ui.platform.LocalDensity.current) { androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp.toPx() }
    // Mantener apretada una portada abre el registro en dos toques (ver [RegistroRapido]).
    var rapida by rememberSaveable { mutableStateOf<String?>(null) }
    val registrarRapido = stringResource(R.string.rapido_registrar)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        visibles.chunked(3).forEachIndexed { fila, grupo ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                grupo.forEachIndexed { k, o ->
                    val n = fila * 3 + k
                    // Mantener presionado abre el registro rápido (que también deja elegir varias, para borrarlas o
                    // cambiarlas); con algo elegido, tocar o mantener elige.
                    val elegida = o.id in elegidas
                    val eligiendo = conSeleccion && elegidas.isNotEmpty()
                    Column(Modifier.weight(1f).aparecer(n.coerceAtMost(12)).clip(forma(12.dp))
                        .combinedClickable(onLongClickLabel = if (eligiendo) null else registrarRapido,
                            onLongClick = { if (eligiendo) vm.alternarSeleccion(o.id) else rapida = o.id }) {
                            if (eligiendo) vm.alternarSeleccion(o.id) else vm.abrir(Pantalla.Ficha(o.id))
                        }
                        .then(if (elegida) Modifier.border(2.dp, f.texto, forma(12.dp)) else Modifier),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Todas en el mismo marco vertical; los discos (cuadrados) se apoyan abajo, así los títulos quedan parejos.
                        Box(Modifier.fillMaxWidth().aspectRatio(marco), contentAlignment = Alignment.BottomCenter) {
                            Portada(o.portada, o.tipo, o.titulo, Modifier.fillMaxWidth(), conTitulo = false, natural = marco == 1f)
                            if (elegida) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp).clip(FormaPildora).background(f.pildora),
                                contentAlignment = Alignment.Center) { IconoRiso(Iconos.Chulo, null, tamano = 16.dp, tinta = f.sobrePildora) }
                            if (numerada) Text("${n + 1}", fontFamily = f.cifras, fontSize = 15.sp, color = f.sobrePildora,
                                modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(FormaPildora).background(f.pildora).padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                        Text(o.titulo, Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelMedium, color = f.texto,
                            textAlign = TextAlign.Start, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Estrellas(o.nota, 12.sp)
                            if (o.meGusta) IconoRiso(Iconos.Corazon, null, tamano = 12.dp, tinta = f.texto, relleno = f.texto)
                            if (o.nota == 0 && !o.meGusta) Text(nombreEstado(o.estado, o.tipo), style = MaterialTheme.typography.labelSmall, color = f.textoSuave, maxLines = 1)
                        }
                    }
                }
                repeat(3 - grupo.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        // El final: cuando entra (o casi) en la pantalla, se arma la tanda siguiente.
        if (porTandas && visibles.size < obras.size) Spacer(Modifier.fillMaxWidth().height(1.dp).onGloballyPositioned { c ->
            if (c.boundsInWindow().top < altoPantalla * 2) tope = visibles.size + TANDA
        })
    }
    rapida?.let { id -> obras.firstOrNull { it.id == id }?.let { o ->
        RegistroRapido(vm, o, onElegirVarias = if (conSeleccion) ({ vm.alternarSeleccion(o.id) }) else null) { rapida = null }
    } }
}

/** Cuántas portadas se arman de una vez en el cajón entero. */
private const val TANDA = 60

/** Píldoras para filtrar, en una fila que se desliza; si sigue a un lado, ese borde se desvanece. */
@Composable
fun <T> Chips(opciones: List<Pair<T, String>>, elegido: T, onElegir: (T) -> Unit) {
    val f = FogonTema.actual
    val desliza = rememberScrollState()
    Row(Modifier.fillMaxWidth().desvanecerBordes(desliza.canScrollBackward, desliza.canScrollForward).horizontalScroll(desliza)
        .padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opciones.forEach { (v, t) ->
            val elegido0 = v == elegido
            val fondo by androidx.compose.animation.animateColorAsState(if (elegido0) f.texto else Color.Transparent, label = "chip")
            Text(t, style = MaterialTheme.typography.labelLarge, color = if (elegido0) f.fondo else f.textoSuave,
                modifier = Modifier.clip(FormaPildora).background(fondo).clickable { onElegir(v) }
                    .semantics { selected = elegido0; role = androidx.compose.ui.semantics.Role.Tab }
                    .heightIn(min = 40.dp).padding(horizontal = 14.dp, vertical = 10.dp))
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
    Seccion(stringResource(R.string.escuchado_poco), derecha = {
        if (lista.size > 8) TextButton({ vm.abrir(Pantalla.OidosPendientes) }) {
            Text(stringResource(R.string.ver_mas), color = f.texto)
        }
    })
    val estado = rememberLazyListState()
    val muestra = lista.take(8)
    LaunchedEffect(muestra, estado) {
        snapshotFlow { estado.layoutInfo.visibleItemsInfo.mapNotNull { muestra.getOrNull(it.index)?.clave } }
            .collect { vm.completarPortadasOidas(it) }
    }
    LazyRow(state = estado, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(muestra, key = { it.clave }) { a -> TarjetaAlbumOido(a, vm, Modifier.width(136.dp)) }
    }
}

/** Todos los álbumes pendientes, con desplazamiento propio y columnas adaptables. */
@Composable
fun OidosPendientes(vm: CajonViewModel) {
    val oidos by vm.oidos.collectAsStateWithLifecycle()
    val lista = vm.oidosPendientes(oidos)
    val estado = rememberLazyGridState()
    Cabecera(stringResource(R.string.escuchado_poco), vm::cerrar)
    LaunchedEffect(lista, estado) {
        snapshotFlow { estado.layoutInfo.visibleItemsInfo.mapNotNull { lista.getOrNull(it.index)?.clave } }
            .collect { vm.completarPortadasOidas(it) }
    }
    LazyVerticalGrid(columns = GridCells.Adaptive(136.dp), state = estado,
        contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items(lista, key = { it.clave }) { a -> TarjetaAlbumOido(a, vm) }
    }
}

@Composable
private fun TarjetaAlbumOido(a: io.github.fogonapps.cajon.datos.AlbumOido, vm: CajonViewModel, modifier: Modifier = Modifier) {
    val f = FogonTema.actual
    val descripcion = stringResource(R.string.album_oido_descripcion, a.album, a.artista)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Portada(a.portada, Tipos.ALBUM, a.album, Modifier.matchParentSize().clearAndSetSemantics { contentDescription = descripcion }, conTitulo = false, natural = true)
            AccionAlbumOido(Iconos.Cerrar, stringResource(R.string.descartar_album_oido, a.album), Modifier.align(Alignment.TopEnd)) { vm.descartarOido(a.clave) }
            AccionAlbumOido(Iconos.Mas, stringResource(R.string.agregar_album_oido, a.album), Modifier.align(Alignment.BottomEnd)) { vm.agregarOido(a.clave) }
        }
        Text(a.album, style = MaterialTheme.typography.bodyLarge, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(a.artista + " · " + pluralStringResource(R.plurals.canciones, a.canciones.size, a.canciones.size),
            style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AccionAlbumOido(icono: io.github.fogonapps.identidad.Dibujo, descripcion: String, modifier: Modifier, accion: () -> Unit) {
    val f = FogonTema.actual
    // El área táctil es de 48 dp; el círculo visible, más chico, tapa menos la portada.
    Box(modifier.size(48.dp).clickable(role = Role.Button, onClickLabel = descripcion, onClick = accion)
        .semantics { contentDescription = descripcion }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(32.dp).background(f.fondo.copy(alpha = 0.9f), CircleShape), contentAlignment = Alignment.Center) {
            IconoRiso(icono, null, tamano = 16.dp, tinta = f.texto)
        }
    }
}

/** Avance compartido por el cajón y Ajustes. */
@Composable
fun AvanceEnlace(vm: CajonViewModel) {
    val completando by vm.completando.collectAsStateWithLifecycle()
    val f = FogonTema.actual
    completando?.let { k ->
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            androidx.compose.material3.LinearProgressIndicator({ k.hechas / k.total.toFloat() }, Modifier.fillMaxWidth(), color = f.tinta1, trackColor = f.linea)
            Text(stringResource(R.string.portadas_avance, k.hechas, k.total, k.encontradas), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            BotonSecundario(stringResource(R.string.detener_enlace), vm::detenerEnlace)
        }
    }
}

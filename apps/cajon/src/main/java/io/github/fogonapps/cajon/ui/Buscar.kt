package io.github.fogonapps.cajon.ui

import io.github.fogonapps.identidad.forma
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.cajon.Busqueda
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.Pantalla
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.Secciones
import io.github.fogonapps.cajon.datos.ArtistaEncontrado
import io.github.fogonapps.cajon.datos.Encontrado
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Personas
import io.github.fogonapps.cajon.datos.Tipos
import io.github.fogonapps.cajon.datos.normal
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.FormaPildora
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.aparecer

/**
 * El buscador, como el de una app de música: arriba se elige Todo, Cine, Música o Libros; abajo salen el mejor
 * resultado y los grupos (artistas, álbumes, películas, series, animes, libros), cada uno con "Ver más".
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun Buscar(vm: CajonViewModel, e: Estado, p: Pantalla.Buscar) {
    val f = FogonTema.actual
    val b by vm.busqueda.collectAsStateWithLifecycle()
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { foco.requestFocus() }
    // X-07: lo que se buscó queda en «Búsquedas recientes» al salir (o al tocar buscar en el teclado).
    val textoActual by rememberUpdatedState(b.texto)
    DisposableEffect(Unit) { onDispose { if (!p.paraMensaje) vm.recientes.anotar(textoActual) } }

    Column(Modifier.fillMaxSize()) {
        if (p.paraMensaje) Text(stringResource(R.string.chat_nuevo), style = MaterialTheme.typography.titleLarge, color = f.texto,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).semantics { heading() })
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonIcono(Iconos.Atras, stringResource(R.string.volver), grande = true) { vm.cerrar() }
            OutlinedTextField(
                b.texto, { vm.buscar(it.take(80)) }, Modifier.weight(1f).focusRequester(foco),
                placeholder = { Text(stringResource(if (p.paraMensaje) R.string.buscar_persona_pista else R.string.buscar_pista)) }, singleLine = true, shape = FormaPildora,
                leadingIcon = { IconoRiso(Iconos.Buscar, null, tamano = 20.dp, tinta = f.textoSuave) },
                trailingIcon = if (b.texto.isNotEmpty()) ({ BotonIcono(Iconos.Cerrar, stringResource(R.string.borrar_texto)) { vm.buscar("") } }) else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { if (!p.paraMensaje) vm.recientes.anotar(b.texto) }),
            )
        }
        // Los filtros: todo, películas, series, animes, música, libros, juegos y personas. Todos a la vista, en dos filas si hace falta.
        // Buscando a quién escribirle no hay filtros: solo personas.
        if (!p.paraMensaje) FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Secciones.BUSQUEDA.let { l -> if (b.seccion in l) l else l.dropLast(1) + b.seccion + l.last() }.forEach { s ->
                val elegida = s == b.seccion
                Box(Modifier.clip(FormaPildora).background(if (elegida) f.tinta1 else f.superficie)
                    .border(f.borde, if (elegida) f.tinta1 else f.linea, FormaPildora).clickable(role = androidx.compose.ui.semantics.Role.Tab) { vm.cambiarSeccion(s) }
                    .semantics { selected = elegida }.padding(horizontal = 16.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center) {
                    Text(nombreSeccion(s), style = MaterialTheme.typography.labelLarge, color = if (elegida) f.sobreTinta1 else f.texto, maxLines = 1)
                }
            }
        }
        if (b.cargando.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = f.tinta1, trackColor = f.linea)

        if (b.texto.trim().length < 2) {
            if (p.paraMensaje) Pista(stringResource(R.string.red_personas_pista), Modifier.padding(16.dp)) else SinBuscar(vm, e, b)
            return@Column
        }

        // Cada fuente llega a su tiempo y sus grupos van arriba de otros: mientras la persona no haya bajado por
        // su cuenta, la lista vuelve arriba para que lo nuevo no quede escondido.
        val lista = androidx.compose.foundation.lazy.rememberLazyListState()
        val llegadas = (b.artistas.size + b.autores.size + b.directores.size + b.personas.size + b.canciones.size) * 1000 + b.obras.values.sumOf { it.size }
        val titulos = mapOf("artistas" to nombreTipo(CajonViewModel.TIPO_ARTISTA), "directores" to nombreTipo(Personas.DIRECTOR), "autores" to nombreTipo(Personas.AUTOR))
        // (Se mira si la persona arrastró la lista, no en qué fila está: lo del momento va debajo de los resultados y, sin
        // esto, la lista se quedaba pegada a él y los resultados quedaban arriba, fuera de la vista.)
        var movida by remember(b.texto, b.seccion) { mutableStateOf(false) }
        LaunchedEffect(lista) { lista.interactionSource.interactions.collect { if (it is androidx.compose.foundation.interaction.DragInteraction.Start) movida = true } }
        // requestScrollToItem gana sobre el ancla de la lista (que si no deja a la vista lo que ya estaba, abajo).
        LaunchedEffect(llegadas, b.texto, b.seccion) { if (!movida) lista.requestScrollToItem(0) }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = lista) {
            val todo = b.seccion == Secciones.TODO
            fun maximo(n: Int) = if (todo) n else Int.MAX_VALUE

            // Lo que ya está en tu cajón con ese nombre: sale al instante, sin esperar a internet.
            val palabrasCajon = normal(b.texto).split(" ").filter { it.isNotBlank() }
            val tiposSeccion = Secciones.tipos(b.seccion)
            val delCajon = if (b.seccion == Secciones.PERSONAS || palabrasCajon.isEmpty()) emptyList() else e.vivas.filter { o ->
                (todo || o.tipo in tiposSeccion) && normal("${o.titulo} ${o.original} ${o.creador}").let { t -> palabrasCajon.all { it in t } }
            }.sortedByDescending { if (normal(it.titulo) == normal(b.texto.trim())) 1 else 0 }
            if (delCajon.isNotEmpty()) {
                item(key = "t-cajon") { Titulo(stringResource(R.string.buscar_en_tu_cajon), null) }
                items(delCajon.take(maximo(4)), key = { "caj" + it.id }) { o ->
                    FilaVidrio({ vm.abrir(Pantalla.Ficha(o.id)) }) {
                        Portada(o.portada, o.tipo, o.titulo, Modifier.width(46.dp), esquina = 6.dp, conTitulo = false)
                        Column(Modifier.weight(1f)) {
                            Text(o.titulo, style = MaterialTheme.typography.bodyLarge, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(nombreTipo(o.tipo, plural = false), o.anio?.toString()).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1)
                        }
                        Estrellas(o.nota)
                    }
                }
            }

            // Personas de la red (en "Todo", las primeras tres; en "Personas", todas).
            if (b.personas.isNotEmpty()) {
                item("personas-titulo") { Titulo(stringResource(R.string.red_personas), if (todo) ({ vm.cambiarSeccion(Secciones.PERSONAS) }) else null) }
                items(b.personas.take(maximo(3)), key = { "persona-" + it.usuario }) { u -> FilaPersona(u, { if (p.paraMensaje) vm.reemplazar(Pantalla.Conversacion(u.usuario)) else vm.abrir(Pantalla.PerfilRed(u.usuario)) }) }
            } else if (b.seccion == Secciones.PERSONAS && b.mostrarNoEncontrado) item("sin-personas") { Pista(stringResource(R.string.red_nadie_encontrado), Modifier.padding(16.dp)) }

            // Si se buscó un género o una etiqueta: primero lo del cajón que la lleva (artistas y obras), después lo de afuera.
            val genero = b.genero
            var hayDelCajon = false
            if (genero != null) {
                val g = io.github.fogonapps.cajon.datos.Logica.tituloLimpio(genero)
                fun tiene(lista: List<String>) = lista.any { io.github.fogonapps.cajon.datos.Logica.tituloLimpio(it) == g }
                val susArtistas = if (todo || b.seccion == Secciones.MUSICA) e.musicosVivos.filter { tiene(it.generos) } else emptyList()
                val tipos = Secciones.tipos(b.seccion)
                val susAlbumes = e.vivas.filter { (todo || it.tipo in tipos) && tiene(it.generos + io.github.fogonapps.cajon.datos.GenerosJuego.visibles(it) + it.etiquetas) }
                if (susArtistas.isNotEmpty() || susAlbumes.isNotEmpty()) {
                    hayDelCajon = true
                    item(key = "t-genero") { Titulo(stringResource(R.string.genero_en_tu_cajon, genero), null) }
                    if (susArtistas.isNotEmpty()) item(key = "genero-artistas") {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(susArtistas, key = { "g" + it.id }) { a ->
                                Column(Modifier.width(92.dp).clip(io.github.fogonapps.identidad.forma(12.dp)).clickable { vm.abrir(Pantalla.VerArtista(a.id)) },
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FotoArtista(a.foto, a.nombre, 84.dp, a.tipo)
                                    Text(a.nombre, style = MaterialTheme.typography.labelMedium, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                    items(susAlbumes.take(maximo(6)), key = { "ga" + it.id }) { o ->
                        FilaVidrio({ vm.abrir(Pantalla.Ficha(o.id)) }) {
                            Portada(o.portada, o.tipo, o.titulo, Modifier.width(46.dp), esquina = 6.dp, conTitulo = false)
                            Column(Modifier.weight(1f)) {
                                Text(o.titulo, style = MaterialTheme.typography.bodyLarge, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(listOfNotNull(o.anio?.toString(), o.creador.takeIf { it.isNotBlank() }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1)
                            }
                            Estrellas(o.nota)
                        }
                    }
                }
            }

            // Las personas de cada sección: artistas (Música), directores (Cine) y escritores (Libros).
            val personas = listOf(Triple("artistas", Secciones.MUSICA, b.artistas), Triple("directores", Secciones.PELICULAS, b.directores),
                Triple("autores", Secciones.LIBROS, b.autores)).filter { todo || b.seccion == it.second || it.first == "directores" && b.seccion in Secciones.DE_CINE }

            // Los grupos de obras, en el orden de la sección.
            val orden = when (b.seccion) {
                Secciones.PELICULAS -> listOf(Tipos.PELICULA)
                Secciones.SERIES -> listOf(Tipos.SERIE)
                Secciones.ANIMES -> listOf(Tipos.ANIME)
                Secciones.MUSICA -> listOf(Tipos.ALBUM)
                Secciones.LIBROS -> listOf(Tipos.LIBRO)
                Secciones.JUEGOS -> listOf(Tipos.JUEGO)
                else -> listOf(Tipos.ALBUM, Tipos.PELICULA, Tipos.SERIE, Tipos.ANIME, Tipos.LIBRO, Tipos.JUEGO)
            }

            // El mejor resultado: la persona más conocida (cada lista viene de la más a la menos) cuyo nombre tiene
            // todas las palabras buscadas, como en las apps de música; pero si una obra conocida se llama así, van
            // primero las obras (ver [OrdenBusqueda]).
            val decision = io.github.fogonapps.cajon.datos.OrdenBusqueda.decidir(b.texto, orden.map { b.obras[it].orEmpty() }, personas.flatMap { it.third })
            val exacto = decision.mejorPersona

            // Lo que no se pudo buscar (sin internet, la fuente no respondió a tiempo), arriba para que se vea y con
            // «Volver a intentar»: una fuente caída no deja la lista vacía y callada.
            b.avisos.forEach { error ->
                item(key = "e-$error") {
                    Column(Modifier.padding(16.dp).fillMaxWidth().vidrio().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(error), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                        if (error != R.string.buscar_falta_tmdb && error != R.string.buscar_tmdb_mala)
                            BotonSecundario(stringResource(R.string.inicio_reintentar), { vm.buscar(b.texto) }, Modifier.fillMaxWidth())
                    }
                }
            }
            if (exacto != null) item(key = "mejor") { MejorArtista(vm, e, exacto) }

            fun grupoPersonas() = personas.forEach { (clave, seccion, lista0) ->
                val lista = if (todo) io.github.fogonapps.cajon.datos.OrdenBusqueda.sinLaMejor(lista0, exacto) else lista0
                if (lista.isNotEmpty()) {
                    item(key = "t-$clave") { Titulo(titulos.getValue(clave), if (todo) ({ vm.cambiarSeccion(seccion) }) else null) }
                    if (todo) item(key = "fila-$clave") {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(lista.take(8), key = { it.fuente }) { a -> ArtistaRedondo(vm, a) }
                        }
                    } else items(lista, key = { "a" + it.fuente }) { a -> FilaArtista(vm, e, a, Modifier.animateItem()) }
                }
            }
            if (!decision.obrasPrimero) grupoPersonas()

            // Canciones (de Apple): escucharlas 30 segundos, ir a Apple Music o, al tocarlas, buscar su álbum.
            val cancionesArriba = b.seccion == Secciones.MUSICA
            if (b.canciones.isNotEmpty() && cancionesArriba) {
                item(key = "t-canciones") { Titulo(stringResource(io.github.fogonapps.social.ui.R.string.historia_tipo_cancion), null) }
                items(b.canciones, key = { "c" + it.audio + it.titulo }) { c -> FilaCancionBuscada(vm, c) }
            }

            // En «Todo», primero el grupo cuyo primer resultado se parece más a lo buscado (una película no queda
            // debajo de los discos si es lo que se buscaba). Si dos grupos se parecen igual (la película «Inception» y
            // su banda sonora), primero el de más votos.
            val ordenFinal = if (todo) io.github.fogonapps.cajon.datos.OrdenBusqueda.ordenGrupos(b.texto, orden, b.obras) else orden
            ordenFinal.forEach { t ->
                val lista = b.obras[t].orEmpty()
                if (lista.isNotEmpty()) {
                    item(key = "t-$t") { Titulo(nombreTipo(t), if (todo) ({ vm.cambiarSeccion(Secciones.de(t)) }) else null) }
                    items(lista.take(maximo(4)), key = { "o" + it.fuente }) { r -> FilaEncontrado(vm, e, r, Modifier.animateItem()) }
                }
            }
            if (decision.obrasPrimero) grupoPersonas()

            if (b.canciones.isNotEmpty() && todo) {
                item(key = "t-canciones") { Titulo(stringResource(io.github.fogonapps.social.ui.R.string.historia_tipo_cancion), null) }
                items(b.canciones, key = { "c" + it.audio + it.titulo }) { c -> FilaCancionBuscada(vm, c) }
            }

            // Lo del momento también debajo de los resultados (en todas las secciones), cuando ya llegó todo: si
            // estuviera desde el principio, la lista se quedaría anclada a él y los resultados entrarían por arriba, fuera de la vista.
            if (b.cargando.isEmpty() && !p.paraMensaje) item(key = "momento") { Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { LoDelMomento(vm, b.seccion, compacto = true) } }

            if (!p.paraMensaje) item(key = "pie") {
                val vacio = !hayDelCajon && b.seccion != Secciones.PERSONAS && b.mostrarNoEncontrado
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (vacio) Pista(stringResource(R.string.buscar_nada, b.texto.trim()), adentro = true)
                    BotonSecundario(stringResource(R.string.no_esta_a_mano), { vm.reemplazar(Pantalla.EditarObra(null, tipoAMano(b.seccion))) }, Modifier.fillMaxWidth())
                    Pista(stringResource(R.string.fuentes_busqueda), adentro = true)
                }
                Espacio(40.dp)
            }
        }
    }
}

/** Una canción encontrada (de Apple): escucharla 30 segundos, ir a Apple Music o, al tocarla, buscar su álbum. */
@Composable
private fun FilaCancionBuscada(vm: CajonViewModel, c: io.github.fogonapps.social.CancionSocial) {
    Row(Modifier.fillMaxWidth().clip(forma(14.dp)).clickable { vm.cambiarSeccion(Secciones.MUSICA); vm.buscar(c.album.ifBlank { c.titulo } + " " + c.artista) }
        .padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        coil3.compose.AsyncImage(c.portada, null, Modifier.size(48.dp).clip(forma(8.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        Column(Modifier.weight(1f)) {
            Text(c.titulo, style = MaterialTheme.typography.bodyMedium, color = FogonTema.actual.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(c.artista, c.album).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = FogonTema.actual.textoSuave, maxLines = 1, overflow = TextOverflow.Ellipsis)
            io.github.fogonapps.social.ui.EnlaceAppleMusic(c.enlace)
        }
        io.github.fogonapps.social.ui.BotonAvance(c.audio)
    }
}

/** Antes de escribir: para qué sirve cada sección y el botón de agregar a mano. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SinBuscar(vm: CajonViewModel, e: Estado, b: Busqueda) {
    val f = FogonTema.actual
    // Con su propio scroll: los géneros de abajo no caben en la pantalla.
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Recientes(vm)
        LoDelMomento(vm, b.seccion)
        Secciones.BUSQUEDA.drop(1).forEach { s ->
            Row(Modifier.fillMaxWidth().vidrio().clickable { vm.cambiarSeccion(s) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Ficha(if (s == b.seccion) f.tinta1 else f.tinta2, iconoSeccion(s))
                Column(Modifier.weight(1f)) {
                    Text(nombreSeccion(s), style = MaterialTheme.typography.bodyLarge, color = f.texto)
                    Text(stringResource(when (s) { Secciones.PELICULAS -> R.string.seccion_peliculas_pista; Secciones.SERIES -> R.string.seccion_series_pista
                        Secciones.ANIMES -> R.string.seccion_animes_pista; Secciones.MUSICA -> R.string.seccion_musica_pista
                        Secciones.PERSONAS -> R.string.red_personas_pista; Secciones.JUEGOS -> R.string.seccion_juegos_pista
                        else -> R.string.seccion_libros_pista }),
                        style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                }
            }
        }
        if (b.seccion == Secciones.PERSONAS) { Pista(stringResource(R.string.red_personas_pista)); Espacio(40.dp); return@Column }
        BotonSecundario(stringResource(R.string.agregar_a_mano), { vm.reemplazar(Pantalla.EditarObra(null, tipoAMano(b.seccion))) }, Modifier.fillMaxWidth())
        // Los géneros, para buscar por uno (como en las apps de música), en todas las secciones: los del cajón primero,
        // después los comunes de la sección. Tocar uno busca en la sección elegida, sin cambiarla.
        Text(stringResource(R.string.generos), style = MaterialTheme.typography.titleMedium, color = f.texto, modifier = Modifier.padding(top = 8.dp))
        val cine = stringArrayResource(R.array.generos_cine).toList()
        val anime = stringArrayResource(R.array.generos_anime).toList()
        val musica = io.github.fogonapps.cajon.datos.Logica.GENEROS_COMUNES.keys.take(24)
        val libros = stringArrayResource(R.array.generos_libros).toList()
        val lista = remember(b.seccion, e) { vm.generosParaBuscar(b.seccion, cine, anime, musica, libros) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            lista.forEach { g -> Pildora(g) { vm.buscar(g) } }
        }
        Espacio(40.dp)
    }
}

/**
 * Lo del momento, según la sección elegida: arriba lo más visto, escuchado o leído de la semana (con portada), después
 * lo que más se busca hoy en Wikipedia (títulos para tocar y buscar) y, en música, los artistas de la semana.
 * [compacto]: una sola fila y los más buscados, para debajo de los resultados.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun LoDelMomento(vm: CajonViewModel, seccion: String, compacto: Boolean = false) {
    val f = FogonTema.actual
    val m by vm.momento.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.cargarMomento() }
    if (seccion == Secciones.PERSONAS) return
    val tipos = when (seccion) {
        Secciones.PELICULAS, Secciones.CINE -> listOf(Tipos.PELICULA)
        Secciones.SERIES -> listOf(Tipos.SERIE)
        Secciones.ANIMES -> listOf(Tipos.ANIME)
        Secciones.MUSICA -> listOf(Tipos.ALBUM)
        Secciones.LIBROS -> listOf(Tipos.LIBRO)
        Secciones.JUEGOS -> listOf(Tipos.JUEGO)
        else -> listOf(Tipos.PELICULA, Tipos.SERIE, Tipos.ANIME, Tipos.ALBUM, Tipos.LIBRO)
    }
    // Con todo: un poco de cada tipo, intercalado.
    val porTipo = tipos.map { m.obras[it].orEmpty() }
    val obras = (if (tipos.size == 1) porTipo.first() else (0 until 4).flatMap { i -> porTipo.mapNotNull { it.getOrNull(i) } })
        .distinctBy { it.fuente }.take(if (compacto) 3 else 12)
    val buscados = tipos.flatMap { t -> m.buscados[t].orEmpty().map { t to it } }
        .let { l -> if (tipos.size == 1) l else l.sortedByDescending { it.second.vistas } }.take(if (compacto) 6 else 12)
    val artistas = if (!compacto && (seccion == Secciones.TODO || seccion == Secciones.MUSICA)) m.artistas.filter { it.foto.isNotBlank() }.take(10) else emptyList()
    // Por plataforma (con la clave de TMDB): lo de moda en Netflix, Prime Video, Crunchyroll…
    val enPlataforma by vm.enPlataforma.collectAsStateWithLifecycle()
    val tipoPlataforma = tipos.singleOrNull()?.takeIf { it in setOf(Tipos.PELICULA, Tipos.SERIE, Tipos.ANIME) }
    var plataforma by rememberSaveable(seccion) { mutableStateOf<String?>(null) }
    LaunchedEffect(plataforma, tipoPlataforma) { val p = plataforma; if (p != null && tipoPlataforma != null) vm.cargarPlataforma(p, tipoPlataforma) }
    val dePlataforma = plataforma?.let { p -> tipoPlataforma?.let { enPlataforma["$p|$it"] } }
    if (obras.isEmpty() && buscados.isEmpty() && artistas.isEmpty() && m.avisos.isEmpty()) return

    // Dentro de la búsqueda y sin nada que mostrar (sin internet): ni el título, que quedaba solo.
    if (compacto && obras.isEmpty() && buscados.isEmpty() && artistas.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.lo_del_momento), style = MaterialTheme.typography.titleMedium, color = f.texto)
        // Dentro de la búsqueda, el aviso ya sale arriba; aquí solo cuando Lo del momento va solo.
        if (!compacto) io.github.fogonapps.cajon.resumirAvisos(m.avisos).forEach { Pista(stringResource(it)) }
        if (!compacto && tipoPlataforma != null) {
            val nombres = if (tipoPlataforma == Tipos.ANIME) vm.plataformasAnime else vm.plataformasCine
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pildora(stringResource(R.string.todo), acento = plataforma == null) { plataforma = null }
                nombres.forEach { n -> Pildora(nombrePlataforma(n), acento = plataforma == n) { plataforma = n } }
            }
        }
        if (plataforma != null) {
            val l = dePlataforma
            when {
                l == null -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = f.tinta1, trackColor = f.linea)
                l.isEmpty() && m.avisos.isEmpty() -> Pista(stringResource(R.string.momento_plataforma_vacia, nombrePlataforma(plataforma.orEmpty())))
                else -> l.take(12).chunked(3).forEach { fila ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        fila.forEach { r ->
                            Column(Modifier.weight(1f).clip(io.github.fogonapps.identidad.forma(12.dp)).clickable { vm.abrirDelMomento(r) }) {
                                Portada(r.portada, r.tipo, r.titulo, Modifier.fillMaxWidth(), conTitulo = false)
                                Text(r.titulo, style = MaterialTheme.typography.labelSmall, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                        repeat(3 - fila.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
            Text(stringResource(R.string.momento_credito_justwatch), style = MaterialTheme.typography.labelSmall, color = f.textoSuave)
            return@Column
        }
        if (obras.isNotEmpty()) {
            Text(stringResource(when (seccion) {
                Secciones.MUSICA -> R.string.momento_musica; Secciones.LIBROS -> R.string.momento_libros
                Secciones.SERIES -> if (m.conTvMaze) R.string.momento_estrenos else R.string.momento_visto
                Secciones.PELICULAS, Secciones.CINE, Secciones.ANIMES -> R.string.momento_visto
                else -> R.string.momento_todo
            }), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            obras.chunked(3).forEach { fila ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    fila.forEach { r ->
                        Column(Modifier.weight(1f).clip(io.github.fogonapps.identidad.forma(12.dp)).clickable { vm.abrirDelMomento(r) }) {
                            Portada(r.portada, r.tipo, r.titulo, Modifier.fillMaxWidth(), conTitulo = false)
                            Text(r.titulo, style = MaterialTheme.typography.labelSmall, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    repeat(3 - fila.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
        if (buscados.isNotEmpty()) {
            Text(stringResource(R.string.momento_buscado), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                buscados.forEach { (t, x) ->
                    Pildora(x.titulo, iconoSeccion(Secciones.de(t))) {
                        if (x.tmdb.isNotBlank()) vm.abrirDelMomento(io.github.fogonapps.cajon.datos.Encontrado(t, x.titulo, null, "", "", x.tmdb))
                        else vm.buscarEn(Secciones.de(t), x.titulo)
                    }
                }
            }
        }
        if (artistas.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            artistas.forEach { a ->
                Column(Modifier.width(76.dp).clip(io.github.fogonapps.identidad.forma(14.dp)).clickable { vm.abrir(Pantalla.PreviaArtista(a)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    FotoArtista(a.foto, a.nombre, 68.dp)
                    Text(a.nombre, style = MaterialTheme.typography.labelSmall, color = f.texto, maxLines = 1)
                }
            }
        }
        if (m.conTvMaze && Tipos.SERIE in tipos && m.obras[Tipos.SERIE].orEmpty().isNotEmpty()) {
            val uri = androidx.compose.ui.platform.LocalUriHandler.current
            Text(stringResource(R.string.momento_credito_tvmaze), style = MaterialTheme.typography.labelSmall, color = f.textoSuave,
                modifier = Modifier.clip(io.github.fogonapps.identidad.forma(8.dp)).clickable { uri.openUri(io.github.fogonapps.cajon.datos.TvMaze.SITIO) })
        }
    }
}

/** El nombre de una plataforma como se dice aquí ("Amazon Prime Video" → "Prime Video"). */
private fun nombrePlataforma(n: String) = when (n) { "Amazon Prime Video" -> "Prime Video"; "Disney Plus" -> "Disney+"; else -> n }

private fun tipoAMano(seccion: String) = when (seccion) {
    Secciones.MUSICA -> Tipos.ALBUM; Secciones.LIBROS -> Tipos.LIBRO; Secciones.SERIES -> Tipos.SERIE; Secciones.ANIMES -> Tipos.ANIME
    Secciones.JUEGOS -> Tipos.JUEGO; else -> Tipos.PELICULA
}

@Composable
private fun Titulo(texto: String, onVerMas: (() -> Unit)?) {
    Seccion(texto) { if (onVerMas != null) Pildora(stringResource(R.string.ver_mas), onClick = onVerMas) }
}

/** La foto redonda de una persona (o su ícono si no tiene: micrófono, libro o claqueta, según [tipo]). */
@Composable
fun FotoArtista(foto: String, nombre: String, tamano: Dp, tipo: String = "") {
    val f = FogonTema.actual
    Box(Modifier.size(tamano).clip(CircleShape).background(f.superficie).border(f.borde, f.linea, CircleShape), contentAlignment = Alignment.Center) {
        // Sin foto: sus iniciales (un micrófono repetido en cada artista no dice quién es).
        val iniciales = nombre.split(' ').filter { it.firstOrNull()?.isLetterOrDigit() == true }.take(2).joinToString("") { it.take(1).uppercase() }
        // (Van debajo de la foto: si la foto no carga, se ven igual.)
        if (iniciales.isNotEmpty()) Text(iniciales, fontFamily = f.titulos, fontSize = (tamano.value * 0.34f).sp, color = f.acentoTexto, maxLines = 1)
        else IconoRiso(when (tipo) { Personas.AUTOR -> Iconos.Libro; Personas.DIRECTOR -> Iconos.Pelicula; else -> Iconos.Artista }, null, tamano = tamano * 0.4f, tinta = f.acentoTexto)
        // En listas y cuadrículas, la foto de Wikimedia más chica (200 px en vez de 400): baja el doble de rápido.
        val url = if (tamano < 100.dp) foto.replace("?width=400", "?width=200") else foto
        // Recortada un poco hacia arriba: en los retratos la cabeza está arriba, no en el centro.
        if (foto.isNotBlank()) coil3.compose.AsyncImage(url, nombre, Modifier.matchParentSize(), contentScale = ContentScale.Crop,
            alignment = androidx.compose.ui.BiasAlignment(0f, -0.5f))
    }
}

@Composable
private fun MejorArtista(vm: CajonViewModel, e: Estado, a: ArtistaEncontrado) {
    val f = FogonTema.actual
    val ya = e.artistasVivos.any { it.fuente == a.fuente }
    Row(Modifier.aparecer(0).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).vidrio().clickable { vm.abrir(Pantalla.PreviaArtista(a)) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        FotoArtista(a.foto, a.nombre, 76.dp, a.tipo)
        Column(Modifier.weight(1f)) {
            Text(a.nombre, fontFamily = f.titulos, fontSize = 22.sp, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOf(nombreTipo(if (a.tipo == Personas.AUTOR || a.tipo == Personas.DIRECTOR) a.tipo else CajonViewModel.TIPO_ARTISTA, plural = false), a.pais, a.desde?.toString() ?: "").filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        }
        if (ya) IconoRiso(Iconos.Chulo, stringResource(R.string.ya_en_cajon), tamano = 20.dp, tinta = f.acentoTexto)
    }
}

@Composable
private fun ArtistaRedondo(vm: CajonViewModel, a: ArtistaEncontrado) {
    val f = FogonTema.actual
    Column(Modifier.width(92.dp).clip(io.github.fogonapps.identidad.forma(12.dp)).clickable { vm.abrir(Pantalla.PreviaArtista(a)) },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FotoArtista(a.foto, a.nombre, 84.dp, a.tipo)
        Text(a.nombre, style = MaterialTheme.typography.labelMedium, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FilaArtista(vm: CajonViewModel, e: Estado, a: ArtistaEncontrado, modifier: Modifier) {
    val f = FogonTema.actual
    val ya = e.artistasVivos.any { it.fuente == a.fuente }
    FilaVidrio({ vm.abrir(Pantalla.PreviaArtista(a)) }, modifier) {
        FotoArtista(a.foto, a.nombre, 46.dp, a.tipo)
        Column(Modifier.weight(1f)) {
            Text(a.nombre, style = MaterialTheme.typography.bodyLarge, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(tipoArtista(a.tipo), a.pais, a.desde?.toString() ?: "", a.detalle).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (ya) IconoRiso(Iconos.Chulo, stringResource(R.string.ya_en_cajon), tamano = 18.dp, tinta = f.acentoTexto)
    }
}

@Composable
private fun FilaEncontrado(vm: CajonViewModel, e: Estado, r: Encontrado, modifier: Modifier) {
    val f = FogonTema.actual
    val trayendo by vm.trayendo.collectAsStateWithLifecycle()
    val ya = vm.L.porFuente(e, r.fuente) != null
    FilaVidrio({ vm.abrirEncontrado(r) }, modifier) {
        Portada(r.portada, r.tipo, r.titulo, Modifier.width(46.dp), esquina = 6.dp, conTitulo = false)
        Column(Modifier.weight(1f)) {
            Text(r.titulo, style = MaterialTheme.typography.bodyLarge, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val nota = r.publica?.let { "★ " + String.format(LocalConfiguration.current.locales[0], "%.1f", it) } ?: ""
            // La nota va antes de lo largo (plataformas, formato): al final quedaba cortada en unos y en otros no.
            Text(listOf(nombreTipo(r.tipo, plural = false), r.anio?.toString() ?: "", nota, r.creador, r.detalle).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        when {
            trayendo == r.fuente -> CircularProgressIndicator(Modifier.size(20.dp), color = f.tinta1, strokeWidth = 2.dp)
            ya -> IconoRiso(Iconos.Chulo, stringResource(R.string.ya_en_cajon), tamano = 18.dp, tinta = f.acentoTexto)
        }
    }
}

@Composable
fun nombreSeccion(s: String): String = stringResource(when (s) {
    Secciones.CINE -> R.string.seccion_cine
    Secciones.PELICULAS -> R.string.seccion_peliculas
    Secciones.SERIES -> R.string.seccion_series
    Secciones.ANIMES -> R.string.seccion_animes
    Secciones.MUSICA -> R.string.seccion_musica
    Secciones.LIBROS -> R.string.seccion_libros
    Secciones.JUEGOS -> R.string.seccion_juegos
    Secciones.PERSONAS -> R.string.red_personas
    else -> R.string.todo
})

fun iconoSeccion(s: String) = when (s) {
    Secciones.CINE, Secciones.PELICULAS -> Iconos.Pelicula
    Secciones.SERIES -> Iconos.de(Tipos.SERIE)
    Secciones.ANIMES -> Iconos.de(Tipos.ANIME)
    Secciones.MUSICA -> Iconos.Disco
    Secciones.LIBROS -> Iconos.Libro
    Secciones.JUEGOS -> Iconos.Juego
    Secciones.PERSONAS -> Iconos.Gente
    else -> Iconos.Cajon
}

/** De dónde salen los datos de una persona, por su fuente. */
fun fuentePersona(fuente: String) = when {
    fuente.startsWith("ol:") -> R.string.fuente_open_library
    fuente.startsWith("tmdb:") -> R.string.fuente_tmdb
    else -> R.string.fuente_musicbrainz
}

/** De dónde salen los datos de cada tipo (se muestra como crédito). */
fun fuenteDe(tipo: String) = when (tipo) {
    Tipos.LIBRO -> R.string.fuente_open_library
    Tipos.ALBUM, CajonViewModel.TIPO_ARTISTA -> R.string.fuente_musicbrainz
    Tipos.JUEGO -> R.string.fuente_wikidata
    else -> R.string.fuente_tmdb
}

/** El tipo de artista de MusicBrainz, en el idioma de la app. */
@Composable
fun tipoArtista(t: String): String = when (t) {
    "Person" -> stringResource(R.string.artista_persona)
    "Group" -> stringResource(R.string.artista_grupo)
    "Orchestra" -> stringResource(R.string.artista_orquesta)
    "Choir" -> stringResource(R.string.artista_coro)
    Personas.AUTOR -> stringResource(R.string.tipo_autor)
    Personas.DIRECTOR -> stringResource(R.string.tipo_director)
    else -> t
}

/** X-07: las últimas búsquedas (solo en este teléfono): tocar una la repite; la equis la quita. */
@Composable
private fun Recientes(vm: CajonViewModel) {
    val f = FogonTema.actual
    val lista by vm.recientes.lista.collectAsStateWithLifecycle()
    if (lista.isEmpty()) return
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.busquedas_recientes), style = MaterialTheme.typography.titleMedium, color = f.texto, modifier = Modifier.weight(1f))
            androidx.compose.material3.TextButton({ vm.recientes.borrarTodas() }) { Text(stringResource(R.string.busquedas_borrar), color = f.textoSuave) }
        }
        lista.forEach { t ->
            Row(Modifier.fillMaxWidth().clip(io.github.fogonapps.identidad.forma(12.dp)).clickable { vm.buscar(t) }.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconoRiso(io.github.fogonapps.identidad.IconosFogon.Reloj, null, tamano = 18.dp, tinta = f.textoSuave)
                Text(t, style = MaterialTheme.typography.bodyLarge, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                BotonIcono(Iconos.Cerrar, stringResource(R.string.busquedas_quitar, t)) { vm.recientes.quitar(t) }
            }
        }
    }
}

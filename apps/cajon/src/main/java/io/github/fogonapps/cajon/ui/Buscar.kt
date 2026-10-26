package io.github.fogonapps.cajon.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
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
@Composable
fun Buscar(vm: CajonViewModel, e: Estado, p: Pantalla.Buscar) {
    val f = FogonTema.actual
    val b by vm.busqueda.collectAsStateWithLifecycle()
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { foco.requestFocus() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonIcono(Iconos.Atras, stringResource(R.string.volver), grande = true) { vm.cerrar() }
            OutlinedTextField(
                b.texto, { vm.buscar(it.take(80)) }, Modifier.weight(1f).focusRequester(foco),
                placeholder = { Text(stringResource(R.string.buscar_pista)) }, singleLine = true, shape = FormaPildora,
                leadingIcon = { IconoRiso(Iconos.Buscar, null, tamano = 20.dp, tinta = f.textoSuave) },
                trailingIcon = if (b.texto.isNotEmpty()) ({ BotonIcono(Iconos.Cerrar, stringResource(R.string.borrar_texto)) { vm.buscar("") } }) else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        }
        // Las cuatro secciones caben siempre a lo ancho.
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Secciones.TODAS.forEach { s ->
                val elegida = s == b.seccion
                Box(Modifier.weight(1f).clip(FormaPildora).background(if (elegida) f.tinta1 else f.superficie)
                    .border(f.borde, if (elegida) f.tinta1 else f.linea, FormaPildora).clickable { vm.cambiarSeccion(s) }.padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center) {
                    Text(nombreSeccion(s), style = MaterialTheme.typography.labelLarge, color = if (elegida) f.sobreTinta1 else f.texto, maxLines = 1)
                }
            }
        }
        if (b.cargando.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = f.tinta1, trackColor = f.linea)

        if (b.texto.trim().length < 2) { SinBuscar(vm, b); return@Column }

        // Cada fuente llega a su tiempo y sus grupos van arriba de otros: mientras la persona no haya bajado por
        // su cuenta, la lista vuelve arriba para que lo nuevo no quede escondido.
        val lista = androidx.compose.foundation.lazy.rememberLazyListState()
        val llegadas = (b.artistas.size + b.autores.size + b.directores.size) * 1000 + b.obras.values.sumOf { it.size }
        val titulos = mapOf("artistas" to nombreTipo(CajonViewModel.TIPO_ARTISTA), "directores" to nombreTipo(Personas.DIRECTOR), "autores" to nombreTipo(Personas.AUTOR))
        LaunchedEffect(llegadas) { if (lista.firstVisibleItemIndex <= 2 && !lista.isScrollInProgress) lista.scrollToItem(0) }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = lista) {
            val todo = b.seccion == Secciones.TODO
            fun maximo(n: Int) = if (todo) n else Int.MAX_VALUE

            // Las personas de cada sección: artistas (Música), directores (Cine) y escritores (Libros).
            val personas = listOf(Triple("artistas", Secciones.MUSICA, b.artistas), Triple("directores", Secciones.CINE, b.directores),
                Triple("autores", Secciones.LIBROS, b.autores)).filter { todo || b.seccion == it.second }

            // El mejor resultado: la persona más conocida (cada lista viene de la más a la menos) cuyo nombre tiene
            // todas las palabras buscadas, como en las apps de música.
            val palabras = normal(b.texto).split(" ").filter { it.isNotBlank() }
            val exacto = personas.flatMap { it.third }.firstOrNull { a -> normal(a.nombre) == normal(b.texto.trim()) }
                ?: personas.flatMap { it.third }.firstOrNull { a -> palabras.size >= 2 && palabras.all { it in normal(a.nombre) } }
            if (exacto != null) item(key = "mejor") { MejorArtista(vm, e, exacto) }

            // En Todo, una fila de fotos redondas por grupo; en su sección, la lista completa.
            personas.forEach { (clave, seccion, lista0) ->
                val lista = lista0.filter { it != exacto || !todo }
                if (lista.isNotEmpty()) {
                    item(key = "t-$clave") { Titulo(titulos.getValue(clave), if (todo) ({ vm.cambiarSeccion(seccion) }) else null) }
                    if (todo) item(key = "fila-$clave") {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(lista.take(8), key = { it.fuente }) { a -> ArtistaRedondo(vm, a) }
                        }
                    } else items(lista, key = { "a" + it.fuente }) { a -> FilaArtista(vm, e, a, Modifier.animateItem()) }
                }
            }

            // Los grupos de obras, en el orden de la sección.
            val orden = when (b.seccion) {
                Secciones.CINE -> listOf(Tipos.PELICULA, Tipos.SERIE, Tipos.ANIME)
                Secciones.MUSICA -> listOf(Tipos.ALBUM)
                Secciones.LIBROS -> listOf(Tipos.LIBRO)
                else -> listOf(Tipos.ALBUM, Tipos.PELICULA, Tipos.SERIE, Tipos.ANIME, Tipos.LIBRO)
            }
            orden.forEach { t ->
                val lista = b.obras[t].orEmpty()
                if (lista.isNotEmpty()) {
                    item(key = "t-$t") { Titulo(nombreTipo(t), if (todo) ({ vm.cambiarSeccion(Secciones.de(t)) }) else null) }
                    items(lista.take(maximo(4)), key = { "o" + it.fuente }) { r -> FilaEncontrado(vm, e, r, Modifier.animateItem()) }
                }
            }

            // Lo que no se pudo buscar (sin clave de TMDB, sin internet).
            b.errores.forEach { (fuente, error) ->
                item(key = "e-$fuente") {
                    Column(Modifier.padding(16.dp).fillMaxWidth().vidrio().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(error.let { stringResource(it) }, style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                        if (fuente == "tmdb") BotonSecundario(stringResource(R.string.poner_clave_tmdb), { vm.abrir(Pantalla.ClaveTmdb) }, Modifier.fillMaxWidth())
                    }
                }
            }

            item(key = "pie") {
                val vacio = b.cargando.isEmpty() && b.artistas.isEmpty() && b.autores.isEmpty() && b.directores.isEmpty() && b.obras.values.all { it.isEmpty() }
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

/** Antes de escribir: para qué sirve cada sección y el botón de agregar a mano. */
@Composable
private fun SinBuscar(vm: CajonViewModel, b: Busqueda) {
    val f = FogonTema.actual
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Secciones.TODAS.drop(1).forEach { s ->
            Row(Modifier.fillMaxWidth().vidrio().clickable { vm.cambiarSeccion(s) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Ficha(if (s == b.seccion) f.tinta1 else f.tinta2, iconoSeccion(s))
                Column(Modifier.weight(1f)) {
                    Text(nombreSeccion(s), style = MaterialTheme.typography.bodyLarge, color = f.texto)
                    Text(stringResource(when (s) { Secciones.CINE -> R.string.seccion_cine_pista; Secciones.MUSICA -> R.string.seccion_musica_pista; else -> R.string.seccion_libros_pista }),
                        style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                }
            }
        }
        BotonSecundario(stringResource(R.string.agregar_a_mano), { vm.reemplazar(Pantalla.EditarObra(null, tipoAMano(b.seccion))) }, Modifier.fillMaxWidth())
    }
}

private fun tipoAMano(seccion: String) = when (seccion) { Secciones.MUSICA -> Tipos.ALBUM; Secciones.LIBROS -> Tipos.LIBRO; else -> Tipos.PELICULA }

@Composable
private fun Titulo(texto: String, onVerMas: (() -> Unit)?) {
    Seccion(texto) { if (onVerMas != null) Pildora(stringResource(R.string.ver_mas), onClick = onVerMas) }
}

/** La foto redonda de una persona (o su ícono si no tiene: micrófono, libro o claqueta, según [tipo]). */
@Composable
fun FotoArtista(foto: String, nombre: String, tamano: Dp, tipo: String = "") {
    val f = FogonTema.actual
    Box(Modifier.size(tamano).clip(CircleShape).background(f.superficie).border(f.borde, f.linea, CircleShape), contentAlignment = Alignment.Center) {
        IconoRiso(when (tipo) { Personas.AUTOR -> Iconos.Libro; Personas.DIRECTOR -> Iconos.Pelicula; else -> Iconos.Artista }, null, tamano = tamano * 0.4f, tinta = f.acentoTexto)
        if (foto.isNotBlank()) coil3.compose.AsyncImage(foto, nombre, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
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
            Text(listOf(nombreTipo(r.tipo, plural = false), r.anio?.toString() ?: "", r.creador, r.detalle, nota).filter { it.isNotBlank() }.joinToString(" · "),
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
    Secciones.MUSICA -> R.string.seccion_musica
    Secciones.LIBROS -> R.string.seccion_libros
    else -> R.string.todo
})

fun iconoSeccion(s: String) = when (s) {
    Secciones.CINE -> Iconos.Pelicula
    Secciones.MUSICA -> Iconos.Disco
    Secciones.LIBROS -> Iconos.Libro
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

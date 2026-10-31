package io.github.fogonapps.cajon.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.border
import androidx.compose.material3.CircularProgressIndicator
import io.github.fogonapps.cajon.Secciones
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.LinearProgressIndicator
import java.text.NumberFormat
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.Pantalla
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Estados
import io.github.fogonapps.cajon.datos.Obra
import io.github.fogonapps.cajon.datos.Registro
import io.github.fogonapps.cajon.datos.Temporada
import io.github.fogonapps.cajon.datos.Tipos
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.aparecer
import io.github.fogonapps.identidad.forma

/** Lo de arriba de la ficha: el fondo (cine), la portada y los datos de afuera. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Encabezado(o: Obra, vm: CajonViewModel, conGeneros: Boolean = true) {
    val f = FogonTema.actual
    Box(Modifier.fillMaxWidth()) {
        if (o.fondo.isNotBlank()) coil3.compose.AsyncImage(
            model = o.fondo, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 16.dp).clip(forma(18.dp)).background(f.superficie),
        )
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = if (o.fondo.isNotBlank()) 96.dp else 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
            Portada(o.portada, o.tipo, o.titulo, Modifier.width(118.dp).aparecer(0))
            Column(Modifier.weight(1f).padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(o.titulo, fontFamily = f.titulos, fontSize = 22.sp, lineHeight = 26.sp, color = f.texto, maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (o.original.isNotBlank()) Text(o.original, style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 2)
                Text(listOfNotNull(o.anio?.toString(), o.creador.takeIf { it.isNotBlank() }).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = f.texto)
                Text(medida(o), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                NotaPublica(o.fuente, o.publica, o.votos)
            }
        }
    }
    if (conGeneros && o.generos.isNotEmpty()) FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        o.generos.forEach { g ->
            // En los álbumes, tocar un género lo busca (artistas y álbumes de ese género).
            if (o.tipo == Tipos.ALBUM) Pildora(g) { vm.buscarGenero(g) } else Etiqueta(g, f.superficie, f.texto)
        }
    }
}

/** La nota del público en su fuente: "★ 7,8 de 10 en TMDB · 11.536 votos". */
@Composable
fun NotaPublica(fuente: String, publica: Double?, votos: Int, modifier: Modifier = Modifier) {
    if (publica == null) return
    val f = FogonTema.actual
    val idioma = LocalConfiguration.current.locales[0]
    val (escala, nombre) = when {
        fuente.startsWith("tmdb:") -> 10 to R.string.nombre_tmdb
        fuente.startsWith("ol:") -> 5 to R.string.nombre_open_library
        else -> 5 to R.string.nombre_musicbrainz
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconoRiso(Iconos.Estrella, null, tamano = 14.dp, tinta = f.acentoTexto, relleno = f.acentoTexto)
        Text(pluralStringResource(R.plurals.nota_publica, votos, String.format(idioma, "%.1f", publica), escala, stringResource(nombre),
            NumberFormat.getIntegerInstance(idioma).format(votos)), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
    }
}

/** "1 h 52 min", "3 temporadas · 30 episodios", "432 páginas", "10 canciones · 47 min". */
@Composable
private fun medida(o: Obra): String = when {
    Tipos.porEpisodios(o.tipo) && o.temporadas.isNotEmpty() -> pluralStringResource(R.plurals.temporadas, o.temporadas.size, o.temporadas.size) + " · " +
        pluralStringResource(R.plurals.episodios, o.temporadas.sumOf { it.episodios }, o.temporadas.sumOf { it.episodios })
    o.tipo == Tipos.LIBRO && o.paginas != null -> pluralStringResource(R.plurals.paginas, o.paginas, o.paginas)
    o.tipo == Tipos.ALBUM && o.canciones.isNotEmpty() -> pluralStringResource(R.plurals.canciones, o.canciones.size, o.canciones.size) +
        (o.minutos?.let { " · " + stringResource(R.string.minutos_n, it) } ?: "") + (o.formato.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
    o.minutos != null -> if (o.minutos >= 60) stringResource(R.string.horas_minutos, o.minutos / 60, o.minutos % 60) else stringResource(R.string.minutos_n, o.minutos)
    else -> nombreTipo(o.tipo, plural = false)
}

/** Una obra encontrada afuera, antes de agregarla: se elige si ya se vio, se está viendo o queda pendiente. */
@Composable
fun Previa(vm: CajonViewModel, o: Obra) {
    val f = FogonTema.actual
    val trayendo by vm.trayendo.collectAsStateWithLifecycle()
    Cabecera(nombreTipo(o.tipo, plural = false), vm::cerrar)
    // Mientras llegan la sinopsis, las canciones y los géneros, una línea fina arriba (la ficha ya se puede usar).
    if (trayendo == o.fuente) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(2.dp), color = f.tinta1, trackColor = f.linea)
    Encabezado(o, vm)
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BotonRiso(nombreEstado(Estados.QUIERO, o.tipo), { vm.agregar(o, Estados.QUIERO) }, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonSecundario(nombreEstado(Estados.EN_CURSO, o.tipo), { vm.agregar(o, Estados.EN_CURSO) }, Modifier.weight(1f))
            BotonSecundario(nombreEstado(Estados.TERMINADO, o.tipo), { vm.agregar(o, Estados.TERMINADO) }, Modifier.weight(1f))
        }
    }
    Sinopsis(o.sinopsis)
    if (o.canciones.isNotEmpty()) {
        Seccion(stringResource(R.string.canciones))
        o.canciones.forEachIndexed { i, c -> Text("${i + 1}. ${c.titulo}", style = MaterialTheme.typography.bodyMedium, color = f.texto, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) }
    }
    Pista(stringResource(fuenteDe(o.tipo)), Modifier.padding(top = 12.dp))
}

@Composable
private fun Sinopsis(texto: String) {
    if (texto.isBlank()) return
    val f = FogonTema.actual
    var abierta by rememberSaveable { mutableStateOf(false) }
    Seccion(stringResource(R.string.sinopsis))
    Text(texto, style = MaterialTheme.typography.bodyMedium, color = f.texto, maxLines = if (abierta) Int.MAX_VALUE else 6, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).clickable { abierta = !abierta })
    if (!abierta && texto.length > 300) Text(stringResource(R.string.leer_mas), style = MaterialTheme.typography.labelLarge, color = f.acentoTexto,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp).clickable { abierta = true })
}

/** "Anotar cuándo lo vi", "…lo leí", "…lo escuché": guarda en el diario el día que se vio, leyó o escuchó. */
@Composable
fun textoAnotar(tipo: String) = stringResource(when (tipo) { Tipos.LIBRO -> R.string.anotar_leido; Tipos.ALBUM -> R.string.anotar_escuchado; else -> R.string.anotar_visto })

/** "Cuándo lo viste", "…lo leíste", "…lo escuchaste": la lista de esas fechas en la ficha. */
@Composable
fun textoCuando(tipo: String) = stringResource(when (tipo) { Tipos.LIBRO -> R.string.cuando_leido; Tipos.ALBUM -> R.string.cuando_escuchado; else -> R.string.cuando_visto })

/** La ficha de una obra del cajón: todo lo de afuera y todo lo de la persona. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Ficha(vm: CajonViewModel, e: Estado, id: String) {
    val f = FogonTema.actual
    val confirmar = LocalConfirmar.current
    val o = e.vivas.firstOrNull { it.id == id }
    if (o == null) { Cabecera("", vm::cerrar); Pista(stringResource(R.string.ya_no_esta)); return }
    LaunchedEffect(id) { vm.renovarSiHaceFalta(o) }
    val idioma = vm.idioma()
    val acciones = LocalAcciones.current

    var compartiendo by rememberSaveable { mutableStateOf(false) }
    Cabecera(nombreTipo(o.tipo, plural = false), vm::cerrar) {
        if (acciones != null) BotonIcono(Iconos.Compartir, stringResource(R.string.compartir), grande = true) { compartiendo = true }
        // Lo que viene de una fuente oficial (TMDB, Open Library, MusicBrainz) no se edita: solo se puede cambiar la portada.
        BotonIcono(Iconos.Imagen, stringResource(R.string.cambiar_portada), grande = true) { vm.abrir(Pantalla.CambiarPortada(o.id)) }
        if (o.fuente.isEmpty()) BotonIcono(Iconos.Lapiz, stringResource(R.string.corregir_datos), grande = true) { vm.abrir(Pantalla.EditarObra(o.id, o.tipo)) }
    }
    if (compartiendo && acciones != null) DialogoCompartir({ compartiendo = false }, { acciones.compartir(it, o.titulo) }) { TarjetaObra(o) }
    Encabezado(o, vm, conGeneros = false)

    // Sin fuente y sin clave de TMDB, las películas, series y animes no pueden traer portada ni datos: se dice cómo.
    if (o.fuente.isEmpty() && o.tipo != Tipos.LIBRO && o.tipo != Tipos.ALBUM && !vm.hayClaveTmdb()) Column(Modifier.padding(16.dp).fillMaxWidth().vidrio().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.falta_tmdb_ficha), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        BotonSecundario(stringResource(R.string.poner_clave_tmdb), { vm.abrir(Pantalla.ClaveTmdb) }, Modifier.fillMaxWidth())
    }

    // Los géneros, como etiquetas (tocar uno busca lo del cajón con ese género).
    EditorEtiquetas("g" + o.id, stringResource(R.string.generos), o.generos, { vm.sugerenciasEtiquetas(Secciones.tipos(Secciones.de(o.tipo))) },
        { vm.ponerGenerosObra(o.id, it) }, { vm.buscarEtiqueta(it, Secciones.de(o.tipo)) }, stringResource(R.string.agregar_genero))

    // El estado, como píldoras.
    Selector(Estados.TODOS.map { it to nombreEstado(it, o.tipo) }, o.estado, { vm.ponerEstado(o.id, it) }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

    // La nota, el corazón y las favoritas.
    Column(Modifier.aparecer(1).fillMaxWidth().padding(16.dp).vidrio().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tu_nota), style = MaterialTheme.typography.labelLarge, color = f.textoSuave, modifier = Modifier.weight(1f))
            val fav = o.id in e.ajustes.favoritas
            BotonIcono(Iconos.Estrella, stringResource(if (fav) R.string.quitar_favorita else R.string.poner_favorita), tinta = if (fav) f.acentoTexto else f.textoSuave, lleno = fav) { vm.favorita(o.id) }
            BotonIcono(Iconos.Corazon, stringResource(R.string.me_gusta), tinta = if (o.meGusta) f.acentoTexto else f.textoSuave, lleno = o.meGusta) { vm.meGusta(o.id) }
        }
        ElegirEstrellas(o.nota, { vm.ponerNota(o.id, it) }, descripcion = stringResource(R.string.tu_nota))
        BotonRiso(textoAnotar(o.tipo), { vm.abrir(Pantalla.Registrar(o.id)) }, Modifier.fillMaxWidth())
        BotonSecundario(stringResource(R.string.agregar_a_lista), { vm.abrir(Pantalla.AgregarALista(o.id)) }, Modifier.fillMaxWidth())
    }

    // El progreso: páginas o canciones.
    when {
        o.tipo == Tipos.LIBRO -> Paginas(vm, o)
        o.tipo == Tipos.ALBUM && o.canciones.isNotEmpty() -> Canciones(vm, o)
    }

    Sinopsis(o.sinopsis)

    // Lo que escribe la persona.
    Seccion(stringResource(R.string.tu_resena))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        var resena by rememberSaveable(o.id) { mutableStateOf(o.resena) }
        OutlinedTextField(resena, { resena = it; vm.ponerResena(o.id, it) }, Modifier.fillMaxWidth(), minLines = 3,
            placeholder = { Text(stringResource(R.string.resena_pista)) })
        // Los datos curiosos solo se muestran si hay algo (o si la persona quiere escribir).
        var notas by rememberSaveable(o.id) { mutableStateOf(o.notas) }
        var conNotas by rememberSaveable(o.id) { mutableStateOf(o.notas.isNotBlank()) }
        if (conNotas) OutlinedTextField(notas, { notas = it; vm.ponerNotas(o.id, it) }, Modifier.fillMaxWidth(), minLines = 2,
            label = { Text(stringResource(R.string.datos_curiosos)) })
        else Pildora(stringResource(R.string.agregar_datos_curiosos), Iconos.Mas) { conNotas = true }
    }
    // Las etiquetas propias ("para ver con amigos", "favorita de 2024"…): como los géneros, sin comas.
    EditorEtiquetas("e" + o.id, stringResource(R.string.etiquetas), o.etiquetas, { vm.sugerenciasEtiquetas(null) },
        { vm.ponerEtiquetasObra(o.id, it) }, { vm.buscarEtiqueta(it, Secciones.de(o.tipo)) }, stringResource(R.string.agregar_etiqueta))

    // Los episodios favoritos de una serie o un anime.
    if (Tipos.porEpisodios(o.tipo)) EpisodiosFavoritos(vm, o)

    // Las veces que la vio, leyó o escuchó, con su fecha (el diario).
    if (o.registros.isNotEmpty()) {
        Seccion(textoCuando(o.tipo))
        o.registros.sortedByDescending { it.fecha }.forEach { r ->
            FilaVidrio({ vm.abrir(Pantalla.Registrar(o.id, r.id)) }) {
                IconoRiso(if (r.otraVez) Iconos.Repetir else Iconos.Diario, null, tamano = 20.dp, tinta = f.textoSuave)
                Column(Modifier.weight(1f)) {
                    Text(fechaCorta(r.fecha, idioma), style = MaterialTheme.typography.bodyLarge, color = f.texto)
                    if (r.texto.isNotBlank()) Text(r.texto, style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Estrellas(r.nota)
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        o.empezada?.let { Dato(stringResource(R.string.empezada), fechaCorta(it, idioma), Modifier.weight(1f)) }
        o.terminada?.let { Dato(stringResource(R.string.terminada), fechaCorta(it, idioma), Modifier.weight(1f)) }
    }

    // El artista del álbum.
    o.artista?.let { aid -> e.artistasVivos.firstOrNull { it.id == aid } }?.let { a ->
        Seccion(stringResource(R.string.tipo_artista))
        FilaVidrio({ vm.abrir(Pantalla.VerArtista(a.id)) }) {
            Ficha(f.tinta2, Iconos.Artista)
            Text(a.nombre, style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.weight(1f))
            IconoRiso(Iconos.Siguiente, null, tamano = 18.dp, tinta = f.textoSuave)
        }
    }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (o.fuente.isNotEmpty()) {
            Pista(stringResource(fuenteDe(o.tipo)), adentro = true)
            BotonSecundario(stringResource(R.string.actualizar_datos), { vm.actualizarDatos(o.id) }, Modifier.fillMaxWidth())
        }
        BotonSecundario(stringResource(R.string.borrar), {
            confirmar(vm.tx(R.string.borrar_obra_pregunta, o.titulo), vm.tx(R.string.borrar_obra_texto)) { vm.borrarObra(o.id) }
        }, Modifier.fillMaxWidth(), peligro = true)
    }
}

/**
 * Los episodios favoritos: se ven como píldoras ("T1 · E5 · Nombre"); "Agregar" deja elegir la temporada y tocar los
 * episodios (con su nombre si la serie es de TMDB y hay clave; si no, por número).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EpisodiosFavoritos(vm: CajonViewModel, o: Obra) {
    val f = FogonTema.actual
    var eligiendo by rememberSaveable(o.id) { mutableStateOf(false) }
    if (o.episodiosFavoritos.isEmpty() && !eligiendo) {
        if (o.temporadas.isNotEmpty()) Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { Pildora(stringResource(R.string.agregar_episodio_favorito), Iconos.Estrella) { eligiendo = true } }
        return
    }
    Seccion(stringResource(R.string.episodios_favoritos)) {
        if (o.temporadas.isNotEmpty()) Pildora(stringResource(if (eligiendo) R.string.listo else R.string.agregar)) { eligiendo = !eligiendo }
    }
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        o.episodiosFavoritos.forEach { e ->
            val (t, ep) = e.substringBefore("|").split("x").let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "") }
            val nombre = e.substringAfter("|", "")
            Pildora(listOf(stringResource(R.string.temporada_episodio, t, ep), nombre).filter { it.isNotBlank() }.joinToString(" · "), if (eligiendo) Iconos.Cerrar else Iconos.Estrella) {
                if (eligiendo) vm.episodioFavorito(o.id, t.toIntOrNull() ?: 0, ep.toIntOrNull() ?: 0, nombre)
            }
        }
    }
    if (eligiendo && o.temporadas.isNotEmpty()) {
        var temporada by rememberSaveable(o.id + "t") { mutableIntStateOf(o.temporadas.first().numero) }
        val eps by vm.episodios.collectAsStateWithLifecycle()
        LaunchedEffect(o.id, temporada) { vm.cargarEpisodios(o.id, temporada) }
        Chips(o.temporadas.map { it.numero to stringResource(R.string.temporada_n_corta, it.numero) }, temporada) { temporada = it }
        val lista = eps?.takeIf { it.first == "${o.id}:$temporada" }?.second
        if (lista == null) Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = f.tinta1) }
        else lista.forEach { (n, nombre) ->
            val es = o.episodiosFavoritos.any { it.substringBefore("|") == "${temporada}x$n" }
            FilaVidrio({ vm.episodioFavorito(o.id, temporada, n, nombre) }) {
                Text("$n", fontFamily = f.cifras, color = f.textoSuave)
                Text(nombre.ifBlank { stringResource(R.string.episodio_numero, n) }, style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.weight(1f))
                IconoRiso(Iconos.Estrella, stringResource(R.string.episodios_favoritos), tamano = 20.dp, tinta = if (es) f.acentoTexto else f.textoSuave, relleno = if (es) f.acentoTexto else null)
            }
        }
    }
}

/** Cambiar la portada: elegir otra (ediciones de Open Library, pósteres de TMDB) o subir una imagen del teléfono. */
@Composable
fun CambiarPortada(vm: CajonViewModel, e: Estado, id: String) {
    val f = FogonTema.actual
    val acciones = LocalAcciones.current
    val o = e.vivas.firstOrNull { it.id == id }
    if (o == null) { Cabecera("", vm::cerrar); Pista(stringResource(R.string.ya_no_esta)); return }
    val c by vm.candidatas.collectAsStateWithLifecycle()
    LaunchedEffect(id) { vm.cargarCandidatas(id) }
    Cabecera(stringResource(R.string.cambiar_portada), vm::cerrar)
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (acciones != null) BotonRiso(stringResource(R.string.subir_imagen), { acciones.elegirImagen { uri -> vm.subirPortada(id, uri) } }, Modifier.fillMaxWidth())
        Pista(stringResource(R.string.subir_imagen_pista), adentro = true)
    }
    val lista = c?.takeIf { it.first == id }?.second
    when {
        lista == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = f.tinta1) }
        lista.size <= 1 -> Pista(stringResource(if (o.fuente.startsWith("tmdb:") && !vm.hayClaveTmdb()) R.string.falta_tmdb_ficha else R.string.sin_otras_portadas))
        else -> {
            Seccion(stringResource(R.string.elegir_otra))
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                lista.chunked(3).forEach { fila ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        fila.forEach { url ->
                            Box(Modifier.weight(1f).clip(forma(12.dp)).clickable { vm.elegirPortada(id, url) }
                                .then(if (url == o.portada) Modifier.border(2.dp, f.tinta1, forma(12.dp)) else Modifier)) {
                                Portada(url, o.tipo, o.titulo, Modifier.fillMaxWidth(), conTitulo = false)
                            }
                        }
                        repeat(3 - fila.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
    Espacio(24.dp)
}

/** Por qué página va (libros). */
@Composable
private fun Paginas(vm: CajonViewModel, o: Obra) {
    val f = FogonTema.actual
    Seccion(stringResource(R.string.tu_lectura))
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).vidrio().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (o.paginas != null) stringResource(R.string.pagina_de, o.pagina, o.paginas) else stringResource(R.string.va_en_pagina, o.pagina),
            style = MaterialTheme.typography.bodyLarge, color = f.texto)
        vm.L.progreso(o)?.let { Barra(it, f.tinta1) }
        var pagina by rememberSaveable(o.id) { mutableStateOf("") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CampoNumero(pagina, { pagina = it }, stringResource(R.string.voy_en_pagina), Modifier.weight(1f))
            BotonSecundario(stringResource(R.string.guardar), { pagina.toIntOrNull()?.let { vm.ponerPagina(o.id, it); pagina = "" } })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10, 25, 50).forEach { n -> Pildora("+$n") { vm.ponerPagina(o.id, o.pagina + n) } }
        }
    }
}

/** Las canciones del álbum, cada una con su nota y su corazón (como en un tracklist con calificación). */
@Composable
private fun Canciones(vm: CajonViewModel, o: Obra) {
    val f = FogonTema.actual
    Seccion(stringResource(R.string.canciones))
    Pista(stringResource(R.string.canciones_pista))
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).vidrio().padding(vertical = 6.dp)) {
        o.canciones.forEachIndexed { i, c ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", fontFamily = f.cifras, color = f.textoSuave, modifier = Modifier.width(28.dp))
                    Text(c.titulo, style = MaterialTheme.typography.bodyMedium, color = f.texto, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (c.segundos > 0) Text("%d:%02d".format(c.segundos / 60, c.segundos % 60), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                    BotonIcono(Iconos.Corazon, stringResource(R.string.cancion_favorita), tinta = if (c.favorita) f.acentoTexto else f.linea, lleno = c.favorita) { vm.cancionFavorita(o.id, i) }
                }
                Box(Modifier.padding(start = 28.dp)) {
                    ElegirEstrellas(c.nota, { vm.notaCancion(o.id, i, it) }, tamano = 20.dp, descripcion = stringResource(R.string.nota_de, c.titulo))
                }
            }
        }
    }
    o.canciones.filter { it.favorita }.takeIf { it.isNotEmpty() }?.let { favs ->
        Pista(stringResource(R.string.tus_favoritas, favs.joinToString(", ") { it.titulo }))
    }
}

/** Anotar en el diario (o corregir un registro): la fecha, la nota de ese día, si fue otra vez y unas palabras. */
@Composable
fun Registrar(vm: CajonViewModel, e: Estado, p: Pantalla.Registrar) {
    val f = FogonTema.actual
    val confirmar = LocalConfirmar.current
    val o = e.vivas.firstOrNull { it.id == p.id }
    if (o == null) { Cabecera("", vm::cerrar); Pista(stringResource(R.string.ya_no_esta)); return }
    val viejo = p.registro?.let { r -> o.registros.firstOrNull { it.id == r } }
    var fecha by rememberSaveable { mutableStateOf(viejo?.fecha ?: vm.hoy().toString()) }
    var nota by rememberSaveable { mutableStateOf(viejo?.nota ?: o.nota) }
    var otraVez by rememberSaveable { mutableStateOf(viejo?.otraVez ?: o.registros.isNotEmpty()) }
    var texto by rememberSaveable { mutableStateOf(viejo?.texto ?: "") }

    Cabecera(textoAnotar(o.tipo), vm::cerrar)
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Portada(o.portada, o.tipo, o.titulo, Modifier.width(64.dp), esquina = 8.dp)
        Text(o.titulo, fontFamily = f.titulos, fontSize = 20.sp, color = f.texto, maxLines = 3)
    }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.cuando), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
        BotonFecha(fecha, vm.idioma(), { fecha = it }, vm.hoy())
        Text(stringResource(R.string.tu_nota), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
        ElegirEstrellas(nota, { nota = it }, descripcion = stringResource(R.string.tu_nota))
        Row(Modifier.fillMaxWidth().vidrio().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.otra_vez_pregunta), style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.weight(1f))
            Switch(otraVez, { otraVez = it })
        }
        OutlinedTextField(texto, { texto = it.take(2000) }, Modifier.fillMaxWidth(), minLines = 3, label = { Text(stringResource(R.string.unas_palabras)) })
        BotonRiso(stringResource(R.string.guardar), { vm.registrar(o.id, Registro(viejo?.id ?: "", fecha, nota, otraVez, texto.trim())) }, Modifier.fillMaxWidth())
        if (viejo != null) BotonSecundario(stringResource(R.string.borrar), {
            confirmar(vm.tx(R.string.borrar_registro_pregunta), null) { vm.borrarRegistro(o.id, viejo.id); vm.cerrar() }
        }, Modifier.fillMaxWidth(), peligro = true)
    }
}

/** Agregar algo a mano (si no está en ninguna fuente) o corregir sus datos. Lo que viene de una fuente no se edita. */
@Composable
fun EditarObra(vm: CajonViewModel, e: Estado, p: Pantalla.EditarObra) {
    val viejo = p.id?.let { id -> e.vivas.firstOrNull { it.id == id } }
    var tipo by rememberSaveable { mutableStateOf(viejo?.tipo ?: p.tipo) }
    var titulo by rememberSaveable { mutableStateOf(viejo?.titulo ?: "") }
    var original by rememberSaveable { mutableStateOf(viejo?.original ?: "") }
    var anio by rememberSaveable { mutableStateOf(viejo?.anio?.toString() ?: "") }
    var creador by rememberSaveable { mutableStateOf(viejo?.creador ?: "") }
    var sinopsis by rememberSaveable { mutableStateOf(viejo?.sinopsis ?: "") }
    var medida by rememberSaveable { mutableStateOf((if (tipo == Tipos.LIBRO) viejo?.paginas else viejo?.minutos)?.toString() ?: "") }
    // Series: "10, 8, 12" = episodios de cada temporada. Álbum: una canción por línea.
    var temporadas by rememberSaveable { mutableStateOf(viejo?.temporadas?.joinToString(", ") { it.episodios.toString() } ?: "") }
    var canciones by rememberSaveable { mutableStateOf(viejo?.canciones?.joinToString("\n") { it.titulo } ?: "") }

    Cabecera(stringResource(if (viejo == null) R.string.agregar_a_mano else R.string.corregir_datos), vm::cerrar)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (viejo == null) Selector(Tipos.TODOS.map { it to nombreTipo(it, plural = false) }, tipo, { tipo = it })
        CampoTexto(titulo, { titulo = it }, stringResource(R.string.titulo), max = 150)
        CampoTexto(original, { original = it }, stringResource(R.string.titulo_original), max = 150)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CampoNumero(anio, { anio = it.take(4) }, stringResource(R.string.anio), Modifier.weight(1f))
            CampoNumero(medida, { medida = it }, stringResource(if (tipo == Tipos.LIBRO) R.string.paginas_campo else R.string.minutos_campo), Modifier.weight(1f))
        }
        CampoTexto(creador, { creador = it }, stringResource(when (tipo) {
            Tipos.LIBRO -> R.string.autor; Tipos.ALBUM -> R.string.tipo_artista; Tipos.PELICULA -> R.string.director; else -> R.string.creador
        }), max = 150)
        if (Tipos.porEpisodios(tipo)) {
            CampoTexto(temporadas, { temporadas = it.filter { c -> c.isDigit() || c == ',' || c == ' ' } }, stringResource(R.string.temporadas_campo), max = 200)
            Pista(stringResource(R.string.temporadas_pista), adentro = true)
        }
        if (tipo == Tipos.ALBUM) OutlinedTextField(canciones, { canciones = it.take(5000) }, Modifier.fillMaxWidth(), minLines = 3,
            label = { Text(stringResource(R.string.canciones_campo)) })
        OutlinedTextField(sinopsis, { sinopsis = it.take(5000) }, Modifier.fillMaxWidth(), minLines = 3, label = { Text(stringResource(R.string.sinopsis)) })
        BotonRiso(stringResource(R.string.guardar), {
            val base = viejo ?: Obra("", tipo, "")
            val eps = temporadas.split(",").mapNotNull { it.trim().toIntOrNull()?.takeIf { n -> n in 1..2000 } }
            val lineas = canciones.lines().map { it.trim() }.filter { it.isNotEmpty() }
            vm.guardarObra(base.copy(
                tipo = tipo, titulo = titulo.trim(), original = original.trim(), anio = anio.toIntOrNull(), creador = creador.trim(),
                sinopsis = sinopsis.trim(),
                paginas = if (tipo == Tipos.LIBRO) medida.toIntOrNull() else base.paginas,
                minutos = if (tipo != Tipos.LIBRO) medida.toIntOrNull() else base.minutos,
                temporadas = if (Tipos.porEpisodios(tipo)) eps.mapIndexed { i, n -> base.temporadas.getOrNull(i)?.copy(episodios = n) ?: Temporada(i + 1, n) } else base.temporadas,
                canciones = if (tipo == Tipos.ALBUM) lineas.mapIndexed { i, t -> base.canciones.getOrNull(i)?.takeIf { it.titulo == t } ?: io.github.fogonapps.cajon.datos.Cancion(t) } else base.canciones,
            ))
        }, Modifier.fillMaxWidth(), habilitado = titulo.isNotBlank())
        // Los géneros y la portada se ponen después, en la ficha (como etiquetas y con "Cambiar la portada").
        Pista(stringResource(R.string.a_mano_pista), adentro = true)
    }
}

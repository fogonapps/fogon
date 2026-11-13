package io.github.fogonapps.cajon.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import kotlinx.serialization.json.put
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import io.github.fogonapps.cajon.datos.Imagenes
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.remember
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.Pantalla
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Estados
import io.github.fogonapps.cajon.datos.Generos
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

private fun etiquetaLibroVisible(o: Obra, texto: String, idioma: String): String =
    if (o.tipo == Tipos.LIBRO) Generos.etiquetaLibro(texto, idioma) else texto

/** Lo de arriba de la ficha: el fondo (cine), la portada y los datos de afuera. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Encabezado(o: Obra, vm: CajonViewModel, conGeneros: Boolean = true, conCreadores: Boolean = true) {
    val f = FogonTema.actual
    // Como en las apps de cine: el fondo arriba, la portada montada sobre su borde de abajo y el título siempre
    // debajo del fondo (nunca la mitad sobre la imagen y la mitad afuera), con la letra según lo largo que sea.
    val conFondo = o.fondo.isNotBlank()
    Box(Modifier.fillMaxWidth()) {
        if (conFondo) coil3.compose.AsyncImage(
            model = o.fondo, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(170.dp).padding(horizontal = 16.dp).clip(forma(18.dp)).background(f.superficie),
        )
        Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 16.dp, top = if (conFondo) 104.dp else 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            var visor by remember { mutableStateOf(false) }
            Portada(o.portada, o.tipo, o.titulo, Modifier.width(112.dp).aparecer(0).clickable { visor = true }, lugar = Imagenes.Lugar.FICHA)
            if (visor) androidx.compose.ui.window.Dialog(onDismissRequest = { visor = false }) {
                // La portada grande; debajo, de dónde salió y, plegado, reportarla.
                Column(Modifier.clip(forma(24.dp)).background(f.fondo).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(o.titulo, style = MaterialTheme.typography.titleMedium, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        BotonIcono(Iconos.Cerrar, stringResource(R.string.volver)) { visor = false }
                    }
                    Portada(o.portada, o.tipo, o.titulo, Modifier.fillMaxWidth(), lugar = Imagenes.Lugar.VISOR)
                    SobreImagen(vm, o.id, o.portada, o.fuente)
                    if (Imagenes.autorizada(o.portada)) {
                        var reportar by remember { mutableStateOf(false) }
                        Pildora(stringResource(R.string.imagen_reportar), Iconos.Bandera, acento = reportar) { reportar = !reportar }
                        if (reportar) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("no_corresponde" to R.string.imagen_no_corresponde, "ofensiva" to R.string.imagen_ofensiva, "derechos" to R.string.imagen_derechos).forEach { (motivo, texto) ->
                                Pildora(stringResource(texto)) { vm.red.reportar("imagen", Imagenes.clave(o.portada), motivo); visor = false }
                            }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).padding(top = if (conFondo) 74.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val largo = o.titulo.length
                Text(o.titulo, fontFamily = f.titulos, fontSize = if (largo <= 22) 22.sp else if (largo <= 40) 19.sp else 17.sp,
                    lineHeight = if (largo <= 22) 26.sp else if (largo <= 40) 23.sp else 21.sp, color = f.texto, maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (o.original.isNotBlank()) Text(o.original, style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 2, overflow = TextOverflow.Ellipsis)
                o.anio?.let { Text("$it", style = MaterialTheme.typography.bodyMedium, color = f.texto) }
            }
        }
    }
    // Quién la hizo: cada nombre se toca para abrir su ficha (director, escritor o artista), sin guardarlo.
    val nombres = o.creador.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val buscando by vm.buscandoPersona.collectAsStateWithLifecycle()
    if (conCreadores && nombres.isNotEmpty()) FlowRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        nombres.take(6).forEach { n ->
            Pildora(if (buscando == n.trimEnd('.')) stringResource(R.string.buscando) else n, if (o.tipo == Tipos.ALBUM) Iconos.Artista else Iconos.Persona) { vm.abrirPersona(n, o.tipo) }
        }
    }
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(medida(o), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        NotaPublica(o.fuente, o.publica, o.votos)
    }
    // A Spotify, Wikipedia, IMDb, Letterboxd, Goodreads… (los que tenga).
    Enlaces(vm, o.fuente)
    if (conGeneros && o.generos.isNotEmpty()) FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Tocar un género lo busca en la sección de la obra: lo del cajón y lo de afuera con ese género (en todos los tipos).
        o.generos.forEach { g -> Pildora(etiquetaLibroVisible(o, g, vm.idioma())) { vm.buscarEtiqueta(g, Secciones.de(o.tipo)) } }
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

/**
 * Quién la hizo (artista, director o autor), una fila por persona con su foto: tocarla abre su ficha sin guardarla.
 * [personas] son las que ya trajo la fuente (todos los artistas de un álbum); si no hay, se busca por el nombre.
 */
@Composable
private fun QuienLaHizo(vm: CajonViewModel, o: Obra, personas: List<io.github.fogonapps.cajon.datos.ArtistaEncontrado>) {
    val f = FogonTema.actual
    val buscando by vm.buscandoPersona.collectAsStateWithLifecycle()
    val nombres = o.creador.split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(6)
    if (personas.isEmpty() && nombres.isEmpty()) return
    Seccion(stringResource(when (o.tipo) { Tipos.ALBUM -> R.string.tipo_artista; Tipos.LIBRO -> R.string.autor; Tipos.PELICULA -> R.string.director; else -> R.string.creador }))
    @Composable
    fun Fila(nombre: String, foto: String, tipo: String, onClick: () -> Unit) = FilaVidrio(onClick) {
        FotoArtista(foto, nombre, 46.dp, tipo)
        Text(if (buscando == nombre.trimEnd('.')) stringResource(R.string.buscando) else nombre, style = MaterialTheme.typography.bodyLarge, color = f.texto,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        IconoRiso(Iconos.Siguiente, null, tamano = 18.dp, tinta = f.textoSuave)
    }
    val tipoPersona = when (o.tipo) { Tipos.ALBUM -> ""; Tipos.LIBRO -> io.github.fogonapps.cajon.datos.Personas.AUTOR; else -> io.github.fogonapps.cajon.datos.Personas.DIRECTOR }
    if (personas.isNotEmpty()) personas.forEach { a -> Fila(a.nombre, a.foto, a.tipo) { vm.abrir(Pantalla.PreviaArtista(a)) } }
    else nombres.forEach { n -> Fila(n, "", tipoPersona) { vm.abrirPersona(n, o.tipo) } }
}

/** Una obra encontrada afuera, antes de agregarla: se elige si ya se vio, se está viendo o queda pendiente. */
@Composable
fun Previa(vm: CajonViewModel, o: Obra, personas: List<io.github.fogonapps.cajon.datos.ArtistaEncontrado> = emptyList()) {
    val f = FogonTema.actual
    val trayendo by vm.trayendo.collectAsStateWithLifecycle()
    Cabecera(nombreTipo(o.tipo, plural = false), vm::cerrar)
    // Mientras llegan la sinopsis, las canciones y los géneros, una línea fina arriba (la ficha ya se puede usar).
    if (trayendo == o.fuente) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(2.dp), color = f.tinta1, trackColor = f.linea)
    // Quién la hizo va en su sección, con foto, antes de la sinopsis (como en la ficha).
    Encabezado(o, vm, conCreadores = false)
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BotonRiso(nombreEstado(Estados.QUIERO, o.tipo), { vm.agregar(o, Estados.QUIERO) }, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonSecundario(nombreEstado(Estados.EN_CURSO, o.tipo), { vm.agregar(o, Estados.EN_CURSO) }, Modifier.weight(1f))
            BotonSecundario(nombreEstado(Estados.TERMINADO, o.tipo), { vm.agregar(o, Estados.TERMINADO) }, Modifier.weight(1f))
        }
    }
    QuienLaHizo(vm, o, personas)
    Sinopsis(o.sinopsis)
    if (o.canciones.isNotEmpty()) {
        Seccion(stringResource(R.string.canciones))
        // Cada canción con su avance (si Apple la tiene) y, al mantenerla, sus acciones.
        var avances by remember(o.titulo) { mutableStateOf<List<io.github.fogonapps.social.CancionSocial>>(emptyList()) }
        LaunchedEffect(o.titulo) { avances = vm.cancionesApple(o.titulo, o.creador) }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).vidrio().padding(vertical = 6.dp)) {
            o.canciones.forEachIndexed { i, c ->
                FilaCancionAlbum(vm, o, i, c.titulo, avances.firstOrNull { vm.L.tituloLimpio(it.titulo) == vm.L.tituloLimpio(c.titulo) })
            }
        }
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
    if (compartiendo && acciones != null) DialogoCompartir({ compartiendo = false }, { acciones.compartir(it, o.titulo) }) { TarjetaObra(o, e.resenas.firstOrNull { it.obra == o.id && !it.borrada && !it.spoiler && it.parte.isBlank() }?.texto.orEmpty()) }
    Encabezado(o, vm, conGeneros = false)
    if (o.fuente.startsWith("ol:")) {
        var elegir by rememberSaveable(id) { mutableStateOf(false) }
        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { Pildora(stringResource(R.string.imagen_edicion), Iconos.Libro, acento = elegir) { elegir = !elegir } }
        val ediciones by vm.ediciones.collectAsStateWithLifecycle()
        val comunidades by vm.portadasComunidad.collectAsStateWithLifecycle()
        // Las ediciones como portadas en fila (se desliza): debajo, editorial, año e idioma.
        if (elegir) androidx.compose.foundation.lazy.LazyRow(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val lista = ediciones[id].orEmpty().filter { it.portada.isNotBlank() && !Imagenes.bloqueada(it.portada, comunidades[id]?.bloqueadas.orEmpty()) }
            items(lista.size) { i ->
                val ed = lista[i]
                val elegida = o.edicion == ed.id
                Column(Modifier.width(104.dp).clip(forma(12.dp)).clickable { vm.elegirEdicion(id, ed); elegir = false }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Portada(ed.portada, o.tipo, o.titulo, Modifier.fillMaxWidth().then(if (elegida) Modifier.border(2.dp, f.tinta1, forma(12.dp)) else Modifier), conTitulo = false)
                    Text(Imagenes.descripcion(ed, vm.idioma(), conId = false), style = MaterialTheme.typography.labelSmall, color = if (elegida) f.acentoTexto else f.textoSuave, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    // Sin fuente y sin clave de TMDB, las películas, series y animes no pueden traer portada ni datos: se dice cómo.

    // Los géneros, como etiquetas (tocar uno busca lo del cajón con ese género).
    val tituloEtiquetas = stringResource(if (o.tipo == Tipos.LIBRO) R.string.temas else R.string.generos)
    val textoAgregar = stringResource(if (o.tipo == Tipos.LIBRO) R.string.agregar_tema else R.string.agregar_genero)
    EditorEtiquetas("g" + o.id, tituloEtiquetas, o.generos, { vm.sugerenciasEtiquetas(Secciones.tipos(Secciones.de(o.tipo).let { if (it in Secciones.DE_CINE) Secciones.CINE else it })) },
        { vm.ponerGenerosObra(o.id, it) }, { vm.buscarEtiqueta(it, Secciones.de(o.tipo)) }, textoAgregar,
        textoVisible = { etiquetaLibroVisible(o, it, idioma) })

    // El estado, como píldoras. Al pasarla a terminada, El Cajón celebra (da una vuelta en el aire).
    var celebrando by remember(id) { mutableStateOf(false) }
    Selector(Estados.TODOS.map { it to nombreEstado(it, o.tipo) }, o.estado, {
        if (it == Estados.TERMINADO && o.estado != Estados.TERMINADO) celebrando = true
        vm.ponerEstado(o.id, it)
    }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    if (celebrando) {
        io.github.fogonapps.identidad.TarjetaPersonaje(io.github.fogonapps.identidad.Quien.CAJON, io.github.fogonapps.identidad.Cara.FELIZ, Modifier.padding(vertical = 8.dp).aparecer(0), tamano = 88.dp, celebrar = true) {
            Text(stringResource(R.string.terminaste_titulo), style = MaterialTheme.typography.titleMedium, color = f.texto)
            Text(stringResource(R.string.terminaste_texto), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        }
    }

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
        BotonSecundario(stringResource(R.string.chat_mandar_a_alguien), { vm.abrir(if (vm.red.usuario.value == null) Pantalla.CuentaRed else Pantalla.Chats(o.id)) }, Modifier.fillMaxWidth())
        BotonSecundario(stringResource(R.string.red_publicar_sobre), { vm.abrir(if (vm.red.usuario.value == null) Pantalla.CuentaRed else Pantalla.Publicar(o.id)) }, Modifier.fillMaxWidth())
    }

    // El progreso: páginas o canciones.
    when {
        o.tipo == Tipos.LIBRO -> Paginas(vm, o)
        o.tipo == Tipos.ALBUM && o.canciones.isNotEmpty() -> Canciones(vm, o)
    }

    Sinopsis(o.sinopsis)

    // Reseñas: la tuya (con tu nota, privada o para la red, y para compartir) y las de la gente.
    Seccion(stringResource(R.string.resenas))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Los datos curiosos solo se muestran si hay algo (o si la persona quiere escribir).
        var notas by rememberSaveable(o.id) { mutableStateOf(o.notas) }
        var conNotas by rememberSaveable(o.id) { mutableStateOf(o.notas.isNotBlank()) }
        if (conNotas) OutlinedTextField(notas, { notas = it; vm.ponerNotas(o.id, it) }, Modifier.fillMaxWidth(), minLines = 2,
            label = { Text(stringResource(R.string.datos_curiosos)) })
        else Pildora(stringResource(R.string.agregar_datos_curiosos), Iconos.Mas) { conNotas = true }
    }
    ResenasPropias(vm, e, o)
    ResenasDeLaGente(vm, o)

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
                    e.resenas.firstOrNull { !it.borrada && it.obra == o.id && it.registro == r.id }?.takeIf { !it.spoiler }?.let { resena -> Text(resena.texto, style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 2, overflow = TextOverflow.Ellipsis) }
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
            FotoArtista(a.foto, a.nombre, 46.dp, a.tipo)
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
fun CambiarPortada(vm: CajonViewModel, e: Estado, id: String, que: String = CajonViewModel.IMAGEN_PORTADA) {
    val f = FogonTema.actual
    val acciones = LocalAcciones.current
    val foto = que == CajonViewModel.IMAGEN_FOTO
    val fondo = que == CajonViewModel.IMAGEN_FONDO
    val o = if (foto) null else e.vivas.firstOrNull { it.id == id }
    val a = if (foto) e.artistasVivos.firstOrNull { it.id == id } else null
    if (o == null && a == null) { Cabecera("", vm::cerrar); Pista(stringResource(R.string.ya_no_esta)); return }
    val actual = when { a != null -> a.foto; fondo -> o!!.fondo; else -> o!!.portada }
    val c by vm.candidatas.collectAsStateWithLifecycle()
    val comunidades by vm.portadasComunidad.collectAsStateWithLifecycle()
    val comunidad = comunidades[id]
    val cuenta by vm.red.cuenta.collectAsStateWithLifecycle()
    LaunchedEffect(id, que) { vm.cargarCandidatas(id, que) }
    Cabecera(stringResource(when { foto -> R.string.cambiar_foto; fondo -> R.string.cambiar_fondo; else -> R.string.cambiar_portada }), vm::cerrar)
    // En cine se elige entre cambiar la portada o el fondo.
    if (o != null && o.fuente.startsWith("tmdb:")) Selector(listOf(CajonViewModel.IMAGEN_PORTADA to stringResource(R.string.portada_nombre),
        CajonViewModel.IMAGEN_FONDO to stringResource(R.string.fondo_nombre)), que, { vm.reemplazar(Pantalla.CambiarPortada(id, it)) },
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (acciones != null) BotonRiso(stringResource(R.string.subir_imagen), { acciones.elegirImagen { uri -> vm.subirImagen(id, que, uri) } }, Modifier.fillMaxWidth())
        Pista(stringResource(if (foto) R.string.subir_foto_pista else R.string.subir_imagen_pista), adentro = true)
    }
    val lista = c?.takeIf { it.first == "$id|$que" }?.second
    val fuente = o?.fuente ?: a?.fuente.orEmpty()
    when {
        lista == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = f.tinta1) }
        lista.isEmpty() -> Pista(stringResource(R.string.sin_otras_portadas))
        else -> {
            Seccion(stringResource(R.string.elegir_otra))
            if (o != null && !fondo) Pista(stringResource(R.string.imagen_votos_pista), Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp))
            // Fondos en dos columnas (son anchos); portadas y fotos en tres.
            val columnas = if (fondo) 2 else 3
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                lista.chunked(columnas).forEach { fila ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        fila.forEach { url ->
                            val opcion = lista.indexOf(url) + 1
                            val nombreOpcion = stringResource(R.string.imagen_opcion, opcion)
                            val elegirLocal = stringResource(R.string.imagen_para_mi, opcion)
                            // Cada opción: la imagen (tocarla la usa para ti), una marca si es la que usas, los votos
                            // para la portada de todos (con cuenta, tocarlos vota) y la «i» con de dónde salió.
                            var info by remember(url) { mutableStateOf(false) }
                            Box(Modifier.weight(1f).clip(forma(12.dp)).semantics { contentDescription = elegirLocal; selected = url == actual }.clickable { vm.elegirImagen(id, que, url) }
                                .then(if (url == actual) Modifier.border(2.dp, f.tinta1, forma(12.dp)) else Modifier)) {
                                when {
                                    o != null && !fondo -> Portada(url, o.tipo, o.titulo, Modifier.fillMaxWidth(), conTitulo = false)
                                    else -> coil3.compose.AsyncImage(url, null, contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = nombreOpcion }.aspectRatio(if (fondo) 16f / 9f else 1f).background(f.superficie))
                                }
                                if (url == actual) Box(Modifier.align(Alignment.TopStart).padding(6.dp).size(24.dp).clip(androidx.compose.foundation.shape.CircleShape).background(f.tinta1), contentAlignment = Alignment.Center) {
                                    IconoRiso(Iconos.Chulo, stringResource(R.string.imagen_elegida), tamano = 14.dp, tinta = f.sobreTinta1)
                                }
                                Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp).clip(androidx.compose.foundation.shape.CircleShape).background(f.fondo.copy(alpha = 0.85f))
                                    .clickable { info = true }, contentAlignment = Alignment.Center) {
                                    Text("i", style = MaterialTheme.typography.labelLarge, color = f.texto, modifier = Modifier.semantics { contentDescription = "$nombreOpcion · " })
                                }
                                if (o != null && !fondo && Imagenes.autorizada(url)) {
                                    val votada = comunidad?.miVoto?.let { Imagenes.clave(it) == Imagenes.clave(url) } == true
                                    val votos = comunidad?.votos.orEmpty().filterKeys { Imagenes.clave(it) == Imagenes.clave(url) }.values.sum()
                                    val accionVoto = stringResource(if (votada) R.string.imagen_quitar_voto else R.string.imagen_votar)
                                    val estadoVoto = stringResource(if (votada) R.string.imagen_votada else R.string.imagen_sin_voto)
                                    Row(Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(io.github.fogonapps.identidad.FormaPildora).background(if (votada) f.tinta1 else f.fondo.copy(alpha = 0.85f))
                                        .then(if (cuenta != null) Modifier.clickable { vm.votarImagen(id, if (votada) null else url) } else Modifier)
                                        .semantics(mergeDescendants = true) { contentDescription = "$nombreOpcion · ${if (cuenta != null) accionVoto else ""}"; stateDescription = estadoVoto; selected = votada }
                                        .padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconoRiso(Iconos.Corazon, null, tamano = 14.dp, tinta = if (votada) f.sobreTinta1 else f.texto, relleno = if (votada) f.sobreTinta1 else null)
                                        Text("$votos", style = MaterialTheme.typography.labelMedium, color = if (votada) f.sobreTinta1 else f.texto)
                                    }
                                }
                            }
                            if (info) androidx.compose.ui.window.Dialog({ info = false }) {
                                Column(Modifier.clip(forma(24.dp)).background(f.fondo).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SobreImagen(vm, id, url, fuente, abiertaDeEntrada = true)
                                    BotonSecundario(stringResource(R.string.volver), { info = false }, Modifier.fillMaxWidth())
                                }
                            }
                        }
                        repeat(columnas - fila.size) { Spacer(Modifier.weight(1f)) }
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

/** Una canción de un álbum que todavía no está en el cajón: número, título, avance y, al tocarla o mantenerla, sus acciones. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilaCancionAlbum(vm: CajonViewModel, o: Obra, i: Int, titulo: String, avance: io.github.fogonapps.social.CancionSocial?) {
    val f = FogonTema.actual
    var acciones by remember { mutableStateOf(false) }
    if (acciones) AccionesCancion(vm, o, titulo, avance) { acciones = false }
    Row(Modifier.fillMaxWidth().combinedClickable(onLongClick = { acciones = true }) { acciones = true }.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${i + 1}", fontFamily = f.cifras, color = f.textoSuave, modifier = Modifier.width(28.dp))
        Text(titulo, style = MaterialTheme.typography.bodyMedium, color = f.texto, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        avance?.let { io.github.fogonapps.social.ui.BotonAvance(it.audio) }
    }
}

/**
 * Lo que se puede hacer con una canción (como en una app de música): escucharla 30 segundos en Apple Music, ponerla en
 * tu perfil, en tu nota o en una historia.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccionesCancion(vm: CajonViewModel, o: Obra, titulo: String, avance: io.github.fogonapps.social.CancionSocial?, onCerrar: () -> Unit) {
    val f = FogonTema.actual
    var historia by remember { mutableStateOf(false) }
    val interfaz = interfazSocial(vm)
    val adjunto = kotlinx.serialization.json.buildJsonObject {
        put("tipo", "cancion"); put("titulo", titulo); put("detalle", o.creador)
        (avance?.portada ?: o.portada).takeIf { it.startsWith("https://") }?.let { put("imagen", it) }
        avance?.let { put("audio", it.audio); put("enlace", it.enlace) }
    }
    ModalBottomSheet(onCerrar, containerColor = f.fondo, contentColor = f.texto) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, color = f.texto, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            avance?.let { a ->
                Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    io.github.fogonapps.social.ui.BotonAvance(a.audio)
                    io.github.fogonapps.social.ui.EnlaceAppleMusic(a.enlace)
                }
            }
            FilaAccion(Iconos.Persona, stringResource(R.string.cancion_al_perfil)) {
                vm.ponerCancion(listOf("v3", titulo, o.creador, o.titulo, avance?.portada ?: o.portada, avance?.audio.orEmpty(), avance?.enlace.orEmpty()).joinToString("|") { it.replace("|", "/") }); onCerrar()
            }
            FilaAccion(Iconos.Comentario, stringResource(R.string.cancion_a_la_nota)) { vm.red.ponerNotaGente("", adjunto); onCerrar() }
            FilaAccion(Iconos.Mas, stringResource(R.string.cancion_a_historia)) { historia = true }
        }
    }
    if (historia) io.github.fogonapps.social.ui.CrearHistoria(interfaz, adjunto) { historia = false; onCerrar() }
}

@Composable
private fun FilaAccion(icono: io.github.fogonapps.identidad.Dibujo, texto: String, onClick: () -> Unit) {
    val f = FogonTema.actual
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        IconoRiso(icono, null, tamano = 22.dp, tinta = f.texto)
        Text(texto, style = MaterialTheme.typography.bodyLarge, color = f.texto)
    }
}

/** Las canciones del álbum, cada una con su nota y su corazón (como en un tracklist con calificación). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Canciones(vm: CajonViewModel, o: Obra) {
    val f = FogonTema.actual
    Seccion(stringResource(R.string.canciones))
    Pista(stringResource(R.string.canciones_pista))
    // Los avances de Apple de este álbum (si los encuentra), para escuchar 30 segundos de cada canción.
    var avances by remember(o.id) { mutableStateOf<List<io.github.fogonapps.social.CancionSocial>>(emptyList()) }
    LaunchedEffect(o.id) { avances = vm.cancionesApple(o.titulo, o.creador) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).vidrio().padding(vertical = 6.dp)) {
        o.canciones.forEachIndexed { i, c ->
            val avance = avances.firstOrNull { vm.L.tituloLimpio(it.titulo) == vm.L.tituloLimpio(c.titulo) }
            var acciones by remember { mutableStateOf(false) }
            if (acciones) AccionesCancion(vm, o, c.titulo, avance) { acciones = false }
            Column(Modifier.fillMaxWidth().combinedClickable(onLongClick = { acciones = true }) { acciones = true }.padding(horizontal = 14.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", fontFamily = f.cifras, color = f.textoSuave, modifier = Modifier.width(28.dp))
                    Text(c.titulo, style = MaterialTheme.typography.bodyMedium, color = f.texto, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (c.segundos > 0) Text("%d:%02d".format(c.segundos / 60, c.segundos % 60), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                    avance?.let { io.github.fogonapps.social.ui.BotonAvance(it.audio) }
                    BotonIcono(Iconos.Corazon, stringResource(R.string.cancion_favorita), tinta = if (c.favorita) f.acentoTexto else f.linea, lleno = c.favorita) { vm.cancionFavorita(o.id, i) }
                }
                avance?.let { Box(Modifier.padding(start = 24.dp)) { io.github.fogonapps.social.ui.EnlaceAppleMusic(it.enlace) } }
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
    var visibilidad by rememberSaveable { mutableStateOf(viejo?.visibilidad ?: "") }

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
        BotonSecundario(stringResource(R.string.resena_escribir), {
            val registro = vm.registrar(o.id, Registro(viejo?.id ?: "", fecha, nota, otraVez, visibilidad = visibilidad))
            vm.escribirResena(o.id, registro)
        }, Modifier.fillMaxWidth())
        // Quién la vería cuando haya perfiles públicos (sin elegir, como diga el diario en el perfil).
        Text(stringResource(R.string.quien_ve_entrada), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
        Selector(listOf("" to stringResource(R.string.como_el_diario), io.github.fogonapps.cajon.datos.Visibilidad.PRIVADA to stringResource(R.string.vis_privada),
            io.github.fogonapps.cajon.datos.Visibilidad.AMIGOS to stringResource(R.string.vis_amigos), io.github.fogonapps.cajon.datos.Visibilidad.PUBLICA to stringResource(R.string.vis_publica)),
            visibilidad, { visibilidad = it })
        BotonRiso(stringResource(R.string.guardar), { vm.registrar(o.id, Registro(viejo?.id ?: "", fecha, nota, otraVez, visibilidad = visibilidad)) }, Modifier.fillMaxWidth())
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

@Composable
private fun SobreImagen(vm: CajonViewModel, id: String, url: String, fuenteObra: String, abiertaDeEntrada: Boolean = false) {
    val f = FogonTema.actual
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    var abierta by remember(url) { mutableStateOf(abiertaDeEntrada) }
    val ediciones by vm.ediciones.collectAsStateWithLifecycle()
    var i by remember(url, ediciones[id]) { mutableStateOf(vm.procedencia(id, url)) }
    LaunchedEffect(url, abierta) {
        if (abierta && i.fuente == "wikimedia") runCatching { io.github.fogonapps.cajon.datos.Wikimedia.procedencia(url) }.getOrNull()?.let { i = it }
    }
    if (!abiertaDeEntrada) Pildora(stringResource(R.string.imagen_sobre), acento = abierta) { abierta = !abierta }
    if (abierta) {
        val texto = when (i.fuente) {
            "tmdb" -> stringResource(R.string.imagen_tmdb)
            "ol" -> if (i.edicion == null) stringResource(R.string.imagen_ol_obra) else stringResource(R.string.imagen_ol,
                ediciones[id]?.firstOrNull { it.id == i.edicion }?.let { Imagenes.descripcion(it, vm.idioma()) } ?: i.edicion.orEmpty())
            "caa" -> stringResource(R.string.imagen_caa)
            "wikimedia" -> listOfNotNull("Wikimedia Commons", i.autor, i.licencia).joinToString(" · ")
            else -> stringResource(R.string.imagen_local)
        }
        Text(texto, color = f.texto)
        if (i.fuente != "local") BotonSecundario(stringResource(R.string.imagen_fuente), { uri.openUri(Imagenes.enlace(i, fuenteObra)) })
    }
}

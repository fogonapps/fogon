package io.github.fogonapps.social.ui

import io.github.fogonapps.identidad.IconosSociales as Iconos
import io.github.fogonapps.identidad.PistaSocial as Pista
import io.github.fogonapps.identidad.PildoraSocial as Pildora
import io.github.fogonapps.identidad.BotonIconoSocial as BotonIcono
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.identidad.*
import io.github.fogonapps.social.*
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

/*
 * Historias y notas de 24 horas, como en Instagram, en todas las apps. Una historia lleva una foto, un texto o un
 * adjunto; una nota, unas palabras y, si quiere, un adjunto. El adjunto lo arma cada app con lo suyo (una canción u
 * obra en El Cajón, un entreno o un récord en El Forzudo, un plato en La Cucha, una meta en La Alcancía): ver
 * [InterfazSocial.adjuntosApp]. La canción suena 30 segundos (el avance de Apple, junto al botón de Apple Music).
 */

private fun JsonObject.t(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull.orEmpty()

/** Los fondos de una historia sin foto: colores del tema, nunca fijos. */
@Composable
private fun fondoHistoria(i: Int): Pair<Color, Color> {
    val f = FogonTema.actual
    return when (i % 4) {
        0 -> f.tinta1 to f.sobreTinta1
        1 -> f.tinta2 to f.fondo
        2 -> f.tinta1.copy(alpha = 0.4f).compositeOver(f.fondo) to f.texto
        else -> f.fondo to f.texto
    }
}

/** Una foto del teléfono achicada para subirla (lado mayor 1440 px, JPEG de menos de 900 KB). */
internal suspend fun jpegHistoria(ctx: android.content.Context, uri: android.net.Uri): ByteArray? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    runCatching {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
        val medidas = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, medidas)
        var muestra = 1
        while (maxOf(medidas.outWidth, medidas.outHeight) / (muestra * 2) >= 1440) muestra *= 2
        val original = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = muestra }) ?: return@runCatching null
        val escala = minOf(1f, 1440f / maxOf(original.width, original.height))
        val chica = android.graphics.Bitmap.createScaledBitmap(original, (original.width * escala).toInt().coerceAtLeast(1), (original.height * escala).toInt().coerceAtLeast(1), true)
        var calidad = 88
        var salida: ByteArray
        do {
            salida = java.io.ByteArrayOutputStream().also { chica.compress(android.graphics.Bitmap.CompressFormat.JPEG, calidad, it) }.toByteArray()
            calidad -= 12
        } while (salida.size > 900_000 && calidad > 30)
        salida.takeIf { it.size <= 900_000 }
    }.getOrNull()
}

/** Un avatar con el aro de historias: lleno si hay sin ver, fino si ya se vieron todas. */
@Composable
fun AvatarConAro(p: PersonaSocial, tamano: Dp, estado: Int, mas: Boolean = false, onClick: (() -> Unit)? = null) {
    val f = FogonTema.actual
    // El recorte redondo va solo en el avatar: así el «+» de la esquina se ve entero.
    Box(Modifier.size(tamano + 10.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().clip(CircleShape).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier), contentAlignment = Alignment.Center) {
            if (estado > 0) Box(Modifier.matchParentSize().border(if (estado == 2) 1.5.dp else 3.dp, when (estado) { 2 -> f.linea; 3 -> f.tinta2; else -> f.tinta1 }, CircleShape))
            AvatarRed(p, tamano)
        }
        if (mas) Box(Modifier.align(Alignment.BottomEnd).size(24.dp).clip(CircleShape).background(f.tinta1).border(2.dp, f.fondo, CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier), contentAlignment = Alignment.Center) {
            IconoRiso(Iconos.Mas, null, tamano = 14.dp, tinta = f.sobreTinta1)
        }
    }
}

/**
 * La fila de arriba del feed: tu historia y tu nota, y las de tu gente (las no vistas primero). Tocar un avatar abre
 * sus historias; tocar una nota la muestra (con su canción, si tiene).
 */
@Composable
fun BarraHistorias(vm: InterfazSocial) {
    val f = FogonTema.actual
    val yo by vm.red.usuario.collectAsStateWithLifecycle()
    val grupos by vm.red.historias.collectAsStateWithLifecycle()
    val notas by vm.red.notasGente.collectAsStateWithLifecycle()
    val usuario = yo ?: return
    LaunchedEffect(usuario) { vm.red.cargarHistorias() }
    var creando by remember { mutableStateOf(false) }
    var menuPropio by remember { mutableStateOf(false) }
    var escribiendoNota by remember { mutableStateOf(false) }
    var notaAbierta by remember { mutableStateOf<NotaGente?>(null) }
    var visor by remember { mutableStateOf<Pair<List<GrupoHistoriasSocial>, Int>?>(null) }
    val propio = grupos.firstOrNull { it.persona.usuario == usuario }
    val miNota = notas.firstOrNull { it.persona.usuario == usuario }
    val otros = grupos.filter { it.persona.usuario != usuario }
    val soloNota = notas.filter { n -> n.persona.usuario != usuario && otros.none { it.persona.usuario == n.persona.usuario } }
    val yoPersona = propio?.persona ?: miNota?.persona ?: PersonaSocial(usuario, stringResource(R.string.historia_tu), foto = true)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ItemHistoria(yoPersona.copy(nombre = stringResource(R.string.historia_tu)), miNota?.texto?.ifBlank { miNota.datos["adjunto"]?.jsonObject?.t("titulo") } ?: stringResource(R.string.historia_nota_pon),
            notaVacia = miNota == null, estado = if (propio == null) 0 else if (propio.todasVistas) 2 else 1, mas = propio == null,
            alNota = { escribiendoNota = true }) { if (propio == null) creando = true else menuPropio = true }
        otros.forEach { g ->
            val nota = notas.firstOrNull { it.persona.usuario == g.persona.usuario }
            // Silenciadas: al final y apagadas. Las de mejores amigos sin ver llevan el aro de la segunda tinta.
            Box(Modifier.alpha(if (g.silenciada) 0.45f else 1f)) {
                ItemHistoria(g.persona, nota?.let { it.texto.ifBlank { it.datos["adjunto"]?.jsonObject?.t("titulo").orEmpty() } }, false,
                    when { g.todasVistas -> 2; g.historias.any { !it.vista && it.mejores } -> 3; else -> 1 },
                    alNota = { notaAbierta = nota }) { visor = otros to otros.indexOf(g) }
            }
        }
        soloNota.forEach { n -> ItemHistoria(n.persona, n.texto.ifBlank { n.datos["adjunto"]?.jsonObject?.t("titulo").orEmpty() }, false, 0, alNota = { notaAbierta = n }) { notaAbierta = n } }
    }
    var archivo by remember { mutableStateOf(false) }
    if (menuPropio) MenuHistoriaPropia(vm, { menuPropio = false }, ver = { visor = listOfNotNull(propio) to 0 }, nueva = { creando = true }, nota = { escribiendoNota = true }, archivo = { archivo = true })
    if (archivo) ArchivoHistorias(vm, yoPersona, { archivo = false }) { l, i -> archivo = false; visor = listOf(GrupoHistoriasSocial(yoPersona, l.drop(i).map { it.copy(vista = true) })) to 0 }
    if (creando) CrearHistoria(vm) { creando = false }
    if (escribiendoNota) HojaNota(vm, miNota) { escribiendoNota = false }
    notaAbierta?.let { n -> HojaVerNota(vm, n) { notaAbierta = null } }
    visor?.let { (l, i) -> if (l.isNotEmpty()) VisorHistorias(vm, l, i) { visor = null } }
}

@Composable
private fun ItemHistoria(p: PersonaSocial, nota: String?, notaVacia: Boolean, estado: Int, mas: Boolean = false, alNota: () -> Unit, onClick: () -> Unit) {
    val f = FogonTema.actual
    Column(Modifier.width(80.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.height(30.dp), contentAlignment = Alignment.BottomCenter) {
            if (!nota.isNullOrBlank()) Box(Modifier.clip(forma(12.dp)).background(if (notaVacia) f.superficie else f.fondo).border(f.borde, f.linea, forma(12.dp))
                .clickable(onClick = alNota).padding(horizontal = 7.dp, vertical = 3.dp)) {
                Text(nota, style = MaterialTheme.typography.labelSmall, color = if (notaVacia) f.textoSuave else f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
        AvatarConAro(p, 58.dp, estado, mas, onClick)
        Text(p.nombre.ifBlank { p.usuario }, style = MaterialTheme.typography.labelSmall, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuHistoriaPropia(vm: InterfazSocial, onCerrar: () -> Unit, ver: () -> Unit, nueva: () -> Unit, nota: () -> Unit, archivo: () -> Unit) {
    val f = FogonTema.actual
    ModalBottomSheet(onCerrar, containerColor = f.fondo, contentColor = f.texto) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            listOf(Triple(Iconos.Anime, R.string.historia_ver_tuya, ver), Triple(Iconos.Mas, R.string.historia_nueva, nueva), Triple(Iconos.Comentario, R.string.historia_tu_nota, nota),
                Triple(Iconos.Estrella, R.string.historia_archivo, archivo)).forEach { (icono, texto, accion) ->
                Row(Modifier.fillMaxWidth().clickable { onCerrar(); accion() }.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconoRiso(icono, null, tamano = 22.dp, tinta = f.texto)
                    Text(stringResource(texto), style = MaterialTheme.typography.bodyLarge, color = f.texto)
                }
            }
        }
    }
}

/** Tu archivo: las historias de los últimos 90 días, también las que ya vencieron (solo tú las ves). Desde aquí se destacan. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchivoHistorias(vm: InterfazSocial, yo: PersonaSocial, onCerrar: () -> Unit, abrir: (List<HistoriaSocial>, Int) -> Unit) {
    val f = FogonTema.actual
    var lista by remember { mutableStateOf<List<HistoriaSocial>?>(null) }
    LaunchedEffect(Unit) { lista = vm.red.archivoHistorias() }
    ModalBottomSheet(onCerrar, containerColor = f.fondo, contentColor = f.texto) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.historia_archivo), style = MaterialTheme.typography.titleMedium, color = f.texto)
            Text(stringResource(R.string.historia_archivo_explica), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            val l = lista
            when {
                l == null -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = f.tinta1, trackColor = f.linea)
                l.isEmpty() -> Text(stringResource(R.string.historia_archivo_vacio), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
                else -> androidx.compose.foundation.lazy.grid.LazyVerticalGrid(androidx.compose.foundation.lazy.grid.GridCells.Fixed(3), Modifier.heightIn(max = 520.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(l.size) { i ->
                        val h = l[i]
                        val (color, _) = fondoHistoria((h.datos["fondo"] as? JsonPrimitive)?.intOrNull ?: 0)
                        Box(Modifier.aspectRatio(9f / 16f).clip(forma(10.dp)).background(color).clickable { abrir(l, i) }) {
                            h.datos.t("imagen").takeIf { it.isNotBlank() }?.let { coil3.compose.AsyncImage(ClienteSocial.imagen(it), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                            Text(haceCuanto(h.cuando), style = MaterialTheme.typography.labelSmall, color = f.texto,
                                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).clip(FormaPildora).background(f.fondo.copy(alpha = 0.8f)).padding(horizontal = 6.dp, vertical = 2.dp))
                            if (h.destacada.isNotBlank()) IconoRiso(Iconos.Estrella, h.destacada, tamano = 16.dp, tinta = f.acentoTexto, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
                        }
                    }
                }
            }
        }
    }
}

/* ----- Adjuntos ----- */

/** Una canción, obra, entreno, plato o meta, como tarjeta. La canción suena su avance (30 s) con el botón de Apple Music al lado. */
@Composable
fun TarjetaAdjunto(a: JsonObject, modifier: Modifier = Modifier, sonar: Boolean = false, solida: Boolean = false) {
    val f = FogonTema.actual
    val uri = LocalUriHandler.current
    // Sobre el color de una historia, la tarjeta va opaca (el vidrio no se leería).
    val fondo = if (solida) Modifier.clip(forma(18.dp)).background(f.fondo).border(f.borde, f.linea, forma(18.dp)) else Modifier.tarjetaFogon()
    Column(modifier.fillMaxWidth().then(fondo).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val imagen = a.t("imagen")
            if (imagen.isNotBlank()) coil3.compose.AsyncImage(if (imagen.startsWith("https://")) imagen else ClienteSocial.imagen(imagen), null,
                Modifier.size(56.dp).clip(forma(10.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f)) {
                Text(nombreTipoAdjunto(a.t("tipo"), a.t("tipoObra")), style = MaterialTheme.typography.labelSmall, color = f.acentoTexto)
                Text(a.t("titulo"), style = MaterialTheme.typography.titleSmall, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (a.t("detalle").isNotBlank()) Text(a.t("detalle"), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (a.t("cifra").isNotBlank()) Text(a.t("cifra"), style = MaterialTheme.typography.titleLarge, color = f.acentoTexto)
        }
        val audio = a.t("audio"); val enlace = a.t("enlace"); val escena = a.t("escena")
        if (audio.isNotBlank() || escena.isNotBlank() || enlace.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (audio.isNotBlank()) Avance(audio, sonar)
            if (audio.isNotBlank() && enlace.isNotBlank()) EnlaceAppleMusic(enlace)
            if (escena.isNotBlank()) Pildora(stringResource(R.string.historia_ver_escena)) {
                uri.openUri("https://www.youtube.com/watch?v=$escena" + (a["segundo"] as? JsonPrimitive)?.intOrNull?.let { "&t=${it}s" }.orEmpty())
            }
            if (audio.isBlank() && escena.isBlank() && enlace.isNotBlank()) Pildora(stringResource(R.string.historia_abrir)) { uri.openUri(enlace) }
        }
    }
}

@Composable
private fun nombreTipoAdjunto(tipo: String, tipoObra: String): String = stringResource(when (tipo) {
    "cancion" -> R.string.historia_tipo_cancion
    "artista" -> R.string.historia_tipo_artista_uno
    "obra" -> when (tipoObra) { "serie" -> R.string.historia_tipo_serie; "libro" -> R.string.historia_tipo_libro; "album", "disco" -> R.string.historia_tipo_album; "anime" -> R.string.historia_tipo_anime; else -> R.string.historia_tipo_pelicula }
    "entreno" -> R.string.historia_tipo_entreno
    "ejercicio" -> R.string.historia_tipo_ejercicio
    "logro", "racha" -> R.string.historia_tipo_logro
    "publicacion" -> R.string.historia_tipo_publicacion
    "plato", "receta" -> R.string.historia_tipo_plato
    "meta", "ahorro" -> R.string.historia_tipo_meta
    else -> R.string.historia_tipo_otro
})

/** El enlace discreto a la canción en Apple Music (sus condiciones piden el enlace junto al avance; el logo solo no se permite). */
@Composable
fun EnlaceAppleMusic(enlace: String) {
    if (!enlace.startsWith("https://music.apple.com/")) return
    val f = FogonTema.actual
    val uri = LocalUriHandler.current
    Text(stringResource(R.string.historia_apple_music_corto), style = MaterialTheme.typography.labelSmall.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
        color = f.textoSuave, modifier = Modifier.clip(forma(6.dp)).clickable { uri.openUri(enlace) }.padding(4.dp))
}

/** El botón del avance de 30 segundos (solo de Apple), para usarlo fuera de las historias (la canción del perfil). */
@Composable
fun BotonAvance(url: String) { if (url.startsWith("https://audio-ssl.itunes.apple.com/")) Avance(url, false) }

/** El avance de 30 segundos: suena al tocar (o solo, en el visor); se apaga al salir. */
@Composable
private fun Avance(url: String, sonar: Boolean) {
    val f = FogonTema.actual
    var sonando by remember(url) { mutableStateOf(false) }
    val reproductor = remember(url) { android.media.MediaPlayer() }
    var listo by remember(url) { mutableStateOf(false) }
    DisposableEffect(url) {
        runCatching {
            reproductor.setAudioAttributes(android.media.AudioAttributes.Builder().setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).setUsage(android.media.AudioAttributes.USAGE_MEDIA).build())
            reproductor.setDataSource(url)
            reproductor.setOnPreparedListener { listo = true; if (sonando) it.start() }
            reproductor.setOnCompletionListener { sonando = false }
            reproductor.prepareAsync()
        }
        onDispose { runCatching { reproductor.release() } }
    }
    // Con cada canción nueva (la siguiente historia), vuelve a sonar sola.
    LaunchedEffect(url, sonar) { if (sonar) sonando = true }
    // Al salir de la app (o apagar la pantalla), la canción se calla.
    val ciclo = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(ciclo) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) { sonando = false; runCatching { if (reproductor.isPlaying) reproductor.pause() } } }
        ciclo.lifecycle.addObserver(obs)
        onDispose { ciclo.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(sonando, listo) { runCatching { if (listo) { if (sonando) reproductor.start() else if (reproductor.isPlaying) reproductor.pause() } } }
    val nombre = stringResource(if (sonando) R.string.historia_pausar else R.string.historia_escuchar)
    Box(Modifier.size(40.dp).clip(CircleShape).background(f.tinta1).clickable { sonando = !sonando }, contentAlignment = Alignment.Center) {
        IconoRiso(if (sonando) IconosMultimedia.Pausa else IconosMultimedia.Reproducir, nombre, tamano = 20.dp, tinta = f.sobreTinta1)
    }
}

/**
 * Elegir qué adjuntar: buscar una canción o algo de la app ([InterfazSocial.adjuntosApp]). Antes de devolverlo, la app
 * puede completarlo ([InterfazSocial.completarAdjunto]: por ejemplo, la escena del tráiler de una película).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElegirAdjunto(vm: InterfazSocial, onCerrar: () -> Unit, onElegir: (JsonObject) -> Unit) {
    val f = FogonTema.actual
    val alcance = rememberCoroutineScope()
    var buscar by rememberSaveable { mutableStateOf("") }
    var canciones by remember { mutableStateOf<List<CancionSocial>?>(null) }
    var encontrados by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var ocupado by remember { mutableStateOf(false) }
    val opciones = remember { vm.adjuntosApp() }
    LaunchedEffect(buscar) {
        if (buscar.trim().length < 2) { canciones = null; encontrados = emptyList(); return@LaunchedEffect }
        delay(450)
        // Canciones y lo de la app (películas, series, libros…), a la vez.
        val obras = async { runCatching { vm.buscarAdjuntos(buscar.trim()) }.getOrDefault(emptyList()) }
        canciones = vm.red.canciones(buscar)
        encontrados = obras.await()
    }
    var dentro by remember { mutableStateOf<Pair<JsonObject, List<JsonObject>>?>(null) }
    fun usar(a: JsonObject) { ocupado = true; alcance.launch { val listo = runCatching { vm.completarAdjunto(a) }.getOrDefault(a); ocupado = false; onElegir(listo); onCerrar() } }
    // Un álbum o un artista: primero sus canciones, para elegir una (con su avance); si no hay, se usa tal cual.
    fun elegir(a: JsonObject) {
        if (dentro == null && (a.t("tipo") == "artista" || a.t("tipoObra") == "album")) {
            ocupado = true
            alcance.launch { val l = runCatching { vm.detallarAdjunto(a) }.getOrDefault(emptyList()); ocupado = false; if (l.isEmpty()) usar(a) else dentro = a to l }
        } else usar(a)
    }
    ModalBottomSheet(onCerrar, containerColor = f.fondo, contentColor = f.texto) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.historia_adjuntar), style = MaterialTheme.typography.titleMedium, color = f.texto)
            OutlinedTextField(buscar, { buscar = it.take(80) }, Modifier.fillMaxWidth(), singleLine = true, shape = forma(18.dp),
                placeholder = { Text(vm.textoBuscarAdjunto() ?: stringResource(R.string.historia_buscar_cancion)) })
            if (ocupado) LinearProgressIndicator(Modifier.fillMaxWidth(), color = f.tinta1, trackColor = f.linea)
            dentro?.let { (a, l) ->
                Pildora(stringResource(R.string.historia_otra_obra), Iconos.Atras) { dentro = null }
                Text(a.t("titulo"), style = MaterialTheme.typography.titleSmall, color = f.texto)
                l.forEach { c -> Box(Modifier.clip(forma(18.dp)).clickable { usar(c) }) { TarjetaAdjunto(c) } }
                if (a.t("tipo") != "artista") Pildora(stringResource(R.string.historia_usar_album)) { usar(a) }
                return@Column
            }
            // Por secciones, como en un buscador de música: artistas, canciones, álbumes y después lo demás.
            @Composable fun Seccion(titulo: String, l: List<JsonObject>) {
                if (l.isEmpty()) return
                Text(titulo, style = MaterialTheme.typography.labelLarge, color = f.textoSuave, modifier = Modifier.padding(top = 4.dp))
                l.forEach { a -> Box(Modifier.clip(forma(18.dp)).clickable { elegir(a) }) { TarjetaAdjunto(a) } }
            }
            val clave = { a: JsonObject -> if (a.t("tipo") == "obra") a.t("tipoObra") else a.t("tipo") }
            Seccion(stringResource(R.string.historia_tipo_artista), encontrados.filter { clave(it) == "artista" })
            canciones?.let { l ->
                if (l.isNotEmpty()) Text(stringResource(R.string.historia_tipo_cancion), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
                if (l.isEmpty() && encontrados.isEmpty()) Pista(stringResource(R.string.historia_sin_canciones))
                l.forEach { c ->
                    Row(Modifier.fillMaxWidth().clip(forma(12.dp)).clickable {
                        elegir(buildJsonObject { put("tipo", "cancion"); put("titulo", c.titulo); put("detalle", c.artista); put("imagen", c.portada); put("audio", c.audio); put("enlace", c.enlace) })
                    }.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        coil3.compose.AsyncImage(c.portada, null, Modifier.size(44.dp).clip(forma(8.dp)), contentScale = ContentScale.Crop)
                        Column(Modifier.weight(1f)) {
                            Text(c.titulo, style = MaterialTheme.typography.bodyMedium, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(c.artista, style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Text(stringResource(R.string.historia_canciones_apple), style = MaterialTheme.typography.labelSmall, color = f.textoSuave)
            }
            Seccion(stringResource(R.string.historia_tipo_albumes), encontrados.filter { clave(it) == "album" })
            Seccion(stringResource(R.string.historia_tipo_peliculas), encontrados.filter { clave(it) == "pelicula" })
            Seccion(stringResource(R.string.historia_tipo_series), encontrados.filter { clave(it) == "serie" || clave(it) == "anime" })
            Seccion(stringResource(R.string.historia_tipo_libros), encontrados.filter { clave(it) == "libro" })
            Seccion(stringResource(R.string.historia_de_la_app), encontrados.filter { clave(it) !in setOf("artista", "album", "pelicula", "serie", "anime", "libro") })
            if (opciones.isNotEmpty() && canciones == null) {
                Text(stringResource(R.string.historia_de_la_app), style = MaterialTheme.typography.labelLarge, color = f.textoSuave, modifier = Modifier.padding(top = 6.dp))
                opciones.forEach { a -> Box(Modifier.clip(forma(18.dp)).clickable { elegir(a) }) { TarjetaAdjunto(a) } }
            }
        }
    }
}

/* ----- Notas ----- */

/** Escribir (o quitar) tu nota: unas palabras y, si quieres, una canción u otra cosa de la app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaNota(vm: InterfazSocial, mia: NotaGente?, onCerrar: () -> Unit) {
    val f = FogonTema.actual
    var texto by rememberSaveable { mutableStateOf(mia?.texto.orEmpty()) }
    val previos = listOfNotNull(mia?.datos?.get("adjunto") as? JsonObject, mia?.datos?.get("adjunto2") as? JsonObject)
    var adjunto by remember { mutableStateOf(previos.firstOrNull { it.t("tipo") == "cancion" }) }
    var adjunto2 by remember { mutableStateOf(previos.firstOrNull { it.t("tipo") != "cancion" }) }
    var eligiendo by remember { mutableStateOf(false) }
    ModalBottomSheet(onCerrar, containerColor = f.fondo, contentColor = f.texto) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.historia_tu_nota), style = MaterialTheme.typography.titleMedium, color = f.texto)
            Pista(stringResource(R.string.historia_nota_explica))
            OutlinedTextField(texto, { texto = it.take(60) }, Modifier.fillMaxWidth(), singleLine = true, shape = forma(18.dp),
                placeholder = { Text(stringResource(R.string.historia_nota_pista)) })
            adjunto?.let { a -> Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.weight(1f)) { ControlesAdjunto(a) }; BotonIcono(Iconos.Cerrar, stringResource(R.string.historia_quitar_adjunto)) { adjunto = null } } }
            adjunto2?.let { a -> Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.weight(1f)) { ControlesAdjunto(a) }; BotonIcono(Iconos.Cerrar, stringResource(R.string.historia_quitar_adjunto)) { adjunto2 = null } } }
            if (adjunto == null || adjunto2 == null) Pildora(stringResource(R.string.historia_adjuntar), Iconos.Mas) { eligiendo = true }
            BotonRiso(stringResource(R.string.historia_nota_poner), { vm.red.ponerNotaGente(texto, adjunto, adjunto2); onCerrar() }, Modifier.fillMaxWidth(),
                habilitado = texto.isNotBlank() || adjunto != null || adjunto2 != null)
            if (mia != null) BotonSecundario(stringResource(R.string.historia_nota_quitar), { vm.red.ponerNotaGente("", null); onCerrar() }, Modifier.fillMaxWidth())
        }
    }
    if (eligiendo) ElegirAdjunto(vm, { eligiendo = false }) { if (it.t("tipo") == "cancion") adjunto = it else adjunto2 = it }
}

/** La nota de otra persona, con su canción sonando; con [responder] (en los mensajes), se le contesta en el chat. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaVerNota(vm: InterfazSocial, n: NotaGente, responder: (() -> Unit)? = null, onCerrar: () -> Unit) {
    val f = FogonTema.actual
    ModalBottomSheet(onCerrar, containerColor = f.fondo, contentColor = f.texto) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AvatarRed(n.persona, 64.dp)
            NombreRed(n.persona)
            if (n.texto.isNotBlank()) Text(n.texto, style = MaterialTheme.typography.titleMedium, color = f.texto, textAlign = TextAlign.Center)
            Text(haceCuanto(n.cuando), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            (n.datos["adjunto"] as? JsonObject)?.let { TarjetaAdjunto(it, sonar = true) }
            (n.datos["adjunto2"] as? JsonObject)?.let { TarjetaAdjunto(it, sonar = n.datos["adjunto"] == null) }
            if (responder != null) BotonRiso(stringResource(R.string.historia_responder), { onCerrar(); responder() }, Modifier.fillMaxWidth())
            BotonSecundario(stringResource(R.string.historia_ver_perfil), { onCerrar(); vm.navegacion.perfil(n.persona.usuario) }, Modifier.fillMaxWidth())
        }
    }
}

/* ----- Crear una historia: el editor ----- */

/** Una capa sobre la historia: un sticker (WebP en base64 o un dibujo de Fogón) o un texto. */
private data class Capa(val id: Int, val sticker: String? = null, val recurso: Int? = null, val texto: String = "", val color: Int = 0, val conFondo: Boolean = false)

/** Los stickers de Fogón (los personajes de las apps y la chispa), para cualquier app. */
private val STICKERS_FOGON = listOf(
    io.github.fogonapps.identidad.R.drawable.personaje_cajon_feliz, io.github.fogonapps.identidad.R.drawable.personaje_forzudo_feliz,
    io.github.fogonapps.identidad.R.drawable.personaje_ahorrador_feliz, io.github.fogonapps.identidad.R.drawable.personaje_cucha_feliz,
    io.github.fogonapps.identidad.R.drawable.chispa,
)

/** El color de texto y de lápiz (de [colorTexto]) que se lee sobre cada fondo de [fondoHistoria]. */
private fun contraste(fondo: Int) = if (fondo % 4 == 0 || fondo % 4 == 2) 1 else 0

@Composable
private fun colorTexto(i: Int): Color { val f = FogonTema.actual; return listOf(f.texto, f.fondo, f.tinta1, f.tinta2)[i % 4] }

/**
 * El editor de una historia, como en Instagram: la foto se acerca y se mueve con los dedos; encima van textos y
 * stickers que se mueven, se agrandan o se achican (con dos dedos o con los botones) y se quitan; abajo, la música u
 * otra cosa de la app. Al compartir, todo se vuelve una sola imagen (la canción suena aparte, al verla).
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CrearHistoria(vm: InterfazSocial, adjuntoInicial: JsonObject? = null, onCerrar: () -> Unit) {
    val f = FogonTema.actual
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    val capa = androidx.compose.ui.graphics.rememberGraphicsLayer()
    // Una foto del teléfono (Uri) o una de una obra o persona (dirección de internet).
    var foto by remember { mutableStateOf<Any?>(null) }
    // Un video corto (hasta 15 s): su primer cuadro hace de foto en el lienzo y la historia lo reproduce.
    var video by remember { mutableStateOf<io.github.fogonapps.social.VideoPreparado?>(null) }
    var preparandoVideo by remember { mutableStateOf(false) }
    // Desde qué segundo abre la escena (el tráiler) de una película o serie.
    var pidiendoSegundo by remember { mutableStateOf(false) }
    var fotosObra by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var mover by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var fondo by rememberSaveable { mutableStateOf(0) }
    // Dos cosas a la vez, como en Instagram: la canción y además una obra, un entreno, un plato…
    var adjunto by remember { mutableStateOf(adjuntoInicial?.takeIf { it.t("tipo") == "cancion" }) }
    var adjunto2 by remember { mutableStateOf(adjuntoInicial?.takeIf { it.t("tipo") != "cancion" }) }
    val capas = remember { mutableStateListOf<Capa>() }
    val posiciones = remember { mutableStateMapOf<Int, androidx.compose.ui.geometry.Offset>() }
    val escalas = remember { mutableStateMapOf<Int, Float>() }
    val tamanos = remember { mutableStateMapOf<Int, androidx.compose.ui.unit.IntSize>() }
    var elegida by remember { mutableStateOf<Int?>(null) }
    var siguiente by remember { mutableStateOf(1) }
    var escribiendo by remember { mutableStateOf<Capa?>(null) }
    var eligiendoSticker by remember { mutableStateOf(false) }
    var eligiendo by remember { mutableStateOf(false) }
    var subiendo by remember { mutableStateOf(false) }
    var soloMejores by rememberSaveable { mutableStateOf(false) }
    var soloAmigos by rememberSaveable { mutableStateOf(false) }
    var pregunta by rememberSaveable { mutableStateOf("") }
    var preguntando by remember { mutableStateOf(false) }
    val menciones = remember { mutableStateListOf<String>() }
    var mencionando by remember { mutableStateOf(false) }
    var encuesta by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    var creandoEncuesta by remember { mutableStateOf(false) }
    var listaMejores by remember { mutableStateOf(false) }
    // Dibujo a mano: cada trazo con su color; mientras se dibuja, los dedos no mueven nada más.
    val trazos = remember { mutableStateListOf<Pair<Int, List<androidx.compose.ui.geometry.Offset>>>() }
    var dibujando by remember { mutableStateOf(false) }
    var colorTrazo by remember { mutableStateOf(2) }
    val coloresTrazo = (0..3).map { colorTexto(it) }
    val noSePudo = stringResource(R.string.historia_no_se_pudo)
    val elegirFoto = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { u ->
        if (u != null) { foto = u; video?.archivo?.delete(); video = null; zoom = 1f; mover = androidx.compose.ui.geometry.Offset.Zero }
    }
    val noSePudoVideo = stringResource(R.string.historia_video_no_se_pudo)
    val elegirVideo = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { u ->
        if (u != null) { preparandoVideo = true; alcance.launch {
            runCatching { io.github.fogonapps.social.prepararVideo(ctx, u, {}, {}, maximoMs = 15_000) }
                .onSuccess { v -> video?.archivo?.delete(); video = v; foto = v.poster; zoom = 1f; mover = androidx.compose.ui.geometry.Offset.Zero }
                .onFailure { android.widget.Toast.makeText(ctx, noSePudoVideo, android.widget.Toast.LENGTH_SHORT).show() }
            preparandoVideo = false
        } }
    }
    DisposableEffect(Unit) { onDispose { video?.archivo?.delete() } }
    fun agregar(c: Capa) { capas += c; escalas[c.id] = 1f; posiciones[c.id] = androidx.compose.ui.geometry.Offset.Zero; elegida = c.id; siguiente++ }
    Dialog(onCerrar, DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Column(Modifier.fillMaxSize().background(f.fondo).systemBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BotonIcono(Iconos.Cerrar, stringResource(R.string.historia_cerrar)) { onCerrar() }
                Text(stringResource(R.string.historia_nueva), style = MaterialTheme.typography.titleMedium, color = f.texto, modifier = Modifier.weight(1f))
            }
            // El lienzo (9:16): lo que queda adentro es la historia.
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val (color, _) = fondoHistoria(fondo)
                Box(Modifier.aspectRatio(9f / 16f, matchHeightConstraintsFirst = true).clip(forma(18.dp))
                    .drawWithContent { capa.record { this@drawWithContent.drawContent() }; drawLayer(capa) }
                    .background(color)
                    .pointerInput(foto) {
                        detectTransformGestures { _, paso, escala, _ ->
                            if (foto != null) { zoom = (zoom * escala).coerceIn(1f, 5f); mover += paso }
                        }
                    }
                    .pointerInput(Unit) { detectTapGestures { elegida = null } }, contentAlignment = Alignment.Center) {
                    foto?.let { coil3.compose.AsyncImage(it, null, Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = mover.x; translationY = mover.y },
                        contentScale = ContentScale.Crop) }
                    capas.forEach { c ->
                        key(c.id) {
                            val pos = posiciones[c.id] ?: androidx.compose.ui.geometry.Offset.Zero
                            val esc = escalas[c.id] ?: 1f
                            Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(pos.x.toInt(), pos.y.toInt()) }
                                .onSizeChanged { tamanos[c.id] = it }
                                .graphicsLayer { scaleX = esc; scaleY = esc }
                                .then(if (elegida == c.id) Modifier.border(1.5.dp, f.acentoTexto, forma(10.dp)) else Modifier)
                                .pointerInput(c.id) {
                                    detectTransformGestures { _, paso, escala, _ ->
                                        elegida = c.id
                                        val e = ((escalas[c.id] ?: 1f) * escala).coerceIn(0.3f, 6f)
                                        escalas[c.id] = e
                                        posiciones[c.id] = (posiciones[c.id] ?: androidx.compose.ui.geometry.Offset.Zero) + paso * e
                                    }
                                }
                                .pointerInput(c.id) { detectTapGestures(onDoubleTap = { if (c.sticker == null && c.recurso == null) escribiendo = c }) { elegida = c.id } }
                                .padding(4.dp)) {
                                when {
                                    c.sticker != null -> ImagenB64(c.sticker, Modifier.size(110.dp))
                                    c.recurso != null -> androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(c.recurso), null, Modifier.size(110.dp))
                                    else -> Text(c.texto, style = MaterialTheme.typography.headlineSmall, color = colorTexto(c.color), textAlign = TextAlign.Center,
                                        modifier = Modifier.widthIn(max = 260.dp).then(if (c.conFondo) Modifier.clip(forma(10.dp)).background(if (c.color == 1) f.texto else f.fondo).padding(horizontal = 10.dp, vertical = 4.dp) else Modifier))
                                }
                            }
                        }
                    }
                    // El tirador de la esquina de lo elegido: arrastrarlo lo agranda o lo achica (como en Instagram).
                    elegida?.let { id ->
                        val pos = posiciones[id] ?: androidx.compose.ui.geometry.Offset.Zero
                        val t = tamanos[id] ?: androidx.compose.ui.unit.IntSize(1, 1)
                        val esc = escalas[id] ?: 1f
                        Box(Modifier.offset { androidx.compose.ui.unit.IntOffset((pos.x + t.width * esc / 2f).toInt(), (pos.y + t.height * esc / 2f).toInt()) }
                            .size(34.dp).clip(CircleShape).background(f.tinta1).border(2.dp, f.fondo, CircleShape)
                            .pointerInput(id) {
                                detectDragGestures { cambio, paso ->
                                    cambio.consume()
                                    val base = ((tamanos[id]?.width ?: 1) + (tamanos[id]?.height ?: 1)).coerceAtLeast(1) / 2f
                                    escalas[id] = ((escalas[id] ?: 1f) * (1f + (paso.x + paso.y) / 2f / base)).coerceIn(0.3f, 6f)
                                }
                            }, contentAlignment = Alignment.Center) {
                            IconoRiso(Iconos.Mas, stringResource(R.string.historia_tamano), tamano = 16.dp, tinta = f.sobreTinta1)
                        }
                    }
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize().then(if (dibujando) Modifier.pointerInput(colorTrazo) {
                        detectDragGestures(onDragStart = { o -> trazos += colorTrazo to listOf(o) }) { cambio, _ ->
                            val ultimo = trazos.removeAt(trazos.lastIndex)
                            trazos += ultimo.first to (ultimo.second + cambio.position)
                        }
                    } else Modifier)) {
                        trazos.forEach { (c, puntos) ->
                            if (puntos.size > 1) drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(puntos[0].x, puntos[0].y); puntos.drop(1).forEach { lineTo(it.x, it.y) } },
                                coloresTrazo[c], style = androidx.compose.ui.graphics.drawscope.Stroke(width = 7.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                        }
                    }
                }
            }
            adjunto?.let { a ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { ControlesAdjunto(a) }
                    BotonIcono(Iconos.Cerrar, stringResource(R.string.historia_quitar_adjunto)) { adjunto = null }
                }
            }
            adjunto2?.let { a ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { ControlesAdjunto(a) }
                    BotonIcono(Iconos.Cerrar, stringResource(R.string.historia_quitar_adjunto)) { adjunto2 = null }
                }
                if (a.t("escena").isNotBlank()) Pildora((a["segundo"] as? JsonPrimitive)?.intOrNull?.let { stringResource(R.string.historia_escena_desde_n, it) } ?: stringResource(R.string.historia_escena_desde)) { pidiendoSegundo = true }
            }
            video?.let { v -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(androidx.compose.ui.res.pluralStringResource(R.plurals.social_video_reproducir, v.segundos, v.segundos), style = MaterialTheme.typography.bodySmall, color = f.texto, modifier = Modifier.weight(1f))
                BotonIcono(Iconos.Cerrar, stringResource(R.string.historia_quitar)) { v.archivo.delete(); video = null; foto = null }
            } }
            if (preparandoVideo) LinearProgressIndicator(Modifier.fillMaxWidth(), color = f.tinta2, trackColor = f.linea)
            encuesta?.let { (p, o) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.historia_encuesta) + ": " + p.ifBlank { o.joinToString(" / ") }, style = MaterialTheme.typography.bodySmall, color = f.texto, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BotonIcono(Iconos.Cerrar, stringResource(R.string.historia_quitar)) { encuesta = null }
                }
            }
            elegida?.let { id ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pildora(stringResource(R.string.historia_mas_grande)) { escalas[id] = ((escalas[id] ?: 1f) * 1.25f).coerceAtMost(6f) }
                    Pildora(stringResource(R.string.historia_mas_chico)) { escalas[id] = ((escalas[id] ?: 1f) / 1.25f).coerceAtLeast(0.3f) }
                    Pildora(stringResource(R.string.historia_quitar), Iconos.Borrar) { capas.removeAll { it.id == id }; elegida = null }
                }
            }
            if (dibujando) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                coloresTrazo.forEachIndexed { i, c ->
                    Box(Modifier.size(30.dp).clip(CircleShape).background(c).border(if (colorTrazo == i) 3.dp else 1.dp, if (colorTrazo == i) f.acentoTexto else f.linea, CircleShape).clickable { colorTrazo = i })
                }
                if (trazos.isNotEmpty()) Pildora(stringResource(R.string.historia_deshacer)) { trazos.removeAt(trazos.lastIndex) }
                Pildora(stringResource(R.string.historia_dibujo_listo), acento = true) { dibujando = false }
            } else androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Pildora(stringResource(R.string.historia_dibujar)) { dibujando = true; elegida = null; colorTrazo = if (foto != null) 0 else contraste(fondo) }
                Pildora(stringResource(if (foto == null) R.string.historia_foto else R.string.historia_otra_foto), Iconos.Mas) { elegirFoto.launch("image/*") }
                Pildora(stringResource(R.string.historia_video), Iconos.Mas) { elegirVideo.launch("video/*") }
                if (vm.fotosDeObras) Pildora(stringResource(R.string.historia_foto_obra)) { fotosObra = true }
                Pildora(stringResource(R.string.historia_texto_corto)) { escribiendo = Capa(0, color = if (foto != null) 0 else contraste(fondo)) }
                Pildora(stringResource(R.string.historia_sticker)) { eligiendoSticker = true }
                Pildora(stringResource(R.string.historia_musica)) { eligiendo = true }
                Pildora(stringResource(R.string.historia_encuesta)) { creandoEncuesta = true }
                Pildora(stringResource(R.string.historia_pregunta_corta)) { preguntando = true }
                Pildora(stringResource(R.string.historia_mencionar)) { mencionando = true }
                if (foto != null) Pildora(stringResource(R.string.historia_sin_foto), Iconos.Cerrar) { foto = null; video?.archivo?.delete(); video = null }
            }
            if (foto == null && !dibujando) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                (0..3).forEach { i ->
                    val (c, _) = fondoHistoria(i)
                    Box(Modifier.size(30.dp).clip(CircleShape).background(c).border(if (fondo == i) 3.dp else 1.dp, if (fondo == i) f.acentoTexto else f.linea, CircleShape).clickable { fondo = i })
                }
            }
            if (pregunta.isNotBlank() || menciones.isNotEmpty()) Text(listOfNotNull(pregunta.takeIf { it.isNotBlank() }?.let { "? $it" }, menciones.takeIf { it.isNotEmpty() }?.joinToString(" ") { "@$it" }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
            // Quién la ve: todos los que te siguen, solo tus amigos o tus mejores amigos.
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pildora(stringResource(R.string.historia_para_todos), acento = !soloAmigos && !soloMejores) { soloAmigos = false; soloMejores = false }
                Pildora(stringResource(R.string.historia_para_amigos), acento = soloAmigos) { soloAmigos = true; soloMejores = false }
                Pildora(stringResource(R.string.historia_mejores_amigos), acento = soloMejores) { soloMejores = true; soloAmigos = false }
                Pildora(stringResource(R.string.historia_lista_mejores)) { listaMejores = true }
            }
            if (capas.isEmpty() && trazos.isEmpty()) Text(stringResource(R.string.historia_editor_pista), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            if (subiendo) LinearProgressIndicator(Modifier.fillMaxWidth(), color = f.tinta1, trackColor = f.linea)
            BotonRiso(stringResource(R.string.historia_compartir), {
                subiendo = true; elegida = null; dibujando = false
                alcance.launch {
                    // Dos cuadros sin la selección, y se guarda lo que se ve en el lienzo.
                    androidx.compose.runtime.withFrameNanos { }; androidx.compose.runtime.withFrameNanos { }
                    val imagen = runCatching { capa.toImageBitmap().asAndroidBitmap() }.getOrNull()
                    val jpeg = imagen?.let { jpegDeLienzo(it) }
                    if (jpeg == null) { subiendo = false; android.widget.Toast.makeText(ctx, noSePudo, android.widget.Toast.LENGTH_SHORT).show(); return@launch }
                    val datos = buildJsonObject {
                        put("compuesta", true)
                        if (soloMejores) put("mejores", true)
                        if (soloAmigos) put("amigos", true)
                        if (pregunta.isNotBlank()) put("pregunta", pregunta.trim())
                        if (menciones.isNotEmpty()) put("menciones", JsonArray(menciones.map(::JsonPrimitive)))
                        encuesta?.let { (p, o) -> put("encuesta", buildJsonObject { put("pregunta", p); put("opciones", JsonArray(o.map(::JsonPrimitive))) }) }
                        // Las palabras van también como texto: para leerlas en voz alta y para moderar.
                        capas.mapNotNull { it.texto.takeIf { t -> t.isNotBlank() } }.joinToString(" · ").takeIf { it.isNotBlank() }?.let { put("texto", it.take(200)) }
                        put("fondo", fondo)
                        video?.let { put("segundos", it.segundos) }
                        adjunto?.let { put("adjunto", it) }
                        adjunto2?.let { put("adjunto2", it) }
                    }
                    val subir = video?.archivo
                    video = null
                    vm.red.subirHistoria(datos, jpeg, subir) { onCerrar() }
                    delay(if (subir != null) 60_000 else 15_000); subiendo = false
                }
            }, Modifier.fillMaxWidth(), habilitado = !subiendo && !preparandoVideo && (foto != null || capas.isNotEmpty() || trazos.isNotEmpty() || adjunto != null || adjunto2 != null || encuesta != null))
        }
    }
    escribiendo?.let { c -> EditarTextoHistoria(c, { escribiendo = null }) { nueva ->
        escribiendo = null
        if (c.id == 0) agregar(nueva.copy(id = siguiente)) else { val i = capas.indexOfFirst { it.id == c.id }; if (i >= 0) capas[i] = nueva.copy(id = c.id) }
    } }
    if (eligiendoSticker) ElegirStickerHistoria(vm, { eligiendoSticker = false }) { b64, recurso -> eligiendoSticker = false; agregar(Capa(siguiente, sticker = b64, recurso = recurso)) }
    if (eligiendo) ElegirAdjunto(vm, { eligiendo = false }) { if (it.t("tipo") == "cancion") adjunto = it else adjunto2 = it }
    if (listaMejores) HojaMejoresAmigos(vm) { listaMejores = false }
    if (preguntando) PedirTexto(stringResource(R.string.historia_pregunta_corta), stringResource(R.string.historia_pregunta_pista), { preguntando = false }) { pregunta = it.take(80); preguntando = false }
    if (mencionando) ElegirMenciones(vm, menciones) { mencionando = false }
    if (creandoEncuesta) CrearEncuesta({ creandoEncuesta = false }) { encuesta = it; creandoEncuesta = false }
    if (pidiendoSegundo) PedirTexto(stringResource(R.string.historia_escena_desde), stringResource(R.string.historia_escena_segundo), { pidiendoSegundo = false }) { t ->
        pidiendoSegundo = false
        val s = t.trim().split(":").mapNotNull { it.toIntOrNull() }.fold(0) { acc, n -> acc * 60 + n }
        adjunto2 = adjunto2?.let { a -> JsonObject(if (s > 0) a + ("segundo" to JsonPrimitive(s)) else a - "segundo") }
    }
    if (fotosObra) ElegirFotoDeObra(vm, { fotosObra = false }) { url -> fotosObra = false; video?.archivo?.delete(); video = null; foto = url; zoom = 1f; mover = androidx.compose.ui.geometry.Offset.Zero }
}

/** Elegir de fondo una foto de una película, serie, libro o persona (como al cambiar una portada). */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ElegirFotoDeObra(vm: InterfazSocial, onCerrar: () -> Unit, onElegir: (String) -> Unit) {
    val f = FogonTema.actual
    val alcance = rememberCoroutineScope()
    var buscar by rememberSaveable { mutableStateOf("") }
    var resultados by remember { mutableStateOf<List<JsonObject>?>(null) }
    var fotos by remember { mutableStateOf<List<String>?>(null) }
    var cargando by remember { mutableStateOf(false) }
    LaunchedEffect(buscar) {
        if (buscar.trim().length < 2) { resultados = null; return@LaunchedEffect }
        delay(450); resultados = runCatching { vm.buscarConFotos(buscar.trim()) }.getOrDefault(emptyList())
    }
    ModalBottomSheet(onCerrar, containerColor = f.fondo, contentColor = f.texto) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.historia_foto_obra), style = MaterialTheme.typography.titleMedium, color = f.texto)
            val lista = fotos
            if (lista != null) {
                Pildora(stringResource(R.string.historia_otra_obra), Iconos.Atras) { fotos = null }
                if (lista.isEmpty()) Pista(stringResource(R.string.historia_sin_fotos))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    lista.forEach { u ->
                        coil3.compose.AsyncImage(u, null, Modifier.width(104.dp).aspectRatio(2f / 3f).clip(forma(10.dp)).clickable { onElegir(u) }, contentScale = ContentScale.Crop)
                    }
                }
            } else {
                OutlinedTextField(buscar, { buscar = it.take(80) }, Modifier.fillMaxWidth(), singleLine = true, shape = forma(18.dp),
                    placeholder = { Text(stringResource(R.string.historia_buscar_obra_persona)) })
                if (cargando) LinearProgressIndicator(Modifier.fillMaxWidth(), color = f.tinta1, trackColor = f.linea)
                resultados?.let { l -> if (l.isEmpty()) Pista(stringResource(R.string.historia_sin_fotos)) }
                resultados.orEmpty().forEach { r ->
                    Row(Modifier.fillMaxWidth().clip(forma(12.dp)).clickable {
                        cargando = true
                        alcance.launch { fotos = runCatching { vm.fotosDe(r.t("fuente")) }.getOrDefault(emptyList()); cargando = false }
                    }.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        coil3.compose.AsyncImage(r.t("imagen"), null, Modifier.size(44.dp).clip(forma(8.dp)), contentScale = ContentScale.Crop)
                        Column(Modifier.weight(1f)) {
                            Text(r.t("titulo"), style = MaterialTheme.typography.bodyMedium, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(r.t("detalle"), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** La imagen del lienzo a JPEG para subir (lado mayor de hasta 1920 px y menos de 900 KB). */
private suspend fun jpegDeLienzo(b: android.graphics.Bitmap): ByteArray? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
    runCatching {
        val escala = minOf(1f, 1920f / maxOf(b.width, b.height))
        val chica = if (escala < 1f) android.graphics.Bitmap.createScaledBitmap(b, (b.width * escala).toInt(), (b.height * escala).toInt(), true) else b
        val rgb = chica.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
        var calidad = 90
        var salida: ByteArray
        do {
            salida = java.io.ByteArrayOutputStream().also { rgb.compress(android.graphics.Bitmap.CompressFormat.JPEG, calidad, it) }.toByteArray()
            calidad -= 10
        } while (salida.size > 900_000 && calidad > 30)
        salida.takeIf { it.size <= 900_000 }
    }.getOrNull()
}

/** Un sticker guardado (WebP en base64). */
@Composable
private fun ImagenB64(b64: String, modifier: Modifier) {
    val imagen = remember(b64) { runCatching { java.util.Base64.getDecoder().decode(b64).let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }.getOrNull() }
    if (imagen != null) androidx.compose.foundation.Image(imagen, null, modifier)
}

/** Escribir (o cambiar) un texto: color y si va sobre un fondo. */
@Composable
private fun EditarTextoHistoria(c: Capa, onCerrar: () -> Unit, onListo: (Capa) -> Unit) {
    val f = FogonTema.actual
    var texto by remember { mutableStateOf(c.texto) }
    var color by remember { mutableStateOf(c.color) }
    var conFondo by remember { mutableStateOf(c.conFondo) }
    AlertDialog(onCerrar, containerColor = f.fondo,
        title = { Text(stringResource(R.string.historia_texto_corto), color = f.texto) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(texto, { texto = it.take(120) }, Modifier.fillMaxWidth(), minLines = 2, shape = forma(18.dp), placeholder = { Text(stringResource(R.string.historia_texto)) })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    (0..3).forEach { i ->
                        Box(Modifier.size(30.dp).clip(CircleShape).background(colorTexto(i)).border(if (color == i) 3.dp else 1.dp, if (color == i) f.acentoTexto else f.linea, CircleShape).clickable { color = i })
                    }
                }
                Pildora(stringResource(R.string.historia_con_fondo), acento = conFondo) { conFondo = !conFondo }
            }
        },
        confirmButton = { TextButton({ onListo(c.copy(texto = texto.trim(), color = color, conFondo = conFondo)) }, enabled = texto.isNotBlank()) { Text(stringResource(R.string.sancion_listo), color = f.acentoTexto) } },
        dismissButton = { TextButton(onCerrar) { Text(stringResource(R.string.sancion_cancelar), color = f.textoSuave) } })
}

/** Los stickers para la historia: los de Fogón y los tuyos (si la app los tiene, como El Cajón). */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ElegirStickerHistoria(vm: InterfazSocial, onCerrar: () -> Unit, onElegir: (String?, Int?) -> Unit) {
    val f = FogonTema.actual
    val propios = remember { vm.stickersApp() }
    ModalBottomSheet(onCerrar, containerColor = f.fondo, contentColor = f.texto) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.historia_sticker), style = MaterialTheme.typography.titleMedium, color = f.texto)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                STICKERS_FOGON.forEach { r ->
                    Box(Modifier.size(72.dp).clip(forma(14.dp)).background(f.superficie).clickable { onElegir(null, r) }.padding(8.dp)) {
                        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(r), null, Modifier.fillMaxSize())
                    }
                }
                propios.forEach { s ->
                    Box(Modifier.size(72.dp).clip(forma(14.dp)).background(f.superficie).clickable { onElegir(s, null) }.padding(4.dp)) { ImagenB64(s, Modifier.fillMaxSize()) }
                }
            }
        }
    }
}

/** La música (u otro adjunto) en pequeño: el avance, el título y sus botones. Va sobre la historia ya armada. */
@Composable
fun ControlesAdjunto(a: JsonObject, sonar: Boolean = false) {
    val f = FogonTema.actual
    val uri = LocalUriHandler.current
    Row(Modifier.fillMaxWidth().clip(FormaPildora).background(f.fondo.copy(alpha = 0.9f)).border(f.borde, f.linea, FormaPildora).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val audio = a.t("audio"); val enlace = a.t("enlace"); val escena = a.t("escena")
        if (audio.isNotBlank()) Avance(audio, sonar)
        else a.t("imagen").takeIf { it.isNotBlank() }?.let { coil3.compose.AsyncImage(if (it.startsWith("https://")) it else ClienteSocial.imagen(it), null, Modifier.size(36.dp).clip(CircleShape), contentScale = ContentScale.Crop) }
        Column(Modifier.weight(1f)) {
            Text(a.t("titulo"), style = MaterialTheme.typography.labelLarge, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detalle = listOf(a.t("detalle"), a.t("cifra")).filter { it.isNotBlank() }.joinToString(" · ")
            if (detalle.isNotBlank()) Text(detalle, style = MaterialTheme.typography.labelSmall, color = f.textoSuave, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (audio.isNotBlank() && enlace.isNotBlank()) EnlaceAppleMusic(enlace)
        if (escena.isNotBlank()) Pildora(stringResource(R.string.historia_ver_escena)) {
            uri.openUri("https://www.youtube.com/watch?v=$escena" + (a["segundo"] as? JsonPrimitive)?.intOrNull?.let { "&t=${it}s" }.orEmpty())
        }
    }
}

/* ----- Ver historias ----- */

/**
 * Las historias a pantalla completa: barras de avance, tocar a la derecha (siguiente) o a la izquierda (anterior),
 * mantener para pausar. En las propias: quién la vio y borrarla; en las ajenas: reportarla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisorHistorias(vm: InterfazSocial, grupos: List<GrupoHistoriasSocial>, inicio: Int, onCerrar: () -> Unit) {
    val f = FogonTema.actual
    val yo by vm.red.usuario.collectAsStateWithLifecycle()
    val alcance = rememberCoroutineScope()
    var g by remember { mutableStateOf(inicio.coerceIn(0, grupos.lastIndex)) }
    var h by remember { mutableStateOf(grupos.getOrNull(inicio)?.historias?.indexOfFirst { !it.vista }?.takeIf { it >= 0 } ?: 0) }
    var pausado by remember { mutableStateOf(false) }
    var progreso by remember { mutableFloatStateOf(0f) }
    var vistas by remember { mutableStateOf<List<VistaHistoriaSocial>?>(null) }
    var reportando by remember { mutableStateOf(false) }
    var respuesta by remember { mutableStateOf("") }
    var destacando by remember { mutableStateOf(false) }
    var portadaLista by remember { mutableStateOf(false) }
    if (portadaLista) LaunchedEffect(Unit) { delay(1500); portadaLista = false }
    val grupo = grupos.getOrNull(g) ?: run { onCerrar(); return }
    val historia = grupo.historias.getOrNull(h) ?: run { onCerrar(); return }
    val propia = grupo.persona.usuario == yo
    fun siguiente() { if (h < grupo.historias.lastIndex) h++ else if (g < grupos.lastIndex) { g++; h = 0 } else onCerrar() }
    fun anterior() { if (h > 0) h-- else if (g > 0) { g--; h = 0 } }
    val adjunto = historia.datos["adjunto"] as? JsonObject
    val videoHistoria = historia.datos.t("video")
    val duracion = when {
        videoHistoria.isNotBlank() -> ((historia.datos["segundos"] as? JsonPrimitive)?.intOrNull ?: 15).coerceIn(3, 15) * 1000f
        adjunto?.t("audio").isNullOrBlank() -> 6000f
        else -> 15000f
    }
    LaunchedEffect(historia.id) { progreso = 0f; vm.red.verHistoria(historia) }
    LaunchedEffect(historia.id, pausado, vistas, reportando, respuesta.isNotEmpty(), destacando) {
        if (pausado || vistas != null || reportando || respuesta.isNotEmpty() || destacando) return@LaunchedEffect
        while (progreso < 1f) { delay(50); progreso += 50f / duracion }
        siguiente()
    }
    Dialog(onCerrar, DialogProperties(usePlatformDefaultWidth = false)) {
        val imagen = historia.datos.t("imagen")
        val (color, sobre) = fondoHistoria((historia.datos["fondo"] as? JsonPrimitive)?.intOrNull ?: 0)
        Box(Modifier.fillMaxSize().background(if (imagen.isNotBlank()) f.fondo else color).systemBarsPadding()
            .pointerInput(historia.id) {
                detectTapGestures(onPress = { pausado = true; tryAwaitRelease(); pausado = false },
                    onTap = { o -> if (o.x < size.width / 3f) anterior() else siguiente() })
            }) {
            if (imagen.isNotBlank()) coil3.compose.AsyncImage(ClienteSocial.imagen(imagen), stringResource(R.string.historia_foto_de, grupo.persona.nombre.ifBlank { grupo.persona.usuario }),
                Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            if (videoHistoria.isNotBlank()) key(historia.id) {
                VideoHistoria(vm::cabeceras, videoHistoria, pausado || vistas != null || reportando || respuesta.isNotEmpty() || destacando, Modifier.fillMaxSize())
            }
            val compuesta = (historia.datos["compuesta"] as? JsonPrimitive)?.booleanOrNull == true
            if (!compuesta) Column(Modifier.align(Alignment.Center).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val texto = historia.datos.t("texto")
                if (texto.isNotBlank() && imagen.isBlank()) Text(texto, style = MaterialTheme.typography.headlineSmall, color = sobre, textAlign = TextAlign.Center)
                if (adjunto != null && imagen.isBlank()) TarjetaAdjunto(adjunto, sonar = true, solida = true)
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                (historia.datos["encuesta"] as? JsonObject)?.let { EncuestaHistoria(vm, historia, it, propia) }
                historia.datos.t("pregunta").takeIf { it.isNotBlank() }?.let { PreguntaHistoria(vm, historia, it, propia) { escribiendo -> pausado = escribiendo } }
                (historia.datos["menciones"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.takeIf { it.isNotEmpty() }?.let { l ->
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        l.forEach { u -> Pildora("@$u") { onCerrar(); vm.navegacion.perfil(u) } }
                    }
                }
                if (compuesta) adjunto?.let { ControlesAdjunto(it, sonar = true) }
                (historia.datos["adjunto2"] as? JsonObject)?.let { ControlesAdjunto(it, sonar = adjunto == null) }
                val texto = historia.datos.t("texto")
                if (!compuesta && texto.isNotBlank() && imagen.isNotBlank()) Text(texto, style = MaterialTheme.typography.titleMedium, color = f.texto, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(forma(12.dp)).background(f.fondo.copy(alpha = 0.75f)).padding(10.dp))
                if (!compuesta && adjunto != null && imagen.isNotBlank()) TarjetaAdjunto(adjunto, sonar = true, solida = true)
                // Responder por mensaje (en las apps con chat).
                if (!propia && vm.respondeHistorias) Row(Modifier.clip(FormaPildora).background(f.fondo.copy(alpha = 0.85f)).padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.text.BasicTextField(respuesta, { respuesta = it.take(500) }, Modifier.weight(1f).padding(vertical = 12.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = f.texto), cursorBrush = androidx.compose.ui.graphics.SolidColor(f.texto),
                        decorationBox = { campo -> if (respuesta.isEmpty()) Text(stringResource(R.string.historia_responder_a, grupo.persona.nombre.ifBlank { grupo.persona.usuario }), color = f.textoSuave, style = MaterialTheme.typography.bodyMedium); campo() })
                    if (respuesta.isNotBlank()) BotonIcono(Iconos.Enviar, stringResource(R.string.historia_responder)) { vm.responderHistoria(grupo.persona.usuario, respuesta.trim()); respuesta = "" }
                }
                Row(Modifier.clip(FormaPildora).background(f.fondo.copy(alpha = 0.85f)).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (propia) {
                        Pildora(stringResource(R.string.historia_vistas), Iconos.Anime) { alcance.launch { vistas = vm.red.vistasHistoria(historia) } }
                        Pildora(stringResource(R.string.historia_destacar), Iconos.Estrella) { destacando = true }
                        if (historia.destacada.isNotBlank()) Pildora(stringResource(R.string.historia_portada), acento = portadaLista) { vm.red.portadaDestacada(historia); portadaLista = true }
                        Pildora(stringResource(R.string.historia_borrar), Iconos.Borrar) {
                            pausado = true
                            vm.confirmar(vm.tx(R.string.historia_borrar), vm.tx(R.string.historia_borrar_confirmar)) { vm.red.borrarHistoria(historia); onCerrar() }
                        }
                    } else {
                        var gusta by remember(historia.id) { mutableStateOf(historia.meGusta) }
                        Pildora(stringResource(if (gusta) R.string.historia_te_gusto else R.string.historia_me_gusta), Iconos.Corazon, acento = gusta) { gusta = !gusta; vm.red.meGustaHistoria(historia, gusta) }
                        Pildora(stringResource(if (grupo.silenciada) R.string.historia_activar else R.string.historia_silenciar)) { vm.red.silenciarHistorias(grupo.persona.usuario, !grupo.silenciada); onCerrar() }
                        Pildora(stringResource(R.string.social_red_reportar), Iconos.Bandera) { reportando = true }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.clip(FormaPildora).background(f.fondo.copy(alpha = 0.6f)).padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    grupo.historias.forEachIndexed { i, _ ->
                        val lleno = when { i < h -> 1f; i == h -> progreso.coerceIn(0f, 1f); else -> 0f }
                        Box(Modifier.weight(1f).height(3.dp).clip(FormaPildora).background(f.linea)) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(lleno).background(f.texto))
                        }
                    }
                }
                Row(Modifier.clip(FormaPildora).background(f.fondo.copy(alpha = 0.7f)).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AvatarRed(grupo.persona, 34.dp)
                    Text(grupo.persona.nombre.ifBlank { grupo.persona.usuario }, style = MaterialTheme.typography.titleSmall, color = f.texto)
                    Text(haceCuanto(historia.cuando) + if (historia.mejores) " · " + stringResource(R.string.historia_mejores_amigos) else "", style = MaterialTheme.typography.bodySmall,
                        color = if (historia.mejores) f.acentoTexto else f.textoSuave, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BotonIcono(Iconos.Cerrar, stringResource(R.string.historia_cerrar)) { onCerrar() }
                }
            }
        }
        vistas?.let { l ->
            ModalBottomSheet({ vistas = null }, containerColor = f.fondo, contentColor = f.texto) {
                Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                    Text(pluralStringResourceSeguro(l.size), style = MaterialTheme.typography.titleMedium, color = f.texto, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    l.forEach { v -> FilaPersona(v.persona, { vistas = null; onCerrar(); vm.navegacion.perfil(v.persona.usuario) }) {
                        if (v.meGusta) IconoRiso(Iconos.Corazon, stringResource(R.string.historia_te_gusto), tamano = 20.dp, tinta = f.acentoTexto, relleno = f.acentoTexto)
                    } }
                }
            }
        }
        if (destacando) PedirTexto(stringResource(R.string.historia_destacar), stringResource(R.string.historia_nombre_destacada), { destacando = false }) {
            vm.red.destacarHistoria(historia, it); destacando = false
        }
        if (reportando) ModalBottomSheet({ reportando = false }, containerColor = f.fondo, contentColor = f.texto) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                MotivosReporte { codigo, texto -> vm.red.reportar("historia", "${historia.id}", "$codigo: $texto"); reportando = false; siguiente() }
            }
        }
    }
}

@Composable
private fun pluralStringResourceSeguro(n: Int) = androidx.compose.ui.res.pluralStringResource(R.plurals.historia_vistas_n, n, n)

/**
 * El avatar de un perfil con el aro de sus historias de esta app: si hay, tocarlo las abre. [propio]: el perfil es el
 * de quien mira (sus historias ya están en la barra).
 */
@Composable
fun AvatarPerfilConHistorias(vm: InterfazSocial, persona: PersonaSocial, tamano: Dp = 96.dp, contenido: @Composable () -> Unit = { AvatarRed(persona, tamano) }) {
    val grupos by vm.red.historias.collectAsStateWithLifecycle()
    var grupo by remember(persona.usuario) { mutableStateOf(grupos.firstOrNull { it.persona.usuario == persona.usuario }) }
    LaunchedEffect(persona.usuario, grupos) { grupo = grupos.firstOrNull { it.persona.usuario == persona.usuario } ?: vm.red.historiasDe(persona.usuario) }
    var abierto by remember { mutableStateOf(false) }
    val f = FogonTema.actual
    val g = grupo
    Box(Modifier.size(tamano + 10.dp).then(if (g != null) Modifier.clip(CircleShape).clickable { abierto = true } else Modifier), contentAlignment = Alignment.Center) {
        if (g != null) Box(Modifier.matchParentSize().border(if (g.todasVistas) 1.5.dp else 3.dp, if (g.todasVistas) f.linea else f.tinta1, CircleShape))
        contenido()
    }
    if (abierto && g != null) VisorHistorias(vm, listOf(g), 0) { abierto = false }
}

/** Tu foto de perfil con el aro de tus historias (si tienes cuenta y alguna vigente, tocarla las abre). */
@Composable
fun AroHistoriasPropias(vm: InterfazSocial, tamano: Dp = 96.dp, contenido: @Composable () -> Unit) {
    val yo by vm.red.usuario.collectAsStateWithLifecycle()
    val u = yo ?: run { contenido(); return }
    AvatarPerfilConHistorias(vm, PersonaSocial(u, "", foto = true), tamano, contenido)
}

/** Elegir tus mejores amigos (entre tus amigos): ellos ven tus historias «solo mejores amigos». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaMejoresAmigos(vm: InterfazSocial, onCerrar: () -> Unit) {
    val f = FogonTema.actual
    val amigos by vm.red.amigos.collectAsStateWithLifecycle()
    val elegidos = remember { mutableStateListOf<String>() }
    LaunchedEffect(Unit) { vm.red.cargarAmigos(); elegidos.addAll(vm.red.mejoresAmigos()) }
    ModalBottomSheet(onCerrar, containerColor = f.fondo, contentColor = f.texto) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.historia_lista_mejores), style = MaterialTheme.typography.titleMedium, color = f.texto, modifier = Modifier.padding(horizontal = 16.dp))
            Pista(stringResource(R.string.historia_mejores_explica), Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            if (amigos.isEmpty()) Pista(stringResource(R.string.historia_sin_amigos), Modifier.padding(16.dp))
            amigos.forEach { p ->
                FilaPersona(p, { if (p.usuario in elegidos) elegidos.remove(p.usuario) else elegidos.add(p.usuario) }) {
                    Checkbox(p.usuario in elegidos, { si -> if (si) elegidos.add(p.usuario) else elegidos.remove(p.usuario) },
                        colors = CheckboxDefaults.colors(checkedColor = f.tinta1, checkmarkColor = f.sobreTinta1))
                }
            }
            BotonRiso(stringResource(R.string.historia_guardar_lista), { vm.red.ponerMejoresAmigos(elegidos.toList()); onCerrar() }, Modifier.fillMaxWidth().padding(16.dp))
        }
    }
}

/** Crear una encuesta: una pregunta y de 2 a 4 opciones. */
@Composable
private fun CrearEncuesta(onCerrar: () -> Unit, onListo: (Pair<String, List<String>>) -> Unit) {
    val f = FogonTema.actual
    var pregunta by remember { mutableStateOf("") }
    val opciones = remember { mutableStateListOf("", "") }
    AlertDialog(onCerrar, containerColor = f.fondo,
        title = { Text(stringResource(R.string.historia_encuesta), color = f.texto) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(pregunta, { pregunta = it.take(80) }, Modifier.fillMaxWidth(), singleLine = true, shape = forma(14.dp), placeholder = { Text(stringResource(R.string.historia_pregunta)) })
                opciones.forEachIndexed { i, o ->
                    OutlinedTextField(o, { opciones[i] = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true, shape = forma(14.dp), placeholder = { Text(stringResource(R.string.historia_opcion, i + 1)) })
                }
                if (opciones.size < 4) Pildora(stringResource(R.string.historia_otra_opcion), Iconos.Mas) { opciones.add("") }
            }
        },
        confirmButton = { TextButton({ onListo(pregunta.trim() to opciones.map { it.trim() }.filter { it.isNotBlank() }) }, enabled = opciones.count { it.isNotBlank() } >= 2) { Text(stringResource(R.string.sancion_listo), color = f.acentoTexto) } },
        dismissButton = { TextButton(onCerrar) { Text(stringResource(R.string.sancion_cancelar), color = f.textoSuave) } })
}

/** La encuesta de una historia: tocar una opción vota; después (o si es tuya) se ven los porcentajes. */
@Composable
private fun EncuestaHistoria(vm: InterfazSocial, h: HistoriaSocial, e: JsonObject, propia: Boolean) {
    val f = FogonTema.actual
    val alcance = rememberCoroutineScope()
    var actual by remember(h.id) { mutableStateOf(h) }
    val opciones = (e["opciones"] as? JsonArray).orEmpty().map { (it as? JsonPrimitive)?.contentOrNull.orEmpty() }
    val total = actual.votos.sum()
    val verResultados = propia || actual.miVoto >= 0
    Column(Modifier.fillMaxWidth().clip(forma(18.dp)).background(f.fondo).border(f.borde, f.linea, forma(18.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val pregunta = e.t("pregunta")
        if (pregunta.isNotBlank()) Text(pregunta, style = MaterialTheme.typography.titleSmall, color = f.texto)
        opciones.forEachIndexed { i, o ->
            val pct = if (total > 0) (actual.votos.getOrNull(i) ?: 0) * 100 / total else 0
            Box(Modifier.fillMaxWidth().clip(FormaPildora).background(f.superficie).border(if (actual.miVoto == i) 2.dp else f.borde, if (actual.miVoto == i) f.acentoTexto else f.linea, FormaPildora)
                .clickable(enabled = !propia) { alcance.launch { vm.red.votarHistoria(actual, i)?.let { actual = actual.copy(votos = it.votos, miVoto = it.miVoto) } } }) {
                if (verResultados) Box(Modifier.matchParentSize().fillMaxWidth(pct / 100f).background(f.tinta1.copy(alpha = 0.35f)))
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(o, style = MaterialTheme.typography.bodyMedium, color = f.texto, modifier = Modifier.weight(1f))
                    if (verResultados) Text("$pct %", style = MaterialTheme.typography.labelLarge, color = f.texto)
                }
            }
        }
        if (propia) Text(androidx.compose.ui.res.pluralStringResource(R.plurals.historia_votos, total, total), style = MaterialTheme.typography.labelSmall, color = f.textoSuave)
    }
}

/** Las destacadas de un perfil: círculos con su nombre; tocar uno abre sus historias (no vencen). */
@Composable
fun DestacadasPerfil(vm: InterfazSocial, usuario: String) {
    val f = FogonTema.actual
    var grupos by remember(usuario) { mutableStateOf<List<GrupoDestacadasSocial>>(emptyList()) }
    var abierto by remember { mutableStateOf<GrupoDestacadasSocial?>(null) }
    val cumplesHoy by vm.red.cumplesHoy.collectAsStateWithLifecycle()
    LaunchedEffect(usuario) { grupos = vm.red.destacadas(usuario); vm.red.cargarCumples() }
    if (usuario in cumplesHoy) Text(stringResource(R.string.cumple_hoy_perfil), style = MaterialTheme.typography.bodyMedium, color = f.acentoTexto)
    if (grupos.isEmpty()) return
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        grupos.forEach { g ->
            Column(Modifier.width(70.dp).clip(forma(14.dp)).clickable { abierto = g }, horizontalAlignment = Alignment.CenterHorizontally) {
                val imagen = g.historias.firstNotNullOfOrNull { it.datos.t("imagen").takeIf { i -> i.isNotBlank() } }
                Box(Modifier.size(58.dp).clip(CircleShape).background(f.superficie).border(1.5.dp, f.linea, CircleShape), contentAlignment = Alignment.Center) {
                    if (imagen != null) coil3.compose.AsyncImage(ClienteSocial.imagen(imagen), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else IconoRiso(Iconos.Estrella, null, tamano = 22.dp, tinta = f.textoSuave)
                }
                Text(g.titulo, style = MaterialTheme.typography.labelSmall, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    abierto?.let { g ->
        val persona = PersonaSocial(usuario, g.titulo, foto = true)
        VisorHistorias(vm, listOf(GrupoHistoriasSocial(persona, g.historias.map { it.copy(vista = true) })), 0) { abierto = null }
    }
}

/** La canción del perfil (de cualquier app): portada, título, artista, su avance de 30 s y Apple Music. */
@Composable
fun TarjetaCancionPerfil(datos: JsonObject, onCambiar: (() -> Unit)? = null) {
    val f = FogonTema.actual
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).tarjetaFogon().then(if (onCambiar != null) Modifier.clickable(onClick = onCambiar) else Modifier).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val portada = datos.t("portada")
        if (portada.startsWith("https://")) coil3.compose.AsyncImage(portada, null, Modifier.size(48.dp).clip(forma(8.dp)), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f)) {
            Text(datos.t("cancion"), style = MaterialTheme.typography.titleSmall, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(datos.t("creador"), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1, overflow = TextOverflow.Ellipsis)
            EnlaceAppleMusic(datos.t("enlace"))
        }
        BotonAvance(datos.t("audio"))
    }
}

/** En el perfil propio de las apps sin canción propia de El Cajón: ponerla, cambiarla o quitarla. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CancionPerfilPropia(vm: InterfazSocial) {
    val f = FogonTema.actual
    val red = vm.red as? RedApp ?: return
    val identidad by red.identidad.collectAsStateWithLifecycle()
    val actual = identidad?.partes?.get("cancion") as? JsonObject
    var buscando by remember { mutableStateOf(false) }
    if (actual != null) TarjetaCancionPerfil(actual) { buscando = true }
    else BotonSecundario(stringResource(R.string.cancion_perfil_poner), { buscando = true }, Modifier.fillMaxWidth())
    if (buscando) ModalBottomSheet({ buscando = false }, containerColor = f.fondo, contentColor = f.texto) {
        var q by rememberSaveable { mutableStateOf("") }
        var l by remember { mutableStateOf<List<CancionSocial>>(emptyList()) }
        LaunchedEffect(q) { if (q.trim().length >= 2) { delay(400); l = vm.red.canciones(q.trim()) } else l = emptyList() }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.cancion_perfil_titulo), style = MaterialTheme.typography.titleMedium, color = f.texto)
            OutlinedTextField(q, { q = it.take(60) }, Modifier.fillMaxWidth(), singleLine = true, shape = forma(18.dp), placeholder = { Text(stringResource(R.string.historia_buscar_cancion)) })
            if (actual != null) Pildora(stringResource(R.string.cancion_perfil_quitar), Iconos.Cerrar) { red.ponerCancionPerfil(null); buscando = false }
            l.forEach { c ->
                Row(Modifier.fillMaxWidth().clip(forma(12.dp)).clickable { red.ponerCancionPerfil(c); buscando = false }.padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    coil3.compose.AsyncImage(c.portada, null, Modifier.size(44.dp).clip(forma(8.dp)), contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f)) {
                        Text(c.titulo, style = MaterialTheme.typography.bodyMedium, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(c.artista, style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    BotonAvance(c.audio)
                }
            }
        }
    }
}

/** El cumpleaños en los ajustes de la red: solo día y mes; lo ven solo tus amigos. */
@Composable
fun EditorCumple(vm: InterfazSocial) {
    val f = FogonTema.actual
    val cumple by vm.red.cumple.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.red.cargarCumples() }
    var dia by remember(cumple) { mutableStateOf(cumple.substringAfter('-', "").trimStart('0')) }
    var mes by remember(cumple) { mutableStateOf(cumple.substringBefore('-', "").trimStart('0')) }
    val d = dia.toIntOrNull(); val m = mes.toIntOrNull()
    val valida = d != null && m != null && runCatching { java.time.MonthDay.of(m, d) }.isSuccess
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Pista(stringResource(R.string.cumple_explica))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(dia, { dia = it.filter(Char::isDigit).take(2) }, Modifier.width(90.dp), singleLine = true, shape = forma(14.dp), label = { Text(stringResource(R.string.cumple_dia)) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
            OutlinedTextField(mes, { mes = it.filter(Char::isDigit).take(2) }, Modifier.width(90.dp), singleLine = true, shape = forma(14.dp), label = { Text(stringResource(R.string.cumple_mes)) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pildora(stringResource(R.string.cumple_guardar), acento = true) { if (valida) vm.red.ponerCumple("%02d-%02d".format(m, d)) }
            if (cumple.isNotBlank()) Pildora(stringResource(R.string.cumple_quitar)) { vm.red.ponerCumple(""); dia = ""; mes = "" }
        }
        if (cumple.isNotBlank()) Text(stringResource(R.string.cumple_guardado), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
    }
}

/** Mencionar a alguien (@): elegir entre tus amigos o escribir el usuario; reciben un aviso. Hasta 5. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ElegirMenciones(vm: InterfazSocial, elegidas: MutableList<String>, onCerrar: () -> Unit) {
    val f = FogonTema.actual
    val amigos by vm.red.amigos.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.red.cargarAmigos() }
    var escrito by remember { mutableStateOf("") }
    ModalBottomSheet(onCerrar, containerColor = f.fondo, contentColor = f.texto) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.historia_mencionar), style = MaterialTheme.typography.titleMedium, color = f.texto, modifier = Modifier.padding(horizontal = 16.dp))
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(escrito, { escrito = it.removePrefix("@").lowercase().filter { c -> c.isLetterOrDigit() || c == '.' || c == '_' }.take(30) }, Modifier.weight(1f), singleLine = true, shape = forma(14.dp), placeholder = { Text("@usuario") })
                Pildora(stringResource(R.string.historia_agregar), Iconos.Mas) { if (escrito.length >= 3 && escrito !in elegidas && elegidas.size < 5) elegidas.add(escrito); escrito = "" }
            }
            amigos.forEach { p ->
                FilaPersona(p, { if (p.usuario in elegidas) elegidas.remove(p.usuario) else if (elegidas.size < 5) elegidas.add(p.usuario) }) {
                    Checkbox(p.usuario in elegidas, { si -> if (si && elegidas.size < 5) elegidas.add(p.usuario) else elegidas.remove(p.usuario) },
                        colors = CheckboxDefaults.colors(checkedColor = f.tinta1, checkmarkColor = f.sobreTinta1))
                }
            }
            BotonRiso(stringResource(R.string.sancion_listo), onCerrar, Modifier.fillMaxWidth().padding(16.dp))
        }
    }
}

/** La pregunta de una historia: los demás responden (solo la lee quien la subió); la propia muestra las respuestas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreguntaHistoria(vm: InterfazSocial, h: HistoriaSocial, pregunta: String, propia: Boolean, alEscribir: (Boolean) -> Unit) {
    val f = FogonTema.actual
    val alcance = rememberCoroutineScope()
    var texto by remember(h.id) { mutableStateOf("") }
    var enviada by remember(h.id) { mutableStateOf(false) }
    var respuestas by remember { mutableStateOf<List<RespuestaHistoriaSocial>?>(null) }
    Column(Modifier.fillMaxWidth().clip(forma(18.dp)).background(f.fondo).border(f.borde, f.linea, forma(18.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(pregunta, style = MaterialTheme.typography.titleSmall, color = f.texto)
        if (propia) Pildora(stringResource(R.string.historia_ver_respuestas)) { alEscribir(true); alcance.launch { respuestas = vm.red.respuestasPregunta(h) } }
        else if (enviada) Text(stringResource(R.string.historia_respuesta_enviada), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        else Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(texto, { texto = it.take(200); alEscribir(it.isNotEmpty()) }, Modifier.weight(1f), shape = forma(14.dp), placeholder = { Text(stringResource(R.string.historia_tu_respuesta)) })
            if (texto.isNotBlank()) BotonIcono(Iconos.Enviar, stringResource(R.string.historia_responder)) { vm.red.responderPregunta(h, texto.trim()); enviada = true; alEscribir(false) }
        }
    }
    respuestas?.let { l ->
        ModalBottomSheet({ respuestas = null; alEscribir(false) }, containerColor = f.fondo, contentColor = f.texto) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
                Text(pregunta, style = MaterialTheme.typography.titleMedium, color = f.texto, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                if (l.isEmpty()) Pista(stringResource(R.string.historia_sin_respuestas), Modifier.padding(16.dp))
                l.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        AvatarRed(r.persona, 36.dp)
                        Column(Modifier.weight(1f)) {
                            NombreRed(r.persona)
                            Text(r.texto, style = MaterialTheme.typography.bodyMedium, color = f.texto)
                        }
                    }
                }
            }
        }
    }
}

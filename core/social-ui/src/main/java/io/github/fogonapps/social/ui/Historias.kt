package io.github.fogonapps.social.ui

import io.github.fogonapps.identidad.IconosSociales as Iconos
import io.github.fogonapps.identidad.PistaSocial as Pista
import io.github.fogonapps.identidad.PildoraSocial as Pildora
import io.github.fogonapps.identidad.BotonIconoSocial as BotonIcono
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
    Box(Modifier.size(tamano + 10.dp).then(if (onClick != null) Modifier.clip(CircleShape).clickable(onClick = onClick) else Modifier), contentAlignment = Alignment.Center) {
        if (estado > 0) Box(Modifier.matchParentSize().border(if (estado == 1) 3.dp else 1.5.dp, if (estado == 1) f.tinta1 else f.linea, CircleShape))
        AvatarRed(p, tamano)
        if (mas) Box(Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape).background(f.tinta1).border(2.dp, f.fondo, CircleShape), contentAlignment = Alignment.Center) {
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
            ItemHistoria(g.persona, nota?.let { it.texto.ifBlank { it.datos["adjunto"]?.jsonObject?.t("titulo").orEmpty() } }, false, if (g.todasVistas) 2 else 1,
                alNota = { notaAbierta = nota }) { visor = otros to otros.indexOf(g) }
        }
        soloNota.forEach { n -> ItemHistoria(n.persona, n.texto.ifBlank { n.datos["adjunto"]?.jsonObject?.t("titulo").orEmpty() }, false, 0, alNota = { notaAbierta = n }) { notaAbierta = n } }
    }
    if (menuPropio) MenuHistoriaPropia(vm, { menuPropio = false }, ver = { visor = listOfNotNull(propio) to 0 }, nueva = { creando = true }, nota = { escribiendoNota = true })
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
private fun MenuHistoriaPropia(vm: InterfazSocial, onCerrar: () -> Unit, ver: () -> Unit, nueva: () -> Unit, nota: () -> Unit) {
    val f = FogonTema.actual
    ModalBottomSheet(onCerrar, containerColor = f.fondo, contentColor = f.texto) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            listOf(Triple(Iconos.Anime, R.string.historia_ver_tuya, ver), Triple(Iconos.Mas, R.string.historia_nueva, nueva), Triple(Iconos.Comentario, R.string.historia_tu_nota, nota)).forEach { (icono, texto, accion) ->
                Row(Modifier.fillMaxWidth().clickable { onCerrar(); accion() }.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconoRiso(icono, null, tamano = 22.dp, tinta = f.texto)
                    Text(stringResource(texto), style = MaterialTheme.typography.bodyLarge, color = f.texto)
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
            if (audio.isNotBlank() && enlace.isNotBlank()) Pildora(stringResource(R.string.historia_apple_music)) { uri.openUri(enlace) }
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
    "obra" -> when (tipoObra) { "serie" -> R.string.historia_tipo_serie; "libro" -> R.string.historia_tipo_libro; "album", "disco" -> R.string.historia_tipo_album; "anime" -> R.string.historia_tipo_anime; else -> R.string.historia_tipo_pelicula }
    "entreno" -> R.string.historia_tipo_entreno
    "ejercicio" -> R.string.historia_tipo_ejercicio
    "logro", "racha" -> R.string.historia_tipo_logro
    "plato", "receta" -> R.string.historia_tipo_plato
    "meta", "ahorro" -> R.string.historia_tipo_meta
    else -> R.string.historia_tipo_otro
})

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
    LaunchedEffect(sonar) { if (sonar) sonando = true }
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
    var ocupado by remember { mutableStateOf(false) }
    val opciones = remember { vm.adjuntosApp() }
    LaunchedEffect(buscar) {
        if (buscar.trim().length < 2) { canciones = null; return@LaunchedEffect }
        delay(450); canciones = vm.red.canciones(buscar)
    }
    fun elegir(a: JsonObject) { ocupado = true; alcance.launch { val listo = runCatching { vm.completarAdjunto(a) }.getOrDefault(a); ocupado = false; onElegir(listo); onCerrar() } }
    ModalBottomSheet(onCerrar, containerColor = f.fondo, contentColor = f.texto) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.historia_adjuntar), style = MaterialTheme.typography.titleMedium, color = f.texto)
            OutlinedTextField(buscar, { buscar = it.take(80) }, Modifier.fillMaxWidth(), singleLine = true, shape = forma(18.dp),
                placeholder = { Text(stringResource(R.string.historia_buscar_cancion)) })
            if (ocupado) LinearProgressIndicator(Modifier.fillMaxWidth(), color = f.tinta1, trackColor = f.linea)
            canciones?.let { l ->
                if (l.isEmpty()) Pista(stringResource(R.string.historia_sin_canciones))
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
    var adjunto by remember { mutableStateOf(mia?.datos?.get("adjunto") as? JsonObject) }
    var eligiendo by remember { mutableStateOf(false) }
    ModalBottomSheet(onCerrar, containerColor = f.fondo, contentColor = f.texto) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.historia_tu_nota), style = MaterialTheme.typography.titleMedium, color = f.texto)
            Pista(stringResource(R.string.historia_nota_explica))
            OutlinedTextField(texto, { texto = it.take(60) }, Modifier.fillMaxWidth(), singleLine = true, shape = forma(18.dp),
                placeholder = { Text(stringResource(R.string.historia_nota_pista)) })
            adjunto?.let { a ->
                TarjetaAdjunto(a)
                Pildora(stringResource(R.string.historia_quitar_adjunto), Iconos.Cerrar) { adjunto = null }
            } ?: Pildora(stringResource(R.string.historia_adjuntar), Iconos.Mas) { eligiendo = true }
            BotonRiso(stringResource(R.string.historia_nota_poner), { vm.red.ponerNotaGente(texto, adjunto); onCerrar() }, Modifier.fillMaxWidth(),
                habilitado = texto.isNotBlank() || adjunto != null)
            if (mia != null) BotonSecundario(stringResource(R.string.historia_nota_quitar), { vm.red.ponerNotaGente("", null); onCerrar() }, Modifier.fillMaxWidth())
        }
    }
    if (eligiendo) ElegirAdjunto(vm, { eligiendo = false }) { adjunto = it }
}

/** La nota de otra persona, con su canción sonando. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaVerNota(vm: InterfazSocial, n: NotaGente, onCerrar: () -> Unit) {
    val f = FogonTema.actual
    ModalBottomSheet(onCerrar, containerColor = f.fondo, contentColor = f.texto) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AvatarRed(n.persona, 64.dp)
            NombreRed(n.persona)
            if (n.texto.isNotBlank()) Text(n.texto, style = MaterialTheme.typography.titleMedium, color = f.texto, textAlign = TextAlign.Center)
            Text(haceCuanto(n.cuando), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            (n.datos["adjunto"] as? JsonObject)?.let { TarjetaAdjunto(it, sonar = true) }
            BotonSecundario(stringResource(R.string.historia_ver_perfil), { onCerrar(); vm.navegacion.perfil(n.persona.usuario) }, Modifier.fillMaxWidth())
        }
    }
}

/* ----- Crear una historia ----- */

/** Foto, texto, fondo y adjunto; se comparte con tu gente por 24 horas. */
@Composable
fun CrearHistoria(vm: InterfazSocial, adjuntoInicial: JsonObject? = null, onCerrar: () -> Unit) {
    val f = FogonTema.actual
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var foto by remember { mutableStateOf<android.net.Uri?>(null) }
    var texto by rememberSaveable { mutableStateOf("") }
    var fondo by rememberSaveable { mutableStateOf(0) }
    var adjunto by remember { mutableStateOf(adjuntoInicial) }
    var eligiendo by remember { mutableStateOf(false) }
    var subiendo by remember { mutableStateOf(false) }
    val noSePudo = stringResource(R.string.historia_no_se_pudo)
    val elegirFoto = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { u -> if (u != null) foto = u }
    Dialog(onCerrar, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(f.fondo).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BotonIcono(Iconos.Cerrar, stringResource(R.string.historia_cerrar)) { onCerrar() }
                Text(stringResource(R.string.historia_nueva), style = MaterialTheme.typography.titleLarge, color = f.texto, modifier = Modifier.weight(1f).padding(start = 8.dp))
            }
            val (color, sobre) = fondoHistoria(fondo)
            Box(Modifier.fillMaxWidth().aspectRatio(9f / 14f).clip(forma(22.dp)).background(color), contentAlignment = Alignment.Center) {
                foto?.let { coil3.compose.AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (texto.isNotBlank()) Text(texto, style = MaterialTheme.typography.headlineSmall, color = if (foto != null) f.texto else sobre, textAlign = TextAlign.Center,
                        modifier = if (foto != null) Modifier.clip(forma(12.dp)).background(f.fondo.copy(alpha = 0.75f)).padding(10.dp) else Modifier)
                    adjunto?.let { TarjetaAdjunto(it, solida = true) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pildora(stringResource(if (foto == null) R.string.historia_foto else R.string.historia_otra_foto), Iconos.Mas) { elegirFoto.launch("image/*") }
                if (foto != null) Pildora(stringResource(R.string.historia_sin_foto), Iconos.Cerrar) { foto = null }
            }
            if (foto == null) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                (0..3).forEach { i ->
                    val (c, _) = fondoHistoria(i)
                    Box(Modifier.size(34.dp).clip(CircleShape).background(c).border(if (fondo == i) 3.dp else 1.dp, if (fondo == i) f.acentoTexto else f.linea, CircleShape).clickable { fondo = i })
                }
            }
            OutlinedTextField(texto, { texto = it.take(200) }, Modifier.fillMaxWidth(), minLines = 2, shape = forma(18.dp), placeholder = { Text(stringResource(R.string.historia_texto)) })
            adjunto?.let { Pildora(stringResource(R.string.historia_quitar_adjunto), Iconos.Cerrar) { adjunto = null } }
                ?: Pildora(stringResource(R.string.historia_adjuntar), Iconos.Mas) { eligiendo = true }
            Pista(stringResource(R.string.historia_explica))
            if (subiendo) LinearProgressIndicator(Modifier.fillMaxWidth(), color = f.tinta1, trackColor = f.linea)
            BotonRiso(stringResource(R.string.historia_compartir), {
                subiendo = true
                alcance.launch {
                    val jpeg = foto?.let { jpegHistoria(ctx, it) }
                    if (foto != null && jpeg == null) { subiendo = false; android.widget.Toast.makeText(ctx, noSePudo, android.widget.Toast.LENGTH_SHORT).show(); return@launch }
                    val datos = buildJsonObject {
                        if (texto.isNotBlank()) put("texto", texto.trim())
                        put("fondo", fondo)
                        adjunto?.let { put("adjunto", it) }
                    }
                    vm.red.subirHistoria(datos, jpeg) { onCerrar() }
                    delay(15_000); subiendo = false
                }
            }, Modifier.fillMaxWidth(), habilitado = !subiendo && (foto != null || texto.isNotBlank() || adjunto != null))
        }
    }
    if (eligiendo) ElegirAdjunto(vm, { eligiendo = false }) { adjunto = it }
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
    val grupo = grupos.getOrNull(g) ?: run { onCerrar(); return }
    val historia = grupo.historias.getOrNull(h) ?: run { onCerrar(); return }
    val propia = grupo.persona.usuario == yo
    fun siguiente() { if (h < grupo.historias.lastIndex) h++ else if (g < grupos.lastIndex) { g++; h = 0 } else onCerrar() }
    fun anterior() { if (h > 0) h-- else if (g > 0) { g--; h = 0 } }
    val adjunto = historia.datos["adjunto"] as? JsonObject
    val duracion = if (adjunto?.t("audio").isNullOrBlank()) 6000f else 15000f
    LaunchedEffect(historia.id) { progreso = 0f; vm.red.verHistoria(historia) }
    LaunchedEffect(historia.id, pausado, vistas, reportando) {
        if (pausado || vistas != null || reportando) return@LaunchedEffect
        while (progreso < 1f) { delay(50); progreso += 50f / duracion }
        siguiente()
    }
    Dialog(onCerrar, DialogProperties(usePlatformDefaultWidth = false)) {
        val imagen = historia.datos.t("imagen")
        val (color, sobre) = fondoHistoria((historia.datos["fondo"] as? JsonPrimitive)?.intOrNull ?: 0)
        Box(Modifier.fillMaxSize().background(if (imagen.isNotBlank()) f.fondo else color)
            .pointerInput(historia.id) {
                detectTapGestures(onPress = { pausado = true; tryAwaitRelease(); pausado = false },
                    onTap = { o -> if (o.x < size.width / 3f) anterior() else siguiente() })
            }) {
            if (imagen.isNotBlank()) coil3.compose.AsyncImage(ClienteSocial.imagen(imagen), stringResource(R.string.historia_foto_de, grupo.persona.nombre.ifBlank { grupo.persona.usuario }),
                Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            Column(Modifier.align(Alignment.Center).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val texto = historia.datos.t("texto")
                if (texto.isNotBlank() && imagen.isBlank()) Text(texto, style = MaterialTheme.typography.headlineSmall, color = sobre, textAlign = TextAlign.Center)
                if (adjunto != null && imagen.isBlank()) TarjetaAdjunto(adjunto, sonar = true, solida = true)
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val texto = historia.datos.t("texto")
                if (texto.isNotBlank() && imagen.isNotBlank()) Text(texto, style = MaterialTheme.typography.titleMedium, color = f.texto, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(forma(12.dp)).background(f.fondo.copy(alpha = 0.75f)).padding(10.dp))
                if (adjunto != null && imagen.isNotBlank()) TarjetaAdjunto(adjunto, sonar = true, solida = true)
                Row(Modifier.clip(FormaPildora).background(f.fondo.copy(alpha = 0.85f)).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (propia) {
                        Pildora(stringResource(R.string.historia_vistas), Iconos.Anime) { alcance.launch { vistas = vm.red.vistasHistoria(historia) } }
                        Pildora(stringResource(R.string.historia_borrar), Iconos.Borrar) {
                            pausado = true
                            vm.confirmar(vm.tx(R.string.historia_borrar), vm.tx(R.string.historia_borrar_confirmar)) { vm.red.borrarHistoria(historia); onCerrar() }
                        }
                    } else {
                        Pildora(stringResource(R.string.historia_ver_perfil), Iconos.Persona) { onCerrar(); vm.navegacion.perfil(grupo.persona.usuario) }
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
                    Text(haceCuanto(historia.cuando), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, modifier = Modifier.weight(1f))
                    BotonIcono(Iconos.Cerrar, stringResource(R.string.historia_cerrar)) { onCerrar() }
                }
            }
        }
        vistas?.let { l ->
            ModalBottomSheet({ vistas = null }, containerColor = f.fondo, contentColor = f.texto) {
                Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                    Text(pluralStringResourceSeguro(l.size), style = MaterialTheme.typography.titleMedium, color = f.texto, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    l.forEach { v -> FilaPersona(v.persona, { vistas = null; onCerrar(); vm.navegacion.perfil(v.persona.usuario) }) }
                }
            }
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

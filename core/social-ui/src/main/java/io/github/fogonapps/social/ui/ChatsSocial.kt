package io.github.fogonapps.social.ui

import io.github.fogonapps.identidad.IconosSociales as Iconos
import io.github.fogonapps.identidad.PistaSocial as Pista
import io.github.fogonapps.identidad.PildoraSocial as Pildora
import io.github.fogonapps.identidad.BotonIconoSocial as BotonIcono
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import kotlin.math.roundToInt
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.paint
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.identidad.*
import io.github.fogonapps.social.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * Los mensajes (el chat cifrado de extremo a extremo) para las apps que no tienen pantallas propias: la lista con
 * amigos arriba y buscador, la conversación (stickers con editor y tamaños, reacciones, responder, borrar) y la
 * información del chat (colores, letra, apodo, silenciar, vaciar). La lógica y el cifrado son los de [ChatSocial],
 * los mismos de El Cajón: las conversaciones llegan a las cuatro apps.
 */

/** Adónde llevan los mensajes (lo resuelve cada app). */
data class NavegacionChat(val conversacion: (String) -> Unit, val info: (String) -> Unit)

/** El chat de la cuenta en esta app (las apps con [RedApp]); null si no hay. */
@Composable
fun rememberChat(vm: InterfazSocial): ChatSocial? {
    val ctx = LocalContext.current
    val red = vm.red as? RedApp ?: return null
    return remember(red) {
        red.chat(ctx) { codigo ->
            val texto = ctx.getString(when (codigo) {
                "sin_llaves" -> R.string.social_chat_sin_llaves; "no_se_puede" -> R.string.social_chat_no_se_puede
                "muy_grande" -> R.string.social_chat_muy_grande; "sticker_guardado" -> R.string.social_chat_sticker_guardado
                "abre_cajon" -> R.string.social_chat_abre_cajon; else -> R.string.social_red_error
            })
            android.widget.Toast.makeText(ctx, texto, android.widget.Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
private fun resumenMensaje(m: MensajeChat): String = when {
    m.borrado -> stringResource(if (m.mio) R.string.social_chat_borrado_mio else R.string.social_chat_borrado_suyo)
    m.noSeLee -> stringResource(R.string.social_chat_no_se_lee)
    m.sticker != null -> (if (m.mio) stringResource(R.string.social_chat_tu) + " " else "") + stringResource(R.string.social_chat_un_sticker)
    m.foto != null -> (if (m.mio) stringResource(R.string.social_chat_tu) + " " else "") + stringResource(R.string.social_chat_foto)
    m.audio != null -> (if (m.mio) stringResource(R.string.social_chat_tu) + " " else "") + stringResource(R.string.social_chat_nota_voz)
    m.obra != null -> "▸ " + ((m.obra?.get("titulo") as? JsonPrimitive)?.content ?: "")
    else -> (if (m.mio) stringResource(R.string.social_chat_tu) + " " else "") + m.texto
}

/** La lista de conversaciones: notas arriba, buscador, amigos (para escribirles) y los chats. */
@Composable
fun ChatsSocial(vm: InterfazSocial, chat: ChatSocial, nav: NavegacionChat) {
    val f = FogonTema.actual
    val chats by chat.conversaciones.collectAsStateWithLifecycle()
    val personas by chat.personas.collectAsStateWithLifecycle()
    val estilos by chat.estilos.collectAsStateWithLifecycle()
    val amigos by vm.red.amigos.collectAsStateWithLifecycle()
    val notas by vm.red.notasGente.collectAsStateWithLifecycle()
    val yo by vm.red.usuario.collectAsStateWithLifecycle()
    var buscar by rememberSaveable { mutableStateOf("") }
    var abierta by remember { mutableStateOf<NotaGente?>(null) }
    var escribiendo by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { chat.recibir(); vm.red.cargarAmigos(); vm.red.cargarHistorias() }
    Cabecera(stringResource(R.string.social_chat_titulo), vm.navegacion.volver)
    if (yo == null) { Pista(stringResource(R.string.social_red_invita_texto), Modifier.padding(16.dp)); return }
    // Las notas: la tuya y las de tu gente (tocar una la abre, con «Responder»).
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val mia = notas.firstOrNull { it.persona.usuario == yo }
        NotaChat(PersonaSocial(yo.orEmpty(), stringResource(R.string.historia_tu), foto = true), mia?.let { it.texto.ifBlank { (it.datos["adjunto"] as? JsonObject)?.get("titulo")?.let { t -> (t as? JsonPrimitive)?.content } } } ?: stringResource(R.string.historia_nota_pon)) { escribiendo = true }
        notas.filter { it.persona.usuario != yo }.forEach { n -> NotaChat(n.persona, n.texto.ifBlank { ((n.datos["adjunto"] as? JsonObject)?.get("titulo") as? JsonPrimitive)?.content.orEmpty() }) { abierta = n } }
    }
    if (escribiendo) HojaNota(vm, notas.firstOrNull { it.persona.usuario == yo }) { escribiendo = false }
    abierta?.let { n -> HojaVerNota(vm, n, responder = { nav.conversacion(n.persona.usuario) }) { abierta = null } }
    CumplesHoyChat(vm) { nav.conversacion(it) }
    OutlinedTextField(buscar, { buscar = it.take(40) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), singleLine = true, shape = forma(22.dp),
        placeholder = { Text(stringResource(R.string.social_chat_buscar_amigos)) })
    val q = buscar.trim().lowercase()
    fun coincide(p: PersonaSocial, u: String) = q.isEmpty() || q in u.lowercase() || q in p.nombre.lowercase() || q in estilos[u]?.apodo.orEmpty().lowercase()
    val amigosVisibles = amigos.filter { coincide(it, it.usuario) }
    if (amigosVisibles.isNotEmpty()) {
        Text(stringResource(R.string.social_amigos), style = MaterialTheme.typography.labelLarge, color = f.textoSuave, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            amigosVisibles.forEach { a ->
                Column(Modifier.width(68.dp).clip(forma(14.dp)).clickable { nav.conversacion(a.usuario) }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AvatarRed(a, 52.dp)
                    Text(estilos[a.usuario]?.apodo?.ifBlank { null } ?: a.nombre.ifBlank { a.usuario }, style = MaterialTheme.typography.labelSmall, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    if (chats.isEmpty()) { Pista(stringResource(R.string.social_chat_vacio), Modifier.padding(16.dp)); return }
    chats.entries.filter { (u, _) -> coincide(personas[u] ?: PersonaSocial(u), u) }.sortedByDescending { it.value.lastOrNull()?.cuando ?: 0 }.forEach { (u, l) ->
        val ultimo = l.lastOrNull()
        val nuevos = l.count { !it.mio && !it.leido }
        val p = personas[u] ?: PersonaSocial(u, foto = true)
        FilaPersona(estilos[u]?.apodo?.takeIf { it.isNotBlank() }?.let { p.copy(nombre = it) } ?: p, { nav.conversacion(u) }) {
            Column(horizontalAlignment = Alignment.End) {
                Text(ultimo?.let { resumenMensaje(it) }.orEmpty(), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
                if (estilos[u]?.silenciado == true) Text(stringResource(R.string.social_chat_silenciado), style = MaterialTheme.typography.labelSmall, color = f.textoSuave)
                if (nuevos > 0) Box(Modifier.clip(forma(10.dp)).background(f.tinta1).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text("$nuevos", style = MaterialTheme.typography.labelSmall, color = f.sobreTinta1)
                }
            }
        }
    }
}

/** Los amigos que cumplen años hoy, con un toque para escribirles. */
@Composable
fun CumplesHoyChat(vm: InterfazSocial, escribir: (String) -> Unit) {
    val f = FogonTema.actual
    val cumples by vm.red.cumplesHoy.collectAsStateWithLifecycle()
    val amigos by vm.red.amigos.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.red.cargarCumples() }
    amigos.filter { it.usuario in cumples }.forEach { p ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(forma(16.dp)).background(f.superficie).border(f.borde, f.linea, forma(16.dp))
            .clickable { escribir(p.usuario) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AvatarRed(p, 36.dp)
            Text(stringResource(R.string.cumple_hoy_aviso, p.nombre.ifBlank { p.usuario }), style = MaterialTheme.typography.bodyMedium, color = f.texto, modifier = Modifier.weight(1f))
            Pildora(stringResource(R.string.cumple_escribele), acento = true) { escribir(p.usuario) }
        }
    }
}

@Composable
private fun NotaChat(p: PersonaSocial, texto: String, onClick: () -> Unit) {
    val f = FogonTema.actual
    Column(Modifier.width(80.dp).clip(forma(16.dp)).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.clip(forma(14.dp)).background(f.superficie).border(f.borde, f.linea, forma(14.dp)).padding(horizontal = 8.dp, vertical = 5.dp)) {
            Text(texto, style = MaterialTheme.typography.labelSmall, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        AvatarRed(p, 52.dp)
        Text(p.nombre.ifBlank { p.usuario }, style = MaterialTheme.typography.labelSmall, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Los colores de las burbujas propias (0 a 5, tintas del tema) y su letra. */
@Composable
private fun coloresBurbuja(estilo: EstiloChat): Pair<Color, Color> {
    val f = FogonTema.actual
    fun legible(c: Color) = if (c.luminance() > 0.5f) (if (f.texto.luminance() < 0.5f) f.texto else f.fondo) else (if (f.texto.luminance() >= 0.5f) f.texto else f.fondo)
    return when (estilo.color) {
        1 -> f.tinta2 to legible(f.tinta2)
        2 -> f.superficie to f.texto
        3 -> f.tinta1.copy(alpha = 0.45f).compositeOver(f.fondo).let { it to legible(it) }
        4 -> f.tinta2.copy(alpha = 0.45f).compositeOver(f.fondo).let { it to legible(it) }
        5 -> f.pildora to f.sobrePildora
        else -> f.tinta1 to f.sobreTinta1
    }
}
private fun Color.luminance() = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/**
 * Lo que una app suma a la conversación común: El Cajón manda obras (con su tarjeta, que abre la ficha o la busca) y
 * comparte imágenes con su propia hoja.
 */
class ExtrasChat(
    /** La tarjeta de una obra recibida o mandada. */
    val tarjetaObra: (@Composable (obra: JsonObject, color: Color, onMenu: () -> Unit) -> Unit)? = null,
    /** El panel para elegir una obra y mandarla (con su botón a la izquierda de la barra de escribir). */
    val elegirObra: (@Composable (enviar: (JsonObject) -> Unit) -> Unit)? = null,
    val iconoObra: Dibujo? = null,
    val textoObra: Int = 0,
    /** Compartir una imagen (un sticker o una foto) fuera de la app. */
    val compartirImagen: ((android.graphics.Bitmap) -> Unit)? = null,
    /** Un panel de stickers propio de la app (en lugar del común). */
    val panelStickers: (@Composable (mandar: (String, Int) -> Unit) -> Unit)? = null,
)

private fun diaDe(cuando: Long): java.time.LocalDate = java.time.Instant.ofEpochMilli(cuando).atZone(java.time.ZoneId.systemDefault()).toLocalDate()

/** "Hoy", "Ayer" o la fecha, entre los mensajes de días distintos. */
@Composable
private fun SeparadorDia(dia: java.time.LocalDate) {
    val f = FogonTema.actual
    val hoy = java.time.LocalDate.now()
    val texto = when (dia) {
        hoy -> stringResource(R.string.social_hoy)
        hoy.minusDays(1) -> stringResource(R.string.social_ayer)
        else -> dia.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(texto, style = MaterialTheme.typography.labelSmall, color = f.textoSuave,
            modifier = Modifier.clip(forma(10.dp)).background(f.superficie).padding(horizontal = 10.dp, vertical = 3.dp))
    }
}

/** El fondo de puntos de una conversación. */
private fun Modifier.fondoPuntos(color: Color) = drawBehind {
    val paso = 22.dp.toPx()
    var y = paso / 2
    while (y < size.height) {
        var x = paso / 2
        while (x < size.width) { drawCircle(color, 1.4.dp.toPx(), androidx.compose.ui.geometry.Offset(x, y)); x += paso }
        y += paso
    }
}

/** Una imagen en base64 (sticker o foto), decodificada una vez. */
@Composable
private fun imagenB64(b64: String) = remember(b64) {
    runCatching { java.util.Base64.getDecoder().decode(b64).let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) } }.getOrNull()
}

/** Graba una nota de voz corta (AAC, mono, liviana para que quepa en un sobre). */
private class Grabadora(private val ctx: android.content.Context) {
    private var rec: android.media.MediaRecorder? = null
    private var archivo: java.io.File? = null
    var inicio = 0L; private set
    fun empezar(): Boolean = runCatching {
        val f = java.io.File.createTempFile("voz-", ".m4a", ctx.cacheDir)
        @Suppress("DEPRECATION")
        val r = if (android.os.Build.VERSION.SDK_INT >= 31) android.media.MediaRecorder(ctx) else android.media.MediaRecorder()
        r.setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
        r.setAudioChannels(1); r.setAudioSamplingRate(16_000); r.setAudioEncodingBitRate(12_000)
        r.setMaxDuration(ChatSocial.VOZ_MAX * 1000)
        r.setOutputFile(f.absolutePath)
        r.prepare(); r.start()
        rec = r; archivo = f; inicio = android.os.SystemClock.elapsedRealtime()
        true
    }.getOrElse { cancelar(); false }
    /** La nota en base64 y sus segundos (null si quedó vacía o no cabe). */
    fun terminar(): Pair<String, Int>? {
        val segundos = ((android.os.SystemClock.elapsedRealtime() - inicio) / 1000).toInt().coerceIn(1, ChatSocial.VOZ_MAX)
        runCatching { rec?.stop() }; rec?.release(); rec = null
        val f = archivo ?: return null; archivo = null
        val b64 = runCatching { java.util.Base64.getEncoder().encodeToString(f.readBytes()) }.getOrNull()
        f.delete()
        return b64?.takeIf { it.isNotEmpty() && it.length <= ChatSocial.ADJUNTO_MAX }?.let { it to segundos }
    }
    fun cancelar() { runCatching { rec?.stop() }; rec?.release(); rec = null; archivo?.delete(); archivo = null }
}

/** Una nota de voz: tocar para escucharla (se escribe a un archivo temporal privado mientras suena). */
@Composable
private fun NotaVoz(b64: String, segundos: Int, color: Color) {
    val ctx = LocalContext.current
    var sonando by remember(b64) { mutableStateOf(false) }
    var avance by remember(b64) { mutableFloatStateOf(0f) }
    val reproductor = remember(b64) { mutableStateOf<android.media.MediaPlayer?>(null) }
    val archivo = remember(b64) { java.io.File(ctx.cacheDir, "voz-${b64.hashCode()}.m4a") }
    fun parar() { reproductor.value?.release(); reproductor.value = null; sonando = false; avance = 0f }
    DisposableEffect(b64) { onDispose { reproductor.value?.release(); archivo.delete() } }
    LaunchedEffect(sonando) {
        while (sonando) {
            val r = reproductor.value ?: break
            avance = runCatching { r.currentPosition.toFloat() / r.duration.coerceAtLeast(1) }.getOrDefault(0f)
            kotlinx.coroutines.delay(100)
        }
    }
    val etiqueta = stringResource(if (sonando) R.string.social_video_pausa else R.string.social_chat_escuchar)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.widthIn(min = 180.dp)) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)).clickable {
            if (sonando) parar() else runCatching {
                if (!archivo.exists()) archivo.writeBytes(java.util.Base64.getDecoder().decode(b64))
                reproductor.value = android.media.MediaPlayer().apply {
                    setDataSource(archivo.absolutePath); prepare(); setOnCompletionListener { parar() }; start()
                }
                sonando = true
            }
        }.semantics { contentDescription = etiqueta }, contentAlignment = Alignment.Center) {
            IconoRiso(if (sonando) IconosMultimedia.Pausa else IconosMultimedia.Reproducir, null, tamano = 20.dp, tinta = color)
        }
        Box(Modifier.weight(1f).height(4.dp).clip(FormaPildora).background(color.copy(alpha = 0.3f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(avance.coerceIn(0f, 1f)).background(color))
        }
        Text("0:%02d".format(segundos), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** Una conversación: los mensajes (del más nuevo abajo), fotos, notas de voz, stickers, reacciones, responder y borrar. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConversacionSocial(vm: InterfazSocial, chat: ChatSocial, nav: NavegacionChat, usuario: String, extras: ExtrasChat = ExtrasChat()) {
    val f = FogonTema.actual
    val ctx = LocalContext.current
    val chats by chat.conversaciones.collectAsStateWithLifecycle()
    val estilos by chat.estilos.collectAsStateWithLifecycle()
    val personas by chat.personas.collectAsStateWithLifecycle()
    val enviando by chat.enviando.collectAsStateWithLifecycle()
    val conVisto by chat.confirmaLectura.collectAsStateWithLifecycle()
    val yo by vm.red.usuario.collectAsStateWithLifecycle()
    val persona = personas[usuario] ?: PersonaSocial(usuario)
    val estilo = estilos[usuario] ?: EstiloChat()
    val nombre = estilo.apodo.ifBlank { persona.nombre.ifBlank { "@$usuario" } }
    var texto by rememberSaveable { mutableStateOf("") }
    var responder by remember { mutableStateOf<MensajeChat?>(null) }
    var stickers by remember { mutableStateOf(false) }
    var elegirObra by remember { mutableStateOf(false) }
    var menuDe by remember { mutableStateOf<MensajeChat?>(null) }
    var fotoAbierta by remember { mutableStateOf<String?>(null) }
    val lista = chats[usuario].orEmpty()
    val alReves = lista.asReversed()
    val estado = androidx.compose.foundation.lazy.rememberLazyListState()
    val alcance = rememberCoroutineScope()
    LaunchedEffect(usuario) { chat.conocer(usuario) }
    // Mientras está abierta, revisa cada 8 segundos si llegó algo.
    LaunchedEffect(usuario) { while (true) { chat.recibir(); chat.marcarLeida(usuario); kotlinx.coroutines.delay(8000) } }
    // Al llegar o mandar algo, baja a lo último.
    LaunchedEffect(lista.lastOrNull()?.id) { if (lista.isNotEmpty()) estado.animateScrollToItem(0) }
    // El último mensaje propio que la otra persona vio (lleva el «Visto» debajo).
    val ultimoVisto = if (conVisto) lista.lastOrNull { it.mio }?.takeIf { it.visto }?.id else null

    // Fotos y notas de voz.
    val noSePudo = stringResource(R.string.social_chat_foto_no_se_pudo)
    val manejar_elegirFoto10: (android.net.Uri?) -> Unit = { u ->
        if (u != null) alcance.launch {
            val b64 = chat.fotoParaMensaje(u)
            if (b64 == null) android.widget.Toast.makeText(ctx, noSePudo, android.widget.Toast.LENGTH_SHORT).show()
            else { chat.enviar(usuario, "", responde = responder, foto = b64); responder = null }
        }
    }
    // Antes de usarla(s), el paso para girarlas.
    val girar_elegirFoto10 = io.github.fogonapps.social.ui.conGiro { l -> manejar_elegirFoto10(l.firstOrNull()) }
    val elegirFoto = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { r -> if (r != null) girar_elegirFoto10(listOf(r)) else manejar_elegirFoto10(null) }
    val grabadora = remember { Grabadora(ctx.applicationContext) }
    var grabando by remember { mutableStateOf(false) }
    var segundosGrabados by remember { mutableStateOf(0) }
    DisposableEffect(Unit) { onDispose { grabadora.cancelar() } }
    fun mandarVoz() {
        grabando = false
        grabadora.terminar()?.let { (b64, s) -> chat.enviar(usuario, "", responde = responder, audio = b64, segundos = s); responder = null }
    }
    LaunchedEffect(grabando) {
        while (grabando) {
            segundosGrabados = ((android.os.SystemClock.elapsedRealtime() - grabadora.inicio) / 1000).toInt()
            if (segundosGrabados >= ChatSocial.VOZ_MAX) { mandarVoz(); break }
            kotlinx.coroutines.delay(250)
        }
    }
    val sinMicrofono = stringResource(R.string.social_chat_sin_microfono)
    val pedirMicrofono = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { si ->
        if (si) grabando = grabadora.empezar() else android.widget.Toast.makeText(ctx, sinMicrofono, android.widget.Toast.LENGTH_LONG).show()
    }
    fun grabar() {
        stickers = false; elegirObra = false
        if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) grabando = grabadora.empezar()
        else pedirMicrofono.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    // El fondo de la conversación: el póster de una obra (tenue) o puntos, si se eligió.
    val poster = coil3.compose.rememberAsyncImagePainter(estilo.poster.takeIf { it.startsWith("https://") })
    Column(Modifier.fillMaxSize().then(if (estilo.poster.startsWith("https://")) Modifier.paint(poster, contentScale = ContentScale.Crop, alpha = 0.18f) else Modifier)
        .then(if (estilo.puntos) Modifier.fondoPuntos(f.linea) else Modifier)) {
        // Arriba: volver, la foto y el nombre (llevan al perfil) y la información del chat.
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            BotonIcono(Iconos.Atras, stringResource(R.string.social_volver)) { vm.navegacion.volver() }
            Row(Modifier.weight(1f).clip(forma(16.dp)).clickable { vm.navegacion.perfil(usuario) }.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AvatarRed(persona.copy(foto = persona.foto || personas[usuario] == null), 38.dp)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(nombre, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleSmall, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        io.github.fogonapps.perfil.InsigniaCuenta(persona.insignia, tamano = 15.dp)
                    }
                    if (persona.nombre.isNotBlank()) Text("@$usuario", style = MaterialTheme.typography.labelSmall, color = f.textoSuave, maxLines = 1)
                }
            }
            BotonIcono(Iconos.Opciones, stringResource(R.string.social_chat_info)) { nav.info(usuario) }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = estado, reverseLayout = true, contentPadding = PaddingValues(vertical = 8.dp)) {
            items(alReves.size, key = { alReves[it].id }) { i ->
                val m = alReves[i]
                val dia = diaDe(m.cuando)
                val anterior = alReves.getOrNull(i + 1)
                Column {
                    if (anterior == null || diaDe(anterior.cuando) != dia) SeparadorDia(dia)
                    // La foto de la otra persona va junto a su último mensaje seguido.
                    val siguiente = alReves.getOrNull(i - 1)
                    BurbujaChat(m, yo, nombre, estilo, persona.takeIf { !m.mio && (siguiente == null || siguiente.mio) }, extras,
                        onResponder = { responder = m }, onMenu = { menuDe = m }, onFoto = { fotoAbierta = it },
                        onIrA = { clave -> alReves.indexOfFirst { it.clave == clave }.takeIf { it >= 0 }?.let { alcance.launch { estado.animateScrollToItem(it) } } })
                    if (m.id == ultimoVisto) Text(stringResource(R.string.social_chat_visto), style = MaterialTheme.typography.labelSmall, color = f.textoSuave,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }
            if (lista.isEmpty()) item(key = "vacio") { Pista(stringResource(R.string.social_chat_cifrado), Modifier.padding(16.dp)) }
        }

        if (enviando) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = f.tinta1, trackColor = f.linea)
        // Respondiendo a un mensaje: la cita encima del campo.
        responder?.let { r ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(forma(14.dp)).background(f.superficie).border(f.borde, f.linea, forma(14.dp)).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(3.dp).heightIn(min = 36.dp).background(f.tinta1))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(stringResource(R.string.social_chat_respondiendo, if (r.mio) stringResource(R.string.social_chat_ti) else nombre), style = MaterialTheme.typography.labelMedium, color = f.acentoTexto)
                    Text(resumenMensaje(r), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BotonIcono(Iconos.Cerrar, stringResource(R.string.social_cancelar)) { responder = null }
            }
        }
        if (elegirObra) extras.elegirObra?.invoke { o -> chat.enviar(usuario, "", o, responder); responder = null; elegirObra = false }
        if (stickers) {
            val mandar: (String, Int) -> Unit = { s, z -> chat.enviar(usuario, "", responde = responder, sticker = s, tamano = z); responder = null }
            extras.panelStickers?.invoke(mandar) ?: PanelStickersChat(chat, mandar)
        }
        // La barra de escribir: una sola píldora. Grabando, muestra el tiempo con cancelar y enviar.
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp).clip(FormaPildora).background(f.superficie).border(f.borde, f.linea, FormaPildora)
            .padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (grabando) {
                BotonIcono(Iconos.Cerrar, stringResource(R.string.social_cancelar)) { grabadora.cancelar(); grabando = false }
                Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                Text(stringResource(R.string.social_chat_grabando, segundosGrabados, ChatSocial.VOZ_MAX), style = MaterialTheme.typography.bodyLarge, color = f.texto,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                BotonIcono(Iconos.Enviar, stringResource(R.string.social_chat_mensaje), lleno = true, activo = !enviando) { mandarVoz() }
                return@Row
            }
            if (extras.elegirObra != null && extras.iconoObra != null) Box(Modifier.size(40.dp).clip(CircleShape).background(f.tinta1).clickable { elegirObra = !elegirObra; stickers = false }, contentAlignment = Alignment.Center) {
                IconoRiso(extras.iconoObra, stringResource(extras.textoObra), tamano = 20.dp, tinta = f.sobreTinta1)
            }
            androidx.compose.foundation.text.BasicTextField(texto, { texto = it.take(4000) }, Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp), maxLines = 5,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = f.texto), cursorBrush = androidx.compose.ui.graphics.SolidColor(f.acentoTexto),
                decorationBox = { campo -> Box { if (texto.isEmpty()) Text(stringResource(R.string.social_chat_escribe), style = MaterialTheme.typography.bodyLarge, color = f.textoSuave); campo() } })
            if (texto.isBlank()) {
                BotonIcono(IconosFogon.Imagen, stringResource(R.string.social_chat_mandar_foto)) { stickers = false; elegirObra = false; elegirFoto.launch("image/*") }
                BotonIcono(IconosMultimedia.Microfono, stringResource(R.string.social_chat_grabar)) { grabar() }
                BotonIcono(Iconos.Estrella, stringResource(R.string.social_chat_stickers), tinta = if (stickers) f.acentoTexto else null) { stickers = !stickers; elegirObra = false }
            } else BotonIcono(Iconos.Enviar, stringResource(R.string.social_chat_mensaje), lleno = true, activo = !enviando) {
                chat.enviar(usuario, texto, responde = responder); texto = ""; responder = null
            }
        }
    }

    fotoAbierta?.let { b64 ->
        val imagen = imagenB64(b64)
        androidx.compose.ui.window.Dialog({ fotoAbierta = null }, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(f.fondo).systemBarsPadding().clickable { fotoAbierta = null }, contentAlignment = Alignment.Center) {
                if (imagen != null) Image(imagen.asImageBitmap(), stringResource(R.string.social_chat_foto), Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
                Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) { BotonIcono(Iconos.Cerrar, stringResource(R.string.social_cancelar)) { fotoAbierta = null } }
            }
        }
    }

    // El menú de un mensaje.
    menuDe?.let { m ->
        val portapapeles = androidx.compose.ui.platform.LocalClipboardManager.current
        val copiado = stringResource(R.string.social_chat_copiado)
        var reportando by remember { mutableStateOf(false) }
        ModalBottomSheet({ menuDe = null }, containerColor = f.fondo, contentColor = f.texto) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                if (reportando) {
                    Pista(stringResource(R.string.social_chat_reportar_explica, usuario), Modifier.padding(horizontal = 16.dp))
                    MotivosReporte { codigo, t -> vm.red.reportar("mensaje", "$usuario:mensajes", "$codigo: $t\n${m.texto}"); menuDe = null }
                    return@Column
                }
                // Reaccionar con un emoji (tocar la misma la quita).
                if (!m.borrado && !m.noSeLee) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("❤️", "😂", "😮", "😢", "👍", "🔥").forEach { emo ->
                        Box(Modifier.size(44.dp).clip(CircleShape).background(if (m.reaccionMia == emo) f.superficie else Color.Transparent)
                            .clickable { chat.reaccion(usuario, m, if (m.reaccionMia == emo) "" else emo); menuDe = null }, contentAlignment = Alignment.Center) { Text(emo, fontSize = 24.sp) }
                    }
                }
                @Composable fun Fila(icono: Dibujo, t: String, peligro: Boolean = false, accion: () -> Unit) {
                    val color = if (peligro) MaterialTheme.colorScheme.error else f.texto
                    Row(Modifier.fillMaxWidth().clickable { accion() }.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        IconoRiso(icono, null, tamano = 20.dp, tinta = color)
                        Text(t, style = MaterialTheme.typography.bodyLarge, color = color)
                    }
                }
                if (!m.borrado && !m.noSeLee) Fila(Iconos.Comentario, stringResource(R.string.social_chat_responder)) { responder = m; menuDe = null }
                if (m.texto.isNotBlank()) {
                    Fila(IconosFogon.Lista, stringResource(R.string.social_chat_copiar)) {
                        portapapeles.setText(androidx.compose.ui.text.AnnotatedString(m.texto)); android.widget.Toast.makeText(ctx, copiado, android.widget.Toast.LENGTH_SHORT).show(); menuDe = null
                    }
                    Fila(Iconos.Compartir, stringResource(R.string.social_compartir)) {
                        ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, m.texto), null)); menuDe = null
                    }
                }
                m.obra?.let { o -> Fila(Iconos.Compartir, stringResource(R.string.social_compartir)) {
                    ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND)
                        .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, (o["titulo"] as? JsonPrimitive)?.content.orEmpty()), null)); menuDe = null
                } }
                m.sticker?.let { s -> Fila(Iconos.Guardar, stringResource(R.string.social_chat_sticker_guardar)) { chat.guardarSticker(s); menuDe = null } }
                val compartir = extras.compartirImagen
                (m.sticker ?: m.foto)?.let { b64 -> if (compartir != null) Fila(Iconos.Compartir, stringResource(R.string.social_compartir)) {
                    runCatching { java.util.Base64.getDecoder().decode(b64).let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) } }.getOrNull()?.let(compartir)
                    menuDe = null
                } }
                Fila(Iconos.Borrar, stringResource(R.string.social_chat_borrar_para_mi)) { chat.borrarParaMi(usuario, m); menuDe = null }
                if (m.mio && !m.borrado) Fila(Iconos.Borrar, stringResource(R.string.social_chat_borrar_para_todos), peligro = true) { chat.borrarParaTodos(usuario, m); menuDe = null }
                if (!m.mio && !m.borrado) Fila(Iconos.Bandera, stringResource(R.string.social_red_reportar), peligro = true) { reportando = true }
            }
        }
    }
}

/** Un mensaje: deslizarlo a la derecha responde; la cita lleva al mensaje citado; la hora abajo. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BurbujaChat(
    m: MensajeChat, yo: String?, nombre: String, estilo: EstiloChat, foto: PersonaSocial?, extras: ExtrasChat,
    onResponder: () -> Unit, onMenu: () -> Unit, onFoto: (String) -> Unit, onIrA: (String) -> Unit,
) {
    val f = FogonTema.actual
    val (fondoMio, letraMia) = coloresBurbuja(estilo)
    val color = if (m.mio) letraMia else f.texto
    val tam = when (estilo.tamanoLetra) { 0 -> 13.sp; 2 -> 18.sp; else -> MaterialTheme.typography.bodyMedium.fontSize }
    var corrido by remember { mutableFloatStateOf(0f) }
    val umbral = with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.toPx() }
    val hora = remember(m.cuando) { java.time.Instant.ofEpochMilli(m.cuando).atZone(java.time.ZoneId.systemDefault()).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) }
    val soloImagen = (m.sticker != null || m.foto != null) && !m.borrado && m.texto.isBlank()
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)
        .pointerInput(m.id) {
            if (m.borrado || m.noSeLee) return@pointerInput
            detectHorizontalDragGestures(
                onDragEnd = { if (corrido > umbral) onResponder(); corrido = 0f },
                onDragCancel = { corrido = 0f },
            ) { _, d -> corrido = (corrido + d).coerceIn(0f, umbral * 1.5f) }
        }
        .offset { androidx.compose.ui.unit.IntOffset(corrido.roundToInt(), 0) },
        horizontalArrangement = if (m.mio) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
        if (!m.mio) {
            if (foto != null) AvatarRed(foto, 28.dp) else Box(Modifier.size(28.dp))
            Box(Modifier.width(8.dp))
        }
        Column(Modifier.widthIn(max = 280.dp).clip(forma(18.dp))
            .then(if (soloImagen) Modifier else Modifier.background(if (m.mio) fondoMio else f.superficie).border(f.borde, if (m.mio) fondoMio else f.linea, forma(18.dp)))
            .combinedClickable(onClick = { m.foto?.let(onFoto) }, onLongClick = onMenu)
            .padding(horizontal = if (soloImagen) 0.dp else 12.dp, vertical = if (soloImagen) 0.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (m.borrado) Text(stringResource(if (m.mio) R.string.social_chat_borrado_mio else R.string.social_chat_borrado_suyo),
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = 0.75f))
            if (m.noSeLee) Text(stringResource(R.string.social_chat_no_se_lee), style = MaterialTheme.typography.bodySmall, color = color)
            m.responde?.let { r ->
                Text(stringResource(if (m.mio) R.string.social_chat_respondiste else R.string.social_chat_respondio), style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.7f))
                Row(Modifier.clip(forma(10.dp)).background(f.fondo.copy(alpha = 0.35f)).combinedClickable(onClick = { onIrA(r.k) }, onLongClick = onMenu)) {
                    Box(Modifier.width(3.dp).heightIn(min = 34.dp).background(if (m.mio) letraMia else f.tinta1))
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(if (r.de == yo) stringResource(R.string.social_chat_ti) else nombre, style = MaterialTheme.typography.labelMedium, color = color)
                        Text(if (r.sticker) stringResource(R.string.social_chat_un_sticker) else r.texto, style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            m.sticker?.let { s -> imagenB64(s)?.let { Image(it.asImageBitmap(), stringResource(R.string.social_chat_un_sticker), Modifier.size(when (m.tamano) { 0 -> 96.dp; 2 -> 220.dp; else -> 150.dp })) } }
            m.foto?.let { s -> imagenB64(s)?.let { Image(it.asImageBitmap(), stringResource(R.string.social_chat_foto), Modifier.width(240.dp).heightIn(max = 320.dp).clip(forma(14.dp)), contentScale = ContentScale.Crop) } }
            m.audio?.let { NotaVoz(it, m.segundos, color) }
            m.obra?.let { o ->
                extras.tarjetaObra?.invoke(o, color, onMenu)
                    ?: Text("▸ " + ((o["titulo"] as? JsonPrimitive)?.content.orEmpty()), style = MaterialTheme.typography.titleSmall, color = color)
            }
            if (m.texto.isNotBlank() && !m.borrado) Text(m.texto, style = MaterialTheme.typography.bodyMedium, fontSize = tam, color = color)
            Text(hora, style = MaterialTheme.typography.labelSmall, color = (if (soloImagen) f.textoSuave else color).copy(alpha = 0.7f), modifier = Modifier.align(Alignment.End).padding(end = if (soloImagen) 6.dp else 0.dp, bottom = if (soloImagen) 4.dp else 0.dp))
        }
        val reacciones = listOf(m.reaccionSuya, m.reaccionMia).filter { it.isNotBlank() }
        if (reacciones.isNotEmpty()) Box(Modifier.offset(y = (-6).dp).clip(FormaPildora).background(f.fondo).border(f.borde, f.linea, FormaPildora).padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text(reacciones.joinToString(" "), fontSize = 14.sp)
        }
    }
}

/** Un sticker (WebP en base64), decodificado una vez. */
@Composable
private fun ImagenStickerChat(b64: String, modifier: Modifier) {
    val imagen = remember(b64) { runCatching { java.util.Base64.getDecoder().decode(b64).let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }.getOrNull() }
    if (imagen != null) Image(imagen, null, modifier)
}

/** Los stickers: tamaño, los tuyos, los de Fogón y crear uno con una foto (con el editor de recorte). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PanelStickersChat(chat: ChatSocial, onMandar: (String, Int) -> Unit) {
    val f = FogonTema.actual
    val guardados by chat.stickers.collectAsStateWithLifecycle()
    val alcance = rememberCoroutineScope()
    val ctx = LocalContext.current
    var tamano by rememberSaveable { mutableStateOf(1) }
    var editando by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val noSePudo = stringResource(R.string.social_chat_sticker_no_se_pudo)
    val elegir = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { u ->
        if (u != null) alcance.launch { chat.fotoParaSticker(u)?.let { editando = it } ?: android.widget.Toast.makeText(ctx, noSePudo, android.widget.Toast.LENGTH_SHORT).show() }
    }
    editando?.let { foto ->
        EditorStickerChat(foto, { editando = null }) { recorte, redondo ->
            editando = null
            alcance.launch { chat.stickerRecortado(foto, recorte, redondo)?.let { chat.guardarSticker(it); onMandar(it, tamano) } }
        }
    }
    Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.social_chat_sticker_tamano), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
            listOf(R.string.social_chat_sticker_chico, R.string.social_chat_sticker_normal, R.string.social_chat_sticker_grande).forEachIndexed { i, t -> Pildora(stringResource(t), acento = tamano == i) { tamano = i } }
        }
        Pildora(stringResource(R.string.social_chat_sticker_crear), Iconos.Mas) { elegir.launch("image/*") }
        if (guardados.isNotEmpty()) Text(stringResource(R.string.social_chat_stickers_tuyos), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            guardados.forEach { s -> Box(Modifier.size(72.dp).clip(forma(14.dp)).background(f.superficie).clickable { onMandar(s.imagen, tamano) }.padding(4.dp)) { ImagenStickerChat(s.imagen, Modifier.fillMaxSize()) } }
        }
        Text(stringResource(R.string.social_chat_stickers_fogon), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(io.github.fogonapps.identidad.R.drawable.personaje_cajon_feliz, io.github.fogonapps.identidad.R.drawable.personaje_forzudo_feliz,
                io.github.fogonapps.identidad.R.drawable.personaje_ahorrador_feliz, io.github.fogonapps.identidad.R.drawable.personaje_cucha_feliz,
                io.github.fogonapps.identidad.R.drawable.chispa).forEach { r ->
                Box(Modifier.size(72.dp).clip(forma(14.dp)).background(f.superficie).clickable { alcance.launch { chat.stickerDe(r)?.let { onMandar(it, tamano) } } }.padding(8.dp)) {
                    Image(androidx.compose.ui.res.painterResource(r), null, Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** El editor de un sticker nuevo: la foto se mueve y se acerca con los dedos dentro del cuadro; cuadrado o redondo. */
@Composable
private fun EditorStickerChat(foto: android.graphics.Bitmap, onCancelar: () -> Unit, onListo: (android.graphics.Rect, Boolean) -> Unit) {
    val f = FogonTema.actual
    var zoom by remember { mutableStateOf(1f) }
    var desplazamiento by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var redondo by remember { mutableStateOf(false) }
    var lado by remember { mutableStateOf(1) }
    val imagen = remember(foto) { foto.asImageBitmap() }
    androidx.compose.ui.window.Dialog(onCancelar) {
        Column(Modifier.fillMaxWidth().clip(forma(24.dp)).background(f.fondo).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.social_chat_sticker_editar), style = MaterialTheme.typography.titleMedium, color = f.texto)
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(if (redondo) CircleShape else forma(18.dp)).background(f.superficie)
                .onSizeChanged { lado = it.width }
                .pointerInput(foto) {
                    detectTransformGestures { _, mover, escala, _ ->
                        zoom = (zoom * escala).coerceIn(1f, 6f)
                        val base = lado / minOf(foto.width, foto.height).toFloat()
                        val maxX = (foto.width * base * zoom - lado) / 2f; val maxY = (foto.height * base * zoom - lado) / 2f
                        desplazamiento = androidx.compose.ui.geometry.Offset((desplazamiento.x + mover.x).coerceIn(-maxX, maxX), (desplazamiento.y + mover.y).coerceIn(-maxY, maxY))
                    }
                }) {
                Image(imagen, null, Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = desplazamiento.x; translationY = desplazamiento.y }, contentScale = ContentScale.Crop)
            }
            Pista(stringResource(R.string.social_chat_sticker_editar_pista))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pildora(stringResource(R.string.social_chat_sticker_cuadrado), acento = !redondo) { redondo = false }
                Pildora(stringResource(R.string.social_chat_sticker_redondo), acento = redondo) { redondo = true }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonSecundario(stringResource(R.string.social_cancelar), onCancelar, Modifier.weight(1f))
                BotonRiso(stringResource(R.string.social_chat_sticker_listo), {
                    val s = lado / minOf(foto.width, foto.height).toFloat() * zoom
                    val izquierda = ((-lado / 2f - desplazamiento.x) / s + foto.width / 2f).toInt()
                    val arriba = ((-lado / 2f - desplazamiento.y) / s + foto.height / 2f).toInt()
                    val tam = (lado / s).toInt()
                    onListo(android.graphics.Rect(izquierda, arriba, izquierda + tam, arriba + tam), redondo)
                }, Modifier.weight(1f))
            }
        }
    }
}

/** La información del chat: personalizar (colores, letra, apodo), silenciar, vaciar y borrar. */
@Composable
fun InfoChatSocial(vm: InterfazSocial, chat: ChatSocial, usuario: String) {
    val f = FogonTema.actual
    val ctx = LocalContext.current
    val estilos by chat.estilos.collectAsStateWithLifecycle()
    val estilo = estilos[usuario] ?: EstiloChat()
    val red = vm.red as? RedApp
    Cabecera(stringResource(R.string.social_chat_info), vm.navegacion.volver)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Pista(stringResource(R.string.social_chat_cifrado))
        Text(stringResource(R.string.social_chat_color_burbujas), style = MaterialTheme.typography.bodyMedium, color = f.texto)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(f.tinta1, f.tinta2, f.superficie, f.tinta1.copy(alpha = 0.45f).compositeOver(f.fondo), f.tinta2.copy(alpha = 0.45f).compositeOver(f.fondo), f.pildora).forEachIndexed { i, c ->
                Box(Modifier.size(40.dp).clip(CircleShape).background(c).border(if (estilo.color == i) 3.dp else f.borde, if (estilo.color == i) f.texto else f.linea, CircleShape)
                    .clickable { chat.ponerEstilo(usuario, estilo.copy(color = i)) })
            }
        }
        Text(stringResource(R.string.social_chat_tamano_letra), style = MaterialTheme.typography.bodyMedium, color = f.texto)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.social_chat_sticker_chico, R.string.social_chat_sticker_normal, R.string.social_chat_sticker_grande).forEachIndexed { i, t ->
                Pildora(stringResource(t), acento = estilo.tamanoLetra == i) { chat.ponerEstilo(usuario, estilo.copy(letra = i, letraGrande = i == 2)) }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.social_chat_fondo_puntos), style = MaterialTheme.typography.bodyMedium, color = f.texto, modifier = Modifier.weight(1f))
            Switch(estilo.puntos, { chat.ponerEstilo(usuario, estilo.copy(puntos = it)) }, colors = SwitchDefaults.colors(checkedTrackColor = f.tinta1, checkedThumbColor = f.sobreTinta1))
        }
        val conVisto by chat.confirmaLectura.collectAsStateWithLifecycle()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.social_chat_confirma_lectura), style = MaterialTheme.typography.bodyMedium, color = f.texto, modifier = Modifier.weight(1f))
            Switch(conVisto, { chat.ponerConfirmaLectura(it) }, colors = SwitchDefaults.colors(checkedTrackColor = f.tinta1, checkedThumbColor = f.sobreTinta1))
        }
        Text(stringResource(R.string.social_chat_confirma_lectura_explica), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        var apodo by remember(usuario) { mutableStateOf(estilo.apodo) }
        OutlinedTextField(apodo, { apodo = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true, shape = forma(18.dp), label = { Text(stringResource(R.string.social_chat_apodo)) })
        if (apodo != estilo.apodo) Pildora(stringResource(R.string.social_chat_guardar_apodo)) { chat.ponerEstilo(usuario, estilo.copy(apodo = apodo.trim())) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.social_chat_silenciar), style = MaterialTheme.typography.bodyMedium, color = f.texto, modifier = Modifier.weight(1f))
            Switch(estilo.silenciado, { si ->
                chat.ponerEstilo(usuario, estilo.copy(silenciado = si))
                val prefs = ctx.getSharedPreferences(Avisos.PREFS, android.content.Context.MODE_PRIVATE)
                val perfil = (ctx.applicationContext as? AppConAvisos)?.avisosPerfilActivo() ?: "1"
                val clave = "p$perfil.silenciados"
                val actual = prefs.getStringSet(clave, emptySet()).orEmpty()
                prefs.edit().putStringSet(clave, if (si) actual + usuario else actual - usuario).apply()
            }, colors = SwitchDefaults.colors(checkedTrackColor = f.tinta1, checkedThumbColor = f.sobreTinta1))
        }
        BotonSecundario(stringResource(R.string.social_chat_vaciar), {
            vm.confirmar(vm.tx(R.string.social_chat_vaciar), vm.tx(R.string.social_chat_vaciar_seguro)) { chat.vaciar(usuario) }
        }, Modifier.fillMaxWidth())
        BotonSecundario(stringResource(R.string.social_chat_borrar), {
            vm.confirmar(vm.tx(R.string.social_chat_borrar), vm.tx(R.string.social_chat_borrar_seguro)) { chat.borrarConversacion(usuario); vm.navegacion.volver() }
        }, Modifier.fillMaxWidth())
        if (red != null) Text(stringResource(R.string.social_chat_cifrado_corto), style = MaterialTheme.typography.labelSmall, color = f.textoSuave)
    }
}

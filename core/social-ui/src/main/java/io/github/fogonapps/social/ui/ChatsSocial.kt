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

/** Una conversación: los mensajes (del más nuevo abajo), stickers, reacciones, responder y borrar. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConversacionSocial(vm: InterfazSocial, chat: ChatSocial, nav: NavegacionChat, usuario: String) {
    val f = FogonTema.actual
    val chats by chat.conversaciones.collectAsStateWithLifecycle()
    val estilos by chat.estilos.collectAsStateWithLifecycle()
    val personas by chat.personas.collectAsStateWithLifecycle()
    val enviando by chat.enviando.collectAsStateWithLifecycle()
    val persona = personas[usuario] ?: PersonaSocial(usuario)
    val estilo = estilos[usuario] ?: EstiloChat()
    val nombre = estilo.apodo.ifBlank { persona.nombre.ifBlank { "@$usuario" } }
    var texto by rememberSaveable { mutableStateOf("") }
    var responder by remember { mutableStateOf<MensajeChat?>(null) }
    var stickers by remember { mutableStateOf(false) }
    var menuDe by remember { mutableStateOf<MensajeChat?>(null) }
    val lista = chats[usuario].orEmpty()
    LaunchedEffect(usuario) { chat.conocer(usuario) }
    LaunchedEffect(usuario) { while (true) { chat.recibir(); chat.marcarLeida(usuario); kotlinx.coroutines.delay(8000) } }
    val (fondoMio, letraMia) = coloresBurbuja(estilo)
    val tam = when (estilo.tamanoLetra) { 0 -> 13.sp; 2 -> 18.sp; else -> MaterialTheme.typography.bodyMedium.fontSize }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            BotonIcono(Iconos.Atras, stringResource(R.string.social_volver)) { vm.navegacion.volver() }
            Row(Modifier.weight(1f).clip(forma(16.dp)).clickable { vm.navegacion.perfil(usuario) }.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AvatarRed(persona.copy(foto = persona.foto || personas[usuario] == null), 38.dp)
                Column(Modifier.weight(1f)) {
                    Text(nombre, style = MaterialTheme.typography.titleSmall, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("@$usuario", style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1)
                }
            }
            BotonIcono(Iconos.Opciones, stringResource(R.string.social_chat_info)) { nav.info(usuario) }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), reverseLayout = true, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (lista.isEmpty()) item { Pista(stringResource(R.string.social_chat_cifrado), Modifier.padding(8.dp)) }
            items(lista.asReversed(), key = { it.id }) { m ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.mio) Alignment.End else Alignment.Start) {
                    val soloSticker = m.sticker != null && !m.borrado && m.texto.isBlank()
                    val color = if (m.mio) letraMia else f.texto
                    Column(Modifier.widthIn(max = 300.dp).clip(forma(18.dp))
                        .then(if (soloSticker) Modifier else Modifier.background(if (m.mio) fondoMio else f.superficie).border(f.borde, if (m.mio) fondoMio else f.linea, forma(18.dp)))
                        .combinedClickable(onLongClick = { menuDe = m }) {}
                        .padding(horizontal = if (soloSticker) 0.dp else 12.dp, vertical = if (soloSticker) 0.dp else 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        m.responde?.let { r -> Text(if (r.sticker) stringResource(R.string.social_chat_un_sticker) else r.texto, style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = 0.75f), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        when {
                            m.borrado || m.noSeLee -> Text(resumenMensaje(m), style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = 0.7f))
                            m.sticker != null -> ImagenStickerChat(m.sticker!!, Modifier.size(when (m.tamano) { 0 -> 96.dp; 2 -> 220.dp; else -> 150.dp }))
                            m.obra != null -> Text("▸ " + ((m.obra?.get("titulo") as? JsonPrimitive)?.content.orEmpty()), style = MaterialTheme.typography.titleSmall, color = color)
                        }
                        if (m.texto.isNotBlank() && !m.borrado) Text(m.texto, style = MaterialTheme.typography.bodyMedium, fontSize = tam, color = color)
                    }
                    val reacciones = listOf(m.reaccionSuya, m.reaccionMia).filter { it.isNotBlank() }
                    if (reacciones.isNotEmpty()) Text(reacciones.joinToString(" "), fontSize = 14.sp, modifier = Modifier.clip(FormaPildora).background(f.fondo).border(f.borde, f.linea, FormaPildora).padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }
        responder?.let { r ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.social_chat_respondiendo, if (r.mio) stringResource(R.string.social_chat_ti) else nombre), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, modifier = Modifier.weight(1f))
                BotonIcono(Iconos.Cerrar, stringResource(R.string.social_cancelar)) { responder = null }
            }
        }
        if (stickers) PanelStickersChat(chat) { s, z -> chat.enviar(usuario, "", responde = responder, sticker = s, tamano = z); responder = null }
        Row(Modifier.fillMaxWidth().padding(8.dp).clip(FormaPildora).background(f.superficie).border(f.borde, f.linea, FormaPildora).padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.text.BasicTextField(texto, { texto = it.take(4000) }, Modifier.weight(1f).padding(vertical = 12.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = f.texto), cursorBrush = androidx.compose.ui.graphics.SolidColor(f.texto),
                decorationBox = { campo -> if (texto.isEmpty()) Text(stringResource(R.string.social_chat_escribe), color = f.textoSuave, style = MaterialTheme.typography.bodyMedium); campo() })
            if (texto.isBlank()) BotonIcono(Iconos.Estrella, stringResource(R.string.social_chat_stickers), tinta = if (stickers) f.acentoTexto else null) { stickers = !stickers }
            else BotonIcono(Iconos.Enviar, stringResource(R.string.social_chat_mensaje), lleno = true, activo = !enviando) { chat.enviar(usuario, texto, responde = responder); texto = ""; responder = null }
        }
    }
    menuDe?.let { m ->
        val portapapeles = androidx.compose.ui.platform.LocalClipboardManager.current
        val ctx = LocalContext.current
        val copiado = stringResource(R.string.social_chat_copiado)
        var reportando by remember { mutableStateOf(false) }
        ModalBottomSheet({ menuDe = null }, containerColor = f.fondo, contentColor = f.texto) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                if (reportando) {
                    Pista(stringResource(R.string.social_chat_reportar_explica, usuario), Modifier.padding(horizontal = 16.dp))
                    MotivosReporte { codigo, t -> vm.red.reportar("mensaje", "$usuario:mensajes", "$codigo: $t\n${m.texto}"); menuDe = null }
                    return@Column
                }
                if (!m.borrado && !m.noSeLee) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("❤️", "😂", "😮", "😢", "👍", "🔥").forEach { emo ->
                        Box(Modifier.size(44.dp).clip(CircleShape).background(if (m.reaccionMia == emo) f.superficie else Color.Transparent)
                            .clickable { chat.reaccion(usuario, m, if (m.reaccionMia == emo) "" else emo); menuDe = null }, contentAlignment = Alignment.Center) { Text(emo, fontSize = 24.sp) }
                    }
                }
                @Composable fun Fila(t: String, accion: () -> Unit) = Text(t, style = MaterialTheme.typography.bodyLarge, color = f.texto,
                    modifier = Modifier.fillMaxWidth().clickable { accion(); menuDe = null }.padding(horizontal = 24.dp, vertical = 14.dp))
                if (!m.borrado && !m.noSeLee) Fila(stringResource(R.string.social_chat_responder)) { responder = m }
                if (m.texto.isNotBlank()) Fila(stringResource(R.string.social_chat_copiar)) {
                    portapapeles.setText(androidx.compose.ui.text.AnnotatedString(m.texto)); android.widget.Toast.makeText(ctx, copiado, android.widget.Toast.LENGTH_SHORT).show()
                }
                m.sticker?.let { s -> Fila(stringResource(R.string.social_chat_sticker_guardar)) { chat.guardarSticker(s) } }
                Fila(stringResource(R.string.social_chat_borrar_para_mi)) { chat.borrarParaMi(usuario, m) }
                if (m.mio && !m.borrado) Fila(stringResource(R.string.social_chat_borrar_para_todos)) { chat.borrarParaTodos(usuario, m) }
                if (!m.mio) Text(stringResource(R.string.social_red_reportar), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().clickable { reportando = true }.padding(horizontal = 24.dp, vertical = 14.dp))
            }
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

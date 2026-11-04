package io.github.fogonapps.cajon.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.EstiloChat
import io.github.fogonapps.cajon.MensajeChat
import io.github.fogonapps.cajon.Pantalla
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.Sticker
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.PersonaSocial
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.FormaPildora
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.forma
import io.github.fogonapps.identidad.legibleSobre
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt
import io.github.fogonapps.identidad.R as RId

/** Las conversaciones. Con [obra], tocar una le manda esa obra (viene de "Mandar a alguien" en una ficha). */
@Composable
fun Chats(vm: CajonViewModel, e: Estado, obra: String?) {
    val f = FogonTema.actual
    val chats by vm.chat.conversaciones.collectAsStateWithLifecycle()
    val personas by vm.chat.personas.collectAsStateWithLifecycle()
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    val b by vm.busqueda.collectAsStateWithLifecycle()
    var buscar by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.chat.recibir() }
    val o = obra?.let { id -> e.vivas.firstOrNull { it.id == id } }
    Cabecera(stringResource(if (o != null) R.string.chat_mandar_a else R.string.chat_titulo), vm::cerrar) {
        if (o == null) BotonIcono(Iconos.Lapiz, stringResource(R.string.chat_nuevo), grande = true) { vm.abrir(Pantalla.Buscar(io.github.fogonapps.cajon.Secciones.PERSONAS)) }
    }
    if (usuario == null) { Pista(stringResource(R.string.red_invita_texto), Modifier.padding(16.dp)); return }
    LaunchedEffect(Unit) { vm.red.cargarNotas() }
    if (o == null) FilaNotas(vm, e)
    o?.let { Pista(stringResource(R.string.chat_mandar_obra, it.titulo), Modifier.padding(horizontal = 16.dp)) }
    fun abrir(u: String) {
        if (o != null) { vm.chat.enviar(u, "", o); vm.reemplazar(Pantalla.Conversacion(u)) } else vm.abrir(Pantalla.Conversacion(u))
    }
    if (chats.isEmpty()) { Pista(stringResource(R.string.chat_vacio), Modifier.padding(16.dp)); return }
    chats.entries.sortedByDescending { it.value.lastOrNull()?.cuando ?: 0 }.forEach { (u, l) ->
        val ultimo = l.lastOrNull()
        val nuevos = l.count { !it.mio && !it.leido }
        FilaPersona(personas[u] ?: PersonaSocial(u, foto = true), { abrir(u) }) {
            Column(horizontalAlignment = Alignment.End) {
                Text(ultimo?.let { resumen(it) }.orEmpty(), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
                if (nuevos > 0) Box(Modifier.clip(forma(10.dp)).background(f.tinta1).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text("$nuevos", style = MaterialTheme.typography.labelSmall, color = f.sobreTinta1)
                }
            }
        }
    }
}

@Composable
private fun resumen(m: MensajeChat): String = when {
    m.borrado -> stringResource(if (m.mio) R.string.chat_borrado_mio else R.string.chat_borrado_suyo)
    m.noSeLee -> stringResource(R.string.chat_no_se_lee)
    m.sticker != null -> (if (m.mio) stringResource(R.string.chat_tu) + " " else "") + stringResource(R.string.chat_un_sticker)
    m.obra != null -> "▸ " + ((m.obra["titulo"] as? JsonPrimitive)?.content ?: "")
    else -> (if (m.mio) stringResource(R.string.chat_tu) + " " else "") + m.texto
}

/** El color de las burbujas propias según el estilo de la conversación, y el de su letra. */
@Composable
private fun coloresPropios(estilo: EstiloChat): Pair<Color, Color> {
    val f = FogonTema.actual
    return when (estilo.color) {
        1 -> f.tinta2 to legibleSobre(f.tinta2, listOf(f.texto, f.fondo))
        2 -> f.superficie to f.texto
        else -> f.tinta1 to f.sobreTinta1
    }
}

/** El fondo con la trama de puntos de Fogón (si la persona lo eligió para esta conversación). */
private fun Modifier.fondoPuntos(color: Color) = drawBehind {
    val paso = 18.dp.toPx(); val r = 1.2.dp.toPx()
    var y = paso / 2
    while (y < size.height) {
        var x = paso / 2
        while (x < size.width) { drawCircle(color, r, Offset(x, y)); x += paso }
        y += paso
    }
}

/** Una imagen de sticker (WebP en base64), decodificada una vez. */
@Composable
private fun ImagenSticker(b64: String, modifier: Modifier = Modifier) {
    val imagen = remember(b64) {
        runCatching { java.util.Base64.getDecoder().decode(b64).let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }.getOrNull()
    }
    if (imagen != null) Image(imagen, null, modifier) else Box(modifier)
}

/**
 * Una conversación: empieza abajo (lo último) y se sube para ver lo anterior. Arriba, la foto y el nombre (llevan al
 * perfil) y las opciones del chat; cada mensaje se responde deslizándolo a la derecha y con el dedo apretado abre su
 * menú (responder, copiar, compartir, borrar para mí o para todos, guardar el sticker, reportar).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Conversacion(vm: CajonViewModel, e: Estado, usuario: String) {
    val f = FogonTema.actual
    val acciones = LocalAcciones.current
    val chats by vm.chat.conversaciones.collectAsStateWithLifecycle()
    val enviando by vm.chat.enviando.collectAsStateWithLifecycle()
    val estilos by vm.chat.estilos.collectAsStateWithLifecycle()
    val personas by vm.chat.personas.collectAsStateWithLifecycle()
    val yo by vm.red.usuario.collectAsStateWithLifecycle()
    val persona = personas[usuario] ?: PersonaSocial(usuario)
    val nombre = persona.nombre.ifBlank { "@$usuario" }
    val estilo = estilos[usuario] ?: EstiloChat()
    var texto by rememberSaveable { mutableStateOf("") }
    var responder by remember { mutableStateOf<MensajeChat?>(null) }
    var elegir by remember { mutableStateOf(false) }
    var stickers by remember { mutableStateOf(false) }
    var menuDe by remember { mutableStateOf<MensajeChat?>(null) }
    val lista = chats[usuario].orEmpty()
    val alReves = lista.asReversed()
    val estado = rememberLazyListState()
    val alcance = rememberCoroutineScope()
    LaunchedEffect(usuario) { vm.chat.conocer(usuario) }
    // Mientras está abierta, revisa cada 8 segundos si llegó algo.
    LaunchedEffect(usuario) { while (true) { vm.chat.recibir(); vm.chat.marcarLeida(usuario); delay(8000) } }
    // Al llegar o mandar algo, baja a lo último.
    LaunchedEffect(lista.lastOrNull()?.id) { if (lista.isNotEmpty()) estado.animateScrollToItem(0) }

    Column(Modifier.fillMaxSize().then(if (estilo.puntos) Modifier.fondoPuntos(f.linea) else Modifier)) {
        // Arriba: volver, la foto y el nombre (llevan al perfil) y las opciones del chat.
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            BotonIcono(Iconos.Atras, stringResource(R.string.volver)) { vm.cerrar() }
            Row(Modifier.weight(1f).clip(forma(16.dp)).clickable { vm.abrir(Pantalla.PerfilRed(usuario)) }.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AvatarRed(persona.copy(foto = persona.foto || personas[usuario] == null), 38.dp)
                Column(Modifier.weight(1f)) {
                    Text(nombre, style = MaterialTheme.typography.titleSmall, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("@$usuario", style = MaterialTheme.typography.labelSmall, color = f.textoSuave, maxLines = 1)
                }
            }
            BotonIcono(Iconos.Opciones, stringResource(R.string.chat_info)) { vm.abrir(Pantalla.InfoChat(usuario)) }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = estado, reverseLayout = true, contentPadding = PaddingValues(vertical = 8.dp)) {
            itemsIndexed(alReves, key = { _, m -> m.id }) { i, m ->
                val dia = diaDe(m.cuando)
                val anterior = alReves.getOrNull(i + 1)
                Column {
                    if (anterior == null || diaDe(anterior.cuando) != dia) SeparadorDia(dia)
                    // La foto de la otra persona va junto a su último mensaje seguido (como en los chats de siempre).
                    val siguiente = alReves.getOrNull(i - 1)
                    Burbuja(vm, e, m, usuario, yo, nombre, estilo, persona.takeIf { !m.mio && (siguiente == null || siguiente.mio) },
                        onResponder = { responder = m },
                        onMenu = { menuDe = m },
                        onIrA = { clave -> alReves.indexOfFirst { it.clave == clave }.takeIf { it >= 0 }?.let { alcance.launch { estado.animateScrollToItem(it) } } })
                }
            }
            if (lista.isEmpty()) itemsIndexed(listOf(0), key = { _, _ -> "vacio" }) { _, _ -> Pista(stringResource(R.string.chat_primero), Modifier.padding(16.dp)) }
        }

        if (enviando) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = f.tinta1, trackColor = f.linea)
        // Respondiendo a un mensaje: la cita encima del campo.
        responder?.let { r ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(forma(14.dp)).background(f.superficie).border(f.borde, f.linea, forma(14.dp)).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(3.dp).heightIn(min = 36.dp).background(f.tinta1))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(stringResource(R.string.chat_respondiendo, if (r.mio) stringResource(R.string.chat_ti) else nombre), style = MaterialTheme.typography.labelMedium, color = f.acentoTexto)
                    Text(if (r.sticker != null) stringResource(R.string.chat_un_sticker) else resumen(r), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BotonIcono(Iconos.Cerrar, stringResource(R.string.cancelar)) { responder = null }
            }
        }
        if (elegir) Column(Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
            Seccion(stringResource(R.string.chat_elige_obra)) { Pildora(stringResource(R.string.volver)) { elegir = false } }
            e.vivas.sortedByDescending { it.cambio }.take(30).forEach { o ->
                Row(Modifier.fillMaxWidth().clip(forma(14.dp)).clickable { vm.chat.enviar(usuario, "", o, responder); responder = null; elegir = false }.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Portada(o.portada, o.tipo, o.titulo, Modifier.width(40.dp), conTitulo = false)
                    Text(o.titulo, style = MaterialTheme.typography.bodyMedium, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (stickers) PanelStickers(vm) { s -> vm.chat.enviar(usuario, "", responde = responder, sticker = s); responder = null }
        // La barra de escribir: una sola píldora, con mandar una obra a la izquierda y los stickers (o enviar) a la derecha.
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp).clip(FormaPildora).background(f.superficie).border(f.borde, f.linea, FormaPildora)
            .padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(f.tinta1).clickable { elegir = !elegir; stickers = false }, contentAlignment = Alignment.Center) {
                IconoRiso(Iconos.Cajon, stringResource(R.string.chat_mandar_una_obra), tamano = 20.dp, tinta = f.sobreTinta1)
            }
            androidx.compose.foundation.text.BasicTextField(texto, { texto = it.take(4000) }, Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp), maxLines = 5,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = f.texto), cursorBrush = androidx.compose.ui.graphics.SolidColor(f.acentoTexto),
                decorationBox = { campo -> Box { if (texto.isEmpty()) Text(stringResource(R.string.chat_escribe), style = MaterialTheme.typography.bodyLarge, color = f.textoSuave); campo() } })
            if (texto.isBlank()) BotonIcono(Iconos.Sticker, stringResource(R.string.chat_stickers), tinta = if (stickers) f.acentoTexto else null) { stickers = !stickers; elegir = false }
            else BotonIcono(Iconos.Enviar, stringResource(R.string.red_enviar), activo = !enviando, lleno = true) {
                vm.chat.enviar(usuario, texto, responde = responder); texto = ""; responder = null
            }
        }
    }

    // El menú de un mensaje.
    menuDe?.let { m ->
        val portapapeles = LocalClipboardManager.current
        val contexto = androidx.compose.ui.platform.LocalContext.current
        val copiado = stringResource(R.string.chat_copiado)
        var reportando by remember { mutableStateOf(false) }
        ModalBottomSheet({ menuDe = null }, containerColor = f.fondo) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                if (reportando) {
                    Pista(stringResource(R.string.chat_reportar_explica, usuario), Modifier.padding(horizontal = 16.dp))
                    FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MOTIVOS_REPORTE.forEach { (codigo, mo) -> val t = stringResource(mo)
                            Pildora(t) { vm.red.reportar("mensaje", "$usuario:mensajes", "$codigo: $t\n${m.texto}"); menuDe = null } }
                    }
                    return@Column
                }
                if (!m.borrado && !m.noSeLee) FilaMenu(Iconos.Comentario, stringResource(R.string.chat_responder)) { responder = m; menuDe = null }
                if (m.texto.isNotBlank()) {
                    FilaMenu(Iconos.Lista, stringResource(R.string.chat_copiar)) { portapapeles.setText(AnnotatedString(m.texto)); vm.decir(copiado); menuDe = null }
                    FilaMenu(Iconos.Compartir, stringResource(R.string.compartir)) {
                        contexto.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND)
                            .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, m.texto), null)); menuDe = null
                    }
                }
                m.obra?.let { o -> FilaMenu(Iconos.Compartir, stringResource(R.string.compartir)) {
                    contexto.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND)
                        .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, (o["titulo"] as? JsonPrimitive)?.content.orEmpty()), null)); menuDe = null
                } }
                m.sticker?.let { s ->
                    FilaMenu(Iconos.Guardar, stringResource(R.string.chat_sticker_guardar)) { vm.chat.guardarSticker(s); menuDe = null }
                    FilaMenu(Iconos.Compartir, stringResource(R.string.compartir)) {
                        runCatching { java.util.Base64.getDecoder().decode(s).let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) } }.getOrNull()
                            ?.let { acciones?.compartir?.invoke(it, "sticker") }
                        menuDe = null
                    }
                }
                FilaMenu(Iconos.Borrar, stringResource(R.string.chat_borrar_para_mi)) { vm.chat.borrarParaMi(usuario, m); menuDe = null }
                if (m.mio && !m.borrado) FilaMenu(Iconos.Borrar, stringResource(R.string.chat_borrar_para_todos), peligro = true) { vm.chat.borrarParaTodos(usuario, m); menuDe = null }
                if (!m.mio && !m.borrado) FilaMenu(Iconos.Bandera, stringResource(R.string.red_reportar), peligro = true) { reportando = true }
            }
        }
    }
}

/** Una fila de un menú de abajo. */
@Composable
fun FilaMenu(icono: io.github.fogonapps.identidad.Dibujo, texto: String, peligro: Boolean = false, onClick: () -> Unit) {
    val f = FogonTema.actual
    val color = if (peligro) MaterialTheme.colorScheme.error else f.texto
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        IconoRiso(icono, null, tamano = 20.dp, tinta = color)
        Text(texto, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

private fun diaDe(cuando: Long): LocalDate = Instant.ofEpochMilli(cuando).atZone(ZoneId.systemDefault()).toLocalDate()

/** "Hoy", "Ayer" o la fecha, entre los mensajes de días distintos. */
@Composable
private fun SeparadorDia(dia: LocalDate) {
    val f = FogonTema.actual
    val hoy = LocalDate.now()
    val texto = when (dia) {
        hoy -> stringResource(R.string.hoy)
        hoy.minusDays(1) -> stringResource(R.string.ayer)
        else -> dia.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(texto, style = MaterialTheme.typography.labelSmall, color = f.textoSuave,
            modifier = Modifier.clip(forma(10.dp)).background(f.superficie).padding(horizontal = 10.dp, vertical = 3.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Burbuja(
    vm: CajonViewModel, e: Estado, m: MensajeChat, usuario: String, yo: String?, nombre: String, estilo: EstiloChat,
    foto: PersonaSocial?, onResponder: () -> Unit, onMenu: () -> Unit, onIrA: (String) -> Unit,
) {
    val f = FogonTema.actual
    val (fondoMio, letraMia) = coloresPropios(estilo)
    val color = if (m.mio) letraMia else f.texto
    val tam = if (estilo.letraGrande) 18.sp else MaterialTheme.typography.bodyMedium.fontSize
    // Deslizar a la derecha para responder.
    var corrido by remember { mutableFloatStateOf(0f) }
    val umbral = with(LocalDensity.current) { 64.dp.toPx() }
    val hora = remember(m.cuando) { Instant.ofEpochMilli(m.cuando).atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")) }
    val soloSticker = m.sticker != null && !m.borrado && m.texto.isBlank()
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)
        .pointerInput(m.id) {
            if (m.borrado || m.noSeLee) return@pointerInput
            detectHorizontalDragGestures(
                onDragEnd = { if (corrido > umbral) onResponder(); corrido = 0f },
                onDragCancel = { corrido = 0f },
            ) { _, d -> corrido = (corrido + d).coerceIn(0f, umbral * 1.5f) }
        }
        .offset { IntOffset(corrido.roundToInt(), 0) },
        horizontalArrangement = if (m.mio) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
        if (!m.mio) {
            if (foto != null) AvatarRed(foto, 28.dp) else Box(Modifier.size(28.dp))
            Box(Modifier.width(8.dp))
        }
        Column(Modifier.widthIn(max = 280.dp).clip(forma(18.dp))
            .then(if (soloSticker) Modifier else Modifier.background(if (m.mio) fondoMio else f.superficie).border(f.borde, if (m.mio) fondoMio else f.linea, forma(18.dp)))
            .combinedClickable(onClick = {}, onLongClick = onMenu)
            .padding(horizontal = if (soloSticker) 0.dp else 12.dp, vertical = if (soloSticker) 0.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (m.borrado) {
                Text(stringResource(if (m.mio) R.string.chat_borrado_mio else R.string.chat_borrado_suyo), fontStyle = FontStyle.Italic,
                    style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = 0.75f))
            }
            if (m.noSeLee) Text(stringResource(R.string.chat_no_se_lee), style = MaterialTheme.typography.bodySmall, color = color)
            // A qué responde: tocarlo lleva a ese mensaje.
            m.responde?.let { r ->
                val deMi = r.de == yo
                Text(stringResource(if (m.mio) R.string.chat_respondiste else R.string.chat_respondio), style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.7f))
                Row(Modifier.clip(forma(10.dp)).background(f.fondo.copy(alpha = 0.35f)).clickable { onIrA(r.k) }) {
                    Box(Modifier.width(3.dp).heightIn(min = 34.dp).background(if (m.mio) letraMia else f.tinta1))
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(if (deMi) stringResource(R.string.chat_ti) else nombre, style = MaterialTheme.typography.labelMedium, color = color)
                        Text(if (r.sticker) stringResource(R.string.chat_un_sticker) else r.texto, style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            m.sticker?.let { ImagenSticker(it, Modifier.size(150.dp)) }
            m.obra?.let { o ->
                val titulo = (o["titulo"] as? JsonPrimitive)?.content.orEmpty()
                val id = (o["id"] as? JsonPrimitive)?.content
                val fuente = (o["fuente"] as? JsonPrimitive)?.content.orEmpty()
                // Si ya está en el cajón, abre su ficha; si no, la busca para agregarla.
                val propia = e.vivas.firstOrNull { it.id == id || fuente.isNotBlank() && it.fuente == fuente }
                Row(Modifier.clip(forma(12.dp)).clickable { if (propia != null) vm.abrir(Pantalla.Ficha(propia.id)) else { vm.abrir(Pantalla.Buscar(io.github.fogonapps.cajon.Secciones.TODO)); vm.buscar(titulo) } },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Portada((o["portada"] as? JsonPrimitive)?.content.orEmpty(), (o["tipoObra"] as? JsonPrimitive)?.content ?: "pelicula", titulo, Modifier.width(48.dp), conTitulo = false)
                    Column {
                        Text(titulo, style = MaterialTheme.typography.titleSmall, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(if (propia != null) R.string.chat_en_tu_cajon else R.string.chat_agregar), style = MaterialTheme.typography.bodySmall, color = color)
                    }
                }
            }
            if (m.texto.isNotBlank()) Text(m.texto, style = MaterialTheme.typography.bodyMedium, fontSize = tam, color = color)
            Text(hora, style = MaterialTheme.typography.labelSmall, color = (if (soloSticker) f.textoSuave else color).copy(alpha = 0.7f), modifier = Modifier.align(Alignment.End))
        }
    }
}

/**
 * Los stickers: buscar entre los guardados por su nombre, los de Fogón, y crear uno nuevo con una foto (se recorta
 * cuadrado y se le pone un nombre para encontrarlo). Tocar uno lo manda; con el dedo apretado, se quita.
 */
@Composable
private fun PanelStickers(vm: CajonViewModel, onMandar: (String) -> Unit) {
    val f = FogonTema.actual
    val acciones = LocalAcciones.current
    val guardados by vm.chat.stickers.collectAsStateWithLifecycle()
    val alcance = rememberCoroutineScope()
    var buscar by rememberSaveable { mutableStateOf("") }
    var nuevo by remember { mutableStateOf<String?>(null) }
    var nombreNuevo by remember { mutableStateOf("") }
    var quitar by remember { mutableStateOf<Sticker?>(null) }
    val noSePudo = stringResource(R.string.chat_sticker_no_se_pudo)
    Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        nuevo?.let { s ->
            // Recién creado: ponerle nombre y guardarlo (o mandarlo de una).
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ImagenSticker(s, Modifier.size(84.dp).clip(forma(14.dp)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CampoTexto(nombreNuevo, { nombreNuevo = it }, stringResource(R.string.chat_sticker_nombre), Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pildora(stringResource(R.string.guardar), acento = true) { vm.chat.guardarSticker(s, nombreNuevo); nuevo = null; nombreNuevo = "" }
                        Pildora(stringResource(R.string.red_enviar)) { vm.chat.guardarSticker(s, nombreNuevo); onMandar(s); nuevo = null; nombreNuevo = "" }
                    }
                }
            }
        }
        OutlinedTextField(buscar, { buscar = it.take(40) }, Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.chat_stickers_buscar)) }, singleLine = true,
            shape = forma(22.dp), leadingIcon = { IconoRiso(Iconos.Buscar, null, tamano = 18.dp, tinta = f.textoSuave) })
        if (acciones != null) Pildora(stringResource(R.string.chat_sticker_crear), Iconos.Imagen) {
            acciones.elegirImagen { uri -> alcance.launch { vm.chat.crearSticker(uri)?.let { nuevo = it } ?: vm.decir(noSePudo) } }
        }
        val q = buscar.trim().lowercase()
        val tuyos = guardados.filter { q.isEmpty() || q in it.nombre.lowercase() }
        Text(stringResource(R.string.chat_stickers_tuyos), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
        if (tuyos.isEmpty() && q.isNotEmpty()) Pista(stringResource(R.string.chat_stickers_nada))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tuyos.forEach { s -> CeldaSticker(s.imagen, { onMandar(s.imagen) }, { quitar = s }) }
        }
        // Los de Fogón: los personajes de las apps.
        val deFogon = listOf(
            RId.drawable.personaje_cajon_feliz to R.string.chat_sticker_cajon, RId.drawable.personaje_forzudo_feliz to R.string.chat_sticker_forzudo,
            RId.drawable.personaje_ahorrador_feliz to R.string.chat_sticker_ahorrador, RId.drawable.personaje_cucha_feliz to R.string.chat_sticker_cucha,
            RId.drawable.chispa to R.string.chat_sticker_chispa,
        ).map { (r, n) -> Triple(r, n, stringResource(n)) }.filter { q.isEmpty() || q in it.third.lowercase() }
        if (deFogon.isNotEmpty()) {
            Text(stringResource(R.string.chat_stickers_fogon), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                deFogon.forEach { (r, _, n) ->
                    Box(Modifier.size(72.dp).clip(forma(14.dp)).background(f.superficie).clickable { alcance.launch { vm.chat.stickerDe(r)?.let(onMandar) } }.padding(8.dp)) {
                        Image(androidx.compose.ui.res.painterResource(r), n, Modifier.fillMaxSize())
                    }
                }
            }
        }
        quitar?.let { s ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pista(stringResource(R.string.chat_sticker_quitar_seguro), Modifier.weight(1f))
                Pildora(stringResource(R.string.chat_sticker_quitar), acento = true) { vm.chat.quitarSticker(s.id); quitar = null }
                Pildora(stringResource(R.string.cancelar)) { quitar = null }
            }
        }
        Box(Modifier.size(4.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CeldaSticker(b64: String, onClick: () -> Unit, onLargo: () -> Unit) {
    val f = FogonTema.actual
    Box(Modifier.size(72.dp).clip(forma(14.dp)).background(f.superficie).combinedClickable(onClick = onClick, onLongClick = onLargo).padding(4.dp)) {
        ImagenSticker(b64, Modifier.fillMaxSize())
    }
}

/**
 * La información de un chat, al estilo de los chats de siempre: la foto grande, el nombre y el @usuario, ver su perfil,
 * las obras que se mandaron, personalizar, el cifrado (con el número de seguridad), vaciar, borrar, bloquear y reportar.
 */
@Composable
fun InfoChat(vm: CajonViewModel, e: Estado, usuario: String) {
    val f = FogonTema.actual
    val chats by vm.chat.conversaciones.collectAsStateWithLifecycle()
    val estilos by vm.chat.estilos.collectAsStateWithLifecycle()
    val personas by vm.chat.personas.collectAsStateWithLifecycle()
    val persona = personas[usuario] ?: PersonaSocial(usuario)
    val estilo = estilos[usuario] ?: EstiloChat()
    val lista = chats[usuario].orEmpty()
    var verNumero by remember { mutableStateOf(false) }
    var reportar by remember { mutableStateOf(false) }
    var confirmar by remember { mutableStateOf<String?>(null) }
    var personalizar by remember { mutableStateOf(false) }
    var opciones by remember { mutableStateOf(false) }
    val numero by produceState<String?>(null, usuario, verNumero) { if (verNumero) value = vm.chat.numeroSeguridad(usuario) }
    LaunchedEffect(usuario) { vm.chat.conocer(usuario) }
    Cabecera(stringResource(R.string.chat_info), vm::cerrar)
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AvatarRed(persona.copy(foto = persona.foto || personas[usuario] == null), 112.dp)
        Text(persona.nombre.ifBlank { "@$usuario" }, style = MaterialTheme.typography.headlineSmall, color = f.texto)
        Text("@$usuario", style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            @Composable
            fun Accion(icono: io.github.fogonapps.identidad.Dibujo, texto: String, onClick: () -> Unit) =
                Column(Modifier.clip(forma(16.dp)).clickable(onClick = onClick).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconoRiso(icono, null, tamano = 26.dp, tinta = f.texto)
                    Text(texto, style = MaterialTheme.typography.labelMedium, color = f.texto)
                }
            Accion(Iconos.Persona, stringResource(R.string.chat_perfil)) { vm.abrir(Pantalla.PerfilRed(usuario)) }
            Accion(Iconos.Imagen, stringResource(R.string.chat_personalizar_corto)) { personalizar = !personalizar }
            Accion(Iconos.Candado, stringResource(R.string.chat_seguridad)) { verNumero = !verNumero }
            Accion(Iconos.Opciones, stringResource(R.string.chat_opciones)) { opciones = !opciones }
        }
    }

    // Lo que se mandaron: obras y stickers.
    val obras = lista.mapNotNull { it.obra }.distinctBy { (it["titulo"] as? JsonPrimitive)?.content }.asReversed()
    val stickers = lista.mapNotNull { it.sticker }.distinct().asReversed()
    if (obras.isNotEmpty() || stickers.isNotEmpty()) {
        Seccion(stringResource(R.string.chat_lo_que_se_mandaron))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            obras.take(20).forEach { o ->
                val titulo = (o["titulo"] as? JsonPrimitive)?.content.orEmpty()
                Portada((o["portada"] as? JsonPrimitive)?.content.orEmpty(), (o["tipoObra"] as? JsonPrimitive)?.content ?: "pelicula", titulo, Modifier.width(72.dp), conTitulo = false)
            }
            stickers.take(20).forEach { s -> CeldaSticker(s, { vm.chat.guardarSticker(s) }, {}) }
        }
    }

    if (personalizar) Seccion(stringResource(R.string.chat_personalizar))
    if (personalizar) Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.chat_color_burbujas), style = MaterialTheme.typography.bodyMedium, color = f.texto)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(f.tinta1, f.tinta2, f.superficie).forEachIndexed { i, c ->
                Box(Modifier.size(40.dp).clip(CircleShape).background(c).border(if (estilo.color == i) 3.dp else f.borde, if (estilo.color == i) f.texto else f.linea, CircleShape)
                    .clickable { vm.chat.ponerEstilo(usuario, estilo.copy(color = i)) })
            }
        }
        FilaInterruptor(stringResource(R.string.chat_fondo_puntos), estilo.puntos) { vm.chat.ponerEstilo(usuario, estilo.copy(puntos = it)) }
        FilaInterruptor(stringResource(R.string.chat_letra_grande), estilo.letraGrande) { vm.chat.ponerEstilo(usuario, estilo.copy(letraGrande = it)) }
    }

    if (verNumero) Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(forma(18.dp)).background(f.superficie).border(f.borde, f.linea, forma(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.chat_numero), style = MaterialTheme.typography.titleSmall, color = f.texto)
        Text(numero ?: "…", fontFamily = f.cifras, style = MaterialTheme.typography.titleMedium, color = f.acentoTexto)
        Text(stringResource(R.string.chat_numero_pista, usuario), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
    }

    if (opciones) {
        FilaAjuste(Iconos.Borrar, stringResource(R.string.chat_vaciar), null) { confirmar = "vaciar" }
        FilaAjuste(Iconos.Borrar, stringResource(R.string.chat_borrar), null) { confirmar = "borrar" }
        FilaAjuste(Iconos.Cerrar, stringResource(R.string.red_bloquear), null) { confirmar = "bloquear" }
        FilaAjuste(Iconos.Bandera, stringResource(R.string.red_reportar), null) { reportar = !reportar }
    }
    confirmar?.let { c ->
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pista(stringResource(when (c) { "vaciar" -> R.string.chat_vaciar_seguro; "borrar" -> R.string.chat_borrar_seguro; else -> R.string.chat_bloquear_seguro }), Modifier.weight(1f))
            Pildora(stringResource(R.string.red_si_borrar).takeIf { c != "bloquear" } ?: stringResource(R.string.red_bloquear), acento = true) {
                when (c) {
                    "vaciar" -> vm.chat.vaciar(usuario)
                    "borrar" -> { vm.chat.borrarConversacion(usuario); vm.cerrar(); vm.cerrar() }
                    else -> { vm.red.bloquear(usuario, true); vm.chat.borrarConversacion(usuario); vm.cerrar(); vm.cerrar() }
                }
                confirmar = null
            }
            Pildora(stringResource(R.string.cancelar)) { confirmar = null }
        }
    }
    if (reportar) Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Pista(stringResource(R.string.chat_reportar_explica, usuario))
        val suyos = lista.filter { !it.mio && it.texto.isNotBlank() }.takeLast(5).joinToString("\n") { it.texto }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MOTIVOS_REPORTE.forEach { (codigo, m) -> val t = stringResource(m)
                Pildora(t) { vm.red.reportar("mensaje", "$usuario:mensajes", "$codigo: $t\n$suyos"); reportar = false } }
        }
    }
}

/** Una fila con un interruptor. */
@Composable
private fun FilaInterruptor(texto: String, valor: Boolean, onCambio: (Boolean) -> Unit) {
    val f = FogonTema.actual
    Row(Modifier.fillMaxWidth().clip(forma(14.dp)).clickable { onCambio(!valor) }, verticalAlignment = Alignment.CenterVertically) {
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = f.texto, modifier = Modifier.weight(1f))
        Switch(valor, onCambio)
    }
}


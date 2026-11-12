package io.github.fogonapps.cajon.ui

import io.github.fogonapps.cajon.obraJson
import io.github.fogonapps.cajon.enviarObra
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.compositeOver
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
    LaunchedEffect(Unit) { vm.red.cargarHistorias() }
    if (o == null) FilaNotas(vm, e)
    o?.let { Pista(stringResource(R.string.chat_mandar_obra, it.titulo), Modifier.padding(horizontal = 16.dp)) }
    fun abrir(u: String) {
        if (o != null) { vm.chat.enviarObra(u, "", o); vm.reemplazar(Pantalla.Conversacion(u)) } else vm.abrir(Pantalla.Conversacion(u))
    }
    // Buscar entre los chats y los amigos (quienes se siguen contigo), que salen primero para escribirles.
    val amigos by vm.red.amigos.collectAsStateWithLifecycle()
    val estilosChat by vm.chat.estilos.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.red.cargarAmigos() }
    val q = buscar.trim().lowercase()
    fun coincide(p: PersonaSocial, u: String) = q.isEmpty() || q in u.lowercase() || q in p.nombre.lowercase() || q in estilosChat[u]?.apodo.orEmpty().lowercase()
    if (o == null) io.github.fogonapps.social.ui.CumplesHoyChat(interfazSocial(vm)) { abrir(it) }
    if (o == null) OutlinedTextField(buscar, { buscar = it.take(40) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), singleLine = true, shape = forma(22.dp),
        placeholder = { Text(stringResource(R.string.chat_buscar_amigos)) }, leadingIcon = { IconoRiso(Iconos.Buscar, null, tamano = 18.dp, tinta = f.textoSuave) })
    val amigosVisibles = amigos.filter { coincide(it, it.usuario) }
    if (amigosVisibles.isNotEmpty()) {
        Text(stringResource(R.string.amigos), style = MaterialTheme.typography.labelLarge, color = f.textoSuave, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            amigosVisibles.forEach { a ->
                Column(Modifier.width(68.dp).clip(forma(14.dp)).clickable { abrir(a.usuario) }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AvatarRed(a, 52.dp)
                    Text(estilosChat[a.usuario]?.apodo?.ifBlank { null } ?: a.nombre.ifBlank { a.usuario }, style = MaterialTheme.typography.labelSmall, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    if (chats.isEmpty()) { Pista(stringResource(R.string.chat_vacio), Modifier.padding(16.dp)); return }
    chats.entries.filter { (u, _) -> coincide(personas[u] ?: PersonaSocial(u), u) }.sortedByDescending { it.value.lastOrNull()?.cuando ?: 0 }.forEach { (u, l) ->
        val ultimo = l.lastOrNull()
        val nuevos = l.count { !it.mio && !it.leido }
        val p = personas[u] ?: PersonaSocial(u, foto = true)
        FilaPersona(estilosChat[u]?.apodo?.takeIf { it.isNotBlank() }?.let { p.copy(nombre = it) } ?: p, { abrir(u) }) {
            Column(horizontalAlignment = Alignment.End) {
                Text(ultimo?.let { resumen(it) }.orEmpty(), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
                if (estilosChat[u]?.silenciado == true) Text(stringResource(R.string.chat_silenciado), style = MaterialTheme.typography.labelSmall, color = f.textoSuave)
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
    m.foto != null -> (if (m.mio) stringResource(R.string.chat_tu) + " " else "") + stringResource(R.string.chat_foto)
    m.audio != null -> (if (m.mio) stringResource(R.string.chat_tu) + " " else "") + stringResource(R.string.chat_nota_voz)
    m.obra != null -> "▸ " + ((m.obra?.get("titulo") as? JsonPrimitive)?.content ?: "")
    else -> (if (m.mio) stringResource(R.string.chat_tu) + " " else "") + m.texto
}

/** El color de las burbujas propias según el estilo de la conversación, y el de su letra. */
@Composable
private fun coloresPropios(estilo: EstiloChat): Pair<Color, Color> {
    val f = FogonTema.actual
    return when (estilo.color) {
        1 -> f.tinta2 to legibleSobre(f.tinta2, listOf(f.texto, f.fondo))
        2 -> f.superficie to f.texto
        3 -> f.tinta1.copy(alpha = 0.45f).compositeOver(f.fondo).let { it to legibleSobre(it, listOf(f.texto, f.fondo)) }
        4 -> f.tinta2.copy(alpha = 0.45f).compositeOver(f.fondo).let { it to legibleSobre(it, listOf(f.texto, f.fondo)) }
        5 -> f.pildora to f.sobrePildora
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
 * Una conversación: la común de las cuatro apps (fotos, notas de voz, stickers, reacciones, «Visto»…) con lo de
 * El Cajón: mandar una obra (su tarjeta abre la ficha o la busca), los stickers con buscador y compartir imágenes.
 */
@Composable
fun Conversacion(vm: CajonViewModel, e: Estado, usuario: String) {
    val f = FogonTema.actual
    val acciones = LocalAcciones.current
    val social = interfazSocial(vm)
    io.github.fogonapps.social.ui.ConversacionSocial(social, vm.chat,
        io.github.fogonapps.social.ui.NavegacionChat({ vm.abrir(Pantalla.Conversacion(it)) }, { vm.abrir(Pantalla.InfoChat(it)) }), usuario,
        io.github.fogonapps.social.ui.ExtrasChat(
            tarjetaObra = { o, color, onMenu -> TarjetaObraChat(vm, e, o, color, onMenu) },
            elegirObra = { enviar ->
                Column(Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.chat_elige_obra), style = MaterialTheme.typography.titleSmall, color = f.texto, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    e.vivas.sortedByDescending { it.cambio }.take(30).forEach { o ->
                        Row(Modifier.fillMaxWidth().clip(forma(14.dp)).clickable { enviar(obraJson(o)) }.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Portada(o.portada, o.tipo, o.titulo, Modifier.width(40.dp), conTitulo = false)
                            Text(o.titulo, style = MaterialTheme.typography.bodyMedium, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            iconoObra = Iconos.Cajon, textoObra = R.string.chat_mandar_una_obra,
            compartirImagen = acciones?.compartir?.let { c -> { b: android.graphics.Bitmap -> c(b, "sticker") } },
            panelStickers = { mandar -> PanelStickers(vm, mandar) },
        ))
}

/** Una obra mandada por el chat: si ya está en el cajón abre su ficha; si no, la busca para agregarla. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TarjetaObraChat(vm: CajonViewModel, e: Estado, o: kotlinx.serialization.json.JsonObject, color: Color, onMenu: () -> Unit) {
    val titulo = (o["titulo"] as? JsonPrimitive)?.content.orEmpty()
    val id = (o["id"] as? JsonPrimitive)?.content
    val fuente = (o["fuente"] as? JsonPrimitive)?.content.orEmpty()
    val propia = e.vivas.firstOrNull { it.id == id || fuente.isNotBlank() && it.fuente == fuente }
    Row(Modifier.clip(forma(12.dp)).combinedClickable(onLongClick = onMenu, onClick = { if (propia != null) vm.abrir(Pantalla.Ficha(propia.id)) else { vm.abrir(Pantalla.Buscar(io.github.fogonapps.cajon.Secciones.TODO)); vm.buscar(titulo) } }),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Portada((o["portada"] as? JsonPrimitive)?.content.orEmpty(), (o["tipoObra"] as? JsonPrimitive)?.content ?: "pelicula", titulo, Modifier.width(48.dp), conTitulo = false)
        Column {
            Text(titulo, style = MaterialTheme.typography.titleSmall, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(if (propia != null) R.string.chat_en_tu_cajon else R.string.chat_agregar), style = MaterialTheme.typography.bodySmall, color = color)
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

/**
 * Los stickers: buscar entre los guardados por su nombre, los de Fogón, y crear uno nuevo con una foto (se elige el
 * recorte, cuadrado o redondo, y se le pone un nombre para encontrarlo). Tocar uno lo manda en el tamaño elegido
 * (chico, normal o grande); con el dedo apretado, se quita.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PanelStickers(vm: CajonViewModel, onMandarConTamano: (String, Int) -> Unit) {
    var tamano by rememberSaveable { mutableStateOf(1) }
    val onMandar: (String) -> Unit = { onMandarConTamano(it, tamano) }
    var editando by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
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
        editando?.let { foto ->
            EditorSticker(foto, { editando = null }) { recorte, redondo ->
                editando = null
                alcance.launch { vm.chat.stickerRecortado(foto, recorte, redondo)?.let { nuevo = it } ?: vm.decir(noSePudo) }
            }
        }
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
            acciones.elegirImagen { uri -> alcance.launch { vm.chat.fotoParaSticker(uri)?.let { editando = it } ?: vm.decir(noSePudo) } }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.chat_sticker_tamano), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
            listOf(R.string.chat_sticker_chico, R.string.chat_sticker_normal, R.string.chat_sticker_grande).forEachIndexed { i, t ->
                Pildora(stringResource(t), acento = tamano == i) { tamano = i }
            }
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(persona.nombre.ifBlank { "@$usuario" }, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.headlineSmall, color = f.texto)
            io.github.fogonapps.perfil.InsigniaCuenta(persona.insignia, tamano = 20.dp)
        }
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(f.tinta1, f.tinta2, f.superficie, f.tinta1.copy(alpha = 0.45f).compositeOver(f.fondo), f.tinta2.copy(alpha = 0.45f).compositeOver(f.fondo), f.pildora).forEachIndexed { i, c ->
                Box(Modifier.size(40.dp).clip(CircleShape).background(c).border(if (estilo.color == i) 3.dp else f.borde, if (estilo.color == i) f.texto else f.linea, CircleShape)
                    .clickable { vm.chat.ponerEstilo(usuario, estilo.copy(color = i)) })
            }
        }
        FilaInterruptor(stringResource(R.string.chat_fondo_puntos), estilo.puntos) { vm.chat.ponerEstilo(usuario, estilo.copy(puntos = it)) }
        val conVisto by vm.chat.confirmaLectura.collectAsStateWithLifecycle()
        FilaInterruptor(stringResource(R.string.chat_confirma_lectura), conVisto) { vm.chat.ponerConfirmaLectura(it) }
        Text(stringResource(R.string.chat_confirma_lectura_explica), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        Text(stringResource(R.string.chat_tamano_letra), style = MaterialTheme.typography.bodyMedium, color = f.texto)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.chat_sticker_chico, R.string.chat_sticker_normal, R.string.chat_sticker_grande).forEachIndexed { i, t ->
                Pildora(stringResource(t), acento = estilo.tamanoLetra == i) { vm.chat.ponerEstilo(usuario, estilo.copy(letra = i, letraGrande = i == 2)) }
            }
        }
        // De fondo, el póster (tenue) de una obra de tu cajón.
        Text(stringResource(R.string.chat_fondo_poster), style = MaterialTheme.typography.bodyMedium, color = f.texto)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(56.dp).aspectRatio(2f / 3f).clip(forma(8.dp)).background(f.superficie).border(if (estilo.poster.isBlank()) 3.dp else 1.dp, if (estilo.poster.isBlank()) f.texto else f.linea, forma(8.dp))
                .clickable { vm.chat.ponerEstilo(usuario, estilo.copy(poster = "")) }, contentAlignment = Alignment.Center) { IconoRiso(Iconos.Cerrar, stringResource(R.string.chat_sin_fondo), tamano = 18.dp, tinta = f.textoSuave) }
            e.vivas.filter { it.portada.startsWith("https://") }.sortedByDescending { it.cambio }.take(30).forEach { ob ->
                Box(Modifier.width(56.dp).clip(forma(8.dp)).border(if (estilo.poster == ob.portada) 3.dp else 0.dp, f.texto, forma(8.dp)).clickable { vm.chat.ponerEstilo(usuario, estilo.copy(poster = ob.portada)) }) {
                    Portada(ob.portada, ob.tipo, ob.titulo, Modifier.fillMaxWidth(), conTitulo = false)
                }
            }
        }
        // Silenciar: sus mensajes no avisan (la revisión de avisos lee la lista de silenciados del perfil).
        val contexto = androidx.compose.ui.platform.LocalContext.current
        FilaInterruptor(stringResource(R.string.chat_silenciar), estilo.silenciado) { si ->
            vm.chat.ponerEstilo(usuario, estilo.copy(silenciado = si))
            val prefs = contexto.getSharedPreferences(io.github.fogonapps.social.ui.Avisos.PREFS, android.content.Context.MODE_PRIVATE)
            val clave = "p${vm.recursos.id}.silenciados"
            val actual = prefs.getStringSet(clave, emptySet()).orEmpty()
            prefs.edit().putStringSet(clave, if (si) actual + usuario else actual - usuario).apply()
        }
        // Un apodo para la otra persona: solo lo ves tú.
        var apodo by remember(usuario) { mutableStateOf(estilo.apodo) }
        OutlinedTextField(apodo, { apodo = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true, shape = forma(18.dp), label = { Text(stringResource(R.string.chat_apodo)) },
            trailingIcon = { if (apodo != estilo.apodo) BotonIcono(Iconos.Chulo, stringResource(R.string.guardar)) { vm.chat.ponerEstilo(usuario, estilo.copy(apodo = apodo.trim())) } })
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
        MotivosReporte { codigo, texto ->
            vm.red.reportar("mensaje", "$usuario:mensajes", "$codigo: $texto\n$suyos"); reportar = false
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

/**
 * El editor de un sticker nuevo: la foto se mueve y se acerca con los dedos dentro del cuadro; lo que queda adentro es
 * el sticker, cuadrado o redondo.
 */
@Composable
private fun EditorSticker(foto: android.graphics.Bitmap, onCancelar: () -> Unit, onListo: (android.graphics.Rect, Boolean) -> Unit) {
    val f = FogonTema.actual
    var zoom by remember { mutableStateOf(1f) }
    var desplazamiento by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var redondo by remember { mutableStateOf(false) }
    var lado by remember { mutableStateOf(1) }
    val imagen = remember(foto) { foto.asImageBitmap() }
    androidx.compose.ui.window.Dialog(onCancelar) {
        Column(Modifier.fillMaxWidth().clip(forma(24.dp)).background(f.fondo).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.chat_sticker_editar), style = MaterialTheme.typography.titleMedium, color = f.texto)
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(if (redondo) CircleShape else forma(18.dp)).background(f.superficie)
                .onSizeChanged { lado = it.width }
                .pointerInput(foto) {
                    detectTransformGestures { _, mover, escala, _ ->
                        zoom = (zoom * escala).coerceIn(1f, 6f)
                        // Que la foto siempre cubra el cuadro.
                        val base = lado / minOf(foto.width, foto.height).toFloat()
                        val maxX = (foto.width * base * zoom - lado) / 2f; val maxY = (foto.height * base * zoom - lado) / 2f
                        desplazamiento = androidx.compose.ui.geometry.Offset((desplazamiento.x + mover.x).coerceIn(-maxX, maxX), (desplazamiento.y + mover.y).coerceIn(-maxY, maxY))
                    }
                }) {
                Image(imagen, null, Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = desplazamiento.x; translationY = desplazamiento.y },
                    contentScale = ContentScale.Crop)
            }
            Pista(stringResource(R.string.chat_sticker_editar_pista))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pildora(stringResource(R.string.chat_sticker_cuadrado), acento = !redondo) { redondo = false }
                Pildora(stringResource(R.string.chat_sticker_redondo), acento = redondo) { redondo = true }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonSecundario(stringResource(R.string.cancelar), onCancelar, Modifier.weight(1f))
                BotonRiso(stringResource(R.string.chat_sticker_listo), {
                    // Del cuadro en pantalla a píxeles de la foto (la foto se ve recortada, centrada, con el zoom y el desplazamiento).
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


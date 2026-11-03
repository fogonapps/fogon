package io.github.fogonapps.cajon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.MensajeChat
import io.github.fogonapps.cajon.Pantalla
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.PersonaSocial
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.forma
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive

/** Las conversaciones. Con [obra], tocar una le manda esa obra (viene de "Mandar a alguien" en una ficha). */
@Composable
fun Chats(vm: CajonViewModel, e: Estado, obra: String?) {
    val f = FogonTema.actual
    val chats by vm.chat.conversaciones.collectAsStateWithLifecycle()
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    val b by vm.busqueda.collectAsStateWithLifecycle()
    var buscar by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.chat.recibir() }
    val o = obra?.let { id -> e.vivas.firstOrNull { it.id == id } }
    Cabecera(stringResource(if (o != null) R.string.chat_mandar_a else R.string.chat_titulo), vm::cerrar)
    if (usuario == null) { Pista(stringResource(R.string.red_invita_texto), Modifier.padding(16.dp)); return }
    o?.let { Pista(stringResource(R.string.chat_mandar_obra, it.titulo), Modifier.padding(horizontal = 16.dp)) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconoRiso(Iconos.Candado, null, tamano = 16.dp, tinta = f.textoSuave)
        Text(stringResource(R.string.chat_cifrado), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
    }
    // Empezar una conversación: buscar a alguien por su @usuario.
    OutlinedTextField(buscar, { buscar = it.take(40); vm.buscarPersonas(it) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        placeholder = { Text(stringResource(R.string.chat_buscar)) }, singleLine = true, shape = forma(22.dp),
        leadingIcon = { IconoRiso(Iconos.Buscar, null, tamano = 18.dp, tinta = f.textoSuave) })
    fun abrir(u: String) {
        if (o != null) { vm.chat.enviar(u, "", o); vm.reemplazar(Pantalla.Conversacion(u)) } else vm.abrir(Pantalla.Conversacion(u))
    }
    if (buscar.trim().length >= 2) {
        b.personas.filter { it.usuario != usuario }.forEach { p -> FilaPersona(p, { abrir(p.usuario) }) }
        return
    }
    if (chats.isEmpty()) { Pista(stringResource(R.string.chat_vacio), Modifier.padding(16.dp)); return }
    chats.entries.sortedByDescending { it.value.lastOrNull()?.cuando ?: 0 }.forEach { (u, l) ->
        val ultimo = l.lastOrNull()
        val nuevos = l.count { !it.mio && !it.leido }
        FilaPersona(PersonaSocial(u, foto = true), { abrir(u) }) {
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
    m.noSeLee -> stringResource(R.string.chat_no_se_lee)
    m.obra != null -> "▸ " + ((m.obra["titulo"] as? JsonPrimitive)?.content ?: "")
    else -> (if (m.mio) stringResource(R.string.chat_tu) + " " else "") + m.texto
}

/** Una conversación: los mensajes, mandar texto u obras del cajón, y el número de seguridad. */
@Composable
fun Conversacion(vm: CajonViewModel, e: Estado, usuario: String) {
    val f = FogonTema.actual
    val chats by vm.chat.conversaciones.collectAsStateWithLifecycle()
    val enviando by vm.chat.enviando.collectAsStateWithLifecycle()
    var texto by rememberSaveable { mutableStateOf("") }
    var elegir by remember { mutableStateOf(false) }
    var verNumero by remember { mutableStateOf(false) }
    val numero by produceState<String?>(null, usuario, verNumero) { if (verNumero) value = vm.chat.numeroSeguridad(usuario) }
    // Mientras está abierta, revisa cada 8 segundos si llegó algo.
    LaunchedEffect(usuario) { while (true) { vm.chat.recibir(); vm.chat.marcarLeida(usuario); delay(8000) } }
    Cabecera("@$usuario", vm::cerrar) {
        Row {
            BotonIcono(Iconos.Candado, stringResource(R.string.chat_numero)) { verNumero = !verNumero }
            BotonIcono(Iconos.Persona, stringResource(R.string.red_ver_perfil)) { vm.abrir(Pantalla.PerfilRed(usuario)) }
        }
    }
    if (verNumero) Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(forma(18.dp)).background(f.superficie).border(f.borde, f.linea, forma(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.chat_numero), style = MaterialTheme.typography.titleSmall, color = f.texto)
        Text(numero ?: "…", fontFamily = f.cifras, style = MaterialTheme.typography.titleMedium, color = f.acentoTexto)
        Text(stringResource(R.string.chat_numero_pista, usuario), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
    }
    val lista = chats[usuario].orEmpty()
    if (lista.isEmpty()) Pista(stringResource(R.string.chat_primero), Modifier.padding(16.dp))
    lista.forEach { m -> Burbuja(vm, e, m) }
    if (enviando) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = f.tinta1, trackColor = f.linea)
    if (elegir) {
        Seccion(stringResource(R.string.chat_elige_obra)) { Pildora(stringResource(R.string.volver)) { elegir = false } }
        e.vivas.sortedByDescending { it.cambio }.take(30).forEach { o ->
            Row(Modifier.fillMaxWidth().clip(forma(14.dp)).clickable { vm.chat.enviar(usuario, "", o); elegir = false }.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Portada(o.portada, o.tipo, o.titulo, Modifier.width(40.dp), conTitulo = false)
                Text(o.titulo, style = MaterialTheme.typography.bodyMedium, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BotonIcono(Iconos.Cajon, stringResource(R.string.chat_mandar_una_obra)) { elegir = !elegir }
        OutlinedTextField(texto, { texto = it.take(4000) }, Modifier.weight(1f), placeholder = { Text(stringResource(R.string.chat_escribe)) }, shape = forma(22.dp), maxLines = 5)
        BotonIcono(Iconos.Enviar, stringResource(R.string.red_enviar), activo = texto.isNotBlank() && !enviando, lleno = true) { vm.chat.enviar(usuario, texto); texto = "" }
    }
}

@Composable
private fun Burbuja(vm: CajonViewModel, e: Estado, m: MensajeChat) {
    val f = FogonTema.actual
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp), horizontalArrangement = if (m.mio) Arrangement.End else Arrangement.Start) {
        Column(Modifier.widthIn(max = 290.dp).clip(forma(18.dp)).background(if (m.mio) f.tinta1 else f.superficie)
            .border(f.borde, if (m.mio) f.tinta1 else f.linea, forma(18.dp)).padding(horizontal = 14.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val color = if (m.mio) f.sobreTinta1 else f.texto
            if (m.noSeLee) Text(stringResource(R.string.chat_no_se_lee), style = MaterialTheme.typography.bodySmall, color = color)
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
            if (m.texto.isNotBlank()) Text(m.texto, style = MaterialTheme.typography.bodyMedium, color = color)
        }
    }
}

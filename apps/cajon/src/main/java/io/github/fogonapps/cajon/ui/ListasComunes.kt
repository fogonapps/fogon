package io.github.fogonapps.cajon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.Pantalla
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.identidad.*
import io.github.fogonapps.social.DetalleListaComunSocial
import io.github.fogonapps.social.ListaComunSocial
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

/*
 * Listas en común: se arman entre amigos (quienes se siguen mutuamente); cada uno agrega obras de su cajón y vota las
 * que quiere ver. Viven en Fogón Social (no en el cajón): sin cuenta no hay.
 */

private fun texto(o: kotlinx.serialization.json.JsonObject, k: String) = (o[k] as? JsonPrimitive)?.content.orEmpty()

/** Todas tus listas en común y armar una nueva. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ListasComunes(vm: CajonViewModel) {
    val f = FogonTema.actual
    val alcance = rememberCoroutineScope()
    var listas by remember { mutableStateOf<List<ListaComunSocial>?>(null) }
    var creando by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { listas = vm.red.listasComunes() ?: emptyList() }
    Cabecera(stringResource(R.string.listas_comunes), vm::cerrar) {
        Pildora(stringResource(R.string.nueva), Iconos.Mas, acento = true) { creando = true }
    }
    Pista(stringResource(R.string.listas_comunes_pista), Modifier.padding(horizontal = 16.dp))
    val l = listas
    when {
        l == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.tinta1, trackColor = f.linea)
        l.isEmpty() -> Pista(stringResource(R.string.listas_comunes_vacio), Modifier.padding(16.dp))
        else -> l.forEach { lista ->
            FilaVidrio({ vm.abrir(Pantalla.ListaComun(lista.id)) }) {
                Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) { lista.miembros.take(4).forEach { AvatarRed(it, 32.dp) } }
                Column(Modifier.weight(1f)) {
                    Text(lista.nombre, style = MaterialTheme.typography.bodyLarge, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(pluralStringResource(R.plurals.listas_comunes_obras, lista.obras, lista.obras), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                }
                IconoRiso(Iconos.Siguiente, null, tamano = 18.dp, tinta = f.textoSuave)
            }
        }
    }
    if (creando) ModalBottomSheet({ creando = false }, containerColor = f.fondo) {
        val amigos by vm.red.amigos.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { vm.red.cargarAmigos() }
        var nombre by remember { mutableStateOf("") }
        val elegidos = remember { mutableStateListOf<String>() }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.listas_comunes_nueva), style = MaterialTheme.typography.titleMedium, color = f.texto)
            OutlinedTextField(nombre, { nombre = it.take(60) }, Modifier.fillMaxWidth(), singleLine = true, shape = forma(18.dp),
                placeholder = { Text(stringResource(R.string.listas_comunes_nombre)) })
            Text(stringResource(R.string.listas_comunes_con_quien), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
            if (amigos.isEmpty()) Pista(stringResource(R.string.listas_comunes_sin_amigos))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                amigos.forEach { a ->
                    val esta = a.usuario in elegidos
                    Pildora("@" + a.usuario, if (esta) Iconos.Chulo else null, acento = esta) { if (esta) elegidos.remove(a.usuario) else elegidos.add(a.usuario) }
                }
            }
            BotonRiso(stringResource(R.string.listas_comunes_crear), {
                alcance.launch {
                    val nueva = vm.red.crearListaComun(nombre.trim(), elegidos.toList())
                    if (nueva != null) { creando = false; listas = (listOf(nueva) + listas.orEmpty()); vm.abrir(Pantalla.ListaComun(nueva.id)) }
                    else vm.decir(vm.tx(R.string.aviso_sin_detalles))
                }
            }, Modifier.fillMaxWidth(), habilitado = nombre.isNotBlank())
        }
    }
}

/** Una lista en común: las obras (las más votadas primero), votar, quitar lo tuyo, agregar de tu cajón y sumar amigos. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ListaComun(vm: CajonViewModel, e: Estado, id: Long) {
    val f = FogonTema.actual
    val alcance = rememberCoroutineScope()
    val yo by vm.red.usuario.collectAsStateWithLifecycle()
    var detalle by remember { mutableStateOf<DetalleListaComunSocial?>(null) }
    var agregando by remember { mutableStateOf(false) }
    var sumando by remember { mutableStateOf(false) }
    val confirmar = LocalConfirmar.current
    fun recargar() { alcance.launch { detalle = vm.red.listaComun(id) } }
    LaunchedEffect(id) { recargar() }
    val d = detalle
    Cabecera(d?.lista?.nombre ?: stringResource(R.string.listas_comunes), vm::cerrar)
    if (d == null) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.tinta1, trackColor = f.linea); return }
    // Quiénes están.
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) { d.lista.miembros.take(6).forEach { AvatarRed(it, 32.dp) } }
        Text(d.lista.miembros.joinToString(", ") { "@" + it.usuario }, style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
    FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Pildora(stringResource(R.string.listas_comunes_agregar), Iconos.Mas, acento = true) { agregando = true }
        Pildora(stringResource(R.string.listas_comunes_sumar), Iconos.Gente) { sumando = true }
        Pildora(stringResource(if (d.lista.creador.usuario == yo) R.string.listas_comunes_borrar else R.string.listas_comunes_salir), Iconos.Cerrar) {
            confirmar(vm.tx(if (d.lista.creador.usuario == yo) R.string.listas_comunes_borrar_pregunta else R.string.listas_comunes_salir_pregunta), null) {
                alcance.launch { if (vm.red.salirDeListaComun(id)) vm.cerrar() }
            }
        }
    }
    if (d.obras.isEmpty()) Pista(stringResource(R.string.listas_comunes_sin_obras), Modifier.padding(16.dp))
    d.obras.forEach { o ->
        val tipo = texto(o.datos, "tipoObra").ifBlank { "pelicula" }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clip(forma(18.dp)).background(f.superficie).border(f.borde, f.linea, forma(18.dp))
            .clickable { vm.abrirObraDePerfil(texto(o.datos, "titulo"), tipo, texto(o.datos, "portada"), texto(o.datos, "fuente"), texto(o.datos, "anio").toIntOrNull(), texto(o.datos, "creador")) }
            .padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Portada(texto(o.datos, "portada"), tipo, texto(o.datos, "titulo"), Modifier.width(48.dp), esquina = 8.dp, conTitulo = false)
            Column(Modifier.weight(1f)) {
                Text(texto(o.datos, "titulo"), style = MaterialTheme.typography.titleSmall, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                o.quien?.let { Text(stringResource(R.string.listas_comunes_agrego, it.usuario), style = MaterialTheme.typography.labelSmall, color = f.textoSuave) }
            }
            // Votar: «la quiero ver». Quitar: lo que agregaste tú (o todo, si armaste la lista).
            Row(Modifier.clip(FormaPildora).background(if (o.yoVoto) f.tinta1 else f.fondo).border(f.borde, if (o.yoVoto) f.tinta1 else f.linea, FormaPildora)
                .clickable { alcance.launch { if (vm.red.votarListaComun(id, o.id, !o.yoVoto)) recargar() } }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconoRiso(Iconos.Corazon, stringResource(R.string.listas_comunes_votar), tamano = 16.dp, tinta = if (o.yoVoto) f.sobreTinta1 else f.texto,
                    relleno = if (o.yoVoto) f.sobreTinta1 else null)
                Text("${o.votos}", fontFamily = f.cifras, color = if (o.yoVoto) f.sobreTinta1 else f.texto)
            }
            if (o.quien?.usuario == yo || d.lista.creador.usuario == yo) BotonIcono(Iconos.Cerrar, stringResource(R.string.listas_comunes_quitar)) {
                alcance.launch { if (vm.red.quitarDeListaComun(id, o.id)) recargar() }
            }
        }
    }
    Spacer(Modifier.height(40.dp))
    // Agregar de tu cajón: buscar por el título entre lo tuyo.
    if (agregando) ModalBottomSheet({ agregando = false }, containerColor = f.fondo) {
        var q by remember { mutableStateOf("") }
        val ya = d.obras.map { texto(it.datos, "fuente").ifBlank { texto(it.datos, "titulo") } }.toSet()
        val lista = e.vivas.filter { it.tipo != io.github.fogonapps.cajon.datos.Tipos.ALBUM && (q.isBlank() || io.github.fogonapps.cajon.datos.Logica.tituloLimpio(it.titulo).contains(io.github.fogonapps.cajon.datos.Logica.tituloLimpio(q))) }
            .sortedWith(compareByDescending<io.github.fogonapps.cajon.datos.Obra> { it.estado == io.github.fogonapps.cajon.datos.Estados.QUIERO }.thenByDescending { it.agregada }).take(40)
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(q, { q = it.take(60) }, Modifier.fillMaxWidth(), singleLine = true, shape = forma(18.dp),
                placeholder = { Text(stringResource(R.string.listas_comunes_buscar)) })
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(lista.size) { i ->
                    val ob = lista[i]
                    val esta = (ob.fuente.ifBlank { ob.titulo }) in ya
                    Row(Modifier.fillMaxWidth().clip(forma(12.dp)).clickable(enabled = !esta) {
                        alcance.launch { if (vm.red.agregarAListaComun(id, io.github.fogonapps.cajon.RedSocial.obraBreve(ob))) { agregando = false; recargar() } }
                    }.padding(6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Portada(ob.portada, ob.tipo, ob.titulo, Modifier.width(40.dp), esquina = 6.dp, conTitulo = false)
                        Text(ob.titulo, style = MaterialTheme.typography.bodyMedium, color = if (esta) f.textoSuave else f.texto, maxLines = 2, modifier = Modifier.weight(1f))
                        if (esta) IconoRiso(Iconos.Chulo, null, tamano = 16.dp, tinta = f.textoSuave)
                    }
                }
            }
        }
    }
    // Sumar amigos que aún no están.
    if (sumando) ModalBottomSheet({ sumando = false }, containerColor = f.fondo) {
        val amigos by vm.red.amigos.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { vm.red.cargarAmigos() }
        val fuera = amigos.filter { a -> d.lista.miembros.none { it.usuario == a.usuario } }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.listas_comunes_sumar), style = MaterialTheme.typography.titleMedium, color = f.texto)
            if (fuera.isEmpty()) Pista(stringResource(R.string.listas_comunes_sin_amigos))
            fuera.forEach { a -> FilaPersona(a, { alcance.launch { if (vm.red.sumarAListaComun(id, a.usuario)) { sumando = false; recargar() } } }) }
        }
    }
}

/** En «Agregar a una lista»: tus listas en común (con cuenta). */
@Composable
internal fun ListasComunesParaAgregar(vm: CajonViewModel, o: io.github.fogonapps.cajon.datos.Obra) {
    val f = FogonTema.actual
    val yo by vm.red.usuario.collectAsStateWithLifecycle()
    if (yo == null) return
    val alcance = rememberCoroutineScope()
    var listas by remember { mutableStateOf<List<ListaComunSocial>?>(null) }
    var agregadas by remember { mutableStateOf(setOf<Long>()) }
    LaunchedEffect(Unit) { listas = vm.red.listasComunes() }
    Seccion(stringResource(R.string.listas_comunes))
    listas.orEmpty().forEach { l ->
        val ya = l.id in agregadas
        FilaVidrio({ if (!ya) alcance.launch { if (vm.red.agregarAListaComun(l.id, io.github.fogonapps.cajon.RedSocial.obraBreve(o))) agregadas = agregadas + l.id } }) {
            Ficha(if (ya) f.tinta1 else f.linea, if (ya) Iconos.Chulo else Iconos.Gente)
            Text(l.nombre, style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.weight(1f))
            Text("${l.obras + if (ya) 1 else 0}", fontFamily = f.cifras, color = f.textoSuave)
        }
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        BotonSecundario(stringResource(R.string.listas_comunes_ver), { vm.abrir(Pantalla.ListasComunes) }, Modifier.fillMaxWidth())
    }
}

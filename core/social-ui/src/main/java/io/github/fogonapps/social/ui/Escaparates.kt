package io.github.fogonapps.social.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.network.httpHeaders
import io.github.fogonapps.identidad.*
import io.github.fogonapps.perfil.*
import io.github.fogonapps.perfil.R as RP
import io.github.fogonapps.social.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

/** Instantánea preparada localmente; nada se publica hasta confirmar. */
fun escaparateElementos(tipo: String, elementos: List<ElementoEscaparate>) = EscaparateSocial(tipo, buildJsonObject {
    put("elementos", JsonArray(elementos.map { e -> buildJsonObject {
        put("titulo", e.titulo.take(500)); put("texto", e.texto.take(300))
        put("portada", e.portada.takeIf { it.startsWith("https://") }.orEmpty().take(500))
    } }))
})
fun escaparateNumero(tipo: String, numero: Number, etiqueta: String) = EscaparateSocial(tipo, buildJsonObject {
    put("numero", numero); put("etiqueta", etiqueta.take(80))
})
fun escaparateInsignias(ids: List<String>) = EscaparateSocial("insignias", buildJsonObject { put("insignias", JsonArray(ids.distinct().map(::JsonPrimitive))) })

internal fun EscaparateSocial.paraVista(): EscaparateVista {
    fun texto(d: JsonObject, clave: String) = (d[clave] as? JsonPrimitive)?.content.orEmpty()
    return EscaparateVista(tipo, texto = texto(datos, "texto"),
        insignias = (datos["insignias"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content },
        numero = texto(datos, "numero"), etiqueta = texto(datos, "etiqueta"),
        fotos = (datos["fotos"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { f -> texto(f, "nombre") } },
        elementos = (datos["elementos"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { e -> ElementoEscaparate(texto(e, "titulo"), texto(e, "texto"), texto(e, "portada")) } })
}

@Composable
private fun ImagenEscaparate(vm: InterfazSocial, url: String, modifier: Modifier) {
    val contexto = LocalContext.current
    val cabeceras = vm.cabeceras()
    val solicitud = remember(url, cabeceras) {
        if (url.startsWith("https://")) url else coil3.request.ImageRequest.Builder(contexto)
            .data(ClienteSocial.imagen(url))
            .httpHeaders(coil3.network.NetworkHeaders.Builder().apply { cabeceras.forEach { (clave, valor) -> set(clave, valor) } }.build())
            .memoryCachePolicy(coil3.request.CachePolicy.DISABLED).diskCachePolicy(coil3.request.CachePolicy.DISABLED).build()
    }
    AsyncImage(solicitud, stringResource(RP.string.escaparate_galeria), modifier,
        contentScale = ContentScale.Crop)
}

@Composable
fun EscaparatesEnPerfil(vm: InterfazSocial, usuario: String) {
    val yo by vm.red.usuario.collectAsStateWithLifecycle()
    val revision by vm.red.revisionEscaparates.collectAsStateWithLifecycle()
    var lista by remember(usuario, yo) { mutableStateOf(emptyList<EscaparateSocial>()) }
    LaunchedEffect(usuario, yo, revision) {
        lista = emptyList()
        try { lista = vm.red.leerEscaparates(usuario) } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Compatible con servidores anteriores; no modifica el perfil existente. */ }
    }
    var editar by remember(usuario, yo) { mutableStateOf(false) }
    Escaparates(lista.map { it.paraVista() }, alTocar = if (usuario == yo) ({ editar = true }) else null) { url, m -> ImagenEscaparate(vm, url, m) }
    if (editar) EdicionEscaparates(vm, usuario, emptyList()) { editar = false }
}

@Composable
fun BotonEditorEscaparates(vm: InterfazSocial, candidatos: List<EscaparateSocial> = emptyList()) {
    val yo by vm.red.usuario.collectAsStateWithLifecycle()
    if (yo == null) return
    var lista by remember(yo) { mutableStateOf<List<EscaparateSocial>?>(null) }
    var cupo by remember(yo) { mutableStateOf<CupoEscaparates?>(null) }
    var fotos by remember(yo) { mutableStateOf(emptyList<FotoEscaparate>()) }
    var selector by remember(yo) { mutableStateOf(false) }
    var tienda by remember(yo) { mutableStateOf(false) }
    var ocupado by remember(yo) { mutableStateOf(false) }
    var error by remember(yo) { mutableStateOf(false) }
    var recarga by remember(yo) { mutableIntStateOf(0) }
    val revision by vm.red.revisionEscaparates.collectAsStateWithLifecycle()
    val alcance = rememberCoroutineScope()
    LaunchedEffect(yo, recarga, revision) {
        lista = null; cupo = null; error = false
        try {
            lista = vm.red.leerEscaparates(yo!!)
            cupo = vm.red.cupoEscaparates()
            fotos = vm.red.fotosEscaparates()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().tarjetaFogon()
        .clickable(enabled = !ocupado) {
            if (error) recarga++
            else if (lista != null && cupo != null) {
                if (lista!!.size < cupo!!.total) selector = true else tienda = true
            }
        }.padding(20.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        IconoRiso(IconosSociales.Mas, null, tamano = 40.dp)
        Text(stringResource(RP.string.escaparates_agregar), color = FogonTema.actual.texto)
        if (ocupado || cupo == null && !error) CircularProgressIndicator()
        if (error) Text(stringResource(RP.string.escaparates_error), color = FogonTema.actual.texto)
    }
    if (tienda) TiendaTemas(vm, escaparatesExtra = true) { tienda = false; recarga++ }
    if (selector) SelectorEscaparate(vm, candidatos + listOfNotNull(cupo?.insignias?.takeIf { it.isNotEmpty() }?.let(::escaparateInsignias)).filter { extra -> candidatos.none { it.tipo == extra.tipo } }, fotos, { e ->
        selector = false; ocupado = true; error = false
        alcance.launch {
            try { vm.red.guardarEscaparates(lista.orEmpty() + e); vm.red.avisarCambioEscaparates() }
            catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
            finally { ocupado = false }
        }
    }) { selector = false }
}

@Composable
private fun EdicionEscaparates(vm: InterfazSocial, usuario: String, candidatos: List<EscaparateSocial>, cerrar: () -> Unit) {
    var lista by remember { mutableStateOf<List<EscaparateSocial>?>(null) }
    var cupo by remember { mutableStateOf(CupoEscaparates()) }
    var fotos by remember { mutableStateOf(emptyList<FotoEscaparate>()) }
    var error by remember { mutableStateOf(false) }
    var ocupado by remember { mutableStateOf(false) }
    var tienda by remember { mutableStateOf(false) }
    var agregar by remember { mutableStateOf(false) }
    var recarga by remember { mutableIntStateOf(0) }
    val alcance = rememberCoroutineScope()
    LaunchedEffect(recarga) {
        error = false
        try {
            val bloques = vm.red.leerEscaparates(usuario)
            cupo = vm.red.cupoEscaparates()
            fotos = vm.red.fotosEscaparates()
            if (lista == null) lista = bloques
        } catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
    }
    Dialog(cerrar, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().fondoFogon(FogonTema.actual).systemBarsPadding()) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(RP.string.escaparates_titulo), Modifier.weight(1f), color = FogonTema.actual.texto)
                TextButton(cerrar, enabled = !ocupado) { Text(stringResource(RP.string.escaparates_cancelar)) }
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (error) {
                    Text(stringResource(RP.string.escaparates_error), color = FogonTema.actual.texto)
                    TextButton({ recarga++ }) { Text(stringResource(RP.string.escaparates_reintentar)) }
                }
                val actual = lista
                if (actual == null) Text(stringResource(RP.string.escaparates_cargando), color = FogonTema.actual.texto)
                else EditorEscaparates(actual.map { it.paraVista() }, cupo.total, ocupado,
                    mover = { de, a -> lista = actual.toMutableList().apply { add(a, removeAt(de)) } },
                    quitar = { i -> lista = actual.filterIndexed { indice, _ -> indice != i } },
                    agregar = { agregar = true },
                    guardar = {
                        ocupado = true; error = false
                        alcance.launch {
                            try { vm.red.guardarEscaparates(actual); vm.red.avisarCambioEscaparates(); cerrar() }
                            catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
                            finally { ocupado = false }
                        }
                    }, tienda = { tienda = true }, contenido = {}, imagen = { url, m -> ImagenEscaparate(vm, url, m) })
            }
        }
    }
    if (tienda) TiendaTemas(vm, escaparatesExtra = true) { tienda = false; recarga++ }
    if (agregar) SelectorEscaparate(vm, candidatos + listOfNotNull(cupo.insignias.takeIf { it.isNotEmpty() }?.let(::escaparateInsignias)).filter { extra -> candidatos.none { it.tipo == extra.tipo } }, fotos, { e ->
        if (lista.orEmpty().size < cupo.total) lista = lista.orEmpty() + e
        agregar = false
    }) { agregar = false }
}

@Composable
private fun SelectorEscaparate(vm: InterfazSocial, candidatos: List<EscaparateSocial>, fotos: List<FotoEscaparate>, agregar: (EscaparateSocial) -> Unit, cerrar: () -> Unit) {
    var tipo by remember { mutableStateOf("") }
    var texto by remember { mutableStateOf("") }
    var numero by remember { mutableStateOf("") }
    var etiqueta by remember { mutableStateOf("") }
    var elegidas by remember { mutableStateOf(emptyList<FotoEscaparate>()) }
    var subiendo by remember { mutableStateOf(false) }
    var errorSubida by remember { mutableStateOf(false) }
    var subidas by remember { mutableStateOf(emptyList<FotoEscaparate>()) }
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && elegidas.size < 6) {
            subiendo = true; errorSubida = false
            alcance.launch {
                try {
                    val jpeg = prepararImagen(contexto, uri) ?: error("imagen")
                    val foto = FotoEscaparate(nombre = vm.red.subirImagenEscaparate(jpeg))
                    subidas = subidas + foto
                    elegidas = elegidas + foto
                } catch (e: CancellationException) { throw e } catch (_: Exception) { errorSubida = true }
                finally { subiendo = false }
            }
        }
    }
    AlertDialog(onDismissRequest = { if (!subiendo) cerrar() }, title = { Text(stringResource(RP.string.escaparates_elegir)) }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tipo.isEmpty()) {
                listOf("texto", "numero", "galeria").forEach { t -> TextButton({ tipo = t }) { Text(nombreEscaparate(t)) } }
                candidatos.forEach { e -> TextButton({ agregar(e) }) {
                    Column { Text(nombreEscaparate(e.tipo)); val vista = e.paraVista(); vista.elementos.firstOrNull()?.let { Text(it.titulo) }; if (vista.numero.isNotEmpty()) Text(vista.numero + if (e.tipo == "alcancia:meta") "%" else "") }
                } }
            } else when (tipo) {
                "texto" -> OutlinedTextField(texto, { if (it.codePointCount(0, it.length) <= 300) texto = it }, label = { Text(nombreEscaparate(tipo)) })
                "numero" -> {
                    OutlinedTextField(numero, { numero = it }, label = { Text(stringResource(RP.string.escaparates_valor)) })
                    OutlinedTextField(etiqueta, { etiqueta = it.take(80) }, label = { Text(stringResource(RP.string.escaparates_etiqueta)) })
                }
                "galeria" -> {
                    Text(stringResource(RP.string.escaparates_fotos))
                    Text("${elegidas.size} / 6")
                    TextButton({ selector.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !subiendo && elegidas.size < 6) { Text(stringResource(RP.string.escaparates_subir_telefono)) }
                    if (subiendo) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(RP.string.escaparates_subiendo)) }
                    if (errorSubida) Text(stringResource(RP.string.escaparates_error_subida), color = FogonTema.actual.texto)
                    (subidas + fotos).chunked(3).forEach { fila -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        fila.forEach { foto -> Column(Modifier.weight(1f).clickable(enabled = !subiendo) {
                            elegidas = if (foto in elegidas) elegidas - foto else if (elegidas.size < 6) elegidas + foto else elegidas
                        }) {
                            ImagenEscaparate(vm, foto.nombre, Modifier.fillMaxWidth().aspectRatio(1f))
                            Checkbox(foto in elegidas, { si -> elegidas = if (!si) elegidas - foto else if (elegidas.size < 6) elegidas + foto else elegidas }, enabled = !subiendo)
                        } }
                        repeat(3 - fila.size) { Spacer(Modifier.weight(1f)) }
                    } }
                }
            }
        }
    }, confirmButton = {
        if (tipo.isNotEmpty()) TextButton({
            agregar(when (tipo) {
                "texto" -> EscaparateSocial(tipo, buildJsonObject { put("texto", texto) })
                "numero" -> escaparateNumero(tipo, numero.toDouble(), etiqueta)
                else -> EscaparateSocial(tipo, buildJsonObject { put("fotos", JsonArray(elegidas.map { buildJsonObject { it.publicacion?.let { id -> put("publicacion", id) }; put("nombre", it.nombre) } })) })
            })
        }, enabled = !subiendo && (tipo != "numero" || numero.toDoubleOrNull()?.let { it.isFinite() && it >= 0 && it <= 999999999 } == true)) { Text(stringResource(RP.string.escaparates_confirmar)) }
    }, dismissButton = { TextButton(cerrar, enabled = !subiendo) { Text(stringResource(RP.string.escaparates_cancelar)) } })
}

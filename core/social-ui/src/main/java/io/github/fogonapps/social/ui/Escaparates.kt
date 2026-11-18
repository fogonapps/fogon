package io.github.fogonapps.social.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
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
import kotlinx.serialization.builtins.ListSerializer
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

/** Guarda un borrador de repisas o fotos en el estado de la pantalla (sobrevive a girar el teléfono o cambiar la letra). */
private val listaEscaparates = ListSerializer(EscaparateSocial.serializer())
private val listaFotos = ListSerializer(FotoEscaparate.serializer())
internal val guardaEscaparates = Saver<List<EscaparateSocial>?, String>(
    save = { lista -> lista?.let { Json.encodeToString(listaEscaparates, it) } },
    restore = { texto -> runCatching { Json.decodeFromString(listaEscaparates, texto) }.getOrNull() },
)
internal val guardaFotos = Saver<List<FotoEscaparate>, String>(
    save = { Json.encodeToString(listaFotos, it) },
    restore = { texto -> runCatching { Json.decodeFromString(listaFotos, texto) }.getOrNull() },
)

/** Las repisas que se escriben a mano y por eso se pueden editar (las demás se cambian por otra). */
internal val TIPOS_EDITABLES = setOf("texto", "numero", "galeria")

/** Las fotos guardadas en una repisa de galería, para volver a editarlas. */
internal fun fotosDe(e: EscaparateSocial): List<FotoEscaparate> = (e.datos["fotos"] as? JsonArray).orEmpty().mapNotNull { f ->
    val o = f as? JsonObject ?: return@mapNotNull null
    val nombre = (o["nombre"] as? JsonPrimitive)?.content ?: return@mapNotNull null
    FotoEscaparate((o["publicacion"] as? JsonPrimitive)?.content?.toLongOrNull(), nombre)
}

/** Las repisas que reflejan datos que cambian solos; las demás son lo que la persona eligió y no se tocan. */
internal val REPISAS_VIVAS = setOf("cajon:albumes", "forzudo:records", "forzudo:racha", "cucha:racha")

internal fun actualizarRepisasVivas(lista: List<EscaparateSocial>, candidatos: List<EscaparateSocial>): List<EscaparateSocial> =
    lista.map { r -> if (r.tipo in REPISAS_VIVAS) candidatos.firstOrNull { it.tipo == r.tipo } ?: r else r }

internal fun List<EscaparateSocial>.moverRepisa(de: Int, a: Int) =
    toMutableList().apply { add(a, removeAt(de)) }

internal fun List<EscaparateSocial>.quitarRepisa(indice: Int) =
    toMutableList().apply { removeAt(indice) }

internal fun List<EscaparateSocial>.cambiarRepisa(indice: Int, repisa: EscaparateSocial) =
    toMutableList().apply { this[indice] = repisa }

private fun conInsignias(candidatos: List<EscaparateSocial>, cupo: CupoEscaparates?): List<EscaparateSocial> {
    val insignias = cupo?.insignias.orEmpty()
    return if (insignias.isNotEmpty() && candidatos.none { it.tipo == "insignias" }) candidatos + escaparateInsignias(insignias)
    else candidatos
}

@Composable
fun EscaparatesEnPerfil(
    vm: InterfazSocial,
    usuario: String,
    candidatos: List<EscaparateSocial>? = null,
    mostrarAgregar: Boolean = false,
) {
    val yo by vm.red.usuario.collectAsStateWithLifecycle()
    val revision by vm.red.revisionEscaparates.collectAsStateWithLifecycle()
    val propio = usuario == yo
    var lista by remember(usuario, yo) { mutableStateOf(emptyList<EscaparateSocial>()) }
    var listaVigente by remember(usuario, yo, revision) { mutableStateOf(false) }
    var editar by rememberSaveable(usuario, yo) { mutableStateOf(false) }
    var cambiar by rememberSaveable(usuario, yo, revision) { mutableStateOf<Int?>(null) }
    var editarRepisa by rememberSaveable(usuario, yo, revision) { mutableStateOf(false) }
    var ocupado by remember(usuario, yo) { mutableStateOf(false) }
    var error by remember(usuario, yo) { mutableStateOf(false) }
    val alcance = rememberCoroutineScope()
    LaunchedEffect(usuario, yo, revision) {
        try {
            lista = vm.red.leerEscaparates(usuario)
            listaVigente = true
        } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Compatible con servidores anteriores. */ }
    }

    fun guardar(nueva: List<EscaparateSocial>) {
        if (!propio || !listaVigente || ocupado) return
        ocupado = true
        error = false
        alcance.launch {
            try {
                vm.red.guardarEscaparates(nueva)
                lista = nueva
                listaVigente = false
                vm.red.avisarCambioEscaparates()
            } catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
            finally { ocupado = false }
        }
    }

    // Las repisas «vivas» (lo que escuchas, la racha, las marcas, la meta) se ponen al día solas en tu perfil.
    LaunchedEffect(lista, listaVigente, candidatos) {
        if (!propio || !listaVigente || candidatos == null) return@LaunchedEffect
        val nueva = actualizarRepisasVivas(lista, candidatos)
        if (nueva != lista) guardar(nueva)
    }

    val quitar = stringResource(RP.string.escaparates_quitar)
    val subir = stringResource(RP.string.escaparates_subir)
    val bajar = stringResource(RP.string.escaparates_bajar)
    val reemplazar = stringResource(RP.string.escaparates_cambiar)
    val editarTexto = stringResource(RP.string.escaparates_editar)
    val mostradas = lista
    Escaparates(
        mostradas.map { it.paraVista() },
        alTocar = if (propio && !ocupado && listaVigente) ({ editar = true }) else null,
        acciones = { i ->
            if (!propio || candidatos == null || ocupado || !listaVigente) emptyList()
            else buildList {
                if (mostradas[i].tipo in TIPOS_EDITABLES) add(CustomAccessibilityAction(editarTexto) { editarRepisa = true; cambiar = i; true })
                add(CustomAccessibilityAction(quitar) { guardar(mostradas.quitarRepisa(i)); true })
                if (i > 0) add(CustomAccessibilityAction(subir) { guardar(mostradas.moverRepisa(i, i - 1)); true })
                if (i < mostradas.lastIndex) add(CustomAccessibilityAction(bajar) { guardar(mostradas.moverRepisa(i, i + 1)); true })
                add(CustomAccessibilityAction(reemplazar) { editarRepisa = false; cambiar = i; true })
            }
        },
    ) { url, m -> ImagenEscaparate(vm, url, m) }
    if (propio && error) Text(
        stringResource(RP.string.escaparates_error), Modifier.padding(horizontal = 16.dp), color = FogonTema.actual.texto,
    )
    if (propio && mostrarAgregar) BotonEditorEscaparates(
        vm, candidatos.orEmpty(), habilitado = !ocupado,
        alGuardar = { ocupado = it },
    )
    if (propio && editar) EdicionEscaparates(vm, candidatos.orEmpty(), lista) { editar = false }
    cambiar?.takeIf { propio && listaVigente && it in lista.indices }?.let { indice ->
        var opciones by remember(indice) { mutableStateOf<Pair<CupoEscaparates, List<FotoEscaparate>>?>(null) }
        var fallo by remember(indice) { mutableStateOf(false) }
        LaunchedEffect(indice) {
            try {
                opciones = vm.red.cupoEscaparates() to vm.red.fotosEscaparates()
            } catch (e: CancellationException) { throw e } catch (_: Exception) { fallo = true }
        }
        val datos = opciones
        if (datos != null) {
            SelectorEscaparate(vm, conInsignias(candidatos.orEmpty(), datos.first), datos.second, { nueva ->
                cambiar = null
                guardar(lista.cambiarRepisa(indice, nueva))
            }, inicial = if (editarRepisa) lista[indice] else null) { cambiar = null }
        } else {
            AlertDialog(
                onDismissRequest = { cambiar = null },
                text = { Text(stringResource(if (fallo) RP.string.escaparates_error else RP.string.escaparates_cargando)) },
                confirmButton = {
                    TextButton({ cambiar = null }) { Text(stringResource(RP.string.escaparates_cancelar)) }
                },
            )
        }
    }
}

@Composable
fun BotonEditorEscaparates(
    vm: InterfazSocial, candidatos: List<EscaparateSocial> = emptyList(),
    habilitado: Boolean = true, alGuardar: (Boolean) -> Unit = {},
) {
    val yo by vm.red.usuario.collectAsStateWithLifecycle()
    if (yo == null) return
    var lista by remember(yo) { mutableStateOf<List<EscaparateSocial>?>(null) }
    var cupo by remember(yo) { mutableStateOf<CupoEscaparates?>(null) }
    var fotos by remember(yo) { mutableStateOf(emptyList<FotoEscaparate>()) }
    var selector by rememberSaveable(yo) { mutableStateOf(false) }
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
        .clickable(enabled = habilitado && !ocupado) {
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
    if (selector) SelectorEscaparate(vm, conInsignias(candidatos, cupo), fotos, { e ->
        selector = false; ocupado = true; error = false
        alGuardar(true)
        alcance.launch {
            try { vm.red.guardarEscaparates(lista.orEmpty() + e); vm.red.avisarCambioEscaparates() }
            catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
            finally { ocupado = false; alGuardar(false) }
        }
    }) { selector = false }
}

@Composable
private fun EdicionEscaparates(
    vm: InterfazSocial, candidatos: List<EscaparateSocial>, inicial: List<EscaparateSocial>, cerrar: () -> Unit,
) {
    var lista by rememberSaveable(stateSaver = guardaEscaparates) { mutableStateOf<List<EscaparateSocial>?>(inicial) }
    var cupo by remember { mutableStateOf<CupoEscaparates?>(null) }
    var fotos by remember { mutableStateOf(emptyList<FotoEscaparate>()) }
    var error by remember { mutableStateOf(false) }
    var ocupado by remember { mutableStateOf(false) }
    var tienda by remember { mutableStateOf(false) }
    var agregar by rememberSaveable { mutableStateOf(false) }
    var recarga by remember { mutableIntStateOf(0) }
    val alcance = rememberCoroutineScope()
    LaunchedEffect(recarga) {
        error = false
        try {
            val nuevoCupo = vm.red.cupoEscaparates()
            fotos = vm.red.fotosEscaparates()
            cupo = nuevoCupo
        } catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
    }
    Dialog({ if (!ocupado) cerrar() }, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().fondoFogon(FogonTema.actual).systemBarsPadding()) {
            Row(Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(RP.string.escaparates_titulo), Modifier.weight(1f), color = FogonTema.actual.texto)
                TextButton(cerrar, enabled = !ocupado) { Text(stringResource(RP.string.escaparates_cancelar)) }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (error) {
                    Text(stringResource(RP.string.escaparates_error), color = FogonTema.actual.texto)
                    TextButton({ recarga++ }) { Text(stringResource(RP.string.escaparates_reintentar)) }
                }
                val actual = lista.orEmpty()
                EditorEscaparates(actual.map { it.paraVista() }, cupo?.total, ocupado,
                    mover = { de, a -> lista = actual.moverRepisa(de, a) },
                    quitar = { i -> lista = actual.quitarRepisa(i) },
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
    if (agregar && cupo != null) SelectorEscaparate(vm, conInsignias(candidatos, cupo), fotos, { e ->
        if (lista.orEmpty().size < (cupo?.total ?: 0)) lista = lista.orEmpty() + e
        agregar = false
    }) { agregar = false }
}

private val formatoNumeroRepisa = Regex("(?:[0-9]+(?:[.,][0-9]*)?|[.,][0-9]+)")
private val maximoNumeroRepisa = 999_999_999.toBigDecimal()

internal fun limpiarNumeroRepisa(texto: String): String? {
    if (texto.isEmpty()) return texto
    if (!formatoNumeroRepisa.matches(texto)) return null
    val normalizado = texto.replace(',', '.')
    return normalizado.takeIf { it.toBigDecimal() <= maximoNumeroRepisa }
}

@Composable
private fun SelectorEscaparate(vm: InterfazSocial, candidatos: List<EscaparateSocial>, fotos: List<FotoEscaparate>, agregar: (EscaparateSocial) -> Unit, inicial: EscaparateSocial? = null, cerrar: () -> Unit) {
    // Con «inicial», se edita esa repisa: abre directo en su tipo con lo que ya tiene.
    val editable = inicial?.takeIf { it.tipo in TIPOS_EDITABLES }
    val inicialVista = editable?.paraVista()
    var tipo by rememberSaveable { mutableStateOf(editable?.tipo.orEmpty()) }
    var texto by rememberSaveable { mutableStateOf(inicialVista?.texto.orEmpty()) }
    var numero by rememberSaveable { mutableStateOf(inicialVista?.numero.orEmpty()) }
    var etiqueta by rememberSaveable { mutableStateOf(inicialVista?.etiqueta.orEmpty()) }
    var elegidas by rememberSaveable(stateSaver = guardaFotos) { mutableStateOf(editable?.let(::fotosDe).orEmpty()) }
    var subiendo by remember { mutableStateOf(false) }
    var errorSubida by remember { mutableStateOf(false) }
    var subidas by rememberSaveable(stateSaver = guardaFotos) { mutableStateOf(emptyList<FotoEscaparate>()) }
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
                    Column {
                        Text(nombreEscaparate(e.tipo))
                        val vista = e.paraVista()
                        vista.elementos.firstOrNull()?.let { Text(it.titulo) }
                        if (vista.numero.isNotEmpty()) Text(when (e.tipo) {
                            "numero" -> numeroLocalizado(vista.numero)
                            "alcancia:meta" -> vista.numero + "%"
                            else -> vista.numero
                        })
                    }
                } }
            } else when (tipo) {
                "texto" -> OutlinedTextField(texto, { if (it.codePointCount(0, it.length) <= 300) texto = it }, label = { Text(nombreEscaparate(tipo)) })
                "numero" -> {
                    OutlinedTextField(
                        numero,
                        { cambio -> limpiarNumeroRepisa(cambio)?.let { numero = it } },
                        label = { Text(stringResource(RP.string.escaparates_valor)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
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
        }, enabled = !subiendo && (tipo != "numero" || numero.isNotEmpty())) { Text(stringResource(RP.string.escaparates_confirmar)) }
    }, dismissButton = { TextButton(cerrar, enabled = !subiendo) { Text(stringResource(RP.string.escaparates_cancelar)) } })
}

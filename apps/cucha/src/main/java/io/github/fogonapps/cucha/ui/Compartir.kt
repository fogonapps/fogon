package io.github.fogonapps.cucha.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.cucha.CuchaViewModel
import io.github.fogonapps.cucha.R
import io.github.fogonapps.cucha.datos.*
import io.github.fogonapps.identidad.*
import io.github.fogonapps.social.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/*
 * Los compositores de La Cucha (CU-01), como «Compartir» de El Forzudo: una instantánea elegida, con clave estable
 * y «Actualizar publicación» explícito. Plato y receta: la nutrición va apagada por defecto (D-031).
 * Restaurante: lugar a mano con sugerencias y, solo al tocar, el GPS con la dirección editable (D-031).
 */

/** Fotos (hasta 10) o un vídeo de 30 s, subidos al elegirlos, como en El Forzudo. */
private class Multimedia(fotos: List<String>, video: String, poster: String) {
    var fotos by mutableStateOf(fotos)
    var video by mutableStateOf(video)
    var poster by mutableStateOf(poster)
    var preparando by mutableStateOf(false)
    var error by mutableStateOf(false)
}

@Composable
private fun rememberMultimedia(anterior: PublicacionSubida?) = remember(anterior?.clave) {
    Multimedia(anterior?.datos?.get("imagenes")?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
        anterior?.datos?.get("video")?.jsonPrimitive?.content.orEmpty(), anterior?.datos?.get("poster")?.jsonPrimitive?.content.orEmpty())
}

@Composable
private fun ControlesMultimedia(vm: CuchaViewModel, m: Multimedia, ocupado: Boolean) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val selectorFotos = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        alcance.launch {
            m.preparando = true; m.error = false
            try {
                m.fotos = m.fotos + uris.take(10 - m.fotos.size).map { uri ->
                    val imagen = requireNotNull(io.github.fogonapps.perfil.imagenPerfilDesde(contexto, uri))
                    vm.red.subirFotoPublicacion(android.util.Base64.decode(imagen, android.util.Base64.DEFAULT))
                }
            } catch (x: kotlinx.coroutines.CancellationException) { throw x }
            catch (_: Exception) { m.error = true }
            finally { m.preparando = false }
        }
    }
    val selectorVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) alcance.launch {
            m.preparando = true; m.error = false
            var preparado: VideoPreparado? = null
            try {
                preparado = prepararVideo(contexto, uri, {}, {})
                m.video = vm.red.subirVideoPublicacion(preparado.archivo)
                m.poster = vm.red.subirFotoPublicacion(preparado.poster)
            } catch (x: kotlinx.coroutines.CancellationException) { throw x }
            catch (_: Exception) { m.video = ""; m.poster = ""; m.error = true }
            finally { preparado?.archivo?.delete(); m.preparando = false }
        }
    }
    val libre = !ocupado && !m.preparando
    if (libre && m.video.isBlank() && m.fotos.size < 10) PildoraSocial(stringResource(R.string.red_agregar_foto), IconosFogon.Imagen) { selectorFotos.launch("image/*") }
    if (libre && m.fotos.isEmpty() && m.video.isBlank()) PildoraSocial(stringResource(R.string.red_agregar_video), IconosFogon.Imagen) { selectorVideo.launch("video/*") }
    m.fotos.forEachIndexed { i, foto -> BotonSecundario(stringResource(R.string.red_quitar_foto, i + 1), { m.fotos = m.fotos - foto }, habilitado = libre) }
    if (m.video.isNotBlank()) BotonSecundario(stringResource(R.string.red_quitar_video), { m.video = ""; m.poster = "" }, habilitado = libre)
}

/** La nota de 1 a 5; [opcional] deja «Sin nota». */
@Composable
private fun ElegirNota(nota: Int?, opcional: Boolean, habilitado: Boolean, onNota: (Int?) -> Unit) {
    Text(stringResource(R.string.red_nota), style = MaterialTheme.typography.labelLarge, color = FogonTema.actual.textoSuave)
    val opciones = (if (opcional) listOf<Pair<Int?, String>>(null to stringResource(R.string.red_quitar_nota)) else emptyList()) +
        (1..5).map { it to "★".repeat(it) }
    Selector(opciones, nota, { if (habilitado) onNota(it) })
}

/** El lugar elegido: uno que ya existe, o uno nuevo que se crea al publicar. */
private sealed interface LugarElegido {
    data class Existente(val lugar: Lugar) : LugarElegido
    data class Nuevo(val datos: LugarNuevo) : LugarElegido
}

private fun lugarComoJson(l: LugarElegido): JsonObject = when (l) {
    is LugarElegido.Existente -> JsonSocial.encodeToJsonElement(Lugar.serializer(), l.lugar).jsonObject
    is LugarElegido.Nuevo -> JsonSocial.encodeToJsonElement(Lugar.serializer(), Lugar(0, l.datos.nombre, l.datos.ciudad, l.datos.direccion, l.datos.lat, l.datos.lon)).jsonObject
}

/** Lo que se recuerda del lugar de una publicación propia, para volver a editarla. */
private fun lugarRecordado(vm: CuchaViewModel, clave: String): Lugar? = vm.datoRed("lugar.$clave")?.let {
    runCatching { JsonSocial.decodeFromString(Lugar.serializer(), it) }.getOrNull()
}

/** Pide el permiso de ubicación solo al tocar, toma una posición y propone la dirección con el Geocoder del teléfono. */
@Composable
private fun rememberUbicacion(onLista: (Double, Double, String?, String?) -> Unit, onFallo: () -> Unit): Pair<Boolean, () -> Unit> {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    var buscando by remember { mutableStateOf(false) }
    fun tomar() = alcance.launch {
        buscando = true
        try {
            val lm = contexto.getSystemService(android.location.LocationManager::class.java)
            val fina = ContextCompat.checkSelfPermission(contexto, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val proveedor = listOf(if (fina) android.location.LocationManager.GPS_PROVIDER else null, android.location.LocationManager.NETWORK_PROVIDER)
                .filterNotNull().firstOrNull { lm.isProviderEnabled(it) }
            val posicion: android.location.Location? = proveedor?.let { p ->
                kotlinx.coroutines.withTimeoutOrNull(20_000) {
                    suspendCancellableCoroutine { c ->
                        val senal = androidx.core.os.CancellationSignal()
                        c.invokeOnCancellation { senal.cancel() }
                        @Suppress("MissingPermission")
                        androidx.core.location.LocationManagerCompat.getCurrentLocation(lm, p, senal, ContextCompat.getMainExecutor(contexto)) { c.resume(it) { _, _, _ -> } }
                    }
                }
            }
            if (posicion == null) { onFallo(); return@launch }
            val direccion = withContext(Dispatchers.IO) {
                if (!android.location.Geocoder.isPresent()) null
                else runCatching {
                    @Suppress("DEPRECATION")
                    android.location.Geocoder(contexto).getFromLocation(posicion.latitude, posicion.longitude, 1)?.firstOrNull()
                }.getOrNull()
            }
            val calle = direccion?.let { listOfNotNull(it.thoroughfare?.let { t -> listOfNotNull(t, it.subThoroughfare).joinToString(" ") }, it.subLocality).joinToString(", ") }
            onLista(posicion.latitude, posicion.longitude, calle?.takeIf { it.isNotBlank() }, direccion?.locality)
        } catch (x: kotlinx.coroutines.CancellationException) { throw x }
        catch (_: Exception) { onFallo() }
        finally { buscando = false }
    }
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) tomar() else onFallo()
    }
    return buscando to {
        val alguno = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(contexto, it) == PackageManager.PERMISSION_GRANTED }
        if (alguno) tomar() else permiso.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
}

/** Elegir o crear el restaurante: sugerencias de los que existen, dirección a mano y GPS solo al tocar. */
@Composable
private fun ElegirLugar(vm: CuchaViewModel, elegido: LugarElegido?, habilitado: Boolean, onElegir: (LugarElegido?) -> Unit) {
    val f = FogonTema.actual
    if (elegido != null) {
        val l = lugarComoJson(elegido)
        Column(Modifier.fillMaxWidth().tarjetaFogon().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(l["nombre"]?.jsonPrimitive?.content.orEmpty(), style = MaterialTheme.typography.titleSmall, color = f.texto)
            Text(listOfNotNull(l["ciudad"]?.jsonPrimitive?.content, l["direccion"]?.jsonPrimitive?.contentOrNull).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            val conUbicacion = l["lat"] != null
            Text(stringResource(if (conUbicacion) R.string.red_ubicacion_sale else R.string.red_ubicacion_no_sale),
                style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            if (elegido is LugarElegido.Nuevo && conUbicacion) BotonSecundario(stringResource(R.string.red_quitar_ubicacion),
                { onElegir(LugarElegido.Nuevo(elegido.datos.copy(lat = null, lon = null))) }, habilitado = habilitado)
            BotonSecundario(stringResource(R.string.red_cambiar_lugar), { onElegir(null) }, habilitado = habilitado)
        }
        return
    }
    var nombre by rememberSaveable { mutableStateOf("") }
    var ciudad by rememberSaveable { mutableStateOf("") }
    var direccion by rememberSaveable { mutableStateOf("") }
    var lat by rememberSaveable { mutableStateOf<Double?>(null) }
    var lon by rememberSaveable { mutableStateOf<Double?>(null) }
    var fallo by remember { mutableStateOf(false) }
    var sugerencias by remember { mutableStateOf(emptyList<Lugar>()) }
    LaunchedEffect(nombre, ciudad) {
        sugerencias = emptyList()
        if (nombre.trim().length < 2) return@LaunchedEffect
        delay(400)
        sugerencias = runCatching { vm.red.buscarLugares(nombre.trim(), ciudad.trim()) }.getOrDefault(emptyList())
    }
    val (buscando, usarUbicacion) = rememberUbicacion({ la, lo, calle, localidad ->
        lat = la; lon = lo; fallo = false
        if (calle != null) direccion = calle.take(200)
        if (ciudad.isBlank() && localidad != null) ciudad = localidad.take(80)
    }, { fallo = true })
    OutlinedTextField(nombre, { nombre = it.take(120) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.red_lugar_nombre)) }, singleLine = true, enabled = habilitado)
    OutlinedTextField(ciudad, { ciudad = it.take(80) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.red_lugar_ciudad)) }, singleLine = true, enabled = habilitado)
    if (sugerencias.isNotEmpty()) {
        Text(stringResource(R.string.red_sugerencias), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
        sugerencias.take(8).forEach { s ->
            TextButton({ onElegir(LugarElegido.Existente(s)) }, Modifier.fillMaxWidth(), enabled = habilitado) {
                Text(listOfNotNull(s.nombre, s.ciudad, s.direccion).joinToString(" · "))
            }
        }
    }
    if (nombre.isNotBlank() && ciudad.isNotBlank()) {
        OutlinedTextField(direccion, { direccion = it.take(200) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.red_lugar_direccion)) }, enabled = habilitado)
        PildoraSocial(stringResource(if (buscando) R.string.red_buscando_ubicacion else R.string.red_usar_ubicacion), IconosFogon.Inicio) { if (!buscando && habilitado) usarUbicacion() }
        Text(stringResource(R.string.red_ubicacion_pista), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        if (fallo) Text(stringResource(R.string.red_ubicacion_no), style = MaterialTheme.typography.bodySmall, color = f.texto)
        // La vista previa de la ubicación: se ve que sale y se puede quitar antes de crear el lugar.
        if (lat != null) {
            Text(stringResource(R.string.red_ubicacion_sale), style = MaterialTheme.typography.bodySmall, color = f.texto)
            BotonSecundario(stringResource(R.string.red_quitar_ubicacion), { lat = null; lon = null }, habilitado = habilitado)
        }
        BotonSecundario(stringResource(R.string.red_lugar_nuevo, nombre.trim(), ciudad.trim()), {
            onElegir(LugarElegido.Nuevo(LugarNuevo(nombre.trim(), ciudad.trim(), direccion.trim().ifBlank { null }, lat, lon)))
        }, Modifier.fillMaxWidth(), habilitado = habilitado)
    }
}

/** Lo común de los tres compositores: texto, multimedia, visibilidad, vista previa y publicar o actualizar. */
@Composable
private fun PieCompositor(vm: CuchaViewModel, tipo: String, clave: String, anterior: PublicacionSubida?, m: Multimedia,
                          texto: String, onTexto: (String) -> Unit, seguidores: Boolean, onSeguidores: (Boolean) -> Unit,
                          lugar: LugarElegido?, datos: () -> JsonObject?) {
    val f = FogonTema.actual
    val ocupada by vm.red.publicando.collectAsStateWithLifecycle()
    val alcance = rememberCoroutineScope()
    var creando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val libre = !ocupada && !m.preparando && !creando
    OutlinedTextField(texto, { onTexto(it.take(2000)) }, Modifier.fillMaxWidth().height(140.dp),
        placeholder = { Text(stringResource(R.string.red_texto), color = f.textoSuave) },
        supportingText = { Text(stringResource(R.string.red_etiquetas_pista), color = f.textoSuave) }, enabled = libre)
    ControlesMultimedia(vm, m, !libre)
    OpcionSocial(stringResource(R.string.red_seguidores), seguidores, { if (libre) onSeguidores(it) })
    val base = runCatching { datos() }.getOrNull()
    val completo = base?.let { runCatching { conMultimedia(it, m.fotos, m.video, m.poster) }.getOrNull() }
    if (completo != null) {
        Seccion(stringResource(R.string.red_vista_previa))
        Column(Modifier.fillMaxWidth().tarjetaFogon().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val vista = JsonObject(completo + (lugar?.let { mapOf("lugarDetalle" to lugarComoJson(it)) } ?: emptyMap()))
            CuerpoCucha(vm, PublicacionSocial(0, PersonaSocial(""), tipo, 0, if (seguidores) "amigos" else "publica", vista))
            if (texto.isNotBlank()) Text(texto, color = f.texto)
        }
    }
    if (m.preparando || ocupada || creando) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (error || m.error || (completo != null && !cabePublicacionCucha(completo))) Text(stringResource(R.string.red_error), color = f.texto)
    val cuando = rememberSaveable(clave) { anterior?.cuando ?: System.currentTimeMillis() }
    BotonRiso(stringResource(if (anterior == null) R.string.red_publicar else R.string.red_actualizar), {
        alcance.launch {
            creando = true; error = false
            try {
                // Un lugar nuevo se crea justo antes de publicar (o se reutiliza si ya existía con ese nombre y ciudad).
                val final = when (lugar) {
                    is LugarElegido.Nuevo -> LugarElegido.Existente(vm.red.crearLugar(lugar.datos))
                    else -> lugar
                }
                val id = (final as? LugarElegido.Existente)?.lugar?.id
                val conLugar = datos()?.let { d -> if (id != null) JsonObject(d + ("lugar" to JsonPrimitive(id))) else JsonObject(d - "lugar") }
                    ?.let { conMultimedia(it, m.fotos, m.video, m.poster) } ?: error("sin datos")
                require(cabePublicacionCucha(conLugar))
                vm.guardarDatoRed("lugar.$clave", (final as? LugarElegido.Existente)?.let { JsonSocial.encodeToString(Lugar.serializer(), it.lugar) })
                vm.red.publicar(PublicacionSubida(clave, if (seguidores) "amigos" else "publica", cuando, tipo, conLugar), vm::cerrar)
            } catch (x: kotlinx.coroutines.CancellationException) { throw x }
            catch (_: Exception) { error = true }
            finally { creando = false }
        }
    }, Modifier.fillMaxWidth(), habilitado = completo != null && cabePublicacionCucha(completo) && libre)
}

@Composable
private fun sinCuenta(vm: CuchaViewModel, titulo: String): Boolean {
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    Cabecera(titulo, vm::cerrar)
    if (usuario == null) BotonRiso(stringResource(R.string.red_entrar), { vm.abrir(io.github.fogonapps.cucha.Pantalla.Cuenta) }, Modifier.padding(16.dp))
    return usuario == null
}

/** Compartir un plato: desde algo anotado (con su nutrición, que va apagada) o uno nuevo. */
@Composable
fun CompartirPlato(vm: CuchaViewModel, e: Estado, fecha: String?, idAnotado: String?) {
    if (sinCuenta(vm, stringResource(R.string.red_compartir_plato))) return
    val anotado = remember(fecha, idAnotado) { fecha?.let { e.dias[it] }?.anotados?.firstOrNull { it.id == idAnotado } }
    val origen = rememberSaveable(idAnotado) { idAnotado ?: ("nuevo-" + java.util.UUID.randomUUID()) }
    val clave = clavePublicacion(TIPO_PLATO, origen)
    val anterior = remember(clave) { vm.red.publicada(clave) }
    val d = anterior?.datos
    var nombre by rememberSaveable(clave) { mutableStateOf(d?.get("nombre")?.jsonPrimitive?.content ?: anotado?.nombre.orEmpty()) }
    var nota by rememberSaveable(clave) { mutableStateOf(d?.get("nota")?.jsonPrimitive?.intOrNull) }
    var texto by rememberSaveable(clave) { mutableStateOf(d?.get("texto")?.jsonPrimitive?.content.orEmpty()) }
    var seguidores by rememberSaveable(clave) { mutableStateOf(anterior?.visibilidad == "amigos") }
    var nutricion by rememberSaveable(clave) { mutableStateOf(d?.get("nutricion") != null) }
    var lugar by remember(clave) { mutableStateOf<LugarElegido?>(lugarRecordado(vm, clave)?.takeIf { d?.get("lugar") != null }?.let { LugarElegido.Existente(it) }) }
    val m = rememberMultimedia(anterior)
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Pista(stringResource(R.string.red_aviso_plato), adentro = true)
        OutlinedTextField(nombre, { nombre = it.take(120) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.red_nombre_plato)) }, singleLine = true)
        ElegirNota(nota, opcional = true, habilitado = true) { nota = it }
        if (anotado != null) Interruptor(stringResource(R.string.red_nutricion), stringResource(R.string.red_nutricion_pista), nutricion) { nutricion = it }
        Seccion(stringResource(R.string.red_donde_comiste))
        ElegirLugar(vm, lugar, true) { lugar = it }
        PieCompositor(vm, TIPO_PLATO, clave, anterior, m, texto, { texto = it }, seguidores, { seguidores = it }, lugar) {
            instantaneaPlato(nombre, nota, texto, null, anotado?.n?.takeIf { nutricion })
        }
    }
}

/** Compartir una receta propia como instantánea: ingredientes con nombre, cantidad y unidad; la nutrición por porción, apagada. */
@Composable
fun CompartirReceta(vm: CuchaViewModel, e: Estado, id: String) {
    if (sinCuenta(vm, stringResource(R.string.red_compartir_receta))) return
    val receta = remember(id) { e.recetas.firstOrNull { it.id == id } } ?: return
    val idioma = vm.idioma()
    val clave = clavePublicacion(TIPO_RECETA, id)
    val anterior = remember(clave) { vm.red.publicada(clave) }
    var texto by rememberSaveable(clave) { mutableStateOf(anterior?.datos?.get("texto")?.jsonPrimitive?.content.orEmpty()) }
    var seguidores by rememberSaveable(clave) { mutableStateOf(anterior?.visibilidad == "amigos") }
    var nutricion by rememberSaveable(clave) { mutableStateOf(anterior?.datos?.get("nutricion") != null) }
    val ingredientes = remember(receta, idioma) { ingredientesCompartidos(receta, vm.L.base, e.productos, idioma) }
    val m = rememberMultimedia(anterior)
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Pista(stringResource(R.string.red_aviso_receta), adentro = true)
        Interruptor(stringResource(R.string.red_nutricion), stringResource(R.string.red_nutricion_pista), nutricion) { nutricion = it }
        PieCompositor(vm, TIPO_RECETA, clave, anterior, m, texto, { texto = it }, seguidores, { seguidores = it }, null) {
            instantaneaReceta(receta.nombre(idioma), receta.porciones, receta.minutos, ingredientes, receta.pasos(idioma), texto,
                vm.L.porPorcion(receta).takeIf { nutricion })
        }
    }
}

/** Reseñar un restaurante: el lugar (obligatorio), la nota, el texto y, si quiere, lo que pidió. */
@Composable
fun ResenarRestaurante(vm: CuchaViewModel, claveAnterior: String? = null) {
    if (sinCuenta(vm, stringResource(R.string.red_resenar))) return
    val origen = rememberSaveable { "resena-" + java.util.UUID.randomUUID() }
    val clave = claveAnterior ?: clavePublicacion(TIPO_RESENA, origen)
    // Al actualizar, se parte de lo publicado y del lugar que se recordó al publicar.
    val anterior = remember(clave) { vm.red.publicada(clave) }
    val d = anterior?.datos
    var nota by rememberSaveable(clave) { mutableStateOf(d?.get("nota")?.jsonPrimitive?.intOrNull) }
    var texto by rememberSaveable(clave) { mutableStateOf(d?.get("texto")?.jsonPrimitive?.content.orEmpty()) }
    var plato by rememberSaveable(clave) { mutableStateOf(d?.get("plato")?.jsonPrimitive?.content.orEmpty()) }
    var seguidores by rememberSaveable(clave) { mutableStateOf(anterior?.visibilidad == "amigos") }
    var lugar by remember(clave) { mutableStateOf<LugarElegido?>(lugarRecordado(vm, clave)?.let { LugarElegido.Existente(it) }) }
    val m = rememberMultimedia(anterior)
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ElegirLugar(vm, lugar, true) { lugar = it }
        ElegirNota(nota, opcional = false, habilitado = true) { nota = it }
        OutlinedTextField(plato, { plato = it.take(120) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.red_plato_pedido)) }, singleLine = true)
        PieCompositor(vm, TIPO_RESENA, clave, anterior, m, texto, { texto = it }, seguidores, { seguidores = it }, lugar) {
            // El id real del lugar se pone al publicar (un lugar nuevo todavía no lo tiene).
            if (lugar == null) null else instantaneaResena(1, requireNotNull(nota), texto, plato)
        }
    }
}

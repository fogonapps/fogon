package io.github.fogonapps.forzudo.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fogonapps.forzudo.*
import io.github.fogonapps.forzudo.R
import io.github.fogonapps.forzudo.datos.*
import io.github.fogonapps.identidad.*
import io.github.fogonapps.social.*
import io.github.fogonapps.social.ui.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

@Composable
fun BotonCompartirEntreno(vm: GymViewModel, id: String) {
    val publicando by vm.red.publicando.collectAsState()
    BotonSecundario(stringResource(if (vm.red.publicada(claveEntreno(id)) == null) R.string.entreno_compartir else R.string.entreno_actualizar_boton), { vm.abrir(Pantalla.Compartir(id)) }, Modifier.fillMaxWidth().padding(16.dp), habilitado = !publicando)
}

@Composable
fun CuerpoEntreno(datos: JsonObject) {
    val f = FogonTema.actual
    Text(datos["titulo"]?.jsonPrimitive?.content.orEmpty(), color = f.texto, style = MaterialTheme.typography.titleMedium)
    val fecha = datos["fecha"]?.jsonPrimitive?.longOrNull
    val segundos = datos["duracion"]?.jsonPrimitive?.longOrNull ?: 0
    Text(stringResource(R.string.entreno_fecha_duracion,
        fecha?.let { java.text.DateFormat.getDateInstance().apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(it)) }.orEmpty(), stringResource(R.string.entreno_minutos, ((segundos + 30) / 60).toInt())), color = f.textoSuave)
    datos["ejercicios"]?.jsonArray?.forEach { elemento ->
        val ejercicio = elemento.jsonObject
        val series = ejercicio["series"]?.jsonArray.orEmpty()
        val resumen = resumenSeriesEntreno(series)
        Text("${ejercicio["nombre"]?.jsonPrimitive?.content.orEmpty()} · $resumen", color = f.texto)
    }
}

@Composable
fun CompartirEntreno(vm: GymViewModel, estado: Estado, id: String) {
    val usuario by vm.red.usuario.collectAsState()
    val ocupada by vm.red.publicando.collectAsState()
    Cabecera(stringResource(R.string.entreno_compartir), vm::cerrar)
    if (usuario == null) {
        BotonRiso(stringResource(R.string.entreno_entrar), { vm.abrir(Pantalla.Cuenta) }, Modifier.padding(16.dp))
        return
    }
    val sesion = estado.sessions.find { it.id == id } ?: return
    // La copia se toma al abrir: las ediciones del cuaderno no alteran el borrador.
    val copia = remember(id) { sesion }
    var excluidos by rememberSaveable(id) { mutableStateOf(emptyList<String>()) }
    val anterior = remember(id) { vm.red.publicada(claveEntreno(id)) }
    var seriesExcluidas by rememberSaveable(id) { mutableStateOf(emptyList<String>()) }
    var texto by rememberSaveable(id) { mutableStateOf(anterior?.datos?.get("texto")?.jsonPrimitive?.content.orEmpty()) }
    var seguidores by rememberSaveable(id) { mutableStateOf(anterior?.visibilidad == "amigos") }
    var fotos by remember { mutableStateOf(anterior?.datos?.get("imagenes")?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()) }
    var video by remember { mutableStateOf(anterior?.datos?.get("video")?.jsonPrimitive?.content.orEmpty()) }
    var poster by remember { mutableStateOf(anterior?.datos?.get("poster")?.jsonPrimitive?.content.orEmpty()) }
    val cuando = rememberSaveable(id) { anterior?.cuando ?: System.currentTimeMillis() }
    var preparando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val manejar_selectorFotos4: (List<android.net.Uri>) -> Unit = { uris ->
        alcance.launch {
            preparando = true; error = false
            try {
                val nuevas = uris.take(10 - fotos.size).map { uri ->
                    val imagen = requireNotNull(io.github.fogonapps.perfil.imagenPerfilDesde(contexto, uri))
                    vm.red.subirFotoPublicacion(android.util.Base64.decode(imagen, android.util.Base64.DEFAULT))
                }
                fotos = fotos + nuevas
            } catch (x: kotlinx.coroutines.CancellationException) { throw x }
            catch (_: Exception) { error = true }
            finally { preparando = false }
        }
    }
    // Antes de usarla(s), el paso para girarlas.
    val girar_selectorFotos4 = io.github.fogonapps.social.ui.conGiro { l -> manejar_selectorFotos4(l) }
    val selectorFotos = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { r -> if (r.isNotEmpty()) girar_selectorFotos4(r) }
    val selectorVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) alcance.launch {
            preparando = true; error = false
            var preparado: VideoPreparado? = null
            try {
                preparado = prepararVideo(contexto, uri, {}, {})
                video = vm.red.subirVideoPublicacion(preparado.archivo)
                poster = vm.red.subirFotoPublicacion(preparado.poster)
            } catch (x: kotlinx.coroutines.CancellationException) { throw x }
            catch (_: Exception) { video = ""; poster = ""; error = true }
            finally { preparado?.archivo?.delete(); preparando = false }
        }
    }
    val instantanea = remember(copia, excluidos, seriesExcluidas) { runCatching { instantaneaEntreno(copia, vm.bib.ex, excluir = excluidos.toSet(), seriesExcluidas = seriesExcluidas.toSet()) }.getOrNull() }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth().tarjetaFogon().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(copia.title ?: copia.date, style = MaterialTheme.typography.titleMedium, color = FogonTema.actual.texto)
            Text(stringResource(R.string.entreno_fecha_duracion, fechaLegible(copia.date),
                stringResource(R.string.entreno_minutos, (((copia.ms ?: 0) + 30000) / 60000).toInt())),
                style = MaterialTheme.typography.bodySmall, color = FogonTema.actual.textoSuave)
            copia.entries.keys.filter { it !in excluidos }.forEach { ejercicio ->
                var expandido by rememberSaveable(ejercicio) { mutableStateOf(false) }
                val series = instantanea?.get("ejercicios")?.jsonArray?.firstOrNull {
                    it.jsonObject["id"]?.jsonPrimitive?.content == ejercicio
                }?.jsonObject?.get("series")?.jsonArray.orEmpty()
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable(enabled = !ocupada && !preparando) { expandido = !expandido }.padding(vertical = 8.dp)) {
                            Text(vm.bib.ex[ejercicio]?.n ?: ejercicio, style = MaterialTheme.typography.bodyMedium, color = FogonTema.actual.texto)
                            Text(resumenSeriesEntreno(series), style = MaterialTheme.typography.bodySmall, color = FogonTema.actual.textoSuave)
                        }
                        BotonIconoSocial(IconosSociales.Opciones, stringResource(R.string.series), activo = !ocupada && !preparando) { expandido = !expandido }
                        BotonIconoSocial(IconosSociales.Cerrar, stringResource(R.string.entreno_quitar, vm.bib.ex[ejercicio]?.n ?: ejercicio),
                            activo = !ocupada && !preparando) { excluidos = excluidos + ejercicio }
                    }
                    if (expandido) copia.entries[ejercicio].orEmpty().forEachIndexed { indice, _ ->
                        if ("$ejercicio:$indice" !in seriesExcluidas) TextButton(
                            { seriesExcluidas = seriesExcluidas + "$ejercicio:$indice" }, enabled = !ocupada && !preparando) {
                            Text(stringResource(R.string.entreno_quitar_serie, indice + 1))
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.entreno_aviso), style = MaterialTheme.typography.bodySmall, color = FogonTema.actual.textoSuave)
        OutlinedTextField(texto, { texto = it.take(2000) }, Modifier.fillMaxWidth().height(160.dp),
            placeholder = { Text(stringResource(R.string.entreno_texto), color = FogonTema.actual.textoSuave) }, shape = forma(20.dp),
            supportingText = { Text(stringResource(R.string.entreno_etiquetas_pista), color = FogonTema.actual.textoSuave) }, enabled = !ocupada && !preparando)
        if (!ocupada && !preparando && video.isBlank() && fotos.size < 10) PildoraSocial(stringResource(R.string.entreno_agregar_foto), IconosFogon.Imagen) { selectorFotos.launch("image/*") }
        if (!ocupada && !preparando && fotos.isEmpty() && video.isBlank()) PildoraSocial(stringResource(R.string.entreno_agregar_video), IconosFogon.Imagen) { selectorVideo.launch("video/*") }
        fotos.forEachIndexed { indice, foto -> BotonSecundario(stringResource(R.string.entreno_quitar_foto, indice + 1), { fotos = fotos - foto }, habilitado = !ocupada && !preparando) }
        if (video.isNotBlank()) BotonSecundario(stringResource(R.string.entreno_quitar_video), { video = ""; poster = "" }, habilitado = !ocupada && !preparando)
        OpcionSocial(stringResource(R.string.entreno_seguidores), seguidores, { if (!ocupada && !preparando) seguidores = it })
        if (preparando || ocupada) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error || instantanea == null) Text(stringResource(R.string.entreno_error), color = FogonTema.actual.texto)
        val datos = instantanea?.let { base -> JsonObject(base + buildJsonObject {
            if (texto.isNotBlank()) put("texto", texto)
            if (fotos.isNotEmpty()) put("imagenes", JsonArray(fotos.map { JsonPrimitive(it) }))
            if (video.isNotBlank() && poster.isNotBlank()) { put("video", video); put("poster", poster) }
        }) }
        if (datos != null && !cabeInstantaneaEntreno(datos)) Text(stringResource(R.string.entreno_error), color = FogonTema.actual.texto)
        BotonRiso(stringResource(if (anterior == null) R.string.entreno_publicar else R.string.entreno_actualizar_boton), {
            if (datos != null) vm.red.publicarEntreno(PublicacionSubida(claveEntreno(id), if (seguidores) "amigos" else "publica",
                cuando, "entreno", datos), vm::cerrar)
        }, Modifier.fillMaxWidth(), habilitado = datos != null && cabeInstantaneaEntreno(datos) && !ocupada && !preparando)
    }
}

@Composable
fun RedEntrenos(vm: GymViewModel, destino: Pantalla.Social) {
    val interfaz = interfazCuenta(vm)
    val navChat = io.github.fogonapps.social.ui.NavegacionChat({ vm.abrir(Pantalla.Social("chat", usuario = it)) }, { vm.abrir(Pantalla.Social("infochat", usuario = it)) })
    when (destino.destino) {
        "mensajes" -> io.github.fogonapps.social.ui.rememberChat(interfaz)?.let { io.github.fogonapps.social.ui.ChatsSocial(interfaz, it, navChat) }
        "chat" -> io.github.fogonapps.social.ui.rememberChat(interfaz)?.let { io.github.fogonapps.social.ui.ConversacionSocial(interfaz, it, navChat, destino.usuario) }
        "infochat" -> io.github.fogonapps.social.ui.rememberChat(interfaz)?.let { io.github.fogonapps.social.ui.InfoChatSocial(interfaz, it, destino.usuario) }
        "publicacion" -> PublicacionRed(interfaz, destino.id)
        "avisos" -> AvisosRed(interfaz)
        "buscar" -> io.github.fogonapps.social.ui.BuscarGente(interfaz, java.io.File(vm.perfiles.directorio(vm.idPerfil), "busquedas-gente.txt"), vm::cerrar)
        "guardados" -> GuardadosRed(interfaz)
        "moderacion" -> Moderacion(interfaz)
        "etiqueta" -> EtiquetaRed(interfaz, destino.usuario)
        "perfil" -> {
            val perfiles by vm.red.perfiles.collectAsState()
            LaunchedEffect(destino.usuario) { vm.red.verPerfil(destino.usuario) }
            val perfil = perfiles[destino.usuario]
            io.github.fogonapps.social.ui.CabeceraPerfilAjeno(interfaz, destino.usuario)
            if (perfil != null) {
                val yo by vm.red.usuario.collectAsState()
                val secciones = interfaz.contenido.SeccionesPerfil(perfil, perfil.persona.usuario == yo) {
                    perfil.publicaciones.filter { it.tipo == "entreno" }.forEach { publicacion -> TarjetaPublicacion(interfaz, publicacion) }
                }
                val vista = io.github.fogonapps.perfil.PerfilVista(perfil.persona.nombre, perfil.persona.usuario,
                    "", perfil.portada, perfil.bio, perfil.enlaces, perfil.persona.privada, perfil.relacion,
                    secciones = secciones, insignia = perfil.persona.insignia)
                io.github.fogonapps.perfil.Perfil(vista.copy(tema = perfil.persona.tema, marco = perfil.persona.marco, contadores = io.github.fogonapps.social.ui.cifrasSociales(perfil) + vista.contadores), 0, {}, portada = { io.github.fogonapps.social.ui.PortadaPerfilRed(perfil.portada, Modifier.matchParentSize()) },
                    foto = { io.github.fogonapps.social.ui.AvatarPerfilConHistorias(interfaz, perfil.persona) },
                    bajoNombre = { Text("@${perfil.persona.usuario}", color = FogonTema.actual.textoSuave) },
                    enlace = {}, acciones = {
            // Escribirle (el chat cifrado, igual en las cuatro apps).
            if (yo != null && perfil.persona.usuario != yo) io.github.fogonapps.perfil.BotonPerfil(stringResource(io.github.fogonapps.social.ui.R.string.social_chat_mensaje), { vm.abrir(Pantalla.Social("chat", usuario = perfil.persona.usuario)) }, Modifier.weight(1f))
                        if (perfil.persona.usuario != yo) io.github.fogonapps.perfil.BotonPerfil(stringResource(
                            if (perfil.relacion == "sigo" && perfil.meSigue) io.github.fogonapps.social.ui.R.string.amigos_dejar else if (perfil.relacion == "sigo") R.string.entreno_dejar_seguir else R.string.entreno_seguir), {
                            if (perfil.relacion == "sigo") interfaz.confirmar(interfaz.tx(io.github.fogonapps.social.ui.R.string.social_dejar_de_seguir), interfaz.tx(io.github.fogonapps.social.ui.R.string.social_dejar_de_seguir), { vm.red.dejarDeSeguir(destino.usuario) }) else vm.red.seguir(destino.usuario)
                        }, Modifier.weight(1f))
                    }, despuesAcciones = {
            Box(Modifier.padding(horizontal = 16.dp)) { io.github.fogonapps.social.ui.DestacadasPerfil(interfaz, perfil.persona.usuario) }
            // Su canción, sonando sola al entrar (si el ajuste lo deja).
            (perfil.partes["cancion"] as? kotlinx.serialization.json.JsonObject)?.let {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { io.github.fogonapps.social.ui.TarjetaCancionPerfil(it, sonar = perfil.persona.usuario != yo, buscar = { q -> interfaz.red.canciones(q) }) }
            }
        })
            }
        }
        else -> TuGente(vm, interfaz, conAtras = true)
    }
}

/** «Tu gente»: Siguiendo y Explorar de El Forzudo; es una sección de la barra (X-07) y también se abre encima. */
@Composable
fun TuGente(vm: GymViewModel, interfaz: InterfazSocial = interfazCuenta(vm), conAtras: Boolean = false) {
    val usuario by vm.red.usuario.collectAsState()
    var pestana by rememberSaveable { mutableIntStateOf(1) }
    val feed by vm.red.feed.collectAsState()
    val explorar by vm.red.explorar.collectAsState()
    val ocultas by vm.red.ocultas.collectAsState()
    val sinLeer by vm.red.sinLeer.collectAsState()
    val cuenta by vm.red.cuenta.collectAsState()
    var eligiendo by rememberSaveable { mutableStateOf(false) }
    val estado by vm.estado.collectAsState()
    LaunchedEffect(Unit) { vm.red.cargarFeed(); vm.red.cargarExplorar(); vm.red.revisarSinLeer() }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (conAtras) BotonIconoSocial(IconosSociales.Atras, stringResource(R.string.volver)) { vm.cerrar() }
        Text(stringResource(R.string.entreno_tu_gente), style = MaterialTheme.typography.headlineSmall, color = FogonTema.actual.texto, modifier = Modifier.weight(1f))
        if (usuario != null) ConCuentaSocial(sinLeer) {
            BotonIconoSocial(IconosFogon.Campana, stringResource(R.string.entreno_avisos), grande = true) { vm.abrir(Pantalla.Social("avisos")) }
        }
        if (usuario != null) BotonIconoSocial(IconosSociales.Enviar, stringResource(io.github.fogonapps.social.ui.R.string.social_chat_titulo), grande = true) { vm.abrir(Pantalla.Social("mensajes")) }
        if (usuario != null) BotonIconoSocial(io.github.fogonapps.social.ui.IconoBuscar, stringResource(io.github.fogonapps.social.ui.R.string.gente_buscar), grande = true) { vm.abrir(Pantalla.Social("buscar")) }
        if (!cuenta?.permisos.isNullOrEmpty()) BotonIconoSocial(IconosSociales.Bandera, stringResource(R.string.entreno_moderacion), grande = true) { vm.abrir(Pantalla.Social("moderacion")) }
    }
    if (usuario == null) BotonRiso(stringResource(R.string.entreno_entrar), { vm.abrir(Pantalla.Cuenta) }, Modifier.padding(16.dp))
    CopiaCuenta(vm.red, soloSiFalta = true)
    if (usuario != null) io.github.fogonapps.social.ui.BarraHistorias(interfaz)
    PestanasFeedSocial(pestana, usuario, { pestana = it }, textoPublicar = stringResource(R.string.entreno_compartir)) { eligiendo = true }
    if (eligiendo) AlertDialog(onDismissRequest = { eligiendo = false }, title = { Text(stringResource(R.string.entreno_elegir)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                val recientes = estado?.sessions.orEmpty().asReversed().sortedByDescending { it.date }.take(20)
                if (recientes.isEmpty()) Text(stringResource(R.string.sin_entrenos), color = FogonTema.actual.textoSuave)
                recientes.forEach { sesion -> TextButton({ eligiendo = false; vm.abrir(Pantalla.Compartir(sesion.id)) }, Modifier.fillMaxWidth()) {
                    Text("${sesion.title ?: fechaLegible(sesion.date)} · ${fechaLegible(sesion.date)}")
                } }
            }
        }, confirmButton = { TextButton({ eligiendo = false }) { Text(stringResource(R.string.volver)) } })
    val palabrasOcultas = io.github.fogonapps.social.ui.palabrasOcultasRed()
    ListaFeedSocial((if (pestana == 0) (if (usuario == null) emptyList() else feed) else explorar)?.filter { it.id !in ocultas && !io.github.fogonapps.social.ui.PalabrasOcultasRed.lleva(palabrasOcultas, io.github.fogonapps.social.ui.PalabrasOcultasRed.textoDe(it)) }?.map { it to Unit }, pestana,
        vm.red::masFeed, stringResource(R.string.entreno_feed_pie)) { p, _ -> TarjetaPublicacion(interfaz, p) }
}

@Composable
fun MenuEntreno(vm: GymViewModel, interfaz: InterfazSocial, p: PublicacionSocial, cerrar: () -> Unit) {
    val usuario by vm.red.usuario.collectAsState()
    val estado by vm.estado.collectAsState()
    val confirmar = LocalConfirmar.current
    if (usuario == p.autor.usuario) {
        val borrar = stringResource(R.string.entreno_borrar)
        val privado = stringResource(R.string.entreno_privado)
        AlertDialog(onDismissRequest = cerrar, title = { Text(stringResource(R.string.entreno_publicado)) }, text = {
            Column {
                val clave = vm.red.origen(p)
                val sesion = estado?.sessions?.find { claveEntreno(it.id) == clave }
                if (sesion != null) TextButton({ cerrar(); vm.abrir(Pantalla.Compartir(sesion.id)) }) {
                    Text(stringResource(R.string.entreno_actualizar, sesion.title ?: sesion.date))
                }
                TextButton({ cerrar(); confirmar(privado, privado) { if (clave != null) vm.red.retirarEntreno(clave, p.id) else vm.red.borrarEntreno(p.id) } }) { Text(privado) }
                TextButton({ cerrar(); confirmar(borrar, borrar) { vm.red.borrarEntreno(p.id) } }) { Text(borrar) }
            }
        }, confirmButton = { TextButton(cerrar) { Text(stringResource(R.string.volver)) } })
    } else MenuPublicacion(interfaz, p, "", cerrar)
}

@Composable
fun ReporteEntreno(vm: GymViewModel, contenido: String) {
    val datos = remember(contenido) { runCatching {
        val raiz = JsonSocial.parseToJsonElement(contenido).jsonObject
        (raiz["datos"] as? JsonObject) ?: raiz
    }.getOrNull() }
    if (datos != null && datos["ejercicios"] is JsonArray) {
        CuerpoEntreno(datos)
        datos["texto"]?.jsonPrimitive?.content?.let { Text(it, color = FogonTema.actual.texto) }
        MultimediaEntreno(vm, datos)
    }
    else Text(contenido.take(2000), color = FogonTema.actual.texto)
}

@Composable
fun MultimediaEntreno(vm: GymViewModel, datos: JsonObject) {
    val video = datos["video"]?.jsonPrimitive?.content.orEmpty()
    if (video.isNotBlank()) VideoPublicacion(vm.red::cabecerasPublicacion, video,
        datos["poster"]?.jsonPrimitive?.content.orEmpty(), 0, stringResource(R.string.entreno_reproducir))
    else CarruselFotos(imagenesPublicacion(datos))
}

private fun resumenSeriesEntreno(series: List<JsonElement>): String {
    return series.groupingBy { it.jsonObject }.eachCount().entries.joinToString(" · ") { (serie, cantidad) ->
            val reps = serie["repeticiones"]?.jsonPrimitive?.content
                ?: serie["duracion"]?.jsonPrimitive?.content?.plus(" s").orEmpty()
            val peso = serie["peso"]?.jsonPrimitive?.doubleOrNull?.let { fk(it) + " " + serie["unidadPeso"]?.jsonPrimitive?.content }.orEmpty()
            "$cantidad × $reps${if (peso.isNotBlank()) " · $peso" else ""}"
        }
}

/** «9 nov 2026», como en el resto de la app. */
private fun fechaLegible(s: String): String = runCatching {
    "${io.github.fogonapps.forzudo.datos.fechaCorta(s)} ${io.github.fogonapps.forzudo.datos.fecha(s).year}"
}.getOrDefault(s)

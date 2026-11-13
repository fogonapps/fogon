package io.github.fogonapps.ahorrador.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import io.github.fogonapps.identidad.*
import io.github.fogonapps.perfil.*
import io.github.fogonapps.ahorrador.*
import io.github.fogonapps.ahorrador.R
import io.github.fogonapps.ahorrador.datos.Estado
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Perfil exclusivamente local: las cifras y el contenido no presentan montos. */
@Composable
fun Perfil(vm: AhorradorViewModel, e: Estado, exportar: () -> Unit = {}) {
    var pestana by rememberSaveable { mutableIntStateOf(0) }
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    val remoto by vm.red.identidad.collectAsStateWithLifecycle()
    val fotoRemota by vm.red.foto.collectAsStateWithLifecycle()
    val portadaRemota by vm.red.portadaImagen.collectAsStateWithLifecycle()
    val perfilesRed by vm.red.perfiles.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    LaunchedEffect(usuario) { usuario?.let { vm.red.cargarIdentidad(); vm.red.verPerfil(it) } }
    // Con cuenta, la identidad es la global de Fogón Social (D-030); sin cuenta, la de este teléfono.
    val nombre = if (usuario == null) e.nombre.orEmpty() else remoto?.persona?.nombre.orEmpty()
    val bio = if (usuario == null) e.bio else remoto?.bio.orEmpty()
    val foto = if (usuario == null) e.fotoPerfil else fotoRemota
    val portada = if (usuario == null) e.portadaPerfil else portadaRemota
    val publicados = usuario?.let { perfilesRed[it]?.publicaciones }.orEmpty().filter { it.tipo == io.github.fogonapps.ahorrador.datos.TIPO_LOGRO }
    val interfaz = interfazCuenta(vm)
    val cumplidas = e.metas.count { vm.L.progreso(e, it) >= 1.0 }
    val dias = e.movimientos.map { it.fecha }.distinct().size
    val secciones = listOf(
        SeccionPerfil(0, Iconos.Moneda, stringResource(R.string.pestana_metas)) {
            if (e.metas.isEmpty()) PerfilVacio(Iconos.Moneda, stringResource(R.string.perfil_metas_vacias))
            e.metas.forEach { meta ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().tarjetaFogon().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(meta.nombre, Modifier.weight(1f), color = FogonTema.actual.texto)
                    Text(stringResource(R.string.perfil_porcentaje, (vm.L.progreso(e, meta).coerceIn(0.0, 1.0) * 100).roundToInt()), color = FogonTema.actual.acentoTexto)
                }
            }
        },
        SeccionPerfil(1, Iconos.Estrella, stringResource(R.string.perfil_logros)) {
            if (dias == 0 && cumplidas == 0 && e.cierres.isEmpty()) PerfilVacio(Iconos.Estrella, stringResource(R.string.perfil_logros_vacios))
            if (dias > 0) Logro(stringResource(R.string.perfil_primera_anotacion))
            if (cumplidas > 0) Logro(stringResource(R.string.perfil_primera_meta))
            if (e.cierres.isNotEmpty()) Logro(stringResource(R.string.perfil_cierres, e.cierres.size))
            if (usuario != null) {
                BotonSecundario(stringResource(R.string.red_compartir), { vm.abrir(Pantalla.CompartirLogro()) }, Modifier.fillMaxWidth().padding(16.dp))
                if (publicados.isNotEmpty()) Seccion(stringResource(R.string.red_publicados))
                publicados.forEach { io.github.fogonapps.social.ui.TarjetaPublicacion(interfaz, it) }
            }
        },
    )
    val miInsignia = vm.red.cuenta.collectAsState().value?.insignia.orEmpty()
    val vista = PerfilVista(nombre.ifBlank { stringResource(R.string.perfil_sin_nombre) }, usuario.orEmpty(), foto,
        portada, bio, emptyList(), true, "yo", listOf(
            ContadorPerfil(dias.toString(), stringResource(R.string.perfil_dias)),
            ContadorPerfil(e.movimientos.size.toString(), stringResource(R.string.movimientos)),
            ContadorPerfil(cumplidas.toString(), stringResource(R.string.perfil_cumplidas)),
        ), secciones, insignia = miInsignia)
    io.github.fogonapps.perfil.Perfil(vista.copy(tema = io.github.fogonapps.social.ui.temaPropio(interfaz), marco = io.github.fogonapps.social.ui.marcoPropio(interfaz)), pestana, { pestana = it }, nombreSuave = nombre.isBlank(), interlineado = true,
        navegacion = {
            io.github.fogonapps.social.ui.CabeceraPerfil(usuario?.let { "@$it" } ?: stringResource(R.string.tu_perfil), vm::cerrar) {
                io.github.fogonapps.identidad.BotonIconoSocial(Iconos.Ajustes, stringResource(R.string.ajustes)) { vm.abrir(Pantalla.Ajustes) }
            }
        },
        portada = { ImagenLocal(portada, Modifier.matchParentSize()) },
        foto = {
            Box {
                io.github.fogonapps.social.ui.AroHistoriasPropias(interfaz) { FotoPerfilLocal(foto) }
                BotonMasFoto(stringResource(R.string.perfil_elegir_foto), Modifier.align(Alignment.BottomEnd)) { vm.abrir(Pantalla.EditarPerfil) }
            }
        }, bajoNombre = { if (usuario != null) Text("@$usuario", color = FogonTema.actual.textoSuave) }, enlace = {},
        acciones = {
            io.github.fogonapps.perfil.BotonPerfil(stringResource(R.string.editar_perfil), { vm.abrir(Pantalla.EditarPerfil) }, Modifier.weight(1f))
            if (usuario != null) io.github.fogonapps.perfil.BotonPerfil(stringResource(io.github.fogonapps.social.ui.R.string.social_red_compartir_perfil), {
                contexto.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND)
                    .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, io.github.fogonapps.social.ClienteSocial.enlacePerfil(usuario!!)), null))
            }, Modifier.weight(1f))
        }, despuesAcciones = {
            usuario?.let { u -> Box(Modifier.padding(horizontal = 16.dp)) { io.github.fogonapps.social.ui.DestacadasPerfil(interfaz, u) }; io.github.fogonapps.social.ui.CancionPerfilPropia(interfaz) } },
    )
    Espacio(40.dp)
}

@Composable
private fun Logro(texto: String) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().tarjetaFogon().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconoRiso(Iconos.Estrella, null)
        Text(texto, color = FogonTema.actual.texto)
    }
}

@Composable
fun EditarPerfil(vm: AhorradorViewModel, e: Estado) {
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    if (usuario != null) { EditarPerfilRed(vm); return }
    var nombre by rememberSaveable { mutableStateOf(recortarNombrePerfil(e.nombre.orEmpty())) }
    var bio by rememberSaveable { mutableStateOf(recortarBioPerfil(e.bio)) }
    var foto by remember { mutableStateOf(e.fotoPerfil) }
    var portada by remember { mutableStateOf(e.portadaPerfil) }
    var eligiendoPortada by rememberSaveable { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val manejar_selector5: (android.net.Uri?) -> Unit = { uri ->
        if (uri != null) {
            val esPortada = eligiendoPortada
            cargando = true
            error = false
            alcance.launch {
                val datos = imagenPerfilDesde(contexto, uri)
                error = datos == null
                if (datos != null) { if (esPortada) portada = datos else foto = datos; vm.imagenPerfil(esPortada, datos) }
                cargando = false
            }
        }
    }
    // Antes de usarla(s), el paso para girarlas.
    val girar_selector5 = io.github.fogonapps.social.ui.conGiro { l -> manejar_selector5(l.firstOrNull()) }
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { r -> if (r != null) girar_selector5(listOf(r)) else manejar_selector5(null) }
    Cabecera(stringResource(R.string.editar_perfil), vm::cerrar)
    EditorPerfilLocal(nombre, bio, foto, portada,
        onNombre = { nombre = it }, onBio = { bio = it },
        onElegirFoto = { eligiendoPortada = false; selector.launch("image/*") },
        onQuitarFoto = { foto = ""; vm.imagenPerfil(false, "") },
        onElegirPortada = { eligiendoPortada = true; selector.launch("image/*") },
        onQuitarPortada = { portada = ""; vm.imagenPerfil(true, "") },
        onGuardar = { vm.guardarPerfil(nombre, bio) }, habilitado = !cargando)
    if (error) Text(stringResource(io.github.fogonapps.perfil.R.string.perfil_local_error), Modifier.padding(16.dp), color = FogonTema.actual.texto)
    Espacio(40.dp)
}

/** Con cuenta, «Editar perfil» cambia la identidad global de Fogón Social y el «Quién lo ve» de las metas. */
@Composable
private fun EditarPerfilRed(vm: AhorradorViewModel) {
    val remoto by vm.red.identidad.collectAsStateWithLifecycle()
    val fotoRemota by vm.red.foto.collectAsStateWithLifecycle()
    val portadaRemota by vm.red.portadaImagen.collectAsStateWithLifecycle()
    val guardando by vm.red.guardandoPerfil.collectAsStateWithLifecycle()
    Cabecera(stringResource(R.string.editar_perfil), vm::cerrar)
    val r = remoto
    if (r == null) { Text(stringResource(R.string.cuenta_edicion_pendiente), Modifier.padding(16.dp), color = FogonTema.actual.texto); return }
    io.github.fogonapps.social.ui.TiendaEnEditarPerfil(interfazCuenta(vm))
    var nombre by rememberSaveable(r.persona.nombre, r.bio) { mutableStateOf(recortarNombrePerfil(r.persona.nombre)) }
    var bio by rememberSaveable(r.persona.nombre, r.bio) { mutableStateOf(recortarBioPerfil(r.bio)) }
    var foto by remember(fotoRemota) { mutableStateOf(fotoRemota) }
    var portada by remember(portadaRemota) { mutableStateOf(portadaRemota) }
    var eligiendoPortada by rememberSaveable { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val manejar_selector6: (android.net.Uri?) -> Unit = { uri ->
        if (uri != null) {
            val esPortada = eligiendoPortada
            cargando = true; error = false
            alcance.launch {
                val datos = imagenPerfilDesde(contexto, uri)
                error = datos == null
                if (datos != null) { if (esPortada) portada = datos else foto = datos }
                cargando = false
            }
        }
    }
    // Antes de usarla(s), el paso para girarlas.
    val girar_selector6 = io.github.fogonapps.social.ui.conGiro { l -> manejar_selector6(l.firstOrNull()) }
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { r -> if (r != null) girar_selector6(listOf(r)) else manejar_selector6(null) }
    EditorPerfilLocal(nombre, bio, foto, portada,
        onNombre = { nombre = it }, onBio = { bio = it },
        onElegirFoto = { eligiendoPortada = false; selector.launch("image/*") },
        onQuitarFoto = { foto = "" },
        onElegirPortada = { eligiendoPortada = true; selector.launch("image/*") },
        onQuitarPortada = { portada = "" },
        onGuardar = {
            val bytes = foto.takeIf { it.isNotBlank() && it != fotoRemota }?.substringAfter(",")?.let { android.util.Base64.decode(it, android.util.Base64.DEFAULT) }
            vm.red.editarIdentidad(nombre, bio, portada, bytes, foto.isBlank(), vm::cerrar)
        }, habilitado = !cargando && !guardando)
    PartesAlcancia(vm)
    if (error) Text(stringResource(io.github.fogonapps.perfil.R.string.perfil_local_error), Modifier.padding(16.dp), color = FogonTema.actual.texto)
    Espacio(40.dp)
}

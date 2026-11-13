package io.github.fogonapps.forzudo.ui

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
import io.github.fogonapps.forzudo.*
import io.github.fogonapps.forzudo.R
import io.github.fogonapps.forzudo.datos.Estado
import io.github.fogonapps.forzudo.datos.fmtVol
import io.github.fogonapps.forzudo.datos.medallas
import io.github.fogonapps.social.ClienteSocial

@Composable
fun PerfilLocal(vm: GymViewModel, e: Estado, exportar: () -> Unit) {
    val usuario by vm.red.usuario.collectAsState()
    val remoto by vm.red.identidad.collectAsState()
    val fotoRemota by vm.red.foto.collectAsState()
    val portadaRemota by vm.red.portadaImagen.collectAsState()
    val contexto = LocalContext.current
    LaunchedEffect(usuario) { if (usuario != null) vm.red.cargarIdentidad() }
    val identidad = if (usuario == null) e else e.copy(nombre = remoto?.persona?.nombre.orEmpty(),
        bio = remoto?.bio.orEmpty(), foto = fotoRemota, portada = portadaRemota)
    var elegida by rememberSaveable { mutableIntStateOf(0) }
    val secciones = listOf(
        SeccionPerfil(0, Iconos.Pesa, stringResource(R.string.titulo_entrenos)) {
            if (usuario != null) {
                BotonSecundario(stringResource(R.string.entreno_publicados), { vm.abrir(Pantalla.Social("perfil", usuario = usuario!!)) })
            }
            if (e.sessions.isEmpty()) PerfilVacio(Iconos.Pesa, stringResource(R.string.sin_entrenos))
            e.sessions.sortedByDescending { it.date }.forEach { sesion ->
                TarjetaEntreno(vm, sesion, vm.L.dias(e)) { vm.abrir(Pantalla.Entreno(sesion.id)) }
            }
            Seccion(stringResource(R.string.volumen_total)) {
                Text(fmtVol(e.sessions.sumOf { vm.L.volumen(it.entries) }), color = FogonTema.actual.texto,
                    modifier = Modifier.padding(horizontal = 16.dp))
            }
            Constancia(vm, e)
        },
        SeccionPerfil(1, Iconos.Rango, stringResource(R.string.pestana_rangos)) {
            PesoCorporal(vm, e)
            if (e.sessions.isEmpty()) PerfilVacio(Iconos.Rango, stringResource(R.string.sin_registros))
            Rangos(vm, e)
        },
        SeccionPerfil(2, IconosFogon.Estrella, stringResource(R.string.perfil_medallas)) {
            val medallas = vm.L.medallas(e, vm.hoy())
            if (medallas.none { it.lograda }) PerfilVacio(IconosFogon.Estrella, stringResource(R.string.perfil_sin_medallas))
            SeccionMedallas(vm, medallas)
        },
        SeccionPerfil(3, IconosFogon.Chispa, stringResource(R.string.records)) {
            if (e.sessions.isEmpty()) PerfilVacio(IconosFogon.Chispa, stringResource(R.string.sin_registros))
            else EjerciciosRegistrados(vm, e)
        },
    )
    val miInsignia = vm.red.cuenta.collectAsState().value?.insignia.orEmpty()
    val vista = PerfilVista(identidad.nombre?.takeIf { it.isNotBlank() } ?: stringResource(R.string.tu_nombre), usuario.orEmpty(), identidad.foto, identidad.portada, identidad.bio,
        emptyList(), true, "yo", listOf(
            ContadorPerfil(e.sessions.size.toString(), stringResource(R.string.titulo_entrenos)),
            ContadorPerfil(vm.L.racha(e, vm.L.dias(e), vm.hoy()).actual.toString(), stringResource(R.string.perfil_racha)),
            ContadorPerfil(e.sessions.flatMap { it.entries.keys }.distinct().count { vm.L.maxKg(e, it) != null || vm.L.maxReps(e, it) != null }.toString(), stringResource(R.string.records)),
        ), secciones, insignia = miInsignia)
    io.github.fogonapps.perfil.Perfil(vista.copy(tema = io.github.fogonapps.social.ui.temaPropio(interfazCuenta(vm)), marco = io.github.fogonapps.social.ui.marcoPropio(interfazCuenta(vm))), elegida, { elegida = it }, nombreSuave = identidad.nombre.isNullOrBlank(), interlineado = true,
        navegacion = {
            io.github.fogonapps.social.ui.CabeceraPerfil(usuario?.let { "@$it" } ?: stringResource(R.string.tu_perfil), vm::cerrar) {
                io.github.fogonapps.identidad.BotonIconoSocial(IconoAjustes, stringResource(R.string.ajustes)) { vm.abrir(Pantalla.Ajustes) }
            }
        },
        portada = { ImagenLocal(identidad.portada, Modifier.matchParentSize()) },
        foto = {
            Box {
                io.github.fogonapps.social.ui.AroHistoriasPropias(interfazCuenta(vm)) { FotoPerfilLocal(identidad.foto, tamano = 96.dp) }
                BotonMasFoto(stringResource(R.string.perfil_editar), Modifier.align(Alignment.BottomEnd)) { vm.abrir(Pantalla.EditarPerfil) }
            }
        }, bajoNombre = { if (usuario != null) Text("@$usuario", color = FogonTema.actual.textoSuave) }, enlace = {}, acciones = {
            io.github.fogonapps.perfil.BotonPerfil(stringResource(R.string.perfil_editar), { vm.abrir(Pantalla.EditarPerfil) }, Modifier.weight(1f))
            if (usuario != null) io.github.fogonapps.perfil.BotonPerfil(stringResource(io.github.fogonapps.social.ui.R.string.social_red_compartir_perfil), {
                contexto.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND)
                    .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, ClienteSocial.enlacePerfil(usuario!!)), null))
            }, Modifier.weight(1f))
        }, despuesAcciones = {
            usuario?.let { u -> Box(Modifier.padding(horizontal = 16.dp)) { io.github.fogonapps.social.ui.DestacadasPerfil(interfazCuenta(vm), u) }; io.github.fogonapps.social.ui.CancionPerfilPropia(interfazCuenta(vm)) } })
}

@Composable
fun EditarPerfilLocal(vm: GymViewModel, estado: Estado) {
    val usuario by vm.red.usuario.collectAsState()
    val remoto by vm.red.identidad.collectAsState()
    val fotoRemota by vm.red.foto.collectAsState()
    val portadaRemota by vm.red.portadaImagen.collectAsState()
    if (usuario != null && remoto == null) {
        Cabecera(stringResource(R.string.perfil_editar), vm::cerrar)
        Text(stringResource(R.string.cuenta_edicion_pendiente), color = FogonTema.actual.texto)
        return
    }
    val e = if (usuario == null) estado else estado.copy(nombre = remoto?.persona?.nombre,
        bio = remoto?.bio.orEmpty(), foto = fotoRemota, portada = portadaRemota)
    var nombre by rememberSaveable(e.nombre, e.bio, e.foto, e.portada) { mutableStateOf(recortarNombrePerfil(e.nombre.orEmpty())) }
    var bio by rememberSaveable(e.nombre, e.bio, e.foto, e.portada) { mutableStateOf(recortarBioPerfil(e.bio)) }
    var foto by remember(e.nombre, e.bio, e.foto, e.portada) { mutableStateOf(e.foto) }
    var portada by remember(e.nombre, e.bio, e.foto, e.portada) { mutableStateOf(e.portada) }
    var eligiendoPortada by rememberSaveable { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(false) }
    val guardando by vm.red.guardandoPerfil.collectAsState()
    var error by remember { mutableStateOf(false) }
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val manejar_selector9: (android.net.Uri?) -> Unit = { uri ->
        if (uri != null) {
            val esPortada = eligiendoPortada
            cargando = true
            error = false
            alcance.launch {
                val datos = imagenPerfilDesde(contexto, uri)
                error = datos == null
                if (datos != null) { if (esPortada) portada = datos else foto = datos }
                cargando = false
            }
        }
    }
    // Antes de usarla(s), el paso para girarlas.
    val girar_selector9 = io.github.fogonapps.social.ui.conGiro { l -> manejar_selector9(l.firstOrNull()) }
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { r -> if (r != null) girar_selector9(listOf(r)) else manejar_selector9(null) }
    Cabecera(stringResource(R.string.perfil_editar), vm::cerrar)
    io.github.fogonapps.social.ui.TiendaEnEditarPerfil(interfazCuenta(vm))
    EditorPerfilLocal(nombre, bio, foto, portada,
        onNombre = { nombre = it }, onBio = { bio = it },
        onElegirFoto = { eligiendoPortada = false; selector.launch("image/*") },
        onQuitarFoto = { foto = "" },
        onElegirPortada = { eligiendoPortada = true; selector.launch("image/*") },
        onQuitarPortada = { portada = "" },
        onGuardar = { vm.guardarPerfil(nombre, bio, foto, portada, vm::cerrar) }, habilitado = !cargando && !guardando)
    if (usuario != null) PartesDeportivas(vm)
    if (error) Text(stringResource(io.github.fogonapps.perfil.R.string.perfil_local_error), Modifier.padding(16.dp), color = FogonTema.actual.texto)
    Spacer(Modifier.height(40.dp))
}

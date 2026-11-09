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

@Composable
fun PerfilLocal(vm: GymViewModel, e: Estado) {
    var elegida by rememberSaveable { mutableIntStateOf(0) }
    val secciones = listOf(
        SeccionPerfil(0, Iconos.Pesa, stringResource(R.string.titulo_entrenos)) {
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
    val vista = PerfilVista(e.nombre?.takeIf { it.isNotBlank() } ?: stringResource(R.string.tu_nombre), "", e.foto, e.portada, e.bio,
        emptyList(), true, "yo", listOf(
            ContadorPerfil(e.sessions.size.toString(), stringResource(R.string.titulo_entrenos)),
            ContadorPerfil(vm.L.racha(e, vm.L.dias(e), vm.hoy()).actual.toString(), stringResource(R.string.perfil_racha)),
            ContadorPerfil(e.sessions.flatMap { it.entries.keys }.distinct().count { vm.L.maxKg(e, it) != null || vm.L.maxReps(e, it) != null }.toString(), stringResource(R.string.records)),
        ), secciones)
    io.github.fogonapps.perfil.Perfil(vista, elegida, { elegida = it }, nombreSuave = e.nombre.isNullOrBlank(), interlineado = true,
        navegacion = {
            Cabecera(stringResource(R.string.tu_perfil), vm::cerrar, derecha = {
                BotonCirculoPerfil(IconoAjustes, stringResource(R.string.ajustes)) { vm.abrir(Pantalla.Ajustes) }
            })
        },
        portada = { ImagenLocal(e.portada, Modifier.matchParentSize()) },
        foto = {
            Box(Modifier.size(96.dp)) {
                FotoPerfilLocal(e.foto, tamano = 96.dp)
                BotonMasFoto(stringResource(R.string.perfil_editar), Modifier.align(Alignment.BottomEnd)) { vm.abrir(Pantalla.EditarPerfil) }
            }
        }, enlace = {}, acciones = {
            BotonSecundario(stringResource(R.string.perfil_editar), { vm.abrir(Pantalla.EditarPerfil) }, Modifier.weight(1f))
        })
}

@Composable
fun EditarPerfilLocal(vm: GymViewModel, e: Estado) {
    var nombre by rememberSaveable { mutableStateOf(recortarNombrePerfil(e.nombre.orEmpty())) }
    var bio by rememberSaveable { mutableStateOf(recortarBioPerfil(e.bio)) }
    var foto by remember { mutableStateOf(e.foto) }
    var portada by remember { mutableStateOf(e.portada) }
    var eligiendoPortada by rememberSaveable { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
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
    Cabecera(stringResource(R.string.perfil_editar), vm::cerrar)
    EditorPerfilLocal(nombre, bio, foto, portada,
        onNombre = { nombre = it }, onBio = { bio = it },
        onElegirFoto = { eligiendoPortada = false; selector.launch("image/*") },
        onQuitarFoto = { foto = "" },
        onElegirPortada = { eligiendoPortada = true; selector.launch("image/*") },
        onQuitarPortada = { portada = "" },
        onGuardar = { vm.guardarPerfil(nombre, bio, foto, portada); vm.cerrar() }, habilitado = !cargando)
    if (error) Text(stringResource(io.github.fogonapps.perfil.R.string.perfil_local_error), Modifier.padding(16.dp), color = FogonTema.actual.texto)
    Spacer(Modifier.height(40.dp))
}

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

/** Perfil exclusivamente local: las cifras y el contenido no presentan montos. */
@Composable
fun Perfil(vm: AhorradorViewModel, e: Estado) {
    var pestana by rememberSaveable { mutableIntStateOf(0) }
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
        },
    )
    val vista = PerfilVista(e.nombre.orEmpty().ifBlank { stringResource(R.string.perfil_sin_nombre) }, "", e.fotoPerfil,
        e.portadaPerfil, e.bio, emptyList(), true, "yo", listOf(
            ContadorPerfil(dias.toString(), stringResource(R.string.perfil_dias)),
            ContadorPerfil(e.movimientos.size.toString(), stringResource(R.string.movimientos)),
            ContadorPerfil(cumplidas.toString(), stringResource(R.string.perfil_cumplidas)),
        ), secciones)
    io.github.fogonapps.perfil.Perfil(vista, pestana, { pestana = it }, nombreSuave = e.nombre.isNullOrBlank(), interlineado = true,
        navegacion = {
            Cabecera(stringResource(R.string.tu_perfil), vm::cerrar) {
                BotonCirculoPerfil(Iconos.Ajustes, stringResource(R.string.ajustes)) { vm.abrir(Pantalla.Ajustes) }
            }
        },
        portada = { ImagenLocal(e.portadaPerfil, Modifier.matchParentSize()) },
        foto = {
            Box {
                FotoPerfilLocal(e.fotoPerfil)
                BotonMasFoto(stringResource(R.string.perfil_elegir_foto), Modifier.align(Alignment.BottomEnd)) { vm.abrir(Pantalla.EditarPerfil) }
            }
        }, enlace = {},
        acciones = { BotonSecundario(stringResource(R.string.editar_perfil), { vm.abrir(Pantalla.EditarPerfil) }, Modifier.weight(1f)) },
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
    var nombre by rememberSaveable { mutableStateOf(recortarNombrePerfil(e.nombre.orEmpty())) }
    var bio by rememberSaveable { mutableStateOf(recortarBioPerfil(e.bio)) }
    var foto by remember { mutableStateOf(e.fotoPerfil) }
    var portada by remember { mutableStateOf(e.portadaPerfil) }
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
                if (datos != null) { if (esPortada) portada = datos else foto = datos; vm.imagenPerfil(esPortada, datos) }
                cargando = false
            }
        }
    }
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

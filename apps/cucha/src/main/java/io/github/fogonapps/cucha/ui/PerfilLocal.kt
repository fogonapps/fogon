package io.github.fogonapps.cucha.ui

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
import io.github.fogonapps.cucha.*
import io.github.fogonapps.cucha.R
import io.github.fogonapps.cucha.datos.Estado
import io.github.fogonapps.perfil.Perfil as PerfilComun
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun Perfil(vm: CuchaViewModel, e: Estado) {
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    val remoto by vm.red.identidad.collectAsStateWithLifecycle()
    val fotoRemota by vm.red.foto.collectAsStateWithLifecycle()
    val portadaRemota by vm.red.portadaImagen.collectAsStateWithLifecycle()
    val perfilesRed by vm.red.perfiles.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val acciones = LocalAcciones.current
    LaunchedEffect(usuario) { usuario?.let { vm.red.cargarIdentidad(); vm.red.verPerfil(it) } }
    // Con cuenta, la identidad es la global de Fogón Social (D-030); sin cuenta, la de este teléfono.
    val nombre = if (usuario == null) e.nombre else remoto?.persona?.nombre.orEmpty()
    val bio = if (usuario == null) e.bio else remoto?.bio.orEmpty()
    val foto = if (usuario == null) e.fotoPerfil else fotoRemota
    val portada = if (usuario == null) e.portadaPerfil else portadaRemota
    val publicadas = usuario?.let { perfilesRed[it]?.publicaciones }.orEmpty()
    val interfaz = interfazCuenta(vm)
    var parte by rememberSaveable { mutableIntStateOf(0) }
    val secciones = seccionesRed { seccion ->
        when (seccion) {
            0 -> {
                if (usuario != null) ContenidoSeccionRed(interfaz, publicadas, 0, avisoVacio = false)
                ComidasPerfil(vm, e)
            }
            1 -> {
                if (usuario != null) ContenidoSeccionRed(interfaz, publicadas, 1, avisoVacio = false)
                if (e.recetas.isEmpty()) PerfilVacio(Iconos.Recetas, stringResource(R.string.perfil_sin_recetas))
                e.recetas.forEach { receta ->
                    FilaAjuste(Iconos.Recetas, receta.nombre(vm.idioma()), null) { vm.abrir(Pantalla.VerReceta(receta.id)) }
                }
                FilaAjuste(IconosFogon.Mas, stringResource(R.string.perfil_crear_receta), null) { vm.abrir(Pantalla.EditarReceta(null)) }
            }
            else -> {
                if (usuario == null) BotonRiso(stringResource(R.string.red_entrar), { vm.abrir(Pantalla.Cuenta) }, Modifier.padding(16.dp))
                else {
                    ContenidoSeccionRed(interfaz, publicadas, seccion)
                    BotonSecundario(stringResource(R.string.red_resenar), { vm.abrir(Pantalla.ResenarRestaurante()) }, Modifier.fillMaxWidth().padding(16.dp))
                }
            }
        }
    } + if (usuario != null) listOf(SeccionPerfil(4, IconosSociales.Guardar, stringResource(R.string.red_guardados)) {
        val guardados by vm.red.guardados.collectAsStateWithLifecycle()
        LaunchedEffect(usuario) { vm.red.cargarGuardados() }
        guardados.orEmpty().forEach { io.github.fogonapps.social.ui.TarjetaPublicacion(interfaz, it) }
    }) else emptyList()
    val contadores = if (usuario != null) cifrasRed(publicadas) else listOf(
        ContadorPerfil(e.dias.count { it.value.anotados.isNotEmpty() }.toString(), stringResource(R.string.perfil_dias)),
        ContadorPerfil(e.recetas.size.toString(), stringResource(R.string.pestana_recetas)),
        ContadorPerfil(e.dias.values.sumOf { it.anotados.size }.toString(), stringResource(R.string.perfil_platos)),
    )
    val vista = PerfilVista(nombre.ifBlank { stringResource(R.string.perfil_sin_nombre) }, usuario.orEmpty(), foto, portada,
        bio, emptyList(), true, "yo", contadores = contadores, secciones = secciones)
    PerfilComun(vista, parte, { parte = it }, nombreSuave = nombre.isBlank(), interlineado = true,
        navegacion = {
            Cabecera(stringResource(R.string.tu_perfil), vm::cerrar, derecha = {
                BotonCirculoPerfil(Iconos.Ajustes, stringResource(R.string.ajustes)) { vm.abrir(Pantalla.Ajustes) }
            })
        },
        portada = { ImagenLocal(portada, Modifier.fillMaxSize()) },
        foto = {
            Box {
                FotoPerfilLocal(foto, tamano = 96.dp, vacio = { Personaje(Quien.CUCHA, null, tamano = 80.dp) })
                BotonMasFoto(stringResource(R.string.perfil_foto), Modifier.align(Alignment.BottomEnd)) { vm.abrir(Pantalla.EditarPerfil) }
            }
        }, bajoNombre = { if (usuario != null) Text("@$usuario", color = FogonTema.actual.textoSuave) }, enlace = {}, acciones = {
            BotonSecundario(stringResource(R.string.perfil_editar), { vm.abrir(Pantalla.EditarPerfil) }, Modifier.weight(1f))
            if (usuario != null) BotonSecundario(stringResource(io.github.fogonapps.social.ui.R.string.social_red_compartir_perfil), {
                contexto.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND)
                    .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, io.github.fogonapps.social.ClienteSocial.enlacePerfil(usuario!!)), null))
            }, Modifier.weight(1f))
        }, despuesAcciones = { CambiarDeCuenta(vm, acciones?.exportar ?: {}) })
}

@Composable
private fun ComidasPerfil(vm: CuchaViewModel, e: Estado) {
    val dias = e.dias.filterValues { it.anotados.isNotEmpty() }.toSortedMap(reverseOrder())
    if (dias.isEmpty()) PerfilVacio(Iconos.Plato, stringResource(R.string.perfil_sin_comidas))
    dias.forEach { (fecha, dia) ->
        Seccion(fecha)
        dia.anotados.forEach { anotado ->
            FilaAjuste(Iconos.Plato, anotado.nombre, nombreComida(anotado.comida)) {
                vm.abrir(if (anotado.ref.startsWith("rapido:")) Pantalla.Rapido(anotado.comida, fecha, anotado.id)
                    else Pantalla.Cantidad(anotado.ref, anotado.comida, fecha, anotado.id))
            }
        }
    }
}

@Composable
fun EditarPerfil(vm: CuchaViewModel, e: Estado) {
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
    Cabecera(stringResource(R.string.perfil_editar), vm::cerrar)
    EditorPerfilLocal(nombre, bio, foto, portada,
        onNombre = { nombre = it }, onBio = { bio = it },
        onElegirFoto = { eligiendoPortada = false; selector.launch("image/*") },
        onQuitarFoto = { foto = ""; vm.imagenPerfil(false, "") },
        onElegirPortada = { eligiendoPortada = true; selector.launch("image/*") },
        onQuitarPortada = { portada = ""; vm.imagenPerfil(true, "") },
        onGuardar = { vm.guardarPresentacion(nombre, bio); vm.cerrar() }, habilitado = !cargando)
    if (error) Text(stringResource(io.github.fogonapps.perfil.R.string.perfil_local_error), Modifier.padding(16.dp), color = FogonTema.actual.texto)
    Espacio(40.dp)
}

/** Con cuenta, «Editar perfil» cambia la identidad global de Fogón Social (nombre, bio, foto y portada). */
@Composable
private fun EditarPerfilRed(vm: CuchaViewModel) {
    val remoto by vm.red.identidad.collectAsStateWithLifecycle()
    val fotoRemota by vm.red.foto.collectAsStateWithLifecycle()
    val portadaRemota by vm.red.portadaImagen.collectAsStateWithLifecycle()
    val guardando by vm.red.guardandoPerfil.collectAsStateWithLifecycle()
    Cabecera(stringResource(R.string.perfil_editar), vm::cerrar)
    val r = remoto
    if (r == null) { Text(stringResource(R.string.cuenta_edicion_pendiente), Modifier.padding(16.dp), color = FogonTema.actual.texto); return }
    var nombre by rememberSaveable(r.persona.nombre, r.bio) { mutableStateOf(recortarNombrePerfil(r.persona.nombre)) }
    var bio by rememberSaveable(r.persona.nombre, r.bio) { mutableStateOf(recortarBioPerfil(r.bio)) }
    var foto by remember(fotoRemota) { mutableStateOf(fotoRemota) }
    var portada by remember(portadaRemota) { mutableStateOf(portadaRemota) }
    var eligiendoPortada by rememberSaveable { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
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
    PartesCucha(vm)
    if (error) Text(stringResource(io.github.fogonapps.perfil.R.string.perfil_local_error), Modifier.padding(16.dp), color = FogonTema.actual.texto)
    Espacio(40.dp)
}

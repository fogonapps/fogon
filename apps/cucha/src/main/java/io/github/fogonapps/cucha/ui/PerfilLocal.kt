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

/** Catálogo local ampliable cuando existan nuevas partes del perfil. */
private enum class PartePerfil { COMIDAS, RECETAS }

@Composable
fun Perfil(vm: CuchaViewModel, e: Estado) {
    var parte by rememberSaveable { mutableStateOf(PartePerfil.COMIDAS) }
    val secciones = PartePerfil.entries.map { tipo ->
        SeccionPerfil(tipo.ordinal, if (tipo == PartePerfil.COMIDAS) Iconos.Plato else Iconos.Recetas,
            stringResource(if (tipo == PartePerfil.COMIDAS) R.string.perfil_comidas else R.string.pestana_recetas)) {
            when (tipo) {
                PartePerfil.COMIDAS -> ComidasPerfil(vm, e)
                PartePerfil.RECETAS -> {
                    if (e.recetas.isEmpty()) PerfilVacio(Iconos.Recetas, stringResource(R.string.perfil_sin_recetas))
                    e.recetas.forEach { receta ->
                        FilaAjuste(Iconos.Recetas, receta.nombre(vm.idioma()), null) { vm.abrir(Pantalla.VerReceta(receta.id)) }
                    }
                    FilaAjuste(IconosFogon.Mas, stringResource(R.string.perfil_crear_receta), null) { vm.abrir(Pantalla.EditarReceta(null)) }
                }
            }
        }
    }
    val vista = PerfilVista(e.nombre.ifBlank { stringResource(R.string.perfil_sin_nombre) }, "", e.fotoPerfil, e.portadaPerfil,
        e.bio, emptyList(), true, "yo", contadores = listOf(
            ContadorPerfil(e.dias.count { it.value.anotados.isNotEmpty() }.toString(), stringResource(R.string.perfil_dias)),
            ContadorPerfil(e.recetas.size.toString(), stringResource(R.string.pestana_recetas)),
            ContadorPerfil(e.dias.values.sumOf { it.anotados.size }.toString(), stringResource(R.string.perfil_platos)),
        ), secciones = secciones)
    PerfilComun(vista, parte.ordinal, { parte = PartePerfil.entries[it] }, nombreSuave = e.nombre.isBlank(), interlineado = true,
        navegacion = {
            Cabecera(stringResource(R.string.tu_perfil), vm::cerrar, derecha = {
                BotonCirculoPerfil(Iconos.Ajustes, stringResource(R.string.ajustes)) { vm.abrir(Pantalla.Ajustes) }
            })
        },
        portada = { ImagenLocal(e.portadaPerfil, Modifier.fillMaxSize()) },
        foto = {
            Box {
                FotoPerfilLocal(e.fotoPerfil, tamano = 96.dp, vacio = { Personaje(Quien.CUCHA, null, tamano = 80.dp) })
                BotonMasFoto(stringResource(R.string.perfil_foto), Modifier.align(Alignment.BottomEnd)) { vm.abrir(Pantalla.EditarPerfil) }
            }
        }, enlace = {}, acciones = {
            BotonSecundario(stringResource(R.string.perfil_editar), { vm.abrir(Pantalla.EditarPerfil) }, Modifier.fillMaxWidth())
        })
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

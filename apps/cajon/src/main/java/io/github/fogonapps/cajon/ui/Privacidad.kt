package io.github.fogonapps.cajon.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.Pantalla
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.ClienteSocial
import io.github.fogonapps.identidad.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoliticaPrivacidad(vm: CajonViewModel) {
    val ciclo = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(ciclo) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, ev -> if (ev == androidx.lifecycle.Lifecycle.Event.ON_START) vm.red.alAbrir() }
        ciclo.lifecycle.addObserver(o); onDispose { ciclo.lifecycle.removeObserver(o) }
    }
    val mostrar by vm.red.hojaPolitica.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    val f = FogonTema.actual
    if (mostrar) ModalBottomSheet(onDismissRequest = {}, containerColor = f.fondo,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden }),
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.politica_actualizada), style = MaterialTheme.typography.headlineSmall, color = f.texto, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.politica_resumen), color = f.texto)
            BotonSecundario(stringResource(R.string.red_politica), { uri.openUri(ClienteSocial.SERVIDOR + "/privacidad") }, Modifier.fillMaxWidth())
            BotonRiso(stringResource(R.string.politica_aceptar), { vm.red.aceptarPolitica() }, Modifier.fillMaxWidth())
            BotonSecundario(stringResource(R.string.politica_ahora_no), vm.red::posponerPolitica, Modifier.fillMaxWidth())
            BotonSecundario(stringResource(R.string.red_borrar_cuenta), { vm.red.posponerPolitica(); vm.abrir(Pantalla.BorrarCuenta) }, Modifier.fillMaxWidth(), peligro = true)
        }
    }
}

@Composable
fun AvisoPolitica(vm: CajonViewModel) {
    val pendiente by vm.red.politicaPendiente.collectAsStateWithLifecycle()
    if (pendiente) Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.politica_pausa), color = FogonTema.actual.texto)
        BotonSecundario(stringResource(R.string.politica_revisar), vm.red::revisarPolitica, Modifier.fillMaxWidth())
    }
}

@Composable
fun Transparencia(vm: CajonViewModel) {
    val uri = LocalUriHandler.current
    val c = LocalContext.current
    val f = FogonTema.actual
    var revision by remember { mutableIntStateOf(0) }
    val ciclo = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(ciclo) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, ev -> if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) revision++ }
        ciclo.lifecycle.addObserver(o); onDispose { ciclo.lifecycle.removeObserver(o) }
    }
    // Los ajustes externos se vuelven a consultar al regresar de Android o de otra pantalla.
    val opciones = revision.let { vm.opcionesPrivacidad() }
    Cabecera(stringResource(R.string.privacidad_titulo), vm::cerrar)
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        @Composable fun Frases(recurso: Int) {
            stringResource(recurso).split('\n').filter { it.isNotBlank() }.forEach { Text(it, color = f.texto) }
        }
        Seccion(stringResource(R.string.privacidad_local_titulo))
        Frases(R.string.privacidad_local)
        Seccion(stringResource(R.string.privacidad_social_titulo))
        Frases(R.string.privacidad_social)
        Seccion(stringResource(R.string.privacidad_servicios_titulo))
        Frases(R.string.privacidad_servicios)
        Seccion(stringResource(R.string.privacidad_opciones))
        @Composable fun OpcionPrivada(titulo: Int, activo: Boolean, accion: () -> Unit) {
            FilaAjuste(Iconos.Candado, stringResource(titulo), stringResource(if (activo) R.string.privacidad_activa else R.string.privacidad_inactiva), accion)
        }
        OpcionPrivada(R.string.spotify_titulo, opciones.spotify) { vm.abrir(Pantalla.MusicaEscuchada) }
        OpcionPrivada(R.string.sync_titulo, opciones.github) { vm.abrir(Pantalla.Sincronizar) }
        OpcionPrivada(R.string.privacidad_lectura, opciones.leerNotificaciones) {
            c.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        OpcionPrivada(R.string.aj_notificaciones, opciones.avisos) { vm.abrir(Pantalla.AjustesAvisos) }
        OpcionPrivada(R.string.nube_titulo, opciones.copiaCifrada) { vm.abrir(Pantalla.CajonEnCuenta) }
        Frases(R.string.privacidad_opcionales)
        Seccion(stringResource(R.string.privacidad_derechos_titulo))
        BotonSecundario(stringResource(R.string.red_politica), { uri.openUri(ClienteSocial.SERVIDOR + "/privacidad") }, Modifier.fillMaxWidth())
        BotonSecundario(stringResource(R.string.privacidad_contacto), { uri.openUri("mailto:fogonapps@gmail.com") }, Modifier.fillMaxWidth())
    }
    Espacio()
}

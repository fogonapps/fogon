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

@Composable
fun PoliticaPrivacidad(vm: CajonViewModel) { io.github.fogonapps.social.ui.PoliticaPrivacidad(interfazSocial(vm)) }
@Composable
fun AvisoPolitica(vm: CajonViewModel) { io.github.fogonapps.social.ui.AvisoPolitica(interfazSocial(vm)) }

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
    io.github.fogonapps.social.ui.Transparencia(
        volver = vm::cerrar,
        textoLocal = stringResource(R.string.privacidad_local),
        textoSocial = stringResource(R.string.privacidad_social),
        servicios = stringResource(R.string.privacidad_servicios).split('\n').filter { it.isNotBlank() },
        detallesOpcionales = stringResource(R.string.privacidad_opcionales),
        opciones = listOf(
            io.github.fogonapps.social.ui.OpcionPrivacidad(stringResource(R.string.spotify_titulo), opciones.spotify) { vm.abrir(Pantalla.MusicaEscuchada) },
            io.github.fogonapps.social.ui.OpcionPrivacidad(stringResource(R.string.sync_titulo), opciones.github) { vm.abrir(Pantalla.Sincronizar) },
            io.github.fogonapps.social.ui.OpcionPrivacidad(stringResource(R.string.privacidad_lectura), opciones.leerNotificaciones) {
                c.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
            io.github.fogonapps.social.ui.OpcionPrivacidad(stringResource(R.string.aj_notificaciones), opciones.avisos) { vm.abrir(Pantalla.AjustesAvisos) },
            io.github.fogonapps.social.ui.OpcionPrivacidad(stringResource(R.string.nube_titulo), opciones.copiaCifrada) { vm.abrir(Pantalla.CajonEnCuenta) },
        ),
    )
}

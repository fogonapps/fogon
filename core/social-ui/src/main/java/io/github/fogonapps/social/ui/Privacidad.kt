package io.github.fogonapps.social.ui
import io.github.fogonapps.identidad.SeccionSocial as Seccion
import io.github.fogonapps.identidad.IconosSociales as Iconos

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
import io.github.fogonapps.identidad.*

import io.github.fogonapps.social.ClienteSocial
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoliticaPrivacidad(vm: InterfazSocial) {
    val rutaPolitica = stringResource(R.string.social_ruta_politica)
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
            Text(stringResource(R.string.social_politica_actualizada), style = MaterialTheme.typography.headlineSmall, color = f.texto, modifier = Modifier.semantics { heading() })
            Text(vm.textosApp.resumenPolitica, color = f.texto)
            BotonSecundario(stringResource(R.string.social_red_politica), { uri.openUri(ClienteSocial.SERVIDOR + rutaPolitica) }, Modifier.fillMaxWidth())
            BotonRiso(stringResource(R.string.social_politica_aceptar), { vm.red.aceptarPolitica() }, Modifier.fillMaxWidth())
            BotonSecundario(stringResource(R.string.social_politica_ahora_no), vm.red::posponerPolitica, Modifier.fillMaxWidth())
            BotonSecundario(stringResource(R.string.social_red_borrar_cuenta), { vm.red.posponerPolitica(); vm.navegacion.ajustes(AjusteSocial.BORRAR_CUENTA) }, Modifier.fillMaxWidth(), peligro = true)
        }
    }
}

@Composable
fun AvisoPolitica(vm: InterfazSocial) {
    val pendiente by vm.red.politicaPendiente.collectAsStateWithLifecycle()
    if (pendiente) Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(vm.textosApp.pausaPolitica, color = FogonTema.actual.texto)
        BotonSecundario(stringResource(R.string.social_politica_revisar), vm.red::revisarPolitica, Modifier.fillMaxWidth())
    }
}


data class OpcionPrivacidad(val titulo: String, val activa: Boolean, val abrir: () -> Unit)

@Composable
fun Transparencia(
    volver: () -> Unit,
    textoLocal: String,
    textoSocial: String,
    servicios: List<String>,
    detallesOpcionales: String,
    opciones: List<OpcionPrivacidad>,
) {
    val rutaPolitica = stringResource(R.string.social_ruta_politica)
    val uri = LocalUriHandler.current
    val f = FogonTema.actual
    @Composable fun Frases(texto: String) {
        texto.split('\n').filter { it.isNotBlank() }.forEach { Text(it, color = f.texto) }
    }
    Cabecera(stringResource(R.string.social_privacidad_titulo), volver)
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Seccion(stringResource(R.string.social_privacidad_local_titulo))
        Frases(textoLocal)
        Seccion(stringResource(R.string.social_privacidad_social_titulo))
        Frases(textoSocial)
        Seccion(stringResource(R.string.social_privacidad_servicios_titulo))
        servicios.forEach { Text(it, color = f.texto) }
        Seccion(stringResource(R.string.social_privacidad_opciones))
        opciones.forEach { opcion ->
            FilaAjuste(Iconos.Candado, opcion.titulo, stringResource(if (opcion.activa) R.string.social_privacidad_activa else R.string.social_privacidad_inactiva), opcion.abrir)
        }
        Frases(detallesOpcionales)
        Seccion(stringResource(R.string.social_privacidad_derechos_titulo))
        BotonSecundario(stringResource(R.string.social_red_politica), { uri.openUri(ClienteSocial.SERVIDOR + rutaPolitica) }, Modifier.fillMaxWidth())
        BotonSecundario(stringResource(R.string.social_privacidad_contacto), { uri.openUri("mailto:fogonapps@gmail.com") }, Modifier.fillMaxWidth())
    }
    androidx.compose.foundation.layout.Spacer(Modifier.height(100.dp).width(1.dp))
}

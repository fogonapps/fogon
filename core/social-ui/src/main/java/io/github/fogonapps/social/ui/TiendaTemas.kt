package io.github.fogonapps.social.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.identidad.*
import io.github.fogonapps.identidad.PildoraSocial as Pildora
import io.github.fogonapps.perfil.MuestraTema
import io.github.fogonapps.perfil.TemasPerfil

/** El tema que tienes puesto en tu perfil (para dibujar tu propia cabecera). */
@Composable
fun temaPropio(vm: InterfazSocial): String {
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    val temas by vm.red.temas.collectAsStateWithLifecycle()
    LaunchedEffect(usuario) { vm.red.cargarTemas() }
    return if (usuario == null) "" else temas.puesto
}

/** El botón del perfil propio que abre la tienda de temas (solo con cuenta: el tema lo ven los demás). */
@Composable
fun BotonTiendaTemas(vm: InterfazSocial, modifier: Modifier = Modifier) {
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    if (usuario == null) return
    var abierta by remember { mutableStateOf(false) }
    Box(modifier) { Pildora(stringResource(R.string.social_temas_tienda), IconosFogon.Estrella) { abierta = true } }
    if (abierta) TiendaTemas(vm) { abierta = false }
}

/**
 * La tienda de temas del perfil: cada tema con su muestra; se reclama (por ahora, gratis) y después se pone. El que
 * tienes puesto lo ven todos los que entran a tu perfil, en cualquiera de las apps.
 */
@Composable
fun TiendaTemas(vm: InterfazSocial, onCerrar: () -> Unit) {
    val f = FogonTema.actual
    val temas by vm.red.temas.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.red.cargarTemas() }
    Dialog(onCerrar, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(f.fondo).systemBarsPadding()) {
            Cabecera(stringResource(R.string.social_temas_tienda), onCerrar)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PistaSocial(stringResource(R.string.social_temas_explica))
                (listOf("") + TemasPerfil.TODOS).chunked(2).forEach { fila ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        fila.forEach { tema ->
                            val reclamado = tema.isEmpty() || tema in temas.reclamados
                            val puesto = temas.puesto == tema
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                MuestraTema(tema, Modifier.fillMaxWidth())
                                Text(TemasPerfil.descripcion(tema), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, minLines = 2, maxLines = 2)
                                when {
                                    puesto -> BotonSecundario(stringResource(R.string.social_temas_en_uso), {}, Modifier.fillMaxWidth(), habilitado = false)
                                    reclamado -> BotonSecundario(stringResource(R.string.social_temas_usar), { vm.red.ponerTema(tema) }, Modifier.fillMaxWidth())
                                    else -> BotonRiso(stringResource(R.string.social_temas_reclamar), { vm.red.reclamarTema(tema) }, Modifier.fillMaxWidth())
                                }
                            }
                        }
                        if (fila.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

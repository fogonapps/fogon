package io.github.fogonapps.social.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.identidad.*
import io.github.fogonapps.identidad.IconosSociales as Iconos
import io.github.fogonapps.identidad.FilaMenuSocial as FilaMenu
import io.github.fogonapps.identidad.BotonIconoSocial as BotonIcono
import io.github.fogonapps.social.ClienteSocial

/**
 * La barra de arriba de un perfil (el tuyo o el de otra persona), como en las redes de fotos: la flecha sola y liviana,
 * el nombre chico en una línea y, a la derecha, los botones (íconos sin círculo).
 */
@Composable
fun CabeceraPerfil(titulo: String, volver: (() -> Unit)?, derecha: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (volver != null) Box(Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable(onClick = volver), contentAlignment = androidx.compose.ui.Alignment.Center) {
            IconoRiso(Iconos.Atras, stringResource(R.string.social_volver), tamano = 22.dp, corrida = null)
        }
        Text(titulo, style = MaterialTheme.typography.titleMedium, color = FogonTema.actual.texto, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { derecha() }
    }
}

/**
 * La barra de arriba del perfil de otra persona, la misma en las cuatro apps: «@usuario» chico y un solo botón (⋯) con
 * copiar el enlace y compartir (siempre), y bloquear y reportar (con cuenta).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CabeceraPerfilAjeno(vm: InterfazSocial, usuario: String) {
    val f = FogonTema.actual
    val ctx = LocalContext.current
    val yo by vm.red.usuario.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var hoja by remember { mutableStateOf<String?>(null) }
    val enlace = ClienteSocial.enlacePerfil(usuario)
    CabeceraPerfil("@$usuario", vm.navegacion.volver) {
        Box {
            BotonIcono(Iconos.Opciones, stringResource(R.string.social_red_mas_opciones)) { menu = true }
            val portapapeles = androidx.compose.ui.platform.LocalClipboardManager.current
            androidx.compose.material3.DropdownMenu(menu, { menu = false }, shape = forma(24.dp), containerColor = f.fondo,
                border = androidx.compose.foundation.BorderStroke(f.borde, f.linea), shadowElevation = 12.dp) {
                @Composable
                fun Opcion(icono: Dibujo, texto: String, peligro: Boolean = false, onClick: () -> Unit) {
                    val color = if (peligro) MaterialTheme.colorScheme.error else f.texto
                    androidx.compose.material3.DropdownMenuItem({ Text(texto, style = MaterialTheme.typography.bodyLarge, color = color) }, { menu = false; onClick() },
                        leadingIcon = { IconoRiso(icono, null, tamano = 22.dp, tinta = color) }, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp))
                }
                Opcion(Iconos.Enlace, stringResource(R.string.social_menu_copiar_enlace)) { portapapeles.setText(androidx.compose.ui.text.AnnotatedString(enlace)) }
                Opcion(Iconos.Compartir, stringResource(R.string.social_perfil_compartir)) {
                    ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND)
                        .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, enlace), null))
                }
                if (yo != null && yo != usuario) {
                    Opcion(Iconos.Candado, stringResource(R.string.social_perfil_bloquear)) { hoja = "bloquear" }
                    Opcion(Iconos.Bandera, stringResource(R.string.social_red_reportar), peligro = true) { hoja = "reportar" }
                }
            }
        }
    }
    hoja?.let { h ->
        androidx.compose.material3.ModalBottomSheet({ hoja = null }, containerColor = f.fondo) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                if (h == "reportar") MotivosReporte { codigo, texto -> vm.red.reportar("cuenta", usuario, "$codigo: $texto"); hoja = null }
                else {
                    Text(stringResource(R.string.social_perfil_bloquear_a, usuario), style = MaterialTheme.typography.titleMedium, color = f.texto, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                    Text(stringResource(R.string.social_perfil_bloquear_explica), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave, modifier = Modifier.padding(horizontal = 24.dp))
                    FilaMenu(Iconos.Candado, stringResource(R.string.social_perfil_bloquear), peligro = true) { vm.red.bloquear(usuario, true); hoja = null; vm.navegacion.volver() }
                    FilaMenu(Iconos.Atras, stringResource(R.string.social_cancelar)) { hoja = null }
                }
            }
        }
    }
}

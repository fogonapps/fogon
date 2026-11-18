package io.github.fogonapps.social.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.identidad.PildoraSocial
import io.github.fogonapps.social.ArticuloMercado
import io.github.fogonapps.social.PerfilSocial
import kotlinx.coroutines.CancellationException

/** Artículos del mercado que lleva el perfil ajeno, con el crédito de cada creador. */
@Composable
fun MercadoEnPerfil(vm: InterfazSocial, perfil: PerfilSocial) {
    val yo by vm.red.usuario.collectAsStateWithLifecycle()
    if (perfil.persona.usuario == yo || perfil.relacion == "yo") return
    val banner = idArticuloMercado(perfil.portada.substringBefore('~').takeIf { it.startsWith("banner:m") }.orEmpty())
    val marco = idArticuloMercado("marco:${perfil.persona.marco}")
    val ids = listOfNotNull(banner, marco).distinct()
    if (ids.isEmpty()) return

    var abierto by remember(perfil.persona.usuario, yo, ids) { mutableStateOf<Long?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ids.forEach { id ->
            val articulo by produceState<ArticuloMercado?>(null, vm.red, yo, id) {
                value = null
                try { value = vm.red.articuloMercado(id) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* El artículo puede haber dejado de estar disponible. */ }
            }
            articulo?.let { a ->
                val texto = if (a.tipo == "banner") R.string.social_mercado_ver_banner else R.string.social_mercado_ver_marco
                PildoraSocial(stringResource(texto, a.creador)) { abierto = a.id }
            }
        }
    }
    abierto?.let { id -> AbrirFichaMercado(vm, id) { abierto = null } }
}

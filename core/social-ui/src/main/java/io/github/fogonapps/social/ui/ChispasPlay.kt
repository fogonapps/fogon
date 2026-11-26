package io.github.fogonapps.social.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.tarjetaFogon
import io.github.fogonapps.social.EstadoPlay
import io.github.fogonapps.social.PaquetePlay
import io.github.fogonapps.social.TiendaPlay

/** En la versión de Play se vende con Google Play Billing, si la app trae su pasarela. */
internal fun ofrecePagosPlay(tiendaPlay: Boolean, hayPasarela: Boolean): Boolean = tiendaPlay && hayPasarela

/**
 * «Conseguir chispas» en la versión de Play: los paquetes que decide el servidor, con el precio que da Google en la
 * moneda de cada persona (nunca un precio fijo), y el apoyo voluntario aparte. Sin prisas ni cuentas regresivas.
 */
@Composable
internal fun ConseguirChispasPlay(tienda: TiendaPlay, soloChispas: Boolean) {
    val f = FogonTema.actual
    val paquetes by tienda.paquetes.collectAsStateWithLifecycle()
    val estado by tienda.estado.collectAsStateWithLifecycle()
    val actividad = LocalActivity.current
    val contadores = contadoresDeLaApp()
    LaunchedEffect(tienda) { tienda.cargar() }
    val lista = paquetes ?: return
    val ocupada = estado == EstadoPlay.Comprando
    fun comprar(p: PaquetePlay) {
        val a = actividad ?: return
        contadores.contar("tienda.paquete_elegido")
        tienda.comprar(a, p.id)
    }
    if (lista.isEmpty()) {
        if (soloChispas) {
            Text(stringResource(R.string.social_play_sin_paquetes), color = f.textoSuave)
            BotonSecundario(stringResource(R.string.social_mercado_reintentar), { tienda.cargar() })
        }
        return
    }
    if (!soloChispas) Text(stringResource(R.string.social_wompi_titulo), style = MaterialTheme.typography.titleMedium, color = f.texto)
    lista.filterNot { it.unico }.forEach { p ->
        Row(Modifier.fillMaxWidth().tarjetaFogon().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconoChispa(24.dp)
            Text(if (p.regalo > 0) stringResource(R.string.social_play_paquete_regalo, numeroSocial(p.chispas), numeroSocial(p.regalo))
                else stringResource(R.string.social_play_paquete, numeroSocial(p.chispas)),
                Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = f.texto)
            BotonRiso(p.precio, { comprar(p) }, habilitado = !ocupada)
        }
    }
    Text(stringResource(R.string.social_play_explica), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
    MensajePlay(estado, tienda)
    lista.filter { it.unico }.forEach { p ->
        Column(Modifier.fillMaxWidth().tarjetaFogon().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.social_play_apoyo_titulo), style = MaterialTheme.typography.titleMedium, color = f.texto)
            Text(stringResource(R.string.social_play_apoyo_texto, numeroSocial(p.chispas + p.regalo)), color = f.textoSuave)
            BotonSecundario(p.precio, { comprar(p) }, Modifier.fillMaxWidth(), habilitado = !ocupada)
        }
    }
}

@Composable
private fun MensajePlay(estado: EstadoPlay, tienda: TiendaPlay) {
    val texto = when (estado) {
        EstadoPlay.Libre -> return
        EstadoPlay.Comprando -> stringResource(R.string.social_play_comprando)
        EstadoPlay.Pendiente -> stringResource(R.string.social_play_pendiente)
        is EstadoPlay.Acreditada -> stringResource(R.string.social_play_acreditada, numeroSocial(estado.chispas))
        is EstadoPlay.Error -> stringResource(textoErrorPlay(estado.motivo))
    }
    Text(texto, Modifier.fillMaxWidth(), color = FogonTema.actual.texto)
    // El aviso se queda hasta la próxima compra; al salir de la tienda se olvida.
    androidx.compose.runtime.DisposableEffect(tienda) { onDispose { tienda.visto() } }
}

internal fun textoErrorPlay(motivo: String): Int = when (motivo) {
    "cancelada" -> R.string.social_play_cancelada
    "no_disponible" -> R.string.social_play_no_disponible
    "conexion" -> R.string.social_play_conexion
    "rechazada" -> R.string.social_play_rechazada
    "sesion" -> R.string.social_play_sesion
    "ya_tenia" -> R.string.social_play_ya_tenia
    else -> R.string.social_play_error
}

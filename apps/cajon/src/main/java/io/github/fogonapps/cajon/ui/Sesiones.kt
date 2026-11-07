package io.github.fogonapps.cajon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.R
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FogonTema
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable
internal fun ReenviarCodigo(vm: CajonViewModel) {
    val desde by vm.red.reenviarDesde.collectAsStateWithLifecycle()
    val acceso by vm.red.acceso.collectAsStateWithLifecycle()
    var segundos by remember(desde) { mutableStateOf(60L) }
    LaunchedEffect(desde) {
        do {
            segundos = ((desde - android.os.SystemClock.elapsedRealtime() + 999) / 1000).coerceAtLeast(0)
            if (segundos > 0) delay(250)
        } while (segundos > 0)
    }
    BotonSecundario(if (segundos > 0) stringResource(R.string.red_reenviar_en, segundos) else stringResource(R.string.red_reenviar),
        { vm.red.reenviarCodigo() }, Modifier.fillMaxWidth(), habilitado = segundos == 0L && !acceso.ocupado)
}

@Composable
fun Sesiones(vm: CajonViewModel) {
    val f = FogonTema.actual
    val confirmar = LocalConfirmar.current
    val sesiones by vm.red.sesiones.collectAsStateWithLifecycle()
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.red.cargarSesiones() }
    LaunchedEffect(usuario) { if (usuario == null) vm.cerrar() }
    Cabecera(stringResource(R.string.red_sesiones), vm::cerrar)
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BotonSecundario(stringResource(R.string.red_actualizar_sesiones), { vm.red.cargarSesiones() }, Modifier.fillMaxWidth())
        val lista = sesiones
        if (lista == null) LinearProgressIndicator(Modifier.fillMaxWidth(), color = f.tinta1, trackColor = f.linea)
        else {
            lista.forEach { sesion ->
                Text(sesion.dispositivo ?: stringResource(R.string.red_dispositivo_desconocido), color = f.texto)
                if (sesion.actual) Pista(stringResource(R.string.red_este_telefono))
                val formato = DateFormat.getDateTimeInstance()
                Pista(stringResource(R.string.red_sesion_fechas, formato.format(Date(sesion.creada)), formato.format(Date(sesion.ultimoUso))))
                BotonSecundario(stringResource(R.string.red_cerrar_sesion), {
                    confirmar(vm.tx(R.string.red_cerrar_sesion), vm.tx(R.string.red_cerrar_sesion_confirmar)) {
                        vm.red.cerrarSesion(sesion.id, sesion.actual)
                    }
                }, Modifier.fillMaxWidth())
            }
            BotonSecundario(stringResource(R.string.red_cerrar_otras), {
                confirmar(vm.tx(R.string.red_cerrar_otras), vm.tx(R.string.red_cerrar_otras_confirmar)) { vm.red.cerrarOtrasSesiones() }
            }, Modifier.fillMaxWidth(), habilitado = lista.any { !it.actual })
        }
    }
}

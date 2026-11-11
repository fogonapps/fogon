package io.github.fogonapps.social.ui

import io.github.fogonapps.identidad.PistaSocial as Pista
import io.github.fogonapps.identidad.PildoraSocial as Pildora
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.identidad.*
import io.github.fogonapps.perfil.InsigniaCuenta
import kotlinx.coroutines.launch

/**
 * X-08: el equipo da o quita la insignia de una cuenta (permiso «gestionar usuarios», con MFA). Verificada a mano,
 * nunca (aunque cumpla la regla) o de vuelta a la regla automática. «Oficial de Fogón» solo la da la cuenta principal.
 * Cada cambio pide un motivo y queda en el registro del equipo.
 */
@Composable
internal fun InsigniasEquipo(vm: InterfazSocial) {
    val f = FogonTema.actual
    val scope = rememberCoroutineScope()
    val ocupada by vm.red.equipoOcupado.collectAsStateWithLifecycle()
    val cuenta by vm.red.cuenta.collectAsStateWithLifecycle()
    var usuario by remember { mutableStateOf("") }
    var insignia by remember { mutableStateOf("verificada") }
    var motivo by remember { mutableStateOf("") }
    var listo by remember { mutableStateOf<String?>(null) }
    val opciones = buildList {
        add("verificada" to R.string.insignias_verificada)
        if (cuenta?.rol == "principal") add("fogon" to R.string.insignias_fogon)
        add("no" to R.string.insignias_no)
        add("auto" to R.string.insignias_auto)
    }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Pista(stringResource(R.string.insignias_explica))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InsigniaCuenta("verificada", tamano = 18.dp)
            Text(stringResource(R.string.insignias_regla), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        }
        OutlinedTextField(usuario, { usuario = it.removePrefix("@").trim().take(40); listo = null }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.social_equipo_usuario)) }, shape = forma(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            opciones.take(2).forEach { (valor, texto) -> Pildora(stringResource(texto), acento = insignia == valor) { insignia = valor } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            opciones.drop(2).forEach { (valor, texto) -> Pildora(stringResource(texto), acento = insignia == valor) { insignia = valor } }
        }
        OutlinedTextField(motivo, { motivo = it.take(500) }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.insignias_motivo)) }, shape = forma(18.dp))
        listo?.let { Text(it, color = f.texto) }
        BotonRiso(stringResource(R.string.insignias_aplicar), {
            vm.confirmar(vm.tx(R.string.insignias_aplicar), vm.tx(R.string.insignias_confirmar)) {
                scope.launch {
                    val hecho = vm.red.operarEquipo { it.cambiarInsignia(usuario, insignia, motivo.trim()) }
                    if (hecho != null) { listo = vm.tx(R.string.insignias_hecho); motivo = "" }
                }
            }
        }, habilitado = !ocupada && usuario.isNotBlank() && motivo.isNotBlank())
    }
}

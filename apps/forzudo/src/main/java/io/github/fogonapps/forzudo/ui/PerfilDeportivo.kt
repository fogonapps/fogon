package io.github.fogonapps.forzudo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fogonapps.forzudo.GymViewModel
import io.github.fogonapps.forzudo.R
import io.github.fogonapps.forzudo.datos.partesDeportivas
import io.github.fogonapps.identidad.*

/** Qué partes deportivas se ven en el perfil de la red, como «Quién lo ve» de El Cajón. Nunca cifras del cuerpo. */
@Composable
fun PartesDeportivas(vm: GymViewModel) {
    val f = FogonTema.actual
    val remoto by vm.red.identidad.collectAsState()
    var rangos by remember(remoto) { mutableStateOf(vm.red.visibilidad("rangos")) }
    var medallas by remember(remoto) { mutableStateOf(vm.red.visibilidad("medallas")) }
    val opciones = listOf("privado" to stringResource(R.string.cuenta_privado), "publica" to stringResource(R.string.cuenta_publico),
        "amigos" to stringResource(R.string.cuenta_seguidores))
    Seccion(stringResource(R.string.cuenta_quien_lo_ve)) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.cuenta_que_se_comparte), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            listOf(Triple(R.string.cuenta_rangos, rangos) { v: String -> rangos = v },
                Triple(R.string.cuenta_medallas, medallas) { v: String -> medallas = v }).forEach { (titulo, valor, cambiar) ->
                Column(Modifier.fillMaxWidth().tarjetaFogon().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(titulo), style = MaterialTheme.typography.bodyLarge, color = f.texto)
                    SelectorPildoras(opciones, valor, cambiar)
                }
            }
            BotonRiso(stringResource(io.github.fogonapps.social.ui.R.string.social_guardar), {
                vm.estado.value?.let { vm.red.publicarPartes(partesDeportivas(it, vm.L, vm.hoy(), rangos, medallas)) }
            }, Modifier.fillMaxWidth())
        }
    }
}

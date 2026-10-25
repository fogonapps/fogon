package io.github.fogonapps.cajon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.Tipos
import io.github.fogonapps.identidad.AlmacenApariencia
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.Paleta
import io.github.fogonapps.identidad.PasoDaleVida
import io.github.fogonapps.identidad.Personaje
import io.github.fogonapps.identidad.aparecer
import io.github.fogonapps.identidad.R as RId

/** Primera vez: bienvenida, el nombre y, al final, "¡Dale vida!" para elegir el estilo. */
@Composable
fun Tutorial(vm: CajonViewModel, acciones: Acciones) {
    val f = FogonTema.actual
    var paso by rememberSaveable { mutableIntStateOf(0) }
    var nombre by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (paso) {
            0 -> Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Personaje(RId.drawable.personaje_el_cajon, null, Modifier.align(Alignment.CenterHorizontally), tamano = 180.dp)
                Text(stringResource(R.string.tut_hola), style = MaterialTheme.typography.headlineLarge, color = f.texto,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.tut_hola_texto), style = MaterialTheme.typography.bodyLarge, color = f.textoSuave,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                // Lo que cabe en el cajón.
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Tipos.TODOS.forEachIndexed { i, t ->
                        Column(Modifier.aparecer(i), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconoRiso(Iconos.de(t), null, tamano = 26.dp, tinta = f.acentoTexto)
                            Text(nombreTipo(t), style = MaterialTheme.typography.labelSmall, color = f.textoSuave)
                        }
                    }
                }
                BotonRiso(stringResource(R.string.siguiente), { paso = 1 }, Modifier.fillMaxWidth())
                BotonSecundario(stringResource(R.string.tut_tengo_respaldo), acciones.importar, Modifier.fillMaxWidth())
            }
            1 -> Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.tut_nombre), style = MaterialTheme.typography.headlineSmall, color = f.texto)
                CampoTexto(nombre, { nombre = it }, stringResource(R.string.tu_nombre))
                Text(stringResource(R.string.tut_privado), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
                BotonRiso(stringResource(R.string.siguiente), { paso = 2 }, Modifier.fillMaxWidth())
            }
            else -> Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PasoDaleVida(Paleta.Cajon, AlmacenApariencia.de(LocalContext.current))
                BotonRiso(stringResource(R.string.empezar), { vm.terminarTutorial(nombre) }, Modifier.fillMaxWidth())
            }
        }
    }
}

package io.github.fogonapps.cucha.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import io.github.fogonapps.cucha.CuchaViewModel
import io.github.fogonapps.cucha.R
import io.github.fogonapps.cucha.datos.Metas
import io.github.fogonapps.cucha.datos.Perfil
import io.github.fogonapps.identidad.AlmacenApariencia
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.Paleta
import io.github.fogonapps.identidad.PasoDaleVida
import io.github.fogonapps.identidad.PersonajeTenido
import io.github.fogonapps.identidad.R as RId

/**
 * Primera vez: bienvenida, los datos para calcular las metas, las metas (calculadas o propias) y, al final,
 * "¡Dale vida!" para elegir el estilo.
 */
@Composable
fun Tutorial(vm: CuchaViewModel, onImportar: () -> Unit) {
    val f = FogonTema.actual
    var paso by rememberSaveable { mutableIntStateOf(0) }
    var nombre by rememberSaveable { mutableStateOf("") }
    var perfil by rememberSaveable(stateSaver = perfilGuardable) { mutableStateOf(Perfil()) }
    var propias by rememberSaveable { mutableStateOf(false) }
    var kcal by rememberSaveable { mutableStateOf("") }
    var prot by rememberSaveable { mutableStateOf("") }
    var carb by rememberSaveable { mutableStateOf("") }
    var gras by rememberSaveable { mutableStateOf("") }
    // Si está El Forzudo, el peso viene de allá (solo si la persona todavía no llegó a ese paso).
    val forzudo by vm.forzudo.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(forzudo) {
        forzudo?.peso?.let { if (paso == 0) perfil = perfil.copy(pesoKg = it.kg) }
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (paso) {
            0 -> Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PersonajeTenido(RId.drawable.personaje_la_cucha_t1, RId.drawable.personaje_la_cucha_t2, RId.drawable.personaje_la_cucha_fijo,
                    null, Modifier.align(Alignment.CenterHorizontally), tamano = 170.dp)
                Text(stringResource(R.string.tut_hola), style = MaterialTheme.typography.headlineLarge, color = f.texto,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.tut_hola_texto), style = MaterialTheme.typography.bodyLarge, color = f.textoSuave,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                BotonRiso(stringResource(R.string.siguiente), { paso = 1 }, Modifier.fillMaxWidth())
                BotonSecundario(stringResource(R.string.tut_tengo_respaldo), onImportar, Modifier.fillMaxWidth())
                // El aviso de salud, también en el tutorial (docs/LEGAL.md → Aviso de salud).
                Text(stringResource(R.string.aviso_salud), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, textAlign = TextAlign.Center)
            }
            1 -> {
                Text(stringResource(R.string.tut_datos), style = MaterialTheme.typography.headlineMedium, color = f.texto, modifier = Modifier.padding(horizontal = 24.dp))
                Text(stringResource(R.string.tut_datos_texto), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave, modifier = Modifier.padding(horizontal = 24.dp))
                DatosPersona(vm, nombre, perfil, pesoDeForzudo = forzudo?.peso != null, textoBoton = stringResource(R.string.siguiente)) { n, p ->
                    nombre = n; perfil = p; paso = 2
                }
            }
            2 -> Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val m = vm.L.metasDe(perfil)
                Text(stringResource(R.string.tut_metas), style = MaterialTheme.typography.headlineMedium, color = f.texto)
                Text(stringResource(R.string.tut_metas_texto, m.kcal, m.proteina, m.carbos, m.grasa), style = MaterialTheme.typography.bodyLarge, color = f.texto)
                Row(Modifier.fillMaxWidth().vidrio().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tut_metas_propias), color = f.texto, modifier = Modifier.weight(1f))
                    Switch(propias, { propias = it })
                }
                if (propias) {
                    CampoNumero(kcal, { kcal = it }, stringResource(R.string.calorias), Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CampoNumero(prot, { prot = it }, stringResource(R.string.proteina_campo), Modifier.weight(1f))
                        CampoNumero(carb, { carb = it }, stringResource(R.string.carbos_campo), Modifier.weight(1f))
                        CampoNumero(gras, { gras = it }, stringResource(R.string.grasa_campo), Modifier.weight(1f))
                    }
                }
                Text(stringResource(R.string.tut_metas_nota), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BotonSecundario(stringResource(R.string.volver), { paso = 1 }, Modifier.weight(1f))
                    BotonRiso(stringResource(R.string.siguiente), { paso = 3 }, Modifier.weight(1f))
                }
            }
            else -> Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PasoDaleVida(Paleta.Cucha, AlmacenApariencia.de(LocalContext.current))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BotonSecundario(stringResource(R.string.volver), { paso = 2 }, Modifier.weight(1f))
                    BotonRiso(stringResource(R.string.tut_empezar), {
                        val calc = vm.L.metasDe(perfil)
                        vm.empezar(nombre, perfil, if (!propias) null else Metas(
                            kcal.toIntOrNull()?.coerceIn(800, 8000) ?: calc.kcal, prot.toIntOrNull() ?: calc.proteina,
                            carb.toIntOrNull() ?: calc.carbos, gras.toIntOrNull() ?: calc.grasa, calc.aguaMl, manual = true,
                        ))
                    }, Modifier.weight(1f))
                }
            }
        }
        // Puntos del paso actual
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            repeat(4) { i -> Box(Modifier.size(8.dp).background(if (i == paso) f.tinta1 else f.linea, CircleShape)) }
        }
    }
}

/** Para que el perfil del tutorial sobreviva a girar el teléfono. */
private val perfilGuardable = androidx.compose.runtime.saveable.Saver<Perfil, String>(
    save = { io.github.fogonapps.cucha.datos.JsonCucha.encodeToString(Perfil.serializer(), it) },
    restore = { io.github.fogonapps.cucha.datos.JsonCucha.decodeFromString(Perfil.serializer(), it) },
)

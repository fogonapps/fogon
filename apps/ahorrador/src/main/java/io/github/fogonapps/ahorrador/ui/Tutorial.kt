package io.github.fogonapps.ahorrador.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fogonapps.ahorrador.AhorradorViewModel
import io.github.fogonapps.ahorrador.R
import io.github.fogonapps.ahorrador.datos.Sistema
import io.github.fogonapps.identidad.Bagel
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.FormaRiso
import io.github.fogonapps.identidad.Personaje
import io.github.fogonapps.identidad.R as RId

/** Nombre y explicación de cada sistema; se usan aquí y en Perfil. */
val Sistema.nombre get() = when (this) {
    Sistema.SOBRES -> R.string.sistema_sobres
    Sistema.REGLA_50_30_20 -> R.string.sistema_503020
    Sistema.KAKEIBO -> R.string.sistema_kakeibo
    Sistema.SOLO_ANOTAR -> R.string.sistema_solo
}

val Sistema.explicacion get() = when (this) {
    Sistema.SOBRES -> R.string.sistema_sobres_texto
    Sistema.REGLA_50_30_20 -> R.string.sistema_503020_texto
    Sistema.KAKEIBO -> R.string.sistema_kakeibo_texto
    Sistema.SOLO_ANOTAR -> R.string.sistema_solo_texto
}

/** Tarjeta para elegir un sistema: nombre en Bagel y su explicación. */
@Composable
fun TarjetaSistema(s: Sistema, elegido: Boolean, onElegir: () -> Unit) {
    val f = FogonTema.actual
    Column(
        Modifier.fillMaxWidth().then(if (elegido) Modifier.border(2.dp, f.paleta.tinta, FormaRiso) else Modifier).vidrio()
            .clickable(onClick = onElegir).semantics { selected = elegido; role = Role.RadioButton }.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(s.nombre), fontFamily = Bagel, fontSize = 18.sp, color = if (elegido) f.acentoTexto else f.texto)
        Text(stringResource(s.explicacion), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
    }
}

/** Primera vez: bienvenida y nombre, cómo quiere organizarse y con cuánta plata empieza. */
@Composable
fun Tutorial(vm: AhorradorViewModel, onImportar: () -> Unit) {
    val f = FogonTema.actual
    var paso by rememberSaveable { mutableIntStateOf(0) }
    var nombre by rememberSaveable { mutableStateOf("") }
    var sistema by rememberSaveable { mutableStateOf(Sistema.SOBRES) }
    var efectivo by rememberSaveable { mutableStateOf("") }
    var banco by rememberSaveable { mutableStateOf("") }
    var billetera by rememberSaveable { mutableStateOf("") }
    var conTarjeta by rememberSaveable { mutableStateOf(false) }
    var deudaTarjeta by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (paso) {
            0 -> {
                Personaje(RId.drawable.personaje_el_ahorrador, null, Modifier.align(Alignment.CenterHorizontally), tamano = 170.dp)
                Text(stringResource(R.string.tut_hola), style = MaterialTheme.typography.headlineLarge, color = f.texto,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.tut_hola_texto), style = MaterialTheme.typography.bodyLarge, color = f.textoSuave,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                CampoTexto(nombre, { nombre = it }, stringResource(R.string.tut_tu_nombre))
                BotonRiso(stringResource(R.string.siguiente), { paso = 1 }, Modifier.fillMaxWidth())
                BotonSecundario(stringResource(R.string.tut_tengo_respaldo), onImportar, Modifier.fillMaxWidth())
            }
            1 -> {
                Text(stringResource(R.string.tut_sistema), style = MaterialTheme.typography.headlineMedium, color = f.texto)
                Text(stringResource(R.string.tut_sistema_texto), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
                Sistema.entries.forEach { s -> TarjetaSistema(s, s == sistema) { sistema = s } }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BotonSecundario(stringResource(R.string.volver), { paso = 0 }, Modifier.weight(1f))
                    BotonRiso(stringResource(R.string.siguiente), { paso = 2 }, Modifier.weight(1f))
                }
            }
            else -> {
                Text(stringResource(R.string.tut_saldos), style = MaterialTheme.typography.headlineMedium, color = f.texto)
                Text(stringResource(R.string.tut_saldos_texto), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
                CampoPesos(efectivo, { efectivo = it }, stringResource(R.string.cuenta_efectivo))
                CampoPesos(banco, { banco = it }, stringResource(R.string.cuenta_banco))
                CampoPesos(billetera, { billetera = it }, stringResource(R.string.tut_billetera))
                Row(Modifier.fillMaxWidth().vidrio().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tut_tengo_tarjeta), color = f.texto, modifier = Modifier.weight(1f))
                    Switch(conTarjeta, { conTarjeta = it })
                }
                if (conTarjeta) CampoPesos(deudaTarjeta, { deudaTarjeta = it }, stringResource(R.string.tut_deuda_tarjeta))
                Text(stringResource(R.string.tut_privado), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BotonSecundario(stringResource(R.string.volver), { paso = 1 }, Modifier.weight(1f))
                    BotonRiso(stringResource(R.string.tut_empezar), {
                        vm.empezar(nombre, sistema, efectivo.toLongOrNull() ?: 0, banco.toLongOrNull() ?: 0, billetera.toLongOrNull() ?: 0,
                            if (conTarjeta) deudaTarjeta.toLongOrNull() ?: 0 else null)
                    }, Modifier.weight(1f))
                }
            }
        }
        // Puntos del paso actual
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            repeat(3) { i ->
                androidx.compose.foundation.layout.Box(
                    Modifier.padding(2.dp).then(Modifier.background(if (i == paso) f.paleta.tinta else f.linea, androidx.compose.foundation.shape.CircleShape))
                        .padding(4.dp),
                )
            }
        }
    }
}

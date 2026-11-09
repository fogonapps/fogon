package io.github.fogonapps.identidad

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PersonalizacionLuz(paleta: Paleta, almacen: AlmacenApariencia, a: Apariencia) {
    val f = FogonTema.actual
    val luz = a.de().luz
    var intensidadExcedida by remember(luz?.color, luz?.segundo) { mutableStateOf(false) }
    var selector by remember { mutableStateOf<Int?>(null) }
    var nombre by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Encabezado(stringResource(R.string.fogon_ap_luz), Modifier.padding(top = 24.dp, bottom = 8.dp))
        Interruptor(stringResource(R.string.fogon_ap_luz), stringResource(R.string.fogon_ap_luz_texto), a.adorno() == Decoracion.LUZ) {
            almacen.ponerAdorno(a.estilo, if (it) Decoracion.LUZ else Decoracion.NADA)
        }
        Interruptor(stringResource(R.string.fogon_luz_tema), "", luz == null) {
            almacen.ponerLuz(a.estilo, if (it) null else LuzPropia(a.colores(paleta).brillo.copy(alpha = 1f)).legible(a.colores(paleta)))
        }
        if (luz != null) {
            BotonSecundario(stringResource(R.string.fogon_luz_color), onClick = { selector = 0 })
            Interruptor(stringResource(R.string.fogon_luz_segundo), "", luz.segundo != null) {
                almacen.ponerLuz(a.estilo, luz.copy(segundo = if (it) luz.color else null))
            }
            if (luz.segundo != null) BotonSecundario(stringResource(R.string.fogon_luz_segundo), onClick = { selector = 1 })
            BarraLuz(stringResource(R.string.fogon_luz_intensidad), luz.legible(a.colores(paleta)).intensidad, 0f..100f) {
                val pedida = luz.copy(intensidad = it)
                val efectiva = pedida.legible(a.colores(paleta))
                intensidadExcedida = efectiva.intensidad < it
                almacen.ponerLuz(a.estilo, efectiva)
            }
            BarraLuz(stringResource(R.string.fogon_luz_tamano), luz.tamano, 50f..150f) {
                almacen.ponerLuz(a.estilo, luz.copy(tamano = it))
            }
            if (intensidadExcedida)
                Text(stringResource(R.string.fogon_luz_legible), color = f.textoSuave)
        }
        Encabezado(stringResource(R.string.fogon_tintas_guardadas), Modifier.padding(top = 24.dp, bottom = 8.dp))
        OutlinedTextField(nombre, { nombre = it.take(60) }, label = { Text(stringResource(R.string.fogon_tintas_nombre), color = f.texto) }, modifier = Modifier.fillMaxWidth())
        if (a.guardadas.size < 12 && nombre.isNotBlank()) BotonSecundario(stringResource(R.string.fogon_tintas_guardar), onClick = {
            almacen.guardarTintas(nombre, paleta); nombre = ""
        })
        a.guardadas.forEachIndexed { i, t ->
            Column(Modifier.fillMaxWidth().tarjetaFogon(forma(20.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t.nombre, Modifier.weight(1f), color = f.texto)
                    listOf(Ranura.TINTA_1, Ranura.TINTA_2).forEach { ranura ->
                        Box(Modifier.size(24.dp).clip(CircleShape).background(t.ajuste.colores[ranura] ?: a.color(paleta, ranura)))
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotonSecundario(stringResource(R.string.fogon_tintas_aplicar, t.nombre), onClick = { almacen.aplicarTintas(t) })
                    BotonSecundario(stringResource(R.string.fogon_tintas_borrar, t.nombre), onClick = { almacen.borrarTintas(i) })
                }
            }
        }
    }
    selector?.let { cual ->
        if (luz != null) DialogoColor(stringResource(if (cual == 0) R.string.fogon_luz_color else R.string.fogon_luz_segundo),
            if (cual == 0) luz.color else luz.segundo ?: luz.color, a.colores(paleta).brillo.copy(alpha = 1f),
            onListo = { c -> almacen.ponerLuz(a.estilo, if (cual == 0) luz.copy(color = c ?: a.colores(paleta).brillo.copy(alpha = 1f)) else luz.copy(segundo = c)); selector = null },
            onCerrar = { selector = null })
    }
}

@Composable
private fun BarraLuz(nombre: String, valor: Int, rango: ClosedFloatingPointRange<Float>, cambiar: (Int) -> Unit) {
    Text("$nombre: $valor %", color = FogonTema.actual.texto)
    Slider(valor.toFloat(), { cambiar(it.toInt()) }, valueRange = rango, steps = 99,
        modifier = Modifier.semantics { contentDescription = nombre })
}

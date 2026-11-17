package io.github.fogonapps.perfil

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.fogonapps.identidad.*

/** Decenas de color y centenas de forma independientes; la escala vuelve a empezar. */
fun decenaNivel(nivel: Int): Int = nivel.coerceAtLeast(0) / 10 % 10
fun formaNivel(nivel: Int): Int = nivel.coerceAtLeast(0) / 100 % 2
fun tintaNivel(nivel: Int, gris: Color): Color = when (decenaNivel(nivel)) {
    1 -> Tintas.Rojo; 2 -> Tintas.Naranja; 3 -> Tintas.Amarillo; 4 -> Tintas.Verde
    5 -> Tintas.Azul; 6 -> Tintas.Violeta; 7 -> Tintas.Rosa; 8 -> Tintas.Cafe; 9 -> Tintas.Dorado
    else -> gris
}

@Composable
fun DistintivoNivel(nivel: Int, tamano: Dp = 44.dp) {
    val f = FogonTema.actual
    val color = tintaNivel(nivel, f.textoSuave)
    Box(Modifier.size(tamano), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(3.dp)) {
            val borde = Stroke(2.dp.toPx())
            if (formaNivel(nivel) == 0) drawCircle(color, style = borde)
            else drawPath(Path().apply {
                moveTo(size.width * .5f, 0f); lineTo(size.width, size.height * .25f)
                lineTo(size.width, size.height * .75f); lineTo(size.width * .5f, size.height)
                lineTo(0f, size.height * .75f); lineTo(0f, size.height * .25f); close()
            }, color, style = borde)
        }
        Text(nivel.toString(), fontFamily = Puntos, style = if (tamano > 48.dp) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge, color = f.texto)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BloqueNivel(vista: PerfilVista) {
    var abierto by remember(vista.usuario) { mutableStateOf(false) }
    val f = FogonTema.actual
    val descripcion = stringResource(R.string.perfil_nivel, vista.nivel)
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).semantics { contentDescription = descripcion }.clickable { vista.abrirNivel?.invoke(); abierto = true }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        DistintivoNivel(vista.nivel, 76.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.perfil_nivel, vista.nivel), style = MaterialTheme.typography.titleLarge, color = f.texto)
            if (vista.relacion == "yo") {
                if (vista.nivel < 12) {
                    LinearProgressIndicator(progress = { (vista.nivel % 3) / 3f }, modifier = Modifier.fillMaxWidth(), color = f.tinta1, trackColor = f.linea)
                    Text(stringResource(R.string.perfil_nivel_faltan, 3 - vista.nivel % 3), color = f.textoSuave)
                } else Text(stringResource(R.string.perfil_nivel_maximo), color = f.textoSuave)
            }
        }
    }
    if (abierto) ModalBottomSheet(onDismissRequest = { abierto = false }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.perfil_nivel, vista.nivel), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.perfil_nivel_explica))
            vista.insigniasNivel.forEach { id ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconoRiso(iconoInsigniaNivel(id), null, tamano = 28.dp)
                    Text(nombreInsigniaNivel(id))
                }
            }
            if (vista.insigniasNivel.isEmpty()) Text(stringResource(R.string.perfil_nivel_sin_insignias))
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun iconoInsigniaNivel(id: String): Dibujo = when (id) {
    "constancia" -> IconosFogon.Racha
    else -> IconosFogon.Estrella
}

@Composable
private fun nombreInsigniaNivel(id: String) = stringResource(when (id) {
    "cine" -> R.string.escaparate_ins_cine; "series" -> R.string.escaparate_ins_series
    "anime" -> R.string.escaparate_ins_anime; "lector" -> R.string.escaparate_ins_lector
    "discos" -> R.string.escaparate_ins_discos; "constancia" -> R.string.escaparate_ins_constancia
    "critico" -> R.string.escaparate_ins_critico; "explorador" -> R.string.escaparate_ins_explorador
    else -> R.string.escaparate_ins_reto
})

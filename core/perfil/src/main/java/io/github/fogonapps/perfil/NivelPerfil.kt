package io.github.fogonapps.perfil

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.clearAndSetSemantics
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
private fun ContornoNivel(nivel: Int, modifier: Modifier, trazo: Dp) {
    val color = tintaNivel(nivel, FogonTema.actual.textoSuave)
    Canvas(modifier.padding(trazo / 2)) {
        val borde = Stroke(trazo.toPx())
        if (formaNivel(nivel) == 0) drawCircle(color, style = borde)
        else drawPath(Path().apply {
            moveTo(size.width * .5f, 0f); lineTo(size.width, size.height * .25f)
            lineTo(size.width, size.height * .75f); lineTo(size.width * .5f, size.height)
            lineTo(0f, size.height * .75f); lineTo(0f, size.height * .25f); close()
        }, color, style = borde)
    }
}

@Composable
fun DistintivoNivel(nivel: Int, tamano: Dp = 44.dp) {
    Box(Modifier.size(tamano), contentAlignment = Alignment.Center) {
        ContornoNivel(nivel, Modifier.fillMaxSize().padding(3.dp), 2.dp)
        Text(nivel.toString(), fontFamily = Puntos, style = if (tamano > 48.dp) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge, color = FogonTema.actual.texto)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NivelChico(vista: PerfilVista) {
    if (vista.nivel <= 0) return
    var abierto by remember(vista.usuario) { mutableStateOf(false) }
    val f = FogonTema.actual
    val descripcion = stringResource(R.string.perfil_nivel, vista.nivel)
    Box(Modifier.rebote().clickable {
        if (vista.relacion == "yo" && vista.abrirNivel != null) vista.abrirNivel.invoke()
        else abierto = true
    }.minimumInteractiveComponentSize().semantics(mergeDescendants = true) { contentDescription = descripcion; role = Role.Button }, contentAlignment = Alignment.Center) {
        Row(Modifier.height(20.dp).clip(forma(10.dp)).background(f.superficie).padding(horizontal = 6.dp).clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ContornoNivel(vista.nivel, Modifier.size(12.dp), 1.5.dp)
            Text(vista.nivel.toString(), style = MaterialTheme.typography.labelMedium, color = f.texto)
        }
    }
    if (abierto) ModalBottomSheet(onDismissRequest = { abierto = false }) {
        NivelDetalle(vista.nivel, vista.insigniasNivel, propio = false,
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp))
    }
}

@Composable
fun NivelDetalle(nivel: Int, insignias: List<String>, propio: Boolean, gratis: Int? = null, modifier: Modifier = Modifier) {
    val f = FogonTema.actual
    Column(modifier.fillMaxWidth().tarjetaFogon().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DistintivoNivel(nivel, 56.dp)
            Text(stringResource(R.string.perfil_nivel, nivel), style = MaterialTheme.typography.titleMedium, color = f.texto)
        }
        if (propio) {
            if (nivel < 12) {
                val faltan = 3 - nivel % 3
                val texto = pluralStringResource(R.plurals.perfil_nivel_faltan, faltan, faltan)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.semantics { contentDescription = texto }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(3) { i -> Box(Modifier.size(10.dp).background(if (i < nivel % 3) f.tinta1 else f.linea, forma(5.dp))) }
                    }
                    Text(texto, style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
                }
            } else Text(stringResource(R.string.perfil_nivel_maximo), color = f.textoSuave)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.perfil_nivel_como), style = MaterialTheme.typography.titleSmall, color = f.texto)
            Text(stringResource(R.string.perfil_nivel_explica), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.perfil_nivel_desbloquea), style = MaterialTheme.typography.titleSmall, color = f.texto)
            if (gratis != null) Text(stringResource(R.string.perfil_nivel_escaparates, gratis), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
            Text(stringResource(R.string.perfil_nivel_aspecto), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.perfil_nivel_suman), style = MaterialTheme.typography.titleSmall, color = f.texto)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                insignias.forEach { id ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconoRiso(iconoInsigniaNivel(id), null, tamano = 24.dp)
                        Text(nombreInsigniaNivel(id), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
                    }
                }
                if (insignias.isEmpty()) Text(stringResource(R.string.perfil_nivel_sin_insignias), color = f.textoSuave)
            }
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

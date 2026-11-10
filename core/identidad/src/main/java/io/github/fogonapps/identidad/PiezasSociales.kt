package io.github.fogonapps.identidad

import io.github.fogonapps.identidad.tarjetaFogon
import io.github.fogonapps.identidad.FormaPildora
import io.github.fogonapps.identidad.forma
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import io.github.fogonapps.identidad.contraste
import io.github.fogonapps.identidad.rebote
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.fogonapps.identidad.Dibujo
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.tarjetaDestacada
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.FormaRiso
import io.github.fogonapps.identidad.brillo
import io.github.fogonapps.identidad.haloPuntos
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset


@Composable
fun SeccionSocial(titulo: String, modifier: Modifier = Modifier, derecha: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(titulo, style = MaterialTheme.typography.titleLarge, color = FogonTema.actual.texto,
            modifier = Modifier.weight(1f).semantics { heading() })
        derecha()
    }
}

@Composable
fun PistaSocial(texto: String, modifier: Modifier = Modifier, centro: Boolean = false, adentro: Boolean = false) {
    Text(texto, style = MaterialTheme.typography.bodySmall, color = FogonTema.actual.textoSuave,
        textAlign = if (centro) TextAlign.Center else TextAlign.Start,
        modifier = modifier.fillMaxWidth().padding(horizontal = if (adentro) 0.dp else 20.dp, vertical = 4.dp))
}

@Composable
fun FilaVidrioSocial(onClick: (() -> Unit)? = null, modifier: Modifier = Modifier, adentro: Boolean = false, contenido: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = if (adentro) 0.dp else 16.dp, vertical = 5.dp).tarjetaFogon(sombra = false)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 60.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), content = contenido,
    )
}

@Composable
fun CampoTextoSocial(valor: String, onCambio: (String) -> Unit, etiqueta: String, modifier: Modifier = Modifier, max: Int = 40) {
    OutlinedTextField(valor, { onCambio(it.take(max)) }, modifier.fillMaxWidth(), label = { Text(etiqueta) }, singleLine = true)
}

@Composable
fun CabeceraSocial(titulo: String, onAtras: (() -> Unit)?, descripcionAtras: String, icono: Dibujo = IconosSociales.Atras, chico: Boolean = false, derecha: @Composable () -> Unit = {}) {
    val f = FogonTema.actual
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (onAtras != null) Box(Modifier.size(46.dp).brillo(f.tinta1, CircleShape).clip(CircleShape).background(f.superficie).border(f.borde, f.linea, CircleShape)
            .clickable(onClick = onAtras), contentAlignment = Alignment.Center) {
            IconoRiso(icono, descripcionAtras, tamano = 22.dp, corrida = null)
        }
        if (chico) Text(titulo, style = MaterialTheme.typography.titleMedium, color = FogonTema.actual.texto, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        else Text(titulo, fontFamily = FogonTema.actual.titulos, fontSize = 22.sp, color = FogonTema.actual.texto, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 26.sp, modifier = Modifier.weight(1f))
        derecha()
    }
}

@Composable
fun PildoraSocial(texto: String, icono: Dibujo? = null, acento: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val f = FogonTema.actual
    Row(
        modifier.rebote().clip(FormaPildora).background(if (acento) f.tinta1 else f.superficie)
            .border(f.borde, if (acento) f.tinta1 else f.linea, FormaPildora)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icono != null) IconoRiso(icono, null, tamano = 16.dp, tinta = if (acento) f.sobreTinta1 else f.texto)
        Text(texto, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (acento) f.sobreTinta1 else f.texto, maxLines = 1)
    }
}

@Composable
fun FilaMenuSocial(icono: io.github.fogonapps.identidad.Dibujo, texto: String, peligro: Boolean = false, onClick: () -> Unit) {
    val f = FogonTema.actual
    val color = if (peligro) MaterialTheme.colorScheme.error else f.texto
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        IconoRiso(icono, null, tamano = 20.dp, tinta = color)
        Text(texto, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

@Composable
fun <T> ChipsSocial(opciones: List<Pair<T, String>>, elegido: T, onElegir: (T) -> Unit) {
    val f = FogonTema.actual
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opciones.forEach { (v, t) ->
            FilterChip(v == elegido, { onElegir(v) }, { Text(t) }, shape = FormaPildora,
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = f.paleta.tinta, selectedLabelColor = f.paleta.sobreTinta))
        }
    }
}

@Composable
fun BotonIconoSocial(icono: Dibujo, descripcion: String, grande: Boolean = false, activo: Boolean = true, tinta: Color? = null, lleno: Boolean = false, onClick: () -> Unit) {
    val f = FogonTema.actual
    Box(
        Modifier.rebote(0.9f).size(if (grande) 46.dp else 40.dp).clip(CircleShape)
            .then(if (grande) Modifier.background(f.superficie).border(f.borde, f.linea, CircleShape) else Modifier)
            .clickable(enabled = activo, onClick = onClick).semantics { contentDescription = descripcion; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) { IconoRiso(icono, null, tamano = if (grande) 22.dp else 20.dp, tinta = tinta ?: f.texto, relleno = if (lleno) tinta ?: f.texto else null) }
}


@Composable
fun ConCuentaSocial(n: Int, boton: @Composable () -> Unit) {
    val f = FogonTema.actual
    Box {
        boton()
        if (n > 0) Box(Modifier.align(Alignment.TopEnd).clip(CircleShape).background(f.tinta1).padding(horizontal = 5.dp, vertical = 1.dp)) {
            Text(if (n > 9) "9+" else "$n", style = MaterialTheme.typography.labelSmall, color = f.sobreTinta1)
        }
    }
}

@Composable
fun OpcionSocial(texto: String, si: Boolean, onCambio: (Boolean) -> Unit) {
    val f = FogonTema.actual
    Row(Modifier.fillMaxWidth().clip(forma(14.dp)).clickable { onCambio(!si) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = f.texto, modifier = Modifier.weight(1f))
        Switch(si, onCambio, colors = SwitchDefaults.colors(checkedTrackColor = f.tinta1, checkedThumbColor = f.sobreTinta1))
    }
}

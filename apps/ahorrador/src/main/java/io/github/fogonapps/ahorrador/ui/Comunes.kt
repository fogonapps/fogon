package io.github.fogonapps.ahorrador.ui

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fogonapps.ahorrador.R
import io.github.fogonapps.ahorrador.datos.Formato
import io.github.fogonapps.identidad.Anton
import io.github.fogonapps.identidad.Bagel
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.FormaRiso
import io.github.fogonapps.identidad.brillo
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Color guardado como Long ARGB. */
fun colorDe(c: Long) = Color(c.toInt())

/** Tarjeta de vidrio: blanco al 8 %, borde fino y esquinas grandes. */
@Composable
fun Modifier.vidrio(forma: androidx.compose.ui.graphics.Shape = FormaRiso): Modifier {
    val f = FogonTema.actual
    return clip(forma).background(f.superficie).border(1.dp, f.linea, forma)
}

/** La tarjeta destacada de cada pantalla: degradado de las dos luces de la app, con brillo. */
@Composable
fun Modifier.destacada(): Modifier {
    val p = FogonTema.actual.paleta
    val forma = RoundedCornerShape(28.dp)
    return brillo(p.segunda, forma, 24.dp).clip(forma).background(Brush.linearGradient(listOf(p.segunda, p.luz2.copy(alpha = 0.85f))))
}

/** Plata en Anton. */
@Composable
fun Pesos(n: Long, tamano: TextUnit = 18.sp, color: Color = FogonTema.actual.texto, modifier: Modifier = Modifier) {
    Text(Formato.pesos(n), fontFamily = Anton, fontSize = tamano, color = color, modifier = modifier, maxLines = 1)
}

/** Nombre de tarjeta (cuenta, sobre, meta) en Bagel Fat One. */
@Composable
fun Nombre(texto: String, tamano: TextUnit = 15.sp, color: Color = FogonTema.actual.texto, modifier: Modifier = Modifier) {
    Text(texto, fontFamily = Bagel, fontSize = tamano, color = color, modifier = modifier, maxLines = 1)
}

/** Encabezado de sección en Anton, en mayúsculas. */
@Composable
fun Seccion(titulo: String, modifier: Modifier = Modifier, derecha: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(titulo.uppercase(), fontFamily = Anton, fontSize = 18.sp, letterSpacing = 0.06.sp * 10, color = FogonTema.actual.texto,
            modifier = Modifier.weight(1f).semantics { heading() })
        derecha()
    }
}

@Composable
fun Pista(texto: String, modifier: Modifier = Modifier, centro: Boolean = false) {
    Text(texto, style = MaterialTheme.typography.bodySmall, color = FogonTema.actual.textoSuave,
        textAlign = if (centro) TextAlign.Center else TextAlign.Start,
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp))
}

/** Ficha redonda de una categoría o cuenta: su tinta, su ícono oscuro y un brillo de su color. */
@Composable
fun Ficha(color: Color, icono: ImageVector, tamano: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(tamano).brillo(color.copy(alpha = 0.7f), CircleShape, 12.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Icon(icono, null, tint = Color(0xFF0D0C0A), modifier = Modifier.size(tamano * 0.5f))
    }
}

/** Fila de lista en vidrio: ficha, textos y lo que vaya a la derecha. */
@Composable
fun FilaVidrio(onClick: (() -> Unit)? = null, modifier: Modifier = Modifier, contenido: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).vidrio()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 60.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), content = contenido,
    )
}

/** Monto en una píldora con contorno, como en los diseños. */
@Composable
fun PildoraPesos(n: Long, relleno: Boolean = false) {
    val f = FogonTema.actual
    val forma = RoundedCornerShape(50)
    Box(
        Modifier.clip(forma).then(if (relleno) Modifier.background(Color.White) else Modifier.border(1.dp, f.texto.copy(alpha = 0.35f), forma))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) { Pesos(n, 16.sp, if (relleno) Color(0xFF0D0C0A) else f.texto) }
}

/** Barra de progreso redondeada. */
@Composable
fun Barra(progreso: Float, color: Color, modifier: Modifier = Modifier) {
    val f = FogonTema.actual
    Box(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(f.linea)) {
        Box(Modifier.fillMaxWidth(progreso.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(color))
    }
}

/** Botón redondo blanco con ícono oscuro (acciones rápidas). */
@Composable
fun AccionRedonda(icono: ImageVector, texto: String, onClick: () -> Unit, fondo: Color = Color.White) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(4.dp)) {
        Box(Modifier.size(62.dp).clip(CircleShape).background(fondo), contentAlignment = Alignment.Center) {
            Icon(icono, null, tint = Color(0xFF0D0C0A), modifier = Modifier.size(24.dp))
        }
        Text(texto, style = MaterialTheme.typography.labelMedium, color = FogonTema.actual.texto)
    }
}

/** Campo de plata: solo dígitos, muestra el número con separadores debajo. */
@Composable
fun CampoPesos(valor: String, onCambio: (String) -> Unit, etiqueta: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        valor, { onCambio(it.filter(Char::isDigit).take(12)) }, modifier.fillMaxWidth(),
        label = { Text(etiqueta) }, singleLine = true, prefix = { Text("$ ") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = valor.toLongOrNull()?.let { { Text(Formato.pesos(it)) } },
    )
}

@Composable
fun CampoTexto(valor: String, onCambio: (String) -> Unit, etiqueta: String, modifier: Modifier = Modifier, max: Int = 40) {
    OutlinedTextField(valor, { onCambio(it.take(max)) }, modifier.fillMaxWidth(), label = { Text(etiqueta) }, singleLine = true)
}

@Composable
fun CampoNumero(valor: String, onCambio: (String) -> Unit, etiqueta: String, modifier: Modifier = Modifier, decimales: Boolean = false) {
    OutlinedTextField(
        valor, { t -> onCambio(t.filter { it.isDigit() || (decimales && (it == ',' || it == '.')) }.take(6)) }, modifier,
        label = { Text(etiqueta) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimales) KeyboardType.Decimal else KeyboardType.Number),
    )
}

/** Elegir una opción entre varias, como píldoras. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Selector(opciones: List<Pair<T, String>>, elegido: T, onElegir: (T) -> Unit, modifier: Modifier = Modifier) {
    val f = FogonTema.actual
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        opciones.forEach { (v, t) ->
            FilterChip(v == elegido, { onElegir(v) }, { Text(t) }, shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = f.paleta.tinta, selectedLabelColor = f.paleta.sobreTinta))
        }
    }
}

/** Botón con la fecha; al tocarlo abre el calendario (no deja elegir días futuros si [hastaHoy]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotonFecha(fecha: String, onElegir: (String) -> Unit, hoy: LocalDate, hastaHoy: Boolean = true) {
    var abierto by remember { mutableStateOf(false) }
    val f = FogonTema.actual
    TextButton({ abierto = true }, Modifier.vidrio(RoundedCornerShape(50))) { Text(Formato.fechaCorta(fecha), color = f.texto) }
    if (abierto) {
        val ms = LocalDate.parse(fecha).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val tope = hoy.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = ms,
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = !hastaHoy || utcTimeMillis <= tope
            },
        )
        DatePickerDialog(
            onDismissRequest = { abierto = false },
            confirmButton = {
                TextButton({
                    estado.selectedDateMillis?.let { onElegir(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    abierto = false
                }) { Text(stringResource(R.string.listo)) }
            },
            dismissButton = { TextButton({ abierto = false }) { Text(stringResource(R.string.cancelar)) } },
        ) { DatePicker(estado) }
    }
}

/** Barra superior de las pantallas que se abren encima: atrás (círculo blanco), título y algo a la derecha. */
@Composable
fun Cabecera(titulo: String, onAtras: (() -> Unit)?, icono: ImageVector = Iconos.Atras, derecha: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (onAtras != null) Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White).clickable(onClick = onAtras), contentAlignment = Alignment.Center) {
            Icon(icono, stringResource(R.string.volver), tint = Color(0xFF0D0C0A), modifier = Modifier.size(20.dp))
        }
        Text(titulo, fontFamily = Bagel, fontSize = 22.sp, color = FogonTema.actual.texto, maxLines = 1, modifier = Modifier.weight(1f))
        derecha()
    }
}

/** Dato pequeño: etiqueta arriba, valor en Anton. */
@Composable
fun Dato(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = FogonTema.actual.textoSuave)
        Text(valor, fontFamily = Anton, fontSize = 20.sp, color = FogonTema.actual.texto, maxLines = 1)
    }
}

@Composable
fun Etiqueta(texto: String, fondo: Color, color: Color) {
    Text(texto, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(fondo).padding(horizontal = 10.dp, vertical = 3.dp))
}

@Composable
fun Espacio(alto: Dp = 100.dp) = androidx.compose.foundation.layout.Spacer(Modifier.height(alto).width(1.dp))

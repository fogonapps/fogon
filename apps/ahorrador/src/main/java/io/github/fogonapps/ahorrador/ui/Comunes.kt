package io.github.fogonapps.ahorrador.ui

import io.github.fogonapps.identidad.tarjetaFogon
import io.github.fogonapps.identidad.FormaPildora
import io.github.fogonapps.identidad.forma
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
import io.github.fogonapps.identidad.contraste
import io.github.fogonapps.identidad.rebote
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.fogonapps.identidad.Dibujo
import io.github.fogonapps.identidad.FormaMancha
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.tarjetaDestacada
import androidx.compose.ui.res.stringResource
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
import io.github.fogonapps.ahorrador.R
import io.github.fogonapps.ahorrador.datos.Formato
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.FormaRiso
import io.github.fogonapps.identidad.brillo
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Color guardado como Long ARGB. */
fun colorDe(c: Long) = Color(c.toInt())

/** Tarjeta de las filas: papel de tarjeta con borde de tinta, sin sombra (las listas quedan tranquilas). */
@Composable
fun Modifier.vidrio(forma: androidx.compose.ui.graphics.Shape = FormaRiso): Modifier {
    return tarjetaFogon(forma, sombra = false)
}

/** La tarjeta destacada de cada pantalla: un bloque de la primera tinta con la segunda corrida detrás. */
@Composable
fun Modifier.destacada(): Modifier = tarjetaDestacada(forma(28.dp))

/** El tamaño del número grande de puntos según cuántas cifras tenga, para que nunca se corte. */
fun tamanoGrande(texto: String) = when {
    texto.length <= 9 -> 40.sp
    texto.length <= 11 -> 34.sp
    else -> 28.sp
}

/** Plata en la letra de las cifras. */
@Composable
fun Pesos(n: Long, tamano: TextUnit = 18.sp, color: Color = FogonTema.actual.texto, modifier: Modifier = Modifier) {
    Text(Formato.pesos(n), fontFamily = FogonTema.actual.cifras, fontSize = tamano, color = color, modifier = modifier, maxLines = 1)
}

/** Nombre de tarjeta (cuenta, sobre, meta) en la letra de los títulos. */
@Composable
fun Nombre(texto: String, tamano: TextUnit = 15.sp, color: Color = FogonTema.actual.texto, modifier: Modifier = Modifier) {
    Text(texto, fontFamily = FogonTema.actual.titulos, fontSize = tamano, color = color, modifier = modifier, maxLines = 1)
}

/** Encabezado de sección, como una frase. */
@Composable
fun Seccion(titulo: String, modifier: Modifier = Modifier, derecha: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(titulo, style = MaterialTheme.typography.titleLarge, color = FogonTema.actual.texto,
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

/**
 * Ficha de una categoría o cuenta, igual en todas: un círculo de vidrio teñido de su color y el ícono de
 * línea en ese color (aclarado u oscurecido para que se distinga del papel).
 */
@Composable
fun Ficha(color: Color, icono: Dibujo, tamano: Dp = 44.dp, modifier: Modifier = Modifier) {
    val f = FogonTema.actual
    val linea = if (contraste(color, f.fondo) >= 3.0) color else lerp(color, f.texto, 0.45f)
    Box(modifier.size(tamano).clip(CircleShape).background(color.copy(alpha = if (f.oscuro) 0.18f else 0.14f)).border(f.borde, f.linea, CircleShape),
        contentAlignment = Alignment.Center) {
        IconoRiso(icono, null, tamano = tamano * 0.5f, tinta = linea)
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
    val forma = FormaPildora
    Box(
        Modifier.clip(forma).then(if (relleno) Modifier.background(f.pildora) else Modifier.border(f.borde, f.linea, forma))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) { Pesos(n, 16.sp, if (relleno) f.sobrePildora else f.texto) }
}

/** Barra de progreso redondeada. */
@Composable
fun Barra(progreso: Float, color: Color, modifier: Modifier = Modifier) {
    val f = FogonTema.actual
    Box(modifier.fillMaxWidth().height(10.dp).clip(forma(5.dp)).background(f.superficie).border(1.5.dp, f.linea, forma(5.dp))) {
        Box(Modifier.fillMaxWidth(io.github.fogonapps.identidad.progresoAnimado(progreso.coerceIn(0f, 1f))).fillMaxHeight().clip(forma(4.dp)).background(color))
    }
}

/** Acción rápida: un círculo de vidrio con su ícono, o lleno de la primera tinta si es la principal ([acento]). */
@Composable
fun AccionRedonda(icono: Dibujo, texto: String, onClick: () -> Unit, acento: Boolean = false) {
    val f = FogonTema.actual
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.rebote(0.92f).clip(forma(16.dp)).clickable(onClick = onClick).padding(4.dp)) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(if (acento) f.tinta1 else f.superficie)
            .border(f.borde, if (acento) f.tinta1 else f.linea, CircleShape), contentAlignment = Alignment.Center) {
            IconoRiso(icono, null, tamano = 24.dp, tinta = if (acento) f.sobreTinta1 else f.texto)
        }
        Text(texto, style = MaterialTheme.typography.labelMedium, color = f.texto)
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
            FilterChip(v == elegido, { onElegir(v) }, { Text(t) }, shape = FormaPildora,
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
    TextButton({ abierto = true }, Modifier.vidrio(FormaPildora)) { Text(Formato.fechaCorta(fecha), color = f.texto) }
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
fun Cabecera(titulo: String, onAtras: (() -> Unit)?, icono: Dibujo = Iconos.Atras, derecha: @Composable () -> Unit = {}) {
    val f = FogonTema.actual
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (onAtras != null) Box(Modifier.size(46.dp).brillo(f.tinta1, CircleShape).clip(CircleShape).background(f.superficie).border(f.borde, f.linea, CircleShape)
            .clickable(onClick = onAtras), contentAlignment = Alignment.Center) {
            IconoRiso(icono, stringResource(R.string.volver), tamano = 22.dp, corrida = null)
        }
        Text(titulo, fontFamily = FogonTema.actual.titulos, fontSize = 22.sp, color = FogonTema.actual.texto, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        derecha()
    }
}

/** Dato pequeño: etiqueta arriba, valor en la letra de las cifras. */
@Composable
fun Dato(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = FogonTema.actual.textoSuave)
        Text(valor, fontFamily = FogonTema.actual.cifras, fontSize = 20.sp, color = FogonTema.actual.texto, maxLines = 1)
    }
}

@Composable
fun Etiqueta(texto: String, fondo: Color, color: Color) {
    Text(texto, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color,
        modifier = Modifier.clip(FormaPildora).background(fondo).padding(horizontal = 10.dp, vertical = 3.dp))
}

@Composable
fun Espacio(alto: Dp = 100.dp) = androidx.compose.foundation.layout.Spacer(Modifier.height(alto).width(1.dp))

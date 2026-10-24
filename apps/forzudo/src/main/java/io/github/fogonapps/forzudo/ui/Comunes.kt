package io.github.fogonapps.forzudo.ui

import io.github.fogonapps.identidad.tarjetaFogon
import io.github.fogonapps.identidad.forma
import io.github.fogonapps.forzudo.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.heading
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.forzudo.datos.Vocabulario
import io.github.fogonapps.forzudo.datos.Biblioteca
import io.github.fogonapps.forzudo.datos.Ejercicio
import io.github.fogonapps.forzudo.datos.ParteCuerpo
import io.github.fogonapps.forzudo.datos.Rango
import io.github.fogonapps.forzudo.datos.fechaCorta
import io.github.fogonapps.forzudo.datos.fk
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


fun colorHex(h: String): Color = Color(android.graphics.Color.parseColor(h))

/* ----- Fotos desde assets, con caché ----- */

private val cacheFotos = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}

private suspend fun cargarFoto(c: Context, ruta: String): ImageBitmap? = cacheFotos.get(ruta) ?: withContext(Dispatchers.IO) {
    runCatching { c.assets.open(ruta).use { BitmapFactory.decodeStream(it) }.asImageBitmap() }.getOrNull()
        ?.also { cacheFotos.put(ruta, it) }
}

@Composable
fun FotoAsset(ruta: String?, descripcion: String?, modifier: Modifier = Modifier, recorte: ContentScale = ContentScale.Crop) {
    val c = LocalContext.current
    val img by produceState<ImageBitmap?>(ruta?.let { cacheFotos.get(it) }, ruta) { value = ruta?.let { cargarFoto(c, it) } }
    img?.let { Image(it, descripcion, modifier, contentScale = recorte) }
}

/** Miniatura del ejercicio: ícono del grupo y, encima, la foto si existe. */
@Composable
fun Miniatura(e: Ejercicio, bib: Biblioteca, tamano: Dp = 46.dp) {
    val f = FogonTema.actual
    Box(
        Modifier.size(tamano).tarjetaFogon(forma(10.dp), sombra = false),
        contentAlignment = Alignment.Center,
    ) {
        io.github.fogonapps.identidad.IconoRiso(Iconos.Grupo[e.g] ?: Iconos.Pesa, null, tamano = tamano * 0.5f, tinta = f.texto)
        FotoAsset(bib.foto(e.id, 1), null, Modifier.fillMaxSize())
    }
}

/* ----- Estructura ----- */

@Composable
fun Seccion(titulo: String?, modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    val f = FogonTema.actual
    Column(modifier.fillMaxWidth().padding(top = 18.dp)) {
        if (titulo != null) {
            // Títulos de sección en la letra de Fogón, en minúsculas como una frase.
            Text(
                titulo.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleLarge,
                color = f.texto, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
            )
        }
        contenido()
    }
}

@Composable
fun Fila(onClick: (() -> Unit)? = null, modifier: Modifier = Modifier, contenido: @Composable RowScope.() -> Unit) {
    Column {
        Row(
            modifier.fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .heightIn(min = 52.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = contenido,
        )
        HorizontalDivider(color = FogonTema.actual.linea.copy(alpha = 0.22f), modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
fun Pista(texto: String, modifier: Modifier = Modifier, centro: Boolean = false) {
    Text(
        texto, style = MaterialTheme.typography.bodySmall, color = FogonTema.actual.textoSuave,
        textAlign = if (centro) TextAlign.Center else TextAlign.Start,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
fun Barra(progreso: Float, color: Color, modifier: Modifier = Modifier) {
    val f = FogonTema.actual
    Box(modifier.fillMaxWidth().height(10.dp).clip(forma(5.dp)).background(f.superficie).border(1.5.dp, f.linea, forma(5.dp))) {
        Box(Modifier.fillMaxWidth(progreso.coerceIn(0f, 1f)).height(10.dp).clip(forma(5.dp)).background(color))
    }
}

/** Tres cifras lado a lado (tiempo, volumen, series…). */
@Composable
fun Trio(vararg datos: Pair<String, String>) {
    val f = FogonTema.actual
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        datos.forEach { (k, v) ->
            Column(Modifier.weight(1f)) {
                Text(k, style = MaterialTheme.typography.labelMedium, color = f.textoSuave)
                Text(v, style = MaterialTheme.typography.displaySmall.copy(fontSize = 24.sp), color = f.texto, maxLines = 1)
            }
        }
    }
}

/* ----- Insignia de rango (escudo) ----- */

private val escudo = PathParser().parsePathString("M20 2l15 6v11c0 9.5-6.4 16.4-15 19-8.6-2.6-15-9.5-15-19V8z").toPath()
private val escudoBrillo = PathParser().parsePathString("M20 6l11 4.4V19c0 7.3-4.8 12.6-11 14.7C13.8 31.6 9 26.3 9 19v-8.6z").toPath()

@Composable
fun Insignia(r: Rango?, tamano: Dp = 40.dp) {
    val f = FogonTema.actual
    val medidor = rememberTextMeasurer()
    val texto = r?.div?.ifEmpty { "★" } ?: "?"
    val color = r?.let { colorHex(it.color) } ?: f.linea
    val descripcion = r?.nombre ?: stringResource(R.string.sin_rango)
    Canvas(Modifier.size(tamano).semantics { contentDescription = descripcion }) {
        val s = size.width / 40f
        scale(s, s, pivot = Offset.Zero) {
            drawPath(escudo, color)
            drawPath(escudoBrillo, Color.White.copy(alpha = 0.2f))
        }
        val t = medidor.measure(texto, TextStyle(color = Color.White, fontSize = (if (texto.length > 2) 9.5f else 11.5f).sp * (tamano.value / 40f), fontWeight = FontWeight.ExtraBold))
        drawText(t, topLeft = Offset((size.width - t.size.width) / 2, size.height * 0.61f - t.size.height / 2))
    }
}

/* ----- Gráfica de línea ----- */

@Composable
fun Grafica(puntos: List<Pair<String, Double?>>, modifier: Modifier = Modifier) {
    val pts = puntos.mapNotNull { (d, v) -> v?.let { d to it } }
    val f = FogonTema.actual
    if (pts.isEmpty()) { Pista(stringResource(R.string.sin_datos)); return }
    val medidor = rememberTextMeasurer()
    val acento = f.paleta.tinta
    val grafica = stringResource(R.string.grafica)
    Canvas(modifier.fillMaxWidth().aspectRatio(2f).padding(horizontal = 16.dp, vertical = 8.dp).semantics { contentDescription = grafica }) {
        val L = 40.dp.toPx(); val R = 8.dp.toPx(); val T = 12.dp.toPx(); val B = 22.dp.toPx()
        var mn = pts.minOf { it.second }; var mx = pts.maxOf { it.second }
        if (mn == mx) { mn -= 1; mx += 1 }
        fun x(i: Int) = if (pts.size == 1) (L + size.width - R) / 2 else L + i * (size.width - L - R) / (pts.size - 1)
        fun y(v: Double) = (T + (1 - (v - mn) / (mx - mn)) * (size.height - T - B)).toFloat()
        val estilo = TextStyle(color = f.textoSuave, fontSize = 11.sp)
        listOf(mx, (mx + mn) / 2, mn).forEach { t ->
            drawLine(f.linea.copy(alpha = 0.22f), Offset(L, y(t)), Offset(size.width - R, y(t)), 1.dp.toPx())
            val m = medidor.measure(fk(Math.round(t * 10) / 10.0), estilo)
            drawText(m, topLeft = Offset(L - 6.dp.toPx() - m.size.width, y(t) - m.size.height / 2))
        }
        val p = Path()
        pts.forEachIndexed { i, (_, v) -> if (i == 0) p.moveTo(x(i), y(v)) else p.lineTo(x(i), y(v)) }
        drawPath(p, acento, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        pts.forEachIndexed { i, (_, v) ->
            drawCircle(f.fondo, 4.dp.toPx(), Offset(x(i), y(v)))
            drawCircle(acento, 4.dp.toPx(), Offset(x(i), y(v)), style = Stroke(2.5.dp.toPx()))
        }
        val a = medidor.measure(fechaCorta(pts.first().first), estilo)
        drawText(a, topLeft = Offset(L, size.height - a.size.height))
        if (pts.size > 1) {
            val b = medidor.measure(fechaCorta(pts.last().first), estilo)
            drawText(b, topLeft = Offset(size.width - R - b.size.width, size.height - b.size.height))
        }
    }
}

/* ----- Mapa del cuerpo (react-body-highlighter, MIT, © 2020 GV79) ----- */

private val cachePoligonos = HashMap<String, Path>()
private fun poligono(pts: String): Path = cachePoligonos.getOrPut(pts) {
    val n = pts.trim().split(" ").map { it.toFloat() }
    Path().apply {
        moveTo(n[0], n[1])
        var i = 2
        while (i + 1 < n.size) { lineTo(n[i], n[i + 1]); i += 2 }
        close()
    }
}

@Composable
fun MapaCuerpo(partes: List<ParteCuerpo>, relleno: (String) -> Color?, descripcion: String, modifier: Modifier = Modifier) {
    val f = FogonTema.actual
    val vacio = f.texto.copy(alpha = 0.16f)
    val neutro = if (f.oscuro) Color(0xFF3A332D) else Color(0xFFCFC3B1)
    Canvas(modifier.aspectRatio(0.5f).semantics { contentDescription = descripcion }) {
        val s = size.width / 1000f
        scale(s, s, pivot = Offset.Zero) {
            partes.forEach { parte ->
                val c = parte.g?.let { relleno(it) ?: vacio } ?: neutro
                parte.p.forEach { drawPath(poligono(it), c) }
            }
        }
    }
}

/* ----- Mapa de días entrenados ----- */

@Composable
fun MapaAnual(anio: Int, dias: Set<String>, hoy: LocalDate) {
    val f = FogonTema.actual
    val medidor = rememberTextMeasurer()
    val inicio = LocalDate.of(anio, 1, 1)
    val desfase = inicio.dayOfWeek.value - 1
    val total = inicio.lengthOfYear()
    val cols = (desfase + total + 6) / 7
    val n = dias.count { it.startsWith("$anio") }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text("$anio · " + pluralStringResource(R.plurals.entrenos, n, n), style = MaterialTheme.typography.labelLarge, color = f.texto)
        val descripcion = stringResource(R.string.dias_entrenados, anio)
        Canvas(Modifier.fillMaxWidth().aspectRatio(cols / 8.2f).semantics { contentDescription = descripcion }) {
            val c = size.width / cols
            val estilo = TextStyle(color = f.textoSuave, fontSize = 8.sp)
            for (i in 0 until total) {
                val d = inicio.plusDays(i.toLong()); val idx = desfase + i
                val x = (idx / 7) * c; val y = (idx % 7) * c + c * 1.2f
                val on = d.toString() in dias
                drawRoundRect(
                    if (on) f.paleta.tinta else f.superficie, Offset(x, y),
                    androidx.compose.ui.geometry.Size(c * 0.84f, c * 0.84f),
                    androidx.compose.ui.geometry.CornerRadius(c * 0.2f), alpha = if (d.isAfter(hoy)) 0.45f else 1f,
                )
                if (d.dayOfMonth == 1) drawText(medidor.measure(Vocabulario.actual.meses[d.monthValue - 1], estilo), topLeft = Offset(x, 0f))
            }
        }
    }
}

/* ----- Entradas ----- */

/** Campo numérico compacto. Guarda el texto tal cual se escribe y avisa cada cambio. */
@Composable
fun CampoNumero(
    inicial: String,
    pista: String,
    descripcion: String,
    decimal: Boolean,
    onCambio: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val f = FogonTema.actual
    var texto by remember(inicial) { mutableStateOf(inicial) }
    BasicTextField(
        value = texto,
        onValueChange = { v -> val l = v.filter { it.isDigit() || it == ',' || it == '.' }.take(6); texto = l; onCambio(l) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = f.texto, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold),
        cursorBrush = SolidColor(f.paleta.tinta),
        modifier = modifier
            .heightIn(min = 44.dp)
            .tarjetaFogon(forma(10.dp), sombra = false)
            .semantics { contentDescription = descripcion },
        decorationBox = { campo ->
            Box(Modifier.padding(horizontal = 6.dp, vertical = 11.dp), contentAlignment = Alignment.Center) {
                if (texto.isEmpty()) Text(pista, style = MaterialTheme.typography.bodyLarge, color = f.textoSuave.copy(alpha = 0.7f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                campo()
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectorFecha(actual: String, hoy: LocalDate, onElegir: (String) -> Unit, onCerrar: () -> Unit) {
    val ms = LocalDate.parse(actual).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    val tope = hoy.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    val estado = rememberDatePickerState(
        initialSelectedDateMillis = ms,
        selectableDates = object : androidx.compose.material3.SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= tope
        },
    )
    DatePickerDialog(
        onDismissRequest = onCerrar,
        confirmButton = {
            TextButton({
                estado.selectedDateMillis?.let { onElegir(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                onCerrar()
            }) { Text(stringResource(R.string.listo)) }
        },
        dismissButton = { TextButton(onCerrar) { Text(stringResource(R.string.cancelar)) } },
    ) { DatePicker(estado) }
}

/** Botón de fecha que abre el selector. */
@Composable
fun BotonFecha(etiqueta: String, fecha: String, hoy: LocalDate, onElegir: (String) -> Unit, modifier: Modifier = Modifier) {
    val f = FogonTema.actual
    var abierto by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = f.textoSuave)
        Text(
            fechaCorta(fecha) + " " + fecha.take(4),
            style = MaterialTheme.typography.bodyLarge, color = f.acentoTexto,
            modifier = Modifier.clip(forma(8.dp)).clickable { abierto = true }
                .border(1.dp, f.linea, forma(8.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
    if (abierto) SelectorFecha(fecha, hoy, onElegir) { abierto = false }
}


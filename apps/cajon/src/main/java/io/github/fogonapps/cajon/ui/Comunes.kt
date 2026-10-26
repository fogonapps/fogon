package io.github.fogonapps.cajon.ui

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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.aspectRatio
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
import io.github.fogonapps.cajon.R
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.FormaRiso
import io.github.fogonapps.identidad.brillo
import io.github.fogonapps.identidad.haloPuntos
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Tarjeta de las filas: papel de tarjeta con borde de tinta, sin sombra (las listas quedan tranquilas). */
@Composable
fun Modifier.vidrio(forma: androidx.compose.ui.graphics.Shape = FormaRiso): Modifier {
    return tarjetaFogon(forma, sombra = false)
}

/** La tarjeta destacada de cada pantalla: un bloque de la primera tinta con la segunda corrida detrás. */
@Composable
fun Modifier.destacada(): Modifier = tarjetaDestacada(forma(28.dp))

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
fun Pista(texto: String, modifier: Modifier = Modifier, centro: Boolean = false, adentro: Boolean = false) {
    Text(texto, style = MaterialTheme.typography.bodySmall, color = FogonTema.actual.textoSuave,
        textAlign = if (centro) TextAlign.Center else TextAlign.Start,
        modifier = modifier.fillMaxWidth().padding(horizontal = if (adentro) 0.dp else 20.dp, vertical = 4.dp))
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
fun FilaVidrio(onClick: (() -> Unit)? = null, modifier: Modifier = Modifier, adentro: Boolean = false, contenido: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = if (adentro) 0.dp else 16.dp, vertical = 5.dp).vidrio()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 60.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), content = contenido,
    )
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
        Text(titulo, fontFamily = FogonTema.actual.titulos, fontSize = 22.sp, color = FogonTema.actual.texto, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 26.sp, modifier = Modifier.weight(1f))
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

/** Un número sin decimales si es entero o grande, y con uno si es pequeño (1,5 tazas; 0,5 porción). */
fun cifra(d: Double, idioma: String, decimales: Int = 1): String {
    val escala = Math.pow(10.0, decimales.toDouble())
    val r = Math.round(d * escala) / escala
    val t = if (r == Math.floor(r) || r >= 100) Math.round(r).toString() else r.toString()
    return if (idioma == "en") t else t.replace('.', ',')
}

/** Lee lo que escribió la persona con coma o punto. */
fun numero(t: String): Double? = t.trim().replace(',', '.').toDoubleOrNull()

/** Botón redondo con un ícono (en las cabeceras y filas). */
@Composable
fun BotonIcono(icono: Dibujo, descripcion: String, grande: Boolean = false, activo: Boolean = true, tinta: Color? = null, lleno: Boolean = false, onClick: () -> Unit) {
    val f = FogonTema.actual
    Box(
        Modifier.rebote(0.9f).size(if (grande) 46.dp else 40.dp).clip(CircleShape)
            .then(if (grande) Modifier.background(f.superficie).border(f.borde, f.linea, CircleShape) else Modifier)
            .clickable(enabled = activo, onClick = onClick).semantics { contentDescription = descripcion; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) { IconoRiso(icono, null, tamano = if (grande) 22.dp else 20.dp, tinta = tinta ?: f.texto, relleno = if (lleno) tinta ?: f.texto else null) }
}

/** Una píldora que se toca (filtros y acciones pequeñas). */
@Composable
fun Pildora(texto: String, icono: Dibujo? = null, acento: Boolean = false, onClick: () -> Unit) {
    val f = FogonTema.actual
    Row(
        Modifier.rebote().clip(FormaPildora).background(if (acento) f.tinta1 else f.superficie)
            .border(f.borde, if (acento) f.tinta1 else f.linea, FormaPildora)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icono != null) IconoRiso(icono, null, tamano = 16.dp, tinta = if (acento) f.sobreTinta1 else f.texto)
        Text(texto, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (acento) f.sobreTinta1 else f.texto, maxLines = 1)
    }
}

/**
 * La portada de una obra (póster, carátula, cubierta). Mientras carga, o si no tiene, se ve una ficha de
 * vidrio con el ícono del tipo y el título, para que nunca quede un hueco.
 */
@Composable
fun Portada(url0: String, tipo: String, titulo: String, modifier: Modifier = Modifier, esquina: Dp = 12.dp, conTitulo: Boolean = true) {
    val f = FogonTema.actual
    // Los libros van sin portada (las de Open Library suelen ser escaneos viejos): también los que ya la tenían guardada.
    val url = if (tipo == "libro") "" else url0
    val forma = RoundedCornerShape(esquina)
    val cuadrada = tipo == "album"
    Box(modifier.aspectRatio(if (cuadrada) 1f else 2f / 3f).clip(forma).background(f.superficie)
        .haloPuntos(f.tinta2.copy(alpha = 0.28f), paso = 8.dp, centro = androidx.compose.ui.geometry.Offset(0.5f, 0.4f))
        .border(f.borde, f.linea, forma), contentAlignment = Alignment.Center) {
        // Sin portada (o mientras carga): el ícono del tipo y el título, en la letra de los títulos.
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            IconoRiso(Iconos.de(tipo), null, tamano = 28.dp, tinta = f.acentoTexto)
            if (conTitulo && titulo.isNotBlank()) Text(titulo, fontFamily = f.titulos, fontSize = 13.sp, lineHeight = 15.sp, color = f.texto,
                textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        if (url.isNotBlank()) coil3.compose.AsyncImage(
            model = url, contentDescription = titulo, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
    }
}

/** Las estrellas de una nota (en medias estrellas, 0 a 10), en la primera tinta. Nada si no tiene nota. */
@Composable
fun Estrellas(nota: Int, tamano: TextUnit = 14.sp, color: Color = FogonTema.actual.acentoTexto) {
    if (nota <= 0) return
    Text(io.github.fogonapps.cajon.datos.estrellas(nota), fontSize = tamano, color = color, maxLines = 1,
        modifier = Modifier.semantics { contentDescription = "" + nota / 2.0 })
}

/**
 * Calificar con estrellas: tocar la mitad izquierda de una estrella pone media y la derecha la entera; tocar la
 * nota que ya tiene la quita. [nota] en medias estrellas (0 a 10).
 */
@Composable
fun ElegirEstrellas(nota: Int, onNota: (Int) -> Unit, tamano: Dp = 40.dp, descripcion: String) {
    val f = FogonTema.actual
    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = descripcion }, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..5) {
            val llena = nota >= i * 2
            val media = nota == i * 2 - 1
            Box(Modifier.size(tamano).rebote(0.85f)) {
                // Fondo: la estrella vacía; encima, la parte llena.
                IconoRiso(Iconos.Estrella, null, tamano = tamano, tinta = f.linea, relleno = f.superficie)
                if (llena || media) Box(Modifier.size(tamano).clip(if (media) MitadIzquierda else androidx.compose.ui.graphics.RectangleShape)) {
                    IconoRiso(Iconos.Estrella, null, tamano = tamano, tinta = f.tinta1, relleno = f.tinta1)
                }
                Row(Modifier.matchParentSize()) {
                    Box(Modifier.weight(1f).fillMaxHeight().clickable { onNota(if (nota == i * 2 - 1) 0 else i * 2 - 1) })
                    Box(Modifier.weight(1f).fillMaxHeight().clickable { onNota(if (nota == i * 2) 0 else i * 2) })
                }
            }
        }
    }
}

/** Recorta la mitad izquierda (para la media estrella). */
private val MitadIzquierda = object : androidx.compose.ui.graphics.Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density) =
        androidx.compose.ui.graphics.Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f, 0f, size.width / 2f, size.height))
}

/** El nombre de un tipo de obra, en plural ("Películas") o singular ("Película"). */
@Composable
fun nombreTipo(tipo: String, plural: Boolean = true): String = stringResource(when (tipo) {
    "pelicula" -> if (plural) R.string.tipo_peliculas else R.string.tipo_pelicula
    "serie" -> if (plural) R.string.tipo_series else R.string.tipo_serie
    "anime" -> if (plural) R.string.tipo_animes else R.string.tipo_anime
    "libro" -> if (plural) R.string.tipo_libros else R.string.tipo_libro
    "album" -> if (plural) R.string.tipo_albumes else R.string.tipo_album
    "autor" -> if (plural) R.string.tipo_autores else R.string.tipo_autor
    "director" -> if (plural) R.string.tipo_directores else R.string.tipo_director
    else -> if (plural) R.string.tipo_artistas else R.string.tipo_artista
})

/** Cómo se dice cada estado según el tipo: "Por ver", "Por leer", "Por escuchar"… */
@Composable
fun nombreEstado(estado: String, tipo: String?): String = stringResource(when (estado) {
    "quiero" -> when (tipo) { "libro" -> R.string.estado_por_leer; "album" -> R.string.estado_por_escuchar; null -> R.string.estado_pendiente; else -> R.string.estado_por_ver }
    "en_curso" -> when (tipo) { "libro" -> R.string.estado_leyendo; "album" -> R.string.estado_escuchando; null -> R.string.estado_en_curso; else -> R.string.estado_viendo }
    "terminado" -> when (tipo) { "libro" -> R.string.estado_leido; "album" -> R.string.estado_escuchado; null -> R.string.estado_terminado; else -> R.string.estado_visto }
    else -> R.string.estado_abandonado
})

/** Una fecha AAAA-MM-DD como "25 oct 2026" (o "Oct 25, 2026"). */
fun fechaCorta(fecha: String, idioma: String): String = runCatching {
    LocalDate.parse(fecha).format(java.time.format.DateTimeFormatter.ofPattern(if (idioma == "en") "MMM d, yyyy" else "d MMM yyyy", java.util.Locale.forLanguageTag(idioma)))
}.getOrDefault(fecha)

/** Botón con la fecha; al tocarlo abre el calendario (sin días futuros). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotonFecha(fecha: String, idioma: String, onElegir: (String) -> Unit, hoy: LocalDate) {
    var abierto by remember { mutableStateOf(false) }
    val f = FogonTema.actual
    TextButton({ abierto = true }, Modifier.vidrio(FormaPildora)) { Text(fechaCorta(fecha, idioma), color = f.texto) }
    if (abierto) {
        val ms = LocalDate.parse(fecha).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val tope = hoy.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = ms,
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= tope
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

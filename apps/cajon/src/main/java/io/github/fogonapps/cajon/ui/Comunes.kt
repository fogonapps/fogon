package io.github.fogonapps.cajon.ui

import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import io.github.fogonapps.identidad.tarjetaFogon
import io.github.fogonapps.identidad.FormaPildora
import io.github.fogonapps.identidad.forma
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import io.github.fogonapps.identidad.rebote
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.lerp
import io.github.fogonapps.identidad.Dibujo
import io.github.fogonapps.identidad.IconoRiso
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
import androidx.compose.ui.semantics.liveRegion
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Superficie apenas más honda que el papel, sin borde ni sombra. Las pantallas limpias no la usan: mejor filas planas y espacio. */
@Composable
fun Modifier.vidrio(forma: androidx.compose.ui.graphics.Shape = FormaRiso): Modifier {
    return tarjetaFogon(forma, sombra = false)
}

/** Nombre de tarjeta (cuenta, sobre, meta) en la letra de los títulos. */
@Composable
fun Nombre(texto: String, tamano: TextUnit = 15.sp, color: Color = FogonTema.actual.texto, modifier: Modifier = Modifier) {
    Text(texto, fontFamily = FogonTema.actual.titulos, fontSize = tamano, color = color, modifier = modifier, maxLines = 1)
}

/**
 * Un lugar vacío, sin tarjeta ni personaje: el ícono de línea (40 dp), un título en la letra de los títulos, una
 * línea que explica y, si hay algo que hacer, el botón en tinta.
 */
@Composable
fun Vacio(icono: Dibujo, titulo: String, texto: String, modifier: Modifier = Modifier, boton: String? = null, onBoton: () -> Unit = {}) {
    val f = FogonTema.actual
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IconoRiso(icono, null, tamano = 40.dp, tinta = f.texto)
        Text(titulo, fontFamily = f.titulos, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 26.sp, color = f.texto,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp).semantics { heading() })
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = f.textoSuave, textAlign = TextAlign.Center)
        if (boton != null) io.github.fogonapps.identidad.BotonRiso(boton, onBoton, Modifier.padding(top = 8.dp))
    }
}

/**
 * Desvanece los bordes de una fila que se desliza cuando sigue de ese lado (con la letra grande, los filtros se
 * cortan al borde y así se nota que hay más). Va antes del `horizontalScroll`, para medir sobre lo que se ve.
 */
fun Modifier.desvanecerBordes(izquierda: Boolean, derecha: Boolean, ancho: Dp = 40.dp): Modifier =
    if (!izquierda && !derecha) this
    else graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val borde = ancho.toPx().coerceAtMost(size.width / 3f)
            val negro = Color.Black
            if (derecha) drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(negro, Color.Transparent), startX = size.width - borde, endX = size.width),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
            if (izquierda) drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Transparent, negro), startX = 0f, endX = borde),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
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
 * Ficha de una categoría o cuenta: el ícono de línea solo, en tinta, sin círculo ni tinte (el diseño limpio de Fabs).
 * [color] marca si está elegida: la tinta del texto si lo está; si no, la suave.
 */
@Composable
fun Ficha(color: Color, icono: Dibujo, tamano: Dp = 44.dp, modifier: Modifier = Modifier) {
    val f = FogonTema.actual
    val tinta = if (color == f.tinta1 || color == f.texto) f.texto else f.textoSuave
    Box(modifier.size(tamano), contentAlignment = Alignment.Center) {
        IconoRiso(icono, null, tamano = tamano * 0.55f, tinta = tinta)
    }
}

/**
 * Fila de lista: ficha, textos y lo que vaya a la derecha. Plana, sobre el papel (el diseño nuevo de Fabs no mete
 * cada fila en su tarjeta); al tocarla se marca toda la fila, de borde a borde.
 */
@Composable
fun FilaVidrio(onClick: (() -> Unit)? = null, modifier: Modifier = Modifier, adentro: Boolean = false, contenido: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp).padding(horizontal = if (adentro) 0.dp else 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), content = contenido,
    )
}

/** Barra de progreso fina: la línea del papel y el avance en tinta, sin borde. */
@Composable
fun Barra(progreso: Float, color: Color, modifier: Modifier = Modifier) {
    val f = FogonTema.actual
    Box(modifier.fillMaxWidth().height(4.dp).clip(FormaPildora).background(f.linea)) {
        Box(Modifier.fillMaxWidth(io.github.fogonapps.identidad.progresoAnimado(progreso.coerceIn(0f, 1f))).fillMaxHeight().clip(FormaPildora).background(color))
    }
}

@Composable
fun CampoTexto(valor: String, onCambio: (String) -> Unit, etiqueta: String, modifier: Modifier = Modifier, max: Int = 40) {
    // Al llegar al tope se dice (también al lector de pantalla), para que no parezca que el teclado dejó de funcionar.
    OutlinedTextField(valor, { onCambio(io.github.fogonapps.cajon.datos.Logica.recortarSinPartir(it, max)) }, modifier.fillMaxWidth(), label = { Text(etiqueta) }, singleLine = true,
        supportingText = if (valor.length >= max) ({ AvisoTope(max) }) else null)
}

/** «Máximo N caracteres», anunciado por el lector de pantalla cuando aparece. */
@Composable
fun AvisoTope(max: Int) {
    Text(stringResource(R.string.campo_tope, max), Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite })
}

@Composable
fun CampoNumero(valor: String, onCambio: (String) -> Unit, etiqueta: String, modifier: Modifier = Modifier, decimales: Boolean = false) {
    OutlinedTextField(
        valor, { t -> onCambio(t.filter { it.isDigit() || (decimales && (it == ',' || it == '.')) }.take(6)) }, modifier,
        label = { Text(etiqueta) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimales) KeyboardType.Decimal else KeyboardType.Number),
    )
}

/** Elegir una opción entre varias, como píldoras: la elegida llena en tinta; las demás, solo con su borde. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Selector(opciones: List<Pair<T, String>>, elegido: T, onElegir: (T) -> Unit, modifier: Modifier = Modifier) {
    val f = FogonTema.actual
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        opciones.forEach { (v, t) ->
            val elegida = v == elegido
            FilterChip(elegida, { onElegir(v) }, { Text(t) }, shape = FormaPildora,
                colors = FilterChipDefaults.filterChipColors(containerColor = Color.Transparent, labelColor = f.texto,
                    selectedContainerColor = f.pildora, selectedLabelColor = f.sobrePildora),
                border = FilterChipDefaults.filterChipBorder(true, elegida, borderColor = f.texto.copy(alpha = 0.25f),
                    selectedBorderColor = f.pildora, borderWidth = 1.dp, selectedBorderWidth = 1.dp))
        }
    }
}

/**
 * Barra superior de las pantallas que se abren encima: atrás (la flecha sola, en tinta), título y algo a la derecha. [chico]: el
 * título en letra de texto (el @usuario de un perfil).
 */
@Composable
fun Cabecera(titulo: String, onAtras: (() -> Unit)?, icono: Dibujo = Iconos.Atras, chico: Boolean = false, derecha: @Composable () -> Unit = {}) {
    val f = FogonTema.actual
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // La flecha sola, con 48 dp para el dedo, como la `CabeceraSocial` de core.
        if (onAtras != null) Box(Modifier.size(48.dp).clip(CircleShape)
            .clickable(onClick = onAtras), contentAlignment = Alignment.Center) {
            IconoRiso(icono, stringResource(R.string.volver), tamano = 24.dp, tinta = f.texto, corrida = null)
        }
        if (chico) Text(titulo, style = MaterialTheme.typography.titleMedium, color = FogonTema.actual.texto, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).semantics { heading() })
        else Text(titulo, fontFamily = FogonTema.actual.titulos, fontSize = 22.sp, color = FogonTema.actual.texto, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 26.sp, modifier = Modifier.weight(1f).semantics { heading() })
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
            .clickable(enabled = activo, onClick = onClick).semantics { contentDescription = descripcion; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) { IconoRiso(icono, null, tamano = if (grande) 22.dp else 20.dp, tinta = tinta ?: f.texto, relleno = if (lleno) tinta ?: f.texto else null) }
}

/**
 * Una fila con interruptor: se toca toda la fila (no solo el interruptor) y el lector de pantalla dice qué cambia.
 */
@Composable
fun FilaInterruptorCajon(texto: String, valor: Boolean, pista: String? = null, modifier: Modifier = Modifier, onCambio: (Boolean) -> Unit) {
    val f = FogonTema.actual
    Row(modifier.fillMaxWidth().clip(forma(14.dp)).toggleable(valor, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onCambio).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(texto, style = MaterialTheme.typography.bodyLarge, color = f.texto)
            if (!pista.isNullOrBlank()) Text(pista, style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        }
        androidx.compose.material3.Switch(valor, onCheckedChange = null)
    }
}

/** Una píldora que se toca (filtros y acciones pequeñas). Sin [onClick] solo se lee: ni rebota ni es botón. */
@Composable
fun Pildora(texto: String, icono: Dibujo? = null, acento: Boolean = false, modifier: Modifier = Modifier, onClick: (() -> Unit)?) {
    val f = FogonTema.actual
    Row(
        // La destacada, llena en tinta (como `BotonRiso`); las demás, solo con su borde fino (como `BotonSecundario`).
        (if (onClick != null) modifier.rebote() else modifier).clip(FormaPildora)
            .then(if (acento) Modifier.background(f.pildora) else Modifier.border(1.dp, f.texto.copy(alpha = 0.25f), FormaPildora))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier).defaultMinSize(minWidth = 48.dp).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (icono != null) IconoRiso(icono, null, tamano = 16.dp, tinta = if (acento) f.sobrePildora else f.texto)
        Text(texto, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = if (acento) f.sobrePildora else f.texto, maxLines = 1)
    }
}

/**
 * La portada de una obra (póster, carátula, cubierta). Mientras carga, o si no tiene, se ve una portada
 * inventada en el gris del papel, con el ícono del tipo y el título en la letra de los títulos, como la tapa de un
 * libro. Nunca queda un hueco.
 *
 * Todas ocupan el mismo marco de póster (2:3), para que filas y cuadrículas mezcladas queden parejas: la carátula
 * de un álbum va cuadrada en el centro, sobre ella misma ampliada y borrosa. Con [natural], el álbum queda
 * cuadrado (su ficha, o donde solo hay música).
 */
@Composable
fun Portada(url0: String, tipo: String, titulo: String, modifier: Modifier = Modifier, esquina: Dp = 12.dp, conTitulo: Boolean = true, lugar: io.github.fogonapps.cajon.datos.Imagenes.Lugar = io.github.fogonapps.cajon.datos.Imagenes.Lugar.LISTA, natural: Boolean = false) {
    val f = FogonTema.actual
    // Las portadas se guardan grandes (para la ficha); en pantalla basta la de TMDB de 342 px, que pesa la mitad.
    val contexto = androidx.compose.ui.platform.LocalContext.current
    val ahorro = androidx.compose.runtime.remember { runCatching {
        val red = contexto.getSystemService(android.net.ConnectivityManager::class.java)
        val capacidades = red.getNetworkCapabilities(red.activeNetwork)
        capacidades?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true &&
            red.restrictBackgroundStatus == android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
    }.getOrDefault(false) }
    val url = io.github.fogonapps.cajon.datos.Imagenes.url(url0, lugar, ahorro)
    val forma = RoundedCornerShape(esquina)
    val album = tipo == "album"
    val cuadrada = album && natural
    // Sin color propio: el color lo ponen las portadas de verdad; la inventada es un gris del papel.
    val tono = androidx.compose.ui.graphics.lerp(f.fondo, f.texto, 0.09f)
    val crema = f.texto
    // La inventada se ve solo hasta que llega la de verdad (si no, su ícono asoma por las portadas con transparencia).
    var cargada by androidx.compose.runtime.remember(url) { androidx.compose.runtime.mutableStateOf(false) }
    // Se anota dónde está, para que al abrir su ficha la portada vuele desde aquí.
    val vuelos = LocalVuelos.current
    val pieza = androidx.compose.runtime.remember { Any() }
    if (vuelos != null) androidx.compose.runtime.DisposableEffect(vuelos) { onDispose { vuelos.origenes.remove(pieza) } }
    Box(modifier.then(if (vuelos != null && url0.isNotBlank()) Modifier.onGloballyPositioned { c ->
            val r = c.boundsInRoot()
            // De un álbum en marco de póster vuela solo la carátula cuadrada del centro.
            vuelos.origenes[pieza] = Triple(url0, if (album && !cuadrada) androidx.compose.ui.geometry.Rect(r.left, r.center.y - r.width / 2, r.right, r.center.y + r.width / 2) else r, url)
        } else Modifier)
        .aspectRatio(if (cuadrada) 1f else 2f / 3f).clip(forma)
        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.lerp(f.fondo, f.texto, 0.05f), tono)))
        .semantics { contentDescription = titulo }) {
        // Sin portada (o mientras carga): el ícono del tipo arriba y el título abajo, como una tapa.
        if (!cargada) BoxWithConstraints(Modifier.matchParentSize().clearAndSetSemantics {}) {
            val chica = maxWidth < 72.dp
            // La inicial del título, grande y en la letra de títulos: cada hueco se distingue del de al lado (antes, todos el mismo ícono).
            val inicial = titulo.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
            if ((chica || !conTitulo) && inicial != null) {
                val tamanoLetra = with(androidx.compose.ui.platform.LocalDensity.current) { (maxWidth * 0.62f).toSp() }
                Text(inicial.toString(), fontFamily = f.titulos, fontWeight = FontWeight.Black, fontSize = tamanoLetra, lineHeight = tamanoLetra,
                    color = crema.copy(alpha = 0.22f), maxLines = 1, modifier = Modifier.align(Alignment.Center))
                if (!chica) IconoRiso(Iconos.de(tipo), null, tamano = 16.dp, tinta = crema.copy(alpha = 0.45f), modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp))
            } else if (chica || !conTitulo || titulo.isBlank()) {
                IconoRiso(Iconos.de(tipo), null, tamano = if (chica) (maxWidth * 0.42f) else 28.dp, tinta = crema.copy(alpha = 0.55f), modifier = Modifier.align(Alignment.Center))
            } else {
                IconoRiso(Iconos.de(tipo), null, tamano = 18.dp, tinta = crema.copy(alpha = 0.5f), modifier = Modifier.align(Alignment.TopStart).padding(10.dp))
                Text(titulo, fontFamily = f.titulos, fontWeight = FontWeight.Bold, fontSize = if (maxWidth > 150.dp) 22.sp else 14.sp,
                    lineHeight = if (maxWidth > 150.dp) 24.sp else 16.sp, color = crema,
                    maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
            }
        }
        if (url.isNotBlank()) {
            if (album && !cuadrada) {
                // El marco de póster lo llena la misma carátula, ampliada y borrosa; la carátula entera va al centro.
                if (cargada) coil3.compose.AsyncImage(
                    model = coil3.request.ImageRequest.Builder(contexto).data(url).size(36, 36).build(), contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                        .then(if (android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(18.dp) else Modifier)
                        .graphicsLayer { alpha = 0.9f },
                )
                coil3.compose.AsyncImage(
                    model = url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    onState = { cargada = it is coil3.compose.AsyncImagePainter.State.Success },
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(1f)
                        .then(if (cargada) Modifier.shadow(10.dp, RoundedCornerShape(0.dp)) else Modifier),
                )
            } else coil3.compose.AsyncImage(
                model = url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                onState = { cargada = it is coil3.compose.AsyncImagePainter.State.Success },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** Las estrellas de una nota (en medias estrellas, 0 a 10), en la primera tinta. Nada si no tiene nota. */
@Composable
fun Estrellas(nota: Int, tamano: TextUnit = 14.sp, color: Color = FogonTema.actual.acentoTexto) {
    if (nota <= 0) return
    val dicho = estadoDeNota(nota, LocalConfiguration.current.locales[0], stringResource(R.string.nota_sin_calificar), stringResource(R.string.nota_estado_estrellas))
    Text(io.github.fogonapps.cajon.datos.estrellas(nota), fontSize = tamano, color = color, maxLines = 1,
        modifier = Modifier.semantics { contentDescription = dicho })
}

/** Texto accesible de la nota, con las cadenas y el separador decimal del idioma activo. */
internal fun estadoDeNota(nota: Int, idioma: Locale, sinCalificar: String, formato: String): String {
    val valor = nota.coerceIn(0, 10)
    if (valor == 0) return sinCalificar
    val estrellas = java.text.NumberFormat.getNumberInstance(idioma).apply {
        maximumFractionDigits = 1
    }.format(valor / 2.0)
    return String.format(idioma, formato, estrellas)
}

/** La nota (0 a 10, en medias estrellas) que marca el dedo en [x] sobre unas estrellas de [ancho]. */
internal fun notaDeArrastre(x: Float, ancho: Float): Int =
    if (ancho <= 0f) 0 else kotlin.math.ceil(x / ancho * 10f).toInt().coerceIn(0, 10)

/** Ajusta el progreso accesible a una media estrella válida. */
internal fun notaDeProgreso(progreso: Float): Int =
    if (progreso.isNaN()) 0 else progreso.coerceIn(0f, 10f).roundToInt()

/**
 * Calificar con estrellas: tocar la mitad izquierda de una estrella pone media y la derecha la entera; tocar la
 * nota que ya tiene la quita. [nota] en medias estrellas (0 a 10).
 */
@Composable
fun ElegirEstrellas(nota: Int, onNota: (Int) -> Unit, tamano: Dp = 40.dp, descripcion: String, ceroCalificado: Boolean = false, arrastrar: Boolean = false) {
    val f = FogonTema.actual
    val alCambiar by androidx.compose.runtime.rememberUpdatedState(onNota)
    val estado = estadoDeNota(nota, LocalConfiguration.current.locales[0],
        stringResource(if (ceroCalificado) R.string.resena_cero else R.string.nota_sin_calificar), stringResource(R.string.nota_estado_estrellas))
    Row(Modifier.clearAndSetSemantics {
        contentDescription = descripcion
        stateDescription = estado
        progressBarRangeInfo = ProgressBarRangeInfo(nota.coerceIn(0, 10).toFloat(), 0f..10f, 9)
        setProgress { progreso ->
            onNota(notaDeProgreso(progreso))
            true
        }
    }.then(if (arrastrar) Modifier.pointerInput(Unit) {
        // Con [arrastrar], el dedo que corre sobre las estrellas va poniendo la nota (de media en media).
        detectHorizontalDragGestures(onDragStart = { alCambiar(notaDeArrastre(it.x, size.width.toFloat())) }) { cambio, _ ->
            cambio.consume(); alCambiar(notaDeArrastre(cambio.position.x, size.width.toFloat()))
        }
    } else Modifier), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..5) {
            val llena = nota >= i * 2
            val media = nota == i * 2 - 1
            // Al encenderse, cada estrella salta un poco después de la anterior; con la nota máxima, la última suelta chispas.
            val salto = remember { androidx.compose.animation.core.Animatable(1f) }
            val chispas = remember { androidx.compose.animation.core.Animatable(0f) }
            var antes by remember { androidx.compose.runtime.mutableStateOf(llena || media) }
            androidx.compose.runtime.LaunchedEffect(llena || media) {
                val ahora = llena || media
                if (ahora && !antes && !f.menosMovimiento) {
                    kotlinx.coroutines.delay((i - 1) * 70L)
                    salto.animateTo(1.32f, androidx.compose.animation.core.spring(0.5f, androidx.compose.animation.core.Spring.StiffnessMedium))
                    salto.animateTo(1f, androidx.compose.animation.core.spring(0.35f, androidx.compose.animation.core.Spring.StiffnessMediumLow))
                }
                antes = ahora
            }
            val maxima = nota == 10
            androidx.compose.runtime.LaunchedEffect(maxima) {
                if (maxima && i == 5 && !f.menosMovimiento && chispas.value == 0f) {
                    kotlinx.coroutines.delay(4 * 70L + 120)
                    chispas.snapTo(0.001f); chispas.animateTo(1f, androidx.compose.animation.core.tween(650))
                } else if (!maxima) chispas.snapTo(0f)
            }
            Box(Modifier.size(tamano).rebote(0.85f).graphicsLayer { scaleX = salto.value; scaleY = salto.value; rotationZ = (salto.value - 1f) * -30f }
                .drawBehind { if (chispas.value in 0.001f..0.999f) dibujarChispas(chispas.value, f.tinta1) }) {
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

/** Ocho chispitas de cuatro puntas que salen del centro y se apagan; [t] va de 0 a 1. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.dibujarChispas(t: Float, color: Color) {
    val radio = size.minDimension * (0.55f + 0.75f * t)
    val lado = size.minDimension * 0.16f * (1f - t * 0.6f)
    for (k in 0 until 8) {
        val ang = k * Math.PI / 4 + 0.3
        val c = center + androidx.compose.ui.geometry.Offset((kotlin.math.cos(ang) * radio * (if (k % 2 == 0) 1f else 0.78f)).toFloat(),
            (kotlin.math.sin(ang) * radio * (if (k % 2 == 0) 1f else 0.78f)).toFloat())
        val ruta = androidx.compose.ui.graphics.Path().apply {
            moveTo(c.x, c.y - lado); lineTo(c.x + lado * 0.28f, c.y - lado * 0.28f); lineTo(c.x + lado, c.y)
            lineTo(c.x + lado * 0.28f, c.y + lado * 0.28f); lineTo(c.x, c.y + lado); lineTo(c.x - lado * 0.28f, c.y + lado * 0.28f)
            lineTo(c.x - lado, c.y); lineTo(c.x - lado * 0.28f, c.y - lado * 0.28f); close()
        }
        drawPath(ruta, color.copy(alpha = (1f - t).coerceIn(0f, 1f)))
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
    "juego" -> if (plural) R.string.tipo_juegos else R.string.tipo_juego
    "autor" -> if (plural) R.string.tipo_autores else R.string.tipo_autor
    "director" -> if (plural) R.string.tipo_directores else R.string.tipo_director
    else -> if (plural) R.string.tipo_artistas else R.string.tipo_artista
})

/** Cómo se dice cada estado según el tipo: "Por ver", "Por leer", "Por escuchar", "Quiero jugar"… */
@Composable
fun nombreEstado(estado: String, tipo: String?): String = stringResource(when (estado) {
    "quiero" -> when (tipo) { "libro" -> R.string.estado_por_leer; "album" -> R.string.estado_por_escuchar; "juego" -> R.string.estado_por_jugar
        null -> R.string.estado_pendiente; else -> R.string.estado_por_ver }
    "en_curso" -> when (tipo) { "libro" -> R.string.estado_leyendo; "album" -> R.string.estado_escuchando; "juego" -> R.string.estado_jugando
        null -> R.string.estado_en_curso; else -> R.string.estado_viendo }
    "terminado" -> when (tipo) { "libro" -> R.string.estado_leido; "album" -> R.string.estado_escuchado; null, "juego" -> R.string.estado_terminado; else -> R.string.estado_visto }
    "" -> R.string.estado_sin
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
    TextButton({ abierto = true }, Modifier.clip(FormaPildora).background(lerp(f.fondo, f.texto, 0.06f))) { Text(fechaCorta(fecha, idioma), color = f.texto) }
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


/** Los enlaces a otros servicios (Spotify, Wikipedia, IMDb…) de algo enlazado, como píldoras que abren la app o la web. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun Enlaces(vm: io.github.fogonapps.cajon.CajonViewModel, fuente: String) {
    if (fuente.isEmpty()) return
    androidx.compose.runtime.LaunchedEffect(fuente) { vm.cargarEnlaces(fuente) }
    val todos by vm.enlaces.collectAsStateWithLifecycle()
    val lista = todos[fuente].orEmpty()
    if (lista.isEmpty()) return
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val oficial = stringResource(R.string.enlace_sitio_oficial)
        val wiki = io.github.fogonapps.cajon.datos.Enlace.WIKI
        lista.forEach { e ->
            val nombre = when {
                e.nombre == io.github.fogonapps.cajon.datos.Enlace.OFICIAL -> oficial
                e.nombre.startsWith(wiki) -> stringResource(R.string.enlace_wiki, e.nombre.removePrefix(wiki))
                else -> e.nombre
            }
            Pildora(nombre, Iconos.Enlace) { runCatching { uri.openUri(e.url) } }
        }
    }
}

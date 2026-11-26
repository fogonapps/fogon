package io.github.fogonapps.cajon.ui

import android.graphics.Bitmap
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.Pantalla
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Lamina
import io.github.fogonapps.cajon.datos.Obra
import io.github.fogonapps.cajon.datos.ResumenAnio
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.FormaPildora
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.forma
import io.github.fogonapps.identidad.rebote
import kotlinx.coroutines.launch

/** Cuánto se queda cada lámina antes de pasar sola a la siguiente. */
private const val DURACION_LAMINA = 6000

/**
 * El resumen del año como historias: una lámina por pantalla (tocar a la derecha avanza, a la izquierda vuelve, el dedo
 * apretado pausa y Atrás sale). Cada lámina y la del cierre se comparten como imagen de historia (9:16) o cuadrada
 * (1:1). Con muy poco en el año, en vez de un resumen pobre, invita a traer el historial de otras apps.
 */
@Composable
fun Resumen(vm: CajonViewModel, todo: Estado, anio: Int) {
    // Los géneros de los juegos en el idioma de la app, como en las cifras.
    val e = remember(todo) { todo.copy(obras = vm.conGenerosVisibles(todo.obras)) }
    val r = remember(e, anio) { ResumenAnio.de(e, anio) }
    var verIgual by rememberSaveable { mutableStateOf(false) }
    if (r.pocosDatos && !verIgual) PocosDatos(vm, r) { verIgual = true }
    else Historias(vm, r, todo)
}

/** Lo que tiene el año: la cifra, por qué conviene traer el historial y cómo. */
@Composable
private fun PocosDatos(vm: CajonViewModel, r: ResumenAnio, verIgual: () -> Unit) {
    val f = FogonTema.actual
    Cabecera(stringResource(R.string.resumen_titulo, r.anio), vm::cerrar)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.resumen_poco_titulo, r.anio), fontFamily = f.titulos, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 34.sp,
            color = f.texto, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.resumen_poco_texto), style = MaterialTheme.typography.bodyLarge, color = f.textoSuave)
        TraerHistorial(LocalAcciones.current, conTitulo = false)
        if (r.total + r.registros > 0) BotonSecundario(stringResource(R.string.resumen_ver_igual), verIgual, Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Historias(vm: CajonViewModel, r: ResumenAnio, todo: Estado) {
    val f = FogonTema.actual
    val laminas = r.laminas
    var i by rememberSaveable { mutableIntStateOf(0) }
    if (i > laminas.lastIndex) i = laminas.lastIndex
    var pausa by remember { mutableStateOf(false) }
    var compartir by remember { mutableStateOf<Lamina?>(null) }
    val progreso = remember { Animatable(0f) }
    var de by remember { mutableIntStateOf(-1) }
    // Con «menos movimiento» o con un lector de pantalla no pasan solas: se avanza tocando.
    val contexto = LocalContext.current
    val lector = remember { contexto.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true }
    val sola = !f.menosMovimiento && !lector && compartir == null
    LaunchedEffect(i, pausa, sola) {
        if (de != i) { progreso.snapTo(0f); de = i }
        if (!sola || pausa || laminas[i] == Lamina.CIERRE) return@LaunchedEffect
        progreso.animateTo(1f, tween(((1f - progreso.value) * DURACION_LAMINA).toInt(), easing = LinearEasing))
        if (i < laminas.lastIndex) i++
    }
    fun adelante() { if (i < laminas.lastIndex) i++ }
    fun atras() { if (i > 0) i-- }
    val txSiguiente = stringResource(R.string.resumen_siguiente)
    val txAnterior = stringResource(R.string.resumen_anterior)
    val perfil = PerfilDelResumen(todo.ajustes.nombre, todo.perfil.portada)

    Box(Modifier.fillMaxSize()) {
        // La lámina ocupa todo: tocar a la izquierda vuelve, a la derecha avanza, el dedo apretado pausa.
        Box(Modifier.fillMaxSize()
            .pointerInput(laminas.size) {
                detectTapGestures(
                    onPress = { pausa = true; tryAwaitRelease(); pausa = false },
                    onTap = { o -> if (o.x < size.width * 0.3f) atras() else adelante() },
                )
            }
            .semantics { customActions = listOf(CustomAccessibilityAction(txSiguiente) { adelante(); true }, CustomAccessibilityAction(txAnterior) { atras(); true }) }) {
            AnimatedContent(i, label = "lamina", transitionSpec = {
                if (f.menosMovimiento) EnterTransition.None togetherWith ExitTransition.None
                else (fadeIn(tween(260)) + slideInHorizontally(tween(260)) { (if (targetState > initialState) 1 else -1) * it / 14 }) togetherWith fadeOut(tween(160))
            }) { n ->
                Encajar(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 76.dp, bottom = if (laminas[n] == Lamina.CIERRE) 150.dp else 32.dp)) {
                    ContenidoLamina(r, laminas[n], perfil.nombre, compacto = false)
                }
            }
        }
        // Arriba: las rayas de avance, compartir esta lámina y cerrar.
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "${i + 1} / ${laminas.size}" }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                laminas.indices.forEach { k ->
                    val lleno = when { k < i -> 1f; k > i -> 0f; !sola || laminas[i] == Lamina.CIERRE -> 1f; else -> progreso.value }
                    Box(Modifier.weight(1f).height(3.dp).clip(FormaPildora).background(f.linea)) {
                        Box(Modifier.fillMaxWidth(lleno).fillMaxHeight().background(f.texto))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                BotonRedondo(Iconos.Compartir, stringResource(R.string.resumen_compartir_lamina)) { compartir = laminas[i] }
                BotonRedondo(Iconos.Cerrar, stringResource(R.string.historia_cerrar)) { vm.cerrar() }
            }
        }
        // En el cierre: compartir el año entero y volver a verlo.
        if (laminas[i] == Lamina.CIERRE) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonRiso(stringResource(R.string.resumen_compartir_anio), { compartir = Lamina.CIERRE }, Modifier.fillMaxWidth())
            BotonSecundario(stringResource(R.string.resumen_ver_de_nuevo), { i = 0 }, Modifier.fillMaxWidth())
        }
    }

    compartir?.let { l ->
        val acciones = LocalAcciones.current
        val texto = stringResource(R.string.resumen_compartir_texto, r.anio)
        DialogoCompartirResumen(r, l, perfil, { compartir = null }) { acciones?.compartir?.invoke(it, texto) }
    }
}

/** Lo del perfil que va en las imágenes: el nombre y el banner, si lo tiene. */
private data class PerfilDelResumen(val nombre: String, val banner: String)

/** Un botón redondo de 48 dp con su ícono. */
@Composable
private fun BotonRedondo(icono: io.github.fogonapps.identidad.Dibujo, descripcion: String, onClick: () -> Unit) {
    Box(Modifier.rebote(0.9f).size(48.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = descripcion; role = Role.Button },
        contentAlignment = Alignment.Center) { IconoRiso(icono, null, tamano = 22.dp, tinta = FogonTema.actual.texto) }
}

/**
 * Lo que dice cada lámina, igual en la pantalla y en la imagen que se comparte. [compacto]: la imagen cuadrada, donde
 * cabe menos (letras más chicas y menos portadas).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.ContenidoLamina(r: ResumenAnio, lamina: Lamina, nombre: String, compacto: Boolean) {
    val f = FogonTema.actual
    val idioma = LocalConfiguration.current.locales[0]
    fun t(grande: Int, chico: Int): TextUnit = (if (compacto) chico else grande).sp
    @Composable
    fun Etiqueta(texto: String) = Text(texto, style = if (compacto) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall, color = f.textoSuave)
    @Composable
    fun Titulo(texto: String) = Text(texto, fontFamily = f.titulos, fontWeight = FontWeight.Bold, fontSize = t(34, 22), lineHeight = t(38, 26), color = f.texto,
        modifier = Modifier.semantics { heading() })
    @Composable
    fun Enorme(texto: String, grande: Int = 120, chico: Int = 64) = Text(texto, fontFamily = f.titulos, fontWeight = FontWeight.Black, fontSize = t(grande, chico),
        lineHeight = t(grande + 4, chico + 2), color = f.texto, maxLines = 1,
        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = t(grande, chico)))
    @Composable
    fun Linea(texto: String) = Text(texto, style = if (compacto) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium, color = f.texto)
    val espacio = if (compacto) 6.dp else 12.dp

    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(espacio)) {
        when (lamina) {
            Lamina.TOTAL -> {
                Etiqueta(stringResource(R.string.resumen_titulo, r.anio))
                Enorme("${r.total}")
                Titulo(pluralStringResource(R.plurals.resumen_terminadas, r.total))
                if (r.registros > 0) Linea(pluralStringResource(R.plurals.resumen_en_diario, r.registros, r.registros))
                if (r.recientes.isNotEmpty()) Abanico(r.recientes.take(if (compacto) 5 else 7), if (compacto) 50.dp else 84.dp)
            }
            Lamina.TIPOS -> {
                Etiqueta(stringResource(R.string.resumen_titulo, r.anio))
                Titulo(stringResource(R.string.resumen_tipos))
                r.porTipo.forEach { (tipo, n) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconoRiso(Iconos.de(tipo), null, tamano = if (compacto) 18.dp else 26.dp, tinta = f.texto)
                        Text("$n", fontFamily = f.titulos, fontWeight = FontWeight.Black, fontSize = t(40, 22), lineHeight = t(44, 24), color = f.texto)
                        Text(nombreTipo(tipo, plural = n != 1), style = if (compacto) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium, color = f.textoSuave)
                    }
                }
            }
            Lamina.TIEMPO -> {
                Etiqueta(stringResource(R.string.resumen_titulo, r.anio))
                Titulo(stringResource(R.string.resumen_tiempo))
                val horas = r.minutos / 60
                if (horas > 0) { Enorme("$horas", 88, 44); Linea(pluralStringResource(R.plurals.resumen_horas, horas)) }
                if (r.paginas > 0) { Enorme("${r.paginas}", 88, 44); Linea(pluralStringResource(R.plurals.resumen_paginas, r.paginas)) }
                if (r.canciones > 0) { Enorme("${r.canciones}", 88, 44); Linea(pluralStringResource(R.plurals.resumen_canciones, r.canciones)) }
            }
            Lamina.MEJORES -> {
                Etiqueta(stringResource(R.string.resumen_titulo, r.anio))
                Titulo(stringResource(R.string.resumen_mejores))
                Portadas(r.mejores, if (compacto) 84.dp else null) { o -> Estrellas(o.nota, if (compacto) 11.sp else 14.sp) }
            }
            Lamina.GUSTOS -> {
                r.genero?.let { (g, n) ->
                    Etiqueta(stringResource(R.string.resumen_genero))
                    Enorme(g, 56, 30)
                    Linea(pluralStringResource(R.plurals.resumen_en_obras, n, n))
                }
                if (r.genero != null && r.creador != null) Spacer(Modifier.height(espacio))
                r.creador?.let { (c, n) ->
                    Etiqueta(stringResource(R.string.resumen_creador))
                    Enorme(c, 48, 26)
                    Linea(pluralStringResource(R.plurals.resumen_en_obras, n, n))
                }
            }
            Lamina.MES -> {
                val mes = r.mes ?: 1
                Etiqueta(stringResource(R.string.resumen_mes))
                Enorme(java.time.Month.of(mes).getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, idioma).replaceFirstChar { it.titlecase(idioma) }, 72, 40)
                Linea(pluralStringResource(R.plurals.resumen_anotaciones, r.enMes, r.enMes))
                BarrasDelAnio(r.porMes, if (compacto) 60.dp else 120.dp)
            }
            Lamina.RACHA -> {
                Etiqueta(stringResource(R.string.resumen_racha))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconoRiso(Iconos.Racha, null, tamano = if (compacto) 36.dp else 64.dp, tinta = f.texto)
                    Enorme("${r.racha}")
                }
                Titulo(pluralStringResource(R.plurals.resumen_dias_seguidos, r.racha))
            }
            Lamina.FAVORITAS -> {
                Etiqueta(stringResource(R.string.resumen_titulo, r.anio))
                Titulo(stringResource(R.string.resumen_favoritas))
                val obras = r.favoritas.take(if (compacto) 4 else 6)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = if (compacto) 4 else 3) {
                    obras.forEach { o -> Portada(o.portada, o.tipo, o.titulo, Modifier.width(if (compacto) 64.dp else 96.dp), esquina = 8.dp, conTitulo = false) }
                }
            }
            Lamina.CIERRE -> {
                Etiqueta(stringResource(R.string.resumen_titulo, r.anio))
                if (nombre.isNotBlank()) Titulo(nombre)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Enorme("${r.total}", 88, 48)
                    Text(pluralStringResource(R.plurals.resumen_terminadas, r.total), style = MaterialTheme.typography.titleMedium, color = f.texto,
                        modifier = Modifier.padding(bottom = if (compacto) 8.dp else 16.dp))
                }
                // Lo que hay, de a dos columnas.
                val datos = buildList {
                    if (r.minutos >= 60) add(stringResource(R.string.resumen_dato_horas) to "${r.minutos / 60}")
                    if (r.paginas > 0) add(stringResource(R.string.paginas_leidas) to "${r.paginas}")
                    r.genero?.let { add(stringResource(R.string.resumen_genero) to it.first) }
                    r.creador?.let { add(stringResource(R.string.resumen_creador) to it.first) }
                    r.mes?.let { add(stringResource(R.string.resumen_mes) to java.time.Month.of(it).getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, idioma).replaceFirstChar { c -> c.titlecase(idioma) }) }
                    if (r.racha >= 2) add(stringResource(R.string.resumen_racha) to pluralStringResource(R.plurals.resumen_dias, r.racha, r.racha))
                }.take(if (compacto) 4 else 6)
                datos.chunked(2).forEach { fila ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        fila.forEach { (etiqueta, valor) -> Dato(etiqueta, valor, compacto, Modifier.weight(1f)) }
                        if (fila.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                val portadas = (r.mejores + r.favoritas + r.recientes).distinctBy { it.id }.take(if (compacto) 5 else 4)
                if (portadas.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.clearAndSetSemantics {}) {
                    portadas.forEach { o -> Portada(o.portada, o.tipo, o.titulo, Modifier.width(if (compacto) 40.dp else 64.dp), esquina = if (compacto) 5.dp else 8.dp, conTitulo = false) }
                }
            }
        }
    }
}

/**
 * Centra la lámina en el alto que hay y, si no cabe (letra grande, pantalla chica, la imagen cuadrada), la achica
 * entera en vez de cortarla o encimar sus partes.
 */
@Composable
private fun Encajar(modifier: Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    androidx.compose.ui.layout.Layout({ Column(Modifier.fillMaxWidth()) { contenido() } }, modifier) { medibles, c ->
        val p = medibles.first().measure(c.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
        val alto = if (c.hasBoundedHeight) c.maxHeight else p.height
        val escala = if (p.height > alto) alto.toFloat() / p.height else 1f
        layout(c.maxWidth, alto) {
            p.placeWithLayer(((c.maxWidth - p.width * escala) / 2).toInt(), ((alto - p.height * escala) / 2).toInt()) {
                scaleX = escala; scaleY = escala; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
            }
        }
    }
}

/** Un dato del cierre: la raya fina arriba, el valor en la letra de los títulos y la etiqueta. */
@Composable
private fun Dato(etiqueta: String, valor: String, compacto: Boolean, modifier: Modifier) {
    val f = FogonTema.actual
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(f.linea))
        Text(valor, fontFamily = f.titulos, fontWeight = FontWeight.Bold, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = if (compacto) 17.sp else 24.sp))
        Text(etiqueta, style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Las portadas en abanico, la más reciente arriba: el color de la lámina. */
@Composable
private fun Abanico(obras: List<Obra>, ancho: Dp) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(ancho * 1.6f).clearAndSetSemantics {}) {
        // Se aprietan si no caben a lo ancho.
        val paso = if (obras.size > 1) minOf(ancho * 0.6f, (maxWidth - ancho - 8.dp) / (obras.size - 1)) else 0.dp
        obras.reversed().forEachIndexed { i, o ->
            val giro = (i - (obras.size - 1) / 2f) * 3f
            Portada(o.portada, o.tipo, o.titulo, Modifier.width(ancho).align(Alignment.CenterStart).offset(x = paso * i)
                .graphicsLayer { rotationZ = giro }.shadow(6.dp, forma(6.dp), clip = false), esquina = 6.dp, conTitulo = false)
        }
    }
}

/** Hasta tres portadas lado a lado con su título y lo de abajo. Con [ancho], de ese ancho; si no, se reparten la fila. */
@Composable
private fun Portadas(obras: List<Obra>, ancho: Dp?, abajo: @Composable (Obra) -> Unit) {
    val f = FogonTema.actual
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        obras.forEach { o ->
            Column(if (ancho != null) Modifier.width(ancho) else Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Portada(o.portada, o.tipo, o.titulo, Modifier.fillMaxWidth(), esquina = 8.dp, conTitulo = false)
                Text(o.titulo, style = MaterialTheme.typography.labelLarge, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                abajo(o)
            }
        }
        if (ancho == null) repeat(3 - obras.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** Doce barras, una por mes; el más lleno, en tinta entera. */
@Composable
private fun BarrasDelAnio(meses: IntArray, alto: Dp) {
    val f = FogonTema.actual
    val maximo = meses.max().coerceAtLeast(1)
    val idioma = LocalConfiguration.current.locales[0]
    Column(Modifier.clearAndSetSemantics {}) {
        Canvas(Modifier.fillMaxWidth().height(alto)) {
            val hueco = size.width / 12; val ancho = hueco * 0.6f
            meses.forEachIndexed { i, n ->
                val h = if (n > 0) ((n.toFloat() / maximo) * size.height).coerceAtLeast(3.dp.toPx()) else 2.dp.toPx()
                val color = when { n <= 0 -> f.linea; n == maximo -> f.texto; else -> f.texto.copy(alpha = 0.4f) }
                drawRoundRect(color, Offset(i * hueco + (hueco - ancho) / 2, size.height - h), Size(ancho, h), CornerRadius(ancho / 2))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            for (m in 1..12) Text(java.time.Month.of(m).getDisplayName(java.time.format.TextStyle.NARROW, idioma), style = MaterialTheme.typography.labelSmall,
                color = f.textoSuave, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * La imagen para compartir: la lámina en papel, con el banner del perfil arriba si lo tiene y la marca «fabs» abajo.
 * Mide 360 × 640 dp (historia, 9:16) o 360 × 360 dp (cuadrada, 1:1).
 */
@Composable
private fun TarjetaResumen(r: ResumenAnio, lamina: Lamina, perfil: PerfilDelResumen, historia: Boolean) {
    val f = FogonTema.actual
    Column(Modifier.size(360.dp, if (historia) 640.dp else 360.dp).background(f.fondo)) {
        if (perfil.banner.isNotBlank()) Box(Modifier.fillMaxWidth().height(if (historia) 132.dp else 52.dp).background(f.superficie)) {
            if (perfil.banner.startsWith("banner:")) io.github.fogonapps.perfil.BannerPerfil(perfil.banner.removePrefix("banner:"), Modifier.matchParentSize())
            else coil3.compose.AsyncImage(perfil.banner, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
        Encajar(Modifier.weight(1f).fillMaxWidth().padding(horizontal = if (historia) 28.dp else 22.dp, vertical = if (historia) 24.dp else 14.dp)) {
            ContenidoLamina(r, lamina, perfil.nombre, compacto = !historia)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = if (historia) 28.dp else 22.dp).padding(bottom = if (historia) 22.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { MarcaCompartir() }
            Text("${r.anio}", fontFamily = f.titulos, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = f.textoSuave)
        }
    }
}

/**
 * Elegir el formato (historia o cuadrada) y compartir. La tarjeta se dibuja a su tamaño real y se ve achicada: lo que se
 * comparte es la tarjeta entera, a la resolución del teléfono.
 */
@Composable
private fun DialogoCompartirResumen(r: ResumenAnio, lamina: Lamina, perfil: PerfilDelResumen, onCerrar: () -> Unit, onCompartir: (Bitmap) -> Unit) {
    val f = FogonTema.actual
    val capa = rememberGraphicsLayer()
    val alcance = rememberCoroutineScope()
    var historia by rememberSaveable { mutableStateOf(true) }
    val altoPantalla = with(androidx.compose.ui.platform.LocalDensity.current) { androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height.toDp() }
    Dialog(onCerrar, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(12.dp).clip(forma(28.dp)).background(f.fondo).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val alto = if (historia) 640.dp else 360.dp
                val escala = minOf(maxWidth / 360.dp, (altoPantalla * 0.58f) / alto, 1f)
                Box(Modifier.size(360.dp * escala, alto * escala).clip(forma(16.dp)), contentAlignment = Alignment.Center) {
                    Box(Modifier.requiredSize(360.dp, alto).graphicsLayer { scaleX = escala; scaleY = escala }
                        .drawWithContent { capa.record { this@drawWithContent.drawContent() }; drawLayer(capa) }) {
                        TarjetaResumen(r, lamina, perfil, historia)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipPerfil(stringResource(R.string.resumen_formato_historia), historia) { historia = true }
                ChipPerfil(stringResource(R.string.resumen_formato_cuadrada), !historia) { historia = false }
            }
            BotonRiso(stringResource(R.string.compartir), { alcance.launch { onCompartir(capa.toImageBitmap().asAndroidBitmap()); onCerrar() } }, Modifier.fillMaxWidth())
            BotonSecundario(stringResource(R.string.cancelar), onCerrar, Modifier.fillMaxWidth())
        }
    }
}

/**
 * El acceso al resumen (en el Inicio en diciembre y enero, y siempre en las cifras): el abanico de lo último terminado,
 * «Tu 2026 en Fabs» en la letra de los títulos y una línea que invita a verlo y compartirlo.
 */
@Composable
fun AccesoResumen(anio: Int, recientes: List<Obra>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val f = FogonTema.actual
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).clip(forma(16.dp)).clickable(onClick = onClick).semantics(mergeDescendants = true) { role = Role.Button }
        .padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        val portadas = recientes.take(3)
        if (portadas.isNotEmpty()) Box(Modifier.width((36 + 14 * (portadas.size - 1)).dp).height(56.dp).clearAndSetSemantics {}) {
            portadas.reversed().forEachIndexed { i, o ->
                Portada(o.portada, o.tipo, o.titulo, Modifier.width(36.dp).align(Alignment.CenterStart).offset(x = (14 * i).dp)
                    .graphicsLayer { rotationZ = (i - (portadas.size - 1) / 2f) * 6f }.shadow(4.dp, forma(5.dp), clip = false), esquina = 5.dp, conTitulo = false)
            }
        } else IconoRiso(Iconos.Cifras, null, tamano = 26.dp, tinta = f.texto)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.resumen_titulo, anio), fontFamily = f.titulos, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp, color = f.texto)
            Text(stringResource(R.string.resumen_acceso_texto), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        }
        IconoRiso(Iconos.Siguiente, null, tamano = 18.dp, tinta = f.textoSuave)
    }
}

/** Tras traer el historial de otra app: cuántas llegaron y el botón para mirar los números. */
@Composable
fun AvisoImportado(n: Int, onVer: () -> Unit, onCerrar: () -> Unit, modifier: Modifier = Modifier) {
    val f = FogonTema.actual
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(f.linea))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(pluralStringResource(R.plurals.resumen_importadas, n, n), fontFamily = f.titulos, fontWeight = FontWeight.SemiBold, fontSize = 19.sp,
                lineHeight = 23.sp, color = f.texto, modifier = Modifier.weight(1f).semantics { heading() })
            BotonRedondo(Iconos.Cerrar, stringResource(R.string.historia_cerrar), onCerrar)
        }
        BotonRiso(stringResource(R.string.resumen_mira_tus_numeros), onVer, Modifier.fillMaxWidth())
    }
}

/** El año que el Inicio y el aviso de importar abren: el que corre o, en enero, el que acaba de terminar. */
fun anioDelResumen(vm: CajonViewModel): Int = ResumenAnio.anioDelResumen(vm.hoy())

/** Abre el resumen de [anio]. */
fun abrirResumen(vm: CajonViewModel, anio: Int = anioDelResumen(vm)) = vm.abrir(Pantalla.Resumen(anio))

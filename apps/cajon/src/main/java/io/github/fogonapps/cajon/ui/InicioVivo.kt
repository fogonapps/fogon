package io.github.fogonapps.cajon.ui

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.Pantalla
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.Imagenes
import io.github.fogonapps.cajon.datos.Obra
import io.github.fogonapps.cajon.datos.Tipos
import io.github.fogonapps.identidad.Estilo
import io.github.fogonapps.identidad.FogonTema
import kotlin.math.abs
import kotlinx.coroutines.launch

/*
 * El Inicio vivo de Fabs (rebranding 2026-11, ronda 3 aprobada en prototipo): lo que tienes empezado en un carrusel
 * con profundidad, con las portadas reales en grande y el fondo teñido con el color de la portada que miras.
 * Sale de las referencias más vistas de apps de cine, libros y música (carátulas protagonistas, profundidad,
 * color que sale del contenido, movimiento con resorte). Con «menos movimiento», el carrusel queda plano.
 */

/** El fondo ambiental: la portada enfocada, muy borrosa y tenue, que se funde con el papel hacia abajo. */
@Composable
fun FondoDePortada(url: String?, modifier: Modifier = Modifier) {
    val f = FogonTema.actual
    if (f.estilo == Estilo.CONTRASTE) return
    val contexto = LocalContext.current
    Box(modifier) {
        Crossfade(url, animationSpec = tween(700), label = "fondoPortada") { u ->
            if (!u.isNullOrBlank()) coil3.compose.AsyncImage(
                // Pedida diminuta: al estirarla ya se ve borrosa, también en teléfonos sin desenfoque (antes de Android 12).
                model = coil3.request.ImageRequest.Builder(contexto).data(u).size(48, 72).build(),
                contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
                    .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(48.dp) else Modifier)
                    .graphicsLayer { alpha = if (f.oscuro) 0.62f else 0.7f },
                // Más saturada: el color de la portada es lo único con color en la pantalla.
                colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(1.5f) }),
            )
        }
        // Abajo se funde con el papel, para que la pantalla siga sin cortes.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to f.fondo.copy(alpha = 0.3f), 0.45f to f.fondo.copy(alpha = 0.3f), 1f to f.fondo)))
    }
}

/**
 * Lo que tienes empezado: portadas grandes en un carrusel que gira hacia los lados, el título en la letra de Fabs
 * y su avance. Tocar la del centro abre la ficha; tocar una del lado la trae al centro. Mantener apretada la del centro
 * abre el registro en dos toques ([RegistroRapido]).
 */
@Composable
fun EmpezadoVivo(obras: List<Obra>, vm: CajonViewModel, alEnfocar: (Obra) -> Unit) {
    val f = FogonTema.actual
    val quieto = f.menosMovimiento
    // El total se lee de la lista de ahora: si una deja de estar empezada, el carrusel no busca una que ya no está.
    val lista by androidx.compose.runtime.rememberUpdatedState(obras)
    val estado = rememberPagerState { lista.size }
    val alcance = androidx.compose.runtime.rememberCoroutineScope()
    // Si la lista se achica por debajo de la página actual (se terminó la última), el carrusel vuelve a la última que queda.
    androidx.compose.runtime.LaunchedEffect(obras.size) { if (obras.isNotEmpty() && estado.currentPage > obras.lastIndex) estado.scrollToPage(obras.lastIndex) }
    val actual = obras.getOrNull(estado.currentPage.coerceIn(0, (obras.size - 1).coerceAtLeast(0))) ?: return
    androidx.compose.runtime.LaunchedEffect(actual.id) { alEnfocar(actual) }
    var rapida by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    rapida?.let { id -> obras.firstOrNull { it.id == id }?.let { o -> RegistroRapido(vm, o) { rapida = null } } }
    val registrarRapido = stringResource(R.string.rapido_registrar)

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.en_curso), style = MaterialTheme.typography.labelLarge, color = f.textoSuave,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val ancho = 200.dp
            val lado = ((maxWidth - ancho) / 2).coerceAtLeast(16.dp)
            HorizontalPager(estado, Modifier.fillMaxWidth().height(318.dp), contentPadding = PaddingValues(horizontal = lado),
                pageSpacing = if (quieto) 12.dp else (-18).dp, key = { obras.getOrNull(it)?.id ?: it }, beyondViewportPageCount = 2) { i ->
                val o = obras.getOrNull(i) ?: return@HorizontalPager
                val corrimiento = (estado.currentPage - i) + estado.currentPageOffsetFraction
                val d = corrimiento.coerceIn(-1.5f, 1.5f)
                val forma = RoundedCornerShape(18.dp)
                Box(Modifier.fillMaxSize().padding(vertical = 8.dp).graphicsLayer {
                    val escala = 1f - (if (quieto) 0.08f else 0.16f) * abs(d).coerceAtMost(1f)
                    scaleX = escala; scaleY = escala
                    if (!quieto) { rotationY = d * 34f; cameraDistance = 14f * density }
                    alpha = 1f - 0.3f * abs(d).coerceAtMost(1.5f)
                }.shadow(if (abs(d) < 0.5f) 22.dp else 8.dp, forma, clip = false)
                    .clip(forma)
                    // Las de los lados, giradas y achicadas, no son un botón para el lector de pantalla (el carrusel se
                    // recorre deslizando); el dedo sí las puede tocar para traerlas al centro.
                    .then(if (i != estado.currentPage) Modifier.clearAndSetSemantics {} else Modifier)
                    .combinedClickable(onLongClickLabel = registrarRapido, onLongClick = { if (i == estado.currentPage) rapida = o.id }) {
                        if (i == estado.currentPage) vm.abrir(Pantalla.Ficha(o.id))
                        else alcance.launch { estado.animateScrollToPage(i, animationSpec = spring(0.72f, Spring.StiffnessMediumLow)) }
                    }) {
                    Portada(o.portada, o.tipo, o.titulo, Modifier.fillMaxSize(), esquina = 18.dp, lugar = Imagenes.Lugar.FICHA)
                }
            }
        }
        // El título y el avance de la del centro; al cambiar, el nuevo entra desde el lado hacia donde se deslizó.
        AnimatedContent(actual, label = "tituloEmpezado", contentKey = { it.id }, transitionSpec = {
            val haciaDerecha = obras.indexOf(targetState) > obras.indexOf(initialState)
            if (quieto) fadeIn(tween(150)) togetherWith fadeOut(tween(150))
            else (slideInHorizontally(spring(0.75f, Spring.StiffnessMediumLow)) { if (haciaDerecha) it / 3 else -it / 3 } + fadeIn(tween(220))) togetherWith
                (slideOutHorizontally(tween(180)) { if (haciaDerecha) -it / 4 else it / 4 } + fadeOut(tween(140)))
        }) { o ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(o.titulo, style = MaterialTheme.typography.headlineLarge, color = f.texto, textAlign = TextAlign.Center,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                val sigue = if (o.tipo == Tipos.LIBRO && o.pagina > 0) stringResource(R.string.va_en_pagina, o.pagina) else null
                Text(listOfNotNull(nombreTipo(o.tipo, plural = false), sigue, o.creador.takeIf { it.isNotBlank() && sigue == null }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = f.textoSuave, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                vm.L.progreso(o)?.let { p ->
                    val lleno by animateFloatAsState(p, spring(0.7f, Spring.StiffnessLow), label = "avance")
                    Box(Modifier.padding(top = 6.dp).width(150.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(f.texto.copy(alpha = 0.16f))) {
                        Box(Modifier.fillMaxWidth(lleno).height(6.dp).clip(RoundedCornerShape(3.dp)).background(f.tinta1))
                    }
                }
            }
        }
    }
}

/** Un número que, al cambiar, rueda hacia arriba como un contador (lo terminado este año). */
@Composable
fun NumeroQueRueda(valor: Int, contenido: @Composable (Int) -> Unit) {
    val quieto = FogonTema.actual.menosMovimiento
    AnimatedContent(valor, label = "numero", transitionSpec = {
        if (quieto) fadeIn(tween(120)) togetherWith fadeOut(tween(120))
        else (slideInVertically(spring(0.6f, Spring.StiffnessMediumLow)) { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
    }) { contenido(it) }
}

/** Color de las chispas que salen al calificar con la nota máxima. */
internal val ColorChispa: Color @Composable get() = FogonTema.actual.tinta1

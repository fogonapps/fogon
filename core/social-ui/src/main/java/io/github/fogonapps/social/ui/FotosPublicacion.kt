package io.github.fogonapps.social.ui
import io.github.fogonapps.identidad.BotonIconoSocial as BotonIcono

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import io.github.fogonapps.social.ClienteSocial
import io.github.fogonapps.identidad.*

/** Fotos remotas sin descarga al almacenamiento personal. */
@Composable
fun CarruselFotos(fotos: List<String>, modifier: Modifier = Modifier) {
    if (fotos.isEmpty()) return
    val f = FogonTema.actual
    val paginas = rememberPagerState { fotos.size }
    var abierta by remember(fotos) { mutableStateOf<Int?>(null) }
    Column(modifier) {
        HorizontalPager(paginas) { i ->
            AsyncImage(ClienteSocial.imagen(fotos[i]), stringResource(R.string.social_publicacion_foto, i + 1, fotos.size),
                Modifier.fillMaxWidth().aspectRatio(1f).clickable { abierta = i }, contentScale = ContentScale.Fit)
        }
        Text(stringResource(R.string.social_publicacion_posicion, paginas.currentPage + 1, fotos.size), color = f.textoSuave,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp))
    }
    abierta?.let { VisorFotos(fotos, it) { abierta = null } }
}

@Composable
fun VisorFotos(fotos: List<String>, inicial: Int, cerrar: () -> Unit) {
    val f = FogonTema.actual
    val paginas = rememberPagerState(initialPage = inicial) { fotos.size }
    var escala by remember(paginas.currentPage) { mutableFloatStateOf(1f) }
    var desplazamiento by remember(paginas.currentPage) { mutableStateOf(Offset.Zero) }
    Dialog(cerrar, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // En temas claros, la tinta del texto ofrece el fondo oscuro del visor.
        Box(Modifier.fillMaxSize().background(if (f.oscuro) f.fondo else f.texto)) {
            HorizontalPager(paginas, userScrollEnabled = escala == 1f, modifier = Modifier.fillMaxSize()) { i ->
                val transformar = rememberTransformableState { zoom, mover, _ ->
                    escala = (escala * zoom).coerceIn(1f, 5f)
                    desplazamiento = if (escala > 1f) desplazamiento + mover else Offset.Zero
                }
                AsyncImage(ClienteSocial.imagen(fotos[i]), stringResource(R.string.social_publicacion_foto, i + 1, fotos.size),
                    Modifier.fillMaxSize()
                        .pointerInput(i, paginas.currentPage) { detectTapGestures(onDoubleTap = {
                            escala = if (escala > 1f) 1f else 2.5f
                            desplazamiento = Offset.Zero
                        }) }
                        .pointerInput(i, paginas.currentPage) {
                            // Observa al final sin consumir: un pellizco o el paginador tienen prioridad.
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                var recorrido = Offset.Zero
                                var valida = escala == 1f
                                do {
                                    val evento = awaitPointerEvent(PointerEventPass.Final)
                                    if (evento.changes.size != 1 || evento.changes.any { it.isConsumed } || escala > 1f) valida = false
                                    if (valida) evento.changes.first().let { recorrido += it.position - it.previousPosition }
                                } while (evento.changes.any { it.pressed })
                                if (valida && recorrido.y > 120.dp.toPx() && recorrido.y > kotlin.math.abs(recorrido.x)) cerrar()
                            }
                        }
                        .transformable(transformar, canPan = { escala > 1f })
                        .graphicsLayer { scaleX = escala; scaleY = escala; translationX = desplazamiento.x; translationY = desplazamiento.y },
                    contentScale = ContentScale.Fit)
            }
            // Los controles conservan el contraste del tema sobre su propia barra.
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(f.barra).statusBarsPadding().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.social_publicacion_posicion, paginas.currentPage + 1, fotos.size), color = f.texto, modifier = Modifier.weight(1f))
                BotonIcono(IconosFogon.Cerrar, stringResource(R.string.social_publicacion_cerrar), tinta = f.texto, onClick = cerrar)
            }
        }
    }
}

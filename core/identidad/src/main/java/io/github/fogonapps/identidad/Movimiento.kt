package io.github.fogonapps.identidad

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * El movimiento de Fogón (ver docs/IDENTIDAD.md → Vidrio y puntos → Movimiento): resortes suaves, como en
 * las apps de iPhone. Las pantallas entran desde la derecha y la de atrás se hunde un poco; los botones
 * se achican al tocarlos; las tarjetas suben al aparecer, una tras otra; los números ruedan cifra por cifra
 * y las ilustraciones aparecen con un rebote. Con "Menos movimiento" todo queda en un fundido corto o quieto.
 */

/** El resorte de Fogón: rápido y sin rebote, para casi todo. */
fun <T> resorte() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 380f)

/** Un resorte con un poco de rebote, para lo que celebra (ilustraciones, logros). */
fun <T> resorteAlegre() = spring<T>(dampingRatio = 0.55f, stiffness = 260f)

/**
 * La pantalla de encima de la pila, animada. Al abrir una más profunda entra desde la derecha y la de atrás
 * se hunde y se apaga; al volver, al revés. [profundidad] es cuántas pantallas hay en la pila.
 */
@Composable
fun <T> PilaAnimada(pantalla: T?, profundidad: Int, contenido: @Composable (T) -> Unit) {
    val quieto = FogonTema.actual.menosMovimiento
    AnimatedContent(
        targetState = pantalla to profundidad,
        transitionSpec = {
            val adelante = targetState.second >= initialState.second
            if (quieto) fadeIn(tween(120)) togetherWith fadeOut(tween(120))
            else if (adelante) ContentTransform(
                slideInHorizontally(resorte()) { it } + fadeIn(tween(160)),
                scaleOut(resorte(), targetScale = 0.94f) + fadeOut(tween(220)),
                targetContentZIndex = targetState.second.toFloat(),
            ) else ContentTransform(
                scaleIn(resorte(), initialScale = 0.94f) + fadeIn(tween(220)),
                slideOutHorizontally(resorte()) { it } + fadeOut(tween(200)),
                targetContentZIndex = targetState.second.toFloat(),
            )
        },
        contentKey = { it.first?.let { p -> p::class } to it.second },
        label = "pila",
    ) { (p, _) -> if (p != null) contenido(p) }
}

/** El cambio de pestaña: la nueva sube un poco mientras se aclara. */
@Composable
fun <T> PestanaAnimada(pestana: T, contenido: @Composable (T) -> Unit) {
    val quieto = FogonTema.actual.menosMovimiento
    val d = with(LocalDensity.current) { 14.dp.roundToPx() }
    AnimatedContent(
        targetState = pestana,
        transitionSpec = {
            if (quieto) fadeIn(tween(100)) togetherWith fadeOut(tween(100))
            else (slideInVertically(resorte()) { d } + fadeIn(tween(200))) togetherWith fadeOut(tween(120))
        },
        label = "pestana",
    ) { contenido(it) }
}

/** Al tocar, se achica un poco y vuelve con un resorte, como un botón de iPhone. No cambia el clic. */
fun Modifier.rebote(escala: Float = 0.96f): Modifier = composed {
    val quieto = FogonTema.actual.menosMovimiento
    val s = remember { Animatable(1f) }
    val alcance = rememberCoroutineScope()
    if (quieto) this else this
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                alcance.launch { s.animateTo(escala, resorte()) }
                waitForUpOrCancellation(PointerEventPass.Initial)
                alcance.launch { s.animateTo(1f, resorteAlegre()) }
            }
        }
        .graphicsLayer { scaleX = s.value; scaleY = s.value }
}

/** Aparece subiendo y aclarándose, después de [indice] × 45 ms: así una lista de tarjetas entra una tras otra. */
fun Modifier.aparecer(indice: Int = 0): Modifier = composed {
    val quieto = FogonTema.actual.menosMovimiento
    val t = remember { Animatable(if (quieto) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (t.value < 1f) { delay(indice * 45L); t.animateTo(1f, resorte()) }
    }
    graphicsLayer {
        alpha = t.value
        translationY = (1f - t.value) * 18.dp.toPx()
    }
}

/** Aparece con un rebote (las ilustraciones y los logros). */
fun Modifier.aparecerConRebote(retraso: Long = 120): Modifier = composed {
    val quieto = FogonTema.actual.menosMovimiento
    val t = remember { Animatable(if (quieto) 1f else 0f) }
    LaunchedEffect(Unit) { if (t.value < 1f) { delay(retraso); t.animateTo(1f, resorteAlegre()) } }
    graphicsLayer {
        alpha = t.value.coerceIn(0f, 1f)
        val e = 0.82f + 0.18f * t.value
        scaleX = e; scaleY = e
    }
}

/**
 * Un texto cuyas cifras ruedan: cada carácter que cambia sale hacia arriba y el nuevo entra desde abajo.
 * Al aparecer, las cifras se encienden una tras otra. Para los lectores de pantalla es un solo texto.
 */
@Composable
fun CifrasRodantes(
    texto: String,
    familia: FontFamily,
    tamano: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    alto: TextUnit = tamano * 0.95f,
) {
    val quieto = FogonTema.actual.menosMovimiento
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = texto }, verticalAlignment = Alignment.CenterVertically) {
        // Se alinean por la derecha, como en una calculadora: cambia la unidad sin mover las demás.
        texto.forEachIndexed { i, c ->
            val desdeFin = texto.length - 1 - i
            AnimatedContent(
                targetState = c,
                transitionSpec = {
                    if (quieto) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    else (slideInVertically(resorte()) { it / 2 } + fadeIn(tween(180))) togetherWith
                        (slideOutVertically(resorte()) { -it / 2 } + fadeOut(tween(140)))
                },
                modifier = Modifier.aparecer(desdeFin.coerceAtMost(8)).clearAndSetSemantics { },
                label = "cifra$desdeFin",
            ) { ch -> Text(ch.toString(), fontFamily = familia, fontSize = tamano, lineHeight = alto, color = color, maxLines = 1, softWrap = false) }
        }
    }
}

/** Un progreso que se llena desde 0 al aparecer y se mueve con un resorte cuando cambia. */
@Composable
fun progresoAnimado(objetivo: Float): Float {
    val quieto = FogonTema.actual.menosMovimiento
    val p = remember { Animatable(if (quieto) objetivo else 0f) }
    LaunchedEffect(objetivo) { if (quieto) p.snapTo(objetivo) else p.animateTo(objetivo, spring(dampingRatio = 0.85f, stiffness = 120f)) }
    return p.value
}

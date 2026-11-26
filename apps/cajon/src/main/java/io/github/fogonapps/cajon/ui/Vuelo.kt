package io.github.fogonapps.cajon.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.fogonapps.cajon.datos.Imagenes

/*
 * El vuelo de la portada (rediseño 2026-11): al tocar una obra, su portada crece desde donde estaba hasta su lugar en
 * la ficha; al volver, hace el camino al revés. Es una capa encima de todo: la ficha entra con un fundido y su portada
 * aparece cuando la que vuela llega. Con «menos movimiento» no vuela.
 */

/** Una portada en vuelo: su imagen y de dónde a dónde va (en píxeles de la pantalla). */
data class Vuelo(val url: String, val desde: Rect, val hasta: Rect, val imagen: String = url)

class Vuelos {
    /** Dónde está cada portada a la vista (por pieza, con su dirección), para saber de dónde sale. */
    val origenes = HashMap<Any, Triple<String, Rect, String>>()
    /** La portada que estaba bajo el último toque y cuándo se tocó (se anota en el toque, antes de que la ficha exista). */
    private var tocadas: List<Triple<String, Rect, String>> = emptyList()
    private var cuando = 0L
    /** Dónde queda la portada de la ficha abierta. */
    var activo by mutableStateOf<Vuelo?>(null)

    fun tocar(punto: Offset) {
        tocadas = origenes.values.filter { it.second.contains(punto) }
        cuando = System.currentTimeMillis()
    }

    /** De dónde sale la portada [url]: la que se tocó hace menos de un segundo, si es esa. */
    /** Si se acaba de tocar una portada (la pantalla que se abre puede recibirla volando). */
    fun hayTocada() = tocadas.isNotEmpty() && System.currentTimeMillis() - cuando < 1000

    /** De dónde sale la portada [url] y con qué imagen ya cargada: la que se tocó hace menos de un segundo, si es esa. */
    fun origen(url: String): Pair<Rect, String>? = tocadas.takeIf { System.currentTimeMillis() - cuando < 1000 }?.lastOrNull { it.first == url }
        ?.let { it.second to it.third }.also { tocadas = emptyList() }
}

val LocalVuelos = staticCompositionLocalOf<Vuelos?> { null }

/** Anota cada toque de la pantalla, sin quitárselo a nadie. */
fun Modifier.anotarToques(v: Vuelos) = pointerInput(v) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            e.changes.firstOrNull { it.pressed && !it.previousPressed }?.let { v.tocar(it.position) }
        }
    }
}

/** La capa del vuelo: la portada que se mueve, encima de todo. */
@Composable
fun CapaVuelo(v: Vuelos) {
    val vuelo = v.activo ?: return
    val avance = remember(vuelo) { Animatable(0f) }
    LaunchedEffect(vuelo) {
        avance.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 320f))
        v.activo = null
    }
    val r = lerp(vuelo.desde, vuelo.hasta, avance.value)
    val d = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        coil3.compose.AsyncImage(
            model = vuelo.imagen, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.offset { IntOffset(r.left.toInt(), r.top.toInt()) }
                .size(with(d) { r.width.toDp() }, with(d) { r.height.toDp() })
                .shadow((24 * avance.value).dp, RoundedCornerShape(14.dp), clip = false).clip(RoundedCornerShape(14.dp)),
        )
    }
}

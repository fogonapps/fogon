package io.github.fogonapps.identidad

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/*
 * El tutorial guiado de Fogón (AL-04): oscurece la pantalla, deja ver el elemento de cada paso y explica en un
 * globo qué hace, con «Omitir», «Siguiente» y los puntos de avance. Igual en las cuatro apps: cada una da sus pasos
 * y marca sus elementos con [destinoGuia]. Sale la primera vez (por [version]) y se repite desde Ajustes.
 */

/** Un paso: el elemento que señala ([destino], o null para un globo centrado), su título y su explicación. */
data class PasoGuia(val destino: String?, val titulo: String, val texto: String)

/** Dónde está cada elemento marcado y si el tutorial está abierto. */
class ControladorGuia internal constructor(private val prefs: android.content.SharedPreferences?, private val clave: String, private val version: Int) {
    internal val zonas = mutableStateMapOf<String, Rect>()
    var activo by mutableStateOf(false)
        private set
    internal var paso by mutableIntStateOf(0)
    /** Ya se vio esta versión del tutorial. */
    val visto: Boolean get() = (prefs?.getInt(clave, 0) ?: 0) >= version
    /** Abre el tutorial desde el primer paso (por ejemplo, desde Ajustes → «Ver el tutorial»). */
    fun empezar() { paso = 0; activo = true }
    internal fun terminar() {
        activo = false
        prefs?.edit()?.putInt(clave, version)?.apply()
    }
}

val LocalGuia = staticCompositionLocalOf<ControladorGuia?> { null }

/** El controlador del tutorial de una app; [version] sube cuando los pasos cambian lo suficiente para volver a mostrarlo. */
@Composable
fun rememberGuia(app: String, version: Int = 1): ControladorGuia {
    val contexto = LocalContext.current
    return remember(app, version) {
        ControladorGuia(contexto.applicationContext.getSharedPreferences("guia", Context.MODE_PRIVATE), "vista.$app", version)
    }
}

/** Marca un elemento para que un paso lo señale. Sin tutorial alrededor, no hace nada. */
fun Modifier.destinoGuia(id: String): Modifier = composed {
    val guia = LocalGuia.current ?: return@composed this
    DisposableEffect(guia, id) { onDispose { guia.zonas.remove(id) } }
    onGloballyPositioned { guia.zonas[id] = it.boundsInRoot() }
}

/**
 * Pone el tutorial sobre [contenido]. Se abre solo la primera vez que [disponible] es true (después del primer
 * arranque de la app y sin pantallas encima); después, solo con [ControladorGuia.empezar].
 */
@Composable
fun ConGuia(guia: ControladorGuia, pasos: List<PasoGuia>, disponible: Boolean, contenido: @Composable () -> Unit) {
    LaunchedEffect(disponible) { if (disponible && !guia.visto && !guia.activo && pasos.isNotEmpty()) guia.empezar() }
    CompositionLocalProvider(LocalGuia provides guia) {
        Box(Modifier.fillMaxSize()) {
            contenido()
            if (guia.activo && pasos.isNotEmpty()) CapaGuia(guia, pasos)
        }
    }
}

@Composable
private fun CapaGuia(guia: ControladorGuia, pasos: List<PasoGuia>) {
    val f = FogonTema.actual
    val densidad = LocalDensity.current
    val indice = guia.paso.coerceIn(0, pasos.lastIndex)
    val paso = pasos[indice]
    val ultimo = indice == pasos.lastIndex
    var origen by remember { mutableStateOf(Offset.Zero) }
    var alto by remember { mutableFloatStateOf(0f) }
    val margen = with(densidad) { 8.dp.toPx() }
    val zona = paso.destino?.let { guia.zonas[it] }?.translate(-origen)?.inflate(margen)
    fun siguiente() { if (ultimo) guia.terminar() else guia.paso = indice + 1 }
    BackHandler { guia.terminar() }
    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { origen = it.positionInRoot(); alto = it.size.height.toFloat() }
            // Los toques no pasan a la app mientras el tutorial está abierto.
            .pointerInput(indice) { detectTapGestures { } },
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Color.Black.copy(alpha = 0.62f))
            if (zona != null) drawRoundRect(Color.Transparent, zona.topLeft, zona.size, CornerRadius(24.dp.toPx()), blendMode = BlendMode.Clear)
        }
        // Hasta medir la pantalla no se sabe dónde va el globo.
        if (alto <= 0f) return@Box
        // El globo va debajo del elemento si está arriba, y encima si está abajo; sin elemento, al centro.
        val arriba = zona != null && zona.center.y < alto / 2
        val posicion = when {
            zona == null -> Modifier.align(Alignment.Center)
            arriba -> Modifier.align(Alignment.TopCenter).padding(top = with(densidad) { (zona.bottom + margen).coerceAtLeast(0f).toDp() })
            else -> Modifier.align(Alignment.BottomCenter).padding(bottom = with(densidad) { (alto - zona.top + margen).coerceAtLeast(0f).toDp() })
        }
        val titulo = stringResource(R.string.fogon_guia_titulo)
        Column(
            posicion.padding(horizontal = 16.dp).widthIn(max = 520.dp).fillMaxWidth()
                .clip(forma(24.dp)).background(f.fondo).tarjetaFogon(forma(24.dp)).padding(20.dp)
                .semantics { paneTitle = titulo; liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pasos.indices.forEach { i ->
                    Box(Modifier.height(6.dp).width(if (i == indice) 22.dp else 6.dp).clip(CircleShape)
                        .background(if (i == indice) f.tinta1 else f.linea))
                }
            }
            Text(paso.titulo, style = MaterialTheme.typography.titleMedium, color = f.texto)
            Text(paso.texto, style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (!ultimo) TextButton({ guia.terminar() }) { Text(stringResource(R.string.fogon_guia_omitir), color = f.textoSuave) }
                Spacer(Modifier.weight(1f))
                BotonRiso(stringResource(if (ultimo) R.string.fogon_guia_entendido else R.string.fogon_guia_siguiente), ::siguiente)
            }
        }
    }
}

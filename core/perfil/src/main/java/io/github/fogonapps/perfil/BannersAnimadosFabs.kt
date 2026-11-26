package io.github.fogonapps.perfil

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/*
 * Los banners animados del catálogo de Fabs, dibujados aquí (sin video). Lo fijo es una imagen hecha con el mismo
 * script del catálogo; lo que se mueve se dibuja encima. Se trabaja sobre el lienzo del banner (1360 × 400) y se
 * escala y recorta al centro como los banners quietos. Con «menos movimiento», o si el banner no se ve, queda el
 * primer cuadro.
 */

private const val ANCHO = 1360f
private const val ALTO = 400f

/** El ciclo de cada animado, en milisegundos (el bucle cierra sin salto). */
internal val ANIMADOS_FABS = mapOf(
    "negro-ventilador" to 6000, "videoclub-abierto" to 4000, "biblioteca-polvo" to 8000,
    "vinilo-giradiscos" to 3600, "arcade-hiperespacio" to 4000,
)

@Composable
internal fun BannerAnimadoFabs(id: String, modifier: Modifier) {
    val ciclo = ANIMADOS_FABS[id] ?: return
    var visible by remember { mutableStateOf(false) }
    val quieto = menosMovimientoBanner()
    val fase = if (visible && !quieto) rememberInfiniteTransition(label = "fabs").animateFloat(
        0f, 1f, infiniteRepeatable(tween(ciclo, easing = LinearEasing)), label = "t") else null
    val capas = capasDe(id)
    val datos = remember(id) { datosDe(id) }
    Canvas(modifier.clipToBounds().visibilidadBanner { visible = it }) {
        // La fase se lee al dibujar: solo se vuelve a pintar, sin recomponer.
        val t = fase?.value ?: 0f
        val s = max(size.width / ANCHO, size.height / ALTO)
        translate((size.width - ANCHO * s) / 2f, (size.height - ALTO * s) / 2f) {
            scale(s, s, pivot = Offset.Zero) { dibujar(id, capas, datos, t) }
        }
    }
}

@Composable
private fun capasDe(id: String): List<ImageBitmap> = when (id) {
    "negro-ventilador" -> listOf(ImageBitmap.imageResource(R.drawable.animado_ventilador_base),
        ImageBitmap.imageResource(R.drawable.animado_ventilador_humo), ImageBitmap.imageResource(R.drawable.animado_ventilador_haz))
    "videoclub-abierto" -> listOf(ImageBitmap.imageResource(R.drawable.animado_abierto_apagado), ImageBitmap.imageResource(R.drawable.animado_abierto_encendido))
    "biblioteca-polvo" -> listOf(ImageBitmap.imageResource(R.drawable.animado_polvo_base))
    "vinilo-giradiscos" -> listOf(ImageBitmap.imageResource(R.drawable.animado_giradiscos_base),
        ImageBitmap.imageResource(R.drawable.animado_giradiscos_etiqueta), ImageBitmap.imageResource(R.drawable.animado_giradiscos_brazo))
    else -> listOf(ImageBitmap.imageResource(R.drawable.animado_hiperespacio_base), ImageBitmap.imageResource(R.drawable.animado_hiperespacio_moneda))
}

/** Valores al azar fijos (motas de polvo o estrellas), los mismos en cada cuadro. */
private fun datosDe(id: String): Array<FloatArray> = when (id) {
    "biblioteca-polvo" -> Random(5).let { r -> Array(420) { FloatArray(6) { r.nextFloat() } } }
    "arcade-hiperespacio" -> Random(7).let { r -> Array(520) { FloatArray(3) { r.nextFloat() } } }
    else -> emptyArray()
}

private fun DrawScope.imagen(img: ImageBitmap, x: Float = 0f, y: Float = 0f, ancho: Float = ANCHO, alto: Float = ALTO, alfa: Float = 1f, modo: BlendMode = BlendMode.SrcOver) =
    drawImage(img, dstOffset = IntOffset(x.toInt(), y.toInt()), dstSize = IntSize(ancho.toInt(), alto.toInt()), alpha = alfa, blendMode = modo)

private fun DrawScope.dibujar(id: String, capas: List<ImageBitmap>, datos: Array<FloatArray>, t: Float) {
    when (id) {
        "negro-ventilador" -> ventilador(capas, t)
        "videoclub-abierto" -> abierto(capas, t)
        "biblioteca-polvo" -> polvo(capas, datos, t)
        "vinilo-giradiscos" -> giradiscos(capas, t)
        else -> hiperespacio(capas, datos, t)
    }
}

/** La sombra de un ventilador de cuatro aspas gira un cuarto de vuelta por ciclo; el humo cruza la luz. */
private fun DrawScope.ventilador(c: List<ImageBitmap>, t: Float) {
    imagen(c[0])
    val centro = Offset(1040f, 150f)
    val aspa = Path().apply {
        moveTo(20f, -10f); lineTo(40f, -10f); lineTo(240f, -32f); lineTo(286f, -32f); lineTo(290f, 0f)
        lineTo(286f, 32f); lineTo(240f, 32f); lineTo(40f, 10f); lineTo(20f, 10f); close()
    }
    // La sombra multiplica: oscurece la luz y casi no cambia la pared, que ya es oscura. Con borde difuso.
    withTransform({ translate(centro.x, centro.y); scale(1f, 1f / 1.25f, Offset.Zero); rotate(t * 90f, Offset.Zero) }) {
        drawIntoCanvas { lienzo ->
            val nativo = lienzo.nativeCanvas
            repeat(4) { i -> nativo.save(); nativo.rotate(i * 90f); nativo.drawPath(aspa.asAndroidPath(), pinturaSombra); nativo.restore() }
            nativo.drawCircle(0f, 0f, 40f, pinturaSombra)
        }
    }
    // El humo: una tira sin costura que avanza un ancho por ciclo, visible solo dentro del haz de luz.
    drawIntoCanvas { lienzo ->
        lienzo.saveLayer(Rect(0f, 0f, ANCHO, ALTO), Paint().apply { blendMode = BlendMode.Plus; alpha = 0.3f })
        val x = -t * ANCHO
        imagen(c[1], x); imagen(c[1], x + ANCHO)
        imagen(c[2], modo = BlendMode.DstIn)
        lienzo.restore()
    }
}

private val pinturaSombra = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.rgb(31, 30, 29)
    xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.MULTIPLY)
    maskFilter = android.graphics.BlurMaskFilter(5f, android.graphics.BlurMaskFilter.Blur.NORMAL)
}

/** El neón rosado zumba y falla dos veces por ciclo; el cian queda firme. */
private fun DrawScope.abierto(c: List<ImageBitmap>, t: Float) {
    val nivel = if (t in 0.31f..0.335f || t in 0.36f..0.372f || t in 0.78f..0.79f) 0.12f else 0.93f + 0.07f * sin(t * 2f * PI.toFloat() * 30f)
    imagen(c[0])
    imagen(c[1], alfa = ((nivel - 0.12f) / 0.88f).coerceIn(0f, 1f))
}

/** Rayo de sol con polvo: cada mota da su vuelta en una elipse lenta y brilla dentro del rayo. */
private fun DrawScope.polvo(c: List<ImageBitmap>, motas: Array<FloatArray>, t: Float) {
    imagen(c[0])
    val tau = 2f * PI.toFloat() * t
    for (m in motas) {
        val (px, py, fase) = Triple(m[0], m[1], m[2]); val amp = m[3]; val tam = m[4]; val br = m[5]
        val x = ((px * ANCHO + sin(tau + fase * 6.28f) * 40f * amp + 25f * sin(2 * tau + fase * 9f)) % ANCHO + ANCHO) % ANCHO
        val y = ((py * ALTO + cos(tau + fase * 6.28f) * 22f * amp - 10f * sin(tau * 3f + fase * 3f)) % ALTO + ALTO) % ALTO
        val d = ((x - 1180f) + y * 0.85f) / 1.31f
        val rayo = ((120f - abs(d)) / 45f).coerceIn(0f, 1f) * (1.15f - y / 520f).coerceIn(0f, 1f) * 0.8f
        val brillo = ((140f + 115f * br) / 255f) * (rayo * 1.6f + 0.04f) * 1.6f
        if (brillo < 0.02f) continue
        drawCircle(Color(1f, 0.85f, 0.6f, min(1f, brillo)), 0.6f + tam * 1.6f, Offset(x, y), blendMode = BlendMode.Plus)
    }
}

/** El plato a 33⅓: la etiqueta y los puntos del borde dan dos vueltas por ciclo; el brillo de los surcos no gira. */
private fun DrawScope.giradiscos(c: List<ImageBitmap>, t: Float) {
    imagen(c[0])
    val centro = Offset(850f, 200f)
    val giro = t * 720f
    val punto = Color(0.55f, 0.55f, 0.58f, 0.8f)
    rotate(giro, centro) {
        for (k in 0 until 90) rotate(k * 4f, centro) {
            drawRect(punto, Offset(centro.x + 182.5f, centro.y - 2.6f), Size(7f, 5.2f))
        }
        val lado = 121f
        imagen(c[1], centro.x - lado / 2f, centro.y - lado / 2f, lado, lado)
    }
    drawCircle(Color(0xFF070707), 3.9f, centro)
    imagen(c[2])
}

/** Hiperespacio en el tubo: rayas que salen del punto de fuga y «INSERTE MONEDA» que parpadea. */
private fun DrawScope.hiperespacio(c: List<ImageBitmap>, estrellas: Array<FloatArray>, t: Float) {
    imagen(c[0])
    val cx = 820f; val cy = 190f
    for (e in estrellas) {
        val z = ((e[1] - t * 2f) % 1f + 1f) % 1f + 0.02f
        val a = e[0] * 2f * PI.toFloat()
        val r1 = 18f / z; val r2 = 18f / (z + 0.06f)
        val b = min(1f, 0.15f / z)
        val color = when { e[2] < 0.3f -> Color(0.35f, 0.95f, 1f); e[2] < 0.4f -> Color(1f, 0.3f, 0.75f); else -> Color(0.95f, 0.95f, 0.92f) }
        drawLine(color.copy(alpha = b), Offset(cx + cos(a) * r2 * 3f, cy + sin(a) * r2 * 1.6f), Offset(cx + cos(a) * r1 * 3f, cy + sin(a) * r1 * 1.6f),
            if (z < 0.2f) 2f else 1f, blendMode = BlendMode.Plus)
    }
    // Las líneas del tubo sobre las rayas.
    var y = 1f
    while (y < ALTO) { drawLine(Color.Black.copy(alpha = 0.3f), Offset(0f, y), Offset(ANCHO, y), 1f); y += 2f }
    drawRoundRect(Color.Black.copy(alpha = 0.88f), Offset(582f, 280f), Size(476f, 74f), CornerRadius(14f))
    if ((t * 8f) % 2f < 1.3f) imagen(c[1], 560f, 270f, 520f, 90f, modo = BlendMode.Plus)
}

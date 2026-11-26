package io.github.fogonapps.perfil

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.fogonapps.identidad.Dibujo
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.IconosFogon
import kotlin.math.roundToInt

/*
 * Una insignia como el talón de una entrada de cine: troquelado, con dos mordidas a la altura del corte y la línea
 * perforada. Ganada, tinta llena con la perforación calada; por ganar, el contorno vacío con la perforación que se
 * llena punto a punto con lo que lleva. Los niveles apilan talones detrás. Todo vectorial, con los colores del tema.
 */

/** Los colores de un talón: [tinta] (el talón lleno), [papel] (lo calado y lo de encima), [suave] y [linea] (el vacío). */
@Immutable
data class TintasTalon(val tinta: Color, val papel: Color, val suave: Color, val linea: Color)

/** Tinta sobre el papel del tema; [invertidas], papel sobre tinta (sobre una cortina oscura). */
@Composable
fun tintasTalon(invertidas: Boolean = false): TintasTalon {
    val f = FogonTema.actual
    val tinta = if (invertidas) f.fondo else f.texto
    val papel = if (invertidas) f.texto else f.fondo
    return TintasTalon(tinta, papel, lerp(papel, tinta, 0.5f), lerp(papel, tinta, 0.2f))
}

/** Lo que lleva escrito el talón grande: la marca arriba, «admite uno» sobre el corte y la unidad bajo el número. */
@Immutable
data class TextosTalon(val encabezado: String, val admite: String, val unidad: String)

/** Dónde se corta el talón, de arriba a abajo. */
private const val CORTE = 0.62f

/**
 * El talón en un cuadro de [lado]. [numero] va en la parte de abajo (la meta, o «lleva/meta» si falta); sin número,
 * una ganada lleva un chulo. [apilados] (1 a 3, solo ganadas) son los niveles. Con [grande], los textos de la hoja.
 */
@Composable
fun TalonInsignia(
    icono: Dibujo,
    ganada: Boolean,
    modifier: Modifier = Modifier,
    numero: String? = null,
    lleva: Int = 0,
    meta: Int = 1,
    apilados: Int = 1,
    lado: Dp = 72.dp,
    grande: TextosTalon? = null,
    tintas: TintasTalon = tintasTalon(),
) {
    val f = FogonTema.actual
    val medidor = rememberTextMeasurer()
    val pilas = if (ganada) apilados.coerceIn(1, 3) else 1
    val alto = lado * 0.98f * (if (pilas > 1) 0.92f else 1f)
    val ancho = alto * 0.64f
    val izquierda = (lado - ancho) / 2 + ancho * 0.06f * (pilas - 1)
    val arriba = (lado - alto) / 2
    val esGrande = grande != null
    val encima = if (ganada) tintas.papel else tintas.suave
    val n = if (esGrande) 13 else 9
    val hechos = when {
        ganada -> n
        lleva <= 0 -> 0
        else -> (n * lleva.toFloat() / meta.coerceAtLeast(1)).roundToInt().coerceIn(1, n - 1)
    }
    Box(modifier.size(lado)) {
        Spacer(Modifier.size(lado).drawWithCache {
            val w = ancho.toPx(); val h = alto.toPx(); val x = izquierda.toPx(); val y = arriba.toPx()
            val textos = numero?.let {
                val base = if (esGrande) (if (ganada) 0.28f else 0.2f) else if (it.length < 4) (if (ganada) 0.3f else 0.22f) else 0.2f
                listOf(Letrero(medidor, it, f.titulos, FontWeight.Black, w * base, encima, x + w / 2, y + h * (if (esGrande) 0.79f else 0.815f), 0f, w * 0.84f, this))
            }.orEmpty() + grande?.let { g ->
                listOf(
                    Letrero(medidor, g.encabezado, f.cifras, FontWeight.Bold, w * 0.055f, encima, x + w / 2, y + h * 0.075f, 0.35f, w * 0.9f, this),
                    Letrero(medidor, g.admite, f.cifras, FontWeight.Bold, w * 0.05f, encima, x + w / 2, y + h * (CORTE - 0.065f), 0.3f, w * 0.9f, this),
                    Letrero(medidor, g.unidad, f.cifras, FontWeight.Bold, w * 0.05f, encima, x + w / 2, y + h * 0.91f, 0.3f, w * 0.9f, this),
                )
            }.orEmpty()
            onDrawBehind {
                // Los niveles: los talones de atrás, girados como un rollo de entradas.
                for (k in pilas - 1 downTo 1) {
                    translate(size.width / 2 + w * 0.06f * (pilas - 1), size.height / 2) {
                        rotate(-6f * k, Offset.Zero) {
                            translate(-w * 0.05f * k, -h * 0.01f * k) { talon(-w / 2, -h / 2, w, h, true, true, n, n, esGrande, tintas) }
                        }
                    }
                }
                talon(x, y, w, h, ganada, pilas > 1, hechos, n, esGrande, tintas)
                textos.forEach { it.dibujar(this) }
            }
        })
        val iconoLado = ancho * (if (esGrande) 0.36f else 0.46f)
        val iconoY = arriba + alto * (CORTE / 2 + if (esGrande) 0.01f else 0.015f)
        IconoRiso(icono, null, Modifier.offset(izquierda + ancho / 2 - iconoLado / 2, iconoY - iconoLado / 2), tamano = iconoLado, tinta = encima)
        if (numero == null && ganada) {
            val chulo = ancho * 0.3f
            IconoRiso(IconosFogon.Chulo, null, Modifier.offset(izquierda + ancho / 2 - chulo / 2, arriba + alto * 0.81f - chulo / 2), tamano = chulo, tinta = encima)
        }
    }
}

/** Un texto medido una vez y centrado en ([cx], [cy]) por la altura de las mayúsculas; se achica si no cabe en [maximo]. */
private class Letrero(
    medidor: TextMeasurer, texto: String, familia: FontFamily, peso: FontWeight, tam: Float, val color: Color,
    val cx: Float, val cy: Float, espacio: Float, maximo: Float, densidad: Density,
) {
    val medida: TextLayoutResult
    val tamFinal: Float

    init {
        fun medir(t: Float) = medidor.measure(texto, TextStyle(fontFamily = familia, fontWeight = peso, color = color,
            fontSize = with(densidad) { t.toSp() }, letterSpacing = espacio.em), maxLines = 1, softWrap = false)
        val primera = medir(tam)
        tamFinal = if (primera.size.width > maximo && primera.size.width > 0) tam * maximo / primera.size.width else tam
        medida = if (tamFinal == tam) primera else medir(tamFinal)
    }

    fun dibujar(d: DrawScope) {
        val arriba = cy + tamFinal * 0.35f - medida.firstBaseline
        d.drawText(medida, topLeft = Offset(cx - medida.size.width / 2f, arriba))
    }
}

/** La silueta: rectángulo de esquinas chicas con una mordida de troquel a cada lado, a la altura del corte. */
private fun silueta(x: Float, y: Float, w: Float, h: Float): Path = Path().apply {
    val r = w * 0.07f
    val rn = w * 0.085f
    val ty = y + h * CORTE
    moveTo(x + r, y)
    lineTo(x + w - r, y)
    arcTo(Rect(Offset(x + w - r, y + r), r), -90f, 90f, false)
    lineTo(x + w, ty - rn)
    arcTo(Rect(Offset(x + w, ty), rn), -90f, -180f, false)
    lineTo(x + w, y + h - r)
    arcTo(Rect(Offset(x + w - r, y + h - r), r), 0f, 90f, false)
    lineTo(x + r, y + h)
    arcTo(Rect(Offset(x + r, y + h - r), r), 90f, 90f, false)
    lineTo(x, ty + rn)
    arcTo(Rect(Offset(x, ty), rn), 90f, -180f, false)
    lineTo(x, y + r)
    arcTo(Rect(Offset(x + r, y + r), r), 180f, 90f, false)
    close()
}

/** Un talón: lleno (con un filo de papel si [filo], para separarlo del de atrás) o vacío, y su perforación. */
private fun DrawScope.talon(x: Float, y: Float, w: Float, h: Float, ganada: Boolean, filo: Boolean, hechos: Int, n: Int, grande: Boolean, t: TintasTalon) {
    val rn = w * 0.085f
    val ty = y + h * CORTE
    if (ganada) {
        val p = silueta(x, y, w, h)
        drawPath(p, t.tinta)
        if (filo) drawPath(p, t.papel, style = Stroke(w * 0.03f))
    } else {
        val g = maxOf(0.6.dp.toPx(), w * 0.022f)
        drawPath(silueta(x + g / 2, y + g / 2, w - g, h - g), t.linea, style = Stroke(g))
    }
    val x0 = x + rn * 1.55f
    val x1 = x + w - rn * 1.55f
    val rp = w * (if (grande) 0.011f else 0.016f)
    for (k in 0 until n) {
        val px = x0 + (x1 - x0) * k / (n - 1)
        if (ganada) drawCircle(t.papel, rp, Offset(px, ty))
        else drawCircle(if (k < hechos) t.tinta else t.linea, rp * 1.25f, Offset(px, ty))
    }
}

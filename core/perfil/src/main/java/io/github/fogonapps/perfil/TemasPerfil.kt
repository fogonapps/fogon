package io.github.fogonapps.perfil

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.fogonapps.identidad.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Los fondos y marcos del perfil, como los de Steam. Cada fondo es una escena dibujada por Fogón (arte propio, sin
 * licencias de terceros) con su paleta: solo dentro de la página del perfil, los colores son los del fondo y no los
 * del tema de la app. Se compran con chispas en la tienda; el catálogo y los precios los guarda el servidor.
 */

/** La paleta de un fondo: el color de base, los paneles (semitransparentes), el texto y el acento. */
data class EstiloPerfil(val base: Color, val panel: Color, val texto: Color, val textoSuave: Color, val acento: Color, val sobreAcento: Color, val linea: Color)

object TemasPerfil {
    val FONDOS = listOf("nieve", "carmesi", "niebla", "ojos", "atardecer", "ciudad", "selva", "cosmos")
    val MARCOS = listOf("doble", "llamas", "pixel", "estrellas", "espinas", "luna")
    /** Los precios en chispas (los mismos del servidor, que es el que cobra). */
    val PRECIOS = mapOf("nieve" to 1200, "carmesi" to 1200, "niebla" to 1000, "ojos" to 900, "atardecer" to 1000, "ciudad" to 1500, "selva" to 1000, "cosmos" to 1500,
        "marco:doble" to 400, "marco:llamas" to 800, "marco:pixel" to 600, "marco:estrellas" to 700, "marco:espinas" to 800, "marco:luna" to 600)

    fun estilo(tema: String): EstiloPerfil? = when (tema) {
        "nieve" -> EstiloPerfil(Color(0xFF0E1A27), Color(0xA61A2B3D), Color(0xFFE8F1FA), Color(0xFF9DB4CC), Color(0xFF7FC4FF), Color(0xFF0E1A27), Color(0x557FA8D0))
        "carmesi" -> EstiloPerfil(Color(0xFF2A0405), Color(0xA63D0709), Color(0xFFFBE9E4), Color(0xFFD39A92), Color(0xFFFF3B30), Color(0xFF1A0203), Color(0x66FF6B5E))
        "niebla" -> EstiloPerfil(Color(0xFFBFC3C2), Color(0x99E9ECEA), Color(0xFF1E2321), Color(0xFF555C59), Color(0xFF2E3532), Color(0xFFF2F4F3), Color(0x553A403D))
        "ojos" -> EstiloPerfil(Color(0xFF050505), Color(0xB3121212), Color(0xFFF5F5F5), Color(0xFFA0A0A0), Color(0xFFFFFFFF), Color(0xFF050505), Color(0x55FFFFFF))
        "atardecer" -> EstiloPerfil(Color(0xFF2B1236), Color(0xA63E1C4A), Color(0xFFFFF1E6), Color(0xFFE6B8A8), Color(0xFFFF8A3D), Color(0xFF2B1236), Color(0x66FFB37A))
        "ciudad" -> EstiloPerfil(Color(0xFF0B0D1A), Color(0xA6151A33), Color(0xFFEDEBFF), Color(0xFFA7A3D6), Color(0xFFB98CFF), Color(0xFF0B0D1A), Color(0x55B98CFF))
        "selva" -> EstiloPerfil(Color(0xFF0B2016), Color(0xA6123222), Color(0xFFE9F7EE), Color(0xFF9CC7AD), Color(0xFF5FDB8F), Color(0xFF0B2016), Color(0x555FDB8F))
        "cosmos" -> EstiloPerfil(Color(0xFF05040F), Color(0xA612102A), Color(0xFFF0EEFF), Color(0xFFA9A4D9), Color(0xFFFFD166), Color(0xFF05040F), Color(0x55FFD166))
        else -> null
    }

    @Composable
    fun nombre(tema: String): String = stringResource(when (tema) {
        "nieve" -> R.string.tema_nieve; "carmesi" -> R.string.tema_carmesi; "niebla" -> R.string.tema_niebla; "ojos" -> R.string.tema_ojos
        "atardecer" -> R.string.tema_atardecer; "ciudad" -> R.string.tema_ciudad; "selva" -> R.string.tema_selva; "cosmos" -> R.string.tema_cosmos
        "doble" -> R.string.marco_doble; "llamas" -> R.string.marco_llamas; "pixel" -> R.string.marco_pixel; "estrellas" -> R.string.marco_estrellas
        "espinas" -> R.string.marco_espinas; "luna" -> R.string.marco_luna
        else -> R.string.tema_clasico
    })
}

/** El perfil con los colores de su fondo (solo dentro del perfil). */
fun Fogon.conEstilo(e: EstiloPerfil): Fogon = copy(
    fondo = e.base, superficie = e.panel, texto = e.texto, textoSuave = e.textoSuave, linea = e.linea, acentoTexto = e.acento,
    tinta1 = e.acento, sobreTinta1 = e.sobreAcento, tinta2 = e.textoSuave, pildora = e.texto, sobrePildora = e.base,
    barra = e.base, brillo = Color.Transparent, luz = null,
)

/** La escena de un fondo, a todo lo que ocupe; con movimiento (nieve, lluvia, estrellas) salvo con «menos movimiento». */
@Composable
fun FondoTemaPerfil(tema: String, modifier: Modifier = Modifier) {
    val e = TemasPerfil.estilo(tema) ?: return
    val quieto = FogonTema.actual.menosMovimiento
    val t = if (quieto) 0f else rememberInfiniteTransition(label = "fondo").animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "t").value
    Canvas(modifier) { drawRect(e.base); escena(tema, e, t) }
}

private fun DrawScope.escena(tema: String, e: EstiloPerfil, t: Float) {
    val w = size.width; val h = size.height
    // La escena se dibuja en la parte de arriba (como un fondo de Steam) y abajo sigue el color de base.
    val alto = minOf(h, w * 2.1f)
    fun sierra(y0: Float, amp: Float, picos: Int, color: Color, semilla: Int) {
        val al = java.util.Random(semilla.toLong())
        val p = Path().apply {
            moveTo(0f, alto); lineTo(0f, y0)
            val paso = w / picos
            for (i in 0..picos) { lineTo(i * paso, y0 - al.nextFloat() * amp); lineTo(i * paso + paso / 2, y0 - amp * (0.3f + al.nextFloat() * 0.7f)) }
            lineTo(w, alto); close()
        }
        drawPath(p, color)
    }
    fun pinos(y0: Float, n: Int, alturaMax: Float, color: Color, semilla: Int) {
        val al = java.util.Random(semilla.toLong())
        drawRect(color, Offset(0f, y0), Size(w, alto - y0))
        repeat(n) {
            val x = al.nextFloat() * w; val a = alturaMax * (0.5f + al.nextFloat() * 0.5f); val ancho = a * 0.32f
            val p = Path().apply { moveTo(x, y0 - a); lineTo(x - ancho, y0 + 2f); lineTo(x + ancho, y0 + 2f); close() }
            drawPath(p, color)
        }
    }
    fun particulas(n: Int, color: Color, tam: Float, caida: Float, viento: Float, semilla: Int, linea: Boolean = false) {
        val al = java.util.Random(semilla.toLong())
        repeat(n) {
            val x0 = al.nextFloat() * w; val y0 = al.nextFloat() * h; val v = 0.6f + al.nextFloat() * 0.8f
            val y = (y0 + t * caida * h * v) % h; val x = (x0 + t * viento * w * v + w) % w
            if (linea) drawLine(color, Offset(x, y), Offset(x - viento * 18f, y + 22f), 1.5f) else drawCircle(color, tam * v, Offset(x, y))
        }
    }
    when (tema) {
        "nieve" -> {
            drawCircle(Color(0x33CFE6FF), w * 0.12f, Offset(w * 0.78f, alto * 0.16f))
            sierra(alto * 0.45f, alto * 0.18f, 5, Color(0xFF203448), 3)
            pinos(alto * 0.62f, 18, alto * 0.22f, Color(0xFF16263A), 5)
            pinos(alto * 0.78f, 14, alto * 0.26f, Color(0xFF0E1A27), 8)
            particulas(140, Color(0xA6FFFFFF), 2.2f, 3f, 0.4f, 11)
        }
        "carmesi" -> {
            for (i in 0 until 6) drawRect(Color(0xFF8B0A0E).copy(alpha = 0.18f + i * 0.08f), Offset(0f, alto * i / 6f), Size(w, alto / 6f))
            drawCircle(Color(0xFF050101), w * 0.2f, Offset(w * 0.5f, alto * 0.26f))
            drawCircle(Color(0xFFFF3B30), w * 0.2f, Offset(w * 0.5f, alto * 0.26f), style = Stroke(6f))
            sierra(alto * 0.68f, alto * 0.3f, 7, Color(0xFF120203), 21)
            particulas(40, Color(0x88FF6B5E), 1.6f, -1.5f, 0.2f, 4)
        }
        "niebla" -> {
            drawCircle(Color(0xFFF4F4EE), w * 0.08f, Offset(w * 0.22f, alto * 0.14f))
            sierra(alto * 0.42f, alto * 0.22f, 4, Color(0xFF9EA4A2), 7)
            drawRect(Color(0x55E9ECEA), Offset(0f, alto * 0.38f), Size(w, alto * 0.08f))
            sierra(alto * 0.58f, alto * 0.2f, 6, Color(0xFF7D8582), 9)
            drawRect(Color(0x55E9ECEA), Offset(0f, alto * 0.55f), Size(w, alto * 0.08f))
            sierra(alto * 0.76f, alto * 0.16f, 9, Color(0xFF4A524F), 13)
            repeat(5) { i -> val x = (w * (0.55f + i * 0.06f) + t * w * 0.4f) % w; val y = alto * (0.12f + (i % 2) * 0.03f)
                drawPath(Path().apply { moveTo(x - 10f, y); quadraticTo(x - 5f, y - 6f, x, y); quadraticTo(x + 5f, y - 6f, x + 10f, y) }, Color(0xFF2E3532), style = Stroke(2.5f)) }
            particulas(70, Color(0x553A403D), 1f, 4f, -0.1f, 2, linea = true)
        }
        "ojos" -> {
            val al = java.util.Random(6)
            repeat(14) {
                val c = Offset(al.nextFloat() * w, al.nextFloat() * alto); val r = w * (0.08f + al.nextFloat() * 0.1f)
                val ojo = Path().apply { moveTo(c.x - r, c.y); quadraticTo(c.x, c.y - r * 0.9f, c.x + r, c.y); quadraticTo(c.x, c.y + r * 0.9f, c.x - r, c.y); close() }
                drawPath(ojo, Color(0xFFEDEDED))
                val mira = sin((t * 2 * PI + it).toFloat()) * r * 0.2f
                drawCircle(Color(0xFF050505), r * 0.32f, Offset(c.x + mira, c.y))
                drawCircle(Color(0xFFEDEDED), r * 0.08f, Offset(c.x + mira + r * 0.1f, c.y - r * 0.1f))
            }
        }
        "atardecer" -> {
            for (i in 0 until 8) drawRect(Color(0xFFFF7A45).copy(alpha = 0.06f + i * 0.05f), Offset(0f, alto * 0.06f * i), Size(w, alto * 0.06f))
            val sol = Offset(w * 0.5f, alto * 0.5f); val r = w * 0.22f
            drawCircle(Color(0xFFFFB35C), r, sol)
            for (i in 0 until 5) drawRect(Color(0xFF2B1236), Offset(0f, sol.y + r * (0.15f + i * 0.17f)), Size(w, r * 0.06f * (i + 1)))
            sierra(alto * 0.62f, alto * 0.08f, 3, Color(0xFF4A1F4F), 5)
            sierra(alto * 0.74f, alto * 0.06f, 4, Color(0xFF2B1236), 9)
        }
        "ciudad" -> {
            particulas(60, Color(0x99FFFFFF), 1f, 0f, 0f, 3)
            val al = java.util.Random(12)
            var x = 0f
            while (x < w) {
                val bw = w * (0.06f + al.nextFloat() * 0.1f); val bh = alto * (0.2f + al.nextFloat() * 0.45f); val y = alto * 0.85f - bh
                drawRect(Color(0xFF151A33), Offset(x, y), Size(bw - 3f, alto - y))
                var wy = y + 10f
                while (wy < alto * 0.85f - 10f) { var wx = x + 6f; while (wx < x + bw - 12f) { if (al.nextFloat() < 0.35f) drawRect(Color(0xA6B98CFF), Offset(wx, wy), Size(5f, 7f)); wx += 12f }; wy += 16f }
                x += bw
            }
            drawRect(Color(0xFF0B0D1A), Offset(0f, alto * 0.85f), Size(w, alto * 0.15f))
            particulas(120, Color(0x66B9C7FF), 1f, 6f, -0.15f, 9, linea = true)
        }
        "selva" -> {
            val al = java.util.Random(5)
            repeat(60) {
                val c = Offset(al.nextFloat() * w, al.nextFloat() * alto); val l = w * (0.08f + al.nextFloat() * 0.14f)
                val ang = al.nextFloat() * 360f + sin((t * 2 * PI + it).toFloat()) * 4f
                rotate(ang, c) {
                    drawPath(Path().apply { moveTo(c.x, c.y); quadraticTo(c.x + l * 0.5f, c.y - l * 0.35f, c.x + l, c.y); quadraticTo(c.x + l * 0.5f, c.y + l * 0.35f, c.x, c.y); close() },
                        listOf(Color(0xFF1E5A3A), Color(0xFF2E7D4F), Color(0xFF14432B))[it % 3])
                }
            }
            particulas(30, Color(0x885FDB8F), 1.4f, -0.5f, 0.3f, 8)
        }
        "cosmos" -> {
            val al = java.util.Random(4)
            repeat(160) { val c = Offset(al.nextFloat() * w, al.nextFloat() * h); val b = 0.4f + 0.6f * ((sin((t * 2 * PI * 3 + it).toFloat()) + 1) / 2)
                drawCircle(Color.White.copy(alpha = b * 0.8f), 0.6f + al.nextFloat() * 1.8f, c) }
            val pl = Offset(w * 0.72f, alto * 0.3f); val r = w * 0.18f
            drawCircle(Color(0xFF3B2F7A), r, pl)
            drawCircle(Color(0xFF5B4AB0), r * 0.75f, Offset(pl.x - r * 0.2f, pl.y - r * 0.2f))
            rotate(-18f, pl) { drawOval(Color(0xFFFFD166), Offset(pl.x - r * 1.7f, pl.y - r * 0.25f), Size(r * 3.4f, r * 0.5f), style = Stroke(4f)) }
        }
    }
}

/** El marco de la foto de perfil, dibujado alrededor de una foto de [tamano] (va encima, centrado). */
@Composable
fun MarcoFoto(marco: String, tamano: Dp, modifier: Modifier = Modifier) {
    if (marco !in TemasPerfil.MARCOS) return
    val f = FogonTema.actual
    val acento = f.tinta1; val texto = f.texto
    val quieto = f.menosMovimiento
    val giro = if (quieto) 0f else rememberInfiniteTransition(label = "marco").animateFloat(0f, 360f, infiniteRepeatable(tween(20000, easing = LinearEasing)), label = "g").value
    Canvas(modifier.size(tamano + 22.dp)) {
        val c = center; val r = tamano.toPx() / 2
        when (marco) {
            "doble" -> { drawCircle(acento, r + 4.dp.toPx(), c, style = Stroke(3.dp.toPx())); drawCircle(texto, r + 9.dp.toPx(), c, style = Stroke(1.5.dp.toPx())) }
            "llamas" -> rotate(giro / 4, c) {
                val n = 18
                for (i in 0 until n) {
                    val a = (i * 2 * PI / n).toFloat(); val a2 = a + (PI / n).toFloat(); val a3 = a + (2 * PI / n).toFloat()
                    val p = Path().apply {
                        moveTo(c.x + cos(a) * (r + 2f), c.y + sin(a) * (r + 2f))
                        lineTo(c.x + cos(a2) * (r + 10.dp.toPx() + (i % 3) * 3f), c.y + sin(a2) * (r + 10.dp.toPx() + (i % 3) * 3f))
                        lineTo(c.x + cos(a3) * (r + 2f), c.y + sin(a3) * (r + 2f)); close()
                    }
                    drawPath(p, if (i % 2 == 0) acento else acento.copy(alpha = 0.6f))
                }
            }
            "pixel" -> {
                val lado = r * 2 + 12.dp.toPx(); val s = 4.dp.toPx(); val o = Offset(c.x - lado / 2, c.y - lado / 2)
                var x = 0f; var i = 0
                while (x < lado) { drawRect(if (i % 2 == 0) acento else texto, Offset(o.x + x, o.y), Size(s, s)); drawRect(if (i % 2 == 0) texto else acento, Offset(o.x + x, o.y + lado - s), Size(s, s))
                    drawRect(if (i % 2 == 0) acento else texto, Offset(o.x, o.y + x), Size(s, s)); drawRect(if (i % 2 == 0) texto else acento, Offset(o.x + lado - s, o.y + x), Size(s, s)); x += s; i++ }
            }
            "estrellas" -> {
                drawCircle(acento, r + 3.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                rotate(giro, c) { for (i in 0 until 6) { val a = (i * PI / 3).toFloat(); estrella(Offset(c.x + cos(a) * (r + 7.dp.toPx()), c.y + sin(a) * (r + 7.dp.toPx())), 5.dp.toPx(), acento) } }
            }
            "espinas" -> {
                drawCircle(texto, r + 3.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                for (i in 0 until 24) { val a = (i * 2 * PI / 24).toFloat(); val l = if (i % 2 == 0) 8.dp.toPx() else 5.dp.toPx()
                    drawLine(texto, Offset(c.x + cos(a) * (r + 3.dp.toPx()), c.y + sin(a) * (r + 3.dp.toPx())), Offset(c.x + cos(a) * (r + 3.dp.toPx() + l), c.y + sin(a) * (r + 3.dp.toPx() + l)), 2.dp.toPx()) }
            }
            "luna" -> {
                drawCircle(acento.copy(alpha = 0.5f), r + 4.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                val m = Offset(c.x + r * 0.72f, c.y - r * 0.72f); val mr = 10.dp.toPx()
                drawCircle(acento, mr, m); drawCircle(f.fondo, mr * 0.85f, Offset(m.x + mr * 0.45f, m.y - mr * 0.3f))
            }
        }
    }
}

private fun DrawScope.estrella(c: Offset, r: Float, color: Color) {
    val p = Path()
    for (i in 0 until 10) { val a = (i * PI / 5 - PI / 2).toFloat(); val rr = if (i % 2 == 0) r else r * 0.45f
        if (i == 0) p.moveTo(c.x + cos(a) * rr, c.y + sin(a) * rr) else p.lineTo(c.x + cos(a) * rr, c.y + sin(a) * rr) }
    p.close(); drawPath(p, color)
}

/** Una muestra de un fondo para la tienda: su escena con un panel y una foto en chico. */
@Composable
fun MuestraTema(tema: String, modifier: Modifier = Modifier) {
    val e = TemasPerfil.estilo(tema)
    val f = FogonTema.actual
    Box(modifier.aspectRatio(0.8f).clip(forma(16.dp)).tarjetaFogon(forma(16.dp), sombra = false)) {
        if (e != null) FondoTemaPerfil(tema, Modifier.matchParentSize())
        Row(Modifier.align(Alignment.BottomStart).padding(8.dp).clip(forma(10.dp)).then(if (e != null) Modifier.background(e.panel) else Modifier).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(e?.texto ?: f.linea))
            Text(TemasPerfil.nombre(tema), style = MaterialTheme.typography.labelMedium, color = e?.texto ?: f.texto, maxLines = 1)
        }
    }
}

/** Una muestra de un marco para la tienda: una foto vacía con el marco puesto. */
@Composable
fun MuestraMarco(marco: String, modifier: Modifier = Modifier) {
    val f = FogonTema.actual
    Box(modifier.aspectRatio(1.3f).clip(forma(16.dp)).tarjetaFogon(forma(16.dp), sombra = false), contentAlignment = Alignment.Center) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(f.superficie))
        MarcoFoto(marco, 56.dp)
    }
}

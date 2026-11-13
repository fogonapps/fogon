package io.github.fogonapps.perfil

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fogonapps.identidad.*

/*
 * Los temas del perfil, como los fondos de perfil de Steam: cambian la cabecera (una trama dibujada con las tintas
 * del tema de la app, sin colores fijos ni degradados) y el aro de la foto. Se reclaman en la tienda (por ahora gratis)
 * y los ven todos los que entran al perfil. El catálogo lo valida el servidor (`TemasPerfil.GRATIS`).
 */
object TemasPerfil {
    val TODOS = listOf("neon", "papel", "atardecer", "arcade", "constelacion", "riso", "selva", "medianoche")

    @Composable
    fun nombre(tema: String): String = stringResource(when (tema) {
        "neon" -> R.string.tema_neon; "papel" -> R.string.tema_papel; "atardecer" -> R.string.tema_atardecer
        "arcade" -> R.string.tema_arcade; "constelacion" -> R.string.tema_constelacion; "riso" -> R.string.tema_riso
        "selva" -> R.string.tema_selva; "medianoche" -> R.string.tema_medianoche; else -> R.string.tema_clasico
    })

    @Composable
    fun descripcion(tema: String): String = stringResource(when (tema) {
        "neon" -> R.string.tema_neon_d; "papel" -> R.string.tema_papel_d; "atardecer" -> R.string.tema_atardecer_d
        "arcade" -> R.string.tema_arcade_d; "constelacion" -> R.string.tema_constelacion_d; "riso" -> R.string.tema_riso_d
        "selva" -> R.string.tema_selva_d; "medianoche" -> R.string.tema_medianoche_d; else -> R.string.tema_clasico_d
    })

    /** El color del aro de la foto con ese tema (null: el de siempre). */
    @Composable
    fun aro(tema: String): Color? {
        val f = FogonTema.actual
        return when (tema) {
            "neon", "arcade", "medianoche" -> f.tinta1
            "atardecer", "riso", "selva", "constelacion" -> f.tinta2
            "papel" -> f.texto
            else -> null
        }
    }
}

/** La trama del tema detrás de la cabecera del perfil. */
@Composable
fun Modifier.fondoTemaPerfil(tema: String): Modifier {
    if (tema !in TemasPerfil.TODOS) return this
    val f = FogonTema.actual
    val t1 = f.tinta1; val t2 = f.tinta2; val tx = f.texto; val ln = f.linea; val sup = f.superficie
    return drawBehind { dibujarTema(tema, t1, t2, tx, ln, sup) }
}

private fun DrawScope.dibujarTema(tema: String, t1: Color, t2: Color, tx: Color, ln: Color, sup: Color) {
    val w = size.width; val h = size.height
    when (tema) {
        "neon" -> {
            drawRect(t1.copy(alpha = 0.07f))
            val paso = 26.dp.toPx()
            var x = 0f; while (x < w) { drawLine(t1.copy(alpha = 0.22f), Offset(x, 0f), Offset(x, h), 1.dp.toPx()); x += paso }
            var y = 0f; while (y < h) { drawLine(t1.copy(alpha = 0.22f), Offset(0f, y), Offset(w, y), 1.dp.toPx()); y += paso }
            drawRect(t1.copy(alpha = 0.6f), style = Stroke(2.dp.toPx()))
        }
        "papel" -> {
            drawRect(sup)
            val paso = 22.dp.toPx()
            var y = paso; while (y < h) { drawLine(ln, Offset(0f, y), Offset(w, y), 1.dp.toPx()); y += paso }
            drawLine(t2.copy(alpha = 0.5f), Offset(36.dp.toPx(), 0f), Offset(36.dp.toPx(), h), 1.5.dp.toPx())
        }
        "atardecer" -> {
            // Franjas de riso (sin degradado): cada una un poco más tenue.
            val n = 6; val alto = h / n
            for (i in 0 until n) drawRect(t2.copy(alpha = 0.32f - i * 0.05f), Offset(0f, i * alto), Size(w, alto))
            drawCircle(t1.copy(alpha = 0.35f), h * 0.28f, Offset(w * 0.78f, h * 0.42f))
        }
        "arcade" -> {
            val lado = 14.dp.toPx()
            var y = 0; var py = 0f
            while (py < h) {
                var x = 0; var px = 0f
                while (px < w) { if ((x * 7 + y * 3) % 5 == 0) drawRect((if ((x + y) % 2 == 0) t1 else t2).copy(alpha = 0.3f), Offset(px, py), Size(lado, lado)); px += lado; x++ }
                py += lado; y++
            }
        }
        "constelacion" -> {
            drawRect(tx.copy(alpha = 0.04f))
            val al = java.util.Random(7)
            val puntos = List(40) { Offset(al.nextFloat() * w, al.nextFloat() * h) }
            puntos.forEachIndexed { i, p -> drawCircle(tx.copy(alpha = 0.5f), (1 + al.nextInt(3)).dp.toPx() / 1.5f, p); if (i % 5 == 0 && i + 1 < puntos.size) drawLine(t2.copy(alpha = 0.35f), p, puntos[i + 1], 1.dp.toPx()) }
        }
        "riso" -> {
            val paso = 16.dp.toPx()
            var y = 0f; var fila = 0
            while (y < h + paso) {
                var x = if (fila % 2 == 0) 0f else paso / 2
                while (x < w + paso) { drawCircle(t2.copy(alpha = 0.28f), paso * 0.32f, Offset(x, y)); drawCircle(t1.copy(alpha = 0.22f), paso * 0.2f, Offset(x + 3.dp.toPx(), y + 2.dp.toPx())); x += paso }
                y += paso; fila++
            }
        }
        "selva" -> {
            val al = java.util.Random(3)
            repeat(26) {
                val c = Offset(al.nextFloat() * w, al.nextFloat() * h); val l = (18 + al.nextInt(26)).dp.toPx()
                val hoja = Path().apply { moveTo(c.x, c.y); quadraticTo(c.x + l * 0.6f, c.y - l * 0.5f, c.x + l, c.y); quadraticTo(c.x + l * 0.4f, c.y + l * 0.5f, c.x, c.y); close() }
                drawPath(hoja, (if (it % 3 == 0) t1 else t2).copy(alpha = 0.22f))
            }
        }
        "medianoche" -> {
            drawRect(tx.copy(alpha = 0.06f))
            drawCircle(t1.copy(alpha = 0.45f), h * 0.22f, Offset(w * 0.82f, h * 0.3f))
            drawCircle(sup, h * 0.2f, Offset(w * 0.86f, h * 0.26f))
            val al = java.util.Random(11)
            repeat(30) { drawCircle(tx.copy(alpha = 0.45f), 1.2.dp.toPx(), Offset(al.nextFloat() * w, al.nextFloat() * h)) }
        }
    }
}

/** Una muestra del tema (para la tienda): la cabecera en chico, con la foto y dos rayas de nombre. */
@Composable
fun MuestraTema(tema: String, modifier: Modifier = Modifier) {
    val f = FogonTema.actual
    Box(modifier.aspectRatio(1.6f).clip(forma(16.dp)).tarjetaFogon(forma(16.dp), sombra = false).fondoTemaPerfil(tema)) {
        Row(Modifier.align(Alignment.BottomStart).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(34.dp).clip(CircleShape).tarjetaFogon(CircleShape, sombra = false).border(2.dp, TemasPerfil.aro(tema) ?: f.linea, CircleShape))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.width(54.dp).height(7.dp).clip(FormaPildora).tarjetaFogon(FormaPildora, sombra = false))
                Box(Modifier.width(34.dp).height(5.dp).clip(FormaPildora).tarjetaFogon(FormaPildora, sombra = false))
            }
        }
        Text(TemasPerfil.nombre(tema), style = MaterialTheme.typography.labelLarge, color = f.texto, modifier = Modifier.align(Alignment.TopStart).padding(10.dp))
    }
}

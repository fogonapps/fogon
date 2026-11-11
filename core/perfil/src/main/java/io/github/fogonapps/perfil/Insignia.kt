package io.github.fogonapps.perfil

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * La insignia junto al nombre (X-08), igual en las cuatro apps. Solo la da el servidor: "fogon" es una cuenta
 * oficial de Fogón y "verificada" una cuenta que cumplió la regla o que el equipo verificó. Es neutra: el sello
 * va con el color del texto (negro o blanco según el tema) y la palomita con el del fondo, para que vaya con todo.
 */

/** Si [insignia] es una que se muestra. */
fun insigniaVisible(insignia: String) = insignia == "fogon" || insignia == "verificada"

@Composable
fun InsigniaCuenta(insignia: String, modifier: Modifier = Modifier, tamano: Dp = 16.dp) {
    if (!insigniaVisible(insignia)) return
    val oficial = insignia == "fogon"
    val descripcion = stringResource(if (oficial) R.string.insignia_fogon else R.string.insignia_verificada)
    val f = io.github.fogonapps.identidad.FogonTema.actual
    val sello = f.texto
    val marca = f.fondo
    Canvas(modifier.size(tamano).semantics { contentDescription = descripcion }) {
        val r = size.minDimension / 2
        val c = center
        // Un sello de 12 puntas suaves.
        val forma = Path()
        val puntas = 12
        for (i in 0 until puntas * 2) {
            val a = PI * i / puntas - PI / 2
            val radio = if (i % 2 == 0) r else r * 0.84f
            val p = Offset(c.x + (radio * cos(a)).toFloat(), c.y + (radio * sin(a)).toFloat())
            if (i == 0) forma.moveTo(p.x, p.y) else forma.lineTo(p.x, p.y)
        }
        forma.close()
        drawPath(forma, sello)
        val visto = Path().apply {
            moveTo(c.x - r * 0.38f, c.y + r * 0.02f)
            lineTo(c.x - r * 0.1f, c.y + r * 0.3f)
            lineTo(c.x + r * 0.4f, c.y - r * 0.28f)
        }
        drawPath(visto, marca, style = Stroke(width = r * 0.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

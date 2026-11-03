package io.github.fogonapps.identidad

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Los íconos de Fogón son de línea redonda, todos igual (ver docs/IDENTIDAD.md → Vidrio y puntos → Íconos):
 * el contorno de cada parte, de punta redonda, y nada relleno, salvo que se pida un [relleno] suave. Suelen
 * ir dentro de una ficha de vidrio redonda. Cada ícono es un [Dibujo] de 24×24 hecho de partes: rellenos
 * (se dibuja su contorno), trazos (líneas), huecos (contorno) y trazos de hueco (detalles).
 */

/** Una parte de un dibujo. [ancho] es el grosor del trazo, en unidades del dibujo (de 24). */
@Immutable
data class Parte(val d: String, val tipo: Tipo, val ancho: Float = 0f) {
    enum class Tipo { RELLENO, TRAZO, HUECO, HUECO_TRAZO }
}

/** Un ícono de 24×24. Los caminos se leen una sola vez. */
@Immutable
class Dibujo(val nombre: String, val partes: List<Parte>) {
    internal val caminos: List<Path> by lazy { partes.map { PathParser().parsePathString(it.d).toPath() } }
    private val conHuecos = partes.any { it.tipo == Parte.Tipo.HUECO || it.tipo == Parte.Tipo.HUECO_TRAZO }

    /** El cuerpo (ya escalado a 24): rellenos y trazos en [color], con los huecos recortados en una capa propia. */
    internal fun DrawScope.pintarCuerpo(color: Color, lado: Float) {
        if (conHuecos) drawIntoCanvas { it.saveLayer(Rect(-4f, -4f, lado + 4f, lado + 4f), Paint()) }
        partes.forEachIndexed { i, p ->
            val c = caminos[i]
            when (p.tipo) {
                Parte.Tipo.RELLENO -> drawPath(c, color, style = Fill)
                Parte.Tipo.TRAZO -> drawPath(c, color, style = Stroke(p.ancho, cap = StrokeCap.Round, join = StrokeJoin.Round))
                Parte.Tipo.HUECO -> drawPath(c, Color.Black, style = Fill, blendMode = BlendMode.Clear)
                Parte.Tipo.HUECO_TRAZO -> Unit
            }
        }
        if (conHuecos) drawIntoCanvas { it.restore() }
    }

    /** El contorno de tinta: rellenos y huecos por el borde, trazos y detalles como líneas redondas. */
    internal fun DrawScope.pintarContorno(color: Color) {
        partes.forEachIndexed { i, p ->
            val ancho = when (p.tipo) {
                Parte.Tipo.RELLENO, Parte.Tipo.HUECO, Parte.Tipo.HUECO_TRAZO -> CONTORNO
                Parte.Tipo.TRAZO -> p.ancho.coerceAtMost(TRAZO_MAX)
            }
            drawPath(caminos[i], color, style = Stroke(ancho, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    private companion object {
        /** Grosor del contorno, en unidades del dibujo (de 24). */
        const val CONTORNO = 1.9f
        /** Las líneas sueltas (flechas, cruces) no pasan de este grosor, para que pesen como los contornos. */
        const val TRAZO_MAX = 2.2f
    }
}

fun relleno(d: String) = Parte(d, Parte.Tipo.RELLENO)
fun trazo(d: String, ancho: Float = 3f) = Parte(d, Parte.Tipo.TRAZO, ancho)
fun hueco(d: String) = Parte(d, Parte.Tipo.HUECO)
fun huecoTrazo(d: String, ancho: Float = 1.8f) = Parte(d, Parte.Tipo.HUECO_TRAZO, ancho)
fun dibujo(nombre: String, vararg partes: Parte) = Dibujo(nombre, partes.toList())

/**
 * Un ícono de línea en [tinta]. Con [relleno], el cuerpo va detrás en ese color (suave, para fichas de
 * categoría). [corrida] y [desfase] quedan por compatibilidad: ya no hay tinta corrida.
 * Si lleva [descripcion], los lectores de pantalla la dicen.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun IconoRiso(
    dibujo: Dibujo,
    descripcion: String?,
    modifier: Modifier = Modifier,
    tamano: Dp = 24.dp,
    tinta: Color = FogonTema.actual.texto,
    corrida: Color? = null,
    desfase: Dp = 0.dp,
    relleno: Color? = null,
) {
    val sem = if (descripcion != null) Modifier.semantics { contentDescription = descripcion; role = Role.Image } else Modifier
    Canvas(modifier.size(tamano).then(sem)) {
        val escala = size.minDimension / 24f
        with(dibujo) {
            if (relleno != null) scale(escala, escala, Offset.Zero) { pintarCuerpo(relleno, 24f) }
            scale(escala, escala, Offset.Zero) { pintarContorno(tinta) }
        }
    }
}

/** Los íconos que comparten todas las apps. Cada app suma los suyos en su `Iconos`. */
object IconosFogon {
    val Inicio = dibujo("inicio", relleno("M4 11.2 12 4.6l8 6.6V19.6H4z"), hueco("M10 19.8v-4.6a2 2 0 0 1 4 0v4.6z"))
    val Perfil = dibujo("perfil", relleno("M12 3.8a4 4 0 1 1 0 8 4 4 0 0 1 0-8z"), relleno("M4.6 20.4c.8-4.2 3.6-6.6 7.4-6.6s6.6 2.4 7.4 6.6z"))
    val Racha = dibujo(
        "racha",
        relleno("M12.4 2.8c.6 3.6 5.6 5.4 5.6 10.6a6 6 0 0 1-12 0c0-2.6 1.4-4.2 2.6-5.2-.1 2.2.9 3.6 2.2 3.8-1.2-3.4-.2-6.6 1.6-9.2z"),
        hueco("M12 13.6c1.3 1.2 2.4 2.2 2.4 3.6a2.4 2.4 0 0 1-4.8 0c0-1.4 1.1-2.4 2.4-3.6z"),
    )
    val Tinta = dibujo("tinta", relleno("M12 3c3.4 4.8 6.4 8 6.4 11.6a6.4 6.4 0 0 1-12.8 0C5.6 11 8.6 7.8 12 3z"), huecoTrazo("M9.6 14.4a2.6 2.6 0 0 0 2.4 2.8"))
    val Moneda = dibujo(
        "moneda", relleno("M12 3.6a8.4 8.4 0 1 1 0 16.8 8.4 8.4 0 0 1 0-16.8z"),
        huecoTrazo("M14.2 9.2c-.6-.8-1.4-1.1-2.3-1.1-1.4 0-2.3.8-2.3 1.8 0 2.6 4.8 1.4 4.8 4.1 0 1.1-1 1.9-2.5 1.9-1 0-1.9-.4-2.5-1.2M12 6.6v1.5M12 15.9v1.5"),
    )
    val Mas = dibujo("mas", trazo("M12 5v14M5 12h14", 3.6f))
    val Chulo = dibujo("chulo", trazo("M5 12.6l4.4 4.4L19 7.4", 3.4f))
    val Cerrar = dibujo("cerrar", trazo("M6.5 6.5l11 11M17.5 6.5l-11 11", 3.4f))
    val Atras = dibujo("atras", trazo("M14.8 5.4 8.2 12l6.6 6.6", 3.4f))
    val Siguiente = dibujo("siguiente", trazo("M9.2 5.4 15.8 12l-6.6 6.6", 3.4f))
    val Abajo = dibujo("abajo", trazo("M5.4 9.2 12 15.8l6.6-6.6", 3.4f))
    val Reloj = dibujo("reloj", trazo("M12 4.6a7.8 7.8 0 1 1 0 15.6 7.8 7.8 0 0 1 0-15.6z", 2.6f), trazo("M12 8.6v4l2.8 1.8", 2.6f), trazo("M9.6 2.6h4.8", 2.6f))
    val Candado = dibujo("candado", relleno("M5.5 10.5h13v10h-13z"), trazo("M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5", 2.8f), hueco("M12 13.6a1.5 1.5 0 0 1 .8 2.8v1.6h-1.6v-1.6a1.5 1.5 0 0 1 .8-2.8z"))
    val Campana = dibujo("campana", relleno("M6 16.2V11a6 6 0 0 1 12 0v5.2l1.6 2H4.4z"), relleno("M9.8 19.6h4.4a2.2 2.2 0 0 1-4.4 0z"))
    val Lapiz = dibujo("lapiz", relleno("M4 20l.9-4.6L15.4 4.9a1.9 1.9 0 0 1 2.7 0l1 1a1.9 1.9 0 0 1 0 2.7L8.6 19.1z"), huecoTrazo("M13.6 6.8l3.6 3.6", 1.6f))
    val Borrar = dibujo("borrar", relleno("M9 5.5h10.5a1.5 1.5 0 0 1 1.5 1.5v10a1.5 1.5 0 0 1-1.5 1.5H9L3 12z"), huecoTrazo("M12 9.5l5 5M17 9.5l-5 5", 2f))
    val Aviso = dibujo("aviso", relleno("M12 3.2 21.4 20H2.6z"), huecoTrazo("M12 10v4.4", 2.2f), hueco("M12 16.4a1.2 1.2 0 1 1 0 2.4 1.2 1.2 0 0 1 0-2.4z"))
    val Repetir = dibujo("repetir", trazo("M4.5 12a7.5 7.5 0 0 1 13.2-4.9M19.5 12a7.5 7.5 0 0 1-13.2 4.9", 2.8f), relleno("M19 3.2v5.4h-5.4z"), relleno("M5 20.8v-5.4h5.4z"))
    val Chispa = dibujo("chispa", relleno("M12 1.5c.8 6.2 3.9 9.6 10.5 10.5-6.6.9-9.7 4.3-10.5 10.5-.8-6.2-3.9-9.6-10.5-10.5C8.1 11.1 11.2 7.7 12 1.5z"))
}

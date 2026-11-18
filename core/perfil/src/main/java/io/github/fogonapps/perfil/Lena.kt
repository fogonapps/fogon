package io.github.fogonapps.perfil

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import io.github.fogonapps.identidad.*

internal enum class FormaLena {
    CHISPA, TRONCO, FOGATA, FOGON,
}

internal enum class NivelLena(val codigo: String, val nombre: Int, val forma: FormaLena) {
    CHISPA("chispa", R.string.lena_chispa, FormaLena.CHISPA),
    BRASA("brasa", R.string.lena_brasa, FormaLena.TRONCO),
    FOGATA("fogata", R.string.lena_fogata, FormaLena.FOGATA),
    FOGON("fogon", R.string.lena_fogon, FormaLena.FOGON),
}

internal fun nivelLena(codigo: String) = NivelLena.entries.firstOrNull { it.codigo == codigo }

fun debePreguntarLena(codigo: String, preguntada: Boolean) = nivelLena(codigo) != null && !preguntada

@Composable
internal fun InsigniaLena(nivel: NivelLena, abierta: Boolean, alternar: () -> Unit) {
    val tinta = FogonTema.actual.texto
    val nombre = stringResource(nivel.nombre)
    val descripcion = stringResource(R.string.lena_descripcion, nombre)
    Canvas(Modifier.size(18.dp).clickable(role = Role.Button, onClick = alternar)
        .semantics {
            contentDescription = descripcion
            selected = abierta
        }) {
        val escala = size.width / 24f
        if (nivel.forma == FormaLena.TRONCO) {
            val leno = Path().apply {
                moveTo(17f * escala, 10f * escala)
                lineTo(5f * escala, 10f * escala)
                cubicTo(0f, 10f * escala, 0f, 21f * escala, 5f * escala, 21f * escala)
                lineTo(17f * escala, 21f * escala)
                addOval(Rect(12f * escala, 10f * escala, 22f * escala, 21f * escala))
                addOval(Rect(15.6f * escala, 13.6f * escala, 18.4f * escala, 17.4f * escala))
            }
            drawPath(leno, tinta, style = Stroke(1.7f * escala, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        drawPath(siluetaLena(nivel.forma, escala), tinta)
    }
}

@Composable
internal fun TarjetaDetalleLena(nivel: NivelLena) {
    val f = FogonTema.actual
    TarjetaLena(Modifier.padding(vertical = 6.dp).background(f.fondo.copy(alpha = 1f), forma(18.dp))) {
        Text(stringResource(R.string.lena_detalle, stringResource(nivel.nombre)), color = f.texto)
        Text(stringResource(R.string.lena_gracias), color = f.texto)
    }
}

private fun siluetaLena(forma: FormaLena, escala: Float) = Path().apply {
    fun poligono(vararg puntos: Int) {
        moveTo(puntos[0] * escala, puntos[1] * escala)
        for (i in 2 until puntos.size step 2) lineTo(puntos[i] * escala, puntos[i + 1] * escala)
        close()
    }
    when (forma) {
        FormaLena.CHISPA -> poligono(12, 3, 14, 10, 21, 12, 14, 14, 12, 21, 10, 14, 3, 12, 10, 10)
        FormaLena.TRONCO -> poligono(10, 2, 13, 5, 13, 7, 9, 8, 7, 6, 8, 4)
        FormaLena.FOGATA -> {
            poligono(12, 3, 15, 7, 16, 10, 18, 8, 19, 12, 16, 15, 8, 15, 6, 12, 7, 9, 9, 10)
            poligono(3, 18, 5, 15, 21, 20, 20, 23)
            poligono(3, 20, 19, 15, 21, 18, 4, 23)
        }
        FormaLena.FOGON -> {
            poligono(12, 0, 16, 5, 17, 8, 20, 5, 22, 11, 19, 15, 5, 15, 2, 11, 4, 6, 7, 9, 8, 5)
            poligono(3, 17, 21, 17, 22, 19, 2, 19)
            poligono(2, 21, 10, 21, 11, 24, 1, 24)
            poligono(14, 21, 22, 21, 23, 24, 13, 24)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TarjetaPreguntaLena(habilitada: Boolean, elegir: (Boolean) -> Unit) {
    TarjetaLena(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(stringResource(R.string.lena_gracias), color = FogonTema.actual.texto)
        Text(stringResource(R.string.lena_pregunta), color = FogonTema.actual.texto)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BotonRiso(stringResource(R.string.lena_mostrar), { elegir(true) }, habilitado = habilitada)
            BotonSecundario(stringResource(R.string.lena_ocultar), { elegir(false) }, habilitado = habilitada)
        }
    }
}

@Composable
private fun TarjetaLena(modifier: Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    val forma = forma(18.dp)
    Column(modifier.fillMaxWidth().tarjetaFogon(forma).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = contenido)
}

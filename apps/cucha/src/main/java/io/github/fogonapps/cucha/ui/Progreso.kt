package io.github.fogonapps.cucha.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.cucha.CuchaViewModel
import io.github.fogonapps.cucha.R
import io.github.fogonapps.cucha.datos.Estado
import io.github.fogonapps.cucha.datos.Peso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.NumeroRiso
import io.github.fogonapps.identidad.aparecer
import io.github.fogonapps.identidad.progresoAnimado
import kotlin.math.roundToInt

@Composable
fun Progreso(vm: CuchaViewModel, e: Estado) {
    val f = FogonTema.actual
    val idioma = vm.idioma()
    val hoy = vm.hoy()
    val forzudo by vm.forzudo.collectAsStateWithLifecycle()

    Seccion(stringResource(R.string.pestana_progreso), Modifier.padding(top = 12.dp))

    // La racha y el promedio de la semana.
    val racha = vm.L.racha(e, hoy)
    val prom = vm.L.promedio(e, hoy)
    Row(Modifier.aparecer(0).fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f).vidrio().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            NumeroRiso("$racha", 40.sp)
            Text(pluralStringResource(R.plurals.racha_dias, racha), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        }
        Column(Modifier.weight(1f).vidrio().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            NumeroRiso(prom?.kcal?.roundToInt()?.toString() ?: "—", 40.sp)
            Text(stringResource(R.string.promedio_kcal), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        }
    }
    prom?.let { p ->
        Row(Modifier.aparecer(1).fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).vidrio().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BarraMacro(stringResource(R.string.proteina), p.proteina, e.metas.proteina, f.tinta1, Modifier.weight(1f))
            BarraMacro(stringResource(R.string.carbos), p.carbos, e.metas.carbos, f.tinta2, Modifier.weight(1f))
            BarraMacro(stringResource(R.string.grasa), p.grasa, e.metas.grasa, f.textoSuave, Modifier.weight(1f))
        }
        Pista(stringResource(R.string.promedio_pista))
    }

    // Las calorías de los últimos 14 días contra la meta.
    Seccion(stringResource(R.string.ultimos_14))
    val dias = (13 downTo 0).map { hoy.minusDays(it.toLong()) }
    val kcal = dias.map { vm.L.totalDia(e.dias[it.toString()]).kcal }
    Column(Modifier.aparecer(2).fillMaxWidth().padding(horizontal = 16.dp).vidrio().padding(16.dp)) {
        Barras(kcal, e.metas.kcal.toDouble(), stringResource(R.string.ultimos_14_desc, e.metas.kcal))
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(nombreDia(dias.first(), hoy, idioma), style = MaterialTheme.typography.labelSmall, color = f.textoSuave, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.hoy), style = MaterialTheme.typography.labelSmall, color = f.textoSuave)
        }
    }
    Pista(stringResource(R.string.ultimos_14_pista))

    // El peso: de El Forzudo, o anotado aquí.
    Seccion(stringResource(R.string.peso))
    val pesos = forzudo?.pesos?.takeIf { it.isNotEmpty() } ?: e.pesos.sortedBy { it.fecha }
    if (pesos.size >= 2) {
        Column(Modifier.aparecer(3).fillMaxWidth().padding(horizontal = 16.dp).vidrio().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val ultimo = pesos.last()
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumeroRiso(cifra(ultimo.kg, idioma), 36.sp)
                Text("kg", style = MaterialTheme.typography.titleMedium, color = f.textoSuave, modifier = Modifier.padding(bottom = 4.dp))
            }
            cambio(pesos, hoy.minusDays(28).toString())?.let { d ->
                Text(stringResource(R.string.peso_cambio, (if (d > 0) "+" else "") + cifra(d, idioma)), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
            }
            Linea(pesos.takeLast(30).map { it.kg }, stringResource(R.string.peso))
        }
    } else Pista(stringResource(R.string.peso_pocos))
    if (forzudo != null) Pista(stringResource(R.string.peso_de_forzudo))
    else {
        var kg by rememberSaveable { mutableStateOf("") }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CampoNumero(kg, { kg = it }, stringResource(R.string.peso_hoy), Modifier.weight(1f), decimales = true)
            BotonSecundario(stringResource(R.string.anotar), { numero(kg)?.takeIf { it in 25.0..350.0 }?.let { vm.anotarPeso(it); kg = "" } })
        }
        Pista(stringResource(R.string.peso_sin_forzudo))
    }
}

/** Cuánto cambió el peso desde [desde] (el primer peso de esa fecha en adelante), o null si no hay con qué comparar. */
private fun cambio(pesos: List<Peso>, desde: String): Double? {
    val antes = pesos.firstOrNull { it.fecha >= desde } ?: return null
    val ahora = pesos.last()
    return if (antes == ahora) null else ahora.kg - antes.kg
}

/** Barras que crecen desde abajo; la línea punteada es la meta y lo que la pasa va en la segunda tinta. */
@Composable
private fun Barras(valores: List<Double>, meta: Double, descripcion: String) {
    val f = FogonTema.actual
    val p = progresoAnimado(1f)
    val tope = maxOf(valores.maxOrNull() ?: 0.0, meta) * 1.1
    Canvas(Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = descripcion }) {
        val hueco = size.width / valores.size
        val ancho = hueco * 0.6f
        valores.forEachIndexed { i, v ->
            val alto = (v / tope).toFloat() * size.height * p
            if (v > 0) drawRoundRect(
                if (v > meta * 1.05) f.tinta2 else f.tinta1,
                Offset(i * hueco + (hueco - ancho) / 2, size.height - alto), Size(ancho, alto), CornerRadius(ancho / 2),
            )
        }
        val y = size.height - (meta / tope).toFloat() * size.height
        drawLine(f.textoSuave, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
    }
}

/** Una línea que se dibuja de izquierda a derecha. */
@Composable
private fun Linea(valores: List<Double>, descripcion: String) {
    val f = FogonTema.actual
    val p = progresoAnimado(1f)
    val min = valores.min(); val max = valores.max(); val rango = (max - min).takeIf { it > 0.1 } ?: 1.0
    Canvas(Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = descripcion }) {
        val paso = size.width / (valores.size - 1).coerceAtLeast(1)
        fun punto(i: Int) = Offset(i * paso, size.height - ((valores[i] - min) / rango).toFloat() * size.height * 0.85f - size.height * 0.075f)
        val camino = Path().apply { moveTo(punto(0).x, punto(0).y); for (i in 1 until valores.size) lineTo(punto(i).x, punto(i).y) }
        clipRect(right = size.width * p) {
            drawPath(camino, f.tinta1, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
        if (p >= 1f) drawCircle(f.tinta1, 5.dp.toPx(), punto(valores.lastIndex))
    }
}

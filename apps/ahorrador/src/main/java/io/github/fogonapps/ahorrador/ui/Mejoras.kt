package io.github.fogonapps.ahorrador.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fogonapps.ahorrador.AhorradorViewModel
import io.github.fogonapps.ahorrador.R
import io.github.fogonapps.ahorrador.datos.Estado
import io.github.fogonapps.ahorrador.datos.FilaExtracto
import io.github.fogonapps.ahorrador.datos.Formato
import io.github.fogonapps.ahorrador.datos.TipoCuenta
import io.github.fogonapps.ahorrador.datos.mesAnterior
import io.github.fogonapps.ahorrador.datos.montoReto
import io.github.fogonapps.ahorrador.datos.totalReto
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.FormaPildora
import io.github.fogonapps.identidad.Ilustracion
import io.github.fogonapps.identidad.NumeroRiso
import io.github.fogonapps.identidad.aparecer
import io.github.fogonapps.identidad.forma
import io.github.fogonapps.identidad.progresoAnimado
import io.github.fogonapps.identidad.rebote
import java.time.YearMonth

/* =================== Resumen: la dona del mes y los últimos meses =================== */

/** La dona de en qué se fue la plata (cada categoría con su color) y en el centro el total, que se dibuja al aparecer. */
@Composable
fun DonaDelMes(porCategoria: List<Pair<io.github.fogonapps.ahorrador.datos.Categoria, Long>>) {
    val f = FogonTema.actual
    val total = porCategoria.sumOf { it.second }.coerceAtLeast(1)
    val avance = progresoAnimado(1f)
    val descripcion = porCategoria.joinToString(", ") { (c, n) -> "${c.nombre} ${n * 100 / total} %" }
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).aparecer(1), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(220.dp).semantics { contentDescription = descripcion }) {
            val grosor = 26.dp.toPx()
            val caja = Size(size.width - grosor, size.height - grosor)
            val esquina = Offset(grosor / 2, grosor / 2)
            drawArc(f.superficie, 0f, 360f, false, esquina, caja, style = Stroke(grosor))
            var inicio = -90f
            val hueco = if (porCategoria.size > 1) 2.5f else 0f
            porCategoria.forEach { (c, n) ->
                val barrido = 360f * n / total * avance
                if (barrido > hueco) drawArc(colorDe(c.color), inicio + hueco / 2, barrido - hueco, false, esquina, caja, style = Stroke(grosor, cap = StrokeCap.Butt))
                inicio += barrido
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.salio), style = MaterialTheme.typography.labelMedium, color = f.textoSuave)
            NumeroRiso(Formato.pesos(total), if (Formato.pesos(total).length <= 9) 26.sp else 20.sp, color = f.texto)
        }
    }
}

/** Los gastos de los últimos 6 meses en barras, el mes elegido resaltado. */
@Composable
fun UltimosMeses(vm: AhorradorViewModel, e: Estado, mes: String) {
    val f = FogonTema.actual
    val meses = generateSequence(mes) { mesAnterior(it) }.take(6).toList().reversed()
    val montos = meses.map { vm.L.gastos(e, it) }
    val mayor = montos.maxOrNull()?.coerceAtLeast(1) ?: 1
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).aparecer(2).vidrio().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.ultimos_meses), style = MaterialTheme.typography.titleSmall, color = f.texto)
        Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            meses.zip(montos).forEachIndexed { i, (m, n) ->
                val alto = progresoAnimado(n.toFloat() / mayor)
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(alto.coerceAtLeast(0.02f) * 0.8f).clip(forma(8.dp))
                        .background(if (m == mes) f.tinta1 else f.tinta1.copy(alpha = 0.28f)))
                    Text(Formato.mesCorto(m), style = MaterialTheme.typography.labelSmall, color = if (m == mes) f.texto else f.textoSuave, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

/* =================== Pagos fijos: calendario del mes =================== */

/** El mes con un punto en cada día que vence un pago (lleno si ya se pagó). Hoy, con borde. */
@Composable
fun CalendarioPagos(vm: AhorradorViewModel, e: Estado) {
    val f = FogonTema.actual
    val hoy = vm.hoy()
    val ym = YearMonth.from(hoy)
    val mes = ym.toString()
    val activos = e.fijos.filter { it.activo }
    val porDia = activos.groupBy { vm.L.vence(it, mes).dayOfMonth }
    val primero = ym.atDay(1).dayOfWeek.value - 1
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).aparecer(1).vidrio().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(Formato.mes(mes).replaceFirstChar { it.uppercase() } + " " + ym.year, style = MaterialTheme.typography.titleSmall, color = f.texto)
        Row(Modifier.fillMaxWidth()) {
            Formato.diasCortos().forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = f.textoSuave, textAlign = TextAlign.Center) }
        }
        val celdas = (List(primero) { null } + (1..ym.lengthOfMonth()).toList()).chunked(7)
        celdas.forEach { semana ->
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { i ->
                    val d = semana.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (d != null) {
                            val pagos = porDia[d].orEmpty()
                            val pagados = pagos.all { vm.L.pagadoEnMes(e, it, mes) }
                            Column(
                                Modifier.fillMaxWidth().fillMaxHeight().clip(CircleShape)
                                    .then(if (d == hoy.dayOfMonth) Modifier.border(1.5.dp, f.acentoTexto, CircleShape) else Modifier)
                                    .semantics(mergeDescendants = true) { if (pagos.isNotEmpty()) contentDescription = "$d: " + pagos.joinToString { it.nombre } },
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                            ) {
                                Text("$d", style = MaterialTheme.typography.labelMedium, color = if (pagos.isEmpty()) f.textoSuave else f.texto)
                                if (pagos.isNotEmpty()) Box(Modifier.size(6.dp).clip(CircleShape)
                                    .background(if (pagados) f.textoSuave.copy(alpha = 0.5f) else f.tinta1))
                            }
                        }
                    }
                }
            }
        }
    }
}

/* =================== Importar el extracto del banco =================== */

/** Las filas del extracto, cada una con la categoría sugerida (se puede cambiar) y para elegir cuáles anotar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportarExtracto(vm: AhorradorViewModel, e: Estado, filas: List<FilaExtracto>) {
    val f = FogonTema.actual
    val cuentas = e.cuentas.filter { !it.archivada }
    var cuenta by rememberSaveable { mutableStateOf(cuentas.firstOrNull { it.tipo == TipoCuenta.BANCO }?.id ?: cuentas.firstOrNull()?.id.orEmpty()) }
    val elegidas = remember { mutableStateListOf<Boolean>().apply { addAll(filas.map { true }) } }
    val categorias = remember { mutableStateListOf<String>().apply { addAll(filas.map { vm.categoriaSugerida(e, it) }) } }
    var cambiando by remember { mutableStateOf<Int?>(null) }

    Cabecera(stringResource(R.string.extracto_titulo), vm::cerrar)
    Pista(stringResource(R.string.extracto_pista, filas.size))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.extracto_cuenta), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
        Selector(cuentas.map { it.id to it.nombre }, cuenta, { cuenta = it })
    }
    filas.forEachIndexed { i, fila ->
        val cat = e.categorias.firstOrNull { it.id == categorias[i] }
        FilaVidrio({ elegidas[i] = !elegidas[i] }) {
            Checkbox(elegidas[i], { elegidas[i] = it })
            Column(Modifier.weight(1f)) {
                Text(fila.descripcion.ifBlank { "—" }, style = MaterialTheme.typography.titleSmall, color = f.texto, maxLines = 1)
                Text(Formato.fechaCorta(java.time.LocalDate.parse(fila.fecha)) + " · " + (cat?.nombre ?: ""), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1,
                    modifier = Modifier.clickable { cambiando = i })
            }
            Text((if (fila.monto < 0) "−" else "+") + Formato.pesos(kotlin.math.abs(fila.monto)), style = MaterialTheme.typography.titleSmall,
                color = if (fila.monto < 0) f.texto else f.acentoTexto)
        }
    }
    val n = elegidas.count { it }
    BotonRiso(pluralStringResource(R.plurals.extracto_anotar, n, n), {
        vm.importarExtracto(filas.indices.filter { elegidas[it] }.map { filas[it] to categorias[it] }, cuenta)
    }, Modifier.fillMaxWidth().padding(16.dp), habilitado = n > 0 && cuenta.isNotEmpty())
    Espacio(40.dp)

    cambiando?.let { i ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { cambiando = null },
            title = { Text(stringResource(R.string.extracto_categoria)) },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    e.categorias.filter { it.ingreso == (filas[i].monto > 0) }.forEach { c ->
                        Column(Modifier.clip(forma(14.dp)).clickable { categorias[i] = c.id; cambiando = null }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Ficha(colorDe(c.color), Iconos.de(c.icono), 40.dp)
                            Text(c.nombre, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ cambiando = null }) { Text(stringResource(R.string.cancelar)) } },
        )
    }
}

/* =================== Metas: el reto de ahorro por semanas =================== */

/** La tarjeta del reto: si no se ha empezado, una invitación; si va, la semana, lo de esta semana y lo que se lleva. */
@Composable
fun TarjetaReto(vm: AhorradorViewModel, e: Estado, onAbonar: (io.github.fogonapps.ahorrador.datos.Meta, Long) -> Unit) {
    val f = FogonTema.actual
    var eligiendo by remember { mutableStateOf(false) }
    val reto = e.reto
    val meta = reto?.let { r -> e.metas.firstOrNull { it.id == r.meta } }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).aparecer(1).vidrio().padding(start = 18.dp, top = 16.dp, bottom = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (reto == null || meta == null) {
                Text(stringResource(R.string.reto_titulo), style = MaterialTheme.typography.titleMedium, color = f.texto)
                Text(stringResource(R.string.reto_texto), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                Text(stringResource(R.string.reto_empezar), style = MaterialTheme.typography.labelLarge, color = f.sobreTinta1,
                    modifier = Modifier.rebote().clip(FormaPildora).background(f.tinta1).clickable { eligiendo = true }.padding(horizontal = 16.dp, vertical = 8.dp))
            } else {
                val semana = vm.semanaReto(reto)
                val esta = montoReto(reto.base, semana)
                val llevas = vm.L.ahorradoEn(e, meta.id)
                Text(stringResource(R.string.reto_semana, semana, reto.semanas), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                Text(stringResource(R.string.reto_esta_semana, Formato.pesos(esta)), style = MaterialTheme.typography.titleMedium, color = f.texto)
                Barra((llevas.toFloat() / totalReto(reto.base, reto.semanas)).coerceIn(0f, 1f), f.tinta2)
                Text(stringResource(R.string.reto_llevas, Formato.pesos(llevas), Formato.pesos(totalReto(reto.base, semana))), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.abonar), style = MaterialTheme.typography.labelLarge, color = f.sobreTinta1,
                        modifier = Modifier.rebote().clip(FormaPildora).background(f.tinta1).clickable { onAbonar(meta, esta) }.padding(horizontal = 16.dp, vertical = 8.dp))
                    TextButton(vm::dejarReto) { Text(stringResource(R.string.reto_dejar), color = f.textoSuave) }
                }
            }
        }
        Ilustracion(R.drawable.ilustracion_monedas, tamano = 96.dp)
    }

    if (eligiendo) androidx.compose.material3.AlertDialog(
        onDismissRequest = { eligiendo = false },
        title = { Text(stringResource(R.string.reto_titulo)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.reto_elige), style = MaterialTheme.typography.bodyMedium)
                listOf(1000L, 2000L, 5000L, 10000L).forEach { b ->
                    BotonSecundario(stringResource(R.string.reto_opcion, Formato.pesos(b), Formato.pesos(totalReto(b, 52))), { vm.empezarReto(b); eligiendo = false }, Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { TextButton({ eligiendo = false }) { Text(stringResource(R.string.cancelar)) } },
    )
}

/* =================== Perfil: recordatorio diario =================== */

/** Encender el recordatorio de anotar los gastos y elegir la hora. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AjusteRecordatorio(vm: AhorradorViewModel) {
    val f = FogonTema.actual
    val hora by vm.recordatorio.collectAsState()
    Interruptor(stringResource(R.string.recordatorio), stringResource(R.string.recordatorio_pista), hora >= 0) { vm.ponerRecordatorio(if (it) 20 else -1) }
    if (hora >= 0) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(18, 19, 20, 21, 22).forEach { h ->
            val elegida = h == hora
            Text("%d:00".format(h), style = MaterialTheme.typography.labelLarge, color = if (elegida) f.sobreTinta1 else f.texto,
                modifier = Modifier.rebote().clip(FormaPildora).background(if (elegida) f.tinta1 else f.superficie).border(f.borde, f.linea, FormaPildora)
                    .clickable { vm.ponerRecordatorio(h) }.padding(horizontal = 14.dp, vertical = 8.dp))
        }
    }
}

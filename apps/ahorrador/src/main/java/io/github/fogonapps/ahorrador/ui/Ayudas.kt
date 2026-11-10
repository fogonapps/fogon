package io.github.fogonapps.ahorrador.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fogonapps.ahorrador.AhorradorViewModel
import io.github.fogonapps.ahorrador.Pantalla
import io.github.fogonapps.ahorrador.Pestana
import io.github.fogonapps.ahorrador.R
import io.github.fogonapps.ahorrador.datos.*
import io.github.fogonapps.identidad.*
import kotlin.math.roundToInt

/*
 * Pantallas de AL-04: anotar varios con texto, «¿Me alcanza?», las alertas de Inicio y el historial.
 * Todo se calcula en el teléfono (datos/Ayudas.kt); nada sale de él.
 */

/** «Anotar varios»: escribes o pegas «almuerzo 25 mil + uber 12k» y revisas lo leído antes de guardar. */
@Composable
fun AnotarVarios(vm: AhorradorViewModel, e: Estado) {
    val f = FogonTema.actual
    var texto by rememberSaveable { mutableStateOf("") }
    val cuentas = e.cuentas.filter { !it.archivada }
    var cuenta by rememberSaveable { mutableStateOf(cuentas.firstOrNull()?.id.orEmpty()) }
    var fecha by rememberSaveable { mutableStateOf(vm.hoy().toString()) }
    val leidos = remember(texto, e) { leerVarios(texto, e) }
    // Lo que la persona corrige de cada línea (categoría o quitarla), por posición.
    var cambios by remember(texto) { mutableStateOf(mapOf<Int, String?>()) }
    var quitados by remember(texto) { mutableStateOf(setOf<Int>()) }
    var eligiendo by remember { mutableStateOf<Int?>(null) }
    val finales = leidos.mapIndexedNotNull { i, b -> if (i in quitados) null else if (i in cambios) b.copy(categoria = cambios[i]) else b }
    Cabecera(stringResource(R.string.varios_titulo), vm::cerrar)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Pista(stringResource(R.string.varios_pista))
        OutlinedTextField(texto, { texto = it.take(2000) }, Modifier.fillMaxWidth().heightIn(min = 120.dp),
            placeholder = { Text(stringResource(R.string.varios_ejemplo), color = f.textoSuave) }, shape = forma(20.dp))
        leidos.forEachIndexed { i, b ->
            if (i in quitados) return@forEachIndexed
            val categoria = if (i in cambios) cambios[i] else b.categoria
            val cat = e.categorias.firstOrNull { it.id == categoria }
            FilaVidrio({ eligiendo = i }) {
                Ficha(colorDe(cat?.color ?: 0xFFD6CBB9), Iconos.de(cat?.icono ?: "otros"), 40.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(b.nota.ifBlank { stringResource(if (b.tipo == TipoMovimiento.INGRESO) R.string.ingreso else R.string.gasto) }, color = f.texto, maxLines = 1)
                    Text(cat?.nombre ?: stringResource(R.string.varios_sin_categoria), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                }
                Text((if (b.tipo == TipoMovimiento.INGRESO) "+" else "−") + Formato.pesos(b.monto), color = f.texto)
                IconoRiso(Iconos.Cerrar, stringResource(R.string.varios_quitar, b.nota), tamano = 20.dp,
                    modifier = Modifier.padding(start = 8.dp).clickable { quitados = quitados + i })
            }
        }
        if (texto.isNotBlank() && leidos.isEmpty()) Pista(stringResource(R.string.varios_nada))
        if (cuentas.size > 1) {
            Text(stringResource(R.string.varios_cuenta), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
            Selector(cuentas.map { it.id to it.nombre }, cuenta, { cuenta = it })
        }
        BotonFecha(fecha, { fecha = it }, vm.hoy())
        BotonRiso(pluralStringResource(R.plurals.varios_guardar, finales.size, finales.size), {
            vm.guardarVarios(finales, cuenta, fecha)
        }, Modifier.fillMaxWidth(), habilitado = finales.isNotEmpty() && cuenta.isNotBlank())
    }
    eligiendo?.let { i ->
        val tipo = leidos.getOrNull(i)?.tipo ?: TipoMovimiento.GASTO
        androidx.compose.material3.AlertDialog(onDismissRequest = { eligiendo = null },
            title = { Text(stringResource(R.string.varios_elegir_categoria)) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    e.categorias.filter { it.ingreso == (tipo == TipoMovimiento.INGRESO) }.forEach { c ->
                        androidx.compose.material3.TextButton({ cambios = cambios + (i to c.id); eligiendo = null }, Modifier.fillMaxWidth()) { Text(c.nombre) }
                    }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton({ eligiendo = null }) { Text(stringResource(R.string.volver)) } })
    }
    Espacio(40.dp)
}

/** «¿Me alcanza?»: escribes cuánto quieres gastar hoy y la app hace las cuentas, en el teléfono y sin internet. */
@Composable
fun MeAlcanza(vm: AhorradorViewModel, e: Estado, inicial: Long?) {
    val f = FogonTema.actual
    var valor by rememberSaveable { mutableStateOf(inicial?.toString().orEmpty()) }
    var pregunta by rememberSaveable { mutableIntStateOf(0) }
    val monto = valor.toLongOrNull() ?: 0
    val a = remember(e, monto) { meAlcanza(e, vm.L, monto, vm.hoy()) }
    Cabecera(stringResource(R.string.alcanza_titulo), vm::cerrar)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Pista(stringResource(R.string.alcanza_pista))
        CampoPesos(valor, { valor = it; pregunta = 0 }, stringResource(R.string.alcanza_cuanto))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PildoraSocial(stringResource(R.string.alcanza_hoy), acento = pregunta == 1) { pregunta = 1; valor = "" }
            PildoraSocial(stringResource(R.string.alcanza_mes), acento = pregunta == 2) { pregunta = 2; valor = "" }
        }
        when {
            pregunta == 1 -> Column(Modifier.fillMaxWidth().vidrio().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.alcanza_te_queda_hoy), color = f.textoSuave)
                Pesos(maxOf(0, a.quedaHoy), MaterialTheme.typography.headlineMedium.fontSize)
                Dato(stringResource(R.string.alcanza_cupo_dia), Formato.pesos(a.cupoDia))
                Dato(stringResource(R.string.alcanza_gastado_hoy), Formato.pesos(a.gastadoHoy))
                Dato(stringResource(R.string.alcanza_dias), a.diasRestantes.toString())
            }
            pregunta == 2 -> ComoVoy(vm, e)
            monto > 0 -> Column(Modifier.fillMaxWidth().vidrio().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(when (a.veredicto) {
                    Veredicto.SI -> R.string.alcanza_si
                    Veredicto.SE_COME_DIAS -> R.string.alcanza_se_come
                    Veredicto.NO -> R.string.alcanza_no
                }), style = MaterialTheme.typography.titleLarge, color = f.texto)
                Dato(stringResource(R.string.alcanza_tienes), Formato.pesos(a.tienes))
                Dato(stringResource(R.string.alcanza_comprometido), "− " + Formato.pesos(a.comprometido))
                Dato(stringResource(R.string.alcanza_seguro), Formato.pesos(a.seguro))
                Dato(stringResource(R.string.alcanza_cuesta), "− " + Formato.pesos(a.gasto))
                Dato(stringResource(R.string.alcanza_quedarian), Formato.pesos(a.teQuedarian))
                Dato(stringResource(R.string.alcanza_cupo_dia), Formato.pesos(a.cupoDia))
                if (a.diasQueSeCome.isFinite()) Dato(stringResource(R.string.alcanza_se_come_dias), stringResource(R.string.alcanza_n_dias, a.diasQueSeCome))
                Dato(stringResource(R.string.alcanza_ritmo_despues), stringResource(R.string.alcanza_por_dia, Formato.pesos(a.ritmoDespues)))
                Text(stringResource(R.string.alcanza_como), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
        }
    }
    Espacio(40.dp)
}

/** «¿Cómo voy este mes?»: lo que entró, lo que salió, cuánto va del mes y los sobres pasados. */
@Composable
private fun ComoVoy(vm: AhorradorViewModel, e: Estado) {
    val f = FogonTema.actual
    val hoy = vm.hoy()
    val mes = mesDe(hoy)
    val entro = vm.L.ingresos(e, mes)
    val salio = vm.L.gastos(e, mes)
    val delMes = (hoy.dayOfMonth * 100.0 / hoy.lengthOfMonth()).roundToInt()
    val gastadoPct = if (entro > 0) (salio * 100.0 / entro).roundToInt() else null
    val al = alertas(e, vm.L, hoy)
    Column(Modifier.fillMaxWidth().vidrio().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Dato(stringResource(R.string.alcanza_entro), Formato.pesos(entro))
        Dato(stringResource(R.string.alcanza_salio), Formato.pesos(salio))
        Dato(stringResource(R.string.alcanza_va_del_mes), "$delMes %")
        if (gastadoPct != null) Text(stringResource(if (gastadoPct <= delMes) R.string.alcanza_vas_bien else R.string.alcanza_vas_rapido, gastadoPct, delMes),
            color = f.texto)
        if (al.sobresPasados.isNotEmpty()) Text(pluralStringResource(R.plurals.alertas_sobres, al.sobresPasados.size, al.sobresPasados.size), color = f.texto)
    }
}

/** En Inicio: cupo de hoy, días que quedan y las alertas del mes, con la entrada a «¿Me alcanza?». */
@Composable
fun TarjetaAlcanza(vm: AhorradorViewModel, e: Estado, modifier: Modifier = Modifier) {
    val f = FogonTema.actual
    val hoy = vm.hoy()
    val a = remember(e) { meAlcanza(e, vm.L, 0, hoy) }
    val al = remember(e) { alertas(e, vm.L, hoy) }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).vidrio().clickable { vm.abrir(Pantalla.MeAlcanza()) }.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.alcanza_te_queda_hoy), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                Pesos(maxOf(0, a.quedaHoy))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.alcanza_dias), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                Text(a.diasRestantes.toString(), style = MaterialTheme.typography.titleMedium, color = f.texto)
            }
            PildoraSocial(stringResource(R.string.alcanza_titulo), Iconos.Moneda, acento = true) { vm.abrir(Pantalla.MeAlcanza()) }
        }
        if (al.sobresPasados.isNotEmpty()) Text("⚠ " + pluralStringResource(R.plurals.alertas_sobres, al.sobresPasados.size, al.sobresPasados.size),
            color = f.texto, modifier = Modifier.clickable { vm.irA(Pestana.SOBRES) })
        al.metasCerca.forEach { m -> Text(stringResource(R.string.alertas_meta_cerca, m.nombre, (vm.L.progreso(e, m) * 100).roundToInt()), color = f.texto) }
        al.metasCumplidas.take(2).forEach { m -> Text(stringResource(R.string.alertas_meta_cumplida, m.nombre), color = f.acentoTexto) }
    }
}

/** Historial: cómo le fue a cada sobre en los meses terminados, y las metas cumplidas. */
@Composable
fun Historial(vm: AhorradorViewModel, e: Estado) {
    val f = FogonTema.actual
    val meses = remember(e) { historialSobres(e, vm.L, vm.hoy()) }
    val cumplidas = e.metas.filter { vm.L.progreso(e, it) >= 1.0 }
    var pestana by rememberSaveable { mutableIntStateOf(0) }
    Cabecera(stringResource(R.string.historial_titulo), vm::cerrar)
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PildoraSocial(stringResource(R.string.historial_sobres), acento = pestana == 0) { pestana = 0 }
        PildoraSocial(stringResource(R.string.historial_metas), acento = pestana == 1) { pestana = 1 }
    }
    if (pestana == 0) {
        if (meses.isEmpty()) Pista(stringResource(R.string.historial_sin_sobres), Modifier.padding(16.dp))
        meses.forEach { (mes, resultados) ->
            Seccion(Formato.nombreMes(mes) + " " + mes.take(4))
            resultados.forEach { r ->
                val cat = e.categorias.firstOrNull { it.id == r.sobre.clave }
                FilaVidrio(null, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Ficha(colorDe(r.sobre.color), Iconos.de(r.sobre.icono), 36.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(cat?.nombre ?: r.sobre.nombre, color = f.texto)
                        Text(if (r.respetado) stringResource(R.string.historial_respetado, Formato.pesos(r.diferencia))
                            else stringResource(R.string.historial_pasado, Formato.pesos(-r.diferencia)),
                            style = MaterialTheme.typography.bodySmall, color = if (r.respetado) f.acentoTexto else MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    } else {
        if (cumplidas.isEmpty()) Pista(stringResource(R.string.historial_sin_metas), Modifier.padding(16.dp))
        cumplidas.forEach { m ->
            FilaVidrio(null, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Ficha(colorDe(m.color), Iconos.de(m.icono), 36.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(m.nombre, color = f.texto)
                    Text(stringResource(R.string.historial_meta_cumplida, Formato.pesos(vm.L.ahorradoEn(e, m.id)), Formato.pesos(m.objetivo)),
                        style = MaterialTheme.typography.bodySmall, color = f.acentoTexto)
                }
            }
        }
    }
    Espacio(40.dp)
}

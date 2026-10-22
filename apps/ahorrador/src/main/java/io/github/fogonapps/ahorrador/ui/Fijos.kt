package io.github.fogonapps.ahorrador.ui

import io.github.fogonapps.identidad.FormaPildora
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fogonapps.ahorrador.AhorradorViewModel
import io.github.fogonapps.ahorrador.Pantalla
import io.github.fogonapps.ahorrador.R
import io.github.fogonapps.ahorrador.datos.Deuda
import io.github.fogonapps.ahorrador.datos.Estado
import io.github.fogonapps.ahorrador.datos.Fijo
import io.github.fogonapps.ahorrador.datos.Formato
import io.github.fogonapps.ahorrador.datos.Sistema
import io.github.fogonapps.ahorrador.datos.TipoCuenta
import io.github.fogonapps.ahorrador.datos.mesDe
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.Logro
import io.github.fogonapps.identidad.Tintas

/* =================== Pagos fijos =================== */

@Composable
fun Fijos(vm: AhorradorViewModel, e: Estado) {
    val f = FogonTema.actual
    val mes = mesDe(vm.hoy())
    Cabecera(stringResource(R.string.pagos_fijos), vm::cerrar)
    Pista(stringResource(R.string.fijos_pista))
    if (e.fijos.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().padding(16.dp).vidrio().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Dato(stringResource(R.string.al_mes), Formato.pesos(e.fijos.filter { it.activo }.sumOf { it.monto }))
            Dato(stringResource(R.string.faltan_este_mes), Formato.pesos(e.fijos.filter { it.activo && !vm.L.pagadoEnMes(e, it, mes) }.sumOf { it.monto }))
        }
    }
    e.fijos.sortedBy { it.dia }.forEach { fijo ->
        val cat = e.categorias.firstOrNull { it.id == fijo.categoria }
        val pagado = vm.L.pagadoEnMes(e, fijo, mes)
        FilaVidrio({ vm.abrir(Pantalla.EditarFijo(fijo.id)) }) {
            Ficha(cat?.let { colorDe(it.color) } ?: Tintas.GrisClaro, Iconos.de(cat?.icono ?: "otros"), 42.dp)
            Column(Modifier.weight(1f)) {
                Text(fijo.nombre, style = MaterialTheme.typography.titleSmall, color = if (fijo.activo) f.texto else f.textoSuave, maxLines = 1)
                Text(buildString {
                    append(stringResource(R.string.cada_dia, fijo.dia))
                    if (fijo.automatico) append(" · ").append(stringResource(R.string.se_anota_solo))
                    if (!fijo.activo) append(" · ").append(stringResource(R.string.pausado))
                }, style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Pesos(fijo.monto, 16.sp)
                if (pagado) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Iconos.Chulo, null, tint = f.acentoTexto, modifier = Modifier.padding(end = 4.dp).heightIn(max = 14.dp))
                    Text(stringResource(R.string.pagado), style = MaterialTheme.typography.labelSmall, color = f.acentoTexto)
                } else if (fijo.activo) Text(stringResource(R.string.pagar), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = FogonTema.actual.paleta.sobreTinta,
                    modifier = Modifier.padding(top = 4.dp).clip(FormaPildora).background(f.paleta.tinta).clickable { vm.pagarFijo(fijo) }
                        .padding(horizontal = 12.dp, vertical = 4.dp))
            }
        }
    }
    BotonSecundario(stringResource(R.string.agregar_fijo), { vm.abrir(Pantalla.EditarFijo(null)) }, Modifier.fillMaxWidth().padding(16.dp))
}

@Composable
fun EditarFijo(vm: AhorradorViewModel, e: Estado, id: String?) {
    val f = FogonTema.actual
    val confirmar = LocalConfirmar.current
    val fijo = e.fijos.firstOrNull { it.id == id }
    val activas = e.cuentas.filter { !it.archivada }
    var nombre by rememberSaveable { mutableStateOf(fijo?.nombre.orEmpty()) }
    var monto by rememberSaveable { mutableStateOf(fijo?.monto?.toString().orEmpty()) }
    var categoria by rememberSaveable { mutableStateOf(fijo?.categoria ?: "servicios") }
    var cuenta by rememberSaveable { mutableStateOf(fijo?.cuenta ?: (activas.firstOrNull { it.tipo == TipoCuenta.BANCO } ?: activas.first()).id) }
    var dia by rememberSaveable { mutableStateOf((fijo?.dia ?: 5).toString()) }
    var automatico by rememberSaveable { mutableStateOf(fijo?.automatico ?: false) }
    var activo by rememberSaveable { mutableStateOf(fijo?.activo ?: true) }

    Cabecera(stringResource(if (fijo == null) R.string.agregar_fijo else R.string.editar_fijo), vm::cerrar)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CampoTexto(nombre, { nombre = it }, stringResource(R.string.fijo_nombre))
        CampoPesos(monto, { monto = it }, stringResource(R.string.monto))
        CampoNumero(dia, { dia = it.take(2) }, stringResource(R.string.dia_del_mes), Modifier.fillMaxWidth())
        Text(stringResource(R.string.categoria), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
        Selector(e.categorias.filter { !it.ingreso }.map { it.id to it.nombre }, categoria, { categoria = it })
        Text(stringResource(R.string.sale_de), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
        Selector(activas.map { it.id to it.nombre }, cuenta, { cuenta = it })
        Interruptor(stringResource(R.string.anotar_solo), stringResource(R.string.anotar_solo_pista), automatico) { automatico = it }
        if (fijo != null) Interruptor(stringResource(R.string.activo), null, activo) { activo = it }
        BotonRiso(stringResource(R.string.guardar), {
            vm.guardarFijo(Fijo(fijo?.id.orEmpty(), nombre.trim(), monto.toLongOrNull() ?: 0, categoria, cuenta,
                (dia.toIntOrNull() ?: 1).coerceIn(1, 31), automatico, activo))
            vm.cerrar()
        }, Modifier.fillMaxWidth(), habilitado = nombre.isNotBlank() && (monto.toLongOrNull() ?: 0) > 0)
        if (fijo != null) BotonSecundario(stringResource(R.string.borrar_fijo), {
            confirmar(vm.tx(R.string.borrar_fijo_pregunta, fijo.nombre), vm.tx(R.string.borrar_fijo_texto)) { vm.borrarFijo(fijo.id) }
        }, Modifier.fillMaxWidth(), peligro = true)
    }
    Espacio(40.dp)
}

/** Fila con un interruptor y su explicación. */
@Composable
fun Interruptor(titulo: String, pista: String?, valor: Boolean, onCambio: (Boolean) -> Unit) {
    val f = FogonTema.actual
    Row(Modifier.fillMaxWidth().vidrio().clickable { onCambio(!valor) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleSmall, color = f.texto)
            pista?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = f.textoSuave) }
        }
        Switch(valor, onCambio)
    }
}

/* =================== Me deben y debo =================== */

@Composable
fun Deudas(vm: AhorradorViewModel, e: Estado) {
    val f = FogonTema.actual
    val L = vm.L
    var abonando by remember { mutableStateOf<Deuda?>(null) }
    Cabecera(stringResource(R.string.deudas_titulo), vm::cerrar)
    Row(Modifier.fillMaxWidth().padding(16.dp).vidrio().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Dato(stringResource(R.string.me_deben), Formato.pesos(L.meDeben(e)))
        Dato(stringResource(R.string.debo), Formato.pesos(L.debo(e)))
    }
    listOf(true, false).forEach { meDeben ->
        Seccion(stringResource(if (meDeben) R.string.me_deben else R.string.debo))
        val lista = e.deudas.filter { it.meDeben == meDeben }.sortedBy { L.pendiente(it) == 0L }
        if (lista.isEmpty()) Pista(stringResource(if (meDeben) R.string.nadie_te_debe else R.string.no_debes))
        lista.forEach { d ->
            val pend = L.pendiente(d)
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).vidrio().clickable { vm.abrir(Pantalla.EditarDeuda(d.id, d.meDeben)) }.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Ficha(if (meDeben) Tintas.Verde else Tintas.Naranja, Iconos.Persona, 40.dp)
                    Column(Modifier.weight(1f)) {
                        Nombre(d.nombre)
                        Text(buildString {
                            append(Formato.fechaCorta(d.fecha))
                            L.cuotaDe(d)?.let { append(" · ").append(d.cuotas).append(" × ").append(Formato.pesos(it)) }
                            if (d.nota.isNotBlank()) append(" · ").append(d.nota)
                        }, style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.de_x, Formato.pesos(d.monto)), style = MaterialTheme.typography.labelSmall, color = f.textoSuave)
                        Pesos(pend, 17.sp)
                    }
                }
                if (d.monto > 0) Barra(1f - pend.toFloat() / d.monto, if (meDeben) Tintas.Verde else Tintas.Naranja)
                if (pend == 0L) Logro(stringResource(if (meDeben) R.string.te_pagaron else R.string.pagaste_todo))
                else Text(stringResource(if (meDeben) R.string.me_abonaron else R.string.abone), style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold, color = FogonTema.actual.paleta.sobreTinta,
                    modifier = Modifier.clip(FormaPildora).background(f.paleta.tinta).clickable { abonando = d }.padding(horizontal = 16.dp, vertical = 7.dp))
            }
        }
        BotonSecundario(stringResource(if (meDeben) R.string.nuevo_me_deben else R.string.nuevo_debo), { vm.abrir(Pantalla.EditarDeuda(null, meDeben)) },
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
    }
    // Las tarjetas de crédito también son deuda: se ven aquí y se pagan desde su cuenta.
    val tarjetas = e.cuentas.filter { it.tipo == TipoCuenta.CREDITO && !it.archivada && L.deuda(e, it.id) > 0 }
    if (tarjetas.isNotEmpty()) {
        Seccion(stringResource(R.string.tarjetas))
        tarjetas.forEach { c ->
            FilaVidrio({ vm.abrir(Pantalla.Cuenta(c.id)) }) {
                Ficha(c.tipo.color(), c.tipo.icono(), 40.dp)
                Nombre(c.nombre, modifier = Modifier.weight(1f))
                Pesos(L.deuda(e, c.id), 17.sp)
            }
        }
    }
    Pista(stringResource(R.string.deudas_pista), Modifier.padding(top = 8.dp))
    Espacio(40.dp)

    abonando?.let { d ->
        DialogoPesos(stringResource(if (d.meDeben) R.string.me_abonaron else R.string.abone), L.cuotaDe(d)?.coerceAtMost(L.pendiente(d)),
            { vm.abonarDeuda(d, it) }, { abonando = null }, stringResource(R.string.falta_x, Formato.pesos(L.pendiente(d))))
    }
}

@Composable
fun EditarDeuda(vm: AhorradorViewModel, e: Estado, id: String?, meDebenInicial: Boolean) {
    val confirmar = LocalConfirmar.current
    val d = e.deudas.firstOrNull { it.id == id }
    var meDeben by rememberSaveable { mutableStateOf(d?.meDeben ?: meDebenInicial) }
    var nombre by rememberSaveable { mutableStateOf(d?.nombre.orEmpty()) }
    var monto by rememberSaveable { mutableStateOf(d?.monto?.toString().orEmpty()) }
    var fecha by rememberSaveable { mutableStateOf(d?.fecha ?: vm.hoy().toString()) }
    var cuotas by rememberSaveable { mutableStateOf(d?.cuotas?.toString().orEmpty()) }
    var nota by rememberSaveable { mutableStateOf(d?.nota.orEmpty()) }

    Cabecera(stringResource(if (d == null) (if (meDeben) R.string.nuevo_me_deben else R.string.nuevo_debo) else R.string.editar_deuda), vm::cerrar)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Selector(listOf(true to stringResource(R.string.me_deben), false to stringResource(R.string.debo)), meDeben, { meDeben = it })
        CampoTexto(nombre, { nombre = it }, stringResource(if (meDeben) R.string.quien_te_debe else R.string.a_quien_debes))
        CampoPesos(monto, { monto = it }, stringResource(R.string.cuanto))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.desde), color = FogonTema.actual.textoSuave)
            BotonFecha(fecha, { fecha = it }, vm.hoy())
        }
        CampoNumero(cuotas, { cuotas = it.take(3) }, stringResource(R.string.cuotas_opcional), Modifier.fillMaxWidth())
        CampoTexto(nota, { nota = it }, stringResource(R.string.nota), max = 80)
        d?.abonos?.takeIf { it.isNotEmpty() }?.let { abonos ->
            Text(stringResource(R.string.abonos), style = MaterialTheme.typography.labelLarge, color = FogonTema.actual.textoSuave)
            abonos.forEach { a ->
                Row(Modifier.fillMaxWidth()) {
                    Text(Formato.fechaCorta(a.fecha), color = FogonTema.actual.textoSuave, modifier = Modifier.weight(1f))
                    Pesos(a.monto, 15.sp)
                }
            }
        }
        BotonRiso(stringResource(R.string.guardar), {
            vm.guardarDeuda(Deuda(d?.id.orEmpty(), nombre.trim(), meDeben, monto.toLongOrNull() ?: 0, fecha, nota.trim(),
                cuotas.toIntOrNull()?.takeIf { it > 1 }, d?.abonos.orEmpty()))
            vm.cerrar()
        }, Modifier.fillMaxWidth(), habilitado = nombre.isNotBlank() && (monto.toLongOrNull() ?: 0) > 0)
        if (d != null) BotonSecundario(stringResource(R.string.borrar_deuda), {
            confirmar(vm.tx(R.string.borrar_deuda_pregunta, d.nombre), null) { vm.borrarDeuda(d.id) }
        }, Modifier.fillMaxWidth(), peligro = true)
    }
    Espacio(40.dp)
}

/* =================== Cierre del mes (las preguntas del kakeibo) =================== */

@Composable
fun CierrePantalla(vm: AhorradorViewModel, e: Estado, mes: String) {
    val f = FogonTema.actual
    val c = vm.L.cierre(e, mes)
    var nota by rememberSaveable { mutableStateOf(c.nota) }
    Cabecera(stringResource(R.string.cierre_de, Formato.mes(mes)), vm::cerrar)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Pregunta(stringResource(R.string.cierre_entro), Formato.pesos(c.ingresos))
        c.ahorroPlaneado?.let { Pregunta(stringResource(R.string.cierre_querias), Formato.pesos(it)) }
        Pregunta(stringResource(R.string.cierre_gastaste), Formato.pesos(c.gastos))
        Column(Modifier.fillMaxWidth().destacada().padding(20.dp)) {
            Text(stringResource(R.string.cierre_ahorraste), style = MaterialTheme.typography.labelLarge, color = FogonTema.actual.sobreDestacada.copy(alpha = 0.85f))
            Pesos(c.ahorrado, 38.sp, FogonTema.actual.sobreDestacada)
            c.ahorroPlaneado?.let { plan ->
                Text(if (c.ahorrado >= plan) stringResource(R.string.cierre_lo_lograste) else stringResource(R.string.cierre_faltaron, Formato.pesos(plan - c.ahorrado)),
                    style = MaterialTheme.typography.bodyMedium, color = FogonTema.actual.sobreDestacada)
            }
        }
        vm.L.porCategoria(e, mes).take(3).takeIf { it.isNotEmpty() }?.let { top ->
            Text(stringResource(R.string.cierre_donde), style = MaterialTheme.typography.titleSmall, color = f.texto)
            top.forEach { (cat, n) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Ficha(colorDe(cat.color), Iconos.de(cat.icono), 32.dp)
                    Text(cat.nombre, color = f.texto, modifier = Modifier.weight(1f))
                    Pesos(n, 15.sp)
                }
            }
        }
        Text(stringResource(R.string.cierre_mejorar), style = MaterialTheme.typography.titleSmall, color = f.texto)
        OutlinedTextField(nota, { if (it.length <= 500) nota = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp),
            placeholder = { Text(stringResource(R.string.cierre_mejorar_pista)) })
        BotonRiso(stringResource(R.string.guardar), { vm.notaCierre(mes, nota.trim()); vm.cerrar() }, Modifier.fillMaxWidth())
        if (e.sistema != Sistema.KAKEIBO) Text(stringResource(R.string.cierre_pista), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
    }
    Espacio(40.dp)
}

@Composable
private fun Pregunta(texto: String, valor: String) {
    val f = FogonTema.actual
    Row(Modifier.fillMaxWidth().vidrio().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = f.texto, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Text(valor, fontFamily = io.github.fogonapps.identidad.Anton, fontSize = 18.sp, color = f.texto)
    }
}

package io.github.fogonapps.ahorrador.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fogonapps.ahorrador.AhorradorViewModel
import io.github.fogonapps.ahorrador.Pantalla
import io.github.fogonapps.ahorrador.R
import io.github.fogonapps.ahorrador.datos.Credito
import io.github.fogonapps.ahorrador.datos.Cuenta
import io.github.fogonapps.ahorrador.datos.Estado
import io.github.fogonapps.ahorrador.datos.Formato
import io.github.fogonapps.ahorrador.datos.TipoCuenta
import io.github.fogonapps.ahorrador.datos.TipoMovimiento
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FogonTema
import java.util.Locale

/* =================== Todas las cuentas =================== */

@Composable
fun Cuentas(vm: AhorradorViewModel, e: Estado) {
    val f = FogonTema.actual
    val L = vm.L
    Cabecera(stringResource(R.string.cuentas), vm::cerrar)
    Column(Modifier.fillMaxWidth().padding(16.dp).destacada().padding(22.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.tienes), style = MaterialTheme.typography.labelLarge, color = FogonTema.actual.sobreDestacada.copy(alpha = 0.85f))
        io.github.fogonapps.identidad.NumeroRiso(Formato.pesos(L.patrimonio(e)), 40.sp, color = FogonTema.actual.sobreDestacada)
        val enMetas = e.metas.sumOf { L.ahorradoEn(e, it.id) }
        if (enMetas > 0) Text(stringResource(R.string.mas_en_metas, Formato.pesos(enMetas)), style = MaterialTheme.typography.bodySmall, color = FogonTema.actual.sobreDestacada.copy(alpha = 0.9f))
        Text(stringResource(R.string.tienes_pista), style = MaterialTheme.typography.bodySmall, color = FogonTema.actual.sobreDestacada.copy(alpha = 0.85f))
    }
    e.cuentas.filter { !it.archivada }.forEach { c ->
        FilaVidrio({ vm.abrir(Pantalla.Cuenta(c.id)) }) {
            Ficha(c.tipo.color(), c.tipo.icono(), 44.dp)
            Column(Modifier.weight(1f)) {
                Nombre(c.nombre)
                Text(stringResource(c.tipo.nombre), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
            if (c.tipo == TipoCuenta.CREDITO) Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.debes), style = MaterialTheme.typography.labelSmall, color = f.textoSuave)
                Pesos(L.deuda(e, c.id), 17.sp)
            } else Pesos(L.saldo(e, c.id), 17.sp)
        }
    }
    BotonSecundario(stringResource(R.string.nueva_cuenta), { vm.abrir(Pantalla.EditarCuenta(null)) }, Modifier.fillMaxWidth().padding(16.dp))
}

/* =================== Una cuenta (y su tarjeta de crédito) =================== */

@Composable
fun CuentaPantalla(vm: AhorradorViewModel, e: Estado, id: String) {
    val f = FogonTema.actual
    val L = vm.L
    val c = e.cuentas.firstOrNull { it.id == id } ?: run { vm.cerrar(); return }
    val hoy = vm.hoy()
    Cabecera(c.nombre, vm::cerrar) {
        IconButton({ vm.abrir(Pantalla.EditarCuenta(c.id)) }) { io.github.fogonapps.identidad.IconoRiso(Iconos.Lapiz, stringResource(R.string.editar)) }
    }
    val t = L.tarjeta(e, id, hoy)
    if (c.tipo == TipoCuenta.CREDITO && t != null) {
        Column(Modifier.fillMaxWidth().padding(16.dp).destacada().padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.debes), style = MaterialTheme.typography.labelLarge, color = FogonTema.actual.sobreDestacada.copy(alpha = 0.85f))
            Pesos(t.deuda, 42.sp, FogonTema.actual.sobreDestacada)
            if (t.cupo > 0) {
                Barra(t.usado.toFloat(), FogonTema.actual.sobreDestacada)
                Text(stringResource(R.string.cupo_disponible, Formato.pesos(t.disponible), Formato.pesos(t.cupo)), style = MaterialTheme.typography.bodySmall, color = FogonTema.actual.sobreDestacada.copy(alpha = 0.9f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).vidrio().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Dato(stringResource(R.string.corte), Formato.fechaCorta(t.corte))
            Dato(stringResource(R.string.pago_antes), Formato.fechaCorta(t.pago))
            Dato(stringResource(R.string.minimo), Formato.pesos(t.minimo))
        }
        if (t.deuda > 0) {
            Pista(stringResource(R.string.pago_total_pista, Formato.pesos(t.deuda)), Modifier.padding(top = 8.dp))
            if (t.interesesSiMinimo > 0) Pista(stringResource(R.string.intereses_si_minimo, Formato.pesos(t.interesesSiMinimo)))
            BotonRiso(stringResource(R.string.pagar_tarjeta), { vm.abrir(Pantalla.Anotar(TipoMovimiento.TRASPASO, aCuenta = c.id)) },
                Modifier.fillMaxWidth().padding(16.dp))
        }
        if (t.cuotas.isNotEmpty()) {
            Seccion(stringResource(R.string.compras_a_cuotas))
            t.cuotas.forEach { q ->
                FilaVidrio {
                    Column(Modifier.weight(1f)) {
                        Text(q.movimiento.nota.ifBlank { e.categorias.firstOrNull { it.id == q.movimiento.categoria }?.nombre.orEmpty() },
                            style = MaterialTheme.typography.titleSmall, color = f.texto, maxLines = 1)
                        Text(stringResource(R.string.cuota_de, q.pagadas, q.total, Formato.pesos(q.valor)), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.faltan), style = MaterialTheme.typography.labelSmall, color = f.textoSuave)
                        Pesos(q.restante, 16.sp)
                    }
                }
            }
        }
        Pista(stringResource(R.string.tarjeta_aproximado), Modifier.padding(top = 8.dp))
    } else {
        Column(Modifier.fillMaxWidth().padding(16.dp).destacada().padding(22.dp)) {
            Text(stringResource(R.string.saldo), style = MaterialTheme.typography.labelLarge, color = FogonTema.actual.sobreDestacada.copy(alpha = 0.85f))
            Pesos(L.saldo(e, id), 42.sp, FogonTema.actual.sobreDestacada)
        }
    }
    Seccion(stringResource(R.string.movimientos))
    val lista = recientes(e.movimientos.filter { it.cuenta == id || it.aCuenta == id })
    if (lista.isEmpty()) Pista(stringResource(R.string.cuenta_sin_movimientos))
    lista.take(60).forEach { m -> FilaMovimiento(e, m) { vm.abrir(Pantalla.Anotar(m.tipo, m.id)) } }
    Espacio(40.dp)
}

/* =================== Crear o editar una cuenta =================== */

@Composable
fun EditarCuenta(vm: AhorradorViewModel, e: Estado, id: String?) {
    val f = FogonTema.actual
    val confirmar = LocalConfirmar.current
    val c = e.cuentas.firstOrNull { it.id == id }
    val L = vm.L
    // Se muestra el saldo de hoy (o la deuda, en tarjetas); al guardar se ajusta el saldo inicial para que cuadre.
    val hoyTiene = c?.let { if (it.tipo == TipoCuenta.CREDITO) L.deuda(e, it.id) else L.saldo(e, it.id) }
    var nombre by rememberSaveable { mutableStateOf(c?.nombre.orEmpty()) }
    var tipo by rememberSaveable { mutableStateOf(c?.tipo ?: TipoCuenta.BANCO) }
    var saldo by rememberSaveable { mutableStateOf(hoyTiene?.toString().orEmpty()) }
    var cupo by rememberSaveable { mutableStateOf(c?.credito?.cupo?.takeIf { it > 0 }?.toString().orEmpty()) }
    var corte by rememberSaveable { mutableStateOf((c?.credito?.diaCorte ?: 15).toString()) }
    var pago by rememberSaveable { mutableStateOf((c?.credito?.diaPago ?: 30).toString()) }
    var tasa by rememberSaveable { mutableStateOf(String.format(Locale.ROOT, "%.2f", c?.credito?.tasaMensual ?: 2.0)) }
    var minimo by rememberSaveable { mutableStateOf(String.format(Locale.ROOT, "%.0f", c?.credito?.minimoPct ?: 5.0)) }

    Cabecera(stringResource(if (c == null) R.string.nueva_cuenta else R.string.editar_cuenta), vm::cerrar)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CampoTexto(nombre, { nombre = it }, stringResource(R.string.nombre_cuenta))
        // El tipo de una cuenta con movimientos no se cambia: una tarjeta cuenta la plata al revés.
        if (c == null || e.movimientos.none { it.cuenta == c.id || it.aCuenta == c.id }) {
            Selector(TipoCuenta.entries.map { it to stringResource(it.nombre) }, tipo, { tipo = it })
        }
        val credito = tipo == TipoCuenta.CREDITO
        CampoPesos(saldo, { saldo = it }, stringResource(if (credito) R.string.deuda_hoy else R.string.saldo_hoy))
        if (credito) {
            CampoPesos(cupo, { cupo = it }, stringResource(R.string.cupo))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CampoNumero(corte, { corte = it.take(2) }, stringResource(R.string.dia_corte), Modifier.weight(1f))
                CampoNumero(pago, { pago = it.take(2) }, stringResource(R.string.dia_pago), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CampoNumero(tasa, { tasa = it }, stringResource(R.string.tasa_mensual), Modifier.weight(1f), decimales = true)
                CampoNumero(minimo, { minimo = it }, stringResource(R.string.minimo_pct), Modifier.weight(1f), decimales = true)
            }
            Text(stringResource(R.string.tasa_pista), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        }
        BotonRiso(stringResource(R.string.guardar), {
            val deseado = saldo.toLongOrNull() ?: 0
            val movimientos = c?.let { (L.saldo(e, it.id) - it.saldoInicial) } ?: 0
            val inicial = (if (credito) -deseado else deseado) - movimientos
            fun dia(s: String, d: Int) = (s.toIntOrNull() ?: d).coerceIn(1, 31)
            fun num(s: String, d: Double) = s.replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: d
            vm.guardarCuenta(Cuenta(
                id = c?.id.orEmpty(), nombre = nombre.trim(), tipo = tipo, saldoInicial = inicial,
                credito = if (credito) Credito(cupo.toLongOrNull() ?: 0, dia(corte, 15), dia(pago, 30), num(tasa, 2.0), num(minimo, 5.0)) else null,
            ))
            vm.cerrar()
        }, Modifier.fillMaxWidth(), habilitado = nombre.isNotBlank())
        if (c != null) BotonSecundario(stringResource(R.string.archivar_cuenta), {
            confirmar(vm.tx(R.string.archivar_pregunta, c.nombre), vm.tx(R.string.archivar_texto)) { vm.archivarCuenta(c.id); vm.cerrar() }
        }, Modifier.fillMaxWidth(), peligro = true)
    }
    Espacio(40.dp)
}

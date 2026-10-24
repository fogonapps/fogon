package io.github.fogonapps.ahorrador.ui

import io.github.fogonapps.identidad.FormaPildora
import io.github.fogonapps.identidad.forma
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fogonapps.ahorrador.AhorradorViewModel
import io.github.fogonapps.ahorrador.Pantalla
import io.github.fogonapps.ahorrador.R
import io.github.fogonapps.ahorrador.datos.Estado
import io.github.fogonapps.ahorrador.datos.Formato
import io.github.fogonapps.ahorrador.datos.Movimiento
import io.github.fogonapps.ahorrador.datos.TipoCuenta
import io.github.fogonapps.ahorrador.datos.TipoMovimiento
import io.github.fogonapps.ahorrador.datos.gmf
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.Tintas

val TipoMovimiento.nombre get() = when (this) {
    TipoMovimiento.GASTO -> R.string.gasto
    TipoMovimiento.INGRESO -> R.string.ingreso
    TipoMovimiento.TRASPASO -> R.string.pasar
    TipoMovimiento.AHORRO -> R.string.ahorrar
}

/**
 * Anotar un movimiento: el monto con el teclado de la app (grande, sin abrir el del teléfono),
 * la categoría con un toque y la cuenta. También sirve para corregir o borrar uno ya anotado.
 */
@Composable
fun Anotar(vm: AhorradorViewModel, e: Estado, p: Pantalla.Anotar) {
    val f = FogonTema.actual
    val confirmar = LocalConfirmar.current
    val viejo = p.editar?.let { id -> e.movimientos.firstOrNull { it.id == id } }
    val activas = e.cuentas.filter { !it.archivada }
    val sinCredito = activas.filter { it.tipo != TipoCuenta.CREDITO }

    var tipo by rememberSaveable { mutableStateOf(viejo?.tipo ?: p.tipo) }
    var monto by rememberSaveable { mutableStateOf(viejo?.monto?.toString().orEmpty()) }
    var categoria by rememberSaveable { mutableStateOf(viejo?.categoria) }
    var cuenta by rememberSaveable {
        mutableStateOf(viejo?.cuenta ?: when (p.tipo) {
            TipoMovimiento.GASTO -> vm.ultimoGasto()?.cuenta?.takeIf { id -> activas.any { it.id == id } } ?: activas.first().id
            else -> (sinCredito.firstOrNull() ?: activas.first()).id
        })
    }
    var aCuenta by rememberSaveable { mutableStateOf(viejo?.aCuenta ?: p.aCuenta ?: activas.firstOrNull { it.id != cuenta }?.id) }
    var meta by rememberSaveable { mutableStateOf(viejo?.meta ?: e.metas.firstOrNull()?.id) }
    var fecha by rememberSaveable { mutableStateOf(viejo?.fecha ?: vm.hoy().toString()) }
    var nota by rememberSaveable { mutableStateOf(viejo?.nota.orEmpty()) }
    var cuotas by rememberSaveable { mutableStateOf(viejo?.cuotas?.toString() ?: "1") }

    val laCuenta = activas.firstOrNull { it.id == cuenta } ?: e.cuentas.firstOrNull { it.id == cuenta }
    val esCredito = laCuenta?.tipo == TipoCuenta.CREDITO

    Column(Modifier.fillMaxSize()) {
        Cabecera(stringResource(if (viejo == null) R.string.anotar else R.string.corregir), vm::cerrar, Iconos.Cerrar) {
            if (viejo != null) IconButton({
                confirmar(vm.tx(R.string.borrar_movimiento), null) { vm.borrarMovimiento(viejo.id); vm.cerrar() }
            }) { io.github.fogonapps.identidad.IconoRiso(Iconos.Borrar, stringResource(R.string.borrar)) }
        }
        // Tipo de movimiento (al corregir no se cambia: sería otro movimiento)
        if (viejo == null) Selector(TipoMovimiento.entries.map { it to stringResource(it.nombre) }, tipo, {
            tipo = it
            categoria = null
            if (it != TipoMovimiento.GASTO && esCredito && it != TipoMovimiento.TRASPASO) cuenta = (sinCredito.firstOrNull() ?: activas.first()).id
        }, Modifier.padding(horizontal = 16.dp))

        // El monto, grande
        Text(
            if (monto.isEmpty()) Formato.pesos(0) else Formato.pesos(monto.toLong()),
            fontFamily = FogonTema.actual.cifras, fontSize = 48.sp, maxLines = 1, textAlign = TextAlign.Center,
            color = if (monto.isEmpty()) f.textoSuave else if (tipo == TipoMovimiento.INGRESO) f.acentoTexto else f.texto,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val ultimo = vm.ultimoGasto()
            if (viejo == null && tipo == TipoMovimiento.GASTO && ultimo != null && monto.isEmpty()) {
                val catU = e.categorias.firstOrNull { it.id == ultimo.categoria }?.nombre.orEmpty()
                TextButton({
                    monto = ultimo.monto.toString(); categoria = ultimo.categoria; nota = ultimo.nota
                    if (activas.any { it.id == ultimo.cuenta }) cuenta = ultimo.cuenta
                }, Modifier.vidrio(FormaPildora)) {
                    io.github.fogonapps.identidad.IconoRiso(Iconos.Repetir, null, Modifier.padding(end = 6.dp), corrida = null)
                    Text(stringResource(R.string.igual_que_ultimo, catU, Formato.pesos(ultimo.monto)), color = f.texto, maxLines = 1)
                }
            }

            when (tipo) {
                TipoMovimiento.GASTO, TipoMovimiento.INGRESO -> {
                    // De a cuatro por fila; la última fila completa con huecos para que no se estire.
                    e.categorias.filter { it.ingreso == (tipo == TipoMovimiento.INGRESO) }.chunked(4).forEach { fila ->
                      Row(Modifier.fillMaxWidth()) {
                        fila.forEach { c ->
                            val elegida = c.id == categoria
                            Column(
                                Modifier.weight(1f).clip(forma(16.dp))
                                    .then(if (elegida) Modifier.background(f.superficie).border(1.5.dp, colorDe(c.color), forma(16.dp)) else Modifier)
                                    .clickable { categoria = c.id }.semantics { selected = elegida; role = Role.RadioButton }.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Ficha(colorDe(c.color), Iconos.de(c.icono), 44.dp)
                                Text(c.nombre, style = MaterialTheme.typography.labelSmall, color = if (elegida) f.texto else f.textoSuave, maxLines = 1, textAlign = TextAlign.Center)
                            }
                        }
                        repeat(4 - fila.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
                      }
                    }
                    Text(stringResource(if (tipo == TipoMovimiento.GASTO) R.string.sale_de else R.string.entra_a), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
                    Selector((if (tipo == TipoMovimiento.GASTO) activas else sinCredito.ifEmpty { activas }).map { it.id to it.nombre }, cuenta, { cuenta = it })
                    if (tipo == TipoMovimiento.GASTO && esCredito) {
                        CampoNumero(cuotas, { cuotas = it.take(2) }, stringResource(R.string.cuotas), Modifier.fillMaxWidth())
                    }
                }
                TipoMovimiento.TRASPASO -> {
                    Text(stringResource(R.string.sale_de), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
                    Selector(sinCredito.ifEmpty { activas }.map { it.id to it.nombre }, cuenta, { cuenta = it; if (aCuenta == it) aCuenta = null })
                    Text(stringResource(R.string.llega_a), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
                    Selector(activas.filter { it.id != cuenta }.map { it.id to it.nombre }, aCuenta.orEmpty(), { aCuenta = it })
                    Pista(stringResource(R.string.pasar_pista), Modifier.padding(horizontal = 0.dp))
                }
                TipoMovimiento.AHORRO -> {
                    Text(stringResource(R.string.para_meta), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
                    Selector(listOf<Pair<String?, String>>(null to stringResource(R.string.sin_meta)) + e.metas.map { it.id to it.nombre }, meta, { meta = it })
                    Text(stringResource(R.string.sale_de), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
                    Selector(sinCredito.ifEmpty { activas }.map { it.id to it.nombre }, cuenta, { cuenta = it })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.fecha), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
                BotonFecha(fecha, { fecha = it }, vm.hoy())
            }
            CampoTexto(nota, { nota = it }, stringResource(R.string.nota), max = 80)
            val m = monto.toLongOrNull() ?: 0
            if (viejo == null && e.gmf && laCuenta?.tipo == TipoCuenta.BANCO && (tipo == TipoMovimiento.GASTO || tipo == TipoMovimiento.TRASPASO) && gmf(m) > 0) {
                Text(stringResource(R.string.mas_4x1000, Formato.pesos(gmf(m))), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
        }

        Teclado({ d -> if (monto.length < 12 && !(monto.isEmpty() && d.startsWith("0"))) monto += d }, { monto = monto.dropLast(1) })
        BotonRiso(stringResource(R.string.guardar), {
            val mov = Movimiento(
                id = viejo?.id.orEmpty(), fecha = fecha, tipo = tipo, monto = monto.toLongOrNull() ?: 0, cuenta = cuenta,
                aCuenta = if (tipo == TipoMovimiento.TRASPASO) aCuenta else null,
                categoria = if (tipo == TipoMovimiento.GASTO || tipo == TipoMovimiento.INGRESO) categoria ?: "otros".takeIf { tipo == TipoMovimiento.GASTO } ?: "extra" else null,
                nota = nota.trim(),
                cuotas = if (tipo == TipoMovimiento.GASTO && esCredito) cuotas.toIntOrNull()?.coerceIn(1, 48)?.takeIf { it > 1 } else null,
                fijo = viejo?.fijo,
                meta = if (tipo == TipoMovimiento.AHORRO) meta else null,
            )
            if (vm.guardarMovimiento(mov)) vm.cerrar()
        }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp))
    }
}

/** Teclado numérico grande: 1–9, 000, 0 y borrar. */
@Composable
private fun Teclado(onDigito: (String) -> Unit, onBorrar: () -> Unit) {
    val f = FogonTema.actual
    val borrar = stringResource(R.string.borrar)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("000", "0", "⌫")).forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                fila.forEach { t ->
                    Box(
                        Modifier.weight(1f).height(48.dp).clip(forma(16.dp)).background(f.superficie)
                            .clickable { if (t == "⌫") onBorrar() else onDigito(t) }
                            .then(if (t == "⌫") Modifier.semantics { contentDescription = borrar } else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (t == "⌫") io.github.fogonapps.identidad.IconoRiso(Iconos.Borrar, null)
                        else Text(t, fontFamily = FogonTema.actual.cifras, fontSize = 24.sp, color = if (t == "000") f.textoSuave else f.texto)
                    }
                }
            }
        }
    }
}

package io.github.fogonapps.ahorrador.ui

import io.github.fogonapps.identidad.brillo
import io.github.fogonapps.identidad.FormaPildora
import io.github.fogonapps.identidad.forma
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import io.github.fogonapps.identidad.haloPuntos
import io.github.fogonapps.identidad.NumeroRiso
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import io.github.fogonapps.ahorrador.datos.Cuenta
import io.github.fogonapps.ahorrador.datos.Estado
import io.github.fogonapps.ahorrador.datos.Formato
import io.github.fogonapps.ahorrador.datos.Movimiento
import io.github.fogonapps.ahorrador.datos.Sistema
import io.github.fogonapps.ahorrador.datos.TipoCuenta
import io.github.fogonapps.ahorrador.datos.TipoMovimiento
import io.github.fogonapps.ahorrador.datos.mesAnterior
import io.github.fogonapps.ahorrador.datos.mesDe
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.EstadoVacio
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.Tintas
import io.github.fogonapps.identidad.R as RId
import java.time.YearMonth

/* =================== Piezas que usan varias pantallas =================== */

fun TipoCuenta.icono() = when (this) {
    TipoCuenta.EFECTIVO -> Iconos.de("efectivo")
    TipoCuenta.BILLETERA -> Iconos.de("billetera")
    TipoCuenta.BANCO -> Iconos.de("banco")
    TipoCuenta.CREDITO -> Iconos.de("tarjeta")
}

fun TipoCuenta.color() = when (this) {
    TipoCuenta.EFECTIVO -> Tintas.Verde
    TipoCuenta.BILLETERA -> Tintas.Cian
    TipoCuenta.BANCO -> Tintas.Violeta
    TipoCuenta.CREDITO -> Tintas.Naranja
}

val TipoCuenta.nombre get() = when (this) {
    TipoCuenta.EFECTIVO -> R.string.tipo_efectivo
    TipoCuenta.BILLETERA -> R.string.tipo_billetera
    TipoCuenta.BANCO -> R.string.tipo_banco
    TipoCuenta.CREDITO -> R.string.tipo_credito
}

/** Nombre de los sobres de grupo (50/30/20 y kakeibo), que no son categorías. */
fun nombreGrupo(clave: String) = when (clave) {
    "NECESIDAD" -> R.string.grupo_necesidad
    "GUSTO" -> R.string.grupo_gusto
    "CULTURA" -> R.string.grupo_cultura
    "IMPREVISTO" -> R.string.grupo_imprevisto
    else -> R.string.grupo_ahorro
}

/** Un movimiento en una lista: ficha de su categoría, qué fue, de qué cuenta y cuánto. */
@Composable
fun FilaMovimiento(e: Estado, m: Movimiento, onClick: () -> Unit) {
    val f = FogonTema.actual
    val cat = e.categorias.firstOrNull { it.id == m.categoria }
    val cuenta = e.cuentas.firstOrNull { it.id == m.cuenta }?.nombre.orEmpty()
    val meta = e.metas.firstOrNull { it.id == m.meta }
    val (color, icono: io.github.fogonapps.identidad.Dibujo, titulo) = when (m.tipo) {
        TipoMovimiento.TRASPASO -> Triple(Tintas.GrisClaro, Iconos.Pasar, stringResource(R.string.paso_a, e.cuentas.firstOrNull { it.id == m.aCuenta }?.nombre.orEmpty()))
        TipoMovimiento.AHORRO -> Triple(meta?.let { colorDe(it.color) } ?: Tintas.Verde, Iconos.de(meta?.icono ?: "moneda"), meta?.nombre ?: stringResource(R.string.ahorro))
        else -> Triple(cat?.let { colorDe(it.color) } ?: Tintas.GrisClaro, Iconos.de(cat?.icono ?: "otros"), cat?.nombre ?: stringResource(R.string.sin_categoria))
    }
    FilaVidrio(onClick) {
        Ficha(color, icono, 42.dp)
        Column(Modifier.weight(1f)) {
            Text(if (m.nota.isNotBlank() && m.tipo != TipoMovimiento.AHORRO) m.nota else titulo, style = MaterialTheme.typography.titleSmall, color = f.texto, maxLines = 1)
            val detalle = buildString {
                if (m.nota.isNotBlank() && m.tipo != TipoMovimiento.AHORRO) append(titulo).append(" · ")
                append(cuenta).append(" · ").append(Formato.fechaCorta(m.fecha))
                m.cuotas?.takeIf { it > 1 }?.let { append(" · ").append(it).append("×") }
            }
            Text(detalle, style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1)
        }
        val signo = when (m.tipo) { TipoMovimiento.INGRESO -> "+ "; TipoMovimiento.TRASPASO -> ""; else -> "− " }
        Text(signo + Formato.pesos(m.monto), fontFamily = FogonTema.actual.cifras, fontSize = 17.sp,
            color = if (m.tipo == TipoMovimiento.INGRESO) f.acentoTexto else f.texto, maxLines = 1)
    }
}

/** Movimientos del más nuevo al más viejo (a igual fecha, el último anotado primero). */
fun recientes(lista: List<Movimiento>) = lista.withIndex().sortedWith(compareByDescending<IndexedValue<Movimiento>> { it.value.fecha }.thenByDescending { it.index }).map { it.value }

/* =================== Inicio =================== */

@Composable
fun Inicio(vm: AhorradorViewModel, e: Estado) {
    val f = FogonTema.actual
    val L = vm.L
    val hoy = vm.hoy()
    val mes = mesDe(hoy)

    // Cabecera: el personaje en una ficha de vidrio, el saludo y el perfil.
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(f.superficie).border(f.borde, f.linea, CircleShape), contentAlignment = Alignment.Center) {
            io.github.fogonapps.identidad.Personaje(RId.drawable.personaje_el_ahorrador, null, tamano = 40.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(Formato.fechaCorta(hoy) + " " + hoy.year, style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            Text(e.nombre?.let { stringResource(R.string.hola_nombre, it) } ?: stringResource(R.string.hola),
                style = MaterialTheme.typography.headlineSmall, color = f.texto, maxLines = 1)
        }
        Box(Modifier.size(48.dp).clip(CircleShape).background(f.superficie).border(f.borde, f.linea, CircleShape)
            .clickable { vm.abrir(Pantalla.Perfil) }, contentAlignment = Alignment.Center) {
            io.github.fogonapps.identidad.IconoRiso(Iconos.Persona, stringResource(R.string.perfil), tamano = 22.dp)
        }
    }

    // Lo que tienes: el número de puntos sobre su halo, y el mes en una línea.
    Column(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp).height(150.dp)
            .haloPuntos(f.tinta2.copy(alpha = if (f.oscuro) 0.9f else 0.6f), centro = androidx.compose.ui.geometry.Offset(0.5f, 0.45f)),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.tienes), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
        val total = Formato.pesos(L.patrimonio(e))
        NumeroRiso(total, (if (total.length <= 10) 50 else if (total.length <= 13) 40 else 32).sp, color = f.texto)
        Text(stringResource(R.string.inicio_mes, Formato.mes(mes), Formato.pesos(L.ingresos(e, mes)), Formato.pesos(L.gastos(e, mes))),
            style = MaterialTheme.typography.bodySmall, color = f.textoSuave, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
        if (e.sistema == Sistema.SOBRES && L.porAsignar(e, mes) > 0) {
            Text(stringResource(R.string.por_asignar_pista, Formato.pesos(L.porAsignar(e, mes))), style = MaterialTheme.typography.labelLarge,
                color = f.sobreTinta1,
                modifier = Modifier.padding(top = 8.dp).clip(FormaPildora).background(f.tinta1).clickable { vm.irA(io.github.fogonapps.ahorrador.Pestana.SOBRES) }
                    .padding(horizontal = 14.dp, vertical = 6.dp))
        }
    }

    // Las acciones, en una tarjeta de vidrio.
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).vidrio().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        AccionRedonda(Iconos.Gasto, stringResource(R.string.gasto), { vm.abrir(Pantalla.Anotar(TipoMovimiento.GASTO)) }, acento = true)
        AccionRedonda(Iconos.Ingreso, stringResource(R.string.ingreso), { vm.abrir(Pantalla.Anotar(TipoMovimiento.INGRESO)) })
        AccionRedonda(Iconos.Pasar, stringResource(R.string.pasar), { vm.abrir(Pantalla.Anotar(TipoMovimiento.TRASPASO)) })
        AccionRedonda(Iconos.Moneda, stringResource(R.string.ahorrar), { vm.abrir(Pantalla.Anotar(TipoMovimiento.AHORRO)) })
    }

    // La meta que va más cerca, con el personaje empujando su moneda.
    e.metas.maxByOrNull { L.progreso(e, it) }?.let { m ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).vidrio().clickable { vm.irA(io.github.fogonapps.ahorrador.Pestana.METAS) }
                .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.inicio_meta, m.nombre), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1)
                Text(Formato.pesos(L.ahorradoEn(e, m.id)) + " " + stringResource(R.string.de_x, Formato.pesos(m.objetivo)), style = MaterialTheme.typography.titleMedium, color = f.texto, maxLines = 1)
                Barra(L.progreso(e, m).toFloat(), f.tinta2)
            }
            io.github.fogonapps.identidad.Ilustracion(io.github.fogonapps.ahorrador.R.drawable.ilustracion_monedas, tamano = 88.dp,
                tinta = if (f.tinta2.luminance() > 0.3f) f.tinta2 else f.tinta1)
        }
    }

    // En los primeros días del mes, invita a cerrar el anterior (el kakeibo lo hace siempre).
    val anterior = mesAnterior(mes)
    if (hoy.dayOfMonth <= 10 && L.delMes(e, anterior).isNotEmpty() && e.cierres[anterior] == null) {
        FilaVidrio({ vm.abrir(Pantalla.Cierre(anterior)) }, Modifier.padding(top = 10.dp)) {
            Ficha(f.paleta.tinta, Iconos.Chulo, 42.dp)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.cierra_mes, Formato.mes(anterior)), style = MaterialTheme.typography.titleSmall, color = f.texto)
                Text(stringResource(R.string.cierra_mes_pista), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
            io.github.fogonapps.identidad.IconoRiso(Iconos.Siguiente, null, tamano = 20.dp, tinta = f.textoSuave, corrida = null)
        }
    }

    // Cuentas
    val cuentas = e.cuentas.filter { !it.archivada }
    Seccion(stringResource(R.string.cuentas)) { TextButton({ vm.abrir(Pantalla.Cuentas) }) { Text(stringResource(R.string.ver_todas)) } }
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        cuentas.forEach { c -> TarjetaCuenta(vm, e, c) { vm.abrir(Pantalla.Cuenta(c.id)) } }
    }

    // Próximos pagos
    Seccion(stringResource(R.string.proximos_pagos)) { TextButton({ vm.abrir(Pantalla.Fijos) }) { Text(stringResource(R.string.ver_todos)) } }
    val proximos = L.proximos(e, hoy)
    if (e.fijos.isEmpty()) {
        Pista(stringResource(R.string.fijos_vacio))
        BotonSecundario(stringResource(R.string.agregar_fijo), { vm.abrir(Pantalla.EditarFijo(null)) }, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
    } else if (proximos.isEmpty()) {
        Pista(stringResource(R.string.fijos_al_dia))
    } else proximos.take(3).forEach { (fijo, fecha) ->
        val cat = e.categorias.firstOrNull { it.id == fijo.categoria }
        FilaVidrio({ vm.abrir(Pantalla.EditarFijo(fijo.id)) }) {
            Ficha(cat?.let { colorDe(it.color) } ?: Tintas.GrisClaro, Iconos.de(cat?.icono ?: "otros"), 42.dp)
            Column(Modifier.weight(1f)) {
                Text(fijo.nombre, style = MaterialTheme.typography.titleSmall, color = f.texto, maxLines = 1)
                val vencido = fecha.isBefore(hoy)
                Text(if (vencido) stringResource(R.string.vencio_el, Formato.fechaCorta(fecha)) else stringResource(R.string.vence_el, Formato.fechaCorta(fecha)),
                    style = MaterialTheme.typography.bodySmall, color = if (vencido) MaterialTheme.colorScheme.error else f.textoSuave)
            }
            Column(horizontalAlignment = Alignment.End) {
                Pesos(fijo.monto, 16.sp)
                Text(stringResource(R.string.pagar), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = FogonTema.actual.paleta.sobreTinta,
                    modifier = Modifier.padding(top = 4.dp).clip(FormaPildora).background(f.paleta.tinta).clickable { vm.pagarFijo(fijo) }
                        .padding(horizontal = 12.dp, vertical = 4.dp))
            }
        }
    }

    // Me deben / debo
    Seccion(stringResource(R.string.deudas_titulo))
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f).vidrio().clickable { vm.abrir(Pantalla.Deudas) }.padding(16.dp)) {
            Text(stringResource(R.string.me_deben), style = MaterialTheme.typography.labelMedium, color = f.textoSuave)
            Pesos(L.meDeben(e), 22.sp, f.acentoTexto)
        }
        Column(Modifier.weight(1f).vidrio().clickable { vm.abrir(Pantalla.Deudas) }.padding(16.dp)) {
            Text(stringResource(R.string.debo), style = MaterialTheme.typography.labelMedium, color = f.textoSuave)
            Pesos(L.debo(e), 22.sp)
        }
    }

    // Últimos movimientos
    Seccion(stringResource(R.string.ultimos)) { TextButton({ vm.irA(io.github.fogonapps.ahorrador.Pestana.RESUMEN) }) { Text(stringResource(R.string.ver_todos)) } }
    val ultimos = recientes(e.movimientos).take(6)
    if (ultimos.isEmpty()) EstadoVacio(RId.drawable.personaje_el_ahorrador, stringResource(R.string.vacio_titulo), stringResource(R.string.vacio_texto))
    else ultimos.forEach { m -> FilaMovimiento(e, m) { vm.abrir(Pantalla.Anotar(m.tipo, m.id)) } }
}

@Composable
private fun MiniDato(etiqueta: String, n: Long) {
    Column {
        Text(etiqueta, style = MaterialTheme.typography.labelSmall, color = FogonTema.actual.sobreDestacada.copy(alpha = 0.8f))
        Pesos(n, 15.sp, FogonTema.actual.sobreDestacada)
    }
}

/** Tarjeta pequeña de una cuenta, para la fila de Inicio. */
@Composable
private fun TarjetaCuenta(vm: AhorradorViewModel, e: Estado, c: Cuenta, onClick: () -> Unit) {
    val f = FogonTema.actual
    Column(Modifier.width(150.dp).vidrio().clickable(onClick = onClick).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Ficha(c.tipo.color(), c.tipo.icono(), 38.dp)
        Nombre(c.nombre, 14.sp)
        if (c.tipo == TipoCuenta.CREDITO) {
            Text(stringResource(R.string.debes), style = MaterialTheme.typography.labelSmall, color = f.textoSuave)
            Pesos(vm.L.deuda(e, c.id), 18.sp)
        } else Pesos(vm.L.saldo(e, c.id), 18.sp)
    }
}

/* =================== Resumen =================== */

@Composable
fun Resumen(vm: AhorradorViewModel, e: Estado) {
    val f = FogonTema.actual
    val L = vm.L
    var mes by rememberSaveable { mutableStateOf(vm.mesActual()) }
    val actual = vm.mesActual()

    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.pestana_resumen), style = MaterialTheme.typography.headlineLarge, color = f.texto, modifier = Modifier.weight(1f))
    }
    SelectorMes(mes, { mes = it }, hasta = actual)

    Row(Modifier.fillMaxWidth().padding(16.dp).vidrio().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Dato(stringResource(R.string.entro), Formato.pesos(L.ingresos(e, mes)))
        Dato(stringResource(R.string.salio), Formato.pesos(L.gastos(e, mes)))
        Dato(stringResource(R.string.guardaste), Formato.pesos(L.ahorrado(e, mes)))
    }
    L.cambioGastos(e, mes)?.let { c ->
        Pista(when {
            c > 0 -> stringResource(R.string.gastaste_mas, c, Formato.mes(mesAnterior(mes)))
            c < 0 -> stringResource(R.string.gastaste_menos, -c, Formato.mes(mesAnterior(mes)))
            else -> stringResource(R.string.gastaste_igual, Formato.mes(mesAnterior(mes)))
        })
    }

    val porCat = L.porCategoria(e, mes)
    if (porCat.isNotEmpty()) {
        Seccion(stringResource(R.string.en_que_se_fue))
        val total = porCat.sumOf { it.second }.coerceAtLeast(1)
        // Una barra con los colores de cada categoría, del tamaño de lo que se gastó.
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(14.dp).clip(forma(7.dp))) {
            porCat.forEach { (c, n) -> Box(Modifier.weight(n.toFloat() / total).fillMaxHeight().background(colorDe(c.color))) }
        }
        porCat.forEach { (c, n) ->
            FilaVidrio {
                Ficha(colorDe(c.color), Iconos.de(c.icono), 38.dp)
                Text(c.nombre, style = MaterialTheme.typography.titleSmall, color = f.texto, modifier = Modifier.weight(1f), maxLines = 1)
                Text("${n * 100 / total} %", style = MaterialTheme.typography.labelMedium, color = f.textoSuave)
                Pesos(n, 16.sp)
            }
        }
    }

    BotonSecundario(stringResource(R.string.cierre_del_mes), { vm.abrir(Pantalla.Cierre(mes)) }, Modifier.fillMaxWidth().padding(16.dp))

    Seccion(stringResource(R.string.movimientos))
    val lista = recientes(L.delMes(e, mes))
    if (lista.isEmpty()) Pista(stringResource(R.string.mes_sin_movimientos))
    lista.groupBy { it.fecha }.forEach { (fecha, ms) ->
        Text(Formato.fechaCorta(fecha), style = MaterialTheme.typography.labelLarge, color = f.textoSuave, modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 2.dp))
        ms.forEach { m -> FilaMovimiento(e, m) { vm.abrir(Pantalla.Anotar(m.tipo, m.id)) } }
    }
}

/** ‹ Octubre 2026 › para cambiar de mes (no deja pasar del mes [hasta]). */
@Composable
fun SelectorMes(mes: String, onCambio: (String) -> Unit, hasta: String) {
    val f = FogonTema.actual
    val ym = YearMonth.parse(mes)
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton({ onCambio(ym.minusMonths(1).toString()) }) { io.github.fogonapps.identidad.IconoRiso(Iconos.Atras, stringResource(R.string.mes_anterior), corrida = null) }
        Text(Formato.nombreMes(mes) + " " + ym.year, fontFamily = FogonTema.actual.titulos, fontSize = 18.sp, color = f.texto,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
        IconButton({ onCambio(ym.plusMonths(1).toString()) }, enabled = mes < hasta) {
            io.github.fogonapps.identidad.IconoRiso(Iconos.Siguiente, stringResource(R.string.mes_siguiente), tinta = if (mes < hasta) f.texto else f.textoSuave.copy(alpha = 0.4f), corrida = null)
        }
    }
}

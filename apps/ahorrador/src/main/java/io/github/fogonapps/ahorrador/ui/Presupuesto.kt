package io.github.fogonapps.ahorrador.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fogonapps.ahorrador.AhorradorViewModel
import io.github.fogonapps.ahorrador.Pantalla
import io.github.fogonapps.ahorrador.R
import io.github.fogonapps.ahorrador.datos.Estado
import io.github.fogonapps.ahorrador.datos.Formato
import io.github.fogonapps.ahorrador.datos.Meta
import io.github.fogonapps.ahorrador.datos.Sistema
import io.github.fogonapps.ahorrador.datos.Sobre
import io.github.fogonapps.ahorrador.datos.TipoCuenta
import io.github.fogonapps.ahorrador.datos.mesAnterior
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.EstadoVacio
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.Logro
import io.github.fogonapps.identidad.Tintas
import io.github.fogonapps.identidad.R as RId

/** Diálogo para escribir una cantidad de plata. [extra] va debajo del campo. */
@Composable
fun DialogoPesos(
    titulo: String,
    inicial: Long?,
    onListo: (Long) -> Unit,
    onCerrar: () -> Unit,
    texto: String? = null,
    extra: @Composable () -> Unit = {},
) {
    var valor by rememberSaveable { mutableStateOf(inicial?.takeIf { it > 0 }?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                texto?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                CampoPesos(valor, { valor = it }, stringResource(R.string.monto))
                extra()
            }
        },
        confirmButton = { TextButton({ onListo(valor.toLongOrNull() ?: 0); onCerrar() }) { Text(stringResource(R.string.listo), fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onCerrar) { Text(stringResource(R.string.cancelar)) } },
    )
}

/* =================== Presupuesto (según el sistema) =================== */

@Composable
fun Presupuesto(vm: AhorradorViewModel, e: Estado) {
    val f = FogonTema.actual
    var mes by rememberSaveable { mutableStateOf(vm.mesActual()) }

    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
        Text(stringResource(R.string.pestana_presupuesto), style = MaterialTheme.typography.headlineLarge, color = f.texto)
        Text(stringResource(e.sistema.nombre), style = MaterialTheme.typography.labelLarge, color = f.acentoTexto)
    }
    if (e.sistema != Sistema.SOLO_ANOTAR) SelectorMes(mes, { mes = it }, hasta = vm.mesActual())

    when (e.sistema) {
        Sistema.SOBRES -> Sobres(vm, e, mes)
        Sistema.REGLA_50_30_20 -> Regla(vm, e, mes)
        Sistema.KAKEIBO -> Kakeibo(vm, e, mes)
        Sistema.SOLO_ANOTAR -> {
            Pista(stringResource(R.string.solo_anotar_pista))
            BotonSecundario(stringResource(R.string.elegir_sistema), { vm.abrir(Pantalla.Perfil) }, Modifier.padding(16.dp))
        }
    }
}

@Composable
private fun TarjetaSobre(s: Sobre, nombre: String, onClick: (() -> Unit)?) {
    val f = FogonTema.actual
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).vidrio()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Ficha(colorDe(s.color), Iconos.de(s.icono), 40.dp)
            Column(Modifier.weight(1f)) {
                Nombre(nombre, 15.sp)
                Text(
                    if (s.asignado == null) stringResource(R.string.gastado_x, Formato.pesos(s.gastado))
                    else stringResource(R.string.gastado_de, Formato.pesos(s.gastado), Formato.pesos(s.asignado)),
                    style = MaterialTheme.typography.bodySmall, color = f.textoSuave,
                )
            }
            s.queda?.let { q ->
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(if (q < 0) R.string.te_pasaste else R.string.queda), style = MaterialTheme.typography.labelSmall,
                        color = if (q < 0) MaterialTheme.colorScheme.error else f.textoSuave)
                    Pesos(kotlin.math.abs(q), 17.sp, if (q < 0) MaterialTheme.colorScheme.error else f.texto)
                }
            }
        }
        if (s.asignado != null) Barra(if (s.asignado <= 0) (if (s.gastado > 0) 1f else 0f) else s.gastado.toFloat() / s.asignado,
            if (s.pasado) MaterialTheme.colorScheme.error else colorDe(s.color))
    }
}

/** Sistema Sobres: cada peso que entra se reparte en sobres; lo que se gasta sale del sobre. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Sobres(vm: AhorradorViewModel, e: Estado, mes: String) {
    val f = FogonTema.actual
    val L = vm.L
    var editando by remember { mutableStateOf<Sobre?>(null) }
    val asignados = e.sobres[mes].orEmpty()
    val sobres = e.categorias.filter { !it.ingreso }.map { Sobre(it.id, it.nombre, it.color, it.icono, asignados[it.id] ?: 0, L.gastadoEn(e, mes, it.id)) }

    Column(Modifier.fillMaxWidth().padding(16.dp).destacada().padding(22.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.por_asignar), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
        Pesos(L.porAsignar(e, mes), 40.sp, Color.White)
        Text(stringResource(R.string.por_asignar_texto), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
    }
    if (asignados.isEmpty() && e.sobres[mesAnterior(mes)].orEmpty().isNotEmpty()) {
        BotonSecundario(stringResource(R.string.copiar_sobres, Formato.mes(mesAnterior(mes))), { vm.copiarSobresAnteriores(mes) },
            Modifier.fillMaxWidth().padding(horizontal = 16.dp))
    }
    Pista(stringResource(R.string.sobres_pista))
    sobres.sortedByDescending { (it.asignado ?: 0) + it.gastado }.forEach { s -> TarjetaSobre(s, s.nombre) { editando = s } }

    editando?.let { s ->
        val exceso = -(s.queda ?: 0)
        val conPlata = sobres.filter { it.clave != s.clave && (it.queda ?: 0) > 0 }
        DialogoPesos(
            stringResource(R.string.asignar_a, s.nombre), s.asignado, { vm.asignar(mes, s.clave, it) }, { editando = null },
            stringResource(R.string.asignar_texto, Formato.pesos(L.porAsignar(e, mes))),
        ) {
            if (exceso > 0 && conPlata.isNotEmpty()) {
                Text(stringResource(R.string.cubrir_con, Formato.pesos(exceso)), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    conPlata.forEach { o ->
                        TextButton({ vm.cubrir(mes, o.clave, s.clave, minOf(exceso, o.queda ?: 0)); editando = null }, Modifier.vidrio(RoundedCornerShape(50))) {
                            Text(o.nombre + " · " + Formato.pesos(o.queda ?: 0), color = f.texto)
                        }
                    }
                }
            }
        }
    }
}

/** Regla 50/30/20: lo que entra se parte en necesidades, gustos y ahorro. */
@Composable
private fun Regla(vm: AhorradorViewModel, e: Estado, mes: String) {
    var cambiando by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(16.dp).destacada().padding(22.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.entro_en, Formato.mes(mes)), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
        Pesos(vm.L.ingresos(e, mes), 40.sp, Color.White)
        Text(stringResource(R.string.reparto_es, e.reparto[0], e.reparto[1], e.reparto[2]), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
    }
    vm.L.sobres(e, mes).forEach { s -> TarjetaSobre(s, stringResource(nombreGrupo(s.clave)), null) }
    Pista(stringResource(R.string.regla_pista))
    BotonSecundario(stringResource(R.string.cambiar_reparto), { cambiando = true }, Modifier.fillMaxWidth().padding(16.dp))
    if (cambiando) DialogoReparto(e.reparto, vm::cambiarReparto) { cambiando = false }
}

@Composable
fun DialogoReparto(actual: List<Int>, onListo: (List<Int>) -> Unit, onCerrar: () -> Unit) {
    var n by rememberSaveable { mutableStateOf(actual[0].toString()) }
    var g by rememberSaveable { mutableStateOf(actual[1].toString()) }
    var a by rememberSaveable { mutableStateOf(actual[2].toString()) }
    val suma = listOf(n, g, a).sumOf { it.toIntOrNull() ?: 0 }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(stringResource(R.string.cambiar_reparto)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoNumero(n, { n = it.take(3) }, stringResource(R.string.grupo_necesidad) + " %", Modifier.fillMaxWidth())
                CampoNumero(g, { g = it.take(3) }, stringResource(R.string.grupo_gusto) + " %", Modifier.fillMaxWidth())
                CampoNumero(a, { a = it.take(3) }, stringResource(R.string.grupo_ahorro) + " %", Modifier.fillMaxWidth())
                Text(stringResource(R.string.suma_100, suma), color = if (suma == 100) FogonTema.actual.textoSuave else MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton({ onListo(listOf(n, g, a).map { it.toIntOrNull() ?: 0 }); if (suma == 100) onCerrar() }, enabled = suma == 100) {
                Text(stringResource(R.string.listo), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onCerrar) { Text(stringResource(R.string.cancelar)) } },
    )
}

/** Kakeibo: cuánto quiero ahorrar, en qué se va la plata (cuatro grupos) y, al final del mes, qué voy a mejorar. */
@Composable
private fun Kakeibo(vm: AhorradorViewModel, e: Estado, mes: String) {
    val L = vm.L
    var meta by remember { mutableStateOf(false) }
    val ahorro = L.ingresos(e, mes) - L.gastos(e, mes)
    Column(Modifier.fillMaxWidth().padding(16.dp).destacada().clickable { meta = true }.padding(22.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.quiero_ahorrar), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
        Pesos(e.metaAhorroMes ?: 0, 40.sp, Color.White)
        Text(stringResource(R.string.llevas_ahorrado, Formato.pesos(ahorro)), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
        if (e.metaAhorroMes != null && e.metaAhorroMes > 0) Barra(ahorro.toFloat() / e.metaAhorroMes, Color.White, Modifier.padding(top = 6.dp))
    }
    L.sobres(e, mes).forEach { s -> TarjetaSobre(s, stringResource(nombreGrupo(s.clave)), null) }
    Pista(stringResource(R.string.kakeibo_pista))
    BotonRiso(stringResource(R.string.cierre_del_mes), { vm.abrir(Pantalla.Cierre(mes)) }, Modifier.fillMaxWidth().padding(16.dp))
    if (meta) DialogoPesos(stringResource(R.string.quiero_ahorrar), e.metaAhorroMes, { vm.cambiarMetaAhorro(it) }, { meta = false })
}

/* =================== Metas =================== */

@Composable
fun Metas(vm: AhorradorViewModel, e: Estado) {
    val f = FogonTema.actual
    val L = vm.L
    val hoy = vm.hoy()
    var abonando by remember { mutableStateOf<Meta?>(null) }

    Text(stringResource(R.string.pestana_metas), style = MaterialTheme.typography.headlineLarge, color = f.texto,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp))
    if (e.metas.isEmpty()) {
        EstadoVacio(RId.drawable.personaje_el_ahorrador, stringResource(R.string.metas_vacio_titulo), stringResource(R.string.metas_vacio_texto))
    } else {
        Column(Modifier.fillMaxWidth().padding(16.dp).destacada().padding(22.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.en_metas), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
            Pesos(e.metas.sumOf { L.ahorradoEn(e, it.id) }, 40.sp, Color.White)
        }
        e.metas.forEach { m ->
            val ahorrado = L.ahorradoEn(e, m.id)
            val cumplida = ahorrado >= m.objetivo
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).vidrio().clickable { vm.abrir(Pantalla.EditarMeta(m.id)) }.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Ficha(colorDe(m.color), Iconos.de(m.icono), 46.dp)
                    Column(Modifier.weight(1f)) {
                        Nombre(m.nombre, 17.sp)
                        Text(stringResource(R.string.de_x, Formato.pesos(m.objetivo)), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                    }
                    Pesos(ahorrado, 20.sp)
                }
                Barra(L.progreso(e, m).toFloat(), colorDe(m.color))
                when {
                    cumplida -> Logro(stringResource(R.string.meta_cumplida))
                    else -> {
                        L.porQuincena(e, m, hoy)?.let {
                            val limite = java.time.LocalDate.parse(m.fecha!!)
                            Text(stringResource(R.string.por_quincena, Formato.pesos(it), Formato.fechaCorta(limite) + if (limite.year != hoy.year) " ${limite.year}" else ""), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                        }
                        L.llegaEn(e, m, hoy)?.let {
                            Text(stringResource(R.string.llegas_en, Formato.mesesLargos[it.monthValue - 1], it.year), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                        }
                        Text(stringResource(R.string.abonar), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Tintas.Tinta,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(f.paleta.tinta).clickable { abonando = m }.padding(horizontal = 18.dp, vertical = 8.dp))
                    }
                }
            }
        }
    }
    BotonSecundario(stringResource(R.string.nueva_meta), { vm.abrir(Pantalla.EditarMeta(null)) }, Modifier.fillMaxWidth().padding(16.dp))

    abonando?.let { m ->
        val cuentas = e.cuentas.filter { !it.archivada && it.tipo != TipoCuenta.CREDITO }
        var cuenta by rememberSaveable { mutableStateOf(cuentas.firstOrNull()?.id.orEmpty()) }
        DialogoPesos(stringResource(R.string.abonar_a, m.nombre), null, { if (it > 0) vm.abonarMeta(m, it, cuenta) }, { abonando = null },
            stringResource(R.string.abonar_texto)) {
            Text(stringResource(R.string.sale_de), style = MaterialTheme.typography.labelLarge)
            Selector(cuentas.map { it.id to it.nombre }, cuenta, { cuenta = it })
        }
    }
}

/** Colores para metas: las tintas riso de Fogón. */
val COLORES_META = listOf(Tintas.Verde, Tintas.Amarillo, Tintas.Rosa, Tintas.Azul, Tintas.Violeta, Tintas.Naranja, Tintas.Cian, Tintas.Rojo)
val ICONOS_META = listOf("moneda", "viaje", "casa", "estudio", "transporte", "celular", "salud", "prima", "extra", "compras")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditarMeta(vm: AhorradorViewModel, e: Estado, id: String?) {
    val f = FogonTema.actual
    val confirmar = LocalConfirmar.current
    val m = e.metas.firstOrNull { it.id == id }
    var nombre by rememberSaveable { mutableStateOf(m?.nombre.orEmpty()) }
    var objetivo by rememberSaveable { mutableStateOf(m?.objetivo?.toString().orEmpty()) }
    var conFecha by rememberSaveable { mutableStateOf(m?.fecha != null) }
    var fecha by rememberSaveable { mutableStateOf(m?.fecha ?: vm.hoy().plusMonths(6).toString()) }
    var color by rememberSaveable { mutableLongStateOf(m?.color ?: (COLORES_META[e.metas.size % COLORES_META.size].toArgb().toLong() and 0xFFFFFFFFL)) }
    var icono by rememberSaveable { mutableStateOf(m?.icono ?: "moneda") }

    Cabecera(stringResource(if (m == null) R.string.nueva_meta else R.string.editar_meta), vm::cerrar)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CampoTexto(nombre, { nombre = it }, stringResource(R.string.meta_nombre))
        CampoPesos(objetivo, { objetivo = it }, stringResource(R.string.meta_objetivo))
        Row(Modifier.fillMaxWidth().vidrio().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.meta_con_fecha), color = f.texto, modifier = Modifier.weight(1f))
            Switch(conFecha, { conFecha = it })
        }
        if (conFecha) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.meta_para), color = f.textoSuave)
            BotonFecha(fecha, { fecha = it }, vm.hoy(), hastaHoy = false)
        }
        Text(stringResource(R.string.color), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            COLORES_META.forEach { c ->
                val valor = c.toArgb().toLong() and 0xFFFFFFFFL
                Box(Modifier.size(40.dp).clip(CircleShape).background(c).then(if (valor == color) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                    .clickable { color = valor }.semantics { selected = valor == color })
            }
        }
        Text(stringResource(R.string.icono), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ICONOS_META.forEach { i ->
                Box(Modifier.size(46.dp).clip(CircleShape).background(if (i == icono) colorDe(color) else f.superficie).clickable { icono = i }
                    .semantics { selected = i == icono; contentDescription = i }, contentAlignment = Alignment.Center) {
                    Icon(Iconos.de(i), null, tint = if (i == icono) Tintas.Tinta else f.texto, modifier = Modifier.size(22.dp))
                }
            }
        }
        BotonRiso(stringResource(R.string.guardar), {
            val obj = objetivo.toLongOrNull() ?: 0
            if (nombre.isNotBlank() && obj > 0) {
                vm.guardarMeta(Meta(m?.id.orEmpty(), nombre.trim(), obj, if (conFecha) fecha else null, color, icono))
                vm.cerrar()
            }
        }, Modifier.fillMaxWidth(), habilitado = nombre.isNotBlank() && (objetivo.toLongOrNull() ?: 0) > 0)
        if (m != null) BotonSecundario(stringResource(R.string.borrar_meta), {
            confirmar(vm.tx(R.string.borrar_meta_pregunta, m.nombre), vm.tx(R.string.borrar_meta_texto)) { vm.borrarMeta(m.id) }
        }, Modifier.fillMaxWidth(), peligro = true)
    }
    Espacio(40.dp)
}

package io.github.fogonapps.ahorrador.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.fogonapps.ahorrador.AhorradorViewModel
import io.github.fogonapps.ahorrador.Pantalla
import io.github.fogonapps.ahorrador.R
import io.github.fogonapps.ahorrador.datos.Estado
import io.github.fogonapps.ahorrador.datos.Formato
import io.github.fogonapps.ahorrador.datos.TipoMovimiento
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.FormaPildora

/**
 * Todos los movimientos, con búsqueda por texto o valor y filtros de categoría y mes. Se llega desde "Ver todos"
 * en Inicio, desde "Buscar" en Resumen y tocando una categoría de "En qué se fue" (ya filtrada).
 */
@Composable
fun Movimientos(vm: AhorradorViewModel, e: Estado, p: Pantalla.Movimientos) {
    val f = FogonTema.actual
    var texto by rememberSaveable { mutableStateOf("") }
    var categoria by rememberSaveable { mutableStateOf(p.categoria) }
    var mes by rememberSaveable { mutableStateOf(p.mes) }
    val lista = recientes(vm.L.buscar(e, texto, categoria, mes))

    Cabecera(stringResource(R.string.movimientos), vm::cerrar)
    OutlinedTextField(texto, { texto = it.take(40) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true, shape = FormaPildora,
        placeholder = { Text(stringResource(R.string.buscar_movimientos)) }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search))

    // Mes: el que venía, o todos.
    if (p.mes != null) Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pastilla(Formato.nombreMes(p.mes) + " " + p.mes.take(4), mes != null) { mes = p.mes }
        Pastilla(stringResource(R.string.todos_los_meses), mes == null) { mes = null }
    }
    // Categorías que tienen movimientos, las más usadas primero.
    val usadas = e.movimientos.groupingBy { it.categoria }.eachCount()
    // La que se abrió va primero, para que se vea elegida sin tener que correr la fila.
    val cats = e.categorias.filter { (usadas[it.id] ?: 0) > 0 }.sortedWith(compareByDescending<io.github.fogonapps.ahorrador.datos.Categoria> { it.id == p.categoria }.thenByDescending { usadas[it.id] })
    if (cats.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pastilla(stringResource(R.string.todas), categoria == null) { categoria = null }
        cats.forEach { c -> Pastilla(c.nombre, categoria == c.id) { categoria = if (categoria == c.id) null else c.id } }
    }

    val salio = lista.filter { it.tipo == TipoMovimiento.GASTO }.sumOf { it.monto }
    val entro = lista.filter { it.tipo == TipoMovimiento.INGRESO }.sumOf { it.monto }
    Text(pluralStringResource(R.plurals.n_movimientos, lista.size, lista.size) + " · " + stringResource(R.string.salio_entro, Formato.pesos(salio), Formato.pesos(entro)),
        style = MaterialTheme.typography.bodySmall, color = f.textoSuave, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))

    if (lista.isEmpty()) Pista(stringResource(R.string.sin_resultados))
    // Hasta 300 para que la pantalla no se vuelva lenta; buscando se llega a los demás.
    lista.take(300).groupBy { it.fecha }.forEach { (fecha, ms) ->
        Text(Formato.fechaCorta(fecha) + if (fecha.take(4) != vm.hoy().year.toString()) " " + fecha.take(4) else "",
            style = MaterialTheme.typography.labelLarge, color = f.textoSuave, modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 2.dp))
        ms.forEach { m -> FilaMovimiento(e, m, conFecha = false) { vm.abrir(Pantalla.Anotar(m.tipo, m.id)) } }
    }
    Espacio()
}

@Composable
private fun Pastilla(texto: String, elegida: Boolean, onClick: () -> Unit) {
    val f = FogonTema.actual
    FilterChip(elegida, onClick, { Text(texto, maxLines = 1) }, shape = FormaPildora,
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = f.paleta.tinta, selectedLabelColor = f.paleta.sobreTinta))
}

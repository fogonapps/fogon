package io.github.fogonapps.cucha.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fogonapps.cucha.CuchaViewModel
import io.github.fogonapps.cucha.Pantalla
import io.github.fogonapps.cucha.R
import io.github.fogonapps.cucha.datos.Comible
import io.github.fogonapps.cucha.datos.Estado
import io.github.fogonapps.cucha.datos.Ingrediente
import io.github.fogonapps.cucha.datos.Nutrientes
import io.github.fogonapps.cucha.datos.Receta
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.FormaPildora
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.NumeroRiso
import io.github.fogonapps.identidad.aparecer
import kotlin.math.roundToInt

/** La pestaña de recetas: filtro por comida y la lista. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Recetas(vm: CuchaViewModel, e: Estado) {
    val f = FogonTema.actual
    val idioma = vm.idioma()
    var filtro by rememberSaveable { mutableStateOf<String?>(null) }
    val lista = vm.L.recetas(e).filter { filtro == null || filtro in it.comidas }.sortedBy { it.nombre(idioma) }

    Seccion(stringResource(R.string.pestana_recetas), Modifier.padding(top = 12.dp)) {
        Pildora(stringResource(R.string.nueva), Iconos.Mas, acento = true) { vm.abrir(Pantalla.EditarReceta(null)) }
    }
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (listOf<String?>(null) + vm.comidas).forEach { c ->
            FilterChip(c == filtro, { filtro = c }, { Text(if (c == null) stringResource(R.string.todas) else nombreComida(c)) }, shape = FormaPildora,
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = f.tinta1, selectedLabelColor = f.sobreTinta1))
        }
    }
    lista.forEachIndexed { i, r ->
        val n = vm.L.porPorcion(r)
        FilaVidrio({ vm.abrir(Pantalla.VerReceta(r.id)) }, Modifier.aparecer(i.coerceAtMost(10))) {
            Ficha(f.tinta2, Iconos.Recetas)
            Column(Modifier.weight(1f)) {
                Text(r.nombre(idioma), style = MaterialTheme.typography.bodyLarge, color = f.texto, maxLines = 2)
                Text(
                    listOfNotNull(
                        stringResource(R.string.kcal_porcion, n.kcal.roundToInt()),
                        stringResource(R.string.proteina_g, n.proteina.roundToInt()),
                        r.minutos.takeIf { it > 0 }?.let { stringResource(R.string.minutos, it) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1,
                )
            }
            if ("r:${r.id}" in e.favoritos) IconoRiso(Iconos.Estrella, null, tamano = 16.dp, tinta = f.acentoTexto)
        }
    }
    if (lista.isEmpty()) Pista(stringResource(R.string.sin_recetas), centro = true)
}

/** Una receta: lo que aporta cada porción, los ingredientes y los pasos. */
@Composable
fun VerReceta(vm: CuchaViewModel, e: Estado, id: String) {
    val f = FogonTema.actual
    val idioma = vm.idioma()
    val confirmar = LocalConfirmar.current
    val r = vm.L.receta(e, id)
    if (r == null) { Cabecera(stringResource(R.string.pestana_recetas), vm::cerrar); Pista(stringResource(R.string.ya_no_esta)); return }
    val n = vm.L.porPorcion(r)
    val original = vm.L.base.receta(id)
    val editada = original != null && e.recetas.any { it.id == id }

    Cabecera(r.nombre(idioma), vm::cerrar) {
        BotonIcono(Iconos.Lapiz, stringResource(R.string.editar), grande = true) { vm.abrir(Pantalla.EditarReceta(id)) }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.aparecer(0).fillMaxWidth().vidrio().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumeroRiso("${n.kcal.roundToInt()}", 44.sp)
                Text(stringResource(R.string.kcal_cada_porcion), style = MaterialTheme.typography.titleSmall, color = f.textoSuave, modifier = Modifier.padding(bottom = 6.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Dato(stringResource(R.string.proteina), "${n.proteina.roundToInt()} g", Modifier.weight(1f))
                Dato(stringResource(R.string.carbos), "${n.carbos.roundToInt()} g", Modifier.weight(1f))
                Dato(stringResource(R.string.grasa), "${n.grasa.roundToInt()} g", Modifier.weight(1f))
            }
            Text(
                listOfNotNull(
                    androidx.compose.ui.res.pluralStringResource(R.plurals.porciones_n, r.porciones, r.porciones),
                    r.minutos.takeIf { it > 0 }?.let { stringResource(R.string.minutos, it) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = f.textoSuave,
            )
        }
        BotonRiso(stringResource(R.string.anotar_porcion), {
            vm.abrir(Pantalla.Cantidad("r:${r.id}", r.comidas.firstOrNull() ?: vm.comidaAhora(), vm.dia.value.toString()))
        }, Modifier.fillMaxWidth())

        if (r.ingredientes.isNotEmpty()) {
            Text(stringResource(R.string.ingredientes), style = MaterialTheme.typography.titleLarge, color = f.texto)
            Column(Modifier.fillMaxWidth().vidrio().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                r.ingredientes.forEach { i ->
                    val a = vm.L.base.alimento(i.alimento)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a?.nombre(idioma) ?: i.alimento, style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.weight(1f))
                        Text("${cifra(i.gramos, idioma)} g", fontFamily = f.cifras, color = f.textoSuave)
                    }
                }
            }
        }
        val pasos = r.pasos(idioma)
        if (pasos.isNotEmpty()) {
            Text(stringResource(R.string.preparacion), style = MaterialTheme.typography.titleLarge, color = f.texto)
            pasos.forEachIndexed { i, paso ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(28.dp).clip(CircleShape).vidrio(CircleShape), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", fontFamily = f.cifras, fontWeight = FontWeight.Bold, color = f.acentoTexto)
                    }
                    Text(paso, style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.weight(1f).padding(top = 3.dp))
                }
            }
        }
        if (r.nota.isNotBlank()) Pista(r.nota, adentro = true)
        if (r.ingredientes.isNotEmpty()) Pista(stringResource(R.string.fuente_receta), adentro = true)
        if (editada) BotonSecundario(stringResource(R.string.volver_original), {
            confirmar(vm.tx(R.string.volver_original_pregunta), null) { vm.borrarReceta(id) }
        }, Modifier.fillMaxWidth())
        else if (original == null) BotonSecundario(stringResource(R.string.borrar_receta), {
            confirmar(vm.tx(R.string.borrar_pregunta, r.nombre(idioma)), null) { vm.borrarReceta(id) }
        }, Modifier.fillMaxWidth(), peligro = true)
    }
}

/** Crear o corregir una receta. Al corregir una de ejemplo se guarda una copia propia con el mismo id. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditarReceta(vm: CuchaViewModel, e: Estado, p: Pantalla.EditarReceta) {
    val f = FogonTema.actual
    val idioma = vm.idioma()
    val r = p.id?.let { vm.L.receta(e, it) }
    // Una receta nueva armada con lo anotado en una comida trae sus ingredientes y un nombre para cambiar.
    val nombreComida = p.comida?.let { nombreComida(it).lowercase() }
    val nombreInicial = r?.nombre(idioma) ?: nombreComida?.let { stringResource(R.string.receta_de_comida, it) } ?: ""
    var nombre by rememberSaveable { mutableStateOf(nombreInicial) }
    var porciones by rememberSaveable { mutableStateOf((r?.porciones ?: 1).toString()) }
    var minutos by rememberSaveable { mutableStateOf(r?.minutos?.takeIf { it > 0 }?.toString() ?: "") }
    var pasos by rememberSaveable { mutableStateOf(r?.pasos(idioma)?.joinToString("\n") ?: "") }
    var nota by rememberSaveable { mutableStateOf(r?.nota ?: "") }
    val comidas = remember { mutableStateListOf(*(r?.comidas ?: listOfNotNull(p.comida)).toTypedArray()) }
    val ingredientes = remember { mutableStateListOf(*(r?.ingredientes ?: p.ingredientes).toTypedArray()) }
    var buscar by rememberSaveable { mutableStateOf("") }
    // Sin ingredientes, los valores de cada porción se escriben a mano.
    var kcal by rememberSaveable { mutableStateOf(r?.porPorcion?.kcal?.let { cifra(it, idioma) } ?: "") }
    var prot by rememberSaveable { mutableStateOf(r?.porPorcion?.proteina?.let { cifra(it, idioma) } ?: "") }
    var carb by rememberSaveable { mutableStateOf(r?.porPorcion?.carbos?.let { cifra(it, idioma) } ?: "") }
    var gras by rememberSaveable { mutableStateOf(r?.porPorcion?.grasa?.let { cifra(it, idioma) } ?: "") }

    Cabecera(stringResource(if (r == null) R.string.nueva_receta else R.string.editar_receta), vm::cerrar)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CampoTexto(nombre, { nombre = it }, stringResource(R.string.nombre), max = 60)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CampoNumero(porciones, { porciones = it }, stringResource(R.string.porciones), Modifier.weight(1f))
            CampoNumero(minutos, { minutos = it }, stringResource(R.string.minutos_campo), Modifier.weight(1f))
        }
        Text(stringResource(R.string.para_que_comida), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            vm.comidas.forEach { c ->
                FilterChip(c in comidas, { if (c in comidas) comidas.remove(c) else comidas.add(c) }, { Text(nombreComida(c)) }, shape = FormaPildora,
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = f.tinta1, selectedLabelColor = f.sobreTinta1))
            }
        }

        Text(stringResource(R.string.ingredientes), style = MaterialTheme.typography.titleLarge, color = f.texto)
        ingredientes.forEachIndexed { i, ing ->
            val a = vm.L.base.alimento(ing.alimento)
            Row(Modifier.fillMaxWidth().vidrio().padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(a?.nombre(idioma) ?: ing.alimento, style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.weight(1f), maxLines = 2)
                CampoGramos(ing.gramos, idioma) { g -> ingredientes[i] = ing.copy(gramos = g) }
                BotonIcono(Iconos.Cerrar, stringResource(R.string.quitar)) { ingredientes.removeAt(i) }
            }
        }
        CampoTexto(buscar, { buscar = it }, stringResource(R.string.agregar_ingrediente))
        if (buscar.isNotBlank()) {
            vm.L.buscar(e, buscar, idioma).filterIsInstance<Comible.DeBase>().map { it.a }.take(6).forEach { a ->
                FilaVidrio({ ingredientes.add(Ingrediente(a.id, a.porciones.firstOrNull()?.gramos ?: 100.0)); buscar = "" }, adentro = true) {
                    IconoRiso(Iconos.Mas, null, tamano = 18.dp, tinta = f.acentoTexto)
                    Text(a.nombre(idioma), style = MaterialTheme.typography.bodyLarge, color = f.texto)
                }
            }
        }
        if (ingredientes.isEmpty()) {
            Pista(stringResource(R.string.sin_ingredientes_pista), adentro = true)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CampoNumero(kcal, { kcal = it }, "kcal", Modifier.weight(1f), decimales = true)
                CampoNumero(prot, { prot = it }, stringResource(R.string.proteina), Modifier.weight(1f), decimales = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CampoNumero(carb, { carb = it }, stringResource(R.string.carbos), Modifier.weight(1f), decimales = true)
                CampoNumero(gras, { gras = it }, stringResource(R.string.grasa), Modifier.weight(1f), decimales = true)
            }
        }

        Text(stringResource(R.string.preparacion), style = MaterialTheme.typography.titleLarge, color = f.texto)
        OutlinedTextField(pasos, { pasos = it.take(4000) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.pasos_pista)) }, minLines = 4)
        OutlinedTextField(nota, { nota = it.take(400) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.nota)) })
        BotonRiso(stringResource(R.string.guardar), {
            val lista = pasos.lines().map { it.trim() }.filter { it.isNotEmpty() }
            // Al guardar en un idioma, los pasos van a ese idioma y se conserva el otro.
            vm.guardarReceta(Receta(
                id = r?.id ?: "", es = if (idioma == "en") r?.es?.ifBlank { nombre } ?: nombre else nombre.trim(),
                en = if (idioma == "en") nombre.trim() else r?.en ?: "",
                comidas = vm.comidas.filter { it in comidas }, porciones = (porciones.toIntOrNull() ?: 1).coerceIn(1, 50),
                minutos = minutos.toIntOrNull() ?: 0, ingredientes = ingredientes.toList(),
                pasosEs = if (idioma == "en") r?.pasosEs ?: emptyList() else lista,
                pasosEn = if (idioma == "en") lista else r?.pasosEn ?: emptyList(),
                porPorcion = if (ingredientes.isEmpty()) Nutrientes(numero(kcal) ?: 0.0, numero(prot) ?: 0.0, numero(carb) ?: 0.0, numero(gras) ?: 0.0) else null,
                nota = nota.trim(),
            ))
        }, Modifier.fillMaxWidth(), habilitado = nombre.isNotBlank())
    }
}

/** Los gramos de un ingrediente: guarda su propio texto, así se puede borrar y volver a escribir. */
@Composable
private fun CampoGramos(gramos: Double, idioma: String, onCambio: (Double) -> Unit) {
    var texto by remember { mutableStateOf(cifra(gramos, idioma)) }
    OutlinedTextField(
        texto, { t -> texto = t.filter { it.isDigit() || it == ',' || it == '.' }.take(6); numero(texto)?.let { onCambio(it.coerceIn(0.0, 99999.0)) } },
        Modifier.width(96.dp), singleLine = true, suffix = { Text("g") },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
    )
}

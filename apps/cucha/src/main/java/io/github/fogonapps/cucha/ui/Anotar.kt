package io.github.fogonapps.cucha.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fogonapps.cucha.CuchaViewModel
import io.github.fogonapps.cucha.Pantalla
import io.github.fogonapps.cucha.R
import io.github.fogonapps.cucha.datos.Anotado
import io.github.fogonapps.cucha.datos.Comible
import io.github.fogonapps.cucha.datos.Estado
import io.github.fogonapps.cucha.datos.Ingrediente
import io.github.fogonapps.cucha.datos.Nutrientes
import io.github.fogonapps.cucha.datos.Producto
import io.github.fogonapps.cucha.datos.suma
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.NumeroRiso
import io.github.fogonapps.identidad.aparecer
import kotlin.math.roundToInt

/** Buscar qué anotar: recetas, productos propios y la base de alimentos. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Anotar(vm: CuchaViewModel, e: Estado, p: Pantalla.Anotar, acciones: Acciones) {
    val f = FogonTema.actual
    val idioma = vm.idioma()
    // Lo ya anotado en esta comida: al sumar algo, la búsqueda vuelve aquí y empieza en blanco.
    val enLaComida = e.dias[p.fecha]?.anotados?.filter { it.comida == p.comida } ?: emptyList()
    var texto by rememberSaveable { mutableStateOf("") }
    var cuantosVi by rememberSaveable { mutableIntStateOf(enLaComida.size) }
    LaunchedEffect(enLaComida.size) { if (enLaComida.size != cuantosVi) { texto = ""; cuantosVi = enLaComida.size } }
    val resultados = remember(texto, e) { vm.L.buscar(e, texto, idioma) }
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { foco.requestFocus() }

    Column(Modifier.fillMaxSize()) {
        Cabecera(stringResource(R.string.anotar_en, nombreComida(p.comida)), vm::cerrar)
        OutlinedTextField(
            texto, { texto = it.take(40) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(foco),
            placeholder = { Text(stringResource(R.string.buscar_pista)) }, singleLine = true,
            leadingIcon = { IconoRiso(Iconos.Buscar, null, tamano = 20.dp, tinta = f.textoSuave) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), shape = io.github.fogonapps.identidad.FormaPildora,
        )
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (e.codigoBarras) Pildora(stringResource(R.string.escanear), Iconos.Codigo) { acciones.escanear(p.comida, p.fecha) }
            Pildora(stringResource(R.string.producto_nuevo), Iconos.Mas) { vm.abrir(Pantalla.EditarProducto(null, p.comida, p.fecha)) }
        }
        if (enLaComida.isNotEmpty()) LlevasEn(vm, p, enLaComida)
        if (texto.isBlank()) Pista(stringResource(if (resultados.isEmpty()) R.string.buscar_vacio else R.string.recientes))
        else if (resultados.isEmpty()) Pista(stringResource(R.string.buscar_nada, texto))
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(resultados, key = { it.ref }) { c ->
                FilaVidrio({ vm.abrir(Pantalla.Cantidad(c.ref, p.comida, p.fecha)) }, Modifier.animateItem()) {
                    Ficha(if (c is Comible.DeReceta) f.tinta2 else f.tinta1, iconoDe(c))
                    Column(Modifier.weight(1f)) {
                        Text(c.nombre(idioma), style = MaterialTheme.typography.bodyLarge, color = f.texto, maxLines = 2)
                        Text(resumen(vm, c), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1)
                    }
                    if (c.ref in e.favoritos) IconoRiso(Iconos.Estrella, null, tamano = 16.dp, tinta = f.acentoTexto)
                }
            }
            item { Espacio(40.dp) }
        }
    }
}

fun iconoDe(c: Comible) = when (c) {
    is Comible.DeReceta -> Iconos.Recetas
    is Comible.DeProducto -> Iconos.Codigo
    is Comible.DeBase -> Iconos.Plato
}

/** "130 kcal por 100 g" o "520 kcal la porción". */
@Composable
private fun resumen(vm: CuchaViewModel, c: Comible): String = when (c) {
    is Comible.DeReceta -> stringResource(R.string.kcal_porcion, vm.L.porPorcion(c.r).kcal.roundToInt())
    is Comible.DeProducto -> stringResource(R.string.kcal_100g, c.p.por100.kcal.roundToInt())
    is Comible.DeBase -> stringResource(R.string.kcal_100g, c.a.k.roundToInt())
}

/** Una forma de medir lo que se comió: un nombre y cuántos gramos (o porciones de receta) vale cada una. */
private data class Medida(val nombre: String, val valor: Double)

/** Cuánto se comió, en qué comida, y lo que eso aporta. También corrige o borra algo ya anotado. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Cantidad(vm: CuchaViewModel, e: Estado, p: Pantalla.Cantidad) {
    val f = FogonTema.actual
    val idioma = vm.idioma()
    val confirmar = LocalConfirmar.current
    val c = vm.L.comible(e, p.ref)
    if (c == null) { Cabecera(stringResource(R.string.anotar), vm::cerrar); Pista(stringResource(R.string.ya_no_esta)); return }
    val anterior = p.editar?.let { id -> e.dias[p.fecha]?.anotados?.firstOrNull { it.id == id } }

    val gramos = stringResource(R.string.gramos)
    val porcionTxt = stringResource(R.string.porciones)
    val medidas = when (c) {
        is Comible.DeBase -> c.a.porciones.map { Medida(it.nombre(idioma), it.gramos) } + Medida(gramos, 1.0)
        is Comible.DeProducto -> listOfNotNull(c.p.porcionGramos?.let { Medida(stringResource(R.string.porcion_de, cifra(it, idioma)), it) }) + Medida(gramos, 1.0)
        is Comible.DeReceta -> listOf(Medida(porcionTxt, 1.0))
    }
    // Al corregir se parte de lo anotado (en gramos o porciones); si no, de una porción casera.
    var medida by rememberSaveable { mutableStateOf(if (anterior != null) medidas.last().nombre else medidas.first().nombre) }
    val soloGramos = medidas.first().valor == 1.0 && c !is Comible.DeReceta
    var cuantas by rememberSaveable { mutableStateOf(anterior?.let { cifra(it.cantidad, idioma) } ?: if (soloGramos) "100" else "1") }
    var comida by rememberSaveable { mutableStateOf(anterior?.comida ?: p.comida) }
    val m = medidas.firstOrNull { it.nombre == medida } ?: medidas.first()
    val cantidad = (numero(cuantas) ?: 0.0) * m.valor
    val n = vm.valores(c, cantidad)
    val detalle = when {
        c is Comible.DeReceta -> "${cifra(cantidad, idioma)} $porcionTxt".lowercase()
        m.valor == 1.0 -> "${cifra(cantidad, idioma)} g"
        else -> "${cifra(numero(cuantas) ?: 0.0, idioma)} ${m.nombre.lowercase()} · ${cifra(cantidad, idioma)} g"
    }

    Cabecera(c.nombre(idioma), vm::cerrar) {
        val fav = c.ref in e.favoritos
        BotonIcono(Iconos.Estrella, stringResource(if (fav) R.string.quitar_favorito else R.string.favorito), grande = true) { vm.favorito(c.ref) }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Lo que aporta, grande y arriba.
        Column(Modifier.aparecer(0).fillMaxWidth().vidrio().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumeroRiso("${n.kcal.roundToInt()}", 44.sp)
                Text("kcal", style = MaterialTheme.typography.titleMedium, color = f.textoSuave, modifier = Modifier.padding(bottom = 6.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Dato(stringResource(R.string.proteina), "${cifra(n.proteina, idioma)} g", Modifier.weight(1f))
                Dato(stringResource(R.string.carbos), "${cifra(n.carbos, idioma)} g", Modifier.weight(1f))
                Dato(stringResource(R.string.grasa), "${cifra(n.grasa, idioma)} g", Modifier.weight(1f))
            }
        }
        if (medidas.size > 1) Selector(medidas.map { it.nombre to it.nombre }, m.nombre, { nuevo ->
            medida = nuevo
            cuantas = if (medidas.first { it.nombre == nuevo }.valor == 1.0 && c !is Comible.DeReceta) "100" else "1"
        })
        CampoNumero(cuantas, { cuantas = it }, if (m.valor == 1.0 && c !is Comible.DeReceta) gramos else stringResource(R.string.cuantas), Modifier.fillMaxWidth(), decimales = true)
        Text(stringResource(R.string.en_que_comida), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
        Selector(vm.comidas.map { it to nombreComidaTexto(it) }, comida, { comida = it })
        BotonRiso(stringResource(if (anterior != null) R.string.guardar else R.string.anotar), {
            vm.anotar(c, cantidad, detalle, comida, p.fecha, editar = anterior?.id)
        }, Modifier.fillMaxWidth(), habilitado = cantidad > 0)
        if (anterior != null) BotonSecundario(stringResource(R.string.borrar), {
            confirmar(vm.tx(R.string.borrar_pregunta, anterior.nombre), null) { vm.borrarAnotado(p.fecha, anterior.id); vm.cerrar() }
        }, Modifier.fillMaxWidth(), peligro = true)
        if (c is Comible.DeReceta) BotonSecundario(stringResource(R.string.ver_receta), { vm.abrir(Pantalla.VerReceta(c.r.id)) }, Modifier.fillMaxWidth())
        if (c is Comible.DeProducto) BotonSecundario(stringResource(R.string.corregir_producto), { vm.abrir(Pantalla.EditarProducto(c.p, comida, p.fecha)) }, Modifier.fillMaxWidth())
        Fuente(c)
    }
}

@Composable
private fun nombreComidaTexto(id: String) = nombreComida(id)

/** De dónde salen los valores: USDA (con el alimento exacto) u Open Food Facts (con su crédito). */
@Composable
fun Fuente(c: Comible) {
    val f = FogonTema.actual
    val uri = LocalUriHandler.current
    when (c) {
        is Comible.DeBase -> {
            Pista(stringResource(R.string.fuente_usda, c.a.usda, c.a.fdc.toString()), adentro = true)
            if (c.a.aprox) Pista(stringResource(R.string.fuente_aprox), adentro = true)
            Pista(stringResource(R.string.fuente_porciones), adentro = true)
        }
        is Comible.DeProducto -> if (c.p.fuente == "off") Text(
            stringResource(R.string.fuente_off), style = MaterialTheme.typography.bodySmall, color = f.acentoTexto,
            modifier = Modifier.clickable { uri.openUri("https://world.openfoodfacts.org/product/${c.p.codigo}") }.padding(vertical = 4.dp),
        )
        is Comible.DeReceta -> Pista(stringResource(R.string.fuente_receta), adentro = true)
    }
}

/** Un producto propio: escrito a mano o traído de Open Food Facts para revisarlo. Valores por 100 g. */
@Composable
fun EditarProducto(vm: CuchaViewModel, p: Pantalla.EditarProducto) {
    val f = FogonTema.actual
    val idioma = vm.idioma()
    val inicial = p.producto
    var nombre by rememberSaveable { mutableStateOf(inicial?.nombre ?: "") }
    fun txt(d: Double?) = d?.takeIf { it > 0 || inicial?.fuente == "off" }?.let { cifra(it, idioma) } ?: ""
    var kcal by rememberSaveable { mutableStateOf(txt(inicial?.por100?.kcal)) }
    var prot by rememberSaveable { mutableStateOf(txt(inicial?.por100?.proteina)) }
    var carb by rememberSaveable { mutableStateOf(txt(inicial?.por100?.carbos)) }
    var gras by rememberSaveable { mutableStateOf(txt(inicial?.por100?.grasa)) }
    var porcion by rememberSaveable { mutableStateOf(inicial?.porcionGramos?.let { cifra(it, idioma) } ?: "") }
    val uri = LocalUriHandler.current

    Cabecera(stringResource(if (inicial?.id.isNullOrEmpty()) R.string.producto_nuevo else R.string.corregir_producto), vm::cerrar)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        inicial?.codigo?.let { Pista(stringResource(R.string.codigo_n, it), adentro = true) }
        CampoTexto(nombre, { nombre = it }, stringResource(R.string.nombre), max = 60)
        Text(stringResource(R.string.por_100g), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CampoNumero(kcal, { kcal = it }, "kcal", Modifier.weight(1f), decimales = true)
            CampoNumero(prot, { prot = it }, stringResource(R.string.proteina), Modifier.weight(1f), decimales = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CampoNumero(carb, { carb = it }, stringResource(R.string.carbos), Modifier.weight(1f), decimales = true)
            CampoNumero(gras, { gras = it }, stringResource(R.string.grasa), Modifier.weight(1f), decimales = true)
        }
        CampoNumero(porcion, { porcion = it }, stringResource(R.string.porcion_gramos), Modifier.fillMaxWidth(), decimales = true)
        Pista(stringResource(R.string.producto_pista), adentro = true)
        BotonRiso(stringResource(R.string.guardar_y_anotar), {
            vm.guardarProducto(
                Producto(inicial?.id ?: "", nombre, Nutrientes(numero(kcal) ?: 0.0, numero(prot) ?: 0.0, numero(carb) ?: 0.0, numero(gras) ?: 0.0),
                    inicial?.codigo, numero(porcion)?.takeIf { it > 0 }, inicial?.fuente ?: "propio"),
                p.comida, p.fecha,
            )
        }, Modifier.fillMaxWidth(), habilitado = nombre.isNotBlank() && (numero(kcal) ?: -1.0) >= 0)
        if (inicial?.fuente == "off") Text(
            stringResource(R.string.fuente_off), style = MaterialTheme.typography.bodySmall, color = f.acentoTexto,
            modifier = Modifier.clickable { uri.openUri("https://world.openfoodfacts.org/product/${inicial.codigo}") }.padding(vertical = 4.dp),
        )
    }
}

/**
 * Lo que va en la comida mientras se anota (huevos, champiñones…), con el total, "Listo" para volver a Hoy y
 * "Guardar como receta" para repetirlo con un toque (si hay dos o más alimentos de la base).
 */
@Composable
private fun LlevasEn(vm: CuchaViewModel, p: Pantalla.Anotar, anotados: List<Anotado>) {
    val f = FogonTema.actual
    val idioma = vm.idioma()
    val total = anotados.map { it.n }.suma()
    val ingredientes = anotados.filter { it.ref.startsWith("a:") }.map { Ingrediente(it.ref.removePrefix("a:"), it.cantidad) }
    Column(
        Modifier.aparecer(0).fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp).vidrio().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.llevas_en, nombreComida(p.comida).lowercase()), style = MaterialTheme.typography.labelLarge, color = f.textoSuave, modifier = Modifier.weight(1f))
            Text("${total.kcal.roundToInt()} kcal", fontFamily = f.cifras, color = f.texto)
        }
        Text(anotados.joinToString(" · ") { it.nombre }, style = MaterialTheme.typography.bodyMedium, color = f.texto, maxLines = 3)
        Text(stringResource(R.string.agrega_otro), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pildora(stringResource(R.string.listo), Iconos.Chulo, acento = true) { vm.cerrar() }
            if (ingredientes.size >= 2) Pildora(stringResource(R.string.guardar_como_receta), Iconos.Recetas) {
                vm.abrir(Pantalla.EditarReceta(null, ingredientes, p.comida))
            }
        }
    }
}

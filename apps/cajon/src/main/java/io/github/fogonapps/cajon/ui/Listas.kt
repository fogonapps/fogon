package io.github.fogonapps.cajon.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import io.github.fogonapps.identidad.forma
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.Pantalla
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Estados
import io.github.fogonapps.cajon.datos.Lista
import io.github.fogonapps.cajon.datos.Obra
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.Dibujo
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.FormaPildora
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.aparecer
import io.github.fogonapps.identidad.rebote

/** Las obras de una lista, en su orden (sin las que se borraron). */
fun obrasDe(e: Estado, l: Lista): List<Obra> = l.obras.mapNotNull { id -> e.vivas.firstOrNull { it.id == id } }

@Composable
fun Listas(vm: CajonViewModel, e: Estado) {
    val f = FogonTema.actual
    Cabecera(stringResource(R.string.pestana_listas), vm::cerrar) {
        Pildora(stringResource(R.string.nueva), Iconos.Mas, acento = true) { vm.abrir(Pantalla.EditarLista(null)) }
    }
    // Con cuenta: las listas en común con amigos.
    if (vm.red.usuario.collectAsState().value != null) FilaVidrio({ vm.abrir(Pantalla.ListasComunes) }) {
        IconoFila(Iconos.Gente)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.listas_comunes), style = MaterialTheme.typography.bodyLarge, color = f.texto)
            Text(stringResource(R.string.listas_comunes_corta), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        }
        IconoRiso(Iconos.Siguiente, null, tamano = 18.dp, tinta = f.textoSuave)
    }
    val listas = e.listasVivas.sortedByDescending { it.cambio }
    if (listas.isEmpty()) VacioListas { vm.abrir(Pantalla.EditarLista(null)) }
    listas.forEachIndexed { i, l ->
        val obras = obrasDe(e, l)
        FilaVidrio({ vm.abrir(Pantalla.VerLista(l.id)) }, Modifier.aparecer(i)) {
            // Una pila de tres portadas, la elegida como portada de la lista delante.
            PilaPortadas(obras.sortedByDescending { it.id == l.portada }.take(3))
            Column(Modifier.weight(1f).padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(l.nombre, fontFamily = f.titulos, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp, color = f.texto,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(pluralStringResource(R.plurals.obras_n, obras.size, obras.size), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
            IconoRiso(Iconos.Siguiente, null, tamano = 18.dp, tinta = f.textoSuave)
        }
    }

    // Las de siempre: lo que le gustó y sus pendientes.
    Seccion(stringResource(R.string.listas_automaticas))
    val gustadas = e.vivas.filter { it.meGusta }
    FilaVidrio {
        IconoFila(Iconos.Corazon)
        Text(stringResource(R.string.lo_que_me_gusto), style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.weight(1f))
        Text("${gustadas.size}", style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
    }
    FilaVidrio({ vm.abrir(Pantalla.Diario) }) {
        IconoFila(Iconos.Diario)
        Text(stringResource(R.string.tu_diario), style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.weight(1f))
        Text("${e.vivas.sumOf { it.registros.size }}", style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
    }
    if (gustadas.isNotEmpty()) {
        Seccion(stringResource(R.string.lo_que_me_gusto))
        Cuadricula(gustadas.sortedByDescending { it.cambio }.take(12), vm)
    }
    val pendientes = e.vivas.count { it.estado == Estados.QUIERO }
    if (pendientes > 0) Pista(pluralStringResource(R.plurals.pendientes_n, pendientes, pendientes), Modifier.padding(top = 12.dp))
}

/** El ícono de una fila, en tinta y sin círculo, en el mismo ancho que una portada chica para que los textos alineen. */
@Composable
private fun IconoFila(icono: Dibujo, tinta: androidx.compose.ui.graphics.Color = FogonTema.actual.texto) {
    Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) { IconoRiso(icono, null, tamano = 22.dp, tinta = tinta) }
}

/**
 * Una lista en su fila: hasta tres portadas apiladas, la primera delante y las otras asomando detrás (como en el
 * Inicio, más chicas). Sin obras, el hueco de una portada con el ícono de lista.
 */
@Composable
private fun PilaPortadas(obras: List<Obra>) {
    val f = FogonTema.actual
    Box(Modifier.width(68.dp).height(74.dp)) {
        obras.reversed().forEachIndexed { i, o ->
            val atras = obras.size - 1 - i
            Portada(o.portada, o.tipo, o.titulo, Modifier.width(44.dp).align(Alignment.BottomStart).offset(x = (12 * atras).dp, y = (-4 * atras).dp)
                .graphicsLayer { alpha = 1f - 0.2f * atras }.shadow(if (atras == 0) 4.dp else 1.dp, forma(6.dp), clip = false), esquina = 6.dp, conTitulo = false)
        }
        if (obras.isEmpty()) Box(Modifier.size(44.dp, 66.dp).align(Alignment.BottomStart).clip(forma(6.dp)).background(f.superficie), contentAlignment = Alignment.Center) {
            IconoRiso(Iconos.Lista, null, tamano = 20.dp, tinta = f.textoSuave)
        }
    }
}

/** Sin listas todavía: el ícono, dos líneas y el botón para armar la primera. Sin tarjeta. */
@Composable
private fun VacioListas(nueva: () -> Unit) {
    val f = FogonTema.actual
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IconoRiso(Iconos.Lista, null, tamano = 40.dp, tinta = f.texto)
        Text(stringResource(R.string.listas_vacio_titulo), fontFamily = f.titulos, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = f.texto,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp).semantics { heading() })
        Text(stringResource(R.string.listas_vacio_texto), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave, textAlign = TextAlign.Center)
        BotonTinta(stringResource(R.string.nueva_lista), nueva, Modifier.padding(top = 8.dp))
    }
}

/** El botón principal del diseño limpio: una píldora en la tinta del texto con la letra del papel. */
@Composable
internal fun BotonTinta(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, habilitado: Boolean = true) =
    io.github.fogonapps.identidad.BotonRiso(texto, onClick, modifier, habilitado)

@Composable
fun VerLista(vm: CajonViewModel, e: Estado, id: String) {
    val f = FogonTema.actual
    val l = e.listasVivas.firstOrNull { it.id == id }
    if (l == null) { Cabecera("", vm::cerrar); Pista(stringResource(R.string.ya_no_esta)); return }
    val obras = obrasDe(e, l)
    val acciones = LocalAcciones.current
    var ordenando by rememberSaveable { mutableStateOf(false) }
    var compartiendo by rememberSaveable { mutableStateOf(false) }

    Cabecera(l.nombre, vm::cerrar) {
        if (acciones != null && obras.isNotEmpty()) BotonIcono(Iconos.Compartir, stringResource(R.string.compartir), grande = true) { compartiendo = true }
        BotonIcono(Iconos.Lapiz, stringResource(R.string.editar_lista), grande = true) { vm.abrir(Pantalla.EditarLista(l.id)) }
    }
    if (compartiendo && acciones != null) DialogoCompartir({ compartiendo = false }, { acciones.compartir(it, l.nombre) }) { TarjetaLista(l, obras) }
    if (l.descripcion.isNotBlank()) Pista(l.descripcion)
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(pluralStringResource(R.plurals.obras_n, obras.size, obras.size), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, modifier = Modifier.weight(1f))
        if (obras.size > 1) Pildora(stringResource(if (ordenando) R.string.listo else R.string.ordenar), Iconos.Ordenar) { ordenando = !ordenando }
    }
    if (obras.isEmpty()) { Pista(stringResource(R.string.lista_vacia)); return }
    if (ordenando) {
        obras.forEachIndexed { i, o ->
            FilaVidrio {
                Text("${i + 1}", fontFamily = f.titulos, fontWeight = FontWeight.SemiBold, color = f.textoSuave, modifier = Modifier.width(24.dp))
                Portada(o.portada, o.tipo, o.titulo, Modifier.width(36.dp), esquina = 6.dp, conTitulo = false)
                Text(o.titulo, style = MaterialTheme.typography.bodyMedium, color = f.texto, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                BotonIcono(Iconos.Arriba, stringResource(R.string.subir), activo = i > 0) { vm.moverEnLista(l.id, l.obras.indexOf(o.id), l.obras.indexOf(o.id) - 1) }
                BotonIcono(Iconos.Abajo, stringResource(R.string.bajar), activo = i < obras.lastIndex) { vm.moverEnLista(l.id, l.obras.indexOf(o.id), l.obras.indexOf(o.id) + 1) }
            }
        }
    } else Cuadricula(obras, vm, numerada = l.numerada)
}

@Composable
fun EditarLista(vm: CajonViewModel, e: Estado, id: String?) {
    val confirmar = LocalConfirmar.current
    val f = FogonTema.actual
    val l = id?.let { i -> e.listasVivas.firstOrNull { it.id == i } }
    var nombre by rememberSaveable { mutableStateOf(l?.nombre ?: "") }
    var descripcion by rememberSaveable { mutableStateOf(l?.descripcion ?: "") }
    var numerada by rememberSaveable { mutableStateOf(l?.numerada ?: false) }
    Cabecera(stringResource(if (l == null) R.string.nueva_lista else R.string.editar_lista), vm::cerrar)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CampoTexto(nombre, { nombre = it }, stringResource(R.string.nombre), max = 60)
        OutlinedTextField(descripcion, { descripcion = it.take(500) }, Modifier.fillMaxWidth(), minLines = 2, label = { Text(stringResource(R.string.descripcion)) })
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            FilaInterruptorCajon(stringResource(R.string.numerada), numerada, stringResource(R.string.numerada_pista)) { numerada = it }
        }
        if (l != null) {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.quien_ve_lista), style = MaterialTheme.typography.bodyLarge, color = f.texto)
                ElegirVisibilidad(l.visibilidad) { vm.visibilidadLista(l.id, it) }
            }
            val obras = obrasDe(e, l)
            if (obras.size > 1) {
                Text(stringResource(R.string.portada_lista), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(obras.size) { i ->
                        val o = obras[i]
                        val elegida = o.id == l.portada || (l.portada.isEmpty() && i == 0)
                        androidx.compose.foundation.layout.Box(Modifier.width(72.dp).clip(forma(8.dp))
                            .border(if (elegida) 3.dp else 0.dp, if (elegida) f.texto else androidx.compose.ui.graphics.Color.Transparent, forma(8.dp))
                            .clickable { vm.portadaLista(l.id, o.id) }) {
                            Portada(o.portada, o.tipo, o.titulo, Modifier.fillMaxWidth(), esquina = 8.dp, conTitulo = false)
                        }
                    }
                }
            }
        }
        BotonTinta(stringResource(R.string.guardar), { vm.guardarLista(l?.id, nombre, descripcion, numerada) }, Modifier.fillMaxWidth(), habilitado = nombre.isNotBlank())
        if (l != null) BotonSecundario(stringResource(R.string.borrar_lista), {
            confirmar(vm.tx(R.string.borrar_lista_pregunta, l.nombre), vm.tx(R.string.borrar_lista_texto)) { vm.borrarLista(l.id) }
        }, Modifier.fillMaxWidth(), peligro = true)
    }
}

/** Elegir en qué listas va una obra (se marcan y desmarcan). */
@Composable
fun AgregarALista(vm: CajonViewModel, e: Estado, obra: String) {
    val f = FogonTema.actual
    val o = e.vivas.firstOrNull { it.id == obra }
    Cabecera(stringResource(R.string.agregar_a_lista), vm::cerrar)
    if (o != null) Pista(o.titulo)
    val listas = e.listasVivas.sortedBy { it.nombre }
    if (listas.isEmpty()) Pista(stringResource(R.string.listas_vacio))
    listas.forEach { l ->
        val esta = obra in l.obras
        FilaVidrio({ vm.alternarEnLista(l.id, obra) }) {
            IconoFila(if (esta) Iconos.Chulo else Iconos.Lista, if (esta) f.texto else f.textoSuave)
            Text(l.nombre, style = MaterialTheme.typography.bodyLarge, color = f.texto, fontWeight = if (esta) FontWeight.SemiBold else null, modifier = Modifier.weight(1f))
            Text("${l.obras.size}", style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
        }
    }
    Column(Modifier.padding(16.dp)) {
        BotonSecundario(stringResource(R.string.nueva_lista), { vm.abrir(Pantalla.EditarLista(null)) }, Modifier.fillMaxWidth())
    }
    if (o != null) ListasComunesParaAgregar(vm, o)
}

/**
 * Todo el diario, por meses: el mes en la letra de los títulos y debajo sus registros, en una cuadrícula de portadas
 * con la nota de esa vez debajo (como viene) o en filas planas. Cómo se ve se recuerda en el teléfono.
 */
@Composable
fun Diario(vm: CajonViewModel, e: Estado) {
    val f = FogonTema.actual
    val idioma = vm.idioma()
    Cabecera(stringResource(R.string.tu_diario), vm::cerrar)
    val entradas = vm.L.diario(e)
    if (entradas.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IconoRiso(Iconos.Diario, null, tamano = 40.dp, tinta = f.texto)
            Text(stringResource(R.string.diario_vacio), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave, textAlign = TextAlign.Center)
        }
        return
    }
    var cuadricula by rememberSaveable { mutableStateOf(vm.diarioEnCuadricula) }
    Selector(listOf(true to stringResource(R.string.diario_cuadricula), false to stringResource(R.string.diario_lista)), cuadricula,
        { cuadricula = it; vm.diarioEnCuadricula = it }, Modifier.padding(horizontal = 20.dp))
    entradas.groupBy { it.registro.fecha.take(7) }.forEach { (mes, lista) ->
        val titulo = runCatching {
            java.time.YearMonth.parse(mes).format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.forLanguageTag(idioma))).replaceFirstChar { it.uppercase() }
        }.getOrDefault(mes)
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
            Text(titulo, fontFamily = f.titulos, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp, color = f.texto,
                modifier = Modifier.weight(1f).semantics { heading() })
            Text("${lista.size}", style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
        }
        if (cuadricula) CuadriculaDiario(vm, lista)
        else lista.forEach { d ->
            EntradaDiario(d.obra, d.registro,
                { vm.abrir(Pantalla.Ficha(d.obra.id)) }, { vm.escribirResena(d.obra.id, d.registro.id) })
        }
    }
}

/**
 * Un mes del diario en portadas, de a cuatro: debajo de cada una, la nota de esa vez (y si fue otra vez o te gustó).
 * Tocar abre la ficha; mantener apretado, la reseña de ese registro.
 */
@Composable
private fun CuadriculaDiario(vm: CajonViewModel, lista: List<io.github.fogonapps.cajon.datos.EntradaDiario>) {
    val f = FogonTema.actual
    val idioma = LocalConfiguration.current.locales[0].language
    val escribir = stringResource(R.string.resena_escribir)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        lista.chunked(4).forEach { fila ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                fila.forEach { d ->
                    val fecha = fechaCorta(d.registro.fecha, idioma)
                    Column(Modifier.weight(1f).clip(forma(8.dp))
                        .combinedClickable(onLongClickLabel = escribir, onLongClick = { vm.escribirResena(d.obra.id, d.registro.id) }) {
                            vm.abrir(Pantalla.Ficha(d.obra.id))
                        }.semantics { stateDescription = fecha },
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Portada(d.obra.portada, d.obra.tipo, d.obra.titulo, Modifier.fillMaxWidth(), esquina = 6.dp, conTitulo = false)
                        Row(Modifier.heightIn(min = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Estrellas(d.registro.nota, 11.sp)
                            if (d.registro.otraVez) IconoRiso(Iconos.Repetir, stringResource(R.string.otra_vez), tamano = 11.dp, tinta = f.textoSuave)
                            if (d.obra.meGusta) IconoRiso(Iconos.Corazon, stringResource(R.string.me_gusta), tamano = 11.dp, tinta = f.texto, relleno = f.texto)
                        }
                    }
                }
                repeat(4 - fila.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** El mismo registro se dibuja en el diario y en los ejemplos del tutorial. */
@Composable
fun EntradaDiario(o: Obra, registro: io.github.fogonapps.cajon.datos.Registro, abrir: (() -> Unit)? = null, escribir: (() -> Unit)? = null) {
    val f = FogonTema.actual
    val idioma = LocalConfiguration.current.locales[0].language
    FilaVidrio(abrir) {
        Portada(o.portada, o.tipo, o.titulo, Modifier.width(40.dp), esquina = 5.dp, conTitulo = false)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(o.titulo, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(fechaCorta(registro.fecha, idioma), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                Estrellas(registro.nota, 12.sp)
                if (registro.otraVez) IconoRiso(Iconos.Repetir, stringResource(R.string.otra_vez), tamano = 14.dp, tinta = f.textoSuave)
            }
        }
        if (o.meGusta) IconoRiso(Iconos.Corazon, stringResource(R.string.me_gusta), tamano = 18.dp, tinta = f.texto, relleno = f.texto)
        if (escribir != null) BotonIcono(Iconos.Lapiz, stringResource(R.string.resena_escribir), onClick = escribir)
    }
}

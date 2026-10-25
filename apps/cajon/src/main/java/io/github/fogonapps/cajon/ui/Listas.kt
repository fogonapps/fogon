package io.github.fogonapps.cajon.ui

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
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.aparecer

/** Las obras de una lista, en su orden (sin las que se borraron). */
fun obrasDe(e: Estado, l: Lista): List<Obra> = l.obras.mapNotNull { id -> e.vivas.firstOrNull { it.id == id } }

@Composable
fun Listas(vm: CajonViewModel, e: Estado) {
    val f = FogonTema.actual
    Seccion(stringResource(R.string.pestana_listas), Modifier.padding(top = 12.dp)) {
        Pildora(stringResource(R.string.nueva), Iconos.Mas, acento = true) { vm.abrir(Pantalla.EditarLista(null)) }
    }
    val listas = e.listasVivas.sortedByDescending { it.cambio }
    if (listas.isEmpty()) Pista(stringResource(R.string.listas_vacio))
    listas.forEachIndexed { i, l ->
        val obras = obrasDe(e, l)
        FilaVidrio({ vm.abrir(Pantalla.VerLista(l.id)) }, Modifier.aparecer(i)) {
            // Las tres primeras portadas, montadas.
            Row(horizontalArrangement = Arrangement.spacedBy((-14).dp)) {
                obras.take(3).forEach { Portada(it.portada, it.tipo, it.titulo, Modifier.width(40.dp), esquina = 6.dp) }
                if (obras.isEmpty()) Ficha(f.tinta1, Iconos.Lista)
            }
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(l.nombre, style = MaterialTheme.typography.bodyLarge, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(pluralStringResource(R.plurals.obras_n, obras.size, obras.size), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
            IconoRiso(Iconos.Siguiente, null, tamano = 18.dp, tinta = f.textoSuave)
        }
    }

    // Las de siempre: lo que le gustó y sus pendientes.
    Seccion(stringResource(R.string.listas_automaticas))
    val gustadas = e.vivas.filter { it.meGusta }
    FilaVidrio({ vm.irA(io.github.fogonapps.cajon.Pestana.CAJON) }) {
        Ficha(f.tinta1, Iconos.Corazon)
        Text(stringResource(R.string.lo_que_me_gusto), style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.weight(1f))
        Text("${gustadas.size}", fontFamily = f.cifras, color = f.textoSuave)
    }
    FilaVidrio({ vm.abrir(Pantalla.Diario) }) {
        Ficha(f.tinta2, Iconos.Diario)
        Text(stringResource(R.string.tu_diario), style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.weight(1f))
        Text("${e.vivas.sumOf { it.registros.size }}", fontFamily = f.cifras, color = f.textoSuave)
    }
    if (gustadas.isNotEmpty()) {
        Seccion(stringResource(R.string.lo_que_me_gusto))
        Cuadricula(gustadas.sortedByDescending { it.cambio }.take(12), vm)
    }
    val pendientes = e.vivas.count { it.estado == Estados.QUIERO }
    if (pendientes > 0) Pista(pluralStringResource(R.plurals.pendientes_n, pendientes, pendientes), Modifier.padding(top = 12.dp))
}

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
                Text("${i + 1}", fontFamily = f.cifras, color = f.textoSuave, modifier = Modifier.width(24.dp))
                Portada(o.portada, o.tipo, o.titulo, Modifier.width(36.dp), esquina = 6.dp)
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
        Row(Modifier.fillMaxWidth().vidrio().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.numerada), style = MaterialTheme.typography.bodyLarge, color = f.texto)
                Text(stringResource(R.string.numerada_pista), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
            Switch(numerada, { numerada = it })
        }
        BotonRiso(stringResource(R.string.guardar), { vm.guardarLista(l?.id, nombre, descripcion, numerada) }, Modifier.fillMaxWidth(), habilitado = nombre.isNotBlank())
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
            Ficha(if (esta) f.tinta1 else f.linea, if (esta) Iconos.Chulo else Iconos.Lista)
            Text(l.nombre, style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.weight(1f))
            Text("${l.obras.size}", fontFamily = f.cifras, color = f.textoSuave)
        }
    }
    Column(Modifier.padding(16.dp)) {
        BotonSecundario(stringResource(R.string.nueva_lista), { vm.abrir(Pantalla.EditarLista(null)) }, Modifier.fillMaxWidth())
    }
}

/** Todo el diario, por meses. */
@Composable
fun Diario(vm: CajonViewModel, e: Estado) {
    val f = FogonTema.actual
    val idioma = vm.idioma()
    Cabecera(stringResource(R.string.tu_diario), vm::cerrar)
    val entradas = vm.L.diario(e)
    if (entradas.isEmpty()) { Pista(stringResource(R.string.diario_vacio)); return }
    entradas.groupBy { it.registro.fecha.take(7) }.forEach { (mes, lista) ->
        Seccion(runCatching {
            java.time.YearMonth.parse(mes).format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.forLanguageTag(idioma))).replaceFirstChar { it.uppercase() }
        }.getOrDefault(mes))
        lista.forEach { d ->
            FilaVidrio({ vm.abrir(Pantalla.Ficha(d.obra.id)) }) {
                Text(d.registro.fecha.takeLast(2), fontFamily = f.cifras, color = f.textoSuave, modifier = Modifier.width(26.dp))
                Portada(d.obra.portada, d.obra.tipo, d.obra.titulo, Modifier.width(38.dp), esquina = 6.dp)
                Column(Modifier.weight(1f)) {
                    Text(d.obra.titulo, style = MaterialTheme.typography.bodyLarge, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(nombreTipo(d.obra.tipo, plural = false), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                        Estrellas(d.registro.nota)
                        if (d.registro.otraVez) IconoRiso(Iconos.Repetir, stringResource(R.string.otra_vez), tamano = 14.dp, tinta = f.textoSuave)
                    }
                }
                if (d.obra.meGusta) IconoRiso(Iconos.Corazon, null, tamano = 16.dp, tinta = f.acentoTexto, relleno = f.acentoTexto)
            }
        }
    }
}

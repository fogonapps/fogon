package io.github.fogonapps.cajon.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow
import io.github.fogonapps.identidad.tarjetaFogon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.Pantalla
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.ClienteSocial
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Obra
import io.github.fogonapps.cajon.datos.Resena
import io.github.fogonapps.cajon.datos.Resenas
import io.github.fogonapps.cajon.datos.Tipos
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FogonTema

/** Un borrador persistido por perfil, independiente de lo que ya está publicado. */
@Composable
fun EscribirResena(vm: CajonViewModel, e: Estado, id: String, elegirObra: Boolean) {
    val f = FogonTema.actual
    val r = e.borradoresResena.firstOrNull { it.id == id && !it.borrada }
    Cabecera(stringResource(R.string.resena_escribir), vm::cerrar)
    if (r == null) return
    val o = Resenas.obra(e, r.obra)
    val subiendo by vm.red.subiendoFotoResena.collectAsStateWithLifecycle()
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    val acciones = LocalAcciones.current
    val confirmar = LocalConfirmar.current
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.resena_borrador), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        if (elegirObra) BotonSecundario(o?.titulo ?: stringResource(R.string.resena_elegir_obra), {
            vm.abrir(Pantalla.Buscar(io.github.fogonapps.cajon.Secciones.TODO, elegirPara = "resena:$id"))
        }, Modifier.fillMaxWidth()) else Text(o?.titulo.orEmpty(), style = MaterialTheme.typography.titleMedium, color = f.texto)
        if (o != null) PartesResena(vm, o, r) { vm.borradorResena(r.copy(parte = it)) }
        if (r.registro != null) Text(fechaCorta(r.fecha, vm.idioma()), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        OutlinedTextField(r.texto, { vm.borradorResena(r.copy(texto = it)) }, Modifier.fillMaxWidth(), minLines = 5,
            label = { Text(stringResource(R.string.resena_pista)) }, isError = r.texto.length > 5000,
            supportingText = { Text(stringResource(R.string.resena_limite, r.texto.length)) })
        FilaResena(stringResource(R.string.resena_con_nota), r.nota != null) {
            vm.borradorResena(r.copy(nota = if (it) 0 else null))
        }
        r.nota?.let { ElegirEstrellas(it, { n -> vm.borradorResena(r.copy(nota = n)) }, descripcion = stringResource(R.string.tu_nota), ceroCalificado = true) }
        FilaResena(stringResource(R.string.red_tiene_spoilers), r.spoiler) { vm.borradorResena(r.copy(spoiler = it)) }
        r.fotos.forEachIndexed { i, foto ->
            coil3.compose.AsyncImage(ClienteSocial.imagen(foto), stringResource(R.string.resena_foto, i + 1), Modifier.fillMaxWidth().height(180.dp))
            BotonSecundario(stringResource(R.string.resena_quitar_foto, i + 1), { vm.borradorResena(r.copy(fotos = r.fotos.filterIndexed { indice, _ -> indice != i })) }, Modifier.fillMaxWidth())
        }
        if (subiendo) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (acciones != null && r.fotos.size < 4) BotonSecundario(stringResource(R.string.red_agregar_foto), {
            acciones.elegirImagen { uri -> vm.red.subirFotoResena(uri) { foto -> vm.fotoResena(id, foto) } }
        }, Modifier.fillMaxWidth(), habilitado = !subiendo && usuario != null)
        ElegirVisibilidad(r.visibilidad) { vm.borradorResena(r.copy(visibilidad = it)) }
        BotonRiso(stringResource(R.string.red_publicar), { vm.guardarResena(r) }, Modifier.fillMaxWidth(),
            habilitado = !subiendo && Resenas.valida(r) && o != null && Resenas.parteValida(o, r.parte))
        if (e.resenas.any { it.id == id && !it.borrada }) BotonSecundario(stringResource(R.string.borrar), {
            confirmar(vm.tx(R.string.resena_borrar_pregunta), null) { vm.borrarResena(id); vm.cerrar() }
        }, Modifier.fillMaxWidth(), peligro = true)
    }
}

@Composable
private fun FilaResena(texto: String, activo: Boolean, cambiar: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(texto, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = FogonTema.actual.texto)
        Switch(activo, cambiar, modifier = Modifier.resenaInterruptor(texto))
    }
}

@Composable
private fun PartesResena(vm: CajonViewModel, o: Obra, r: Resena, cambiar: (String) -> Unit) {
    val partes = Resenas.partes(o)
    // Dos selectores evitan cientos de píldoras para una serie larga.
    if (Tipos.porEpisodios(o.tipo)) {
        val temporada = if (r.parte.startsWith("ep:")) r.parte.substringAfter('S').substringBefore('E').toIntOrNull()
            else r.parte.removePrefix("temp:").toIntOrNull()
        SelectorResena(listOf("" to stringResource(R.string.resena_obra_entera)) + o.temporadas.map { it.numero.toString() to
            stringResource(R.string.resena_temporada, it.numero) }, temporada?.toString().orEmpty(), {
            cambiar(if (it.isEmpty()) "" else "temp:$it")
        })
        o.temporadas.firstOrNull { it.numero == temporada }?.let { t ->
            SelectorResena(listOf("temp:${t.numero}" to stringResource(R.string.resena_temporada_entera)) + (1..t.episodios).map {
                "ep:S${t.numero}E$it" to Resenas.etiqueta("ep:S${t.numero}E$it", o, vm.idioma() == "en")
            }, r.parte, cambiar)
        }
    } else if (o.tipo == Tipos.LIBRO) {
        val tipo = r.parte.substringBefore(':').takeIf { it in listOf("cap", "pag") }.orEmpty()
        SelectorResena(listOf("" to stringResource(R.string.resena_obra_entera), "cap" to stringResource(R.string.resena_capitulo),
            "pag" to stringResource(R.string.resena_pagina)), tipo, { cambiar(if (it.isBlank()) "" else "$it:") })
        if (tipo.isNotEmpty()) OutlinedTextField(r.parte.substringAfter(':'), { cambiar("$tipo:${it.take(40)}") }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(if (tipo == "cap") R.string.resena_capitulo else R.string.resena_pagina)) }, singleLine = true)
    } else if (o.tipo == Tipos.ALBUM && partes.size > 1) {
        SelectorResena(partes.map { it to if (it.isBlank()) stringResource(R.string.resena_obra_entera) else Resenas.etiqueta(it, o, vm.idioma() == "en") }, r.parte, cambiar)
    }
}

@Composable
fun ResenasPropias(vm: CajonViewModel, e: Estado, o: Obra) {
    val f = FogonTema.actual
    val propias = e.resenas.filter { it.obra == o.id && !it.borrada }.sortedByDescending { it.fecha }
    val confirmar = LocalConfirmar.current
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (propias.isNotEmpty()) Text(stringResource(R.string.resena_tuyas), style = MaterialTheme.typography.titleSmall, color = f.texto)
        propias.forEach { r ->
            Text(listOf(fechaCorta(r.fecha, vm.idioma()), Resenas.etiqueta(r.parte, o, vm.idioma() == "en")).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            r.nota?.let { NotaResena(it) }
            var destapada by rememberSaveable(r.id) { mutableStateOf(false) }
            if (r.spoiler && !destapada) BotonSecundario(stringResource(R.string.red_spoiler_tocar), { destapada = true }, Modifier.fillMaxWidth())
            else {
                Text(r.texto, style = MaterialTheme.typography.bodyLarge, color = f.texto)
                r.fotos.forEach { coil3.compose.AsyncImage(ClienteSocial.imagen(it), null, Modifier.fillMaxWidth().height(160.dp)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pildora(stringResource(R.string.editar)) { vm.escribirResena(o.id, editar = r.id) }
                Pildora(stringResource(R.string.borrar)) {
                    confirmar(vm.tx(R.string.resena_borrar_pregunta), null) { vm.borrarResena(r.id) }
                }
            }
        }
        BotonSecundario(stringResource(R.string.resena_escribir), { vm.escribirResena(o.id) }, Modifier.fillMaxWidth())
    }
}

@Composable
fun ResenasDeLaGente(vm: CajonViewModel, o: Obra) {
    if (o.fuente.isBlank()) return
    val listas by vm.red.listas.collectAsStateWithLifecycle()
    val cargando by vm.red.cargandoResenas.collectAsStateWithLifecycle()
    val fin by vm.red.finResenas.collectAsStateWithLifecycle()
    val yo by vm.red.usuario.collectAsStateWithLifecycle()
    var orden by rememberSaveable(o.id) { mutableStateOf("utiles") }
    var parte by rememberSaveable(o.id) { mutableStateOf("") }
    val clave = vm.red.claveResenas(o.fuente, parte, orden)
    LaunchedEffect(o.fuente, parte, orden) { vm.red.cargarResenas(o.fuente, parte, orden) }
    val todas = listas[clave].orEmpty()
    val opciones = (listas[vm.red.claveResenas(o.fuente, "", orden)].orEmpty() + todas).mapNotNull {
        (it.datos["parte"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { p -> p.isNotBlank() }
    }.distinct()
    Seccion(stringResource(R.string.resenas_de_la_gente))
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectorResena(listOf("utiles" to stringResource(R.string.resena_mas_utiles), "recientes" to stringResource(R.string.resena_recientes)), orden, { orden = it })
        if (opciones.isNotEmpty()) SelectorResena(listOf("" to stringResource(R.string.resena_todas_partes)) + opciones.map {
            it to Resenas.etiqueta(it, o, vm.idioma() == "en")
        }, parte, { parte = it })
    }
    todas.filter { it.autor.usuario != yo }.forEach { TarjetaPublicacion(vm, it) }
    if (clave in cargando) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
    else if (clave !in fin) BotonSecundario(stringResource(R.string.resena_cargar_mas), {
        vm.red.cargarResenas(o.fuente, parte, orden, mas = true)
    }, Modifier.fillMaxWidth().padding(16.dp))
}

private fun Modifier.resenaInterruptor(nombre: String): Modifier = this.then(Modifier.semantics { contentDescription = nombre })


@Composable
internal fun NotaResena(nota: Int) {
    val nombre = stringResource(R.string.resena_nota_valor, nota)
    Column(Modifier.clearAndSetSemantics { contentDescription = nombre }) {
        if (nota == 0) Text(stringResource(R.string.resena_cero), color = FogonTema.actual.texto) else Estrellas(nota, 18.sp)
    }
}


/** Mantiene corta la pantalla incluso con cientos de episodios o canciones. */
@Composable
private fun SelectorResena(opciones: List<Pair<String, String>>, elegido: String, elegir: (String) -> Unit) {
    if (opciones.size <= 8) { Selector(opciones, elegido, elegir); return }
    var abierto by rememberSaveable { mutableStateOf(false) }
    BotonSecundario(opciones.firstOrNull { it.first == elegido }?.second.orEmpty(), { abierto = true }, Modifier.fillMaxWidth())
    if (abierto) AlertDialog(onDismissRequest = { abierto = false },
        title = { Text(stringResource(R.string.resena_parte)) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                items(opciones, key = { it.first }) { (valor, nombre) ->
                    Text(nombre, Modifier.fillMaxWidth().selectable(valor == elegido, role = Role.RadioButton) {
                        elegir(valor); abierto = false
                    }.padding(16.dp))
                }
            }
        }, confirmButton = { TextButton({ abierto = false }) { Text(stringResource(R.string.cancelar)) } })
}

/** Reseñas locales, incluidas las privadas, sin mezclar publicaciones de la red. */
@Composable
internal fun ResenasDelPerfil(vm: CajonViewModel, e: Estado) {
    val resenas = e.resenasDelPerfil()
    val f = FogonTema.actual
    if (resenas.isEmpty()) { NadaAqui(); return }
    resenas.forEach { r ->
        val o = Resenas.obra(e, r.obra)
        var destapada by rememberSaveable(r.id, r.spoiler) { mutableStateOf(false) }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).tarjetaFogon()
            .clickable {
                if (e.artistasVivos.any { it.id == r.obra }) vm.abrir(Pantalla.VerArtista(r.obra))
                else vm.abrir(Pantalla.Ficha(r.obra))
            }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Portada(o?.portada.orEmpty(), o?.tipo.orEmpty(), o?.titulo.orEmpty(), Modifier.width(64.dp), conTitulo = false)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(o?.titulo ?: stringResource(R.string.resena_obra_entera), style = MaterialTheme.typography.titleMedium,
                        color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val parte = Resenas.etiqueta(r.parte, o, vm.idioma() == "en")
                    if (parte.isNotBlank()) Text(parte, style = MaterialTheme.typography.labelMedium, color = f.textoSuave)
                    r.nota?.let { NotaResena(it) }
                    Text(nombreVisibilidad(r.visibilidad), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                }
            }
            if (r.spoiler && !destapada) BotonSecundario(stringResource(R.string.red_spoiler_tocar), { destapada = true }, Modifier.fillMaxWidth())
            else Text(r.texto, style = MaterialTheme.typography.bodyLarge, color = f.texto, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

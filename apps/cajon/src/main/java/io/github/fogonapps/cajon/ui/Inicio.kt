package io.github.fogonapps.cajon.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.Pantalla
import io.github.fogonapps.cajon.Pestana
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Estados
import io.github.fogonapps.cajon.datos.Obra
import io.github.fogonapps.cajon.datos.Tipos
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.EstadoVacio
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.NumeroRiso
import io.github.fogonapps.identidad.Personaje
import io.github.fogonapps.identidad.aparecer
import io.github.fogonapps.identidad.forma
import io.github.fogonapps.identidad.haloPuntos
import io.github.fogonapps.identidad.R as RId

@Composable
fun Inicio(vm: CajonViewModel, e: Estado) {
    val f = FogonTema.actual
    val anio = vm.hoy().year
    val c = vm.L.cifras(e, anio)

    // Arriba: el saludo y los ajustes.
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (e.ajustes.nombre.isBlank()) stringResource(R.string.hola) else stringResource(R.string.hola_nombre, e.ajustes.nombre),
                style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
            Text(stringResource(R.string.app_name), fontFamily = f.titulos, fontSize = 26.sp, color = f.texto)
        }
        BotonIcono(Iconos.Persona, stringResource(R.string.ajustes), grande = true) { vm.abrir(Pantalla.Ajustes) }
    }

    if (e.vivas.isEmpty()) {
        EstadoVacio(RId.drawable.personaje_el_cajon, stringResource(R.string.vacio_titulo), stringResource(R.string.vacio_texto))
        Column(Modifier.padding(horizontal = 24.dp)) {
            BotonRiso(stringResource(R.string.agregar_lo_primero), { vm.abrir(Pantalla.Buscar(io.github.fogonapps.cajon.Secciones.TODO)) }, Modifier.fillMaxWidth())
        }
        return
    }

    // La tarjeta grande: lo terminado este año, en puntos sobre su halo, con el personaje.
    val total = c.porTipo.values.sum()
    Box(Modifier.aparecer(0).fillMaxWidth().padding(16.dp).vidrio(forma(28.dp)).haloPuntos(f.tinta2.copy(alpha = 0.35f), centro = androidx.compose.ui.geometry.Offset(0.25f, 0.45f))
        .clickable { vm.abrir(Pantalla.Cifras) }) {
        Column(Modifier.padding(20.dp).fillMaxWidth(0.62f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.este_anio, anio), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
            NumeroRiso("$total", 56.sp)
            Text(pluralStringResource(R.plurals.terminadas, total, total), style = MaterialTheme.typography.bodyMedium, color = f.texto)
            // Cuántas de cada tipo, en una línea de íconos.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Tipos.TODOS.filter { (c.porTipo[it] ?: 0) > 0 }.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        IconoRiso(Iconos.de(t), null, tamano = 16.dp, tinta = f.textoSuave)
                        Text("${c.porTipo[t]}", fontFamily = f.cifras, fontSize = 14.sp, color = f.texto)
                    }
                }
            }
        }
        Personaje(RId.drawable.personaje_el_cajon, null, Modifier.align(Alignment.BottomEnd).padding(8.dp), tamano = 120.dp)
    }

    // Todo lo del cajón, desde siempre: cada cifra lleva a su apartado.
    Seccion(stringResource(R.string.todo_tu_cajon))
    val terminadas = e.vivas.filter { it.estado == Estados.TERMINADO }
    val cifras = listOf(
        Triple(Tipos.PELICULA, terminadas.count { it.tipo == Tipos.PELICULA }, R.plurals.peliculas_vistas),
        Triple(Tipos.SERIE, terminadas.count { it.tipo == Tipos.SERIE }, R.plurals.series_vistas),
        Triple(Tipos.ANIME, terminadas.count { it.tipo == Tipos.ANIME }, R.plurals.animes_vistos),
        Triple(Tipos.ALBUM, terminadas.count { it.tipo == Tipos.ALBUM }, R.plurals.albumes_escuchados),
        Triple(CajonViewModel.TIPO_ARTISTA, e.musicosVivos.size, R.plurals.artistas_guardados),
        Triple(Tipos.LIBRO, terminadas.count { it.tipo == Tipos.LIBRO }, R.plurals.libros_leidos),
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cifras.chunked(3).forEachIndexed { fila, grupo ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                grupo.forEachIndexed { k, (t, n, etiqueta) ->
                    Column(Modifier.weight(1f).aparecer(fila * 3 + k + 1).vidrio().clickable {
                        vm.irA(when (t) { Tipos.LIBRO -> Pestana.LIBROS; Tipos.ALBUM, CajonViewModel.TIPO_ARTISTA -> Pestana.MUSICA; else -> Pestana.CINE })
                    }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconoRiso(Iconos.de(t), null, tamano = 20.dp, tinta = f.acentoTexto)
                        Text("$n", fontFamily = f.cifras, fontSize = 24.sp, color = f.texto)
                        Text(pluralStringResource(etiqueta, n), style = MaterialTheme.typography.labelSmall, color = f.textoSuave, maxLines = 2)
                    }
                }
            }
        }
    }

    // Lo que está viendo, leyendo o escuchando.
    val enCurso = e.vivas.filter { it.estado == Estados.EN_CURSO }.sortedByDescending { it.cambio }
    if (enCurso.isNotEmpty()) {
        Seccion(stringResource(R.string.en_curso))
        Carrusel(enCurso, vm, conProgreso = true)
    }

    // El diario: lo último.
    val diario = vm.L.diario(e).take(5)
    if (diario.isNotEmpty()) {
        Seccion(stringResource(R.string.tu_diario)) { Pildora(stringResource(R.string.ver_todo)) { vm.abrir(Pantalla.Diario) } }
        diario.forEachIndexed { i, d ->
            FilaVidrio({ vm.abrir(Pantalla.Ficha(d.obra.id)) }, Modifier.aparecer(i + 1)) {
                Portada(d.obra.portada, d.obra.tipo, d.obra.titulo, Modifier.width(40.dp), esquina = 6.dp, conTitulo = false)
                Column(Modifier.weight(1f)) {
                    Text(d.obra.titulo, style = MaterialTheme.typography.bodyLarge, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(fechaCorta(d.registro.fecha, vm.idioma()), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                        Estrellas(d.registro.nota.takeIf { it > 0 } ?: d.obra.nota, 12.sp)
                        if (d.registro.otraVez) IconoRiso(Iconos.Repetir, stringResource(R.string.otra_vez), tamano = 14.dp, tinta = f.textoSuave)
                    }
                }
                if (d.obra.meGusta) IconoRiso(Iconos.Corazon, stringResource(R.string.me_gusta), tamano = 18.dp, tinta = f.acentoTexto, relleno = f.acentoTexto)
            }
        }
    }

    // Los pendientes más recientes.
    val pendientes = e.vivas.filter { it.estado == Estados.QUIERO }.sortedByDescending { it.agregada }
    if (pendientes.isNotEmpty()) {
        Seccion(stringResource(R.string.pendientes))
        Carrusel(pendientes.take(12), vm, conProgreso = false)
    }

    // Sus listas.
    val listas = e.listasVivas.sortedByDescending { it.cambio }
    Seccion(stringResource(R.string.pestana_listas)) { Pildora(stringResource(if (listas.isEmpty()) R.string.nueva else R.string.ver_todo)) { vm.abrir(if (listas.isEmpty()) Pantalla.EditarLista(null) else Pantalla.Listas) } }
    if (listas.isEmpty()) Pista(stringResource(R.string.listas_vacio))
    else LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(listas, key = { it.id }) { l ->
            val obras = obrasDe(e, l)
            Column(Modifier.width(150.dp).vidrio().clickable { vm.abrir(Pantalla.VerLista(l.id)) }.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy((-16).dp)) {
                    obras.take(3).forEach { Portada(it.portada, it.tipo, it.titulo, Modifier.width(48.dp), esquina = 6.dp, conTitulo = false) }
                    if (obras.isEmpty()) Ficha(f.tinta1, Iconos.Lista)
                }
                Text(l.nombre, style = MaterialTheme.typography.labelLarge, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(pluralStringResource(R.plurals.obras_n, obras.size, obras.size), style = MaterialTheme.typography.labelSmall, color = f.textoSuave)
            }
        }
    }

    // Las cifras completas y el diario.
    Seccion(stringResource(R.string.mas))
    FilaVidrio({ vm.abrir(Pantalla.Cifras) }) {
        Ficha(f.tinta1, Iconos.Cifras)
        Text(stringResource(R.string.tus_cifras), style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.weight(1f))
        IconoRiso(Iconos.Siguiente, null, tamano = 18.dp, tinta = f.textoSuave)
    }
    FilaVidrio({ vm.abrir(Pantalla.Diario) }) {
        Ficha(f.tinta2, Iconos.Diario)
        Text(stringResource(R.string.tu_diario), style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.weight(1f))
        IconoRiso(Iconos.Siguiente, null, tamano = 18.dp, tinta = f.textoSuave)
    }

    // Los retos del año, si tiene.
    if (e.ajustes.retos.isNotEmpty()) {
        Seccion(stringResource(R.string.retos_del_anio, anio)) { Pildora(stringResource(R.string.cambiar)) { vm.abrir(Pantalla.Retos) } }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).vidrio().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            e.ajustes.retos.forEach { (t, meta) ->
                val lleva = c.porTipo[t] ?: 0
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconoRiso(Iconos.de(t), null, tamano = 18.dp, tinta = f.textoSuave)
                        Text(nombreTipo(t), style = MaterialTheme.typography.bodyMedium, color = f.texto, modifier = Modifier.weight(1f).padding(start = 8.dp))
                        Text("$lleva / $meta", fontFamily = f.cifras, color = f.texto)
                    }
                    Barra(lleva.toFloat() / meta.coerceAtLeast(1), f.tinta1)
                }
            }
        }
    }
}

/** Una fila de portadas que se desliza; con [conProgreso], cada una lleva su barra y lo que sigue. */
@Composable
fun Carrusel(obras: List<Obra>, vm: CajonViewModel, conProgreso: Boolean) {
    val f = FogonTema.actual
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(obras, key = { it.id }) { o ->
            Column(Modifier.width(112.dp).clip(forma(12.dp)).clickable { vm.abrir(Pantalla.Ficha(o.id)) }.animateItem(),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Portada(o.portada, o.tipo, o.titulo, Modifier.fillMaxWidth(), conTitulo = false)
                Text(o.titulo, style = MaterialTheme.typography.labelLarge, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (conProgreso) {
                    vm.L.progreso(o)?.let { Barra(it, f.tinta1) }
                    val sigue = if (o.tipo == Tipos.LIBRO && o.pagina > 0) stringResource(R.string.va_en_pagina, o.pagina) else null
                    if (sigue != null) Text(sigue, style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1)
                } else Text(nombreTipo(o.tipo, plural = false), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1)
            }
        }
    }
}

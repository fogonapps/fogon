package io.github.fogonapps.cajon.ui

import io.github.fogonapps.cajon.datos.Personas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.Pantalla
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.Secciones
import io.github.fogonapps.cajon.datos.Artista
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Estados
import io.github.fogonapps.cajon.datos.Obra
import io.github.fogonapps.cajon.datos.Orden
import io.github.fogonapps.cajon.datos.Tipos
import io.github.fogonapps.cajon.datos.normal
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.FormaPildora
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.NumeroRiso
import io.github.fogonapps.identidad.aparecer
import io.github.fogonapps.identidad.forma
import io.github.fogonapps.identidad.haloPuntos

/** Un apartado de la app: Cine, Música o Libros, con sus cifras, lo que está en curso y su cajón. */
@Composable
fun Apartado(vm: CajonViewModel, e: Estado, seccion: String) {
    val f = FogonTema.actual
    val tipos = Secciones.tipos(seccion)
    val musica = seccion == Secciones.MUSICA
    // Las personas de este apartado: artistas en Música, directores en Cine y escritores en Libros.
    val tipoPersona = when (seccion) { Secciones.LIBROS -> Personas.AUTOR; Secciones.CINE -> Personas.DIRECTOR; else -> CajonViewModel.TIPO_ARTISTA }
    val esDeAqui: (Artista) -> Boolean = { a -> if (musica) Personas.esMusico(a) else a.tipo == tipoPersona }
    // En Cine se elige entre películas, series, animes y directores; en Música, entre álbumes y artistas; en Libros, entre libros y escritores.
    var tipo by rememberSaveable(seccion) { mutableStateOf<String?>(null) }
    var artistas by rememberSaveable(seccion) { mutableStateOf(false) }
    var estado by rememberSaveable(seccion) { mutableStateOf<String?>(null) }
    var texto by rememberSaveable(seccion) { mutableStateOf("") }
    var orden by rememberSaveable(seccion) { mutableStateOf(Orden.RECIENTES) }
    LaunchedEffect(seccion) { vm.completarFotos() }

    Seccion(nombreSeccion(seccion), Modifier.padding(top = 12.dp)) {
        Pildora(stringResource(R.string.buscar), Iconos.Buscar, acento = true) { vm.abrir(Pantalla.Buscar(seccion)) }
    }

    // Las cifras del apartado: lo terminado este año en grande, y lo demás al lado.
    val anio = vm.hoy().year
    val delAnio = vm.L.cifras(e, anio)
    val obras = e.vivas.filter { it.tipo in tipos }
    val terminadasAnio = tipos.sumOf { delAnio.porTipo[it] ?: 0 }
    Row(Modifier.aparecer(0).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).vidrio(forma(24.dp))
        .haloPuntos(f.tinta2.copy(alpha = 0.3f), centro = androidx.compose.ui.geometry.Offset(0.2f, 0.5f)).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            NumeroRiso("$terminadasAnio", 48.sp)
            Text(stringResource(when (seccion) { Secciones.MUSICA -> R.string.escuchados_en; Secciones.LIBROS -> R.string.leidos_en; else -> R.string.vistas_en }, anio),
                style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniDato(stringResource(R.string.en_curso), obras.count { it.estado == Estados.EN_CURSO })
            MiniDato(stringResource(R.string.pendientes), obras.count { it.estado == Estados.QUIERO })
            if (musica) MiniDato(nombreTipo(CajonViewModel.TIPO_ARTISTA), e.musicosVivos.size)
            else MiniDato(stringResource(R.string.siempre), obras.count { it.estado == Estados.TERMINADO })
        }
    }

    val enCurso = obras.filter { it.estado == Estados.EN_CURSO }.sortedByDescending { it.cambio }
    if (enCurso.isNotEmpty()) {
        Seccion(stringResource(R.string.en_curso))
        Carrusel(enCurso, vm, conProgreso = true)
    }

    Seccion(stringResource(R.string.tu_cajon))
    if (seccion == Secciones.CINE) {
        // Las obras por tipo y, al final, los directores.
        val opciones = listOf<Pair<String?, String>>(null to stringResource(R.string.todo)) + tipos.map { it to nombreTipo(it) } + (Personas.DIRECTOR to nombreTipo(Personas.DIRECTOR))
        Chips(opciones, if (artistas) Personas.DIRECTOR else tipo) { if (it == Personas.DIRECTOR) artistas = true else { artistas = false; tipo = it } }
    } else Chips(listOf(false to nombreTipo(tipos.first()), true to nombreTipo(tipoPersona)), artistas) { artistas = it }

    OutlinedTextField(
        texto, { texto = it.take(40) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        placeholder = { Text(stringResource(R.string.buscar_en_cajon)) }, singleLine = true, shape = FormaPildora,
        leadingIcon = { IconoRiso(Iconos.Buscar, null, tamano = 20.dp, tinta = f.textoSuave) },
    )

    if (artistas) { CajonArtistas(vm, e, texto, esDeAqui, musica); return }

    val tipoEstados = tipo ?: tipos.first()
    Chips(listOf<Pair<String?, String>>(null to stringResource(R.string.todo)) + Estados.TODOS.map { it to nombreEstado(it, tipoEstados) }, estado) { estado = it }
    val lista = remember(e, tipos, tipo, estado, texto, orden) {
        vm.L.filtrar(e, null, estado, texto, orden).filter { it.tipo in tipos && (tipo == null || it.tipo == tipo) }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(pluralStringResource(R.plurals.obras_n, lista.size, lista.size), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, modifier = Modifier.weight(1f))
        val siguiente = Orden.entries[(orden.ordinal + 1) % Orden.entries.size]
        Pildora(stringResource(nombreOrden(orden)), Iconos.Ordenar) { orden = siguiente }
    }
    if (lista.isEmpty()) {
        Pista(stringResource(if (obras.isEmpty()) R.string.apartado_vacio else R.string.cajon_nada), centro = true, modifier = Modifier.padding(top = 16.dp))
        return
    }
    Cuadricula(lista, vm)
}

@Composable
private fun MiniDato(etiqueta: String, n: Int) {
    val f = FogonTema.actual
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$n", fontFamily = f.cifras, fontSize = 16.sp, color = f.texto)
        Text(etiqueta, style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
    }
}

/** Las personas del apartado (artistas, directores o escritores) en cuadrícula, con su foto redonda y su nota. */
@Composable
private fun CajonArtistas(vm: CajonViewModel, e: Estado, texto: String, esDeAqui: (Artista) -> Boolean, musica: Boolean) {
    val f = FogonTema.actual
    val lista = e.artistasVivos.filter { esDeAqui(it) && (texto.isBlank() || normal(texto) in normal(it.nombre) || it.generos.any { g -> normal(texto) in normal(g) }) }
        .sortedWith(compareByDescending<Artista> { it.favorito }.thenByDescending { it.nota }.thenBy { normal(it.nombre) })
    if (lista.isEmpty()) {
        Pista(stringResource(if (musica) R.string.artistas_vacio else R.string.personas_vacio), centro = true, modifier = Modifier.padding(top = 16.dp))
        return
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        lista.chunked(3).forEachIndexed { fila, grupo ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                grupo.forEachIndexed { k, a ->
                    val albumes = e.vivas.count { it.artista == a.id || !musica && normal(a.nombre) in normal(it.creador) }
                    Column(Modifier.weight(1f).aparecer((fila * 3 + k).coerceAtMost(12)).clip(forma(12.dp)).clickable { vm.abrir(Pantalla.VerArtista(a.id)) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) { FotoArtista(a.foto, a.nombre, 96.dp, a.tipo) }
                        Text(a.nombre, style = MaterialTheme.typography.labelLarge, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        if (a.nota > 0) Estrellas(a.nota, 12.sp)
                        else Text(pluralStringResource(if (musica) R.plurals.albumes_n else R.plurals.obras_n, albumes, albumes), style = MaterialTheme.typography.labelSmall, color = f.textoSuave)
                    }
                }
                repeat(3 - grupo.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Las portadas en cuadrícula de tres, con sus estrellas y el corazón. */
@Composable
fun Cuadricula(obras: List<Obra>, vm: CajonViewModel, numerada: Boolean = false) {
    val f = FogonTema.actual
    // Si son solo discos, marcos cuadrados; si hay de todo, verticales (los discos se apoyan abajo).
    val marco = if (obras.all { it.tipo == Tipos.ALBUM }) 1f else 2f / 3f
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        obras.chunked(3).forEachIndexed { fila, grupo ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                grupo.forEachIndexed { k, o ->
                    val n = fila * 3 + k
                    Column(Modifier.weight(1f).aparecer(n.coerceAtMost(12)).clip(forma(12.dp)).clickable { vm.abrir(Pantalla.Ficha(o.id)) },
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Todas en el mismo marco vertical; los discos (cuadrados) se apoyan abajo, así los títulos quedan parejos.
                        Box(Modifier.fillMaxWidth().aspectRatio(marco), contentAlignment = Alignment.BottomCenter) {
                            Portada(o.portada, o.tipo, o.titulo, Modifier.fillMaxWidth(), conTitulo = false)
                            if (numerada) Text("${n + 1}", fontFamily = f.cifras, fontSize = 15.sp, color = f.sobrePildora,
                                modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(FormaPildora).background(f.pildora).padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                        Text(o.titulo, style = MaterialTheme.typography.labelMedium, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Estrellas(o.nota, 12.sp)
                            if (o.meGusta) IconoRiso(Iconos.Corazon, null, tamano = 12.dp, tinta = f.acentoTexto, relleno = f.acentoTexto)
                            if (o.nota == 0 && !o.meGusta) Text(nombreEstado(o.estado, o.tipo), style = MaterialTheme.typography.labelSmall, color = f.textoSuave, maxLines = 1)
                        }
                    }
                }
                repeat(3 - grupo.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Píldoras para filtrar, en una fila que se desliza. */
@Composable
fun <T> Chips(opciones: List<Pair<T, String>>, elegido: T, onElegir: (T) -> Unit) {
    val f = FogonTema.actual
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opciones.forEach { (v, t) ->
            FilterChip(v == elegido, { onElegir(v) }, { Text(t) }, shape = FormaPildora,
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = f.paleta.tinta, selectedLabelColor = f.paleta.sobreTinta))
        }
    }
}

fun nombreOrden(o: Orden) = when (o) {
    Orden.RECIENTES -> R.string.orden_recientes
    Orden.NOTA -> R.string.orden_nota
    Orden.ANIO -> R.string.orden_anio
    Orden.TITULO -> R.string.orden_titulo
}

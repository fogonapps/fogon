package io.github.fogonapps.cajon.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.Pantalla
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Logica
import io.github.fogonapps.cajon.datos.PesoGusto
import io.github.fogonapps.cajon.datos.Recomendar
import io.github.fogonapps.cajon.datos.Senales
import io.github.fogonapps.cajon.datos.Tipos
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.forma

/** Algo de "Para ti" sobre lo que la persona puede decir que no (lo que el menú necesita saber). */
private data class Sobre(
    val clave: String, val nombre: String, val tipo: String, val imagen: String = "", val fuente: String = "",
    val creador: String = "", val generos: List<String> = emptyList(), val motivo: String = "",
)

/**
 * "Para ti" en Inicio (con el perfil de gustos): si todavía no contó sus gustos, se lo ofrece; cada
 * recomendación dice por qué, y manteniéndola presionada se puede decir que no, con su matiz (ver [MenuNo]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ParaTi(vm: CajonViewModel, e: Estado) {
    val f = FogonTema.actual
    LaunchedEffect(Unit) { vm.cargarRecomendaciones() }
    val r by vm.recomendaciones.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf<Sobre?>(null) }
    Seccion(stringResource(R.string.para_ti)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (r?.vacias == false) BotonIcono(Iconos.Repetir, stringResource(R.string.actualizar)) { vm.cargarRecomendaciones(otraVez = true) }
            Pildora(stringResource(R.string.tus_gustos), Iconos.Corazon) { vm.abrir(Pantalla.TusGustos) }
        }
    }
    if (!e.ajustes.gustosListos) Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().vidrio().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.cuentame_gustos), style = MaterialTheme.typography.titleMedium, color = f.texto)
        Text(stringResource(R.string.cuentame_gustos_pista), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        BotonRiso(stringResource(R.string.empezar), { vm.abrir(Pantalla.ElegirGustos) }, Modifier.fillMaxWidth())
        BotonSecundario(stringResource(R.string.ahora_no), { vm.gustosListos() }, Modifier.fillMaxWidth())
    }
    val rec = r ?: return
    rec.avisos.forEach { Pista(stringResource(it)) }
    if (rec.vacias && rec.avisos.isNotEmpty()) return
    if (rec.vacias) { Pista(stringResource(R.string.para_ti_vacio)); return }
    // Decir que no se aprende en el tutorial y al elegir gustos; aquí queda solo para lectores de pantalla.
    val decirNo = stringResource(R.string.decir_no)

    @Composable
    fun porque(p: Recomendar.Por, persona: Boolean = false): String = when (p.tipo) {
        Recomendar.Por.SEMILLA -> stringResource(if (persona) R.string.como_artista else R.string.porque_te_gusto, p.texto)
        Recomendar.Por.GENERO -> stringResource(R.string.porque_genero, p.texto)
        Recomendar.Por.EXPLORAR -> stringResource(R.string.para_explorar)
        else -> stringResource(R.string.lo_mas_visto)
    }

    @Composable
    fun Fila(titulo: String, lista: List<Pair<io.github.fogonapps.cajon.datos.Encontrado, Recomendar.Por>>, libros: Boolean = false) {
        if (lista.isEmpty()) return
        Text(titulo, style = MaterialTheme.typography.titleSmall, color = f.textoSuave, modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 6.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(lista, key = { it.first.fuente }) { (x, por) ->
                val motivo = if (libros && por.tipo == Recomendar.Por.SEMILLA) stringResource(R.string.de_autor, por.texto) else porque(por)
                Column(Modifier.width(112.dp).clip(forma(12.dp)).combinedClickable(
                    onClick = { vm.abiertaRecomendacion("obra:" + x.fuente); vm.abrirEncontrado(x) },
                    onLongClickLabel = decirNo,
                    onLongClick = { menu = Sobre("obra:" + x.fuente, x.titulo, x.tipo, x.portada, x.fuente, x.creador, x.generos, motivo) },
                ), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Portada(x.portada, x.tipo, x.titulo, Modifier.fillMaxWidth(), conTitulo = false)
                    Text(x.titulo, style = MaterialTheme.typography.labelLarge, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (libros && por.tipo == Recomendar.Por.SEMILLA) stringResource(R.string.de_autor, por.texto) else porque(por),
                        style = MaterialTheme.typography.labelSmall, color = if (por.tipo == Recomendar.Por.EXPLORAR) f.tinta1 else f.textoSuave,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    Fila(stringResource(R.string.para_ti_cine), rec.cine)
    if (rec.artistas.isNotEmpty()) {
        Text(stringResource(R.string.para_ti_artistas), style = MaterialTheme.typography.titleSmall, color = f.textoSuave, modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 6.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(rec.artistas, key = { it.first.mbid }) { (a, por) ->
                val motivo = porque(por, persona = true)
                Column(Modifier.width(100.dp).clip(forma(12.dp)).combinedClickable(
                    onClick = { vm.abiertaRecomendacion(Recomendar.clavePersona(a.nombre)); vm.abrir(Pantalla.PreviaArtista(a)) },
                    onLongClickLabel = decirNo,
                    onLongClick = { menu = Sobre(Recomendar.clavePersona(a.nombre), a.nombre, TIPO_ARTISTA, a.foto, a.fuente, motivo = motivo) },
                ), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FotoArtista(a.foto, a.nombre, 92.dp, a.tipo)
                    Text(a.nombre, style = MaterialTheme.typography.labelLarge, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    Text(porque(por, persona = true), style = MaterialTheme.typography.labelSmall, color = if (por.tipo == Recomendar.Por.EXPLORAR) f.tinta1 else f.textoSuave,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    Fila(stringResource(R.string.para_ti_libros), rec.libros, libros = true)
    if (rec.pendientes.isNotEmpty()) {
        Text(stringResource(R.string.para_ti_pendientes), style = MaterialTheme.typography.titleSmall, color = f.textoSuave, modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 6.dp))
        Carrusel(rec.pendientes, vm, conProgreso = false)
    }
    menu?.let { s -> MenuNo(vm, s) { menu = null } }
}

private const val TIPO_ARTISTA = "artista"

/**
 * El menú de una recomendación (con el dedo apretado), como en las redes: por qué ves esto, me interesa, no me
 * interesa, no me gusta, no recomendar esto y quitar a una persona o un género entero. Todo se deshace en "Tus gustos".
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MenuNo(vm: CajonViewModel, s: Sobre, onCerrar: () -> Unit) {
    val f = FogonTema.actual
    var verPorque by remember { mutableStateOf(false) }
    fun decir(senal: String) {
        vm.decirGusto(s.clave, s.nombre, senal, s.tipo, s.imagen, s.fuente, s.creador, s.generos, desdeParaTi = true); onCerrar()
    }
    androidx.compose.material3.ModalBottomSheet(onCerrar, containerColor = f.fondo) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (s.tipo == TIPO_ARTISTA) FotoArtista(s.imagen, s.nombre, 48.dp) else Portada(s.imagen, s.tipo, s.nombre, Modifier.width(40.dp), conTitulo = false)
                Text(s.nombre, style = MaterialTheme.typography.titleMedium, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            FilaMenu(Iconos.Comentario, stringResource(R.string.red_por_que)) { verPorque = !verPorque }
            if (verPorque && s.motivo.isNotBlank()) Text(s.motivo, style = MaterialTheme.typography.bodyMedium, color = f.textoSuave, modifier = Modifier.padding(start = 64.dp, end = 24.dp, bottom = 8.dp))
            FilaMenu(Iconos.Chulo, stringResource(R.string.red_me_interesa)) { decir(Senales.GUSTA) }
            FilaMenu(Iconos.Cerrar, stringResource(R.string.no_me_interesa)) { decir(Senales.NO_INTERESA) }
            FilaMenu(Iconos.Corazon, stringResource(R.string.no_me_gusta)) { decir(Senales.NO_GUSTA) }
            if (s.tipo != TIPO_ARTISTA) FilaMenu(Iconos.Borrar, stringResource(R.string.no_recomendar)) { decir(Senales.NO_RECOMENDAR) }
            if (s.tipo == TIPO_ARTISTA) FilaMenu(Iconos.Persona, stringResource(R.string.excluir_a, s.nombre), peligro = true) { decir(Senales.EXCLUIR) }
            val persona = s.creador.split(",").first().trim()
            if (s.tipo != TIPO_ARTISTA && persona.isNotEmpty()) FilaMenu(Iconos.Persona, stringResource(R.string.excluir_a, persona), peligro = true) {
                vm.decirGusto(Recomendar.clavePersona(persona), persona, Senales.EXCLUIR, TIPO_ARTISTA, desdeParaTi = true); onCerrar()
            }
            s.generos.take(2).forEach { g ->
                FilaMenu(Iconos.Cerrar, stringResource(R.string.excluir_genero_de, g), peligro = true) { vm.decirGusto(Recomendar.claveGenero(g), g, Senales.EXCLUIR, desdeParaTi = true); onCerrar() }
            }
        }
    }
}

/** Las categorías de "Para ti", con su nombre. */
@Composable
private fun categorias() = listOf(
    Tipos.PELICULA to stringResource(R.string.seccion_peliculas), Tipos.SERIE to stringResource(R.string.seccion_series),
    Tipos.ANIME to stringResource(R.string.seccion_animes), Tipos.ALBUM to stringResource(R.string.seccion_musica),
    Tipos.LIBRO to stringResource(R.string.seccion_libros),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ElegirCategorias(vm: CajonViewModel, e: Estado) {
    val elegidas = e.ajustes.categorias
    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        categorias().forEach { (t, nombre) ->
            val si = elegidas.isEmpty() || t in elegidas
            Pildora(nombre, if (si) Iconos.Chulo else null, acento = si) {
                val base = elegidas.ifEmpty { Tipos.TODOS }
                val nuevas = if (si) base - t else base + t
                // Todas = ninguna elegida (así lo nuevo que llegue también sale).
                vm.elegirCategorias(if (nuevas.size == Tipos.TODOS.size || nuevas.isEmpty()) emptyList() else nuevas)
            }
        }
    }
}

/**
 * "Cuéntame tus gustos": obras, discos, artistas y libros concretos de lo que se ve, escucha y lee ahora (tocar = me
 * gusta) y qué quiere que le recomiende. Lo elegido no entra al cajón: solo sirve para recomendar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ElegirGustos(vm: CajonViewModel, e: Estado) {
    // Al terminar: si todavía no hay cuenta en la red, el paso 2 de Para ti es crearla.
    val terminar = { vm.gustosListos(); if (vm.red.usuario.value == null) vm.reemplazar(Pantalla.CuentaRed) else vm.cerrar() }
    Cabecera(stringResource(R.string.cuentame_gustos), terminar)
    FilasDeGustos(vm, e)
    Seccion(stringResource(R.string.que_recomiendo))
    ElegirCategorias(vm, e)
    Column(Modifier.padding(16.dp)) { BotonRiso(stringResource(R.string.listo), terminar, Modifier.fillMaxWidth()) }
}

/** La marca de "me gusta" sobre una portada o foto elegida. */
@Composable
private fun Elegido(modifier: Modifier) {
    val f = FogonTema.actual
    Box(modifier.size(28.dp).clip(io.github.fogonapps.identidad.FormaPildora).background(f.tinta1), contentAlignment = Alignment.Center) {
        IconoRiso(Iconos.Chulo, null, tamano = 16.dp, tinta = f.sobreTinta1)
    }
}

/**
 * "Tus gustos": el perfil por dentro, para que la persona lo vea y lo corrija. Lo que dijo (y quitarlo), lo que la
 * app aprendió de su cajón (con qué tanta seguridad, y "no es así"), lo de ahora y lo de siempre, lo que no quiere
 * (y deshacerlo), y las categorías de "Para ti".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TusGustos(vm: CajonViewModel, e: Estado) {
    val f = FogonTema.actual
    val confirmar = LocalConfirmar.current
    val p = remember(e) { vm.perfil() }
    Cabecera(stringResource(R.string.tus_gustos), vm::cerrar)
    Pista(stringResource(R.string.tus_gustos_explica))
    Column(Modifier.padding(horizontal = 16.dp)) {
        BotonRiso(stringResource(R.string.elegir_mis_gustos), { vm.abrir(Pantalla.ElegirGustos) }, Modifier.fillMaxWidth())
    }

    Seccion(stringResource(R.string.que_recomiendo))
    ElegirCategorias(vm, e)

    val positivos = (p.generos + p.personas).filter { it.peso > 0 && !it.quitado }
    val ahora = positivos.filter { it.reciente }.sortedByDescending { it.peso }.take(8)
    if (ahora.isNotEmpty()) {
        Seccion(stringResource(R.string.gustos_ahora))
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ahora.forEach { Etiqueta(it.nombre, f.tinta1, f.sobreTinta1) }
        }
    }
    @Composable
    fun Lista(titulo: String, lista: List<PesoGusto>) {
        if (lista.isEmpty()) return
        Seccion(titulo)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            lista.forEach { g -> FilaGusto(vm, g) }
        }
    }
    Lista(stringResource(R.string.gustos_generos), p.generos.filter { it.peso > 0 && !it.quitado }.take(15))
    Lista(stringResource(R.string.gustos_personas), p.personas.filter { it.peso > 0 && !it.quitado }.take(15))

    // Lo que dijo que le gusta de obras y artistas (al elegir sus gustos).
    val semillas = e.gustosVivos.filter { it.senal == Senales.GUSTA && (it.clave.startsWith("obra:") || it.tipo == TIPO_ARTISTA) }
    if (semillas.isNotEmpty()) {
        Seccion(stringResource(R.string.gustos_elegidos))
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            semillas.forEach { g -> Pildora(g.nombre, Iconos.Cerrar) { vm.decirGusto(g.clave, g.nombre, null) } }
        }
    }

    val no = e.gustosVivos.filter { it.senal in Senales.NEGATIVAS || it.senal == Senales.QUITADO }.sortedByDescending { it.cambio }
    if (no.isNotEmpty()) {
        Seccion(stringResource(R.string.gustos_no))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            no.forEach { g ->
                FilaVidrio(adentro = true) {
                    Column(Modifier.weight(1f)) {
                        Text(g.nombre, style = MaterialTheme.typography.bodyLarge, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(when (g.senal) {
                            Senales.NO_GUSTA -> R.string.no_me_gusta; Senales.NO_INTERESA -> R.string.no_me_interesa
                            Senales.NO_RECOMENDAR -> R.string.no_recomendar; Senales.EXCLUIR -> R.string.excluido
                            else -> R.string.no_es_asi
                        }), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                    }
                    Pildora(stringResource(R.string.deshacer)) { vm.decirGusto(g.clave, g.nombre, null) }
                }
            }
        }
    }

    Seccion(stringResource(R.string.historial_para_ti))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.historial_para_ti_pista), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        BotonSecundario(stringResource(R.string.borrar_historial_para_ti), {
            confirmar(vm.tx(R.string.borrar_historial_para_ti), null) { vm.borrarHistorialParaTi() }
        }, Modifier.fillMaxWidth())
    }
}

/** Un gusto del perfil: su nombre, de dónde salió, qué tan seguro es, y quitarlo o decir "no es así". */
@Composable
private fun FilaGusto(vm: CajonViewModel, g: PesoGusto) {
    val f = FogonTema.actual
    FilaVidrio(adentro = true) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(g.nombre, style = MaterialTheme.typography.bodyLarge, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val origen = listOfNotNull(
                stringResource(R.string.lo_dijiste).takeIf { g.declarado },
                stringResource(R.string.aprendido).takeIf { g.aprendido },
                stringResource(R.string.de_ahora).takeIf { g.reciente },
                stringResource(if (g.confianza >= 0.6) R.string.seguro else R.string.poco_seguro).takeIf { !g.declarado },
            ).joinToString(" · ")
            Text(origen, style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            Barra((g.peso / 2.0).toFloat().coerceIn(0.05f, 1f), f.tinta1, Modifier.fillMaxWidth())
        }
        if (g.declarado) Pildora(stringResource(R.string.quitar)) { vm.decirGusto(g.clave, g.nombre, null) }
        else Pildora(stringResource(R.string.no_es_asi)) { vm.decirGusto(g.clave, g.nombre, Senales.QUITADO) }
    }
}

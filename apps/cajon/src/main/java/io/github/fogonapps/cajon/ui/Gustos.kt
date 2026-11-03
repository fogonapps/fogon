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
    val creador: String = "", val generos: List<String> = emptyList(),
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
        Pildora(stringResource(R.string.tus_gustos), Iconos.Corazon) { vm.abrir(Pantalla.TusGustos) }
    }
    if (!e.ajustes.gustosListos) Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().vidrio().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.cuentame_gustos), style = MaterialTheme.typography.titleMedium, color = f.texto)
        Text(stringResource(R.string.cuentame_gustos_pista), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        BotonRiso(stringResource(R.string.empezar), { vm.abrir(Pantalla.ElegirGustos) }, Modifier.fillMaxWidth())
        BotonSecundario(stringResource(R.string.ahora_no), { vm.gustosListos() }, Modifier.fillMaxWidth())
    }
    val rec = r ?: return
    if (rec.vacias) { Pista(stringResource(R.string.para_ti_vacio)); return }
    // Decir que no se aprende en el tutorial y al elegir gustos; aquí queda solo para lectores de pantalla.
    val decirNo = stringResource(R.string.decir_no)
    Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
        Pildora(stringResource(R.string.actualizar), Iconos.Repetir) { vm.cargarRecomendaciones(otraVez = true) }
    }

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
                Column(Modifier.width(112.dp).clip(forma(12.dp)).combinedClickable(
                    onClick = { vm.abiertaRecomendacion("obra:" + x.fuente); vm.abrirEncontrado(x) },
                    onLongClickLabel = decirNo,
                    onLongClick = { menu = Sobre("obra:" + x.fuente, x.titulo, x.tipo, x.portada, x.fuente, x.creador, x.generos) },
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
                Column(Modifier.width(100.dp).clip(forma(12.dp)).combinedClickable(
                    onClick = { vm.abiertaRecomendacion(Recomendar.clavePersona(a.nombre)); vm.abrir(Pantalla.PreviaArtista(a)) },
                    onLongClickLabel = decirNo,
                    onLongClick = { menu = Sobre(Recomendar.clavePersona(a.nombre), a.nombre, TIPO_ARTISTA, a.foto, a.fuente) },
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
 * Decir que no, con su matiz: no me gusta (baja lo parecido), no me interesa (no ahora), no recomendar esto (sin
 * decir nada de los gustos) o excluir un género o una persona entera. Todo se deshace en "Tus gustos".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MenuNo(vm: CajonViewModel, s: Sobre, onCerrar: () -> Unit) {
    val f = FogonTema.actual
    fun decir(senal: String) {
        vm.decirGusto(s.clave, s.nombre, senal, s.tipo, s.imagen, s.fuente, s.creador, s.generos, desdeParaTi = true); onCerrar()
    }
    Dialog(onCerrar) {
        Column(Modifier.clip(forma(28.dp)).background(f.fondo).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(s.nombre, style = MaterialTheme.typography.titleMedium, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
            OpcionNo(stringResource(R.string.no_me_gusta), stringResource(R.string.no_me_gusta_pista)) { decir(Senales.NO_GUSTA) }
            OpcionNo(stringResource(R.string.no_me_interesa), stringResource(R.string.no_me_interesa_pista)) { decir(Senales.NO_INTERESA) }
            if (s.tipo != TIPO_ARTISTA) OpcionNo(stringResource(R.string.no_recomendar), stringResource(R.string.no_recomendar_pista)) { decir(Senales.NO_RECOMENDAR) }
            if (s.tipo == TIPO_ARTISTA) OpcionNo(stringResource(R.string.excluir_a, s.nombre), stringResource(R.string.excluir_pista)) { decir(Senales.EXCLUIR) }
            val persona = s.creador.split(",").first().trim()
            if (s.tipo != TIPO_ARTISTA && persona.isNotEmpty()) OpcionNo(stringResource(R.string.excluir_a, persona), stringResource(R.string.excluir_pista)) {
                vm.decirGusto(Recomendar.clavePersona(persona), persona, Senales.EXCLUIR, TIPO_ARTISTA, desdeParaTi = true); onCerrar()
            }
            if (s.generos.isNotEmpty()) {
                Text(stringResource(R.string.excluir_genero), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.generos.forEach { g -> Pildora(g, Iconos.Cerrar) { vm.decirGusto(Recomendar.claveGenero(g), g, Senales.EXCLUIR, desdeParaTi = true); onCerrar() } }
                }
            }
            BotonSecundario(stringResource(R.string.cancelar), onCerrar, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun OpcionNo(titulo: String, pista: String, onClick: () -> Unit) {
    val f = FogonTema.actual
    Column(Modifier.fillMaxWidth().vidrio().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(titulo, style = MaterialTheme.typography.bodyLarge, color = f.texto)
        Text(pista, style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
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
 * Un género que se toca para decir si gusta: nada → me gusta → no me gusta → nada. Lo dicho se guarda como
 * [io.github.fogonapps.cajon.datos.Gusto] con la clave del género.
 */
@Composable
private fun ChipGenero(vm: CajonViewModel, e: Estado, nombre: String) {
    val clave = Recomendar.claveGenero(nombre)
    val dicho = e.gustosVivos.firstOrNull { it.clave == clave }?.senal
    val f = FogonTema.actual
    val icono = when (dicho) { Senales.GUSTA -> Iconos.Corazon; Senales.NO_GUSTA, Senales.EXCLUIR -> Iconos.Cerrar; else -> null }
    Row(
        Modifier.clip(io.github.fogonapps.identidad.FormaPildora)
            .background(if (dicho == Senales.GUSTA) f.tinta1 else f.superficie)
            .border(f.borde, if (dicho == Senales.GUSTA) f.tinta1 else f.linea, io.github.fogonapps.identidad.FormaPildora)
            .clickable {
                val siguiente = when (dicho) { null -> Senales.GUSTA; Senales.GUSTA -> Senales.NO_GUSTA; else -> null }
                vm.decirGusto(clave, nombre, siguiente)
            }.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icono != null) IconoRiso(icono, null, tamano = 16.dp, tinta = if (dicho == Senales.GUSTA) f.sobreTinta1 else f.textoSuave)
        Text(nombre, style = MaterialTheme.typography.labelLarge, color = when (dicho) {
            Senales.GUSTA -> f.sobreTinta1; Senales.NO_GUSTA, Senales.EXCLUIR -> f.textoSuave; else -> f.texto
        }, maxLines = 1)
    }
}

/** Los géneros para elegir: los de su cajón (los más usados) y una lista base, sin repetir. */
private fun generosParaElegir(e: Estado, tipos: List<String>, base: Array<String>, conArtistas: Boolean = false): List<String> {
    val delCajon = (e.vivas.filter { it.tipo in tipos }.flatMap { it.generos } + if (conArtistas) e.musicosVivos.flatMap { it.generos } else emptyList())
        .filter { it.isNotBlank() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(18).map { it.key }
    // Hasta 24: los suyos primero, después la lista base (en el idioma de la app).
    return (delCajon + base).distinctBy { Logica.tituloLimpio(it) }.take(24)
}

/**
 * "Cuéntame tus gustos": qué quiere que le recomiende, qué géneros le gustan (o no) y, para que "Para ti" arranque
 * aunque el cajón esté vacío, lo que le gusta de lo más visto, escuchado y leído (tocar = me gusta). Lo elegido no
 * entra al cajón: solo sirve para recomendar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ElegirGustos(vm: CajonViewModel, e: Estado) {
    val f = FogonTema.actual
    LaunchedEffect(Unit) { vm.cargarParaElegir() }
    val opciones by vm.paraElegir.collectAsStateWithLifecycle()
    val tmdb by vm.tieneTmdb.collectAsStateWithLifecycle()
    // Al terminar: si todavía no hay cuenta en la red, el paso 2 de Para ti es crearla.
    val terminar = { vm.gustosListos(); if (vm.red.usuario.value == null) vm.reemplazar(Pantalla.CuentaRed) else vm.cerrar() }
    Cabecera(stringResource(R.string.cuentame_gustos), terminar)
    Pista(stringResource(R.string.elegir_gustos_explica))

    Seccion(stringResource(R.string.que_recomiendo))
    ElegirCategorias(vm, e)

    Seccion(stringResource(R.string.generos_que_te_gustan))
    Pista(stringResource(R.string.generos_pista))
    @Composable
    fun Generos(titulo: String, lista: List<String>) {
        Text(titulo, style = MaterialTheme.typography.titleSmall, color = f.textoSuave, modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 6.dp))
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            lista.forEach { ChipGenero(vm, e, it) }
        }
    }
    val p = remember(e) { vm.perfil() }
    if (p.quiere(Tipos.PELICULA) || p.quiere(Tipos.SERIE) || p.quiere(Tipos.ANIME))
        Generos(stringResource(R.string.seccion_cine), generosParaElegir(e, listOf(Tipos.PELICULA, Tipos.SERIE, Tipos.ANIME), stringArrayResource(R.array.generos_cine)))
    if (p.quiere(Tipos.ALBUM)) Generos(stringResource(R.string.seccion_musica), generosParaElegir(e, listOf(Tipos.ALBUM), stringArrayResource(R.array.generos_musica), conArtistas = true))
    if (p.quiere(Tipos.LIBRO)) Generos(stringResource(R.string.seccion_libros), generosParaElegir(e, listOf(Tipos.LIBRO), stringArrayResource(R.array.generos_libros)))

    Seccion(stringResource(R.string.lo_que_te_gusta))
    Pista(stringResource(R.string.lo_que_te_gusta_pista))
    if (!opciones.listo) Pista(stringResource(R.string.cargando))
    val dichos = e.gustosVivos.filter { it.senal == Senales.GUSTA }.map { it.clave }.toSet()
    @Composable
    fun Obras(titulo: String, lista: List<io.github.fogonapps.cajon.datos.Encontrado>) {
        if (lista.isEmpty()) return
        Text(titulo, style = MaterialTheme.typography.titleSmall, color = f.textoSuave, modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 6.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(lista, key = { it.fuente }) { x ->
                val clave = "obra:" + x.fuente
                val si = clave in dichos
                Column(Modifier.width(104.dp).clip(forma(12.dp)).clickable {
                    vm.decirGusto(clave, x.titulo, if (si) null else Senales.GUSTA, x.tipo, x.portada, x.fuente, x.creador, x.generos)
                }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box {
                        Portada(x.portada, x.tipo, x.titulo, Modifier.fillMaxWidth(), conTitulo = false)
                        if (si) Elegido(Modifier.align(Alignment.TopEnd).padding(6.dp))
                    }
                    Text(x.titulo, style = MaterialTheme.typography.labelLarge, color = if (si) f.tinta1 else f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    if (p.quiere(Tipos.PELICULA) || p.quiere(Tipos.SERIE) || p.quiere(Tipos.ANIME)) {
        if (tmdb) Obras(stringResource(R.string.lo_mas_visto_semana), opciones.cine.filter { p.quiere(it.tipo) })
        else Pista(stringResource(R.string.gustos_sin_tmdb))
    }
    if (p.quiere(Tipos.ALBUM) && opciones.artistas.isNotEmpty()) {
        Text(stringResource(R.string.artistas), style = MaterialTheme.typography.titleSmall, color = f.textoSuave, modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 6.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(opciones.artistas, key = { it.mbid }) { a ->
                val clave = Recomendar.clavePersona(a.nombre)
                val si = clave in dichos
                Column(Modifier.width(96.dp).clip(forma(12.dp)).clickable {
                    vm.decirGusto(clave, a.nombre, if (si) null else Senales.GUSTA, TIPO_ARTISTA, a.foto, a.fuente)
                }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box {
                        FotoArtista(a.foto, a.nombre, 88.dp, a.tipo)
                        if (si) Elegido(Modifier.align(Alignment.TopEnd))
                    }
                    Text(a.nombre, style = MaterialTheme.typography.labelLarge, color = if (si) f.tinta1 else f.texto, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
    }
    if (p.quiere(Tipos.LIBRO)) Obras(stringResource(R.string.lo_mas_leido), opciones.libros)
    Pista(stringResource(R.string.gustos_como_decir_no), Modifier.padding(top = 12.dp))
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

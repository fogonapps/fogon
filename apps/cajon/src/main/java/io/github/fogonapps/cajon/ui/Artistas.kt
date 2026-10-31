package io.github.fogonapps.cajon.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.Pantalla
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Personas
import io.github.fogonapps.cajon.datos.normal
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.FormaPildora
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.aparecer

@Composable
fun Artistas(vm: CajonViewModel, e: Estado) {
    val f = FogonTema.actual
    var texto by rememberSaveable { mutableStateOf("") }
    Cabecera(stringResource(R.string.tipo_artistas), vm::cerrar) {
        BotonIcono(Iconos.Buscar, stringResource(R.string.buscar_artista), grande = true) { vm.abrir(Pantalla.Buscar(io.github.fogonapps.cajon.Secciones.MUSICA)) }
    }
    OutlinedTextField(texto, { texto = it.take(60) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true, shape = FormaPildora,
        placeholder = { Text(stringResource(R.string.buscar_o_nuevo_artista)) },
        leadingIcon = { IconoRiso(Iconos.Buscar, null, tamano = 20.dp, tinta = f.textoSuave) })
    val lista = e.musicosVivos.filter { texto.isBlank() || normal(texto) in normal(it.nombre) }.sortedBy { normal(it.nombre) }
    if (texto.isNotBlank() && lista.none { normal(it.nombre) == normal(texto) }) Box(Modifier.padding(16.dp)) {
        BotonSecundario(stringResource(R.string.agregar_artista, texto.trim()), { vm.nuevoArtista(texto); texto = "" }, Modifier.fillMaxWidth())
    }
    if (e.musicosVivos.isEmpty()) Pista(stringResource(R.string.artistas_vacio))
    lista.forEachIndexed { i, a ->
        val albumes = e.vivas.count { it.artista == a.id }
        FilaVidrio({ vm.abrir(Pantalla.VerArtista(a.id)) }, Modifier.aparecer(i.coerceAtMost(10))) {
            FotoArtista(a.foto, a.nombre, 46.dp, a.tipo)
            Column(Modifier.weight(1f)) {
                Text(a.nombre, style = MaterialTheme.typography.bodyLarge, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(pluralStringResource(R.plurals.albumes_n, albumes, albumes), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
            Estrellas(a.nota)
        }
    }
}

/** Los géneros de un artista, con el mismo editor de etiquetas que las obras. Tocar uno busca ese género. */
@Composable
private fun Generos(vm: CajonViewModel, a: io.github.fogonapps.cajon.datos.Artista) =
    EditorEtiquetas("a" + a.id, stringResource(R.string.generos), a.generos, { (vm.generosDelCajon() + vm.sugerenciasEtiquetas(listOf(io.github.fogonapps.cajon.datos.Tipos.ALBUM))).distinct() },
        { vm.ponerGeneros(a.id, it) }, { vm.buscarGenero(it) }, stringResource(R.string.agregar_genero))

/**
 * Un músico que no está enlazado (lo trajo un respaldo, o se agregó a mano): se busca en MusicBrainz y se elige
 * cuál es, para que lleguen la foto, la biografía y la discografía.
 */
@Composable
private fun Enlazar(vm: CajonViewModel, a: io.github.fogonapps.cajon.datos.Artista) {
    val f = FogonTema.actual
    val c by vm.candidatosArtista.collectAsStateWithLifecycle()
    val lista = c?.takeIf { it.first == a.id }
    Column(Modifier.padding(16.dp).fillMaxWidth().vidrio().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.enlazar_pista), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        when {
            lista == null -> BotonSecundario(stringResource(R.string.enlazar_musicbrainz), { vm.buscarCandidatosArtista(a.id) }, Modifier.fillMaxWidth())
            lista.second == null -> Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator(color = f.tinta1) }
            lista.second!!.isEmpty() -> Pista(stringResource(R.string.sin_resultados), adentro = true)
            else -> lista.second!!.take(6).forEach { r ->
                FilaVidrio({ vm.enlazarArtista(a.id, r) }, adentro = true) {
                    FotoArtista(r.foto, r.nombre, 44.dp, r.tipo)
                    Column(Modifier.weight(1f)) {
                        Text(r.nombre, style = MaterialTheme.typography.bodyLarge, color = f.texto)
                        val d = listOf(r.pais, r.desde?.toString() ?: "", r.detalle).filter { it.isNotBlank() }.joinToString(" · ")
                        if (d.isNotBlank()) Text(d, style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 2)
                    }
                }
            }
        }
    }
}

/** Una persona encontrada (artista, escritor o director), antes de guardarla. */
@Composable
fun PreviaArtista(vm: CajonViewModel, p: Pantalla.PreviaArtista) {
    val f = FogonTema.actual
    val a = p.artista
    val musico = a.tipo != Personas.AUTOR && a.tipo != Personas.DIRECTOR
    Cabecera(nombreTipo(if (musico) CajonViewModel.TIPO_ARTISTA else a.tipo, plural = false), vm::cerrar)
    Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FotoArtista(a.foto, a.nombre, 150.dp, a.tipo)
        Text(a.nombre, fontFamily = f.titulos, fontSize = 26.sp, color = f.texto)
        Text(listOf(tipoArtista(a.tipo), a.pais, a.desde?.toString() ?: "", a.detalle).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
        BotonRiso(stringResource(if (musico) R.string.guardar_artista else R.string.guardar), { vm.guardarArtista(a) }, Modifier.fillMaxWidth())
        Pista(stringResource(fuentePersona(a.fuente)), adentro = true)
    }
}

@Composable
fun VerArtista(vm: CajonViewModel, e: Estado, id: String) {
    val f = FogonTema.actual
    val confirmar = LocalConfirmar.current
    val a = e.artistasVivos.firstOrNull { it.id == id }
    if (a == null) { Cabecera("", vm::cerrar); Pista(stringResource(R.string.ya_no_esta)); return }
    val disco by vm.discografia.collectAsStateWithLifecycle()
    val trayendo by vm.trayendo.collectAsStateWithLifecycle()
    LaunchedEffect(a.fuente) { vm.cargarDiscografia(a.fuente) }

    Cabecera(a.nombre, vm::cerrar) {
        BotonIcono(Iconos.Estrella, stringResource(if (a.favorito) R.string.quitar_favorita else R.string.poner_favorita), grande = true,
            tinta = if (a.favorito) f.acentoTexto else f.textoSuave) { vm.cambiarArtista(a.id) { it.copy(favorito = !it.favorito) } }
    }
    LaunchedEffect(a.id, a.foto) { vm.completarCredito(a.id) }
    LaunchedEffect(a.id, a.fuente) { vm.completarBio(a.id) }
    // La foto grande, con su crédito (autor y licencia de Wikimedia Commons) debajo.
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FotoArtista(a.foto, a.nombre, 150.dp, a.tipo)
        if (a.foto.isNotBlank() && a.fotoCredito.isNotBlank()) Text(stringResource(R.string.foto_de, a.fotoCredito), style = MaterialTheme.typography.labelSmall,
            color = f.textoSuave, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
        val datos = listOf(tipoArtista(a.tipo), a.pais, a.desde?.let { stringResource(R.string.desde_anio, it) } ?: "").filter { it.isNotBlank() }
        if (datos.isNotEmpty()) Text(datos.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = f.texto)
    }

    // Los géneros, como etiquetas: tocar una busca ese género; "Editar" deja quitar y agregar.
    Generos(vm, a)
    if (a.fuente.isEmpty() && Personas.esMusico(a)) Enlazar(vm, a)

    // La biografía breve (de Wikipedia, con su crédito y su licencia).
    if (a.bio.isNotBlank()) {
        Seccion(stringResource(R.string.biografia))
        var abierta by rememberSaveable(a.id) { mutableStateOf(false) }
        Text(a.bio, style = MaterialTheme.typography.bodyMedium, color = f.texto, maxLines = if (abierta) Int.MAX_VALUE else 6,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).clickable { abierta = !abierta })
        Pista(stringResource(R.string.bio_credito))
    }

    Column(Modifier.fillMaxWidth().padding(16.dp).vidrio().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.tu_nota), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
        ElegirEstrellas(a.nota, { n -> vm.cambiarArtista(a.id) { it.copy(nota = n) } }, descripcion = stringResource(R.string.tu_nota))
        // Los datos curiosos solo se muestran si hay algo (o si la persona quiere escribir).
        var notas by rememberSaveable(a.id) { mutableStateOf(a.notas) }
        var conNotas by rememberSaveable(a.id) { mutableStateOf(a.notas.isNotBlank()) }
        if (conNotas) OutlinedTextField(notas, { notas = it.take(5000); vm.cambiarArtista(a.id) { x -> x.copy(notas = it.take(5000)) } }, Modifier.fillMaxWidth(), minLines = 3,
            label = { Text(stringResource(R.string.datos_curiosos)) })
        else Pildora(stringResource(R.string.agregar_datos_curiosos), Iconos.Mas) { conNotas = true }
    }

    // Lo suyo en el cajón: los álbumes unidos al artista; de escritores y directores, lo que lleva su nombre.
    val musico = Personas.esMusico(a)
    val suyos = e.vivas.filter { it.artista == a.id || !musico && a.nombre.isNotBlank() && normal(a.nombre) in normal(it.creador) }.sortedByDescending { it.anio ?: 0 }
    if (suyos.isNotEmpty()) {
        Seccion(stringResource(R.string.en_tu_cajon))
        Cuadricula(suyos, vm)
    }

    // Sus obras afuera (discografía, libros o filmografía), para sumarlas con un toque.
    if (a.fuente.startsWith("mb:artist:") || a.fuente.startsWith("ol:author:") || a.fuente.startsWith("tmdb:person:")) {
        Seccion(stringResource(when (a.tipo) { Personas.AUTOR -> R.string.sus_libros; Personas.DIRECTOR -> R.string.filmografia; else -> R.string.discografia }))
        val lista = disco?.takeIf { it.first == a.fuente }?.second
        when {
            disco?.first == "error:" + a.fuente -> Pista(stringResource(R.string.buscar_sin_red))
            a.fuente.startsWith("tmdb:") && !vm.hayClaveTmdb() -> Pista(stringResource(R.string.buscar_falta_tmdb))
            lista == null -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = f.tinta1) }
            lista.isEmpty() -> Pista(stringResource(R.string.cajon_nada))
            else -> lista.forEach { r ->
                val ya = vm.L.porFuente(e, r.fuente) != null
                FilaVidrio({ vm.abrirEncontrado(r) }) {
                    Portada(r.portada, r.tipo, r.titulo, Modifier.width(44.dp), esquina = 6.dp, conTitulo = false)
                    Column(Modifier.weight(1f)) {
                        Text(r.titulo, style = MaterialTheme.typography.bodyLarge, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(r.anio?.toString(), r.detalle.takeIf { it.isNotBlank() }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                    }
                    when {
                        trayendo == r.fuente -> CircularProgressIndicator(Modifier.width(20.dp), color = f.tinta1, strokeWidth = 2.dp)
                        ya -> IconoRiso(Iconos.Chulo, stringResource(R.string.ya_en_cajon), tamano = 18.dp, tinta = f.acentoTexto)
                    }
                }
            }
        }
        Pista(stringResource(fuentePersona(a.fuente)))
    }

    Box(Modifier.padding(16.dp)) {
        BotonSecundario(stringResource(R.string.borrar), {
            confirmar(vm.tx(R.string.borrar_artista_pregunta, a.nombre), vm.tx(R.string.borrar_artista_texto)) { vm.borrarArtista(a.id) }
        }, Modifier.fillMaxWidth(), peligro = true)
    }
}

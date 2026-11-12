package io.github.fogonapps.cajon.ui

import io.github.fogonapps.perfil.*
import io.github.fogonapps.perfil.Perfil as PerfilComun
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material3.Slider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.semantics.stateDescription
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import io.github.fogonapps.identidad.FormaPildora
import io.github.fogonapps.cajon.datos.Encuadre
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.Pantalla
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.Destacados
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Logica
import io.github.fogonapps.cajon.datos.Obra
import io.github.fogonapps.cajon.datos.PartesPerfil
import io.github.fogonapps.cajon.datos.Tipos
import io.github.fogonapps.cajon.datos.Visibilidad
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.aparecer
import io.github.fogonapps.identidad.forma
import io.github.fogonapps.identidad.haloPuntos
import io.github.fogonapps.identidad.rebote

@Composable
private fun nombreHueco(h: String): String = stringResource(when (h) {
    Destacados.PELICULA -> R.string.dest_pelicula; Destacados.SERIE -> R.string.dest_serie; Destacados.ANIME -> R.string.dest_anime
    Destacados.ALBUM -> R.string.dest_album; Destacados.LIBRO -> R.string.dest_libro; Destacados.ARTISTA -> R.string.dest_artista
    Destacados.MARCO -> R.string.dest_marco; else -> R.string.dest_descubrimiento
})

@Composable
internal fun nombreVisibilidad(v: String): String = stringResource(when (Visibilidad.de(v)) {
    Visibilidad.PUBLICA -> R.string.vis_publica; Visibilidad.AMIGOS -> R.string.vis_amigos; else -> R.string.vis_privada
})

@Composable
private fun nombreParte(p: String): String = stringResource(when (p) {
    PartesPerfil.DESTACADOS -> R.string.parte_destacados; PartesPerfil.FAVORITAS -> R.string.tus_favoritas_titulo
    PartesPerfil.CIFRAS -> R.string.parte_cifras; PartesPerfil.EVOLUCION -> R.string.parte_evolucion
    PartesPerfil.MUSICA -> R.string.parte_musica; PartesPerfil.GUSTOS -> R.string.parte_gustos; PartesPerfil.GUARDADAS -> R.string.parte_guardadas
    else -> R.string.tu_diario
})

@Composable
internal fun nombreCifra(c: String, anio: Int): String = when (c) {
    PartesPerfil.C_TERMINADAS -> stringResource(R.string.cifra_terminadas, anio)
    PartesPerfil.C_HORAS_CINE -> stringResource(R.string.horas_de_cine)
    PartesPerfil.C_HORAS_SERIES -> stringResource(R.string.horas_de_series)
    PartesPerfil.C_PAGINAS -> stringResource(R.string.paginas_leidas)
    PartesPerfil.C_CANCIONES -> stringResource(R.string.canciones_escuchadas)
    PartesPerfil.C_DIARIO -> stringResource(R.string.en_tu_diario)
    PartesPerfil.C_RACHA -> stringResource(R.string.racha)
    else -> stringResource(R.string.cifra_nota)
}

/** El encuadre como [ContentScale]: llena el marco como [ContentScale.Crop] y lo agranda [zoom] veces. */
private data class EscalaEncuadre(val zoom: Float) : ContentScale {
    override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor {
        val s = max(dstSize.width / srcSize.width, dstSize.height / srcSize.height) * zoom
        return ScaleFactor(s, s)
    }
}

/**
 * Una imagen del perfil con su [Encuadre]: el zoom agranda la imagen ya recortada al marco y la posición la corre
 * dentro de lo que sobra (-1, un borde; 1, el otro). Lo que se sale del marco no se dibuja.
 */
@Composable
private fun ImagenEncuadrada(url: String, encuadre: Encuadre, modifier: Modifier, onTamano: ((Size) -> Unit)? = null) {
    val en = encuadre.limpio()
    val escala = remember(en.zoom) { EscalaEncuadre(en.zoom) }
    coil3.compose.AsyncImage(url, null, modifier, contentScale = escala, alignment = BiasAlignment(en.x, en.y),
        onSuccess = onTamano?.let { cb -> { st -> cb(st.painter.intrinsicSize) } })
}

/** Un contador del encabezado del perfil: su número, su etiqueta (plural; recibe el número y el año) y a dónde lleva. */
private class Contador(val valor: Int, @androidx.annotation.PluralsRes val etiqueta: Int, val abrir: () -> Unit)

/**
 * Los tres contadores del perfil, en un solo lugar para cambiarlos fácil: "en tu cajón",
 * "terminadas en <año>" y "artistas". No hay seguidores: todavía no hay perfiles públicos.
 */
private fun contadoresPerfil(vm: CajonViewModel, e: Estado, anio: Int) = listOf(
    Contador(e.vivas.size, R.plurals.cifra_en_cajon) { vm.irA(io.github.fogonapps.cajon.Pestana.INICIO) },
    Contador(vm.L.cifras(e, anio).porTipo.values.sum(), R.plurals.cifra_terminadas_anio) { vm.abrir(Pantalla.Cifras) },
    Contador(e.musicosVivos.size, R.plurals.cifra_artistas) { vm.abrir(Pantalla.Artistas) },
)

/** Elegir quién podría ver algo: privada, amigos o pública. */
@Composable
fun ElegirVisibilidad(valor: String, onElegir: (String) -> Unit) =
    Selector(Visibilidad.TODAS.map { it to nombreVisibilidad(it) }, Visibilidad.de(valor), onElegir)

/**
 * La foto del perfil: redonda, o el ícono de persona en un círculo de vidrio si no hay. Con [onClick] se puede tocar
 * (el avatar del Inicio abre el perfil) y [descripcion] es lo que dice el lector de pantalla.
 */
@Composable
fun FotoPerfil(foto: String, tamano: androidx.compose.ui.unit.Dp, encuadre: Encuadre = Encuadre(), borde: Boolean = false, descripcion: String? = null, onClick: (() -> Unit)? = null) {
    val f = FogonTema.actual
    Box(Modifier.then(if (onClick != null) Modifier.rebote(0.9f) else Modifier).size(tamano).clip(CircleShape).background(f.superficie)
        .border(if (borde) 4.dp else f.borde, if (borde) f.fondo else f.linea, CircleShape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .then(if (descripcion != null) Modifier.semantics { contentDescription = descripcion; if (onClick != null) role = Role.Button } else Modifier),
        contentAlignment = Alignment.Center) {
        IconoRiso(Iconos.Persona, null, tamano = tamano * 0.42f, tinta = f.acentoTexto)
        if (foto.isNotBlank()) ImagenEncuadrada(foto, encuadre, Modifier.matchParentSize().clip(CircleShape))
    }
}

/**
 * Tu perfil, el único: el mismo que ve la red (sin cuenta, solo en este teléfono). Arriba quién eres; debajo, por
 * íconos: publicaciones, favoritos, artistas, listas, guardadas y cifras. Los ajustes, en el engranaje.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun Perfil(vm: CajonViewModel, e: Estado) {
    val f = FogonTema.actual
    val uri = LocalUriHandler.current
    val acciones = LocalAcciones.current
    val contexto = androidx.compose.ui.platform.LocalContext.current
    val p = e.perfil
    val yo by vm.red.usuario.collectAsStateWithLifecycle()
    val perfiles by vm.red.perfiles.collectAsStateWithLifecycle()
    val listasRed by vm.red.listas.collectAsStateWithLifecycle()
    LaunchedEffect(yo) { yo?.let { vm.red.verPerfil(it) } }
    val red = yo?.let { perfiles[it] }
    val anio = vm.hoy().year
    val descFoto = stringResource(if (p.foto.isBlank()) R.string.poner_foto else R.string.cambiar_foto_perfil)
    var pestana by rememberSaveable { mutableStateOf(0) }

    // Arriba: con cuenta de moderación, su espacio (con lo pendiente); siempre, los ajustes.
    val cuenta by vm.red.cuenta.collectAsStateWithLifecycle()
    val casos by vm.red.casos.collectAsStateWithLifecycle()
    val frasesPendientes by vm.red.frasesPendientes.collectAsStateWithLifecycle()
    LaunchedEffect(cuenta?.permisos) { if (!cuenta?.permisos.isNullOrEmpty()) vm.red.cargarCasos() }
    val navegacion: @Composable () -> Unit = {
        Cabecera(yo?.let { "@$it" } ?: stringResource(R.string.tu_perfil), vm::cerrar, chico = true) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!cuenta?.permisos.isNullOrEmpty()) ConCuenta(casos.orEmpty().size + frasesPendientes.size) {
                    BotonIcono(Iconos.Bandera, stringResource(R.string.mod_titulo), grande = true) { vm.abrir(Pantalla.Moderacion) }
                }
                BotonIcono(Iconos.Ajustes, stringResource(R.string.ajustes), grande = true) { vm.abrir(Pantalla.Ajustes) }
            }
        }
    }

    @Composable
    fun contenidoSeccion(idSeccion: Int) {
        fun obra(id: String?) = id?.let { i -> e.vivas.firstOrNull { it.id == i } }
        // Quién ve cada pestaña en la red (con cuenta): se elige aquí mismo.
        val partesDe = mapOf(1 to listOf(PartesPerfil.DESTACADOS, PartesPerfil.FAVORITAS, PartesPerfil.GUSTOS), 2 to listOf(PartesPerfil.MUSICA),
        4 to listOf(PartesPerfil.GUARDADAS), 5 to listOf(PartesPerfil.CIFRAS))
        if (yo != null) partesDe[idSeccion]?.let { partes ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.quien_lo_ve), style = MaterialTheme.typography.labelMedium, color = f.textoSuave)
                Box(Modifier.weight(1f)) { ElegirVisibilidad(p.visibilidad[partes.first()] ?: "") { v -> partes.forEach { vm.visibilidadPerfil(it, v) } } }
            }
        }
        when (idSeccion) {
            0 -> when {
                yo == null -> NadaAqui()
                red == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.tinta1, trackColor = f.linea)
                red.publicaciones.isEmpty() -> Row(Modifier.padding(16.dp)) { Pildora(stringResource(R.string.red_publicar), Iconos.Lapiz, acento = true) { vm.abrir(Pantalla.Publicar()) } }
                else -> CuadriculaPublicaciones(vm, red.publicaciones)
            }
            1 -> {
                // Favoritos por tipo, las cuatro favoritas, la obra que te marcó y tu descubrimiento.
                Destacados.FAVORITOS.chunked(3).forEach { fila ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        fila.forEach { h ->
                            val id = p.destacados[h]
                            val a = if (h == Destacados.ARTISTA) id?.let { i -> e.artistasVivos.firstOrNull { it.id == i } } else null
                            val o = if (h != Destacados.ARTISTA) obra(id) else null
                            Column(Modifier.weight(1f).clip(forma(12.dp)).combinedClickable(
                            onClick = { when { a != null -> vm.abrir(Pantalla.VerArtista(a.id)); o != null -> vm.abrir(Pantalla.Ficha(o.id)); else -> vm.abrir(Pantalla.ElegirDestacado(h)) } },
                            onLongClick = { vm.abrir(Pantalla.ElegirDestacado(h)) },
                            ), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                when {
                                    a != null -> FotoArtista(a.foto, a.nombre, 96.dp, a.tipo)
                                    o != null -> Portada(o.portada, o.tipo, o.titulo, Modifier.fillMaxWidth(), conTitulo = false)
                                    else -> Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).vidrio(forma(12.dp)), contentAlignment = Alignment.Center) {
                                        IconoRiso(Iconos.Mas, null, tamano = 22.dp, tinta = f.textoSuave)
                                    }
                                }
                                Text(nombreHueco(h), style = MaterialTheme.typography.labelSmall, color = f.textoSuave, maxLines = 1)
                                Text(a?.nombre ?: o?.titulo ?: "", style = MaterialTheme.typography.labelLarge, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                        repeat(3 - fila.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                listOf(Destacados.MARCO, Destacados.DESCUBRIMIENTO).forEach { h ->
                    val o = obra(p.destacados[h])
                    if (o != null) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).vidrio().combinedClickable(
                    onClick = { vm.abrir(Pantalla.Ficha(o.id)) }, onLongClick = { vm.abrir(Pantalla.ElegirDestacado(h)) },
                    ).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Portada(o.portada, o.tipo, o.titulo, Modifier.width(92.dp), conTitulo = false)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(nombreHueco(h), style = MaterialTheme.typography.labelLarge, color = f.acentoTexto)
                            Text(o.titulo, fontFamily = f.titulos, fontSize = 20.sp, lineHeight = 24.sp, color = f.texto, maxLines = 3)
                            p.porQue[h]?.let { Text("“$it”", style = MaterialTheme.typography.bodyMedium, color = f.texto) }
                        }
                    } else FilaVidrio({ vm.abrir(Pantalla.ElegirDestacado(h)) }) {
                        Ficha(f.tinta1, Iconos.Estrella)
                        Text(nombreHueco(h), style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.weight(1f))
                        IconoRiso(Iconos.Mas, null, tamano = 18.dp, tinta = f.textoSuave)
                    }
                }
                val favoritas = e.ajustes.favoritas.mapNotNull { obra(it) }
                Seccion(stringResource(R.string.tus_favoritas_titulo))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (i in 0 until 4) {
                        val o = favoritas.getOrNull(i)
                        Box(Modifier.weight(1f).clip(forma(10.dp)).clickable(enabled = o != null) { o?.let { vm.abrir(Pantalla.Ficha(it.id)) } }) {
                            Portada(o?.portada ?: "", o?.tipo ?: Tipos.PELICULA, o?.titulo ?: "", Modifier.fillMaxWidth(), esquina = 10.dp, conTitulo = false)
                        }
                    }
                }
            }
            2 -> {
                // Los artistas con más discos en el cajón primero.
                val artistas = e.musicosVivos.sortedByDescending { a -> e.vivas.count { it.artista == a.id } }
                if (artistas.isEmpty()) Row(Modifier.padding(16.dp)) { Pildora(stringResource(R.string.buscar_artista), Iconos.Mas) { vm.abrir(Pantalla.Buscar(io.github.fogonapps.cajon.Secciones.MUSICA)) } }
                artistas.take(12).chunked(3).forEach { fila ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        fila.forEach { a ->
                            Column(Modifier.weight(1f).clip(forma(14.dp)).clickable { vm.abrir(Pantalla.VerArtista(a.id)) }, horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                FotoArtista(a.foto, a.nombre, 84.dp, a.tipo)
                                Text(a.nombre, style = MaterialTheme.typography.labelMedium, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                        repeat(3 - fila.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (artistas.size > 12) Row(Modifier.padding(16.dp)) { Pildora(stringResource(R.string.ver_todo)) { vm.abrir(Pantalla.Artistas) } }
            }
            3 -> {
                val listas = e.listasVivas
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listas.forEach { l ->
                        val obras = obrasDe(e, l)
                        val tapa = obras.firstOrNull { it.id == l.portada } ?: obras.firstOrNull()
                        FilaVidrio({ vm.abrir(Pantalla.VerLista(l.id)) }, adentro = true) {
                            Portada(tapa?.portada ?: "", tapa?.tipo ?: Tipos.PELICULA, "", Modifier.width(44.dp), esquina = 6.dp, conTitulo = false)
                            Column(Modifier.weight(1f)) {
                                Text(l.nombre, style = MaterialTheme.typography.bodyLarge, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(pluralStringResource(R.plurals.obras_n, obras.size, obras.size) + " · " + nombreVisibilidad(l.visibilidad),
                                style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                            }
                        }
                    }
                    Row { Pildora(stringResource(R.string.nueva_lista), Iconos.Mas) { vm.abrir(Pantalla.Listas) } }
                }
            }
            4 -> if (yo == null) NadaAqui() else yo?.let { u ->
                LaunchedEffect(u) { vm.red.cargarGuardadasDe(u) }
                val g = listasRed["guardadas:$u"]
                when {
                    g == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.tinta1, trackColor = f.linea)
                    g.isEmpty() -> NadaAqui()
                    else -> CuadriculaPublicaciones(vm, g)
                }
            }
            6 -> ResenasDelPerfil(vm, e)
            else -> CifrasDelPerfil(vm, e, anio)
        }
    }
    val secciones = seccionesCajon(true) { contenidoSeccion(it) }
    val contadores = buildList {
        val u = yo
        if (u != null && red != null) {
            add(ContadorPerfil("${red.siguiendo}", stringResource(R.string.red_siguiendo)) { vm.abrir(Pantalla.GenteRed(u, false)) })
            add(ContadorPerfil("${red.seguidores}", stringResource(R.string.red_seguidores)) { vm.abrir(Pantalla.GenteRed(u, true)) })
            add(ContadorPerfil("${e.vivas.size}", pluralStringResource(R.plurals.cifra_en_cajon, e.vivas.size, e.vivas.size, anio)) { pestana = 5 })
        } else contadoresPerfil(vm, e, anio).forEach { c -> add(ContadorPerfil("${c.valor}", pluralStringResource(c.etiqueta, c.valor, c.valor, anio), c.abrir)) }
    }
    val vistaBase = e.aPerfilVista(secciones = secciones, contadores = contadores, cuenta = cuenta).copy(nombre = e.ajustes.nombre.ifBlank { stringResource(R.string.tu_nombre) })
    PerfilComun(vistaBase, pestana, { pestana = it }, Modifier.aparecer(0), nombreSuave = e.ajustes.nombre.isBlank(), interlineado = true,
        navegacion = navegacion,
        portada = { if (p.portada.isNotBlank()) ImagenEncuadrada(p.portada, p.encuadrePortada, Modifier.matchParentSize()) },
        foto = {
            Box {
                io.github.fogonapps.social.ui.AroHistoriasPropias(interfazSocial(vm)) { FotoPerfil(p.foto, 96.dp, p.encuadreFoto, borde = true) }
                if (acciones != null) Box(Modifier.align(Alignment.BottomEnd).offset(x = 10.dp, y = 10.dp).size(48.dp).clip(CircleShape)
                    .clickable { acciones.elegirImagen { vm.subirImagenPerfil(false, it) } }
                    .semantics { contentDescription = descFoto; role = Role.Button }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(f.tinta1).border(2.dp, f.fondo, CircleShape), contentAlignment = Alignment.Center) {
                        IconoRiso(Iconos.Mas, null, tamano = 16.dp, tinta = f.sobreTinta1)
                    }
                }
            }
        },

        enlace = { u -> Pildora(Logica.enlaceCorto(u), Iconos.Enlace) { runCatching { uri.openUri(u) } } },
        antesBio = {
            val enCurso = e.vivas.filter { it.estado == io.github.fogonapps.cajon.datos.Estados.EN_CURSO }.maxByOrNull { maxOf(it.empezada ?: "", vm.L.ultimaVez(it) ?: "") }
            enCurso?.let { o ->
                Row(Modifier.clip(FormaPildora).background(f.superficie).border(f.borde, f.linea, FormaPildora)
                    .clickable { vm.abrir(Pantalla.Ficha(o.id)) }.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconoRiso(Iconos.de(o.tipo), null, tamano = 16.dp, tinta = f.acentoTexto)
                    val pagina = if (o.tipo == Tipos.LIBRO && o.pagina > 0 && o.paginas != null) " · " + stringResource(R.string.chip_pagina, o.pagina, o.paginas) else ""
                    Text(stringResource(R.string.chip_ahora, o.titulo) + pagina, style = MaterialTheme.typography.labelLarge, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        acciones = {
            BotonSecundario(stringResource(R.string.editar_perfil), { vm.abrir(Pantalla.EditarPerfil) }, Modifier.weight(1f))
            val u = yo
            if (u == null) BotonRiso(stringResource(R.string.red_crear_cuenta), { vm.abrir(Pantalla.CuentaRed) }, Modifier.weight(1f))
            else BotonSecundario(stringResource(R.string.compartir_perfil), {
                contexto.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND)
                    .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, io.github.fogonapps.cajon.datos.ClienteSocial.enlacePerfil(u)), null))
        }, Modifier.weight(1f))
        },
        despuesAcciones = {
            vm.red.usuario.collectAsStateWithLifecycle().value?.let { u -> Box(Modifier.padding(horizontal = 16.dp)) { io.github.fogonapps.social.ui.DestacadasPerfil(interfazSocial(vm), u) } }
            SelectorPerfiles(vm)
            CrearClaveAviso(vm)
            io.github.fogonapps.cajon.RedSocial.cancionDe(e)?.let { c ->
                Box(Modifier.padding(vertical = 8.dp)) { TarjetaCancion(c.titulo, c.artista, c.album, c.portada, audio = c.audio, enlace = c.enlace, onCambiar = { vm.abrir(Pantalla.ElegirCancion) }) }
            } ?: BotonSecundario(stringResource(R.string.cancion_poner), { vm.abrir(Pantalla.ElegirCancion) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
        })
}

/** Lo que sale en una pestaña del perfil que no tiene nada (o que la persona no deja ver). */
@Composable
fun NadaAqui() = PerfilVacio(Iconos.Cajon, stringResource(R.string.nada_aqui))

/** La pestaña de cifras del perfil: las que eligió mostrar, su evolución por año y, solo para la persona, lo que la app aprendió de sus gustos. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CifrasDelPerfil(vm: CajonViewModel, e: Estado, anio: Int) {
    val f = FogonTema.actual
    val cifras = e.perfil.cifras ?: PartesPerfil.CIFRAS_DE_SIEMPRE
    val delAnio = vm.L.cifras(e, anio)
    val siempre = vm.L.cifras(e, null)
    fun valor(c: String): String = io.github.fogonapps.cajon.RedSocial.valorCifra(c, e, vm.hoy())
    cifras.chunked(2).forEach { fila ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            fila.forEach { c ->
                Column(Modifier.weight(1f).vidrio().clickable { vm.abrir(Pantalla.Cifras) }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(valor(c), fontFamily = f.cifras, fontSize = 26.sp, color = f.texto)
                    Text(nombreCifra(c, anio), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                }
            }
            if (fila.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pildora(stringResource(R.string.tus_cifras), Iconos.Cifras, acento = true) { vm.abrir(Pantalla.Cifras) }
    }
    val anios = vm.L.evolucion(e)
    if (anios.isNotEmpty()) {
        Seccion(stringResource(R.string.parte_evolucion))
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            anios.forEach { a ->
                Row(Modifier.fillMaxWidth().vidrio().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${a.anio}", fontFamily = f.cifras, fontSize = 24.sp, color = f.texto)
                        Text(stringResource(R.string.anio_resumen, a.registros) + (a.genero?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 2)
                    }
                    a.mejores.forEach { o ->
                        Box(Modifier.width(44.dp).clip(forma(6.dp)).clickable { vm.abrir(Pantalla.Ficha(o.id)) }) {
                            Portada(o.portada, o.tipo, o.titulo, Modifier.fillMaxWidth(), esquina = 6.dp, conTitulo = false)
                        }
                    }
                }
            }
        }
    }
    val g = remember(e) { vm.perfil() }
    val positivos = (g.generos + g.personas).filter { it.peso > 0 && !it.quitado }
    if (positivos.isNotEmpty()) {
        Seccion(stringResource(R.string.solo_tu))
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            positivos.take(12).forEach { Etiqueta(it.nombre, f.superficie, f.texto) }
        }
    }
}

/** Editar el perfil: foto, portada, nombre, usuario, biografía, enlaces, quién ve cada parte y qué cifras se muestran. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditarPerfil(vm: CajonViewModel, e: Estado) {
    val f = FogonTema.actual
    val acciones = LocalAcciones.current
    val p = e.perfil
    var nombre by rememberSaveable { mutableStateOf(e.ajustes.nombre) }
    val usuario = p.usuario
    val cuenta by vm.red.cuenta.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.red.revisarServidor() }
    var bio by rememberSaveable { mutableStateOf(p.bio) }
    var enlaces by rememberSaveable { mutableStateOf(p.enlaces.joinToString("\n")) }
    Cabecera(stringResource(R.string.editar_perfil), vm::cerrar)

    Seccion(stringResource(R.string.foto_y_portada))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FotoPerfil(p.foto, 72.dp, p.encuadreFoto)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (acciones != null) BotonSecundario(stringResource(R.string.elegir_foto), { acciones.elegirImagen { vm.subirImagenPerfil(false, it) } }, Modifier.fillMaxWidth())
                if (p.foto.isNotBlank()) {
                    BotonSecundario(stringResource(R.string.ajustar_foto), { vm.abrir(Pantalla.AjustarImagenPerfil(false)) }, Modifier.fillMaxWidth())
                    BotonSecundario(stringResource(R.string.quitar_foto), { vm.quitarImagenPerfil(false) }, Modifier.fillMaxWidth())
                }
            }
        }
        // La misma franja que en el perfil (110 dp de alto), para que lo que se ve aquí sea lo que se ve allá.
        Box(Modifier.fillMaxWidth().height(110.dp).clip(forma(24.dp)).vidrio(forma(24.dp)).haloPuntos(f.tinta2.copy(alpha = 0.35f))) {
            if (p.portada.isNotBlank()) ImagenEncuadrada(p.portada, p.encuadrePortada, Modifier.matchParentSize())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonSecundario(stringResource(R.string.portada_del_cajon), { vm.abrir(Pantalla.PortadaPerfil) }, Modifier.weight(1f))
            if (acciones != null) BotonSecundario(stringResource(R.string.subir_imagen), { acciones.elegirImagen { vm.subirImagenPerfil(true, it) } }, Modifier.weight(1f))
        }
        if (p.portada.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonSecundario(stringResource(R.string.ajustar_portada), { vm.abrir(Pantalla.AjustarImagenPerfil(true)) }, Modifier.weight(1f))
            BotonSecundario(stringResource(R.string.quitar_portada), { vm.quitarImagenPerfil(true) }, Modifier.weight(1f))
        }
    }

    Seccion(stringResource(R.string.quien_eres))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Cualquier letra: la persona copia su nombre de una página de letras (son caracteres de Unicode) y lo pega
        // aquí. La app no ofrece letras; el límite cuenta letras de verdad, no unidades de UTF-16 (ver Logica.recortar).
        OutlinedTextField(nombre, { nombre = Logica.recortar(it, 40) }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.tu_nombre)) })
        cuenta?.apodoDesde?.takeIf { it > System.currentTimeMillis() }?.let { desde ->
            Pista(stringResource(R.string.red_apodo_limite, java.text.DateFormat.getDateTimeInstance().format(java.util.Date(desde))))
        }
        OutlinedTextField(bio, { bio = it.take(200) }, Modifier.fillMaxWidth(), minLines = 3, label = { Text(stringResource(R.string.bio)) },
            supportingText = { Text("${bio.length}/200") })
        OutlinedTextField(enlaces, { enlaces = it.take(1000) }, Modifier.fillMaxWidth(), minLines = 2, label = { Text(stringResource(R.string.enlaces)) },
            supportingText = { Text(stringResource(R.string.enlaces_pista)) })
        BotonRiso(stringResource(R.string.guardar), { vm.guardarIdentidad(nombre, usuario, bio, enlaces.lines()) }, Modifier.fillMaxWidth())
    }

    Seccion(stringResource(R.string.quien_lo_ve))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PartesPerfil.TODAS.forEach { parte ->
            Column(Modifier.fillMaxWidth().vidrio().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(nombreParte(parte), style = MaterialTheme.typography.bodyLarge, color = f.texto)
                ElegirVisibilidad(p.visibilidad[parte] ?: "") { vm.visibilidadPerfil(parte, it) }
            }
        }
    }

    Seccion(stringResource(R.string.cifras_a_mostrar))
    val elegidas = p.cifras ?: PartesPerfil.CIFRAS_DE_SIEMPRE
    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PartesPerfil.CIFRAS_TODAS.forEach { c ->
            val si = c in elegidas
            Pildora(nombreCifra(c, vm.hoy().year), if (si) Iconos.Chulo else null, acento = si) { vm.alternarCifraPerfil(c) }
        }
    }
    Espacio(40.dp)
}

/**
 * Elegir lo que va en un hueco de destacados, de lo que ya está en el cajón (del tipo del hueco; "me marcó" y
 * "descubrimiento", cualquier obra, con unas palabras de por qué).
 */
/** En qué sección del buscador se busca cada hueco de los destacados. */
private fun seccionDeHueco(h: String) = when (h) {
    Destacados.PELICULA -> io.github.fogonapps.cajon.Secciones.PELICULAS; Destacados.SERIE -> io.github.fogonapps.cajon.Secciones.SERIES
    Destacados.ANIME -> io.github.fogonapps.cajon.Secciones.ANIMES; Destacados.LIBRO -> io.github.fogonapps.cajon.Secciones.LIBROS
    Destacados.ALBUM, Destacados.ARTISTA -> io.github.fogonapps.cajon.Secciones.MUSICA; else -> io.github.fogonapps.cajon.Secciones.TODO
}

@Composable
fun ElegirDestacado(vm: CajonViewModel, e: Estado, hueco: String) {
    val f = FogonTema.actual
    val p = e.perfil
    val actual = p.destacados[hueco]
    val conPalabras = hueco == Destacados.MARCO || hueco == Destacados.DESCUBRIMIENTO
    var porQue by rememberSaveable { mutableStateOf(p.porQue[hueco] ?: "") }
    var texto by rememberSaveable { mutableStateOf("") }
    Cabecera(nombreHueco(hueco), vm::cerrar)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (conPalabras) {
            OutlinedTextField(porQue, { porQue = it.take(280) }, Modifier.fillMaxWidth(), minLines = 2,
                label = { Text(stringResource(R.string.por_que)) }, supportingText = { Text(stringResource(R.string.por_que_pista)) })
            if (actual != null) BotonRiso(stringResource(R.string.guardar), { vm.ponerDestacado(hueco, actual, porQue); vm.cerrar() }, Modifier.fillMaxWidth())
        }
        if (actual != null) BotonSecundario(stringResource(R.string.quitar_de_aqui), { vm.ponerDestacado(hueco, null); vm.cerrar() }, Modifier.fillMaxWidth())
        BotonRiso(stringResource(R.string.buscar_en_internet), { vm.abrir(Pantalla.Buscar(seccionDeHueco(hueco), "destacado:$hueco")) }, Modifier.fillMaxWidth())
        CampoTexto(texto, { texto = it }, stringResource(R.string.buscar_en_tu_cajon), Modifier.fillMaxWidth())
    }
    val q = Logica.tituloLimpio(texto)
    if (hueco == Destacados.ARTISTA) {
        val lista = e.musicosVivos.filter { q.isEmpty() || q in Logica.tituloLimpio(it.nombre) }
            .sortedWith(compareByDescending<io.github.fogonapps.cajon.datos.Artista> { it.id == actual }.thenByDescending { it.favorito }.thenByDescending { it.nota }.thenBy { it.nombre }).take(60)
        if (lista.isEmpty()) Pista(stringResource(R.string.sin_resultados))
        lista.chunked(3).forEach { fila ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                fila.forEach { a ->
                    Column(Modifier.weight(1f).clip(forma(12.dp)).clickable { vm.ponerDestacado(hueco, a.id); vm.cerrar() },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FotoArtista(a.foto, a.nombre, 92.dp, a.tipo)
                        Text(a.nombre, style = MaterialTheme.typography.labelLarge, color = if (a.id == actual) f.acentoTexto else f.texto, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(3 - fila.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    } else {
        val tipo = when (hueco) { Destacados.PELICULA -> Tipos.PELICULA; Destacados.SERIE -> Tipos.SERIE; Destacados.ANIME -> Tipos.ANIME
            Destacados.ALBUM -> Tipos.ALBUM; Destacados.LIBRO -> Tipos.LIBRO; else -> null }
        val lista = e.vivas.filter { (tipo == null || it.tipo == tipo) && (q.isEmpty() || q in Logica.tituloLimpio(it.titulo + " " + it.creador)) }
            .sortedWith(compareByDescending<Obra> { it.id == actual }.thenByDescending { it.nota }.thenByDescending { it.meGusta }.thenBy { it.titulo }).take(60)
        if (lista.isEmpty()) Pista(stringResource(R.string.sin_resultados))
        lista.chunked(3).forEach { fila ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                fila.forEach { o ->
                    Column(Modifier.weight(1f).clip(forma(12.dp)).clickable { vm.ponerDestacado(hueco, o.id, if (conPalabras) porQue else null); vm.cerrar() },
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Portada(o.portada, o.tipo, o.titulo, Modifier.fillMaxWidth(), conTitulo = false)
                        Text(o.titulo, style = MaterialTheme.typography.labelLarge, color = if (o.id == actual) f.acentoTexto else f.texto, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(3 - fila.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    Espacio(40.dp)
}

/** Elegir la portada del perfil entre los fondos de las películas, series y animes del cajón. */
@Composable
fun PortadaPerfil(vm: CajonViewModel, e: Estado) {
    Cabecera(stringResource(R.string.portada_del_cajon), vm::cerrar)
    val fondos = e.vivas.filter { it.fondo.isNotBlank() }.sortedWith(compareByDescending<Obra> { it.nota }.thenByDescending { it.meGusta }).take(40)
    if (fondos.isEmpty()) { Pista(stringResource(R.string.sin_fondos)); return }
    Pista(stringResource(R.string.portada_del_cajon_pista))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        fondos.forEach { o ->
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(forma(16.dp)).vidrio(forma(16.dp)).clickable { vm.ponerPortadaPerfil(o.fondo) }) {
                coil3.compose.AsyncImage(o.fondo, o.titulo, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            }
        }
    }
    Espacio(40.dp)
}

/**
 * Ajustar el tamaño y la posición de la foto ([portada] = false, en su círculo) o de la portada (en su franja de 110 dp,
 * la misma del perfil): pellizcar acerca y aleja, arrastrar mueve. Los deslizadores hacen lo mismo sin gestos (y
 * los lectores de pantalla los anuncian). Nada cambia hasta Guardar.
 */
@Composable
fun AjustarImagenPerfil(vm: CajonViewModel, e: Estado, portada: Boolean) {
    val f = FogonTema.actual
    val p = e.perfil
    val url = if (portada) p.portada else p.foto
    val inicial = (if (portada) p.encuadrePortada else p.encuadreFoto).limpio()
    var zoom by rememberSaveable { mutableFloatStateOf(inicial.zoom) }
    var x by rememberSaveable { mutableFloatStateOf(inicial.x) }
    var y by rememberSaveable { mutableFloatStateOf(inicial.y) }
    // El tamaño de la imagen (para saber cuánto sobra a cada lado y que el arrastre siga al dedo).
    var imagen by remember { mutableStateOf(Size.Unspecified) }
    Cabecera(stringResource(if (portada) R.string.ajustar_portada else R.string.ajustar_foto), vm::cerrar)
    if (url.isBlank()) { Pista(stringResource(R.string.ajustar_sin_imagen)); return }
    Pista(stringResource(R.string.ajustar_pista))

    val marco = if (portada) Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(110.dp).clip(forma(24.dp))
    else Modifier.size(240.dp).clip(CircleShape)
    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        Box(marco.background(f.superficie).border(2.dp, f.tinta1, if (portada) forma(24.dp) else CircleShape)
            .pointerInput(Unit) {
                detectTransformGestures { _, mover, acercar, _ ->
                    zoom = (zoom * acercar).coerceIn(1f, Encuadre.ZOOM_MAX)
                    val img = imagen
                    if (img == Size.Unspecified || img.width <= 0f || img.height <= 0f) return@detectTransformGestures
                    val w = size.width.toFloat(); val h = size.height.toFloat()
                    val s = max(w / img.width, h / img.height) * zoom
                    // Lo que sobra de la imagen a lo ancho y a lo alto; correr el dedo d píxeles corre la posición 2d/sobra.
                    val sobraX = img.width * s - w; val sobraY = img.height * s - h
                    if (sobraX > 0.5f) x = (x - 2f * mover.x / sobraX).coerceIn(-1f, 1f)
                    if (sobraY > 0.5f) y = (y - 2f * mover.y / sobraY).coerceIn(-1f, 1f)
                }
            }) {
            ImagenEncuadrada(url, Encuadre(zoom, x, y), Modifier.matchParentSize()) { imagen = it }
        }
    }

    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        @Composable
        fun Deslizador(nombre: String, valor: Float, rango: ClosedFloatingPointRange<Float>, estado: String, onCambio: (Float) -> Unit) {
            Text(nombre, style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
            Slider(valor, onCambio, Modifier.fillMaxWidth().semantics { stateDescription = estado }, valueRange = rango)
        }
        Deslizador(stringResource(R.string.ajustar_tamano), zoom, 1f..Encuadre.ZOOM_MAX, "${(zoom * 100).roundToInt()} %") { zoom = it }
        Deslizador(stringResource(R.string.ajustar_horizontal), x, -1f..1f, "${((x + 1) * 50).roundToInt()} %") { x = it }
        Deslizador(stringResource(R.string.ajustar_vertical), y, -1f..1f, "${((y + 1) * 50).roundToInt()} %") { y = it }
    }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BotonSecundario(stringResource(R.string.ajustar_restablecer), { zoom = 1f; x = 0f; y = 0f }, Modifier.weight(1f))
        BotonRiso(stringResource(R.string.guardar), { vm.encuadrePerfil(portada, Encuadre(zoom, x, y)) }, Modifier.weight(1f))
    }
    Espacio(40.dp)
}

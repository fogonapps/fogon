package io.github.fogonapps.cajon.ui

import io.github.fogonapps.perfil.*
import io.github.fogonapps.perfil.Perfil as PerfilComun
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.selectableGroup
import io.github.fogonapps.perfil.PROPORCION_PORTADA
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
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
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.forma
import io.github.fogonapps.identidad.rebote

@Composable
private fun nombreHueco(h: String): String = stringResource(when (h) {
    Destacados.PELICULA -> R.string.dest_pelicula; Destacados.SERIE -> R.string.dest_serie; Destacados.ANIME -> R.string.dest_anime
    Destacados.ALBUM -> R.string.dest_album; Destacados.LIBRO -> R.string.dest_libro; Destacados.JUEGO -> R.string.dest_juego
    Destacados.ARTISTA -> R.string.dest_artista; Destacados.MARCO -> R.string.dest_marco; else -> R.string.dest_descubrimiento
})

@Composable
internal fun nombreVisibilidad(v: String): String = stringResource(when (Visibilidad.de(v)) {
    Visibilidad.PUBLICA -> R.string.vis_publica; Visibilidad.AMIGOS -> R.string.vis_amigos; else -> R.string.vis_privada
})

@Composable
private fun nombreParte(p: String): String = stringResource(when (p) {
    PartesPerfil.DESTACADOS -> R.string.parte_destacados; PartesPerfil.FAVORITAS -> R.string.parte_cuatro_favoritas
    PartesPerfil.CIFRAS -> R.string.parte_cifras; PartesPerfil.EVOLUCION -> R.string.parte_evolucion
    PartesPerfil.MUSICA -> R.string.parte_musica; PartesPerfil.GUSTOS -> R.string.parte_gustos; PartesPerfil.GUARDADAS -> R.string.parte_guardadas
    PartesPerfil.RETO -> R.string.parte_reto; PartesPerfil.VIENDO -> R.string.viendo_ahora; PartesPerfil.POR_VER -> R.string.parte_por_ver
    PartesPerfil.INSIGNIAS -> R.string.insignias_titulo
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
    if (url.startsWith("banner:")) { io.github.fogonapps.perfil.BannerPerfil(url.removePrefix("banner:"), modifier); return }
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ElegirVisibilidad(valor: String, onElegir: (String) -> Unit) {
    val elegida = Visibilidad.de(valor)
    FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Visibilidad.TODAS.forEach { v -> ChipPerfil(nombreVisibilidad(v), v == elegida) { onElegir(v) } }
    }
}

/**
 * La foto del perfil: redonda, o el ícono de persona en un círculo si no hay. Con [onClick] se puede tocar
 * (el avatar del Inicio abre el perfil) y [descripcion] es lo que dice el lector de pantalla.
 */
@Composable
fun FotoPerfil(foto: String, tamano: androidx.compose.ui.unit.Dp, encuadre: Encuadre = Encuadre(), borde: Boolean = false, descripcion: String? = null, onClick: (() -> Unit)? = null) {
    val f = FogonTema.actual
    Box(Modifier.then(if (onClick != null) Modifier.rebote(0.9f) else Modifier).size(tamano).clip(CircleShape).background(tonoPapel())
        .border(if (borde) 4.dp else f.borde, if (borde) f.fondo else f.linea, CircleShape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .then(if (descripcion != null) Modifier.semantics { contentDescription = descripcion; if (onClick != null) role = Role.Button } else Modifier),
        contentAlignment = Alignment.Center) {
        IconoRiso(Iconos.Persona, null, tamano = tamano * 0.42f, tinta = f.textoSuave)
        if (foto.isNotBlank()) ImagenEncuadrada(foto, encuadre, Modifier.matchParentSize().clip(CircleShape))
    }
}

/** El botón principal: una píldora en tinta con el texto en papel. */
@Composable
internal fun BotonPildora(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, habilitado: Boolean = true) =
    io.github.fogonapps.identidad.BotonRiso(texto, onClick, modifier, habilitado)

/** El botón secundario: una píldora con borde de 1 dp en tinta, sin relleno. */
@Composable
internal fun BotonPildoraBorde(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, icono: io.github.fogonapps.identidad.Dibujo? = null) {
    val f = FogonTema.actual
    Row(modifier.rebote().heightIn(min = 48.dp).clip(FormaPildora).border(1.dp, f.texto, FormaPildora).clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        if (icono != null) IconoRiso(icono, null, tamano = 18.dp, tinta = f.texto)
        // Si no cabe, la letra se achica antes de cortarse («Compartir p…», «+ P…» en pantalla angosta con letra grande).
        Text(texto, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = f.texto,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = MaterialTheme.typography.labelLarge.fontSize))
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
    val descFoto = stringResource(R.string.poner_foto)
    var pestana by rememberSaveable { mutableStateOf(PESTANA_INICIAL) }

    // Arriba: con cuenta de moderación, su espacio (con lo pendiente); siempre, los ajustes.
    val cuenta by vm.red.cuenta.collectAsStateWithLifecycle()
    val casos by vm.red.casos.collectAsStateWithLifecycle()
    val frasesPendientes by vm.red.frasesPendientes.collectAsStateWithLifecycle()
    LaunchedEffect(cuenta?.permisos) { if (!cuenta?.permisos.isNullOrEmpty()) vm.red.cargarCasos() }
    val navegacion: @Composable () -> Unit = {
        val f = FogonTema.actual // los colores del fondo del perfil, no los de la app
        Cabecera(stringResource(R.string.tu_perfil), vm::cerrar, chico = true) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!cuenta?.permisos.isNullOrEmpty()) ConCuenta(casos.orEmpty().size + frasesPendientes.size) {
                    BotonIcono(Iconos.Bandera, stringResource(R.string.mod_titulo)) { vm.abrir(Pantalla.Moderacion) }
                }
                BotonIcono(Iconos.Ajustes, stringResource(R.string.ajustes)) { vm.abrir(Pantalla.Ajustes) }
            }
        }
    }

    fun obra(id: String?) = id?.let { i -> e.vivas.firstOrNull { it.id == i } }

    @Composable
    fun contenidoParte(idSeccion: Int) {
        val f = FogonTema.actual // los colores del fondo del perfil, no los de la app
        // Quién ve cada parte se elige en «Editar perfil» (ver QuienVeTuPerfil), no aquí.
        when (idSeccion) {
            0 -> when {
                yo == null -> NadaAqui()
                red == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.texto, trackColor = f.linea)
                red.publicaciones.isEmpty() -> Row(Modifier.padding(16.dp)) { BotonPildora(stringResource(R.string.red_publicar), { vm.abrir(Pantalla.Publicar()) }) }
                else -> CuadriculaPublicaciones(vm, red.publicaciones)
            }
            1 -> LoTuyoDelPerfil(vm, e, anio)
            6 -> ResenasPropias(vm, e)
            else -> CifrasDelPerfil(vm, e, anio)
        }
    }

    val secciones = seccionesCajon(true) { contenidoParte(it) }
    val contadores = buildList {
        val u = yo
        if (u != null && red != null) {
            add(ContadorPerfil("${red.siguiendo}", stringResource(R.string.red_siguiendo)) { vm.abrir(Pantalla.GenteRed(u, false)) })
            add(ContadorPerfil("${red.seguidores}", stringResource(R.string.red_seguidores)) { vm.abrir(Pantalla.GenteRed(u, true)) })
            add(ContadorPerfil("${e.vivas.size}", pluralStringResource(R.plurals.cifra_en_cajon, e.vivas.size, e.vivas.size, anio)) { pestana = 5 })
        } else contadoresPerfil(vm, e, anio).forEach { c -> add(ContadorPerfil("${c.valor}", pluralStringResource(c.etiqueta, c.valor, c.valor, anio), c.abrir)) }
    }
    val vistaBase = e.aPerfilVista(secciones = secciones, contadores = contadores, cuenta = cuenta).copy(nombre = e.ajustes.nombre.ifBlank { stringResource(R.string.tu_nombre) })
    val marco = io.github.fogonapps.social.ui.marcoPropio(interfazSocial(vm))
    val cambiarPortada = stringResource(R.string.perfil_cambiar_portada)
    // Tocar tu marco lleva a los marcos de la tienda; compartir el perfil arma una tarjeta con tu portada.
    var tiendaMarcos by rememberSaveable { mutableStateOf(false) }
    var compartiendo by rememberSaveable { mutableStateOf(false) }
    val etiquetaMarco = stringResource(io.github.fogonapps.social.ui.R.string.social_marco_propio)
    val portadaPropia: @Composable (Modifier) -> Unit = { m ->
        if (p.portada.isNotBlank()) { if (Regex("[A-Za-z0-9_-]{22}").matches(p.portada) || p.portada.startsWith("banner:")) io.github.fogonapps.social.ui.PortadaPerfilRed(p.portada, m, interfazSocial(vm)) else ImagenEncuadrada(p.portada, p.encuadrePortada, m) }
        else PortadaPredeterminada(vistaBase.nombre, m)
    }
    if (tiendaMarcos) io.github.fogonapps.social.ui.TiendaTemas(interfazSocial(vm), inicio = "marco") { tiendaMarcos = false }
    val u0 = yo
    if (compartiendo && acciones != null && u0 != null) DialogoCompartir({ compartiendo = false }, { acciones.compartir(it, io.github.fogonapps.cajon.datos.ClienteSocial.enlacePerfil(u0)) }) {
        TarjetaPerfil(vistaBase.nombre, u0, marco, portadaPropia) { FotoPerfil(p.foto, 72.dp, p.encuadreFoto) }
    }
    PerfilComun(vistaBase.copy(tema = io.github.fogonapps.social.ui.temaPropio(interfazSocial(vm)), marco = marco,
        tocarMarco = if (yo != null) ({ tiendaMarcos = true }) else null, etiquetaMarco = etiquetaMarco), pestana, { pestana = it }, nombreSuave = e.ajustes.nombre.isBlank(), interlineado = true,
        navegacion = navegacion,
        portada = {
            if (p.portada.isNotBlank()) { if (Regex("[A-Za-z0-9_-]{22}").matches(p.portada)) io.github.fogonapps.social.ui.PortadaPerfilRed(p.portada, Modifier.matchParentSize(), interfazSocial(vm)) else ImagenEncuadrada(p.portada, p.encuadrePortada, Modifier.matchParentSize()) }
            // Sin portada propia, la predeterminada la pone la cabecera. Cambiarla: un botón discreto en la esquina.
            Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = cambiarPortada) { vm.abrir(Pantalla.PortadaPerfil) }
                .semantics { contentDescription = cambiarPortada }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(f.fondo.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                    IconoRiso(Iconos.Lapiz, null, tamano = 16.dp, tinta = f.texto)
                }
            }
        },
        foto = {
            val f = FogonTema.actual // los colores del fondo del perfil, no los de la app
            Box {
                io.github.fogonapps.social.ui.AroHistoriasPropias(interfazSocial(vm)) { FotoPerfil(p.foto, 96.dp, p.encuadreFoto, borde = true) }
                if (acciones != null && p.foto.isBlank() && red?.persona?.foto != true && marco.isBlank()) Box(Modifier.align(Alignment.BottomEnd).offset(x = 10.dp, y = 10.dp).size(48.dp).clip(CircleShape)
                    .clickable { acciones.elegirImagen { vm.subirImagenPerfil(false, it) } }
                    .semantics { contentDescription = descFoto; role = Role.Button }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(f.texto).border(2.dp, f.fondo, CircleShape), contentAlignment = Alignment.Center) {
                        IconoRiso(Iconos.Mas, null, tamano = 16.dp, tinta = f.fondo)
                    }
                }
            }
        },

        enlace = { u -> EnlacePerfil(u) { runCatching { uri.openUri(u) } } },
        acciones = {
            // Dos estilos solamente: la píldora en tinta (lo principal) y la de borde.
            BotonPildoraBorde(stringResource(R.string.editar_perfil), { vm.abrir(Pantalla.EditarPerfil) }, Modifier.weight(1f))
            val u = yo
            if (u == null) BotonPildora(stringResource(R.string.red_crear_cuenta), { vm.abrir(Pantalla.CuentaRed) }, Modifier.weight(1f))
            else BotonPildoraBorde(stringResource(R.string.compartir_perfil), {
                if (acciones != null) { compartiendo = true; return@BotonPildoraBorde }
                contexto.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND)
                    .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, io.github.fogonapps.cajon.datos.ClienteSocial.enlacePerfil(u)), null))
            }, Modifier.weight(1f))
        },
        despuesAcciones = {
            // Debajo de los botones, en este orden: la canción, las favoritas (protagonistas), la que te marcó y las
            // repisas. Lo que está vacío no se ve; se agrega desde «Editar perfil».
            io.github.fogonapps.cajon.RedSocial.cancionDe(e)?.let { c ->
                Box(Modifier.padding(vertical = 4.dp)) { TarjetaCancion(c.titulo, c.artista, c.album, c.portada, audio = c.audio, enlace = c.enlace, onCambiar = { vm.abrir(Pantalla.ElegirCancion) }) }
            }
            FavoritasDelPerfil(favoritasPropias(vm, e)) { vm.abrir(Pantalla.EditarVitrina) }
            obra(p.destacados[Destacados.MARCO])?.let { o ->
                DestacadoConPalabras(nombreHueco(Destacados.MARCO), o.titulo, p.porQue[Destacados.MARCO], { vm.abrir(Pantalla.Ficha(o.id)) },
                    { vm.abrir(Pantalla.ElegirDestacado(Destacados.MARCO)) }) { m -> Portada(o.portada, o.tipo, o.titulo, m, esquina = 10.dp, conTitulo = false) }
            }
            yo?.let { u -> EscaparatesPerfilPropio(vm, e, u) }
        })
}

/** Las favoritas de tu perfil (la vitrina), en el orden que elegiste; mantener presionada una abre la edición. */
@Composable
private fun favoritasPropias(vm: CajonViewModel, e: Estado): List<Favorita> = Logica.vitrina(e).mapNotNull { id ->
    val cambiar = { vm.abrir(Pantalla.EditarVitrina) }
    e.vivas.firstOrNull { it.id == id }?.let { o ->
        Favorita(nombreTipo(o.tipo, plural = false), o.titulo, { vm.abrir(Pantalla.Ficha(o.id)) }, cambiar) { m -> Portada(o.portada, o.tipo, o.titulo, m, esquina = 10.dp, conTitulo = false) }
    } ?: e.artistasVivos.firstOrNull { it.id == id }?.let { a ->
        Favorita(nombreTipo("artista", plural = false), a.nombre, { vm.abrir(Pantalla.VerArtista(a.id)) }, cambiar) { m -> FotoEnMarco(a.foto, a.nombre, a.tipo, m) }
    }
}

/**
 * La pestaña «Lo tuyo»: lo que estás viendo, tu descubrimiento, el reto del año, las insignias y tus artistas. Lo
 * que no tiene nada no se ve.
 */
@Composable
private fun LoTuyoDelPerfil(vm: CajonViewModel, e: Estado, anio: Int) {
    val f = FogonTema.actual
    val p = e.perfil
    val enCurso = e.vivas.filter { it.estado == io.github.fogonapps.cajon.datos.Estados.EN_CURSO }.maxByOrNull { maxOf(it.empezada ?: "", vm.L.ultimaVez(it) ?: "") }
    enCurso?.let { o ->
        TituloPerfil(stringResource(R.string.viendo_ahora))
        Row(Modifier.fillMaxWidth().clickable { vm.abrir(Pantalla.Ficha(o.id)) }.padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Portada(o.portada, o.tipo, o.titulo, Modifier.width(56.dp), esquina = 8.dp, conTitulo = false)
            Column(Modifier.weight(1f)) {
                Text(o.titulo, fontFamily = f.titulos, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val avance = if (o.tipo == Tipos.LIBRO && o.pagina > 0 && o.paginas != null) stringResource(R.string.chip_pagina, o.pagina, o.paginas)
                    else io.github.fogonapps.cajon.RedSocial.avanceDe(o)
                if (avance.isNotBlank()) Text(avance, style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
        }
    }
    e.vivas.firstOrNull { it.id == p.destacados[Destacados.DESCUBRIMIENTO] }?.let { o ->
        DestacadoConPalabras(nombreHueco(Destacados.DESCUBRIMIENTO), o.titulo, p.porQue[Destacados.DESCUBRIMIENTO], { vm.abrir(Pantalla.Ficha(o.id)) },
            { vm.abrir(Pantalla.ElegirDestacado(Destacados.DESCUBRIMIENTO)) }) { m -> Portada(o.portada, o.tipo, o.titulo, m, esquina = 10.dp, conTitulo = false) }
    }
    val metasReto = e.ajustes.retos.filterValues { it > 0 }.map { (t, meta) -> Triple(t, vm.L.cifras(e, anio).porTipo[t] ?: 0, meta) }
    RetoDelPerfil(stringResource(R.string.retos_del_anio, anio), metasReto) { vm.abrir(Pantalla.Retos) }
    FilaInsignias(io.github.fogonapps.cajon.datos.Insignias.de(e, anio), propias = true)
    // Los artistas con más discos en el cajón primero.
    val artistas = e.musicosVivos.sortedByDescending { a -> e.vivas.count { it.artista == a.id } }
    if (artistas.isNotEmpty()) FilaArtistasPerfil(artistas.map { it.nombre to it.foto }) { i -> vm.abrir(Pantalla.VerArtista(artistas[i].id)) }
    Espacio(24.dp)
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
    // La cifra grande en la letra de los títulos y su nombre debajo, sin caja.
    CifrasEnRejilla(cifras.map { valor(it) to nombreCifra(it, anio) }) { vm.abrir(Pantalla.Cifras) }
    Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BotonPildoraBorde(stringResource(R.string.tus_cifras), { vm.abrir(Pantalla.Cifras) }, icono = Iconos.Cifras)
    }
    val anios = vm.L.evolucion(e)
    if (anios.isNotEmpty()) {
        TituloPerfil(stringResource(R.string.parte_evolucion))
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            anios.forEach { a ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${a.anio}", fontFamily = f.titulos, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = f.texto)
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
        TituloPerfil(stringResource(R.string.solo_tu))
        FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            positivos.take(12).forEach {
                Text(it.nombre, style = MaterialTheme.typography.labelLarge, color = f.texto,
                    modifier = Modifier.clip(FormaPildora).border(1.dp, f.linea, FormaPildora).padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}

/** Tus reseñas en el perfil (también las privadas): portada, estrellas y un pedazo del texto, sin caja. */
@Composable
private fun ResenasPropias(vm: CajonViewModel, e: Estado) {
    val resenas = e.resenasDelPerfil()
    if (resenas.isEmpty()) { NadaAqui(); return }
    resenas.forEach { r ->
        val o = io.github.fogonapps.cajon.datos.Resenas.obra(e, r.obra)
        var destapada by rememberSaveable(r.id, r.spoiler) { mutableStateOf(false) }
        FilaResenaPerfil(o?.titulo ?: stringResource(R.string.resena_obra_entera), io.github.fogonapps.cajon.datos.Resenas.etiqueta(r.parte, o, vm.idioma() == "en"),
            r.nota, r.texto, nombreVisibilidad(r.visibilidad), r.spoiler, destapada, { destapada = true },
            onClick = { if (e.artistasVivos.any { it.id == r.obra }) vm.abrir(Pantalla.VerArtista(r.obra)) else vm.abrir(Pantalla.Ficha(r.obra)) }) { m ->
            Portada(o?.portada.orEmpty(), o?.tipo.orEmpty(), o?.titulo.orEmpty(), m, esquina = 8.dp, conTitulo = false)
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
    io.github.fogonapps.social.ui.TiendaEnEditarPerfil(interfazSocial(vm))

    Seccion(stringResource(R.string.foto_y_portada))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FotoPerfil(p.foto, 72.dp, p.encuadreFoto)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (acciones != null) BotonPildoraBorde(stringResource(R.string.elegir_foto), { acciones.elegirImagen { vm.subirImagenPerfil(false, it) } }, Modifier.fillMaxWidth())
                if (p.foto.isNotBlank()) {
                    BotonPildoraBorde(stringResource(R.string.ajustar_foto), { vm.abrir(Pantalla.AjustarImagenPerfil(false)) }, Modifier.fillMaxWidth())
                    BotonPildoraBorde(stringResource(R.string.quitar_foto), { vm.quitarImagenPerfil(false) }, Modifier.fillMaxWidth())
                }
            }
        }
        // La misma proporción que en el perfil, para que lo que se ve aquí sea lo que se ve allá. Sin portada, la predeterminada.
        Box(Modifier.fillMaxWidth().aspectRatio(PROPORCION_PORTADA).clip(forma(12.dp)), contentAlignment = Alignment.Center) {
            if (p.portada.isBlank()) PortadaPredeterminada(nombre.ifBlank { stringResource(R.string.tu_nombre) }, Modifier.matchParentSize())
            if (p.portada.isNotBlank()) if (Regex("[A-Za-z0-9_-]{22}").matches(p.portada)) io.github.fogonapps.social.ui.PortadaPerfilRed(p.portada, Modifier.matchParentSize(), interfazSocial(vm)) else ImagenEncuadrada(p.portada, p.encuadrePortada, Modifier.matchParentSize())
        }

        io.github.fogonapps.perfil.BannersGratis(vm::ponerPortadaPerfil, vm.red.temas.collectAsState().value.reclamados, vm.red.cuenta.collectAsStateWithLifecycle().value?.insignia.orEmpty(), p.portada)
        io.github.fogonapps.social.ui.PortadasPropias(interfazSocial(vm))
        if (p.portada.isNotBlank() && !p.portada.startsWith("banner:")) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!Regex("[A-Za-z0-9_-]{22}").matches(p.portada)) BotonPildoraBorde(stringResource(R.string.ajustar_portada), { vm.abrir(Pantalla.AjustarImagenPerfil(true)) }, Modifier.weight(1f))
            BotonPildoraBorde(stringResource(R.string.quitar_portada), { vm.quitarImagenPerfil(true) }, Modifier.weight(1f))
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
        // Así se verán: con el logo de cada red (o la dirección, si no es una red conocida).
        val validos = enlaces.lines().map { it.trim() }.filter { it.startsWith("https://") || it.startsWith("http://") }.take(5)
        if (validos.isNotEmpty()) androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            validos.forEach { u -> EnlacePerfil(u) {} }
        }
        BotonPildora(stringResource(R.string.guardar), { vm.guardarIdentidad(nombre, usuario, bio, enlaces.lines()) }, Modifier.fillMaxWidth())
    }

    TusFavoritasEnEditar(vm, e)

    Seccion(stringResource(R.string.perfil_cancion))
    val cancion = io.github.fogonapps.cajon.RedSocial.cancionDe(e)
    if (cancion != null) TarjetaCancion(cancion.titulo, cancion.artista, cancion.album, cancion.portada, audio = cancion.audio, enlace = cancion.enlace, onCambiar = { vm.abrir(Pantalla.ElegirCancion) })
    else Row(Modifier.padding(horizontal = 16.dp)) { BotonPildoraBorde(stringResource(R.string.cancion_poner), { vm.abrir(Pantalla.ElegirCancion) }, icono = Iconos.Mas) }

    Seccion(stringResource(R.string.cifras_a_mostrar))
    val elegidas = p.cifras ?: PartesPerfil.CIFRAS_DE_SIEMPRE
    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PartesPerfil.CIFRAS_TODAS.forEach { c ->
            val si = c in elegidas
            ChipPerfil(nombreCifra(c, vm.hoy().year), si, icono = if (si) Iconos.Chulo else null) { vm.alternarCifraPerfil(c) }
        }
    }
    Espacio(40.dp)
}

/**
 * Elegir las favoritas desde «Editar perfil»: todos los huecos en una fila (los elegidos con su portada, los demás
 * punteados), más «La que me marcó» y «Mi descubrimiento». El perfil muestra solo lo elegido.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TusFavoritasEnEditar(vm: CajonViewModel, e: Estado) {
    val f = FogonTema.actual
    val p = e.perfil
    fun obra(id: String?) = id?.let { i -> e.vivas.firstOrNull { it.id == i } }
    Seccion(stringResource(R.string.perfil_favoritas))
    Pista(stringResource(R.string.perfil_tus_favoritas_pista))
    val vitrina = Logica.vitrina(e)
    val editar = stringResource(R.string.vitrina_editar)
    androidx.compose.foundation.lazy.LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(vitrina.size) { i ->
            val id = vitrina[i]
            val o = obra(id)
            val a = if (o == null) e.artistasVivos.firstOrNull { it.id == id } else null
            Column(Modifier.width(96.dp).clip(forma(10.dp)).clickable(role = Role.Button, onClickLabel = editar) { vm.abrir(Pantalla.EditarVitrina) }
                .semantics(mergeDescendants = true) { contentDescription = o?.titulo ?: a?.nombre.orEmpty() },
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    o != null -> Portada(o.portada, o.tipo, o.titulo, Modifier.fillMaxWidth(), esquina = 10.dp, conTitulo = false)
                    a != null -> FotoEnMarco(a.foto, a.nombre, a.tipo, Modifier.fillMaxWidth())
                }
                Text(o?.titulo ?: a?.nombre.orEmpty(), style = MaterialTheme.typography.labelSmall, color = f.textoSuave, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (vitrina.size < Logica.MAX_VITRINA) item {
            Column(Modifier.width(96.dp).clip(forma(10.dp)).clickable(role = Role.Button) { vm.abrir(Pantalla.EditarVitrina) }
                .semantics(mergeDescendants = true) { contentDescription = editar }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HuecoVacio(Iconos.Mas, null, Modifier.fillMaxWidth(), esquina = 10.dp)
                Text(stringResource(R.string.vitrina_agregar_corto), style = MaterialTheme.typography.labelSmall, color = f.textoSuave, maxLines = 2)
            }
        }
    }
    listOf(Destacados.MARCO, Destacados.DESCUBRIMIENTO).forEach { h ->
        val o = obra(p.destacados[h])
        FilaVidrio({ vm.abrir(Pantalla.ElegirDestacado(h)) }) {
            if (o != null) Portada(o.portada, o.tipo, o.titulo, Modifier.width(40.dp), esquina = 6.dp, conTitulo = false)
            else IconoRiso(Iconos.Favorita, null, tamano = 22.dp, tinta = f.texto)
            Column(Modifier.weight(1f)) {
                Text(nombreHueco(h), style = MaterialTheme.typography.bodyLarge, color = f.texto)
                if (o != null) Text(o.titulo, style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconoRiso(if (o != null) Iconos.Lapiz else Iconos.Mas, null, tamano = 18.dp, tinta = f.textoSuave)
        }
    }
}

@Composable
internal fun PrivacidadPerfil(vm: CajonViewModel, e: Estado) {
    val f = FogonTema.actual
    val p = e.perfil
    Cabecera(stringResource(R.string.quien_lo_ve), vm::cerrar)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Cada parte por separado (lo que estás viendo y lo que tienes por ver empiezan privados).
        (PartesPerfil.TODAS + listOf(PartesPerfil.RETO, PartesPerfil.INSIGNIAS, PartesPerfil.VIENDO, PartesPerfil.POR_VER)).forEach { parte ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(nombreParte(parte), style = MaterialTheme.typography.bodyLarge, color = f.texto)
                // El reto y las insignias, mientras no elijas, van como tus destacados (lo que de verdad se publica).
                val actual = p.visibilidad[parte] ?: p.visibilidad[PartesPerfil.DESTACADOS].takeIf { parte == PartesPerfil.RETO || parte == PartesPerfil.INSIGNIAS }
                ElegirVisibilidad(actual ?: "") { vm.visibilidadPerfil(parte, it) }
            }
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
    Destacados.ALBUM, Destacados.ARTISTA -> io.github.fogonapps.cajon.Secciones.MUSICA; Destacados.JUEGO -> io.github.fogonapps.cajon.Secciones.JUEGOS
    else -> io.github.fogonapps.cajon.Secciones.TODO
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
            if (actual != null) BotonPildora(stringResource(R.string.guardar), { vm.ponerDestacado(hueco, actual, porQue); vm.cerrar() }, Modifier.fillMaxWidth())
        }
        if (actual != null) BotonPildoraBorde(stringResource(R.string.quitar_de_aqui), { vm.ponerDestacado(hueco, null); vm.cerrar() }, Modifier.fillMaxWidth())
        // Los de una película, serie, anime, álbum o libro favorito salen de las cuatro favoritas (la estrella de la ficha).
        if (hueco !in Logica.HUECOS_DE_FAVORITAS) {
            BotonPildora(stringResource(R.string.buscar_en_internet), { vm.abrir(Pantalla.Buscar(seccionDeHueco(hueco), "destacado:$hueco")) }, Modifier.fillMaxWidth())
            CampoTexto(texto, { texto = it }, stringResource(R.string.buscar_en_tu_cajon), Modifier.fillMaxWidth())
        }
    }
    val q = Logica.tituloLimpio(texto)
    if (hueco in Logica.HUECOS_DE_FAVORITAS) {
        val lista = Logica.tipoDeHueco(hueco)?.let { Logica.favoritasPara(e, it) }.orEmpty()
        if (lista.isEmpty()) Pista(stringResource(R.string.destacado_desde_favoritas))
        lista.chunked(3).forEach { fila ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                fila.forEach { o ->
                    Column(Modifier.weight(1f).clip(forma(12.dp)).clickable { vm.ponerDestacado(hueco, o.id); vm.cerrar() },
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Portada(o.portada, o.tipo, o.titulo, Modifier.fillMaxWidth(), conTitulo = false)
                        Text(o.titulo, style = MaterialTheme.typography.labelLarge, color = f.texto, fontWeight = if (o.id == actual) FontWeight.Bold else null, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(3 - fila.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    } else if (hueco == Destacados.ARTISTA) {
        val lista = e.musicosVivos.filter { q.isEmpty() || q in Logica.tituloLimpio(it.nombre) }
            .sortedWith(compareByDescending<io.github.fogonapps.cajon.datos.Artista> { it.id == actual }.thenByDescending { it.favorito }.thenByDescending { it.nota }.thenBy { it.nombre }).take(60)
        if (lista.isEmpty()) Pista(stringResource(R.string.sin_resultados))
        lista.chunked(3).forEach { fila ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                fila.forEach { a ->
                    Column(Modifier.weight(1f).clip(forma(12.dp)).clickable { vm.ponerDestacado(hueco, a.id); vm.cerrar() },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FotoArtista(a.foto, a.nombre, 92.dp, a.tipo)
                        Text(a.nombre, style = MaterialTheme.typography.labelLarge, color = f.texto, fontWeight = if (a.id == actual) FontWeight.Bold else null, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(3 - fila.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    } else {
        val tipo = Logica.tipoDeHueco(hueco)
        val lista = e.vivas.filter { (tipo == null || it.tipo == tipo) && (q.isEmpty() || q in Logica.tituloLimpio(it.titulo + " " + it.creador)) }
            .sortedWith(compareByDescending<Obra> { it.id == actual }.thenByDescending { it.nota }.thenByDescending { it.meGusta }.thenBy { it.titulo }).take(60)
        if (lista.isEmpty()) Pista(stringResource(R.string.sin_resultados))
        lista.chunked(3).forEach { fila ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                fila.forEach { o ->
                    Column(Modifier.weight(1f).clip(forma(12.dp)).clickable { vm.ponerDestacado(hueco, o.id, if (conPalabras) porQue else null); vm.cerrar() },
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Portada(o.portada, o.tipo, o.titulo, Modifier.fillMaxWidth(), conTitulo = false)
                        Text(o.titulo, style = MaterialTheme.typography.labelLarge, color = f.texto, fontWeight = if (o.id == actual) FontWeight.Bold else null, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(3 - fila.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    Espacio(40.dp)
}

/** La portada del perfil procede del catálogo compartido. */
@Composable
fun PortadaPerfil(vm: CajonViewModel, e: Estado) {
    // «Cambiar portada»: la tienda (abierta en los banners) a un toque, arriba; debajo, las que ya tienes.
    Cabecera(stringResource(R.string.perfil_cambiar_portada), vm::cerrar)
    io.github.fogonapps.social.ui.TiendaEnEditarPerfil(interfazSocial(vm), inicio = "banner", equipar = false)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        io.github.fogonapps.perfil.BannersGratis(vm::ponerPortadaPerfil, vm.red.temas.collectAsState().value.reclamados, vm.red.cuenta.collectAsStateWithLifecycle().value?.insignia.orEmpty(), e.perfil.portada)
        io.github.fogonapps.social.ui.PortadasPropias(interfazSocial(vm))
    }
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

    // La portada con la misma proporción que en el perfil, para que el recorte sea el mismo.
    val marco = if (portada) Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(PROPORCION_PORTADA).clip(forma(12.dp))
    else Modifier.size(240.dp).clip(CircleShape)
    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        Box(marco.background(tonoPapel()).border(1.dp, f.texto, if (portada) forma(12.dp) else CircleShape)
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
        BotonPildoraBorde(stringResource(R.string.ajustar_restablecer), { zoom = 1f; x = 0f; y = 0f }, Modifier.weight(1f))
        BotonPildora(stringResource(R.string.guardar), { vm.encuadrePerfil(portada, Encuadre(zoom, x, y)) }, Modifier.weight(1f))
    }
    Espacio(40.dp)
}


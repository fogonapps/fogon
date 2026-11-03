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
import androidx.compose.runtime.getValue
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
private fun nombreVisibilidad(v: String): String = stringResource(when (Visibilidad.de(v)) {
    Visibilidad.PUBLICA -> R.string.vis_publica; Visibilidad.AMIGOS -> R.string.vis_amigos; else -> R.string.vis_privada
})

@Composable
private fun nombreParte(p: String): String = stringResource(when (p) {
    PartesPerfil.DESTACADOS -> R.string.parte_destacados; PartesPerfil.FAVORITAS -> R.string.tus_favoritas_titulo
    PartesPerfil.CIFRAS -> R.string.parte_cifras; PartesPerfil.EVOLUCION -> R.string.parte_evolucion; else -> R.string.tu_diario
})

@Composable
private fun nombreCifra(c: String, anio: Int): String = when (c) {
    PartesPerfil.C_TERMINADAS -> stringResource(R.string.cifra_terminadas, anio)
    PartesPerfil.C_HORAS_CINE -> stringResource(R.string.horas_de_cine)
    PartesPerfil.C_HORAS_SERIES -> stringResource(R.string.horas_de_series)
    PartesPerfil.C_PAGINAS -> stringResource(R.string.paginas_leidas)
    PartesPerfil.C_CANCIONES -> stringResource(R.string.canciones_escuchadas)
    PartesPerfil.C_DIARIO -> stringResource(R.string.en_tu_diario)
    PartesPerfil.C_RACHA -> stringResource(R.string.racha)
    else -> stringResource(R.string.cifra_nota)
}

/**
 * El nombre de la persona, en la letra de Fogón. Si lo pegó con letras de Unicode copiadas de afuera (𝔄𝔫𝔤𝔢𝔩, 𝓐𝓷𝓰𝓮𝓵,
 * 𝙰𝚗𝚐𝚎𝚕), se ve con esas: son caracteres, no una letra de la app, y el sistema los dibuja con su propia fuente.
 */
@Composable
private fun NombrePerfil(texto: String, tamano: androidx.compose.ui.unit.TextUnit, color: androidx.compose.ui.graphics.Color, maxLineas: Int = 2) {
    Text(texto, fontFamily = FogonTema.actual.titulos, fontSize = tamano, lineHeight = tamano * 1.15f, color = color,
        maxLines = maxLineas, overflow = TextOverflow.Ellipsis)
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
 * El perfil como biografía cultural: portada, foto, nombre, usuario, biografía y enlaces; lo que más le gusta
 * (destacados, la obra que lo marcó, su descubrimiento), las cuatro favoritas, las cifras que eligió, su evolución
 * por año, sus listas y su diario. "Como lo verían otros" muestra solo lo que no es privado, y lo que la app aprendió
 * de sus gustos nunca sale de "Solo tú lo ves".
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun Perfil(vm: CajonViewModel, e: Estado) {
    val f = FogonTema.actual
    val uri = LocalUriHandler.current
    val acciones = LocalAcciones.current
    val p = e.perfil
    var publico by rememberSaveable { mutableStateOf(false) }
    var compartiendo by rememberSaveable { mutableStateOf(false) }
    fun ve(parte: String) = !publico || Visibilidad.de(p.visibilidad[parte]) != Visibilidad.PRIVADA
    val anio = vm.hoy().year
    val descFoto = stringResource(if (p.foto.isBlank()) R.string.poner_foto else R.string.cambiar_foto_perfil)

    // Arriba solo los ajustes; editar y compartir bajan a botones, como en las redes.
    Cabecera(stringResource(R.string.tu_perfil), vm::cerrar) {
        BotonIcono(Iconos.Ajustes, stringResource(R.string.ajustes), grande = true) { vm.abrir(Pantalla.Ajustes) }
    }

    // Portada y foto, la foto montada sobre el borde de la portada y el nombre a su lado, debajo de la portada.
    Box(Modifier.aparecer(0).fillMaxWidth()) {
        Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(110.dp).clip(forma(24.dp)).vidrio(forma(24.dp))
            .haloPuntos(f.tinta2.copy(alpha = 0.35f))) {
            if (p.portada.isNotBlank()) ImagenEncuadrada(p.portada, p.encuadrePortada, Modifier.matchParentSize())
        }
        Row(Modifier.padding(start = 28.dp, end = 16.dp, top = 62.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box {
                FotoPerfil(p.foto, 96.dp, p.encuadreFoto, borde = true)
                // Cambiar la foto sin pasar por editar (solo en la vista propia): un círculo de la primera tinta con el +.
                if (!publico && acciones != null) Box(Modifier.align(Alignment.BottomEnd).offset(x = 10.dp, y = 10.dp).size(48.dp).clip(CircleShape)
                    .clickable { acciones.elegirImagen { vm.subirImagenPerfil(false, it) } }
                    .semantics { contentDescription = descFoto; role = Role.Button }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(f.tinta1).border(2.dp, f.fondo, CircleShape), contentAlignment = Alignment.Center) {
                        IconoRiso(Iconos.Mas, null, tamano = 16.dp, tinta = f.sobreTinta1)
                    }
                }
            }
            Column(Modifier.weight(1f).padding(top = 54.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NombrePerfil(e.ajustes.nombre.ifBlank { stringResource(R.string.tu_nombre) }, 26.sp,
                    if (e.ajustes.nombre.isBlank()) f.textoSuave else f.texto)
                if (p.usuario.isNotBlank()) Text("@" + p.usuario, style = MaterialTheme.typography.bodyMedium, color = f.acentoTexto, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    // Los contadores, como en las redes: tocar uno lleva a lo suyo (ver [contadoresPerfil]).
    if (ve(PartesPerfil.CIFRAS)) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        contadoresPerfil(vm, e, anio).forEach { c ->
            Column(Modifier.weight(1f).clip(forma(14.dp)).clickable(onClick = c.abrir).padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${c.valor}", fontFamily = f.cifras, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = f.texto)
                Text(pluralStringResource(c.etiqueta, c.valor, c.valor, anio), style = MaterialTheme.typography.bodySmall, color = f.textoSuave,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
            }
        }
    }

    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Lo de ahora: lo último que empezó (con su página, si es un libro) o su descubrimiento del momento.
        val vacio = p.bio.isBlank() && p.usuario.isBlank() && p.foto.isBlank()
        if (!publico && vacio) BotonSecundario(stringResource(R.string.completar_perfil), { vm.abrir(Pantalla.EditarPerfil) }, Modifier.fillMaxWidth())
        else {
            val enCurso = if (ve(PartesPerfil.DIARIO)) e.vivas.filter { it.estado == io.github.fogonapps.cajon.datos.Estados.EN_CURSO }
                .maxByOrNull { maxOf(it.empezada ?: "", vm.L.ultimaVez(it) ?: "") } else null
            val descubrimiento = if (ve(PartesPerfil.DESTACADOS)) p.destacados[Destacados.DESCUBRIMIENTO]?.let { i -> e.vivas.firstOrNull { it.id == i } } else null
            val o = enCurso ?: descubrimiento
            if (o != null) Row(Modifier.clip(FormaPildora).background(f.superficie).border(f.borde, f.linea, FormaPildora)
                .clickable { vm.abrir(Pantalla.Ficha(o.id)) }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconoRiso(Iconos.de(o.tipo), null, tamano = 16.dp, tinta = f.acentoTexto)
                val pagina = if (o === enCurso && o.tipo == Tipos.LIBRO && o.pagina > 0 && o.paginas != null) " · " + stringResource(R.string.chip_pagina, o.pagina, o.paginas) else ""
                Text(stringResource(if (o === enCurso) R.string.chip_ahora else R.string.chip_descubrimiento, o.titulo) + pagina,
                    style = MaterialTheme.typography.labelLarge, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (p.bio.isNotBlank()) Text(p.bio, style = MaterialTheme.typography.bodyLarge, color = f.texto)
        if (p.enlaces.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            p.enlaces.forEach { u -> Pildora(Logica.enlaceCorto(u), Iconos.Enlace) { runCatching { uri.openUri(u) } } }
        }
    }

    // Editar, compartir y tus gustos: solo en la vista propia.
    if (!publico) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BotonSecundario(stringResource(R.string.editar_perfil), { vm.abrir(Pantalla.EditarPerfil) }, Modifier.weight(1f))
        BotonSecundario(stringResource(R.string.compartir_perfil), { compartiendo = true }, Modifier.weight(1f))
        BotonIcono(Iconos.Corazon, stringResource(R.string.tus_gustos), grande = true) { vm.abrir(Pantalla.TusGustos) }
    }
    Chips(listOf(false to stringResource(R.string.como_lo_ves), true to stringResource(R.string.como_lo_verian)), publico) { publico = it }
    if (publico) Pista(stringResource(R.string.vista_publica_pista))

    @Composable
    fun Titulo(texto: String, parte: String?) = Seccion(texto) {
        if (!publico && parte != null) Text(nombreVisibilidad(p.visibilidad[parte] ?: ""), style = MaterialTheme.typography.labelSmall, color = f.textoSuave)
    }
    fun obra(id: String?) = id?.let { i -> e.vivas.firstOrNull { it.id == i } }

    // Lo suyo: sus favoritos por tipo.
    if (ve(PartesPerfil.DESTACADOS)) {
        val puestos = Destacados.FAVORITOS.filter { !publico || p.destacados[it] != null }
        if (puestos.isNotEmpty()) {
            Titulo(stringResource(R.string.parte_destacados), PartesPerfil.DESTACADOS)
            if (!publico) Pista(stringResource(R.string.destacados_pista))
            puestos.chunked(3).forEach { fila ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    fila.forEach { h ->
                        val id = p.destacados[h]
                        val a = if (h == Destacados.ARTISTA) id?.let { i -> e.artistasVivos.firstOrNull { it.id == i } } else null
                        val o = if (h != Destacados.ARTISTA) obra(id) else null
                        Column(Modifier.weight(1f).clip(forma(12.dp)).combinedClickable(
                            onClick = {
                                when { a != null -> vm.abrir(Pantalla.VerArtista(a.id)); o != null -> vm.abrir(Pantalla.Ficha(o.id)); !publico -> vm.abrir(Pantalla.ElegirDestacado(h)) }
                            },
                            onLongClick = { if (!publico) vm.abrir(Pantalla.ElegirDestacado(h)) },
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
        }
        // La obra que lo marcó y su descubrimiento del momento: grandes, con sus palabras.
        listOf(Destacados.MARCO, Destacados.DESCUBRIMIENTO).forEach { h ->
            val o = obra(p.destacados[h])
            if (o != null) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).vidrio().combinedClickable(
                onClick = { vm.abrir(Pantalla.Ficha(o.id)) }, onLongClick = { if (!publico) vm.abrir(Pantalla.ElegirDestacado(h)) },
            ).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Portada(o.portada, o.tipo, o.titulo, Modifier.width(92.dp), conTitulo = false)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(nombreHueco(h), style = MaterialTheme.typography.labelLarge, color = f.acentoTexto)
                    Text(o.titulo, fontFamily = f.titulos, fontSize = 20.sp, lineHeight = 24.sp, color = f.texto, maxLines = 3)
                    p.porQue[h]?.let { Text("“$it”", style = MaterialTheme.typography.bodyMedium, color = f.texto) }
                }
            } else if (!publico) FilaVidrio({ vm.abrir(Pantalla.ElegirDestacado(h)) }) {
                Ficha(f.tinta1, Iconos.Estrella)
                Text(nombreHueco(h), style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.weight(1f))
                IconoRiso(Iconos.Mas, null, tamano = 18.dp, tinta = f.textoSuave)
            }
        }
    }

    // Las cuatro favoritas.
    val favoritas = e.ajustes.favoritas.mapNotNull { obra(it) }
    if (ve(PartesPerfil.FAVORITAS) && (favoritas.isNotEmpty() || !publico)) {
        Titulo(stringResource(R.string.tus_favoritas_titulo), PartesPerfil.FAVORITAS)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (i in 0 until 4) {
                val o = favoritas.getOrNull(i)
                Box(Modifier.weight(1f).clip(forma(10.dp)).clickable(enabled = o != null) { o?.let { vm.abrir(Pantalla.Ficha(it.id)) } }) {
                    Portada(o?.portada ?: "", o?.tipo ?: Tipos.PELICULA, o?.titulo ?: "", Modifier.fillMaxWidth(), esquina = 10.dp, conTitulo = false)
                }
            }
        }
        if (favoritas.isEmpty()) Pista(stringResource(R.string.favoritas_pista))
    }

    // Las cifras que eligió mostrar.
    val cifras = p.cifras ?: PartesPerfil.CIFRAS_DE_SIEMPRE
    if (ve(PartesPerfil.CIFRAS) && cifras.isNotEmpty()) {
        Titulo(stringResource(R.string.parte_cifras), PartesPerfil.CIFRAS)
        val delAnio = vm.L.cifras(e, anio)
        val siempre = vm.L.cifras(e, null)
        fun valor(c: String): String = when (c) {
            PartesPerfil.C_TERMINADAS -> "${delAnio.porTipo.values.sum()}"
            PartesPerfil.C_HORAS_CINE -> "${siempre.minutosCine / 60}"
            PartesPerfil.C_HORAS_SERIES -> "${siempre.minutosSeries / 60}"
            PartesPerfil.C_PAGINAS -> "${siempre.paginas}"
            PartesPerfil.C_CANCIONES -> "${siempre.canciones}"
            PartesPerfil.C_DIARIO -> "${siempre.registros}"
            PartesPerfil.C_RACHA -> "${vm.L.racha(e, vm.hoy())}"
            else -> siempre.notaMedia?.let { cifra(it, vm.idioma()) } ?: "–"
        }
        cifras.chunked(2).forEach { fila ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                fila.forEach { c ->
                    Column(Modifier.weight(1f).vidrio().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(valor(c), fontFamily = f.cifras, fontSize = 26.sp, color = f.texto)
                        Text(nombreCifra(c, anio), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                    }
                }
                if (fila.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    // La evolución: año por año, y lo descubierto hace poco.
    val anios = vm.L.evolucion(e)
    val nuevos = vm.L.descubrimientos(e, vm.hoy())
    if (ve(PartesPerfil.EVOLUCION) && (anios.isNotEmpty() || nuevos.isNotEmpty())) {
        Titulo(stringResource(R.string.parte_evolucion), PartesPerfil.EVOLUCION)
        if (nuevos.isNotEmpty()) {
            Text(stringResource(R.string.descubrimientos_recientes), style = MaterialTheme.typography.titleSmall, color = f.textoSuave, modifier = Modifier.padding(start = 20.dp, bottom = 6.dp))
            Carrusel(nuevos, vm, conProgreso = false)
        }
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

    // Lo que la app aprendió de sus gustos: solo para la persona, nunca en el perfil que verían otros.
    if (!publico) {
        val g = remember(e) { vm.perfil() }
        val positivos = (g.generos + g.personas).filter { it.peso > 0 && !it.quitado }
        val ahora = positivos.filter { it.reciente }.take(6)
        val siempre = positivos.filter { !it.reciente }.take(6)
        if (ahora.isNotEmpty() || siempre.isNotEmpty()) {
            Seccion(stringResource(R.string.solo_tu)) { Pildora(stringResource(R.string.tus_gustos), Iconos.Corazon) { vm.abrir(Pantalla.TusGustos) } }
            Pista(stringResource(R.string.solo_tu_pista))
            @Composable
            fun Grupo(t: String, l: List<io.github.fogonapps.cajon.datos.PesoGusto>) {
                if (l.isEmpty()) return
                Text(t, style = MaterialTheme.typography.titleSmall, color = f.textoSuave, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 6.dp))
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    l.forEach { Etiqueta(it.nombre, f.superficie, f.texto) }
                }
            }
            Grupo(stringResource(R.string.gustos_ahora), ahora)
            Grupo(stringResource(R.string.gustos_siempre), siempre)
        }
    }

    // Sus listas (en la vista de otros, solo las que no son privadas).
    val listas = e.listasVivas.filter { !publico || Visibilidad.de(it.visibilidad) != Visibilidad.PRIVADA }
    if (listas.isNotEmpty()) {
        Seccion(stringResource(R.string.pestana_listas))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listas.forEach { l ->
                val obras = obrasDe(e, l)
                val tapa = obras.firstOrNull { it.id == l.portada } ?: obras.firstOrNull()
                FilaVidrio({ vm.abrir(Pantalla.VerLista(l.id)) }, adentro = true) {
                    Portada(tapa?.portada ?: "", tapa?.tipo ?: Tipos.PELICULA, "", Modifier.width(44.dp), esquina = 6.dp, conTitulo = false)
                    Column(Modifier.weight(1f)) {
                        Text(l.nombre, style = MaterialTheme.typography.bodyLarge, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(androidx.compose.ui.res.pluralStringResource(R.plurals.obras_n, obras.size, obras.size) +
                            if (!publico) " · " + nombreVisibilidad(l.visibilidad) else "", style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                    }
                }
            }
        }
    }

    // Su diario: lo último (en la vista de otros, sin lo privado).
    if (ve(PartesPerfil.DIARIO)) {
        val predet = p.visibilidad[PartesPerfil.DIARIO] ?: ""
        val entradas = vm.L.diario(e).filter { d -> !publico || Visibilidad.de(d.registro.visibilidad.ifEmpty { predet }) != Visibilidad.PRIVADA }.take(5)
        if (entradas.isNotEmpty()) {
            Titulo(stringResource(R.string.tu_diario), PartesPerfil.DIARIO)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                entradas.forEach { d ->
                    FilaVidrio({ vm.abrir(Pantalla.Ficha(d.obra.id)) }, adentro = true) {
                        Portada(d.obra.portada, d.obra.tipo, d.obra.titulo, Modifier.width(40.dp), esquina = 6.dp, conTitulo = false)
                        Column(Modifier.weight(1f)) {
                            Text(d.obra.titulo, style = MaterialTheme.typography.bodyLarge, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(fechaCorta(d.registro.fecha, vm.idioma()), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                        }
                        if (d.registro.nota > 0) Estrellas(d.registro.nota)
                    }
                }
            }
        }
    }

    if (!publico) {
        Seccion(stringResource(R.string.mas))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilaVidrio({ vm.abrir(Pantalla.Cifras) }, adentro = true) {
                Ficha(f.tinta2, Iconos.Cifras)
                Text(stringResource(R.string.tus_cifras), style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.weight(1f))
                IconoRiso(Iconos.Siguiente, null, tamano = 18.dp, tinta = f.textoSuave)
            }
            FilaVidrio({ vm.abrir(Pantalla.Ajustes) }, adentro = true) {
                Ficha(f.tinta1, Iconos.Ajustes)
                Text(stringResource(R.string.ajustes), style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.weight(1f))
                IconoRiso(Iconos.Siguiente, null, tamano = 18.dp, tinta = f.textoSuave)
            }
        }
    }

    if (compartiendo && acciones != null) DialogoCompartir({ compartiendo = false }, { acciones.compartir(it, e.ajustes.nombre.ifBlank { "El Cajón" }) }) { TarjetaPerfil(e) }
}

/** La tarjeta para compartir el perfil como imagen: quién es y lo que más le gusta. */
@Composable
private fun TarjetaPerfil(e: Estado) {
    val f = FogonTema.actual
    val p = e.perfil
    Column(Modifier.width(320.dp).clip(forma(24.dp)).background(f.fondo).haloPuntos(f.tinta2.copy(alpha = 0.3f)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FotoPerfil(p.foto, 72.dp, p.encuadreFoto)
            Column {
                NombrePerfil(e.ajustes.nombre.ifBlank { "El Cajón" }, 22.sp, f.texto)
                if (p.usuario.isNotBlank()) Text("@" + p.usuario, style = MaterialTheme.typography.bodySmall, color = f.acentoTexto)
            }
        }
        if (p.bio.isNotBlank()) Text(p.bio, style = MaterialTheme.typography.bodySmall, color = f.texto, maxLines = 4)
        val obras = Destacados.TODOS.filter { it != Destacados.ARTISTA }.mapNotNull { h -> p.destacados[h]?.let { id -> e.vivas.firstOrNull { it.id == id } } }
            .ifEmpty { e.ajustes.favoritas.mapNotNull { id -> e.vivas.firstOrNull { it.id == id } } }.distinctBy { it.id }.take(6)
        obras.chunked(3).forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fila.forEach { o -> Box(Modifier.weight(1f)) { Portada(o.portada, o.tipo, o.titulo, Modifier.fillMaxWidth(), esquina = 8.dp, conTitulo = false) } }
                repeat(3 - fila.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Text("El Cajón · Fogón", style = MaterialTheme.typography.labelSmall, color = f.textoSuave)
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
    var usuario by rememberSaveable { mutableStateOf(p.usuario) }
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
        Pista(stringResource(R.string.foto_perfil_pista), adentro = true)
    }

    Seccion(stringResource(R.string.quien_eres))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Cualquier letra: la persona copia su nombre de una página de letras (son caracteres de Unicode) y lo pega
        // aquí. La app no ofrece letras; el límite cuenta letras de verdad, no unidades de UTF-16 (ver Logica.recortar).
        OutlinedTextField(nombre, { nombre = Logica.recortar(it, 40) }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.tu_nombre)) })
        CampoTexto(usuario, { usuario = Logica.usuarioLimpio(it) }, stringResource(R.string.nombre_usuario), Modifier.fillMaxWidth(), max = 30)
        OutlinedTextField(bio, { bio = it.take(200) }, Modifier.fillMaxWidth(), minLines = 3, label = { Text(stringResource(R.string.bio)) },
            supportingText = { Text("${bio.length}/200") })
        OutlinedTextField(enlaces, { enlaces = it.take(1000) }, Modifier.fillMaxWidth(), minLines = 2, label = { Text(stringResource(R.string.enlaces)) },
            supportingText = { Text(stringResource(R.string.enlaces_pista)) })
        BotonRiso(stringResource(R.string.guardar), { vm.guardarIdentidad(nombre, usuario, bio, enlaces.lines()) }, Modifier.fillMaxWidth())
    }

    Seccion(stringResource(R.string.quien_lo_ve))
    Pista(stringResource(R.string.quien_lo_ve_pista))
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

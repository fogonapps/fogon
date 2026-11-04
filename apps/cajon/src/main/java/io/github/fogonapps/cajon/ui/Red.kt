package io.github.fogonapps.cajon.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.Pantalla
import io.github.fogonapps.cajon.PasoAcceso
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.ClienteSocial
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.PersonaSocial
import io.github.fogonapps.cajon.datos.PublicacionSocial
import io.github.fogonapps.cajon.datos.Visibilidad
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.FormaPildora
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.forma
import io.github.fogonapps.identidad.tarjetaFogon
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/* ----- Piezas ----- */

private fun JsonObject.texto(clave: String) = (this[clave] as? JsonPrimitive)?.content.orEmpty()

/** La foto de una persona de la red (o su inicial, en un círculo de vidrio). */
@Composable
fun AvatarRed(p: PersonaSocial, tamano: Dp = 44.dp) {
    val f = FogonTema.actual
    Box(Modifier.size(tamano).clip(CircleShape).background(f.superficie).border(f.borde, f.linea, CircleShape), contentAlignment = Alignment.Center) {
        Text((p.nombre.ifBlank { p.usuario }).take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = f.acentoTexto)
        if (p.foto) coil3.compose.AsyncImage(ClienteSocial.foto(p.usuario), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
    }
}

/** Una persona en una lista: su foto, su nombre y su @usuario; [derecha] para un botón. */
@Composable
fun FilaPersona(p: PersonaSocial, onClick: () -> Unit, modifier: Modifier = Modifier, derecha: @Composable () -> Unit = {}) {
    val f = FogonTema.actual
    Row(modifier.fillMaxWidth().clip(forma(18.dp)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AvatarRed(p)
        Column(Modifier.weight(1f)) {
            Text(p.nombre.ifBlank { p.usuario }, style = MaterialTheme.typography.titleSmall, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("@" + p.usuario + if (p.privada) " · " + stringResource(R.string.red_privada_corto) else "", style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1)
        }
        derecha()
    }
}

@Composable
private fun haceCuanto(cuando: Long): String {
    val min = ((System.currentTimeMillis() - cuando) / 60_000).coerceAtLeast(0)
    return when {
        min < 1 -> stringResource(R.string.red_ahora)
        min < 60 -> stringResource(R.string.red_hace_min, min.toInt())
        min < 60 * 24 -> stringResource(R.string.red_hace_horas, (min / 60).toInt())
        min < 60 * 24 * 7 -> stringResource(R.string.red_hace_dias, (min / 60 / 24).toInt())
        else -> java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(cuando))
    }
}

/** Las #etiquetas de un texto, sin repetir (en minúsculas, letras, números y guion bajo). */
fun etiquetasDe(texto: String): List<String> = Regex("#([\\p{L}\\p{N}_]{2,40})").findAll(texto).map { it.groupValues[1].lowercase() }.distinct().toList()

/** El texto de una publicación con sus #etiquetas tocables (llevan a lo público que las usa). */
@Composable
private fun TextoConEtiquetas(vm: CajonViewModel, texto: String, maxLineas: Int) {
    val f = FogonTema.actual
    val anotado = androidx.compose.ui.text.buildAnnotatedString {
        var desde = 0
        Regex("#([\\p{L}\\p{N}_]{2,40})").findAll(texto).forEach { m ->
            append(texto.substring(desde, m.range.first))
            val etiqueta = m.groupValues[1].lowercase()
            withLink(androidx.compose.ui.text.LinkAnnotation.Clickable(etiqueta,
                androidx.compose.ui.text.TextLinkStyles(androidx.compose.ui.text.SpanStyle(color = f.acentoTexto, fontWeight = FontWeight.SemiBold))) {
                vm.abrir(Pantalla.EtiquetaRed(etiqueta))
            }) { append(m.value) }
            desde = m.range.last + 1
        }
        append(texto.substring(desde))
    }
    Text(anotado, style = MaterialTheme.typography.bodyMedium, color = f.texto, maxLines = maxLineas, overflow = TextOverflow.Ellipsis)
}

/** 1.234 → "1,2 mil"; 2.300.000 → "2,3 mill." */
@Composable
private fun cifraCorta(n: Int): String = when {
    n >= 1_000_000 -> stringResource(R.string.red_millones, "%.1f".format(n / 1_000_000.0))
    n >= 10_000 -> stringResource(R.string.red_miles, "${n / 1000}")
    n >= 1_000 -> stringResource(R.string.red_miles, "%.1f".format(n / 1000.0))
    else -> "$n"
}

private fun compartirTexto(ctx: android.content.Context, texto: String) {
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto), null))
}

/** Una publicación de la red: quién, qué (la obra, la foto, las estrellas, las palabras) y ♥, comentar, guardar y compartir. */
@Composable
fun TarjetaPublicacion(vm: CajonViewModel, p: PublicacionSocial, modifier: Modifier = Modifier, abrir: Boolean = true, motivo: io.github.fogonapps.cajon.RedSocial.Companion.Motivo? = null) {
    val f = FogonTema.actual
    val ctx = LocalContext.current
    val d = p.datos
    var destapado by remember(p.id) { mutableStateOf(false) }
    var menu by remember(p.id) { mutableStateOf(false) }
    LaunchedEffect(p.id) { vm.red.vista(p.id) }
    if (menu) MenuPublicacion(vm, p, motivo) { menu = false }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).tarjetaFogon()
        .then(if (abrir) Modifier.combinedClickable(onLongClick = { menu = true }) { vm.abrir(Pantalla.PublicacionRed(p.id)) } else Modifier).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().clip(FormaPildora).clickable { vm.abrir(Pantalla.PerfilRed(p.autor.usuario)) },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AvatarRed(p.autor, 36.dp)
            Column(Modifier.weight(1f)) {
                Text(p.autor.nombre.ifBlank { p.autor.usuario }, style = MaterialTheme.typography.titleSmall, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(when (p.tipo) {
                    "resena" -> R.string.red_tipo_resena; "diario" -> R.string.red_tipo_diario; "lista" -> R.string.red_tipo_lista; else -> R.string.red_tipo_publico
                }) + " · " + haceCuanto(p.cuando), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1)
            }
            if (p.visibilidad == Visibilidad.AMIGOS) IconoRiso(Iconos.Candado, stringResource(R.string.red_para_seguidores), tamano = 16.dp, tinta = f.textoSuave)
            BotonIcono(Iconos.Opciones, stringResource(R.string.red_mas_opciones), tinta = f.textoSuave) { menu = true }
        }
        // Por qué ves esto (en Explorar).
        motivo?.let { m ->
            Text(when (m) {
                is io.github.fogonapps.cajon.RedSocial.Companion.Motivo.TienesLaObra -> stringResource(R.string.red_porque_obra, m.titulo)
                is io.github.fogonapps.cajon.RedSocial.Companion.Motivo.TeGustaElCreador -> stringResource(R.string.red_porque_creador, m.creador)
                is io.github.fogonapps.cajon.RedSocial.Companion.Motivo.TusEtiquetas -> stringResource(R.string.red_porque_etiqueta, m.etiqueta)
                io.github.fogonapps.cajon.RedSocial.Companion.Motivo.Nueva -> stringResource(R.string.red_porque_nueva)
            }, style = MaterialTheme.typography.labelSmall, color = f.acentoTexto, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        d.texto("imagen").takeIf { it.isNotBlank() }?.let { img ->
            coil3.compose.AsyncImage(ClienteSocial.imagen(img), null, Modifier.fillMaxWidth().aspectRatio(1f).clip(forma(16.dp))
                .then(if (d.texto("spoiler") == "true" && !destapado) Modifier.blur(24.dp) else Modifier), contentScale = ContentScale.Crop)
        }
        val titulo = d.texto("titulo")
        if (titulo.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val portadas = (d["portadas"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
            val portada = d.texto("portada").ifBlank { portadas.firstOrNull().orEmpty() }
            if (portada.isNotBlank() || d.texto("tipoObra").isNotBlank()) Portada(portada, d.texto("tipoObra").ifBlank { "pelicula" }, titulo, Modifier.width(64.dp), conTitulo = false)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(titulo, style = MaterialTheme.typography.titleMedium, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                listOfNotNull(d.texto("creador").takeIf { it.isNotBlank() }, d.texto("anio").takeIf { it.isNotBlank() },
                    (d["cuantas"] as? JsonPrimitive)?.intOrNull?.let { stringResource(R.string.red_obras_n, it) }).joinToString(" · ").takeIf { it.isNotBlank() }
                    ?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1) }
                (d["estrellas"] as? JsonPrimitive)?.doubleOrNull?.let { Estrellas((it * 2).toInt(), 16.sp) }
            }
        }
        val texto = d.texto("texto")
        if (texto.isNotBlank()) {
            if (d.texto("spoiler") == "true" && !destapado) {
                Box(Modifier.clip(FormaPildora).background(f.superficie).border(f.borde, f.linea, FormaPildora).clickable { destapado = true }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.red_spoiler_tocar), style = MaterialTheme.typography.labelLarge, color = f.texto)
                }
            } else TextoConEtiquetas(vm, texto, if (abrir) 6 else Int.MAX_VALUE)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BotonIcono(Iconos.Corazon, stringResource(R.string.red_me_gusta), tinta = if (p.yoMeGusta) f.acentoTexto else f.textoSuave, lleno = p.yoMeGusta) { vm.red.meGusta(p) }
            if (p.megusta > 0) Text("${p.megusta}", style = MaterialTheme.typography.labelLarge, color = f.texto)
            Box(Modifier.width(8.dp))
            BotonIcono(Iconos.Comentario, stringResource(R.string.red_comentar), tinta = f.textoSuave) { vm.abrir(Pantalla.PublicacionRed(p.id)) }
            if (p.comentarios > 0) Text("${p.comentarios}", style = MaterialTheme.typography.labelLarge, color = f.texto)
            Box(Modifier.weight(1f))
            if (p.vistas > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(end = 6.dp)) {
                IconoRiso(Iconos.Anime, stringResource(R.string.red_vistas), tamano = 18.dp, tinta = f.textoSuave)
                Text(cifraCorta(p.vistas), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
            }
            BotonIcono(Iconos.Guardar, stringResource(if (p.yoGuardo) R.string.red_quitar_guardada else R.string.red_guardar), tinta = if (p.yoGuardo) f.acentoTexto else f.textoSuave, lleno = p.yoGuardo) { vm.red.guardar(p) }
            BotonIcono(Iconos.Compartir, stringResource(R.string.compartir), tinta = f.textoSuave) { compartirTexto(ctx, ClienteSocial.enlacePublicacion(p.id)) }
        }
    }
}

/* ----- El feed en el Inicio ----- */

/** En el Inicio: lo último de tu gente (o la invitación a la red, si no hay cuenta). */
@Composable
fun FeedInicio(vm: CajonViewModel) {
    val f = FogonTema.actual
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    val feed by vm.red.feed.collectAsStateWithLifecycle()
    val sinLeer by vm.red.sinLeer.collectAsStateWithLifecycle()
    if (usuario == null) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).tarjetaFogon().clickable { vm.irA(io.github.fogonapps.cajon.Pestana.PARA_TI) }.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconoRiso(Iconos.Gente, null, tamano = 26.dp, tinta = f.acentoTexto)
                Text(stringResource(R.string.red_invita_titulo), style = MaterialTheme.typography.titleMedium, color = f.texto)
            }
            Text(stringResource(R.string.red_invita_texto), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
        }
        return
    }
    Seccion(stringResource(R.string.red_de_tu_gente)) {
        Pildora(if (sinLeer > 0) "$sinLeer" else "", Iconos.Campana, acento = sinLeer > 0) { vm.abrir(Pantalla.AvisosRed) }
        Pildora(stringResource(R.string.red_ver_todo)) { vm.irA(io.github.fogonapps.cajon.Pestana.PARA_TI) }
    }
    val lista = feed
    when {
        lista == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.tinta1, trackColor = f.linea)
        lista.isEmpty() -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pista(stringResource(R.string.red_feed_vacio))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pildora(stringResource(R.string.red_buscar_gente), Iconos.Buscar) { vm.abrir(Pantalla.Buscar(io.github.fogonapps.cajon.Secciones.PERSONAS)) }
                Pildora(stringResource(R.string.red_explorar), Iconos.Gente) { vm.irA(io.github.fogonapps.cajon.Pestana.PARA_TI) }
            }
        }
        else -> lista.take(4).forEach { TarjetaPublicacion(vm, it) }
    }
}

/* ----- El feed completo ----- */

/**
 * Para ti: la red. Primero (si falta) elegir lo que te gusta, con eso se arma lo que ves; después crear la cuenta.
 * Ya con cuenta: las notas de tu gente arriba, y lo de quienes sigues o lo de todos, ordenado por tus gustos.
 */
@Composable
fun PantallaRed(vm: CajonViewModel, e: Estado, enPestana: Boolean = false) {
    val f = FogonTema.actual
    val feed by vm.red.feed.collectAsStateWithLifecycle()
    val explorar by vm.red.explorar.collectAsStateWithLifecycle()
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    val sinLeer by vm.red.sinLeer.collectAsStateWithLifecycle()
    var pestana by rememberSaveable { mutableStateOf(if (usuario == null) 1 else 0) }
    LaunchedEffect(pestana, usuario) { if (pestana == 0) vm.red.cargarFeed() else vm.red.cargarExplorar(); vm.red.cargarNotas(); vm.red.revisarSinLeer() }
    Cabecera(stringResource(R.string.red_titulo), if (enPestana) null else vm::cerrar) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BotonIcono(Iconos.Buscar, stringResource(R.string.red_buscar_gente)) { vm.abrir(Pantalla.Buscar(io.github.fogonapps.cajon.Secciones.PERSONAS)) }
            usuario?.let { u -> Box(Modifier.padding(horizontal = 4.dp).clip(CircleShape).clickable { vm.abrir(Pantalla.PerfilRed(u)) }) { AvatarRed(PersonaSocial(u, foto = true), 36.dp) } }
            if (usuario != null) {
                BotonIcono(Iconos.Comentario, stringResource(R.string.chat_titulo)) { vm.abrir(Pantalla.Chats()) }
                Box {
                    BotonIcono(Iconos.Campana, stringResource(R.string.red_avisos), tinta = if (sinLeer > 0) f.acentoTexto else null) { vm.abrir(Pantalla.AvisosRed) }
                    if (sinLeer > 0) Box(Modifier.align(Alignment.TopEnd).clip(CircleShape).background(f.tinta1).padding(horizontal = 5.dp, vertical = 1.dp)) {
                        Text(if (sinLeer > 9) "9+" else "$sinLeer", style = MaterialTheme.typography.labelSmall, color = f.sobreTinta1)
                    }
                }
            }
        }
    }
    // Paso 1: lo que te gusta (con eso se ordena lo que ves). Paso 2: tu cuenta.
    if (!e.ajustes.gustosListos) { PasoGustos(vm); return }
    if (usuario == null) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).tarjetaFogon().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.red_paso2_titulo), style = MaterialTheme.typography.titleLarge, color = f.texto)
            Text(stringResource(R.string.red_paso2_texto), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
            BotonRiso(stringResource(R.string.red_crear_cuenta), { vm.abrir(Pantalla.CuentaRed) }, Modifier.fillMaxWidth())
        }
    } else FilaNotas(vm, e)
    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(R.string.red_pestana_siguiendo, R.string.red_explorar).forEachIndexed { i, t ->
            val elegida = pestana == i
            Box(Modifier.clip(FormaPildora).background(if (elegida) f.tinta1 else f.superficie).border(f.borde, if (elegida) f.tinta1 else f.linea, FormaPildora)
                .clickable { pestana = i }.padding(horizontal = 16.dp, vertical = 9.dp)) {
                Text(stringResource(t), style = MaterialTheme.typography.labelLarge, color = if (elegida) f.sobreTinta1 else f.texto)
            }
        }
        Box(Modifier.weight(1f))
        if (usuario != null) Pildora(stringResource(R.string.red_publicar), Iconos.Mas, acento = true) { vm.abrir(Pantalla.Publicar()) }
    }
    if (pestana == 0 && usuario == null) { Pista(stringResource(R.string.red_feed_sin_cuenta), Modifier.padding(16.dp)); return }
    val ocultas by vm.red.ocultas.collectAsStateWithLifecycle()
    val lista = (if (pestana == 0) feed?.map { it to null } else explorar?.let { remember(it, e) { io.github.fogonapps.cajon.RedSocial.ordenarPorGustos(it, e) } })
        ?.filter { (p, _) -> io.github.fogonapps.cajon.RedSocial.seMuestra(p, e, ocultas) }
    when {
        lista == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.tinta1, trackColor = f.linea)
        lista.isEmpty() -> Pista(stringResource(if (pestana == 0) R.string.red_feed_vacio else R.string.red_explorar_vacio), Modifier.padding(16.dp))
        else -> {
            lista.forEach { (p, motivo) -> TarjetaPublicacion(vm, p, motivo = motivo) }
            if (pestana == 0 && lista.size >= 40) BotonSecundario(stringResource(R.string.red_mas), { vm.red.masFeed() }, Modifier.fillMaxWidth().padding(16.dp))
            // Sin scroll infinito: al final, estás al día.
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                io.github.fogonapps.identidad.Chispa(tamano = 22.dp)
                Text(stringResource(R.string.red_al_dia), style = MaterialTheme.typography.titleMedium, color = f.texto)
                Text(stringResource(R.string.red_al_dia_texto), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, textAlign = TextAlign.Center)
            }
        }
    }
}

/** El paso 1 de Para ti: elegir lo que te gusta (lo más visto, escuchado y leído), en "Cuéntame tus gustos". */
@Composable
private fun PasoGustos(vm: CajonViewModel) {
    val f = FogonTema.actual
    val opciones by vm.paraElegir.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.cargarParaElegir() }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).tarjetaFogon().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.red_paso1_titulo), style = MaterialTheme.typography.titleLarge, color = f.texto)
        Text(stringResource(R.string.red_paso1_texto), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
        // Una probadita de lo que se elige: portadas de lo del momento.
        val portadas = (opciones.cine.map { it.portada } + opciones.libros.map { it.portada }).filter { it.isNotBlank() }.take(4)
        if (portadas.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            portadas.forEach { coil3.compose.AsyncImage(it, null, Modifier.weight(1f).aspectRatio(0.68f).clip(forma(12.dp)), contentScale = ContentScale.Crop) }
        }
        BotonRiso(stringResource(R.string.red_paso1_boton), { vm.abrir(Pantalla.ElegirGustos) }, Modifier.fillMaxWidth())
    }
}

/** Las notas de 24 horas: la tuya (tocarla la cambia) y las de tu gente (tocarlas abre el chat). */
@Composable
fun FilaNotas(vm: CajonViewModel, e: Estado) {
    val f = FogonTema.actual
    val notas by vm.red.notas.collectAsStateWithLifecycle()
    val yo by vm.red.usuario.collectAsStateWithLifecycle()
    var escribiendo by remember { mutableStateOf(false) }
    var texto by rememberSaveable { mutableStateOf("") }
    val mia = notas.firstOrNull { it.persona.usuario == yo }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Nota(PersonaSocial(yo.orEmpty(), stringResource(R.string.red_tu_nota), foto = true), mia?.texto ?: stringResource(R.string.red_nota_pon), mia == null) { texto = mia?.texto.orEmpty(); escribiendo = true }
        notas.filter { it.persona.usuario != yo }.forEach { n -> Nota(n.persona, n.texto, false) { vm.abrir(Pantalla.Conversacion(n.persona.usuario)) } }
    }
    if (escribiendo) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(texto, { texto = it.take(60) }, Modifier.weight(1f), placeholder = { Text(stringResource(R.string.red_nota_pista)) }, singleLine = true, shape = forma(22.dp))
        BotonIcono(Iconos.Enviar, stringResource(R.string.red_nota_poner), lleno = true) { vm.red.ponerNota(texto); escribiendo = false }
        if (mia != null) BotonIcono(Iconos.Borrar, stringResource(R.string.red_nota_quitar)) { vm.red.ponerNota(""); escribiendo = false }
    }
}

@Composable
private fun Nota(p: PersonaSocial, texto: String, vacia: Boolean, onClick: () -> Unit) {
    val f = FogonTema.actual
    Column(Modifier.width(84.dp).clip(forma(16.dp)).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.clip(forma(14.dp)).background(if (vacia) f.superficie else f.tinta1).border(f.borde, if (vacia) f.linea else f.tinta1, forma(14.dp)).padding(horizontal = 8.dp, vertical = 5.dp)) {
            Text(texto, style = MaterialTheme.typography.labelSmall, color = if (vacia) f.textoSuave else f.sobreTinta1, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
        AvatarRed(p, 56.dp)
        Text(p.nombre.ifBlank { p.usuario }, style = MaterialTheme.typography.labelSmall, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun InvitaCuenta(vm: CajonViewModel) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Pista(stringResource(R.string.red_invita_texto))
        BotonRiso(stringResource(R.string.red_crear_cuenta), { vm.abrir(Pantalla.CuentaRed) }, Modifier.fillMaxWidth())
    }
}

/* ----- El perfil de otra persona (o el propio, como lo ve la red) ----- */

@Composable
fun PerfilRed(vm: CajonViewModel, usuario: String) {
    val f = FogonTema.actual
    val ctx = LocalContext.current
    val perfiles by vm.red.perfiles.collectAsStateWithLifecycle()
    val noEstan by vm.red.noEstan.collectAsStateWithLifecycle()
    val listas by vm.red.listas.collectAsStateWithLifecycle()
    val yo by vm.red.usuario.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var pestana by rememberSaveable(usuario) { mutableStateOf(0) }
    LaunchedEffect(usuario) { vm.red.verPerfil(usuario) }
    val p = perfiles[usuario]
    val propio = usuario == yo
    Cabecera("@$usuario", vm::cerrar) {
        Row {
            if (propio) {
                BotonIcono(Iconos.Lapiz, stringResource(R.string.editar_perfil)) { vm.abrir(Pantalla.EditarPerfil) }
                BotonIcono(Iconos.Ajustes, stringResource(R.string.red_ajustes_cuenta)) { vm.abrir(Pantalla.CuentaRed) }
            } else BotonIcono(Iconos.Compartir, stringResource(R.string.red_compartir_perfil)) { compartirTexto(ctx, ClienteSocial.enlacePerfil(usuario)) }
            if (yo != null && !propio) BotonIcono(Iconos.Opciones, stringResource(R.string.red_mas_opciones)) { menu = !menu }
        }
    }
    if (p == null) {
        if (usuario in noEstan) Pista(stringResource(R.string.red_perfil_no_existe), Modifier.padding(16.dp))
        else LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.tinta1, trackColor = f.linea)
        return
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (p.portada.isNotBlank()) coil3.compose.AsyncImage(p.portada, null, Modifier.fillMaxWidth().height(120.dp).clip(forma(20.dp)), contentScale = ContentScale.Crop)
        AvatarRed(p.persona, 96.dp)
        Text(p.persona.nombre.ifBlank { usuario }, style = MaterialTheme.typography.headlineSmall, color = f.texto, textAlign = TextAlign.Center)
        if (p.meSigue && !propio) Etiqueta(stringResource(R.string.red_te_sigue), f.superficie, f.textoSuave)
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Cifra("${p.siguiendo}", stringResource(R.string.red_siguiendo)) { if (!p.cerrada) vm.abrir(Pantalla.GenteRed(usuario, false)) }
            Cifra("${p.seguidores}", stringResource(R.string.red_seguidores)) { if (!p.cerrada) vm.abrir(Pantalla.GenteRed(usuario, true)) }
            Cifra(cifraCorta(p.megusta), stringResource(R.string.red_me_gusta_cifra), null)
        }
        if (p.bio.isNotBlank()) Text(p.bio, style = MaterialTheme.typography.bodyMedium, color = f.texto, textAlign = TextAlign.Center)
        else if (propio) Pildora(stringResource(R.string.red_poner_bio), Iconos.Lapiz) { vm.abrir(Pantalla.EditarPerfil) }
        p.enlaces.forEach { e -> val uri = LocalUriHandler.current
            Text(e.removePrefix("https://").removePrefix("http://").removePrefix("www."), style = MaterialTheme.typography.bodySmall, color = f.acentoTexto, modifier = Modifier.clickable { runCatching { uri.openUri(e) } }) }
        when {
            propio -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonSecundario(stringResource(R.string.editar_perfil), { vm.abrir(Pantalla.EditarPerfil) }, Modifier.weight(1f))
                BotonSecundario(stringResource(R.string.red_compartir_perfil), { compartirTexto(ctx, ClienteSocial.enlacePerfil(usuario)) }, Modifier.weight(1f))
            }
            yo == null -> BotonRiso(stringResource(R.string.red_crear_para_seguir), { vm.abrir(Pantalla.CuentaRed) }, Modifier.fillMaxWidth())
            else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (p.relacion) {
                    "sigo" -> BotonSecundario(stringResource(R.string.red_dejar_de_seguir), { vm.red.dejarDeSeguir(usuario) }, Modifier.weight(1f))
                    "pendiente" -> BotonSecundario(stringResource(R.string.red_pendiente), { vm.red.dejarDeSeguir(usuario) }, Modifier.weight(1f))
                    else -> BotonRiso(stringResource(if (p.meSigue) R.string.red_seguir_tambien else R.string.red_seguir), { vm.red.seguir(usuario) }, Modifier.weight(1f))
                }
                BotonSecundario(stringResource(R.string.chat_mensaje), { vm.abrir(Pantalla.Conversacion(usuario)) }, Modifier.weight(1f))
            }
        }
        if (menu) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pildora(stringResource(R.string.red_compartir_perfil)) { menu = false; compartirTexto(ctx, ClienteSocial.enlacePerfil(usuario)) }
            Pildora(stringResource(R.string.red_bloquear)) { menu = false; vm.red.bloquear(usuario, true); vm.cerrar() }
            Pildora(stringResource(R.string.red_reportar)) { menu = false; vm.red.reportar("cuenta", usuario, "") }
        }
    }
    (p.partes["cancion"] as? JsonObject)?.let { c -> TarjetaCancion(c.texto("cancion"), c.texto("creador"), c.texto("titulo"), c.texto("portada")) }
    if (p.cerrada) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IconoRiso(Iconos.Candado, null, tamano = 36.dp, tinta = f.textoSuave)
            Pista(stringResource(R.string.red_cuenta_privada_texto), centro = true)
        }
        return
    }
    // Las pestañas: publicaciones, gustos, música y guardadas (las que la persona deja ver).
    val pestanas = listOfNotNull(
        Triple(0, Iconos.Lista, R.string.red_publicaciones),
        Triple(1, Iconos.Estrella, R.string.parte_gustos).takeIf { propio || p.partes.keys.any { it in setOf("destacados", "favoritas", "gustos", "cifras") } },
        Triple(2, Iconos.Disco, R.string.parte_musica).takeIf { propio || "musica" in p.partes },
        Triple(3, Iconos.Guardar, R.string.parte_guardadas).takeIf { p.guardadasVisibles },
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        pestanas.forEach { (i, icono, t) ->
            val elegida = pestana == i
            Box(Modifier.clip(FormaPildora).background(if (elegida) f.tinta1 else androidx.compose.ui.graphics.Color.Transparent).clickable { pestana = i }.padding(horizontal = 18.dp, vertical = 8.dp)) {
                IconoRiso(icono, stringResource(t), tamano = 22.dp, tinta = if (elegida) f.sobreTinta1 else f.textoSuave)
            }
        }
    }
    when (pestana) {
        0 -> if (p.publicaciones.isEmpty()) Pista(stringResource(R.string.red_sin_publicaciones), Modifier.padding(16.dp)) else CuadriculaPublicaciones(vm, p.publicaciones)
        1 -> GustosDelPerfil(vm, p, propio)
        2 -> MusicaDelPerfil(p, propio)
        3 -> {
            LaunchedEffect(usuario) { vm.red.cargarGuardadasDe(usuario) }
            val g = listas["guardadas:$usuario"]
            if (g == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.tinta1, trackColor = f.linea)
            else if (g.isEmpty()) Pista(stringResource(R.string.red_sin_guardadas), Modifier.padding(16.dp)) else CuadriculaPublicaciones(vm, g)
        }
    }
    if (propio) {
        // Lo demás de tu perfil (cifras, evolución, diario, destacados): en el perfil del teléfono.
        FilaVidrio({ vm.abrir(Pantalla.Perfil) }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            IconoRiso(Iconos.Cifras, null, tamano = 20.dp, tinta = f.acentoTexto)
            Text(stringResource(R.string.red_mas_de_tu_perfil), color = f.texto, modifier = Modifier.weight(1f))
            IconoRiso(Iconos.Siguiente, null, tamano = 18.dp, tinta = f.textoSuave)
        }
        Pista(stringResource(R.string.red_que_ven_pista), Modifier.padding(16.dp))
    }
}

/** Las publicaciones en cuadrícula, con sus vistas: la foto, la portada de la obra o unas palabras. */
@Composable
private fun CuadriculaPublicaciones(vm: CajonViewModel, lista: List<PublicacionSocial>) {
    val f = FogonTema.actual
    lista.chunked(3).forEach { fila ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            fila.forEach { p ->
                val d = p.datos
                val imagen = d.texto("imagen").takeIf { it.isNotBlank() }?.let { ClienteSocial.imagen(it) }
                    ?: d.texto("portada").ifBlank { (d["portadas"] as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.content }.orEmpty() }
                Box(Modifier.weight(1f).aspectRatio(0.75f).clip(forma(10.dp)).background(f.superficie).clickable { vm.abrir(Pantalla.PublicacionRed(p.id)) }) {
                    if (imagen.isNotBlank()) coil3.compose.AsyncImage(imagen, null, Modifier.matchParentSize()
                        .then(if (d.texto("spoiler") == "true") Modifier.blur(20.dp) else Modifier), contentScale = ContentScale.Crop)
                    else Text(d.texto("titulo").ifBlank { d.texto("texto") }, style = MaterialTheme.typography.labelMedium, color = f.texto, maxLines = 5,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.dp))
                    Row(Modifier.align(Alignment.BottomStart).padding(6.dp).clip(FormaPildora).background(f.fondo.copy(alpha = 0.7f)).padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        IconoRiso(Iconos.Anime, null, tamano = 12.dp, tinta = f.texto)
                        Text(cifraCorta(p.vistas), style = MaterialTheme.typography.labelSmall, color = f.texto)
                    }
                }
            }
            repeat(3 - fila.size) { Box(Modifier.weight(1f)) }
        }
    }
}

/** Lo que más le gusta: sus destacados, sus cuatro favoritas, los géneros que dijo y lo que ha terminado. */
@Composable
private fun GustosDelPerfil(vm: CajonViewModel, p: io.github.fogonapps.cajon.datos.PerfilSocial, propio: Boolean) {
    val f = FogonTema.actual
    val destacados = (p.partes["destacados"] as? JsonObject)?.entries?.mapNotNull { (h, v) -> (v as? JsonObject)?.let { h to it } }.orEmpty()
    val favoritas = (p.partes["favoritas"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    val generos = (p.partes["gustos"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
    if (destacados.isEmpty() && favoritas.isEmpty() && generos.isEmpty()) {
        Pista(stringResource(if (propio) R.string.red_gustos_vacios_propio else R.string.red_gustos_vacios), Modifier.padding(16.dp))
        if (propio) Row(Modifier.padding(horizontal = 16.dp)) { Pildora(stringResource(R.string.red_ir_a_mi_perfil), Iconos.Persona) { vm.abrir(Pantalla.Perfil) } }
        return
    }
    if (destacados.isNotEmpty()) {
        Seccion(stringResource(R.string.parte_destacados))
        destacados.chunked(3).forEach { fila ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                fila.forEach { (h, o) ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Portada(o.texto("portada"), o.texto("tipoObra").ifBlank { "pelicula" }, o.texto("titulo"), Modifier.fillMaxWidth(), conTitulo = false)
                        Text(o.texto("titulo"), style = MaterialTheme.typography.labelMedium, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(nombreHuecoRed(h), style = MaterialTheme.typography.labelSmall, color = f.textoSuave, maxLines = 1)
                    }
                }
                repeat(3 - fila.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
    if (favoritas.isNotEmpty()) {
        Seccion(stringResource(R.string.red_sus_favoritas))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            favoritas.take(4).forEach { o -> Portada(o.texto("portada"), o.texto("tipoObra").ifBlank { "pelicula" }, o.texto("titulo"), Modifier.weight(1f)) }
            repeat(4 - favoritas.take(4).size) { Box(Modifier.weight(1f)) }
        }
    }
    if (generos.isNotEmpty()) {
        Seccion(stringResource(R.string.parte_gustos))
        androidx.compose.foundation.layout.FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            generos.forEach { g -> Pildora(g) { vm.abrir(Pantalla.EtiquetaRed(g.lowercase().filter { it.isLetterOrDigit() })) } }
        }
    }
}

@Composable
private fun nombreHuecoRed(h: String): String = stringResource(when (h) {
    "pelicula" -> R.string.dest_pelicula; "serie" -> R.string.dest_serie; "anime" -> R.string.dest_anime
    "album" -> R.string.dest_album; "libro" -> R.string.dest_libro; "artista" -> R.string.dest_artista
    "marco" -> R.string.dest_marco; else -> R.string.dest_descubrimiento
})

/** Los artistas que más le gustan. */
@Composable
private fun MusicaDelPerfil(p: io.github.fogonapps.cajon.datos.PerfilSocial, propio: Boolean) {
    val f = FogonTema.actual
    val artistas = (p.partes["musica"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    if (artistas.isEmpty()) { Pista(stringResource(if (propio) R.string.red_musica_vacia_propio else R.string.red_gustos_vacios), Modifier.padding(16.dp)); return }
    artistas.chunked(3).forEach { fila ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            fila.forEach { a ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FotoArtista(a.texto("foto"), a.texto("nombre"), 84.dp)
                    Text(a.texto("nombre"), style = MaterialTheme.typography.labelMedium, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
            repeat(3 - fila.size) { Box(Modifier.weight(1f)) }
        }
    }
}

/** Lo público que lleva una #etiqueta. */
@Composable
fun EtiquetaRed(vm: CajonViewModel, etiqueta: String) {
    val f = FogonTema.actual
    val listas by vm.red.listas.collectAsStateWithLifecycle()
    LaunchedEffect(etiqueta) { vm.red.cargarEtiqueta(etiqueta) }
    Cabecera("#$etiqueta", vm::cerrar)
    val l = listas["#$etiqueta"]
    when {
        l == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.tinta1, trackColor = f.linea)
        l.isEmpty() -> Pista(stringResource(R.string.red_etiqueta_vacia), Modifier.padding(16.dp))
        else -> l.forEach { TarjetaPublicacion(vm, it) }
    }
}

@Composable
private fun Cifra(valor: String, etiqueta: String, onClick: (() -> Unit)?) {
    val f = FogonTema.actual
    Column(Modifier.clip(forma(14.dp)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valor, fontFamily = f.cifras, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = f.texto)
        Text(etiqueta, style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
    }
}

/* ----- Una publicación con sus comentarios ----- */

@Composable
fun PublicacionRed(vm: CajonViewModel, id: Long) {
    val f = FogonTema.actual
    val vistas by vm.red.vistas.collectAsStateWithLifecycle()
    val todos by vm.red.comentarios.collectAsStateWithLifecycle()
    val yo by vm.red.usuario.collectAsStateWithLifecycle()
    var texto by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(id) { vm.red.cargarPublicacion(id) }
    Cabecera(stringResource(R.string.red_publicacion), vm::cerrar)
    val p = vistas[id] ?: vm.red.buscarPublicacion(id)
    if (p == null) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.tinta1, trackColor = f.linea); return }
    TarjetaPublicacion(vm, p, abrir = false)
    if (p.autor.usuario == yo) {
        val clave = p.datos.texto("clave").ifBlank { p.datos.texto("propia").takeIf { it.isNotBlank() }?.let { "f:$it" }.orEmpty() }
        if (clave.isNotBlank()) BotonSecundario(stringResource(if (clave.startsWith("f:")) R.string.red_borrar_publicacion else R.string.red_quitar_de_la_red),
            { vm.red.quitarDeLaRed(clave); vm.cerrar() }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), peligro = true)
    }
    Seccion(stringResource(R.string.red_comentarios))
    val palabras = (vm.estado.collectAsStateWithLifecycle().value?.ajustes?.palabrasOcultas).orEmpty()
    todos[id].orEmpty().filter { !lleva(palabras, it.texto) }.forEach { c ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.clickable { vm.abrir(Pantalla.PerfilRed(c.autor.usuario)) }) { AvatarRed(c.autor, 32.dp) }
            Column(Modifier.weight(1f)) {
                Text("@${c.autor.usuario} · ${haceCuanto(c.cuando)}", style = MaterialTheme.typography.labelMedium, color = f.textoSuave)
                Text(c.texto, style = MaterialTheme.typography.bodyMedium, color = f.texto)
            }
            if (c.puedoBorrar) BotonIcono(Iconos.Borrar, stringResource(R.string.red_borrar_comentario), tinta = f.textoSuave) { vm.red.borrarComentario(id, c.id) }
            else if (yo != null) BotonIcono(Iconos.Bandera, stringResource(R.string.red_reportar), tinta = f.textoSuave) { vm.red.reportar("comentario", "${c.id}", "") }
        }
    }
    if (yo == null) { InvitaCuenta(vm); return }
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(texto, { texto = it.take(1000) }, Modifier.weight(1f), placeholder = { Text(stringResource(R.string.red_escribe_comentario)) }, shape = forma(22.dp), maxLines = 4)
        BotonIcono(Iconos.Enviar, stringResource(R.string.red_enviar), activo = texto.isNotBlank(), lleno = true) { vm.red.comentar(id, texto); texto = "" }
    }
}

/* ----- Avisos, guardadas, gente ----- */

@Composable
fun AvisosRed(vm: CajonViewModel) {
    val f = FogonTema.actual
    val avisos by vm.red.avisos.collectAsStateWithLifecycle()
    val solicitudes by vm.red.solicitudes.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.red.cargarAvisos() }
    var borrarTodos by remember { mutableStateOf(false) }
    Cabecera(stringResource(R.string.red_avisos), vm::cerrar) {
        if (!avisos.isNullOrEmpty()) BotonIcono(Iconos.Borrar, stringResource(R.string.red_borrar_avisos)) { borrarTodos = !borrarTodos }
    }
    if (borrarTodos) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pista(stringResource(R.string.red_borrar_avisos_seguro), Modifier.weight(1f))
        Pildora(stringResource(R.string.red_si_borrar_avisos), acento = true) { vm.red.borrarAviso(null); borrarTodos = false }
    }
    if (solicitudes.isNotEmpty()) {
        Seccion(stringResource(R.string.red_solicitudes))
        solicitudes.forEach { s ->
            FilaPersona(s, { vm.abrir(Pantalla.PerfilRed(s.usuario)) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pildora(stringResource(R.string.red_aceptar), acento = true) { vm.red.aceptar(s.usuario) }
                    Pildora(stringResource(R.string.red_rechazar)) { vm.red.rechazar(s.usuario) }
                }
            }
        }
        Seccion(stringResource(R.string.red_lo_ultimo))
    }
    val lista = avisos
    when {
        lista == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.tinta1, trackColor = f.linea)
        lista.isEmpty() -> Pista(stringResource(R.string.red_sin_avisos), Modifier.padding(16.dp))
        else -> lista.groupBy { a ->
            val h = (System.currentTimeMillis() - a.cuando) / 3600_000
            when { h < 24 -> R.string.red_hoy; h < 24 * 7 -> R.string.red_ultimos_7; else -> R.string.red_antes }
        }.forEach { (grupo, avisosDelGrupo) ->
          Seccion(stringResource(grupo))
          avisosDelGrupo.forEach { a ->
            val que = stringResource(when (a.tipo) {
                "megusta" -> R.string.red_aviso_megusta; "comentario" -> R.string.red_aviso_comentario; "seguir" -> R.string.red_aviso_seguir
                "solicitud" -> R.string.red_aviso_solicitud; "aceptada" -> R.string.red_aviso_aceptada; "publico" -> R.string.red_aviso_publico; "sancion" -> R.string.red_aviso_sancion; else -> R.string.red_aviso_otro
            })
            FilaPersona(a.de, {
                val pub = a.objeto.toLongOrNull()
                if (pub != null && a.tipo in setOf("megusta", "comentario", "publico")) vm.abrir(Pantalla.PublicacionRed(pub)) else vm.abrir(Pantalla.PerfilRed(a.de.usuario))
            }) {
                Text("$que · ${haceCuanto(a.cuando)}", style = MaterialTheme.typography.bodySmall, color = if (a.leido) f.textoSuave else f.acentoTexto, maxLines = 2, modifier = Modifier.width(130.dp), textAlign = TextAlign.End)
                BotonIcono(Iconos.Cerrar, stringResource(R.string.red_borrar_aviso), tinta = f.textoSuave) { vm.red.borrarAviso(a.id) }
            }
          }
        }
    }
}

@Composable
fun GuardadosRed(vm: CajonViewModel) {
    val f = FogonTema.actual
    val lista by vm.red.guardados.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.red.cargarGuardados() }
    Cabecera(stringResource(R.string.red_guardadas), vm::cerrar)
    val l = lista
    when {
        l == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.tinta1, trackColor = f.linea)
        l.isEmpty() -> Pista(stringResource(R.string.red_sin_guardadas), Modifier.padding(16.dp))
        else -> l.forEach { TarjetaPublicacion(vm, it) }
    }
}

@Composable
fun GenteRed(vm: CajonViewModel, usuario: String, seguidores: Boolean) {
    val f = FogonTema.actual
    val gente by vm.red.gente.collectAsStateWithLifecycle()
    val yo by vm.red.usuario.collectAsStateWithLifecycle()
    LaunchedEffect(usuario, seguidores) { vm.red.cargarGente(usuario, seguidores) }
    Cabecera(stringResource(if (seguidores) R.string.red_tus_seguidores_titulo else R.string.red_pestana_siguiendo), vm::cerrar)
    val l = gente[(if (seguidores) "seguidores:" else "siguiendo:") + usuario]
    when {
        l == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.tinta1, trackColor = f.linea)
        l.isEmpty() -> Pista(stringResource(R.string.red_nadie), Modifier.padding(16.dp))
        else -> l.forEach { p ->
            FilaPersona(p, { vm.abrir(Pantalla.PerfilRed(p.usuario)) }) {
                if (seguidores && usuario == yo) Pildora(stringResource(R.string.red_quitar)) { vm.red.quitarSeguidor(p.usuario) }
            }
        }
    }
}

@Composable
fun BloqueadosRed(vm: CajonViewModel) {
    val f = FogonTema.actual
    val gente by vm.red.gente.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.red.cargarBloqueados() }
    Cabecera(stringResource(R.string.red_bloqueadas), vm::cerrar)
    val l = gente["bloqueados"]
    when {
        l == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.tinta1, trackColor = f.linea)
        l.isEmpty() -> Pista(stringResource(R.string.red_nadie_bloqueado), Modifier.padding(16.dp))
        else -> l.forEach { p -> FilaPersona(p, {}) { Pildora(stringResource(R.string.red_desbloquear)) { vm.red.bloquear(p.usuario, false) } } }
    }
}

/* ----- Publicar ----- */

@Composable
fun Publicar(vm: CajonViewModel, e: Estado, obra: String?) {
    val f = FogonTema.actual
    val acciones = LocalAcciones.current
    val publicando by vm.red.publicando.collectAsStateWithLifecycle()
    var texto by rememberSaveable { mutableStateOf("") }
    var foto by remember { mutableStateOf<android.net.Uri?>(null) }
    var seguidores by rememberSaveable { mutableStateOf(false) }
    var spoiler by rememberSaveable { mutableStateOf(false) }
    val o = obra?.let { id -> e.vivas.firstOrNull { it.id == id } }
    Cabecera(stringResource(R.string.red_publicar), vm::cerrar)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (o != null) Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Portada(o.portada, o.tipo, o.titulo, Modifier.width(56.dp), conTitulo = false)
            Text(o.titulo, style = MaterialTheme.typography.titleMedium, color = f.texto)
        }
        OutlinedTextField(texto, { texto = it.take(2000) }, Modifier.fillMaxWidth().height(160.dp), placeholder = { Text(stringResource(R.string.red_que_quieres_decir)) }, shape = forma(20.dp),
            supportingText = { Text(stringResource(R.string.red_etiquetas_pista)) })
        foto?.let { coil3.compose.AsyncImage(it, null, Modifier.fillMaxWidth().aspectRatio(1f).clip(forma(16.dp)), contentScale = ContentScale.Crop) }
        if (acciones != null) Pildora(stringResource(if (foto == null) R.string.red_agregar_foto else R.string.red_cambiar_foto), Iconos.Imagen) { acciones.elegirImagen { foto = it } }
        Opcion(stringResource(R.string.red_solo_seguidores), seguidores) { seguidores = it }
        Opcion(stringResource(R.string.red_tiene_spoilers), spoiler) { spoiler = it }
        if (publicando) LinearProgressIndicator(Modifier.fillMaxWidth(), color = f.tinta1, trackColor = f.linea)
        BotonRiso(stringResource(R.string.red_publicar), {
            vm.red.publicar(texto, obra, foto, if (seguidores) Visibilidad.AMIGOS else Visibilidad.PUBLICA, spoiler) { vm.cerrar() }
        }, Modifier.fillMaxWidth(), habilitado = !publicando && (texto.isNotBlank() || foto != null))
    }
}

@Composable
private fun Opcion(texto: String, si: Boolean, onCambio: (Boolean) -> Unit) {
    val f = FogonTema.actual
    Row(Modifier.fillMaxWidth().clip(forma(14.dp)).clickable { onCambio(!si) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = f.texto, modifier = Modifier.weight(1f))
        Switch(si, onCambio, colors = SwitchDefaults.colors(checkedTrackColor = f.tinta1, checkedThumbColor = f.sobreTinta1))
    }
}

/* ----- La cuenta ----- */

@Composable
fun CuentaRed(vm: CajonViewModel, e: Estado) {
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.red.revisarServidor() }
    Cabecera(stringResource(R.string.red_tu_cuenta), vm::cerrar)
    val u = usuario
    if (u == null) EntrarRed(vm, e) else ConfigCuenta(vm, u)
}

@Composable
private fun EntrarRed(vm: CajonViewModel, e: Estado) {
    val f = FogonTema.actual
    val correo by vm.red.correoDisponible.collectAsStateWithLifecycle()
    val acceso by vm.red.acceso.collectAsStateWithLifecycle()
    var usuario by rememberSaveable { mutableStateOf(e.perfil.usuario.ifBlank { "" }) }
    var nombre by rememberSaveable { mutableStateOf(e.ajustes.nombre) }
    var correoTexto by rememberSaveable { mutableStateOf("") }
    var codigo by rememberSaveable { mutableStateOf("") }
    var acepto by rememberSaveable { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.red_que_es), style = MaterialTheme.typography.bodyMedium, color = f.texto)
        Pista(stringResource(R.string.red_que_sube))
        if (correo == null) { LinearProgressIndicator(Modifier.fillMaxWidth(), color = f.tinta1, trackColor = f.linea); return }
        val conCorreo = correo == true
        if (conCorreo && acceso.paso == PasoAcceso.CORREO) {
            var conClave by rememberSaveable { mutableStateOf(false) }
            var clave by rememberSaveable { mutableStateOf("") }
            OutlinedTextField(correoTexto, { correoTexto = it.trim().take(120) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.red_tu_correo)) },
                singleLine = true, shape = forma(18.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            if (conClave) {
                OutlinedTextField(clave, { clave = it.take(200) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.red_clave)) }, singleLine = true, shape = forma(18.dp),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                BotonRiso(stringResource(R.string.red_entrar), { vm.red.entrarConClave(correoTexto, clave) }, Modifier.fillMaxWidth(), habilitado = !acceso.ocupado && "@" in correoTexto && clave.length >= 8)
                BotonSecundario(stringResource(R.string.red_mejor_codigo), { conClave = false }, Modifier.fillMaxWidth())
            } else {
                BotonRiso(stringResource(R.string.red_mandar_codigo), { vm.red.pedirCodigo(correoTexto) }, Modifier.fillMaxWidth(), habilitado = !acceso.ocupado && "@" in correoTexto)
                BotonSecundario(stringResource(R.string.red_con_clave), { conClave = true }, Modifier.fillMaxWidth())
            }
            Pista(stringResource(R.string.red_olvide_clave))
            return
        }
        if (conCorreo && acceso.paso == PasoAcceso.CODIGO) {
            Pista(stringResource(R.string.red_revisa_correo, acceso.correo))
            OutlinedTextField(codigo, { codigo = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.red_codigo)) },
                singleLine = true, shape = forma(18.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            BotonRiso(stringResource(R.string.red_entrar), { vm.red.entrar(codigo) }, Modifier.fillMaxWidth(), habilitado = !acceso.ocupado && codigo.length == 6)
            BotonSecundario(stringResource(R.string.red_otro_correo), { vm.red.volverAlCorreo() }, Modifier.fillMaxWidth())
            return
        }
        // Cuenta nueva: el usuario, el nombre y la aceptación (con correo ya comprobado, o sin correo).
        Text(stringResource(R.string.red_elige_usuario), style = MaterialTheme.typography.titleMedium, color = f.texto)
        OutlinedTextField(usuario, { usuario = it.lowercase().filter { c -> c.isLetterOrDigit() && c.code < 128 || c == '.' || c == '_' }.take(30) }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.red_usuario)) }, prefix = { Text("@") }, singleLine = true, shape = forma(18.dp))
        CampoTexto(nombre, { nombre = it }, stringResource(R.string.red_nombre_visible))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(acepto, { acepto = it }, colors = CheckboxDefaults.colors(checkedColor = f.tinta1, checkmarkColor = f.sobreTinta1))
            Text(stringResource(R.string.red_acepto), style = MaterialTheme.typography.bodySmall, color = f.texto, modifier = Modifier.weight(1f).clickable { acepto = !acepto })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pildora(stringResource(R.string.red_politica)) { uri.openUri(ClienteSocial.SERVIDOR + "/privacidad") }
            Pildora(stringResource(R.string.red_terminos)) { uri.openUri(ClienteSocial.SERVIDOR + "/terminos") }
        }
        val listo = !acceso.ocupado && acepto && usuario.length >= 3
        BotonRiso(stringResource(R.string.red_crear_cuenta), {
            if (acceso.paso == PasoAcceso.USUARIO) vm.red.crearConCorreo(usuario, nombre) else vm.red.crearSinCorreo(usuario, nombre)
        }, Modifier.fillMaxWidth(), habilitado = listo)
        if (!conCorreo) Pista(stringResource(R.string.red_sin_correo_aviso))
    }
}

@Composable
private fun ConfigCuenta(vm: CajonViewModel, usuario: String) {
    val f = FogonTema.actual
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val privada by vm.red.privada.collectAsStateWithLifecycle()
    val correo by vm.red.correoDisponible.collectAsStateWithLifecycle()
    val correoCuenta by vm.red.correoCuenta.collectAsStateWithLifecycle()
    val acceso by vm.red.acceso.collectAsStateWithLifecycle()
    var correoTexto by rememberSaveable { mutableStateOf("") }
    var codigo by rememberSaveable { mutableStateOf("") }
    var borrar by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("@$usuario", style = MaterialTheme.typography.headlineSmall, color = f.texto)
        Text(ClienteSocial.enlacePerfil(usuario), style = MaterialTheme.typography.bodySmall, color = f.acentoTexto)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pildora(stringResource(R.string.red_compartir_perfil), Iconos.Compartir, acento = true) { compartirTexto(ctx, ClienteSocial.enlacePerfil(usuario)) }
            Pildora(stringResource(R.string.red_ver_mi_perfil), Iconos.Persona) { vm.abrir(Pantalla.PerfilRed(usuario)) }
        }
        Opcion(stringResource(R.string.red_cuenta_privada), privada) { vm.red.cambiarPrivada(it) }
        Pista(stringResource(R.string.red_cuenta_privada_pista))
        Pista(stringResource(R.string.red_que_se_comparte))
        if (correo == true) {
            if (correoCuenta.isNotBlank()) Pista(stringResource(R.string.red_correo_de_cuenta, correoCuenta))
            else if (acceso.paso == PasoAcceso.CODIGO) {
                OutlinedTextField(codigo, { codigo = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.red_codigo)) },
                    singleLine = true, shape = forma(18.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                BotonRiso(stringResource(R.string.red_vincular), { vm.red.vincularCorreo(codigo) }, Modifier.fillMaxWidth(), habilitado = codigo.length == 6)
            } else {
                Pista(stringResource(R.string.red_vincula_pista))
                OutlinedTextField(correoTexto, { correoTexto = it.trim().take(120) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.red_tu_correo)) },
                    singleLine = true, shape = forma(18.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                BotonSecundario(stringResource(R.string.red_mandar_codigo), { vm.red.pedirCodigo(correoTexto) }, Modifier.fillMaxWidth(), habilitado = "@" in correoTexto)
            }
        }
        // Tu usuario (una vez cada 7 días) y tu contraseña.
        val cuenta by vm.red.cuenta.collectAsStateWithLifecycle()
        Desplegable(Iconos.Persona, stringResource(R.string.red_cambiar_usuario), "@$usuario") {
            var nuevo by rememberSaveable { mutableStateOf(usuario) }
            val desde = cuenta?.usuarioDesde ?: 0
            if (desde > System.currentTimeMillis()) Pista(stringResource(R.string.red_usuario_desde, java.text.DateFormat.getDateInstance().format(java.util.Date(desde))))
            else {
                OutlinedTextField(nuevo, { nuevo = it.lowercase().filter { c -> c.isLetterOrDigit() && c.code < 128 || c == '.' || c == '_' }.take(30) }, Modifier.fillMaxWidth(),
                    prefix = { Text("@") }, singleLine = true, shape = forma(18.dp))
                Pista(stringResource(R.string.red_usuario_regla))
                BotonRiso(stringResource(R.string.guardar), { vm.red.cambiarUsuario(nuevo) {} }, Modifier.fillMaxWidth(), habilitado = nuevo.length >= 3 && nuevo != usuario)
            }
        }
        if (correoCuenta.isNotBlank()) Desplegable(Iconos.Candado, stringResource(if (cuenta?.conClave == true) R.string.red_cambiar_clave else R.string.red_crear_clave), null) {
            var actual by rememberSaveable { mutableStateOf("") }
            var nueva by rememberSaveable { mutableStateOf("") }
            val oculto = androidx.compose.ui.text.input.PasswordVisualTransformation()
            if (cuenta?.conClave == true) OutlinedTextField(actual, { actual = it.take(200) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.red_clave_actual)) },
                singleLine = true, shape = forma(18.dp), visualTransformation = oculto)
            OutlinedTextField(nueva, { nueva = it.take(200) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.red_clave_nueva)) },
                singleLine = true, shape = forma(18.dp), visualTransformation = oculto, supportingText = { Text(stringResource(R.string.red_clave_regla)) })
            BotonRiso(stringResource(R.string.guardar), { vm.red.ponerClave(nueva, actual) { nueva = ""; actual = "" } }, Modifier.fillMaxWidth(), habilitado = nueva.length >= 8)
        } else Pista(stringResource(R.string.red_clave_pide_correo))
        FilaVidrio({ vm.abrir(Pantalla.GenteRed(usuario, true)) }) { Text(stringResource(R.string.red_tus_seguidores), color = f.texto, modifier = Modifier.weight(1f)); IconoRiso(Iconos.Siguiente, null, tamano = 18.dp, tinta = f.textoSuave) }
        FilaVidrio({ vm.abrir(Pantalla.EstadoCuenta) }) { Text(stringResource(R.string.mod_estado), color = f.texto, modifier = Modifier.weight(1f)); IconoRiso(Iconos.Siguiente, null, tamano = 18.dp, tinta = f.textoSuave) }
        if (cuenta?.admin == true) FilaVidrio({ vm.abrir(Pantalla.Moderacion) }) { Text(stringResource(R.string.mod_titulo), color = f.acentoTexto, modifier = Modifier.weight(1f)); IconoRiso(Iconos.Siguiente, null, tamano = 18.dp, tinta = f.textoSuave) }
        FilaVidrio({ vm.abrir(Pantalla.BloqueadosRed) }) { Text(stringResource(R.string.red_bloqueadas), color = f.texto, modifier = Modifier.weight(1f)); IconoRiso(Iconos.Siguiente, null, tamano = 18.dp, tinta = f.textoSuave) }
        FilaVidrio({ uri.openUri(ClienteSocial.SERVIDOR + "/privacidad") }) { Text(stringResource(R.string.red_politica), color = f.texto, modifier = Modifier.weight(1f)); IconoRiso(Iconos.Enlace, null, tamano = 18.dp, tinta = f.textoSuave) }
        FilaVidrio({ uri.openUri(ClienteSocial.SERVIDOR + "/terminos") }) { Text(stringResource(R.string.red_terminos), color = f.texto, modifier = Modifier.weight(1f)); IconoRiso(Iconos.Enlace, null, tamano = 18.dp, tinta = f.textoSuave) }
        BotonSecundario(stringResource(R.string.red_salir), { vm.red.salir() }, Modifier.fillMaxWidth())
        if (!borrar) BotonSecundario(stringResource(R.string.red_borrar_cuenta), { borrar = true }, Modifier.fillMaxWidth(), peligro = true)
        else {
            Pista(stringResource(R.string.red_borrar_cuenta_seguro))
            BotonSecundario(stringResource(R.string.red_si_borrar), { borrar = false; vm.red.borrarCuenta() }, Modifier.fillMaxWidth(), peligro = true)
        }
    }
}

/* ----- La canción del perfil ----- */

/**
 * Pide a la app de música que la persona tenga (la que sea) que busque y toque la canción: es la forma estándar de
 * Android (`MEDIA_PLAY_FROM_SEARCH`), sin depender de ningún servicio ni guardar música en el servidor.
 */
fun tocarCancion(ctx: android.content.Context, titulo: String, artista: String, album: String): Boolean {
    val i = Intent(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
        .putExtra(android.app.SearchManager.QUERY, "$titulo $artista".trim())
        .putExtra(android.provider.MediaStore.EXTRA_MEDIA_FOCUS, android.provider.MediaStore.Audio.Media.ENTRY_CONTENT_TYPE)
        .putExtra(android.provider.MediaStore.EXTRA_MEDIA_TITLE, titulo)
        .putExtra(android.provider.MediaStore.EXTRA_MEDIA_ARTIST, artista)
        .putExtra(android.provider.MediaStore.EXTRA_MEDIA_ALBUM, album)
    return runCatching { ctx.startActivity(i) }.isSuccess
}

/** La tarjeta de la canción del perfil: portada del álbum, la canción, el artista y ▶. */
@Composable
fun TarjetaCancion(titulo: String, artista: String, album: String, portada: String, modifier: Modifier = Modifier, onCambiar: (() -> Unit)? = null) {
    val f = FogonTema.actual
    val ctx = LocalContext.current
    val sinApp = stringResource(R.string.cancion_sin_app)
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).tarjetaFogon().padding(10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Portada(portada, "album", album, Modifier.width(52.dp), conTitulo = false)
        Column(Modifier.weight(1f).then(if (onCambiar != null) Modifier.clickable(onClick = onCambiar) else Modifier)) {
            Text(titulo, style = MaterialTheme.typography.titleSmall, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artista, style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        BotonIcono(Iconos.Tocar, stringResource(R.string.cancion_tocar), lleno = true) {
            if (!tocarCancion(ctx, titulo, artista, album)) android.widget.Toast.makeText(ctx, sinApp, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

/** Elegir la canción del perfil entre las de los álbumes del cajón. */
@Composable
fun ElegirCancion(vm: CajonViewModel, e: Estado) {
    val f = FogonTema.actual
    var buscar by rememberSaveable { mutableStateOf("") }
    val enInternet by vm.canciones.collectAsStateWithLifecycle()
    Cabecera(stringResource(R.string.cancion_elegir), vm::cerrar)
    Pista(stringResource(R.string.cancion_elegir_pista), Modifier.padding(horizontal = 16.dp))
    OutlinedTextField(buscar, { buscar = it.take(60); vm.buscarCancion(it) }, Modifier.fillMaxWidth().padding(16.dp), placeholder = { Text(stringResource(R.string.cancion_buscar)) }, singleLine = true, shape = forma(22.dp),
        leadingIcon = { IconoRiso(Iconos.Buscar, null, tamano = 18.dp, tinta = f.textoSuave) })
    if (e.perfil.cancion.isNotBlank()) BotonSecundario(stringResource(R.string.cancion_quitar), { vm.ponerCancion(""); vm.cerrar() }, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
    // De internet (MusicBrainz), con la carátula de su álbum.
    val lista = enInternet
    if (buscar.trim().length >= 2) {
        Seccion(stringResource(R.string.cancion_en_internet))
        when {
            lista == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.tinta1, trackColor = f.linea)
            lista.isEmpty() -> Pista(stringResource(R.string.sin_resultados), Modifier.padding(horizontal = 16.dp))
            else -> lista.forEach { c ->
                Row(Modifier.fillMaxWidth().clip(forma(14.dp)).clickable { vm.ponerCancion(listOf("v2", c.titulo, c.artista, c.album, c.portada).joinToString("|") { it.replace("|", "/") }); vm.cerrar() }
                    .padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Portada(c.portada, "album", c.album, Modifier.width(44.dp), conTitulo = false)
                    Column(Modifier.weight(1f)) {
                        Text(c.titulo, style = MaterialTheme.typography.bodyMedium, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOf(c.artista, c.album).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Seccion(stringResource(R.string.cancion_de_tu_cajon))
    }
    val q = buscar.trim().lowercase()
    val albumes = e.vivas.filter { it.tipo == "album" && it.canciones.isNotEmpty() }.sortedByDescending { it.cambio }
    if (albumes.isEmpty()) return
    albumes.forEach { a ->
        val canciones = a.canciones.filter { q.isEmpty() || q in it.titulo.lowercase() || q in a.titulo.lowercase() || q in a.creador.lowercase() }
        if (canciones.isEmpty()) return@forEach
        Seccion("${a.titulo} · ${a.creador}")
        canciones.take(if (q.isEmpty()) 6 else 40).forEach { c ->
            Row(Modifier.fillMaxWidth().clip(forma(14.dp)).clickable { vm.ponerCancion(listOf("v2", c.titulo, a.creador, a.titulo, a.portada).joinToString("|") { it.replace("|", "/") }); vm.cerrar() }.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(c.titulo, style = MaterialTheme.typography.bodyMedium, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/* ----- Notificaciones y palabras ocultas ----- */

@Composable
fun AjustesAvisos(vm: CajonViewModel) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences(io.github.fogonapps.cajon.AvisosRed.PREFS, android.content.Context.MODE_PRIVATE) }
    var avisos by remember { mutableStateOf(prefs.getBoolean(io.github.fogonapps.cajon.AvisosRed.AVISOS, true)) }
    var mensajes by remember { mutableStateOf(prefs.getBoolean(io.github.fogonapps.cajon.AvisosRed.MENSAJES, true)) }
    val permiso = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    fun guardar() {
        prefs.edit().putBoolean(io.github.fogonapps.cajon.AvisosRed.AVISOS, avisos).putBoolean(io.github.fogonapps.cajon.AvisosRed.MENSAJES, mensajes).apply()
        if ((avisos || mensajes) && android.os.Build.VERSION.SDK_INT >= 33) permiso.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        io.github.fogonapps.cajon.AvisosRed.programar(ctx)
    }
    Cabecera(stringResource(R.string.aj_notificaciones), vm::cerrar)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Pista(stringResource(R.string.notif_explica))
        Opcion(stringResource(R.string.notif_de_avisos), avisos) { avisos = it; guardar() }
        Opcion(stringResource(R.string.notif_de_mensajes), mensajes) { mensajes = it; guardar() }
        Pista(stringResource(R.string.notif_privacidad))
    }
}

@Composable
fun PalabrasOcultas(vm: CajonViewModel, e: Estado) {
    val f = FogonTema.actual
    var nueva by rememberSaveable { mutableStateOf("") }
    Cabecera(stringResource(R.string.aj_palabras_ocultas), vm::cerrar)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Pista(stringResource(R.string.palabras_explica))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(nueva, { nueva = it.take(40) }, Modifier.weight(1f), placeholder = { Text(stringResource(R.string.palabras_agregar)) }, singleLine = true, shape = forma(22.dp))
            BotonIcono(Iconos.Mas, stringResource(R.string.palabras_agregar), lleno = true, activo = nueva.isNotBlank()) { vm.palabraOculta(nueva, true); nueva = "" }
        }
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            e.ajustes.palabrasOcultas.forEach { p -> Pildora("$p  ✕") { vm.palabraOculta(p, false) } }
        }
        if (e.ajustes.palabrasOcultas.isEmpty()) Text(stringResource(R.string.palabras_vacio), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
    }
}

/** Si una publicación o un comentario lleva alguna de las palabras que la persona escondió. */
fun lleva(palabras: List<String>, texto: String): Boolean {
    if (palabras.isEmpty()) return false
    val t = io.github.fogonapps.cajon.datos.Logica.tituloLimpio(texto)
    return palabras.any { p -> io.github.fogonapps.cajon.datos.Logica.tituloLimpio(p).let { it.isNotBlank() && it in t } }
}

/* ----- El menú de una publicación (abajo, como una hoja) ----- */

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MenuPublicacion(vm: CajonViewModel, p: PublicacionSocial, motivo: io.github.fogonapps.cajon.RedSocial.Companion.Motivo?, onCerrar: () -> Unit) {
    val f = FogonTema.actual
    val ctx = LocalContext.current
    val yo by vm.red.usuario.collectAsStateWithLifecycle()
    var reportando by remember { mutableStateOf(false) }
    var porque by remember { mutableStateOf(false) }
    androidx.compose.material3.ModalBottomSheet(onCerrar, containerColor = f.fondo, contentColor = f.texto) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            @Composable fun Fila(icono: io.github.fogonapps.identidad.Dibujo, texto: String, peligro: Boolean = false, onClick: () -> Unit) {
                Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconoRiso(icono, null, tamano = 22.dp, tinta = if (peligro) MaterialTheme.colorScheme.error else f.texto)
                    Text(texto, style = MaterialTheme.typography.bodyLarge, color = if (peligro) MaterialTheme.colorScheme.error else f.texto)
                }
            }
            when {
                reportando -> {
                    Text(stringResource(R.string.rep_por_que), style = MaterialTheme.typography.titleMedium, color = f.texto, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                    val ayuda = stringResource(R.string.rep_ayuda)
                    MOTIVOS_REPORTE.forEach { (codigo, m) ->
                        val texto = stringResource(m)
                        Fila(Iconos.Bandera, texto) {
                            vm.red.reportar("publicacion", "${p.id}", "$codigo: $texto"); vm.red.ocultar(p.id)
                            if (codigo == "autolesion") android.widget.Toast.makeText(ctx, ayuda, android.widget.Toast.LENGTH_LONG).show()
                            onCerrar()
                        }
                    }
                }
                porque -> {
                    Text(stringResource(R.string.menu_por_que), style = MaterialTheme.typography.titleMedium, color = f.texto, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                    Text(motivoTexto(motivo), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
                    Text(stringResource(R.string.menu_por_que_explica), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                }
                else -> {
                    Fila(Iconos.Estrella, stringResource(R.string.menu_por_que)) { porque = true }
                    if (p.autor.usuario != yo) {
                        Fila(Iconos.Chulo, stringResource(R.string.menu_me_interesa)) { vm.red.interesa(p, true); onCerrar() }
                        Fila(Iconos.Cerrar, stringResource(R.string.menu_no_me_interesa)) { vm.red.interesa(p, false); onCerrar() }
                        Fila(Iconos.Persona, stringResource(R.string.menu_silenciar, p.autor.usuario)) { vm.red.silenciar(p.autor.usuario); onCerrar() }
                    }
                    Fila(Iconos.Enlace, stringResource(R.string.menu_copiar_enlace)) {
                        (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                            .setPrimaryClip(android.content.ClipData.newPlainText("", ClienteSocial.enlacePublicacion(p.id))); onCerrar()
                    }
                    if (p.autor.usuario != yo && yo != null) {
                        Fila(Iconos.Candado, stringResource(R.string.menu_bloquear, p.autor.usuario), peligro = true) { vm.red.bloquear(p.autor.usuario, true); onCerrar() }
                        Fila(Iconos.Bandera, stringResource(R.string.red_reportar), peligro = true) { reportando = true }
                    }
                }
            }
        }
    }
}

/** Los motivos de reporte: el código que entiende el servidor y su texto (lo de menores, primero). */
val MOTIVOS_REPORTE = listOf(
    "menor_en_riesgo" to R.string.rep_menor, "desnudos" to R.string.rep_desnudos, "acoso" to R.string.rep_acoso, "odio" to R.string.rep_odio,
    "autolesion" to R.string.rep_autolesion, "spam" to R.string.rep_spam, "menor_de_edad" to R.string.rep_menor_de_edad,
    "derechos" to R.string.rep_autor, "falso" to R.string.rep_falso, "otro" to R.string.rep_otro,
)

/* ----- Estado de tu cuenta y moderación ----- */

@Composable
fun EstadoCuenta(vm: CajonViewModel) {
    val f = FogonTema.actual
    val estado by vm.red.estadoCuenta.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.red.cargarEstadoCuenta() }
    Cabecera(stringResource(R.string.mod_estado), vm::cerrar)
    val e = estado ?: run { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.tinta1, trackColor = f.linea); return }
    val fecha = { t: Long -> java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(t)) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val ahora = System.currentTimeMillis()
        when {
            e.cerrada -> Text(stringResource(R.string.mod_cerrada), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            e.suspendidaHasta > ahora -> Text(stringResource(R.string.mod_suspendida_hasta, fecha(e.suspendidaHasta)), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            e.restringidaHasta > ahora -> Text(stringResource(R.string.mod_restringida_hasta, fecha(e.restringidaHasta)), style = MaterialTheme.typography.titleMedium, color = f.acentoTexto)
            e.faltas.isEmpty() -> Text(stringResource(R.string.mod_todo_bien), style = MaterialTheme.typography.titleMedium, color = f.texto)
        }
        e.faltas.forEach { x ->
            Column(Modifier.fillMaxWidth().tarjetaFogon().padding(14.dp)) {
                Text(x.regla, style = MaterialTheme.typography.titleSmall, color = f.texto)
                Text(stringResource(R.string.mod_falta_vence, fecha(x.cuando), fecha(x.vence)), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
        }
        Pista(stringResource(R.string.mod_como_funciona))
    }
}

/** La cola de revisión: solo para la cuenta que modera (lo decide el servidor). */
@Composable
fun Moderacion(vm: CajonViewModel) {
    val f = FogonTema.actual
    val casos by vm.red.casos.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.red.cargarCasos() }
    Cabecera(stringResource(R.string.mod_titulo), vm::cerrar)
    val l = casos
    when {
        l == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.tinta1, trackColor = f.linea)
        l.isEmpty() -> Pista(stringResource(R.string.mod_vacio), Modifier.padding(16.dp))
        else -> l.forEach { c ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).tarjetaFogon().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${c.que} · ${c.motivos.entries.joinToString { "${it.key} ×${it.value}" }}", style = MaterialTheme.typography.labelMedium,
                    color = if ("menor_en_riesgo" in c.motivos) MaterialTheme.colorScheme.error else f.acentoTexto)
                c.autor?.let { Text("@${it.usuario} · ${stringResource(R.string.mod_faltas, c.faltasAutor)}", style = MaterialTheme.typography.bodySmall, color = f.textoSuave) }
                Text(c.contenido.take(600), style = MaterialTheme.typography.bodySmall, color = f.texto, maxLines = 8, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.mod_personas, c.personas) + if (c.oculto) " · " + stringResource(R.string.mod_oculto) else "", style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pildora(stringResource(R.string.mod_sin_falta)) { vm.red.decidir(c, "sin_falta") }
                    Pildora(stringResource(R.string.mod_falta), acento = true) { vm.red.decidir(c, "falta") }
                    Pildora(stringResource(R.string.mod_grave)) { vm.red.decidir(c, "falta_grave") }
                    Pildora(stringResource(R.string.mod_cerrar)) { vm.red.decidir(c, "cerrar") }
                }
            }
        }
    }
    Pista(stringResource(R.string.mod_menores), Modifier.padding(16.dp))
}

@Composable
private fun motivoTexto(m: io.github.fogonapps.cajon.RedSocial.Companion.Motivo?): String = when (m) {
    is io.github.fogonapps.cajon.RedSocial.Companion.Motivo.TienesLaObra -> stringResource(R.string.red_porque_obra, m.titulo)
    is io.github.fogonapps.cajon.RedSocial.Companion.Motivo.TeGustaElCreador -> stringResource(R.string.red_porque_creador, m.creador)
    is io.github.fogonapps.cajon.RedSocial.Companion.Motivo.TusEtiquetas -> stringResource(R.string.red_porque_etiqueta, m.etiqueta)
    io.github.fogonapps.cajon.RedSocial.Companion.Motivo.Nueva -> stringResource(R.string.red_porque_nueva)
    null -> stringResource(R.string.menu_por_que_sigues)
}

/* ----- Tu cajón guardado en tu cuenta ----- */

@Composable
fun CajonEnCuenta(vm: CajonViewModel) {
    val f = FogonTema.actual
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    val cuenta by vm.red.cuenta.collectAsStateWithLifecycle()
    val bloqueado by vm.red.cajonBloqueado.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val conLlave = vm.hayLlaveCajon()
    var clave by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.red.revisarServidor() }
    Cabecera(stringResource(R.string.nube_titulo), vm::cerrar)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.nube_explica), style = MaterialTheme.typography.bodyMedium, color = f.texto)
        Pista(stringResource(R.string.nube_cifrado))
        val oculto = androidx.compose.ui.text.input.PasswordVisualTransformation()
        when {
            usuario == null -> BotonRiso(stringResource(R.string.red_crear_cuenta), { vm.abrir(Pantalla.CuentaRed) }, Modifier.fillMaxWidth())
            bloqueado -> {
                Text(stringResource(R.string.nube_bloqueado), style = MaterialTheme.typography.titleSmall, color = f.texto)
                OutlinedTextField(clave, { clave = it.take(200) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.red_clave)) }, singleLine = true, shape = forma(18.dp), visualTransformation = oculto)
                BotonRiso(stringResource(R.string.nube_abrir), { vm.red.desbloquearCajon(clave); clave = "" }, Modifier.fillMaxWidth(), habilitado = clave.length >= 8)
            }
            !conLlave -> {
                Text(stringResource(if (cuenta?.correo.isNullOrBlank()) R.string.nube_falta_correo else R.string.nube_falta_clave), style = MaterialTheme.typography.titleSmall, color = f.texto)
                if (!cuenta?.correo.isNullOrBlank()) {
                    OutlinedTextField(clave, { clave = it.take(200) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.red_clave_nueva)) }, singleLine = true, shape = forma(18.dp),
                        visualTransformation = oculto, supportingText = { Text(stringResource(R.string.red_clave_regla)) })
                    BotonRiso(stringResource(R.string.nube_activar), { vm.red.ponerClave(clave, "") { clave = "" } }, Modifier.fillMaxWidth(), habilitado = clave.length >= 8 && cuenta?.conClave != true)
                    if (cuenta?.conClave == true) {
                        Pista(stringResource(R.string.nube_ya_tienes_clave))
                        BotonRiso(stringResource(R.string.nube_abrir), { vm.red.desbloquearCajon(clave); clave = "" }, Modifier.fillMaxWidth(), habilitado = clave.length >= 8)
                    }
                } else BotonSecundario(stringResource(R.string.red_tu_cuenta), { vm.abrir(Pantalla.CuentaRed) }, Modifier.fillMaxWidth())
            }
            else -> {
                Text(when (sync.estado) {
                    io.github.fogonapps.cajon.EstadoSync.SINCRONIZANDO -> stringResource(R.string.nube_guardando)
                    io.github.fogonapps.cajon.EstadoSync.ERROR, io.github.fogonapps.cajon.EstadoSync.SIN_CONEXION -> stringResource(R.string.nube_sin_conexion)
                    else -> if (sync.ultima > 0) stringResource(R.string.nube_guardado, haceCuanto(sync.ultima)) else stringResource(R.string.nube_listo)
                }, style = MaterialTheme.typography.titleSmall, color = f.acentoTexto)
                BotonSecundario(stringResource(R.string.nube_guardar_ya), { vm.sincronizar() }, Modifier.fillMaxWidth())
            }
        }
        Pista(stringResource(R.string.nube_olvido))
    }
}

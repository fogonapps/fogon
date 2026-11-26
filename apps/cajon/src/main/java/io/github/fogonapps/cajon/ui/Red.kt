package io.github.fogonapps.cajon.ui

import io.github.fogonapps.perfil.*
import io.github.fogonapps.perfil.Perfil as PerfilComun
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
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
import io.github.fogonapps.cajon.datos.arbolComentarios
import io.github.fogonapps.cajon.datos.imagenesPublicacion
import io.github.fogonapps.cajon.datos.ClienteSocial
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.PersonaSocial
import io.github.fogonapps.cajon.datos.PublicacionSocial
import io.github.fogonapps.cajon.datos.Visibilidad
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.FormaPildora
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.forma
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/* ----- Piezas ----- */

private fun JsonObject.texto(clave: String) = (this[clave] as? JsonPrimitive)?.content.orEmpty()

/** La foto de una persona de la red (o su inicial, en un círculo de vidrio). */
@Composable
fun AvatarRed(p: PersonaSocial, tamano: Dp = 44.dp) { io.github.fogonapps.social.ui.AvatarRed(p, tamano) }

/** Una persona en una lista: su foto, su nombre y su @usuario; [derecha] para un botón. */
@Composable
fun FilaPersona(p: PersonaSocial, onClick: () -> Unit, modifier: Modifier = Modifier, derecha: @Composable () -> Unit = {}) { io.github.fogonapps.social.ui.FilaPersona(p, onClick, modifier, derecha) }

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
    val razon = motivo?.let { motivoTexto(it) }
    val interfaz = interfazSocial(vm, motivo)
    io.github.fogonapps.social.ui.TarjetaPublicacion(interfaz, p, modifier, abrir, razon)
}

@Composable
internal fun CuerpoPublicacionCajon(vm: CajonViewModel, p: PublicacionSocial, destapado: Boolean) {
    val f = FogonTema.actual
    val d = p.datos
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
                val nota = (d["nota"] as? JsonPrimitive)?.intOrNull
                if (nota != null) NotaResena(nota)
                else (d["estrellas"] as? JsonPrimitive)?.doubleOrNull?.let { Estrellas((it * 2).toInt(), 16.sp) }
            }
        }
        if (p.tipo == "resena") {
            val e by vm.estado.collectAsStateWithLifecycle()
            val o = e?.let { estado -> estado.vivas.firstOrNull { it.fuente == d.texto("fuente") }
                ?: estado.artistasVivos.firstOrNull { it.fuente == d.texto("fuente") }?.let { io.github.fogonapps.cajon.datos.Resenas.obra(estado, it.id) } }
            d.texto("parte").takeIf { it.isNotBlank() }?.let {
                Text(d.texto("parteEtiqueta").ifBlank { io.github.fogonapps.cajon.datos.Resenas.etiqueta(it, o, vm.idioma() == "en") }, style = MaterialTheme.typography.labelMedium, color = f.textoSuave)
            }
            if (d.texto("spoiler") != "true" || destapado) CarruselFotos(imagenesPublicacion(d))
            val yo by vm.red.usuario.collectAsStateWithLifecycle()
            if (p.autor.usuario != yo && !io.github.fogonapps.social.ui.esCalificacion(p)) {
                val estadoUtil = stringResource(if (p.yoUtil) R.string.resena_util_activo else R.string.resena_util_inactivo)
                Text(stringResource(R.string.resena_util, p.utiles), style = MaterialTheme.typography.labelLarge,
                    color = f.texto, fontWeight = if (p.yoUtil) FontWeight.Bold else null,
                    modifier = Modifier.semantics { stateDescription = estadoUtil }
                        .toggleable(value = p.yoUtil, role = Role.Checkbox, onValueChange = {
                            if (yo == null) vm.abrir(Pantalla.CuentaRed) else vm.red.util(p)
                        }).heightIn(min = 48.dp).padding(12.dp))
            }
        }
}


/* ----- El feed en el Inicio ----- */

/** Preferencias locales de la interfaz del Inicio (no se sincronizan). */
private const val PREFS_INICIO_UI = "inicio-ui"
private const val CLAVE_INVITA_CERRADA = "invita_red_cerrada"

/** En el Inicio: lo último de tu gente (o la invitación a la red, si no hay cuenta). */
@Composable
fun FeedInicio(vm: CajonViewModel) {
    val f = FogonTema.actual
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    val feed by vm.red.feed.collectAsStateWithLifecycle()
    val sinLeer by vm.red.sinLeer.collectAsStateWithLifecycle()
    // Al instante lo guardado en el teléfono; lo nuevo llega después.
    LaunchedEffect(Unit) { vm.red.feedGuardado() }
    LaunchedEffect(usuario) { if (usuario != null) vm.red.cargarFeed(avisar = false) }
    if (usuario == null) {
        // Una franja plana de una línea que se puede cerrar; cerrada no vuelve (se guarda solo en este teléfono).
        val ctx = LocalContext.current
        val prefs = remember { ctx.getSharedPreferences(PREFS_INICIO_UI, android.content.Context.MODE_PRIVATE) }
        var cerrada by remember { mutableStateOf(prefs.getBoolean(CLAVE_INVITA_CERRADA, false)) }
        if (cerrada) return
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconoRiso(Iconos.Gente, null, tamano = 20.dp, tinta = f.texto)
            Text(stringResource(R.string.red_invita_corta), style = MaterialTheme.typography.bodyMedium, color = f.texto, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            BotonPildora(stringResource(R.string.red_crear_cuenta_corto), { vm.abrir(Pantalla.CuentaRed) })
            BotonIcono(Iconos.Cerrar, stringResource(R.string.descartar)) {
                prefs.edit().putBoolean(CLAVE_INVITA_CERRADA, true).apply(); cerrada = true
            }
        }
        return
    }
    Seccion(stringResource(R.string.red_de_tu_gente)) {
        Pildora(if (sinLeer > 0) "$sinLeer" else "", Iconos.Campana, acento = sinLeer > 0) { vm.abrir(Pantalla.AvisosRed) }
        Pildora(stringResource(R.string.red_ver_todo)) { vm.irA(io.github.fogonapps.cajon.Pestana.GENTE) }
    }
    val lista = feed
    when {
        lista == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.texto, trackColor = f.linea)
        lista.isEmpty() -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pista(stringResource(R.string.red_feed_vacio))
            Row { Pildora(stringResource(R.string.red_explorar), Iconos.Gente) { vm.irA(io.github.fogonapps.cajon.Pestana.GENTE) } }
        }
        else -> lista.take(4).forEach { TarjetaPublicacion(vm, it) }
    }
}

/* ----- El feed completo ----- */

/**
 * Para ti: la red. Primero (si falta) elegir lo que te gusta, con eso se arma lo que ves; después crear la cuenta.
 * Ya con cuenta: lo de quienes sigues o lo de todos, ordenado por tus gustos.
 */
@Composable
fun PantallaRed(vm: CajonViewModel, e: Estado, enPestana: Boolean = false) {
    val f = FogonTema.actual
    val feed by vm.red.feed.collectAsStateWithLifecycle()
    val explorar by vm.red.explorar.collectAsStateWithLifecycle()
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    val sinLeer by vm.red.sinLeer.collectAsStateWithLifecycle()
    var pestana by rememberSaveable(usuario != null) { mutableStateOf(if (usuario == null) 1 else 0) }
    LaunchedEffect(pestana, usuario) { if (pestana == 0) vm.red.cargarFeed() else vm.red.cargarExplorar(); vm.red.cargarHistorias(); vm.red.revisarSinLeer() }
    // Arriba: tu foto al principio (abre tu perfil), el título y, a la derecha, avisos y mensajes con lo que tengan sin leer.
    val chats by vm.chat.conversaciones.collectAsStateWithLifecycle()
    val sinLeerChat = remember(chats) { vm.chat.sinLeer() }
    if (enPestana) {
        // En la pestaña, el mismo título que las demás; a la derecha, avisos, mensajes y tu foto (abre tu perfil).
        Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.weight(1f)) { TituloPestana(stringResource(R.string.red_titulo)) }
            if (usuario != null) {
                ConCuenta(sinLeer) { BotonIcono(Iconos.Campana, stringResource(R.string.red_avisos)) { vm.abrir(Pantalla.AvisosRed) } }
                ConCuenta(sinLeerChat) { BotonIcono(Iconos.Enviar, stringResource(R.string.chat_titulo), lleno = sinLeerChat > 0) { vm.abrir(Pantalla.Chats()) } }
            }
            Box(Modifier.padding(start = 4.dp)) {
                FotoPerfil(e.perfil.foto, 36.dp, e.perfil.encuadreFoto, descripcion = stringResource(R.string.tu_perfil)) { vm.abrirMiPerfil() }
            }
        }
    } else Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BotonIcono(Iconos.Atras, stringResource(R.string.volver)) { vm.cerrar() }
        FotoPerfil(e.perfil.foto, 40.dp, e.perfil.encuadreFoto, descripcion = stringResource(R.string.tu_perfil)) { vm.abrirMiPerfil() }
        Text(stringResource(R.string.red_titulo), fontFamily = f.titulos, fontSize = 24.sp, color = f.texto,
            modifier = Modifier.weight(1f).semantics { heading() })
        if (usuario != null) {
            ConCuenta(sinLeer) { BotonIcono(Iconos.Campana, stringResource(R.string.red_avisos), grande = true) { vm.abrir(Pantalla.AvisosRed) } }
            ConCuenta(sinLeerChat) { BotonIcono(Iconos.Enviar, stringResource(R.string.chat_titulo), grande = true, lleno = sinLeerChat > 0) { vm.abrir(Pantalla.Chats()) } }
        }
    }
    CrearClaveAviso(vm)
    // Paso 1: lo que te gusta (con eso se ordena lo que ves). Paso 2: tu cuenta.
    if (!e.ajustes.gustosListos) {
        PasoGustos(vm)
        if (usuario == null) return
    }
    // Sin cuenta: una franja de una línea con su botón, no una tarjeta.
    if (usuario == null) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.red_franja_cuenta), style = MaterialTheme.typography.bodyMedium, color = f.texto, modifier = Modifier.weight(1f))
            BotonPildora(stringResource(R.string.red_franja_crear), { vm.abrir(Pantalla.CuentaRed) })
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(f.linea))
    }
    if (usuario != null) io.github.fogonapps.social.ui.BarraHistorias(interfazSocial(vm))
    // Arriba, con y sin cuenta: reseñas públicas recientes y con me gusta (salen de Explorar, que se lee sin sesión).
    val destacadas = ResenasDeLaComunidad(vm, e, usuario)
    PestanasGente(pestana, usuario != null, { pestana = it }) { vm.abrir(Pantalla.Publicar()) }
    if (pestana == 0 && usuario == null) { GenteVacia(stringResource(R.string.red_feed_sin_cuenta)); return }
    val ocultas by vm.red.ocultas.collectAsStateWithLifecycle()
    val lista = (if (pestana == 0) feed?.map { it to null } else explorar?.let { remember(it, e) { io.github.fogonapps.cajon.RedSocial.ordenarPorGustos(it, e) } })
        ?.filter { (p, _) -> io.github.fogonapps.cajon.RedSocial.seMuestra(p, e, ocultas) && (pestana == 0 || p.id !in destacadas) }
    // Arriba de cada pestaña: en Siguiendo, lo de la semana en tu red; en Explorar, gente con tus gustos.
    if (pestana == 0) feed?.let { f0 -> SemanaEnTuRed(vm, remember(f0) { io.github.fogonapps.cajon.RedSocial.semanaEnTuRed(f0) }) }
    else explorar?.let { ex ->
        val siguiendo = remember(feed) { feed.orEmpty().map { it.autor.usuario }.toSet() }
        GenteConTusGustos(vm, remember(ex, siguiendo, e.vivas.size) { io.github.fogonapps.cajon.RedSocial.genteParecida(ex, siguiendo, usuario, e) })
    }
    if (lista != null && lista.isEmpty()) GenteVacia(stringResource(if (pestana == 0) R.string.red_feed_vacio else R.string.red_explorar_vacio))
    else io.github.fogonapps.social.ui.ListaFeedSocial(lista, pestana, { vm.red.masFeed() }, stringResource(R.string.red_al_dia_texto)) { p, motivo -> TarjetaPublicacion(vm, p, motivo = motivo) }
}

/**
 * Las reseñas de la comunidad que se destacan arriba de Gente: reseñas con palabras (no solo estrellas), de otras
 * personas, del último mes si hay al menos tres (si no, de cuando sean), con más me gusta primero y, entre iguales, la
 * más reciente.
 */
internal fun resenasDeLaComunidad(publicaciones: List<PublicacionSocial>, yo: String?, ahora: Long = System.currentTimeMillis(), cuantas: Int = 8): List<PublicacionSocial> {
    val resenas = publicaciones.distinctBy { it.id }
        .filter { it.tipo == "resena" && it.autor.usuario != yo && it.visibilidad == "publica" && it.datos.texto("texto").isNotBlank() }
    val recientes = resenas.filter { ahora - it.cuando <= 30L * 86_400_000L }.takeIf { it.size >= 3 } ?: resenas
    return recientes.sortedWith(compareByDescending<PublicacionSocial> { it.megusta }.thenByDescending { it.cuando }).take(cuantas)
}

/** «Reseñas de la comunidad»: una tira con la tarjeta de siempre. Devuelve los ids que muestra (para no repetirlos abajo). */
@Composable
private fun ResenasDeLaComunidad(vm: CajonViewModel, e: Estado, usuario: String?): Set<Long> {
    val explorar by vm.red.explorar.collectAsStateWithLifecycle()
    val feed by vm.red.feed.collectAsStateWithLifecycle()
    val ocultas by vm.red.ocultas.collectAsStateWithLifecycle()
    // Con cuenta se abre en Siguiendo: Explorar se pide igual, una vez, para tener de dónde sacarlas.
    LaunchedEffect(usuario) { if (vm.red.explorar.value == null) vm.red.cargarExplorar() }
    val lista = remember(explorar, feed, ocultas, usuario, e.vivas.size) {
        resenasDeLaComunidad(explorar.orEmpty() + feed.orEmpty(), usuario).filter { io.github.fogonapps.cajon.RedSocial.seMuestra(it, e, ocultas) }
    }
    if (lista.isEmpty()) return emptySet()
    Seccion(stringResource(R.string.gente_resenas_comunidad))
    androidx.compose.foundation.lazy.LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(lista.size, key = { lista[it].id }) { i -> TarjetaPublicacion(vm, lista[i], Modifier.width(300.dp)) }
    }
    return remember(lista) { lista.map { it.id }.toSet() }
}

/** Siguiendo y Explorar como pestañas de texto: la elegida en tinta y subrayada, la otra apagada. A la derecha, publicar. */
@Composable
private fun PestanasGente(pestana: Int, conCuenta: Boolean, cambiar: (Int) -> Unit, publicar: () -> Unit) {
    val f = FogonTema.actual
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf(R.string.red_pestana_siguiendo, R.string.red_explorar).forEachIndexed { i, t ->
            val elegida = pestana == i
            Column(Modifier.clip(forma(8.dp)).clickable { cambiar(i) }.semantics { selected = elegida; role = Role.Tab }
                .heightIn(min = 48.dp).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(t), fontFamily = f.titulos, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                    color = if (elegida) f.texto else f.texto.copy(alpha = 0.32f))
                Box(Modifier.width(24.dp).height(2.dp).background(if (elegida) f.texto else androidx.compose.ui.graphics.Color.Transparent))
            }
        }
        // Publicar ocupa lo que dejan las pestañas. Si no cabe con su texto (pantalla angosta, letra grande, quedaba
        // «+ Publi…»), queda solo el «+» con borde, que el lector de pantalla sigue llamando «Publicar».
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (conCuenta) {
                val texto = stringResource(R.string.red_publicar)
                val medidor = androidx.compose.ui.text.rememberTextMeasurer()
                val estilo = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                val necesita = with(androidx.compose.ui.platform.LocalDensity.current) { medidor.measure(texto, estilo).size.width.toDp() } + 18.dp + 6.dp + 28.dp + 2.dp
                if (maxWidth >= necesita) BotonPildoraBorde(texto, publicar, icono = Iconos.Mas)
                else Box(Modifier.size(48.dp).clip(CircleShape).border(1.dp, f.texto, CircleShape).clickable(role = Role.Button, onClick = publicar)
                    .semantics { contentDescription = texto }, contentAlignment = Alignment.Center) {
                    IconoRiso(Iconos.Mas, null, tamano = 20.dp, tinta = f.texto)
                }
            }
        }
    }
}

/** El feed vacío: un ícono, una frase en la letra de los títulos y lo que falta, sin dejar un hueco enorme. */
@Composable
private fun GenteVacia(texto: String) {
    val f = FogonTema.actual
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        IconoRiso(Iconos.Gente, null, tamano = 40.dp, tinta = f.texto)
        Text(stringResource(R.string.red_vacio_titulo), fontFamily = f.titulos, fontSize = 22.sp, lineHeight = 28.sp, color = f.texto,
            textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Text(texto, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp), color = f.textoSuave, textAlign = TextAlign.Center)
    }
}

/** «Esta semana en tu red»: las obras de las que más habló tu gente estos 7 días, con sus caras. */
@Composable
private fun SemanaEnTuRed(vm: CajonViewModel, obras: List<io.github.fogonapps.cajon.RedSocial.Companion.ObraDeLaSemana>) {
    val f = FogonTema.actual
    if (obras.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.semana_red_titulo), fontFamily = f.titulos, fontSize = 20.sp, color = f.texto, modifier = Modifier.semantics { heading() })
        obras.forEach { o ->
            val d = o.datos
            Row(Modifier.fillMaxWidth().clip(forma(12.dp)).clickable {
                vm.abrirObraDePerfil(d.texto("titulo"), d.texto("tipoObra").ifBlank { "pelicula" }, d.texto("portada"), d.texto("fuente"), d.texto("anio").toIntOrNull(), d.texto("creador"))
            }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Portada(d.texto("portada"), d.texto("tipoObra").ifBlank { "pelicula" }, d.texto("titulo"), Modifier.width(44.dp), esquina = 8.dp, conTitulo = false)
                Column(Modifier.weight(1f)) {
                    Text(d.texto("titulo"), style = MaterialTheme.typography.titleSmall, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (o.quienes.size == 1) "@" + o.quienes.first().usuario else pluralStringResource(R.plurals.semana_red_personas, o.quienes.size - 1, "@" + o.quienes.first().usuario, o.quienes.size - 1),
                        style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) { o.quienes.take(3).forEach { AvatarRed(it, 28.dp) } }
            }
        }
    }
}

/** «Gente con tus gustos»: quienes hablaron de lo que a ti también te gusta. Sin números: solo lo que comparten. */
@Composable
private fun GenteConTusGustos(vm: CajonViewModel, gente: List<io.github.fogonapps.cajon.RedSocial.Companion.GenteParecida>) {
    val f = FogonTema.actual
    if (gente.isEmpty()) return
    Seccion(stringResource(R.string.gente_gustos_titulo))
    androidx.compose.foundation.lazy.LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(gente.size) { i ->
            val g = gente[i]
            Column(Modifier.width(150.dp).clip(forma(18.dp))
                .clickable { vm.abrir(Pantalla.PerfilRed(g.persona.usuario)) }.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AvatarRed(g.persona, 56.dp)
                Text("@" + g.persona.usuario, style = MaterialTheme.typography.labelLarge, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.gente_gustos_comparten, g.enComun.take(2).joinToString(", ")), style = MaterialTheme.typography.labelSmall,
                    color = f.textoSuave, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * Para cuentas con correo y sin contraseña: crearla aquí mismo. Sin contraseña el cajón no se puede guardar cifrado
 * en la cuenta, así que se pide donde se ve (tu perfil y Para ti).
 */
@Composable
fun CrearClaveAviso(vm: CajonViewModel) {
    val f = FogonTema.actual
    val cuenta by vm.red.cuenta.collectAsStateWithLifecycle()
    val c = cuenta ?: return
    if (c.conClave || c.correo.isBlank()) return
    var clave by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.red_sin_clave_crea), style = MaterialTheme.typography.titleMedium, color = f.texto)
        OutlinedTextField(clave, { clave = it.take(200) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.red_clave)) }, singleLine = true, shape = forma(18.dp),
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = { Text(stringResource(R.string.red_clave_regla)) })
        BotonPildora(stringResource(R.string.guardar), { vm.red.ponerClave(clave, "") { clave = "" } }, Modifier.fillMaxWidth(), habilitado = clave.length >= 8)
    }
}

/** Las frases que propuso la gente y esperan aprobación. */
@Composable
internal fun FrasesPropuestas(vm: CajonViewModel) {
    val f = FogonTema.actual
    val frases by vm.red.frasesPendientes.collectAsStateWithLifecycle()
    if (frases.isNotEmpty()) {
        Seccion(stringResource(R.string.mod_frases))
        frases.forEach { fr ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(fr.texto, style = MaterialTheme.typography.bodyLarge, color = f.texto)
                Text("@${fr.de} · ${nombresApps()[fr.app] ?: fr.app} · ${fr.idioma}", style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BotonPildora(stringResource(R.string.mod_aprobar), { vm.red.decidirFrase(fr.clave, true) })
                    BotonPildoraBorde(stringResource(R.string.mod_rechazar), { vm.red.decidirFrase(fr.clave, false) })
                }
            }
        }
    }
}

/**
 * Las frases del saludo, para el equipo: agregar una (sale para todos) y ver todas (las de la app y las de la gente)
 * ordenadas por lo que vota la comunidad, con su botón para quitarla.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun FrasesDelEquipo(vm: CajonViewModel) {
    val f = FogonTema.actual
    val votos by vm.frasesComunidad.collectAsStateWithLifecycle()
    val deLaGente by vm.frasesDeLaGente.collectAsStateWithLifecycle()
    val ocultas by vm.frasesOcultas.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.cargarFrases(forzar = true) }
    var nueva by rememberSaveable { mutableStateOf("") }
    var idioma by rememberSaveable { mutableStateOf("es") }
    var franja by rememberSaveable { mutableStateOf("todo") }
    // Para qué app es la frase nueva: cada app tiene sus frases del saludo (las de El Cajón se listan abajo).
    var app by rememberSaveable { mutableStateOf("cajon") }
    val franjas = listOf("todo" to R.string.franja_todo, "manana" to R.string.franja_manana, "tarde" to R.string.franja_tarde,
        "noche" to R.string.franja_noche, "madrugada" to R.string.franja_madrugada)
    val nombreFranja = franjas.associate { (k, r) -> k to stringResource(r) }
    Seccion(stringResource(R.string.mod_frases_saludo))
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(nueva, { nueva = it.take(90) }, Modifier.fillMaxWidth(), singleLine = true, shape = forma(18.dp), placeholder = { Text(stringResource(R.string.mod_frase_nueva)) })
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            nombresApps().forEach { (k, nombre) -> ChipPerfil(nombre, app == k) { app = k } }
        }
        // Cuándo sale: todo el día o una franja.
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            franjas.forEach { (k, _) -> ChipPerfil(nombreFranja.getValue(k), franja == k) { franja = k } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ChipPerfil("ES", idioma == "es") { idioma = "es" }
            ChipPerfil("EN", idioma == "en") { idioma = "en" }
            Box(Modifier.weight(1f))
            BotonPildora(stringResource(R.string.mod_agregar), { vm.agregarFrase(nueva, idioma, franja, app); nueva = "" }, habilitado = nueva.trim().length >= 4)
        }
    }
    // Todas, la más votada primero (sí menos no).
    val textos = TEXTOS_FRASES.filterKeys { it !in ocultas }.map { (k, r) -> Triple(k, stringResource(r, "…"), franjaDeFrase(k)) } +
        deLaGente.map { Triple(it.clave, it.texto, it.franja) }
    textos.sortedByDescending { (k, _, _) -> votos[k]?.let { it.si - it.no } ?: 0 }.forEach { (k, texto, fr) ->
        val v = votos[k]
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(texto, style = MaterialTheme.typography.bodyMedium, color = f.texto)
                Text(nombreFranja[fr].orEmpty() + " · " + stringResource(R.string.mod_votos, v?.si ?: 0, v?.no ?: 0), style = MaterialTheme.typography.labelSmall, color = f.textoSuave)
            }
            BotonIcono(Iconos.Borrar, stringResource(R.string.mod_quitar_frase)) { vm.quitarFrase(k) }
        }
    }
}

/** Un botón con su número arriba (lo que hay sin leer). */
@Composable
fun ConCuenta(n: Int, boton: @Composable () -> Unit) = io.github.fogonapps.identidad.ConCuentaSocial(n, boton)

/** El paso 1 de Para ti: elegir lo que te gusta (lo más visto, escuchado y leído), en "Cuéntame tus gustos". */
@Composable
private fun PasoGustos(vm: CajonViewModel) {
    val f = FogonTema.actual
    val opciones by vm.paraElegir.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.cargarParaElegir() }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.red_paso1_titulo), fontFamily = f.titulos, fontSize = 22.sp, lineHeight = 28.sp, color = f.texto, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.red_paso1_texto), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
        // Una probadita de lo que se elige: portadas de lo del momento. El espacio está reservado desde el principio
        // (recuadros mientras cargan; el aviso, si falló, en el mismo lugar): así los botones no se mueven bajo el dedo.
        val portadas = (opciones.cine.map { it.portada } + opciones.libros.map { it.portada }).filter { it.isNotBlank() }.take(4)
        Box(Modifier.fillMaxWidth().aspectRatio(2.55f), contentAlignment = Alignment.Center) {
            if (portadas.isEmpty() && opciones.avisos.isNotEmpty()) Pista(stringResource(opciones.avisos.first()))
            else Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until 4) {
                    val url = portadas.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(0.68f).clip(forma(12.dp)).background(tonoPapel())) {
                        if (url != null) coil3.compose.AsyncImage(url, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                    }
                }
            }
        }
        BotonPildora(stringResource(R.string.red_paso1_boton), { vm.abrir(Pantalla.ElegirGustos) }, Modifier.fillMaxWidth())
        BotonPildoraBorde(stringResource(R.string.ahora_no), { vm.gustosListos() }, Modifier.fillMaxWidth())
    }
}

/* ----- El perfil de otra persona (o el propio, como lo ve la red) ----- */

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PerfilRed(vm: CajonViewModel, usuario: String) {
    val f = FogonTema.actual
    val ctx = LocalContext.current
    val perfiles by vm.red.perfiles.collectAsStateWithLifecycle()
    val noEstan by vm.red.noEstan.collectAsStateWithLifecycle()
    val listas by vm.red.listas.collectAsStateWithLifecycle()
    val yo by vm.red.usuario.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var hoja by remember { mutableStateOf<String?>(null) }
    var pestana by rememberSaveable(usuario) { mutableStateOf(PESTANA_INICIAL) }
    // Con la sesión lista (un enlace puede abrir el perfil antes), y de nuevo si no llegó.
    LaunchedEffect(usuario, yo) { vm.red.verPerfil(usuario) }
    val p = perfiles[usuario]
    if (p == null && usuario !in noEstan) LaunchedEffect(usuario) { kotlinx.coroutines.delay(4000); while (perfiles[usuario] == null) { vm.red.verPerfil(usuario); kotlinx.coroutines.delay(6000) } }
    val propio = usuario == yo
    // Dejar de seguir pide confirmación (el botón dice «Siguiendo» o «Amigos», como en las redes de fotos).
    val confirmarDejarDeSeguir = LocalConfirmar.current
    val preguntaDejar = stringResource(R.string.dejar_de_seguir_pregunta, usuario)
    val textoDejar = stringResource(R.string.dejar_de_seguir)
    fun confirmarDejar() = confirmarDejarDeSeguir(preguntaDejar, null) { vm.red.dejarDeSeguir(usuario) }
    val navegacion: @Composable () -> Unit = {
        val f = FogonTema.actual // los colores del fondo del perfil, no los de la app
        // El @usuario va bajo el nombre; arriba, solo volver y el menú.
        Cabecera("", vm::cerrar, chico = true) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Un solo botón: compartir, copiar el enlace, bloquear y reportar van en el menú.
                Box {
                    BotonIcono(Iconos.Opciones, stringResource(R.string.red_mas_opciones)) { menu = true }
                    // El menú de los tres puntos, desplegado desde ellos (como en las redes).
                    val portapapeles = androidx.compose.ui.platform.LocalClipboardManager.current
                    val copiado = stringResource(R.string.red_enlace_copiado)
                    androidx.compose.material3.DropdownMenu(menu, { menu = false }, shape = forma(24.dp), containerColor = f.fondo,
                        border = androidx.compose.foundation.BorderStroke(f.borde, f.linea), shadowElevation = 12.dp) {
                        @Composable
                        fun Opcion(icono: io.github.fogonapps.identidad.Dibujo, texto: String, peligro: Boolean = false, onClick: () -> Unit) {
                            val color = if (peligro) MaterialTheme.colorScheme.error else f.texto
                            androidx.compose.material3.DropdownMenuItem({ Text(texto, style = MaterialTheme.typography.bodyLarge, color = color) }, { menu = false; onClick() },
                                leadingIcon = { IconoRiso(icono, null, tamano = 22.dp, tinta = color) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 6.dp))
                        }
                        Opcion(Iconos.Enlace, stringResource(R.string.red_copiar_enlace)) { portapapeles.setText(androidx.compose.ui.text.AnnotatedString(ClienteSocial.enlacePerfil(usuario))); vm.decir(copiado) }
                        Opcion(Iconos.Compartir, stringResource(R.string.compartir_perfil)) { compartirTexto(ctx, ClienteSocial.enlacePerfil(usuario)) }
                        if (yo != null) Opcion(Iconos.Cerrar, stringResource(R.string.red_bloquear)) { hoja = "bloquear" }
                        if (yo != null) Opcion(Iconos.Bandera, stringResource(R.string.red_reportar), peligro = true) { hoja = "reportar" }
                    }
                }
            }
        }
    }

    if (p == null) {
        navegacion()
        if (usuario in noEstan) Pista(stringResource(R.string.red_perfil_no_existe), Modifier.padding(16.dp))
        else LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.texto, trackColor = f.linea)
        return
    }
    @Composable
    fun contenidoParte(idSeccion: Int) {
        val f = FogonTema.actual // los colores del fondo del perfil, no los de la app
        when (idSeccion) {
            0 -> if (p.publicaciones.isEmpty()) NadaAqui() else CuadriculaPublicaciones(vm, p.publicaciones)
            1 -> GustosDelPerfil(vm, p, propio)
            2 -> MusicaDelPerfil(p, propio)
            3 -> p.publicaciones.filter { it.tipo == "lista" }.let { l -> if (l.isEmpty()) NadaAqui() else CuadriculaPublicaciones(vm, l) }
            4 -> if (!p.guardadasVisibles) NadaAqui() else {
                LaunchedEffect(usuario) { vm.red.cargarGuardadasDe(usuario) }
                val g = listas["guardadas:$usuario"]
                if (g == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.texto, trackColor = f.linea)
                else if (g.isEmpty()) NadaAqui() else CuadriculaPublicaciones(vm, g)
            }
            6 -> p.resenasDelPerfil().let { lista ->
                if (lista.isEmpty()) NadaAqui() else lista.forEach { TarjetaPublicacion(vm, it) }
            }
            else -> interfazSocial(vm).contenido.CifrasPerfil(p)
        }
    }

    @Composable
    fun contenidoSeccion(idSeccion: Int) {
        // «Lo tuyo» junta los destacados, los artistas y los géneros (GustosDelPerfil los ordena).
        contenidoParte(idSeccion)
    }
    val contenidoApp = interfazSocial(vm).contenido
    val secciones = contenidoApp.SeccionesPerfil(p, propio) { contenidoSeccion(it) }
    val vistaBase = p.aPerfilVista(secciones = secciones, contadores = listOf(
        // Como en las otras apps (y en Instagram): primero quienes te siguen.
        ContadorPerfil("${p.seguidores}", stringResource(R.string.red_seguidores)) { if (!p.cerrada) vm.abrir(Pantalla.GenteRed(usuario, true)) },
        ContadorPerfil("${p.siguiendo}", stringResource(R.string.red_siguiendo)) { if (!p.cerrada) vm.abrir(Pantalla.GenteRed(usuario, false)) },
        ContadorPerfil(cifraCorta(p.megusta), stringResource(R.string.red_me_gusta_cifra)),
    ))
    val uri = LocalUriHandler.current
    // «Lo quiero»: tocar su banner o su marco abre el artículo (tienda, regalo o el mercado si es de un creador).
    var loQuiero by remember(usuario) { mutableStateOf<String?>(null) }
    loQuiero?.let { io.github.fogonapps.social.ui.HojaLoQuiero(interfazSocial(vm), it) { loQuiero = null } }
    val articuloPortada = if (propio) null else io.github.fogonapps.social.ui.articuloDePortada(p.portada)
    val verPortada = stringResource(io.github.fogonapps.social.ui.R.string.social_portada_ajena)
    PerfilComun(vistaBase.copy(tocarMarco = if (propio) null else ({ loQuiero = "marco:${vistaBase.marco}" }),
        etiquetaMarco = stringResource(io.github.fogonapps.social.ui.R.string.social_marco_ajeno)), pestana, { pestana = it },
        navegacion = navegacion,
        portada = {
            if (p.portada.isNotBlank()) io.github.fogonapps.social.ui.PortadaPerfilRed(p.portada, Modifier.matchParentSize(), interfazSocial(vm))
            if (articuloPortada != null) Box(Modifier.matchParentSize().clickable(onClickLabel = verPortada) { loQuiero = articuloPortada })
        },
        foto = { io.github.fogonapps.social.ui.AvatarPerfilConHistorias(interfazSocial(vm), p.persona) { Box(Modifier.clip(CircleShape).border(4.dp, FogonTema.actual.fondo, CircleShape)) { AvatarRed(p.persona, 96.dp) } } },
        bajoNombre = { if (p.meSigue && !propio) Text(stringResource(R.string.red_te_sigue), style = MaterialTheme.typography.bodySmall, color = FogonTema.actual.textoSuave) },
        antesBio = { io.github.fogonapps.social.ui.MercadoEnPerfil(interfazSocial(vm), p) },
        enlace = { u -> EnlacePerfil(io.github.fogonapps.cajon.datos.Logica.enlaceCorto(u)) { runCatching { uri.openUri(u) } } },
        acciones = {
            val f = FogonTema.actual // los colores del fondo del perfil, no los de la app
            when {
                yo == null -> io.github.fogonapps.perfil.BotonPerfilDestacado(stringResource(R.string.red_crear_para_seguir), { vm.abrir(Pantalla.CuentaRed) }, Modifier.weight(1f))
                else -> {
                    when (p.relacion) {
                        // Se siguen mutuamente: son amigos (el botón lo dice; tocarlo deja de seguir).
                        "sigo" -> io.github.fogonapps.perfil.BotonPerfil(stringResource(if (p.meSigue) R.string.amigos_dejar else R.string.red_dejar_de_seguir), { confirmarDejar() }, Modifier.weight(1f))
                        "pendiente" -> io.github.fogonapps.perfil.BotonPerfil(stringResource(R.string.red_pendiente), { vm.red.dejarDeSeguir(usuario) }, Modifier.weight(1f))
                        else -> io.github.fogonapps.perfil.BotonPerfilDestacado(stringResource(if (p.meSigue) R.string.red_seguir_tambien else R.string.red_seguir), { vm.red.seguir(usuario) }, Modifier.weight(1f))
                    }
                    io.github.fogonapps.perfil.BotonPerfil(stringResource(R.string.chat_mensaje), { vm.abrir(Pantalla.Conversacion(usuario)) }, Modifier.weight(1f))
                    // A quien sigues: lo que pueden ver juntos, lo que ven a la vez y lo que ya vieron los dos.
                    if (p.relacion == "sigo" && !p.cerrada) io.github.fogonapps.perfil.BotonPerfil(stringResource(R.string.juntos), { vm.abrir(Pantalla.Juntos(usuario)) }, Modifier.weight(1f))
                }
            }
        },
        despuesAcciones = {
            val f = FogonTema.actual // los colores del fondo del perfil, no los de la app
            // Como en el tuyo: la canción, sus favoritas, la que le marcó y sus repisas (lo vacío no se ve).
            (p.partes["cancion"] as? JsonObject)?.let { c -> Box(Modifier.padding(vertical = 4.dp)) { TarjetaCancion(c.texto("cancion"), c.texto("creador"), c.texto("titulo"), c.texto("portada"), audio = c.texto("audio"), enlace = c.texto("enlace"), sonar = !propio, buscar = { vm.red.canciones(it) }) } }
            if (!p.cerrada) FavoritasDeLaRed(vm, p)
            if (!p.cerrada) io.github.fogonapps.social.ui.EscaparatesEnPerfil(interfazSocial(vm), usuario)
            // Bloquear o reportar (desde los tres puntos): la confirmación o el motivo, abajo.
            hoja?.let { h ->
                androidx.compose.material3.ModalBottomSheet({ hoja = null }, containerColor = f.fondo) {
                    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                        if (h == "reportar") {
                            MotivosReporte { codigo, texto ->
                                vm.red.reportar("cuenta", usuario, "$codigo: $texto"); hoja = null
                            }
                        } else {
                            Text(stringResource(R.string.red_bloquear_a, usuario), style = MaterialTheme.typography.titleMedium, color = f.texto, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                            Text(stringResource(R.string.chat_bloquear_seguro), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave, modifier = Modifier.padding(horizontal = 24.dp))
                            FilaMenu(Iconos.Cerrar, stringResource(R.string.red_bloquear), peligro = true) { vm.red.bloquear(usuario, true); hoja = null; vm.cerrar() }
                            FilaMenu(Iconos.Atras, stringResource(R.string.cancelar)) { hoja = null }
                        }
                    }
                }
            }
            if (p.cerrada) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconoRiso(Iconos.Candado, null, tamano = 36.dp, tinta = f.textoSuave)
                    Pista(stringResource(R.string.red_cuenta_privada_texto), centro = true)
                }
            }
        })
}

/** Las cifras que la persona deja ver, como en tu pestaña de cifras (las de antes solo traían lo terminado por tipo). */
@Composable
internal fun CifrasDeLaRed(p: io.github.fogonapps.cajon.datos.PerfilSocial) {
    val f = FogonTema.actual
    val c = p.partes["cifras"] as? JsonObject ?: run { NadaAqui(); return }
    val anio = (c["anio"] as? JsonPrimitive)?.content?.toIntOrNull() ?: java.time.LocalDate.now().year
    val valores = (c["valores"] as? JsonObject)?.mapValues { (it.value as? JsonPrimitive)?.content.orEmpty() }
        ?: mapOf(io.github.fogonapps.cajon.datos.PartesPerfil.C_TERMINADAS to "${c.values.sumOf { (it as? JsonPrimitive)?.content?.toIntOrNull() ?: 0 }}")
    if (valores.isEmpty()) { NadaAqui(); return }
    // Como en tu pestaña de cifras: el número grande en la letra de los títulos, sin caja.
    CifrasEnRejilla(valores.entries.map { (k, v) -> v to nombreCifra(k, anio) })
}

/** Las publicaciones en cuadrícula, con sus vistas: la foto, la portada de la obra o unas palabras. */
@Composable
fun CuadriculaPublicaciones(vm: CajonViewModel, lista: List<PublicacionSocial>) {
    val f = FogonTema.actual
    lista.chunked(3).forEach { fila ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            fila.forEach { p ->
                val d = p.datos
                val video = d.texto("video")
                var reproducir by remember(p.id) { mutableStateOf(false) }
                val descripcionVideo = if (video.isNotBlank()) pluralStringResource(R.plurals.video_reproducir, d.texto("duracion").toIntOrNull() ?: 0, d.texto("duracion").toIntOrNull() ?: 0) else ""
                if (reproducir) ReproductorVideo(vm, video) { reproducir = false }
                val imagen = (if (video.isNotBlank()) d.texto("poster") else d.texto("imagen")).takeIf { it.isNotBlank() }?.let { ClienteSocial.imagen(it) }
                    ?: d.texto("portada").ifBlank { (d["portadas"] as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.content }.orEmpty() }
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(0.75f).clip(forma(10.dp)).background(tonoPapel())
                        .clickable {
                            if (video.isNotBlank() && d.texto("spoiler") != "true") reproducir = true
                            else vm.abrir(Pantalla.PublicacionRed(p.id))
                        }.semantics { if (video.isNotBlank()) contentDescription = descripcionVideo }) {
                        if (imagen.isNotBlank()) coil3.compose.AsyncImage(imagen, null, Modifier.matchParentSize()
                            .then(if (d.texto("spoiler") == "true") Modifier.blur(20.dp) else Modifier), contentScale = ContentScale.Crop)
                        else Text(d.texto("titulo").ifBlank { d.texto("texto") }, style = MaterialTheme.typography.labelMedium, color = f.texto, maxLines = 5,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.dp))
                        if (video.isNotBlank()) Box(Modifier.align(Alignment.Center).size(40.dp).clip(CircleShape).background(f.fondo.copy(alpha = 0.75f)),
                            contentAlignment = Alignment.Center) { IconoRiso(Iconos.Reproducir, stringResource(R.string.video_play), tamano = 22.dp, tinta = f.texto) }
                        Row(Modifier.align(Alignment.BottomStart).padding(6.dp).clip(FormaPildora).background(f.fondo.copy(alpha = 0.7f)).padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            IconoRiso(Iconos.Anime, null, tamano = 12.dp, tinta = f.texto)
                            Text(cifraCorta(p.vistas), style = MaterialTheme.typography.labelSmall, color = f.texto)
                        }
                    }
                    if (io.github.fogonapps.social.ui.esCalificacion(p)) Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.red_califico, d.texto("titulo")), style = MaterialTheme.typography.labelSmall,
                            color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        (d["nota"] as? JsonPrimitive)?.intOrNull?.let { Estrellas(it, 12.sp) }
                    }
                }
            }
            repeat(3 - fila.size) { Box(Modifier.weight(1f)) }
        }
    }
}

/**
 * «Lo tuyo» de otra persona, en orden: lo que está viendo, su reto, sus destacados, sus
 * artistas en una fila y sus géneros separados en música y en cine, series y libros. De lo largo se ve una parte y
 * «Ver todos» muestra el resto.
 */
@Composable
internal fun GustosDelPerfil(vm: CajonViewModel, p: io.github.fogonapps.cajon.datos.PerfilSocial, propio: Boolean) {
    val f = FogonTema.actual
    val destacados = (p.partes["destacados"] as? JsonObject)?.entries?.mapNotNull { (h, v) -> (v as? JsonObject)?.let { h to it } }.orEmpty()
    val generos = (p.partes["gustos"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
    val artistas = (p.partes["musica"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    val reto = retoDe(p.partes[io.github.fogonapps.cajon.datos.PartesPerfil.RETO] as? JsonObject)
    val insignias = (p.partes[io.github.fogonapps.cajon.datos.PartesPerfil.INSIGNIAS] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
        ?.map { io.github.fogonapps.cajon.datos.Insignias.meta(it).let { m -> io.github.fogonapps.cajon.datos.Insignia(it, m, m) } }.orEmpty()
    val viendo = p.partes[io.github.fogonapps.cajon.datos.PartesPerfil.VIENDO]
    if (destacados.none { it.first == "descubrimiento" } && generos.isEmpty() && artistas.isEmpty() && reto.isEmpty() && insignias.isEmpty() && viendo == null) { NadaAqui(); return }
    fun abrir(o: JsonObject) = vm.abrirObraDePerfil(o.texto("titulo"), o.texto("tipoObra").ifBlank { "pelicula" }, o.texto("portada"), o.texto("fuente"),
        o.texto("anio").toIntOrNull(), o.texto("creador"))
    // Lo que los dos tienen por ver y lo que ven a la vez vive en «Juntos» (Juntos.kt); aquí, solo lo suyo.
    ViendoAhora(vm, viendo)
    val anioReto = (p.partes[io.github.fogonapps.cajon.datos.PartesPerfil.RETO] as? JsonObject)?.texto("anio")?.toIntOrNull() ?: java.time.LocalDate.now().year
    RetoDelPerfil(stringResource(R.string.reto_suyo, anioReto), reto, null)
    // Sus favoritas y la que le marcó van arriba del perfil; aquí, su descubrimiento.
    destacados.firstOrNull { it.first == "descubrimiento" }?.let { (h, o) ->
        DestacadoConPalabras(nombreHuecoRed(h), o.texto("titulo"), o.texto("porQue"), { abrir(o) }) { m ->
            Portada(o.texto("portada"), o.texto("tipoObra").ifBlank { "pelicula" }, o.texto("titulo"), m, esquina = 10.dp, conTitulo = false)
        }
    }
    if (artistas.isNotEmpty()) FilaArtistasPerfil(artistas.map { it.texto("nombre") to it.texto("foto") }) { i ->
        artistas[i].let { a -> vm.abrirArtistaDePerfil(a.texto("nombre"), a.texto("foto"), a.texto("fuente")) }
    }
    if (generos.isNotEmpty()) {
        val (musica, otros) = generos.partition { io.github.fogonapps.cajon.datos.GenerosPerfil.esMusical(it) }
        TituloPerfil(stringResource(R.string.parte_gustos))
        if (musica.isNotEmpty()) FilaGeneros(vm, stringResource(R.string.generos_musica), musica)
        if (otros.isNotEmpty()) FilaGeneros(vm, stringResource(R.string.generos_cine), otros)
    }
    FilaInsignias(insignias, propias = false)
}

/** Las favoritas de otra persona (las que deja ver) y la que le marcó, arriba de su perfil como en el tuyo. */
@Composable
private fun FavoritasDeLaRed(vm: CajonViewModel, p: io.github.fogonapps.cajon.datos.PerfilSocial) {
    val destacados = (p.partes["destacados"] as? JsonObject)?.entries?.mapNotNull { (h, v) -> (v as? JsonObject)?.let { h to it } }.orEmpty().toMap()
    fun abrir(o: JsonObject) = vm.abrirObraDePerfil(o.texto("titulo"), o.texto("tipoObra").ifBlank { "pelicula" }, o.texto("portada"), o.texto("fuente"),
        o.texto("anio").toIntOrNull(), o.texto("creador"))
    // En el orden de los huecos; el artista abre su ficha y va con su foto redonda.
    val favoritas = io.github.fogonapps.cajon.datos.Destacados.FAVORITOS.mapNotNull { h ->
        val o = destacados[h] ?: return@mapNotNull null
        val titulo = o.texto("titulo").ifBlank { o.texto("nombre") }
        if (h == "artista") Favorita(nombreTipo(h, plural = false), titulo, { vm.abrirArtistaDePerfil(titulo, o.texto("foto").ifBlank { o.texto("portada") }, o.texto("fuente")) }) { m ->
            FotoEnMarco(o.texto("foto").ifBlank { o.texto("portada") }, titulo, "", m)
        } else Favorita(nombreTipo(h, plural = false), titulo, { abrir(o) }) { m ->
            Portada(o.texto("portada"), o.texto("tipoObra").ifBlank { "pelicula" }, titulo, m, esquina = 10.dp, conTitulo = false)
        }
    }
    FavoritasDelPerfil(favoritas)
    destacados["marco"]?.let { o ->
        DestacadoConPalabras(nombreHuecoRed("marco"), o.texto("titulo"), o.texto("porQue"), { abrir(o) }) { m ->
            Portada(o.texto("portada"), o.texto("tipoObra").ifBlank { "pelicula" }, o.texto("titulo"), m, esquina = 10.dp, conTitulo = false)
        }
    }
}

/** Los artistas en una fila que se desliza (foto chica y nombre); «Ver todos» los muestra todos en cuadrícula. */
@Composable
internal fun FilaArtistasPerfil(artistas: List<Pair<String, String>>, onClick: ((Int) -> Unit)? = null) {
    val f = FogonTema.actual
    var todos by rememberSaveable { mutableStateOf(false) }
    TituloPerfil(stringResource(R.string.lotuyo_artistas)) {
        if (artistas.size > 6) Text(stringResource(if (todos) R.string.lotuyo_ver_menos else R.string.lotuyo_ver_todos, artistas.size), style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold, color = f.texto, modifier = Modifier.clip(forma(8.dp)).clickable(role = Role.Button) { todos = !todos }.heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 14.dp))
    }
    @Composable
    fun Uno(i: Int, nombre: String, foto: String, modifier: Modifier) {
        Column(modifier.clip(forma(12.dp)).then(if (onClick != null) Modifier.clickable { onClick(i) } else Modifier).padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FotoArtista(foto, nombre, 64.dp)
            Text(nombre, style = MaterialTheme.typography.labelMedium, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
    if (!todos) androidx.compose.foundation.lazy.LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(artistas.size) { i -> Uno(i, artistas[i].first, artistas[i].second, Modifier.width(76.dp)) }
    } else artistas.withIndex().chunked(4).forEach { fila ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            fila.forEach { (i, a) -> Uno(i, a.first, a.second, Modifier.weight(1f)) }
            repeat(4 - fila.size) { Box(Modifier.weight(1f)) }
        }
    }
}

/** Una fila de géneros con su nombre (Música, o Cine, series y libros): se ven unos pocos y el resto con «Ver todos». */
@Composable
private fun FilaGeneros(vm: CajonViewModel, titulo: String, generos: List<String>) {
    val f = FogonTema.actual
    var todos by rememberSaveable(titulo) { mutableStateOf(false) }
    val visibles = if (todos) generos else generos.take(6)
    Text(titulo, style = MaterialTheme.typography.labelLarge, color = f.textoSuave, modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp))
    androidx.compose.foundation.layout.FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        visibles.forEach { g -> Pildora(g.replaceFirstChar { it.uppercase() }) { vm.abrir(Pantalla.EtiquetaRed(g.lowercase().filter { it.isLetterOrDigit() })) } }
        if (generos.size > 6) Pildora(stringResource(if (todos) R.string.lotuyo_ver_menos else R.string.lotuyo_ver_todos, generos.size)) { todos = !todos }
    }
}

@Composable
private fun nombreHuecoRed(h: String): String = stringResource(when (h) {
    "pelicula" -> R.string.dest_pelicula; "serie" -> R.string.dest_serie; "anime" -> R.string.dest_anime
    "album" -> R.string.dest_album; "libro" -> R.string.dest_libro; "juego" -> R.string.dest_juego; "artista" -> R.string.dest_artista
    "marco" -> R.string.dest_marco; else -> R.string.dest_descubrimiento
})

/** Los artistas que más le gustan. */
@Composable
internal fun MusicaDelPerfil(p: io.github.fogonapps.cajon.datos.PerfilSocial, propio: Boolean) {
    val f = FogonTema.actual
    val artistas = (p.partes["musica"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    if (artistas.isEmpty()) { NadaAqui(); return }
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
fun EtiquetaRed(vm: CajonViewModel, etiqueta: String) { io.github.fogonapps.social.ui.EtiquetaRed(interfazSocial(vm), etiqueta) }

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
fun PublicacionRed(vm: CajonViewModel, id: Long) { io.github.fogonapps.social.ui.PublicacionRed(interfazSocial(vm), id) }

/* ----- Avisos, guardadas, gente ----- */

@Composable
fun AvisosRed(vm: CajonViewModel) { io.github.fogonapps.social.ui.AvisosRed(interfazSocial(vm)) }

@Composable
fun GuardadosRed(vm: CajonViewModel) { io.github.fogonapps.social.ui.GuardadosRed(interfazSocial(vm)) }

@Composable
fun GenteRed(vm: CajonViewModel, usuario: String, seguidores: Boolean) { io.github.fogonapps.social.ui.GenteRed(interfazSocial(vm), usuario, seguidores) }

@Composable
fun BloqueadosRed(vm: CajonViewModel) { io.github.fogonapps.social.ui.BloqueadosRed(interfazSocial(vm)) }

/* ----- Publicar ----- */

@Composable
fun Publicar(vm: CajonViewModel, e: Estado, obra: String?) {
    val f = FogonTema.actual
    val acciones = LocalAcciones.current
    val publicando by vm.red.publicando.collectAsStateWithLifecycle()
    var texto by rememberSaveable { mutableStateOf("") }
    var fotos by rememberSaveable { mutableStateOf(listOf<String>()) }
    var video by rememberSaveable { mutableStateOf<String?>(null) }
    val progresoVideo by vm.red.progresoVideo.collectAsStateWithLifecycle()
    val elegirVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && fotos.isEmpty()) video = uri.toString()
    }
    var seguidores by rememberSaveable { mutableStateOf(false) }
    var spoiler by rememberSaveable { mutableStateOf(false) }
    val o = obra?.let { id -> e.vivas.firstOrNull { it.id == id } }
    Cabecera(stringResource(R.string.red_publicar), vm::cerrar)
    BotonSecundario(stringResource(R.string.red_tipo_resena), { vm.escribirResena(obra.orEmpty()) }, Modifier.fillMaxWidth().padding(16.dp))
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (o != null) Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Portada(o.portada, o.tipo, o.titulo, Modifier.width(56.dp), conTitulo = false)
            Text(o.titulo, style = MaterialTheme.typography.titleMedium, color = f.texto)
        }
        OutlinedTextField(texto, { texto = it.take(2000) }, Modifier.fillMaxWidth().height(160.dp), placeholder = { Text(stringResource(R.string.red_que_quieres_decir), color = f.textoSuave) }, shape = forma(20.dp),
            supportingText = { Text(stringResource(R.string.red_etiquetas_pista), color = f.textoSuave) })
        fotos.forEachIndexed { i, foto ->
            coil3.compose.AsyncImage(android.net.Uri.parse(foto), stringResource(R.string.publicacion_foto, i + 1, fotos.size), Modifier.fillMaxWidth().height(160.dp), contentScale = ContentScale.Fit)
            Row(verticalAlignment = Alignment.CenterVertically) {
                BotonIcono(Iconos.Borrar, stringResource(R.string.publicacion_quitar_foto, i + 1)) { fotos = fotos.filterIndexed { n, _ -> n != i } }
                BotonIcono(Iconos.Atras, stringResource(R.string.publicacion_foto_izquierda, i + 1), activo = i > 0) {
                    fotos = fotos.toMutableList().apply { add(i - 1, removeAt(i)) }
                }
                BotonIcono(Iconos.Siguiente, stringResource(R.string.publicacion_foto_derecha, i + 1), activo = i < fotos.lastIndex) {
                    fotos = fotos.toMutableList().apply { add(i + 1, removeAt(i)) }
                }
            }
        }
        if (!publicando && video == null && acciones != null && fotos.size < 10) Row { BotonPildoraBorde(stringResource(R.string.red_agregar_foto), {
            acciones.elegirImagen { uri -> if (fotos.size < 10) fotos = fotos + uri.toString() }
        }, icono = Iconos.Imagen) }
        if (!publicando && fotos.isEmpty() && video == null) Row { BotonPildoraBorde(stringResource(R.string.video_agregar), {
            elegirVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        }, icono = Iconos.Reproducir) }
        if (video != null) {
            Text(stringResource(R.string.video_elegido), color = f.texto)
            val contexto = androidx.compose.ui.platform.LocalContext.current
            val vistaPrevia by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, video) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        android.media.MediaMetadataRetriever().run {
                            try { setDataSource(contexto, android.net.Uri.parse(video)); getFrameAtTime(0) } finally { release() }
                        }
                    }.getOrNull()
                }
            }
            vistaPrevia?.let { b ->
                androidx.compose.foundation.Image(b.asImageBitmap(), null, Modifier.fillMaxWidth().height(180.dp).clip(forma(16.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            }
            Text(stringResource(R.string.video_limites), color = f.textoSuave)
            if (!publicando) BotonSecundario(stringResource(R.string.video_quitar), { video = null })
        }
        Opcion(stringResource(R.string.red_solo_seguidores), seguidores) { seguidores = it }
        Opcion(stringResource(R.string.red_tiene_spoilers), spoiler) { spoiler = it }
        if (publicando) {
            val progreso = progresoVideo
            if (progreso != null && progreso < 1f) {
                Text(stringResource(R.string.video_preparando), color = f.texto)
                LinearProgressIndicator(progress = { progreso }, modifier = Modifier.fillMaxWidth(), color = f.texto, trackColor = f.linea)
            } else {
                Text(stringResource(R.string.video_subiendo), color = f.texto)
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = f.texto, trackColor = f.linea)
            }
            BotonSecundario(stringResource(R.string.video_cancelar), vm.red::cancelarPublicacion)
        }
        BotonPildora(stringResource(R.string.red_publicar), {
            vm.red.publicar(texto, obra, fotos.map(android.net.Uri::parse), if (seguidores) Visibilidad.AMIGOS else Visibilidad.PUBLICA, spoiler, video?.let(android.net.Uri::parse)) { vm.cerrar() }
        }, Modifier.fillMaxWidth(), habilitado = !publicando && (texto.isNotBlank() || fotos.isNotEmpty() || video != null))
    }
}

@Composable
private fun Opcion(texto: String, si: Boolean, onCambio: (Boolean) -> Unit) = FilaInterruptorCajon(texto, si, onCambio = onCambio)

/* ----- La cuenta ----- */

@Composable
fun CuentaRed(vm: CajonViewModel, e: Estado) { io.github.fogonapps.social.ui.CuentaRed(interfazSocial(vm), e.perfil.usuario, e.ajustes.nombre) }

@Composable
internal fun EntrarRed(vm: CajonViewModel, e: Estado, conIntro: Boolean = true) { io.github.fogonapps.social.ui.EntrarRed(interfazSocial(vm), e.perfil.usuario, e.ajustes.nombre, conIntro) }


/* ----- La canción del perfil ----- */


/** La tarjeta de la canción del perfil: portada del álbum, la canción, el artista y ▶. */
@Composable
fun TarjetaCancion(titulo: String, artista: String, album: String, portada: String, modifier: Modifier = Modifier, audio: String = "", enlace: String = "", onCambiar: (() -> Unit)? = null, sonar: Boolean = false,
                   buscar: (suspend (String) -> List<io.github.fogonapps.social.CancionSocial>)? = null) {
    val f = FogonTema.actual
    // La misma fila que en las otras apps, a todo lo ancho (con el avance de Apple y su enlace).
    io.github.fogonapps.social.ui.FilaCancion(titulo, artista, audio, enlace, modifier.padding(horizontal = 16.dp, vertical = 4.dp), sonar, onCambiar, buscar) { m ->
        Portada(portada, "album", album, m, esquina = 8.dp, conTitulo = false, natural = true)
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
            lista == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.texto, trackColor = f.linea)
            lista.isEmpty() -> Pista(stringResource(R.string.sin_resultados), Modifier.padding(horizontal = 16.dp))
            else -> lista.forEach { c ->
                Row(Modifier.fillMaxWidth().clip(forma(14.dp)).clickable { vm.ponerCancion(listOf("v3", c.titulo, c.artista, c.album, c.portada, c.audio, c.enlace).joinToString("|") { it.replace("|", "/") }); vm.cerrar() }
                    .padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Portada(c.portada, "album", c.album, Modifier.width(44.dp), conTitulo = false, natural = true)
                    Column(Modifier.weight(1f)) {
                        Text(c.titulo, style = MaterialTheme.typography.bodyMedium, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOf(c.artista, c.album).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/* ----- Notificaciones y palabras ocultas ----- */

@Composable
fun AjustesAvisos(vm: CajonViewModel) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences(io.github.fogonapps.cajon.AvisosRed.PREFS, android.content.Context.MODE_PRIVATE) }
    var avisos by remember { mutableStateOf(io.github.fogonapps.cajon.AvisosRed.activo(prefs, vm.recursos.id, io.github.fogonapps.cajon.AvisosRed.AVISOS)) }
    var mensajes by remember { mutableStateOf(io.github.fogonapps.cajon.AvisosRed.activo(prefs, vm.recursos.id, io.github.fogonapps.cajon.AvisosRed.MENSAJES)) }
    val permiso = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    fun guardar() {
        prefs.edit().putBoolean("p${vm.recursos.id}.${io.github.fogonapps.cajon.AvisosRed.AVISOS}", avisos).putBoolean("p${vm.recursos.id}.${io.github.fogonapps.cajon.AvisosRed.MENSAJES}", mensajes).apply()
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
    io.github.fogonapps.social.ui.MenuPublicacion(interfazSocial(vm), p, motivoTexto(motivo), onCerrar)
}

/** Selector común dentro de las hojas de reporte. */
@Composable
fun MotivosReporte(onElegir: (String, String) -> Unit) { io.github.fogonapps.social.ui.MotivosReporte(onElegir) }


/** Los motivos de reporte: el código que entiende el servidor y su texto (lo de menores, primero). */
val MOTIVOS_REPORTE = io.github.fogonapps.cajon.datos.MOTIVOS_REPORTE

/* ----- Estado de tu cuenta y moderación ----- */

@Composable
fun EstadoCuenta(vm: CajonViewModel) { io.github.fogonapps.social.ui.EstadoCuenta(interfazSocial(vm)) }

/** Vista de solo lectura del contenido reportado. */
@Composable
internal fun VistaContenidoReporte(vm: CajonViewModel, contenido: String) {
    val f = FogonTema.actual
    val previa = remember(contenido) { io.github.fogonapps.cajon.datos.leerContenidoReporte(contenido) }
    if (previa.video.isNotBlank()) VideoPublicacion(vm, previa.video, previa.poster, previa.duracion)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (previa.portada.isNotBlank()) Portada(previa.portada, previa.tipoObra.ifBlank { "pelicula" }, previa.titulo, Modifier.width(64.dp), conTitulo = false)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (previa.titulo.isNotBlank()) Text(previa.titulo, style = MaterialTheme.typography.titleMedium, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val detalles = listOf(previa.anio, previa.creador).filter { it.isNotBlank() }.joinToString(" · ")
            if (detalles.isNotBlank()) Text(detalles, style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(previa.texto.ifBlank { if (previa.titulo.isBlank()) stringResource(R.string.mod_sin_contenido) else "" }, style = MaterialTheme.typography.bodySmall, color = f.texto, maxLines = 8, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** La cola de revisión: solo para la cuenta que modera (lo decide el servidor). */
@Composable
fun Moderacion(vm: CajonViewModel) { io.github.fogonapps.social.ui.Moderacion(interfazSocial(vm)) }

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
            usuario == null -> BotonPildora(stringResource(R.string.red_crear_cuenta), { vm.abrir(Pantalla.CuentaRed) }, Modifier.fillMaxWidth())
            bloqueado -> {
                Text(stringResource(R.string.nube_bloqueado), style = MaterialTheme.typography.titleSmall, color = f.texto)
                OutlinedTextField(clave, { clave = it.take(200) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.red_clave)) }, singleLine = true, shape = forma(18.dp), visualTransformation = oculto)
                BotonPildora(stringResource(R.string.nube_abrir), { vm.red.desbloquearCajon(clave); clave = "" }, Modifier.fillMaxWidth(), habilitado = clave.length >= 8)
            }
            !conLlave -> {
                Text(stringResource(if (cuenta?.correo.isNullOrBlank()) R.string.nube_falta_correo else R.string.nube_falta_clave), style = MaterialTheme.typography.titleSmall, color = f.texto)
                if (!cuenta?.correo.isNullOrBlank()) {
                    OutlinedTextField(clave, { clave = it.take(200) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.red_clave_nueva)) }, singleLine = true, shape = forma(18.dp),
                        visualTransformation = oculto, supportingText = { Text(stringResource(R.string.red_clave_regla)) })
                    BotonPildora(stringResource(R.string.nube_activar), { vm.red.ponerClave(clave, "") { clave = "" } }, Modifier.fillMaxWidth(), habilitado = clave.length >= 8 && cuenta?.conClave != true)
                    if (cuenta?.conClave == true) {
                        Pista(stringResource(R.string.nube_ya_tienes_clave))
                        BotonPildora(stringResource(R.string.nube_abrir), { vm.red.desbloquearCajon(clave); clave = "" }, Modifier.fillMaxWidth(), habilitado = clave.length >= 8)
                    }
                } else BotonSecundario(stringResource(R.string.red_tu_cuenta), { vm.abrir(Pantalla.CuentaRed) }, Modifier.fillMaxWidth())
            }
            else -> {
                Text(when (sync.estado) {
                    io.github.fogonapps.cajon.EstadoSync.SINCRONIZANDO -> stringResource(R.string.nube_guardando)
                    io.github.fogonapps.cajon.EstadoSync.ERROR -> sync.mensaje ?: stringResource(R.string.nube_sin_conexion)
                    io.github.fogonapps.cajon.EstadoSync.SIN_CONEXION -> stringResource(R.string.nube_sin_conexion)
                    else -> if (sync.ultima > 0) stringResource(R.string.nube_guardado, haceCuanto(sync.ultima)) else stringResource(R.string.nube_listo)
                }, style = MaterialTheme.typography.titleSmall, color = f.texto)
                BotonSecundario(stringResource(R.string.nube_guardar_ya), { vm.sincronizar() }, Modifier.fillMaxWidth())
            }
        }
        Pista(stringResource(R.string.nube_olvido))
    }
}


@Composable
fun FormularioBorrado(vm: CajonViewModel, volver: () -> Unit) { io.github.fogonapps.social.ui.FormularioBorrado(interfazSocial(vm), volver) }

/** Las apps de Fogón Social, para las frases del saludo que modera el equipo. */
@Composable
private fun nombresApps(): Map<String, String> = linkedMapOf(
    "cajon" to stringResource(R.string.app_cajon), "forzudo" to stringResource(R.string.app_forzudo), "cucha" to stringResource(R.string.app_cucha), "alcancia" to stringResource(R.string.app_alcancia),
)

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
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.haloPuntos
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
                Text(io.github.fogonapps.cajon.datos.Resenas.etiqueta(it, o, vm.idioma() == "en"), style = MaterialTheme.typography.labelMedium, color = f.textoSuave)
            }
            if (d.texto("spoiler") != "true" || destapado) CarruselFotos(imagenesPublicacion(d))
            val yo by vm.red.usuario.collectAsStateWithLifecycle()
            if (p.autor.usuario != yo) {
                val estadoUtil = stringResource(if (p.yoUtil) R.string.resena_util_activo else R.string.resena_util_inactivo)
                Text(stringResource(R.string.resena_util, p.utiles), style = MaterialTheme.typography.labelLarge,
                    color = if (p.yoUtil) f.acentoTexto else f.texto,
                    modifier = Modifier.semantics { stateDescription = estadoUtil }
                        .toggleable(value = p.yoUtil, role = Role.Checkbox, onValueChange = {
                            if (yo == null) vm.abrir(Pantalla.CuentaRed) else vm.red.util(p)
                        }).heightIn(min = 48.dp).padding(12.dp))
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
            Row { Pildora(stringResource(R.string.red_explorar), Iconos.Gente) { vm.irA(io.github.fogonapps.cajon.Pestana.PARA_TI) } }
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
    // Arriba: tu foto al principio (abre tu perfil), el título y, a la derecha, avisos y mensajes con lo que tengan sin leer.
    val chats by vm.chat.conversaciones.collectAsStateWithLifecycle()
    val sinLeerChat = remember(chats) { vm.chat.sinLeer() }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!enPestana) BotonIcono(Iconos.Atras, stringResource(R.string.volver)) { vm.cerrar() }
        FotoPerfil(e.perfil.foto, 40.dp, e.perfil.encuadreFoto, descripcion = stringResource(R.string.tu_perfil)) { vm.abrirMiPerfil() }
        Text(stringResource(R.string.red_titulo), style = MaterialTheme.typography.headlineSmall, color = f.texto, modifier = Modifier.weight(1f))
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
    if (usuario == null) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).tarjetaFogon().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.red_paso2_titulo), style = MaterialTheme.typography.titleLarge, color = f.texto)
            Text(stringResource(R.string.red_paso2_texto), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
            BotonRiso(stringResource(R.string.red_crear_cuenta), { vm.abrir(Pantalla.CuentaRed) }, Modifier.fillMaxWidth())
        }
    }
    io.github.fogonapps.social.ui.PestanasFeedSocial(pestana, usuario, { pestana = it }) { vm.abrir(Pantalla.Publicar()) }
    if (pestana == 0 && usuario == null) { Pista(stringResource(R.string.red_feed_sin_cuenta), Modifier.padding(16.dp)); return }
    val ocultas by vm.red.ocultas.collectAsStateWithLifecycle()
    val lista = (if (pestana == 0) feed?.map { it to null } else explorar?.let { remember(it, e) { io.github.fogonapps.cajon.RedSocial.ordenarPorGustos(it, e) } })
        ?.filter { (p, _) -> io.github.fogonapps.cajon.RedSocial.seMuestra(p, e, ocultas) }
    io.github.fogonapps.social.ui.ListaFeedSocial(lista, pestana, { vm.red.masFeed() }, stringResource(R.string.red_al_dia_texto)) { p, motivo -> TarjetaPublicacion(vm, p, motivo = motivo) }
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
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).tarjetaFogon().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.red_sin_clave_crea), style = MaterialTheme.typography.titleMedium, color = f.texto)
        OutlinedTextField(clave, { clave = it.take(200) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.red_clave)) }, singleLine = true, shape = forma(18.dp),
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = { Text(stringResource(R.string.red_clave_regla)) })
        BotonRiso(stringResource(R.string.guardar), { vm.red.ponerClave(clave, "") { clave = "" } }, Modifier.fillMaxWidth(), habilitado = clave.length >= 8)
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
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).tarjetaFogon().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(fr.texto, style = MaterialTheme.typography.bodyLarge, color = f.texto)
                Text("@${fr.de} · ${fr.idioma}", style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pildora(stringResource(R.string.mod_aprobar), acento = true) { vm.red.decidirFrase(fr.clave, true) }
                    Pildora(stringResource(R.string.mod_rechazar)) { vm.red.decidirFrase(fr.clave, false) }
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
    val franjas = listOf("todo" to R.string.franja_todo, "manana" to R.string.franja_manana, "tarde" to R.string.franja_tarde,
        "noche" to R.string.franja_noche, "madrugada" to R.string.franja_madrugada)
    val nombreFranja = franjas.associate { (k, r) -> k to stringResource(r) }
    Seccion(stringResource(R.string.mod_frases_saludo))
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(nueva, { nueva = it.take(90) }, Modifier.fillMaxWidth(), singleLine = true, shape = forma(18.dp), placeholder = { Text(stringResource(R.string.mod_frase_nueva)) })
        // Cuándo sale: todo el día o una franja.
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            franjas.forEach { (k, _) -> Pildora(nombreFranja.getValue(k), acento = franja == k) { franja = k } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pildora("ES", acento = idioma == "es") { idioma = "es" }
            Pildora("EN", acento = idioma == "en") { idioma = "en" }
            Box(Modifier.weight(1f))
            BotonRiso(stringResource(R.string.mod_agregar), { vm.agregarFrase(nueva, idioma, franja); nueva = "" }, habilitado = nueva.trim().length >= 4)
        }
    }
    // Todas, la más votada primero (sí menos no).
    val textos = TEXTOS_FRASES.filterKeys { it !in ocultas }.map { (k, r) -> Triple(k, stringResource(r, "…"), franjaDeFrase(k)) } +
        deLaGente.map { Triple(it.clave, it.texto, it.franja) }
    textos.sortedByDescending { (k, _, _) -> votos[k]?.let { it.si - it.no } ?: 0 }.forEach { (k, texto, fr) ->
        val v = votos[k]
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).tarjetaFogon().padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
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
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).tarjetaFogon().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.red_paso1_titulo), style = MaterialTheme.typography.titleLarge, color = f.texto)
        Text(stringResource(R.string.red_paso1_texto), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
        opciones.avisos.forEach { Pista(stringResource(it)) }
        // Una probadita de lo que se elige: portadas de lo del momento.
        val portadas = (opciones.cine.map { it.portada } + opciones.libros.map { it.portada }).filter { it.isNotBlank() }.take(4)
        if (portadas.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            portadas.forEach { coil3.compose.AsyncImage(it, null, Modifier.weight(1f).aspectRatio(0.68f).clip(forma(12.dp)), contentScale = ContentScale.Crop) }
        }
        BotonRiso(stringResource(R.string.red_paso1_boton), { vm.abrir(Pantalla.ElegirGustos) }, Modifier.fillMaxWidth())
        BotonSecundario(stringResource(R.string.ahora_no), { vm.gustosListos() }, Modifier.fillMaxWidth())
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
    var pestana by rememberSaveable(usuario) { mutableStateOf(0) }
    LaunchedEffect(usuario) { vm.red.verPerfil(usuario) }
    val p = perfiles[usuario]
    val propio = usuario == yo
    val navegacion: @Composable () -> Unit = {
        Cabecera("@$usuario", vm::cerrar, chico = true) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonIcono(Iconos.Compartir, stringResource(R.string.compartir_perfil), grande = true) { compartirTexto(ctx, ClienteSocial.enlacePerfil(usuario)) }
                if (yo != null) Box {
                    BotonIcono(Iconos.Opciones, stringResource(R.string.red_mas_opciones), grande = true) { menu = true }
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
                        Opcion(Iconos.Cerrar, stringResource(R.string.red_bloquear)) { hoja = "bloquear" }
                        Opcion(Iconos.Bandera, stringResource(R.string.red_reportar), peligro = true) { hoja = "reportar" }
                    }
                }
            }
        }
    }

    if (p == null) {
        navegacion()
        if (usuario in noEstan) Pista(stringResource(R.string.red_perfil_no_existe), Modifier.padding(16.dp))
        else LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.tinta1, trackColor = f.linea)
        return
    }
    @Composable
    fun contenidoSeccion(idSeccion: Int) {
        when (idSeccion) {
            0 -> if (p.publicaciones.isEmpty()) NadaAqui() else CuadriculaPublicaciones(vm, p.publicaciones)
            1 -> GustosDelPerfil(vm, p, propio)
            2 -> MusicaDelPerfil(p, propio)
            3 -> p.publicaciones.filter { it.tipo == "lista" }.let { l -> if (l.isEmpty()) NadaAqui() else CuadriculaPublicaciones(vm, l) }
            4 -> if (!p.guardadasVisibles) NadaAqui() else {
                LaunchedEffect(usuario) { vm.red.cargarGuardadasDe(usuario) }
                val g = listas["guardadas:$usuario"]
                if (g == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.tinta1, trackColor = f.linea)
                else if (g.isEmpty()) NadaAqui() else CuadriculaPublicaciones(vm, g)
            }
            6 -> p.resenasDelPerfil().let { lista ->
                if (lista.isEmpty()) NadaAqui() else lista.forEach { TarjetaPublicacion(vm, it) }
            }
            else -> interfazSocial(vm).contenido.CifrasPerfil(p)
        }
    }
    val contenidoApp = interfazSocial(vm).contenido
    val secciones = contenidoApp.SeccionesPerfil(p, propio) { contenidoSeccion(it) }
    val vistaBase = p.aPerfilVista(secciones = secciones, contadores = listOf(
        ContadorPerfil("${p.siguiendo}", stringResource(R.string.red_siguiendo)) { if (!p.cerrada) vm.abrir(Pantalla.GenteRed(usuario, false)) },
        ContadorPerfil("${p.seguidores}", stringResource(R.string.red_seguidores)) { if (!p.cerrada) vm.abrir(Pantalla.GenteRed(usuario, true)) },
        ContadorPerfil(cifraCorta(p.megusta), stringResource(R.string.red_me_gusta_cifra)),
    ))
    val uri = LocalUriHandler.current
    PerfilComun(vistaBase, pestana, { pestana = it },
        navegacion = navegacion,
        portada = { if (p.portada.isNotBlank()) coil3.compose.AsyncImage(p.portada, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop) },
        foto = { Box(Modifier.clip(CircleShape).border(4.dp, f.fondo, CircleShape)) { AvatarRed(p.persona, 96.dp) } },
        bajoNombre = { if (p.meSigue && !propio) Text(stringResource(R.string.red_te_sigue), style = MaterialTheme.typography.bodySmall, color = f.textoSuave) },
        enlace = { u -> Pildora(io.github.fogonapps.cajon.datos.Logica.enlaceCorto(u), Iconos.Enlace) { runCatching { uri.openUri(u) } } },
        acciones = {
            when {
                yo == null -> BotonRiso(stringResource(R.string.red_crear_para_seguir), { vm.abrir(Pantalla.CuentaRed) }, Modifier.weight(1f))
                else -> {
                    when (p.relacion) {
                        "sigo" -> BotonSecundario(stringResource(R.string.red_dejar_de_seguir), { vm.red.dejarDeSeguir(usuario) }, Modifier.weight(1f))
                        "pendiente" -> BotonSecundario(stringResource(R.string.red_pendiente), { vm.red.dejarDeSeguir(usuario) }, Modifier.weight(1f))
                        else -> BotonRiso(stringResource(if (p.meSigue) R.string.red_seguir_tambien else R.string.red_seguir), { vm.red.seguir(usuario) }, Modifier.weight(1f))
                    }
                    BotonSecundario(stringResource(R.string.chat_mensaje), { vm.abrir(Pantalla.Conversacion(usuario)) }, Modifier.weight(1f))
                }
            }
        },
        despuesAcciones = {
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
            (p.partes["cancion"] as? JsonObject)?.let { c -> TarjetaCancion(c.texto("cancion"), c.texto("creador"), c.texto("titulo"), c.texto("portada")) }
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
    valores.entries.chunked(2).forEach { fila ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            fila.forEach { (k, v) ->
                Column(Modifier.weight(1f).vidrio().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(v, fontFamily = f.cifras, fontSize = 26.sp, color = f.texto)
                    Text(nombreCifra(k, anio), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                }
            }
            if (fila.size == 1) Box(Modifier.weight(1f))
        }
    }
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
                Box(Modifier.weight(1f).aspectRatio(0.75f).clip(forma(10.dp)).background(f.superficie)
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
            }
            repeat(3 - fila.size) { Box(Modifier.weight(1f)) }
        }
    }
}

/** Lo que más le gusta: sus destacados, sus cuatro favoritas, los géneros que dijo y lo que ha terminado. */
@Composable
internal fun GustosDelPerfil(vm: CajonViewModel, p: io.github.fogonapps.cajon.datos.PerfilSocial, propio: Boolean) {
    val f = FogonTema.actual
    val destacados = (p.partes["destacados"] as? JsonObject)?.entries?.mapNotNull { (h, v) -> (v as? JsonObject)?.let { h to it } }.orEmpty()
    val favoritas = (p.partes["favoritas"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    val generos = (p.partes["gustos"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
    if (destacados.isEmpty() && favoritas.isEmpty() && generos.isEmpty()) { NadaAqui(); return }
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
        if (!publicando && video == null && acciones != null && fotos.size < 10) Pildora(stringResource(R.string.red_agregar_foto), Iconos.Imagen) {
            acciones.elegirImagen { uri -> if (fotos.size < 10) fotos = fotos + uri.toString() }
        }
        if (!publicando && fotos.isEmpty() && video == null) Pildora(stringResource(R.string.video_agregar), Iconos.Imagen) {
            elegirVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        }
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
                LinearProgressIndicator(progress = { progreso }, modifier = Modifier.fillMaxWidth(), color = f.tinta1, trackColor = f.linea)
            } else {
                Text(stringResource(R.string.video_subiendo), color = f.texto)
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = f.tinta1, trackColor = f.linea)
            }
            BotonSecundario(stringResource(R.string.video_cancelar), vm.red::cancelarPublicacion)
        }
        BotonRiso(stringResource(R.string.red_publicar), {
            vm.red.publicar(texto, obra, fotos.map(android.net.Uri::parse), if (seguidores) Visibilidad.AMIGOS else Visibilidad.PUBLICA, spoiler, video?.let(android.net.Uri::parse)) { vm.cerrar() }
        }, Modifier.fillMaxWidth(), habilitado = !publicando && (texto.isNotBlank() || fotos.isNotEmpty() || video != null))
    }
}

@Composable
private fun Opcion(texto: String, si: Boolean, onCambio: (Boolean) -> Unit) = io.github.fogonapps.identidad.OpcionSocial(texto, si, onCambio)

/* ----- La cuenta ----- */

@Composable
fun CuentaRed(vm: CajonViewModel, e: Estado) { io.github.fogonapps.social.ui.CuentaRed(interfazSocial(vm), e.perfil.usuario, e.ajustes.nombre) }

@Composable
internal fun EntrarRed(vm: CajonViewModel, e: Estado, conIntro: Boolean = true) { io.github.fogonapps.social.ui.EntrarRed(interfazSocial(vm), e.perfil.usuario, e.ajustes.nombre, conIntro) }


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
                    io.github.fogonapps.cajon.EstadoSync.ERROR -> sync.mensaje ?: stringResource(R.string.nube_sin_conexion)
                    io.github.fogonapps.cajon.EstadoSync.SIN_CONEXION -> stringResource(R.string.nube_sin_conexion)
                    else -> if (sync.ultima > 0) stringResource(R.string.nube_guardado, haceCuanto(sync.ultima)) else stringResource(R.string.nube_listo)
                }, style = MaterialTheme.typography.titleSmall, color = f.acentoTexto)
                BotonSecundario(stringResource(R.string.nube_guardar_ya), { vm.sincronizar() }, Modifier.fillMaxWidth())
            }
        }
        Pista(stringResource(R.string.nube_olvido))
    }
}


@Composable
fun FormularioBorrado(vm: CajonViewModel, volver: () -> Unit) { io.github.fogonapps.social.ui.FormularioBorrado(interfazSocial(vm), volver) }

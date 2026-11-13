package io.github.fogonapps.social.ui

import androidx.compose.foundation.background
import io.github.fogonapps.identidad.PistaSocial as Pista
import io.github.fogonapps.identidad.PildoraSocial as Pildora
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.identidad.*
import io.github.fogonapps.social.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Las decisiones posibles al sancionar, en orden de menos a más. */
private val DECISIONES = listOf(
    "advertencia" to R.string.sancion_advertencia, "falta" to R.string.sancion_falta,
    "falta_grave" to R.string.social_mod_grave, "cerrar" to R.string.social_mod_cerrar,
)
internal val CIERRE_DIRECTO = setOf("menor_en_riesgo", "menor_de_edad", "evadir_sancion", "amenaza_real")

private fun fechaCorta(t: Long): String = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(t))

/**
 * Elegir cómo sancionar: advertencia (sin puntos), falta, falta grave o cierre; la norma y un mensaje con el motivo,
 * que le llega a la persona en la app y por correo. Solo se ofrecen las decisiones que permite el rol.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun HojaSancionar(permisos: Set<String>, reglaInicial: String, faltas: Int, titulo: String, pedirMensaje: Boolean,
                           onCerrar: () -> Unit, decisionInicial: String = "advertencia", onListo: (decision: String, regla: String, mensaje: String) -> Unit) {
    val f = FogonTema.actual
    var regla by remember { mutableStateOf(reglaInicial.takeIf { r -> MOTIVOS_REPORTE.any { it.first == r } || r in CIERRE_DIRECTO } ?: "otro") }
    val permitidas = accionesModeracion(permisos, regla, faltas).let { if ("sancionar" in permisos && regla !in CIERRE_DIRECTO) it + "advertencia" else it }
    var decision by remember { mutableStateOf(decisionInicial) }
    if (decision !in permitidas) decision = DECISIONES.firstOrNull { it.first in permitidas }?.first ?: ""
    var mensaje by remember { mutableStateOf("") }
    ModalBottomSheet(onCerrar, containerColor = f.fondo, contentColor = f.texto) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, color = f.texto)
            Text(stringResource(R.string.sancion_que_hacer), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DECISIONES.filter { it.first in permitidas }.forEach { (valor, texto) -> Pildora(stringResource(texto), acento = decision == valor) { decision = valor } }
            }
            Pista(stringResource(when (decision) {
                "advertencia" -> R.string.sancion_advertencia_explica
                "falta_grave" -> R.string.sancion_grave_explica
                "cerrar" -> R.string.sancion_cerrar_explica
                else -> R.string.sancion_falta_explica
            }))
            Text(stringResource(R.string.sancion_norma), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (MOTIVOS_REPORTE + listOf("evadir_sancion" to R.string.social_mod_evadir_sancion)).forEach { (valor, texto) ->
                    Pildora(stringResource(texto), acento = regla == valor) { regla = valor }
                }
            }
            OutlinedTextField(mensaje, { mensaje = it.take(500) }, Modifier.fillMaxWidth(), minLines = 3,
                label = { Text(stringResource(if (pedirMensaje) R.string.sancion_mensaje else R.string.sancion_mensaje_opcional)) },
                supportingText = { Text(stringResource(R.string.sancion_mensaje_pista)) }, shape = forma(18.dp))
            BotonRiso(stringResource(R.string.sancion_aplicar), { onListo(decision, regla, mensaje.trim()); onCerrar() }, Modifier.fillMaxWidth(),
                habilitado = decision.isNotBlank() && (!pedirMensaje || mensaje.isNotBlank()))
        }
    }
}

/** Lo reportado con sus fotos: el texto lo pinta cada app; aquí van las imágenes, el video y, en una cuenta, su foto y portada. */
@Composable
internal fun ImagenesReporte(c: CasoModeracion, vm: InterfazSocial? = null) {
    val datos = remember(c.contenido) { runCatching { JsonSocial.parseToJsonElement(c.contenido).jsonObject }.getOrNull() }
    when (c.que) {
        // Una historia: su foto (o el video, que se reproduce al tocarlo), su texto y su pregunta.
        "historia" -> {
            val f = FogonTema.actual
            fun t(k: String) = (datos?.get(k) as? JsonPrimitive)?.content.orEmpty()
            val imagen = t("imagen"); val video = t("video")
            var viendo by remember { mutableStateOf(false) }
            if (imagen.isNotBlank()) coil3.compose.AsyncImage(io.github.fogonapps.social.ClienteSocial.imagen(imagen), null,
                Modifier.fillMaxWidth(0.6f).aspectRatio(9f / 16f).clip(forma(14.dp)).then(Modifier.background(f.superficie)), contentScale = ContentScale.Fit)
            if (video.isNotBlank() && vm != null) {
                if (viendo) VideoHistoria(vm::cabeceras, video, false, Modifier.fillMaxWidth(0.6f).aspectRatio(9f / 16f).clip(forma(14.dp)))
                else Pildora(stringResource(R.string.social_mod_ver_video), IconosMultimedia.Reproducir) { viendo = true }
            }
            val compuesta = (datos?.get("compuesta") as? JsonPrimitive)?.content == "true"
            listOf(if (compuesta && imagen.isNotBlank()) "" else t("texto"), t("pregunta")).filter { it.isNotBlank() }.forEach { Text(it.take(600), style = MaterialTheme.typography.bodyMedium, color = f.texto) }
            if (datos == null || (imagen.isBlank() && video.isBlank() && t("texto").isBlank() && t("pregunta").isBlank()))
                Text(stringResource(R.string.social_mod_historia_sin_contenido), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        }
        "publicacion" -> {
            val d = (datos?.get("datos") as? JsonObject) ?: datos ?: return
            val imagenes = imagenesPublicacion(d)
            if (imagenes.isNotEmpty()) CarruselFotos(imagenes)
        }
        "cuenta" -> {
            val portada = (datos?.get("portada") as? JsonPrimitive)?.content.orEmpty()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                c.autor?.let { AvatarRed(it, 72.dp) }
                if (portada.startsWith("https://")) coil3.compose.AsyncImage(portada, null,
                    Modifier.height(72.dp).weight(1f).clip(forma(14.dp)), contentScale = ContentScale.Crop)
            }
            val bio = (datos?.get("bio") as? JsonPrimitive)?.content.orEmpty()
            if (bio.isNotBlank()) Text(bio.take(600), style = MaterialTheme.typography.bodyMedium, color = FogonTema.actual.texto)
        }
    }
}

/** Advertir o sancionar una cuenta directo y ver (o levantar) las sanciones de los últimos 90 días. */
@Composable
internal fun SancionesEquipo(vm: InterfazSocial, permisos: Set<String>) {
    val f = FogonTema.actual
    val lista by vm.red.sanciones.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.red.cargarSanciones() }
    var usuario by rememberSaveable { mutableStateOf("") }
    var hoja by remember { mutableStateOf(false) }
    var levantar by remember { mutableStateOf<String?>(null) }
    if (hoja) HojaSancionar(permisos, "otro", 0, stringResource(R.string.sancion_a, usuario), pedirMensaje = true, { hoja = false }) { decision, regla, mensaje ->
        vm.confirmar(vm.tx(R.string.sancion_aplicar), vm.tx(R.string.sancion_confirmar)) { vm.red.sancionar(usuario, decision, regla, mensaje); usuario = "" }
    }
    levantar?.let { u -> PedirTexto(stringResource(R.string.sancion_levantar_titulo, u), stringResource(R.string.sancion_levantar_motivo), { levantar = null }) { vm.red.levantar(u, it) } }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Pista(stringResource(R.string.sancion_explica))
        OutlinedTextField(usuario, { usuario = it.removePrefix("@").trim().take(40) }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.social_equipo_usuario)) }, shape = forma(18.dp))
        BotonRiso(stringResource(R.string.sancion_advertir_cuenta), { hoja = true }, Modifier.fillMaxWidth(), habilitado = usuario.length >= 3)
        Text(stringResource(R.string.sancion_recientes), style = MaterialTheme.typography.titleSmall, color = f.texto, modifier = Modifier.padding(top = 8.dp))
        val l = lista
        when {
            l == null -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = f.tinta1, trackColor = f.linea)
            l.isEmpty() -> Pista(stringResource(R.string.sancion_ninguna))
            else -> l.forEach { s ->
                val ahora = System.currentTimeMillis()
                Column(Modifier.fillMaxWidth().tarjetaFogon().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("@${s.usuario}" + s.nombre.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(), style = MaterialTheme.typography.titleSmall, color = f.texto)
                    Text(textoFalta(FaltaSocial(s.regla, when (s.decision) { "advertencia" -> 0; "falta_grave" -> 2; "cerrar" -> 99; else -> 1 }, s.cuando, 0, decision = s.decision)) { vm.tx(it) } + " · " + fechaCorta(s.cuando),
                        style = MaterialTheme.typography.bodySmall, color = f.acentoTexto)
                    if (s.mensaje.isNotBlank()) Text("«${s.mensaje}»", style = MaterialTheme.typography.bodyMedium, color = f.texto)
                    val estado = when {
                        s.cerrada -> stringResource(R.string.sancion_estado_cerrada)
                        s.suspendidaHasta > ahora -> stringResource(R.string.sancion_estado_suspendida, fechaCorta(s.suspendidaHasta))
                        s.restringidaHasta > ahora -> stringResource(R.string.sancion_estado_restringida, fechaCorta(s.restringidaHasta))
                        else -> stringResource(R.string.social_mod_faltas, s.puntos)
                    }
                    Text(estado, style = MaterialTheme.typography.bodySmall, color = if (s.cerrada || s.suspendidaHasta > ahora) MaterialTheme.colorScheme.error else f.textoSuave)
                    if ("gestionar_usuarios" in permisos && (s.puntos > 0 || s.cerrada || s.suspendidaHasta > ahora || s.restringidaHasta > ahora))
                        Pildora(stringResource(R.string.sancion_levantar)) { levantar = s.usuario }
                }
            }
        }
    }
}

/** Las apelaciones abiertas: qué dice la persona, sus faltas y la respuesta (aceptar quita la última sanción). */
@Composable
internal fun ApelacionesEquipo(vm: InterfazSocial, permisos: Set<String>) {
    val f = FogonTema.actual
    val lista by vm.red.apelaciones.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.red.cargarApelaciones() }
    var decidiendo by remember { mutableStateOf<Pair<ApelacionSocial, Boolean>?>(null) }
    decidiendo?.let { (a, aceptar) ->
        PedirTexto(stringResource(if (aceptar) R.string.apelacion_aceptar else R.string.apelacion_rechazar), stringResource(R.string.apelacion_respuesta), { decidiendo = null }) {
            vm.red.decidirApelacion(a, aceptar, it)
        }
    }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Pista(stringResource(R.string.apelacion_explica_equipo))
        val l = lista
        when {
            l == null -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = f.tinta1, trackColor = f.linea)
            l.isEmpty() -> Pista(stringResource(R.string.apelacion_ninguna))
            else -> l.forEach { a ->
                Column(Modifier.fillMaxWidth().tarjetaFogon().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("@${a.usuario} · ${fechaCorta(a.cuando)}", style = MaterialTheme.typography.titleSmall, color = f.texto)
                    Text(a.texto, style = MaterialTheme.typography.bodyMedium, color = f.texto)
                    a.faltas.take(5).forEach { x ->
                        Text("• " + textoFalta(x) { vm.tx(it) } + x.mensaje.takeIf { it.isNotBlank() }?.let { " — «$it»" }.orEmpty(),
                            style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if ("gestionar_usuarios" in permisos) Pildora(stringResource(R.string.apelacion_aceptar), acento = true) { decidiendo = a to true }
                        Pildora(stringResource(R.string.apelacion_rechazar)) { decidiendo = a to false }
                    }
                }
            }
        }
    }
}

/** Pedir un texto corto (motivo o respuesta) en una ventana. */
@Composable
internal fun PedirTexto(titulo: String, etiqueta: String, onCerrar: () -> Unit, onListo: (String) -> Unit) {
    val f = FogonTema.actual
    var texto by remember { mutableStateOf("") }
    AlertDialog(onCerrar, containerColor = f.fondo,
        title = { Text(titulo, color = f.texto) },
        text = { OutlinedTextField(texto, { texto = it.take(500) }, Modifier.fillMaxWidth(), minLines = 3, label = { Text(etiqueta) }, shape = forma(18.dp)) },
        confirmButton = { TextButton({ onListo(texto.trim()); onCerrar() }, enabled = texto.isNotBlank()) { Text(stringResource(R.string.sancion_listo), color = f.acentoTexto) } },
        dismissButton = { TextButton(onCerrar) { Text(stringResource(R.string.sancion_cancelar), color = f.textoSuave) } })
}

/** Apelar la última sanción (o ver cómo va la apelación). Sirve en Estado de la cuenta y en la pantalla de sanción. */
@Composable
internal fun BloqueApelar(estado: EstadoCuentaSocial, apelacion: ApelacionSocial?, ocupado: Boolean = false, onApelar: (String) -> Unit) {
    val f = FogonTema.actual
    val ahora = System.currentTimeMillis()
    if (apelacion != null && (estado.faltas.isEmpty() || apelacion.cuando >= (estado.faltas.maxOfOrNull { it.cuando } ?: 0) || apelacion.estado != "abierta")) {
        Column(Modifier.fillMaxWidth().tarjetaFogon().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(when (apelacion.estado) {
                "aceptada" -> R.string.apelacion_estado_aceptada
                "rechazada" -> R.string.apelacion_estado_rechazada
                else -> R.string.apelacion_estado_abierta
            }), style = MaterialTheme.typography.titleSmall, color = f.texto)
            Text("«${apelacion.texto}»", style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            if (apelacion.respuesta.isNotBlank()) Text(stringResource(R.string.apelacion_respuesta_equipo, apelacion.respuesta), style = MaterialTheme.typography.bodyMedium, color = f.texto)
        }
    }
    if (!estado.puedeApelar(ahora)) return
    var abierto by rememberSaveable { mutableStateOf(false) }
    var texto by rememberSaveable { mutableStateOf("") }
    if (!abierto) { BotonSecundario(stringResource(R.string.apelacion_apelar), { abierto = true }, Modifier.fillMaxWidth()); return }
    Pista(stringResource(R.string.apelacion_explica))
    OutlinedTextField(texto, { texto = it.take(1000) }, Modifier.fillMaxWidth(), minLines = 4,
        label = { Text(stringResource(R.string.apelacion_tu_texto)) }, shape = forma(18.dp))
    BotonRiso(stringResource(R.string.apelacion_enviar), { onApelar(texto.trim()); abierto = false; texto = "" }, Modifier.fillMaxWidth(),
        habilitado = !ocupado && texto.trim().length >= 10)
}

/** Las faltas con su motivo y el mensaje del equipo. */
@Composable
internal fun ListaFaltas(faltas: List<FaltaSocial>, traducir: (Int) -> String) {
    val f = FogonTema.actual
    faltas.forEach { x ->
        Column(Modifier.fillMaxWidth().tarjetaFogon().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(textoFalta(x, traducir), style = MaterialTheme.typography.titleSmall, color = f.texto)
            if (x.mensaje.isNotBlank()) Text(stringResource(R.string.sancion_mensaje_equipo, x.mensaje), style = MaterialTheme.typography.bodyMedium, color = f.texto)
            Text(stringResource(R.string.social_mod_falta_vence, fechaCorta(x.cuando), fechaCorta(x.vence)), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        }
    }
}

/**
 * Lo que ve quien entra con una cuenta cerrada: el motivo, el mensaje del equipo, la opción de apelar y la de entrar
 * con otra cuenta (como Twitch o Instagram).
 */
@Composable
internal fun PantallaSancion(vm: InterfazSocial, s: ControladorCuentaSocial.Sancion) {
    val f = FogonTema.actual
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.sancion_pantalla_titulo), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
        Text(s.correo, style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        val e = s.estado
        if (e == null) Pista(stringResource(R.string.sancion_pantalla_sin_detalle))
        else {
            Pista(stringResource(R.string.sancion_pantalla_explica))
            ListaFaltas(e.estado.faltas.take(3)) { vm.tx(it) }
            BloqueApelar(e.estado, e.apelacion ?: e.estado.apelacion) { vm.red.apelarSancion(it) }
        }
        androidx.compose.material3.TextButton({ uri.openUri(ClienteSocial.SERVIDOR + "/terminos#normas") }) { Text(stringResource(R.string.sancion_ver_normas), color = f.acentoTexto) }
        BotonSecundario(stringResource(R.string.sancion_otra_cuenta), { vm.red.cerrarSancion() }, Modifier.fillMaxWidth())
    }
}

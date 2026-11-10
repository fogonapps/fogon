package io.github.fogonapps.social.ui
import io.github.fogonapps.identidad.IconosSociales as Iconos
import io.github.fogonapps.identidad.SeccionSocial as Seccion
import io.github.fogonapps.identidad.PistaSocial as Pista
import io.github.fogonapps.identidad.FilaVidrioSocial as FilaVidrio
import io.github.fogonapps.identidad.CampoTextoSocial as CampoTexto
import io.github.fogonapps.identidad.PildoraSocial as Pildora
import io.github.fogonapps.identidad.FilaMenuSocial as FilaMenu
import io.github.fogonapps.identidad.ChipsSocial as Chips
import io.github.fogonapps.identidad.BotonIconoSocial as BotonIcono

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

import io.github.fogonapps.social.*
import io.github.fogonapps.identidad.*

private fun JsonObject.texto(clave: String) = (this[clave] as? JsonPrimitive)?.content.orEmpty()

@Composable
fun AvatarRed(p: PersonaSocial, tamano: Dp = 44.dp) {
    val f = FogonTema.actual
    Box(Modifier.size(tamano).clip(CircleShape).background(f.superficie).border(f.borde, f.linea, CircleShape), contentAlignment = Alignment.Center) {
        Text((p.nombre.ifBlank { p.usuario }).take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = f.acentoTexto,
            fontSize = androidx.compose.ui.unit.TextUnit(maxOf(16f, tamano.value * 0.4f), androidx.compose.ui.unit.TextUnitType.Sp))
        if (p.foto) coil3.compose.AsyncImage(ClienteSocial.foto(p.usuario), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
fun FilaPersona(p: PersonaSocial, onClick: () -> Unit, modifier: Modifier = Modifier, derecha: @Composable () -> Unit = {}) {
    val f = FogonTema.actual
    Row(modifier.fillMaxWidth().clip(forma(18.dp)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AvatarRed(p)
        Column(Modifier.weight(1f)) {
            Text(p.nombre.ifBlank { p.usuario }, style = MaterialTheme.typography.titleSmall, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("@" + p.usuario + if (p.privada) " · " + stringResource(R.string.social_red_privada_corto) else "", style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1)
        }
        derecha()
    }
}

@Composable
internal fun haceCuanto(cuando: Long): String {
    val min = ((System.currentTimeMillis() - cuando) / 60_000).coerceAtLeast(0)
    return when {
        min < 1 -> stringResource(R.string.social_red_ahora)
        min < 60 -> stringResource(R.string.social_red_hace_min, min.toInt())
        min < 60 * 24 -> stringResource(R.string.social_red_hace_horas, (min / 60).toInt())
        min < 60 * 24 * 7 -> stringResource(R.string.social_red_hace_dias, (min / 60 / 24).toInt())
        else -> java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(cuando))
    }
}

@Composable
internal fun TextoConEtiquetas(vm: InterfazSocial, texto: String, maxLineas: Int) {
    val f = FogonTema.actual
    val anotado = androidx.compose.ui.text.buildAnnotatedString {
        var desde = 0
        Regex("#([\\p{L}\\p{N}_]{2,40})").findAll(texto).forEach { m ->
            append(texto.substring(desde, m.range.first))
            val etiqueta = m.groupValues[1].lowercase()
            withLink(androidx.compose.ui.text.LinkAnnotation.Clickable(etiqueta,
                androidx.compose.ui.text.TextLinkStyles(androidx.compose.ui.text.SpanStyle(color = f.acentoTexto, fontWeight = FontWeight.SemiBold))) {
                vm.navegacion.etiqueta(etiqueta)
            }) { append(m.value) }
            desde = m.range.last + 1
        }
        append(texto.substring(desde))
    }
    Text(anotado, style = MaterialTheme.typography.bodyMedium, color = f.texto, maxLines = maxLineas, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun cifraCorta(n: Int): String = when {
    n >= 1_000_000 -> stringResource(R.string.social_red_millones, "%.1f".format(n / 1_000_000.0))
    n >= 10_000 -> stringResource(R.string.social_red_miles, "${n / 1000}")
    n >= 1_000 -> stringResource(R.string.social_red_miles, "%.1f".format(n / 1000.0))
    else -> "$n"
}

@Composable
fun PublicacionRed(vm: InterfazSocial, id: Long) {
    val confirmar = vm.confirmar
    val f = FogonTema.actual
    val vistas by vm.red.vistas.collectAsStateWithLifecycle()
    val todos by vm.red.comentarios.collectAsStateWithLifecycle()
    val yo by vm.red.usuario.collectAsStateWithLifecycle()
    var texto by rememberSaveable { mutableStateOf("") }
    var respondiendo by remember { mutableStateOf<ComentarioSocial?>(null) }
    var abiertos by rememberSaveable(id) { mutableStateOf(listOf<Long>()) }
    var comentarioReportado by rememberSaveable(id) { mutableStateOf<Long?>(null) }
    comentarioReportado?.let { comentario ->
        HojaReporte({ comentarioReportado = null }) { codigo, motivo ->
            vm.red.reportar("comentario", "$comentario", "$codigo: $motivo")
            comentarioReportado = null
        }
    }
    LaunchedEffect(id) { vm.red.cargarPublicacion(id) }
    Cabecera(stringResource(R.string.social_red_publicacion), vm.navegacion.volver)
    val p = vistas[id] ?: vm.red.buscarPublicacion(id)
    if (p == null) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.tinta1, trackColor = f.linea); return }
    TarjetaPublicacion(vm, p, abrir = false)
    if (p.autor.usuario == yo) {
        val clave = p.datos.texto("clave").ifBlank { p.datos.texto("propia").takeIf { it.isNotBlank() }?.let { "f:$it" }.orEmpty() }
        if (clave.isNotBlank()) BotonSecundario(stringResource(if (clave.startsWith("f:")) R.string.social_red_borrar_publicacion else R.string.social_red_quitar_de_la_red),
            { confirmar(vm.tx(if (clave.startsWith("f:")) R.string.social_red_borrar_publicacion else R.string.social_red_quitar_de_la_red), vm.tx(R.string.social_red_borrar_publicacion_confirmar)) { if (clave.startsWith("f:")) vm.borrarPublicacion(p.id, clave) else vm.quitarDeLaRed(clave); vm.navegacion.volver() } }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), peligro = true)
    }
    Seccion(stringResource(R.string.social_red_comentarios))
    val palabras = vm.palabrasOcultas()
    val arbol = arbolComentarios(todos[id].orEmpty().filter { vm.mostrarComentario(palabras, it.texto) }, { it.id }, { it.padre }, { it.cuando })
    arbol.forEach { (raiz, respuestas) ->
        val filas = listOf(raiz) + if (raiz.id in abiertos) respuestas else emptyList()
        filas.forEach { c ->
            Column(Modifier.fillMaxWidth().padding(start = if (c.id == raiz.id) 16.dp else 40.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)) {
                if (c.borrado) Text(stringResource(R.string.social_publicacion_comentario_borrado), color = f.textoSuave)
                else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.clickable { vm.navegacion.perfil(c.autor.usuario) }) { AvatarRed(c.autor, 32.dp) }
                        Text("@${c.autor.usuario} · ${haceCuanto(c.cuando)}", color = f.textoSuave, style = MaterialTheme.typography.labelMedium)
                    }
                    Text(c.texto, color = f.texto, style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (yo != null) Pildora(stringResource(R.string.social_publicacion_responder)) { respondiendo = c }
                        BotonIcono(Iconos.Corazon, stringResource(R.string.social_publicacion_megusta_estado, c.megusta,
                            stringResource(if (c.yoMeGusta) R.string.social_publicacion_gusta_si else R.string.social_publicacion_gusta_no)),
                            tinta = if (c.yoMeGusta) f.acentoTexto else f.textoSuave, lleno = c.yoMeGusta) {
                            if (yo == null) vm.navegacion.cuenta() else vm.red.meGustaComentario(id, c)
                        }
                        val nombreMeGusta = stringResource(R.string.social_publicacion_megusta, c.megusta)
                        Text("${c.megusta}", color = f.texto, modifier = Modifier.semantics { contentDescription = nombreMeGusta })
                        if (c.puedoBorrar) BotonIcono(Iconos.Borrar, stringResource(R.string.social_red_borrar_comentario), tinta = f.textoSuave) {
                            confirmar(vm.tx(R.string.social_red_borrar_comentario), vm.tx(R.string.social_red_borrar_comentario_confirmar)) { vm.red.borrarComentario(id, c.id) }
                        }
                        if (yo != null && c.autor.usuario != yo) BotonIcono(Iconos.Bandera, stringResource(R.string.social_red_reportar), tinta = f.textoSuave) { comentarioReportado = c.id }
                    }
                }
            }
        }
        if (respuestas.isNotEmpty()) Box(Modifier.padding(start = 56.dp, bottom = 4.dp)) {
            Pildora(if (raiz.id in abiertos) stringResource(R.string.social_publicacion_ocultar_respuestas)
                else pluralStringResource(R.plurals.social_publicacion_ver_respuestas, respuestas.size, respuestas.size)) {
                abiertos = if (raiz.id in abiertos) abiertos - raiz.id else abiertos + raiz.id
            }
        }
    }
    respondiendo?.let { c ->
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.social_publicacion_respondiendo, c.autor.usuario), color = f.texto, modifier = Modifier.weight(1f))
            BotonIcono(Iconos.Cerrar, stringResource(R.string.social_publicacion_cancelar_respuesta)) { respondiendo = null }
        }
    }
    if (yo == null) { InvitaCuenta(vm); return }
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(texto, { texto = it.take(1000) }, Modifier.weight(1f), placeholder = { Text(stringResource(R.string.social_red_escribe_comentario), color = f.textoSuave) }, shape = forma(22.dp), maxLines = 4)
        BotonIcono(Iconos.Enviar, stringResource(R.string.social_red_enviar), activo = texto.isNotBlank(), lleno = true) { val destino = respondiendo
            val mensaje = if (destino?.padre != null) "@${destino.autor.usuario} $texto" else texto
            vm.red.comentar(id, mensaje, destino?.id); texto = ""; respondiendo = null }
    }
}

@Composable
fun AvisosRed(vm: InterfazSocial) {
    val f = FogonTema.actual
    val avisos by vm.red.avisos.collectAsStateWithLifecycle()
    val solicitudes by vm.red.solicitudes.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.red.cargarAvisos() }
    var borrarTodos by remember { mutableStateOf(false) }
    Cabecera(stringResource(R.string.social_red_avisos), vm.navegacion.volver) {
        if (!avisos.isNullOrEmpty()) BotonIcono(Iconos.Borrar, stringResource(R.string.social_red_borrar_avisos)) { borrarTodos = !borrarTodos }
    }
    if (borrarTodos) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pista(stringResource(R.string.social_red_borrar_avisos_seguro), Modifier.weight(1f))
        Pildora(stringResource(R.string.social_red_si_borrar_avisos), acento = true) { vm.red.borrarAviso(null); borrarTodos = false }
    }
    if (solicitudes.isNotEmpty()) {
        Seccion(stringResource(R.string.social_red_solicitudes))
        solicitudes.forEach { s ->
            FilaPersona(s, { vm.navegacion.perfil(s.usuario) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pildora(stringResource(R.string.social_red_aceptar), acento = true) { vm.red.aceptar(s.usuario) }
                    Pildora(stringResource(R.string.social_red_rechazar)) { vm.red.rechazar(s.usuario) }
                }
            }
        }
        Seccion(stringResource(R.string.social_red_lo_ultimo))
    }
    val lista = avisos
    when {
        lista == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.tinta1, trackColor = f.linea)
        lista.isEmpty() -> Pista(stringResource(R.string.social_red_sin_avisos), Modifier.padding(16.dp))
        else -> lista.groupBy { a ->
            val h = (System.currentTimeMillis() - a.cuando) / 3600_000
            when { h < 24 -> R.string.social_red_hoy; h < 24 * 7 -> R.string.social_red_ultimos_7; else -> R.string.social_red_antes }
        }.forEach { (grupo, avisosDelGrupo) ->
          Seccion(stringResource(grupo))
          avisosDelGrupo.forEach { a ->
            val que = stringResource(when (a.tipo) {
                "respuesta" -> R.string.social_publicacion_aviso_respuesta; "megusta" -> R.string.social_red_aviso_megusta; "comentario" -> R.string.social_red_aviso_comentario; "seguir" -> R.string.social_red_aviso_seguir
                "solicitud" -> R.string.social_red_aviso_solicitud; "aceptada" -> R.string.social_red_aviso_aceptada; "publico" -> R.string.social_red_aviso_publico; "sancion" -> R.string.social_red_aviso_sancion; else -> R.string.social_red_aviso_otro
            })
            FilaPersona(a.de, {
                val pub = a.objeto.toLongOrNull()
                if (a.tipo == "sancion") vm.navegacion.ajustes(AjusteSocial.ESTADO_CUENTA)
                else if (pub != null && a.tipo in setOf("megusta", "comentario", "respuesta", "publico")) vm.navegacion.publicacion(pub) else vm.navegacion.perfil(a.de.usuario)
            }) {
                Text("$que · ${haceCuanto(a.cuando)}", style = MaterialTheme.typography.bodySmall, color = if (a.leido) f.textoSuave else f.acentoTexto, maxLines = 2, modifier = Modifier.width(130.dp), textAlign = TextAlign.End)
                BotonIcono(Iconos.Cerrar, stringResource(R.string.social_red_borrar_aviso), tinta = f.textoSuave) { vm.red.borrarAviso(a.id) }
            }
          }
        }
    }
}

@Composable
fun GuardadosRed(vm: InterfazSocial) {
    val f = FogonTema.actual
    val lista by vm.red.guardados.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.red.cargarGuardados() }
    Cabecera(stringResource(R.string.social_red_guardadas), vm.navegacion.volver)
    val l = lista
    when {
        l == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.tinta1, trackColor = f.linea)
        l.isEmpty() -> Pista(stringResource(R.string.social_red_sin_guardadas), Modifier.padding(16.dp))
        else -> l.forEach { TarjetaPublicacion(vm, it) }
    }
}

@Composable
fun GenteRed(vm: InterfazSocial, usuario: String, seguidores: Boolean) {
    val f = FogonTema.actual
    val gente by vm.red.gente.collectAsStateWithLifecycle()
    val yo by vm.red.usuario.collectAsStateWithLifecycle()
    LaunchedEffect(usuario, seguidores) { vm.red.cargarGente(usuario, seguidores) }
    Cabecera(stringResource(if (seguidores) R.string.social_red_tus_seguidores_titulo else R.string.social_red_pestana_siguiendo), vm.navegacion.volver)
    val l = gente[(if (seguidores) "seguidores:" else "siguiendo:") + usuario]
    when {
        l == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.tinta1, trackColor = f.linea)
        l.isEmpty() -> Pista(stringResource(R.string.social_red_nadie), Modifier.padding(16.dp))
        else -> l.forEach { p ->
            FilaPersona(p, { vm.navegacion.perfil(p.usuario) }) {
                if (seguidores && usuario == yo) Pildora(stringResource(R.string.social_red_quitar)) { vm.red.quitarSeguidor(p.usuario) }
            }
        }
    }
}

@Composable
fun BloqueadosRed(vm: InterfazSocial) {
    val f = FogonTema.actual
    val gente by vm.red.gente.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.red.cargarBloqueados() }
    Cabecera(stringResource(R.string.social_red_bloqueadas), vm.navegacion.volver)
    val l = gente["bloqueados"]
    when {
        l == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.tinta1, trackColor = f.linea)
        l.isEmpty() -> Pista(stringResource(R.string.social_red_nadie_bloqueado), Modifier.padding(16.dp))
        else -> l.forEach { p -> FilaPersona(p, {}) { Pildora(stringResource(R.string.social_red_desbloquear)) { vm.red.bloquear(p.usuario, false) } } }
    }
}

@Composable
fun EtiquetaRed(vm: InterfazSocial, etiqueta: String) {
    val f = FogonTema.actual
    val listas by vm.red.listas.collectAsStateWithLifecycle()
    LaunchedEffect(etiqueta) { vm.red.cargarEtiqueta(etiqueta) }
    Cabecera("#$etiqueta", vm.navegacion.volver)
    val l = listas["#$etiqueta"]
    when {
        l == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.tinta1, trackColor = f.linea)
        l.isEmpty() -> Pista(stringResource(R.string.social_red_etiqueta_vacia), Modifier.padding(16.dp))
        else -> l.forEach { TarjetaPublicacion(vm, it) }
    }
}

@Composable
fun CuentaRed(vm: InterfazSocial, usuarioInicial: String, nombreInicial: String) {
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.red.revisarServidor() }
    Cabecera(stringResource(R.string.social_red_tu_cuenta), vm.navegacion.volver)
    val u = usuario
    if (u == null) EntrarRed(vm, usuarioInicial, nombreInicial) else ConfigCuenta(vm, u)
}

@Composable
fun EntrarRed(vm: InterfazSocial, usuarioInicial: String, nombreInicial: String, conIntro: Boolean = true) {
    val f = FogonTema.actual
    val correo by vm.correoDisponible.collectAsStateWithLifecycle()
    val acceso by vm.red.acceso.collectAsStateWithLifecycle()
    var usuario by rememberSaveable { mutableStateOf(usuarioInicial.ifBlank { "" }) }
    var nombre by rememberSaveable { mutableStateOf(nombreInicial) }
    var correoTexto by rememberSaveable { mutableStateOf("") }
    var codigo by rememberSaveable { mutableStateOf("") }
    var acepto by rememberSaveable { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (conIntro) {
            Text(vm.textosApp.introduccion, style = MaterialTheme.typography.bodyMedium, color = f.texto)
            Pista(vm.textosApp.contenido)
        }
        if (correo == false) Pista(stringResource(R.string.social_red_codigo_no_disponible))
        val oculta = androidx.compose.ui.text.input.PasswordVisualTransformation()
        @Composable
        fun CampoClave(valor: String, onCambio: (String) -> Unit, etiqueta: String) =
            OutlinedTextField(valor, { onCambio(it.take(200)) }, Modifier.fillMaxWidth(), label = { Text(etiqueta) }, singleLine = true, shape = forma(18.dp),
                visualTransformation = oculta, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                supportingText = { Text(stringResource(R.string.social_red_clave_regla)) })
        when (acceso.paso) {
            // El correo siempre lleva al envío de código; la contraseña tiene su propio camino.
            PasoAcceso.CORREO -> {
                OutlinedTextField(correoTexto, { correoTexto = it.trim().take(120) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.social_red_tu_correo)) },
                    singleLine = true, shape = forma(18.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                BotonRiso(stringResource(R.string.social_red_mandar_codigo), { vm.red.revisarCorreo(correoTexto) }, Modifier.fillMaxWidth(), habilitado = !acceso.ocupado && "@" in correoTexto && "." in correoTexto)
                BotonSecundario(stringResource(R.string.social_red_entrar_clave), { vm.red.usarClave(correoTexto) }, Modifier.fillMaxWidth(), habilitado = !acceso.ocupado && "@" in correoTexto)
                return
            }
            // Tiene contraseña: la pide, con "¿Olvidaste tu contraseña?".
            PasoAcceso.CLAVE -> {
                var clave by rememberSaveable { mutableStateOf("") }
                Text(acceso.correo, style = MaterialTheme.typography.titleMedium, color = f.texto)
                OutlinedTextField(clave, { clave = it.take(200) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.social_red_clave)) }, singleLine = true, shape = forma(18.dp),
                    visualTransformation = oculta, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                BotonRiso(stringResource(R.string.social_red_entrar), { vm.red.entrarConClave(acceso.correo, clave) }, Modifier.fillMaxWidth(), habilitado = !acceso.ocupado && clave.length >= 8)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pildora(stringResource(R.string.social_red_olvide)) { vm.red.pedirCodigo(MotivoCodigo.OLVIDO) }
                    Pildora(stringResource(R.string.social_red_otro_correo)) { vm.red.volverAlCorreo() }
                }
                return
            }
            // El código: para registrarse, o con una contraseña nueva (si la olvidó o la cuenta no tenía).
            PasoAcceso.CODIGO -> {
                var nueva by rememberSaveable { mutableStateOf("") }
                val conClaveNueva = acceso.motivo != MotivoCodigo.NUEVA
                if (acceso.motivo == MotivoCodigo.SIN_CLAVE) Text(vm.textosApp.sinClave, style = MaterialTheme.typography.titleMedium, color = f.texto)
                Pista(stringResource(R.string.social_red_revisa_correo, acceso.correo))
                OutlinedTextField(codigo, { codigo = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.social_red_codigo)) },
                    singleLine = true, shape = forma(18.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                if (conClaveNueva) {
                    CampoClave(nueva, { nueva = it }, stringResource(R.string.social_red_clave_nueva))
                    if (acceso.motivo == MotivoCodigo.OLVIDO) Pista(vm.textosApp.recuperacion)
                }
                BotonRiso(stringResource(R.string.social_red_entrar), { vm.red.entrar(codigo, nueva) }, Modifier.fillMaxWidth(),
                    habilitado = !acceso.ocupado && codigo.length == 6 && (!conClaveNueva || nueva.length >= 8))
                ReenviarCodigo(vm)
                BotonSecundario(stringResource(R.string.social_red_otro_correo), { vm.red.volverAlCorreo() }, Modifier.fillMaxWidth())
                return
            }
            else -> Unit
        }
        // Cuenta nueva: usuario, apodo y aceptación con el correo comprobado.
        Text(stringResource(R.string.social_red_elige_usuario), style = MaterialTheme.typography.titleMedium, color = f.texto)
        OutlinedTextField(usuario, { usuario = it.lowercase().filter { c -> c.isLetterOrDigit() && c.code < 128 || c == '.' || c == '_' }.take(30) }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.social_red_usuario)) }, prefix = { Text("@") }, singleLine = true, shape = forma(18.dp))
        CampoTexto(nombre, { nombre = it }, stringResource(R.string.social_red_nombre_visible))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(acepto, { acepto = it }, colors = CheckboxDefaults.colors(checkedColor = f.tinta1, checkmarkColor = f.sobreTinta1))
            Text(stringResource(R.string.social_red_acepto), style = MaterialTheme.typography.bodySmall, color = f.texto, modifier = Modifier.weight(1f).clickable { acepto = !acepto })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pildora(stringResource(R.string.social_red_politica)) { uri.openUri(ClienteSocial.SERVIDOR + "/privacidad") }
            Pildora(stringResource(R.string.social_red_terminos)) { uri.openUri(ClienteSocial.SERVIDOR + "/terminos") }
        }
        // Con correo, la contraseña es obligatoria: con ella el cajón queda guardado (cifrado) en la cuenta.
        var claveCuenta by rememberSaveable { mutableStateOf("") }
        if (acceso.paso == PasoAcceso.USUARIO) CampoClave(claveCuenta, { claveCuenta = it }, stringResource(R.string.social_red_clave))
        var fotoCuenta by rememberSaveable { mutableStateOf<String?>(null) }
        val elegirFoto = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { fotoCuenta = it?.toString() }
        BotonSecundario(stringResource(if (fotoCuenta == null) R.string.social_red_foto_opcional else R.string.social_red_foto_elegida), { elegirFoto.launch("image/*") }, Modifier.fillMaxWidth())
        val listo = !acceso.ocupado && acepto && usuario.length >= 3 && (acceso.paso != PasoAcceso.USUARIO || claveCuenta.length >= 8)
        BotonRiso(stringResource(R.string.social_red_crear_cuenta), {
            vm.crearConCorreo(usuario, nombre, claveCuenta, fotoCuenta)
        }, Modifier.fillMaxWidth(), habilitado = listo)
    }
}

@Composable
internal fun ConfigCuenta(vm: InterfazSocial, usuario: String) {
    val f = FogonTema.actual
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val privada by vm.red.privada.collectAsStateWithLifecycle()
    val correo by vm.correoDisponible.collectAsStateWithLifecycle()
    val correoCuenta by vm.red.correoCuenta.collectAsStateWithLifecycle()
    val acceso by vm.red.acceso.collectAsStateWithLifecycle()
    var correoTexto by rememberSaveable { mutableStateOf("") }
    var codigo by rememberSaveable { mutableStateOf("") }
    var borrar by remember { mutableStateOf(false) }
    if (borrar) {
        FormularioBorrado(vm) { borrar = false }
        return
    }
    SeguridadEquipo(vm)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("@$usuario", style = MaterialTheme.typography.headlineSmall, color = f.texto)
        Text(ClienteSocial.enlacePerfil(usuario), style = MaterialTheme.typography.bodySmall, color = f.acentoTexto)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pildora(stringResource(R.string.social_red_compartir_perfil), Iconos.Compartir, acento = true) { compartirTexto(ctx, ClienteSocial.enlacePerfil(usuario)) }
            Pildora(stringResource(R.string.social_red_ver_mi_perfil), Iconos.Persona) { vm.navegacion.perfil(usuario) }
        }
        Opcion(stringResource(R.string.social_red_cuenta_privada), privada) { vm.red.cambiarPrivada(it) }
        Pista(stringResource(R.string.social_red_cuenta_privada_pista))
        Pista(vm.textosApp.compartido)
        if (correoCuenta.isBlank()) Pista(stringResource(R.string.social_red_vincula_pista))
        if (correo == true) {
            if (correoCuenta.isNotBlank()) Pista(stringResource(R.string.social_red_correo_de_cuenta, correoCuenta))
            else if (acceso.paso == PasoAcceso.CODIGO) {
                OutlinedTextField(codigo, { codigo = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.social_red_codigo)) },
                    singleLine = true, shape = forma(18.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                ReenviarCodigo(vm)
                BotonRiso(stringResource(R.string.social_red_vincular), { vm.vincularCorreo(codigo) }, Modifier.fillMaxWidth(), habilitado = !acceso.ocupado && codigo.length == 6)
            } else {
                Pista(stringResource(R.string.social_red_vincula_pista))
                OutlinedTextField(correoTexto, { correoTexto = it.trim().take(120) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.social_red_tu_correo)) },
                    singleLine = true, shape = forma(18.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                BotonSecundario(stringResource(R.string.social_red_mandar_codigo), { vm.red.pedirCodigoVincular(correoTexto) }, Modifier.fillMaxWidth(), habilitado = !acceso.ocupado && "@" in correoTexto)
            }
        }
        if (correoCuenta.isNotBlank()) FormularioCorreo(vm)
        // Tu usuario (una vez cada 30 días) y tu contraseña.
        val cuenta by vm.red.cuenta.collectAsStateWithLifecycle()
        Desplegable(Iconos.Persona, stringResource(R.string.social_red_cambiar_usuario), "@$usuario") {
            var nuevo by rememberSaveable { mutableStateOf(usuario) }
            Pista(stringResource(R.string.social_red_usuario_regla))
            val desde = cuenta?.usuarioDesde ?: 0
            if (desde > System.currentTimeMillis()) Pista(stringResource(R.string.social_red_usuario_desde, java.text.DateFormat.getDateInstance().format(java.util.Date(desde))))
            else {
                OutlinedTextField(nuevo, { nuevo = it.lowercase().filter { c -> c.isLetterOrDigit() && c.code < 128 || c == '.' || c == '_' }.take(30) }, Modifier.fillMaxWidth(),
                    prefix = { Text("@") }, singleLine = true, shape = forma(18.dp))
                BotonRiso(stringResource(R.string.social_guardar), { vm.cambiarUsuario(nuevo) {} }, Modifier.fillMaxWidth(), habilitado = nuevo.length >= 3 && nuevo != usuario)
            }
        }
        if (correoCuenta.isNotBlank()) Desplegable(Iconos.Candado, stringResource(if (cuenta?.conClave == true) R.string.social_red_cambiar_clave else R.string.social_red_crear_clave), null) {
            var actual by rememberSaveable { mutableStateOf("") }
            var nueva by rememberSaveable { mutableStateOf("") }
            val oculto = androidx.compose.ui.text.input.PasswordVisualTransformation()
            if (cuenta?.conClave == true) OutlinedTextField(actual, { actual = it.take(200) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.social_red_clave_actual), color = FogonTema.actual.texto) },
                singleLine = true, shape = forma(18.dp), visualTransformation = oculto)
            OutlinedTextField(nueva, { nueva = it.take(200) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.social_red_clave_nueva)) },
                singleLine = true, shape = forma(18.dp), visualTransformation = oculto, supportingText = { Text(stringResource(R.string.social_red_clave_regla)) })
            BotonRiso(stringResource(R.string.social_guardar), { vm.ponerClave(nueva, actual) { nueva = ""; actual = "" } }, Modifier.fillMaxWidth(), habilitado = nueva.length >= 8)
        } else Pista(stringResource(R.string.social_red_clave_pide_correo))
        FilaVidrio({ vm.navegacion.gente(usuario, true) }) { Text(stringResource(R.string.social_red_tus_seguidores), color = f.texto, modifier = Modifier.weight(1f)); IconoRiso(Iconos.Siguiente, null, tamano = 18.dp, tinta = f.textoSuave) }
        FilaVidrio({ vm.navegacion.ajustes(AjusteSocial.SESIONES) }) { Text(stringResource(R.string.social_red_sesiones), color = f.texto) }
        FilaVidrio({ vm.navegacion.ajustes(AjusteSocial.ESTADO_CUENTA) }) { Text(stringResource(R.string.social_mod_estado), color = f.texto, modifier = Modifier.weight(1f)); IconoRiso(Iconos.Siguiente, null, tamano = 18.dp, tinta = f.textoSuave) }
        FilaVidrio({ vm.navegacion.ajustes(AjusteSocial.BLOQUEADOS) }) { Text(stringResource(R.string.social_red_bloqueadas), color = f.texto, modifier = Modifier.weight(1f)); IconoRiso(Iconos.Siguiente, null, tamano = 18.dp, tinta = f.textoSuave) }
        FilaVidrio({ uri.openUri(ClienteSocial.SERVIDOR + "/privacidad") }) { Text(stringResource(R.string.social_red_politica), color = f.texto, modifier = Modifier.weight(1f)); IconoRiso(Iconos.Enlace, null, tamano = 18.dp, tinta = f.textoSuave) }
        FilaVidrio({ uri.openUri(ClienteSocial.SERVIDOR + "/terminos") }) { Text(stringResource(R.string.social_red_terminos), color = f.texto, modifier = Modifier.weight(1f)); IconoRiso(Iconos.Enlace, null, tamano = 18.dp, tinta = f.textoSuave) }
        BotonSecundario(stringResource(R.string.social_red_salir), { vm.salir() }, Modifier.fillMaxWidth())
        BotonSecundario(stringResource(R.string.social_red_borrar_cuenta), { borrar = true }, Modifier.fillMaxWidth(), peligro = true)
    }
}

@Composable
fun FormularioBorrado(vm: InterfazSocial, volver: () -> Unit) {
    val cuenta by vm.red.cuenta.collectAsStateWithLifecycle()
    val ocupado by vm.red.seguridadOcupada.collectAsStateWithLifecycle()
    var clave by remember { mutableStateOf("") }
    var codigo by remember { mutableStateOf("") }
    var motivo by rememberSaveable { mutableStateOf("") }
    var texto by rememberSaveable { mutableStateOf("") }
    var confirmar by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.social_red_borrar_cuenta), style = MaterialTheme.typography.headlineSmall, color = FogonTema.actual.texto)
        Pista(vm.textosApp.borradoExplicacion)
        Chips(listOf("" to stringResource(R.string.social_red_motivo_ninguno), "no_uso" to stringResource(R.string.social_red_motivo_no_uso),
            "privacidad" to stringResource(R.string.social_red_motivo_privacidad), "problemas" to stringResource(R.string.social_red_motivo_problemas),
            "otro" to stringResource(R.string.social_red_motivo_otro)), motivo) { motivo = it }
        OutlinedTextField(texto, { texto = it.take(300) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.social_red_motivo_texto), color = FogonTema.actual.texto) })
        CredencialSeguridad(vm, cuenta?.conClave == true, clave, { clave = it }, codigo, { codigo = it })
        Opcion(vm.textosApp.borradoSeguro, confirmar) { confirmar = it }
        BotonSecundario(stringResource(R.string.social_red_si_borrar), { vm.red.borrarCuenta(clave, codigo, motivo, texto) }, Modifier.fillMaxWidth(), peligro = true,
            habilitado = !ocupado && cuenta != null && confirmar && credencialBorradoValida(cuenta?.conClave == true, clave, codigo, texto))
        BotonSecundario(stringResource(R.string.social_volver), volver, Modifier.fillMaxWidth(), habilitado = !ocupado)
    }
}

@Composable
internal fun CredencialSeguridad(vm: InterfazSocial, conClave: Boolean, clave: String, cambiarClave: (String) -> Unit, codigo: String, cambiarCodigo: (String) -> Unit) {
    if (conClave) OutlinedTextField(clave, { cambiarClave(it.take(200)) }, Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.social_red_clave_actual), color = FogonTema.actual.texto) }, singleLine = true,
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
    else {
        BotonSecundario(stringResource(R.string.social_red_mandar_codigo), { vm.red.pedirCodigoSeguridad() }, Modifier.fillMaxWidth())
        OutlinedTextField(codigo, { cambiarCodigo(it.filter { c -> c in '0'..'9' }.take(6)) }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.social_red_codigo), color = FogonTema.actual.texto) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
    }
}

@Composable
internal fun FormularioCorreo(vm: InterfazSocial) {
    val cuenta by vm.red.cuenta.collectAsStateWithLifecycle()
    val pendiente by vm.red.correoPendiente.collectAsStateWithLifecycle()
    val ocupado by vm.red.seguridadOcupada.collectAsStateWithLifecycle()
    Desplegable(Iconos.Candado, stringResource(R.string.social_red_cambiar_correo), null) {
        var nuevo by rememberSaveable { mutableStateOf("") }
        var clave by remember { mutableStateOf("") }
        var codigo by remember { mutableStateOf("") }
        var codigoNuevo by remember { mutableStateOf("") }
        var usarCodigo by rememberSaveable { mutableStateOf(false) }
        val pideClave = cuenta?.conClave == true && !usarCodigo
        if (pendiente.isBlank()) {
            OutlinedTextField(nuevo, { nuevo = it.trim().take(120) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.social_red_correo_nuevo)) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            if (cuenta?.conClave == true) Opcion(stringResource(R.string.social_red_usar_codigo_actual), usarCodigo) {
                usarCodigo = it; clave = ""; codigo = ""
            }
            CredencialSeguridad(vm, pideClave, clave, { clave = it }, codigo, { codigo = it })
            BotonRiso(stringResource(R.string.social_red_mandar_codigo), { vm.red.cambiarCorreo(nuevo, clave, codigo) }, Modifier.fillMaxWidth(),
                habilitado = !ocupado && cuenta != null && correoNuevoValido(nuevo, cuenta?.correo.orEmpty()) &&
                    credencialBorradoValida(pideClave, clave, codigo, ""))
        } else {
            androidx.compose.runtime.LaunchedEffect(pendiente) { clave = ""; codigo = ""; codigoNuevo = "" }
            Pista(stringResource(R.string.social_red_codigo_correo_nuevo, pendiente))
            OutlinedTextField(codigoNuevo, { codigoNuevo = it.filter { c -> c in '0'..'9' }.take(6) }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.social_red_codigo)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            BotonRiso(stringResource(R.string.social_guardar), { vm.red.confirmarCorreo(codigoNuevo) }, Modifier.fillMaxWidth(), habilitado = !ocupado && codigoNuevo.length == 6)
            BotonSecundario(stringResource(R.string.social_volver), { vm.red.cancelarCambioCorreo() }, Modifier.fillMaxWidth(), habilitado = !ocupado)
        }
    }
}

@Composable
fun MotivosReporte(onElegir: (String, String) -> Unit) {
    val f = FogonTema.actual
    Text(stringResource(R.string.social_rep_por_que), style = MaterialTheme.typography.titleMedium, color = f.texto,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
    MOTIVOS_REPORTE.forEach { (codigo, recurso) ->
        val texto = stringResource(recurso)
        FilaMenu(Iconos.Bandera, texto) { onElegir(codigo, texto) }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun HojaReporte(onCerrar: () -> Unit, onElegir: (String, String) -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onCerrar, containerColor = FogonTema.actual.fondo) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) { MotivosReporte(onElegir) }
    }
}

@Composable
fun Moderacion(vm: InterfazSocial) {
    val f = FogonTema.actual
    val confirmar = vm.confirmar
    val casos by vm.red.casos.collectAsStateWithLifecycle()
    val cuenta by vm.red.cuenta.collectAsStateWithLifecycle()
    val bloqueado by vm.red.equipoBloqueado.collectAsStateWithLifecycle()
    val permisos = cuenta?.permisos.orEmpty()
    var ahora by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(cuenta?.elevadaHasta) {
        ahora = System.currentTimeMillis()
        val hasta = cuenta?.elevadaHasta ?: 0
        if (hasta > ahora) { kotlinx.coroutines.delay(hasta - ahora); ahora = System.currentTimeMillis() }
    }
    val requiere = cuenta?.let { necesitaElevar(it.mfa, it.elevadaHasta, ahora) } ?: true
    LaunchedEffect(requiere, bloqueado) { if (!requiere && !bloqueado) vm.red.cargarCasos() }
    val cantidadApp = vm.cantidadModeracionApp()
    Cabecera(stringResource(R.string.social_mod_titulo), vm.navegacion.volver)
    if (requiere || bloqueado) { SeguridadEquipo(vm, elevar = true); return }
    var seccion by rememberSaveable { mutableStateOf(if ("ver_reportes" in permisos) 0 else if ("gestionar_frases" in permisos) 1 else if ("asignar_equipo" in permisos) 2 else 3) }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if ("ver_reportes" in permisos) Pildora(stringResource(R.string.social_mod_reportes) + casos?.size?.takeIf { it > 0 }?.let { " · $it" }.orEmpty(), Iconos.Bandera, acento = seccion == 0) { seccion = 0 }
        if ("gestionar_frases" in permisos) Pildora(vm.tituloModeracionApp() + cantidadApp.takeIf { it > 0 }?.let { " · $it" }.orEmpty(), Iconos.Comentario, acento = seccion == 1) { seccion = 1 }
        if ("asignar_equipo" in permisos) Pildora(stringResource(R.string.social_equipo_titulo), acento = seccion == 2) { seccion = 2 }
        if ("ver_registro" in permisos) Pildora(stringResource(R.string.social_registro_titulo), acento = seccion == 3) { seccion = 3 }
    }
    if (seccion == 2 && "asignar_equipo" in permisos) { Equipo(vm); return }
    if (seccion == 3 && "ver_registro" in permisos) { RegistroEquipoPantalla(vm); return }
    if (seccion == 1 && "gestionar_frases" in permisos) { vm.moderacionApp(); return }
    if ("ver_reportes" !in permisos) return
    val l = casos
    when {
        l == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.tinta1, trackColor = f.linea)
        l.isEmpty() -> Pista(stringResource(R.string.social_mod_vacio), Modifier.padding(16.dp))
        else -> l.forEach { c ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).tarjetaFogon().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val tipo = stringResource(when (c.que) {
                    "cuenta" -> R.string.social_mod_tipo_cuenta
                    "comentario" -> R.string.social_mod_tipo_comentario
                    "mensaje" -> R.string.social_mod_tipo_mensaje
                    else -> R.string.social_red_publicacion
                })
                val motivos = c.motivos.entries.map { (codigo, cantidad) ->
                    stringResource(MOTIVOS_REPORTE.firstOrNull { it.first == codigo }?.second ?: R.string.social_rep_otro) + " ×$cantidad"
                }.joinToString()
                Text("$tipo · $motivos", style = MaterialTheme.typography.labelMedium,
                    color = if ("menor_en_riesgo" in c.motivos) MaterialTheme.colorScheme.error else f.acentoTexto)
                c.autor?.let { Text("@${it.usuario} · ${stringResource(R.string.social_mod_faltas, c.faltasAutor)}", style = MaterialTheme.typography.bodySmall, color = f.textoSuave) }
                if (c.que == "mensaje") Text(limpiarReporteMensaje(c.contenido).take(600),
                    style = MaterialTheme.typography.bodyMedium, color = f.texto)
                else vm.contenidoReporte(c.contenido)
                Text(pluralStringResource(R.plurals.social_mod_personas, c.personas, c.personas) + if (c.oculto) " · " + stringResource(R.string.social_mod_oculto) else "", style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                val acciones = accionesModeracion(permisos, c.motivos.maxByOrNull { it.value }?.key.orEmpty(), c.faltasAutor)
                if (puedeRestaurar(c.estado, permisos)) Pildora(stringResource(R.string.social_equipo_restaurar)) {
                    confirmar(vm.tx(R.string.social_equipo_restaurar), vm.tx(R.string.social_equipo_restaurar_confirmar)) { vm.red.restaurar(c) }
                }
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if ("sin_falta" in acciones && c.estado != "retirado") Pildora(stringResource(R.string.social_mod_sin_falta), acento = true) { vm.red.decidir(c, "sin_falta") }
                    if ("falta" in acciones && c.estado != "retirado") Pildora(stringResource(R.string.social_mod_falta)) {
                        confirmar(vm.tx(R.string.social_mod_falta), vm.tx(R.string.social_mod_falta_confirmar)) { vm.red.decidir(c, "falta") }
                    }
                    if ("falta_grave" in acciones && c.estado != "retirado") Pildora(stringResource(R.string.social_mod_grave)) {
                        confirmar(vm.tx(R.string.social_mod_grave), vm.tx(R.string.social_mod_grave_confirmar)) { vm.red.decidir(c, "falta_grave") }
                    }
                    if ("cerrar" in acciones && c.estado != "retirado") Pildora(stringResource(R.string.social_mod_cerrar)) {
                        confirmar(vm.tx(R.string.social_mod_cerrar), vm.tx(R.string.social_mod_cerrar_confirmar)) { vm.red.decidir(c, "cerrar") }
                    }
                }
            }
        }
    }
    Pista(stringResource(R.string.social_mod_menores), Modifier.padding(16.dp))
}

@Composable
fun TarjetaPublicacion(vm: InterfazSocial, p: PublicacionSocial, modifier: Modifier = Modifier, abrir: Boolean = true, motivo: String? = null) {
    val f = FogonTema.actual
    val ctx = LocalContext.current
    val d = p.datos
    var destapado by remember(p.id) { mutableStateOf(false) }
    var menu by remember(p.id) { mutableStateOf(false) }
    LaunchedEffect(p.id) { vm.vista(p.id) }
    if (menu) vm.menu(p) { menu = false }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).tarjetaFogon()
        .then(if (abrir) Modifier.combinedClickable(onLongClick = { menu = true }) { vm.navegacion.publicacion(p.id) } else Modifier).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().clip(FormaPildora).clickable { vm.navegacion.perfil(p.autor.usuario) },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AvatarRed(p.autor, 36.dp)
            Column(Modifier.weight(1f)) {
                Text(p.autor.nombre.ifBlank { p.autor.usuario }, style = MaterialTheme.typography.titleSmall, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text((if (p.tipo in vm.contenido.tiposAdmitidos) vm.contenido.NombreTipo(p.tipo) else stringResource(R.string.social_red_tipo_publico)) + " · " + haceCuanto(p.cuando), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1)
            }
            if (p.visibilidad == "amigos") IconoRiso(Iconos.Candado, stringResource(R.string.social_red_para_seguidores), tamano = 16.dp, tinta = f.textoSuave)
            BotonIcono(Iconos.Opciones, stringResource(R.string.social_red_mas_opciones), tinta = f.textoSuave) { menu = true }
        }
        // Por qué ves esto (en Explorar).
        motivo?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = f.acentoTexto, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        if (!vm.contenido.multimediaEnCuerpo(p.tipo) && (d.texto("spoiler") != "true" || destapado)) {
            if (d.texto("video").isNotBlank()) VideoPublicacion(vm::cabeceras, d.texto("video"), d.texto("poster"), d.texto("duracion").toIntOrNull() ?: 0)
            else CarruselFotos(imagenesPublicacion(d))
        }
        if (p.tipo in vm.contenido.tiposAdmitidos) vm.contenido.Cuerpo(p, destapado)
        else TarjetaNeutra(p)
        val texto = d.texto("texto")
        if (texto.isNotBlank() || (d.texto("spoiler") == "true" && !destapado)) {
            if (d.texto("spoiler") == "true" && !destapado) {
                Box(Modifier.clip(FormaPildora).background(f.superficie).border(f.borde, f.linea, FormaPildora).clickable { destapado = true }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.social_red_spoiler_tocar), style = MaterialTheme.typography.labelLarge, color = f.texto)
                }
            } else TextoConEtiquetas(vm, texto, if (abrir) 6 else Int.MAX_VALUE)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BotonIcono(Iconos.Corazon, stringResource(R.string.social_publicacion_megusta_estado, p.megusta, stringResource(if (p.yoMeGusta) R.string.social_publicacion_gusta_si else R.string.social_publicacion_gusta_no)), tinta = if (p.yoMeGusta) f.acentoTexto else f.textoSuave, lleno = p.yoMeGusta) { vm.red.meGusta(p) }
            val nombreMeGusta = stringResource(R.string.social_publicacion_megusta, p.megusta)
            if (p.megusta > 0) Text("${p.megusta}", style = MaterialTheme.typography.labelLarge, color = f.texto, modifier = Modifier.semantics { contentDescription = nombreMeGusta })
            Box(Modifier.width(8.dp))
            BotonIcono(Iconos.Comentario, pluralStringResource(R.plurals.social_publicacion_comentarios, p.comentarios, p.comentarios), tinta = f.textoSuave) { vm.navegacion.publicacion(p.id) }
            val nombreComentarios = pluralStringResource(R.plurals.social_publicacion_comentarios, p.comentarios, p.comentarios)
            if (p.comentarios > 0) Text("${p.comentarios}", style = MaterialTheme.typography.labelLarge, color = f.texto, modifier = Modifier.semantics { contentDescription = nombreComentarios })
            Box(Modifier.weight(1f))
            if (p.vistas > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(end = 6.dp)) {
                IconoRiso(Iconos.Anime, stringResource(R.string.social_red_vistas), tamano = 18.dp, tinta = f.textoSuave)
                Text(cifraCorta(p.vistas), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
            }
            BotonIcono(Iconos.Guardar, stringResource(if (p.yoGuardo) R.string.social_red_quitar_guardada else R.string.social_red_guardar), tinta = if (p.yoGuardo) f.acentoTexto else f.textoSuave, lleno = p.yoGuardo) { vm.red.guardar(p) }
            BotonIcono(Iconos.Compartir, stringResource(R.string.social_compartir), tinta = f.textoSuave) { compartirTexto(ctx, ClienteSocial.enlacePublicacion(p.id)) }
        }
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
private fun compartirTexto(ctx: android.content.Context, texto: String) {
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto), null))
}

@Composable
private fun InvitaCuenta(vm: InterfazSocial) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Pista(stringResource(R.string.social_red_invita_texto))
        BotonRiso(stringResource(R.string.social_red_crear_cuenta), vm.navegacion.cuenta, Modifier.fillMaxWidth())
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MenuPublicacion(vm: InterfazSocial, p: PublicacionSocial, motivo: String, onCerrar: () -> Unit) {
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
                    val ayuda = stringResource(R.string.social_rep_ayuda)
                    MotivosReporte { codigo, texto ->
                        vm.red.reportar("publicacion", "${p.id}", "$codigo: $texto"); vm.red.ocultar(p.id)
                        if (codigo == "autolesion") android.widget.Toast.makeText(ctx, ayuda, android.widget.Toast.LENGTH_LONG).show()
                        onCerrar()
                    }
                }
                porque -> {
                    Text(stringResource(R.string.social_menu_por_que), style = MaterialTheme.typography.titleMedium, color = f.texto, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                    Text(motivo, style = MaterialTheme.typography.bodyMedium, color = f.textoSuave, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
                    Text(vm.textosApp.explicacionOrden, style = MaterialTheme.typography.bodySmall, color = f.textoSuave, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                }
                else -> {
                    Fila(Iconos.Estrella, stringResource(R.string.social_menu_por_que)) { porque = true }
                    if (p.autor.usuario != yo) {
                        Fila(Iconos.Chulo, stringResource(R.string.social_menu_me_interesa)) { vm.interesa(p, true); onCerrar() }
                        Fila(Iconos.Cerrar, stringResource(R.string.social_menu_no_me_interesa)) { vm.interesa(p, false); onCerrar() }
                        Fila(Iconos.Persona, stringResource(R.string.social_menu_silenciar, p.autor.usuario)) { vm.silenciar(p.autor.usuario); onCerrar() }
                    }
                    Fila(Iconos.Enlace, stringResource(R.string.social_menu_copiar_enlace)) {
                        (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                            .setPrimaryClip(android.content.ClipData.newPlainText("", ClienteSocial.enlacePublicacion(p.id))); onCerrar()
                    }
                    if (p.autor.usuario != yo && yo != null) {
                        Fila(Iconos.Candado, stringResource(R.string.social_menu_bloquear, p.autor.usuario), peligro = true) { vm.red.bloquear(p.autor.usuario, true); onCerrar() }
                        Fila(Iconos.Bandera, stringResource(R.string.social_red_reportar), peligro = true) { reportando = true }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EstadoCuenta(vm: InterfazSocial) {
    val f = FogonTema.actual
    val estado by vm.red.estadoCuenta.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.red.cargarEstadoCuenta() }
    Cabecera(stringResource(R.string.social_mod_estado), vm.navegacion.volver)
    val e = estado ?: run { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = f.tinta1, trackColor = f.linea); return }
    val fecha = { t: Long -> java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(t)) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val ahora = System.currentTimeMillis()
        when {
            e.cerrada -> Text(stringResource(R.string.social_mod_cerrada), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            e.suspendidaHasta > ahora -> Text(stringResource(R.string.social_mod_suspendida_hasta, fecha(e.suspendidaHasta)), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            e.restringidaHasta > ahora -> Text(stringResource(R.string.social_mod_restringida_hasta, fecha(e.restringidaHasta)), style = MaterialTheme.typography.titleMedium, color = f.acentoTexto)
            e.faltas.isEmpty() -> Text(stringResource(R.string.social_mod_todo_bien), style = MaterialTheme.typography.titleMedium, color = f.texto)
        }
        e.faltas.forEach { x ->
            Column(Modifier.fillMaxWidth().tarjetaFogon().padding(14.dp)) {
                Text(textoFalta(x) { vm.tx(it) }, style = MaterialTheme.typography.titleSmall, color = f.texto)
                Text(stringResource(R.string.social_mod_falta_vence, fecha(x.cuando), fecha(x.vence)), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
        }
        Pista(stringResource(R.string.social_mod_como_funciona))
    }
}

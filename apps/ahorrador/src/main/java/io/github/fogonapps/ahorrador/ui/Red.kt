package io.github.fogonapps.ahorrador.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.ahorrador.AhorradorViewModel
import io.github.fogonapps.ahorrador.Pantalla
import io.github.fogonapps.ahorrador.R
import io.github.fogonapps.ahorrador.datos.*
import io.github.fogonapps.identidad.*
import io.github.fogonapps.perfil.FotoPerfilLocal
import io.github.fogonapps.perfil.SeccionPerfil
import io.github.fogonapps.social.*
import io.github.fogonapps.social.ui.*
import kotlinx.serialization.json.*

/*
 * La red de La Alcancía (D-032), como La Cucha y El Forzudo: cuenta, «Tu gente», perfiles ajenos y tarjetas de
 * logros. Solo salen logros y metas en porcentaje; nunca montos, saldos, cuentas, bancos ni movimientos. Sin chat.
 */

@Composable
fun interfazCuenta(vm: AhorradorViewModel): InterfazSocial {
    val confirmar = LocalConfirmar.current
    val explicacion = stringResource(R.string.cuenta_compartido)
    val local = stringResource(R.string.cuenta_local)
    val borrado = stringResource(R.string.cuenta_borrado)
    val intro = stringResource(R.string.cuenta_intro)
    val queSale = stringResource(R.string.cuenta_que_sale)
    val pausa = stringResource(R.string.cuenta_politica_pausa)
    val resumen = stringResource(R.string.cuenta_politica_resumen)
    val nombreTipo = stringResource(R.string.red_tipo_logro)
    return remember(vm, confirmar, explicacion, local, borrado, intro, queSale, pausa, resumen, nombreTipo) {
        object : InterfazSocial {
            override fun adjuntosApp() = vm.adjuntosHistoria()
            override val respondeHistorias = true
            override fun responderHistoria(usuario: String, texto: String) {
                vm.red.chat(vm.getApplication()) {}.enviar(usuario, tx(io.github.fogonapps.social.ui.R.string.historia_respondio).format(texto))
            }
            override suspend fun buscarAdjuntos(q: String) = vm.adjuntosHistoria().filter { (it["titulo"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty().contains(q.trim(), ignoreCase = true) }
            @androidx.compose.runtime.Composable override fun textoBuscarAdjunto() = androidx.compose.ui.res.stringResource(R.string.historia_buscar_app)
            override val red = vm.red
            override val correoDisponible = red.correoDisponible
            override val textosApp = TextosSocialApp(
                introduccion = intro, contenido = queSale, compartido = explicacion, recuperacion = local,
                borradoExplicacion = borrado, borradoSeguro = borrado, sinClave = local,
                pausaPolitica = pausa, resumenPolitica = resumen, explicacionOrden = "",
            )
            override val confirmar: (String, String, () -> Unit) -> Unit = { t, x, si -> confirmar(t, x, si) }
            override val navegacion = NavegacionSocial(
                perfil = { vm.abrir(Pantalla.Social("perfil", usuario = it)) }, publicacion = { vm.abrir(Pantalla.Social("publicacion", id = it)) },
                etiqueta = { vm.abrir(Pantalla.Social("etiqueta", usuario = it)) },
                cuenta = { vm.abrir(Pantalla.CuentaRed) }, ajustes = { vm.abrir(Pantalla.Seguridad(it)) },
                adjunto = {}, volver = vm::cerrar, gente = { u, seguidores -> vm.abrir(Pantalla.Gente(u, seguidores)) },
            )
            override val contenido = object : ContenidoSocialApp {
                override val tiposAdmitidos = setOf(TIPO_LOGRO)
                override fun multimediaEnCuerpo(tipo: String) = false
                @Composable override fun Compositor(tipo: String, adjunto: AdjuntoSocial?) {}
                @Composable override fun NombreTipo(tipo: String) = nombreTipo
                @Composable override fun Cuerpo(publicacion: PublicacionSocial, destapado: Boolean) { CuerpoLogro(publicacion.datos) }
                @Composable override fun SeccionesPerfil(perfil: PerfilSocial, propio: Boolean, contenido: @Composable (Int) -> Unit) =
                    listOf(SeccionPerfil(0, IconosFogon.Estrella, stringResource(R.string.red_logros)) { contenido(0) })
                @Composable override fun CifrasPerfil(perfil: PerfilSocial) {}
            }
            override fun tx(recurso: Int) = vm.tx(recurso)
            @Composable override fun palabrasOcultas() = io.github.fogonapps.social.ui.palabrasOcultasRed()
            @Composable override fun cantidadModeracionApp() = 0
            @Composable override fun tituloModeracionApp() = ""
            override fun salir() { red.salir() }
            override fun interesa(publicacion: PublicacionSocial, si: Boolean) { if (!si) red.ocultar(publicacion.id) }
            override fun silenciar(usuario: String) {
                (red.feed.value.orEmpty() + red.explorar.value.orEmpty()).filter { it.autor.usuario == usuario }.forEach { red.ocultar(it.id) }
            }
            override fun mostrarComentario(palabras: List<String>, texto: String) = !io.github.fogonapps.social.ui.PalabrasOcultasRed.lleva(palabras, texto)
            override fun crearConCorreo(usuario: String, nombre: String, clave: String, foto: String?) {
                val bytes = foto?.let { android.util.Base64.decode(it.substringAfter(','), android.util.Base64.DEFAULT) }
                red.crear(usuario, nombre, clave, bytes)
            }
            override fun ponerClave(nueva: String, actual: String, listo: () -> Unit) { red.ponerClave(nueva, actual, listo) }
            override fun cambiarUsuario(nuevo: String, listo: () -> Unit) { red.cambiarUsuario(nuevo, listo) }
            override fun vincularCorreo(codigo: String) { red.vincular(codigo) }
            override fun vista(id: Long) { red.vistaPublicacion(id) }
            override fun borrarPublicacion(id: Long, clave: String) { red.borrar(id) }
            override fun quitarDeLaRed(clave: String) { red.retirar(clave) }
            override fun cabeceras() = red.cabecerasPublicacion()
            @Composable override fun menu(publicacion: PublicacionSocial, cerrar: () -> Unit) { MenuLogro(vm, this, publicacion, cerrar) }
            @Composable override fun moderacionApp() {}
            @Composable override fun contenidoReporte(contenido: String) {
                val datos = remember(contenido) { runCatching {
                    val raiz = JsonSocial.parseToJsonElement(contenido).jsonObject
                    (raiz["datos"] as? JsonObject) ?: raiz
                }.getOrNull() }
                if (datos != null && "logro" in datos) CuerpoLogro(datos) else Text(contenido.take(2000), color = FogonTema.actual.texto)
            }
        }
    }
}

/** El texto de un logro en el idioma de la app (el servidor solo guarda el código, la meta y el porcentaje). */
@Composable
fun textoLogro(datos: JsonObject): String {
    val meta = datos["meta"]?.jsonPrimitive?.contentOrNull.orEmpty()
    return when (datos["logro"]?.jsonPrimitive?.contentOrNull) {
        Logro.META_CUMPLIDA.codigo -> stringResource(R.string.red_logro_meta_cumplida, meta)
        Logro.META_AVANCE.codigo -> stringResource(R.string.red_logro_meta_avance, meta, datos["porcentaje"]?.jsonPrimitive?.intOrNull ?: 0)
        Logro.PRIMERA_ANOTACION.codigo -> stringResource(R.string.red_logro_primera)
        else -> stringResource(R.string.red_logro_cierres, datos["cantidad"]?.jsonPrimitive?.intOrNull ?: 0)
    }
}

@Composable
fun CuerpoLogro(datos: JsonObject) {
    val f = FogonTema.actual
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Ficha(f.tinta1, if (datos["logro"]?.jsonPrimitive?.contentOrNull == Logro.META_AVANCE.codigo) Iconos.Moneda else IconosFogon.Estrella, 44.dp)
        Text(textoLogro(datos), style = MaterialTheme.typography.titleMedium, color = f.texto, modifier = Modifier.weight(1f))
    }
    datos["porcentaje"]?.jsonPrimitive?.intOrNull?.let { Barra(it / 100f, f.tinta2, Modifier.padding(top = 8.dp)) }
}

@Composable
fun CuentaRedPantalla(vm: AhorradorViewModel, exportar: () -> Unit) {
    val interfaz = interfazCuenta(vm)
    val estado by vm.estado.collectAsStateWithLifecycle()
    CuentaRed(interfaz, "", estado?.nombre.orEmpty())
    CopiaCuenta(vm.red)
    Spacer(Modifier.height(12.dp))
    CambiarDeCuenta(vm, exportar)
}

/** «Cambiar de cuenta»: los perfiles de este teléfono, cada uno con su cuaderno. */
@Composable
fun CambiarDeCuenta(vm: AhorradorViewModel, exportar: () -> Unit) {
    SelectorPerfiles(
        perfilesTelefono = {
            val lista = vm.perfiles.lista
            PerfilesLocales(lista.perfiles.map { PerfilLocal(it.id, it.apodo, it.usuario, it.miniatura) }, lista.activo)
        }, cambiarPerfil = vm::cambiarPerfil, agregarPerfil = vm::agregarPerfil,
        quitarPerfil = vm::retirarPerfil, exportar = exportar,
        foto = { FotoPerfilLocal(it) },
    )
}

@Composable
fun SeguridadCuenta(vm: AhorradorViewModel, ajuste: AjusteSocial) {
    val interfaz = interfazCuenta(vm)
    when (ajuste) {
        AjusteSocial.SESIONES -> Sesiones(interfaz)
        AjusteSocial.ESTADO_CUENTA -> EstadoCuenta(interfaz)
        AjusteSocial.BLOQUEADOS -> BloqueadosRed(interfaz)
        AjusteSocial.BORRAR_CUENTA -> FormularioBorrado(interfaz, vm::cerrar)
    }
}

/** «Qué sale de tu teléfono» de La Alcancía. */
@Composable
fun TransparenciaAlcancia(vm: AhorradorViewModel) {
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    Transparencia(vm::cerrar, stringResource(R.string.cuenta_local), stringResource(R.string.cuenta_que_sale),
        buildList { if (usuario != null) add(stringResource(R.string.cuenta_servicio_social)) }, stringResource(R.string.cuenta_borrado), emptyList())
}

@Composable
fun PoliticaCuenta(vm: AhorradorViewModel) {
    PoliticaPrivacidad(interfazCuenta(vm))
    val gracia by vm.red.gracia.collectAsStateWithLifecycle()
    gracia?.let { GraciaBorrado(it.borrarEl, vm.red::decidirGracia) }
}

/** Las pantallas de la red que se abren encima. */
@Composable
fun RedAlcancia(vm: AhorradorViewModel, destino: Pantalla.Social) {
    val interfaz = interfazCuenta(vm)
    val navChat = io.github.fogonapps.social.ui.NavegacionChat({ vm.abrir(Pantalla.Social("chat", usuario = it)) }, { vm.abrir(Pantalla.Social("infochat", usuario = it)) })
    when (destino.destino) {
        "mensajes" -> io.github.fogonapps.social.ui.rememberChat(interfaz)?.let { io.github.fogonapps.social.ui.ChatsSocial(interfaz, it, navChat) }
        "chat" -> io.github.fogonapps.social.ui.rememberChat(interfaz)?.let { io.github.fogonapps.social.ui.ConversacionSocial(interfaz, it, navChat, destino.usuario) }
        "infochat" -> io.github.fogonapps.social.ui.rememberChat(interfaz)?.let { io.github.fogonapps.social.ui.InfoChatSocial(interfaz, it, destino.usuario) }
        "publicacion" -> PublicacionRed(interfaz, destino.id)
        "avisos" -> AvisosRed(interfaz)
        "buscar" -> io.github.fogonapps.social.ui.BuscarGente(interfaz, java.io.File(vm.perfiles.directorio(vm.idPerfil), "busquedas-gente.txt"), vm::cerrar)
        "guardados" -> GuardadosRed(interfaz)
        "moderacion" -> Moderacion(interfaz)
        "etiqueta" -> EtiquetaRed(interfaz, destino.usuario)
        "perfil" -> PerfilRed(vm, interfaz, destino.usuario)
        else -> TuGente(vm, interfaz, conAtras = true)
    }
}

/** Las metas en porcentaje que la persona decidió mostrar. */
@Composable
fun MetasRed(partes: Map<String, JsonElement>) {
    val metas = metasDeParte(partes[PARTE_METAS])
    if (metas.isEmpty()) return
    val f = FogonTema.actual
    Seccion(stringResource(R.string.red_metas_porcentaje))
    metas.forEach { (nombre, porcentaje) ->
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().vidrio().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row { Text(nombre, color = f.texto, modifier = Modifier.weight(1f)); Text("$porcentaje %", color = f.acentoTexto) }
            Barra(porcentaje / 100f, f.tinta2)
        }
    }
}

@Composable
private fun PerfilRed(vm: AhorradorViewModel, interfaz: InterfazSocial, usuario: String) {
    val perfiles by vm.red.perfiles.collectAsStateWithLifecycle()
    val yo by vm.red.usuario.collectAsStateWithLifecycle()
    LaunchedEffect(usuario) { vm.red.verPerfil(usuario) }
    val perfil = perfiles[usuario]
    Cabecera(usuario, vm::cerrar)
    if (perfil == null) return
    val secciones = listOf(SeccionPerfil(0, IconosFogon.Estrella, stringResource(R.string.red_logros)) {
        MetasRed(perfil.partes)
        val logros = perfil.publicaciones.filter { it.tipo == TIPO_LOGRO }
        if (logros.isEmpty()) Pista(stringResource(R.string.red_sin_publicaciones), Modifier.padding(16.dp))
        logros.forEach { TarjetaPublicacion(interfaz, it) }
    })
    val vista = io.github.fogonapps.perfil.PerfilVista(perfil.persona.nombre, perfil.persona.usuario,
        "", perfil.portada, perfil.bio, perfil.enlaces, perfil.persona.privada, perfil.relacion,
        contadores = listOf(io.github.fogonapps.perfil.ContadorPerfil(perfil.publicaciones.count { it.tipo == TIPO_LOGRO }.toString(), stringResource(R.string.red_logros))),
        secciones = secciones, insignia = perfil.persona.insignia)
    io.github.fogonapps.perfil.Perfil(vista.copy(tema = perfil.persona.tema, marco = perfil.persona.marco, contadores = io.github.fogonapps.social.ui.cifrasSociales(perfil) + vista.contadores), 0, {}, portada = { io.github.fogonapps.social.ui.PortadaPerfilRed(perfil.portada, Modifier.matchParentSize()) },
        foto = { io.github.fogonapps.social.ui.AvatarPerfilConHistorias(interfaz, perfil.persona) },
        bajoNombre = { Text("@${perfil.persona.usuario}", color = FogonTema.actual.textoSuave) },
        enlace = {}, acciones = {
            // Escribirle (el chat cifrado, igual en las cuatro apps).
            if (yo != null && perfil.persona.usuario != yo) io.github.fogonapps.perfil.BotonPerfil(stringResource(io.github.fogonapps.social.ui.R.string.social_chat_mensaje), { vm.abrir(Pantalla.Social("chat", usuario = perfil.persona.usuario)) }, Modifier.weight(1f))
            if (perfil.persona.usuario != yo) io.github.fogonapps.perfil.BotonPerfil(stringResource(
                if (perfil.relacion == "sigo" && perfil.meSigue) io.github.fogonapps.social.ui.R.string.amigos_dejar else if (perfil.relacion == "sigo") R.string.red_dejar_seguir else R.string.red_seguir), {
                if (perfil.relacion == "sigo") interfaz.confirmar(interfaz.tx(io.github.fogonapps.social.ui.R.string.social_dejar_de_seguir), interfaz.tx(io.github.fogonapps.social.ui.R.string.social_dejar_de_seguir), { vm.red.dejarDeSeguir(usuario) }) else vm.red.seguir(usuario)
            }, Modifier.weight(1f))
        }, despuesAcciones = {
            Box(Modifier.padding(horizontal = 16.dp)) { io.github.fogonapps.social.ui.DestacadasPerfil(interfaz, perfil.persona.usuario) }
            // Su canción, sonando sola al entrar (si el ajuste lo deja).
            (perfil.partes["cancion"] as? kotlinx.serialization.json.JsonObject)?.let {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { io.github.fogonapps.social.ui.TarjetaCancionPerfil(it, sonar = perfil.persona.usuario != yo) }
            }
        })
}

/** «Tu gente»: Siguiendo y Explorar de La Alcancía; sección de la barra (X-07) y también se abre encima. */
@Composable
fun TuGente(vm: AhorradorViewModel, interfaz: InterfazSocial = interfazCuenta(vm), conAtras: Boolean = false) {
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    var pestana by rememberSaveable { mutableIntStateOf(1) }
    val feed by vm.red.feed.collectAsStateWithLifecycle()
    val explorar by vm.red.explorar.collectAsStateWithLifecycle()
    val ocultas by vm.red.ocultas.collectAsStateWithLifecycle()
    val sinLeer by vm.red.sinLeer.collectAsStateWithLifecycle()
    val cuenta by vm.red.cuenta.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.red.cargarFeed(); vm.red.cargarExplorar(); vm.red.revisarSinLeer() }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (conAtras) BotonIconoSocial(IconosSociales.Atras, stringResource(R.string.volver)) { vm.cerrar() }
        Text(stringResource(R.string.red_tu_gente), style = MaterialTheme.typography.headlineSmall, color = FogonTema.actual.texto, modifier = Modifier.weight(1f))
        if (usuario != null) ConCuentaSocial(sinLeer) {
            BotonIconoSocial(IconosFogon.Campana, stringResource(R.string.red_avisos), grande = true) { vm.abrir(Pantalla.Social("avisos")) }
        }
        if (usuario != null) BotonIconoSocial(IconosSociales.Enviar, stringResource(io.github.fogonapps.social.ui.R.string.social_chat_titulo), grande = true) { vm.abrir(Pantalla.Social("mensajes")) }
        if (usuario != null) BotonIconoSocial(io.github.fogonapps.social.ui.IconoBuscar, stringResource(io.github.fogonapps.social.ui.R.string.gente_buscar), grande = true) { vm.abrir(Pantalla.Social("buscar")) }
        if (!cuenta?.permisos.isNullOrEmpty()) BotonIconoSocial(IconosSociales.Bandera, stringResource(R.string.red_moderacion), grande = true) { vm.abrir(Pantalla.Social("moderacion")) }
    }
    if (usuario == null) BotonRiso(stringResource(R.string.red_entrar), { vm.abrir(Pantalla.CuentaRed) }, Modifier.padding(16.dp))
    CopiaCuenta(vm.red, soloSiFalta = true)
    if (usuario != null) io.github.fogonapps.social.ui.BarraHistorias(interfaz)
    PestanasFeedSocial(pestana, usuario, { pestana = it }, textoPublicar = stringResource(R.string.red_compartir_corto)) { vm.abrir(Pantalla.CompartirLogro()) }
    val palabrasOcultas = io.github.fogonapps.social.ui.palabrasOcultasRed()
    ListaFeedSocial((if (pestana == 0) (if (usuario == null) emptyList() else feed) else explorar)?.filter { it.id !in ocultas && !io.github.fogonapps.social.ui.PalabrasOcultasRed.lleva(palabrasOcultas, io.github.fogonapps.social.ui.PalabrasOcultasRed.textoDe(it)) }?.map { it to Unit }, pestana,
        vm.red::masFeed, stringResource(R.string.red_feed_pie)) { p, _ -> TarjetaPublicacion(interfaz, p) }
}

@Composable
fun MenuLogro(vm: AhorradorViewModel, interfaz: InterfazSocial, p: PublicacionSocial, cerrar: () -> Unit) {
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    val confirmar = LocalConfirmar.current
    if (usuario != p.autor.usuario) { MenuPublicacion(interfaz, p, "", cerrar); return }
    val borrar = stringResource(R.string.red_borrar)
    val privado = stringResource(R.string.red_privado)
    AlertDialog(onDismissRequest = cerrar, title = { Text(stringResource(R.string.red_publicado)) }, text = {
        Column {
            val clave = vm.red.origen(p)
            if (clave != null) TextButton({ cerrar(); vm.abrir(Pantalla.CompartirLogro(clave)) }) { Text(stringResource(R.string.red_actualizar)) }
            TextButton({ cerrar(); confirmar(privado, null) { if (clave != null) vm.red.retirar(clave, p.id) else vm.red.borrar(p.id) } }) { Text(privado) }
            TextButton({ cerrar(); confirmar(borrar, null) { vm.red.borrar(p.id) } }) { Text(borrar) }
        }
    }, confirmButton = { TextButton(cerrar) { Text(stringResource(R.string.volver)) } })
}

/** Compartir un logro: se elige de los que la persona tiene, con unas palabras y la visibilidad. */
@Composable
fun CompartirLogro(vm: AhorradorViewModel, e: Estado, claveInicial: String?) {
    val f = FogonTema.actual
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    val ocupada by vm.red.publicando.collectAsStateWithLifecycle()
    Cabecera(stringResource(R.string.red_compartir), vm::cerrar)
    if (usuario == null) { BotonRiso(stringResource(R.string.red_entrar), { vm.abrir(Pantalla.CuentaRed) }, Modifier.padding(16.dp)); return }
    val posibles = remember(e) { logrosPosibles(e, vm.L) }
    var clave by rememberSaveable { mutableStateOf(claveInicial ?: posibles.firstOrNull()?.clave) }
    val elegido = posibles.firstOrNull { it.clave == clave }
    val anterior = remember(clave) { clave?.let { vm.red.publicada(it) } }
    var texto by rememberSaveable(clave) { mutableStateOf(anterior?.datos?.get("texto")?.jsonPrimitive?.content.orEmpty()) }
    var seguidores by rememberSaveable(clave) { mutableStateOf(anterior?.visibilidad == "amigos") }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (posibles.isEmpty()) { Pista(stringResource(R.string.red_sin_logros)); return@Column }
        Pista(stringResource(R.string.red_aviso_logro))
        Text(stringResource(R.string.red_elegir_logro), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
        posibles.forEach { l ->
            val d = remember(l) { instantaneaLogro(l, "") }
            FilaVidrio({ clave = l.clave }) {
                Text(textoLogro(d), color = f.texto, modifier = Modifier.weight(1f))
                if (l.clave == clave) IconoRiso(Iconos.Chulo, null, tamano = 20.dp, tinta = f.acentoTexto)
            }
        }
        OutlinedTextField(texto, { texto = it.take(2000) }, Modifier.fillMaxWidth().heightIn(min = 100.dp),
            placeholder = { Text(stringResource(R.string.red_texto), color = f.textoSuave) },
            supportingText = { Text(stringResource(R.string.red_etiquetas_pista), color = f.textoSuave) }, shape = forma(20.dp))
        OpcionSocial(stringResource(R.string.red_seguidores), seguidores, { if (!ocupada) seguidores = it })
        val datos = elegido?.let { instantaneaLogro(it, texto) }
        if (datos != null) {
            Seccion(stringResource(R.string.red_vista_previa))
            Column(Modifier.fillMaxWidth().vidrio().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CuerpoLogro(datos)
                if (texto.isNotBlank()) Text(texto, color = f.texto)
            }
        }
        if (ocupada) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
        val cuando = rememberSaveable(clave) { anterior?.cuando ?: System.currentTimeMillis() }
        BotonRiso(stringResource(if (anterior == null) R.string.red_publicar else R.string.red_actualizar), {
            if (datos != null && elegido != null) vm.red.publicar(PublicacionSubida(elegido.clave, if (seguidores) "amigos" else "publica", cuando, TIPO_LOGRO, datos), vm::cerrar)
        }, Modifier.fillMaxWidth(), habilitado = datos != null && cabeLogro(datos) && !ocupada)
    }
    Espacio(40.dp)
}

/** «Quién lo ve» de las metas en porcentaje, al editar el perfil con cuenta. */
@Composable
fun PartesAlcancia(vm: AhorradorViewModel) {
    val f = FogonTema.actual
    val estado by vm.estado.collectAsStateWithLifecycle()
    val remoto by vm.red.identidad.collectAsStateWithLifecycle()
    val e = estado ?: return
    var vis by remember(remoto) { mutableStateOf(vm.red.visibilidad(PARTE_METAS)) }
    val opciones = listOf("privado" to stringResource(R.string.red_vis_privado), "publica" to stringResource(R.string.red_vis_publica),
        "amigos" to stringResource(R.string.red_vis_seguidores))
    Seccion(stringResource(R.string.red_quien_lo_ve))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.red_partes_pista), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        Column(Modifier.fillMaxWidth().vidrio().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.red_metas_porcentaje), style = MaterialTheme.typography.bodyLarge, color = f.texto)
            Selector(opciones, vis, { vis = it })
        }
        BotonRiso(stringResource(io.github.fogonapps.social.ui.R.string.social_guardar), {
            vm.red.publicarPartes(if (vis == "privado") emptyMap() else mapOf(PARTE_METAS to ParteSubida(vis, parteMetas(e, vm.L))))
        }, Modifier.fillMaxWidth())
    }
}

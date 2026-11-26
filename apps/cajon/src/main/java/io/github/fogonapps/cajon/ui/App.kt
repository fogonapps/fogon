package io.github.fogonapps.cajon.ui

import io.github.fogonapps.identidad.AvisoApoyo
import io.github.fogonapps.identidad.destinoGuia
import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.Pantalla
import io.github.fogonapps.cajon.Pestana
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.Tipos
import io.github.fogonapps.identidad.AlmacenApariencia
import io.github.fogonapps.identidad.Dibujo
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.FormaPildora
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.ManifiestoFogon
import io.github.fogonapps.identidad.Paleta
import io.github.fogonapps.identidad.PantallaApariencia
import io.github.fogonapps.identidad.PestanaAnimada
import io.github.fogonapps.identidad.PilaAnimada
import io.github.fogonapps.identidad.fondoFogon
import io.github.fogonapps.identidad.rebote
import io.github.fogonapps.identidad.R as RId

/** Pide confirmación: (título, texto opcional, acción si dice que sí). */
typealias Confirmar = (String, String?, () -> Unit) -> Unit

val LocalConfirmar = staticCompositionLocalOf<Confirmar> { { _, _, si -> si() } }

/** Lo que solo puede hacer la actividad: elegir archivos y pasar una imagen a otra app. */
data class Acciones(
    val exportar: () -> Unit,
    val importar: () -> Unit,
    val deOtraApp: () -> Unit,
    val compartir: (Bitmap, String) -> Unit,
    /** Elegir una imagen del teléfono (para la portada). */
    val elegirImagen: ((android.net.Uri) -> Unit) -> Unit = {},
)

val LocalAcciones = staticCompositionLocalOf<Acciones?> { null }

private data class Pregunta(val titulo: String, val texto: String?, val si: () -> Unit)

@Composable
fun App(vm: CajonViewModel, acciones: Acciones) {
    PoliticaPrivacidad(vm)
    val e by vm.estado.collectAsStateWithLifecycle()
    val pila by vm.pila.collectAsStateWithLifecycle()
    val pestana by vm.pestana.collectAsStateWithLifecycle()
    val yoEnLaRed by vm.red.usuario.collectAsStateWithLifecycle()
    val equipoBloqueado by vm.red.equipoBloqueado.collectAsStateWithLifecycle()
    LaunchedEffect(equipoBloqueado) {
        if (equipoBloqueado && pila.lastOrNull() != Pantalla.Moderacion) vm.abrir(Pantalla.Moderacion)
    }
    // Con cuenta en la red, se pide una vez el permiso de notificaciones (Android 13+): sin él no llegan avisos ni mensajes.
    val ctxPermiso = androidx.compose.ui.platform.LocalContext.current
    val pedirPermiso = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {
        io.github.fogonapps.cajon.AvisosRed.programar(ctxPermiso)
    }
    val redActiva by vm.red.permitida.collectAsStateWithLifecycle()
    LaunchedEffect(yoEnLaRed, redActiva) {
        if (!redActiva || yoEnLaRed == null || android.os.Build.VERSION.SDK_INT < 33) return@LaunchedEffect
        val ya = androidx.core.content.ContextCompat.checkSelfPermission(ctxPermiso, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val prefs = ctxPermiso.getSharedPreferences("permisos", android.content.Context.MODE_PRIVATE)
        if (!ya && !prefs.getBoolean("notificaciones_pedido", false)) {
            prefs.edit().putBoolean("notificaciones_pedido", true).apply()
            pedirPermiso.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val f = FogonTema.actual
    val snack = remember { SnackbarHostState() }
    var pregunta by remember { mutableStateOf<Pregunta?>(null) }

    LaunchedEffect(Unit) { vm.mensajes.collect { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(it) } }

    val estado = e
    if (estado == null) {
        val cargando = stringResource(R.string.cargando)
        Box(Modifier.fillMaxSize().background(f.fondo), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.app_name).lowercase(), fontFamily = f.titulos, fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                fontSize = 48.sp, color = f.texto, modifier = Modifier.clearAndSetSemantics { contentDescription = cargando })
        }
        return
    }

    val vuelos = remember { Vuelos() }
    CompositionLocalProvider(LocalConfirmar provides { t, x, si -> pregunta = Pregunta(t, x, si) }, LocalAcciones provides acciones,
        LocalVuelos provides vuelos.takeIf { !f.menosMovimiento }) {
        // Al ganar una insignia nueva, su talón entra con un resorte (dentro de las acciones, para poder compartirla).
        CelebracionInsignias(estado)
        val guia = io.github.fogonapps.identidad.rememberGuia("cajon")
        val contadores = io.github.fogonapps.social.ui.contadoresDeLaApp()
        io.github.fogonapps.social.ui.ContarEntrada(when (pila.lastOrNull()) {
            Pantalla.Ajustes -> "ajustes.abrir"
            Pantalla.Perfil -> "perfil.abrir"
            Pantalla.Reporte -> "reporte.abrir"
            Pantalla.Red -> "cajon.pantalla.gente"
            is Pantalla.Buscar -> "cajon.pantalla.buscar"
            is Pantalla.Ficha -> "cajon.pantalla.ficha"
            is Pantalla.Previa -> "cajon.pantalla.previa"
            Pantalla.Listas -> "cajon.pantalla.listas"
            Pantalla.ListasComunes -> "cajon.pantalla.listas_comunes"
            Pantalla.Diario -> "cajon.pantalla.diario"
            Pantalla.Cifras -> "cajon.pantalla.cifras"
            Pantalla.Artistas -> "cajon.pantalla.artistas"
            Pantalla.Retos -> "cajon.pantalla.retos"
            Pantalla.TusGustos -> "cajon.pantalla.gustos"
            Pantalla.MusicaEscuchada -> "cajon.pantalla.escuchas"
            Pantalla.OidosPendientes -> "cajon.pantalla.escuchas_pendientes"
            Pantalla.AvisosRed -> "cajon.pantalla.avisos"
            null -> "cajon.pantalla." + pestana.name.lowercase()
            else -> null
        })
        io.github.fogonapps.identidad.ConGuia(guia, pasosGuia(), disponible = estado.listo && pila.isEmpty() && pestana == Pestana.INICIO, alTerminar = { completa -> contadores.contar(if (completa) "guia.terminada" else "guia.omitida") }) {
        Box(Modifier.fillMaxSize().fondoFogon(f).anotarToques(vuelos)) {
            // Con una pantalla encima, lo de abajo queda tapado: tampoco lo lee el lector de pantalla.
            Box(Modifier.fillMaxSize().then(if (pila.isNotEmpty()) Modifier.clearAndSetSemantics {} else Modifier)) {
                if (!estado.listo) {
                    Tutorial(vm, acciones)
                    // Los avisos también se ven en el tutorial (errores al entrar o crear la cuenta).
                    SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp)) { Aviso(it.visuals.message) }
                } else {
                    // Cada pestaña con su propio scroll (al cambiar, empieza arriba).
                    PestanaAnimada(pestana) { p ->
                        // X-09: deslizar hacia abajo recarga el feed (Para ti) o arma otras recomendaciones (Inicio).
                        val recargar: (suspend () -> Unit)? = when (p) {
                            Pestana.DESCUBRIR -> ({ vm.recargarRecomendaciones() })
                            Pestana.GENTE -> ({ vm.red.recargarFeed() })
                            else -> null
                        }
                        io.github.fogonapps.identidad.DeslizarParaRecargar(recargar, Modifier.fillMaxSize().statusBarsPadding()) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            AvisoPolitica(vm)
                            when (p) {
                                Pestana.INICIO -> {
                                    Inicio(vm, estado)
                                    if (pila.isEmpty()) {
                                        AvisoApoyo(stringResource(R.string.app_name), enTutorial = guia.activo)
                                    }
                                }
                                Pestana.LO_TUYO -> LoTuyo(vm, estado)
                                Pestana.DESCUBRIR -> Descubrir(vm, estado)
                                Pestana.GENTE -> PantallaRed(vm, estado, enPestana = true)
                            }
                            // Lo último no queda bajo la barra flotante, tampoco con la navegación de tres botones.
                            androidx.compose.foundation.layout.Spacer(Modifier.navigationBarsPadding().height(120.dp))
                        }
                        }
                    }
                    // Debajo de la barra, el papel se funde hacia abajo: lo que pasa por detrás no se lee a medias.
                    if (pila.isEmpty()) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().height(96.dp)
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(FogonTema.actual.fondo.copy(alpha = 0f), FogonTema.actual.fondo.copy(alpha = 0.92f)))))
                    if (pila.isEmpty()) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsBottomHeight(androidx.compose.foundation.layout.WindowInsets.Companion.navigationBars).background(FogonTema.actual.fondo.copy(alpha = 0.92f)))
                    if (pila.isEmpty()) BarraFlotante(pestana, Pestana.entries, vm::irA, { vm.abrir(agregarDesde(pestana, vm.seccionLoTuyo.value)) }, Modifier.align(Alignment.BottomCenter))
                    SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 92.dp)) { Aviso(it.visuals.message) }
                }
            }

            // Pantallas encima de las pestañas
            PilaAnimada(pila.lastOrNull(), pila.size, entraSuave = { it is Pantalla.Ficha && vuelos.hayTocada() }) { p ->
                Box(Modifier.fillMaxSize().fondoFogon(f).safeDrawingPadding().imePadding()) {
                    // Las que tienen lista propia (buscar, el cajón de una lista larga) manejan su scroll.
                    val conScroll = p !is Pantalla.Buscar && p !is Pantalla.Conversacion && p != Pantalla.OidosPendientes && p !is Pantalla.Resumen
                    Column(Modifier.fillMaxSize().then(if (conScroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
                        AvisoPolitica(vm)
                        when (p) {
                            is Pantalla.Buscar -> Buscar(vm, estado, p)
                            is Pantalla.Previa -> Previa(vm, p.obra, p.personas)
                            is Pantalla.Ficha -> Ficha(vm, estado, p.id)
                            is Pantalla.CambiarPortada -> CambiarPortada(vm, estado, p.id, p.que)
                            is Pantalla.EditarObra -> EditarObra(vm, estado, p)
                            is Pantalla.Registrar -> Registrar(vm, estado, p)
                            is Pantalla.VerArtista -> VerArtista(vm, estado, p.id)
                            is Pantalla.PreviaArtista -> PreviaArtista(vm, estado, p)
                            is Pantalla.VerLista -> VerLista(vm, estado, p.id)
                            is Pantalla.EditarLista -> EditarLista(vm, estado, p.id)
                            is Pantalla.AgregarALista -> AgregarALista(vm, estado, p.obra)
                            Pantalla.OidosPendientes -> OidosPendientes(vm)
                            Pantalla.Diario -> Diario(vm, estado)
                            Pantalla.Listas -> Listas(vm, estado)
                            Pantalla.ListasComunes -> ListasComunes(vm)
                            is Pantalla.ListaComun -> ListaComun(vm, estado, p.id)
                            is Pantalla.Juntos -> Juntos(vm, estado, p.usuario)
                            Pantalla.Cifras -> Tu(vm, estado)
                            is Pantalla.Resumen -> Resumen(vm, estado, p.anio)
                            is Pantalla.CifrasDe -> Tu(vm, estado, p.seccion)
                            Pantalla.Artistas -> Artistas(vm, estado)
                            Pantalla.Retos -> Retos(vm, estado)
                            Pantalla.MusicaEscuchada -> { Cabecera(stringResource(R.string.musica_que_escuchas), vm::cerrar); MusicaQueEscuchas(vm) }
                            Pantalla.Privacidad -> Transparencia(vm)
                            Pantalla.PrivacidadPerfil -> PrivacidadPerfil(vm, estado)
                            Pantalla.BorrarCuenta -> FormularioBorrado(vm, vm::cerrar)
                            Pantalla.Ajustes -> Ajustes(vm, estado)
                            Pantalla.ConectarSpotify -> ConectarSpotify(vm)
                            Pantalla.AcercaDe -> AcercaDe(vm)
                            Pantalla.Reporte -> Reporte(vm)
                            Pantalla.ElegirGustos -> ElegirGustos(vm, estado)
                            Pantalla.TusGustos -> TusGustos(vm, estado)
                            Pantalla.Perfil -> Perfil(vm, estado)
                            Pantalla.EditarPerfil -> EditarPerfil(vm, estado)
                            is Pantalla.ElegirDestacado -> ElegirDestacado(vm, estado, p.hueco)
                            Pantalla.PortadaPerfil -> PortadaPerfil(vm, estado)
                            is Pantalla.AjustarImagenPerfil -> AjustarImagenPerfil(vm, estado, p.portada)
                            Pantalla.Sesiones -> Sesiones(vm)
                            Pantalla.Apariencia -> Column {
                                Cabecera(stringResource(RId.string.fogon_ap_titulo), vm::cerrar)
                                PantallaApariencia(Paleta.Cajon, AlmacenApariencia.de(LocalContext.current), alCambiarTema = { contadores.contar("apariencia.cambiar_tema") })
                            }
                            Pantalla.Manifiesto -> Column { Cabecera(stringResource(R.string.manifiesto), vm::cerrar); ManifiestoFogon() }
                            Pantalla.Licencias -> Licencias(vm)
                            Pantalla.CuentaRed -> CuentaRed(vm, estado)
                            Pantalla.Red -> PantallaRed(vm, estado)
                            // Tu propio perfil es uno solo, aunque se llegue desde la red.
                            is Pantalla.PerfilRed -> if (p.usuario == yoEnLaRed) Perfil(vm, estado) else PerfilRed(vm, p.usuario)
                            is Pantalla.PublicacionRed -> PublicacionRed(vm, p.id)
                            Pantalla.AvisosRed -> AvisosRed(vm)
                            Pantalla.GuardadosRed -> GuardadosRed(vm)
                            is Pantalla.GenteRed -> GenteRed(vm, p.usuario, p.seguidores)
                            Pantalla.BloqueadosRed -> BloqueadosRed(vm)
                            is Pantalla.Publicar -> interfazSocial(vm).contenido.Compositor("foto", p.obra?.let { io.github.fogonapps.social.ui.AdjuntoSocial("obra", it) })
                            is Pantalla.Resenar -> EscribirResena(vm, estado, p.id, p.elegirObra)
                            is Pantalla.Chats -> Chats(vm, estado, p.obra)
                            is Pantalla.Conversacion -> Conversacion(vm, estado, p.usuario)
                            is Pantalla.InfoChat -> InfoChat(vm, estado, p.usuario)
                            Pantalla.ElegirCancion -> ElegirCancion(vm, estado)
                            Pantalla.AjustesAvisos -> AjustesAvisos(vm)
                            Pantalla.PalabrasOcultas -> PalabrasOcultas(vm, estado)
                            Pantalla.EstadoCuenta -> EstadoCuenta(vm)
                            Pantalla.CajonEnCuenta -> CajonEnCuenta(vm)
                            Pantalla.Moderacion -> Moderacion(vm)
                            is Pantalla.EtiquetaRed -> EtiquetaRed(vm, p.etiqueta)
                        }
                        if (conScroll) Espacio(40.dp)
                    }
                    SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) { Aviso(it.visuals.message) }
                }
            }

            val borrarEl by vm.red.borrarEl.collectAsStateWithLifecycle()
            io.github.fogonapps.social.ui.GraciaBorrado(borrarEl) { vm.red.decidirBorrado(it) }
            pregunta?.let { q ->
                AlertDialog(
                    onDismissRequest = { pregunta = null },
                    title = { Text(q.titulo) },
                    text = q.texto?.let { { Text(it) } },
                    confirmButton = { TextButton({ pregunta = null; q.si() }) { Text(stringResource(R.string.si_continuar), fontWeight = FontWeight.Bold) } },
                    dismissButton = { TextButton({ pregunta = null }) { Text(stringResource(R.string.volver)) } },
                )
            }
            // La portada que vuela hasta la ficha, encima de todo.
            CapaVuelo(vuelos)
        }
        }
    }
}

/** El ícono y el nombre de cada pestaña. */
fun iconoPestana(p: Pestana): Dibujo = when (p) {
    Pestana.INICIO -> Iconos.Inicio; Pestana.LO_TUYO -> Iconos.Cajon; Pestana.DESCUBRIR -> Iconos.Buscar; Pestana.GENTE -> Iconos.Gente
}
fun textoPestana(p: Pestana): Int = when (p) {
    Pestana.INICIO -> R.string.pestana_inicio; Pestana.LO_TUYO -> R.string.pestana_lo_tuyo
    Pestana.DESCUBRIR -> R.string.pestana_descubrir; Pestana.GENTE -> R.string.red_titulo
}

/** Lo que abre el + de la barra: el buscador, en el apartado que se mira en «Lo tuyo» si se está ahí. */
fun agregarDesde(p: Pestana, seccion: String = io.github.fogonapps.cajon.Secciones.TODO): Pantalla =
    Pantalla.Buscar(if (p == Pestana.LO_TUYO) io.github.fogonapps.cajon.Secciones.paraBuscar(seccion) else io.github.fogonapps.cajon.Secciones.TODO)

/**
 * La barra de pestañas: una píldora que flota, con las pestañas en el orden elegido y el botón de agregar al final.
 * Con el dedo apretado se desliza por la barra y se cambia de pestaña al pasar por encima de cada una.
 */
@Composable
private fun BarraFlotante(actual: Pestana, pestanas: List<Pestana>, onIr: (Pestana) -> Unit, onAgregar: () -> Unit, modifier: Modifier = Modifier) {
    val f = FogonTema.actual
    val haptica = LocalHapticFeedback.current
    // Dónde quedó cada pestaña dentro de la barra (para saber cuál está bajo el dedo).
    val bordes = remember { mutableStateMapOf<Pestana, ClosedFloatingPointRange<Float>>() }
    val alIr by rememberUpdatedState(onIr)
    val alActual by rememberUpdatedState(actual)
    Row(
        modifier.navigationBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth()
            // Papel casi opaco con una sombra suave, no una píldora gris: flota sobre lo que pasa por detrás.
            .shadow(18.dp, FormaPildora, clip = false, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
            .clip(FormaPildora).background(f.fondo.copy(alpha = 0.94f)).border(1.dp, f.texto.copy(alpha = 0.08f), FormaPildora).padding(8.dp)
            .pointerInput(Unit) {
                var ultima: Pestana? = null
                fun bajo(x: Float) = bordes.entries.firstOrNull { x in it.value }?.key
                detectDragGesturesAfterLongPress(
                    onDragStart = { o -> ultima = alActual; bajo(o.x)?.let { if (it != ultima) { ultima = it; alIr(it) } }; haptica.performHapticFeedback(HapticFeedbackType.LongPress) },
                    onDrag = { c, _ -> bajo(c.position.x)?.let { if (it != ultima) { ultima = it; haptica.performHapticFeedback(HapticFeedbackType.TextHandleMove); alIr(it) } } },
                )
            },
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        pestanas.forEach { pe ->
            Box(Modifier.destinoGuia("pestana_${pe.name}").onGloballyPositioned { c -> val x = c.positionInParent().x; bordes[pe] = x..(x + c.size.width) }) {
                BotonPestana(iconoPestana(pe), stringResource(textoPestana(pe)), pe == actual) { onIr(pe) }
            }
        }
        val agregar = stringResource(R.string.buscar_y_agregar)
        Box(
            Modifier.destinoGuia("agregar").rebote(0.9f).size(50.dp).clip(CircleShape).background(f.tinta1)
                .clickable(onClick = onAgregar).semantics { contentDescription = agregar; role = Role.Button },
            contentAlignment = Alignment.Center,
        ) { IconoRiso(Iconos.Mas, null, tamano = 24.dp, tinta = f.sobreTinta1) }
    }
}

/** Una pestaña: solo el ícono; la elegida, en una píldora más clara con el ícono en la primera tinta. */
@Composable
private fun BotonPestana(icono: Dibujo, texto: String, elegida: Boolean, onClick: () -> Unit) {
    val f = FogonTema.actual
    // La elegida: el ícono en tinta y lleno, con un punto debajo; las otras, en gris de línea.
    val punto by androidx.compose.animation.core.animateFloatAsState(if (elegida) 1f else 0f, androidx.compose.animation.core.spring(0.6f, 500f), label = "punto")
    Box(
        Modifier.rebote(0.9f).size(48.dp).clip(CircleShape)
            .clickable(onClick = onClick).semantics { contentDescription = texto; selected = elegida; role = Role.Tab },
        contentAlignment = Alignment.Center,
    ) {
        IconoRiso(icono, null, tamano = 24.dp, tinta = if (elegida) f.texto else f.textoSuave, relleno = if (elegida) f.texto.copy(alpha = 0.12f) else null,
            modifier = Modifier.padding(bottom = 4.dp))
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(4.dp).graphicsLayer { scaleX = punto; scaleY = punto; alpha = punto }
            .clip(CircleShape).background(f.texto))
    }
}

/** Aviso con la identidad: una píldora. */
@Composable
private fun Aviso(mensaje: String) {
    val f = FogonTema.actual
    Row(Modifier.fillMaxWidth().padding(12.dp).clip(FormaPildora).background(f.pildora).padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(mensaje, color = f.sobrePildora)
    }
}

/** El recorrido guiado de El Cajón: agregar, las secciones de la barra y el perfil. */
@Composable
private fun pasosGuia(): List<io.github.fogonapps.identidad.PasoGuia> = listOf(
    "agregar" to (R.string.guia_agregar to R.string.guia_agregar_texto),
    "pestana_GENTE" to (R.string.guia_para_ti to R.string.guia_para_ti_texto),
    "pestana_LO_TUYO" to (R.string.guia_secciones to R.string.guia_secciones_texto),
    "perfil" to (R.string.guia_perfil to R.string.guia_perfil_texto),
).map { (d, tx) -> io.github.fogonapps.identidad.PasoGuia(d, stringResource(tx.first), stringResource(tx.second)) }

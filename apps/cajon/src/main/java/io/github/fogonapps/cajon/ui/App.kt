package io.github.fogonapps.cajon.ui

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import io.github.fogonapps.identidad.Personaje
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
    val e by vm.estado.collectAsStateWithLifecycle()
    val pila by vm.pila.collectAsStateWithLifecycle()
    val pestana by vm.pestana.collectAsStateWithLifecycle()
    val yoEnLaRed by vm.red.usuario.collectAsStateWithLifecycle()
    // Con cuenta en la red, se pide una vez el permiso de notificaciones (Android 13+): sin él no llegan avisos ni mensajes.
    val ctxPermiso = androidx.compose.ui.platform.LocalContext.current
    val pedirPermiso = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {
        io.github.fogonapps.cajon.AvisosRed.programar(ctxPermiso)
    }
    LaunchedEffect(yoEnLaRed) {
        if (yoEnLaRed == null || android.os.Build.VERSION.SDK_INT < 33) return@LaunchedEffect
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
        Box(Modifier.fillMaxSize().background(f.fondo), contentAlignment = Alignment.Center) {
            Personaje(io.github.fogonapps.identidad.Quien.CAJON, stringResource(R.string.cargando), tamano = 90.dp)
        }
        return
    }

    CompositionLocalProvider(LocalConfirmar provides { t, x, si -> pregunta = Pregunta(t, x, si) }, LocalAcciones provides acciones) {
        Box(Modifier.fillMaxSize().fondoFogon(f)) {
            if (!estado.listo) {
                Tutorial(vm, acciones)
            } else {
                // Cada pestaña con su propio scroll (al cambiar, empieza arriba).
                PestanaAnimada(pestana) { p ->
                    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
                        when (p) {
                            Pestana.INICIO -> Inicio(vm, estado)
                            Pestana.PARA_TI -> PantallaRed(vm, estado, enPestana = true)
                            Pestana.CINE -> Apartado(vm, estado, io.github.fogonapps.cajon.Secciones.CINE)
                            Pestana.MUSICA -> Apartado(vm, estado, io.github.fogonapps.cajon.Secciones.MUSICA)
                            Pestana.LIBROS -> Apartado(vm, estado, io.github.fogonapps.cajon.Secciones.LIBROS)
                        }
                        Espacio(120.dp)
                    }
                }
                BarraFlotante(pestana, Pestana.enOrden(estado.ajustes.ordenPestanas), vm::irA, { vm.abrir(Pantalla.Buscar(io.github.fogonapps.cajon.Secciones.dePestana(pestana))) }, Modifier.align(Alignment.BottomCenter))
                SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 92.dp)) { Aviso(it.visuals.message) }
            }

            // Pantallas encima de las pestañas
            PilaAnimada(pila.lastOrNull(), pila.size) { p ->
                Box(Modifier.fillMaxSize().fondoFogon(f).safeDrawingPadding().imePadding()) {
                    // Las que tienen lista propia (buscar, el cajón de una lista larga) manejan su scroll.
                    val conScroll = p !is Pantalla.Buscar && p !is Pantalla.Conversacion
                    Column(Modifier.fillMaxSize().then(if (conScroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
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
                            Pantalla.Diario -> Diario(vm, estado)
                            Pantalla.Listas -> Listas(vm, estado)
                            Pantalla.Cifras -> Tu(vm, estado)
                            is Pantalla.CifrasDe -> Tu(vm, estado, p.seccion)
                            Pantalla.Artistas -> Artistas(vm, estado)
                            Pantalla.Retos -> Retos(vm, estado)
                            Pantalla.Ajustes -> Ajustes(vm, estado)
                            Pantalla.Sincronizar -> Sincronizar(vm)
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
                            Pantalla.Apariencia -> Column {
                                Cabecera(stringResource(RId.string.fogon_ap_titulo), vm::cerrar)
                                PantallaApariencia(Paleta.Cajon, AlmacenApariencia.de(LocalContext.current))
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
                            is Pantalla.Publicar -> Publicar(vm, estado, p.obra)
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

            pregunta?.let { q ->
                AlertDialog(
                    onDismissRequest = { pregunta = null },
                    title = { Text(q.titulo) },
                    text = q.texto?.let { { Text(it) } },
                    confirmButton = { TextButton({ pregunta = null; q.si() }) { Text(stringResource(R.string.si_continuar), fontWeight = FontWeight.Bold) } },
                    dismissButton = { TextButton({ pregunta = null }) { Text(stringResource(R.string.volver)) } },
                )
            }
        }
    }
}

/** El ícono y el nombre de cada pestaña. */
fun iconoPestana(p: Pestana): Dibujo = when (p) {
    Pestana.INICIO -> Iconos.Inicio; Pestana.PARA_TI -> Iconos.Gente; Pestana.CINE -> Iconos.Pelicula
    Pestana.MUSICA -> Iconos.Disco; Pestana.LIBROS -> Iconos.Libro
}
fun textoPestana(p: Pestana): Int = when (p) {
    Pestana.INICIO -> R.string.pestana_inicio; Pestana.PARA_TI -> R.string.red_titulo; Pestana.CINE -> R.string.seccion_cine
    Pestana.MUSICA -> R.string.seccion_musica; Pestana.LIBROS -> R.string.seccion_libros
}

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
            .clip(FormaPildora).background(f.barra).border(f.borde, f.linea, FormaPildora).padding(8.dp)
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
            Box(Modifier.onGloballyPositioned { c -> val x = c.positionInParent().x; bordes[pe] = x..(x + c.size.width) }) {
                BotonPestana(iconoPestana(pe), stringResource(textoPestana(pe)), pe == actual) { onIr(pe) }
            }
        }
        // Cinco pestañas y el +: se apretan un poco para que quepan en un teléfono angosto.
        val agregar = stringResource(R.string.buscar_y_agregar)
        Box(
            Modifier.rebote(0.9f).size(50.dp).clip(CircleShape).background(f.tinta1)
                .clickable(onClick = onAgregar).semantics { contentDescription = agregar; role = Role.Button },
            contentAlignment = Alignment.Center,
        ) { IconoRiso(Iconos.Mas, null, tamano = 24.dp, tinta = f.sobreTinta1) }
    }
}

/** Una pestaña: solo el ícono; la elegida, en una píldora más clara con el ícono en la primera tinta. */
@Composable
private fun BotonPestana(icono: Dibujo, texto: String, elegida: Boolean, onClick: () -> Unit) {
    val f = FogonTema.actual
    val fondo by animateColorAsState(if (elegida) f.tinta1.copy(alpha = 0.16f) else Color.Transparent, label = "pestana")
    Box(
        Modifier.rebote(0.9f).size(44.dp).clip(CircleShape).background(fondo)
            .clickable(onClick = onClick).semantics { contentDescription = texto; selected = elegida; role = Role.Tab },
        contentAlignment = Alignment.Center,
    ) { IconoRiso(icono, null, tamano = 22.dp, tinta = if (elegida) f.acentoTexto else f.textoSuave) }
}

/** Aviso con la identidad: una píldora. */
@Composable
private fun Aviso(mensaje: String) {
    val f = FogonTema.actual
    Row(Modifier.fillMaxWidth().padding(12.dp).clip(FormaPildora).background(f.pildora).padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(mensaje, color = f.sobrePildora)
    }
}

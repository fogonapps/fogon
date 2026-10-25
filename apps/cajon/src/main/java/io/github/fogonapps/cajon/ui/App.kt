package io.github.fogonapps.cajon.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
)

val LocalAcciones = staticCompositionLocalOf<Acciones?> { null }

private data class Pregunta(val titulo: String, val texto: String?, val si: () -> Unit)

@Composable
fun App(vm: CajonViewModel, acciones: Acciones) {
    val e by vm.estado.collectAsStateWithLifecycle()
    val pila by vm.pila.collectAsStateWithLifecycle()
    val pestana by vm.pestana.collectAsStateWithLifecycle()
    val f = FogonTema.actual
    val snack = remember { SnackbarHostState() }
    var pregunta by remember { mutableStateOf<Pregunta?>(null) }

    LaunchedEffect(Unit) { vm.mensajes.collect { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(it) } }

    val estado = e
    if (estado == null) {
        Box(Modifier.fillMaxSize().background(f.fondo), contentAlignment = Alignment.Center) {
            Personaje(RId.drawable.personaje_el_cajon, stringResource(R.string.cargando), tamano = 140.dp)
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
                            Pestana.CAJON -> Cajon(vm, estado)
                            Pestana.LISTAS -> Listas(vm, estado)
                            Pestana.TU -> Tu(vm, estado)
                        }
                        Espacio(120.dp)
                    }
                }
                BarraFlotante(pestana, vm::irA, { vm.abrir(Pantalla.Buscar(Tipos.PELICULA)) }, Modifier.align(Alignment.BottomCenter))
                SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 92.dp)) { Aviso(it.visuals.message) }
            }

            // Pantallas encima de las pestañas
            PilaAnimada(pila.lastOrNull(), pila.size) { p ->
                Box(Modifier.fillMaxSize().fondoFogon(f).safeDrawingPadding().imePadding()) {
                    // Las que tienen lista propia (buscar, el cajón de una lista larga) manejan su scroll.
                    val conScroll = p !is Pantalla.Buscar
                    Column(Modifier.fillMaxSize().then(if (conScroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
                        when (p) {
                            is Pantalla.Buscar -> Buscar(vm, estado, p)
                            is Pantalla.Previa -> Previa(vm, p.obra)
                            is Pantalla.Ficha -> Ficha(vm, estado, p.id)
                            is Pantalla.EditarObra -> EditarObra(vm, estado, p)
                            is Pantalla.Registrar -> Registrar(vm, estado, p)
                            is Pantalla.VerArtista -> VerArtista(vm, estado, p.id)
                            is Pantalla.PreviaArtista -> PreviaArtista(vm, p)
                            is Pantalla.VerLista -> VerLista(vm, estado, p.id)
                            is Pantalla.EditarLista -> EditarLista(vm, estado, p.id)
                            is Pantalla.AgregarALista -> AgregarALista(vm, estado, p.obra)
                            Pantalla.Diario -> Diario(vm, estado)
                            Pantalla.Artistas -> Artistas(vm, estado)
                            Pantalla.Retos -> Retos(vm, estado)
                            Pantalla.Ajustes -> Ajustes(vm, estado)
                            Pantalla.Sincronizar -> Sincronizar(vm)
                            Pantalla.ClaveTmdb -> ClaveTmdb(vm)
                            Pantalla.AcercaDe -> AcercaDe(vm)
                            Pantalla.Apariencia -> Column {
                                Cabecera(stringResource(RId.string.fogon_ap_titulo), vm::cerrar)
                                PantallaApariencia(Paleta.Cajon, AlmacenApariencia.de(LocalContext.current))
                            }
                            Pantalla.Manifiesto -> Column { Cabecera(stringResource(R.string.manifiesto), vm::cerrar); ManifiestoFogon() }
                            Pantalla.Licencias -> Licencias(vm)
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

/** La barra de pestañas: una píldora que flota, con el botón de agregar en el centro. */
@Composable
private fun BarraFlotante(actual: Pestana, onIr: (Pestana) -> Unit, onAgregar: () -> Unit, modifier: Modifier = Modifier) {
    val f = FogonTema.actual
    val pestanas = listOf(
        Triple(Pestana.INICIO, Iconos.Inicio, R.string.pestana_inicio),
        Triple(Pestana.CAJON, Iconos.Cajon, R.string.pestana_cajon),
        Triple(Pestana.LISTAS, Iconos.Lista, R.string.pestana_listas),
        Triple(Pestana.TU, Iconos.Cifras, R.string.pestana_tu),
    )
    Row(
        modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth()
            .clip(FormaPildora).background(f.barra).border(f.borde, f.linea, FormaPildora).padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        pestanas.take(2).forEach { (pe, ic, t) -> BotonPestana(ic, stringResource(t), pe == actual) { onIr(pe) } }
        val agregar = stringResource(R.string.agregar)
        Box(
            Modifier.rebote(0.9f).size(52.dp).clip(CircleShape).background(f.tinta1)
                .clickable(onClick = onAgregar).semantics { contentDescription = agregar; role = Role.Button },
            contentAlignment = Alignment.Center,
        ) { IconoRiso(Iconos.Mas, null, tamano = 24.dp, tinta = f.sobreTinta1) }
        pestanas.drop(2).forEach { (pe, ic, t) -> BotonPestana(ic, stringResource(t), pe == actual) { onIr(pe) } }
    }
}

/** Una pestaña: solo el ícono; la elegida, en una píldora más clara con el ícono en la primera tinta. */
@Composable
private fun BotonPestana(icono: Dibujo, texto: String, elegida: Boolean, onClick: () -> Unit) {
    val f = FogonTema.actual
    val fondo by animateColorAsState(if (elegida) f.tinta1.copy(alpha = 0.16f) else Color.Transparent, label = "pestana")
    Box(
        Modifier.rebote(0.9f).size(48.dp).clip(CircleShape).background(fondo)
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

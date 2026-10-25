package io.github.fogonapps.cucha.ui

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
import io.github.fogonapps.cucha.CuchaViewModel
import io.github.fogonapps.cucha.Pantalla
import io.github.fogonapps.cucha.Pestana
import io.github.fogonapps.cucha.R
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

/** Lo que solo puede hacer la actividad: elegir archivos y abrir la cámara. */
data class Acciones(
    val exportar: () -> Unit,
    val importar: () -> Unit,
    val importarRecetas: () -> Unit,
    val escanear: (comida: String, fecha: String) -> Unit,
)

private data class Pregunta(val titulo: String, val texto: String?, val si: () -> Unit)

@Composable
fun App(vm: CuchaViewModel, acciones: Acciones) {
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
            Personaje(RId.drawable.personaje_la_cucha, stringResource(R.string.cargando), tamano = 140.dp)
        }
        return
    }

    CompositionLocalProvider(LocalConfirmar provides { t, x, si -> pregunta = Pregunta(t, x, si) }) {
        Box(Modifier.fillMaxSize().fondoFogon(f)) {
            if (!estado.listo) {
                Tutorial(vm, acciones.importar)
            } else {
                // Cada pestaña con su propio scroll (al cambiar, empieza arriba).
                PestanaAnimada(pestana) { p ->
                    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
                        when (p) {
                            Pestana.HOY -> Hoy(vm, estado)
                            Pestana.RECETAS -> Recetas(vm, estado)
                            Pestana.PLAN -> Plan(vm, estado)
                            Pestana.PROGRESO -> Progreso(vm, estado)
                        }
                        Espacio(120.dp)
                    }
                }
                BarraFlotante(pestana, vm::irA, vm::anotarAhora, Modifier.align(Alignment.BottomCenter))
                SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 92.dp)) { Aviso(it.visuals.message) }
            }

            // Pantallas encima de las pestañas
            PilaAnimada(pila.lastOrNull(), pila.size) { p ->
                Box(Modifier.fillMaxSize().fondoFogon(f).safeDrawingPadding().imePadding()) {
                    // Las pantallas con lista propia (buscar) manejan su scroll; las demás van en una columna que se desliza.
                    val conScroll = p !is Pantalla.Anotar
                    Column(Modifier.fillMaxSize().then(if (conScroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
                        when (p) {
                            is Pantalla.Anotar -> Anotar(vm, estado, p, acciones)
                            is Pantalla.Cantidad -> Cantidad(vm, estado, p)
                            is Pantalla.EditarProducto -> EditarProducto(vm, p)
                            is Pantalla.VerReceta -> VerReceta(vm, estado, p.id)
                            is Pantalla.EditarReceta -> EditarReceta(vm, estado, p)
                            Pantalla.Mercado -> Mercado(vm, estado)
                            Pantalla.Metas -> EditarMetas(vm, estado)
                            Pantalla.Perfil -> Perfil(vm, estado, acciones)
                            Pantalla.AcercaDe -> AcercaDe(vm)
                            Pantalla.Apariencia -> Column {
                                Cabecera(stringResource(RId.string.fogon_ap_titulo), vm::cerrar)
                                PantallaApariencia(Paleta.Cucha, AlmacenApariencia.de(LocalContext.current))
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

/** La barra de pestañas: una píldora que flota, con el botón de anotar en el centro. */
@Composable
private fun BarraFlotante(actual: Pestana, onIr: (Pestana) -> Unit, onAnotar: () -> Unit, modifier: Modifier = Modifier) {
    val f = FogonTema.actual
    val pestanas = listOf(
        Triple(Pestana.HOY, Iconos.Hoy, R.string.pestana_hoy),
        Triple(Pestana.RECETAS, Iconos.Recetas, R.string.pestana_recetas),
        Triple(Pestana.PLAN, Iconos.Plan, R.string.pestana_plan),
        Triple(Pestana.PROGRESO, Iconos.Progreso, R.string.pestana_progreso),
    )
    Row(
        modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth()
            .clip(FormaPildora).background(f.barra).border(f.borde, f.linea, FormaPildora).padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        pestanas.take(2).forEach { (pe, ic, t) -> BotonPestana(ic, stringResource(t), pe == actual) { onIr(pe) } }
        val anotar = stringResource(R.string.anotar)
        Box(
            Modifier.rebote(0.9f).size(52.dp).clip(CircleShape).background(f.tinta1)
                .clickable(onClick = onAnotar).semantics { contentDescription = anotar; role = Role.Button },
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

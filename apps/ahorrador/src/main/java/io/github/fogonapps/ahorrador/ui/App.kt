package io.github.fogonapps.ahorrador.ui

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.ahorrador.AhorradorViewModel
import io.github.fogonapps.ahorrador.MARCA_LOGRO
import io.github.fogonapps.ahorrador.Pantalla
import io.github.fogonapps.ahorrador.Pestana
import io.github.fogonapps.ahorrador.R
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.Chispa
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.FormaPildora
import io.github.fogonapps.identidad.ManifiestoFogon
import io.github.fogonapps.identidad.Personaje
import io.github.fogonapps.identidad.Tintas
import io.github.fogonapps.identidad.brillo
import io.github.fogonapps.identidad.fondoFogon
import io.github.fogonapps.identidad.R as RId

/** Pide confirmación: (título, texto opcional, acción si dice que sí). */
typealias Confirmar = (String, String?, () -> Unit) -> Unit

val LocalConfirmar = staticCompositionLocalOf<Confirmar> { { _, _, si -> si() } }

private data class Pregunta(val titulo: String, val texto: String?, val si: () -> Unit)

@Composable
fun App(vm: AhorradorViewModel, onExportar: () -> Unit, onExportarCsv: () -> Unit, onImportar: () -> Unit, candadoDisponible: Boolean) {
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
            Personaje(RId.drawable.personaje_el_ahorrador, stringResource(R.string.cargando), tamano = 140.dp)
        }
        return
    }

    CompositionLocalProvider(LocalConfirmar provides { t, x, si -> pregunta = Pregunta(t, x, si) }) {
        Box(Modifier.fillMaxSize().fondoFogon(f)) {
            if (!estado.listo) {
                Tutorial(vm, onImportar)
            } else {
                // Cada pestaña con su propio scroll (al cambiar, empieza arriba).
                key(pestana) {
                    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
                        when (pestana) {
                            Pestana.INICIO -> Inicio(vm, estado)
                            Pestana.RESUMEN -> Resumen(vm, estado)
                            Pestana.SOBRES -> Presupuesto(vm, estado)
                            Pestana.METAS -> Metas(vm, estado)
                        }
                        Espacio(120.dp)
                    }
                }
                BarraFlotante(pestana, vm::irA, { vm.abrir(Pantalla.Anotar()) }, Modifier.align(Alignment.BottomCenter))
                SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 92.dp)) { AvisoTinta(it.visuals.message) }
            }

            // Pantallas encima de las pestañas
            pila.lastOrNull()?.let { p ->
                Box(Modifier.fillMaxSize().fondoFogon(f).safeDrawingPadding().imePadding()) {
                    Column(Modifier.fillMaxSize().then(if (p is Pantalla.Anotar) Modifier else Modifier.verticalScroll(rememberScrollState()))) {
                        when (p) {
                            is Pantalla.Anotar -> Anotar(vm, estado, p)
                            is Pantalla.Cuentas -> Cuentas(vm, estado)
                            is Pantalla.Cuenta -> CuentaPantalla(vm, estado, p.id)
                            is Pantalla.EditarCuenta -> EditarCuenta(vm, estado, p.id)
                            is Pantalla.Fijos -> Fijos(vm, estado)
                            is Pantalla.EditarFijo -> EditarFijo(vm, estado, p.id)
                            is Pantalla.Deudas -> Deudas(vm, estado)
                            is Pantalla.EditarDeuda -> EditarDeuda(vm, estado, p.id, p.meDeben)
                            is Pantalla.EditarMeta -> EditarMeta(vm, estado, p.id)
                            is Pantalla.Cierre -> CierrePantalla(vm, estado, p.mes)
                            is Pantalla.Perfil -> Perfil(vm, estado, onExportar, onExportarCsv, onImportar, candadoDisponible)
                            is Pantalla.AcercaDe -> AcercaDe(vm)
                            is Pantalla.Apariencia -> Column { Cabecera(stringResource(RId.string.fogon_ap_titulo), vm::cerrar); io.github.fogonapps.identidad.PantallaApariencia(io.github.fogonapps.identidad.Paleta.Ahorrador, io.github.fogonapps.identidad.AlmacenApariencia.de(androidx.compose.ui.platform.LocalContext.current)) }
                            is Pantalla.Manifiesto -> Column { Cabecera(stringResource(R.string.manifiesto), vm::cerrar); ManifiestoFogon() }
                            is Pantalla.Licencias -> Licencias(vm)
                            is Pantalla.Preguntas -> Preguntas(vm)
                            is Pantalla.Reporte -> Reporte(vm)
                        }
                    }
                    SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) { AvisoTinta(it.visuals.message) }
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

/** La barra de pestañas: una píldora de vidrio que flota, con el botón de anotar en el centro. */
@Composable
private fun BarraFlotante(actual: Pestana, onIr: (Pestana) -> Unit, onAnotar: () -> Unit, modifier: Modifier = Modifier) {
    val f = FogonTema.actual
    val p = f.paleta
    val pestanas = listOf(
        Triple(Pestana.INICIO, Iconos.Inicio, R.string.pestana_inicio),
        Triple(Pestana.RESUMEN, Iconos.Resumen, R.string.pestana_resumen),
        Triple(Pestana.SOBRES, Iconos.Sobres, R.string.pestana_presupuesto),
        Triple(Pestana.METAS, Iconos.Moneda, R.string.pestana_metas),
    )
    Row(
        modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth()
            .clip(FormaPildora).background(androidx.compose.ui.graphics.lerp(p.fondo, f.texto, 0.12f).copy(alpha = 0.94f))
            .border(1.dp, f.linea, FormaPildora).padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
    ) {
        pestanas.take(2).forEach { (pe, ic, t) -> BotonPestana(ic, stringResource(t), pe == actual) { onIr(pe) } }
        val anotar = stringResource(R.string.anotar)
        Box(
            Modifier.size(56.dp).brillo(p.tinta.copy(alpha = 0.7f), CircleShape, 14.dp).clip(CircleShape).background(p.tinta)
                .clickable(onClick = onAnotar).semantics { contentDescription = anotar; role = Role.Button },
            contentAlignment = Alignment.Center,
        ) { Icon(Iconos.Mas, null, tint = p.sobreTinta, modifier = Modifier.size(26.dp)) }
        pestanas.drop(2).forEach { (pe, ic, t) -> BotonPestana(ic, stringResource(t), pe == actual) { onIr(pe) } }
    }
}

@Composable
private fun BotonPestana(icono: ImageVector, texto: String, elegida: Boolean, onClick: () -> Unit) {
    val f = FogonTema.actual
    Box(
        Modifier.size(50.dp).clip(CircleShape).background(if (elegida) f.pildora else Color.Transparent)
            .clickable(onClick = onClick).semantics { contentDescription = texto; selected = elegida; role = Role.Tab },
        contentAlignment = Alignment.Center,
    ) { Icon(icono, null, tint = if (elegida) f.sobrePildora else f.textoSuave, modifier = Modifier.size(22.dp)) }
}

/** Aviso con la identidad: píldora blanca, y la chispa en los logros (meta cumplida, deuda saldada). */
@Composable
private fun AvisoTinta(mensaje: String) {
    val logro = mensaje.startsWith(MARCA_LOGRO)
    Row(
        Modifier.fillMaxWidth().padding(12.dp).brillo(FogonTema.actual.paleta.segunda.copy(alpha = 0.7f), FormaPildora)
            .clip(FormaPildora).background(FogonTema.actual.pildora).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (logro) Chispa(tamano = 22.dp)
        Text(mensaje.removePrefix(MARCA_LOGRO), color = FogonTema.actual.sobrePildora, fontWeight = if (logro) FontWeight.Bold else FontWeight.Normal)
    }
}

/** Lo que se ve con el candado puesto, mientras se pide la huella o el bloqueo del teléfono. */
@Composable
fun Bloqueada(onDesbloquear: () -> Unit) {
    val f = FogonTema.actual
    Column(
        Modifier.fillMaxSize().fondoFogon(f).safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Personaje(RId.drawable.personaje_el_ahorrador, null, tamano = 150.dp)
        Icon(Iconos.Candado, null, tint = f.texto, modifier = Modifier.size(32.dp))
        Text(stringResource(R.string.bloqueada), style = MaterialTheme.typography.headlineSmall, color = f.texto)
        BotonRiso(stringResource(R.string.desbloquear), onDesbloquear)
    }
}

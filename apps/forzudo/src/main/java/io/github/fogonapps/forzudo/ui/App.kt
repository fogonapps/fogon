package io.github.fogonapps.forzudo.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.Personaje
import io.github.fogonapps.identidad.R as RId
import io.github.fogonapps.forzudo.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import io.github.fogonapps.identidad.fondoFogon
import io.github.fogonapps.identidad.brillo
import io.github.fogonapps.forzudo.GymViewModel
import io.github.fogonapps.forzudo.Pantalla
import io.github.fogonapps.forzudo.Pestana
import io.github.fogonapps.forzudo.datos.duracion
import kotlinx.coroutines.delay

/** Pide confirmación: (título, texto opcional, acción si dice que sí). */
typealias Confirmar = (String, String?, () -> Unit) -> Unit

val LocalConfirmar = staticCompositionLocalOf<Confirmar> { { _, _, si -> si() } }

private data class Pregunta(val titulo: String, val texto: String?, val si: () -> Unit)

@Composable
fun App(vm: GymViewModel, onExportar: () -> Unit, onImportar: () -> Unit) {
    val e by vm.estado.collectAsStateWithLifecycle()
    val pila by vm.pila.collectAsStateWithLifecycle()
    val pestana by vm.pestana.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val descanso by vm.descanso.collectAsStateWithLifecycle()
    val f = FogonTema.actual
    val snack = remember { SnackbarHostState() }
    var pregunta by remember { mutableStateOf<Pregunta?>(null) }

    LaunchedEffect(Unit) { vm.mensajes.collect { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(it) } }

    val estado = e
    if (estado == null) {
        Box(Modifier.fillMaxSize().background(f.fondo), contentAlignment = Alignment.Center) {
            Personaje(RId.drawable.personaje_el_forzudo, stringResource(R.string.cargando), tamano = 140.dp)
        }
        return
    }

    CompositionLocalProvider(LocalConfirmar provides { t, x, si -> pregunta = Pregunta(t, x, si) }) {
        Box(Modifier.fillMaxSize().fondoFogon(f)) {
            Scaffold(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                snackbarHost = { SnackbarHost(snack) { AvisoTinta(it.visuals.message) } },
                bottomBar = {
                    Column {
                        val confirmar = LocalConfirmar.current
                        if (estado.active != null && pila.none { it is Pantalla.Vivo }) BarraEntreno(estado.active.title ?: stringResource(R.string.voc_entreno), estado.active.start, { cancelarEntreno(vm, confirmar, vm.L.series(estado.active.entries)) }) { vm.abrir(Pantalla.Vivo) }
                        NavigationBar(containerColor = f.superficie) {
                            listOf(
                                Triple(Pestana.INICIO, stringResource(R.string.pestana_inicio), io.github.fogonapps.forzudo.R.drawable.munieco_nav_inicio),
                                Triple(Pestana.ENTRENAR, stringResource(R.string.pestana_entrenar), io.github.fogonapps.forzudo.R.drawable.munieco_nav_entrenar),
                                Triple(Pestana.RANGOS, stringResource(R.string.pestana_rangos), io.github.fogonapps.forzudo.R.drawable.munieco_nav_rangos),
                                Triple(Pestana.PERFIL, stringResource(R.string.pestana_perfil), io.github.fogonapps.forzudo.R.drawable.munieco_nav_perfil),
                            ).forEach { (p, t, ic) ->
                                NavigationBarItem(
                                    selected = pestana == p, onClick = { vm.irA(p) },
                                    icon = { Icon(androidx.compose.ui.res.painterResource(ic), null, Modifier.size(30.dp)) }, label = { Text(t) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = f.paleta.sobreTinta, indicatorColor = f.paleta.tinta,
                                        selectedTextColor = f.acentoTexto, unselectedIconColor = f.textoSuave, unselectedTextColor = f.textoSuave,
                                    ),
                                )
                            }
                        }
                    }
                },
            ) { pad ->
                // Cada pestaña con su propio scroll (al cambiar, empieza arriba)
                androidx.compose.runtime.key(pestana) { Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(when (pestana) { Pestana.INICIO -> R.string.pestana_inicio; Pestana.ENTRENAR -> R.string.pestana_entrenar; Pestana.RANGOS -> R.string.pestana_rangos; Pestana.PERFIL -> R.string.pestana_perfil }),
                        style = MaterialTheme.typography.headlineLarge, color = f.texto,
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp),
                    )
                    when (pestana) {
                        Pestana.INICIO -> Inicio(vm, estado)
                        Pestana.ENTRENAR -> Entrenar(vm, estado)
                        Pestana.RANGOS -> Rangos(vm, estado)
                        Pestana.PERFIL -> Perfil(vm, estado, sync, onExportar, onImportar)
                    }
                } }
            }

            // Pantallas encima de las pestañas
            pila.lastOrNull()?.let { p ->
                Box(Modifier.fillMaxSize().fondoFogon(f).safeDrawingPadding().imePadding()) {
                    Column(Modifier.fillMaxSize().then(if (p is Pantalla.Zoom) Modifier else Modifier.verticalScroll(rememberScrollState()))) {
                        when (p) {
                            is Pantalla.Vivo -> Vivo(vm, estado)
                            is Pantalla.Resumen -> Resumen(vm, estado, p.r)
                            is Pantalla.Detalle -> Detalle(vm, estado, p.id)
                            is Pantalla.Musculo -> MusculoPantalla(vm, estado, p.id)
                            is Pantalla.Programas -> Programas(vm, estado)
                            is Pantalla.Editor -> Editor(vm, estado, p.rid)
                            is Pantalla.Cambiar -> Cambiar(vm, p)
                            is Pantalla.Zoom -> Zoom(vm, p.id, p.i)
                            is Pantalla.AcercaDe -> AcercaDe(vm)
                            is Pantalla.Manifiesto -> Column { Cabecera(stringResource(R.string.manifiesto), vm::cerrar); io.github.fogonapps.identidad.ManifiestoFogon() }
                            is Pantalla.Licencias -> Licencias(vm)
                            is Pantalla.Preguntas -> Preguntas(vm)
                            is Pantalla.Reporte -> Reporte(vm)
                        }
                    }
                    if (p is Pantalla.Vivo) Box(Modifier.align(Alignment.BottomCenter)) { BarraDescanso(vm, descanso) }
                    SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp)) { AvisoTinta(it.visuals.message) }
                }
            }

            // Tutorial inicial (se puede saltar; se vuelve a ver desde Perfil)
            val verTutorial by vm.tutorial.collectAsStateWithLifecycle()
            if (verTutorial) Tutorial(vm, estado.nombre)

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


/** Aviso con la identidad: tinta sobre papel invertido, y la chispa en los récords. */
@Composable
private fun AvisoTinta(mensaje: String) {
    val record = mensaje.startsWith(io.github.fogonapps.forzudo.MARCA_RECORD)
    Row(
        Modifier.fillMaxWidth().padding(12.dp).brillo(FogonTema.actual.paleta.segunda.copy(alpha = 0.7f), io.github.fogonapps.identidad.FormaPildora)
            .clip(io.github.fogonapps.identidad.FormaPildora).background(Color.White).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (record) io.github.fogonapps.identidad.Chispa(tamano = 22.dp)
        Text(mensaje.removePrefix(io.github.fogonapps.forzudo.MARCA_RECORD), color = io.github.fogonapps.identidad.Tintas.Tinta, fontWeight = if (record) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun BarraEntreno(titulo: String, inicio: Long, onCancelar: () -> Unit, onAbrir: () -> Unit) {
    val f = FogonTema.actual
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { ahora = System.currentTimeMillis(); delay(1000) } }
    Row(
        Modifier.fillMaxWidth().background(f.paleta.tinta).clickable(onClick = onAbrir).heightIn(min = 52.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.entreno_en_curso, titulo), color = f.paleta.sobreTinta, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(duracion(ahora - inicio), color = f.paleta.sobreTinta, style = MaterialTheme.typography.titleMedium)
        IconButton(onCancelar) { Icon(Iconos.Cerrar, stringResource(R.string.cancelar_entreno), tint = f.paleta.sobreTinta) }
    }
}

@Composable
private fun BarraDescanso(vm: GymViewModel, d: io.github.fogonapps.forzudo.Descanso?) {
    val f = FogonTema.actual
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(d) { while (d != null) { ahora = System.currentTimeMillis(); delay(250) } }
    AnimatedVisibility(d != null, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
        val dd = d ?: return@AnimatedVisibility
        val rest = maxOf(0L, (dd.fin - ahora + 999) / 1000)
        Column(Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(16.dp)).background(f.superficie).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Iconos.Reloj, null, tint = f.texto, modifier = Modifier.size(20.dp))
                Text("${rest / 60}:${(rest % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.displaySmall, color = f.texto, modifier = Modifier.weight(1f))
                TextButton({ vm.ajustarDescanso(-15) }) { Text("−15") }
                TextButton({ vm.ajustarDescanso(15) }) { Text("+15") }
                TextButton({ vm.pararDescanso() }) { Text(stringResource(R.string.saltar)) }
            }
            Barra(if (dd.total > 0) rest.toFloat() / dd.total else 0f, f.paleta.tinta)
        }
    }
}

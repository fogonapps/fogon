package io.github.fogonapps.forzudo.ui

import io.github.fogonapps.identidad.brillo
import androidx.compose.foundation.border
import io.github.fogonapps.identidad.tarjetaFogon
import io.github.fogonapps.identidad.forma
import io.github.fogonapps.forzudo.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fogonapps.identidad.AcercaDeFogon
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.CORREO_FOGON
import io.github.fogonapps.identidad.URL_CODIGO
import io.github.fogonapps.identidad.EstadoVacio
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.NumeroRiso
import io.github.fogonapps.identidad.haloPuntos
import io.github.fogonapps.identidad.Licencia
import io.github.fogonapps.identidad.LicenciasFogon
import io.github.fogonapps.identidad.Logro
import io.github.fogonapps.identidad.Personaje
import io.github.fogonapps.identidad.R as RId
import io.github.fogonapps.forzudo.BuildConfig
import io.github.fogonapps.forzudo.GymViewModel
import io.github.fogonapps.forzudo.ModoCambio
import io.github.fogonapps.forzudo.Pantalla
import io.github.fogonapps.forzudo.ResumenEntreno
import io.github.fogonapps.forzudo.datos.Vocabulario
import io.github.fogonapps.forzudo.datos.Biblioteca
import io.github.fogonapps.forzudo.datos.Ejercicio
import io.github.fogonapps.forzudo.datos.EjercicioCfg
import io.github.fogonapps.forzudo.datos.Estado
import io.github.fogonapps.forzudo.datos.duracion
import io.github.fogonapps.forzudo.datos.duracionPalabras
import io.github.fogonapps.forzudo.datos.e1rmDe
import io.github.fogonapps.forzudo.datos.fechaCorta
import io.github.fogonapps.forzudo.datos.fk
import io.github.fogonapps.forzudo.datos.fmtVol
import io.github.fogonapps.forzudo.datos.Item
import kotlinx.coroutines.delay
import java.net.URLEncoder
import java.text.Normalizer

/* ----- Barra superior de las pantallas ----- */

@Composable
fun Cabecera(titulo: String, onAtras: (() -> Unit)?, iconoAtras: io.github.fogonapps.identidad.Dibujo = Iconos.Atras, derecha: @Composable () -> Unit = {}) {
    val f = FogonTema.actual
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(56.dp)) {
            if (onAtras != null) Box(
                Modifier.padding(start = 8.dp).size(46.dp).brillo(f.tinta1, androidx.compose.foundation.shape.CircleShape)
                    .clip(androidx.compose.foundation.shape.CircleShape).background(f.superficie)
                    .border(f.borde, f.linea, androidx.compose.foundation.shape.CircleShape)
                    .clickable(onClickLabel = null, role = androidx.compose.ui.semantics.Role.Button, onClick = onAtras),
                contentAlignment = Alignment.Center,
            ) { io.github.fogonapps.identidad.IconoRiso(iconoAtras, stringResource(R.string.volver), tamano = 22.dp, corrida = null) }
        }
        Text(titulo, style = MaterialTheme.typography.titleLarge, color = f.texto, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        Box(Modifier.width(96.dp), contentAlignment = Alignment.CenterEnd) { derecha() }
    }
}

/** Búsqueda de videos de técnica en YouTube ("<ejercicio> técnica correcta" en el idioma de la app). */
fun urlVideos(vm: GymViewModel, nombre: String) = "https://www.youtube.com/results?search_query=" + URLEncoder.encode(vm.tx(R.string.videos_busqueda, nombre), "UTF-8")

/* =================== Entreno en vivo =================== */

@Composable
fun Vivo(vm: GymViewModel, e: Estado) {
    val f = FogonTema.actual
    val a = e.active ?: run { LaunchedEffect(Unit) { vm.cerrar() }; return }
    val L = vm.L
    val confirmar = LocalConfirmar.current
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { ahora = System.currentTimeMillis(); delay(1000) } }
    Column {
        val marcadas = L.series(a.entries)
        val terminar = {
            if (marcadas == 0) confirmar(vm.tx(R.string.sin_series), vm.tx(R.string.sin_series_texto)) { vm.descartar() }
            else vm.terminar()
        }
        Cabecera(a.title ?: stringResource(R.string.voc_entreno), vm::cerrar, Iconos.Abajo)
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BotonSecundario(stringResource(R.string.cancelar), { cancelarEntreno(vm, confirmar, marcadas) }, Modifier.weight(1f), peligro = true)
            BotonRiso(stringResource(if (marcadas == 0) R.string.terminar else R.string.terminar_guardar), terminar, Modifier.weight(1f))
        }
        Trio(stringResource(R.string.duracion) to duracion(ahora - a.start), stringResource(R.string.volumen) to fmtVol(L.volumen(a.entries)), stringResource(R.string.series) to "${L.series(a.entries)}")
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
            BotonFecha(stringResource(R.string.fecha), a.date, vm.hoy(), vm::fechaActiva)
            SelectorRutina(vm, a.day) { did ->
                val perdidas = vm.seriesQueSePierden(did)
                if (perdidas > 0) confirmar(vm.tx(R.string.cambiar_rutina_pregunta), vm.tx(R.string.series_se_pierden)) { vm.cambiarRutinaActiva(did) }
                else vm.cambiarRutinaActiva(did)
            }
        }
        a.items.forEach { BloqueEjercicio(vm, e, vm.bib.cfg(it)) }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BotonSecundario(stringResource(R.string.mas_agregar_ejercicio), { vm.abrir(Pantalla.Cambiar(null, ModoCambio.VIVO_AGREGAR)) }, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(120.dp))
    }
}

/** Cancela el entreno en curso: sin series marcadas no hay nada que perder y no pregunta. */
fun cancelarEntreno(vm: GymViewModel, confirmar: Confirmar, marcadas: Int) {
    if (marcadas == 0) vm.descartar()
    else confirmar(vm.tx(R.string.cancelar_entreno_pregunta), vm.txPlural(R.plurals.se_borran_series, marcadas)) { vm.descartar() }
}

@Composable
private fun SelectorRutina(vm: GymViewModel, actual: String, onElegir: (String) -> Unit) {
    val f = FogonTema.actual
    var abierto by remember { mutableStateOf(false) }
    val dias = vm.dias()
    Column {
        Text(stringResource(R.string.rutina_sin_nombre), style = MaterialTheme.typography.labelMedium, color = f.textoSuave)
        Box {
            Text(dias.firstOrNull { it.id == actual }?.title ?: "—", style = MaterialTheme.typography.bodyLarge, color = f.acentoTexto,
                modifier = Modifier.clip(forma(8.dp)).clickable { abierto = true }.padding(horizontal = 10.dp, vertical = 8.dp))
            DropdownMenu(abierto, { abierto = false }) {
                dias.forEach { d -> DropdownMenuItem({ Text(d.title) }, { abierto = false; onElegir(d.id) }) }
            }
        }
    }
}

@Composable
private fun BloqueEjercicio(vm: GymViewModel, e: Estado, c: EjercicioCfg) {
    val f = FogonTema.actual
    val L = vm.L
    val a = e.active!!
    val series = a.entries[c.id].orEmpty()
    val previas = L.anterior(e, c.id).orEmpty()
    val std = vm.bib.estandarDe(c.id)
    val uri = LocalUriHandler.current
    var abierto by rememberSaveable(c.id) { mutableStateOf(false) }
    val estAbierta = stringResource(R.string.tecnica_abierta); val estCerrada = stringResource(R.string.tecnica_cerrada)
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Miniatura(c.e, vm.bib)
            Column(Modifier.weight(1f).clickable { abierto = !abierto }.semantics { stateDescription = if (abierto) estAbierta else estCerrada }) {
                Text(c.e.n, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = f.acentoTexto)
                Text(stringResource(R.string.toca_nombre), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
            TextButton({ vm.abrir(Pantalla.Cambiar(c.id, ModoCambio.VIVO, a.day)) }) { Text(stringResource(R.string.cambiar)) }
        }
        if (L.estancado(e, c.id)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(forma(10.dp)).background(f.paleta.segunda.copy(alpha = 0.18f)).padding(10.dp)) {
                Text(stringResource(R.string.estancado), style = MaterialTheme.typography.bodySmall, color = f.texto)
                TextButton({ vm.abrir(Pantalla.Cambiar(c.id, ModoCambio.VIVO, a.day)) }) { Text(stringResource(R.string.ver_alternativas)) }
            }
        }
        if (abierto) {
            FotosEjercicio(vm, c.e)
            Text(c.e.cue, style = MaterialTheme.typography.bodyMedium, color = f.texto, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            TextButton({ uri.openUri(urlVideos(vm, c.e.n)) }, Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.ver_videos)) }
        }
        val (meta, consejo) = L.meta(e, c)
        Text(meta + (consejo?.let { ": " } ?: ""), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        consejo?.let { Text(it, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = f.acentoTexto, modifier = Modifier.padding(horizontal = 16.dp)) }
        if (std?.mancuerna == true) Pista(stringResource(R.string.peso_una_mancuerna))
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            io.github.fogonapps.identidad.IconoRiso(Iconos.Reloj, null, tamano = 16.dp, tinta = f.textoSuave, corrida = null)
            Text(stringResource(R.string.descanso_de, "${c.rest / 60}min" + if (c.rest % 60 > 0) " ${c.rest % 60}s" else ""), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        }
        // Encabezado de la tabla
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val est = MaterialTheme.typography.labelSmall
            Text(stringResource(R.string.col_serie), style = est, color = f.textoSuave, modifier = Modifier.width(44.dp))
            Text(stringResource(R.string.col_anterior), style = est, color = f.textoSuave, modifier = Modifier.weight(1.2f))
            if (c.e.kind == "w") Text(stringResource(if (std?.mancuerna == true) R.string.col_kg_cada else R.string.col_kg), style = est, color = f.textoSuave, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Text(stringResource(if (c.e.kind == "s") R.string.col_seg else R.string.col_reps), style = est, color = f.textoSuave, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Text("✓", style = est, color = f.textoSuave, textAlign = TextAlign.Center, modifier = Modifier.width(52.dp))
        }
        val descCompleta = stringResource(R.string.serie_completa); val descPendiente = stringResource(R.string.dia_pendiente)
        series.forEachIndexed { i, s ->
            val p = previas.getOrNull(i)
            val descCopiar = stringResource(R.string.copiar_anterior)
            val descMarcar = stringResource(R.string.marcar_serie, i + 1)
            Row(
                Modifier.fillMaxWidth().background(if (s.done) f.paleta.tinta.copy(alpha = 0.16f) else Color.Transparent)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("${i + 1}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = f.texto, modifier = Modifier.width(38.dp), textAlign = TextAlign.Center)
                val txtPrev = if (p == null) "" else if (c.e.kind == "w") (p.kg?.let { fk(it) + "kg" } ?: "") + (p.reps?.let { " × " + fk(it) } ?: "") else p.reps?.let { fk(it) + if (c.e.kind == "s") " s" else "" } ?: ""
                Text(txtPrev, style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1,
                    modifier = Modifier.weight(1.2f).clickable(enabled = p != null) { vm.copiarAnterior(c.id, i) }.padding(vertical = 12.dp)
                        .semantics { contentDescription = "$descCopiar: $txtPrev" })
                if (c.e.kind == "w") CampoNumero(s.kg?.let { fk(it) } ?: "", p?.kg?.let { fk(it) } ?: "", stringResource(R.string.kg_serie, i + 1), true,
                    { vm.escribir(c.id, i, "kg", it) }, Modifier.weight(1f))
                CampoNumero(s.reps?.let { fk(it) } ?: "", p?.reps?.let { fk(it) } ?: "${c.lo}", stringResource(if (c.e.kind == "s") R.string.segundos_serie else R.string.reps_serie, i + 1), false,
                    { vm.escribir(c.id, i, "reps", it) }, Modifier.weight(1f))
                Box(
                    Modifier.size(44.dp).clip(forma(10.dp))
                        .background(if (s.done) f.paleta.tinta else f.superficie)
                        .clickable { vm.marcar(c.id, i) }
                        .semantics { contentDescription = descMarcar; stateDescription = if (s.done) descCompleta else descPendiente },
                    contentAlignment = Alignment.Center,
                ) { io.github.fogonapps.identidad.IconoRiso(Iconos.Chulo, null, tamano = 22.dp, tinta = if (s.done) f.sobreTinta1 else f.textoSuave, corrida = null) }
            }
        }
        TextButton({ vm.agregarSerie(c.id) }, Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.mas_agregar_serie)) }
    }
}

@Composable
private fun FotosEjercicio(vm: GymViewModel, e: Ejercicio) {
    if (vm.bib.foto(e.id, 0) == null) return
    val f = FogonTema.actual
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0 to stringResource(R.string.paso_inicio), 1 to stringResource(R.string.paso_final)).forEach { (i, t) ->
            Column(Modifier.weight(1f)) {
                FotoAsset(vm.bib.foto(e.id, i), stringResource(if (i == 0) R.string.posicion_inicial else R.string.posicion_final, e.n),
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(forma(10.dp)).clickable { vm.abrir(Pantalla.Zoom(e.id, i)) },
                    ContentScale.Fit)
                Text(t, style = MaterialTheme.typography.labelSmall, color = f.textoSuave)
            }
        }
    }
}

/* =================== Resumen =================== */

@Composable
fun Resumen(vm: GymViewModel, e: Estado, r: ResumenEntreno) {
    val f = FogonTema.actual
    Column {
        Cabecera(stringResource(R.string.entreno_guardado), null) { TextButton(vm::cerrar) { Text(stringResource(R.string.listo)) } }
        // La tarjeta del final: el saludo, el volumen en puntos sobre su halo y el personaje.
        Box(Modifier.fillMaxWidth().padding(16.dp).height(220.dp).tarjetaFogon(forma(30.dp))) {
            Box(Modifier.matchParentSize().haloPuntos(f.tinta2, centro = androidx.compose.ui.geometry.Offset(0.85f, 0.25f)))
            Column(Modifier.padding(22.dp).fillMaxWidth(0.6f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(e.nombre?.let { stringResource(R.string.buen_trabajo_nombre, it) } ?: stringResource(R.string.buen_trabajo), style = MaterialTheme.typography.headlineSmall, color = f.texto)
                Text("${r.titulo}, ${fechaCorta(r.fecha)}", style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
            NumeroRiso(fmtVol(r.volumen), 44.sp, Modifier.align(Alignment.BottomStart).padding(start = 22.dp, bottom = 18.dp))
            Personaje(RId.drawable.personaje_el_forzudo, stringResource(R.string.app_name), Modifier.align(Alignment.BottomEnd).padding(8.dp), tamano = 140.dp)
        }
        Trio(stringResource(R.string.tiempo) to duracionPalabras(r.ms), stringResource(R.string.volumen) to fmtVol(r.volumen), stringResource(R.string.series) to "${r.series}")
        if (r.volumen >= 150) {
            val motos = r.volumen / 150
            Text(pluralStringResource(R.plurals.motos, motos.toInt(), motos.toInt()),
                style = MaterialTheme.typography.titleMedium, color = f.texto, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp))
        }
        if (r.subidas.isNotEmpty()) Seccion(stringResource(R.string.subiste_rango)) {
            r.subidas.forEach { s ->
                Fila {
                    Insignia(s.rango, 34.dp)
                    Column { Text(s.musculo, fontWeight = FontWeight.Bold, color = f.texto); Text(s.rango.nombre, color = colorHex(s.rango.color), fontWeight = FontWeight.Bold) }
                }
            }
        }
        // Los récords nuevos, en una tarjeta con el trofeo.
        if (r.records.isNotEmpty()) Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).tarjetaFogon(forma(26.dp)).padding(start = 18.dp, top = 16.dp, bottom = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.records_nuevos), style = MaterialTheme.typography.titleLarge, color = f.texto)
                r.records.forEach { Logro(it) }
            }
            io.github.fogonapps.identidad.Ilustracion(io.github.fogonapps.forzudo.R.drawable.ilustracion_trofeo, tamano = 110.dp)
        }
        Box(Modifier.padding(16.dp)) { BotonRiso(stringResource(R.string.volver_inicio), vm::cerrar, Modifier.fillMaxWidth()) }
    }
}

/* =================== Detalle de ejercicio =================== */

@Composable
fun Detalle(vm: GymViewModel, e: Estado, id: String) {
    val f = FogonTema.actual
    val L = vm.L
    val ej = vm.bib.ex[id] ?: return
    val uri = LocalUriHandler.current
    val ses = e.sessions.filter { it.entries.containsKey(id) }.sortedBy { it.date }
    Column {
        Cabecera(ej.n, vm::cerrar)
        FotosEjercicio(vm, ej)
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Miniatura(ej, vm.bib, 58.dp)
            Column {
                Text(ej.n, style = MaterialTheme.typography.titleLarge, color = f.texto)
                Text("${ej.eq} · ${vm.bib.nombreMusculo(ej.m)} · " + stringResource(R.string.por_defecto, "${ej.lo}" + (if (ej.hi != ej.lo) "–${ej.hi}" else ""),
                    L.unidad(ej), "${ej.rest / 60}:${(ej.rest % 60).toString().padStart(2, '0')}"),
                    style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
        }
        Text(ej.cue, style = MaterialTheme.typography.bodyMedium, color = f.texto, modifier = Modifier.padding(horizontal = 16.dp))
        TextButton({ uri.openUri(urlVideos(vm, ej.n)) }, Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.ver_videos)) }

        TarjetaRango(vm, e, id)

        if (ej.kind == "w") Seccion(stringResource(R.string.peso_alto_sesion)) {
            Grafica(ses.map { s -> s.date to s.entries.getValue(id).mapNotNull { it.kg }.maxOrNull() })
        }
        Seccion(stringResource(if (ej.kind == "s") R.string.segundos_sesion else R.string.reps_sesion)) {
            Grafica(ses.map { s -> s.date to s.entries.getValue(id).mapNotNull { it.reps }.takeIf { it.isNotEmpty() }?.sum() })
        }
        var mejorVol: io.github.fogonapps.forzudo.datos.Serie? = null; var rm: Double? = null
        ses.forEach { s -> s.entries.getValue(id).forEach { x -> if (x.kg != null && x.reps != null) {
            val v = e1rmDe(x.kg, x.reps); if (rm == null || v > rm!!) rm = v
            if (mejorVol == null || x.kg * x.reps > mejorVol!!.kg!! * mejorVol!!.reps!!) mejorVol = x
        } } }
        val mk = L.maxKg(e, id); val mxr = L.maxReps(e, id)
        Seccion(stringResource(R.string.records)) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Dato(stringResource(if (ej.kind == "w") R.string.peso_mas_alto else R.string.maximo_serie), if (ej.kind == "w") mk?.let { fk(it) + " kg" } ?: "–" else mxr?.let { fk(it) + " " + L.unidad(ej) } ?: "–")
                Dato(stringResource(R.string.rm_estimado), if (rm != null && ej.kind == "w") fk(Math.round(rm * 10) / 10.0) + " kg" else "–")
            }
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Dato(stringResource(R.string.mejor_serie), if (mejorVol != null && ej.kind == "w") "${fk(mejorVol.kg)} kg × ${fk(mejorVol.reps)}" else "–")
                Dato(stringResource(R.string.sesiones), "${ses.size}")
            }
        }
        Seccion(stringResource(R.string.historial)) {
            if (ses.isEmpty()) Pista(stringResource(R.string.sin_historial))
            ses.reversed().forEach { s ->
                Fila {
                    Text(fechaCorta(s.date), Modifier.weight(1f), color = f.texto)
                    Text(s.entries.getValue(id).joinToString("  ") { x -> (if (x.kg != null && ej.kind == "w") fk(x.kg) + "×" else "") + (x.reps?.let { fk(it) } ?: "–") },
                        fontWeight = FontWeight.Bold, color = f.texto, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun TarjetaRango(vm: GymViewModel, e: Estado, id: String) {
    val f = FogonTema.actual
    val L = vm.L
    val std = vm.bib.estandarDe(id) ?: return
    Seccion(stringResource(if (std.maquina || std.aproximado) R.string.rango_aproximado else R.string.rango)) {
        if (L.pesoCorporal(e) == null) { Pista(stringResource(R.string.rango_falta_peso_ej)); return@Seccion }
        val r = L.rangoEjercicio(e, id)
        if (r == null) { Pista(stringResource(R.string.rango_anota_serie)); return@Seccion }
        val nt = L.siguienteMeta(id, r)
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Insignia(r.rango, 52.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(r.rango.nombre, color = colorHex(r.rango.color), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Barra(L.progresoDivision(r.rango.s).toFloat(), colorHex(r.rango.color))
                Text(nt?.let { stringResource(R.string.siguiente_rango, it.rango, fk(it.kg), it.reps) } ?: stringResource(R.string.rango_maximo), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Dato(stringResource(R.string.rm_estimado), fk(Math.round(r.e1 * 10) / 10.0) + " kg")
            Dato(stringResource(R.string.oro_para_tu_peso), fk(Math.round(r.T[2]).toDouble()) + " kg")
        }
    }
}

/* =================== Músculo =================== */

@Composable
fun MusculoPantalla(vm: GymViewModel, e: Estado, mid: String) {
    val f = FogonTema.actual
    val L = vm.L
    val m = vm.bib.musculos.firstOrNull { it.id == mid } ?: return
    val r = L.rangoMusculo(e, m)
    Column {
        Cabecera(m.n, vm::cerrar)
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Insignia(r?.rango, 62.dp)
            Column {
                Text(m.n, style = MaterialTheme.typography.labelMedium, color = f.textoSuave)
                Text(r?.rango?.nombre ?: stringResource(R.string.sin_datos_aun), style = MaterialTheme.typography.headlineSmall, color = r?.rango?.let { colorHex(it.color) } ?: f.textoSuave)
                Text(stringResource(R.string.promedio_2_mejores), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
        }
        Seccion(stringResource(R.string.ejercicios)) {
            m.ejercicios.forEach { id ->
                val ej = vm.bib.ex.getValue(id); val er = L.rangoEjercicio(e, id); val nt = L.siguienteMeta(id, er)
                Fila({ vm.abrir(Pantalla.Detalle(id)) }) {
                    Miniatura(ej, vm.bib)
                    Column(Modifier.weight(1f)) {
                        Text(ej.n, fontWeight = FontWeight.Bold, color = f.texto)
                        if (er != null) {
                            Text(er.rango.nombre, color = colorHex(er.rango.color), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            Text(nt?.let { stringResource(R.string.siguiente_meta, fk(it.kg), it.reps) } ?: stringResource(R.string.rango_maximo), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                        } else Text(stringResource(if (vm.bib.estandarDe(id) != null) R.string.sin_registros else R.string.no_cuenta_rango), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                    }
                    if (er != null) Insignia(er.rango, 30.dp)
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

/* =================== Programas =================== */

@Composable
fun Programas(vm: GymViewModel, e: Estado) {
    val f = FogonTema.actual
    val confirmar = LocalConfirmar.current
    Column {
        Cabecera(stringResource(R.string.programas), vm::cerrar)
        Pista(stringResource(R.string.pista_programas))
        vm.bib.programas.forEach { p ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).tarjetaFogon(forma(14.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(p.level, p.goal, pluralStringResource(R.plurals.dias, p.routines.size, p.routines.size)).forEach { Etiqueta(it, f.linea, f.texto) }
                }
                Text(p.name, style = MaterialTheme.typography.titleLarge, color = f.texto)
                Text(p.desc, style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
                p.routines.forEach { r ->
                    Text((r.dow?.let { Vocabulario.actual.diasCortos[it] + "  " } ?: "") + r.title + " · " + pluralStringResource(R.plurals.n_ejercicios, r.items.size, r.items.size), style = MaterialTheme.typography.bodySmall, color = f.texto)
                }
                if (e.program == p.key) BotonSecundario(stringResource(if (e.progEdited) R.string.programa_actual_modificado else R.string.programa_actual), {}, habilitado = false)
                else BotonRiso(stringResource(R.string.usar_programa), {
                    confirmar(vm.tx(R.string.usar_programa_pregunta, p.name), vm.tx(R.string.usar_programa_texto)) { vm.aplicarPrograma(p.key) }
                })
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

/* =================== Editor de rutina =================== */

@Composable
fun Editor(vm: GymViewModel, e: Estado, rid: String) {
    val f = FogonTema.actual
    val r = e.routines?.firstOrNull { it.id == rid } ?: run { LaunchedEffect(Unit) { vm.cerrar() }; return }
    val confirmar = LocalConfirmar.current
    Column {
        Cabecera(stringResource(R.string.editar_rutina), vm::cerrar)
        var titulo by remember(rid) { mutableStateOf(r.title) }
        OutlinedTextField(titulo, { titulo = it.take(40); vm.tituloRutina(rid, it) }, label = { Text(stringResource(R.string.nombre)) }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        var abierto by remember { mutableStateOf(false) }
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.dia_semana) + " ", color = f.texto)
            Box {
                TextButton({ abierto = true }) { Text(r.dow?.let { Vocabulario.actual.diasLargos[it] } ?: stringResource(R.string.sin_dia_fijo)) }
                DropdownMenu(abierto, { abierto = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.sin_dia_fijo)) }, { abierto = false; vm.diaRutina(rid, null) })
                    Vocabulario.actual.diasLargos.forEachIndexed { i, n -> DropdownMenuItem({ Text(n) }, { abierto = false; vm.diaRutina(rid, i) }) }
                }
            }
        }
        Seccion(stringResource(R.string.ejercicios_n, r.items.size)) {
            if (r.items.isEmpty()) EstadoVacio(RId.drawable.personaje_el_forzudo, stringResource(R.string.rutina_vacia), stringResource(R.string.rutina_vacia_texto))
            r.items.forEachIndexed { i, it ->
                val c = vm.bib.cfg(it)
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).tarjetaFogon(forma(14.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Miniatura(c.e, vm.bib)
                        Column(Modifier.weight(1f)) {
                            Text(c.e.n, fontWeight = FontWeight.Bold, color = f.texto)
                            Text("${c.e.eq} · ${vm.bib.nombreMusculo(c.e.m)}", style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Triple("sets", stringResource(R.string.series), c.sets), Triple("lo", stringResource(R.string.reps_min), c.lo), Triple("hi", stringResource(R.string.reps_max), c.hi), Triple("rest", stringResource(R.string.descanso_s), c.rest)).forEach { (campo, et, v) ->
                            Column(Modifier.weight(1f)) {
                                Text(et, style = MaterialTheme.typography.labelSmall, color = f.textoSuave, maxLines = 1)
                                CampoNumero("$v", "", stringResource(R.string.campo_de, et, c.e.n), false, { t -> t.toIntOrNull()?.let { n -> vm.campoItem(rid, i, campo, n) } })
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton({ vm.moverItem(rid, i, -1) }, enabled = i > 0) { Text(stringResource(R.string.subir)) }
                        TextButton({ vm.moverItem(rid, i, 1) }, enabled = i < r.items.size - 1) { Text(stringResource(R.string.bajar)) }
                        TextButton({ vm.abrir(Pantalla.Cambiar(it.id, ModoCambio.EDITAR, rid, i)) }) { Text(stringResource(R.string.cambiar)) }
                        TextButton({ confirmar(vm.tx(R.string.quitar_pregunta, c.e.n), vm.tx(R.string.su_historial)) { vm.quitarItem(rid, i) } }) {
                            Text(stringResource(R.string.quitar), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BotonRiso(stringResource(R.string.mas_agregar_ejercicio), { vm.abrir(Pantalla.Cambiar(null, ModoCambio.AGREGAR, rid)) }, Modifier.fillMaxWidth())
            BotonSecundario(stringResource(R.string.eliminar_rutina), { confirmar(vm.tx(R.string.eliminar_rutina_pregunta), vm.tx(R.string.tu_historial)) { vm.eliminarRutina(rid) } }, Modifier.fillMaxWidth(), peligro = true)
        }
        Spacer(Modifier.height(40.dp))
    }
}

/* =================== Cambiar o agregar ejercicio =================== */

@Composable
fun Cambiar(vm: GymViewModel, p: Pantalla.Cambiar) {
    val f = FogonTema.actual
    val actual = p.id?.let { vm.bib.ex[it] }
    var q by rememberSaveable { mutableStateOf("") }
    var guardar by rememberSaveable { mutableStateOf(false) }
    Column {
        Cabecera(stringResource(if (actual != null) R.string.cambiar_ejercicio else R.string.agregar_ejercicio), vm::cerrar)
        if (actual != null) Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Miniatura(actual, vm.bib)
            Column { Text(stringResource(R.string.actual), style = MaterialTheme.typography.labelSmall, color = f.textoSuave); Text(actual.n, fontWeight = FontWeight.Bold, color = f.texto) }
        }
        if (p.modo == ModoCambio.VIVO && p.rid != null) Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(guardar, { guardar = it })
            Text(stringResource(R.string.guardar_en_rutina), style = MaterialTheme.typography.bodySmall, color = f.texto)
        }
        OutlinedTextField(q, { q = it }, label = { Text(stringResource(R.string.buscar_ejercicio)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(16.dp))
        val fila: @Composable (Ejercicio) -> Unit = { x ->
            Fila({ vm.elegir(p, x.id, guardar) }) {
                Miniatura(x, vm.bib)
                Column(Modifier.weight(1f)) {
                    Text(x.n, fontWeight = FontWeight.Bold, color = f.texto)
                    Text("${x.eq} · ${vm.bib.nombreMusculo(x.m)}", style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                }
            }
        }
        val texto = paraBuscar(q.trim())
        if (actual != null && texto.isEmpty()) {
            val alt = vm.L.alternativas(actual.id)
            if (alt.variantes.isNotEmpty()) Seccion(stringResource(R.string.lo_mismo)) { Pista(stringResource(R.string.lo_mismo_pista)); alt.variantes.forEach { fila(it) } }
            if (alt.relacionados.isNotEmpty()) Seccion(stringResource(R.string.relacionados)) { Pista(stringResource(R.string.relacionados_pista)); alt.relacionados.forEach { fila(it) } }
            Pista(stringResource(R.string.buscas_otro), centro = true)
        } else {
            val todos = vm.bib.ejercicios.filter { it.id != p.id && it.id != "press-banca-ligero" && (texto.isEmpty() || paraBuscar("${it.n} ${it.eq} ${vm.bib.nombreMusculo(it.m)}").contains(texto)) }
            vm.bib.musculos.forEach { m -> val xs = todos.filter { it.m == m.id }; if (xs.isNotEmpty()) Seccion(m.n) { xs.forEach { fila(it) } } }
            if (todos.isEmpty()) Pista(stringResource(R.string.no_encontrado), centro = true)
        }
        Spacer(Modifier.height(60.dp))
    }
}

/* =================== Zoom de fotos =================== */

@Composable
fun Zoom(vm: GymViewModel, id: String, i0: Int) {
    val ej = vm.bib.ex[id] ?: return
    var i by rememberSaveable { mutableStateOf(i0) }
    var escala by remember(i) { mutableFloatStateOf(1f) }
    var desp by remember(i) { mutableStateOf(Offset.Zero) }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(ej.n, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(8.dp))
            TextButton(vm::cerrar) { Text(stringResource(R.string.cerrar), color = Color.White) }
        }
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .pointerInput(i) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        escala = (escala * zoom).coerceIn(1f, 4f)
                        desp = if (escala == 1f) Offset.Zero else desp + pan
                    }
                }
                .pointerInput(i) { detectTapGestures(onDoubleTap = { if (escala > 1f) { escala = 1f; desp = Offset.Zero } else escala = 2.5f }) },
            contentAlignment = Alignment.Center,
        ) {
            FotoAsset(vm.bib.foto(id, i), stringResource(if (i == 0) R.string.posicion_inicial else R.string.posicion_final, ej.n),
                Modifier.fillMaxWidth().graphicsLayer { scaleX = escala; scaleY = escala; translationX = desp.x; translationY = desp.y },
                ContentScale.Fit)
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(0 to stringResource(R.string.paso_inicio), 1 to stringResource(R.string.paso_final)).forEach { (k, t) ->
                TextButton({ i = k }, Modifier.weight(1f)) { Text(t, color = if (i == k) Color.White else Color.Gray, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/* =================== Acerca de =================== */

@Composable
fun AcercaDe(vm: GymViewModel) {
    val uri = LocalUriHandler.current
    Column {
        Cabecera(stringResource(R.string.acerca_de_corto), vm::cerrar)
        AcercaDeFogon(
            personaje = RId.drawable.personaje_el_forzudo,
            nombreApp = stringResource(R.string.app_name),
            nombrePersonaje = stringResource(R.string.app_name),
            version = BuildConfig.VERSION_NAME,
            creditos = stringArrayResource(R.array.creditos).toList(),
            onVerManifiesto = { vm.abrir(Pantalla.Manifiesto) },
            onVerLicencias = { vm.abrir(Pantalla.Licencias) },
            onAbrirEnlace = { uri.openUri(it) },
            avisoSalud = stringResource(R.string.aviso_salud),
        )
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun Licencias(vm: GymViewModel) {
    Column {
        Cabecera(stringResource(R.string.licencias), vm::cerrar)
        LicenciasFogon(
            propias = listOf(
                Licencia(
                    stringResource(R.string.lic_modelo),
                    stringResource(R.string.lic_modelo_texto),
                    "mit-react-body-highlighter.txt",
                ),
                Licencia("react-native-body-highlighter", stringResource(R.string.lic_rnbh), "mit-react-native-body-highlighter.txt"),
                Licencia(
                    "OpenPowerlifting",
                    stringResource(R.string.lic_opl),
                ),
                Licencia("Everkinetic", stringResource(R.string.lic_everkinetic), "cc-by-sa-4.0.txt"),
            ),
        )
        Spacer(Modifier.height(40.dp))
    }
}

/* =================== Preguntas frecuentes =================== */

/* Las preguntas y respuestas son los arreglos preguntas y respuestas de strings.xml: las mismas del README de la app. Si cambia una, cambian las dos. */

@Composable
fun Preguntas(vm: GymViewModel) {
    val f = FogonTema.actual
    Column {
        Cabecera(stringResource(R.string.preguntas_frecuentes), vm::cerrar)
        stringArrayResource(R.array.preguntas).zip(stringArrayResource(R.array.respuestas)).forEach { (p, r) ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(p, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = f.texto)
                Text(r, style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

/* =================== Reportar un fallo o sugerir algo =================== */

/**
 * La app no tiene servidor: el mensaje sale por GitHub (un reporte público en el repo) o por el
 * correo de Fogón, con la versión de la app y de Android agregadas. Nada más del teléfono.
 */
@Composable
fun Reporte(vm: GymViewModel) {
    val f = FogonTema.actual
    val uri = LocalUriHandler.current
    var fallo by rememberSaveable { mutableStateOf(true) }
    var texto by rememberSaveable { mutableStateOf("") }
    val datos = "El Forzudo ${BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.RELEASE}"
    val titulo = (if (fallo) "[Bug] " else "[Idea] ") + texto.trim().lineSequence().firstOrNull().orEmpty().take(70)
    val cuerpo = texto.trim() + "\n\n---\n" + datos
    fun cod(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    Column {
        Cabecera(stringResource(R.string.reportar_titulo), vm::cerrar)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Pista(stringResource(R.string.reportar_pista))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(fallo, { fallo = true }, { Text(stringResource(R.string.algo_falla)) })
                FilterChip(!fallo, { fallo = false }, { Text(stringResource(R.string.tengo_idea)) })
            }
            OutlinedTextField(
                texto, { if (it.length <= 3000) texto = it }, Modifier.fillMaxWidth().heightIn(min = 160.dp),
                label = { Text(stringResource(if (fallo) R.string.que_paso else R.string.tu_idea)) },
            )
            Text(stringResource(R.string.se_agrega, datos), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            BotonRiso(stringResource(R.string.enviar_github), { uri.openUri("$URL_CODIGO/issues/new?title=${cod(titulo)}&body=${cod(cuerpo)}") },
                Modifier.fillMaxWidth(), habilitado = texto.isNotBlank() && URL_CODIGO != null)
            Text(stringResource(R.string.enviar_github_pista),
                style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            CORREO_FOGON?.let { correo ->
                BotonSecundario(stringResource(R.string.enviar_correo), { uri.openUri("mailto:$correo?subject=${cod(titulo)}&body=${cod(cuerpo)}") },
                    Modifier.fillMaxWidth(), habilitado = texto.isNotBlank())
                Text(stringResource(R.string.enviar_correo_pista, correo), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

/** Minúsculas y sin tildes, para que "bulgara" encuentre "búlgara". */
private fun paraBuscar(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

package io.github.fogonapps.forzudo.ui

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
import io.github.fogonapps.identidad.AcercaDeFogon
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.EstadoVacio
import io.github.fogonapps.identidad.FogonTema
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
fun Cabecera(titulo: String, onAtras: (() -> Unit)?, iconoAtras: androidx.compose.ui.graphics.vector.ImageVector = Iconos.Atras, derecha: @Composable () -> Unit = {}) {
    val f = FogonTema.actual
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(56.dp)) {
            if (onAtras != null) IconButton(onAtras) { Icon(iconoAtras, "Volver", tint = f.texto) }
        }
        Text(titulo, style = MaterialTheme.typography.titleLarge, color = f.texto, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        Box(Modifier.width(96.dp), contentAlignment = Alignment.CenterEnd) { derecha() }
    }
}

fun urlVideos(nombre: String) = "https://www.youtube.com/results?search_query=" + URLEncoder.encode("$nombre tecnica correcta", "UTF-8")

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
        Cabecera(a.title ?: "Entreno", vm::cerrar, Iconos.Abajo) {
            TextButton({ vm.terminar() }) { Text("Terminar", fontWeight = FontWeight.Bold, color = f.acentoTexto) }
        }
        Trio("Duración" to duracion(ahora - a.start), "Volumen" to fmtVol(L.volumen(a.entries)), "Series" to "${L.series(a.entries)}")
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
            BotonFecha("Fecha", a.date, vm.hoy(), vm::fechaActiva)
            SelectorRutina(vm, a.day) { did ->
                val perdidas = vm.seriesQueSePierden(did)
                if (perdidas > 0) confirmar("¿Cambiar de rutina?", "Las series ya marcadas de ejercicios que no están en esa rutina se pierden.") { vm.cambiarRutinaActiva(did) }
                else vm.cambiarRutinaActiva(did)
            }
        }
        a.items.forEach { BloqueEjercicio(vm, e, vm.bib.cfg(it)) }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BotonSecundario("+ Agregar ejercicio", { vm.abrir(Pantalla.Cambiar(null, ModoCambio.VIVO_AGREGAR)) }, Modifier.fillMaxWidth())
            BotonSecundario("Descartar entreno", {
                confirmar("¿Descartar este entreno?", "Se pierde lo anotado hoy.") { vm.descartar() }
            }, Modifier.fillMaxWidth(), peligro = true)
        }
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun SelectorRutina(vm: GymViewModel, actual: String, onElegir: (String) -> Unit) {
    val f = FogonTema.actual
    var abierto by remember { mutableStateOf(false) }
    val dias = vm.dias()
    Column {
        Text("Rutina", style = MaterialTheme.typography.labelMedium, color = f.textoSuave)
        Box {
            Text(dias.firstOrNull { it.id == actual }?.title ?: "—", style = MaterialTheme.typography.bodyLarge, color = f.acentoTexto,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { abierto = true }.padding(horizontal = 10.dp, vertical = 8.dp))
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
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Miniatura(c.e, vm.bib)
            Column(Modifier.weight(1f).clickable { abierto = !abierto }.semantics { stateDescription = if (abierto) "técnica abierta" else "técnica cerrada" }) {
                Text(c.e.n, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = f.acentoTexto)
                Text("Toca el nombre para ver los dibujos y la técnica", style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
            TextButton({ vm.abrir(Pantalla.Cambiar(c.id, ModoCambio.VIVO, a.day)) }) { Text("Cambiar") }
        }
        if (L.estancado(e, c.id)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(10.dp)).background(f.paleta.segunda.copy(alpha = 0.18f)).padding(10.dp)) {
                Text("Sin progreso en tus últimas 3 sesiones. Prueba una variante o baja un poco el peso y vuelve a subir.", style = MaterialTheme.typography.bodySmall, color = f.texto)
                TextButton({ vm.abrir(Pantalla.Cambiar(c.id, ModoCambio.VIVO, a.day)) }) { Text("Ver alternativas") }
            }
        }
        if (abierto) {
            FotosEjercicio(vm, c.e)
            Text(c.e.cue, style = MaterialTheme.typography.bodyMedium, color = f.texto, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            TextButton({ uri.openUri(urlVideos(c.e.n)) }, Modifier.padding(horizontal = 8.dp)) { Text("▶ Ver videos de técnica") }
        }
        val (meta, consejo) = L.meta(e, c)
        Text(meta + (consejo?.let { ": " } ?: ""), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        consejo?.let { Text(it, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = f.acentoTexto, modifier = Modifier.padding(horizontal = 16.dp)) }
        if (std?.mancuerna == true) Pista("Anota el peso de una mancuerna")
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Iconos.Reloj, null, tint = f.textoSuave, modifier = Modifier.size(16.dp))
            Text("Descanso: ${c.rest / 60}min" + if (c.rest % 60 > 0) " ${c.rest % 60}s" else "", style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        }
        // Encabezado de la tabla
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val est = MaterialTheme.typography.labelSmall
            Text("SERIE", style = est, color = f.textoSuave, modifier = Modifier.width(44.dp))
            Text("ANTERIOR", style = est, color = f.textoSuave, modifier = Modifier.weight(1.2f))
            if (c.e.kind == "w") Text(if (std?.mancuerna == true) "KG C/U" else "KG", style = est, color = f.textoSuave, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Text(if (c.e.kind == "s") "SEG" else "REPS", style = est, color = f.textoSuave, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Text("✓", style = est, color = f.textoSuave, textAlign = TextAlign.Center, modifier = Modifier.width(52.dp))
        }
        series.forEachIndexed { i, s ->
            val p = previas.getOrNull(i)
            Row(
                Modifier.fillMaxWidth().background(if (s.done) f.paleta.tinta.copy(alpha = 0.16f) else Color.Transparent)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("${i + 1}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = f.texto, modifier = Modifier.width(38.dp), textAlign = TextAlign.Center)
                val txtPrev = if (p == null) "" else if (c.e.kind == "w") (p.kg?.let { fk(it) + "kg" } ?: "") + (p.reps?.let { " × " + fk(it) } ?: "") else p.reps?.let { fk(it) + if (c.e.kind == "s") " s" else "" } ?: ""
                Text(txtPrev, style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 1,
                    modifier = Modifier.weight(1.2f).clickable(enabled = p != null) { vm.copiarAnterior(c.id, i) }.padding(vertical = 12.dp)
                        .semantics { contentDescription = "Copiar valores anteriores: $txtPrev" })
                if (c.e.kind == "w") CampoNumero(s.kg?.let { fk(it) } ?: "", p?.kg?.let { fk(it) } ?: "", "Kg serie ${i + 1}", true,
                    { vm.escribir(c.id, i, "kg", it) }, Modifier.weight(1f))
                CampoNumero(s.reps?.let { fk(it) } ?: "", p?.reps?.let { fk(it) } ?: "${c.lo}", (if (c.e.kind == "s") "Segundos" else "Repeticiones") + " serie ${i + 1}", false,
                    { vm.escribir(c.id, i, "reps", it) }, Modifier.weight(1f))
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (s.done) f.paleta.tinta else f.superficie)
                        .clickable { vm.marcar(c.id, i) }
                        .semantics { contentDescription = "Marcar serie ${i + 1} completa"; stateDescription = if (s.done) "completa" else "pendiente" },
                    contentAlignment = Alignment.Center,
                ) { Icon(Iconos.Chulo, null, tint = if (s.done) f.paleta.sobreTinta else f.textoSuave, modifier = Modifier.size(22.dp)) }
            }
        }
        TextButton({ vm.agregarSerie(c.id) }, Modifier.padding(horizontal = 8.dp)) { Text("+ Agregar serie") }
    }
}

@Composable
private fun FotosEjercicio(vm: GymViewModel, e: Ejercicio) {
    if (vm.bib.foto(e.id, 0) == null) return
    val f = FogonTema.actual
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0 to "1 · Inicio", 1 to "2 · Final").forEach { (i, t) ->
            Column(Modifier.weight(1f)) {
                FotoAsset(vm.bib.foto(e.id, i), "${e.n}: posición ${if (i == 0) "inicial" else "final"}",
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)).clickable { vm.abrir(Pantalla.Zoom(e.id, i)) },
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
        Cabecera("Entreno guardado", null) { TextButton(vm::cerrar) { Text("Listo") } }
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Personaje(RId.drawable.personaje_el_forzudo, "El Forzudo", tamano = 150.dp)
            Text("¡Buen trabajo" + (e.nombre?.let { ", $it" } ?: "") + "!", style = MaterialTheme.typography.headlineMedium, color = f.texto, textAlign = TextAlign.Center)
            Text("${r.titulo}, ${fechaCorta(r.fecha)}", color = f.textoSuave)
        }
        Trio("Tiempo" to duracionPalabras(r.ms), "Volumen" to fmtVol(r.volumen), "Series" to "${r.series}")
        if (r.volumen >= 150) {
            val motos = r.volumen / 150
            Text("Levantaste el equivalente a " + (if (motos >= 2) "${motos.toInt()} motos" else "una moto") + " de 150 kg.",
                style = MaterialTheme.typography.titleMedium, color = f.texto, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp))
        }
        if (r.subidas.isNotEmpty()) Seccion("Subiste de rango") {
            r.subidas.forEach { s ->
                Fila {
                    Insignia(s.rango, 34.dp)
                    Column { Text(s.musculo, fontWeight = FontWeight.Bold, color = f.texto); Text(s.rango.nombre, color = colorHex(s.rango.color), fontWeight = FontWeight.Bold) }
                }
            }
        }
        if (r.records.isNotEmpty()) Seccion("Récords nuevos") {
            r.records.forEach { Logro(it, Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
        }
        Box(Modifier.padding(16.dp)) { BotonRiso("Volver al inicio", vm::cerrar, Modifier.fillMaxWidth()) }
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
                Text("${ej.eq} · ${vm.bib.nombreMusculo(ej.m)} · por defecto ${ej.lo}" + (if (ej.hi != ej.lo) "–${ej.hi}" else "") +
                    " ${L.unidad(ej)}, descanso ${ej.rest / 60}:${(ej.rest % 60).toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
        }
        Text(ej.cue, style = MaterialTheme.typography.bodyMedium, color = f.texto, modifier = Modifier.padding(horizontal = 16.dp))
        TextButton({ uri.openUri(urlVideos(ej.n)) }, Modifier.padding(horizontal = 8.dp)) { Text("▶ Ver videos de técnica") }

        TarjetaRango(vm, e, id)

        if (ej.kind == "w") Seccion("Peso más alto por sesión (kg)") {
            Grafica(ses.map { s -> s.date to s.entries.getValue(id).mapNotNull { it.kg }.maxOrNull() })
        }
        Seccion(if (ej.kind == "s") "Segundos totales por sesión" else "Repeticiones totales por sesión") {
            Grafica(ses.map { s -> s.date to s.entries.getValue(id).mapNotNull { it.reps }.takeIf { it.isNotEmpty() }?.sum() })
        }
        var mejorVol: io.github.fogonapps.forzudo.datos.Serie? = null; var rm: Double? = null
        ses.forEach { s -> s.entries.getValue(id).forEach { x -> if (x.kg != null && x.reps != null) {
            val v = e1rmDe(x.kg, x.reps); if (rm == null || v > rm!!) rm = v
            if (mejorVol == null || x.kg * x.reps > mejorVol!!.kg!! * mejorVol!!.reps!!) mejorVol = x
        } } }
        val mk = L.maxKg(e, id); val mxr = L.maxReps(e, id)
        Seccion("Récords") {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Dato(if (ej.kind == "w") "Peso más alto" else "Máximo en una serie", if (ej.kind == "w") mk?.let { fk(it) + " kg" } ?: "–" else mxr?.let { fk(it) + " " + L.unidad(ej) } ?: "–")
                Dato("1RM estimado", if (rm != null && ej.kind == "w") fk(Math.round(rm * 10) / 10.0) + " kg" else "–")
            }
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Dato("Mejor serie", if (mejorVol != null && ej.kind == "w") "${fk(mejorVol.kg)} kg × ${fk(mejorVol.reps)}" else "–")
                Dato("Sesiones", "${ses.size}")
            }
        }
        Seccion("Historial") {
            if (ses.isEmpty()) Pista("Todavía no has hecho este ejercicio.")
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
    Seccion("Rango" + if (std.maquina || std.aproximado) " (aproximado)" else "") {
        if (L.pesoCorporal(e) == null) { Pista("Registra tu peso corporal en Perfil para ver tu rango en este ejercicio."); return@Seccion }
        val r = L.rangoEjercicio(e, id)
        if (r == null) { Pista("Anota una serie con peso y repeticiones para ver tu rango."); return@Seccion }
        val nt = L.siguienteMeta(id, r)
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Insignia(r.rango, 52.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(r.rango.nombre, color = colorHex(r.rango.color), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Barra(L.progresoDivision(r.rango.s).toFloat(), colorHex(r.rango.color))
                Text(nt?.let { "Siguiente (${it.rango}): ${fk(it.kg)} kg × ${it.reps}" } ?: "¡Rango máximo!", style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Dato("1RM estimado", fk(Math.round(r.e1 * 10) / 10.0) + " kg")
            Dato("Intermedio (Oro) para tu peso", fk(Math.round(r.T[2]).toDouble()) + " kg")
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
                Text(r?.rango?.nombre ?: "Sin datos", style = MaterialTheme.typography.headlineSmall, color = r?.rango?.let { colorHex(it.color) } ?: f.textoSuave)
                Text("Promedio de tus 2 mejores ejercicios de este grupo", style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
        }
        Seccion("Ejercicios") {
            m.ejercicios.forEach { id ->
                val ej = vm.bib.ex.getValue(id); val er = L.rangoEjercicio(e, id); val nt = L.siguienteMeta(id, er)
                Fila({ vm.abrir(Pantalla.Detalle(id)) }) {
                    Miniatura(ej, vm.bib)
                    Column(Modifier.weight(1f)) {
                        Text(ej.n, fontWeight = FontWeight.Bold, color = f.texto)
                        if (er != null) {
                            Text(er.rango.nombre, color = colorHex(er.rango.color), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            Text(nt?.let { "Siguiente: ${fk(it.kg)} kg × ${it.reps}" } ?: "¡Rango máximo!", style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                        } else Text(if (vm.bib.estandarDe(id) != null) "Sin registros" else "No cuenta para el rango", style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
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
        Cabecera("Programas", vm::cerrar)
        Pista("Escoge según tu objetivo y los días que puedes ir. Tu historial, récords y rangos se conservan: solo cambian tus rutinas, y después puedes editarlas.")
        vm.bib.programas.forEach { p ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(14.dp)).background(f.superficie).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(p.level, p.goal, "${p.routines.size} días").forEach { Etiqueta(it, f.linea, f.texto) }
                }
                Text(p.name, style = MaterialTheme.typography.titleLarge, color = f.texto)
                Text(p.desc, style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
                p.routines.forEach { r ->
                    Text((r.dow?.let { Biblioteca.DIAS_CORTOS[it] + "  " } ?: "") + r.title + " · ${r.items.size} ejercicios", style = MaterialTheme.typography.bodySmall, color = f.texto)
                }
                if (e.program == p.key) BotonSecundario("Programa actual" + if (e.progEdited) " (modificado)" else "", {}, habilitado = false)
                else BotonRiso("Usar este programa", {
                    confirmar("¿Usar “${p.name}”?", "Esto reemplaza tus rutinas. Tu historial, récords y rangos se conservan.") { vm.aplicarPrograma(p.key) }
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
        Cabecera("Editar rutina", vm::cerrar)
        var titulo by remember(rid) { mutableStateOf(r.title) }
        OutlinedTextField(titulo, { titulo = it.take(40); vm.tituloRutina(rid, it) }, label = { Text("Nombre") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        var abierto by remember { mutableStateOf(false) }
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Día de la semana: ", color = f.texto)
            Box {
                TextButton({ abierto = true }) { Text(r.dow?.let { Biblioteca.DIAS_LARGOS[it] } ?: "Sin día fijo") }
                DropdownMenu(abierto, { abierto = false }) {
                    DropdownMenuItem({ Text("Sin día fijo") }, { abierto = false; vm.diaRutina(rid, null) })
                    Biblioteca.DIAS_LARGOS.forEachIndexed { i, n -> DropdownMenuItem({ Text(n) }, { abierto = false; vm.diaRutina(rid, i) }) }
                }
            }
        }
        Seccion("Ejercicios (${r.items.size})") {
            if (r.items.isEmpty()) EstadoVacio(RId.drawable.personaje_el_forzudo, "Rutina vacía", "Agrega tu primer ejercicio.")
            r.items.forEachIndexed { i, it ->
                val c = vm.bib.cfg(it)
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(14.dp)).background(f.superficie).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Miniatura(c.e, vm.bib)
                        Column(Modifier.weight(1f)) {
                            Text(c.e.n, fontWeight = FontWeight.Bold, color = f.texto)
                            Text("${c.e.eq} · ${vm.bib.nombreMusculo(c.e.m)}", style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Triple("sets", "Series", c.sets), Triple("lo", "Reps mín.", c.lo), Triple("hi", "Reps máx.", c.hi), Triple("rest", "Descanso (s)", c.rest)).forEach { (campo, et, v) ->
                            Column(Modifier.weight(1f)) {
                                Text(et, style = MaterialTheme.typography.labelSmall, color = f.textoSuave, maxLines = 1)
                                CampoNumero("$v", "", "$et de ${c.e.n}", false, { t -> t.toIntOrNull()?.let { n -> vm.campoItem(rid, i, campo, n) } })
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton({ vm.moverItem(rid, i, -1) }, enabled = i > 0) { Text("↑ Subir") }
                        TextButton({ vm.moverItem(rid, i, 1) }, enabled = i < r.items.size - 1) { Text("↓ Bajar") }
                        TextButton({ vm.abrir(Pantalla.Cambiar(it.id, ModoCambio.EDITAR, rid, i)) }) { Text("Cambiar") }
                        TextButton({ confirmar("¿Quitar ${c.e.n} de la rutina?", "Su historial se conserva.") { vm.quitarItem(rid, i) } }) {
                            Text("Quitar", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BotonRiso("+ Agregar ejercicio", { vm.abrir(Pantalla.Cambiar(null, ModoCambio.AGREGAR, rid)) }, Modifier.fillMaxWidth())
            BotonSecundario("Eliminar esta rutina", { confirmar("¿Eliminar esta rutina?", "Tu historial se conserva.") { vm.eliminarRutina(rid) } }, Modifier.fillMaxWidth(), peligro = true)
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
        Cabecera(if (actual != null) "Cambiar ejercicio" else "Agregar ejercicio", vm::cerrar)
        if (actual != null) Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Miniatura(actual, vm.bib)
            Column { Text("Actual", style = MaterialTheme.typography.labelSmall, color = f.textoSuave); Text(actual.n, fontWeight = FontWeight.Bold, color = f.texto) }
        }
        if (p.modo == ModoCambio.VIVO && p.rid != null) Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(guardar, { guardar = it })
            Text("Guardar el cambio también en la rutina (si no, solo aplica hoy)", style = MaterialTheme.typography.bodySmall, color = f.texto)
        }
        OutlinedTextField(q, { q = it }, label = { Text("Buscar ejercicio…") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(16.dp))
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
            if (alt.variantes.isNotEmpty()) Seccion("Lo mismo, de otra forma") { Pista("Mismo movimiento con otro equipo o agarre"); alt.variantes.forEach { fila(it) } }
            if (alt.relacionados.isNotEmpty()) Seccion("Ejercicios relacionados") { Pista("Trabajan el mismo músculo con otro movimiento"); alt.relacionados.forEach { fila(it) } }
            Pista("¿Buscas otro? Escribe arriba para ver toda la biblioteca.", centro = true)
        } else {
            val todos = vm.bib.ejercicios.filter { it.id != p.id && it.id != "press-banca-ligero" && (texto.isEmpty() || paraBuscar("${it.n} ${it.eq} ${vm.bib.nombreMusculo(it.m)}").contains(texto)) }
            vm.bib.musculos.forEach { m -> val xs = todos.filter { it.m == m.id }; if (xs.isNotEmpty()) Seccion(m.n) { xs.forEach { fila(it) } } }
            if (todos.isEmpty()) Pista("No encontré ese ejercicio.", centro = true)
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
            TextButton(vm::cerrar) { Text("Cerrar", color = Color.White) }
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
            FotoAsset(vm.bib.foto(id, i), "${ej.n}: posición ${if (i == 0) "inicial" else "final"}",
                Modifier.fillMaxWidth().graphicsLayer { scaleX = escala; scaleY = escala; translationX = desp.x; translationY = desp.y },
                ContentScale.Fit)
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(0 to "1 · Inicio", 1 to "2 · Final").forEach { (k, t) ->
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
        Cabecera("Acerca de", vm::cerrar)
        AcercaDeFogon(
            personaje = RId.drawable.personaje_el_forzudo,
            nombreApp = "El Forzudo",
            nombrePersonaje = "El Forzudo",
            version = BuildConfig.VERSION_NAME,
            creditos = listOf(
                "Dibujos de ejercicios: Everkinetic (CC BY-SA 4.0), pasados a lámina riso por Fogón.",
                "Modelo muscular: react-body-highlighter y react-native-body-highlighter (licencia MIT).",
                "Estándares de fuerza: modelo propio de Fogón, calibrado con OpenPowerlifting (dominio público).",
                "Letras: Bagel Fat One y Anton (SIL Open Font License).",
            ),
            onVerManifiesto = { vm.abrir(Pantalla.Manifiesto) },
            onVerLicencias = { vm.abrir(Pantalla.Licencias) },
            onAbrirEnlace = { uri.openUri(it) },
            avisoSalud = AVISO_SALUD,
        )
        Spacer(Modifier.height(40.dp))
    }
}

const val AVISO_SALUD =
    "El Forzudo es un cuaderno de entrenos, no un consejo médico. Las rutinas, metas y rangos son guías generales que no conocen tu salud. " +
        "Si tienes una lesión, una enfermedad o hace mucho que no entrenas, consulta a un profesional de la salud antes de empezar. " +
        "Si sientes dolor, mareo o falta de aire, detente. Usas la app bajo tu propia responsabilidad."

@Composable
fun Licencias(vm: GymViewModel) {
    Column {
        Cabecera("Licencias", vm::cerrar)
        LicenciasFogon(
            propias = listOf(
                Licencia(
                    "Modelo muscular",
                    "Dibujo del cuerpo de react-body-highlighter (© 2020 GV79), que lo tomó de react-native-body-highlighter (© 2022 ELABBASSI Hicham). Licencia MIT.",
                    "mit-react-body-highlighter.txt",
                ),
                Licencia("react-native-body-highlighter", "© 2022 ELABBASSI Hicham. Licencia MIT.", "mit-react-native-body-highlighter.txt"),
                Licencia(
                    "OpenPowerlifting",
                    "Esta app usa datos del proyecto OpenPowerlifting, https://www.openpowerlifting.org. Puedes descargar una copia de los datos en https://gitlab.com/openpowerlifting/opl-data. Dominio público.",
                ),
                Licencia("Everkinetic", "Ilustraciones de los ejercicios, de github.com/everkinetic/data (CC BY-SA 4.0) y, las de aperturas y press de hombro en máquina, de Wikimedia Commons (CC BY-SA 3.0, creativecommons.org/licenses/by-sa/3.0, que permite compartir la modificación con la 4.0). Fogón las modificó (lámina riso); las láminas se comparten con la misma licencia. Creative Commons Atribución-CompartirIgual 4.0.", "cc-by-sa-4.0.txt"),
            ),
        )
        Spacer(Modifier.height(40.dp))
    }
}

/* =================== Preguntas frecuentes =================== */

/** Las mismas preguntas del README de la app. Si cambia una, cambian las dos. */
private val PREGUNTAS = listOf(
    "¿Por qué los dibujos solo tienen figuras de hombre?" to
        "Los dibujos salen de Everkinetic, un banco de ilustraciones de ejercicios con licencia libre, y todas sus figuras son de hombre. No encontramos ilustraciones libres con figuras de mujer. Probamos generarlas con inteligencia artificial, pero no quedaban al mismo nivel ni en el mismo estilo, y preferimos no poner algo a medias. El ejercicio y el músculo que trabaja son los mismos para todas las personas. Si alguien quiere dibujar las versiones de mujer con licencia libre, son bienvenidas.",
    "¿Por qué algunos ejercicios no tienen dibujo?" to
        "Usamos solo dibujos con licencia clara, y para esos ejercicios todavía no hay uno. Van a llegar en una actualización.",
    "¿Por qué no hay fotos?" to
        "Las fotos que usaba la app venían de un banco que se presentaba como libre, pero en realidad eran fotos sacadas de internet sin permiso de sus dueños. Por eso se cambiaron por dibujos con licencia clara.",
    "¿Qué significan los colores?" to
        "Cada grupo muscular tiene su color, el mismo en toda la app: la mancha del dibujo marca el músculo que trabaja. La sombra del dibujo dice la zona: rosa para el tren superior, amarillo para los brazos y azul para las piernas.",
    "¿De dónde salen los rangos de fuerza?" to
        "De una tabla propia de Fogón, calibrada con resultados reales de competencias de powerlifting (OpenPowerlifting, de dominio público). Los demás ejercicios se estiman a partir de banca, sentadilla y peso muerto, así que en máquinas el rango es aproximado.",
    "¿Mis datos salen del teléfono?" to
        "No. Todo se guarda en el teléfono. Solo si conectas tu propio GitHub, los entrenos se copian a un archivo privado de tu cuenta.",
    "¿Por qué antes se llamaba Mi Gym?" to
        "Hasta la versión 1.2.1 se llamó así. Desde la 1.3 lleva el nombre de su personaje, El Forzudo, como todas las apps de Fogón.",
)

@Composable
fun Preguntas(vm: GymViewModel) {
    val f = FogonTema.actual
    Column {
        Cabecera("Preguntas frecuentes", vm::cerrar)
        PREGUNTAS.forEach { (p, r) ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(p, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = f.texto)
                Text(r, style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

/** Minúsculas y sin tildes, para que "bulgara" encuentre "búlgara". */
private fun paraBuscar(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

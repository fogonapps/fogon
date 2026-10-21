package io.github.fogonapps.forzudo.ui

import io.github.fogonapps.identidad.Idioma
import androidx.compose.material3.FilterChip
import io.github.fogonapps.forzudo.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.Chispa
import io.github.fogonapps.identidad.EstadoVacio
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.R as RId
import io.github.fogonapps.identidad.sombraRiso
import io.github.fogonapps.forzudo.EstadoSync
import io.github.fogonapps.forzudo.GymViewModel
import io.github.fogonapps.forzudo.Pantalla
import io.github.fogonapps.forzudo.Pestana
import io.github.fogonapps.forzudo.Sync
import io.github.fogonapps.forzudo.datos.Vocabulario
import io.github.fogonapps.forzudo.datos.Biblioteca
import io.github.fogonapps.forzudo.datos.ClienteGist
import io.github.fogonapps.forzudo.datos.Estado
import io.github.fogonapps.forzudo.datos.Sesion
import io.github.fogonapps.forzudo.datos.duracionPalabras
import io.github.fogonapps.forzudo.datos.etiquetaSemana
import io.github.fogonapps.forzudo.datos.fechaCorta
import io.github.fogonapps.forzudo.datos.fk
import io.github.fogonapps.forzudo.datos.fmtVol
import io.github.fogonapps.forzudo.datos.inicioSemana
import io.github.fogonapps.forzudo.datos.iso
import io.github.fogonapps.forzudo.datos.semanasDelMes

/* =================== Inicio =================== */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Inicio(vm: GymViewModel, e: Estado) {
    val f = FogonTema.actual
    val L = vm.L
    val dias = L.dias(e)
    val hoy = vm.hoy()
    val td = iso(hoy); val ws = inicioSemana(td)
    val hechos = e.sessions.filter { inicioSemana(it.date) == ws }.map { it.day }.toSet()
    val mes = td.take(7)
    val racha = L.racha(e, dias, hoy)
    val confirmar = LocalConfirmar.current

    Column {
        // Racha
        Row(
            Modifier.fillMaxWidth().padding(16.dp).sombraRiso(f.paleta.segunda, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(f.superficie).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box {
                io.github.fogonapps.identidad.Personaje(RId.drawable.personaje_el_forzudo, null, tamano = 72.dp)
                if (racha.actual > 0) Chispa(Modifier.align(Alignment.TopEnd), tamano = 22.dp)
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${racha.actual}", style = MaterialTheme.typography.displayLarge, color = f.acentoTexto)
                    Text(
                        pluralStringResource(R.plurals.semanas_seguidas, racha.actual).uppercase(),
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 18.sp, lineHeight = 20.sp), color = f.texto,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Text(
                    stringResource(if (racha.actual > 0) R.string.racha_cumpliendo else R.string.racha_empezar) +
                        " · " + stringResource(R.string.racha_mejor, racha.mejor),
                    style = MaterialTheme.typography.bodySmall, color = f.textoSuave,
                )
            }
        }

        Seccion(stringResource(R.string.esta_semana, hechos.size, pluralStringResource(R.plurals.entrenos, dias.size, dias.size))) {
            val hoyId = L.diaDeHoy(dias, hoy)
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val estHecho = stringResource(R.string.dia_hecho); val estPendiente = stringResource(R.string.dia_pendiente)
                dias.forEachIndexed { i, d ->
                    val hecho = d.id in hechos
                    Column(
                        Modifier.width(64.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (hecho) f.paleta.tinta else f.superficie)
                            .then(if (d.id == hoyId) Modifier.border(2.dp, f.paleta.segunda, RoundedCornerShape(12.dp)) else Modifier)
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                            .semantics { stateDescription = if (hecho) estHecho else estPendiente },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val c = if (hecho) f.paleta.sobreTinta else f.texto
                        Text(d.dow?.let { Vocabulario.actual.diasCortos[it] } ?: "${i + 1}", style = MaterialTheme.typography.labelLarge, color = c)
                        Text(d.title, style = MaterialTheme.typography.labelSmall, color = c, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        Seccion(stringResource(R.string.semanas_de, Vocabulario.actual.mesesLargos[mes.takeLast(2).toInt() - 1])) {
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val estCompleta = stringResource(R.string.semana_completa); val estIncompleta = stringResource(R.string.semana_incompleta)
                semanasDelMes(mes).forEachIndexed { i, w ->
                    val hecha = L.semanaHecha(e, w, dias)
                    Column(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .background(if (hecha) f.paleta.tinta else f.superficie)
                            .semantics { stateDescription = if (hecha) estCompleta else estIncompleta }
                            .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        val c = if (hecha) f.paleta.sobreTinta else f.texto
                        Text(stringResource(R.string.semana_corta, i + 1), style = MaterialTheme.typography.labelLarge, color = c)
                        Text(etiquetaSemana(w), style = MaterialTheme.typography.labelSmall, color = c)
                    }
                }
            }
            Pista(stringResource(R.string.pista_semanas))
        }

        val meses = (e.sessions.map { it.date.take(7) } + e.weekMarks.keys.map { it.take(7) }).toSortedSet().toList().asReversed().filter { it != mes }
        if (meses.isNotEmpty()) Seccion(stringResource(R.string.meses_anteriores)) {
            meses.forEach { m ->
                val w = semanasDelMes(m)
                val dn = w.count { L.semanaHecha(e, it, dias) }
                val n = e.sessions.count { it.date.startsWith(m) }
                Fila {
                    Text("${Vocabulario.actual.mesesLargos[m.takeLast(2).toInt() - 1].replaceFirstChar { it.uppercase() }} ${m.take(4)}", Modifier.weight(1f), color = f.texto)
                    Text(stringResource(R.string.mes_resumen, dn, pluralStringResource(R.plurals.semanas, w.size, w.size), pluralStringResource(R.plurals.entrenos, n, n)), fontWeight = FontWeight.Bold, color = f.texto, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Seccion(stringResource(R.string.titulo_entrenos)) {
            val lista = e.sessions.sortedByDescending { it.date }
            if (lista.isEmpty()) {
                EstadoVacio(RId.drawable.personaje_el_forzudo, stringResource(R.string.sin_entrenos), stringResource(R.string.sin_entrenos_texto))
            } else lista.forEach { s ->
                TarjetaEntreno(vm, s, dias) { confirmar(vm.tx(R.string.borrar_entreno_pregunta), null) { vm.borrarSesion(s.id) } }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TarjetaEntreno(vm: GymViewModel, s: Sesion, dias: List<io.github.fogonapps.forzudo.datos.Dia>, onBorrar: () -> Unit) {
    val f = FogonTema.actual
    val L = vm.L
    val v = L.volumen(s.entries); val n = L.series(s.entries)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).sombraRiso(f.paleta.segunda.copy(alpha = 0.35f), desplazamiento = 3.dp)
        .clip(RoundedCornerShape(14.dp)).background(f.superficie).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(L.tituloRutina(s, dias), style = MaterialTheme.typography.titleLarge, color = f.texto, modifier = Modifier.weight(1f))
            Text(fechaCorta(s.date), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            s.ms?.let { Dato(stringResource(R.string.tiempo), duracionPalabras(it)) }
            Dato(stringResource(R.string.volumen), if (v > 0) fmtVol(v) else "–")
            Dato(stringResource(R.string.series), "$n")
        }
        val ents = s.entries.entries.toList()
        ents.take(4).forEach { (k, a) ->
            Text("${a.size} × ${vm.bib.ex[k]?.n ?: k}", style = MaterialTheme.typography.bodyMedium, color = f.texto)
        }
        if (ents.size > 4) Text(pluralStringResource(R.plurals.ejercicios_mas, ents.size - 4, ents.size - 4), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BotonFecha(stringResource(R.string.fecha), s.date, vm.hoy(), { vm.fechaSesion(s.id, it) }, Modifier.weight(1f))
            TextButton(onBorrar) { Text(stringResource(R.string.borrar_entreno), color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
fun Dato(k: String, v: String) {
    val f = FogonTema.actual
    Column {
        Text(k, style = MaterialTheme.typography.labelSmall, color = f.textoSuave)
        Text(v, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = f.texto)
    }
}

/* =================== Entrenar =================== */

@Composable
fun Entrenar(vm: GymViewModel, e: Estado) {
    val f = FogonTema.actual
    val dias = vm.L.dias(e)
    val hoyId = vm.L.diaDeHoy(dias, vm.hoy())
    val prog = vm.bib.programas.firstOrNull { it.key == e.program }
    val confirmar = LocalConfirmar.current
    Column {
        Seccion(stringResource(R.string.programa)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text((prog?.name ?: stringResource(R.string.personalizado)) + if (prog != null && e.progEdited) " · " + stringResource(R.string.modificado) else "",
                        style = MaterialTheme.typography.titleLarge, color = f.texto)
                    Text(prog?.let { "${it.level} · ${it.goal} · " + pluralStringResource(R.plurals.dias, dias.size, dias.size) } ?: pluralStringResource(R.plurals.rutinas, dias.size, dias.size),
                        style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                }
                BotonSecundario(stringResource(R.string.cambiar), { vm.abrir(Pantalla.Programas) })
            }
        }
        Seccion(stringResource(R.string.mis_rutinas, dias.size)) {
            dias.forEach { d ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(14.dp))
                    .background(f.superficie).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(d.title, style = MaterialTheme.typography.titleLarge, color = f.texto, modifier = Modifier.weight(1f))
                        if (hoyId == d.id) Etiqueta(stringResource(R.string.hoy), f.paleta.tinta, f.paleta.sobreTinta)
                        else if (d.dow != null) Text(Vocabulario.actual.diasLargos[d.dow!!], style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                    }
                    Text(d.ejercicios.joinToString(", ") { it.e.n }.ifEmpty { stringResource(R.string.sin_ejercicios) },
                        style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val a = e.active
                        when {
                            a == null -> BotonRiso(stringResource(R.string.empezar), { vm.empezar(d.id) }, Modifier.weight(1f), habilitado = d.ejercicios.isNotEmpty())
                            a.day == d.id -> BotonRiso(stringResource(R.string.volver_al_entreno), { vm.abrir(Pantalla.Vivo) }, Modifier.weight(1f))
                            else -> BotonSecundario(stringResource(R.string.pasar_entreno), {
                                val perdidas = vm.seriesQueSePierden(d.id)
                                confirmar(vm.tx(R.string.pasar_entreno_pregunta, d.title),
                                    if (perdidas > 0) vm.tx(R.string.series_se_pierden) else null) {
                                    vm.cambiarRutinaActiva(d.id); vm.abrir(Pantalla.Vivo)
                                }
                            }, Modifier.weight(1f))
                        }
                        BotonSecundario(stringResource(R.string.editar), { vm.abrir(Pantalla.Editor(d.id)) })
                    }
                }
            }
            Box(Modifier.padding(16.dp)) { BotonSecundario(stringResource(R.string.mas_nueva_rutina), { vm.nuevaRutina() }, Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
fun Etiqueta(texto: String, fondo: Color, color: Color) {
    Text(texto, style = MaterialTheme.typography.labelMedium, color = color,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(fondo).padding(horizontal = 10.dp, vertical = 4.dp))
}

/* =================== Rangos =================== */

@Composable
fun Rangos(vm: GymViewModel, e: Estado) {
    val f = FogonTema.actual
    val L = vm.L
    val bw = L.pesoCorporal(e)
    var modo by rememberSaveable { mutableStateOf("rango") }
    val ov = L.rangoGeneral(e)
    val ws = L.seriesSemana(e, vm.hoy())
    val mr = vm.bib.musculos.associate { it.id to L.rangoMusculo(e, it) }
    Column {
        if (bw == null) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.rangos_falta_peso), color = f.texto)
                BotonRiso(stringResource(R.string.registrar_peso), { vm.irA(Pestana.PERFIL) })
            }
        } else {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Insignia(ov?.rango, 62.dp)
                Column {
                    Text(stringResource(R.string.rango_general), style = MaterialTheme.typography.labelMedium, color = f.textoSuave)
                    Text(ov?.rango?.nombre ?: stringResource(R.string.sin_datos_aun), style = MaterialTheme.typography.headlineSmall,
                        color = ov?.rango?.let { colorHex(it.color) } ?: f.textoSuave)
                    Text(ov?.let { pluralStringResource(R.plurals.promedio_grupos, it.cuenta, it.cuenta, fk(bw)) }
                        ?: stringResource(R.string.completa_entreno), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                }
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            SegmentedButton(modo == "rango", { modo = "rango" }, SegmentedButtonDefaults.itemShape(0, 2), colors = coloresSegmento()) { Text(stringResource(R.string.pestana_rangos)) }
            SegmentedButton(modo == "semana", { modo = "semana" }, SegmentedButtonDefaults.itemShape(1, 2), colors = coloresSegmento()) { Text(stringResource(R.string.series_semana)) }
        }
        val relleno: (String) -> Color? = if (modo == "rango") { g -> mr[g]?.rango?.let { colorHex(it.color) } }
        else { g -> ws[g]?.let { n -> f.paleta.tinta.copy(alpha = if (n >= 10) 1f else if (n >= 5) 0.62f else 0.32f) } }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                MapaCuerpo(vm.bib.cuerpo.front, relleno, stringResource(R.string.vista_frente), Modifier.fillMaxWidth())
                Text(stringResource(R.string.frente), style = MaterialTheme.typography.labelMedium, color = f.textoSuave)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                MapaCuerpo(vm.bib.cuerpo.back, relleno, stringResource(R.string.vista_espalda), Modifier.fillMaxWidth())
                Text(stringResource(R.string.espalda), style = MaterialTheme.typography.labelMedium, color = f.textoSuave)
            }
        }
        if (modo == "rango") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                vm.bib.niveles.forEach { n ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(colorHex(n.c)))
                        Text(" ${n.n}", style = MaterialTheme.typography.labelSmall, color = f.textoSuave)
                    }
                }
            }
        } else Pista(stringResource(R.string.pista_series), centro = true)

        Seccion(stringResource(if (modo == "rango") R.string.rango_por_musculo else R.string.series_por_musculo)) {
            vm.bib.musculos.forEach { m ->
                if (modo == "semana") {
                    val n = ws[m.id] ?: 0
                    Fila {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(m.n, fontWeight = FontWeight.Bold, color = f.texto)
                            Barra(n / 10f, f.paleta.tinta)
                        }
                        Text("$n", style = MaterialTheme.typography.displaySmall, color = f.texto)
                    }
                } else {
                    val r = mr[m.id]
                    if (r == null) Fila {
                        Insignia(null, 34.dp)
                        Column(Modifier.weight(1f)) {
                            Text(m.n, fontWeight = FontWeight.Bold, color = f.texto)
                            Text(stringResource(when { bw == null -> R.string.falta_peso; m.id == "abdomen" -> R.string.abdomen_crunch; else -> R.string.sin_registros_todavia }),
                                style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                        }
                    } else Fila({ vm.abrir(Pantalla.Musculo(m.id)) }) {
                        Insignia(r.rango, 34.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(m.n, fontWeight = FontWeight.Bold, color = f.texto)
                            Text(r.rango.nombre, color = colorHex(r.rango.color), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Barra(L.progresoDivision(r.rango.s).toFloat(), colorHex(r.rango.color))
                            Text(r.consejo?.let { stringResource(R.string.proxima_meta, fk(it.kg), it.reps, vm.bib.ex[it.id]?.n ?: "") } ?: stringResource(R.string.rango_maximo),
                                style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                        }
                        Icon(Iconos.Siguiente, null, tint = f.textoSuave)
                    }
                }
            }
        }
        Seccion(stringResource(R.string.como_se_calcula)) {
            stringArrayResource(R.array.como_se_calcula_pasos).forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = f.texto, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            Pista(stringResource(R.string.creditos_rangos))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun coloresSegmento() = FogonTema.actual.let { f ->
    SegmentedButtonDefaults.colors(
        activeContainerColor = f.paleta.tinta, activeContentColor = f.paleta.sobreTinta, activeBorderColor = f.texto,
        inactiveContainerColor = androidx.compose.ui.graphics.Color.Transparent, inactiveContentColor = f.texto, inactiveBorderColor = f.texto,
    )
}

/* =================== Perfil =================== */

@Composable
fun Perfil(vm: GymViewModel, e: Estado, sync: Sync, onExportar: () -> Unit, onImportar: () -> Unit) {
    val f = FogonTema.actual
    val L = vm.L
    val total = e.sessions.sumOf { L.volumen(it.entries) }
    val pesos = e.body.sortedBy { it.date }
    val confirmar = LocalConfirmar.current
    var editandoNombre by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(f.paleta.tinta), contentAlignment = Alignment.Center) {
                Text((e.nombre ?: "?").take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, color = f.paleta.sobreTinta)
            }
            Column(Modifier.weight(1f)) {
                Text(e.nombre ?: stringResource(R.string.sin_nombre), style = MaterialTheme.typography.headlineSmall, color = f.texto)
                Text(pluralStringResource(R.plurals.entrenos_guardados, e.sessions.size, e.sessions.size), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
            TextButton({ editandoNombre = true }) { Text(stringResource(R.string.cambiar_nombre)) }
        }
        if (editandoNombre) DialogoNombre(e.nombre ?: "", { vm.ponerNombre(it); editandoNombre = false }) { editandoNombre = false }
        Trio(stringResource(R.string.titulo_entrenos) to "${e.sessions.size}", stringResource(R.string.volumen_total) to (if (total > 0) fmtVol(total) else "–"), stringResource(R.string.peso) to (pesos.lastOrNull()?.let { fk(it.kg) + " kg" } ?: "–"))

        Seccion(stringResource(R.string.peso_corporal_titulo)) {
            var texto by remember { mutableStateOf("") }
            // Cambia cada vez que se guarda, para que el campo quede vacío.
            var guardados by remember { mutableIntStateOf(0) }
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                key(guardados) { CampoNumero("", stringResource(R.string.tu_peso_kg), stringResource(R.string.peso_corporal_kg), true, { texto = it }, Modifier.weight(1f)) }
                BotonRiso(stringResource(R.string.guardar), { if (vm.registrarPeso(texto)) { texto = ""; guardados++ } })
            }
            if (pesos.isEmpty()) Pista(stringResource(R.string.meta_peso))
            else {
                Grafica(pesos.map { it.date to it.kg })
                pesos.reversed().take(5).forEach { p ->
                    Fila { Text(fechaCorta(p.date), Modifier.weight(1f), color = f.texto); Text("${fk(p.kg)} kg", fontWeight = FontWeight.Bold, color = f.texto) }
                }
            }
        }

        Seccion(stringResource(R.string.constancia)) {
            val anioHoy = vm.hoy().year
            val desde = minOf(anioHoy, e.sessions.minOfOrNull { it.date.take(4).toInt() } ?: anioHoy)
            val dias = e.sessions.map { it.date }.toSet()
            for (a in anioHoy downTo desde) MapaAnual(a, dias, vm.hoy())
            Pista(stringResource(R.string.pista_mapa_anual))
        }

        Seccion(stringResource(R.string.idioma)) { SelectorIdioma(vm) }

        Seccion(stringResource(R.string.sync_titulo)) { CajaSync(vm, sync, confirmar) }

        Seccion(stringResource(R.string.respaldo_titulo)) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BotonSecundario(stringResource(R.string.exportar), onExportar, Modifier.weight(1f))
                BotonSecundario(stringResource(R.string.importar), onImportar, Modifier.weight(1f))
            }
            Pista(stringResource(R.string.pista_respaldo))
        }

        val usados = (L.dias(e).flatMap { d -> d.rutina.items.map { it.id } } + e.sessions.flatMap { it.entries.keys }).toSet()
        Seccion(stringResource(R.string.ejercicios)) {
            vm.bib.ejercicios.filter { it.id in usados }.forEach { ej ->
                val mk = L.maxKg(e, ej.id); val mxr = L.maxReps(e, ej.id); val r = L.rangoEjercicio(e, ej.id)
                Fila({ vm.abrir(Pantalla.Detalle(ej.id)) }) {
                    Miniatura(ej, vm.bib)
                    Column(Modifier.weight(1f)) {
                        Text(ej.n, fontWeight = FontWeight.Bold, color = f.texto)
                        Row {
                            r?.let { Text(it.rango.nombre + " · ", color = colorHex(it.rango.color), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall) }
                            Text(
                                if (ej.kind == "w") mk?.let { stringResource(R.string.record_kg, fk(it)) } ?: stringResource(R.string.sin_registros)
                                else mxr?.let { stringResource(R.string.record, fk(it), L.unidad(ej)) } ?: stringResource(R.string.sin_registros),
                                style = MaterialTheme.typography.bodySmall, color = f.textoSuave,
                            )
                        }
                    }
                    Icon(Iconos.Siguiente, null, tint = f.textoSuave)
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonSecundario(stringResource(R.string.ver_tutorial), { vm.verTutorial() }, Modifier.fillMaxWidth())
            BotonSecundario(stringResource(R.string.manifiesto_fogon), { vm.abrir(Pantalla.Manifiesto) }, Modifier.fillMaxWidth())
            BotonSecundario(stringResource(R.string.preguntas_frecuentes), { vm.abrir(Pantalla.Preguntas) }, Modifier.fillMaxWidth())
            BotonSecundario(stringResource(R.string.reportar_boton), { vm.abrir(Pantalla.Reporte) }, Modifier.fillMaxWidth())
            BotonSecundario(stringResource(R.string.acerca_de), { vm.abrir(Pantalla.AcercaDe) }, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SelectorIdioma(vm: GymViewModel) {
    val ctx = LocalContext.current
    val elegido = remember { Idioma.elegido(ctx) }
    val opciones = listOf("" to stringResource(R.string.idioma_telefono)) + Idioma.DISPONIBLES
    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        opciones.forEach { (codigo, nombre) ->
            FilterChip(codigo == elegido, {
                if (codigo != elegido) {
                    vm.cambiarIdioma(codigo)
                    (ctx as? android.app.Activity)?.recreate()
                }
            }, { Text(nombre) })
        }
    }
}

@Composable
private fun CajaSync(vm: GymViewModel, s: Sync, confirmar: Confirmar) {
    val f = FogonTema.actual
    val uri = LocalUriHandler.current
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (s.estado == EstadoSync.APAGADA) {
            Text(stringResource(R.string.sync_explica),
                style = MaterialTheme.typography.bodyMedium, color = f.texto)
            Text(stringResource(R.string.sync_pasos),
                style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            BotonSecundario(stringResource(R.string.crear_token), { uri.openUri(ClienteGist.URL_TOKEN) })
            var token by remember { mutableStateOf("") }
            OutlinedTextField(token, { token = it.trim() }, label = { Text(stringResource(R.string.pega_token)) },
                singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            BotonRiso(stringResource(R.string.conectar), { vm.conectar(token) })
        } else {
            val txt = when (s.estado) {
                EstadoSync.SINCRONIZANDO -> stringResource(R.string.sincronizando)
                EstadoSync.SIN_CONEXION -> stringResource(R.string.sin_conexion)
                EstadoSync.ERROR -> s.mensaje ?: stringResource(R.string.error_sync)
                else -> (s.usuario?.let { stringResource(R.string.conectado_como, it) } ?: stringResource(R.string.conectado)) + " " + stringResource(R.string.ultima_sync, hace(s.ultima))
            }
            Text(txt, style = MaterialTheme.typography.bodyMedium,
                color = if (s.estado == EstadoSync.ERROR) MaterialTheme.colorScheme.error else f.texto)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BotonSecundario(stringResource(R.string.sincronizar_ahora), { vm.sincronizar() })
                BotonSecundario(stringResource(R.string.desconectar), {
                    confirmar(vm.tx(R.string.desconectar_pregunta), vm.tx(R.string.desconectar_texto)) { vm.desconectar() }
                }, peligro = true)
            }
        }
    }
}

@Composable
fun hace(t: Long): String {
    if (t == 0L) return stringResource(R.string.hace_nunca)
    val m = Math.round((System.currentTimeMillis() - t) / 60000.0)
    if (m < 1) return stringResource(R.string.hace_momento)
    if (m < 60) return stringResource(R.string.hace_min, m)
    val h = Math.round(m / 60.0)
    if (h < 24) return stringResource(R.string.hace_h, h)
    val d = Math.round(h / 24.0).toInt()
    return pluralStringResource(R.plurals.hace_dias, d, d)
}

@Composable
fun DialogoNombre(actual: String, onListo: (String) -> Unit, onCerrar: () -> Unit) {
    var t by remember { mutableStateOf(actual) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(stringResource(R.string.tut_nombre)) },
        text = { OutlinedTextField(t, { t = it.take(30) }, singleLine = true, label = { Text(stringResource(R.string.tu_nombre)) }) },
        confirmButton = { TextButton({ onListo(t) }) { Text(stringResource(R.string.guardar)) } },
        dismissButton = { TextButton(onCerrar) { Text(stringResource(R.string.cancelar)) } },
    )
}


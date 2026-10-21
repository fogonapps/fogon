package io.github.fogonapps.forzudo.ui

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
                        (if (racha.actual == 1) "semana seguida" else "semanas seguidas").uppercase(),
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 18.sp, lineHeight = 20.sp), color = f.texto,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Text(
                    (if (racha.actual > 0) "cumpliendo tu semana" else "Completa todos tus entrenos de la semana para empezar tu racha") +
                        " · Mejor racha: ${racha.mejor}",
                    style = MaterialTheme.typography.bodySmall, color = f.textoSuave,
                )
            }
        }

        Seccion("Esta semana: ${hechos.size} de ${plural(dias.size, "entreno")}") {
            val hoyId = L.diaDeHoy(dias, hoy)
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                dias.forEachIndexed { i, d ->
                    val hecho = d.id in hechos
                    Column(
                        Modifier.width(64.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (hecho) f.paleta.tinta else f.superficie)
                            .then(if (d.id == hoyId) Modifier.border(2.dp, f.paleta.segunda, RoundedCornerShape(12.dp)) else Modifier)
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                            .semantics { stateDescription = if (hecho) "hecho" else "pendiente" },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val c = if (hecho) f.paleta.sobreTinta else f.texto
                        Text(d.dow?.let { Biblioteca.DIAS_CORTOS[it] } ?: "${i + 1}", style = MaterialTheme.typography.labelLarge, color = c)
                        Text(d.title, style = MaterialTheme.typography.labelSmall, color = c, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        Seccion("Semanas de ${Biblioteca.MESES_LARGOS[mes.takeLast(2).toInt() - 1]}") {
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                semanasDelMes(mes).forEachIndexed { i, w ->
                    val hecha = L.semanaHecha(e, w, dias)
                    Column(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .background(if (hecha) f.paleta.tinta else f.superficie)
                            .semantics { stateDescription = if (hecha) "semana completa" else "semana sin completar" }
                            .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        val c = if (hecha) f.paleta.sobreTinta else f.texto
                        Text("Sem ${i + 1}", style = MaterialTheme.typography.labelLarge, color = c)
                        Text(etiquetaSemana(w), style = MaterialTheme.typography.labelSmall, color = c)
                    }
                }
            }
            Pista("Una semana se pinta sola cuando completas todas las rutinas de tu programa. La racha se gana entrenando.")
        }

        val meses = (e.sessions.map { it.date.take(7) } + e.weekMarks.keys.map { it.take(7) }).toSortedSet().toList().asReversed().filter { it != mes }
        if (meses.isNotEmpty()) Seccion("Meses anteriores") {
            meses.forEach { m ->
                val w = semanasDelMes(m)
                val dn = w.count { L.semanaHecha(e, it, dias) }
                val n = e.sessions.count { it.date.startsWith(m) }
                Fila {
                    Text("${Biblioteca.MESES_LARGOS[m.takeLast(2).toInt() - 1].replaceFirstChar { it.uppercase() }} ${m.take(4)}", Modifier.weight(1f), color = f.texto)
                    Text("$dn de ${plural(w.size, "semana")}, ${plural(n, "entreno")}", fontWeight = FontWeight.Bold, color = f.texto, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Seccion("Entrenos") {
            val lista = e.sessions.sortedByDescending { it.date }
            if (lista.isEmpty()) {
                EstadoVacio(RId.drawable.personaje_el_forzudo, "Aún no tienes entrenos", "Ve a Entrenar y empieza una rutina.")
            } else lista.forEach { s ->
                TarjetaEntreno(vm, s, dias) { confirmar("¿Borrar este entreno?", null) { vm.borrarSesion(s.id) } }
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
            s.ms?.let { Dato("Tiempo", duracionPalabras(it)) }
            Dato("Volumen", if (v > 0) fmtVol(v) else "–")
            Dato("Series", "$n")
        }
        val ents = s.entries.entries.toList()
        ents.take(4).forEach { (k, a) ->
            Text("${a.size} × ${vm.bib.ex[k]?.n ?: k}", style = MaterialTheme.typography.bodyMedium, color = f.texto)
        }
        if (ents.size > 4) Text("${ents.size - 4} ejercicios más", style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BotonFecha("Fecha", s.date, vm.hoy(), { vm.fechaSesion(s.id, it) }, Modifier.weight(1f))
            TextButton(onBorrar) { Text("Borrar entreno", color = MaterialTheme.colorScheme.error) }
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
        Seccion("Programa") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text((prog?.name ?: "Personalizado") + if (prog != null && e.progEdited) " · modificado" else "",
                        style = MaterialTheme.typography.titleLarge, color = f.texto)
                    Text(prog?.let { "${it.level} · ${it.goal} · ${dias.size} días" } ?: "${dias.size} rutinas",
                        style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                }
                BotonSecundario("Cambiar", { vm.abrir(Pantalla.Programas) })
            }
        }
        Seccion("Mis rutinas (${dias.size})") {
            dias.forEach { d ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(14.dp))
                    .background(f.superficie).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(d.title, style = MaterialTheme.typography.titleLarge, color = f.texto, modifier = Modifier.weight(1f))
                        if (hoyId == d.id) Etiqueta("Hoy", f.paleta.tinta, f.paleta.sobreTinta)
                        else if (d.dow != null) Text(Biblioteca.DIAS_LARGOS[d.dow!!], style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                    }
                    Text(d.ejercicios.joinToString(", ") { it.e.n }.ifEmpty { "Sin ejercicios todavía" },
                        style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val a = e.active
                        when {
                            a == null -> BotonRiso("Empezar", { vm.empezar(d.id) }, Modifier.weight(1f), habilitado = d.ejercicios.isNotEmpty())
                            a.day == d.id -> BotonRiso("Volver al entreno", { vm.abrir(Pantalla.Vivo) }, Modifier.weight(1f))
                            else -> BotonSecundario("Pasar el entreno actual aquí", {
                                val perdidas = vm.seriesQueSePierden(d.id)
                                confirmar("¿Cambiar el entreno en curso a ${d.title}?",
                                    if (perdidas > 0) "Las series ya marcadas de ejercicios que no están en esa rutina se pierden." else null) {
                                    vm.cambiarRutinaActiva(d.id); vm.abrir(Pantalla.Vivo)
                                }
                            }, Modifier.weight(1f))
                        }
                        BotonSecundario("Editar", { vm.abrir(Pantalla.Editor(d.id)) })
                    }
                }
            }
            Box(Modifier.padding(16.dp)) { BotonSecundario("+ Nueva rutina", { vm.nuevaRutina() }, Modifier.fillMaxWidth()) }
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
                Text("Para calcular tus rangos necesito tu peso corporal, porque la fuerza se compara en relación a lo que pesas.", color = f.texto)
                BotonRiso("Registrar mi peso", { vm.irA(Pestana.PERFIL) })
            }
        } else {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Insignia(ov?.rango, 62.dp)
                Column {
                    Text("Rango general", style = MaterialTheme.typography.labelMedium, color = f.textoSuave)
                    Text(ov?.rango?.nombre ?: "Sin datos aún", style = MaterialTheme.typography.headlineSmall,
                        color = ov?.rango?.let { colorHex(it.color) } ?: f.textoSuave)
                    Text(ov?.let { "Promedio de ${plural(it.cuenta, "grupo muscular", "grupos musculares")} · peso corporal ${fk(bw)} kg" }
                        ?: "Completa un entreno anotando peso y repeticiones", style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                }
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            SegmentedButton(modo == "rango", { modo = "rango" }, SegmentedButtonDefaults.itemShape(0, 2), colors = coloresSegmento()) { Text("Rangos") }
            SegmentedButton(modo == "semana", { modo = "semana" }, SegmentedButtonDefaults.itemShape(1, 2), colors = coloresSegmento()) { Text("Series esta semana") }
        }
        val relleno: (String) -> Color? = if (modo == "rango") { g -> mr[g]?.rango?.let { colorHex(it.color) } }
        else { g -> ws[g]?.let { n -> f.paleta.tinta.copy(alpha = if (n >= 10) 1f else if (n >= 5) 0.62f else 0.32f) } }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                MapaCuerpo(vm.bib.cuerpo.front, relleno, "Vista de frente", Modifier.fillMaxWidth())
                Text("Frente", style = MaterialTheme.typography.labelMedium, color = f.textoSuave)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                MapaCuerpo(vm.bib.cuerpo.back, relleno, "Vista de espalda", Modifier.fillMaxWidth())
                Text("Espalda", style = MaterialTheme.typography.labelMedium, color = f.textoSuave)
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
        } else Pista("Más intenso = más series. Para ganar músculo, apunta a unas 10 series por músculo a la semana.", centro = true)

        Seccion(if (modo == "rango") "Rango por músculo" else "Series por músculo esta semana") {
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
                            Text(when { bw == null -> "Falta tu peso corporal"; m.id == "abdomen" -> "Se mide con el crunch en polea"; else -> "Sin registros todavía" },
                                style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                        }
                    } else Fila({ vm.abrir(Pantalla.Musculo(m.id)) }) {
                        Insignia(r.rango, 34.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(m.n, fontWeight = FontWeight.Bold, color = f.texto)
                            Text(r.rango.nombre, color = colorHex(r.rango.color), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Barra(L.progresoDivision(r.rango.s).toFloat(), colorHex(r.rango.color))
                            Text(r.consejo?.let { "Próxima meta: ${fk(it.kg)} kg × ${it.reps} en ${vm.bib.ex[it.id]?.n}" } ?: "¡Rango máximo!",
                                style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                        }
                        Icon(Iconos.Siguiente, null, tint = f.textoSuave)
                    }
                }
            }
        }
        Seccion("¿Cómo se calcula?") {
            listOf(
                "1. De cada ejercicio tomo tu mejor serie y estimo tu máximo de 1 repetición (fórmula de Epley).",
                "2. Lo comparo con la tabla de fuerza de Fogón, ajustada a tu peso corporal. Banca, sentadilla y peso muerto se calibraron con miles de resultados de competencias de powerlifting (OpenPowerlifting); el resto de ejercicios se estima a partir de esos tres.",
                "3. Rangos: Hierro (empezando) · Bronce (principiante) · Plata (novato) · Oro (intermedio) · Platino (avanzado, al nivel de quien empieza a competir) · Diamante (élite, más fuerte que ~3 de cada 4 competidores). Cada rango tiene 3 divisiones: III, II y I.",
                "4. El rango de cada músculo es el promedio de sus 2 mejores ejercicios, y el general es el promedio de todos tus músculos.",
                "5. En mancuernas anota el peso de una mancuerna. En máquinas y poleas el rango es aproximado, porque el peso marcado cambia según la máquina. Plancha, elevaciones y zancadas sin peso no cuentan.",
            ).forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = f.texto, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            Pista("Estándares: modelo de Fogón con datos de OpenPowerlifting (dominio público) · Dibujos: Everkinetic (CC BY-SA 4.0) · Modelo muscular: react-body-highlighter (licencia MIT). Detalles en Perfil → Acerca de El Forzudo → Licencias")
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
                Text(e.nombre ?: "Sin nombre", style = MaterialTheme.typography.headlineSmall, color = f.texto)
                Text(plural(e.sessions.size, "entreno guardado", "entrenos guardados"), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
            TextButton({ editandoNombre = true }) { Text("Cambiar nombre") }
        }
        if (editandoNombre) DialogoNombre(e.nombre ?: "", { vm.ponerNombre(it); editandoNombre = false }) { editandoNombre = false }
        Trio("Entrenos" to "${e.sessions.size}", "Volumen total" to (if (total > 0) fmtVol(total) else "–"), "Peso" to (pesos.lastOrNull()?.let { fk(it.kg) + " kg" } ?: "–"))

        Seccion("Peso corporal (1 vez por semana, en ayunas)") {
            var texto by remember { mutableStateOf("") }
            // Cambia cada vez que se guarda, para que el campo quede vacío.
            var guardados by remember { mutableIntStateOf(0) }
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                key(guardados) { CampoNumero("", "Tu peso en kg", "Peso corporal en kg", true, { texto = it }, Modifier.weight(1f)) }
                BotonRiso("Guardar", { if (vm.registrarPeso(texto)) { texto = ""; guardados++ } })
            }
            if (pesos.isEmpty()) Pista("Meta: subir de 0,25 a 0,5 kg por semana.")
            else {
                Grafica(pesos.map { it.date to it.kg })
                pesos.reversed().take(5).forEach { p ->
                    Fila { Text(fechaCorta(p.date), Modifier.weight(1f), color = f.texto); Text("${fk(p.kg)} kg", fontWeight = FontWeight.Bold, color = f.texto) }
                }
            }
        }

        Seccion("Constancia") {
            val anioHoy = vm.hoy().year
            val desde = minOf(anioHoy, e.sessions.minOfOrNull { it.date.take(4).toInt() } ?: anioHoy)
            val dias = e.sessions.map { it.date }.toSet()
            for (a in anioHoy downTo desde) MapaAnual(a, dias, vm.hoy())
            Pista("Cada cuadrito es un día; los de color son días que entrenaste.")
        }

        Seccion("Sincronización en la nube") { CajaSync(vm, sync, confirmar) }

        Seccion("Respaldo de tus datos") {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BotonSecundario("Exportar", onExportar, Modifier.weight(1f))
                BotonSecundario("Importar", onImportar, Modifier.weight(1f))
            }
            Pista("Copia manual extra. Exporta un archivo y guárdalo donde quieras; si algún día lo necesitas, lo importas.")
        }

        val usados = (L.dias(e).flatMap { d -> d.rutina.items.map { it.id } } + e.sessions.flatMap { it.entries.keys }).toSet()
        Seccion("Ejercicios") {
            vm.bib.ejercicios.filter { it.id in usados }.forEach { ej ->
                val mk = L.maxKg(e, ej.id); val mxr = L.maxReps(e, ej.id); val r = L.rangoEjercicio(e, ej.id)
                Fila({ vm.abrir(Pantalla.Detalle(ej.id)) }) {
                    Miniatura(ej, vm.bib)
                    Column(Modifier.weight(1f)) {
                        Text(ej.n, fontWeight = FontWeight.Bold, color = f.texto)
                        Row {
                            r?.let { Text(it.rango.nombre + " · ", color = colorHex(it.rango.color), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall) }
                            Text(
                                if (ej.kind == "w") mk?.let { "Récord: ${fk(it)} kg" } ?: "Sin registros"
                                else mxr?.let { "Récord: ${fk(it)} ${L.unidad(ej)}" } ?: "Sin registros",
                                style = MaterialTheme.typography.bodySmall, color = f.textoSuave,
                            )
                        }
                    }
                    Icon(Iconos.Siguiente, null, tint = f.textoSuave)
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonSecundario("Ver el tutorial", { vm.verTutorial() }, Modifier.fillMaxWidth())
            BotonSecundario("Manifiesto de Fogón", { vm.abrir(Pantalla.Manifiesto) }, Modifier.fillMaxWidth())
            BotonSecundario("Preguntas frecuentes", { vm.abrir(Pantalla.Preguntas) }, Modifier.fillMaxWidth())
            BotonSecundario("Acerca de El Forzudo", { vm.abrir(Pantalla.AcercaDe) }, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CajaSync(vm: GymViewModel, s: Sync, confirmar: Confirmar) {
    val f = FogonTema.actual
    val uri = LocalUriHandler.current
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (s.estado == EstadoSync.APAGADA) {
            Text("Conecta tu GitHub y tus entrenos se guardan solos en un archivo privado de tu cuenta (un Gist secreto). Si cambias de teléfono o reinstalas la app, se recuperan. Es opcional: sin esto, la app no usa internet.",
                style = MaterialTheme.typography.bodyMedium, color = f.texto)
            Text("1. Toca Crear token (se abre GitHub con todo listo).\n2. En Expiration elige No expiration o 1 año.\n3. Baja, toca Generate token, cópialo y pégalo aquí.",
                style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            BotonSecundario("Crear token", { uri.openUri(ClienteGist.URL_TOKEN) })
            var token by remember { mutableStateOf("") }
            OutlinedTextField(token, { token = it.trim() }, label = { Text("Pega aquí tu token (ghp_…)") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            BotonRiso("Conectar", { vm.conectar(token) })
        } else {
            val txt = when (s.estado) {
                EstadoSync.SINCRONIZANDO -> "Sincronizando…"
                EstadoSync.SIN_CONEXION -> "Sin conexión"
                EstadoSync.ERROR -> s.mensaje ?: "Error al sincronizar"
                else -> "Conectado" + (s.usuario?.let { " como $it" } ?: "") + ". Última sincronización: ${hace(s.ultima)}."
            }
            Text(txt, style = MaterialTheme.typography.bodyMedium,
                color = if (s.estado == EstadoSync.ERROR) MaterialTheme.colorScheme.error else f.texto)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BotonSecundario("Sincronizar ahora", { vm.sincronizar() })
                BotonSecundario("Desconectar", {
                    confirmar("¿Desconectar la sincronización?", "Tus datos siguen en el teléfono y en tu Gist.") { vm.desconectar() }
                }, peligro = true)
            }
        }
    }
}

fun hace(t: Long): String {
    if (t == 0L) return "nunca"
    val m = Math.round((System.currentTimeMillis() - t) / 60000.0)
    if (m < 1) return "hace un momento"
    if (m < 60) return "hace $m min"
    val h = Math.round(m / 60.0)
    if (h < 24) return "hace $h h"
    return "hace ${Math.round(h / 24.0)} días"
}

@Composable
fun DialogoNombre(actual: String, onListo: (String) -> Unit, onCerrar: () -> Unit) {
    var t by remember { mutableStateOf(actual) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("¿Cómo te llamas?") },
        text = { OutlinedTextField(t, { t = it.take(30) }, singleLine = true, label = { Text("Tu nombre") }) },
        confirmButton = { TextButton({ onListo(t) }) { Text("Guardar") } },
        dismissButton = { TextButton(onCerrar) { Text("Cancelar") } },
    )
}


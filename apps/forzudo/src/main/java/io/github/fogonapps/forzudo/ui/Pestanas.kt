package io.github.fogonapps.forzudo.ui

import io.github.fogonapps.identidad.destinoGuia
import androidx.compose.foundation.Image
import androidx.compose.ui.geometry.Offset
import io.github.fogonapps.identidad.RespaldoTodoFogon
import io.github.fogonapps.identidad.tarjetaFogon
import io.github.fogonapps.identidad.FormaPildora
import io.github.fogonapps.identidad.forma
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
import androidx.compose.runtime.collectAsState
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
import kotlinx.coroutines.launch
import io.github.fogonapps.forzudo.datos.medallas
import androidx.compose.ui.unit.sp
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.Chispa
import io.github.fogonapps.identidad.EstadoVacio
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.haloPuntos
import io.github.fogonapps.identidad.aparecer
import io.github.fogonapps.identidad.R as RId
import io.github.fogonapps.identidad.brillo
import io.github.fogonapps.identidad.NumeroRiso
import androidx.compose.ui.graphics.Brush
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

    // La cara de El Forzudo según tu racha: decidido para empezar, feliz si va andando, sorprendido en tu mejor racha.
    val cara = when {
        racha.actual > 0 && racha.actual >= racha.mejor -> io.github.fogonapps.identidad.Cara.SORPRESA
        racha.actual > 0 -> io.github.fogonapps.identidad.Cara.FELIZ
        else -> io.github.fogonapps.identidad.Cara.DECIDIDO
    }
    Column(Modifier.fillMaxWidth()) {
        // La cabecera común (X-07): tu nombre y la frase; El Forzudo, en el círculo del perfil.
        val usuario by vm.red.usuario.collectAsState()
        val fotoRemota by vm.red.foto.collectAsState()
        val foto = if (usuario != null) fotoRemota else e.foto
        // La frase del saludo, como en El Cajón: las propias (fz_), las de la gente de El Forzudo, los votos y el menú.
        val propias = androidx.compose.ui.res.stringArrayResource(R.array.frases_inicio)
        val base = remember(propias.toList()) { propias.mapIndexed { i, t -> io.github.fogonapps.social.FraseBase("fz_${i + 1}", t) } }
        val idioma = java.util.Locale.getDefault().language.takeIf { it == "en" } ?: "es"
        val frase = io.github.fogonapps.social.ui.rememberFraseDelSaludo(vm.frases, base, idioma)
        var menuFrase by remember { mutableStateOf(false) }
        io.github.fogonapps.perfil.CabeceraInicio(e.nombre?.takeIf { it.isNotBlank() } ?: stringResource(R.string.hola),
            frase?.frase?.texto.orEmpty(),
            onMantener = { if (frase != null) menuFrase = true },
            adorno = { if (frase?.gusta == true) io.github.fogonapps.identidad.IconoRiso(io.github.fogonapps.identidad.IconosSociales.Corazon, stringResource(io.github.fogonapps.social.ui.R.string.social_frase_te_gusta), tamano = 14.dp, tinta = f.acentoTexto) },
            perfil = { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.destinoGuia("perfil")) {
                io.github.fogonapps.identidad.BotonPerfilMascota(io.github.fogonapps.identidad.Quien.FORZUDO, stringResource(R.string.pestana_perfil), cara,
                    foto = foto.takeIf { it.isNotBlank() }?.let { { io.github.fogonapps.perfil.FotoPerfilLocal(it, tamano = 48.dp) } }) { vm.abrir(Pantalla.Perfil) }
            }})
        if (menuFrase && frase != null) io.github.fogonapps.social.ui.MenuFrase(frase.frase.texto, frase.gusta, cerrar = { menuFrase = false },
            onGusta = frase::gustar, onNoMostrar = frase::noMostrar, onCompartido = frase::compartida,
            onProponer = { propia ->
                if (usuario == null) vm.abrir(Pantalla.Cuenta)
                else vm.frases.proponer(propia, idioma) { listo -> if (listo) vm.avisar(vm.tx(io.github.fogonapps.social.ui.R.string.social_frase_propuesta_gracias)) }
            })

        // La semana: su número y la fecha, encima de sus días.
        val semana = hoy.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        Text(stringResource(R.string.inicio_fecha, semana, fechaCorta(td)), style = MaterialTheme.typography.labelLarge, color = f.textoSuave,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp))

        // La semana del calendario: los días con entreno van llenos de la primera tinta; hoy, con borde.
        val lunes = java.time.LocalDate.parse(ws)
        val fechasHechas = e.sessions.map { it.date }.toSet()
        val estHecho = stringResource(R.string.dia_hecho); val estPendiente = stringResource(R.string.dia_pendiente)
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp).aparecer(1), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0 until 7).forEach { i ->
                val d = lunes.plusDays(i.toLong())
                val hecho = iso(d) in fechasHechas
                val esHoy = d == hoy
                val forma = io.github.fogonapps.identidad.FormaPildora
                Column(
                    Modifier.weight(1f).height(72.dp).clip(forma)
                        .then(if (hecho) Modifier.background(f.tinta1) else Modifier.background(f.superficie))
                        .border(if (esHoy) 1.5.dp else f.borde, if (esHoy) f.acentoTexto else if (hecho) f.tinta1 else f.linea, forma)
                        .semantics(mergeDescendants = true) { stateDescription = if (hecho) estHecho else estPendiente },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    val c = if (hecho) f.sobreTinta1 else if (esHoy) f.texto else f.textoSuave
                    Box(Modifier.height(16.dp), contentAlignment = Alignment.Center) {
                        if (hecho) io.github.fogonapps.identidad.IconoRiso(Iconos.Chulo, null, tamano = 14.dp, tinta = c)
                        else Box(Modifier.size(5.dp).clip(CircleShape).background(if (esHoy) f.acentoTexto else f.linea))
                    }
                    Text(Vocabulario.actual.diasCortos[d.dayOfWeek.value % 7], style = MaterialTheme.typography.labelSmall, color = c)
                    Text("${d.dayOfMonth}", style = MaterialTheme.typography.titleSmall, color = c)
                }
            }
        }

        // La racha, en la tarjeta: el número en puntos y la mejor racha (El Forzudo vive en el círculo del perfil, X-07).
        io.github.fogonapps.identidad.TarjetaPersonaje(io.github.fogonapps.identidad.Quien.FORZUDO, cara, Modifier.aparecer(2), personaje = false) {
            Text(pluralStringResource(R.plurals.semanas_seguidas, racha.actual).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
            NumeroRiso("${racha.actual}", 96.sp, Modifier.semantics { contentDescription = "${racha.actual}" })
            MejorRacha(stringResource(R.string.racha_mejor, racha.mejor))
        }
        Text(stringResource(if (racha.actual > 0) R.string.racha_cumpliendo else R.string.racha_empezar),
            style = MaterialTheme.typography.bodySmall, color = f.textoSuave, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

        // Esta semana y el volumen, lado a lado.
        val deLaSemana = e.sessions.filter { inicioSemana(it.date) == ws }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).aparecer(3), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).tarjetaFogon(forma(24.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.inicio_esta_semana), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                Text(stringResource(R.string.inicio_de, hechos.size, dias.size), style = MaterialTheme.typography.headlineSmall, color = f.texto)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    dias.forEach { d -> Box(Modifier.size(8.dp).clip(CircleShape).background(if (d.id in hechos) f.tinta1 else f.linea)) }
                }
            }
            Column(Modifier.weight(1f).tarjetaFogon(forma(24.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.inicio_volumen), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                Text(fmtVol(deLaSemana.sumOf { L.volumen(it.entries) }),
                    style = MaterialTheme.typography.headlineSmall, color = f.texto, maxLines = 1)
                Text(pluralStringResource(R.plurals.entrenos, deLaSemana.size, deLaSemana.size), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
        }

        // Lo que toca: la rutina de hoy (o la siguiente sin hacer esta semana) y el botón para empezarla.
        val toca = dias.firstOrNull { it.id == L.diaDeHoy(dias, hoy) && it.id !in hechos } ?: dias.firstOrNull { it.id !in hechos }
        if (toca != null) Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).aparecer(4).tarjetaFogon(forma(24.dp)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            io.github.fogonapps.identidad.Ilustracion(io.github.fogonapps.forzudo.R.drawable.ilustracion_mancuerna, tamano = 64.dp)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.inicio_hoy_toca), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                Text(toca.title, style = MaterialTheme.typography.titleMedium, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(pluralStringResource(R.plurals.n_ejercicios, toca.ejercicios.size, toca.ejercicios.size), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
            if (e.active == null)
                BotonRiso(stringResource(R.string.empezar), { vm.empezar(toca.id) }, habilitado = toca.ejercicios.isNotEmpty())
        }

        Seccion(stringResource(R.string.semanas_de, Vocabulario.actual.mesesLargos[mes.takeLast(2).toInt() - 1])) {
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val estCompleta = stringResource(R.string.semana_completa); val estIncompleta = stringResource(R.string.semana_incompleta)
                semanasDelMes(mes).forEachIndexed { i, w ->
                    val hecha = L.semanaHecha(e, w, dias)
                    Column(
                        Modifier.then(if (hecha) Modifier.clip(forma(12.dp)).background(f.tinta1).border(f.borde, f.linea, forma(12.dp))
                            else Modifier.tarjetaFogon(forma(12.dp), sombra = false))
                            .semantics { stateDescription = if (hecha) estCompleta else estIncompleta }
                            .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        val c = if (hecha) f.sobreTinta1 else f.texto
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
                EstadoVacio(io.github.fogonapps.identidad.Quien.FORZUDO, stringResource(R.string.sin_entrenos), stringResource(R.string.sin_entrenos_texto))
            } else lista.forEachIndexed { i, s ->
                // Las tarjetas entran una tras otra (las primeras; las de más abajo, juntas).
                Box(Modifier.aparecer(i.coerceAtMost(8))) {
                    TarjetaEntreno(vm, s, dias) { vm.abrir(Pantalla.Entreno(s.id)) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun TarjetaEntreno(vm: GymViewModel, s: Sesion, dias: List<io.github.fogonapps.forzudo.datos.Dia>, onAbrir: () -> Unit) {
    val f = FogonTema.actual
    val L = vm.L
    val v = L.volumen(s.entries); val n = L.series(s.entries)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        .tarjetaFogon(forma(24.dp)).clip(forma(24.dp)).clickable(onClick = onAbrir).padding(16.dp),
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
        Text(stringResource(R.string.ver_y_corregir), style = MaterialTheme.typography.labelLarge, color = f.acentoTexto)
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
            dias.forEachIndexed { i, d ->
                Column(Modifier.aparecer(i.coerceAtMost(8)).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).tarjetaFogon(forma(14.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        EjerciciosRegistrados(vm, e)
    }
}

@Composable
fun Etiqueta(texto: String, fondo: Color, color: Color) {
    Text(texto, style = MaterialTheme.typography.labelMedium, color = color,
        modifier = Modifier.clip(FormaPildora).background(fondo).padding(horizontal = 10.dp, vertical = 4.dp))
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
                Text(stringResource(R.string.pestana_rangos), style = MaterialTheme.typography.headlineLarge, color = f.texto)
                Text(stringResource(R.string.rangos_falta_peso), color = f.texto)
                BotonRiso(stringResource(R.string.registrar_peso), { vm.abrir(Pantalla.Perfil) })
            }
        } else {
            // Cabecera con el ADN: el nombre del rango gigante, en su color, y la figura-calcomanía.
            val colorRango = ov?.rango?.let { colorHex(it.color) } ?: f.textoSuave
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                Text(stringResource(R.string.rango_general), style = MaterialTheme.typography.labelSmall, color = f.textoSuave, modifier = Modifier.weight(1f))
                ov?.rango?.let { r -> vm.bib.niveles.indexOfFirst { it.n == r.nombre }.takeIf { it >= 0 } }?.let { i -> Text("${i + 1} / ${vm.bib.niveles.size}", style = MaterialTheme.typography.labelSmall, color = f.textoSuave) }
            }
            Box(Modifier.fillMaxWidth().height(214.dp)) {
                val palabra = (ov?.rango?.nombre ?: stringResource(R.string.sin_datos_aun)).uppercase()
                // Lo más grande que quepa a lo ancho: cada mayúscula mide unos [Fogon.anchoCifra] em.
                val tam = if (ov == null) 44f else minOf(120f, 340f / (f.anchoCifra * palabra.length.coerceAtLeast(1)))
                NumeroRiso(palabra, tam.sp, Modifier.padding(start = 14.dp, top = 4.dp), color = colorRango, alto = tam.sp)
                io.github.fogonapps.identidad.Personaje(io.github.fogonapps.identidad.Quien.FORZUDO, null, Modifier.align(Alignment.BottomEnd).padding(end = 16.dp), cara = io.github.fogonapps.identidad.Cara.FELIZ, tamano = 80.dp)
                Row(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Insignia(ov?.rango, 40.dp)
                    Text(ov?.let { pluralStringResource(R.plurals.promedio_grupos, it.cuenta, it.cuenta, fk(bw)) } ?: stringResource(R.string.completa_entreno),
                        style = MaterialTheme.typography.bodySmall, color = f.textoSuave, modifier = Modifier.width(190.dp))
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
                            Text(r.consejo?.let { stringResource(R.string.proxima_meta, vm.bib.ex[it.id]?.n ?: "", it.rango, textoMeta(it)) } ?: stringResource(R.string.rango_maximo),
                                style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                        }
                        io.github.fogonapps.identidad.IconoRiso(Iconos.Siguiente, null, tamano = 20.dp, tinta = f.textoSuave, corrida = null)
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
        activeContainerColor = f.tinta1, activeContentColor = f.sobreTinta1, activeBorderColor = f.linea,
        inactiveContainerColor = f.superficie, inactiveContentColor = f.texto, inactiveBorderColor = f.linea,
    )
}

/* =================== Perfil =================== */

@Composable
fun PesoCorporal(vm: GymViewModel, e: Estado) {
    val f = FogonTema.actual
    val pesos = e.body.sortedBy { it.date }
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

}

@Composable
fun Constancia(vm: GymViewModel, e: Estado) {
    Seccion(stringResource(R.string.constancia)) {
        val anioHoy = vm.hoy().year
        val desde = minOf(anioHoy, e.sessions.minOfOrNull { it.date.take(4).toInt() } ?: anioHoy)
        val dias = e.sessions.map { it.date }.toSet()
        for (a in anioHoy downTo desde) MapaAnual(a, dias, vm.hoy())
        Pista(stringResource(R.string.pista_mapa_anual))
    }

}

@Composable
fun EjerciciosRegistrados(vm: GymViewModel, e: Estado) {
    val f = FogonTema.actual
    val L = vm.L
    val usados = (L.dias(e).flatMap { d -> d.rutina.items.map { it.id } } + e.sessions.flatMap { it.entries.keys }).toSet()
    Seccion(stringResource(R.string.ejercicios)) {
        vm.bib.ejercicios.filter { it.id in usados }.forEachIndexed { i, ej ->
            val mk = L.maxKg(e, ej.id); val mxr = L.maxReps(e, ej.id); val r = L.rangoEjercicio(e, ej.id)
            Fila({ vm.abrir(Pantalla.Detalle(ej.id)) }, Modifier.aparecer(i.coerceAtMost(8))) {
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
                io.github.fogonapps.identidad.IconoRiso(Iconos.Siguiente, null, tamano = 20.dp, tinta = f.textoSuave, corrida = null)
            }
        }
    }

}

@Composable
internal fun SelectorIdioma(vm: GymViewModel) {
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
internal fun CajaSync(vm: GymViewModel, s: Sync, confirmar: Confirmar) {
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


/** La mejor racha en una píldora: el ícono de la racha y el número, sin girar. */
@Composable
private fun MejorRacha(texto: String) {
    val f = FogonTema.actual
    Row(
        Modifier.clip(FormaPildora).background(f.tinta2.copy(alpha = 0.35f)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        io.github.fogonapps.identidad.IconoRiso(Iconos.Racha, null, tamano = 16.dp, tinta = f.texto, corrida = null)
        Text(texto, style = MaterialTheme.typography.labelMedium, color = f.texto, maxLines = 1)
    }
}

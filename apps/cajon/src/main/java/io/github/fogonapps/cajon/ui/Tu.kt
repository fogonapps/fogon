package io.github.fogonapps.cajon.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.Pantalla
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.Cifras
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Estados
import io.github.fogonapps.cajon.datos.Obra
import io.github.fogonapps.cajon.datos.Tipos
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.FormaPildora
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.aparecer
import io.github.fogonapps.identidad.forma
import io.github.fogonapps.identidad.progresoAnimado

/*
 * Las cifras como una lámina de fin de año, sobria: la cifra grande sobre el papel, lo último terminado en abanico,
 * los datos separados por rayas finas y las barras en la tinta del texto. El color lo ponen solo las portadas.
 */
@Composable
fun Tu(vm: CajonViewModel, todo: Estado, seccion: String? = null) {
    val f = FogonTema.actual
    val anioActual = vm.hoy().year
    var anio by rememberSaveable { mutableStateOf<Int?>(anioActual) }
    // Con una sección, las cifras solo de lo suyo.
    val tipos = when (seccion) {
        io.github.fogonapps.cajon.Secciones.MUSICA -> listOf(Tipos.ALBUM)
        io.github.fogonapps.cajon.Secciones.LIBROS -> listOf(Tipos.LIBRO)
        io.github.fogonapps.cajon.Secciones.JUEGOS -> listOf(Tipos.JUEGO)
        null -> io.github.fogonapps.cajon.Secciones.tiposVisibles(todo)
        else -> listOf(Tipos.PELICULA, Tipos.SERIE, Tipos.ANIME)
    }
    // Los géneros de los juegos, en el idioma de la app: uno guardado en español y otro en inglés cuentan juntos.
    val e = remember(todo, seccion) { todo.copy(obras = vm.conGenerosVisibles(if (seccion == null) todo.obras else todo.obras.filter { it.tipo in tipos })) }
    val c = vm.L.cifras(e, anio)
    // Lo terminado en el periodo (con el mismo criterio que las cifras), para mostrar sus portadas.
    val terminadas = remember(e, anio) {
        e.vivas.filter { it.estado == Estados.TERMINADO && (anio == null || vm.L.ultimaVez(it)?.take(4)?.toIntOrNull() == anio) }
    }

    Cabecera(if (seccion == null) stringResource(R.string.tus_cifras) else stringResource(R.string.cifras_de, nombreSeccion(seccion)), vm::cerrar)

    // De qué año.
    Chips(listOf<Pair<Int?, String>>(anioActual to "$anioActual", anioActual - 1 to "${anioActual - 1}", null to stringResource(R.string.siempre)), anio) { anio = it }

    // La cifra grande: todo lo terminado.
    val total = c.porTipo.values.sum()
    Column(Modifier.aparecer(0).fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp).semantics(mergeDescendants = true) {}) {
        Text(anio?.let { stringResource(R.string.en_el_anio, it) } ?: stringResource(R.string.desde_siempre), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
        NumeroQueRueda(total) {
            Text("$it", fontFamily = f.titulos, fontWeight = FontWeight.Black, fontSize = 72.sp, lineHeight = 76.sp, color = f.texto)
        }
        Text(pluralStringResource(R.plurals.terminadas, total, total), style = MaterialTheme.typography.titleMedium, color = f.texto)
    }
    // Lo último terminado, en abanico (como en el Inicio, más grande): el color de la lámina.
    val recientes = remember(terminadas) { terminadas.sortedByDescending { vm.L.ultimaVez(it) }.take(7) }
    if (recientes.isNotEmpty()) Box(Modifier.aparecer(1).fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp).height(110.dp).clearAndSetSemantics {}) {
        recientes.reversed().forEachIndexed { i, o ->
            val giro = (i - (recientes.size - 1) / 2f) * 3f
            Portada(o.portada, o.tipo, o.titulo, Modifier.width(64.dp).align(Alignment.CenterStart).offset(x = (38 * i).dp)
                .graphicsLayer { rotationZ = giro }.shadow(6.dp, forma(6.dp), clip = false), esquina = 6.dp, conTitulo = false)
        }
    }
    // El año en historias, para mirarlo y compartirlo.
    if (seccion == null && anio != null) AccesoResumen(anio!!, recientes, { vm.abrir(Pantalla.Resumen(anio!!)) }, Modifier.padding(top = 8.dp))
    // Cuántas de cada tipo.
    if (tipos.size > 1) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        tipos.forEach { t ->
            Column(Modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${c.porTipo[t] ?: 0}", fontFamily = f.titulos, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = f.texto)
                IconoRiso(Iconos.de(t), nombreTipo(t), tamano = 18.dp, tinta = f.textoSuave)
            }
        }
    }

    // Lo más tuyo: el género favorito y quien más aparece.
    if (c.generos.isNotEmpty() || c.creadores.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        c.generos.firstOrNull()?.let { Caja(stringResource(R.string.genero_favorito), it.first, Modifier.weight(1f)) }
        c.creadores.firstOrNull()?.let {
            Caja(stringResource(when (seccion) { io.github.fogonapps.cajon.Secciones.MUSICA -> R.string.artista_favorito; io.github.fogonapps.cajon.Secciones.LIBROS -> R.string.autor_favorito
                io.github.fogonapps.cajon.Secciones.JUEGOS -> R.string.estudio_favorito; else -> R.string.creador_favorito }), it.first, Modifier.weight(1f))
        }
    }
    // Lo que suma, según la sección.
    @Composable
    fun Fila(vararg cajas: Pair<String, String>) = Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        cajas.forEach { (t, v) -> Caja(t, v, Modifier.weight(1f)) }
    }
    val cine = seccion == null || tipos.contains(Tipos.PELICULA)
    if (cine) Fila(stringResource(R.string.horas_de_cine) to "${c.minutosCine / 60}", stringResource(R.string.horas_de_series) to "${c.minutosSeries / 60}")
    if (seccion == null) Fila(stringResource(R.string.paginas_leidas) to "${c.paginas}", stringResource(R.string.canciones_escuchadas) to "${c.canciones}")
    if (seccion == io.github.fogonapps.cajon.Secciones.LIBROS) Fila(stringResource(R.string.paginas_leidas) to "${c.paginas}",
        stringResource(R.string.paginas_por_libro) to "${if ((c.porTipo[Tipos.LIBRO] ?: 0) > 0) c.paginas / (c.porTipo[Tipos.LIBRO] ?: 1) else 0}")
    if (seccion == io.github.fogonapps.cajon.Secciones.MUSICA) {
        // Lo que sonó en tus apps de música: reproducciones y los minutos (calculados con 3,5 minutos por canción).
        val oidos by vm.oidos.collectAsStateWithLifecycle()
        val desde = anio?.let { java.time.LocalDate.of(it, 1, 1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() } ?: 0L
        val hasta = anio?.let { java.time.LocalDate.of(it + 1, 1, 1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() } ?: Long.MAX_VALUE
        val sonaron = oidos.albumes.filter { it.ultima in desde until hasta }
        val veces = sonaron.sumOf { it.veces }
        Fila(stringResource(R.string.canciones_escuchadas) to "${c.canciones}", stringResource(R.string.reproducciones) to "$veces")
        Fila(stringResource(R.string.minutos_aprox) to "${(veces * 3.5).toInt()}", stringResource(R.string.artistas) to "${todo.musicosVivos.size}")
        val masOido = sonaron.groupBy { it.artista }.mapValues { (_, l) -> l.sumOf { it.veces } }.entries.sortedByDescending { it.value }.take(5).map { it.key to it.value }
        if (masOido.isNotEmpty()) { Seccion(stringResource(R.string.lo_mas_escuchado)); Tops(masOido) }
    }
    if (seccion == null) Fila(stringResource(R.string.en_tu_diario) to "${c.registros}", stringResource(R.string.racha) to "${vm.L.racha(e, vm.hoy())}")
    else Fila(stringResource(R.string.en_tu_diario) to "${c.registros}", stringResource(R.string.nota_media_corta) to (c.notaMedia?.let { cifra(it, vm.idioma()) } ?: "–"))

    // Lo mejor calificado y lo que más se repitió, con sus portadas.
    val mejores = remember(terminadas) {
        terminadas.filter { it.nota > 0 }.sortedWith(compareByDescending<Obra> { it.nota }.thenByDescending { vm.L.ultimaVez(it) }).take(3)
    }
    if (mejores.isNotEmpty()) {
        Seccion(stringResource(R.string.cifras_mejor_nota))
        PortadasDestacadas(mejores, vm) { o -> Estrellas(o.nota, 12.sp) }
    }
    val repetidas = remember(e, anio) {
        e.vivas.map { o -> o to o.registros.count { r -> anio == null || r.fecha.take(4).toIntOrNull() == anio } }
            .filter { it.second >= 2 }.sortedByDescending { it.second }.take(3)
    }
    if (repetidas.isNotEmpty()) {
        Seccion(stringResource(R.string.cifras_mas_repetido))
        val veces = repetidas.toMap()
        PortadasDestacadas(repetidas.map { it.first }, vm) { o ->
            val n = veces[o] ?: 0
            Text(pluralStringResource(R.plurals.cifras_veces, n, n), style = MaterialTheme.typography.labelMedium, color = f.textoSuave)
        }
    }

    // Las notas, como las barras de estrellas.
    if (c.notas.sum() > 0) {
        Seccion(stringResource(R.string.tus_notas))
        Column(Modifier.aparecer(3).fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            c.notaMedia?.let {
                Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(cifra(it, vm.idioma()), fontFamily = f.titulos, fontWeight = FontWeight.Black, fontSize = 44.sp, lineHeight = 46.sp, color = f.texto)
                    Text(stringResource(R.string.nota_media_corta), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
            BarrasNotas(c, stringResource(R.string.tus_notas))
            Row(Modifier.fillMaxWidth()) {
                Text("½", color = f.textoSuave, modifier = Modifier.weight(1f))
                Text("★★★★★", color = f.textoSuave, fontSize = 12.sp)
            }
        }
    }

    // Por mes.
    if (anio != null && c.porMes.sum() > 0) {
        Seccion(stringResource(R.string.por_mes))
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            BarrasMeses(c.porMes, stringResource(R.string.por_mes))
        }
    }

    if (c.generos.isNotEmpty()) {
        Seccion(stringResource(R.string.tus_generos))
        Tops(c.generos)
    }
    if (c.creadores.isNotEmpty()) {
        Seccion(stringResource(if (seccion == io.github.fogonapps.cajon.Secciones.JUEGOS) R.string.tus_estudios else R.string.tus_creadores))
        Tops(c.creadores)
    }

    if (seccion != null) return
    Seccion(stringResource(R.string.mas))
    FilaVidrio({ vm.abrir(Pantalla.Retos) }) {
        IconoRiso(Iconos.Trofeo, null, tamano = 22.dp, tinta = f.texto)
        Text(stringResource(R.string.retos_del_anio, anioActual), style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.weight(1f))
        IconoRiso(Iconos.Siguiente, null, tamano = 18.dp, tinta = f.textoSuave)
    }
    FilaVidrio({ vm.abrir(Pantalla.Artistas) }) {
        IconoRiso(Iconos.Artista, null, tamano = 22.dp, tinta = f.texto)
        Text(stringResource(R.string.tipo_artistas), style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.weight(1f))
        Text("${e.musicosVivos.size}", style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
    }
    FilaVidrio({ vm.abrir(Pantalla.Diario) }) {
        IconoRiso(Iconos.Diario, null, tamano = 22.dp, tinta = f.texto)
        Text(stringResource(R.string.tu_diario), style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.weight(1f))
        IconoRiso(Iconos.Siguiente, null, tamano = 18.dp, tinta = f.textoSuave)
    }
}

/** Una raya fina de tinta suave: separa los datos sin meterlos en tarjetas. */
@Composable
private fun Raya(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(FogonTema.actual.linea))
}

/** Un dato: la raya arriba, el valor en la letra de los títulos y la etiqueta debajo. */
@Composable
private fun Caja(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    val f = FogonTema.actual
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Raya(Modifier.padding(bottom = 8.dp))
        // Un valor largo («Role-playing», un nombre) se achica para caber en una línea en vez de partir la palabra.
        Text(valor, fontFamily = f.titulos, fontWeight = FontWeight.Bold, color = f.texto, maxLines = 1, overflow = TextOverflow.Ellipsis,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 28.sp))
        Text(etiqueta, style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
    }
}

/** Hasta tres portadas grandes, lado a lado, con su título y lo que va debajo; cada una abre su ficha. */
@Composable
private fun PortadasDestacadas(obras: List<Obra>, vm: CajonViewModel, abajo: @Composable (Obra) -> Unit) {
    val f = FogonTema.actual
    Row(Modifier.aparecer(2).fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        obras.forEach { o ->
            Column(Modifier.weight(1f).clip(forma(8.dp)).clickable { vm.abrir(Pantalla.Ficha(o.id)) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Portada(o.portada, o.tipo, o.titulo, Modifier.fillMaxWidth().shadow(6.dp, forma(8.dp), clip = false), esquina = 8.dp, conTitulo = false)
                Text(o.titulo, style = MaterialTheme.typography.labelLarge, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                abajo(o)
            }
        }
        repeat(3 - obras.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** Un podio en filas: el puesto en la letra de los títulos, el nombre, cuántas y una barra fina de tinta. */
@Composable
private fun Tops(lista: List<Pair<String, Int>>) {
    val f = FogonTema.actual
    val maximo = lista.maxOf { it.second }.coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        lista.forEachIndexed { i, (nombre, n) ->
            Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("${i + 1}", fontFamily = f.titulos, fontWeight = FontWeight.Black, fontSize = 22.sp, color = if (i == 0) f.texto else f.textoSuave, modifier = Modifier.width(24.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(nombre, style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$n", style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
                    }
                    BarraFina(n.toFloat() / maximo)
                }
            }
        }
    }
}

/** Una barra fina en la tinta del texto sobre la raya suave. */
@Composable
private fun BarraFina(progreso: Float) {
    val f = FogonTema.actual
    val p = progresoAnimado(progreso.coerceIn(0f, 1f))
    Box(Modifier.fillMaxWidth().height(4.dp).clip(FormaPildora).background(f.linea)) {
        Box(Modifier.fillMaxWidth(p).fillMaxHeight().clip(FormaPildora).background(f.texto))
    }
}

/** Diez barras, una por media estrella, que crecen desde abajo. */
@Composable
private fun BarrasNotas(c: Cifras, descripcion: String) {
    val f = FogonTema.actual
    val p = progresoAnimado(1f)
    val maximo = (1..10).maxOf { c.notas[it] }.coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(90.dp).semantics { contentDescription = descripcion }) {
        val hueco = size.width / 10; val ancho = hueco * 0.72f
        for (i in 1..10) {
            val alto = (c.notas[i].toFloat() / maximo) * size.height * p
            val base = if (c.notas[i] > 0) alto.coerceAtLeast(3.dp.toPx()) else 2.dp.toPx()
            drawRoundRect(if (c.notas[i] > 0) f.texto else f.linea, Offset((i - 1) * hueco + (hueco - ancho) / 2, size.height - base), Size(ancho, base), CornerRadius(3.dp.toPx()))
        }
    }
}

/** Doce barras, una por mes, con la inicial del mes debajo; el mes más lleno, en tinta entera. */
@Composable
private fun BarrasMeses(meses: IntArray, descripcion: String) {
    val f = FogonTema.actual
    val p = progresoAnimado(1f)
    val maximo = meses.max().coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(100.dp).semantics { contentDescription = descripcion }) {
        val hueco = size.width / 12; val ancho = hueco * 0.6f
        meses.forEachIndexed { i, n ->
            val alto = if (n > 0) ((n.toFloat() / maximo) * size.height * p).coerceAtLeast(3.dp.toPx()) else 2.dp.toPx()
            val color = when { n <= 0 -> f.linea; n == maximo -> f.texto; else -> f.texto.copy(alpha = 0.45f) }
            drawRoundRect(color, Offset(i * hueco + (hueco - ancho) / 2, size.height - alto), Size(ancho, alto), CornerRadius(ancho / 2))
        }
    }
    val idioma = LocalConfiguration.current.locales[0]
    Row(Modifier.fillMaxWidth().padding(top = 6.dp).clearAndSetSemantics {}) {
        for (m in 1..12) Text(java.time.Month.of(m).getDisplayName(java.time.format.TextStyle.NARROW, idioma), style = MaterialTheme.typography.labelSmall,
            color = f.textoSuave, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
    }
}

/** Los retos del año: cuántas obras de cada tipo quiere terminar. */
@Composable
fun Retos(vm: CajonViewModel, e: Estado) {
    val f = FogonTema.actual
    val anio = vm.hoy().year
    val c = vm.L.cifras(e, anio)
    Cabecera(stringResource(R.string.retos_del_anio, anio), vm::cerrar)
    Pista(stringResource(R.string.retos_pista))
    // Fija al entrar: si se borra el número de un reto de juegos, su fila no desaparece mientras se escribe.
    val tipos = remember { io.github.fogonapps.cajon.Secciones.tiposVisibles(e) }
    tipos.forEach { t ->
        var meta by rememberSaveable(t) { mutableStateOf(e.ajustes.retos[t]?.toString() ?: "") }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Raya()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconoRiso(Iconos.de(t), null, tamano = 22.dp, tinta = f.texto)
                Text(nombreTipo(t), style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.weight(1f))
                CampoNumero(meta, { meta = it.take(4); vm.ponerReto(t, it.toIntOrNull() ?: 0) }, stringResource(R.string.meta), Modifier.fillMaxWidth(0.35f))
            }
            val n = e.ajustes.retos[t]
            if (n != null) {
                Text(stringResource(R.string.llevas_de, c.porTipo[t] ?: 0, n), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                BarraFina((c.porTipo[t] ?: 0).toFloat() / n)
            }
        }
    }
}

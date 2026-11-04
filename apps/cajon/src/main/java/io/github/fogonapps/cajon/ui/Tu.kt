package io.github.fogonapps.cajon.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.Pantalla
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.Cifras
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Tipos
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.NumeroRiso
import io.github.fogonapps.identidad.aparecer
import io.github.fogonapps.identidad.forma
import io.github.fogonapps.identidad.haloPuntos
import io.github.fogonapps.identidad.progresoAnimado

@Composable
fun Tu(vm: CajonViewModel, todo: Estado, seccion: String? = null) {
    val f = FogonTema.actual
    val anioActual = vm.hoy().year
    var anio by rememberSaveable { mutableStateOf<Int?>(anioActual) }
    // Con una sección, las cifras solo de lo suyo.
    val tipos = when (seccion) {
        io.github.fogonapps.cajon.Secciones.MUSICA -> listOf(Tipos.ALBUM)
        io.github.fogonapps.cajon.Secciones.LIBROS -> listOf(Tipos.LIBRO)
        null -> Tipos.TODOS
        else -> listOf(Tipos.PELICULA, Tipos.SERIE, Tipos.ANIME)
    }
    val e = if (seccion == null) todo else remember(todo, seccion) { todo.copy(obras = todo.obras.filter { it.tipo in tipos }) }
    val c = vm.L.cifras(e, anio)

    Cabecera(if (seccion == null) stringResource(R.string.tus_cifras) else stringResource(R.string.cifras_de, nombreSeccion(seccion)), vm::cerrar)

    // De qué año.
    Chips(listOf<Pair<Int?, String>>(anioActual to "$anioActual", anioActual - 1 to "${anioActual - 1}", null to stringResource(R.string.siempre)), anio) { anio = it }

    // El número grande: todo lo terminado.
    val total = c.porTipo.values.sum()
    Column(Modifier.aparecer(0).fillMaxWidth().padding(16.dp).vidrio(forma(28.dp)).haloPuntos(f.tinta2.copy(alpha = 0.35f)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(anio?.let { stringResource(R.string.en_el_anio, it) } ?: stringResource(R.string.desde_siempre), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
        NumeroRiso("$total", 60.sp)
        Text(pluralStringResource(R.plurals.terminadas, total, total), style = MaterialTheme.typography.bodyMedium, color = f.texto)
        if (tipos.size > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            tipos.forEach { t ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconoRiso(Iconos.de(t), nombreTipo(t), tamano = 20.dp, tinta = f.textoSuave)
                    Text("${c.porTipo[t] ?: 0}", fontFamily = f.cifras, fontSize = 18.sp, color = f.texto)
                }
            }
        }
    }

    // Lo más tuyo: el género favorito y quien más aparece.
    if (c.generos.isNotEmpty() || c.creadores.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        c.generos.firstOrNull()?.let { Caja(stringResource(R.string.genero_favorito), it.first, Modifier.weight(1f)) }
        c.creadores.firstOrNull()?.let {
            Caja(stringResource(when (seccion) { io.github.fogonapps.cajon.Secciones.MUSICA -> R.string.artista_favorito; io.github.fogonapps.cajon.Secciones.LIBROS -> R.string.autor_favorito
                else -> R.string.creador_favorito }), it.first, Modifier.weight(1f))
        }
    }
    // Lo que suma, según la sección.
    @Composable
    fun Fila(vararg cajas: Pair<String, String>) = Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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

    // Las notas, como las barras de estrellas.
    if (c.notas.sum() > 0) {
        Seccion(stringResource(R.string.tus_notas))
        Column(Modifier.aparecer(3).fillMaxWidth().padding(horizontal = 16.dp).vidrio().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            c.notaMedia?.let { Text(stringResource(R.string.nota_media, cifra(it, vm.idioma())), style = MaterialTheme.typography.bodyMedium, color = f.texto) }
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
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).vidrio().padding(16.dp)) {
            BarrasMeses(c.porMes, stringResource(R.string.por_mes))
        }
    }

    if (c.generos.isNotEmpty()) {
        Seccion(stringResource(R.string.tus_generos))
        Tops(c.generos)
    }
    if (c.creadores.isNotEmpty()) {
        Seccion(stringResource(R.string.tus_creadores))
        Tops(c.creadores)
    }

    if (seccion != null) return
    Seccion(stringResource(R.string.mas))
    FilaVidrio({ vm.abrir(Pantalla.Retos) }) {
        Ficha(f.tinta1, Iconos.Trofeo)
        Text(stringResource(R.string.retos_del_anio, anioActual), style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.weight(1f))
        IconoRiso(Iconos.Siguiente, null, tamano = 18.dp, tinta = f.textoSuave)
    }
    FilaVidrio({ vm.abrir(Pantalla.Artistas) }) {
        Ficha(f.tinta2, Iconos.Artista)
        Text(stringResource(R.string.tipo_artistas), style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.weight(1f))
        Text("${e.musicosVivos.size}", fontFamily = f.cifras, color = f.textoSuave)
    }
    FilaVidrio({ vm.abrir(Pantalla.Diario) }) {
        Ficha(f.tinta1, Iconos.Diario)
        Text(stringResource(R.string.tu_diario), style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.weight(1f))
        IconoRiso(Iconos.Siguiente, null, tamano = 18.dp, tinta = f.textoSuave)
    }
}

@Composable
private fun Caja(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    val f = FogonTema.actual
    Column(modifier.vidrio().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(valor, fontFamily = f.cifras, fontSize = 26.sp, color = f.texto)
        Text(etiqueta, style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
    }
}

@Composable
private fun Tops(lista: List<Pair<String, Int>>) {
    val f = FogonTema.actual
    val maximo = lista.maxOf { it.second }.coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).vidrio().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        lista.forEach { (nombre, n) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row { Text(nombre, style = MaterialTheme.typography.bodyMedium, color = f.texto, modifier = Modifier.weight(1f), maxLines = 1); Text("$n", fontFamily = f.cifras, color = f.textoSuave) }
                Barra(n.toFloat() / maximo, f.tinta1)
            }
        }
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
            drawRoundRect(if (c.notas[i] > 0) f.tinta1 else f.linea, Offset((i - 1) * hueco + (hueco - ancho) / 2, size.height - base), Size(ancho, base), CornerRadius(4.dp.toPx()))
        }
    }
}

@Composable
private fun BarrasMeses(meses: IntArray, descripcion: String) {
    val f = FogonTema.actual
    val p = progresoAnimado(1f)
    val maximo = meses.max().coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(100.dp).semantics { contentDescription = descripcion }) {
        val hueco = size.width / 12; val ancho = hueco * 0.6f
        meses.forEachIndexed { i, n ->
            val alto = if (n > 0) ((n.toFloat() / maximo) * size.height * p).coerceAtLeast(3.dp.toPx()) else 2.dp.toPx()
            drawRoundRect(if (n > 0) f.tinta2 else f.linea, Offset(i * hueco + (hueco - ancho) / 2, size.height - alto), Size(ancho, alto), CornerRadius(ancho / 2))
        }
    }
    Spacer(Modifier.height(6.dp))
}

/** Los retos del año: cuántas obras de cada tipo quiere terminar. */
@Composable
fun Retos(vm: CajonViewModel, e: Estado) {
    val f = FogonTema.actual
    val anio = vm.hoy().year
    val c = vm.L.cifras(e, anio)
    Cabecera(stringResource(R.string.retos_del_anio, anio), vm::cerrar)
    Pista(stringResource(R.string.retos_pista))
    Tipos.TODOS.forEach { t ->
        var meta by rememberSaveable(t) { mutableStateOf(e.ajustes.retos[t]?.toString() ?: "") }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).vidrio().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconoRiso(Iconos.de(t), null, tamano = 22.dp, tinta = f.textoSuave)
                Text(nombreTipo(t), style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.weight(1f))
                CampoNumero(meta, { meta = it.take(4); vm.ponerReto(t, it.toIntOrNull() ?: 0) }, stringResource(R.string.meta), Modifier.fillMaxWidth(0.35f))
            }
            val n = e.ajustes.retos[t]
            if (n != null) {
                Text(stringResource(R.string.llevas_de, c.porTipo[t] ?: 0, n), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                Barra((c.porTipo[t] ?: 0).toFloat() / n, f.tinta1)
            }
        }
    }
}

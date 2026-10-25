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
fun Tu(vm: CajonViewModel, e: Estado) {
    val f = FogonTema.actual
    val anioActual = vm.hoy().year
    var anio by rememberSaveable { mutableStateOf<Int?>(anioActual) }
    val c = vm.L.cifras(e, anio)

    Cabecera(stringResource(R.string.tus_cifras), vm::cerrar) {
        BotonIcono(Iconos.Persona, stringResource(R.string.ajustes), grande = true) { vm.abrir(Pantalla.Ajustes) }
    }

    // Las cuatro favoritas, como en un perfil.
    Seccion(stringResource(R.string.tus_favoritas_titulo))
    val favoritas = e.ajustes.favoritas.mapNotNull { id -> e.vivas.firstOrNull { it.id == id } }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (i in 0 until 4) {
            val o = favoritas.getOrNull(i)
            Box(Modifier.weight(1f).clip(forma(10.dp)).clickable(enabled = o != null) { o?.let { vm.abrir(Pantalla.Ficha(it.id)) } }) {
                if (o != null) Portada(o.portada, o.tipo, o.titulo, Modifier.fillMaxWidth(), esquina = 10.dp)
                else Portada("", "pelicula", "", Modifier.fillMaxWidth(), esquina = 10.dp)
            }
        }
    }
    if (favoritas.isEmpty()) Pista(stringResource(R.string.favoritas_pista))

    // De qué año.
    Chips(listOf<Pair<Int?, String>>(anioActual to "$anioActual", anioActual - 1 to "${anioActual - 1}", null to stringResource(R.string.siempre)), anio) { anio = it }

    // El número grande: todo lo terminado.
    val total = c.porTipo.values.sum()
    Column(Modifier.aparecer(0).fillMaxWidth().padding(16.dp).vidrio(forma(28.dp)).haloPuntos(f.tinta2.copy(alpha = 0.35f)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(anio?.let { stringResource(R.string.en_el_anio, it) } ?: stringResource(R.string.desde_siempre), style = MaterialTheme.typography.labelLarge, color = f.textoSuave)
        NumeroRiso("$total", 60.sp)
        Text(pluralStringResource(R.plurals.terminadas, total, total), style = MaterialTheme.typography.bodyMedium, color = f.texto)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Tipos.TODOS.forEach { t ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconoRiso(Iconos.de(t), nombreTipo(t), tamano = 20.dp, tinta = f.textoSuave)
                    Text("${c.porTipo[t] ?: 0}", fontFamily = f.cifras, fontSize = 18.sp, color = f.texto)
                }
            }
        }
    }

    // Lo que suma.
    Row(Modifier.aparecer(1).fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Caja(stringResource(R.string.horas_de_cine), "${c.minutosCine / 60}", Modifier.weight(1f))
        Caja(stringResource(R.string.episodios_vistos_corto), "${c.episodios}", Modifier.weight(1f))
    }
    Row(Modifier.aparecer(2).fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Caja(stringResource(R.string.paginas_leidas), "${c.paginas}", Modifier.weight(1f))
        Caja(stringResource(R.string.canciones_escuchadas), "${c.canciones}", Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Caja(stringResource(R.string.en_tu_diario), "${c.registros}", Modifier.weight(1f))
        Caja(stringResource(R.string.racha), "${vm.L.racha(e, vm.hoy())}", Modifier.weight(1f))
    }

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

    Seccion(stringResource(R.string.mas))
    FilaVidrio({ vm.abrir(Pantalla.Retos) }) {
        Ficha(f.tinta1, Iconos.Trofeo)
        Text(stringResource(R.string.retos_del_anio, anioActual), style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.weight(1f))
        IconoRiso(Iconos.Siguiente, null, tamano = 18.dp, tinta = f.textoSuave)
    }
    FilaVidrio({ vm.abrir(Pantalla.Artistas) }) {
        Ficha(f.tinta2, Iconos.Artista)
        Text(stringResource(R.string.tipo_artistas), style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.weight(1f))
        Text("${e.artistasVivos.size}", fontFamily = f.cifras, color = f.textoSuave)
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

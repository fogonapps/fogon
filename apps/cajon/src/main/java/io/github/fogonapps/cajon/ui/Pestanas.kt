package io.github.fogonapps.cajon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.dp
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.Pantalla
import io.github.fogonapps.cajon.Pestana
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.Secciones
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.forma

/*
 * Las pestañas nuevas (rediseño 2026-11): «Lo tuyo» junta los cuatro apartados que antes eran pestañas, con un selector
 * arriba, y «Descubrir» junta el buscador y «Para ti».
 */

/** El título grande de una pestaña, en la letra de los títulos. */
@Composable
fun TituloPestana(texto: String) {
    Text(texto, style = MaterialTheme.typography.headlineLarge, color = FogonTema.actual.texto,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp).semantics { heading() })
}

/** Una opción de un selector: negra si está elegida, sin borde si no. */
@Composable
fun OpcionSelector(texto: String, elegida: Boolean, onClick: () -> Unit) {
    val f = FogonTema.actual
    Text(texto, style = MaterialTheme.typography.labelLarge, color = if (elegida) f.sobreTinta1 else f.texto,
        modifier = Modifier.clip(forma(24.dp)).background(if (elegida) f.tinta1 else lerp(f.fondo, f.texto, 0.06f))
            .clickable(onClick = onClick).semantics { selected = elegida; role = Role.Tab }
            .heightIn(min = 48.dp).padding(horizontal = 18.dp, vertical = 14.dp))
}

/** Lo tuyo: el apartado elegido (Cine, Música, Libros o Juegos) y, arriba, tus listas y tu diario. */
@Composable
fun LoTuyo(vm: CajonViewModel, e: Estado) {
    val seccion = vm.seccionLoTuyo.value
    val opciones = listOfNotNull(Secciones.CINE, Secciones.MUSICA, Secciones.LIBROS,
        Secciones.JUEGOS.takeIf { Pestana.juegosVisibles(e.ajustes.barra) || seccion == Secciones.JUEGOS })
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(end = 12.dp)) {
        Box(Modifier.weight(1f)) { TituloPestana(stringResource(R.string.pestana_lo_tuyo)) }
        BotonIcono(Iconos.Lista, stringResource(R.string.pestana_listas)) { vm.abrir(Pantalla.Listas) }
        BotonIcono(Iconos.Diario, stringResource(R.string.tu_diario)) { vm.abrir(Pantalla.Diario) }
    }
    // Los apartados, como un índice en la letra de los títulos: el elegido en tinta, los otros apagados.
    // Con la letra grande no caben los cuatro: el borde que sigue se desvanece, para que se note que hay más.
    val desliza = androidx.compose.foundation.lazy.rememberLazyListState()
    LazyRow(Modifier.desvanecerBordes(desliza.canScrollBackward, desliza.canScrollForward), state = desliza,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        items(opciones) { s ->
            val elegida = s == seccion
            val tinta by androidx.compose.animation.animateColorAsState(if (elegida) FogonTema.actual.texto else FogonTema.actual.texto.copy(alpha = 0.32f), label = "apartado")
            Text(nombreSeccion(s), fontFamily = FogonTema.actual.titulos, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 24.sp, color = tinta,
                modifier = Modifier.clip(forma(8.dp)).clickable { vm.seccionLoTuyo.value = s }.semantics { selected = elegida; role = Role.Tab }
                    .heightIn(min = 48.dp).padding(vertical = 8.dp))
        }
    }
    Apartado(vm, e, seccion, conTitulo = false)
}

/** Descubrir: la búsqueda siempre a mano arriba (abre el buscador) y «Para ti» debajo. */
@Composable
fun Descubrir(vm: CajonViewModel, e: Estado) {
    val f = FogonTema.actual
    TituloPestana(stringResource(R.string.pestana_descubrir))
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(forma(28.dp)).background(lerp(f.fondo, f.texto, 0.06f))
        .clickable(role = Role.Button) { vm.abrir(Pantalla.Buscar(Secciones.TODO)) }.heightIn(min = 56.dp).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconoRiso(Iconos.Buscar, null, tamano = 22.dp, tinta = f.texto)
        // Una línea que se achica antes de cortarse: con letra grande se perdía «juegos…».
        Text(stringResource(R.string.buscar_algo), style = MaterialTheme.typography.bodyLarge, color = f.textoSuave, maxLines = 1, softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = MaterialTheme.typography.bodyLarge.fontSize))
    }
    Column { ParaTi(vm, e) }
}

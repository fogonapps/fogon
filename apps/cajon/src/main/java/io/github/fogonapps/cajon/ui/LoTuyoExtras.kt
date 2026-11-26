package io.github.fogonapps.cajon.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.*
import io.github.fogonapps.identidad.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/*
 * Lo nuevo de «Lo tuyo»: el reto del año, las insignias y lo que está viendo. Lo propio sale de tu cajón; lo ajeno,
 * de las partes de su perfil (que también usa «Juntos», en Juntos.kt).
 */

/** El reto del año: cada tipo con su barra; cumplido, la celebración. [onEditar] solo en el tuyo. */
@Composable
internal fun TarjetaReto(titulo: String, metas: List<Triple<String, Int, Int>>, onEditar: (() -> Unit)?) {
    val f = FogonTema.actual
    if (metas.isEmpty()) return
    val cumplido = metas.all { (_, lleva, meta) -> lleva >= meta }
    Column(Modifier.fillMaxWidth().then(if (onEditar != null) Modifier.clickable(onClick = onEditar) else Modifier)
        .padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconoRiso(Iconos.Trofeo, null, tamano = 22.dp, tinta = f.texto)
            Text(titulo, fontFamily = f.titulos, fontSize = 20.sp, lineHeight = 24.sp, color = f.texto, modifier = Modifier.weight(1f).semantics { heading() })
            if (onEditar != null) IconoRiso(Iconos.Lapiz, null, tamano = 16.dp, tinta = f.textoSuave)
        }
        if (cumplido) Text(stringResource(R.string.reto_cumplido), fontFamily = f.titulos, fontSize = 18.sp, color = f.texto)
        metas.forEach { (t, lleva, meta) ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconoRiso(Iconos.de(t), null, tamano = 16.dp, tinta = f.texto)
                    Text(nombreTipo(t), style = MaterialTheme.typography.bodyMedium, color = f.texto, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    Text("$lleva / $meta", fontFamily = f.cifras, color = f.texto)
                }
                Barra(lleva.toFloat() / meta.coerceAtLeast(1), f.texto)
            }
        }
    }
}

/** El reto que llega en un perfil ajeno («anio» y, por tipo, [lleva, meta]). */
internal fun retoDe(parte: JsonObject?): List<Triple<String, Int, Int>> = parte?.entries?.mapNotNull { (t, v) ->
    (v as? JsonArray)?.takeIf { it.size == 2 }?.let { Triple(t, it[0].jsonPrimitive.content.toIntOrNull() ?: 0, it[1].jsonPrimitive.content.toIntOrNull() ?: 0) }
}?.filter { it.third > 0 }.orEmpty()

/**
 * Las insignias del perfil, como talones de cine en fila (ver [EstanteInsignias] en Talones.kt). No dan chispas: son
 * solo para mostrar (ver [Insignias]).
 */
@Composable
internal fun FilaInsignias(insignias: List<Insignia>, propias: Boolean) = EstanteInsignias(insignias, propias)

/** Una obra que llega en las partes de un perfil, para mostrarla y abrirla. */
internal data class ObraParte(val titulo: String, val tipo: String, val portada: String, val fuente: String, val anio: Int?, val creador: String, val extra: JsonObject)

internal fun obrasDe(parte: Any?): List<ObraParte> = (parte as? JsonArray)?.mapNotNull { it as? JsonObject }?.map { o ->
    fun t(k: String) = (o[k] as? JsonPrimitive)?.content.orEmpty()
    ObraParte(t("titulo"), t("tipoObra").ifBlank { Tipos.PELICULA }, t("portada"), t("fuente"), t("anio").toIntOrNull(), t("creador"), o)
}.orEmpty()

/** Si una obra de tu cajón es la misma que llegó (por su fuente, o por tipo y título). */
internal fun Obra.esLaMisma(o: ObraParte) = (fuente.isNotBlank() && fuente == o.fuente) ||
    (tipo == o.tipo && Logica.tituloLimpio(titulo) == Logica.tituloLimpio(o.titulo))

@Composable
internal fun PortadaChica(vm: CajonViewModel, o: ObraParte, abajo: @Composable ColumnScope.() -> Unit) {
    val f = FogonTema.actual
    Column(Modifier.width(100.dp).clip(forma(10.dp)).clickable { vm.abrirObraDePerfil(o.titulo, o.tipo, o.portada, o.fuente, o.anio, o.creador) },
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Portada(o.portada, o.tipo, o.titulo, Modifier.fillMaxWidth(), esquina = 10.dp, conTitulo = false)
        Text(o.titulo, style = MaterialTheme.typography.labelMedium, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
        abajo()
    }
}

/** Lo que está viendo o leyendo ahora, con por dónde va (solo el capítulo o la página). Lo de los dos vive en «Juntos». */
@Composable
internal fun ViendoAhora(vm: CajonViewModel, parte: Any?) {
    val f = FogonTema.actual
    val lista = obrasDe(parte)
    if (lista.isEmpty()) return
    Seccion(stringResource(R.string.viendo_ahora))
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(lista.size) { i ->
            val o = lista[i]
            PortadaChica(vm, o) {
                val avance = (o.extra["avance"] as? JsonPrimitive)?.content.orEmpty()
                if (avance.isNotBlank()) Text(avance, style = MaterialTheme.typography.labelSmall, color = f.textoSuave)
            }
        }
    }
}

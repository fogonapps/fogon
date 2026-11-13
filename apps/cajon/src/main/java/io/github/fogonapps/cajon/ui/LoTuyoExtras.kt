package io.github.fogonapps.cajon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.*
import io.github.fogonapps.identidad.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/*
 * Lo nuevo de «Lo tuyo»: el reto del año, las insignias, lo que está viendo (y si lo ven los dos) y la maratón de
 * los dos (lo que los dos tienen por ver). Lo propio sale de tu cajón; lo ajeno, de las partes de su perfil.
 */

/** El reto del año: cada tipo con su barra; cumplido, la celebración. [onEditar] solo en el tuyo. */
@Composable
internal fun TarjetaReto(titulo: String, metas: List<Triple<String, Int, Int>>, onEditar: (() -> Unit)?) {
    val f = FogonTema.actual
    if (metas.isEmpty()) return
    val cumplido = metas.all { (_, lleva, meta) -> lleva >= meta }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(forma(22.dp)).background(f.superficie).border(f.borde, f.linea, forma(22.dp))
        .then(if (onEditar != null) Modifier.clickable(onClick = onEditar) else Modifier).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconoRiso(Iconos.Trofeo, null, tamano = 22.dp, tinta = f.acentoTexto)
            Text(titulo, style = MaterialTheme.typography.titleMedium, color = f.texto, modifier = Modifier.weight(1f))
            if (onEditar != null) IconoRiso(Iconos.Lapiz, null, tamano = 16.dp, tinta = f.textoSuave)
        }
        if (cumplido) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Celebracion(io.github.fogonapps.identidad.Quien.CAJON, null, tamano = 56.dp, veces = 1)
            Text(stringResource(R.string.reto_cumplido), style = MaterialTheme.typography.titleSmall, color = f.acentoTexto)
        }
        metas.forEach { (t, lleva, meta) ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconoRiso(Iconos.de(t), null, tamano = 16.dp, tinta = f.textoSuave)
                    Text(nombreTipo(t), style = MaterialTheme.typography.bodyMedium, color = f.texto, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    Text("$lleva / $meta", fontFamily = f.cifras, color = f.texto)
                }
                Barra(lleva.toFloat() / meta.coerceAtLeast(1), f.tinta1)
            }
        }
    }
}

/** El reto que llega en un perfil ajeno («anio» y, por tipo, [lleva, meta]). */
internal fun retoDe(parte: JsonObject?): List<Triple<String, Int, Int>> = parte?.entries?.mapNotNull { (t, v) ->
    (v as? JsonArray)?.takeIf { it.size == 2 }?.let { Triple(t, it[0].jsonPrimitive.content.toIntOrNull() ?: 0, it[1].jsonPrimitive.content.toIntOrNull() ?: 0) }
}?.filter { it.third > 0 }.orEmpty()

@Composable
private fun nombreInsignia(id: String) = stringResource(when (id) {
    "cine" -> R.string.insignia_cine; "series" -> R.string.insignia_series; "anime" -> R.string.insignia_anime; "lector" -> R.string.insignia_lector
    "discos" -> R.string.insignia_discos; "constancia" -> R.string.insignia_constancia; "critico" -> R.string.insignia_critico
    "explorador" -> R.string.insignia_explorador; else -> R.string.insignia_reto
})

private fun iconoInsignia(id: String) = when (id) {
    "cine" -> Iconos.Pelicula; "series" -> Iconos.Serie; "anime" -> Iconos.Anime; "lector" -> Iconos.Libro; "discos" -> Iconos.Disco
    "constancia" -> Iconos.Racha; "critico" -> Iconos.Lapiz; "explorador" -> Iconos.Estrella; else -> Iconos.Trofeo
}

/**
 * Las insignias: las ganadas a color; en tu perfil también las que faltan, apagadas y con cuánto llevas. No dan
 * chispas: son solo para mostrar (ver [Insignias]).
 */
@Composable
internal fun FilaInsignias(insignias: List<Insignia>, propias: Boolean) {
    val f = FogonTema.actual
    val lista = if (propias) insignias else insignias.filter { it.ganada }
    if (lista.isEmpty()) return
    Seccion(stringResource(R.string.insignias_titulo))
    if (propias) Pista(stringResource(R.string.insignias_pista), Modifier.padding(horizontal = 16.dp))
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(lista.size) { i ->
            val ins = lista[i]
            Column(Modifier.width(84.dp).alpha(if (ins.ganada) 1f else 0.45f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(60.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (ins.ganada) f.tinta1 else f.superficie)
                    .border(f.borde, if (ins.ganada) f.tinta1 else f.linea, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                    IconoRiso(iconoInsignia(ins.id), null, tamano = 26.dp, tinta = if (ins.ganada) f.sobreTinta1 else f.textoSuave)
                }
                Text(nombreInsignia(ins.id), style = MaterialTheme.typography.labelMedium, color = f.texto, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                if (!ins.ganada) Text("${ins.lleva} / ${ins.meta}", style = MaterialTheme.typography.labelSmall, color = f.textoSuave)
            }
        }
    }
}

/** Una obra que llega en las partes de un perfil, para mostrarla y abrirla. */
private data class ObraParte(val titulo: String, val tipo: String, val portada: String, val fuente: String, val anio: Int?, val creador: String, val extra: JsonObject)

private fun obrasDe(parte: Any?): List<ObraParte> = (parte as? JsonArray)?.mapNotNull { it as? JsonObject }?.map { o ->
    fun t(k: String) = (o[k] as? JsonPrimitive)?.content.orEmpty()
    ObraParte(t("titulo"), t("tipoObra").ifBlank { Tipos.PELICULA }, t("portada"), t("fuente"), t("anio").toIntOrNull(), t("creador"), o)
}.orEmpty()

/** Si una obra de tu cajón es la misma que llegó (por su fuente, o por tipo y título). */
private fun Obra.esLaMisma(o: ObraParte) = (fuente.isNotBlank() && fuente == o.fuente) ||
    (tipo == o.tipo && Logica.tituloLimpio(titulo) == Logica.tituloLimpio(o.titulo))

@Composable
private fun PortadaChica(vm: CajonViewModel, o: ObraParte, abajo: @Composable ColumnScope.() -> Unit) {
    val f = FogonTema.actual
    Column(Modifier.width(100.dp).clip(forma(10.dp)).clickable { vm.abrirObraDePerfil(o.titulo, o.tipo, o.portada, o.fuente, o.anio, o.creador) },
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Portada(o.portada, o.tipo, o.titulo, Modifier.fillMaxWidth(), esquina = 10.dp, conTitulo = false)
        Text(o.titulo, style = MaterialTheme.typography.labelMedium, color = f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
        abajo()
    }
}

/**
 * Lo que está viendo o leyendo ahora, con por dónde va. Si tú también lo tienes en curso: «Los dos» y por dónde vas
 * tú (para verlo juntos; solo dice el capítulo o la página, nada de lo que pasa).
 */
@Composable
internal fun ViendoAhora(vm: CajonViewModel, parte: Any?, e: Estado?) {
    val f = FogonTema.actual
    val lista = obrasDe(parte)
    if (lista.isEmpty()) return
    Seccion(stringResource(R.string.viendo_ahora))
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(lista.size) { i ->
            val o = lista[i]
            val mia = e?.vivas?.firstOrNull { it.estado == Estados.EN_CURSO && it.esLaMisma(o) }
            PortadaChica(vm, o) {
                val avance = (o.extra["avance"] as? JsonPrimitive)?.content.orEmpty()
                if (avance.isNotBlank()) Text(avance, style = MaterialTheme.typography.labelSmall, color = f.textoSuave)
                if (mia != null) {
                    Text(stringResource(R.string.viendo_los_dos), style = MaterialTheme.typography.labelSmall, color = f.acentoTexto)
                    io.github.fogonapps.cajon.RedSocial.avanceDe(mia).takeIf { it.isNotBlank() }?.let {
                        Text(stringResource(R.string.viendo_tu_vas, it), style = MaterialTheme.typography.labelSmall, color = f.textoSuave)
                    }
                }
            }
        }
    }
}

/**
 * La maratón de los dos: lo que los dos tienen por ver (con cuánto duran las películas juntas). Si no coinciden,
 * lo suyo por ver que tú ya terminaste y te gustó, para recomendárselo.
 */
@Composable
internal fun MaratonDeLosDos(vm: CajonViewModel, parte: Any?, e: Estado?, usuario: String) {
    val f = FogonTema.actual
    val suyas = obrasDe(parte)
    if (suyas.isEmpty() || e == null) return
    val juntos = suyas.filter { o -> e.vivas.any { it.estado == Estados.QUIERO && it.esLaMisma(o) } }
    val recomendar = suyas.filter { o -> e.vivas.any { it.estado == Estados.TERMINADO && it.nota >= 8 && it.esLaMisma(o) } }
    if (juntos.isEmpty() && recomendar.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(forma(22.dp)).background(f.superficie).border(f.borde, f.linea, forma(22.dp)).padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconoRiso(Iconos.Reproducir, null, tamano = 20.dp, tinta = f.acentoTexto)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.maraton_titulo), style = MaterialTheme.typography.titleMedium, color = f.texto)
                val minutos = juntos.filter { it.tipo == Tipos.PELICULA }.sumOf { o -> (o.extra["minutos"] as? JsonPrimitive)?.content?.toIntOrNull()
                    ?: e.vivas.firstOrNull { it.esLaMisma(o) }?.minutos ?: 0 }
                Text(if (juntos.isEmpty()) stringResource(R.string.maraton_recomendar, usuario)
                    else if (minutos > 0) stringResource(R.string.maraton_juntos_horas, juntos.size, (minutos + 30) / 60)
                    else stringResource(R.string.maraton_juntos, juntos.size), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
        }
        val mostrar = (juntos.ifEmpty { recomendar }).take(10)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(mostrar.size) { i -> PortadaChica(vm, mostrar[i]) {} }
        }
    }
}

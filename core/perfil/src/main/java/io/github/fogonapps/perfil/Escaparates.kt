package io.github.fogonapps.perfil

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fogonapps.identidad.*

data class ElementoEscaparate(val titulo: String, val texto: String = "", val portada: String = "")
data class EscaparateVista(
    val tipo: String, val texto: String = "", val numero: String = "", val etiqueta: String = "",
    val elementos: List<ElementoEscaparate> = emptyList(), val fotos: List<String> = emptyList(), val insignias: List<String> = emptyList(),
)

@Composable
fun nombreEscaparate(tipo: String): String = stringResource(when (tipo) {
    "galeria" -> R.string.escaparate_galeria; "insignias" -> R.string.escaparate_insignias
    "texto" -> R.string.escaparate_texto; "numero" -> R.string.escaparate_numero
    "cajon:favoritas" -> R.string.escaparate_favoritas; "cajon:resena" -> R.string.escaparate_resena
    "cajon:lista" -> R.string.escaparate_lista; "cajon:albumes" -> R.string.escaparate_albumes
    "forzudo:records" -> R.string.escaparate_records; "forzudo:racha", "cucha:racha" -> R.string.escaparate_racha
    "forzudo:entreno" -> R.string.escaparate_entreno; "cucha:receta" -> R.string.escaparate_receta
    "alcancia:meta" -> R.string.escaparate_meta; else -> R.string.escaparates_titulo
})

@Composable
private fun nombreInsigniaEscaparate(id: String) = stringResource(when (id) {
    "cine" -> R.string.escaparate_ins_cine; "series" -> R.string.escaparate_ins_series
    "anime" -> R.string.escaparate_ins_anime; "lector" -> R.string.escaparate_ins_lector
    "discos" -> R.string.escaparate_ins_discos; "constancia" -> R.string.escaparate_ins_constancia
    "critico" -> R.string.escaparate_ins_critico; "explorador" -> R.string.escaparate_ins_explorador
    else -> R.string.escaparate_ins_reto
})

/** Render independiente del transporte y del cargador de imágenes de la app. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Escaparates(lista: List<EscaparateVista>, alTocar: (() -> Unit)? = null, imagen: @Composable (String, Modifier) -> Unit) {
    val f = FogonTema.actual
    lista.forEach { e ->
        BoxWithConstraints(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
            .background(f.fondo, forma(24.dp)).tarjetaFogon()
            .then(if (alTocar != null) Modifier.clickable(onClick = alTocar) else Modifier)) {
            val altoMinimo = if (maxWidth >= 600.dp) maxWidth / 3.4f else 0.dp
            Box(Modifier.matchParentSize().haloPuntos(f.tinta2.copy(alpha = 0.18f), centro = Offset(1f, 0.5f)))
            Column(Modifier.fillMaxWidth().heightIn(min = altoMinimo).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val titulo = when (e.tipo) {
                    "forzudo:records" -> stringResource(R.string.escaparate_mis_marcas)
                    "alcancia:meta" -> stringResource(R.string.escaparate_mi_meta)
                    "forzudo:racha", "cucha:racha" -> stringResource(R.string.escaparate_mi_racha)
                    else -> nombreEscaparate(e.tipo).lowercase(java.util.Locale.getDefault())
                }
                Text(titulo, color = f.texto, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Box(Modifier.fillMaxWidth().height(1.dp).background(f.linea))
                when {
                    e.tipo == "alcancia:meta" -> {
                        val porcentaje = e.numero.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(0f, 100f) ?: 0f
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.widthIn(min = 140.dp, max = 420.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(e.texto.ifBlank { titulo }, color = f.texto, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                Box(Modifier.fillMaxWidth().height(22.dp).clip(forma(50.dp)).background(f.superficie)
                                    .semantics { progressBarRangeInfo = ProgressBarRangeInfo(porcentaje / 100f, 0f..1f) }) {
                                    Box(Modifier.fillMaxHeight().fillMaxWidth(porcentaje / 100f).background(f.acentoTexto))
                                }
                            }
                            CifraEscaparate(porcentaje.toInt().toString(), "%")
                        }
                    }
                    e.tipo == "galeria" -> Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        e.fotos.forEach { foto -> imagen(foto, Modifier.size(112.dp).clip(forma(12.dp))) }
                    }
                    e.tipo == "insignias" -> FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        e.insignias.forEach { id -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconoRiso(IconosFogon.Estrella, null, modifier = Modifier.size(40.dp))
                            Text(nombreInsigniaEscaparate(id), color = f.texto, fontWeight = FontWeight.Bold)
                        } }
                    }
                    e.tipo == "cajon:albumes" || e.tipo == "cajon:favoritas" -> {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            e.elementos.forEach { elemento ->
                                Column(Modifier.width(112.dp).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(Modifier.fillMaxWidth().aspectRatio(if (e.tipo == "cajon:albumes") 1f else 2f / 3f)
                                        .clip(forma(12.dp)).background(androidx.compose.ui.graphics.lerp(f.fondo, f.texto, 0.14f)).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                                        IconoRiso(IconosFogon.Imagen, null, tamano = 32.dp, tinta = f.textoSuave)
                                        if (elemento.portada.isNotBlank()) imagen(elemento.portada, Modifier.fillMaxSize())
                                    }
                                    Text(elemento.titulo, color = f.texto, fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (elemento.texto.isNotBlank()) Text(elemento.texto, color = f.textoSuave,
                                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        if (e.texto.isNotBlank()) Text(e.texto, color = f.texto)
                    }
                    e.tipo == "forzudo:records" -> FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        e.elementos.forEach { elemento -> Column(Modifier.widthIn(max = 240.dp)) {
                            // Las marcas existentes llegan como «140 kg · 8»; se conserva todo el contenido.
                            val partes = elemento.texto.split(" ", limit = 2)
                            CifraEscaparate(partes.first(), partes.getOrElse(1) { "" })
                            Text(elemento.titulo, color = f.texto, fontWeight = FontWeight.Bold)
                        } }
                    }
                    else -> {
                        if (e.numero.isNotBlank()) {
                            if (e.tipo == "numero") CifraNumeroEscaparate(numeroLocalizado(e.numero))
                            else CifraEscaparate(e.numero)
                            if (e.etiqueta.isNotBlank()) Text(e.etiqueta, color = f.textoSuave)
                        }
                        if (e.texto.isNotBlank()) {
                            if (e.tipo == "texto") Column {
                                Text(stringResource(R.string.escaparate_comilla_abre), color = f.acentoTexto, fontSize = 36.sp)
                                Text(e.texto, color = f.texto, style = MaterialTheme.typography.headlineSmall)
                                Text(stringResource(R.string.escaparate_comilla_cierra), color = f.acentoTexto, fontSize = 36.sp, modifier = Modifier.align(Alignment.End))
                            } else Text(e.texto, color = f.texto)
                        }
                        e.elementos.forEach { elemento ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (elemento.portada.isNotBlank()) imagen(elemento.portada, Modifier.size(80.dp).clip(forma(12.dp)))
                                Column(Modifier.weight(1f)) {
                                    Text(elemento.titulo, color = f.texto, fontWeight = FontWeight.Bold)
                                    if (elemento.texto.isNotBlank()) Text(elemento.texto, color = f.textoSuave)
                                }
                            }
                        }
                    }
                }
                val app = when (e.tipo.substringBefore(":")) {
                    "cajon" -> R.string.escaparate_app_cajon
                    "forzudo" -> R.string.escaparate_app_forzudo
                    "cucha" -> R.string.escaparate_app_cucha
                    "alcancia" -> R.string.escaparate_app_alcancia
                    else -> when (f.paleta) {
                        Paleta.Forzudo -> R.string.escaparate_app_forzudo
                        Paleta.Ahorrador -> R.string.escaparate_app_alcancia
                        Paleta.Cucha -> R.string.escaparate_app_cucha
                        else -> R.string.escaparate_app_cajon
                    }
                }
                Text(stringResource(R.string.escaparate_firma, stringResource(app)), Modifier.align(Alignment.End), color = f.textoSuave, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CifraEscaparate(numero: String, unidad: String = "") {
    val f = FogonTema.actual
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Cifras largas se parten sin ocultarlas ni reducirlas por debajo del tamaño accesible.
        numero.chunked(5).forEach { NumeroRiso(it, 52.sp, color = f.numero) }
        if (unidad.isNotBlank()) Text(unidad, color = f.textoSuave, style = MaterialTheme.typography.titleSmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CifraNumeroEscaparate(numero: String) {
    val f = FogonTema.actual
    val cuerpo = MaterialTheme.typography.bodyLarge
    FlowRow(
        modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(numero) },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        gruposNumeroFormateado(numero).forEach { grupo ->
            val tieneSeparador = grupo.lastOrNull() == '.' || grupo.lastOrNull() == ','
            val texto = buildAnnotatedString {
                withStyle(SpanStyle(fontFamily = f.puntos, fontSize = 52.sp)) {
                    append(if (tieneSeparador) grupo.dropLast(1) else grupo)
                }
                if (tieneSeparador) {
                    // Un espacio finísimo a cada lado para que el punto o la coma no se peguen a las cifras de puntos.
                    withStyle(SpanStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold)) { append("\u200A"); append(grupo.last()); append("\u200A") }
                }
            }
            Text(
                texto,
                style = cuerpo,
                fontSize = 52.sp,
                lineHeight = 52.sp,
                letterSpacing = 0.sp,
                color = f.numero,
                softWrap = false,
            )
        }
    }
}

/** La lista es un borrador: la integración guarda el orden completo solo al confirmar. */
@Composable
fun EditorEscaparates(
    lista: List<EscaparateVista>, cupo: Int, ocupado: Boolean,
    mover: (Int, Int) -> Unit, quitar: (Int) -> Unit, agregar: () -> Unit,
    guardar: () -> Unit, tienda: () -> Unit, contenido: @Composable () -> Unit,
    imagen: @Composable (String, Modifier) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(stringResource(R.string.escaparates_cupo, lista.size, cupo), color = FogonTema.actual.texto)
    Text(stringResource(R.string.escaparates_compartir), color = FogonTema.actual.textoSuave)
    Text(stringResource(R.string.escaparates_nivel), color = FogonTema.actual.textoSuave)
    lista.forEachIndexed { i, e ->
        Escaparates(listOf(e), imagen = imagen)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.TextButton({ mover(i, i - 1) }, enabled = !ocupado && i > 0) { Text(stringResource(R.string.escaparates_subir)) }
            androidx.compose.material3.TextButton({ mover(i, i + 1) }, enabled = !ocupado && i < lista.lastIndex) { Text(stringResource(R.string.escaparates_bajar)) }
            androidx.compose.material3.TextButton({ quitar(i) }, enabled = !ocupado) { Text(stringResource(R.string.escaparates_quitar)) }
        }
    }
    BotonSecundario(stringResource(R.string.escaparates_agregar), agregar, habilitado = !ocupado && lista.size < cupo)
    contenido()
    BotonSecundario(stringResource(R.string.escaparates_tienda), tienda, habilitado = !ocupado)
    BotonRiso(stringResource(R.string.escaparates_guardar), guardar, habilitado = !ocupado)
    }
}

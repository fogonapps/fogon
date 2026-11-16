package io.github.fogonapps.perfil

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
@Composable
fun Escaparates(lista: List<EscaparateVista>, imagen: @Composable (String, Modifier) -> Unit) {
    val f = FogonTema.actual
    lista.forEach { e ->
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().tarjetaFogon().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(nombreEscaparate(e.tipo), style = MaterialTheme.typography.titleMedium, color = f.texto)
            if (e.tipo == "galeria") e.fotos.chunked(3).forEach { fila ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    fila.forEach { foto -> Box(Modifier.weight(1f).aspectRatio(1f)) { imagen(foto, Modifier.fillMaxSize()) } }
                    repeat(3 - fila.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (e.numero.isNotEmpty()) {
                NumeroRiso(e.numero + if (e.tipo == "alcancia:meta") "%" else "", 56.sp)
                if (e.etiqueta != "%") Text(e.etiqueta, color = f.textoSuave)
            }
            e.insignias.forEach { id ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconoRiso(IconosFogon.Estrella, null)
                    Text(nombreInsigniaEscaparate(id), color = f.texto)
                }
            }
            if (e.texto.isNotEmpty()) Text(e.texto, color = f.texto)
            e.elementos.forEach { elemento ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (elemento.portada.isNotBlank()) imagen(elemento.portada, Modifier.size(64.dp))
                    Column(Modifier.weight(1f)) {
                        Text(elemento.titulo, color = f.texto, style = MaterialTheme.typography.titleSmall)
                        if (elemento.texto.isNotEmpty()) Text(elemento.texto, color = f.textoSuave)
                    }
                }
            }
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
        Escaparates(listOf(e), imagen)
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

package io.github.fogonapps.cajon.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.Lista
import io.github.fogonapps.cajon.datos.Obra
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.Personaje
import io.github.fogonapps.identidad.forma
import io.github.fogonapps.identidad.haloPuntos
import kotlinx.coroutines.launch
import io.github.fogonapps.identidad.R as RId

/**
 * Muestra la tarjeta que se va a compartir y, al tocar "Compartir", la convierte en imagen (lo que se ve en
 * pantalla, tal cual) y se la pasa a otra app.
 */
@Composable
fun DialogoCompartir(onCerrar: () -> Unit, onCompartir: (Bitmap) -> Unit, tarjeta: @Composable () -> Unit) {
    val capa = rememberGraphicsLayer()
    val alcance = rememberCoroutineScope()
    val f = FogonTema.actual
    Dialog(onCerrar) {
        Column(Modifier.clip(forma(28.dp)).background(f.fondo).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.drawWithContent { capa.record { this@drawWithContent.drawContent() }; drawLayer(capa) }) { tarjeta() }
            BotonRiso(stringResource(R.string.compartir), {
                alcance.launch { onCompartir(capa.toImageBitmap().asAndroidBitmap()); onCerrar() }
            }, Modifier.fillMaxWidth())
            BotonSecundario(stringResource(R.string.cancelar), onCerrar, Modifier.fillMaxWidth())
        }
    }
}

/** La tarjeta de una obra: portada, nota, corazón y un pedazo de la reseña, con la marca de El Cajón. */
@Composable
fun TarjetaObra(o: Obra) {
    val f = FogonTema.actual
    Column(Modifier.width(320.dp).clip(forma(24.dp)).background(f.fondo).haloPuntos(f.tinta2.copy(alpha = 0.3f)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
            Portada(o.portada, o.tipo, o.titulo, Modifier.width(120.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(o.titulo, fontFamily = f.titulos, fontSize = 20.sp, lineHeight = 24.sp, color = f.texto, maxLines = 4, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(o.anio?.toString(), o.creador.takeIf { it.isNotBlank() }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Estrellas(o.nota, 20.sp)
                    if (o.meGusta) IconoRiso(Iconos.Corazon, null, tamano = 18.dp, tinta = f.acentoTexto, relleno = f.acentoTexto)
                }
            }
        }
        if (o.resena.isNotBlank()) Text("“" + o.resena.trim().take(280) + if (o.resena.length > 280) "…”" else "”",
            style = MaterialTheme.typography.bodyMedium, color = f.texto)
        Marca()
    }
}

/** La tarjeta de una lista: su nombre y las primeras portadas. */
@Composable
fun TarjetaLista(l: Lista, obras: List<Obra>) {
    val f = FogonTema.actual
    Column(Modifier.width(320.dp).clip(forma(24.dp)).background(f.fondo).haloPuntos(f.tinta2.copy(alpha = 0.3f)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(l.nombre, fontFamily = f.titulos, fontSize = 22.sp, color = f.texto, maxLines = 2)
        if (l.descripcion.isNotBlank()) Text(l.descripcion, style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 3)
        obras.take(9).chunked(3).forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fila.forEach { o -> Portada(o.portada, o.tipo, o.titulo, Modifier.weight(1f), esquina = 8.dp) }
                repeat(3 - fila.size) { Box(Modifier.weight(1f)) }
            }
        }
        if (obras.size > 9) Text(stringResource(R.string.y_mas, obras.size - 9), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        Marca()
    }
}

@Composable
private fun Marca() {
    val f = FogonTema.actual
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Personaje(RId.drawable.personaje_el_cajon, null, tamano = 36.dp)
        Text(stringResource(R.string.marca_compartir), style = MaterialTheme.typography.labelMedium, color = f.textoSuave)
    }
}

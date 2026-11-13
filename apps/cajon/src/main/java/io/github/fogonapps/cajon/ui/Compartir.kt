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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.perfil.conEstilo
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
fun TarjetaObra(o: Obra, resena: String = "") {
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
        if (resena.isNotBlank()) Text("“" + resena.trim().take(280) + if (resena.length > 280) "…”" else "”",
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
        Personaje(io.github.fogonapps.identidad.Quien.CAJON, null, cara = io.github.fogonapps.identidad.Cara.GUINO, tamano = 36.dp)
        Text(stringResource(R.string.marca_compartir), style = MaterialTheme.typography.labelMedium, color = f.textoSuave)
    }
}

/**
 * La tarjeta de una reseña, editable antes de compartirla: el fondo (el de El Cajón o los de la tienda que tengas),
 * el formato (cuadrado o de historia), la frase que quieres destacar y si va la nota. Lo que se ve es lo que se comparte.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DialogoTarjetaObra(vm: io.github.fogonapps.cajon.CajonViewModel, o: Obra, resena: String, onCerrar: () -> Unit, onCompartir: (Bitmap) -> Unit) {
    val f = FogonTema.actual
    val capa = rememberGraphicsLayer()
    val alcance = rememberCoroutineScope()
    val temas by vm.red.temas.collectAsStateWithLifecycle()
    val fondos = listOf("") + io.github.fogonapps.perfil.TemasPerfil.FONDOS.filter { it in temas.reclamados }
    var fondo by remember { mutableStateOf(temas.puesto.takeIf { it in fondos } ?: "") }
    var historia by remember { mutableStateOf(false) }
    var conNota by remember { mutableStateOf(o.nota > 0) }
    var frase by remember { mutableStateOf(resena.trim().take(200)) }
    Dialog(onCerrar, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(12.dp).clip(forma(28.dp)).background(f.fondo).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.drawWithContent { capa.record { this@drawWithContent.drawContent() }; drawLayer(capa) }) {
                TarjetaObraEditada(o, frase, fondo, historia, conNota)
            }
            // Lo que se puede cambiar.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pildora(stringResource(R.string.tarjeta_cuadrada), acento = !historia) { historia = false }
                Pildora(stringResource(R.string.tarjeta_historia), acento = historia) { historia = true }
                if (o.nota > 0) Pildora(stringResource(R.string.tarjeta_con_nota), acento = conNota) { conNota = !conNota }
            }
            if (fondos.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fondos.forEach { t ->
                    Pildora(if (t.isEmpty()) stringResource(R.string.tarjeta_fondo_cajon) else io.github.fogonapps.perfil.TemasPerfil.nombre(t), acento = fondo == t) { fondo = t }
                }
            }
            androidx.compose.material3.OutlinedTextField(frase, { frase = it.take(200) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.tarjeta_frase)) },
                shape = forma(18.dp), maxLines = 4)
            BotonRiso(stringResource(R.string.compartir), { alcance.launch { onCompartir(capa.toImageBitmap().asAndroidBitmap()); onCerrar() } }, Modifier.fillMaxWidth())
            BotonSecundario(stringResource(R.string.cancelar), onCerrar, Modifier.fillMaxWidth())
        }
    }
}

/** La tarjeta como queda: con el fondo de la tienda, sus colores; en formato historia, más alta y con la portada grande. */
@Composable
private fun TarjetaObraEditada(o: Obra, frase: String, fondo: String, historia: Boolean, conNota: Boolean) {
    val estilo = io.github.fogonapps.perfil.TemasPerfil.estilo(fondo)
    val base = FogonTema.actual
    androidx.compose.runtime.CompositionLocalProvider(io.github.fogonapps.identidad.LocalFogon provides (estilo?.let { base.conEstilo(it) } ?: base)) {
        val f = FogonTema.actual
        Box(Modifier.width(300.dp).then(if (historia) Modifier.height(533.dp) else Modifier).clip(forma(24.dp)).background(f.fondo)) {
            if (estilo != null) io.github.fogonapps.perfil.FondoTemaPerfil(fondo, Modifier.matchParentSize()) else Box(Modifier.matchParentSize().haloPuntos(f.tinta2.copy(alpha = 0.3f)))
            Column(Modifier.fillMaxWidth().then(if (historia) Modifier.fillMaxHeight() else Modifier).padding(20.dp),
                verticalArrangement = if (historia) Arrangement.SpaceBetween else Arrangement.spacedBy(12.dp), horizontalAlignment = if (historia) Alignment.CenterHorizontally else Alignment.Start) {
                if (historia) {
                    Spacer(Modifier.height(4.dp))
                    Portada(o.portada, o.tipo, o.titulo, Modifier.width(170.dp))
                    Column(Modifier.fillMaxWidth().clip(forma(18.dp)).background(f.fondo.copy(alpha = 0.82f)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(o.titulo, fontFamily = f.titulos, fontSize = 22.sp, lineHeight = 26.sp, color = f.texto, maxLines = 3, overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        if (conNota && o.nota > 0) Estrellas(o.nota, 22.sp)
                        if (frase.isNotBlank()) Text("“${frase.trim()}”", style = MaterialTheme.typography.bodyMedium, color = f.texto, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                    Marca()
                } else {
                    Row(Modifier.clip(forma(18.dp)).background(f.fondo.copy(alpha = if (estilo != null) 0.82f else 0f)).padding(if (estilo != null) 10.dp else 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
                        Portada(o.portada, o.tipo, o.titulo, Modifier.width(110.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(o.titulo, fontFamily = f.titulos, fontSize = 20.sp, lineHeight = 24.sp, color = f.texto, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(o.anio?.toString(), o.creador.takeIf { it.isNotBlank() }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = f.textoSuave, maxLines = 2)
                            if (conNota && o.nota > 0) Estrellas(o.nota, 20.sp)
                        }
                    }
                    if (frase.isNotBlank()) Text("“${frase.trim()}”", style = MaterialTheme.typography.bodyMedium, color = f.texto,
                        modifier = Modifier.clip(forma(14.dp)).background(f.fondo.copy(alpha = if (estilo != null) 0.82f else 0f)).padding(if (estilo != null) 10.dp else 0.dp))
                    Marca()
                }
            }
        }
    }
}

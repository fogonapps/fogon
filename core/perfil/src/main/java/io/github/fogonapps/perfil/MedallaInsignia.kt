package io.github.fogonapps.perfil

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.fogonapps.identidad.Dibujo
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.IconosFogon
import io.github.fogonapps.identidad.IconosSociales
import io.github.fogonapps.identidad.haloPuntos

@Composable
fun MedallaInsignia(
    icono: Dibujo,
    nombre: String,
    ganada: Boolean,
    lleva: Int? = null,
    meta: Int? = null,
    tamano: Dp = 64.dp,
    onClick: (() -> Unit)? = null,
) {
    val f = FogonTema.actual
    val etiqueta = nombre.lowercase(androidx.compose.ui.platform.LocalConfiguration.current.locales[0])
    val cantidad = lleva ?: 0
    val objetivo = meta ?: 0
    val progreso = if (!ganada && lleva != null && objetivo > 0) {
        (cantidad.toFloat() / objetivo).coerceIn(0f, 1f)
    } else null
    val descripcion = when {
        ganada -> stringResource(R.string.medalla_ganada, etiqueta)
        progreso != null -> stringResource(R.string.medalla_progreso, etiqueta, cantidad, objetivo)
        else -> stringResource(R.string.medalla_pendiente, etiqueta)
    }
    val opacidad = if (ganada) 1f else 0.5f
    val tinta = if (ganada) f.tinta1 else f.textoSuave
    val toque = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier

    Column(
        Modifier.width(maxOf(84.dp, tamano)).then(toque).clearAndSetSemantics { contentDescription = descripcion },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(tamano), contentAlignment = Alignment.Center) {
            Box(Modifier.matchParentSize().alpha(opacidad).clip(CircleShape)
                .background(f.superficie).haloPuntos(f.tinta1.copy(alpha = 0.25f), paso = 6.dp, centro = Offset(0.5f, 0.5f)),
                contentAlignment = Alignment.Center) {
                IconoRiso(icono, null, tamano = tamano * (28f / 64f), tinta = tinta, relleno = tinta.copy(alpha = 0.18f))
            }
            Canvas(Modifier.matchParentSize()) {
                val radioExterior = size.minDimension / 2 - 1.dp.toPx()
                val radioInterior = radioExterior - 4.dp.toPx()
                val aro = Stroke(2.dp.toPx())
                drawCircle(if (ganada) f.tinta1 else f.linea.copy(alpha = f.linea.alpha * opacidad), radioExterior, style = aro)
                drawCircle(f.linea.copy(alpha = f.linea.alpha * opacidad), radioInterior, style = Stroke(1.dp.toPx()))
                if (progreso != null && progreso > 0f) {
                    drawArc(
                        color = f.tinta1,
                        startAngle = -90f,
                        sweepAngle = progreso * 360f,
                        useCenter = false,
                        topLeft = center - Offset(radioExterior, radioExterior),
                        size = Size(radioExterior * 2, radioExterior * 2),
                        style = aro,
                    )
                }
            }
        }
        Text(etiqueta, Modifier.alpha(opacidad), color = f.texto, style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (progreso != null) {
            Text(stringResource(R.string.medalla_fraccion, cantidad, objetivo), Modifier.alpha(opacidad),
                color = f.textoSuave, style = MaterialTheme.typography.labelSmall)
        }
    }
}

fun iconoInsignia(id: String): Dibujo = when (id) {
    "cine" -> IconosSociales.Pelicula
    "series" -> IconosSociales.Serie
    "anime" -> IconosSociales.Anime
    "lector" -> IconosSociales.Libro
    "discos" -> IconosSociales.Disco
    "constancia" -> IconosFogon.Racha
    "critico" -> IconosFogon.Lapiz
    "explorador" -> IconosFogon.Bandera
    else -> IconosSociales.Trofeo
}

@Composable
internal fun nombreInsigniaPerfil(id: String) = stringResource(when (id) {
    "cine" -> R.string.escaparate_ins_cine
    "series" -> R.string.escaparate_ins_series
    "anime" -> R.string.escaparate_ins_anime
    "lector" -> R.string.escaparate_ins_lector
    "discos" -> R.string.escaparate_ins_discos
    "constancia" -> R.string.escaparate_ins_constancia
    "critico" -> R.string.escaparate_ins_critico
    "explorador" -> R.string.escaparate_ins_explorador
    else -> R.string.escaparate_ins_reto
})

package io.github.fogonapps.cajon.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fogonapps.cajon.R
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.IconoRiso

/** De dónde se puede traer el historial y cómo se exporta en cada una (solo el nombre, sin logos). */
private val DE_DONDE = listOf(
    "Letterboxd" to R.string.traer_como_letterboxd,
    "Goodreads" to R.string.traer_como_goodreads,
    "The StoryGraph" to R.string.traer_como_storygraph,
    "Trakt" to R.string.traer_como_trakt,
    "TV Time" to R.string.traer_como_tvtime,
    "Netflix" to R.string.traer_como_netflix,
)

/**
 * «¿Vienes de otra app?»: el título, por qué (ver tus números en un solo lugar), cómo exportar de cada app (se abre
 * al tocar su nombre) y el botón que abre el selector de archivos del flujo de siempre ([Acciones.deOtraApp]).
 * [saltar], si se da, pone el botón secundario para seguir sin traer nada.
 */
@Composable
fun TraerHistorial(acciones: Acciones?, modifier: Modifier = Modifier, conTitulo: Boolean = true, saltar: Pair<String, () -> Unit>? = null) {
    val f = FogonTema.actual
    var abierta by rememberSaveable { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (conTitulo) {
            Text(stringResource(R.string.traer_titulo), fontFamily = f.titulos, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 28.sp,
                color = f.texto, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.traer_texto), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
        }
        Column {
            DE_DONDE.forEach { (nombre, como) ->
                val esta = abierta == nombre
                val estado = stringResource(if (esta) R.string.traer_abierto else R.string.traer_cerrado)
                Box(Modifier.fillMaxWidth().height(1.dp).background(f.linea))
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { abierta = if (esta) null else nombre }
                    .semantics { role = Role.Button; stateDescription = estado }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(nombre, style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.weight(1f))
                    IconoRiso(if (esta) Iconos.Arriba else Iconos.Abajo, null, tamano = 18.dp, tinta = f.textoSuave)
                }
                if (esta) Text(stringResource(como), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave, modifier = Modifier.padding(bottom = 12.dp))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(f.linea))
        }
        if (acciones != null) BotonRiso(stringResource(R.string.traer_elegir), acciones.deOtraApp, Modifier.fillMaxWidth().padding(top = 6.dp))
        saltar?.let { (texto, accion) -> BotonSecundario(texto, accion, Modifier.fillMaxWidth()) }
    }
}

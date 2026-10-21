package io.github.fogonapps.identidad

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** El manifiesto de Fogón, igual en todas las apps y sin necesitar internet. */
@Composable
fun ManifiestoFogon(modifier: Modifier = Modifier) {
    val f = FogonTema.actual
    val parrafo: @Composable (String) -> Unit = {
        Text(it, style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.padding(horizontal = 20.dp))
    }
    val titulo: @Composable (String) -> Unit = {
        Text(
            it.uppercase(), style = MaterialTheme.typography.displaySmall.copy(fontSize = 22.sp, lineHeight = 26.sp), color = f.texto,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp).semantics { heading() },
        )
    }
    Column(modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(
            painterResource(if (f.oscuro) R.drawable.fogon_logotipo_claro else R.drawable.fogon_logotipo), "Fogón",
            Modifier.align(Alignment.CenterHorizontally).size(width = 220.dp, height = 100.dp),
        )
        Text(
            stringResource(R.string.fogon_lema).uppercase(),
            style = MaterialTheme.typography.displayMedium, color = f.acentoTexto, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        Text(
            stringResource(R.string.fogon_man_pregunta),
            style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic), color = f.textoSuave,
            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp),
        )
        parrafo(stringResource(R.string.fogon_man_p1))
        parrafo(stringResource(R.string.fogon_man_p2))
        titulo(stringResource(R.string.fogon_man_t1))
        parrafo(stringResource(R.string.fogon_man_p3))
        parrafo(stringResource(R.string.fogon_man_p4))
        titulo(stringResource(R.string.fogon_man_t2))
        parrafo(stringResource(R.string.fogon_man_p5))
        titulo(stringResource(R.string.fogon_man_t3))
        stringArrayResource(R.array.fogon_promesas).forEach { Logro(it, Modifier.padding(horizontal = 20.dp)) }
        Text(
            stringResource(R.string.fogon_man_cierre), style = MaterialTheme.typography.headlineSmall, color = f.texto,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
    }
}

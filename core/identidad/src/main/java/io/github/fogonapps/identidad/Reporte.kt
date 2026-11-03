package io.github.fogonapps.identidad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.net.URLEncoder

/**
 * "Reportar o sugerir", igual en todas las apps de Fogón: algo falla, falta algo o una idea. Arma el mensaje con la
 * app, la versión y el Android (nada más del teléfono) y lo abre en GitHub (issue nueva, pública) o en el correo.
 * Cada app la pone bajo su propia cabecera, en Perfil o Ajustes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReporteFogon(nombreApp: String, version: String, modifier: Modifier = Modifier) {
    val f = FogonTema.actual
    val uri = LocalUriHandler.current
    var tipo by rememberSaveable { mutableIntStateOf(0) }
    var texto by rememberSaveable { mutableStateOf("") }
    val datos = "$nombreApp $version · Android ${android.os.Build.VERSION.RELEASE}"
    val prefijo = listOf("[Bug] ", "[Falta] ", "[Idea] ")[tipo]
    val titulo = prefijo + texto.trim().lineSequence().firstOrNull().orEmpty().take(70)
    val cuerpo = texto.trim() + "\n\n---\n" + datos
    fun cod(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.fogon_reportar_pista), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.fogon_algo_falla, R.string.fogon_falta_algo, R.string.fogon_tengo_idea).forEachIndexed { i, t ->
                FilterChip(tipo == i, { tipo = i }, { Text(stringResource(t)) }, shape = FormaPildora,
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = f.tinta1, selectedLabelColor = f.sobreTinta1))
            }
        }
        OutlinedTextField(
            texto, { if (it.length <= 3000) texto = it }, Modifier.fillMaxWidth().heightIn(min = 160.dp),
            label = { Text(stringResource(listOf(R.string.fogon_que_paso, R.string.fogon_que_falta, R.string.fogon_tu_idea)[tipo])) },
        )
        Text(stringResource(R.string.fogon_se_agrega, datos), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        URL_CODIGO?.let { url ->
            BotonRiso(stringResource(R.string.fogon_enviar_github), { uri.openUri("$url/issues/new?title=${cod(titulo)}&body=${cod(cuerpo)}") },
                Modifier.fillMaxWidth(), habilitado = texto.isNotBlank())
            Text(stringResource(R.string.fogon_enviar_github_pista), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        }
        CORREO_FOGON?.let { correo ->
            BotonSecundario(stringResource(R.string.fogon_enviar_correo), { uri.openUri("mailto:$correo?subject=${cod(titulo)}&body=${cod(cuerpo)}") },
                Modifier.fillMaxWidth(), habilitado = texto.isNotBlank())
            Text(stringResource(R.string.fogon_enviar_correo_pista, correo), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        }
    }
}

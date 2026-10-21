package io.github.fogonapps.identidad

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Una pieza de terceros (o la propia app) y su licencia.
 * [archivo] es el texto completo en assets/licencias/; null si la licencia no exige incluirlo.
 */
data class Licencia(val nombre: String, val detalle: String, val archivo: String? = null)

/**
 * Lo que llevan todas las apps de Fogón: el código propio, las letras y las bibliotecas.
 * La Public Suffix List viene dentro de OkHttp: solo la llevan las apps que usan internet ([conRed]).
 */
@Composable
fun licenciasFogon(conRed: Boolean = true) = listOfNotNull(
    Licencia(stringResource(R.string.fogon_lic_app), stringResource(R.string.fogon_lic_app_texto), "gpl-3.0.txt"),
    Licencia("Anton", stringResource(R.string.fogon_lic_anton), "ofl-anton.txt"),
    Licencia("Bagel Fat One", stringResource(R.string.fogon_lic_bagel), "ofl-bagel-fat-one.txt"),
    Licencia(stringResource(R.string.fogon_lic_bibliotecas), stringResource(R.string.fogon_lic_bibliotecas_texto), "apache-2.0.txt"),
    if (conRed) Licencia("Public Suffix List", stringResource(R.string.fogon_lic_psl), "mpl-2.0.txt") else null,
)

/** Lista de licencias; cada una muestra su texto completo al tocarla. */
@Composable
fun LicenciasFogon(propias: List<Licencia>, modifier: Modifier = Modifier, conRed: Boolean = true) {
    val f = FogonTema.actual
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            stringResource(R.string.fogon_lic_titulo),
            style = MaterialTheme.typography.titleLarge, color = f.texto,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).semantics { heading() },
        )
        Text(
            stringResource(R.string.fogon_lic_pista),
            style = MaterialTheme.typography.bodyMedium, color = f.textoSuave,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        val comunes = licenciasFogon(conRed)
        (comunes.take(1) + propias + comunes.drop(1)).forEach { FilaLicencia(it) }
    }
}

@Composable
private fun FilaLicencia(l: Licencia) {
    val f = FogonTema.actual
    val contexto = LocalContext.current
    var abierta by rememberSaveable(l.nombre) { mutableStateOf(false) }
    val texto = remember(l.archivo, abierta) {
        if (abierta && l.archivo != null) contexto.assets.open("licencias/${l.archivo}").bufferedReader().use { it.readText() } else null
    }
    Column(
        Modifier.fillMaxWidth()
            .then(if (l.archivo != null) Modifier.clickable { abierta = !abierta } else Modifier)
            .heightIn(min = 52.dp)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(l.nombre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = f.texto)
        Text(l.detalle, style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        if (l.archivo != null) Text(
            stringResource(if (abierta) R.string.fogon_ocultar_texto else R.string.fogon_ver_texto),
            style = MaterialTheme.typography.labelLarge, color = f.acentoTexto,
        )
        texto?.let {
            Text(it, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp), color = f.texto)
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = f.textoSuave.copy(alpha = 0.25f))
}

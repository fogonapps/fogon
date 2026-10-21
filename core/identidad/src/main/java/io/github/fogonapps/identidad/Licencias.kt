package io.github.fogonapps.identidad

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

/** Lo que llevan todas las apps de Fogón: el código propio, las letras y las bibliotecas. */
val LICENCIAS_FOGON = listOf(
    Licencia("Esta app", "Código de Fogón, GPL-3.0 o posterior. Sin ninguna garantía (secciones 15 y 16).", "gpl-3.0.txt"),
    Licencia("Anton", "Letra. © 2020 The Anton Project Authors. SIL Open Font License 1.1.", "ofl-anton.txt"),
    Licencia("Bagel Fat One", "Letra. © 2022 The Bagel Fat Project Authors. SIL Open Font License 1.1.", "ofl-bagel-fat-one.txt"),
    Licencia(
        "Bibliotecas de Android, Kotlin, OkHttp y Okio",
        "AndroidX y Jetpack Compose (Google), Kotlin y kotlinx (JetBrains), OkHttp y Okio (Square), Guava ListenableFuture y JSpecify. Apache License 2.0.",
        "apache-2.0.txt",
    ),
    Licencia(
        "Public Suffix List",
        "Lista de dominios de Mozilla que trae OkHttp, sin cambios. Mozilla Public License 2.0. Código fuente: https://publicsuffix.org/list/",
        "mpl-2.0.txt",
    ),
)

/** Lista de licencias; cada una muestra su texto completo al tocarla. */
@Composable
fun LicenciasFogon(propias: List<Licencia>, modifier: Modifier = Modifier) {
    val f = FogonTema.actual
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            "Mi código y el de otros",
            style = MaterialTheme.typography.titleLarge, color = f.texto,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).semantics { heading() },
        )
        Text(
            "Esta app está hecha con piezas libres de otras personas. Estas son sus licencias; toca una para leer el texto completo.",
            style = MaterialTheme.typography.bodyMedium, color = f.textoSuave,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        (LICENCIAS_FOGON.take(1) + propias + LICENCIAS_FOGON.drop(1)).forEach { FilaLicencia(it) }
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
            if (abierta) "Ocultar texto" else "Ver texto completo",
            style = MaterialTheme.typography.labelLarge, color = f.acentoTexto,
        )
        texto?.let {
            Text(it, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp), color = f.texto)
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = f.textoSuave.copy(alpha = 0.25f))
}

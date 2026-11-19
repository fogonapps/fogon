package io.github.fogonapps.social.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fogonapps.identidad.FilaAjuste
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.IconosFogon
import io.github.fogonapps.social.Contadores
import io.github.fogonapps.social.LoteContadores
import io.github.fogonapps.social.PermisoContadores
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.serialization.json.Json

@Composable
fun contadoresDeLaApp(): Contadores = Contadores.de(LocalContext.current)

@Composable
fun ContarEntrada(nombre: String?) {
    val contadores = contadoresDeLaApp()
    LaunchedEffect(nombre) { nombre?.let(contadores::contar) }
}

@Composable
fun AjustesContadores() {
    val contexto = LocalContext.current
    val contadores = contadoresDeLaApp()
    val permiso by contadores.permiso.collectAsState()
    var muestra by remember { mutableStateOf<LoteContadores?>(null) }
    FilaAjuste(
        IconosFogon.Candado,
        stringResource(R.string.contadores_titulo),
        stringResource(R.string.contadores_pista),
        permiso == PermisoContadores.SI,
        contadores::permitir,
    )
    FilaAjuste(IconosFogon.Lista, stringResource(R.string.contadores_ver), null) { muestra = contadores.verSumas() }
    muestra?.let { datos ->
        DialogoSumas(datos, cerrar = { muestra = null }, enviar = accionEnvioContadores(contexto))
    }
}

@Composable
private fun DialogoSumas(datos: LoteContadores, cerrar: () -> Unit, enviar: (() -> Unit)?) {
    val tema = FogonTema.actual
    val contexto = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val formato = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale) }
    var exactos by remember(datos) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = cerrar,
        containerColor = tema.fondo,
        title = { Text(stringResource(R.string.contadores_ver), color = tema.texto) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.contadores_version, contexto.applicationInfo.loadLabel(contexto.packageManager), datos.version), color = tema.texto)
                if (datos.dias.isEmpty()) {
                    Text(stringResource(R.string.contadores_vacio), color = tema.texto)
                } else {
                    for (dia in datos.dias) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(LocalDate.parse(dia.dia).format(formato), color = tema.texto, style = MaterialTheme.typography.titleSmall)
                            for ((clave, total) in dia.contadores) {
                                @Suppress("DiscouragedApi") // Las claves futuras conservan su nombre técnico.
                                val recurso = contexto.resources.getIdentifier("contador_" + clave.replace('.', '_'), "string", contexto.packageName)
                                val nombre = if (recurso == 0) clave else stringResource(recurso)
                                Text(stringResource(R.string.contadores_linea, nombre, total), color = tema.texto)
                            }
                        }
                    }
                }
                if (datos.dias.isNotEmpty() || enviar != null) Column {
                    if (datos.dias.isNotEmpty()) {
                        TextButton({ exactos = !exactos }) {
                            val texto = if (exactos) R.string.contadores_ocultar_exactos else R.string.contadores_exactos
                            Text(stringResource(texto), color = tema.texto)
                        }
                        if (exactos) Text(Json.encodeToString(datos), color = tema.texto)
                    }
                    if (enviar != null) TextButton({ enviar(); cerrar() }) {
                        Text(stringResource(R.string.contadores_enviar_ahora), color = tema.texto)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(cerrar) { Text(stringResource(R.string.contadores_cerrar), color = tema.texto) }
        },
    )
}

package io.github.fogonapps.identidad

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate

private const val DIAS_MINIMOS = 14
private const val DIAS_DE_USO = "dias_de_uso"
private const val ULTIMO_USO = "ultimo_uso"
private const val ULTIMO_AVISO = "ultimo_aviso"
private const val TOCO_APOYAR = "toco_apoyar"
private const val APAGADO = "apagado"
private const val URL_APOYO = "https://fogonapps.github.io/#apoyar"

fun debeMostrarAvisoApoyo(
    diasDeUso: Int,
    hoy: LocalDate,
    ultimoAviso: LocalDate? = null,
    tocoApoyar: Boolean = false,
    apagado: Boolean = false,
    tiendaPlay: Boolean = false,
    yaApoya: Boolean = false,
    enTutorial: Boolean = false,
): Boolean {
    if (diasDeUso < DIAS_MINIMOS || apagado || tiendaPlay || yaApoya || enTutorial) return false
    val espera = if (tocoApoyar) 90L else 30L
    return ultimoAviso == null || hoy.toEpochDay() - ultimoAviso.toEpochDay() >= espera
}

private fun preferenciasApoyo(contexto: Context) =
    contexto.getSharedPreferences("apoyo", Context.MODE_PRIVATE)

/** Llamar al volver al primer plano. El contador solo necesita guardar hasta el umbral. */
fun registrarDiaDeUsoParaApoyo(contexto: Context) {
    if (BuildConfig.TIENDA_PLAY) return
    val preferencias = preferenciasApoyo(contexto)
    val hoy = LocalDate.now().toString()
    if (preferencias.getString(ULTIMO_USO, null) == hoy) return

    val dias = preferencias.getStringSet(DIAS_DE_USO, emptySet()).orEmpty()
    preferencias.edit().apply {
        putString(ULTIMO_USO, hoy)
        if (dias.size < DIAS_MINIMOS) putStringSet(DIAS_DE_USO, dias + hoy)
    }.apply()
}

private fun SharedPreferences.guardarAviso(hoy: LocalDate, tocoApoyar: Boolean) {
    edit().putLong(ULTIMO_AVISO, hoy.toEpochDay()).putBoolean(TOCO_APOYAR, tocoApoyar).apply()
}

@Composable
fun AvisoApoyo(nombreApp: String, enTutorial: Boolean = false, yaApoya: Boolean = false) {
    if (BuildConfig.TIENDA_PLAY || enTutorial || yaApoya) return
    val contexto = LocalContext.current
    val preferencias = remember(contexto) { preferenciasApoyo(contexto) }
    var diaDeUso by remember(preferencias) { mutableStateOf(preferencias.getString(ULTIMO_USO, null)) }
    DisposableEffect(preferencias) {
        val escucha = SharedPreferences.OnSharedPreferenceChangeListener { _, clave ->
            if (clave == ULTIMO_USO) diaDeUso = preferencias.getString(ULTIMO_USO, null)
        }
        preferencias.registerOnSharedPreferenceChangeListener(escucha)
        diaDeUso = preferencias.getString(ULTIMO_USO, null)
        onDispose { preferencias.unregisterOnSharedPreferenceChangeListener(escucha) }
    }

    // Mantener la tarjeta en pantalla después de registrar que se vio, hasta que se responda.
    var mostrado by remember(diaDeUso) { mutableStateOf(false) }
    var respondido by remember(diaDeUso) { mutableStateOf(false) }
    val hoy = LocalDate.now()
    val ultimoAviso = preferencias.getLong(ULTIMO_AVISO, Long.MIN_VALUE)
        .takeUnless { it == Long.MIN_VALUE }?.let(LocalDate::ofEpochDay)
    if (respondido || (!mostrado && !debeMostrarAvisoApoyo(
            diasDeUso = preferencias.getStringSet(DIAS_DE_USO, emptySet()).orEmpty().size,
            hoy = hoy,
            ultimoAviso = ultimoAviso,
            tocoApoyar = preferencias.getBoolean(TOCO_APOYAR, false),
            apagado = preferencias.getBoolean(APAGADO, false),
        ))
    ) return

    val f = FogonTema.actual
    val uri = LocalUriHandler.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
            .onVisibilityChanged(minDurationMs = 250L, minFractionVisible = 0.25f) { visible ->
                if (visible && !mostrado && !respondido) {
                    mostrado = true
                    preferencias.guardarAviso(LocalDate.now(), tocoApoyar = false)
                }
            }
            .tarjetaFogon().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.fogon_apoyo_titulo, nombreApp),
            style = MaterialTheme.typography.titleMedium,
            color = f.texto,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.fogon_apoyo_cuerpo), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
        BotonRiso(stringResource(R.string.fogon_apoyo_apoyar), {
            respondido = true
            preferencias.guardarAviso(LocalDate.now(), tocoApoyar = true)
            abrirApoyo(uri::openUri)
        }, Modifier.fillMaxWidth())
        BotonSecundario(stringResource(R.string.fogon_apoyo_ahora_no), {
            respondido = true
            preferencias.guardarAviso(LocalDate.now(), tocoApoyar = false)
        }, Modifier.fillMaxWidth())
        BotonSecundario(stringResource(R.string.fogon_apoyo_no_volver), {
            respondido = true
            preferencias.edit().putBoolean(APAGADO, true).apply()
        }, Modifier.fillMaxWidth())
    }
}

@Composable
internal fun OpcionesApoyo(onAbrirEnlace: (String) -> Unit) {
    if (BuildConfig.TIENDA_PLAY) return
    val contexto = LocalContext.current
    val preferencias = remember(contexto) { preferenciasApoyo(contexto) }
    var activado by remember(preferencias) { mutableStateOf(!preferencias.getBoolean(APAGADO, false)) }

    BotonSecundario(stringResource(R.string.fogon_apoyo_acerca), {
        preferencias.guardarAviso(LocalDate.now(), tocoApoyar = true)
        abrirApoyo(onAbrirEnlace)
    })
    Interruptor(stringResource(R.string.fogon_apoyo_recordar), "", activado) {
        activado = it
        preferencias.edit().putBoolean(APAGADO, !it).apply()
    }
}

private fun abrirApoyo(onAbrirEnlace: (String) -> Unit) {
    try {
        onAbrirEnlace(URL_APOYO)
    } catch (_: IllegalArgumentException) {
        // El sistema puede no tener una app que abra el enlace.
    }
}

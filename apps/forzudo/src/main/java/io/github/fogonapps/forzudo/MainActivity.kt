package io.github.fogonapps.forzudo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.Paleta
import io.github.fogonapps.forzudo.datos.Estado
import io.github.fogonapps.forzudo.datos.iso
import io.github.fogonapps.forzudo.ui.App

class MainActivity : ComponentActivity() {
    private val vm: GymViewModel by viewModels()

    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(io.github.fogonapps.identidad.Idioma.envolver(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        val arranque = installSplashScreen()
        super.onCreate(savedInstanceState)
        arranque.setKeepOnScreenCondition { vm.estado.value == null }
        setContent {
            val apariencia by io.github.fogonapps.identidad.AlmacenApariencia.de(this).estado.collectAsStateWithLifecycle()
            // Barras del sistema con íconos claros u oscuros según el fondo del estilo elegido.
            val oscuro = io.github.fogonapps.identidad.fogonDe(Paleta.Forzudo, apariencia).oscuro
            LaunchedEffect(oscuro) {
                val t = android.graphics.Color.TRANSPARENT
                val barra = if (oscuro) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
                enableEdgeToEdge(barra, barra)
            }
            FogonTema(Paleta.Forzudo, apariencia) {
                val pila by vm.pila.collectAsStateWithLifecycle()
                val pestana by vm.pestana.collectAsStateWithLifecycle()
                val estado by vm.estado.collectAsStateWithLifecycle()
                BackHandler(enabled = pila.isNotEmpty() || pestana != Pestana.INICIO) { vm.atras() }

                val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                    if (uri != null) runCatching {
                        contentResolver.openOutputStream(uri)?.use { it.write(vm.textoRespaldo().encodeToByteArray()) }
                    }.onSuccess { aviso(getString(R.string.aviso_respaldo_guardado)) }.onFailure { aviso(getString(R.string.aviso_no_exportado)) }
                }
                var porImportar by remember { mutableStateOf<Estado?>(null) }
                val importar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) {
                        // Tope de 10 MB: un respaldo real pesa unos cientos de KB; un archivo enorme podría agotar la memoria.
                        val texto = runCatching {
                            contentResolver.openInputStream(uri)?.use { ent ->
                                val bytes = ent.buffered().readBytesHasta(MAX_RESPALDO + 1)
                                if (bytes.size > MAX_RESPALDO) null else bytes.decodeToString()
                            }
                        }.getOrNull()
                        val d = texto?.let { vm.leerRespaldo(it) }
                        if (d == null) aviso(getString(R.string.aviso_no_es_respaldo)) else porImportar = d
                    }
                }
                porImportar?.let { d ->
                    AlertDialog(
                        onDismissRequest = { porImportar = null },
                        title = { Text(stringResource(R.string.importar_titulo)) },
                        text = { Text(pluralStringResource(R.plurals.importar_texto, d.sessions.size, d.sessions.size)) },
                        confirmButton = { TextButton({ vm.importar(d); porImportar = null }) { Text(stringResource(R.string.importar_si)) } },
                        dismissButton = { TextButton({ porImportar = null }) { Text(stringResource(R.string.cancelar)) } },
                    )
                }

                // El permiso de notificaciones se pide la primera vez que empieza un entreno (para avisar el fin del descanso).
                val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                LaunchedEffect(estado?.active != null) {
                    if (estado?.active != null && Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) permiso.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

                App(
                    vm,
                    onExportar = { exportar.launch("el-forzudo-respaldo-${iso(vm.hoy())}.json") },
                    onImportar = { importar.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                )
            }
        }
    }

    private fun aviso(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    private companion object {
        const val MAX_RESPALDO = 10 * 1024 * 1024
    }

    override fun onStop() {
        super.onStop()
        vm.guardarAlSalir()
    }

    override fun onStart() {
        super.onStart()
        vm.sincronizar()
    }
}

/** Lee como mucho [max] bytes (InputStream.readNBytes solo existe desde Android 13). */
private fun java.io.InputStream.readBytesHasta(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    while (out.size() < max) {
        val n = read(buf, 0, minOf(buf.size, max - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

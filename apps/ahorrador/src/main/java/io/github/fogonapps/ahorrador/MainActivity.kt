package io.github.fogonapps.ahorrador

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.fogonapps.ahorrador.datos.Estado
import io.github.fogonapps.ahorrador.ui.App
import io.github.fogonapps.ahorrador.ui.Bloqueada
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.Idioma
import io.github.fogonapps.identidad.Paleta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: AhorradorViewModel by viewModels()

    /** Con el candado activado, la app arranca bloqueada y se abre con la huella o el bloqueo del teléfono. */
    private val bloqueada = MutableStateFlow(false)

    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(Idioma.envolver(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        val arranque = installSplashScreen()
        super.onCreate(savedInstanceState)
        arranque.setKeepOnScreenCondition { vm.estado.value == null }
        bloqueada.value = vm.candado.value && candadoDisponible()
        setContent {
            val apariencia by io.github.fogonapps.identidad.AlmacenApariencia.de(this).estado.collectAsStateWithLifecycle()
            // Barras del sistema con íconos claros u oscuros según el fondo del estilo elegido.
            val oscuro = io.github.fogonapps.identidad.fogonDe(Paleta.Ahorrador, apariencia).oscuro
            LaunchedEffect(oscuro) {
                val t = android.graphics.Color.TRANSPARENT
                val barra = if (oscuro) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
                enableEdgeToEdge(barra, barra)
            }
            FogonTema(Paleta.Ahorrador, apariencia) {
                val pila by vm.pila.collectAsStateWithLifecycle()
                val pestana by vm.pestana.collectAsStateWithLifecycle()
                val estado by vm.estado.collectAsStateWithLifecycle()
                val cerrada by bloqueada.collectAsStateWithLifecycle()
                if (cerrada) {
                    LaunchedEffect(Unit) { desbloquear() }
                    Bloqueada { desbloquear() }
                    return@FogonTema
                }
                BackHandler(enabled = pila.isNotEmpty() || pestana != Pestana.INICIO) { vm.atras() }

                val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                    if (uri != null) runCatching {
                        contentResolver.openOutputStream(uri)?.use { it.write(vm.exportarJson().encodeToByteArray()) }
                    }.onSuccess { aviso(getString(R.string.aviso_respaldo_guardado)) }.onFailure { aviso(getString(R.string.aviso_no_exportado)) }
                }
                val exportarCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
                    if (uri != null) runCatching {
                        contentResolver.openOutputStream(uri)?.use { it.write(vm.exportarCsv().encodeToByteArray()) }
                    }.onSuccess { aviso(getString(R.string.aviso_csv_guardado)) }.onFailure { aviso(getString(R.string.aviso_no_exportado)) }
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
                        text = { Text(pluralStringResource(R.plurals.importar_texto, d.movimientos.size, d.movimientos.size)) },
                        confirmButton = { TextButton({ vm.importar(d); porImportar = null }) { Text(stringResource(R.string.importar_si)) } },
                        dismissButton = { TextButton({ porImportar = null }) { Text(stringResource(R.string.cancelar)) } },
                    )
                }

                // El permiso de notificaciones se pide cuando la persona crea su primer pago fijo.
                val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                LaunchedEffect(estado?.fijos?.isNotEmpty() == true) {
                    if (estado?.fijos?.isNotEmpty() == true && Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) permiso.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

                App(
                    vm,
                    onExportar = { exportar.launch("el-ahorrador-respaldo-${vm.hoy()}.json") },
                    onExportarCsv = { exportarCsv.launch("el-ahorrador-movimientos-${vm.hoy()}.csv") },
                    onImportar = { importar.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    candadoDisponible = candadoDisponible(),
                )
            }
        }
    }

    /**
     * Android 10 o más y un bloqueo de pantalla puesto (PIN, patrón, clave o huella). Sin bloqueo no se
     * ofrece: no habría cómo abrir la app.
     */
    private fun candadoDisponible() = Build.VERSION.SDK_INT >= 29 &&
        getSystemService(android.app.KeyguardManager::class.java)?.isDeviceSecure == true

    private fun desbloquear() {
        // Si la persona quitó el bloqueo del teléfono, el candado ya no tiene cómo pedirse: la app se abre.
        if (Build.VERSION.SDK_INT < 29 || !candadoDisponible()) { bloqueada.value = false; return }
        pedirHuella()
    }

    @RequiresApi(29)
    private fun pedirHuella() {
        @Suppress("DEPRECATION")
        val prompt = BiometricPrompt.Builder(this)
            .setTitle(getString(R.string.candado_titulo))
            .setDeviceCredentialAllowed(true)
            .build()
        prompt.authenticate(CancellationSignal(), mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { bloqueada.value = false }
        })
    }

    private fun aviso(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    private companion object {
        const val MAX_RESPALDO = 10 * 1024 * 1024
    }

    override fun onStop() {
        super.onStop()
        lifecycleScope.launch { vm.guardarYa() }
        // Al volver de otra app se vuelve a pedir la huella.
        if (vm.candado.value && candadoDisponible() && !isChangingConfigurations) bloqueada.value = true
    }

    override fun onStart() {
        super.onStart()
        vm.anotarFijos()
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

package io.github.fogonapps.cucha

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
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
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import io.github.fogonapps.cucha.datos.Estado
import io.github.fogonapps.cucha.ui.Acciones
import io.github.fogonapps.cucha.ui.App
import io.github.fogonapps.identidad.AlmacenApariencia
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.Idioma
import io.github.fogonapps.identidad.Paleta
import io.github.fogonapps.identidad.fogonDe
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: CuchaViewModel by viewModels()

    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(Idioma.envolver(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        val arranque = installSplashScreen()
        super.onCreate(savedInstanceState)
        arranque.setKeepOnScreenCondition { vm.estado.value == null }
        if (savedInstanceState == null) abrirDesdeWidget(intent)
        setContent {
            val apariencia by AlmacenApariencia.de(this).estado.collectAsStateWithLifecycle()
            // Barras del sistema con íconos claros u oscuros según el fondo del estilo elegido.
            val oscuro = fogonDe(Paleta.Cucha, apariencia).oscuro
            LaunchedEffect(oscuro) {
                val t = android.graphics.Color.TRANSPARENT
                val barra = if (oscuro) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
                enableEdgeToEdge(barra, barra)
            }
            FogonTema(Paleta.Cucha, apariencia) {
                val pila by vm.pila.collectAsStateWithLifecycle()
                val pestana by vm.pestana.collectAsStateWithLifecycle()
                BackHandler(enabled = pila.isNotEmpty() || pestana != Pestana.HOY) { vm.atras() }

                val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                    if (uri != null) runCatching {
                        contentResolver.openOutputStream(uri, "wt")?.use { it.write(vm.exportarJson().encodeToByteArray()) }
                    }.onSuccess { aviso(getString(R.string.aviso_respaldo_guardado)) }.onFailure { aviso(getString(R.string.aviso_no_exportado)) }
                }
                var porImportar by remember { mutableStateOf<Estado?>(null) }
                val importar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) {
                        val d = leerTexto(uri)?.let { vm.leerRespaldo(it) }
                        if (d == null) aviso(getString(R.string.aviso_no_es_respaldo)) else porImportar = d
                    }
                }
                val importarRecetas = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) leerTexto(uri)?.let { vm.importarRecetas(it) } ?: aviso(getString(R.string.aviso_no_son_recetas))
                }
                // El escáner: abre la cámara (pide el permiso la primera vez) y devuelve el código leído.
                var paraEscanear by remember { mutableStateOf<Pair<String, String>?>(null) }
                val escanear = rememberLauncherForActivityResult(ScanContract()) { r ->
                    val destino = paraEscanear
                    if (r.contents != null && destino != null) vm.buscarCodigo(r.contents, destino.first, destino.second)
                }
                porImportar?.let { d ->
                    AlertDialog(
                        onDismissRequest = { porImportar = null },
                        title = { Text(stringResource(R.string.importar_titulo)) },
                        text = { Text(stringResource(R.string.importar_texto)) },
                        confirmButton = { TextButton({ vm.importar(d); porImportar = null }) { Text(stringResource(R.string.importar_si)) } },
                        dismissButton = { TextButton({ porImportar = null }) { Text(stringResource(R.string.cancelar)) } },
                    )
                }

                App(vm, Acciones(
                    exportar = { exportar.launch("la-cucha-respaldo-${vm.hoy()}.json") },
                    importar = { importar.launch(TIPOS_JSON) },
                    importarRecetas = { importarRecetas.launch(TIPOS_JSON) },
                    escanear = { comida, fecha ->
                        paraEscanear = comida to fecha
                        escanear.launch(ScanOptions().setPrompt(getString(R.string.escanear_pista)).setBeepEnabled(false).setOrientationLocked(false))
                    },
                ))
            }
        }
    }

    /** Lee un archivo de texto elegido por la persona, con un tope de 10 MB. */
    private fun leerTexto(uri: android.net.Uri): String? = runCatching {
        contentResolver.openInputStream(uri)?.use { ent ->
            val bytes = ent.buffered().readBytesHasta(MAX_ARCHIVO + 1)
            if (bytes.size > MAX_ARCHIVO) null else bytes.decodeToString()
        }
    }.getOrNull()

    private fun aviso(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    companion object {
        private const val MAX_ARCHIVO = 10 * 1024 * 1024
        private val TIPOS_JSON = arrayOf("application/json", "text/plain", "application/octet-stream")
        /** El widget abre la app directo en Anotar. */
        const val EXTRA_ANOTAR = "anotar"
    }

    /** Si la app se abrió desde el botón Anotar del widget, va directo a buscar qué anotar. */
    private fun abrirDesdeWidget(i: Intent?) {
        if (i?.getBooleanExtra(EXTRA_ANOTAR, false) == true) {
            i.removeExtra(EXTRA_ANOTAR)
            vm.anotarAhora()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        abrirDesdeWidget(intent)
    }

    override fun onStart() {
        super.onStart()
        vm.leerForzudo()
    }

    override fun onStop() {
        super.onStop()
        lifecycleScope.launch { vm.guardarYa() }
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

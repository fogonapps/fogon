package io.github.fogonapps.cucha

import androidx.compose.ui.unit.dp
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

class MainActivity : ComponentActivity() {
    private var perfilVm: String? = null
    /** Un ViewModel por perfil del teléfono: al cambiar de perfil se arma uno nuevo. */
    private val vm: CuchaViewModel get() {
        val perfil = (application as CuchaApp).perfilActivo.value
        if (perfilVm != null && perfilVm != perfil) viewModelStore.clear()
        perfilVm = perfil
        return androidx.lifecycle.ViewModelProvider(this)[CuchaViewModel::class.java]
    }

    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(Idioma.envolver(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        val arranque = installSplashScreen()
        super.onCreate(savedInstanceState)
        arranque.setKeepOnScreenCondition { vm.estado.value == null }
        if (savedInstanceState == null) { abrirDesdeWidget(intent); abrirChatAvisado(intent) }
        setContent {
            val perfil by (application as CuchaApp).perfilActivo.collectAsStateWithLifecycle()
            androidx.compose.runtime.key(perfil) {
            val vm = vm
            LaunchedEffect(perfil) { vm.leerForzudo() }
            val apariencia by AlmacenApariencia.de(this).estado.collectAsStateWithLifecycle()
            // Barras del sistema con íconos claros u oscuros según el fondo del estilo elegido.
            val oscuro = fogonDe(Paleta.Cucha, apariencia).oscuro
            LaunchedEffect(oscuro) {
                val t = android.graphics.Color.TRANSPARENT
                val barra = if (oscuro) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
                enableEdgeToEdge(barra, barra)
            }
            FogonTema(Paleta.Cucha, apariencia) {
                // Los campos de texto redondeados, como el resto de Fogón (por defecto vienen casi rectos).
                androidx.compose.material3.MaterialTheme(shapes = androidx.compose.material3.MaterialTheme.shapes.copy(
                    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(18.dp))) {
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
                // Una foto del teléfono para una receta: el selector de fotos del sistema (no pide permisos).
                var alElegir by remember { mutableStateOf<((android.net.Uri) -> Unit)?>(null) }
                val manejar_imagen2: (android.net.Uri?) -> Unit = { uri -> if (uri != null) alElegir?.invoke(uri) }
                // Antes de usarla(s), el paso para girarlas.
                val girar_imagen2 = io.github.fogonapps.social.ui.conGiro { l -> manejar_imagen2(l.firstOrNull()) }
                val imagen = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { r -> if (r != null) girar_imagen2(listOf(r)) else manejar_imagen2(null) }
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
                    elegirImagen = { cb -> alElegir = cb; imagen.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                ))
            }
            }
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

    /** Desde el aviso de un mensaje: el chat con quien escribió (o la lista, si fueron varios). */
    private fun abrirChatAvisado(i: Intent?) {
        val chat = i?.getStringExtra(io.github.fogonapps.social.ui.Avisos.EXTRA_CHAT) ?: return
        i.removeExtra(io.github.fogonapps.social.ui.Avisos.EXTRA_CHAT)
        vm.abrir(if (chat.isBlank()) Pantalla.Social("mensajes") else Pantalla.Social("chat", usuario = chat))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        abrirDesdeWidget(intent)
        abrirChatAvisado(intent)
    }

    override fun onStart() {
        super.onStart()
        vm.leerForzudo()
    }

    override fun onStop() {
        super.onStop()
        vm.guardarAlSalir()
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

package io.github.fogonapps.cajon

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.Modifier
import io.github.fogonapps.cajon.datos.Importar
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.ui.Acciones
import io.github.fogonapps.cajon.ui.App
import io.github.fogonapps.identidad.AlmacenApariencia
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.Idioma
import io.github.fogonapps.identidad.Paleta
import io.github.fogonapps.identidad.fogonDe
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: CajonViewModel by viewModels()
    private var cambiandoPerfil = false
    private fun cambiarPerfil(id: String, descartar: Boolean = false) {
        if (cambiandoPerfil) return
        val app = application as CajonApp
        if (id == app.perfiles.lista.activo || app.perfiles.lista.perfiles.none { it.id == id }) return
        cambiandoPerfil = true
        lifecycleScope.launch {
            try {
                val anterior = vm.recursos.id
                vm.detenerPerfil(esperar = descartar)
                (getSystemService(android.app.NotificationManager::class.java)).cancel(anterior, 7)
                app.perfiles.cambiar(id)
                if (descartar) {
                    if (anterior == "1") app.migracion.confirmarArranque()
                    app.cerrarRecursos(anterior)
                    app.perfiles.quitar(anterior) { id ->
                        io.github.fogonapps.cajon.datos.borrarClavesPerfil(app, id)
                        check(File(cacheDir, "compartir/$id").let { !it.exists() || it.deleteRecursively() })
                    }
                }
                WidgetCurso.actualizar(app)
                viewModelStore.clear()
                recreate()
            } catch (_: Exception) {
                viewModelStore.clear()
                cambiandoPerfil = false
                aviso(getString(R.string.perfiles_error))
                recreate()
            }
        }
    }

    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(Idioma.envolver(base))

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("perfil")?.let { cambiarPerfil(it) }
        abrirEnlace(intent)
    }

    /** Un enlace de la red: /@usuario abre ese perfil y /p/<id> esa publicación. */
    private fun abrirEnlace(intent: android.content.Intent?) {
        // Compartido desde otra app (una peli de Netflix, un álbum de Spotify…): se busca para agregarlo.
        if (intent?.action == android.content.Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(android.content.Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { vm.recibirCompartido(it) }
            return
        }
        val ruta = intent?.takeIf { it.action == android.content.Intent.ACTION_VIEW }?.data?.path ?: return
        when {
            ruta.startsWith("/@") -> ruta.removePrefix("/@").trim('/').takeIf { it.isNotBlank() }?.let { vm.abrir(Pantalla.PerfilRed(it.lowercase())) }
            ruta.startsWith("/p/") -> ruta.removePrefix("/p/").trim('/').toLongOrNull()?.let { vm.abrir(Pantalla.PublicacionRed(it)) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val arranque = installSplashScreen()
        super.onCreate(savedInstanceState)
        val perfilNotificado = intent?.getStringExtra("perfil")
        if (savedInstanceState == null && perfilNotificado != null) {
            val app = application as CajonApp
            if (app.perfiles.lista.perfiles.any { it.id == perfilNotificado }) app.perfiles.cambiar(perfilNotificado)
            intent.removeExtra("perfil")
        }
        vm.alCambiarPerfil = ::cambiarPerfil
        arranque.setKeepOnScreenCondition { vm.estado.value == null }
        if (savedInstanceState == null) abrirEnlace(intent)
        setContent {
            val apariencia by AlmacenApariencia.de(this).estado.collectAsStateWithLifecycle()
            // Barras del sistema con íconos claros u oscuros según el fondo del estilo elegido.
            val oscuro = fogonDe(Paleta.Cajon, apariencia).oscuro
            LaunchedEffect(oscuro) {
                val t = android.graphics.Color.TRANSPARENT
                val barra = if (oscuro) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
                enableEdgeToEdge(barra, barra)
            }
            FogonTema(Paleta.Cajon, apariencia) {
                val pila by vm.pila.collectAsStateWithLifecycle()
                val pestana by vm.pestana.collectAsStateWithLifecycle()
                BackHandler(enabled = pila.isNotEmpty() || pestana != Pestana.INICIO) { vm.atras() }

                var perfilOperacion by rememberSaveable { mutableStateOf(vm.recursos.id) }
                fun operacionVigente() = perfilOperacion == vm.recursos.id
                val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                    if (uri != null && !operacionVigente()) aviso(getString(R.string.perfiles_error))
                    else if (uri != null) runCatching {
                        contentResolver.openOutputStream(uri, "wt")?.use { it.write(vm.exportarJson().encodeToByteArray()) }
                    }.onSuccess { aviso(getString(R.string.aviso_respaldo_guardado)) }.onFailure { aviso(getString(R.string.aviso_no_exportado)) }
                }
                var porImportar by remember { mutableStateOf<Estado?>(null) }
                val importar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null && !operacionVigente()) aviso(getString(R.string.perfiles_error))
                    else if (uri != null) {
                        val d = leer(uri)?.decodeToString()?.let { vm.leerRespaldo(it) }
                        if (d == null) aviso(getString(R.string.aviso_no_es_respaldo)) else porImportar = d
                    }
                }
                var netflixPendiente by remember { mutableStateOf<Pair<ByteArray, String>?>(null) }
                netflixPendiente?.let { (bytes, nombre) ->
                    fun elegir(perfil: String?) {
                        netflixPendiente = null
                        if (operacionVigente()) vm.importarDeOtraApp(bytes, nombre, perfil)
                        else aviso(getString(R.string.perfiles_error))
                    }
                    AlertDialog(
                        onDismissRequest = { netflixPendiente = null },
                        title = { Text(stringResource(R.string.netflix_perfil)) },
                        text = {
                            androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState())) {
                                Importar.perfilesNetflix(bytes.decodeToString()).forEach { perfil ->
                                    TextButton({ elegir(perfil) }) { Text(perfil) }
                                }
                            }
                        },
                        confirmButton = { TextButton({ elegir(null) }) { Text(stringResource(R.string.netflix_todos)) } },
                        dismissButton = { TextButton({ netflixPendiente = null }) { Text(stringResource(R.string.cancelar)) } },
                    )
                }
                val deOtraApp = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null && !operacionVigente()) aviso(getString(R.string.perfiles_error))
                    else if (uri != null) {
                        val bytes = leer(uri)
                        if (bytes == null) aviso(getString(R.string.aviso_no_es_de_otra_app))
                        else if (Importar.perfilesNetflix(bytes.decodeToString()).size > 1) netflixPendiente = bytes to (uri.lastPathSegment ?: "")
                        else vm.importarDeOtraApp(bytes, uri.lastPathSegment ?: "")
                    }
                }
                // La foto para una portada: el selector de fotos del sistema (sin permisos; si no hay, el de archivos).
                var alElegir by remember { mutableStateOf<((android.net.Uri) -> Unit)?>(null) }
                val imagen = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                    if (uri != null) alElegir?.invoke(uri)
                    alElegir = null
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
                    exportar = { perfilOperacion = vm.recursos.id; exportar.launch("el-cajon-respaldo-${vm.hoy()}.json") },
                    importar = { perfilOperacion = vm.recursos.id; importar.launch(TIPOS_JSON) },
                    deOtraApp = { perfilOperacion = vm.recursos.id; deOtraApp.launch(TIPOS_OTRAS) },
                    compartir = ::compartir,
                    elegirImagen = { cb -> alElegir = cb; imagen.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                ))
            }
        }
    }

    /** Pasa una imagen (una reseña, una lista) a otra app: WhatsApp, historias… */
    private fun compartir(imagen: Bitmap, titulo: String) {
        runCatching {
            val dir = File(cacheDir, "compartir/${vm.recursos.id}").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val f = File(dir, "el-cajon.png")
            f.outputStream().use { imagen.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(this, "$packageName.compartir", f)
            val i = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_TEXT, titulo).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(i, getString(R.string.compartir)))
        }.onFailure { aviso(getString(R.string.aviso_no_compartido)) }
    }

    /** Lee un archivo elegido por la persona, con un tope de 30 MB. */
    private fun leer(uri: android.net.Uri): ByteArray? = runCatching {
        contentResolver.openInputStream(uri)?.use { ent ->
            val bytes = ent.buffered().readBytesHasta(MAX_ARCHIVO + 1)
            if (bytes.size > MAX_ARCHIVO) null else bytes
        }
    }.getOrNull()

    private fun aviso(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    override fun onStart() {
        super.onStart()
        if (!cambiandoPerfil) vm.sincronizar()
    }

    override fun onStop() {
        super.onStop()
        if (!cambiandoPerfil) lifecycleScope.launch { vm.guardarYa() }
    }

    companion object {
        /** 100 MB: el historial de Spotify de varios años pesa más que un respaldo. */
        private const val MAX_ARCHIVO = 100 * 1024 * 1024
        private val TIPOS_JSON = arrayOf("application/json", "text/plain", "application/octet-stream")
        private val TIPOS_OTRAS = arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/zip", "application/octet-stream", "application/vnd.ms-excel", "application/json")
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

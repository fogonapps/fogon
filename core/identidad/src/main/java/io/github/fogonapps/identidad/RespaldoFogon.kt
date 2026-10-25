package io.github.fogonapps.identidad

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/*
 * El respaldo de todo Fogón: un solo archivo con los datos de todas las apps de Fogón que haya en el teléfono.
 * Cada app expone sus datos con un ProveedorDatosFogon protegido con un permiso de firma: solo otra app firmada
 * con la misma llave (otra app de Fogón) puede leerlos o reemplazarlos. Sirve también para que las apps se hablen
 * (La Cucha lee el peso de El Forzudo).
 *
 * El archivo: {"fogon": 1, "fecha": "2026-10-24", "apps": {"forzudo": {…su respaldo…}, "ahorrador": {…}}}.
 */

/** El permiso de firma que protege los datos de cada app (declarado en el manifiesto de core). */
const val PERMISO_DATOS = "io.github.fogonapps.permiso.DATOS"

/** Las apps de Fogón: la clave en el archivo, el paquete y el nombre que se muestra. */
data class AppFogon(val clave: String, val paquete: String, val nombre: Int)

val APPS_FOGON = listOf(
    AppFogon("forzudo", "io.github.fogonapps.forzudo", R.string.fogon_app_forzudo),
    AppFogon("ahorrador", "io.github.fogonapps.ahorrador", R.string.fogon_app_ahorrador),
    AppFogon("cucha", "io.github.fogonapps.cucha", R.string.fogon_app_cucha),
)

private fun uriDatos(paquete: String): Uri = "content://$paquete.datos/estado".toUri()

/** El tope de lo que se lee (el archivo, o los datos de una app): 20 MB. */
private const val TOPE = 20L * 1024 * 1024

private fun leerConTope(input: java.io.InputStream): String {
    val bytes = input.readNBytesCompat(TOPE + 1)
    require(bytes.size <= TOPE) { "Respaldo demasiado grande" }
    return bytes.decodeToString()
}

private fun java.io.InputStream.readNBytesCompat(max: Long): ByteArray {
    val salida = java.io.ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    var total = 0L
    while (total < max) {
        val n = read(buf, 0, minOf(buf.size.toLong(), max - total).toInt())
        if (n < 0) break
        salida.write(buf, 0, n); total += n
    }
    return salida.toByteArray()
}

/**
 * Lo que cada app pone en su manifiesto (con autoridad `<paquete>.datos`, `exported` y
 * `android:permission="io.github.fogonapps.permiso.DATOS"`). [leer] da el respaldo de la app en JSON;
 * [restaurar] lo reemplaza y devuelve false si el texto no es un respaldo válido.
 */
abstract class ProveedorDatosFogon : ContentProvider() {
    abstract suspend fun leer(): String
    abstract suspend fun restaurar(texto: String): Boolean

    private val resultados = LinkedBlockingQueue<Boolean>()

    override fun onCreate() = true

    // El manifiesto ya exige el permiso para abrir el archivo; call() no lo revisa solo, así que se revisa siempre.
    private fun revisar() = context!!.enforceCallingOrSelfPermission(PERMISO_DATOS, "Fogón")

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        revisar()
        val (lectura, escritura) = ParcelFileDescriptor.createPipe()
        if (mode == "r") {
            thread(name = "fogon-leer") {
                ParcelFileDescriptor.AutoCloseOutputStream(escritura).use { out ->
                    runCatching { out.write(runBlocking { leer() }.encodeToByteArray()) }
                }
            }
            return lectura
        }
        resultados.clear()
        thread(name = "fogon-restaurar") {
            val ok = runCatching {
                val texto = ParcelFileDescriptor.AutoCloseInputStream(lectura).use { leerConTope(it) }
                runBlocking { restaurar(texto) }
            }.getOrDefault(false)
            resultados.offer(ok)
        }
        return escritura
    }

    /** "resultado": espera a que termine la última restauración y dice si salió bien. */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        revisar()
        if (method != "resultado") return null
        return Bundle().apply { putBoolean("ok", resultados.poll(20, TimeUnit.SECONDS) ?: false) }
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String = "application/json"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}

object RespaldoFogon {
    private val json = Json { ignoreUnknownKeys = true }

    /** Las apps de Fogón que hay en este teléfono. */
    fun instaladas(c: Context): List<AppFogon> = APPS_FOGON.filter {
        runCatching { c.packageManager.getPackageInfo(it.paquete, 0) }.isSuccess
    }

    /** Lee los datos de una app de Fogón (otra o esta misma), o null si no está o no deja. */
    suspend fun leerDe(c: Context, app: AppFogon): String? = withContext(Dispatchers.IO) {
        runCatching { c.contentResolver.openInputStream(uriDatos(app.paquete))?.use { leerConTope(it) } }.getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    /** Arma el archivo con los datos de todas las apps instaladas. */
    suspend fun juntar(c: Context): Pair<String, List<AppFogon>> {
        val partes = instaladas(c).mapNotNull { app ->
            leerDe(c, app)?.let { t -> runCatching { app to json.parseToJsonElement(t) }.getOrNull() }
        }
        val texto = buildJsonObject {
            put("fogon", 1)
            put("fecha", LocalDate.now().toString())
            put("apps", JsonObject(partes.associate { (a, j) -> a.clave to j }))
        }.toString()
        return texto to partes.map { it.first }
    }

    /** Las partes del archivo (clave de la app → su respaldo), o null si no es un respaldo de todo Fogón. */
    fun partes(texto: String): Map<String, String>? = runCatching {
        val o = json.parseToJsonElement(texto).jsonObject
        require(o["fogon"]?.jsonPrimitive?.int == 1)
        o["apps"]!!.jsonObject.mapValues { it.value.toString() }
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    /** Restaura cada parte en su app. Devuelve las que quedaron y las que no (no están instaladas o falló). */
    suspend fun repartir(c: Context, partes: Map<String, String>): Pair<List<AppFogon>, List<AppFogon>> = withContext(Dispatchers.IO) {
        val instaladas = instaladas(c).map { it.clave }.toSet()
        val bien = mutableListOf<AppFogon>(); val mal = mutableListOf<AppFogon>()
        APPS_FOGON.filter { it.clave in partes }.forEach { app ->
            val ok = app.clave in instaladas && runCatching {
                c.contentResolver.openOutputStream(uriDatos(app.paquete), "w")!!.use { it.write(partes.getValue(app.clave).encodeToByteArray()) }
                c.contentResolver.call(uriDatos(app.paquete), "resultado", null, null)?.getBoolean("ok") == true
            }.getOrDefault(false)
            (if (ok) bien else mal) += app
        }
        bien to mal
    }
}

/**
 * Perfil → Respaldo: guardar o recuperar los datos de todas las apps de Fogón en un solo archivo.
 * [confirmar] es el diálogo de confirmación de la app (título, texto, acción).
 */
@Composable
fun RespaldoTodoFogon(confirmar: (String, String?, () -> Unit) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalContext.current
    val r = LocalResources.current
    val f = FogonTema.actual
    val alcance = rememberCoroutineScope()
    var aviso by remember { mutableStateOf<String?>(null) }
    fun nombres(l: List<AppFogon>) = l.joinToString(", ") { r.getString(it.nombre) }

    val guardar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        alcance.launch {
            aviso = runCatching {
                val (texto, apps) = RespaldoFogon.juntar(c)
                withContext(Dispatchers.IO) { c.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(texto.encodeToByteArray()) } }
                r.getString(R.string.fogon_todo_guardado, nombres(apps))
            }.getOrElse { r.getString(R.string.fogon_todo_error) }
        }
    }
    val recuperar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        alcance.launch {
            val partes = runCatching {
                withContext(Dispatchers.IO) { c.contentResolver.openInputStream(uri)!!.use { leerConTope(it) } }
            }.getOrNull()?.let { RespaldoFogon.partes(it) }
            if (partes == null) { aviso = r.getString(R.string.fogon_todo_no_es); return@launch }
            val apps = APPS_FOGON.filter { it.clave in partes }
            confirmar(r.getString(R.string.fogon_todo_pregunta), r.getString(R.string.fogon_todo_reemplaza, nombres(apps))) {
                alcance.launch {
                    val (bien, mal) = RespaldoFogon.repartir(c, partes)
                    aviso = r.getString(R.string.fogon_todo_recuperado, nombres(bien).ifEmpty { "—" }) +
                        if (mal.isEmpty()) "" else " " + r.getString(R.string.fogon_todo_faltan, nombres(mal))
                }
            }
        }
    }

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.fogon_todo_titulo), style = MaterialTheme.typography.titleSmall, color = f.texto)
        Text(stringResource(R.string.fogon_todo_pista), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        BotonSecundario(stringResource(R.string.fogon_todo_guardar), { guardar.launch("fogon-respaldo-${LocalDate.now()}.json") }, Modifier.fillMaxWidth())
        BotonSecundario(stringResource(R.string.fogon_todo_recuperar), { recuperar.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, Modifier.fillMaxWidth())
        aviso?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = f.texto) }
    }
}

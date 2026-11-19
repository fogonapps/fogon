package io.github.fogonapps.social

import android.content.Context
import android.util.AtomicFile
import java.io.File
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

enum class PermisoContadores { SI, NO }

@Serializable
data class DiaContadores(val dia: String, val contadores: Map<String, Int>)

@Serializable
data class LoteContadores(val lote: String, val app: String, val version: String, val dias: List<DiaContadores>)

@Serializable
internal data class EstadoContadores(
    val permiso: PermisoContadores = PermisoContadores.SI,
    val dias: Map<String, Map<String, Int>> = emptyMap(),
    val pendiente: LoteContadores? = null,
    val intento: String = "",
)

class Contadores internal constructor(
    val app: String,
    private val version: () -> String,
    leer: () -> String,
    private val escribir: (String) -> Boolean,
    private val hoy: () -> LocalDate = LocalDate::now,
) {
    private var estado = runCatching { formatoEstado.decodeFromString<EstadoContadores>(leer()) }.getOrDefault(EstadoContadores())
    private val permisoActual = MutableStateFlow(estado.permiso)
    val permiso = permisoActual.asStateFlow()
    private var guardado: ScheduledFuture<*>? = null
    private var llamada: Call? = null
    private var vistaPrevia: LoteContadores? = null

    @Synchronized
    fun contar(nombre: String) {
        if (estado.permiso == PermisoContadores.NO || nombre !in nombresContadores) return
        depurar()
        val dia = hoy().toString()
        val cuentas = estado.dias[dia].orEmpty()
        val total = cuentas[nombre] ?: 0
        if (total >= 10_000) return
        estado = estado.copy(dias = estado.dias + (dia to (cuentas + (nombre to total + 1))))
        guardado?.cancel(false)
        guardado = disco.schedule({ synchronized(this) { guardar() } }, 300, TimeUnit.MILLISECONDS)
    }

    @Synchronized
    internal fun limpiar() {
        val anterior = estado
        depurar()
        if (estado != anterior) guardar()
    }

    @Synchronized
    fun permitir(si: Boolean) {
        val eleccion = if (si) PermisoContadores.SI else PermisoContadores.NO
        if (!si) {
            llamada?.cancel()
            vistaPrevia = null
            estado = estado.copy(dias = emptyMap(), pendiente = null)
        }
        estado = estado.copy(permiso = eleccion)
        guardar()
        permisoActual.value = eleccion
    }

    @Synchronized
    fun verSumas(): LoteContadores {
        depurar()
        estado.pendiente?.takeIf { it.dias.all { dia -> dia.dia < hoy().toString() } }?.let { return it }
        val versionActual = version()
        val dias = diasTerminados()
        return vistaPrevia?.takeIf { it.dias == dias && it.version == versionActual }
            ?: LoteContadores(UUID.randomUUID().toString(), app, versionActual, dias).also { vistaPrevia = it }
    }

    @Synchronized
    fun preparar(): LoteContadores? {
        if (estado.permiso != PermisoContadores.SI) return null
        depurar()
        val hoy = hoy().toString()
        if (estado.intento == hoy) return null
        val pendiente = estado.pendiente
        if (pendiente != null && !vigente(pendiente.lote)) return null
        val lote = verSumas().takeIf { it.dias.isNotEmpty() } ?: return null
        estado = estado.copy(pendiente = lote, intento = hoy)
        return lote.takeIf { guardar() }
    }

    @Synchronized
    fun confirmar(lote: String) {
        val pendiente = estado.pendiente?.takeIf { it.lote == lote } ?: return
        val dias = estado.dias.toMutableMap()
        // Aceptado o rechazado por inválido (400), esas sumas salen: reintentarlas no cambiaría la respuesta.
        for (dia in pendiente.dias) {
            val restantes = dias[dia.dia].orEmpty().mapValues { (nombre, total) ->
                total - (dia.contadores[nombre] ?: 0)
            }.filterValues { it > 0 }
            if (restantes.isEmpty()) dias.remove(dia.dia) else dias[dia.dia] = restantes
        }
        vistaPrevia = null
        estado = estado.copy(pendiente = null, dias = dias)
        guardar()
    }

    @Synchronized
    private fun vigente(lote: String): Boolean =
        estado.permiso == PermisoContadores.SI && estado.pendiente?.lote == lote &&
            estado.pendiente?.dias?.all { it.dia < hoy().toString() } == true

    @Synchronized
    internal fun llamadaPropia(lote: LoteContadores, servidor: String): Call? {
        if (!vigente(lote.lote)) return null
        return llamadaContadores(lote, servidor).also { llamada = it }
    }

    private fun diasTerminados(): List<DiaContadores> {
        val hoy = hoy().toString()
        return estado.dias.filterKeys { it < hoy }.toSortedMap().map { (dia, cuentas) -> DiaContadores(dia, cuentas) }
    }

    private fun depurar() {
        val limite = hoy().minusDays(30).toString()
        val pendiente = estado.pendiente?.let { lote ->
            val vigentes = lote.dias.filter { it.dia >= limite }
            if (vigentes.isEmpty()) null else lote.copy(dias = vigentes)
        }
        estado = estado.copy(dias = estado.dias.filterKeys { it >= limite }, pendiente = pendiente)
    }

    private fun guardar(): Boolean = escribir(Json.encodeToString(estado))

    companion object {
        // Los archivos anteriores pueden incluir arranques y SIN_PREGUNTAR.
        private val formatoEstado = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
        private val disco = Executors.newSingleThreadScheduledExecutor()
        private var instancia: Contadores? = null
        @Synchronized
        fun de(contexto: Context): Contadores = instancia ?: crear(contexto.applicationContext).also { instancia = it }

        private fun crear(contexto: Context): Contadores {
            val paquete = contexto.packageName
            val app = if (paquete == "io.github.fogonapps.ahorrador") "alcancia" else paquete.substringAfterLast('.')
            val archivo = AtomicFile(File(contexto.noBackupFilesDir, "contadores.json"))
            return Contadores(
                app,
                version = { contexto.packageManager.getPackageInfo(paquete, 0).versionName.orEmpty() },
                leer = { archivo.readFully().decodeToString() },
                escribir = { datos ->
                    runCatching {
                        val salida = archivo.startWrite()
                        try {
                            salida.write(datos.encodeToByteArray())
                            archivo.finishWrite(salida)
                        } catch (e: java.io.IOException) {
                            archivo.failWrite(salida)
                            throw e
                        }
                    }.isSuccess
                },
            )
        }

        fun arrancar(contexto: Context) {
            de(contexto).contar("app.arranque")
            val trabajo = PeriodicWorkRequestBuilder<EnviarContadores>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(contexto).enqueueUniquePeriodicWork(
                "contadores", ExistingPeriodicWorkPolicy.UPDATE, trabajo,
            )
        }
    }
}

internal fun llamadaContadores(lote: LoteContadores, servidor: String = ClienteSocial.SERVIDOR): Call = ClienteSocial.httpContadores.newCall(
    Request.Builder().url(servidor + "/v1/contadores")
        .header("User-Agent", "Fogon")
        .post(Json.encodeToString(lote).toRequestBody("application/json".toMediaType()))
        .build()
)

class EnviarContadores internal constructor(
    contexto: Context,
    parametros: WorkerParameters,
    private val servidor: String,
) : CoroutineWorker(contexto, parametros) {
    constructor(contexto: Context, parametros: WorkerParameters) : this(contexto, parametros, ClienteSocial.SERVIDOR)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val contadores = Contadores.de(applicationContext)
        contadores.limpiar()
        contadores.preparar()?.let { lote ->
            contadores.llamadaPropia(lote, servidor)?.let { enviar(it) }?.let { contadores.confirmar(lote.lote) }
        }
        Result.success()
    }

    private fun enviar(llamada: Call): Boolean? = runCatching {
        llamada.execute().use { respuesta ->
            when {
                respuesta.isSuccessful -> true
                respuesta.code == 400 -> false
                else -> null
            }
        }
    }.getOrNull()
}

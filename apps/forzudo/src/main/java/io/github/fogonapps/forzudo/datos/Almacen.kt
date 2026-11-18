package io.github.fogonapps.forzudo.datos

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Guarda el estado completo en un archivo JSON con escrituras atómicas (DataStore). */
object SerializadorEstado : Serializer<Estado> {
    override val defaultValue = Estado()
    override suspend fun readFrom(input: InputStream): Estado = try {
        JsonGym.decodeFromString(Estado.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Estado ilegible", e)
    }

    override suspend fun writeTo(t: Estado, output: OutputStream) {
        output.write(JsonGym.encodeToString(Estado.serializer(), t).encodeToByteArray())
    }
}

fun crearAlmacen(context: Context, directorio: File = context.filesDir, scope: kotlinx.coroutines.CoroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())): DataStore<Estado> = DataStoreFactory.create(
    serializer = SerializadorEstado,
    scope = scope,
    // Si el archivo se daña, se conserva una copia antes de empezar de cero.
    corruptionHandler = ReplaceFileCorruptionHandler { e ->
        val f = File(directorio, "datastore/estado.json")
        if (f.exists()) {
            val copia = f.copyTo(File(directorio, "estado-dañado-${System.currentTimeMillis()}.json"))
            check(f.readBytes().contentEquals(copia.readBytes()))
        }
        Estado()
    },
    produceFile = { File(directorio, "datastore/estado.json") },
)

internal fun limpiarClavesRetiradas(contexto: Context, perfiles: List<String> = emptyList()) {
    val retiradas = listOf("token", "iv", "gist", "usuario", "ultima")
    for (nombre in listOf("sincronizacion") + perfiles.map { "sincronizacion-p$it" }) {
        val prefs = contexto.getSharedPreferences(nombre, Context.MODE_PRIVATE)
        if (retiradas.none { prefs.contains(it) }) continue
        val editor = prefs.edit()
        retiradas.forEach { editor.remove(it) }
        editor.apply()
    }
}

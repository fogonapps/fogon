package io.github.fogonapps.cucha.datos

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
        JsonCucha.decodeFromString(Estado.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Estado ilegible", e)
    }

    override suspend fun writeTo(t: Estado, output: OutputStream) {
        output.write(JsonCucha.encodeToString(Estado.serializer(), t).encodeToByteArray())
    }
}

fun crearAlmacen(context: Context): DataStore<Estado> = DataStoreFactory.create(
    serializer = SerializadorEstado,
    // Si el archivo se daña, se conserva una copia antes de empezar de cero.
    corruptionHandler = ReplaceFileCorruptionHandler { e ->
        val f = File(context.filesDir, "datastore/estado.json")
        runCatching { f.copyTo(File(context.filesDir, "estado-dañado-${System.currentTimeMillis()}.json")) }
        Estado()
    },
    produceFile = { File(context.filesDir, "datastore/estado.json") },
)

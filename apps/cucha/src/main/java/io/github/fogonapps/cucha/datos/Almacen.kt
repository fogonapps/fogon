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

/** El cuaderno de un perfil del teléfono, en `perfiles/<id>/datastore/estado.json`. */
fun crearAlmacen(directorio: File, alcance: kotlinx.coroutines.CoroutineScope): DataStore<Estado> = DataStoreFactory.create(
    serializer = SerializadorEstado,
    // Si el archivo se daña, se conserva una copia verificada antes de empezar de cero.
    corruptionHandler = ReplaceFileCorruptionHandler { e ->
        val f = File(directorio, "datastore/estado.json")
        if (f.exists()) {
            val copia = f.copyTo(File(directorio, "estado-dañado-${System.currentTimeMillis()}.json"))
            check(f.readBytes().contentEquals(copia.readBytes()))
        }
        Estado()
    },
    scope = alcance,
    produceFile = { File(directorio, "datastore/estado.json") },
)

/** Pasa el cuaderno de antes (todo en `files/`) al perfil 1, con respaldo verificado y por fases. */
fun migracionPerfiles(context: Context): io.github.fogonapps.social.MigracionPerfilesTelefono =
    io.github.fogonapps.social.MigracionPerfilesTelefono(context.filesDir,
        // La Cucha no guarda claves: la sesión de la cuenta nace ya en su perfil.
        copiarClaves = { io.github.fogonapps.social.copiarPreferenciasClaves(emptyMap<String, Any>()) }, migrarClaves = {},
        validar = { JsonCucha.decodeFromString(Estado.serializer(), it) })

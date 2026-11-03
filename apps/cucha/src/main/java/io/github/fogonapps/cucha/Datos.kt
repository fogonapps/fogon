package io.github.fogonapps.cucha

import io.github.fogonapps.cucha.datos.Estado
import io.github.fogonapps.cucha.datos.JsonCucha
import io.github.fogonapps.identidad.ProveedorDatosFogon
import kotlinx.coroutines.flow.first

/** Lee un respaldo de La Cucha. Devuelve el estado si es válido (ya pasó por el tutorial). */
fun leerRespaldoCucha(texto: String): Estado? =
    runCatching { JsonCucha.decodeFromString(Estado.serializer(), texto) }.getOrNull()?.takeIf { it.listo }

/**
 * Los datos de La Cucha para las otras apps de Fogón (respaldo de todo Fogón).
 * Solo los ve una app firmada con la misma llave (permiso de firma, ver RespaldoFogon.kt en core).
 * Si la app está abierta, el ViewModel toma lo restaurado al verlo cambiar en el disco.
 */
class DatosCucha : ProveedorDatosFogon() {
    private val app get() = context!!.applicationContext as CuchaApp

    override suspend fun leer(): String = JsonCucha.encodeToString(Estado.serializer(), app.almacen.data.first())

    override suspend fun restaurar(texto: String): Boolean {
        val e = leerRespaldoCucha(texto) ?: return false
        app.almacen.updateData { app.logica.normalizar(e).copy(updatedAt = System.currentTimeMillis()) }
        WidgetCalorias.actualizar(app)
        return true
    }
}

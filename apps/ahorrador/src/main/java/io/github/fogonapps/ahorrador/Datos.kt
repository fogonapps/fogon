package io.github.fogonapps.ahorrador

import io.github.fogonapps.ahorrador.datos.Estado
import io.github.fogonapps.ahorrador.datos.JsonAhorrador
import io.github.fogonapps.identidad.ProveedorDatosFogon
import kotlinx.coroutines.flow.first

/** Lee un respaldo de El Ahorrador. Devuelve el estado si es válido (tiene al menos una cuenta). */
fun leerRespaldoAhorrador(texto: String): Estado? =
    runCatching { JsonAhorrador.decodeFromString(Estado.serializer(), texto) }.getOrNull()?.takeIf { it.cuentas.isNotEmpty() }

/**
 * Los datos de El Ahorrador para las otras apps de Fogón (respaldo de todo Fogón).
 * Solo los ve una app firmada con la misma llave (permiso de firma, ver RespaldoFogon.kt en core).
 * Si la app está abierta, el ViewModel toma lo restaurado al verlo cambiar en el disco.
 */
class DatosAhorrador : ProveedorDatosFogon() {
    private val app get() = context!!.applicationContext as AhorradorApp

    override suspend fun leer(): String = JsonAhorrador.encodeToString(Estado.serializer(), app.almacen.data.first())

    override suspend fun restaurar(texto: String): Boolean {
        val e = leerRespaldoAhorrador(texto) ?: return false
        app.almacen.updateData { app.logica.normalizar(e).copy(updatedAt = System.currentTimeMillis()) }
        WidgetPlata.actualizar(app)
        return true
    }
}

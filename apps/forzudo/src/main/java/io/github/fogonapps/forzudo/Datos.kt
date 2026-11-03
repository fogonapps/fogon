package io.github.fogonapps.forzudo

import io.github.fogonapps.forzudo.datos.Estado
import io.github.fogonapps.forzudo.datos.JsonGym
import io.github.fogonapps.identidad.ProveedorDatosFogon
import kotlinx.coroutines.flow.first

/** Lee un respaldo de El Forzudo. Devuelve el estado si es válido (tiene entrenos, rutina o medidas). */
fun leerRespaldoGym(texto: String): Estado? = runCatching {
    JsonGym.decodeFromString(Estado.serializer(), texto)
}.getOrNull()?.takeIf { it.sessions.isNotEmpty() || it.routines != null || it.body.isNotEmpty() }

/**
 * Los datos de El Forzudo para las otras apps de Fogón (respaldo de todo Fogón, La Cucha).
 * Solo los ve una app firmada con la misma llave (permiso de firma, ver RespaldoFogon.kt en core).
 * Si la app está abierta, el ViewModel toma lo restaurado al verlo cambiar en el disco.
 */
class DatosForzudo : ProveedorDatosFogon() {
    private val app get() = context!!.applicationContext as ForzudoApp

    override suspend fun leer(): String =
        JsonGym.encodeToString(Estado.serializer(), app.logica.paraNube(app.logica.normalizar(app.almacen.data.first())))

    override suspend fun restaurar(texto: String): Boolean {
        val d = leerRespaldoGym(texto) ?: return false
        app.almacen.updateData { viejo ->
            app.logica.normalizar(d).copy(active = viejo.active, nombre = d.nombre ?: viejo.nombre, updatedAt = System.currentTimeMillis())
        }
        WidgetRacha.actualizar(app)
        return true
    }
}

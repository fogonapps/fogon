package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.JsonCajon
import io.github.fogonapps.cajon.datos.Logica
import io.github.fogonapps.identidad.ProveedorDatosFogon
import kotlinx.coroutines.flow.first

/** Lee un respaldo de El Cajón. Devuelve el estado si es válido (ya pasó por el tutorial). */
fun leerRespaldoCajon(texto: String): Estado? =
    runCatching { JsonCajon.decodeFromString(Estado.serializer(), texto) }.getOrNull()?.takeIf { it.listo }

/**
 * Los datos de El Cajón para las otras apps de Fogón (respaldo de todo Fogón).
 * Solo los ve una app firmada con la misma llave (permiso de firma, ver RespaldoFogon.kt en core).
 * Las claves (GitHub, TMDB) no van: quedan cifradas en este teléfono.
 */
class DatosCajon : ProveedorDatosFogon() {
    private val app get() = context!!.applicationContext as CajonApp

    override suspend fun leer(): String = JsonCajon.encodeToString(Estado.serializer(), app.almacen.data.first())

    override suspend fun restaurar(texto: String): Boolean {
        val e = leerRespaldoCajon(texto) ?: return false
        app.almacen.updateData { Logica.normalizar(e) }
        WidgetCurso.actualizar(app)
        return true
    }
}

package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.JsonCajon
import io.github.fogonapps.cajon.datos.Resenas
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

    override suspend fun leer(): String {
        val perfil = app.recursos()
        return JsonCajon.encodeToString(Estado.serializer(), Resenas.paraGuardar(perfil.almacen.data.first().copy(escuchas = perfil.escuchas.oidos.value)))
    }

    override suspend fun restaurar(texto: String): Boolean {
        val e = leerRespaldoCajon(texto) ?: return false
        val perfil = app.recursos()
        perfil.escuchas.unir(e.escuchas)
        perfil.almacen.updateData { Logica.normalizar(e.copy(escuchas = null)) }
        WidgetCurso.actualizar(app)
        return true
    }
}

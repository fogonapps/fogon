package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.JsonCajon

/** Lee un respaldo de Fabs. Devuelve el estado si es válido (ya pasó por el tutorial). */
fun leerRespaldoCajon(texto: String): Estado? =
    runCatching { JsonCajon.decodeFromString(Estado.serializer(), texto) }.getOrNull()?.takeIf { it.listo }

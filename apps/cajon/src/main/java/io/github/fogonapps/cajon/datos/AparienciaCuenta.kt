package io.github.fogonapps.cajon.datos

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.fogonapps.identidad.Apariencia
import io.github.fogonapps.identidad.AjustesEstilo
import io.github.fogonapps.identidad.Decoracion
import io.github.fogonapps.identidad.Estilo
import io.github.fogonapps.identidad.Ranura
import kotlinx.serialization.Serializable

/** Formato aditivo del cajón; no incluye el idioma del teléfono. */
@Serializable
data class AparienciaCuenta(
    val estilo: String = "GRAFITO",
    val ajustes: Map<String, AjusteApariencia> = emptyMap(),
    val tamanoTexto: Int = 1,
    val menosMovimiento: Boolean = false,
    val masContraste: Boolean = false,
    val cambio: Long = 0,
)

@Serializable
data class AjusteApariencia(val colores: Map<String, Int> = emptyMap(), val transparencia: Int? = null, val adorno: String? = null)

fun Apariencia.paraCuenta(cambio: Long) = AparienciaCuenta(estilo.name,
    ajustes.mapKeys { it.key.name }.mapValues { (_, a) -> AjusteApariencia(a.colores.mapKeys { it.key.name }.mapValues { it.value.toArgb() }, a.transparencia, a.adorno?.name) },
    tamanoTexto, menosMovimiento, masContraste, cambio)

fun AparienciaCuenta.paraTelefono() = Apariencia(
    estilo = Estilo.entries.firstOrNull { it.name == estilo } ?: Estilo.GRAFITO,
    ajustes = ajustes.mapNotNull { (nombre, a) -> Estilo.entries.firstOrNull { it.name == nombre }?.let { estilo ->
        estilo to AjustesEstilo(a.colores.mapNotNull { (nombre, color) -> Ranura.entries.firstOrNull { it.name == nombre }?.let { it to Color(color) } }.toMap(),
            a.transparencia?.coerceIn(0, 100), Decoracion.entries.firstOrNull { it.name == a.adorno })
    } }.toMap(),
    tamanoTexto = tamanoTexto.coerceIn(0, 3), menosMovimiento = menosMovimiento, masContraste = masContraste,
)

fun unirApariencia(local: AparienciaCuenta?, remota: AparienciaCuenta?): AparienciaCuenta? =
    if (remota != null && (local == null || remota.cambio > local.cambio)) remota else local

package io.github.fogonapps.cajon.datos

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.fogonapps.identidad.Apariencia
import io.github.fogonapps.identidad.AjustesEstilo
import io.github.fogonapps.identidad.Decoracion
import io.github.fogonapps.identidad.Estilo
import io.github.fogonapps.identidad.Ranura
import kotlinx.serialization.Serializable
import io.github.fogonapps.identidad.*

/** Formato aditivo del cajón; no incluye el idioma del teléfono. */
@Serializable
data class AparienciaCuenta(
    val estilo: String = "GRAFITO",
    val ajustes: Map<String, AjusteApariencia> = emptyMap(),
    val tamanoTexto: Int = 1,
    val menosMovimiento: Boolean = false,
    val masContraste: Boolean = false,
    val cambio: Long = 0,
    val guardadas: List<TintaCuenta> = emptyList(),
)

@Serializable
data class AjusteApariencia(val colores: Map<String, Int> = emptyMap(), val transparencia: Int? = null, val adorno: String? = null, val luz: LuzCuenta? = null)

@Serializable
data class LuzCuenta(val color: Int, val segundo: Int? = null, val intensidad: Int = 100, val tamano: Int = 100)
@Serializable
data class TintaCuenta(val nombre: String, val ajuste: AjusteApariencia)

private fun AjustesEstilo.paraCuenta() = AjusteApariencia(
    colores.mapKeys { it.key.name }.mapValues { it.value.toArgb() }, transparencia, adorno?.name,
    luz?.let { LuzCuenta(it.color.toArgb(), it.segundo?.toArgb(), it.intensidad, it.tamano) })
private fun AjusteApariencia.paraTelefono() = AjustesEstilo(
    colores.mapNotNull { (nombre, color) -> Ranura.entries.firstOrNull { it.name == nombre }?.let { it to Color(color) } }.toMap(),
    transparencia?.coerceIn(0, 100), Decoracion.entries.firstOrNull { it.name == adorno },
    luz?.let { LuzPropia(Color(it.color), it.segundo?.let { c -> Color(c) }, it.intensidad.coerceIn(0, 100), it.tamano.coerceIn(50, 150)) })

fun Apariencia.paraCuenta(cambio: Long) = AparienciaCuenta(estilo.name,
    ajustes.mapKeys { it.key.name }.mapValues { (_, a) -> a.paraCuenta() },
    tamanoTexto, menosMovimiento, masContraste, cambio, guardadas.take(12).map { TintaCuenta(it.nombre, it.ajuste.paraCuenta()) })

fun AparienciaCuenta.paraTelefono() = Apariencia(
    guardadas = guardadas.take(12).map { TintasGuardadas(it.nombre, it.ajuste.paraTelefono()) },
    estilo = Estilo.entries.firstOrNull { it.name == estilo } ?: Estilo.GRAFITO,
    ajustes = ajustes.mapNotNull { (nombre, a) -> Estilo.entries.firstOrNull { it.name == nombre }?.let { it to a.paraTelefono() } }.toMap(),
    tamanoTexto = tamanoTexto.coerceIn(0, 3), menosMovimiento = menosMovimiento, masContraste = masContraste,
)

fun unirApariencia(local: AparienciaCuenta?, remota: AparienciaCuenta?): AparienciaCuenta? =
    if (remota != null && (local == null || remota.cambio > local.cambio)) remota else local

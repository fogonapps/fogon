package io.github.fogonapps.identidad

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.text.NumberFormat
import java.util.Locale

/** Formatea el valor decimal recibido, sin redondear sus cifras ni modificar el dato original. */
fun formatearMiles(valor: String, idioma: Locale): String {
    val numero = valor.toBigDecimalOrNull()?.stripTrailingZeros() ?: return valor
    val region = if (idioma.language == "en") Locale.US else Locale.forLanguageTag("es-CO")
    return NumberFormat.getNumberInstance(region).apply {
        maximumFractionDigits = numero.scale().coerceAtLeast(0)
    }.format(numero)
}

@Composable
fun numeroLocalizado(valor: String): String = formatearMiles(valor, LocalConfiguration.current.locales[0])

private val despuesDeSeparador = Regex("(?<=[.,])")

/** Conserva cada separador con el grupo anterior y permite envolver las fracciones largas. */
fun gruposNumeroFormateado(numero: String): List<String> {
    val grupos = numero.split(despuesDeSeparador)
    val ultimo = grupos.last()
    return if (grupos.size == 1) listOf(numero)
    else grupos.dropLast(1) + ultimo.chunked(3)
}

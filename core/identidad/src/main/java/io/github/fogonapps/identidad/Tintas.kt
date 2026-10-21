package io.github.fogonapps.identidad

import androidx.compose.ui.graphics.Color

/** Tintas riso de Fogón. Ver docs/IDENTIDAD.md. */
object Tintas {
    val Tinta = Color(0xFF141210)
    val Papel = Color(0xFFEFE7DA)
    val PapelHondo = Color(0xFFE4D9C8)
    val TintaSuave = Color(0xFF241F1B)
    val TintaHonda = Color(0xFF2E2823)
    val Gris = Color(0xFF6B5F55)
    val GrisClaro = Color(0xFFB5AA9C)

    val Rojo = Color(0xFFE5332A)
    val Amarillo = Color(0xFFFFC93C)
    val Rosa = Color(0xFFFF4FA3)
    val Azul = Color(0xFF2D5BFF)
    val Verde = Color(0xFF1FB45A)
    val Violeta = Color(0xFF7B4DFF)
    val Naranja = Color(0xFFFF7A1A)

    /** La chispa: firma de Fogón en logros y récords. */
    val Chispa = Rojo
}

/**
 * Colores de una app. Cada app tiene su tinta principal y una segunda tinta (la sombra riso).
 * Los textos usan variantes que cumplen contraste 4,5:1 sobre papel (claro) y sobre tinta (oscuro).
 */
data class Paleta(
    val tinta: Color,
    val sobreTinta: Color,
    val textoEnPapel: Color,
    val textoEnTinta: Color,
    val segunda: Color,
) {
    companion object {
        val Forzudo = Paleta(
            tinta = Tintas.Azul,
            sobreTinta = Color.White,
            textoEnPapel = Color(0xFF2051FF),
            textoEnTinta = Color(0xFF6E8BFF),
            segunda = Tintas.Rosa,
        )
    }
}

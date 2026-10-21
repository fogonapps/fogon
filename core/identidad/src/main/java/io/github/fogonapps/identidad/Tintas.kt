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
    val Lima = Color(0xFFC8F560)
    val Cian = Color(0xFF00A0E9)

    /** La chispa: firma de Fogón en logros y récords. */
    val Chispa = Rojo
}

/**
 * Colores de una app, en el estilo "de noche" (ver docs/IDENTIDAD.md → Estilo de noche).
 *
 * - [tinta]: el acento. Botones principales, pestaña activa, barras de progreso. Va con texto [sobreTinta].
 * - [textoEnTinta]: el acento cuando es texto sobre el fondo oscuro.
 * - [segunda] y [luz2]: las dos luces difuminadas del fondo y el brillo de las tarjetas destacadas.
 * - [fondo]: el negro de la app, con un matiz de su color.
 * - [textoEnPapel]: el acento sobre papel claro (láminas, tarjetas de papel).
 */
data class Paleta(
    val tinta: Color,
    val sobreTinta: Color,
    val textoEnPapel: Color,
    val textoEnTinta: Color,
    val segunda: Color,
    val luz2: Color,
    val fondo: Color,
) {
    companion object {
        /** Violeta y azul con acento lima. */
        val Forzudo = Paleta(
            tinta = Tintas.Lima,
            sobreTinta = Color(0xFF0C0A12),
            textoEnPapel = Color(0xFF6C39FF),
            textoEnTinta = Tintas.Lima,
            segunda = Tintas.Violeta,
            luz2 = Tintas.Azul,
            fondo = Color(0xFF0C0A12),
        )

        /** Verde y amarillo con acento amarillo (la moneda). */
        val Ahorrador = Paleta(
            tinta = Tintas.Amarillo,
            sobreTinta = Color(0xFF0D0C0A),
            textoEnPapel = Color(0xFF14753A),
            textoEnTinta = Tintas.Amarillo,
            segunda = Tintas.Verde,
            luz2 = Tintas.Amarillo,
            fondo = Color(0xFF0D0C0A),
        )
    }
}

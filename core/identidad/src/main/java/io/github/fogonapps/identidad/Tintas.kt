package io.github.fogonapps.identidad

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color

/** Tintas riso de Fogón. */
object Tintas {
    val Tinta = Color(0xFF141210)
    val Papel = Color(0xFFEFE7DA)
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
 * Tintas de una app. Con ellas se arma el tema propio de la app
 * ([nombreTema]: Gimnasio, Vino…); en Grafito, Claro y Contraste la app solo pone su [tinta] como acento.
 *
 * - [tinta]: el acento. Botones principales, pestaña activa, el número grande. Va con texto [sobreTinta].
 * - [segunda]: la segunda tinta: barras de metas, el halo de puntos.
 * - [fondo]: el papel oscuro del tema propio, con un matiz de su color.
 * - [brillo]: la luz suave que va arriba en el tema propio.
 * - [textoEnPapel]: el acento sobre papel claro (tema Claro, láminas).
 * - [textoEnTinta], [luz2], [segundaTinta]: se quedan por compatibilidad; las pantallas leen de FogonTema.
 */
data class Paleta(
    val tinta: Color,
    val sobreTinta: Color,
    val textoEnPapel: Color,
    val textoEnTinta: Color,
    val segunda: Color,
    val luz2: Color,
    val fondo: Color,
    val segundaTinta: Color = segunda,
    val brillo: Color = segunda,
    @StringRes val nombreTema: Int = R.string.fogon_estilo_marca,
) {
    companion object {
        /** Gimnasio: azul de hierro sobre casi negro azulado (El Forzudo). */
        val Forzudo = Paleta(
            tinta = Color(0xFF2D5BFF),
            sobreTinta = Color(0xFFFFFFFF),
            textoEnPapel = Color(0xFF1F45D6),
            textoEnTinta = Color(0xFF7A95FF),
            segunda = Color(0xFF00A0E9),
            luz2 = Color(0xFF00A0E9),
            fondo = Color(0xFF090B12),
            brillo = Color(0xFF2443B8),
            nombreTema = R.string.fogon_tema_gimnasio,
        )

        /** Brasa: coral y amarillo sobre casi negro rojizo (La Minga). */
        val Minga = Paleta(
            tinta = Color(0xFFFF5A4F),
            sobreTinta = Color(0xFF120A08),
            textoEnPapel = Color(0xFFC92118),
            textoEnTinta = Color(0xFFFF5A4F),
            segunda = Tintas.Amarillo,
            luz2 = Tintas.Rojo,
            fondo = Color(0xFF120A08),
            brillo = Color(0xFFB0301F),
            nombreTema = R.string.fogon_tema_brasa,
        )

        /** Billete: verde con ámbar sobre casi negro verdoso (El Ahorrador). */
        val Ahorrador = Paleta(
            tinta = Color(0xFF1FB45A),
            sobreTinta = Color(0xFF06140B),
            textoEnPapel = Color(0xFF13793C),
            textoEnTinta = Color(0xFF45D47F),
            segunda = Color(0xFFF5B547),
            luz2 = Color(0xFFF5B547),
            fondo = Color(0xFF080D0A),
            brillo = Color(0xFF137A3C),
            nombreTema = R.string.fogon_tema_vino,
        )

        /** Cocina: naranja de fogón con verde de cilantro sobre casi negro tostado (La Cucha). */
        val Cucha = Paleta(
            tinta = Color(0xFFFF7A1A),
            sobreTinta = Color(0xFF140A05),
            textoEnPapel = Color(0xFFB84A12),
            textoEnTinta = Color(0xFFFF8A4C),
            segunda = Color(0xFF3FBF6A),
            luz2 = Color(0xFF3FBF6A),
            fondo = Color(0xFF100B08),
            brillo = Color(0xFFB8501E),
            nombreTema = R.string.fogon_tema_cocina,
        )

        /** Vinilo: violeta de disco con amarillo de cajón sobre casi negro violeta (El Cajón). */
        val Cajon = Paleta(
            tinta = Color(0xFF8A63FF),
            sobreTinta = Color(0xFFFFFFFF),
            textoEnPapel = Color(0xFF6C39FF),
            textoEnTinta = Color(0xFFA68BFF),
            segunda = Color(0xFFFFC93C),
            luz2 = Color(0xFFFFC93C),
            fondo = Color(0xFF0E0B16),
            brillo = Color(0xFF5B3BCC),
            nombreTema = R.string.fogon_tema_vinilo,
        )
    }
}

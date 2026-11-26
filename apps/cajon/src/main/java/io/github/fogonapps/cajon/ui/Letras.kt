package io.github.fogonapps.cajon.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.fogonapps.cajon.R

/**
 * Las letras de Fabs, dirección «Cine en casa». Títulos en Fraunces (OFL) con los
 * ejes suave y caprichoso al máximo y el tamaño óptico de cartel: serifa cálida, de cine y de libro. El texto en
 * Figtree (OFL), geométrica, clara y amable. Las dos van cortadas en pesos fijos.
 */
val Fraunces = FontFamily(
    Font(R.font.fraunces_600, FontWeight.SemiBold),
    Font(R.font.fraunces_700, FontWeight.Bold),
    Font(R.font.fraunces_900, FontWeight.Black),
)

val Figtree = FontFamily(
    Font(R.font.figtree_400, FontWeight.Normal),
    Font(R.font.figtree_500, FontWeight.Medium),
    Font(R.font.figtree_600, FontWeight.SemiBold),
    Font(R.font.figtree_700, FontWeight.Bold),
    Font(R.font.figtree_800, FontWeight.ExtraBold),
)

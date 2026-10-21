package io.github.fogonapps.identidad

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

val Bagel = FontFamily(Font(R.font.bagel_fat_one))
val Anton = FontFamily(Font(R.font.anton))

/** Lo que cada pantalla necesita saber de la identidad, más allá de Material. */
@Immutable
data class Fogon(
    val paleta: Paleta,
    val oscuro: Boolean,
    val fondo: Color,
    val superficie: Color,
    val texto: Color,
    val textoSuave: Color,
    val linea: Color,
    val acentoTexto: Color,
)

val LocalFogon = staticCompositionLocalOf<Fogon> { error("Falta FogonTema") }

object FogonTema {
    val actual: Fogon
        @Composable @ReadOnlyComposable get() = LocalFogon.current
}

/**
 * El tema de las apps de Fogón. El estilo es de noche (docs/IDENTIDAD.md → Estilo de noche), así que
 * por defecto siempre es oscuro; el modo claro queda para pantallas de papel o una versión de día.
 */
@Composable
fun FogonTema(
    paleta: Paleta,
    oscuro: Boolean = true,
    contenido: @Composable () -> Unit,
) {
    val f = if (oscuro) {
        Fogon(
            paleta = paleta, oscuro = true,
            fondo = paleta.fondo, superficie = Color.White.copy(alpha = 0.08f),
            texto = Color.White, textoSuave = Color.White.copy(alpha = 0.68f),
            linea = Color.White.copy(alpha = 0.16f), acentoTexto = paleta.textoEnTinta,
        )
    } else {
        Fogon(
            paleta = paleta, oscuro = false,
            fondo = Tintas.Papel, superficie = Tintas.PapelHondo,
            texto = Tintas.Tinta, textoSuave = Tintas.Gris,
            linea = Color(0xFFD6CAB8), acentoTexto = paleta.textoEnPapel,
        )
    }
    val esquema = if (oscuro) {
        darkColorScheme(
            primary = paleta.tinta, onPrimary = paleta.sobreTinta,
            secondary = paleta.segunda, onSecondary = Tintas.Tinta,
            background = f.fondo, onBackground = f.texto,
            surface = f.fondo, onSurface = f.texto,
            surfaceVariant = f.superficie, onSurfaceVariant = f.textoSuave,
            // Diálogos y menús: opacos, el vidrio se vería mal sobre otra pantalla.
            surfaceContainer = lerp(paleta.fondo, Color.White, 0.08f),
            surfaceContainerHigh = lerp(paleta.fondo, Color.White, 0.11f),
            surfaceContainerHighest = lerp(paleta.fondo, Color.White, 0.14f),
            outline = Color.White.copy(alpha = 0.32f), outlineVariant = f.linea,
            error = Color(0xFFFF8A80), onError = Tintas.Tinta,
        )
    } else {
        lightColorScheme(
            primary = paleta.tinta, onPrimary = paleta.sobreTinta,
            secondary = paleta.segunda, onSecondary = Tintas.Tinta,
            background = f.fondo, onBackground = f.texto,
            surface = f.fondo, onSurface = f.texto,
            surfaceVariant = f.superficie, onSurfaceVariant = f.textoSuave,
            surfaceContainer = f.superficie, surfaceContainerHigh = Color(0xFFDCD0BE),
            outline = f.textoSuave, outlineVariant = f.linea,
            error = Color(0xFFB3261E), onError = Color.White,
        )
    }
    val base = Typography()
    val tipografia = Typography(
        displayLarge = TextStyle(fontFamily = Anton, fontSize = 56.sp, lineHeight = 56.sp),
        displayMedium = TextStyle(fontFamily = Anton, fontSize = 40.sp, lineHeight = 42.sp),
        displaySmall = TextStyle(fontFamily = Anton, fontSize = 30.sp, lineHeight = 32.sp),
        headlineLarge = TextStyle(fontFamily = Bagel, fontSize = 32.sp, lineHeight = 36.sp),
        headlineMedium = TextStyle(fontFamily = Bagel, fontSize = 26.sp, lineHeight = 30.sp),
        headlineSmall = TextStyle(fontFamily = Bagel, fontSize = 22.sp, lineHeight = 26.sp),
        titleLarge = TextStyle(fontFamily = Bagel, fontSize = 20.sp, lineHeight = 24.sp),
        titleMedium = base.titleMedium,
        titleSmall = base.titleSmall,
        bodyLarge = base.bodyLarge,
        bodyMedium = base.bodyMedium,
        bodySmall = base.bodySmall,
        labelLarge = base.labelLarge,
        labelMedium = base.labelMedium,
        labelSmall = base.labelSmall.copy(letterSpacing = 0.08.em),
    )
    CompositionLocalProvider(LocalFogon provides f) {
        MaterialTheme(colorScheme = esquema, typography = tipografia, content = contenido)
    }
}

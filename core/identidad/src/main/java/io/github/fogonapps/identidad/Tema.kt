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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

val Bagel = FontFamily(Font(R.font.bagel_fat_one))
val Anton = FontFamily(Font(R.font.anton))

/**
 * Lo que cada pantalla necesita saber de la identidad, más allá de Material. Sale de la paleta de la
 * app y de la [Apariencia] que eligió la persona: las pantallas leen siempre de aquí, nunca colores fijos.
 */
@Immutable
data class Fogon(
    /** La paleta en uso: la de la app con los colores del estilo y los que cambió la persona. */
    val paleta: Paleta,
    val oscuro: Boolean,
    val fondo: Color,
    /** Fondo de las tarjetas, ya con su transparencia. */
    val superficie: Color,
    val texto: Color,
    val textoSuave: Color,
    val linea: Color,
    /** El acento cuando es texto sobre el fondo. */
    val acentoTexto: Color,
    val estilo: Estilo = Estilo.NOCHE,
    /** Color de los números y palabras gigantes. */
    val numero: Color = texto,
    /** Botones y círculos "de contraste" (blanco en los estilos oscuros, tinta en los claros) y lo que va encima. */
    val pildora: Color = texto,
    val sobrePildora: Color = fondo,
    /** Los dos colores de la tarjeta destacada (el degradado) y el texto que va encima. */
    val destacada: List<Color> = listOf(paleta.segunda, paleta.luz2),
    val sobreDestacada: Color = Color.White,
    /** Grosor del borde de las tarjetas. */
    val borde: Dp = 1.dp,
    /** Sombra dura corrida (Riso) en lugar del brillo de color. */
    val sombraDura: Boolean = false,
    /** Brillo de color alrededor de botones y tarjetas destacadas. */
    val conBrillo: Boolean = true,
    val decoracion: Decoracion = Decoracion.ORBES,
    val grano: Float = 0.035f,
    val rectas: Boolean = false,
    val menosMovimiento: Boolean = false,
)

val LocalFogon = staticCompositionLocalOf<Fogon> { error("Falta FogonTema") }

object FogonTema {
    val actual: Fogon
        @Composable @ReadOnlyComposable get() = LocalFogon.current
}

/** Arma los colores de una app según su paleta y la Apariencia elegida. */
fun fogonDe(paleta: Paleta, a: Apariencia): Fogon {
    val e = a.estilo
    fun c(r: Ranura) = a.color(paleta, r)
    val fondo = c(Ranura.FONDO)
    val oscuro = fondo.luminance() < 0.4f
    val texto = c(Ranura.TEXTO)
    val acento = c(Ranura.ACENTO)
    val luz1 = c(Ranura.DEGRADADO_1); val luz2 = c(Ranura.DEGRADADO_2)
    val opacidad = 1f - a.transparencia() / 100f
    val superficie = c(Ranura.TARJETAS).copy(alpha = opacidad.coerceIn(0.04f, 1f))
    val conBorde = e == Estilo.RISO || e == Estilo.REBELDE || e == Estilo.CONTRASTE || a.masContraste
    val bordes = c(Ranura.BORDES)
    val textoSuave = if (a.masContraste) texto else c(Ranura.TEXTO_SUAVE)
    val acentoTexto = legibleSobre(fondo, listOf(acento, luz1, texto))
    val pildora = if (e == Estilo.REBELDE) Tintas.Tinta else texto
    // Rebelde: caja negra de cómic; Contraste: negro con borde. Los demás: el degradado de las dos luces.
    val destacada = when (e) {
        Estilo.REBELDE -> listOf(Tintas.Tinta, Tintas.Tinta)
        Estilo.CONTRASTE -> listOf(fondo, fondo)
        Estilo.RISO -> listOf(luz2, luz2)
        else -> listOf(luz1, luz2.copy(alpha = 0.85f))
    }
    val mitad = lerp(destacada[0], destacada[1].copy(alpha = 1f), 0.5f)
    val ef = paleta.copy(
        tinta = acento, sobreTinta = c(Ranura.SOBRE_ACENTO), textoEnTinta = acentoTexto,
        segunda = luz1, luz2 = luz2, fondo = fondo,
    )
    val granos = when (a.grano()) {
        Grano.NADA -> 0f
        Grano.SUAVE -> if (oscuro) 0.035f else 0.05f
        Grano.FUERTE -> if (oscuro) 0.08f else 0.12f
    }
    return Fogon(
        paleta = ef, oscuro = oscuro, fondo = fondo, superficie = superficie,
        texto = texto, textoSuave = textoSuave,
        linea = if (conBorde) bordes else bordes.copy(alpha = if (oscuro) 0.16f else 0.7f),
        acentoTexto = acentoTexto, estilo = e, numero = c(Ranura.NUMERO),
        pildora = pildora, sobrePildora = legibleSobre(pildora, listOf(fondo, Color.White, Tintas.Tinta)),
        destacada = destacada,
        sobreDestacada = legibleSobre(mitad, listOf(Color.White, Tintas.Papel, Tintas.Tinta)),
        borde = if (conBorde) 2.dp else 1.dp,
        sombraDura = e == Estilo.RISO,
        conBrillo = (e == Estilo.NOCHE || e == Estilo.AURA) && !a.menosMovimiento,
        decoracion = e.decoracion, grano = granos, rectas = a.rectas(), menosMovimiento = a.menosMovimiento,
    )
}

/**
 * El tema de las apps de Fogón. Por defecto, el estilo Noche con los colores de la app
 * (docs/IDENTIDAD.md → Estilo de noche); con [apariencia], lo que eligió la persona en Perfil → Apariencia.
 */
@Composable
fun FogonTema(
    paleta: Paleta,
    apariencia: Apariencia = Apariencia(),
    contenido: @Composable () -> Unit,
) {
    val f = fogonDe(paleta, apariencia)
    val p = f.paleta
    val esquema = if (f.oscuro) {
        darkColorScheme(
            primary = f.acentoTexto, onPrimary = legibleSobre(f.acentoTexto, listOf(p.sobreTinta, Color.White, Tintas.Tinta)),
            secondary = p.segunda, onSecondary = legibleSobre(p.segunda, listOf(Color.White, Tintas.Tinta)),
            background = f.fondo, onBackground = f.texto,
            surface = f.fondo, onSurface = f.texto,
            surfaceVariant = f.superficie, onSurfaceVariant = f.textoSuave,
            // Diálogos y menús: opacos, el vidrio se vería mal sobre otra pantalla.
            surfaceContainer = lerp(f.fondo, f.texto, 0.08f),
            surfaceContainerHigh = lerp(f.fondo, f.texto, 0.11f),
            surfaceContainerHighest = lerp(f.fondo, f.texto, 0.14f),
            outline = f.texto.copy(alpha = 0.32f), outlineVariant = f.linea,
            error = Color(0xFFFF8A80), onError = Tintas.Tinta,
        )
    } else {
        lightColorScheme(
            primary = f.acentoTexto, onPrimary = legibleSobre(f.acentoTexto, listOf(p.sobreTinta, Color.White, Tintas.Tinta)),
            secondary = p.segunda, onSecondary = legibleSobre(p.segunda, listOf(Color.White, Tintas.Tinta)),
            background = f.fondo, onBackground = f.texto,
            surface = f.fondo, onSurface = f.texto,
            surfaceVariant = lerp(f.fondo, f.texto, 0.06f), onSurfaceVariant = f.textoSuave,
            surfaceContainer = lerp(f.fondo, Color.White, 0.6f),
            surfaceContainerHigh = lerp(f.fondo, Color.White, 0.75f),
            surfaceContainerHighest = Color.White,
            outline = f.texto.copy(alpha = 0.5f), outlineVariant = f.linea,
            error = if (f.estilo == Estilo.REBELDE) Tintas.Tinta else Color(0xFFB3261E), onError = Color.White,
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
    // El tamaño del texto de la Apariencia se suma al que la persona tiene en el teléfono.
    val d = LocalDensity.current
    CompositionLocalProvider(
        LocalFogon provides f,
        LocalDensity provides Density(d.density, d.fontScale * apariencia.escalaTexto),
    ) {
        MaterialTheme(colorScheme = esquema, typography = tipografia, content = contenido)
    }
}

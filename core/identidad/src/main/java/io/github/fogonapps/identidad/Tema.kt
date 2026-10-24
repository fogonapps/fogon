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
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

val Bagel = FontFamily(Font(R.font.bagel_fat_one))
val Anton = FontFamily(Font(R.font.anton))

/** La letra del teléfono en grosor medio: títulos y cifras del estilo Neutro. */
val Sencilla = FontFamily(Font(DeviceFontFamilyName("sans-serif-medium")))

/**
 * Lo que cada pantalla necesita saber de la identidad, más allá de Material. Sale de la paleta de la
 * app y de la [Apariencia] que eligió la persona: las pantallas leen siempre de aquí, nunca colores fijos.
 * Todo se "imprime" con un papel ([fondo], [superficie]) y dos tintas: [tinta1] y [tinta2], la corrida
 * (ver docs/IDENTIDAD.md → Sistema riso).
 */
@Immutable
data class Fogon(
    /** La paleta en uso: tinta = [tinta1], sobreTinta = [sobreTinta1], segunda y luz2 = [tinta2]. */
    val paleta: Paleta,
    val oscuro: Boolean,
    /** El papel de las pantallas. */
    val fondo: Color,
    /** El papel de las tarjetas, ya con su transparencia. */
    val superficie: Color,
    val texto: Color,
    val textoSuave: Color,
    /** El color de los bordes (son de tinta, como en un dibujo). */
    val linea: Color,
    /** La primera tinta cuando es texto sobre el papel (o el texto, si la tinta no se lee ahí). */
    val acentoTexto: Color,
    val estilo: Estilo = Estilo.NEUTRO,
    /** La primera tinta: botones, pestaña activa, días cumplidos. Lo de encima va en [sobreTinta1]. */
    val tinta1: Color = paleta.tinta,
    val sobreTinta1: Color = paleta.sobreTinta,
    /** La segunda tinta, la que va corrida: sombras, el número fantasma, la trama, el fondo de los íconos. */
    val tinta2: Color = paleta.segunda,
    /** Cuánto se corre la segunda tinta. 0 = sin corrimiento (Contraste). */
    val desfase: Dp = 3.dp,
    /** Color de los números y palabras gigantes. */
    val numero: Color = texto,
    /** Píldoras "de contraste" (la tinta del texto) y lo que va encima: chips elegidos, avisos. */
    val pildora: Color = texto,
    val sobrePildora: Color = fondo,
    /** La tarjeta destacada: un bloque lleno de la primera tinta. Se deja como lista por compatibilidad. */
    val destacada: List<Color> = listOf(tinta1, tinta1),
    val sobreDestacada: Color = sobreTinta1,
    /** Grosor de los bordes de tinta. */
    val borde: Dp = 2.dp,
    val decoracion: Decoracion = Decoracion.TRAMA,
    val grano: Float = 0.035f,
    val rectas: Boolean = false,
    val menosMovimiento: Boolean = false,
    /** Letra de los títulos y nombres (Bagel Fat One; en Neutro, la del teléfono). */
    val titulos: FontFamily = Bagel,
    /** Letra de las cifras y palabras gigantes (Anton; en Neutro, la del teléfono). */
    val cifras: FontFamily = Anton,
    /** Cuánto mide de ancho una mayúscula de [cifras], en em: para que una palabra gigante quepa. */
    val anchoCifra: Float = 0.47f,
) {
    /** El corrimiento de los íconos: menos que el de las tarjetas, para que se lean. */
    val desfaseIcono: Dp get() = desfase * 0.45f
}

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
    val t1 = c(Ranura.TINTA_1); val t2 = c(Ranura.TINTA_2)
    val sobreT1 = c(Ranura.SOBRE_TINTA_1)
    val opacidad = 1f - a.transparencia() / 100f
    val superficie = c(Ranura.TARJETAS).copy(alpha = opacidad.coerceIn(0.04f, 1f))
    val textoSuave = if (a.masContraste) texto else c(Ranura.TEXTO_SUAVE)
    val acentoTexto = legibleSobre(fondo, listOf(t1, texto))
    val ef = paleta.copy(
        tinta = t1, sobreTinta = sobreT1, textoEnTinta = acentoTexto,
        segunda = t2, luz2 = t2, fondo = fondo,
    )
    val granos = when (a.grano()) {
        Grano.NADA -> 0f
        Grano.SUAVE -> if (oscuro) 0.04f else 0.06f
        Grano.FUERTE -> if (oscuro) 0.08f else 0.13f
    }
    return Fogon(
        paleta = ef, oscuro = oscuro, fondo = fondo, superficie = superficie,
        texto = texto, textoSuave = textoSuave, linea = c(Ranura.BORDES),
        acentoTexto = acentoTexto, estilo = e,
        tinta1 = t1, sobreTinta1 = sobreT1, tinta2 = t2, desfase = a.desfase().dp,
        numero = c(Ranura.NUMERO),
        pildora = texto, sobrePildora = legibleSobre(texto, listOf(fondo, Color.White, Tintas.Tinta)),
        destacada = listOf(t1, t1), sobreDestacada = sobreT1,
        borde = when {
            e == Estilo.NEUTRO -> if (a.masContraste) 2.dp else 1.dp
            a.masContraste -> 3.dp
            else -> 2.dp
        },
        decoracion = a.adorno(), grano = granos, rectas = a.rectas(), menosMovimiento = a.menosMovimiento,
        titulos = if (e == Estilo.NEUTRO) Sencilla else Bagel,
        cifras = if (e == Estilo.NEUTRO) Sencilla else Anton,
        anchoCifra = if (e == Estilo.NEUTRO) 0.64f else 0.47f,
    )
}

/**
 * El tema de las apps de Fogón. Por defecto, el estilo Neutro
 * (docs/IDENTIDAD.md → Sistema riso); con [apariencia], lo que eligió la persona en Perfil → Apariencia.
 */
@Composable
fun FogonTema(
    paleta: Paleta,
    apariencia: Apariencia = Apariencia(),
    contenido: @Composable () -> Unit,
) {
    val f = fogonDe(paleta, apariencia)
    val esquema = if (f.oscuro) {
        darkColorScheme(
            primary = f.acentoTexto, onPrimary = legibleSobre(f.acentoTexto, listOf(f.sobreTinta1, Color.White, Tintas.Tinta)),
            secondary = f.tinta2, onSecondary = legibleSobre(f.tinta2, listOf(Color.White, Tintas.Tinta)),
            background = f.fondo, onBackground = f.texto,
            surface = f.fondo, onSurface = f.texto,
            surfaceVariant = f.superficie, onSurfaceVariant = f.textoSuave,
            // Diálogos y menús: opacos, el vidrio se vería mal sobre otra pantalla.
            surfaceContainer = lerp(f.fondo, f.texto, 0.08f),
            surfaceContainerHigh = lerp(f.fondo, f.texto, 0.11f),
            surfaceContainerHighest = lerp(f.fondo, f.texto, 0.14f),
            outline = f.texto.copy(alpha = 0.32f), outlineVariant = f.linea.copy(alpha = 0.25f),
            error = Color(0xFFFF8A80), onError = Tintas.Tinta,
        )
    } else {
        lightColorScheme(
            primary = f.acentoTexto, onPrimary = legibleSobre(f.acentoTexto, listOf(f.sobreTinta1, Color.White, Tintas.Tinta)),
            secondary = f.tinta2, onSecondary = legibleSobre(f.tinta2, listOf(Color.White, Tintas.Tinta)),
            background = f.fondo, onBackground = f.texto,
            surface = f.fondo, onSurface = f.texto,
            surfaceVariant = lerp(f.fondo, f.texto, 0.06f), onSurfaceVariant = f.textoSuave,
            surfaceContainer = lerp(f.fondo, Color.White, 0.6f),
            surfaceContainerHigh = lerp(f.fondo, Color.White, 0.75f),
            surfaceContainerHighest = Color.White,
            outline = f.texto.copy(alpha = 0.5f), outlineVariant = f.linea.copy(alpha = 0.25f),
            error = if (f.estilo == Estilo.REBELDE) Tintas.Tinta else Color(0xFFB3261E), onError = Color.White,
        )
    }
    val base = Typography()
    val tipografia = Typography(
        displayLarge = TextStyle(fontFamily = f.cifras, fontSize = 56.sp, lineHeight = 56.sp),
        displayMedium = TextStyle(fontFamily = f.cifras, fontSize = 40.sp, lineHeight = 42.sp),
        displaySmall = TextStyle(fontFamily = f.cifras, fontSize = 30.sp, lineHeight = 32.sp),
        headlineLarge = TextStyle(fontFamily = f.titulos, fontSize = 32.sp, lineHeight = 36.sp),
        headlineMedium = TextStyle(fontFamily = f.titulos, fontSize = 26.sp, lineHeight = 30.sp),
        headlineSmall = TextStyle(fontFamily = f.titulos, fontSize = 22.sp, lineHeight = 26.sp),
        titleLarge = TextStyle(fontFamily = f.titulos, fontSize = 20.sp, lineHeight = 24.sp),
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

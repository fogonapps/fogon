package io.github.fogonapps.identidad

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** La letra de Fogón: Bricolage Grotesque (OFL), para todo. */
val Bricolage = FontFamily(
    Font(R.font.bricolage_regular, FontWeight.Normal),
    Font(R.font.bricolage_medium, FontWeight.Medium),
    Font(R.font.bricolage_semibold, FontWeight.SemiBold),
    Font(R.font.bricolage_bold, FontWeight.Bold),
)

/** Doto (OFL): números hechos de puntos, solo para el número grande de cada pantalla. */
val Puntos = FontFamily(Font(R.font.doto_bold, FontWeight.Bold))

/**
 * Lo que cada pantalla necesita saber de la identidad, más allá de Material. Sale de la paleta de la
 * app y de la [Apariencia] que eligió la persona: las pantallas leen siempre de aquí, nunca colores fijos.
 * Un papel ([fondo]) con una luz suave ([brillo]), tarjetas de vidrio ([superficie], [linea]) y dos tintas:
 * [tinta1] (botones, acento) y [tinta2] (la segunda: sombra del personaje, metas, halo de puntos).
 */
@Immutable
data class Fogon(
    /** La paleta en uso: tinta = [tinta1], sobreTinta = [sobreTinta1], segunda y luz2 = [tinta2]. */
    val paleta: Paleta,
    val oscuro: Boolean,
    /** El papel de las pantallas. */
    val fondo: Color,
    /** El vidrio de las tarjetas (con transparencia). */
    val superficie: Color,
    val texto: Color,
    val textoSuave: Color,
    /** El borde fino del vidrio. */
    val linea: Color,
    /** La primera tinta cuando es texto sobre el papel (o el texto, si la tinta no se lee ahí). */
    val acentoTexto: Color,
    val estilo: Estilo = Estilo.GRAFITO,
    /** La primera tinta: botones, pestaña activa, días cumplidos. Lo de encima va en [sobreTinta1]. */
    val tinta1: Color = paleta.tinta,
    val sobreTinta1: Color = paleta.sobreTinta,
    /** La segunda tinta: barras de metas, el halo de puntos. */
    val tinta2: Color = paleta.segunda,
    /** El color propio de la app (la tinta de su paleta, sin el tema): el círculo detrás de su personaje en los temas claros. */
    val marca: Color = paleta.tinta,
    /** Ya no hay tinta corrida: queda en 0 por compatibilidad. */
    val desfase: Dp = 0.dp,
    /** Color del número grande. */
    val numero: Color = texto,
    /** Píldoras "de contraste" (la tinta del texto) y lo que va encima: chips elegidos, avisos. */
    val pildora: Color = texto,
    val sobrePildora: Color = fondo,
    /** La tarjeta destacada: un bloque lleno de la primera tinta. Se deja como lista por compatibilidad. */
    val destacada: List<Color> = listOf(tinta1, tinta1),
    val sobreDestacada: Color = sobreTinta1,
    /** Grosor del borde del vidrio. */
    val borde: Dp = 1.dp,
    val decoracion: Decoracion = Decoracion.LUZ,
    /** La luz suave de arriba de cada pantalla. */
    val brillo: Color = Color.Transparent,
    val luz: LuzPropia? = null,
    /** El vidrio opaco de las barras flotantes (pestañas), para que lo de abajo no se cruce. */
    val barra: Color = superficie,
    val menosMovimiento: Boolean = false,
    /** Letra de los títulos y nombres. */
    val titulos: FontFamily = Bricolage,
    /** Letra de las cifras de todos los días (montos, kilos). */
    val cifras: FontFamily = Bricolage,
    /** Letra del número grande de cada pantalla: puntos. */
    val puntos: FontFamily = Puntos,
    /** Cuánto mide de ancho una mayúscula de [puntos], en em: para que una palabra grande quepa. */
    val anchoCifra: Float = 0.62f,
) {
    /**
     * La tinta de las ilustraciones: de las dos, la que tenga más color (en Claro, el acento de la app y no el
     * casi negro de los botones), siempre que se distinga del papel.
     */
    val tintaIlustracion: Color get() {
        fun color(c: Color): Float { val mx = maxOf(c.red, c.green, c.blue); return if (mx == 0f) 0f else (mx - minOf(c.red, c.green, c.blue)) / mx }
        return listOf(tinta1, tinta2).filter { contraste(it, fondo) >= 1.4 }.maxByOrNull { color(it) } ?: tinta1
    }
}

val LocalFogon = staticCompositionLocalOf<Fogon> { error("Falta FogonTema") }

object FogonTema {
    val actual: Fogon
        @Composable @ReadOnlyComposable get() = LocalFogon.current
}

/** Arma los colores de una app según su paleta y la Apariencia elegida. */
fun fogonDe(paleta: Paleta, a: Apariencia): Fogon {
    val e = a.estilo
    val c = a.colores(paleta)
    val oscuro = c.fondo.luminance() < 0.4f
    val textoSuave = if (a.masContraste) c.texto else c.textoSuave
    val acentoTexto = legibleSobre(c.fondo, listOf(c.tinta1, c.texto))
    val ef = paleta.copy(
        tinta = c.tinta1, sobreTinta = c.sobreTinta1, textoEnTinta = acentoTexto,
        segunda = c.tinta2, luz2 = c.tinta2, fondo = c.fondo,
    )
    return Fogon(
        paleta = ef, oscuro = oscuro, fondo = c.fondo, superficie = c.tarjetas,
        texto = c.texto, textoSuave = textoSuave, linea = if (a.masContraste) c.texto else c.bordes,
        acentoTexto = acentoTexto, estilo = e,
        tinta1 = c.tinta1, sobreTinta1 = c.sobreTinta1, tinta2 = c.tinta2, marca = paleta.tinta,
        numero = c.numero,
        pildora = c.texto, sobrePildora = legibleSobre(c.texto, listOf(c.fondo, Color.White, Tintas.Tinta)),
        destacada = listOf(c.tinta1, c.tinta1), sobreDestacada = c.sobreTinta1,
        borde = if (a.masContraste) 2.dp else 1.dp,
        decoracion = a.adorno(), brillo = if (a.adorno() == Decoracion.LUZ && e != Estilo.CONTRASTE) c.brillo else Color.Transparent,
        luz = if (a.adorno() == Decoracion.LUZ && e != Estilo.CONTRASTE) a.de().luz?.legible(c) else null,
        barra = lerp(c.fondo, if (oscuro) Color.White else Color.Black, 0.07f),
        menosMovimiento = a.menosMovimiento,
    )
}

/**
 * El tema de las apps de Fogón. Por defecto, el tema Grafito; con [apariencia], lo que eligió la persona en Perfil → Apariencia.
 */
@Composable
fun FogonTema(
    paleta: Paleta,
    apariencia: Apariencia = Apariencia(),
    /** Letra propia de los títulos de la app (Fabs: Fraunces). Sin ella, Bricolage. */
    letraTitulos: FontFamily? = null,
    /** Letra propia del texto de la app (Fabs: Figtree). Sin ella, Bricolage. */
    letraTexto: FontFamily? = null,
    contenido: @Composable () -> Unit,
) {
    val texto = letraTexto ?: Bricolage
    val f = fogonDe(paleta, apariencia).copy(titulos = letraTitulos ?: texto, cifras = texto)
    val esquema = if (f.oscuro) {
        darkColorScheme(
            primary = f.acentoTexto, onPrimary = legibleSobre(f.acentoTexto, listOf(f.sobreTinta1, Color.White, Tintas.Tinta)),
            secondary = f.tinta2, onSecondary = legibleSobre(f.tinta2, listOf(Color.White, Tintas.Tinta)),
            // Chips y botones elegidos de Material: vidrio un poco más claro, nunca el morado de fábrica.
            primaryContainer = lerp(f.fondo, f.texto, 0.16f), onPrimaryContainer = f.texto,
            secondaryContainer = lerp(f.fondo, f.texto, 0.16f), onSecondaryContainer = f.texto,
            tertiaryContainer = lerp(f.fondo, f.texto, 0.16f), onTertiaryContainer = f.texto,
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
            primaryContainer = lerp(f.fondo, f.texto, 0.1f), onPrimaryContainer = f.texto,
            secondaryContainer = lerp(f.fondo, f.texto, 0.1f), onSecondaryContainer = f.texto,
            tertiaryContainer = lerp(f.fondo, f.texto, 0.1f), onTertiaryContainer = f.texto,
            background = f.fondo, onBackground = f.texto,
            surface = f.fondo, onSurface = f.texto,
            surfaceVariant = lerp(f.fondo, f.texto, 0.06f), onSurfaceVariant = f.textoSuave,
            surfaceContainer = lerp(f.fondo, Color.White, 0.6f),
            surfaceContainerHigh = lerp(f.fondo, Color.White, 0.75f),
            surfaceContainerHighest = Color.White,
            outline = f.texto.copy(alpha = 0.5f), outlineVariant = f.linea.copy(alpha = 0.25f),
            error = Color(0xFFB3261E), onError = Color.White,
        )
    }
    // La letra del texto para todo (Bricolage, o la propia de la app); los títulos grandes, en la de títulos si la app
    // tiene una (Fabs). El número grande de cada pantalla va aparte, en puntos.
    val base = Typography()
    val grande = letraTitulos ?: texto
    // Con letra de títulos propia (Fabs: una serifa de cartel), los grandes van en negrita y también los títulos de
    // sección; sin ella, todo sale de la letra del texto.
    val propia = letraTitulos != null
    val pesoGrande = if (propia) FontWeight.Bold else FontWeight.SemiBold
    val apretarGrande = if (propia) (-0.015).em else (-0.02).em
    fun TextStyle.fogon(peso: FontWeight? = null, apretada: Boolean = false) =
        copy(fontFamily = texto, fontWeight = peso ?: fontWeight, letterSpacing = if (apretada) (-0.02).em else letterSpacing)
    fun TextStyle.grande() = copy(fontFamily = grande, fontWeight = pesoGrande, letterSpacing = apretarGrande)
    val tipografia = Typography(
        displayLarge = base.displayLarge.grande(),
        displayMedium = base.displayMedium.grande(),
        displaySmall = base.displaySmall.grande(),
        headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp).grande(),
        headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp).grande(),
        headlineSmall = TextStyle(fontFamily = if (propia) grande else texto, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.01).em),
        titleLarge = TextStyle(fontFamily = if (propia) grande else texto, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = (-0.01).em),
        titleMedium = base.titleMedium.fogon(FontWeight.SemiBold),
        titleSmall = base.titleSmall.fogon(FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.fogon(),
        bodyMedium = base.bodyMedium.fogon(),
        bodySmall = base.bodySmall.fogon(),
        labelLarge = base.labelLarge.fogon(FontWeight.SemiBold),
        labelMedium = base.labelMedium.fogon(FontWeight.Medium),
        labelSmall = base.labelSmall.fogon(FontWeight.Medium),
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

/**
 * Los íconos de las barras del sistema de un diálogo a pantalla completa, oscuros sobre un [fondo] claro y claros sobre
 * uno oscuro. Un `Dialog` tiene su propia ventana: no hereda lo que la actividad puso con `enableEdgeToEdge` y deja
 * los íconos claros aunque el fondo sea claro. Se llama dentro del contenido del `Dialog`, con el color que de verdad
 * queda detrás de la barra de estado.
 */
@Composable
fun BarrasDelDialogo(fondo: Color = FogonTema.actual.fondo) {
    val vista = LocalView.current
    val claro = fondo.luminance() > 0.5f
    SideEffect {
        val ventana = (vista.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(ventana, vista).run {
            isAppearanceLightStatusBars = claro
            isAppearanceLightNavigationBars = claro
        }
    }
}

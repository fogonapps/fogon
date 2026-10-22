package io.github.fogonapps.identidad

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/*
 * La Apariencia: cómo quiere ver la app cada persona. Hay cinco estilos (ver docs/IDENTIDAD.md →
 * Apariencia). Cada estilo trae sus colores, su transparencia, su grano y sus esquinas por defecto, y la
 * persona puede cambiar cualquiera de ellos; lo que cambia queda guardado para ese estilo.
 * Lo de comodidad (tamaño del texto, menos movimiento, más contraste) vale para todos los estilos.
 */

enum class Estilo { NOCHE, RISO, REBELDE, AURA, CONTRASTE }

enum class Grano { NADA, SUAVE, FUERTE }

/** Qué dibuja cada estilo detrás de las pantallas. */
enum class Decoracion { ORBES, SEMITONO, RAYOS, AURA, NADA }

/** Cada color que la persona puede cambiar. */
enum class Ranura { FONDO, TARJETAS, TEXTO, TEXTO_SUAVE, ACENTO, SOBRE_ACENTO, DEGRADADO_1, DEGRADADO_2, NUMERO, BORDES }

/** Lo que la persona cambió en un estilo. null = lo que trae el estilo. */
data class AjustesEstilo(
    val colores: Map<Ranura, Color> = emptyMap(),
    val transparencia: Int? = null,
    val grano: Grano? = null,
    val rectas: Boolean? = null,
)

data class Apariencia(
    val estilo: Estilo = Estilo.NOCHE,
    val ajustes: Map<Estilo, AjustesEstilo> = emptyMap(),
    /** 0 pequeño, 1 normal, 2 grande, 3 muy grande. */
    val tamanoTexto: Int = 1,
    val menosMovimiento: Boolean = false,
    val masContraste: Boolean = false,
) {
    fun de(e: Estilo = estilo) = ajustes[e] ?: AjustesEstilo()

    fun color(paleta: Paleta, r: Ranura, e: Estilo = estilo): Color = de(e).colores[r] ?: e.colorBase(paleta, r)
    fun transparencia(e: Estilo = estilo) = de(e).transparencia ?: e.transparenciaBase
    fun grano(e: Estilo = estilo) = de(e).grano ?: e.granoBase
    fun rectas(e: Estilo = estilo) = de(e).rectas ?: e.rectasBase

    /** Cuánto crece el texto: se aplica a todas las letras de la app. */
    val escalaTexto get() = listOf(0.88f, 1f, 1.15f, 1.3f)[tamanoTexto.coerceIn(0, 3)]
}

/** Lo que trae cada estilo. Noche, Riso y Aura toman los colores de la app; Rebelde y Contraste son iguales en todas. */
fun Estilo.colorBase(p: Paleta, r: Ranura): Color = when (this) {
    Estilo.NOCHE -> when (r) {
        Ranura.FONDO -> p.fondo
        Ranura.TARJETAS -> Color.White
        Ranura.TEXTO -> Color.White
        Ranura.TEXTO_SUAVE -> Color(0xFFB9B5C0)
        Ranura.ACENTO -> p.tinta
        Ranura.SOBRE_ACENTO -> p.sobreTinta
        Ranura.DEGRADADO_1 -> p.segunda
        Ranura.DEGRADADO_2 -> p.luz2
        Ranura.NUMERO -> Color.White
        Ranura.BORDES -> Color.White
    }
    Estilo.RISO -> when (r) {
        Ranura.FONDO -> Tintas.Papel
        Ranura.TARJETAS -> Color.White
        Ranura.TEXTO -> Tintas.Tinta
        Ranura.TEXTO_SUAVE -> Color(0xFF4A4038)
        Ranura.ACENTO -> p.segundaTinta
        Ranura.SOBRE_ACENTO -> Tintas.Tinta
        Ranura.DEGRADADO_1 -> p.textoEnPapel
        Ranura.DEGRADADO_2 -> p.segundaTinta
        Ranura.NUMERO -> p.textoEnPapel
        Ranura.BORDES -> Tintas.Tinta
    }
    Estilo.REBELDE -> when (r) {
        Ranura.FONDO -> Color(0xFFF03E33)
        Ranura.TARJETAS -> Tintas.Papel
        Ranura.TEXTO -> Tintas.Tinta
        Ranura.TEXTO_SUAVE -> Color(0xFF2A1210)
        Ranura.ACENTO -> Tintas.Tinta
        Ranura.SOBRE_ACENTO -> Tintas.Papel
        Ranura.DEGRADADO_1 -> Tintas.Tinta
        Ranura.DEGRADADO_2 -> Tintas.Papel
        Ranura.NUMERO -> Tintas.Tinta
        Ranura.BORDES -> Tintas.Tinta
    }
    Estilo.AURA -> when (r) {
        Ranura.FONDO -> Color(0xFFF1F0EE)
        Ranura.TARJETAS -> Color.White
        Ranura.TEXTO -> Color(0xFF17151C)
        Ranura.TEXTO_SUAVE -> Color(0xFF4B4752)
        Ranura.ACENTO -> Color(0xFF17151C)
        Ranura.SOBRE_ACENTO -> Color.White
        Ranura.DEGRADADO_1 -> p.segunda
        Ranura.DEGRADADO_2 -> p.tinta
        Ranura.NUMERO -> Color(0xFF17151C)
        Ranura.BORDES -> Color.White
    }
    Estilo.CONTRASTE -> when (r) {
        Ranura.FONDO, Ranura.TARJETAS, Ranura.SOBRE_ACENTO, Ranura.DEGRADADO_1, Ranura.DEGRADADO_2 -> Color.Black
        Ranura.TEXTO, Ranura.TEXTO_SUAVE, Ranura.NUMERO, Ranura.BORDES -> Color.White
        Ranura.ACENTO -> p.tinta
    }
}

/** 0 = tarjetas sólidas; 100 = no se ven. */
val Estilo.transparenciaBase get() = when (this) {
    Estilo.NOCHE -> 92
    Estilo.AURA -> 45
    else -> 0
}

val Estilo.granoBase get() = when (this) {
    Estilo.RISO, Estilo.REBELDE -> Grano.FUERTE
    Estilo.CONTRASTE -> Grano.NADA
    else -> Grano.SUAVE
}

val Estilo.rectasBase get() = this == Estilo.REBELDE

val Estilo.decoracion get() = when (this) {
    Estilo.NOCHE -> Decoracion.ORBES
    Estilo.RISO -> Decoracion.SEMITONO
    Estilo.REBELDE -> Decoracion.RAYOS
    Estilo.AURA -> Decoracion.AURA
    Estilo.CONTRASTE -> Decoracion.NADA
}

/* ----- Contraste (WCAG) ----- */

fun contraste(a: Color, b: Color): Double {
    val la = a.luminance() + 0.05; val lb = b.luminance() + 0.05
    return if (la > lb) la / lb else lb / la
}

/** De los candidatos, el primero que se lee sobre [fondo]; si ninguno, el que más contrasta. */
fun legibleSobre(fondo: Color, candidatos: List<Color>, minimo: Double = 4.5): Color =
    candidatos.firstOrNull { contraste(it, fondo) >= minimo } ?: candidatos.maxBy { contraste(it, fondo) }

/* ----- Dónde se guarda ----- */

/**
 * Guarda la Apariencia en las preferencias "apariencia" de la app. Hay una sola por proceso
 * ([AlmacenApariencia.de]) para que todas las pantallas vean el mismo estado.
 */
class AlmacenApariencia private constructor(c: Context) {
    private val prefs = c.getSharedPreferences("apariencia", Context.MODE_PRIVATE)
    private val _estado = MutableStateFlow(leer())
    val estado: StateFlow<Apariencia> = _estado

    private fun leer(): Apariencia {
        val ajustes = Estilo.entries.associateWith { e ->
            AjustesEstilo(
                colores = Ranura.entries.mapNotNull { r ->
                    val k = "${e.name}.${r.name}"
                    if (prefs.contains(k)) r to Color(prefs.getInt(k, 0)) else null
                }.toMap(),
                transparencia = prefs.getInt("${e.name}.transparencia", -1).takeIf { it >= 0 },
                grano = prefs.getString("${e.name}.grano", null)?.let { g -> Grano.entries.firstOrNull { it.name == g } },
                rectas = if (prefs.contains("${e.name}.rectas")) prefs.getBoolean("${e.name}.rectas", false) else null,
            )
        }.filterValues { it != AjustesEstilo() }
        return Apariencia(
            estilo = prefs.getString("estilo", null)?.let { s -> Estilo.entries.firstOrNull { it.name == s } } ?: Estilo.NOCHE,
            ajustes = ajustes,
            tamanoTexto = prefs.getInt("tamanoTexto", 1),
            menosMovimiento = prefs.getBoolean("menosMovimiento", false),
            masContraste = prefs.getBoolean("masContraste", false),
        )
    }

    private fun recargar() { _estado.value = leer() }

    fun elegirEstilo(e: Estilo) { prefs.edit { putString("estilo", e.name) }; recargar() }

    fun ponerColor(e: Estilo, r: Ranura, c: Color?) {
        prefs.edit { if (c == null) remove("${e.name}.${r.name}") else putInt("${e.name}.${r.name}", c.toArgb()) }
        recargar()
    }

    fun ponerTransparencia(e: Estilo, v: Int) { prefs.edit { putInt("${e.name}.transparencia", v.coerceIn(0, 100)) }; recargar() }
    fun ponerGrano(e: Estilo, g: Grano) { prefs.edit { putString("${e.name}.grano", g.name) }; recargar() }
    fun ponerRectas(e: Estilo, v: Boolean) { prefs.edit { putBoolean("${e.name}.rectas", v) }; recargar() }

    /** Devuelve el estilo a como viene: colores, transparencia, grano y esquinas. */
    fun restablecer(e: Estilo) {
        prefs.edit {
            Ranura.entries.forEach { remove("${e.name}.${it.name}") }
            remove("${e.name}.transparencia"); remove("${e.name}.grano"); remove("${e.name}.rectas")
        }
        recargar()
    }

    fun ponerTamanoTexto(v: Int) { prefs.edit { putInt("tamanoTexto", v.coerceIn(0, 3)) }; recargar() }
    fun ponerMenosMovimiento(v: Boolean) { prefs.edit { putBoolean("menosMovimiento", v) }; recargar() }
    fun ponerMasContraste(v: Boolean) { prefs.edit { putBoolean("masContraste", v) }; recargar() }

    companion object {
        @Volatile private var unico: AlmacenApariencia? = null
        fun de(c: Context): AlmacenApariencia = unico ?: synchronized(this) { unico ?: AlmacenApariencia(c.applicationContext).also { unico = it } }
    }
}

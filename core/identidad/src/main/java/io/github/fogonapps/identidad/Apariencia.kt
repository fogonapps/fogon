package io.github.fogonapps.identidad

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/*
 * La Apariencia: cómo quiere ver la app cada persona. Todo en Fogón se "imprime" como una riso: un papel,
 * dos tintas y la segunda corrida (ver docs/IDENTIDAD.md → Sistema riso). Hay cinco estilos, cada uno con
 * su papel, sus tintas, su corrimiento y su adorno; la persona puede cambiar cualquiera de ellos y lo que
 * cambia queda guardado para ese estilo. Lo de comodidad (tamaño del texto, menos movimiento, más
 * contraste) vale para todos los estilos.
 */

enum class Estilo { NOCHE, RISO, REBELDE, CHICLE, CONTRASTE }

enum class Grano { NADA, SUAVE, FUERTE }

/** El adorno impreso detrás de las pantallas: trama de puntos, rayos de cómic, destellos o nada. */
enum class Decoracion { TRAMA, RAYOS, ESTRELLAS, NADA }

/** Cada color que la persona puede cambiar. */
enum class Ranura { FONDO, TARJETAS, TEXTO, TEXTO_SUAVE, TINTA_1, SOBRE_TINTA_1, TINTA_2, NUMERO, BORDES }

/** Lo que la persona cambió en un estilo. null = lo que trae el estilo. */
data class AjustesEstilo(
    val colores: Map<Ranura, Color> = emptyMap(),
    /** Cuánto se corre la segunda tinta, en dp (0 = registro perfecto). */
    val desfase: Float? = null,
    val transparencia: Int? = null,
    val grano: Grano? = null,
    val rectas: Boolean? = null,
    val adorno: Decoracion? = null,
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

    /**
     * El color de una ranura. Si la persona no la cambió, sale del estilo; algunas siguen a otras para que
     * cambiar una tinta no deje nada ilegible: el número gigante sigue a la primera tinta (en los estilos
     * que lo imprimen en ella), el texto sobre la tinta se elige legible y los bordes siguen al texto.
     */
    fun color(paleta: Paleta, r: Ranura, e: Estilo = estilo): Color {
        de(e).colores[r]?.let { return it }
        val cambiada = { x: Ranura -> x in de(e).colores }
        return when {
            r == Ranura.NUMERO && e.numeroEnTinta && cambiada(Ranura.TINTA_1) -> color(paleta, Ranura.TINTA_1, e)
            r == Ranura.SOBRE_TINTA_1 && cambiada(Ranura.TINTA_1) ->
                legibleSobre(color(paleta, Ranura.TINTA_1, e), listOf(e.colorBase(paleta, r), Color.White, Tintas.Tinta))
            r == Ranura.BORDES && cambiada(Ranura.TEXTO) -> color(paleta, Ranura.TEXTO, e)
            r == Ranura.TARJETAS && cambiada(Ranura.FONDO) && e.tarjetaDelPapel -> color(paleta, Ranura.FONDO, e)
            else -> e.colorBase(paleta, r)
        }
    }
    fun desfase(e: Estilo = estilo) = de(e).desfase ?: e.desfaseBase
    fun transparencia(e: Estilo = estilo) = de(e).transparencia ?: 0
    fun grano(e: Estilo = estilo) = de(e).grano ?: e.granoBase
    fun rectas(e: Estilo = estilo) = de(e).rectas ?: e.rectasBase
    fun adorno(e: Estilo = estilo) = de(e).adorno ?: e.decoracion

    /** Cuánto crece el texto: se aplica a todas las letras de la app. */
    val escalaTexto get() = listOf(0.88f, 1f, 1.15f, 1.3f)[tamanoTexto.coerceIn(0, 3)]
}

/**
 * Lo que trae cada estilo. Noche y Riso imprimen con las tintas de la app; Rebelde, Chicle y Contraste son
 * iguales en todas las apps (Contraste toma de la app solo su segunda tinta).
 */
fun Estilo.colorBase(p: Paleta, r: Ranura): Color = when (this) {
    Estilo.NOCHE -> when (r) {
        Ranura.FONDO -> p.fondo
        Ranura.TARJETAS -> lerp(p.fondo, Color.White, 0.07f)
        Ranura.TEXTO, Ranura.NUMERO, Ranura.BORDES -> Color(0xFFF3EEE6)
        Ranura.TEXTO_SUAVE -> Color(0xFFBDB5CB)
        Ranura.TINTA_1 -> p.tinta
        Ranura.SOBRE_TINTA_1 -> p.sobreTinta
        Ranura.TINTA_2 -> p.segunda
    }
    Estilo.RISO -> when (r) {
        Ranura.FONDO -> Tintas.Papel
        Ranura.TARJETAS -> Color(0xFFFBF7F0)
        Ranura.TEXTO, Ranura.BORDES -> Tintas.Tinta
        Ranura.TEXTO_SUAVE -> Color(0xFF4A4038)
        Ranura.TINTA_1, Ranura.NUMERO -> p.textoEnPapel
        Ranura.SOBRE_TINTA_1 -> legibleSobre(p.textoEnPapel, listOf(Color.White, Tintas.Tinta))
        Ranura.TINTA_2 -> p.segundaTinta
    }
    Estilo.REBELDE -> when (r) {
        Ranura.FONDO -> Color(0xFFF03E33)
        Ranura.TARJETAS, Ranura.SOBRE_TINTA_1, Ranura.TINTA_2 -> Tintas.Papel
        Ranura.TEXTO, Ranura.TINTA_1, Ranura.NUMERO, Ranura.BORDES -> Tintas.Tinta
        Ranura.TEXTO_SUAVE -> Color(0xFF2A1210)
    }
    Estilo.CHICLE -> when (r) {
        Ranura.FONDO -> Color(0xFFFFE1EE)
        Ranura.TARJETAS, Ranura.SOBRE_TINTA_1 -> Color.White
        Ranura.TEXTO, Ranura.BORDES -> Color(0xFF1A1035)
        Ranura.TEXTO_SUAVE -> Color(0xFF4B3A66)
        Ranura.TINTA_1, Ranura.NUMERO -> Color(0xFF2D5BFF)
        Ranura.TINTA_2 -> Color(0xFFFFB020)
    }
    Estilo.CONTRASTE -> when (r) {
        Ranura.FONDO, Ranura.TARJETAS, Ranura.SOBRE_TINTA_1 -> Color.Black
        Ranura.TEXTO, Ranura.TEXTO_SUAVE, Ranura.NUMERO, Ranura.BORDES, Ranura.TINTA_1 -> Color.White
        Ranura.TINTA_2 -> p.tinta
    }
}

/** Los estilos que imprimen el número gigante en la primera tinta (y no en el color del texto). */
private val Estilo.numeroEnTinta get() = this == Estilo.RISO || this == Estilo.CHICLE

/** Contraste: las tarjetas son del mismo papel que el fondo. */
private val Estilo.tarjetaDelPapel get() = this == Estilo.CONTRASTE

val Estilo.desfaseBase get() = when (this) {
    Estilo.REBELDE -> 4f
    Estilo.CONTRASTE -> 0f
    else -> 3f
}

val Estilo.granoBase get() = when (this) {
    Estilo.RISO, Estilo.REBELDE -> Grano.FUERTE
    Estilo.CONTRASTE -> Grano.NADA
    else -> Grano.SUAVE
}

val Estilo.rectasBase get() = this == Estilo.REBELDE

val Estilo.decoracion get() = when (this) {
    Estilo.NOCHE, Estilo.RISO -> Decoracion.TRAMA
    Estilo.REBELDE -> Decoracion.RAYOS
    Estilo.CHICLE -> Decoracion.ESTRELLAS
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

    init { migrar() }

    private val _estado = MutableStateFlow(leer())
    val estado: StateFlow<Apariencia> = _estado

    /**
     * Versión 2 (sistema riso): los colores, la transparencia y lo demás de cada estilo cambiaron de
     * sentido (ya no hay degradados), así que se borran y cada estilo vuelve a como viene. Aura pasó a Chicle.
     * Lo de comodidad se queda.
     */
    private fun migrar() {
        if (prefs.getInt("version", 1) >= 2) return
        val comodidad = setOf("estilo", "tamanoTexto", "menosMovimiento", "masContraste")
        prefs.edit {
            prefs.all.keys.filter { it !in comodidad }.forEach { remove(it) }
            if (prefs.getString("estilo", null) == "AURA") putString("estilo", Estilo.CHICLE.name)
            putInt("version", 2)
        }
    }

    private fun leer(): Apariencia {
        val ajustes = Estilo.entries.associateWith { e ->
            AjustesEstilo(
                colores = Ranura.entries.mapNotNull { r ->
                    val k = "${e.name}.${r.name}"
                    if (prefs.contains(k)) r to Color(prefs.getInt(k, 0)) else null
                }.toMap(),
                desfase = prefs.getFloat("${e.name}.desfase", -1f).takeIf { it >= 0f },
                transparencia = prefs.getInt("${e.name}.transparencia", -1).takeIf { it >= 0 },
                grano = prefs.getString("${e.name}.grano", null)?.let { g -> Grano.entries.firstOrNull { it.name == g } },
                rectas = if (prefs.contains("${e.name}.rectas")) prefs.getBoolean("${e.name}.rectas", false) else null,
                adorno = prefs.getString("${e.name}.adorno", null)?.let { g -> Decoracion.entries.firstOrNull { it.name == g } },
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

    fun ponerDesfase(e: Estilo, v: Float) { prefs.edit { putFloat("${e.name}.desfase", v.coerceIn(0f, 6f)) }; recargar() }
    fun ponerTransparencia(e: Estilo, v: Int) { prefs.edit { putInt("${e.name}.transparencia", v.coerceIn(0, 100)) }; recargar() }
    fun ponerGrano(e: Estilo, g: Grano) { prefs.edit { putString("${e.name}.grano", g.name) }; recargar() }
    fun ponerRectas(e: Estilo, v: Boolean) { prefs.edit { putBoolean("${e.name}.rectas", v) }; recargar() }
    fun ponerAdorno(e: Estilo, d: Decoracion) { prefs.edit { putString("${e.name}.adorno", d.name) }; recargar() }

    /** Devuelve el estilo a como viene: colores, corrimiento, transparencia, grano, esquinas y adorno. */
    fun restablecer(e: Estilo) {
        prefs.edit {
            Ranura.entries.forEach { remove("${e.name}.${it.name}") }
            listOf("desfase", "transparencia", "grano", "rectas", "adorno").forEach { remove("${e.name}.$it") }
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

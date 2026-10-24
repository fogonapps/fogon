package io.github.fogonapps.identidad

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/*
 * La Apariencia: cómo quiere ver la app cada persona (ver docs/IDENTIDAD.md → Vidrio y puntos). Hay cuatro
 * temas: Grafito (casi negro con blanco, el que viene), el propio de la app (Gimnasio, Vino…), Claro y
 * Contraste. En cada uno se cambian solo el papel y las dos tintas: las letras, las tarjetas de vidrio y
 * los bordes salen de ellos (ver [Apariencia.colores]), así nada queda ilegible. Lo de comodidad (tamaño
 * del texto, menos movimiento, más contraste) vale para todos.
 */

enum class Estilo { GRAFITO, MARCA, CLARO, CONTRASTE }

/** Lo que va detrás de las pantallas: la luz suave del tema o nada. */
enum class Decoracion { LUZ, NADA }

/** Lo que la persona puede cambiar de color: el papel y las dos tintas. Lo demás se calcula. */
enum class Ranura { FONDO, TINTA_1, TINTA_2 }

/** Todos los colores de un tema, ya calculados para que se lean. [tarjetas] es vidrio: lleva transparencia. */
data class Colores(
    val fondo: Color,
    val tarjetas: Color,
    val texto: Color,
    val textoSuave: Color,
    val tinta1: Color,
    val sobreTinta1: Color,
    val tinta2: Color,
    val numero: Color,
    val bordes: Color,
    val brillo: Color,
)

/** Lo que la persona cambió en un tema. null = lo que trae el tema. */
data class AjustesEstilo(
    val colores: Map<Ranura, Color> = emptyMap(),
    /** Qué tan transparente es el vidrio de las tarjetas, de 0 (el del tema) a 100 (casi invisible). */
    val transparencia: Int? = null,
    val adorno: Decoracion? = null,
)

data class Apariencia(
    val estilo: Estilo = Estilo.GRAFITO,
    val ajustes: Map<Estilo, AjustesEstilo> = emptyMap(),
    /** 0 pequeño, 1 normal, 2 grande, 3 muy grande. */
    val tamanoTexto: Int = 1,
    val menosMovimiento: Boolean = false,
    val masContraste: Boolean = false,
) {
    fun de(e: Estilo = estilo) = ajustes[e] ?: AjustesEstilo()

    /** El papel o una tinta: lo que eligió la persona o lo que trae el tema. */
    fun color(paleta: Paleta, r: Ranura, e: Estilo = estilo): Color = de(e).colores[r] ?: e.colorBase(paleta, r)

    /**
     * Todos los colores del tema. Las letras se eligen claras u oscuras según el papel (las del tema si se
     * leen bien), las suaves se acercan al papel sin bajar de 4,6:1, las tarjetas son vidrio (blanco con poca
     * opacidad en los oscuros, blanco lechoso en los claros) y el texto sobre la primera tinta, el que más
     * contraste.
     */
    fun colores(paleta: Paleta, e: Estilo = estilo): Colores {
        val fondo = color(paleta, Ranura.FONDO, e)
        val t1 = color(paleta, Ranura.TINTA_1, e)
        val t2 = color(paleta, Ranura.TINTA_2, e)
        val oscuro = fondo.luminance() < 0.25f
        val texto = e.textoBase.takeIf { contraste(it, fondo) >= 7.0 } ?: legible(fondo)
        val claridad = 1f - transparencia(e) / 100f * 0.8f
        val tarjetas = when {
            e == Estilo.CONTRASTE -> fondo
            oscuro -> Color.White.copy(alpha = 0.06f * claridad)
            else -> Color.White.copy(alpha = 0.75f * claridad)
        }.let { v ->
            // En papeles de brillo medio el vidrio claro le quita contraste a las letras claras: entonces va oscuro.
            if (contraste(texto, v.compositeOver(fondo)) >= 4.5) v
            else (if (texto.luminance() > 0.5f) Color.Black else Color.White).copy(alpha = v.alpha.coerceAtLeast(0.06f))
        }
        // Las letras suaves se tienen que leer sobre el papel y sobre el vidrio.
        val vidrio = tarjetas.compositeOver(fondo)
        val textoSuave = (9 downTo 1).map { lerp(texto, fondo, it * 0.05f) }
            .firstOrNull { contraste(it, fondo) >= 4.6 && contraste(it, vidrio) >= 4.6 } ?: texto
        return Colores(
            fondo = fondo, tarjetas = tarjetas, texto = texto, textoSuave = textoSuave,
            tinta1 = t1, sobreTinta1 = legible(t1), tinta2 = t2,
            numero = if (contraste(t1, fondo) >= 3.0) t1 else texto,
            bordes = when {
                e == Estilo.CONTRASTE -> texto
                oscuro -> Color.White.copy(alpha = 0.11f)
                else -> CASI_NEGRO.copy(alpha = 0.09f)
            },
            brillo = when (e) {
                Estilo.GRAFITO -> Color.White.copy(alpha = 0.09f)
                Estilo.MARCA -> paleta.brillo.copy(alpha = 0.34f)
                Estilo.CLARO -> paleta.tinta.copy(alpha = 0.22f)
                Estilo.CONTRASTE -> Color.Transparent
            },
        )
    }
    fun transparencia(e: Estilo = estilo) = de(e).transparencia ?: 0
    fun adorno(e: Estilo = estilo) = de(e).adorno ?: e.decoracion

    /** Cuánto crece el texto: se aplica a todas las letras de la app. */
    val escalaTexto get() = listOf(0.88f, 1f, 1.15f, 1.3f)[tamanoTexto.coerceIn(0, 3)]
}

private val CASI_NEGRO = Color(0xFF19181A)
private val CASI_BLANCO = Color(0xFFF2F2F3)

/**
 * Letras que se leen sobre [fondo]: casi negras o casi blancas, las que más contrasten; si ni así llegan a
 * 4,5:1 (fondos de brillo medio), negro o blanco puros, que siempre llegan.
 */
private fun legible(fondo: Color): Color =
    listOf(CASI_NEGRO, CASI_BLANCO).maxBy { contraste(it, fondo) }.takeIf { contraste(it, fondo) >= 4.5 }
        ?: listOf(Color.Black, Color.White).maxBy { contraste(it, fondo) }

/**
 * El papel y las tintas que trae cada tema. Grafito y Claro son iguales en todas las apps salvo por la
 * segunda tinta (el acento de la app); el tema propio es el de la [Paleta]; Contraste toma de la app solo
 * su acento.
 */
fun Estilo.colorBase(p: Paleta, r: Ranura): Color = when (this) {
    Estilo.GRAFITO -> when (r) { Ranura.FONDO -> Color(0xFF0E0E10); Ranura.TINTA_1 -> Color(0xFFEDEDEF); Ranura.TINTA_2 -> Color(0xFF5E5E66) }
    Estilo.MARCA -> when (r) { Ranura.FONDO -> p.fondo; Ranura.TINTA_1 -> p.tinta; Ranura.TINTA_2 -> p.segunda }
    Estilo.CLARO -> when (r) { Ranura.FONDO -> Color(0xFFF3F2EE); Ranura.TINTA_1 -> CASI_NEGRO; Ranura.TINTA_2 -> p.tinta }
    Estilo.CONTRASTE -> when (r) { Ranura.FONDO -> Color.Black; Ranura.TINTA_1 -> Color.White; Ranura.TINTA_2 -> p.tinta }
}

/** Las letras que el tema usa si se leen bien sobre su papel. */
private val Estilo.textoBase get() = when (this) {
    Estilo.GRAFITO -> CASI_BLANCO
    Estilo.MARCA -> Color(0xFFF3F2EC)
    Estilo.CLARO -> CASI_NEGRO
    Estilo.CONTRASTE -> Color.White
}

val Estilo.decoracion get() = if (this == Estilo.CONTRASTE) Decoracion.NADA else Decoracion.LUZ

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
     * Versión 4 (vidrio y puntos, 24-10-2026): cuatro temas nuevos. Lo de cada tema se borra; el tema
     * elegido pasa al que más se le parece (Neutro → Claro, Noche → Grafito, Riso, Bosque y Durazno → el de
     * la app). Lo de comodidad se queda.
     */
    private fun migrar() {
        if (prefs.getInt("version", 1) >= 4) return
        val comodidad = setOf("estilo", "tamanoTexto", "menosMovimiento", "masContraste")
        val nuevo = mapOf(
            "NEUTRO" to Estilo.CLARO, "NOCHE" to Estilo.GRAFITO, "RISO" to Estilo.MARCA, "BOSQUE" to Estilo.MARCA,
            "DURAZNO" to Estilo.MARCA, "CHICLE" to Estilo.MARCA, "AURA" to Estilo.MARCA, "REBELDE" to Estilo.MARCA,
        )
        prefs.edit {
            prefs.all.keys.filter { it !in comodidad }.forEach { remove(it) }
            nuevo[prefs.getString("estilo", null)]?.let { putString("estilo", it.name) }
            putInt("version", 4)
        }
    }

    private fun leer(): Apariencia {
        val ajustes = Estilo.entries.associateWith { e ->
            AjustesEstilo(
                colores = Ranura.entries.mapNotNull { r ->
                    val k = "${e.name}.${r.name}"
                    if (prefs.contains(k)) r to Color(prefs.getInt(k, 0)) else null
                }.toMap(),
                transparencia = prefs.getInt("${e.name}.transparencia", -1).takeIf { it >= 0 },
                adorno = prefs.getString("${e.name}.adorno", null)?.let { g -> Decoracion.entries.firstOrNull { it.name == g } },
            )
        }.filterValues { it != AjustesEstilo() }
        return Apariencia(
            estilo = prefs.getString("estilo", null)?.let { s -> Estilo.entries.firstOrNull { it.name == s } } ?: Estilo.GRAFITO,
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
    fun ponerAdorno(e: Estilo, d: Decoracion) { prefs.edit { putString("${e.name}.adorno", d.name) }; recargar() }

    /** Devuelve el tema a como viene: colores, transparencia y luz. */
    fun restablecer(e: Estilo) {
        prefs.edit {
            Ranura.entries.forEach { remove("${e.name}.${it.name}") }
            listOf("transparencia", "adorno").forEach { remove("${e.name}.$it") }
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

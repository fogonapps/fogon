package io.github.fogonapps.perfil

import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.fogonapps.identidad.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Los fondos y marcos del perfil, como los de Steam. Cada fondo es una escena dibujada por Fogón (arte propio, sin
 * licencias de terceros) con su paleta: solo dentro de la página del perfil, los colores son los del fondo y no los
 * del tema de la app. Se compran con chispas en la tienda; el catálogo y los precios los guarda el servidor.
 */

/** La paleta de un fondo: el color de base, los paneles (semitransparentes), el texto y el acento. */
data class EstiloPerfil(val base: Color, val panel: Color, val texto: Color, val textoSuave: Color, val acento: Color, val sobreAcento: Color, val linea: Color)

/**
 * Una colección del catálogo oficial de Fabs: dos banners, un banner animado, un marco y un fondo con el mismo tema.
 * Los ids van sin prefijo; [articulos] da los que se compran en la tienda ("banner:…", "marco:…" y el fondo).
 */
class ColeccionFabs(val id: String, val nombre: Int, val lema: Int, val banners: List<String>, val animado: String, val marco: String, val fondo: String) {
    val articulos: List<String> get() = banners.map { "banner:$it" } + "banner:$animado" + "marco:$marco" + fondo
}

object TemasPerfil {
    val EXTRAS = (1..3).map { "escaparate:extra$it" }
    const val PESOS_POR_CHISPA = 10

    /** El catálogo oficial de Fabs. En Fabs solo se vende esto; en las otras apps no aparece. */
    val COLECCIONES = listOf(
        ColeccionFabs("negro", R.string.coleccion_negro, R.string.coleccion_negro_lema, listOf("negro-persiana", "negro-hotel"), "negro-ventilador", "negro-diafragma", "negro-estreno"),
        ColeccionFabs("videoclub", R.string.coleccion_videoclub, R.string.coleccion_videoclub_lema, listOf("videoclub-estanteria", "videoclub-rebobinar"), "videoclub-abierto", "videoclub-rebobine", "videoclub-pasillo"),
        ColeccionFabs("biblioteca", R.string.coleccion_biblioteca, R.string.coleccion_biblioteca_lema, listOf("biblioteca-lomos", "biblioteca-marmolado"), "biblioteca-polvo", "biblioteca-exlibris", "biblioteca-sala"),
        ColeccionFabs("vinilo", R.string.coleccion_vinilo, R.string.coleccion_vinilo_lema, listOf("vinilo-surco", "vinilo-sello"), "vinilo-giradiscos", "vinilo-disco", "vinilo-funda"),
        ColeccionFabs("arcade", R.string.coleccion_arcade, R.string.coleccion_arcade_lema, listOf("arcade-puntajes", "arcade-vector"), "arcade-hiperespacio", "arcade-fosforo", "arcade-moqueta"),
    )
    /** Los banners gratis de Fabs: sobrios, de papel, grano y tinta (en las otras apps siguen los de siempre). */
    val GRATIS_FABS = listOf("fabs-papel", "fabs-grano", "fabs-tinta")

    val BANNERS_ANIMADOS = listOf("deriva", "ascenso", "estelar", "olas", "lluvia") + COLECCIONES.map { it.animado }
    val BANNERS = listOf("papel", "riso-rosa", "riso-azul", "riso-amarillo", "puntos", "chispas", "ranita", "forzudo", "nube", "cajita", "noche", "pasto").associateWith { 0 } +
        mapOf("neon" to 1200, "videoclub" to 1000, "arcade" to 1000, "plastilina" to 1200, "aero" to 800, "pizzeria" to 800) +
        listOf("deriva", "ascenso", "estelar", "olas", "lluvia").associateWith { 600 } + GRATIS_FABS.associateWith { 0 } +
        mapOf("negro-persiana" to 1000, "negro-hotel" to 1200, "videoclub-estanteria" to 1000, "videoclub-rebobinar" to 900,
            "biblioteca-lomos" to 1100, "biblioteca-marmolado" to 1000, "vinilo-surco" to 1200, "vinilo-sello" to 900,
            "arcade-puntajes" to 1000, "arcade-vector" to 1100,
            "negro-ventilador" to 1800, "videoclub-abierto" to 1600, "biblioteca-polvo" to 1600, "vinilo-giradiscos" to 2000, "arcade-hiperespacio" to 1800)
    /** Referencias directas (no por nombre) para que la versión publicada no descarte las imágenes. */
    val RECURSOS_BANNER = mapOf(
        "oficial" to R.drawable.banner_oficial,
        "papel" to R.drawable.banner_papel, "riso-rosa" to R.drawable.banner_riso_rosa, "riso-azul" to R.drawable.banner_riso_azul,
        "riso-amarillo" to R.drawable.banner_riso_amarillo, "puntos" to R.drawable.banner_puntos, "chispas" to R.drawable.banner_chispas,
        "ranita" to R.drawable.banner_ranita, "forzudo" to R.drawable.banner_forzudo, "nube" to R.drawable.banner_nube,
        "cajita" to R.drawable.banner_cajita, "noche" to R.drawable.banner_noche, "pasto" to R.drawable.banner_pasto,
        "neon" to R.drawable.banner_neon, "videoclub" to R.drawable.banner_videoclub, "arcade" to R.drawable.banner_arcade,
        "plastilina" to R.drawable.banner_plastilina, "aero" to R.drawable.banner_aero, "pizzeria" to R.drawable.banner_pizzeria,
        "fabs-papel" to R.drawable.banner_fabs_papel, "fabs-grano" to R.drawable.banner_fabs_grano, "fabs-tinta" to R.drawable.banner_fabs_tinta,
        "negro-persiana" to R.drawable.banner_negro_persiana, "negro-hotel" to R.drawable.banner_negro_hotel,
        "videoclub-estanteria" to R.drawable.banner_videoclub_estanteria, "videoclub-rebobinar" to R.drawable.banner_videoclub_rebobinar,
        "biblioteca-lomos" to R.drawable.banner_biblioteca_lomos, "biblioteca-marmolado" to R.drawable.banner_biblioteca_marmolado,
        "vinilo-surco" to R.drawable.banner_vinilo_surco, "vinilo-sello" to R.drawable.banner_vinilo_sello,
        "arcade-puntajes" to R.drawable.banner_arcade_puntajes, "arcade-vector" to R.drawable.banner_arcade_vector,
    )
    /** Los marcos que son imagen (el catálogo de Fabs); los demás se dibujan con código. */
    val RECURSOS_MARCO = mapOf(
        "negro-diafragma" to R.drawable.marco_negro_diafragma, "videoclub-rebobine" to R.drawable.marco_videoclub_rebobine,
        "biblioteca-exlibris" to R.drawable.marco_biblioteca_exlibris, "vinilo-disco" to R.drawable.marco_vinilo_disco,
        "arcade-fosforo" to R.drawable.marco_arcade_fosforo,
    )
    /** Los fondos que son imagen (el catálogo de Fabs): la escena de arriba, alto = ancho × 2,1. */
    val RECURSOS_FONDO = mapOf(
        "negro-estreno" to R.drawable.fondo_negro_estreno, "videoclub-pasillo" to R.drawable.fondo_videoclub_pasillo,
        "biblioteca-sala" to R.drawable.fondo_biblioteca_sala, "vinilo-funda" to R.drawable.fondo_vinilo_funda,
        "arcade-moqueta" to R.drawable.fondo_arcade_moqueta,
    )

    val FONDOS = listOf("nieve", "carmesi", "niebla", "ojos", "atardecer", "ciudad", "selva", "cosmos") + COLECCIONES.map { it.fondo }
    val MARCOS = listOf("doble", "llamas", "pixel", "estrellas", "espinas", "luna") + COLECCIONES.map { it.marco }
    /** Los precios en chispas (los mismos del servidor, que es el que cobra). */
    val PRECIOS = mapOf("nieve" to 1200, "carmesi" to 1200, "niebla" to 1000, "ojos" to 900, "atardecer" to 1000, "ciudad" to 1500, "selva" to 1000, "cosmos" to 1500,
        "marco:doble" to 400, "marco:llamas" to 800, "marco:pixel" to 600, "marco:estrellas" to 700, "marco:espinas" to 800, "marco:luna" to 600,
        "negro-estreno" to 1200, "videoclub-pasillo" to 1200, "biblioteca-sala" to 1200, "vinilo-funda" to 1000, "arcade-moqueta" to 1200) +
        COLECCIONES.associate { "marco:${it.marco}" to 500 } +
        BANNERS.mapKeys { "banner:${it.key}" } + EXTRAS.associateWith { 1000 } + mapOf("portada:propia" to 2500, "portada:animada" to 5000)

    /** Los ids (sin prefijo) del catálogo de Fabs, con sus gratis. */
    private val DE_FABS: Set<String> = (COLECCIONES.flatMap { it.banners + it.animado + it.marco + it.fondo } + GRATIS_FABS).toSet()
    fun deFabs(id: String): Boolean = id.removePrefix("banner:").removePrefix("marco:").substringBefore('~') in DE_FABS
    /**
     * Si un artículo se ofrece en la tienda de esta app: con el diseño nuevo (Fabs) solo el catálogo de Fabs; en las
     * demás, solo lo de siempre. Lo ya comprado se sigue pudiendo usar en todas.
     */
    fun seOfrece(id: String): Boolean = if (Trama.apagada) deFabs(id) else !deFabs(id)
    /** Los banners riso gratis y los animados de puntos se pueden teñir; los del catálogo de Fabs van como son. */
    fun admiteColor(id: String): Boolean = (BANNERS[id] == 0 || id in BANNERS_ANIMADOS) && !deFabs(id)
    /** La colección de la vitrina: cambia cada semana (lunes a domingo, hora universal). */
    fun coleccionDeLaSemana(ahora: Long = System.currentTimeMillis()): ColeccionFabs =
        COLECCIONES[Math.floorMod((ahora / 86_400_000L + 3) / 7, COLECCIONES.size.toLong()).toInt()]
    fun coleccionDe(id: String): ColeccionFabs? {
        val base = id.removePrefix("banner:").removePrefix("marco:").substringBefore('~')
        return COLECCIONES.firstOrNull { base in it.banners || base == it.animado || base == it.marco || base == it.fondo }
    }

    fun estilo(tema: String): EstiloPerfil? = when (tema) {
        "nieve" -> EstiloPerfil(Color(0xFF0E1A27), Color(0xA61A2B3D), Color(0xFFE8F1FA), Color(0xFF9DB4CC), Color(0xFF7FC4FF), Color(0xFF0E1A27), Color(0x557FA8D0))
        "carmesi" -> EstiloPerfil(Color(0xFF2A0405), Color(0xA63D0709), Color(0xFFFBE9E4), Color(0xFFD39A92), Color(0xFFFF3B30), Color(0xFF1A0203), Color(0x66FF6B5E))
        "niebla" -> EstiloPerfil(Color(0xFFBFC3C2), Color(0x99E9ECEA), Color(0xFF1E2321), Color(0xFF555C59), Color(0xFF2E3532), Color(0xFFF2F4F3), Color(0x553A403D))
        "ojos" -> EstiloPerfil(Color(0xFF050505), Color(0xB3121212), Color(0xFFF5F5F5), Color(0xFFA0A0A0), Color(0xFFFFFFFF), Color(0xFF050505), Color(0x55FFFFFF))
        "atardecer" -> EstiloPerfil(Color(0xFF2B1236), Color(0xA63E1C4A), Color(0xFFFFF1E6), Color(0xFFE6B8A8), Color(0xFFFF8A3D), Color(0xFF2B1236), Color(0x66FFB37A))
        "ciudad" -> EstiloPerfil(Color(0xFF0B0D1A), Color(0xA6151A33), Color(0xFFEDEBFF), Color(0xFFA7A3D6), Color(0xFFB98CFF), Color(0xFF0B0D1A), Color(0x55B98CFF))
        "selva" -> EstiloPerfil(Color(0xFF0B2016), Color(0xA6123222), Color(0xFFE9F7EE), Color(0xFF9CC7AD), Color(0xFF5FDB8F), Color(0xFF0B2016), Color(0x555FDB8F))
        "cosmos" -> EstiloPerfil(Color(0xFF05040F), Color(0xA612102A), Color(0xFFF0EEFF), Color(0xFFA9A4D9), Color(0xFFFFD166), Color(0xFF05040F), Color(0x55FFD166))
        "negro-estreno" -> EstiloPerfil(Color(0xFF0E0E0F), Color(0xA61A1A1D), Color(0xFFEDEDED), Color(0xFF9C9C9C), Color(0xFFE8D6B0), Color(0xFF0E0E0F), Color(0x40FFFFFF))
        "videoclub-pasillo" -> EstiloPerfil(Color(0xFF0D0C12), Color(0xA6191722), Color(0xFFF4EEF6), Color(0xFFB9A9BE), Color(0xFFFF4FA0), Color(0xFF0D0C12), Color(0x55FF4FA0))
        "biblioteca-sala" -> EstiloPerfil(Color(0xFF120D0A), Color(0xA6231A13), Color(0xFFF3EADB), Color(0xFFC2AE92), Color(0xFFDDB15C), Color(0xFF120D0A), Color(0x55DDB15C))
        "vinilo-funda" -> EstiloPerfil(Color(0xFFEEE8DA), Color(0xD9F7F3EA), Color(0xFF141414), Color(0xFF5E5A52), Color(0xFFE2512A), Color(0xFFFFFFFF), Color(0x33141414))
        "arcade-moqueta" -> EstiloPerfil(Color(0xFF0E0C1E), Color(0xB3161430), Color(0xFFF3F0FF), Color(0xFFABA6CF), Color(0xFF3CE6FF), Color(0xFF0E0C1E), Color(0x553CE6FF))
        else -> null
    }

    @Composable
    fun nombre(tema: String): String = stringResource(when (tema) {
        "portada:animada" -> R.string.portada_animada; "portada:propia" -> R.string.portada_propia; "nieve" -> R.string.tema_nieve; "carmesi" -> R.string.tema_carmesi; "niebla" -> R.string.tema_niebla; "ojos" -> R.string.tema_ojos
        "atardecer" -> R.string.tema_atardecer; "ciudad" -> R.string.tema_ciudad; "selva" -> R.string.tema_selva; "cosmos" -> R.string.tema_cosmos
        "doble" -> R.string.marco_doble; "llamas" -> R.string.marco_llamas; "pixel" -> R.string.marco_pixel; "estrellas" -> R.string.marco_estrellas
        "espinas" -> R.string.marco_espinas; "luna" -> R.string.marco_luna
        "negro-estreno" -> R.string.fondo_negro_estreno; "videoclub-pasillo" -> R.string.fondo_videoclub_pasillo; "biblioteca-sala" -> R.string.fondo_biblioteca_sala
        "vinilo-funda" -> R.string.fondo_vinilo_funda; "arcade-moqueta" -> R.string.fondo_arcade_moqueta
        "negro-diafragma" -> R.string.marco_negro_diafragma; "videoclub-rebobine" -> R.string.marco_videoclub_rebobine; "biblioteca-exlibris" -> R.string.marco_biblioteca_exlibris
        "vinilo-disco" -> R.string.marco_vinilo_disco; "arcade-fosforo" -> R.string.marco_arcade_fosforo
        else -> R.string.tema_clasico
    })
}

/** El perfil con los colores de su fondo (solo dentro del perfil). */
fun Fogon.conEstilo(e: EstiloPerfil): Fogon = copy(
    fondo = e.base, superficie = e.panel, texto = e.texto, textoSuave = e.textoSuave, linea = e.linea, acentoTexto = e.acento,
    tinta1 = e.acento, sobreTinta1 = e.sobreAcento, tinta2 = e.textoSuave, pildora = e.texto, sobrePildora = e.base,
    barra = e.base, brillo = Color.Transparent, luz = null,
)

/** La escena de un fondo, a todo lo que ocupe; con movimiento (nieve, lluvia, estrellas) salvo con «menos movimiento». */
@Composable
fun FondoTemaPerfil(tema: String, modifier: Modifier = Modifier) {
    val e = TemasPerfil.estilo(tema) ?: return
    TemasPerfil.RECURSOS_FONDO[tema]?.let { FondoImagen(it, e.base, modifier); return }
    val quieto = FogonTema.actual.menosMovimiento
    val t = if (quieto) 0f else rememberInfiniteTransition(label = "fondo").animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "t").value
    Canvas(modifier) { drawRect(e.base); escena(tema, e, t) }
}

/** Un fondo del catálogo de Fabs: la escena arriba a todo lo ancho y, abajo, se funde con el color de base. */
@Composable
private fun FondoImagen(recurso: Int, base: Color, modifier: Modifier) {
    val imagen = androidx.compose.ui.graphics.ImageBitmap.imageResource(recurso)
    Canvas(modifier) {
        drawRect(base)
        val alto = size.width * imagen.height / imagen.width
        drawImage(imagen, dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), alto.toInt()),
            filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium)
        drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, base), startY = alto * 0.86f, endY = alto),
            Offset(0f, alto * 0.86f), Size(size.width, alto * 0.14f + 1f))
    }
}

// Sin lunas, soles ni planetas sueltos: un círculo arriba se confunde con un botón. La escena es el cielo, el paisaje y
// lo que se mueve (nieve, chispas, estrellas).
private fun DrawScope.escena(tema: String, e: EstiloPerfil, t: Float) {
    val w = size.width; val h = size.height
    // La escena se dibuja en la parte de arriba (como un fondo de Steam) y abajo sigue el color de base.
    val alto = minOf(h, w * 2.1f)
    fun sierra(y0: Float, amp: Float, picos: Int, color: Color, semilla: Int) {
        val al = java.util.Random(semilla.toLong())
        val p = Path().apply {
            moveTo(0f, alto); lineTo(0f, y0)
            val paso = w / picos
            for (i in 0..picos) { lineTo(i * paso, y0 - al.nextFloat() * amp); lineTo(i * paso + paso / 2, y0 - amp * (0.3f + al.nextFloat() * 0.7f)) }
            lineTo(w, alto); close()
        }
        drawPath(p, color)
    }
    fun pinos(y0: Float, n: Int, alturaMax: Float, color: Color, semilla: Int) {
        val al = java.util.Random(semilla.toLong())
        drawRect(color, Offset(0f, y0), Size(w, alto - y0))
        repeat(n) {
            val x = al.nextFloat() * w; val a = alturaMax * (0.5f + al.nextFloat() * 0.5f); val ancho = a * 0.32f
            val p = Path().apply { moveTo(x, y0 - a); lineTo(x - ancho, y0 + 2f); lineTo(x + ancho, y0 + 2f); close() }
            drawPath(p, color)
        }
    }
    fun particulas(n: Int, color: Color, tam: Float, caida: Float, viento: Float, semilla: Int, linea: Boolean = false) {
        val al = java.util.Random(semilla.toLong())
        repeat(n) {
            val x0 = al.nextFloat() * w; val y0 = al.nextFloat() * h; val v = 0.6f + al.nextFloat() * 0.8f
            val y = (y0 + t * caida * h * v) % h; val x = (x0 + t * viento * w * v + w) % w
            if (linea) drawLine(color, Offset(x, y), Offset(x - viento * 18f, y + 22f), 1.5f) else drawCircle(color, tam * v, Offset(x, y))
        }
    }
    when (tema) {
        "nieve" -> {
            sierra(alto * 0.45f, alto * 0.18f, 5, Color(0xFF203448), 3)
            pinos(alto * 0.62f, 18, alto * 0.22f, Color(0xFF16263A), 5)
            pinos(alto * 0.78f, 14, alto * 0.26f, Color(0xFF0E1A27), 8)
            particulas(140, Color(0xA6FFFFFF), 2.2f, 3f, 0.4f, 11)
        }
        "carmesi" -> {
            for (i in 0 until 6) drawRect(Color(0xFF8B0A0E).copy(alpha = 0.18f + i * 0.08f), Offset(0f, alto * i / 6f), Size(w, alto / 6f))
            sierra(alto * 0.68f, alto * 0.3f, 7, Color(0xFF120203), 21)
            particulas(40, Color(0x88FF6B5E), 1.6f, -1.5f, 0.2f, 4)
        }
        "niebla" -> {
            sierra(alto * 0.42f, alto * 0.22f, 4, Color(0xFF9EA4A2), 7)
            drawRect(Color(0x55E9ECEA), Offset(0f, alto * 0.38f), Size(w, alto * 0.08f))
            sierra(alto * 0.58f, alto * 0.2f, 6, Color(0xFF7D8582), 9)
            drawRect(Color(0x55E9ECEA), Offset(0f, alto * 0.55f), Size(w, alto * 0.08f))
            sierra(alto * 0.76f, alto * 0.16f, 9, Color(0xFF4A524F), 13)
            repeat(5) { i -> val x = (w * (0.55f + i * 0.06f) + t * w * 0.4f) % w; val y = alto * (0.12f + (i % 2) * 0.03f)
                drawPath(Path().apply { moveTo(x - 10f, y); quadraticTo(x - 5f, y - 6f, x, y); quadraticTo(x + 5f, y - 6f, x + 10f, y) }, Color(0xFF2E3532), style = Stroke(2.5f)) }
            particulas(70, Color(0x553A403D), 1f, 4f, -0.1f, 2, linea = true)
        }
        "ojos" -> {
            val al = java.util.Random(6)
            repeat(14) {
                val c = Offset(al.nextFloat() * w, al.nextFloat() * alto); val r = w * (0.08f + al.nextFloat() * 0.1f)
                val ojo = Path().apply { moveTo(c.x - r, c.y); quadraticTo(c.x, c.y - r * 0.9f, c.x + r, c.y); quadraticTo(c.x, c.y + r * 0.9f, c.x - r, c.y); close() }
                drawPath(ojo, Color(0xFFEDEDED))
                val mira = sin((t * 2 * PI + it).toFloat()) * r * 0.2f
                drawCircle(Color(0xFF050505), r * 0.32f, Offset(c.x + mira, c.y))
                drawCircle(Color(0xFFEDEDED), r * 0.08f, Offset(c.x + mira + r * 0.1f, c.y - r * 0.1f))
            }
        }
        "atardecer" -> {
            for (i in 0 until 8) drawRect(Color(0xFFFF7A45).copy(alpha = 0.06f + i * 0.05f), Offset(0f, alto * 0.06f * i), Size(w, alto * 0.06f))
            sierra(alto * 0.62f, alto * 0.08f, 3, Color(0xFF4A1F4F), 5)
            sierra(alto * 0.74f, alto * 0.06f, 4, Color(0xFF2B1236), 9)
        }
        "ciudad" -> {
            particulas(60, Color(0x99FFFFFF), 1f, 0f, 0f, 3)
            val al = java.util.Random(12)
            var x = 0f
            while (x < w) {
                val bw = w * (0.06f + al.nextFloat() * 0.1f); val bh = alto * (0.2f + al.nextFloat() * 0.45f); val y = alto * 0.85f - bh
                drawRect(Color(0xFF151A33), Offset(x, y), Size(bw - 3f, alto - y))
                var wy = y + 10f
                while (wy < alto * 0.85f - 10f) { var wx = x + 6f; while (wx < x + bw - 12f) { if (al.nextFloat() < 0.35f) drawRect(Color(0xA6B98CFF), Offset(wx, wy), Size(5f, 7f)); wx += 12f }; wy += 16f }
                x += bw
            }
            drawRect(Color(0xFF0B0D1A), Offset(0f, alto * 0.85f), Size(w, alto * 0.15f))
            particulas(120, Color(0x66B9C7FF), 1f, 6f, -0.15f, 9, linea = true)
        }
        "selva" -> {
            val al = java.util.Random(5)
            repeat(60) {
                val c = Offset(al.nextFloat() * w, al.nextFloat() * alto); val l = w * (0.08f + al.nextFloat() * 0.14f)
                val ang = al.nextFloat() * 360f + sin((t * 2 * PI + it).toFloat()) * 4f
                rotate(ang, c) {
                    drawPath(Path().apply { moveTo(c.x, c.y); quadraticTo(c.x + l * 0.5f, c.y - l * 0.35f, c.x + l, c.y); quadraticTo(c.x + l * 0.5f, c.y + l * 0.35f, c.x, c.y); close() },
                        listOf(Color(0xFF1E5A3A), Color(0xFF2E7D4F), Color(0xFF14432B))[it % 3])
                }
            }
            particulas(30, Color(0x885FDB8F), 1.4f, -0.5f, 0.3f, 8)
        }
        "cosmos" -> {
            val al = java.util.Random(4)
            repeat(160) { val c = Offset(al.nextFloat() * w, al.nextFloat() * h); val b = 0.4f + 0.6f * ((sin((t * 2 * PI * 3 + it).toFloat()) + 1) / 2)
                drawCircle(Color.White.copy(alpha = b * 0.8f), 0.6f + al.nextFloat() * 1.8f, c) }
        }
    }
}

/** El marco de la foto de perfil, dibujado alrededor de una foto de [tamano] (va encima, centrado). */
@Composable
fun MarcoFoto(marco: String, tamano: Dp, modifier: Modifier = Modifier) {
    if (Regex("m[1-9][0-9]*").matches(marco)) {
        ArteMercado(marco.drop(1), modifier.size(tamano + 22.dp), androidx.compose.ui.layout.ContentScale.Fit)
        return
    }
    TemasPerfil.RECURSOS_MARCO[marco]?.let {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(it), null, modifier.size(tamano + 22.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit)
        return
    }
    if (marco !in TemasPerfil.MARCOS) return
    val f = FogonTema.actual
    val acento = f.tinta1; val texto = f.texto
    val quieto = f.menosMovimiento
    val giro = if (quieto) 0f else rememberInfiniteTransition(label = "marco").animateFloat(0f, 360f, infiniteRepeatable(tween(20000, easing = LinearEasing)), label = "g").value
    Canvas(modifier.size(tamano + 22.dp)) {
        val c = center; val r = tamano.toPx() / 2
        when (marco) {
            "doble" -> { drawCircle(acento, r + 4.dp.toPx(), c, style = Stroke(3.dp.toPx())); drawCircle(texto, r + 9.dp.toPx(), c, style = Stroke(1.5.dp.toPx())) }
            "llamas" -> rotate(giro / 4, c) {
                val n = 18
                for (i in 0 until n) {
                    val a = (i * 2 * PI / n).toFloat(); val a2 = a + (PI / n).toFloat(); val a3 = a + (2 * PI / n).toFloat()
                    val p = Path().apply {
                        moveTo(c.x + cos(a) * (r + 2f), c.y + sin(a) * (r + 2f))
                        lineTo(c.x + cos(a2) * (r + 10.dp.toPx() + (i % 3) * 3f), c.y + sin(a2) * (r + 10.dp.toPx() + (i % 3) * 3f))
                        lineTo(c.x + cos(a3) * (r + 2f), c.y + sin(a3) * (r + 2f)); close()
                    }
                    drawPath(p, if (i % 2 == 0) acento else acento.copy(alpha = 0.6f))
                }
            }
            "pixel" -> {
                val lado = r * 2 + 12.dp.toPx(); val s = 4.dp.toPx(); val o = Offset(c.x - lado / 2, c.y - lado / 2)
                var x = 0f; var i = 0
                while (x < lado) { drawRect(if (i % 2 == 0) acento else texto, Offset(o.x + x, o.y), Size(s, s)); drawRect(if (i % 2 == 0) texto else acento, Offset(o.x + x, o.y + lado - s), Size(s, s))
                    drawRect(if (i % 2 == 0) acento else texto, Offset(o.x, o.y + x), Size(s, s)); drawRect(if (i % 2 == 0) texto else acento, Offset(o.x + lado - s, o.y + x), Size(s, s)); x += s; i++ }
            }
            "estrellas" -> {
                drawCircle(acento, r + 3.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                rotate(giro, c) { for (i in 0 until 6) { val a = (i * PI / 3).toFloat(); estrella(Offset(c.x + cos(a) * (r + 7.dp.toPx()), c.y + sin(a) * (r + 7.dp.toPx())), 5.dp.toPx(), acento) } }
            }
            "espinas" -> {
                drawCircle(texto, r + 3.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                for (i in 0 until 24) { val a = (i * 2 * PI / 24).toFloat(); val l = if (i % 2 == 0) 8.dp.toPx() else 5.dp.toPx()
                    drawLine(texto, Offset(c.x + cos(a) * (r + 3.dp.toPx()), c.y + sin(a) * (r + 3.dp.toPx())), Offset(c.x + cos(a) * (r + 3.dp.toPx() + l), c.y + sin(a) * (r + 3.dp.toPx() + l)), 2.dp.toPx()) }
            }
            "luna" -> {
                drawCircle(acento.copy(alpha = 0.5f), r + 4.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                val m = Offset(c.x + r * 0.72f, c.y - r * 0.72f); val mr = 10.dp.toPx()
                drawCircle(acento, mr, m); drawCircle(f.fondo, mr * 0.85f, Offset(m.x + mr * 0.45f, m.y - mr * 0.3f))
            }
        }
    }
}

private fun DrawScope.estrella(c: Offset, r: Float, color: Color) {
    val p = Path()
    for (i in 0 until 10) { val a = (i * PI / 5 - PI / 2).toFloat(); val rr = if (i % 2 == 0) r else r * 0.45f
        if (i == 0) p.moveTo(c.x + cos(a) * rr, c.y + sin(a) * rr) else p.lineTo(c.x + cos(a) * rr, c.y + sin(a) * rr) }
    p.close(); drawPath(p, color)
}

/** Una muestra de un fondo para la tienda: su escena con un panel y una foto en chico. */
@Composable
fun MuestraTema(tema: String, modifier: Modifier = Modifier) {
    val e = TemasPerfil.estilo(tema)
    val f = FogonTema.actual
    Box(modifier.aspectRatio(0.8f).clip(forma(16.dp)).tarjetaFogon(forma(16.dp), sombra = false)) {
        if (e != null) FondoTemaPerfil(tema, Modifier.matchParentSize())
        Box(Modifier.align(Alignment.BottomStart).padding(8.dp).clip(forma(10.dp))
            .then(if (e != null) Modifier.background(e.panel) else Modifier).padding(6.dp)) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(e?.texto ?: f.linea))
        }
    }
}

/** Una muestra de un marco para la tienda: una foto vacía con el marco puesto. */
@Composable
fun MuestraMarco(marco: String, modifier: Modifier = Modifier) {
    val f = FogonTema.actual
    Box(modifier.aspectRatio(1.3f).clip(forma(16.dp)).tarjetaFogon(forma(16.dp), sombra = false), contentAlignment = Alignment.Center) {
        Box(Modifier.size(56.dp).background(f.superficie, CircleShape).border(f.borde, f.linea, CircleShape))
        MarcoFoto(marco, 56.dp)
    }
}

/** El arte del banner; si el id no existe, papel con puntos de la identidad. */
@Composable
fun BannerPerfil(id: String, modifier: Modifier = Modifier) {
    if (Regex("m[1-9][0-9]*").matches(id)) { ArteMercado(id.drop(1), modifier); return }
    val base = id.substringBefore('~')
    val tinta = tintasBanner[id.substringAfter('~', "")]?.first
    if (base in ANIMADOS_FABS) { BannerAnimadoFabs(base, modifier); return }
    if (base in TemasPerfil.BANNERS_ANIMADOS) {
        BannerAnimado(base, tinta, modifier)
        return
    }
    val recurso = TemasPerfil.RECURSOS_BANNER[base] ?: 0
    val f = FogonTema.actual
    if (base == "puntos" && tinta != null) Box(modifier.background(f.fondo).haloPuntos(tinta))
    else if (recurso != 0) androidx.compose.foundation.Image(
        androidx.compose.ui.res.painterResource(recurso), null, modifier,
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        colorFilter = tinta?.let { androidx.compose.ui.graphics.ColorFilter.tint(it, androidx.compose.ui.graphics.BlendMode.Color) },
    )
    else Box(modifier.background(f.fondo).haloPuntos(f.textoSuave.copy(alpha = 0.3f)))
}

internal fun bannersDisponibles(reclamados: List<String>, insignia: String): List<String> = buildList {
    addAll(TemasPerfil.BANNERS.filter { (id, precio) -> precio == 0 && TemasPerfil.seOfrece(id) || "banner:$id" in reclamados }.keys)
    addAll(reclamados.filter { Regex("banner:m[1-9][0-9]*").matches(it) }.map { it.removePrefix("banner:") })
    if (insignia == "fogon") add("oficial")
}

/** Banners gratis y adquiridos, con una marca en los comprados. */
@Composable
fun BannersGratis(
    onElegir: (String) -> Unit, reclamados: List<String> = emptyList(), insignia: String = "",
    portada: String = "", guardarAlElegir: Boolean = true,
) {
    var seleccion by androidx.compose.runtime.saveable.rememberSaveable(portada) { mutableStateOf(portada) }
    val idActivo = seleccion.substringBefore('~').removePrefix("banner:")
    val admiteColor = TemasPerfil.admiteColor(idActivo)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        bannersDisponibles(reclamados, insignia).forEach { id ->
            androidx.compose.material3.TextButton(onClick = {
                val valor = if (id == idActivo) seleccion else "banner:$id"
                seleccion = valor
                if (!guardarAlElegir || !TemasPerfil.admiteColor(id)) onElegir(valor)
            }, modifier = Modifier.width(128.dp)) {
                Column {
                    val miniatura = Modifier.fillMaxWidth().height(40.dp)
                    val f = FogonTema.actual
                    BannerPerfil(if (id == idActivo) seleccion.removePrefix("banner:") else id, if (id == "oficial") miniatura.border(f.borde, f.textoSuave.copy(alpha = 0.35f)) else miniatura)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if ("banner:$id" in reclamados) IconoRiso(IconosFogon.Chulo, stringResource(R.string.perfil_articulo_comprado), tamano = 16.dp)
                        Text(if (id.startsWith("m") && id.drop(1).toLongOrNull() != null) stringResource(R.string.perfil_banner_comprado) else nombreBanner(id), color = FogonTema.actual.texto)
                    }
                }
            }
        }
    }
    if (admiteColor) {
        val f = FogonTema.actual
        val elegido = seleccion.substringAfter('~', "original")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            (listOf("original") + tintasBanner.keys).forEach { color ->
                val muestra = tintasBanner[color]
                val nombre = stringResource(muestra?.second ?: R.string.banner_color_original)
                val elegir = {
                    seleccion = "banner:$idActivo~$color"
                    if (!guardarAlElegir) onElegir(seleccion)
                }
                if (muestra == null) androidx.compose.material3.TextButton(onClick = elegir) { Text(nombre, color = f.texto) }
                else Box(Modifier.size(48.dp)
                    .selectable(elegido == color, onClick = elegir)
                    .semantics { contentDescription = nombre }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(28.dp).background(muestra.first, CircleShape).border(f.borde, f.textoSuave, CircleShape))
                    if (elegido == color) IconoRiso(IconosFogon.Chulo, null, tamano = 16.dp, tinta = Tintas.Tinta)
                }
            }
        }
        if (guardarAlElegir) BotonSecundario(stringResource(R.string.banner_usar), { onElegir(seleccion) })
    }
}

@Composable
fun nombreBanner(id: String): String = stringResource(when (id) {
    "deriva" -> R.string.banner_deriva
    "ascenso" -> R.string.banner_ascenso
    "estelar" -> R.string.banner_estelar
    "olas" -> R.string.banner_olas
    "lluvia" -> R.string.banner_lluvia
    "oficial" -> R.string.banner_oficial
    "papel" -> R.string.banner_papel
    "riso-rosa" -> R.string.banner_riso_rosa
    "riso-azul" -> R.string.banner_riso_azul
    "riso-amarillo" -> R.string.banner_riso_amarillo
    "puntos" -> R.string.banner_puntos
    "chispas" -> R.string.banner_chispas
    "ranita" -> R.string.banner_ranita
    "forzudo" -> R.string.banner_forzudo
    "nube" -> R.string.banner_nube
    "cajita" -> R.string.banner_cajita
    "noche" -> R.string.banner_noche
    "pasto" -> R.string.banner_pasto
    "neon" -> R.string.banner_neon
    "videoclub" -> R.string.banner_videoclub
    "arcade" -> R.string.banner_arcade
    "plastilina" -> R.string.banner_plastilina
    "aero" -> R.string.banner_aero
    "pizzeria" -> R.string.banner_pizzeria
    "fabs-papel" -> R.string.banner_fabs_papel
    "fabs-grano" -> R.string.banner_fabs_grano
    "fabs-tinta" -> R.string.banner_fabs_tinta
    "negro-persiana" -> R.string.banner_negro_persiana
    "negro-hotel" -> R.string.banner_negro_hotel
    "negro-ventilador" -> R.string.banner_negro_ventilador
    "videoclub-estanteria" -> R.string.banner_videoclub_estanteria
    "videoclub-rebobinar" -> R.string.banner_videoclub_rebobinar
    "videoclub-abierto" -> R.string.banner_videoclub_abierto
    "biblioteca-lomos" -> R.string.banner_biblioteca_lomos
    "biblioteca-marmolado" -> R.string.banner_biblioteca_marmolado
    "biblioteca-polvo" -> R.string.banner_biblioteca_polvo
    "vinilo-surco" -> R.string.banner_vinilo_surco
    "vinilo-sello" -> R.string.banner_vinilo_sello
    "vinilo-giradiscos" -> R.string.banner_vinilo_giradiscos
    "arcade-puntajes" -> R.string.banner_arcade_puntajes
    "arcade-vector" -> R.string.banner_arcade_vector
    "arcade-hiperespacio" -> R.string.banner_arcade_hiperespacio
    else -> R.string.tema_clasico
})

/** Solo el servidor de Fogón entrega el arte aprobado; sin caché para respetar retiros por derechos. */
@Composable
private fun ArteMercado(id: String, modifier: Modifier, escala: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Crop) {
    val contexto = androidx.compose.ui.platform.LocalContext.current
    val solicitud = androidx.compose.runtime.remember(id) { coil3.request.ImageRequest.Builder(contexto)
        .data("https://fogonapps.duckdns.org/v1/mercado/$id/imagen")
        .memoryCachePolicy(coil3.request.CachePolicy.DISABLED).diskCachePolicy(coil3.request.CachePolicy.DISABLED).build() }
    coil3.compose.AsyncImage(solicitud, null, modifier, contentScale = escala)
}

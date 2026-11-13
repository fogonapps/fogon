package io.github.fogonapps.revision

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot

/** Algo que se ve mal en una pantalla. */
data class Hallazgo(val pantalla: String, val tipo: String, val detalle: String) {
    override fun toString() = "[$pantalla] $tipo: $detalle"
}

/**
 * Revisa lo que hay en pantalla y devuelve lo que se ve mal:
 * - «pegados»: dos botones uno encima del otro sin aire entre ellos (los que ocupan todo el ancho, como las filas de
 *   una lista o de un menú, sí van pegados y no cuentan);
 * - «encimados»: dos cosas tocables que se tapan (sin ser una parte de la otra);
 * - «chico»: algo tocable de menos de 32 dp (el alto de un chip), difícil de tocar con el dedo;
 * - «sin nombre»: algo tocable sin texto ni descripción (el lector de pantalla no puede decir qué es).
 */
fun ComposeContentTestRule.revisar(pantalla: String, barraAbajo: Float = 0f): List<Hallazgo> {
    // Con animaciones infinitas (una canción que suena, un fondo que se mueve) la pantalla nunca queda quieta: el
    // reloj de las animaciones se avanza a mano para que lo nuevo aparezca y se mida.
    mainClock.autoAdvance = false
    mainClock.advanceTimeBy(1500)
    waitForIdle()
    val raiz = onRoot(useUnmergedTree = false).fetchSemanticsNode()
    val densidad = density.density
    val ancho = raiz.boundsInRoot.width
    val alto = raiz.boundsInRoot.height
    val todos = mutableListOf<SemanticsNode>()
    fun recorrer(n: SemanticsNode) { todos += n; n.children.forEach(::recorrer) }
    recorrer(raiz)
    fun visible(r: Rect) = r.width > 1 && r.height > 1 && r.bottom > 0 && r.top < alto && r.right > 0 && r.left < ancho
    val tocables = todos.filter { it.config.getOrNull(SemanticsActions.OnClick) != null && it.config.getOrNull(SemanticsProperties.Disabled) == null && visible(it.boundsInRoot) }
    fun nombre(n: SemanticsNode): String {
        val t = n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
            ?: n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
        if (!t.isNullOrBlank()) return t
        fun dentro(x: SemanticsNode): String? = x.children.firstNotNullOfOrNull { c ->
            (c.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: c.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "))
                ?.takeIf { it.isNotBlank() } ?: dentro(c)
        }
        return dentro(n).orEmpty()
    }
    fun ancestro(a: SemanticsNode, b: SemanticsNode): Boolean { var p = b.parent; while (p != null) { if (p.id == a.id) return true; p = p.parent }; return false }
    fun dp(px: Float) = px / densidad
    val hallazgos = mutableListOf<Hallazgo>()
    tocables.forEach { n ->
        val r = n.boundsInRoot
        // (Lo cortado por el borde de la pantalla o de una fila que se desliza no cuenta como chico.)
        val cortado = r.left <= 0.5f || r.right >= ancho - 0.5f || r.top <= 0.5f || r.bottom >= alto - 0.5f ||
            n.parent?.boundsInRoot?.let { p -> r.left <= p.left + 0.5f || r.right >= p.right - 0.5f } == true && dp(minOf(r.width, r.height)) < 20f
        if (!cortado && dp(minOf(r.width, r.height)) < 32f) hallazgos += Hallazgo(pantalla, "chico", "«${nombre(n)}» mide ${dp(r.width).toInt()}×${dp(r.height).toInt()} dp")
        if (nombre(n).isBlank()) hallazgos += Hallazgo(pantalla, "sin nombre", "algo tocable de ${dp(r.width).toInt()}×${dp(r.height).toInt()} dp en (${dp(r.left).toInt()}, ${dp(r.top).toInt()})")
    }
    for (i in tocables.indices) for (j in i + 1 until tocables.size) {
        val a = tocables[i]; val b = tocables[j]
        if (ancestro(a, b) || ancestro(b, a)) continue
        // La barra de pestañas flota sobre el contenido (que pasa por debajo al desplazarse): no se compara con él.
        if (barraAbajo > 0 && (a.boundsInRoot.bottom > alto - barraAbajo * densidad || b.boundsInRoot.bottom > alto - barraAbajo * densidad)) continue
        val ra = a.boundsInRoot; val rb = b.boundsInRoot
        val cruce = ra.intersect(rb)
        // (Los botones segmentados de Material se enciman 1 dp a propósito: solo cuenta si es más de 2 dp.)
        if (dp(cruce.width) > 2 && dp(cruce.height) > 2) { hallazgos += Hallazgo(pantalla, "encimados", "«${nombre(a)}» y «${nombre(b)}»"); continue }
        // Uno debajo del otro, compartiendo ancho, con menos de 4 dp entre ellos.
        val comparten = minOf(ra.right, rb.right) - maxOf(ra.left, rb.left)
        val hueco = maxOf(rb.top - ra.bottom, ra.top - rb.bottom)
        val esBoton = { x: SemanticsNode -> x.config.getOrNull(SemanticsProperties.Role) == Role.Button || x.boundsInRoot.width < ancho * 0.97f }
        if (comparten > minOf(ra.width, rb.width) * 0.5f && dp(hueco) in -0.5f..3.9f && esBoton(a) && esBoton(b) && dp(ra.height) <= 72 && dp(rb.height) <= 72)
            hallazgos += Hallazgo(pantalla, "pegados", "«${nombre(a)}» y «${nombre(b)}» (${dp(hueco).toInt()} dp entre ellos)")
    }
    return hallazgos.distinct()
}

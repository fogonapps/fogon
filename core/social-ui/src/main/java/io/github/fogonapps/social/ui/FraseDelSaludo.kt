package io.github.fogonapps.social.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.fogonapps.social.FraseBase
import io.github.fogonapps.social.FraseElegida
import io.github.fogonapps.social.FrasesSaludo
import io.github.fogonapps.social.elegirFraseSaludo
import io.github.fogonapps.social.franjaDelDia

/** La frase del saludo en pantalla y lo que se puede hacer con ella. */
class FraseEnPantalla(val frase: FraseElegida, val gusta: Boolean, private val frases: FrasesSaludo, private val otra: () -> Unit) {
    fun gustar() = frases.votar(frase.clave, if (gusta) 0 else 1)
    fun compartida() = frases.votar(frase.clave, 1)
    /** No mostrarla más: se vota que no y sale otra. */
    fun noMostrar() { frases.votar(frase.clave, -1); otra() }
}

/**
 * Elige la frase del saludo como El Cajón (ver [elegirFraseSaludo]) y la deja toda la franja del día, aunque se cierre
 * la app. [base]: las frases propias de la app, con claves que llevan su prefijo.
 */
@Composable
fun rememberFraseDelSaludo(frases: FrasesSaludo, base: List<FraseBase>, idioma: String): FraseEnPantalla? {
    val votos by frases.votos.collectAsState()
    val deLaGente by frases.deLaGente.collectAsState()
    val ocultas by frases.ocultas.collectAsState()
    val gustan by frases.gustan.collectAsState()
    val noGustan by frases.noGustan.collectAsState()
    LaunchedEffect(idioma) { frases.cargar(idioma) }
    var vuelta by rememberSaveable { mutableIntStateOf(0) }
    val hoy = java.time.LocalDate.now()
    val hora = java.time.LocalTime.now().hour
    val franja = "$hoy-${franjaDelDia(hora)}"
    val elegida = remember(franja, vuelta, base, deLaGente, ocultas, noGustan) {
        val textos = base.associate { it.clave to it.texto } + deLaGente.associate { it.clave to it.texto }
        val guardada = if (vuelta == 0) frases.guardada(franja)?.takeIf { it in textos && it !in noGustan && it !in ocultas } else null
        guardada?.let { FraseElegida(it, textos.getValue(it)) }
            ?: elegirFraseSaludo(base, deLaGente, votos, gustan, noGustan + ocultas, hoy.dayOfYear, hora, vuelta)?.also { frases.guardar(franja, it.clave) }
    } ?: return null
    return FraseEnPantalla(elegida, elegida.clave in gustan, frases) { vuelta++ }
}

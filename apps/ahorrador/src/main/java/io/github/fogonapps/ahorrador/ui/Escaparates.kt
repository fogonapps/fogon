package io.github.fogonapps.ahorrador.ui

import androidx.compose.runtime.Composable
import io.github.fogonapps.ahorrador.AhorradorViewModel
import io.github.fogonapps.ahorrador.datos.Estado
import io.github.fogonapps.social.ui.BotonEditorEscaparates
import io.github.fogonapps.social.ui.escaparateNumero
import kotlin.math.roundToInt

/** Solo porcentaje: ni nombre, ni objetivo, ni ahorro, ni identificador de la meta salen del teléfono. */
internal fun escaparatesMetas(e: Estado, progreso: (io.github.fogonapps.ahorrador.datos.Meta) -> Double) = e.metas.map {
    escaparateNumero("alcancia:meta", (progreso(it).coerceIn(0.0, 1.0) * 100).roundToInt(), "%")
}.distinct()

@Composable
internal fun EditorEscaparatesApp(vm: AhorradorViewModel, e: Estado) {
    BotonEditorEscaparates(interfazCuenta(vm), escaparatesMetas(e) { vm.L.progreso(e, it) })
}

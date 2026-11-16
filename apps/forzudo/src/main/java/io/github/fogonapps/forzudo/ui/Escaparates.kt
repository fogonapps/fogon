package io.github.fogonapps.forzudo.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.fogonapps.forzudo.GymViewModel
import io.github.fogonapps.forzudo.R
import io.github.fogonapps.forzudo.datos.Estado
import io.github.fogonapps.perfil.ElementoEscaparate
import io.github.fogonapps.social.ui.*

@Composable
internal fun EditorEscaparatesApp(vm: GymViewModel, e: Estado) {
    val dias = vm.L.dias(e)
    val records = e.sessions.flatMap { it.entries.keys }.distinct().mapNotNull { id ->
        val kg = vm.L.maxKg(e, id)
        val reps = vm.L.maxReps(e, id)
        if (kg == null && reps == null) null else ElementoEscaparate(vm.L.b.ex[id]?.n ?: id, listOfNotNull(kg?.let { "$it kg" }, reps?.let { "$it" }).joinToString(" · "))
    }.take(6)
    val entrenos = e.sessions.sortedByDescending { it.date }.map { s ->
        escaparateElementos("forzudo:entreno", listOf(ElementoEscaparate(vm.L.tituloRutina(s, dias), s.entries.keys.mapNotNull { vm.L.b.ex[it]?.n }.joinToString(" · "))))
    }.distinct()
    val candidatos = entrenos + escaparateNumero("forzudo:racha", vm.L.racha(e, dias, vm.hoy()).actual, stringResource(R.string.perfil_racha)) +
        if (records.isEmpty()) emptyList() else listOf(escaparateElementos("forzudo:records", records))
    BotonEditorEscaparates(interfazCuenta(vm), candidatos)
}

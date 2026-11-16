package io.github.fogonapps.cucha.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.fogonapps.cucha.CuchaViewModel
import io.github.fogonapps.cucha.R
import io.github.fogonapps.cucha.datos.Estado
import io.github.fogonapps.perfil.ElementoEscaparate
import io.github.fogonapps.social.ui.*

@Composable
internal fun EditorEscaparatesApp(vm: CuchaViewModel, e: Estado) {
    val recetas = e.recetas.map { escaparateElementos("cucha:receta", listOf(ElementoEscaparate(it.nombre(vm.idioma()), it.pasos(vm.idioma()).joinToString("\n")))) }
    BotonEditorEscaparates(interfazCuenta(vm), recetas + escaparateNumero("cucha:racha", vm.L.racha(e, vm.hoy()), stringResource(R.string.perfil_dias)))
}

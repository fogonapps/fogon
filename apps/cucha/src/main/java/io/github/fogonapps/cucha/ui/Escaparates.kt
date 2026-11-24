package io.github.fogonapps.cucha.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import io.github.fogonapps.cucha.CuchaViewModel
import io.github.fogonapps.cucha.R
import io.github.fogonapps.cucha.datos.Estado
import io.github.fogonapps.perfil.ElementoEscaparate
import io.github.fogonapps.social.ui.*

@Composable
internal fun EscaparatesPerfilPropio(vm: CuchaViewModel, e: Estado, usuario: String) {
    val recetas = e.recetas.map { escaparateElementos("cucha:receta", listOf(ElementoEscaparate(it.nombre(vm.idioma()), it.pasos(vm.idioma()).joinToString("\n")))) }
    EscaparatesEnPerfil(interfazCuenta(vm), usuario, recetas + escaparateNumero("cucha:racha", vm.L.racha(e, vm.hoy()), vm.L.racha(e, vm.hoy()).let { n -> pluralStringResource(R.plurals.perfil_dias, n) }), mostrarAgregar = true)
}

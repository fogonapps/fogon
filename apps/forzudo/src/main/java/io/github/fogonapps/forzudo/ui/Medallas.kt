package io.github.fogonapps.forzudo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fogonapps.forzudo.GymViewModel
import io.github.fogonapps.forzudo.R
import io.github.fogonapps.forzudo.datos.Medalla
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.Ilustracion
import io.github.fogonapps.identidad.IconosSociales
import io.github.fogonapps.identidad.aparecer
import io.github.fogonapps.identidad.aparecerConRebote
import io.github.fogonapps.identidad.forma
import io.github.fogonapps.identidad.tarjetaFogon
import io.github.fogonapps.perfil.MedallaInsignia

/** El nombre de una medalla, en el idioma de la app. */
@Composable
fun nombreMedalla(vm: GymViewModel, m: Medalla): String {
    val n = m.meta.toInt()
    return when {
        m.id == "entrenos_1" -> stringResource(R.string.medalla_primer_entreno)
        m.id.startsWith("entrenos_") -> stringResource(R.string.medalla_entrenos, n)
        m.id.startsWith("racha_") -> pluralStringResource(R.plurals.medalla_racha, n, n)
        m.id.startsWith("volumen_") -> stringResource(R.string.medalla_volumen, "%,d".format(n / 1000))
        else -> stringResource(R.string.medalla_rango, vm.bib.niveles.getOrNull(n)?.n ?: "")
    }
}

/** Perfil → Medallas: primero las ganadas, después las que faltan, por cercanía. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeccionMedallas(vm: GymViewModel, lista: List<Medalla>) {
    val ganadas = lista.count { it.lograda }
    Seccion(stringResource(R.string.medallas_titulo, ganadas, lista.size)) {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            lista.sortedWith(compareByDescending<Medalla> { it.lograda }.thenByDescending { it.progreso })
                .forEachIndexed { indice, medalla ->
                    val icono = when (medalla.id.substringBefore('_')) {
                        "entrenos", "volumen" -> Iconos.Pesa
                        "racha" -> Iconos.Racha
                        "rango" -> Iconos.Rango
                        else -> IconosSociales.Trofeo
                    }
                    Box(Modifier.aparecer(indice)) {
                        MedallaInsignia(
                            icono, nombreMedalla(vm, medalla), medalla.lograda,
                            lleva = medalla.actual.toInt().coerceAtLeast(0), meta = medalla.meta.toInt(),
                        )
                    }
                }
        }
    }
}

/** En "Entreno terminado": las medallas que se acaban de ganar, con el trofeo que salta. */
@Composable
fun MedallasNuevas(vm: GymViewModel, nuevas: List<Medalla>) {
    if (nuevas.isEmpty()) return
    val f = FogonTema.actual
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).aparecerConRebote(300).tarjetaFogon(forma(26.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Ilustracion(R.drawable.ilustracion_trofeo, tamano = 84.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(pluralStringResource(R.plurals.medalla_nueva, nuevas.size), style = MaterialTheme.typography.titleLarge, color = f.texto)
            nuevas.forEach { Text(nombreMedalla(vm, it), style = MaterialTheme.typography.bodyMedium, color = f.acentoTexto) }
        }
    }
}

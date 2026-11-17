package io.github.fogonapps.cajon.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Insignias
import io.github.fogonapps.perfil.ElementoEscaparate
import io.github.fogonapps.social.ui.*

@Composable
internal fun EditorEscaparatesApp(vm: CajonViewModel, e: Estado) {
    val oidos by vm.oidos.collectAsState()
    val obras = e.vivas.associateBy { it.id }
    val resenas = e.resenas.filter { !it.borrada }.mapNotNull { r -> obras[r.obra]?.let { o -> escaparateElementos("cajon:resena", listOf(ElementoEscaparate(o.titulo, r.texto, o.portada))) } }
    val listas = e.listasVivas.map { l -> escaparateElementos("cajon:lista", listOf(ElementoEscaparate(l.nombre, l.descripcion, obras[l.portada.ifBlank { l.obras.firstOrNull().orEmpty() }]?.portada.orEmpty()))) }
    val albumes = oidos.albumes.sortedByDescending { it.ultima }.take(6).map { ElementoEscaparate(it.album, it.artista, it.portada) }
    val insignias = Insignias.de(e, vm.hoy().year).filter { it.ganada }.map { it.id }
    val candidatos = resenas + listas + listOfNotNull(
        albumes.takeIf { it.isNotEmpty() }?.let { escaparateElementos("cajon:albumes", it) },
        insignias.takeIf { it.isNotEmpty() }?.let { escaparateInsignias(it) },
    )
    BotonEditorEscaparates(interfazSocial(vm), candidatos)
}

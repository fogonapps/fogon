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
internal fun EscaparatesPerfilPropio(vm: CajonViewModel, e: Estado, usuario: String) {
    val oidos by vm.oidos.collectAsState()
    val obras = e.vivas.associateBy { it.id }
    val resenas = e.resenas.filter { !it.borrada }.mapNotNull { r -> obras[r.obra]?.let { o -> escaparateElementos("cajon:resena", listOf(ElementoEscaparate(o.titulo, r.texto, o.portada))) } }
    val listas = e.listasVivas.map { l -> escaparateElementos("cajon:lista", listOf(ElementoEscaparate(l.nombre, l.descripcion, obras[l.portada.ifBlank { l.obras.firstOrNull().orEmpty() }]?.portada.orEmpty()))) }
    // La carátula de lo oído vive en el teléfono; a la repisa va la de internet del mismo álbum en el cajón.
    val porClave = e.vivas.filter { it.tipo == io.github.fogonapps.cajon.datos.Tipos.ALBUM }
        .associateBy { io.github.fogonapps.cajon.datos.RegistroEscuchas.clave(it.creador, it.titulo) }
    val albumes = oidos.albumes.sortedByDescending { it.ultima }.take(6).map { a ->
        val portada = listOfNotNull(a.obra?.let { obras[it]?.portada }, porClave[a.clave]?.portada, a.portada).firstOrNull { it.startsWith("https://") }.orEmpty()
        ElementoEscaparate(a.album, a.artista, portada)
    }
    val insignias = Insignias.de(e, vm.hoy().year).filter { it.ganada }.map { it.id }
    val candidatos = resenas + listas + listOfNotNull(
        albumes.takeIf { it.isNotEmpty() }?.let { escaparateElementos("cajon:albumes", it) },
        insignias.takeIf { it.isNotEmpty() }?.let { escaparateInsignias(it) },
    )
    EscaparatesEnPerfil(interfazSocial(vm), usuario, candidatos, mostrarAgregar = true)
}

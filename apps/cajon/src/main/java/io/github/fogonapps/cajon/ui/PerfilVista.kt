package io.github.fogonapps.cajon.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.ClienteSocial
import io.github.fogonapps.cajon.datos.MiCuenta
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.PerfilSocial
import io.github.fogonapps.perfil.ContadorPerfil
import io.github.fogonapps.perfil.PerfilVista
import io.github.fogonapps.perfil.SeccionPerfil

/** La persona siempre puede ver todas sus partes, incluso las que no comparte en la red. */
internal fun Estado.aPerfilVista(
    secciones: List<SeccionPerfil> = emptyList(), contadores: List<ContadorPerfil> = emptyList(), cuenta: MiCuenta? = null,
) = PerfilVista(ajustes.nombre, cuenta?.usuario ?: perfil.usuario, perfil.foto, perfil.portada, perfil.bio, perfil.enlaces,
    privada = cuenta?.privada ?: false, relacion = "yo", contadores = contadores, secciones = secciones, insignia = cuenta?.insignia.orEmpty())

/** El servidor ya filtra las partes por visibilidad y relación; no se reconstruyen desde datos locales. */
internal fun PerfilSocial.aPerfilVista(
    secciones: List<SeccionPerfil> = emptyList(), contadores: List<ContadorPerfil> = emptyList(), bloqueado: Boolean = false,
): PerfilVista {
    // Una cuenta privada muestra su presentación (foto, portada, bio y enlaces) pero no su contenido.
    return PerfilVista(persona.nombre.ifBlank { persona.usuario }, persona.usuario,
        if (!bloqueado && persona.foto) ClienteSocial.foto(persona.usuario, persona.fotoV) else "",
        if (bloqueado) "" else portada, if (bloqueado) "" else bio, if (bloqueado) emptyList() else enlaces,
        persona.privada, relacion, if (bloqueado) emptyList() else contadores, if (cerrada || bloqueado) emptyList() else secciones,
        insignia = if (bloqueado) "" else persona.insignia, tema = if (bloqueado) "" else persona.tema, marco = if (bloqueado) "" else persona.marco)
}

/**
 * Un único catálogo de pestañas evita volver a anunciar «Lo tuyo» en perfiles ajenos (H-CJ-07). Lo de la persona
 * (destacados / lo suyo) va primero y es la pestaña que se abre, con las cifras al lado.
 */
internal fun catalogoSeccionesPerfil(propio: Boolean) = listOf(
    Triple(1, Iconos.Estrella, if (propio) R.string.parte_destacados else R.string.red_lo_suyo),
    Triple(5, Iconos.Cifras, if (propio) R.string.tus_cifras else R.string.red_cifras),
    Triple(0, Iconos.Imagen, R.string.red_publicaciones),
    Triple(2, Iconos.Artista, R.string.tipo_artistas),
    Triple(3, Iconos.Lista, R.string.pestana_listas),
    Triple(4, Iconos.Guardar, R.string.parte_guardadas),
    Triple(6, Iconos.Lapiz, if (propio) R.string.resena_tuyas else R.string.resenas_suyas),
)

/** La pestaña con que abre un perfil: lo de la persona. */
internal const val PESTANA_INICIAL = 1

@Composable
internal fun seccionesCajon(propio: Boolean, contenido: @Composable (Int) -> Unit): List<SeccionPerfil> =
    catalogoSeccionesPerfil(propio).map { (id, icono, nombre) -> SeccionPerfil(id, icono, stringResource(nombre)) { contenido(id) } }

/** Selección local: la visibilidad nunca oculta reseñas a su dueño. */
internal fun Estado.resenasDelPerfil() = resenas.filter { !it.borrada }.sortedByDescending { it.fecha }

/** La visibilidad ya viene resuelta por el servidor. */
internal fun PerfilSocial.resenasDelPerfil() = publicaciones.filter { it.tipo == "resena" }

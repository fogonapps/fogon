package io.github.fogonapps.cucha.ui

import io.github.fogonapps.identidad.Dibujo
import io.github.fogonapps.identidad.IconosFogon
import io.github.fogonapps.identidad.dibujo
import io.github.fogonapps.identidad.hueco
import io.github.fogonapps.identidad.huecoTrazo
import io.github.fogonapps.identidad.relleno
import io.github.fogonapps.identidad.trazo

/**
 * Los íconos de La Cucha, gordos y llenos como el personaje (ver docs/IDENTIDAD.md → Sistema riso → Íconos).
 * Los de todas las apps están en [IconosFogon].
 */
object Iconos {
    val Hoy = IconosFogon.Inicio
    /** Una olla con su tapa: las recetas. */
    val Recetas = dibujo("olla", relleno("M4 10.4h16v6.2a3.4 3.4 0 0 1-3.4 3.4H7.4A3.4 3.4 0 0 1 4 16.6z"), trazo("M2.4 11.2H4M20 11.2h1.6", 2.6f), trazo("M5.4 7.6h13.2M12 7.6V5.4", 2.6f))
    /** Un calendario: el menú de la semana. */
    val Plan = dibujo("calendario", relleno("M3.6 5.6h16.8v14.8H3.6z"), huecoTrazo("M3.6 9.6h16.8", 1.8f), trazo("M8 3.4v4M16 3.4v4", 2.6f))
    /** Una gráfica que sube: el progreso. */
    val Progreso = dibujo("progreso", trazo("M4 17.6l5-5.2 3.6 3.2L20 7.6", 3f), trazo("M15.4 7.6H20v4.6", 3f))
    val Mas = IconosFogon.Mas
    val Atras = IconosFogon.Atras
    val Cerrar = IconosFogon.Cerrar
    val Siguiente = IconosFogon.Siguiente
    val Chulo = IconosFogon.Chulo
    val Borrar = IconosFogon.Borrar
    val Lapiz = IconosFogon.Lapiz
    val Reloj = IconosFogon.Reloj
    val Repetir = IconosFogon.Repetir
    val Persona = IconosFogon.Perfil
    val Racha = IconosFogon.Racha
    val Gota = IconosFogon.Tinta
    /** La lupa de buscar. */
    val Buscar = dibujo("buscar", trazo("M10.6 4.4a6.2 6.2 0 1 1 0 12.4 6.2 6.2 0 0 1 0-12.4zM15.2 15.2l4.6 4.6", 2.8f))
    /** Rayas de un código de barras. */
    val Codigo = dibujo("codigo", trazo("M4.4 5v14M8 5v14M14.6 5v14M19.6 5v14", 2.4f), trazo("M11.2 5v14M17.2 5v14", 1.2f))
    /** Una estrella: favorito. */
    val Estrella = dibujo("estrella", relleno("M12 3.4l2.6 5.2 5.8.8-4.2 4.1 1 5.8L12 16.6l-5.2 2.7 1-5.8-4.2-4.1 5.8-.8z"))
    /** Una canasta: el mercado. */
    val Mercado = dibujo("mercado", trazo("M3 5h3l2 10h10l2-7H7", 2.8f), relleno("M10.2 19.5a1.4 1.4 0 1 1-2.8 0 1.4 1.4 0 0 1 2.8 0z"), relleno("M18.2 19.5a1.4 1.4 0 1 1-2.8 0 1.4 1.4 0 0 1 2.8 0z"))
    /** Una pesa: El Forzudo. */
    val Pesa = dibujo("pesa", relleno("M3.6 9.6h2.6v4.8H3.6z"), relleno("M17.8 9.6h2.6v4.8h-2.6z"), relleno("M6.2 7h2.8v10H6.2z"), relleno("M15 7h2.8v10H15z"), trazo("M9 12h6", 2.6f))
    /** Un plato con cubiertos: las comidas. */
    val Plato = dibujo("plato", relleno("M12 5.4a6.6 6.6 0 1 1 0 13.2 6.6 6.6 0 0 1 0-13.2z"), hueco("M12 8.6a3.4 3.4 0 1 1 0 6.8 3.4 3.4 0 0 1 0-6.8z"), trazo("M2.6 5v14M21.4 5v14", 1.8f))
}

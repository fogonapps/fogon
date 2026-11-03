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

    /* ----- Figuras de los platos (una por tipo; ver figuraDe en Recetas.kt) ----- */
    private const val TAZON = "M3.4 11h17.2a8.6 7.6 0 0 1-17.2 0z"
    val Sopa = dibujo("sopa", relleno(TAZON), trazo("M9 3.6c-1 1.2 1 2.2 0 3.6M13.4 3.6c-1 1.2 1 2.2 0 3.6", 1.8f), trazo("M7.6 21h8.8", 2.2f))
    val Arroz = dibujo("arroz", relleno(TAZON), relleno("M5 11a7 4.6 0 0 1 14 0z"), huecoTrazo("M8.6 8.8l1 .5M12 7.4l1 .4M15 9l.9.5", 1.4f))
    val Arepa = dibujo("arepa", relleno("M12 4.2a7.8 7.8 0 1 1 0 15.6 7.8 7.8 0 0 1 0-15.6z"), huecoTrazo("M8.2 10.4l2.2 2.2M12.2 8.2l2.6 2.6M10.4 13.6l2.6 2.6", 1.6f))
    val Huevo = dibujo("huevo", relleno("M5 13.4c-1.4-3.6 1.4-7.6 5.4-7.4 2 0 3.4.8 4.8.6 3.2-.4 5.6 2.6 4.6 5.8-.8 2.6-2.6 3.4-4.4 4.4-1.8 1-3 3-6.2 2.6C6.4 19 5.6 15 5 13.4z"), huecoTrazo("M12 9.4a2.8 2.8 0 1 1 0 5.6 2.8 2.8 0 0 1 0-5.6z", 1.8f))
    val Ensalada = dibujo("ensalada", relleno(TAZON), trazo("M7 9.6c.6-2.6 2.6-3.8 4.4-3.4M12 9.4c0-2.8 1.6-4.6 4-4.6M15.6 9.8c1-1.6 2.6-2 3.6-1.6", 2.2f))
    val Carne = dibujo("carne", relleno("M4.4 10.6c0-3.6 3.4-6 7.4-6 4.6 0 8.2 2.6 8.2 6.6 0 4.4-3.8 8.4-9.2 8.4-3.8 0-6.4-2.8-6.4-5.6z"), huecoTrazo("M9.6 9.6a1.8 1.8 0 1 1 0 3.6 1.8 1.8 0 0 1 0-3.6z", 1.6f))
    val Pollo = dibujo("pollo", relleno("M14.2 3.6a6.2 6.2 0 0 1 4.4 10.6 6.2 6.2 0 0 1-7.6.8L8.6 17.4a2 2 0 1 1-2.8 1.4 2 2 0 1 1 1.4-2.8l2.4-2.4a6.2 6.2 0 0 1 4.6-10z"))
    val Pescado = dibujo("pescado", relleno("M2.8 12c2.4-3.8 6-5.6 9.6-5.6 3.2 0 5.4 2 6.4 5.6-1 3.6-3.2 5.6-6.4 5.6-3.6 0-7.2-1.8-9.6-5.6z"), relleno("M17.8 12l3.6-4v8z"), hueco("M7.4 10.2a1.1 1.1 0 1 1 0 2.2 1.1 1.1 0 0 1 0-2.2z"))
    val Bebida = dibujo("bebida", relleno("M5.6 6.4h12.8l-1.6 13.4a1.6 1.6 0 0 1-1.6 1.4H8.8a1.6 1.6 0 0 1-1.6-1.4z"), trazo("M13.6 6.4l2.4-3.6", 2.0f), huecoTrazo("M6.4 10.4h11.2", 1.4f))
    val Postre = dibujo("postre", relleno("M3.6 13.2l16-5.2v11.4H3.6z"), huecoTrazo("M3.6 15.8h16", 1.6f), relleno("M16.8 3.4a1.7 1.7 0 1 1 0 3.4 1.7 1.7 0 0 1 0-3.4z"))
    val Sanduche = dibujo("sanduche", relleno("M3.4 10.4c0-3.2 3.8-5.4 8.6-5.4s8.6 2.2 8.6 5.4z"), trazo("M3.4 13.2h17.2", 2.6f), relleno("M3.4 15.6h17.2v2.4a1.8 1.8 0 0 1-1.8 1.8H5.2a1.8 1.8 0 0 1-1.8-1.8z"))
    val Pasta = dibujo("pasta", relleno(TAZON), trazo("M7.6 8.6c.8-1 1.6 1 2.4 0s1.6 1 2.4 0 1.6 1 2.4 0", 2.0f), trazo("M17.4 2.8l-2 6", 2.0f))
    val Fruta = dibujo("fruta", relleno("M12 8c-1.6-1.2-4.8-1.4-6.2 1.2-1.6 3 0 8.4 2.8 10.4 1.4 1 2.4.8 3.4.2 1 .6 2 .8 3.4-.2 2.8-2 4.4-7.4 2.8-10.4C16.8 6.6 13.6 6.8 12 8z"), trazo("M12 8c0-1.8.6-3.2 2-4", 2.0f))
}

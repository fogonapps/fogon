package io.github.fogonapps.cajon.ui

import io.github.fogonapps.identidad.Dibujo
import io.github.fogonapps.identidad.IconosFogon
import io.github.fogonapps.identidad.dibujo
import io.github.fogonapps.identidad.hueco
import io.github.fogonapps.identidad.huecoTrazo
import io.github.fogonapps.identidad.relleno
import io.github.fogonapps.identidad.trazo

/** Los íconos de El Cajón, de línea redonda como los de todas las apps (los comunes están en [IconosFogon]). */
object Iconos {
    val Inicio = IconosFogon.Inicio
    /** El cajón de madera: la biblioteca. */
    val Cajon = dibujo("cajon", relleno("M3.6 10.4h16.8v9.2H3.6z"), trazo("M4.6 10.4 6.6 5h10.8l2 5.4", 2.4f), huecoTrazo("M9.4 14.4h5.2", 2f))
    /** Una claqueta: películas. */
    val Pelicula = dibujo("pelicula", relleno("M3.6 9.6h16.8v10H3.6z"), trazo("M3.8 9.4 3.2 6l16.4-2.6.6 3.4", 2.4f), huecoTrazo("M8.4 5.2l2 3.4M13.4 4.4l2 3.4", 1.8f))
    /** Un televisor: series. */
    val Serie = dibujo("serie", relleno("M3.4 7.4h17.2v11.4H3.4z"), trazo("M8.6 3.6 12 7.2l3.4-3.6", 2.4f))
    /** Un ojo grande con brillo: animes. */
    val Anime = dibujo("anime", relleno("M3 12c2.8-5 15.2-5 18 0-2.8 5-15.2 5-18 0z"), hueco("M12 9a3 3 0 1 1 0 6 3 3 0 0 1 0-6z"), relleno("M13 10.2a.9.9 0 1 1 0 1.8.9.9 0 0 1 0-1.8z"))
    /** Un libro: libros. */
    val Libro = dibujo("libro", relleno("M5 4.4h11.6a2 2 0 0 1 2 2v13.2H7a2 2 0 0 1-2-2z"), huecoTrazo("M5 17.6a2 2 0 0 1 2-2h11.6", 1.8f))
    /** Un disco de vinilo: álbumes. */
    val Disco = dibujo("disco", relleno("M12 3.6a8.4 8.4 0 1 1 0 16.8 8.4 8.4 0 0 1 0-16.8z"), hueco("M12 9.6a2.4 2.4 0 1 1 0 4.8 2.4 2.4 0 0 1 0-4.8z"), huecoTrazo("M7.4 9.4a5.4 5.4 0 0 1 2.4-2.6", 1.6f))
    /** Un micrófono: artistas. */
    val Artista = dibujo("artista", relleno("M9.4 4.6a2.6 2.6 0 0 1 5.2 0v6a2.6 2.6 0 0 1-5.2 0z"), trazo("M6.4 10.6a5.6 5.6 0 0 0 11.2 0M12 16.2v3.8M8.8 20h6.4", 2.2f))
    /** Renglones con viñeta: las listas. */
    val Lista = dibujo("lista", trazo("M9.4 6.4h10.2M9.4 12h10.2M9.4 17.6h10.2", 2.6f), relleno("M5.2 5a1.4 1.4 0 1 1 0 2.8 1.4 1.4 0 0 1 0-2.8zM5.2 10.6a1.4 1.4 0 1 1 0 2.8 1.4 1.4 0 0 1 0-2.8zM5.2 16.2a1.4 1.4 0 1 1 0 2.8 1.4 1.4 0 0 1 0-2.8z"))
    /** Un cuaderno: el diario. */
    val Diario = dibujo("diario", relleno("M6 3.6h12.4v16.8H6z"), huecoTrazo("M9.4 8h5.6M9.4 11.6h5.6", 1.8f), trazo("M4.4 7.4h2.8M4.4 12h2.8M4.4 16.6h2.8", 1.8f))
    val Corazon = dibujo("corazon", relleno("M12 20s-7.6-4.6-7.6-10a4.2 4.2 0 0 1 7.6-2.6A4.2 4.2 0 0 1 19.6 10c0 5.4-7.6 10-7.6 10z"))
    val Estrella = dibujo("estrella", relleno("M12 3.4l2.6 5.2 5.8.8-4.2 4.1 1 5.8L12 16.6l-5.2 2.7 1-5.8-4.2-4.1 5.8-.8z"))
    val Buscar = dibujo("buscar", trazo("M10.6 4.4a6.2 6.2 0 1 1 0 12.4 6.2 6.2 0 0 1 0-12.4zM15.2 15.2l4.6 4.6", 2.8f))
    /** Una flecha que sale de una caja: compartir. */
    val Compartir = dibujo("compartir", trazo("M12 14.6V3.8M8 7.4l4-3.6 4 3.6", 2.6f), trazo("M7.4 10.8H5v9.4h14v-9.4h-2.4", 2.6f))
    /** Una nube: sincronizar. */
    val Nube = dibujo("nube", relleno("M7.4 18.6a4.4 4.4 0 0 1-.6-8.8 5.8 5.8 0 0 1 11 1.6 3.6 3.6 0 0 1-.4 7.2z"))
    /** Una gráfica que sube: tus cifras. */
    val Cifras = dibujo("cifras", trazo("M4 17.6l5-5.2 3.6 3.2L20 7.6", 3f), trazo("M15.4 7.6H20v4.6", 3f))
    /** Una copa: los retos del año. */
    val Trofeo = dibujo("trofeo", relleno("M7.2 4.4h9.6v4.8a4.8 4.8 0 0 1-9.6 0z"), trazo("M7.2 6.2H4.4a2.6 2.6 0 0 0 3 3.6M16.8 6.2h2.8a2.6 2.6 0 0 1-3 3.6M12 14v3.4M8.4 19.8h7.2", 2.2f))
    /** Flechas de ordenar. */
    val Ordenar = dibujo("ordenar", trazo("M7 5v14M4.4 16.4 7 19l2.6-2.6M13.6 7h6.4M13.6 12h4.6M13.6 17h2.8", 2.4f))
    val Mas = IconosFogon.Mas
    val Atras = IconosFogon.Atras
    val Cerrar = IconosFogon.Cerrar
    val Siguiente = IconosFogon.Siguiente
    val Abajo = IconosFogon.Abajo
    val Arriba = dibujo("arriba", trazo("M5.4 14.8 12 8.2l6.6 6.6", 3.4f))
    val Chulo = IconosFogon.Chulo
    val Borrar = IconosFogon.Borrar
    val Lapiz = IconosFogon.Lapiz
    val Repetir = IconosFogon.Repetir
    val Persona = IconosFogon.Perfil
    val Racha = IconosFogon.Racha
    val Reloj = IconosFogon.Reloj

    /** El ícono de cada tipo de obra. */
    fun de(tipo: String): Dibujo = when (tipo) {
        "pelicula" -> Pelicula
        "serie" -> Serie
        "anime" -> Anime
        "libro" -> Libro
        "album" -> Disco
        else -> Artista
    }
}

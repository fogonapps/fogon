package io.github.fogonapps.identidad

/*
 * Íconos de Lucide (https://lucide.dev, licencia ISC), pasados al formato de [Dibujo]: lo cerrado es [relleno]
 * (se ve como contorno y se llena cuando el ícono va marcado) y lo abierto, [trazo]. Una app los activa con
 * [Iconografia.usarLucide]; entonces cada ícono de Fogón con el mismo nombre se dibuja con su par de Lucide.
 * Se generan con un script desde el paquete `lucide-static`; no se editan a mano.
 */

/** Qué dibujo usa cada ícono de Fogón en esta app. */
object Iconografia {
    @Volatile var reemplazos: Map<String, Dibujo> = emptyMap()
        private set

    /** Fabs (rebranding 2026-11): todos los íconos pasan a Lucide. Las otras apps siguen con los suyos hasta su turno. */
    fun usarLucide() { reemplazos = IconosLucide.porNombre }
}

internal object IconosLucide {
    val porNombre: Map<String, Dibujo> = mapOf(
        "candado" to dibujo("candado", relleno("M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-7a2 2 0 0 1 2 -2z"), trazo("M7 11V7a5 5 0 0 1 10 0v4", 2f)),
        "reproducir" to dibujo("reproducir", relleno("M5 5a2 2 0 0 1 3.008-1.728l11.997 6.998a2 2 0 0 1 .003 3.458l-12 7A2 2 0 0 1 5 19z")),
        "opciones" to dibujo("opciones", relleno("M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0z"), relleno("M18 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0z"), relleno("M4 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0z")),
        "guardar" to dibujo("guardar", relleno("M17 3a2 2 0 0 1 2 2v15a1 1 0 0 1-1.496.868l-4.512-2.578a2 2 0 0 0-1.984 0l-4.512 2.578A1 1 0 0 1 5 20V5a2 2 0 0 1 2-2z")),
        "gente" to dibujo("gente", trazo("M18 21a8 8 0 0 0-16 0", 2f), relleno("M5 8a5 5 0 1 0 10 0a5 5 0 1 0 -10 0z"), trazo("M22 20c0-3.37-2-6.5-4-8a5 5 0 0 0-.45-8.3", 2f)),
        "enviar" to dibujo("enviar", relleno("M14.536 21.686a.5.5 0 0 0 .937-.024l6.5-19a.496.496 0 0 0-.635-.635l-19 6.5a.5.5 0 0 0-.024.937l7.93 3.18a2 2 0 0 1 1.112 1.11z"), trazo("m21.854 2.147-10.94 10.939", 2f)),
        "enlace" to dibujo("enlace", trazo("M15 3h6v6", 2f), trazo("M10 14 21 3", 2f), trazo("M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6", 2f)),
        "disco" to dibujo("disco", relleno("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z"), trazo("M6 12c0-1.7.7-3.2 1.8-4.2", 2f), relleno("M10 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z"), trazo("M18 12c0 1.7-.7 3.2-1.8 4.2", 2f)),
        "corazon" to dibujo("corazon", trazo("M2 9.5a5.5 5.5 0 0 1 9.591-3.676.56.56 0 0 0 .818 0A5.49 5.49 0 0 1 22 9.5c0 2.29-1.5 4-3 5.5l-5.492 5.313a2 2 0 0 1-3 .019L5 15c-1.5-1.5-3-3.2-3-5.5", 2f)),
        "compartir" to dibujo("compartir", trazo("M12 2v13", 2f), trazo("m16 6-4-4-4 4", 2f), trazo("M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8", 2f)),
        "chispa" to dibujo("chispa", relleno("M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2 2 0 0 0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594z")),
        "campana" to dibujo("campana", trazo("M10.268 21a2 2 0 0 0 3.464 0", 2f), trazo("M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326", 2f)),
        "buscar" to dibujo("buscar", trazo("m21 21-4.34-4.34", 2f), relleno("M3 11a8 8 0 1 0 16 0a8 8 0 1 0 -16 0z")),
        "arriba" to dibujo("arriba", trazo("m18 15-6-6-6 6", 2f)),
        "anime" to dibujo("anime", trazo("M12 12V4a1 1 0 0 1 1-1h6.297a1 1 0 0 1 .651 1.759l-4.696 4.025", 2f), trazo("m12 21-7.414-7.414A2 2 0 0 1 4 12.172V6.415a1.002 1.002 0 0 1 1.707-.707L20 20.009", 2f), trazo("m12.214 3.381 8.414 14.966a1 1 0 0 1-.167 1.199l-1.168 1.163a1 1 0 0 1-.706.291H6.351a1 1 0 0 1-.625-.219L3.25 18.8a1 1 0 0 1 .631-1.781l4.165.027", 2f)),
        "trofeo" to dibujo("trofeo", trazo("M10 14.66V17a1 1 0 0 1-1 1 2 2 0 0 0-2 2v2", 2f), trazo("M14 14.66V17a1 1 0 0 0 1 1 2 2 0 0 1 2 2v2", 2f), trazo("M17.916 10H19.5A2.5 2.5 0 0 0 22 7.5V5a1 1 0 0 0-1-1h-3", 2f), trazo("M4 22h16", 2f), relleno("M6 9a6 6 0 0 0 12 0V3a1 1 0 0 0-1-1H7a1 1 0 0 0-1 1z"), trazo("M6.084 10H4.5A2.5 2.5 0 0 1 2 7.5V5a1 1 0 0 1 1-1h3", 2f)),
        "tocar" to dibujo("tocar", relleno("M5 5a2 2 0 0 1 3.008-1.728l11.997 6.998a2 2 0 0 1 .003 3.458l-12 7A2 2 0 0 1 5 19z")),
        "tinta" to dibujo("tinta", relleno("M12 22a7 7 0 0 0 7-7c0-2-1-3.9-3-5.5s-3.5-4-4-6.5c-.5 2.5-2 4.9-4 6.5C6 11.1 5 13 5 15a7 7 0 0 0 7 7z")),
        "sticker" to dibujo("sticker", relleno("M21 9a2.4 2.4 0 0 0-.706-1.706l-3.588-3.588A2.4 2.4 0 0 0 15 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2z"), trazo("M15 3v5a1 1 0 0 0 1 1h5", 2f), trazo("M8 13h.01", 2f), trazo("M16 13h.01", 2f), trazo("M10 16s.8 1 2 1c1.3 0 2-1 2-1", 2f)),
        "sonido" to dibujo("sonido", relleno("M11 4.702a.705.705 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.705.705 0 0 0 11 19.298z"), trazo("M16 9a5 5 0 0 1 0 6", 2f), trazo("M19.364 18.364a9 9 0 0 0 0-12.728", 2f)),
        "silencio" to dibujo("silencio", relleno("M11 4.702a.7.7 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.7.7 0 0 0 11 19.298z"), trazo("m16.5 14.5 5-5", 2f), trazo("m16.5 9.5 5 5", 2f)),
        "siguiente" to dibujo("siguiente", trazo("m9 18 6-6-6-6", 2f)),
        "serie" to dibujo("serie", trazo("m17 2-5 5-5-5", 2f), relleno("M4 7h16a2 2 0 0 1 2 2v11a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-11a2 2 0 0 1 2 -2z")),
        "repetir" to dibujo("repetir", trazo("M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8", 2f), trazo("M21 3v5h-5", 2f), trazo("M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16", 2f), trazo("M8 16H3v5", 2f)),
        "reloj" to dibujo("reloj", relleno("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z"), trazo("M12 6v6l4 2", 2f)),
        "perfil" to dibujo("perfil", relleno("M7 8a5 5 0 1 0 10 0a5 5 0 1 0 -10 0z"), trazo("M20 21a8 8 0 0 0-16 0", 2f)),
        "pelicula" to dibujo("pelicula", trazo("m12.296 3.464 3.02 3.956", 2f), relleno("M20.2 6 3 11l-.9-2.4c-.3-1.1.3-2.2 1.3-2.5l13.5-4c1.1-.3 2.2.3 2.5 1.3z"), relleno("M3 11h18v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"), trazo("m6.18 5.276 3.1 3.899", 2f)),
        "pausa" to dibujo("pausa", relleno("M15 3h3a1 1 0 0 1 1 1v16a1 1 0 0 1 -1 1h-3a1 1 0 0 1 -1 -1v-16a1 1 0 0 1 1 -1z"), relleno("M6 3h3a1 1 0 0 1 1 1v16a1 1 0 0 1 -1 1h-3a1 1 0 0 1 -1 -1v-16a1 1 0 0 1 1 -1z")),
        "ordenar" to dibujo("ordenar", trazo("m3 16 4 4 4-4", 2f), trazo("M7 20V4", 2f), trazo("M11 4h10", 2f), trazo("M11 8h7", 2f), trazo("M11 12h4", 2f)),
        "ojo" to dibujo("ojo", trazo("M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0", 2f), relleno("M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z")),
        "nube" to dibujo("nube", relleno("M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z")),
        "microfono" to dibujo("microfono", trazo("M12 19v3", 2f), trazo("M19 10v2a7 7 0 0 1-14 0v-2", 2f), relleno("M12 2h0a3 3 0 0 1 3 3v7a3 3 0 0 1 -3 3h-0a3 3 0 0 1 -3 -3v-7a3 3 0 0 1 3 -3z")),
        "mas" to dibujo("mas", trazo("M5 12h14", 2f), trazo("M12 5v14", 2f)),
        "lista" to dibujo("lista", trazo("M3 5h.01", 2f), trazo("M3 12h.01", 2f), trazo("M3 19h.01", 2f), trazo("M8 5h13", 2f), trazo("M8 12h13", 2f), trazo("M8 19h13", 2f)),
        "libro" to dibujo("libro", trazo("M12 5v16", 2f), relleno("M20.001 19A2 2 0 0022 17V5a2 2 0 00-1.999-2L16 3.002A5 5 0 0012 5a5 5 0 00-4-2H4a2 2 0 00-2 2v12a2 2 0 001.999 2H8a5 5 0 014 2 5 5 0 014-2z")),
        "lapiz" to dibujo("lapiz", relleno("M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z"), trazo("m15 5 4 4", 2f)),
        "juego" to dibujo("juego", trazo("M6 11L10 11", 2f), trazo("M8 9L8 13", 2f), trazo("M15 12L15.01 12", 2f), trazo("M18 10L18.01 10", 2f), relleno("M17.32 5H6.68a4 4 0 0 0-3.978 3.59c-.006.052-.01.101-.017.152C2.604 9.416 2 14.456 2 16a3 3 0 0 0 3 3c1 0 1.5-.5 2-1l1.414-1.414A2 2 0 0 1 9.828 16h4.344a2 2 0 0 1 1.414.586L17 18c.5.5 1 1 2 1a3 3 0 0 0 3-3c0-1.545-.604-6.584-.685-7.258-.007-.05-.011-.1-.017-.151A4 4 0 0 0 17.32 5z")),
        "inicio" to dibujo("inicio", trazo("M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8", 2f), relleno("M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z")),
        "imagen" to dibujo("imagen", relleno("M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2z"), relleno("M7 9a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z"), trazo("m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21", 2f)),
        "estrella" to dibujo("estrella", relleno("M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.123 2.123 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 3.638a2.123 2.123 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.122 2.122 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.122 2.122 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.122 2.122 0 0 0 1.597-1.16z")),
        "diario" to dibujo("diario", trazo("M13.4 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-7.4", 2f), trazo("M2 6h4", 2f), trazo("M2 10h4", 2f), trazo("M2 14h4", 2f), trazo("M2 18h4", 2f), relleno("M21.378 5.626a1 1 0 1 0-3.004-3.004l-5.01 5.012a2 2 0 0 0-.506.854l-.837 2.87a.5.5 0 0 0 .62.62l2.87-.837a2 2 0 0 0 .854-.506z")),
        "comentario" to dibujo("comentario", trazo("M2.992 16.342a2 2 0 0 1 .094 1.167l-1.065 3.29a1 1 0 0 0 1.236 1.168l3.413-.998a2 2 0 0 1 1.099.092 10 10 0 1 0-4.777-4.719", 2f)),
        "cifras" to dibujo("cifras", trazo("M16 7h6v6", 2f), trazo("m22 7-8.5 8.5-5-5L2 17", 2f)),
        "chulo" to dibujo("chulo", trazo("M20 6 9 17l-5-5", 2f)),
        "cerrar" to dibujo("cerrar", trazo("M18 6 6 18", 2f), trazo("m6 6 12 12", 2f)),
        "cajon" to dibujo("cajon", relleno("M4 3h6a1 1 0 0 1 1 1v16a1 1 0 0 1 -1 1h-6a1 1 0 0 1 -1 -1v-16a1 1 0 0 1 1 -1z"), trazo("M7 3v18", 2f), relleno("M20.4 18.9c.2.5-.1 1.1-.6 1.3l-1.9.7c-.5.2-1.1-.1-1.3-.6L11.1 5.1c-.2-.5.1-1.1.6-1.3l1.9-.7c.5-.2 1.1.1 1.3.6Z")),
        "borrar" to dibujo("borrar", trazo("M10 11v6", 2f), trazo("M14 11v6", 2f), trazo("M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6", 2f), trazo("M3 6h18", 2f), trazo("M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2", 2f)),
        "bandera" to dibujo("bandera", trazo("M4 22V4a1 1 0 0 1 .4-.8A6 6 0 0 1 8 2c3 0 5 2 7.333 2q2 0 3.067-.8A1 1 0 0 1 20 4v10a1 1 0 0 1-.4.8A6 6 0 0 1 16 16c-3 0-5-2-8-2a6 6 0 0 0-4 1.528", 2f)),
        "aviso" to dibujo("aviso", trazo("m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3", 2f), trazo("M12 9v4", 2f), trazo("M12 17h.01", 2f)),
        "artista" to dibujo("artista", trazo("m11 7.601-5.994 8.19a1 1 0 0 0 .1 1.298l.817.818a1 1 0 0 0 1.314.087L15.09 12", 2f), trazo("M16.5 21.174C15.5 20.5 14.372 20 13 20c-2.058 0-3.928 2.356-6 2-2.072-.356-2.775-3.369-1.5-4.5", 2f), relleno("M11 7a5 5 0 1 0 10 0a5 5 0 1 0 -10 0z")),
        "ajustes" to dibujo("ajustes", trazo("M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915", 2f), relleno("M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z")),
        "abajo" to dibujo("abajo", trazo("m6 9 6 6 6-6", 2f)),
        "atras" to dibujo("atras", trazo("m15 18-6-6 6-6", 2f)),
        "racha" to dibujo("racha", trazo("M12 3q1 4 4 6.5t3 5.5a1 1 0 0 1-14 0 5 5 0 0 1 1-3 1 1 0 0 0 5 0c0-2-1.5-3-1.5-5q0-2 2.5-4", 2f)),
        "moneda" to dibujo("moneda", trazo("M13.744 17.736a6 6 0 1 1-7.48-7.48", 2f), trazo("M15 6h1v4", 2f), trazo("m6.134 14.768.866-.5 2 3.464", 2f), relleno("M10 8a6 6 0 1 0 12 0a6 6 0 1 0 -12 0z")),
        "favorita" to dibujo("favorita", relleno("M11.562 3.266a.5.5 0 0 1 .876 0L15.39 8.87a1 1 0 0 0 1.516.294L21.183 5.5a.5.5 0 0 1 .798.519l-2.834 10.246a1 1 0 0 1-.956.734H5.81a1 1 0 0 1-.957-.734L2.02 6.02a.5.5 0 0 1 .798-.519l4.276 3.664a1 1 0 0 0 1.516-.294z"), trazo("M5 21h14", 2f)),
    )
}

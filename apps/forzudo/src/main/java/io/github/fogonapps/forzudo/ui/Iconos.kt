package io.github.fogonapps.forzudo.ui

import io.github.fogonapps.identidad.Dibujo
import io.github.fogonapps.identidad.IconosFogon
import io.github.fogonapps.identidad.dibujo
import io.github.fogonapps.identidad.hueco
import io.github.fogonapps.identidad.relleno
import io.github.fogonapps.identidad.trazo

/**
 * Los íconos de El Forzudo, gordos y líquidos como el personaje, para imprimirlos en dos tintas con
 * `IconoRiso` (ver docs/IDENTIDAD.md → Sistema riso → Íconos). Los de todas las apps están en [IconosFogon].
 */
object Iconos {
    val Inicio = IconosFogon.Inicio
    val Pesa = dibujo("entrenar", trazo("M7 6.5v11M17 6.5v11M4 9.2v5.6M20 9.2v5.6M7 12h10", 3.4f))
    val Rango = dibujo(
        "rangos", trazo("M8.5 3.5 12 9l3.5-5.5", 2.8f), relleno("M12 8.6a5.6 5.6 0 1 1 0 11.2 5.6 5.6 0 0 1 0-11.2z"),
        hueco("M12 11.4l1 2 2.2.3-1.6 1.5.4 2.2-2-1.1-2 1.1.4-2.2-1.6-1.5 2.2-.3z"),
    )
    val Persona = IconosFogon.Perfil
    val Racha = IconosFogon.Racha
    val Atras = IconosFogon.Atras
    val Abajo = IconosFogon.Abajo
    val Mas = IconosFogon.Mas
    val Cerrar = IconosFogon.Cerrar
    val Siguiente = IconosFogon.Siguiente
    val Chulo = IconosFogon.Chulo
    val Reloj = IconosFogon.Reloj
    val Trofeo = dibujo(
        "trofeo",
        relleno("M7 3h10v2h3v3a4 4 0 01-4 4h-.3A5 5 0 0113 14.9V17h3v3H8v-3h3v-2.1A5 5 0 018.3 12H8a4 4 0 01-4-4V5h3V3zm0 4H6v1a2 2 0 002 2V7zm10 0v3a2 2 0 002-2V7h-2z"),
    )

    /** Grupos musculares (miniatura cuando no hay foto). */
    val Grupo: Map<String, Dibujo> = mapOf(
        "pecho" to dibujo("pecho", relleno("M12 7.5C9.8 5.2 5.2 5.3 4.6 9c-.5 3 1.6 6 7.4 6.6zM12 7.5c2.2-2.3 6.8-2.2 7.4 1.5.5 3-1.6 6-7.4 6.6z"), trazo("M12 6v12", 2.6f)),
        "espalda" to dibujo("espalda", relleno("M4.5 5.5L12 8.5l7.5-3-3 13h-9z"), hueco("M11.2 9.5h1.6v8h-1.6z")),
        "hombro" to dibujo("hombro", relleno("M14.6 6.5a2.6 2.6 0 1 1-5.2 0a2.6 2.6 0 1 1 5.2 0z"), relleno("M4 17.4c0-4.4 3-6.8 8-6.8s8 2.4 8 6.8z")),
        "brazo" to dibujo("brazo", relleno("M4.5 19H11c4.6 0 7.5-3 7.5-6.8 0-2.3-1.5-3.8-3.7-3.8-1.5 0-2.3.7-3 1.6L10.3 5l-3 .8 1.5 6c-2.3.8-3.6 3-4.3 7.2z")),
        "pierna" to dibujo("pierna", relleno("M8.4 3h5.8l-.4 7.2c-.1 1.6.1 3 .6 4.4l1.2 3.4 3.6 1.2c.8.3 1.2.9 1.2 1.6H10.2l-.4-6.2c-.1-1.4-.6-2.6-1-4.2C8.2 8.4 8 6 8.4 3z")),
        "abdomen" to dibujo(
            "abdomen",
            relleno("M7 4.5h4.4v4H7z"), relleno("M12.6 4.5h4.4v4h-4.4z"), relleno("M7 10h4.4v4H7z"),
            relleno("M12.6 10h4.4v4h-4.4z"), relleno("M7 15.5h4.4v4H7z"), relleno("M12.6 15.5h4.4v4h-4.4z"),
        ),
    )
}

/** Los muñequitos del tutorial en capas, para teñirlos con las tintas del tema (identidad/vidrio/capas). */
val MUNIECOS: Map<Int, Triple<Int, Int, Int>> = mapOf(
    io.github.fogonapps.forzudo.R.drawable.munieco_tut_hola_t1 to Triple(io.github.fogonapps.forzudo.R.drawable.munieco_tut_hola_t1, io.github.fogonapps.forzudo.R.drawable.munieco_tut_hola_t2, io.github.fogonapps.forzudo.R.drawable.munieco_tut_hola_fijo),
    io.github.fogonapps.forzudo.R.drawable.munieco_tut_anota_t1 to Triple(io.github.fogonapps.forzudo.R.drawable.munieco_tut_anota_t1, io.github.fogonapps.forzudo.R.drawable.munieco_tut_anota_t2, io.github.fogonapps.forzudo.R.drawable.munieco_tut_anota_fijo),
    io.github.fogonapps.forzudo.R.drawable.munieco_tut_racha_t1 to Triple(io.github.fogonapps.forzudo.R.drawable.munieco_tut_racha_t1, io.github.fogonapps.forzudo.R.drawable.munieco_tut_racha_t2, io.github.fogonapps.forzudo.R.drawable.munieco_tut_racha_fijo),
    io.github.fogonapps.forzudo.R.drawable.munieco_tut_rangos_t1 to Triple(io.github.fogonapps.forzudo.R.drawable.munieco_tut_rangos_t1, io.github.fogonapps.forzudo.R.drawable.munieco_tut_rangos_t2, io.github.fogonapps.forzudo.R.drawable.munieco_tut_rangos_fijo),
    io.github.fogonapps.forzudo.R.drawable.munieco_tut_datos_t1 to Triple(io.github.fogonapps.forzudo.R.drawable.munieco_tut_datos_t1, io.github.fogonapps.forzudo.R.drawable.munieco_tut_datos_t2, io.github.fogonapps.forzudo.R.drawable.munieco_tut_datos_fijo),
)

/** Un muñequito del tutorial, teñido con las tintas del tema. */
@androidx.compose.runtime.Composable
fun Munieco(recurso: Int, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier, tamano: androidx.compose.ui.unit.Dp) {
    val c = MUNIECOS.getValue(recurso)
    io.github.fogonapps.identidad.PersonajeTenido(c.first, c.second, c.third, null, modifier, tamano)
}

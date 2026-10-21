package io.github.fogonapps.forzudo.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Íconos de trazo, con los mismos dibujos de la versión web (24×24). */
private fun trazo(nombre: String, ancho: Float, vararg d: String, relleno: Boolean = false): ImageVector {
    val b = ImageVector.Builder(nombre, 24.dp, 24.dp, 24f, 24f)
    d.forEach {
        val nodos = PathParser().parsePathString(it).toNodes()
        if (relleno) b.addPath(nodos, fill = SolidColor(Color.Black))
        else b.addPath(
            nodos, stroke = SolidColor(Color.Black), strokeLineWidth = ancho,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
    }
    return b.build()
}

object Iconos {
    val Inicio = trazo("inicio", 1.8f, "M4 10.5L12 4l8 6.5V20h-5.5v-5.5h-5V20H4z")
    val Pesa = trazo("pesa", 1.9f, "M2.5 10v4M5.5 7.5v9M18.5 7.5v9M21.5 10v4M5.5 12h13")
    val Rango = trazo("rango", 1.8f, "M12 2.8l7.5 3v5.6c0 4.8-3.2 8.3-7.5 9.8-4.3-1.5-7.5-5-7.5-9.8V5.8z", "M8.5 12.2l2.4 2.4 4.6-4.8")
    val Persona = trazo("persona", 1.8f, "M16 8a4 4 0 1 1-8 0a4 4 0 1 1 8 0z", "M4 20.5c1.2-4 4.3-6 8-6s6.8 2 8 6")
    val Atras = trazo("atras", 2.2f, "M15 5l-7 7 7 7")
    val Abajo = trazo("abajo", 2.2f, "M6 9l6 6 6-6")
    val Siguiente = trazo("siguiente", 2f, "M9 6l6 6-6 6")
    val Chulo = trazo("chulo", 3f, "M5 12.5l4.5 4.5L19 7.5")
    val Reloj = trazo("reloj", 2f, "M20 13a8 8 0 1 1-16 0a8 8 0 1 1 16 0z", "M12 9v4l2.5 2M9 2h6")
    val Trofeo = trazo(
        "trofeo", 0f,
        "M7 3h10v2h3v3a4 4 0 01-4 4h-.3A5 5 0 0113 14.9V17h3v3H8v-3h3v-2.1A5 5 0 018.3 12H8a4 4 0 01-4-4V5h3V3zm0 4H6v1a2 2 0 002 2V7zm10 0v3a2 2 0 002-2V7h-2z",
        relleno = true,
    )

    /** Grupos musculares (miniatura cuando no hay foto). */
    val Grupo: Map<String, ImageVector> = mapOf(
        "pecho" to trazo("pecho", 1.8f, "M12 6v12M12 7.5C9.8 5.2 5.2 5.3 4.6 9c-.5 3 1.6 6 7.4 6.6M12 7.5c2.2-2.3 6.8-2.2 7.4 1.5.5 3-1.6 6-7.4 6.6"),
        "espalda" to trazo("espalda", 1.8f, "M4.5 5.5L12 8.5l7.5-3-3 13h-9z", "M12 8.5v10"),
        "hombro" to trazo("hombro", 1.8f, "M14.6 6.5a2.6 2.6 0 1 1-5.2 0a2.6 2.6 0 1 1 5.2 0z", "M4 17c0-4.4 3-6.8 8-6.8s8 2.4 8 6.8"),
        "brazo" to trazo("brazo", 1.8f, "M4.5 19H11c4.6 0 7.5-3 7.5-6.8 0-2.3-1.5-3.8-3.7-3.8-1.5 0-2.3.7-3 1.6L10.3 5l-3 .8 1.5 6c-2.3.8-3.6 3-4.3 7.2z"),
        "pierna" to trazo("pierna", 1.8f, "M9 3c-.8 4.5 0 7.5 1.5 9.8L9.8 20.5h4.5l-.8-8.3c.8-3 .8-6 0-9.2", "M9.8 20.5h6"),
        "abdomen" to trazo(
            "abdomen", 1.6f,
            "M7 4.5h4.4v4H7z", "M12.6 4.5h4.4v4h-4.4z", "M7 10h4.4v4H7z", "M12.6 10h4.4v4h-4.4z", "M7 15.5h4.4v4H7z", "M12.6 15.5h4.4v4h-4.4z",
        ),
    )
}

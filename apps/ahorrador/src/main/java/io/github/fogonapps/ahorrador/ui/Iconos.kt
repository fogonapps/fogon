package io.github.fogonapps.ahorrador.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Íconos de trazo (24×24), dibujados en código para no depender de bibliotecas de íconos. */
private fun trazo(nombre: String, ancho: Float, vararg d: String): ImageVector {
    val b = ImageVector.Builder(nombre, 24.dp, 24.dp, 24f, 24f)
    d.forEach {
        b.addPath(
            PathParser().parsePathString(it).toNodes(), stroke = SolidColor(Color.Black), strokeLineWidth = ancho,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
    }
    return b.build()
}

object Iconos {
    val Inicio = trazo("inicio", 2f, "M4 10.5L12 4l8 6.5V20h-5.5v-5.5h-5V20H4z")
    val Resumen = trazo("resumen", 2.2f, "M5 19V11M12 19V5M19 19v-5")
    val Sobres = trazo("sobres", 2f, "M3 7h18v12H3zM3 7l9 6 9-6")
    val Moneda = trazo("moneda", 2f, "M20 12a8 8 0 1 1-16 0a8 8 0 1 1 16 0z", "M12 8v8M9.5 10.5h4a1.5 1.5 0 0 1 0 3h-3a1.5 1.5 0 0 0 0 3h4")
    val Mas = trazo("mas", 2.4f, "M12 5v14M5 12h14")
    val Atras = trazo("atras", 2.2f, "M15 5l-7 7 7 7")
    val Cerrar = trazo("cerrar", 2.2f, "M6 6l12 12M18 6L6 18")
    val Siguiente = trazo("siguiente", 2f, "M9 6l6 6-6 6")
    val Chulo = trazo("chulo", 3f, "M5 12.5l4.5 4.5L19 7.5")
    val Borrar = trazo("borrar", 1.8f, "M9 6h11v12H9l-6-6zM12 9.5l5 5M17 9.5l-5 5")
    val Gasto = trazo("gasto", 2.4f, "M7 17L17 7M9 7h8v8")
    val Ingreso = trazo("ingreso", 2.4f, "M17 7L7 17M15 17H7V9")
    val Pasar = trazo("pasar", 2.2f, "M5 9h13l-3-3M19 15H6l3 3")
    val Aviso = trazo("aviso", 2f, "M12 3l9.5 17h-19zM12 10v4.5M12 17.5v.5")
    val Campana = trazo("campana", 1.8f, "M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15zM10 20.5h4")
    val Persona = trazo("persona", 1.8f, "M16 8a4 4 0 1 1-8 0a4 4 0 1 1 8 0z", "M4 20.5c1.2-4 4.3-6 8-6s6.8 2 8 6")
    val Repetir = trazo("repetir", 2f, "M4 12a8 8 0 0 1 14-5.3M20 12a8 8 0 0 1-14 5.3M18 3v4h-4M6 21v-4h4")
    val Lapiz = trazo("lapiz", 2f, "M4 20h4L19 9l-4-4L4 16zM13.5 6.5l4 4")
    val Candado = trazo("candado", 2f, "M6 11h12v9H6zM8.5 11V8a3.5 3.5 0 0 1 7 0v3")

    /** Íconos de cuentas y categorías, por nombre (el que se guarda en Categoria.icono). */
    val porNombre: Map<String, ImageVector> = mapOf(
        "efectivo" to trazo("efectivo", 2f, "M3 7h18v10H3zM14.5 12a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0z"),
        "billetera" to trazo("billetera", 2f, "M6 3h12v18H6zM10.5 17.5h3"),
        "banco" to trazo("banco", 2f, "M3 10l9-6 9 6M5 10v8M19 10v8M9 10v8M15 10v8M3 20h18"),
        "tarjeta" to trazo("tarjeta", 2f, "M3 5h18v14H3zM3 10h18M7 15h4"),
        "casa" to trazo("casa", 2f, "M4 10.5L12 4l8 6.5V20H4z"),
        "mercado" to trazo("mercado", 2f, "M3 5h3l2 10h10l2-7H7M10 19.5a1 1 0 1 1-2 0a1 1 0 1 1 2 0M18 19.5a1 1 0 1 1-2 0a1 1 0 1 1 2 0"),
        "servicios" to trazo("servicios", 2f, "M13 3L5 13h6l-1 8 8-10h-6z"),
        "transporte" to trazo("transporte", 2f, "M5 16V8a3 3 0 0 1 3-3h8a3 3 0 0 1 3 3v8zM5 12h14M8 16v3M16 16v3"),
        "salud" to trazo("salud", 2f, "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"),
        "comida" to trazo("comida", 2f, "M4 11h16M6 11c0 4 2.5 7 6 7s6-3 6-7M9 7c0-1.5 1-2.5 3-2.5s3 1 3 2.5"),
        "salidas" to trazo("salidas", 2f, "M8 4h8l-1 7a3 3 0 0 1-6 0zM12 14v6M9 20h6"),
        "compras" to trazo("compras", 2f, "M5 8h14l-1 12H6zM9 8a3 3 0 0 1 6 0"),
        "cultura" to trazo("cultura", 2f, "M4 5h6a2 2 0 0 1 2 2v12a2 2 0 0 0-2-2H4zM20 5h-6a2 2 0 0 0-2 2v12a2 2 0 0 1 2-2h6z"),
        "estudio" to trazo("estudio", 2f, "M2 9l10-5 10 5-10 5zM6 11v5c3 2 9 2 12 0v-5"),
        "imprevisto" to trazo("imprevisto", 2f, "M12 3l9.5 17h-19zM12 10v4.5M12 17.5v.5"),
        "deudas" to trazo("deudas", 2f, "M4 7h16v10H4zM8 12h8M12 9v6"),
        "impuestos" to trazo("impuestos", 2f, "M6 3h12v18l-3-2-3 2-3-2-3 2zM9 8h6M9 12h6"),
        "otros" to trazo("otros", 2.4f, "M6 12h.01M12 12h.01M18 12h.01"),
        "sueldo" to trazo("sueldo", 2f, "M4 7h16v12H4zM9 7V5h6v2M4 12h16"),
        "prima" to trazo("prima", 2f, "M4 10h16v10H4zM12 10v10M3 7h18v3H3zM12 7C10 3 7 4 8 6M12 7c2-4 5-3 4-1"),
        "extra" to trazo("extra", 2f, "M12 4l2.5 5 5.5.8-4 3.9 1 5.5-5-2.7-5 2.7 1-5.5-4-3.9 5.5-.8z"),
        "moneda" to Moneda,
        "viaje" to trazo("viaje", 2f, "M3 18c3-1 5-1 9 0s6 1 9 0M12 4c3 2 4 5 3 9M12 4c-3 2-4 5-3 9M12 4v14"),
        "celular" to trazo("celular", 2f, "M6 3h12v18H6zM10.5 17.5h3"),
    )

    fun de(nombre: String): ImageVector = porNombre[nombre] ?: Moneda
}

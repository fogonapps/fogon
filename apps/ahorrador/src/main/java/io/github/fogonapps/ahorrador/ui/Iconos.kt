package io.github.fogonapps.ahorrador.ui

import io.github.fogonapps.identidad.Dibujo
import io.github.fogonapps.identidad.IconosFogon
import io.github.fogonapps.identidad.dibujo
import io.github.fogonapps.identidad.hueco
import io.github.fogonapps.identidad.huecoTrazo
import io.github.fogonapps.identidad.relleno
import io.github.fogonapps.identidad.trazo

/** Un ícono de trazos gordos de punta redonda (24×24). */
private fun gordo(nombre: String, ancho: Float, vararg d: String): Dibujo = dibujo(nombre, *d.map { trazo(it, ancho) }.toTypedArray())

/**
 * Los íconos de El Ahorrador, gordos y llenos como el personaje, para imprimirlos en dos tintas con
 * `IconoRiso` (ver docs/IDENTIDAD.md → Sistema riso → Íconos). Los de todas las apps están en [IconosFogon].
 */
object Iconos {
    val Inicio = IconosFogon.Inicio
    val Resumen = gordo("resumen", 3.4f, "M5.5 19V11M12 19V5M18.5 19v-5")
    val Sobres = dibujo("sobres", relleno("M3 6.5h18v12.5H3z"), huecoTrazo("M3.6 7.4 12 13l8.4-5.6", 2f))
    val Moneda = IconosFogon.Moneda
    val Mas = IconosFogon.Mas
    val Atras = IconosFogon.Atras
    val Cerrar = IconosFogon.Cerrar
    val Siguiente = IconosFogon.Siguiente
    val Chulo = IconosFogon.Chulo
    val Borrar = IconosFogon.Borrar
    val Gasto = gordo("gasto", 3.2f, "M7 17L17 7M9 7h8v8")
    val Ingreso = gordo("ingreso", 3.2f, "M17 7L7 17M15 17H7V9")
    val Pasar = gordo("pasar", 2.8f, "M5 9h13l-3-3M19 15H6l3 3")
    val Aviso = IconosFogon.Aviso
    val Campana = IconosFogon.Campana
    val Persona = IconosFogon.Perfil
    val Repetir = IconosFogon.Repetir
    val Lapiz = IconosFogon.Lapiz
    val Candado = IconosFogon.Candado

    /** Íconos de cuentas y categorías, por nombre (el que se guarda en Categoria.icono). */
    val porNombre: Map<String, Dibujo> = mapOf(
        "efectivo" to dibujo("efectivo", relleno("M2.6 6.6h18.8v10.8H2.6z"), hueco("M14.6 12a2.6 2.6 0 1 1-5.2 0a2.6 2.6 0 1 1 5.2 0z")),
        "billetera" to dibujo("billetera", relleno("M3.5 7h15a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-15z"), relleno("M3.5 7 15 3.6V7z"), hueco("M15 12.2h5.5v3.6H15z")),
        "banco" to dibujo("banco", relleno("M2.8 10 12 3.6 21.2 10z"), gordoParte("M5.5 11.5v6.5M10 11.5v6.5M14 11.5v6.5M18.5 11.5v6.5"), relleno("M2.8 18.8h18.4v2.2H2.8z")),
        "tarjeta" to dibujo("tarjeta", relleno("M2.6 5h18.8v14H2.6z"), hueco("M2.6 8.6h18.8v2.6H2.6z"), huecoTrazo("M6.6 15h4", 2f)),
        "casa" to dibujo("casa", relleno("M3.6 10.6 12 3.8l8.4 6.8V20H3.6z")),
        "mercado" to dibujo("mercado", trazo("M3 5h3l2 10h10l2-7H7", 2.8f), relleno("M10.2 19.5a1.4 1.4 0 1 1-2.8 0 1.4 1.4 0 0 1 2.8 0z"), relleno("M18.2 19.5a1.4 1.4 0 1 1-2.8 0 1.4 1.4 0 0 1 2.8 0z")),
        "servicios" to dibujo("servicios", relleno("M13.4 2.6 4.6 13.4h6.2L9.8 21.4l8.8-10.8h-6.2z")),
        "transporte" to dibujo("transporte", relleno("M5 16.4V8a3 3 0 0 1 3-3h8a3 3 0 0 1 3 3v8.4z"), hueco("M7 8h10v4H7z"), trazo("M8 16.4v3M16 16.4v3", 2.8f)),
        "salud" to dibujo("salud", relleno("M12 20.4s-7.4-4.6-7.4-10.4a4.2 4.2 0 0 1 7.4-2.7 4.2 4.2 0 0 1 7.4 2.7c0 5.8-7.4 10.4-7.4 10.4z"), huecoTrazo("M12 9.6v5M9.5 12.1h5", 2f)),
        "comida" to dibujo("comida", relleno("M3.6 10.6h16.8c0 4.6-3.2 8-8.4 8s-8.4-3.4-8.4-8z"), trazo("M9 7c0-1.5 1-2.5 3-2.5s3 1 3 2.5", 2.6f)),
        "salidas" to dibujo("salidas", relleno("M7.6 3.6h8.8l-1.1 7.2a3.3 3.3 0 0 1-6.6 0z"), trazo("M12 14.4v6M9 20.4h6", 2.6f)),
        "compras" to dibujo("compras", relleno("M4.6 8h14.8l-1 12.4H5.6z"), trazo("M9 8.4V7.6a3 3 0 0 1 6 0v.8", 2.6f)),
        "cultura" to dibujo("cultura", relleno("M3.4 5h6.4a2.2 2.2 0 0 1 2.2 2.2V19.6a2.2 2.2 0 0 0-2.2-2.2H3.4z"), relleno("M20.6 5h-6.4A2.2 2.2 0 0 0 12 7.2V19.6a2.2 2.2 0 0 1 2.2-2.2h6.4z")),
        "estudio" to dibujo("estudio", relleno("M1.6 9 12 3.8 22.4 9 12 14.2z"), relleno("M6 11.4v4.8c3 2.2 9 2.2 12 0v-4.8L12 14.4z")),
        "imprevisto" to IconosFogon.Aviso,
        "deudas" to dibujo("deudas", relleno("M3.4 6.4h17.2v11.2H3.4z"), huecoTrazo("M8 12h8M12 9v6", 2.2f)),
        "impuestos" to dibujo("impuestos", relleno("M5.6 2.8h12.8v18.6l-3.2-2-3.2 2-3.2-2-3.2 2z"), huecoTrazo("M9 8h6M9 12h6", 2f)),
        "otros" to gordo("otros", 3.6f, "M6 12h.01M12 12h.01M18 12h.01"),
        "sueldo" to dibujo("sueldo", relleno("M3.4 7h17.2v12.4H3.4z"), trazo("M9 7V5h6v2", 2.6f), huecoTrazo("M3.4 12h17.2", 1.8f)),
        "prima" to dibujo("prima", relleno("M4.4 10.4h15.2v10H4.4z"), relleno("M3 7h18v3.4H3z"), huecoTrazo("M12 10.4v10", 2f), trazo("M12 7C10 3 7 4 8 6M12 7c2-4 5-3 4-1", 2.4f)),
        "extra" to dibujo("extra", relleno("M12 3.4l2.6 5.2 5.8.8-4.2 4.1 1 5.8L12 16.6l-5.2 2.7 1-5.8-4.2-4.1 5.8-.8z")),
        "moneda" to Moneda,
        "viaje" to gordo("viaje", 2.6f, "M3 18c3-1 5-1 9 0s6 1 9 0M12 4c3 2 4 5 3 9M12 4c-3 2-4 5-3 9M12 4v14"),
        "celular" to dibujo("celular", relleno("M6.4 2.6h11.2a1.6 1.6 0 0 1 1.6 1.6v15.6a1.6 1.6 0 0 1-1.6 1.6H6.4a1.6 1.6 0 0 1-1.6-1.6V4.2a1.6 1.6 0 0 1 1.6-1.6z"), huecoTrazo("M10.5 18h3", 2f)),
    )

    fun de(nombre: String): Dibujo = porNombre[nombre] ?: Moneda
}

private fun gordoParte(d: String) = trazo(d, 2.8f)

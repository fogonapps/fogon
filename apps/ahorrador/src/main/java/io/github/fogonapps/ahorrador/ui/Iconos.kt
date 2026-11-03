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

    /** Íconos de cuentas, categorías y metas, por nombre (el que se guarda en Categoria.icono y Meta.icono). */
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
        "viaje" to dibujo("viaje", relleno("M20.6 3.4c-.9-.9-2.4-.7-3.4.3l-3.3 3.3-8.4-2.4-1.8 1.8 6.8 4-3.3 3.3-2.5-.4-1.4 1.4 3.3 1.9 1.9 3.3 1.4-1.4-.4-2.5 3.3-3.3 4 6.8 1.8-1.8-2.4-8.4 3.3-3.3c1-1 1.2-2.5.3-3.4z")),
        "celular" to dibujo("celular", relleno("M6.4 2.6h11.2a1.6 1.6 0 0 1 1.6 1.6v15.6a1.6 1.6 0 0 1-1.6 1.6H6.4a1.6 1.6 0 0 1-1.6-1.6V4.2a1.6 1.6 0 0 1 1.6-1.6z"), huecoTrazo("M10.5 18h3", 2f)),
        "mascota" to dibujo("mascota", relleno("M12 11.6c3 0 5.4 2.6 5.4 5 0 1.7-1.4 2.6-2.8 2.6-1.1 0-1.7-.5-2.6-.5s-1.5.5-2.6.5c-1.4 0-2.8-.9-2.8-2.6 0-2.4 2.4-5 5.4-5z"), relleno("M3.8 10.4a1.8 1.8 0 1 0 3.6 0a1.8 1.8 0 1 0 -3.6 0z"), relleno("M7.3 6.4a1.9 1.9 0 1 0 3.8 0a1.9 1.9 0 1 0 -3.8 0z"), relleno("M12.9 6.4a1.9 1.9 0 1 0 3.8 0a1.9 1.9 0 1 0 -3.8 0z"), relleno("M16.6 10.4a1.8 1.8 0 1 0 3.6 0a1.8 1.8 0 1 0 -3.6 0z")),
        "ejercicio" to dibujo("ejercicio", relleno("M3.6 9.6h2.6v4.8H3.6z"), relleno("M17.8 9.6h2.6v4.8h-2.6z"), relleno("M6.2 7h2.8v10H6.2z"), relleno("M15 7h2.8v10H15z"), trazo("M9 12h6", 2.6f)),
        "cafe" to dibujo("cafe", relleno("M4.4 9h11v5.4a4.6 4.6 0 0 1-4.6 4.6H9a4.6 4.6 0 0 1-4.6-4.6z"), trazo("M15.4 10.6h1.4a2.5 2.5 0 0 1 0 5h-1.6", 2.4f), trazo("M8.4 3.6c-.8 1 .8 2 0 3M12 3.6c-.8 1 .8 2 0 3", 2f)),
        "ropa" to dibujo("ropa", relleno("M8.6 3.8 3.4 6.6l1.8 3.6 2.6-1V20.2h8.4V9.2l2.6 1 1.8-3.6-5.2-2.8c-.5 1.4-1.8 2.2-3.4 2.2s-2.9-.8-3.4-2.2z")),
        "internet" to dibujo("internet", trazo("M3.6 9.4a12 12 0 0 1 16.8 0M6.6 12.7a7.6 7.6 0 0 1 10.8 0M9.6 16a3.4 3.4 0 0 1 4.8 0", 2.6f), relleno("M10.6 19.4a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0z")),
        "musica" to dibujo("musica", relleno("M5.2 17.6a2.6 2.6 0 1 0 5.2 0a2.6 2.6 0 1 0 -5.2 0z"), relleno("M14.4 15.6a2.6 2.6 0 1 0 5.2 0a2.6 2.6 0 1 0 -5.2 0z"), trazo("M10.4 17.6V5.8l9.2-2.2v12", 2.4f)),
        "juegos" to dibujo("juegos", relleno("M7.2 7.8h9.6a4.6 4.6 0 0 1 4.4 5.9l-1 3.4a2.2 2.2 0 0 1-3.8.8L14.6 16H9.4l-1.8 1.9a2.2 2.2 0 0 1-3.8-.8l-1-3.4a4.6 4.6 0 0 1 4.4-5.9z"), huecoTrazo("M7.6 10.6v3.2M6 12.2h3.2", 1.8f), hueco("M15.1 12.2a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 1 0 -2.2 0z")),
        "regalo" to dibujo("regalo", relleno("M4.4 10.4h15.2v10H4.4z"), relleno("M3 7h18v3.4H3z"), huecoTrazo("M12 7v13.4", 2f), trazo("M12 7C10 3 7 4 8 6M12 7c2-4 5-3 4-1", 2.4f)),
    )

    fun de(nombre: String): Dibujo = porNombre[nombre] ?: Moneda
}

private fun gordoParte(d: String) = trazo(d, 2.8f)

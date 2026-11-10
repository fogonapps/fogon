package io.github.fogonapps.social

const val POLITICA_VIGENTE = "2026-11-13"

fun debeAceptarPolitica(sesion: Boolean, aceptada: String?, vigente: String): Boolean =
    sesion && aceptada != vigente

/** La gestión de la cuenta sigue disponible para aceptar, salir o ejercer el borrado. */
fun rutaPermitidaSinPolitica(ruta: String): Boolean =
    ruta == "/estado" || ruta == "/cuenta" || ruta.startsWith("/cuenta/") ||
        ruta == "/salir" || ruta.startsWith("/acceso/")

/** Revisar el servidor al abrir, como mucho una vez por minuto (0 = nunca se revisó). */
fun debeRevisarAlAbrir(ultima: Long, ahora: Long): Boolean = ultima == 0L || ahora - ultima >= 60_000L

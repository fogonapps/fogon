package io.github.fogonapps.cajon.datos

const val POLITICA_VIGENTE = "2026-11-08"

fun debeAceptarPolitica(sesion: Boolean, aceptada: String?, vigente: String): Boolean =
    sesion && aceptada != vigente

/** La gestión de la cuenta sigue disponible para aceptar, salir o ejercer el borrado. */
fun rutaPermitidaSinPolitica(ruta: String): Boolean =
    ruta == "/estado" || ruta == "/cuenta" || ruta.startsWith("/cuenta/") ||
        ruta == "/salir" || ruta.startsWith("/acceso/")

/** Estado efectivo; conectar una opción no sustituye permisos ni una sesión vigente. */
data class OpcionesPrivacidad(
    val spotify: Boolean, val github: Boolean, val leerNotificaciones: Boolean,
    val avisos: Boolean, val copiaCifrada: Boolean,
)

fun opcionesPrivacidad(spotify: Boolean, github: Boolean, permisoLectura: Boolean,
    permisoAvisos: Boolean, avisos: Boolean, mensajes: Boolean, sesion: Boolean, llave: Boolean) =
    OpcionesPrivacidad(spotify, github, permisoLectura, permisoAvisos && sesion && (avisos || mensajes), sesion && llave)

/** Revisar el servidor al abrir, como mucho una vez por minuto (0 = nunca se revisó). */
fun debeRevisarAlAbrir(ultima: Long, ahora: Long): Boolean = ultima == 0L || ahora - ultima >= 60_000L

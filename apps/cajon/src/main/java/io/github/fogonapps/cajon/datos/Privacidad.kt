package io.github.fogonapps.cajon.datos

/** Estado efectivo; conectar una opción no sustituye permisos ni una sesión vigente. */
data class OpcionesPrivacidad(
    val spotify: Boolean, val leerNotificaciones: Boolean,
    val avisos: Boolean, val copiaCifrada: Boolean,
)

fun opcionesPrivacidad(spotify: Boolean, permisoLectura: Boolean,
    permisoAvisos: Boolean, avisos: Boolean, mensajes: Boolean, sesion: Boolean, llave: Boolean) =
    OpcionesPrivacidad(spotify, permisoLectura, permisoAvisos && sesion && (avisos || mensajes), sesion && llave)

const val POLITICA_VIGENTE = io.github.fogonapps.social.POLITICA_VIGENTE
fun debeAceptarPolitica(sesion: Boolean, aceptada: String?, vigente: String) = io.github.fogonapps.social.debeAceptarPolitica(sesion, aceptada, vigente)
fun rutaPermitidaSinPolitica(ruta: String) = io.github.fogonapps.social.rutaPermitidaSinPolitica(ruta)
fun debeRevisarAlAbrir(ultima: Long, ahora: Long) = io.github.fogonapps.social.debeRevisarAlAbrir(ultima, ahora)

package io.github.fogonapps.social

/** Credenciales de un único perfil local. La app conserva el cifrado y las ubicaciones persistidas. */
interface AlmacenSesionSocial {
    var token: String?
    var politicaAceptada: String?
    val idCuenta: String
    val sesion: SesionCuenta
    fun guardarIdCuenta(idCuenta: String)
}

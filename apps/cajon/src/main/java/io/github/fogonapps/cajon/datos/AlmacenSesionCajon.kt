package io.github.fogonapps.cajon.datos

import io.github.fogonapps.social.AlmacenSesionSocial
import io.github.fogonapps.social.SesionCuenta

/** Delega las credenciales a la caja existente y la identidad estable al registro de perfiles. */
class AlmacenSesionCajon(
    private val caja: CajaFuerte,
    private val leerIdCuenta: () -> String,
    private val guardarIdentidad: (String) -> Unit,
) : AlmacenSesionSocial {
    override var token: String?
        get() = caja.tokenSocial
        set(valor) { caja.tokenSocial = valor }
    override var politicaAceptada: String?
        get() = caja.politicaSocial
        set(valor) { caja.politicaSocial = valor }
    override val idCuenta: String get() = leerIdCuenta()
    override val sesion = SesionCuenta { token }
    override fun guardarIdCuenta(idCuenta: String) = guardarIdentidad(idCuenta)
}

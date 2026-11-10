package io.github.fogonapps.forzudo.datos

import android.content.Context
import io.github.fogonapps.social.*

/** La credencial social usa el mismo cifrado del Gist, con preferencias y alias propios. */
class SesionForzudo(contexto: Context, private val perfiles: Perfiles, val perfil: String) : AlmacenSesionSocial {
    private val caja = CajaFuerte(contexto, "social-$perfil")
    private val preferencias = contexto.getSharedPreferences("social-p$perfil", Context.MODE_PRIVATE)
    override var token: String?
        get() = caja.token
        set(valor) { caja.token = valor }
    var usuario: String?
        get() = preferencias.getString("usuario", null)
        set(valor) { check(preferencias.edit().putString("usuario", valor).commit()) }
    override var politicaAceptada: String?
        get() = preferencias.getString("politica", null)
        set(valor) { check(preferencias.edit().putString("politica", valor).commit()) }
    fun visibilidad(clave: String) = preferencias.getString("visibilidad.$clave", "privado") ?: "privado"
    fun guardarVisibilidad(clave: String, valor: String) { check(preferencias.edit().putString("visibilidad.$clave", valor).commit()) }
    override val idCuenta get() = perfiles.lista.perfiles.first { it.id == perfil }.idCuenta
    override val sesion = SesionCuenta { token }
    override fun guardarIdCuenta(idCuenta: String) { check(perfiles.completarIdCuenta(perfil, idCuenta)) }
}

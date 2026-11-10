package io.github.fogonapps.cajon.datos

internal class SesionCuenta(token: () -> String?) {
    class Marca internal constructor(internal val comun: io.github.fogonapps.social.SesionCuenta.Marca)
    private val sesion = io.github.fogonapps.social.SesionCuenta(token)
    fun actual() = Marca(sesion.actual())
    fun vigente(marca: Marca) = sesion.vigente(marca.comun)
    fun invalidar() = sesion.invalidar()
    suspend fun intentar(inicio: Marca = actual(), trabajo: suspend () -> Unit, alError: (java.io.IOException) -> Unit) =
        sesion.intentar(inicio.comun, trabajo, alError)
}

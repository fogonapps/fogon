package io.github.fogonapps.social

/** Un error tardío solo pertenece a la sesión que inició la operación. */
class SesionCuenta(private val token: () -> String?) {
    class Marca internal constructor(internal val generacion: Long, internal val token: String?)
    private var generacion = 0L

    fun actual() = Marca(generacion, token())
    fun vigente(marca: Marca) = marca.generacion == generacion && marca.token == token()
    fun invalidar() { generacion++ }

    suspend fun intentar(inicio: Marca = actual(), trabajo: suspend () -> Unit, alError: (java.io.IOException) -> Unit): Boolean {
        return try {
            trabajo()
            vigente(inicio)
        } catch (x: java.io.IOException) {
            if (vigente(inicio)) alError(x)
            false
        }
    }
}


package io.github.fogonapps.cucha.datos

import io.github.fogonapps.identidad.PerfilDeApp

/** De qué cuaderno de El Forzudo lee La Cucha el peso y los entrenos. */
sealed interface EnlaceForzudo {
    /** El Forzudo no tiene perfiles (versión anterior): se lee su cuaderno, como antes. */
    data object Unico : EnlaceForzudo
    data class Perfil(val id: String) : EnlaceForzudo
    /** Hay varios perfiles y ninguno es claramente el de esta persona: se pregunta en Ajustes. */
    data object Elegir : EnlaceForzudo
    /** No se lee nada (la persona eligió «ninguno», o son cuentas distintas). */
    data object Ninguno : EnlaceForzudo
}

/** Lo que se guarda cuando la persona dice que ningún perfil de El Forzudo es el suyo. */
const val FORZUDO_NINGUNO = "ninguno"

/**
 * Nunca se asume el mismo número de perfil: primero la misma cuenta ([idCuenta]), después lo que la persona
 * eligió ([elegido]) y, solo si las dos apps tienen un único perfil y no son cuentas distintas, ese.
 */
fun enlaceForzudo(forzudo: List<PerfilDeApp>?, idCuenta: String, elegido: String?, perfilesCucha: Int): EnlaceForzudo {
    if (forzudo == null) return if (perfilesCucha == 1) EnlaceForzudo.Unico else EnlaceForzudo.Ninguno
    if (forzudo.isEmpty()) return EnlaceForzudo.Ninguno
    if (idCuenta.isNotBlank()) forzudo.firstOrNull { it.idCuenta == idCuenta }?.let { return EnlaceForzudo.Perfil(it.id) }
    if (elegido == FORZUDO_NINGUNO) return EnlaceForzudo.Ninguno
    if (elegido != null) forzudo.firstOrNull { it.id == elegido }?.let { return EnlaceForzudo.Perfil(it.id) }
    if (forzudo.size == 1 && perfilesCucha == 1) {
        val unico = forzudo.single()
        val otraCuenta = idCuenta.isNotBlank() && unico.idCuenta.isNotBlank() && unico.idCuenta != idCuenta
        return if (otraCuenta) EnlaceForzudo.Ninguno else EnlaceForzudo.Perfil(unico.id)
    }
    return EnlaceForzudo.Elegir
}

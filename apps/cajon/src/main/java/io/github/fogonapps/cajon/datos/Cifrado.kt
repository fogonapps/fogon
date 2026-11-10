package io.github.fogonapps.cajon.datos

import kotlinx.serialization.Serializable

object Cifrado {
    @Serializable data class Propias(val cifrar: String, val firmar: String)
    private fun Propias.comunes() = io.github.fogonapps.social.Cifrado.Propias(cifrar, firmar)
    private fun LlavesPublicas.comunes() = io.github.fogonapps.social.LlavesPublicas(cifrar, firmar)
    fun nuevas() = io.github.fogonapps.social.Cifrado.nuevas().let { Propias(it.cifrar, it.firmar) }
    fun publicas(p: Propias) = io.github.fogonapps.social.Cifrado.publicas(p.comunes()).let { LlavesPublicas(it.cifrar, it.firmar) }
    fun cerrar(texto: ByteArray, de: String, para: String, suyas: LlavesPublicas, mias: Propias) =
        io.github.fogonapps.social.Cifrado.cerrar(texto, de, para, suyas.comunes(), mias.comunes())
    fun abrir(sobreB64: String, firmaB64: String, de: String, para: String, suyas: LlavesPublicas, mias: Propias) =
        io.github.fogonapps.social.Cifrado.abrir(sobreB64, firmaB64, de, para, suyas.comunes(), mias.comunes())
    fun numeroSeguridad(a: LlavesPublicas, b: LlavesPublicas) = io.github.fogonapps.social.Cifrado.numeroSeguridad(a.comunes(), b.comunes())
}

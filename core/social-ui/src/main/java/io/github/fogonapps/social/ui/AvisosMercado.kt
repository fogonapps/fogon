package io.github.fogonapps.social.ui

import android.content.Context
import io.github.fogonapps.identidad.formatearMiles
import io.github.fogonapps.social.AvisoSocial

internal fun idArticuloMercado(item: String): Long? {
    val numero = when {
        item.startsWith("banner:m") -> item.removePrefix("banner:m")
        item.startsWith("marco:m") -> item.removePrefix("marco:m")
        else -> return null
    }
    return numero.toLongOrNull()?.takeIf { it > 0 }
}

private val tiposAvisoMercado = setOf("regalo", "mercado_venta", "mercado_aprobado", "mercado_rechazado")

internal fun articuloDelAviso(aviso: AvisoSocial): Long? =
    if (aviso.tipo in tiposAvisoMercado) idArticuloMercado(aviso.objeto.substringBefore('|')) else null

internal fun textoAvisoMercado(contexto: Context, aviso: AvisoSocial): String? {
    if (aviso.tipo !in tiposAvisoMercado) return null
    val partes = aviso.objeto.split('|', limit = 3)
    val nombre = partes.getOrNull(1)?.takeIf { it.isNotBlank() } ?: partes.first()
    val extra = partes.getOrNull(2).orEmpty()
    return when (aviso.tipo) {
        "regalo" -> contexto.getString(
            if (partes.size == 1) R.string.social_aviso_regalo else R.string.social_aviso_regalo_nombre,
            aviso.de.usuario, nombre,
        )
        "mercado_venta" -> if (extra.toIntOrNull() == null) {
            contexto.getString(R.string.social_aviso_mercado_venta_sin_monto, aviso.de.usuario, nombre)
        } else contexto.getString(
            R.string.social_aviso_mercado_venta, aviso.de.usuario, nombre,
            formatearMiles(extra, contexto.resources.configuration.locales[0]),
        )
        "mercado_aprobado" -> contexto.getString(R.string.social_aviso_mercado_aprobado, nombre)
        else -> contexto.getString(
            if (extra.isBlank()) R.string.social_aviso_mercado_rechazado_sin_motivo else R.string.social_aviso_mercado_rechazado,
            nombre, extra,
        )
    }
}

package io.github.fogonapps.perfil

const val LIMITE_NOMBRE_PERFIL = 40
const val LIMITE_BIO_PERFIL = 200

/** Cuenta puntos de código para conservar las letras fuera del plano básico de Unicode. */
fun recortarNombrePerfil(texto: String): String =
    if (texto.codePointCount(0, texto.length) <= LIMITE_NOMBRE_PERFIL) texto
    else texto.substring(0, texto.offsetByCodePoints(0, LIMITE_NOMBRE_PERFIL))

fun recortarBioPerfil(texto: String): String = texto.take(LIMITE_BIO_PERFIL)

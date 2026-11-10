package io.github.fogonapps.social

/** La gracia exige una decisión antes de recuperar o sincronizar datos de la cuenta. */
fun debePreguntarBorrado(borrarEl: Long): Boolean = borrarEl > 0

fun credencialBorradoValida(conClave: Boolean, clave: String, codigo: String, texto: String): Boolean =
    texto.length <= 300 && if (conClave) clave.isNotEmpty() && clave.length <= 200
    else codigo.length == 6 && codigo.all { it in '0'..'9' }

fun correoNuevoValido(nuevo: String, actual: String): Boolean =
    nuevo.length <= 120 && Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+").matches(nuevo.trim()) && !nuevo.trim().equals(actual, ignoreCase = true)

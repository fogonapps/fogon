package io.github.fogonapps.social

/** Cómo va entrar a la red: pedir el correo, poner el código, elegir el usuario (cuenta nueva). */
/**
 * El correo lleva siempre al código. La respuesta cuenta_nueva continúa al registro;
 * la contraseña es un camino independiente y también permite recuperar por código.
 */
enum class PasoAcceso { CORREO, CLAVE, CODIGO, USUARIO }

/** Para qué es el código: cuenta nueva, olvidó la contraseña o la cuenta no tenía (en esos dos se pone una nueva). */
enum class MotivoCodigo { NUEVA, OLVIDO, SIN_CLAVE }

data class EstadoAcceso(
    val paso: PasoAcceso = PasoAcceso.CORREO, val correo: String = "", val codigo: String = "", val ocupado: Boolean = false,
    val motivo: MotivoCodigo = MotivoCodigo.NUEVA,
)


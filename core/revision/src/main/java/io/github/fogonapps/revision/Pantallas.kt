package io.github.fogonapps.revision

import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor

/**
 * Todas las pantallas que declara una app (las clases de su `sealed interface Pantalla`), armadas con valores de
 * ejemplo: así el recorrido no se olvida de ninguna nueva. [ejemplo] da el valor de un parámetro que no tiene uno por
 * defecto (por ejemplo, el id de una obra del estado de ejemplo); si devuelve null y el parámetro admite null, va null.
 */
fun <T : Any> pantallasDe(tipo: KClass<T>, ejemplo: (KParameter) -> Any? = { null }): List<T> =
    tipo.sealedSubclasses.flatMap { c ->
        c.objectInstance?.let { return@flatMap listOf(it) }
        if (c.isSealed) return@flatMap pantallasDe(c, ejemplo)
        val k = c.primaryConstructor ?: return@flatMap emptyList()
        val args = mutableMapOf<KParameter, Any?>()
        for (p in k.parameters) {
            if (p.isOptional) continue
            val v = ejemplo(p) ?: when (p.type.classifier) {
                String::class -> "x"; Long::class -> 1L; Int::class -> 0; Boolean::class -> false; Double::class -> 0.0; Float::class -> 0f
                else -> null
            }
            if (v == null && !p.type.isMarkedNullable) return@flatMap emptyList()
            args[p] = v
        }
        listOf(k.callBy(args))
    }

package io.github.fogonapps.social

import kotlin.random.Random

/**
 * Cómo se elige la frase del saludo en Inicio. Cada frase candidata trae un peso por el momento (la hora, lo que la
 * persona tiene en curso o acaba de terminar) y se multiplica por lo que dice la comunidad y por lo que dijo la
 * persona:
 *
 * - Comunidad: media con un empujón inicial (2 sí y 2 no imaginarios), así una frase nueva arranca en el medio y
 *   unos pocos votos no la disparan. El peso es 4^(2·media − 1): una frase neutra pesa 1, una que gusta a todos
 *   hasta 4 y una que nadie quiere baja hasta 0,25 (nunca desaparece del todo, para que pueda recuperarse).
 * - La persona: las que le gustan (o compartió) pesan 2,5 veces más; las que dijo que no, nunca más salen.
 *
 * Después se sortea con esos pesos, con una semilla fija por hora para que no cambie cada vez que se abre Inicio
 * (tocar la frase pasa a la siguiente vuelta del sorteo).
 */
object Frases {
    /** Lo que votó la gente para una frase. */
    data class Votos(val si: Int = 0, val no: Int = 0)

    fun peso(contexto: Double, votos: Votos?, gusta: Boolean, noGusta: Boolean): Double {
        if (noGusta || contexto <= 0) return 0.0
        val v = votos ?: Votos()
        val comunidad = (v.si + 2.0) / (v.si + v.no + 4.0)
        return contexto * Math.pow(4.0, 2 * comunidad - 1) * (if (gusta) 2.5 else 1.0)
    }

    /** Sortea una clave entre las candidatas (clave → peso del momento). Null si ninguna puede salir. */
    fun elegir(candidatas: Map<String, Double>, votos: Map<String, Votos>, gustan: Set<String>, noGustan: Set<String>, semilla: Long): String? {
        val pesos = candidatas.map { (k, c) -> k to peso(c, votos[k], k in gustan, k in noGustan) }.filter { it.second > 0 }
        val total = pesos.sumOf { it.second }
        if (total <= 0) return null
        var r = Random(semilla).nextDouble() * total
        for ((k, p) in pesos) { r -= p; if (r <= 0) return k }
        return pesos.last().first
    }
}

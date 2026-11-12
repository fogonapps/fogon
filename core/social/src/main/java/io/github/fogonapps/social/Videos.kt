package io.github.fogonapps.social

/** El tramo inicial es el único que se prepara y publica. */
data class TramoVideo(val finMs: Long, val recortado: Boolean)
fun tramoVideo(duracionMs: Long, maximoMs: Long = 30_000): TramoVideo {
    require(duracionMs > 0)
    return TramoVideo(minOf(duracionMs, maximoMs), duracionMs > maximoMs)
}

/** Nunca se descargan bytes de vídeo antes de una acción explícita, tampoco con wifi. */
fun cargarVideo(solicitado: Boolean): Boolean = solicitado

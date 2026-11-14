package io.github.fogonapps.cajon.datos

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Reintenta únicamente la saturación del servicio; la cancelación y los demás errores salen al llamador. */
internal suspend fun <T> reintentarOcupado(pedir: suspend () -> T): T {
    var espera = 5L
    while (true) {
        currentCoroutineContext().ensureActive()
        try { return pedir() } catch (x: Ocupado) {
            delay((x.segundos ?: espera).coerceAtLeast(1).coerceAtMost(Long.MAX_VALUE / 1000) * 1000)
            espera = (espera * 2).coerceAtMost(60)
        }
    }
}

internal fun segundosDeEspera(valor: String?, ahora: Long = System.currentTimeMillis()): Long? {
    valor ?: return null
    valor.toLongOrNull()?.let { return it.coerceAtLeast(0) }
    return runCatching {
        ((ZonedDateTime.parse(valor, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - ahora + 999) / 1000).coerceAtLeast(0)
    }.getOrNull()
}

/** Solo el trabajo masivo hereda este contexto; las consultas manuales no esperan. */
internal class EnlaceMasivo : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<EnlaceMasivo> { const val CORTES = 5 }
    private val turno = Mutex()
    private var fallo: Exception? = null
    private var cortes = 0
    fun fallar(x: Exception) { fallo = x }
    /** Un corte de red suelto se salta; con [CORTES] seguidos no hay internet y el trabajo se detiene. */
    fun corte(x: Exception) { if (++cortes >= CORTES) fallo = x }
    fun respondio() { cortes = 0 }
    suspend fun comprobar() {
        currentCoroutineContext().ensureActive()
        fallo?.let { throw it }
    }
    suspend fun pausarTmdb() = turno.withLock {
        comprobar()
        delay(350)
    }
}

package io.github.fogonapps.social

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lo escrito en el chat que no se pudo entregar, por conversación, para devolverlo al campo y reintentar. Vive con el
 * chat y no en la pantalla: si la persona se fue a otra pantalla mientras se enviaba, lo encuentra al volver. Solo en
 * memoria; se olvida al borrar la conversación, al bloquear a esa persona o al cerrar la cuenta.
 */
class PendientesChat {
    /** Un pedazo que no salió: [escrito] ordena los pedazos como se escribieron, aunque fallen en otro orden. */
    data class Parte(val escrito: Long, val texto: String)

    /** Lo que no salió en una conversación, de la [cuenta] que lo escribió, y a qué respondía. */
    data class SinEntregar(val cuenta: String, val partes: List<Parte>, val responde: MensajeChat?) {
        val texto: String get() = partes.sortedBy { it.escrito }.joinToString(" ") { it.texto }
    }

    private var guardados = emptyMap<String, SinEntregar>()
    /** Envíos en curso por conversación: mientras haya alguno, lo que no salió espera para volver todo junto y en orden. */
    private var enCurso = emptyMap<String, Int>()
    private val _estado = MutableStateFlow<Map<String, SinEntregar>>(emptyMap())
    /** Lo que ya se puede devolver al campo (de las conversaciones sin envíos en curso). */
    val estado: StateFlow<Map<String, SinEntregar>> = _estado

    @Synchronized private fun publicar() { _estado.value = guardados.filterKeys { (enCurso[it] ?: 0) == 0 } }

    /** Empieza un envío a [usuario] (se llama al tocar enviar, antes de esperar a nada). */
    @Synchronized fun empezar(usuario: String) { enCurso = enCurso + (usuario to (enCurso[usuario] ?: 0) + 1); publicar() }

    /** Termina un envío a [usuario], haya salido o no. */
    @Synchronized fun terminar(usuario: String) {
        val n = (enCurso[usuario] ?: 0) - 1
        enCurso = if (n > 0) enCurso + (usuario to n) else enCurso - usuario
        publicar()
    }

    /** Anota que [texto], escrito en el momento [escrito] por [cuenta] a [usuario], no salió. */
    fun guardar(usuario: String, cuenta: String, escrito: Long, texto: String, responde: MensajeChat?) {
        if (texto.isBlank()) return
        synchronized(this) {
            val antes = guardados[usuario]?.takeIf { it.cuenta == cuenta }
            guardados = guardados + (usuario to SinEntregar(cuenta, antes?.partes.orEmpty() + Parte(escrito, texto), antes?.responde ?: responde))
            publicar()
        }
    }

    /**
     * Lo que hay que devolver al campo de [usuario] y lo quita; null si no hay, si lo escribió otra cuenta o si todavía
     * hay envíos en curso a esa persona (vuelve cuando terminen, todo junto y en el orden en que se escribió).
     */
    @Synchronized fun tomar(usuario: String, cuentaActual: String?): SinEntregar? {
        if ((enCurso[usuario] ?: 0) > 0) return null
        val tomado = guardados[usuario] ?: return null
        guardados = guardados - usuario
        publicar()
        return tomado.takeIf { cuentaActual != null && it.cuenta == cuentaActual }
    }

    private val _descartes = MutableStateFlow<Map<String, Int>>(emptyMap())
    /**
     * Cuántas veces se olvidó cada conversación (al borrarla o bloquear a la persona; [TODAS] al cerrar la cuenta). La
     * pantalla vacía el campo cuando cambia: lo que ya había vuelto al campo tampoco debe quedar.
     */
    val descartes: StateFlow<Map<String, Int>> = _descartes

    /** Las veces que se olvidó la conversación con [usuario], contando las de toda la cuenta. */
    fun vecesOlvidada(usuario: String): Int = _descartes.value.let { (it[usuario] ?: 0) + (it[TODAS] ?: 0) }

    @Synchronized fun olvidar(usuario: String) { guardados = guardados - usuario; contar(usuario); publicar() }

    @Synchronized fun olvidarTodo() { guardados = emptyMap(); contar(TODAS); publicar() }

    private fun contar(clave: String) { _descartes.value = _descartes.value + (clave to (_descartes.value[clave] ?: 0) + 1) }

    companion object {
        /** La clave de [descartes] para todas las conversaciones (ningún usuario tiene un «*»). */
        const val TODAS = "*"
    }
}

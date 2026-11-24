package io.github.fogonapps.social

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/**
 * Lo escrito en cada conversación y todavía no enviado, guardado en este teléfono ([archivo]) para que no se pierda al
 * salir del chat ni si Android cierra la app. Cada borrador lleva la [Guardado.cuenta] que lo escribió: otra cuenta
 * en el mismo teléfono no lo ve. Se borra al enviar (el campo queda vacío), al borrar la conversación, al bloquear a
 * la persona y al cerrar la sesión. No va al servidor ni al respaldo.
 *
 * Sus métodos leen y escriben el archivo: se llaman fuera del hilo principal.
 */
class BorradoresChat(private val archivo: File) {
    @Serializable
    data class Guardado(val cuenta: String, val texto: String)

    private val serie = MapSerializer(String.serializer(), Guardado.serializer())
    private var guardados: Map<String, Guardado>? = null

    private fun todos(): Map<String, Guardado> = guardados ?: (runCatching { JsonSocial.decodeFromString(serie, archivo.readText()) }.getOrNull() ?: emptyMap())
        .also { guardados = it }

    private fun escribir(nuevos: Map<String, Guardado>) {
        if (nuevos == todos()) return
        guardados = nuevos
        runCatching {
            if (nuevos.isEmpty()) archivo.delete()
            else {
                // Primero a un archivo aparte y después se cambia: si la app se cierra a mitad, queda el borrador anterior entero.
                val temporal = File(archivo.path + ".tmp")
                temporal.writeText(JsonSocial.encodeToString(serie, nuevos))
                if (!temporal.renameTo(archivo)) { archivo.writeText(temporal.readText()); temporal.delete() }
            }
        }
    }

    /** El borrador de la conversación con [usuario] si lo escribió [cuenta]; vacío si no hay o es de otra cuenta. */
    @Synchronized fun de(cuenta: String?, usuario: String): String =
        todos()[usuario]?.takeIf { cuenta != null && it.cuenta == cuenta }?.texto.orEmpty()

    /** Guarda lo que [cuenta] tiene escrito para [usuario] (en blanco lo quita). */
    @Synchronized fun poner(cuenta: String?, usuario: String, texto: String) {
        if (cuenta == null) return
        val t = texto.take(MAX)
        escribir(if (t.isBlank()) todos() - usuario else todos() + (usuario to Guardado(cuenta, t)))
    }

    /** Olvida el borrador de [usuario] (al borrar la conversación o bloquearlo). */
    @Synchronized fun olvidar(usuario: String) = escribir(todos() - usuario)

    /** Olvida todos (al cerrar la sesión o borrar la cuenta). */
    @Synchronized fun olvidarTodo() = escribir(emptyMap())

    companion object {
        /** Lo mismo que cabe en el campo de escribir. */
        const val MAX = 4000

        /** El archivo de borradores del chat guardado en [chats]. */
        fun archivoDe(chats: File) = File(chats.path + ".borradores")

        /**
         * Borra los borradores del chat de [chats] al cerrar la sesión, aunque el chat no se haya abierto (en La
         * Cucha, La Alcancía y El Forzudo se crea al entrar a él).
         */
        fun borrarDe(chats: File) { runCatching { archivoDe(chats).delete(); File(archivoDe(chats).path + ".tmp").delete() } }
    }
}

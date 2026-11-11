package io.github.fogonapps.identidad

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * X-07: las últimas búsquedas de un perfil, solo en el teléfono (un archivo de texto en la carpeta del perfil; nunca
 * van al servidor). La más nueva primero, sin repetir (sin importar mayúsculas), hasta [maximo].
 */
class BusquedasRecientes(private val archivo: File, private val maximo: Int = 10) {
    private val _lista = MutableStateFlow(leer())
    val lista: StateFlow<List<String>> = _lista

    fun anotar(texto: String) {
        val t = texto.trim().replace(Regex("\\s+"), " ")
        if (t.length < 2) return
        guardar(listOf(t) + _lista.value.filterNot { it.equals(t, ignoreCase = true) })
    }
    fun quitar(texto: String) = guardar(_lista.value.filterNot { it == texto })
    fun borrarTodas() = guardar(emptyList())

    private fun leer(): List<String> = runCatching { archivo.readLines().filter { it.isNotBlank() }.take(maximo) }.getOrDefault(emptyList())
    @Synchronized private fun guardar(nueva: List<String>) {
        val l = nueva.take(maximo)
        _lista.value = l
        runCatching {
            if (l.isEmpty()) { archivo.delete(); return }
            val tmp = File(archivo.parentFile, archivo.name + ".tmp")
            tmp.writeText(l.joinToString("\n"))
            if (!tmp.renameTo(archivo)) { archivo.delete(); tmp.renameTo(archivo) }
        }
    }
}

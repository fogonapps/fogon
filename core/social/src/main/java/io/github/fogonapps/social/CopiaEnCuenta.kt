package io.github.fogonapps.social

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * D-033: lo de una app se guarda solo en la cuenta, cifrado con la llave común (que abre la contraseña), y vuelve solo
 * al reinstalar o en otro teléfono. El servidor guarda un sobre que no puede abrir.
 *
 * - [leer] da el JSON de lo que hay en el teléfono; [aplicar] reemplaza lo del teléfono con lo de la cuenta.
 * - [vacio]: el teléfono no tiene nada propio todavía (recién instalada): entonces siempre gana la cuenta y nunca
 *   se sube un cuaderno vacío encima de uno lleno.
 * - [marca]: la hora del último cambio dentro de un JSON (`updatedAt`); si cambiaron los dos lados, gana el más nuevo.
 */
class CopiaEnCuenta(
    private val red: RedApp,
    private val prefs: SharedPreferences,
    private val alcance: CoroutineScope,
    private val leer: suspend () -> String?,
    private val aplicar: suspend (String) -> Unit,
    private val vacio: () -> Boolean,
    private val marca: (String) -> Long,
) {
    private val mutex = Mutex()
    private var pendiente: Job? = null
    private var cambio: Long
        get() = prefs.getLong("cambio", 0)
        set(v) { prefs.edit().putLong("cambio", v).apply() }
    private var sucio: Boolean
        get() = prefs.getBoolean("sucio", false)
        set(v) { prefs.edit().putBoolean("sucio", v).apply() }

    init {
        red.ultimaCopia.value = prefs.getLong("ultima", 0)
        red.alAbrirCopia = { sincronizar() }
    }

    /** Hubo un cambio en el teléfono: se guarda en la cuenta unos segundos después (juntando los cambios seguidos). */
    fun cambio() {
        sucio = true
        pendiente?.cancel()
        pendiente = alcance.launch { delay(4000); sincronizar().join() }
    }

    /** Trae lo de la cuenta si es más nuevo y guarda lo del teléfono si cambió. Sin llave o sin red, no hace nada. */
    fun sincronizar(): Job = alcance.launch {
        val llave = red.llaveCopia() ?: return@launch
        mutex.withLock {
            runCatching { repeat(2) { if (unaVez(llave)) return@runCatching } }
        }
    }

    /** true si terminó; false si otro teléfono subió algo entre medio (409) y hay que repetir. */
    private suspend fun unaVez(llave: String): Boolean {
        val remota = red.descargarCopia()
        val local = leer()
        if (remota.sobre.isNotBlank() && remota.cambio != cambio) {
            val texto = withContext(Dispatchers.Default) { NubeCifrada.abrir(remota.sobre, llave) }
            // La primera vez en un teléfono que ya tenía lo suyo, no se pisa: gana el más nuevo de los dos.
            val primeraVez = cambio == 0L
            val ganaLaCuenta = local == null || vacio() || (!sucio && !primeraVez) || marca(texto) >= marca(local)
            if (ganaLaCuenta) {
                aplicar(texto)
                cambio = remota.cambio; sucio = false
                return true
            }
        }
        if (local == null || vacio() || (!sucio && remota.sobre.isNotBlank())) return true
        val sobre = withContext(Dispatchers.Default) { NubeCifrada.cerrar(local, llave) }
        val nuevo = try { red.subirCopia(sobre, remota.cambio) } catch (x: ErrorSocial) { if (x.codigo == 409) return false else throw x }
        cambio = nuevo; sucio = false
        val ahora = System.currentTimeMillis()
        prefs.edit().putLong("ultima", ahora).apply(); red.ultimaCopia.value = ahora
        return true
    }
}

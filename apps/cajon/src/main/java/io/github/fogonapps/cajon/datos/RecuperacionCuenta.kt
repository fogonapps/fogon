package io.github.fogonapps.cajon.datos

import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** La migración no depende de volver a entrar; tampoco quita un pendiente ya guardado. */
internal fun perfilPendienteTrasMigrar(haySesion: Boolean, migrada: Boolean, pendiente: Boolean) =
    pendiente || (haySesion && !migrada)

/** Serializa comprobar, abrir la llave y recuperar el cajón. Una llave sola no autoriza publicar. */
internal class RecuperacionCajon {
    private val mutex = Mutex()
    private var generacion = 0L
    var recuperado = false
        private set

    fun reiniciar(cuentaNueva: Boolean = false) {
        generacion++
        recuperado = cuentaNueva // Una cuenta recién creada todavía no tiene un cajón remoto que recuperar.
    }

    suspend fun revisar(
        sesionValida: () -> Boolean,
        prepararLlave: suspend () -> Unit = {},
        tieneLlave: () -> Boolean,
        hayCajon: suspend () -> Boolean,
        unir: suspend () -> Boolean,
        bloquear: (Boolean) -> Unit,
    ): Boolean {
        val actual = generacion
        fun vigente() = actual == generacion && sesionValida()
        return mutex.withLock {
            if (!vigente()) return@withLock false
            recuperado = false
            prepararLlave()
            if (!vigente()) return@withLock false
            if (!tieneLlave()) {
                val remoto = hayCajon()
                if (!vigente()) return@withLock false
                // Se vuelve a validar la llave: una respuesta antigua no puede bloquear una llave disponible.
                if (!tieneLlave()) {
                    bloquear(remoto)
                    recuperado = !remoto
                    return@withLock recuperado
                }
            }
            bloquear(false)
            val unido = unir()
            if (!vigente()) return@withLock false
            recuperado = unido
            unido
        }
    }
}

internal suspend fun prepararSubidaPerfil(
    pendiente: () -> Boolean,
    recuperarPerfil: suspend () -> Boolean,
    recuperarCajon: suspend () -> Boolean,
    vigente: () -> Boolean,
): Boolean = io.github.fogonapps.social.prepararSubidaPerfil(pendiente, recuperarPerfil, recuperarCajon, vigente)

/** Agrupa las peticiones durante una sincronización en una sola repetición, sin una cola de trabajos. */
internal class SincronizacionesAgrupadas(private val scope: kotlinx.coroutines.CoroutineScope) {
    private var trabajo: kotlinx.coroutines.Job? = null
    private var repetir = false
    private var generacion = 0L

    fun cancelar() {
        generacion++
        repetir = false
        trabajo?.cancel()
        trabajo = null
    }

    fun solicitar(accion: suspend () -> Unit): kotlinx.coroutines.Job {
        trabajo?.takeIf { it.isActive }?.let { repetir = true; return it }
        val actual = generacion
        val nuevo = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                do {
                    repetir = false
                    accion()
                } while (repetir && actual == generacion)
            } finally {
                if (actual == generacion) trabajo = null
            }
        }
        trabajo = nuevo
        nuevo.start()
        return nuevo
    }
}

internal typealias SubidasPerfil = io.github.fogonapps.social.SubidasPerfil

/** Identifica la llave autorizada sin guardar otra copia de la llave en claro. */
internal fun huellaLlaveCajon(llave: String): String = java.util.Base64.getEncoder().encodeToString(
    java.security.MessageDigest.getInstance("SHA-256").digest(llave.encodeToByteArray()),
)

internal fun permiteReemplazarCajon(llave: String, autorizacion: String?) =
    autorizacion != null && autorizacion == huellaLlaveCajon(llave)

internal class LlaveRecuperada(val llave: String, val reemplazar: Boolean)

/** Solo perder la llave local en la recuperación por código autoriza descartar el sobre anterior. */
internal fun llaveParaRecuperacion(local: String?, crear: () -> String = Nube::llaveNueva): LlaveRecuperada =
    LlaveRecuperada(local ?: crear(), reemplazar = local == null)

internal class CajonIlegible(causa: Exception) : java.io.IOException("No se pudo descifrar el cajón guardado", causa)

internal fun abrirCajonRemoto(sobre: String, llave: String, autorizacion: String?, desempacar: (String) -> String = { it }): Estado? {
    if (sobre.isBlank()) return null
    return try {
        JsonCajon.decodeFromString(Estado.serializer(), desempacar(Nube.abrir(sobre, llave)))
    } catch (x: kotlinx.coroutines.CancellationException) {
        throw x
    } catch (x: Exception) {
        if (!permiteReemplazarCajon(llave, autorizacion)) throw CajonIlegible(x)
        null
    }
}

/** Reemplaza un sobre ilegible solo con autorización para esta llave; un 409 obliga a descargar otra vez. */
internal suspend fun sincronizarCajonCifrado(
    llave: String,
    autorizacion: () -> String?,
    vigente: () -> Boolean,
    descargar: suspend () -> CajonCifrado,
    unir: suspend (Estado) -> Unit,
    local: () -> Estado?,
    guardar: suspend (String, Long) -> Unit,
    confirmar: () -> Unit,
    /** Las imágenes del teléfono adentro de la copia, y de vuelta como archivos (ver ImagenesPerfilCopia). */
    empacar: (String) -> String = { it },
    desempacar: (String) -> String = { it },
): Boolean {
    while (vigente()) {
        val guardado = descargar()
        if (!vigente()) return false
        val permiso = autorizacion()
        val remoto = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            abrirCajonRemoto(guardado.sobre, llave, permiso, desempacar)
        }
        if (!vigente()) return false
        if (remoto != null) unir(remoto)
        if (!vigente()) return false
        val actual = local() ?: return false
        val sobre = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            Nube.cerrar(empacar(JsonCajon.encodeToString(Estado.serializer(), Resenas.paraGuardar(actual))), llave)
        }
        if (!vigente()) return false
        try {
            guardar(sobre, guardado.cambio)
        } catch (x: ErrorSocial) {
            if (!vigente()) return false
            if (x.codigo == 409 && x.error == "cambio") continue
            throw x
        }
        if (!vigente()) return false
        confirmar()
        return true
    }
    return false
}

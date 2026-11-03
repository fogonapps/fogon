package io.github.fogonapps.cajon

import android.app.Application
import io.github.fogonapps.cajon.datos.CajaFuerte
import io.github.fogonapps.cajon.datos.Cifrado
import io.github.fogonapps.cajon.datos.ClienteSocial
import io.github.fogonapps.cajon.datos.JsonCajon
import io.github.fogonapps.cajon.datos.LlavesPublicas
import io.github.fogonapps.cajon.datos.Obra
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException

/** Un mensaje del chat, como se guarda en el teléfono (ya abierto). [obra]: una obra mandada como tarjeta. */
@Serializable
data class MensajeChat(val id: String, val mio: Boolean, val cuando: Long, val texto: String = "", val obra: JsonObject? = null, val noSeLee: Boolean = false, val leido: Boolean = false)

/** Lo que va dentro del sobre cifrado. */
@Serializable private data class Contenido(val t: String = "", val o: JsonObject? = null, val c: Long = 0)

@Serializable private data class Chats(val conversaciones: Map<String, List<MensajeChat>> = emptyMap(), val ultimo: Long = 0)

/**
 * El chat de la red, cifrado de extremo a extremo (ver [Cifrado]). Las conversaciones viven solo en este teléfono
 * (`files/chats.json`): no van en el respaldo, ni en el Gist, ni quedan en el servidor, que solo pasa los sobres.
 */
class Chat(private val app: Application, private val scope: CoroutineScope, private val caja: CajaFuerte, private val red: RedSocial, private val decir: (Int) -> Unit) {
    private val archivo = File(app.filesDir, "chats.json")
    private val candado = Mutex()
    private val _chats = MutableStateFlow(Chats())
    /** Conversación (el @usuario) → sus mensajes, del más viejo al más nuevo. */
    val conversaciones: StateFlow<Map<String, List<MensajeChat>>> get() = _conversaciones
    private val _conversaciones = MutableStateFlow<Map<String, List<MensajeChat>>>(emptyMap())
    private val _enviando = MutableStateFlow(false)
    val enviando: StateFlow<Boolean> = _enviando
    private val llavesDe = HashMap<String, LlavesPublicas>()

    init {
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { JsonCajon.decodeFromString(Chats.serializer(), archivo.readText()) }.getOrNull() }?.let { poner(it) }
            // Al cerrar o borrar la cuenta, el chat de este teléfono se va con ella.
            red.usuario.collect { u -> if (u == null && caja.chatLlaves != null) borrarTodo() else if (u != null) asegurarLlaves() }
        }
    }

    private fun poner(c: Chats) { _chats.value = c; _conversaciones.value = c.conversaciones }

    private suspend fun guardar() = withContext(Dispatchers.IO) { runCatching { archivo.writeText(JsonCajon.encodeToString(Chats.serializer(), _chats.value)) } }

    private fun propias(): Cifrado.Propias? = caja.chatLlaves?.let { runCatching { JsonCajon.decodeFromString(Cifrado.Propias.serializer(), it) }.getOrNull() }

    /** Que este teléfono tenga sus llaves y que el servidor tenga las públicas. */
    private suspend fun asegurarLlaves() {
        val p = propias() ?: withContext(Dispatchers.Default) { Cifrado.nuevas() }.also { caja.chatLlaves = JsonCajon.encodeToString(Cifrado.Propias.serializer(), it) }
        runCatching { ClienteSocial(caja.tokenSocial).ponerLlaves(withContext(Dispatchers.Default) { Cifrado.publicas(p) }) }
    }

    private suspend fun llaves(usuario: String): LlavesPublicas = llavesDe[usuario] ?: ClienteSocial(caja.tokenSocial).llaves(usuario).also { llavesDe[usuario] = it }

    /** El número de seguridad con [usuario] (null si todavía no se puede saber). */
    suspend fun numeroSeguridad(usuario: String): String? = runCatching {
        val p = propias() ?: return null
        Cifrado.numeroSeguridad(Cifrado.publicas(p), llaves(usuario))
    }.getOrNull()

    /** Manda unas palabras o una obra (como tarjeta) a [usuario]. */
    fun enviar(usuario: String, texto: String, obra: Obra? = null) = scope.launch {
        val yo = red.usuario.value ?: return@launch
        val p = propias() ?: run { asegurarLlaves(); propias() } ?: return@launch
        val ahora = System.currentTimeMillis()
        val o = obra?.let { buildJsonObject {
            put("id", it.id); put("titulo", it.titulo); put("tipoObra", it.tipo)
            put("portada", it.portada.takeIf { u -> u.startsWith("https://") }.orEmpty()); it.anio?.let { a -> put("anio", a) }
            if (it.fuente.isNotBlank()) put("fuente", it.fuente)
        } }
        _enviando.value = true
        try {
            val suyas = try { llaves(usuario) } catch (x: io.github.fogonapps.cajon.datos.ErrorSocial) {
                if (x.codigo == 404) { decir(R.string.chat_sin_llaves); return@launch } else throw x
            }
            val contenido = JsonCajon.encodeToString(Contenido.serializer(), Contenido(texto.trim().take(4000), o, ahora)).toByteArray()
            val (sobre, firma) = withContext(Dispatchers.Default) { Cifrado.cerrar(contenido, yo, usuario, suyas, p) }
            ClienteSocial(caja.tokenSocial).mandar(usuario, sobre, firma)
            agregar(usuario, MensajeChat("m${ahora.toString(36)}", true, ahora, texto.trim(), o, leido = true))
        } catch (x: io.github.fogonapps.cajon.datos.ErrorSocial) {
            decir(if (x.error == "no_existe") R.string.chat_no_se_puede else R.string.red_error)
        } catch (x: IOException) { decir(R.string.red_sin_conexion) }
        finally { _enviando.value = false }
    }

    private suspend fun agregar(usuario: String, m: MensajeChat, ultimo: Long? = null) = candado.withLock {
        val c = _chats.value
        val lista = (c.conversaciones[usuario].orEmpty() + m).distinctBy { it.id }.takeLast(2000)
        poner(c.copy(conversaciones = c.conversaciones + (usuario to lista), ultimo = ultimo ?: c.ultimo))
        guardar()
    }

    /** Trae los sobres nuevos, los abre y los guarda; al pedir los siguientes, el servidor borra los ya recibidos. */
    fun recibir() = scope.launch {
        val yo = red.usuario.value ?: return@launch
        val p = propias() ?: return@launch
        val sobres = runCatching { ClienteSocial(caja.tokenSocial).recibir(_chats.value.ultimo) }.getOrNull() ?: return@launch
        for (s in sobres) {
            val de = s.de.usuario
            val m = runCatching {
                val bytes = withContext(Dispatchers.Default) { Cifrado.abrir(s.sobre, s.firma, de, yo, llaves(de), p) }
                val c = JsonCajon.decodeFromString(Contenido.serializer(), String(bytes))
                MensajeChat("s${s.id}", false, s.cuando, c.t, c.o)
            }.getOrElse { MensajeChat("s${s.id}", false, s.cuando, noSeLee = true) }
            agregar(de, m, s.id)
        }
        // Confirmar lo recibido (el servidor borra hasta el último).
        if (sobres.isNotEmpty()) runCatching { ClienteSocial(caja.tokenSocial).recibir(_chats.value.ultimo) }
    }

    fun sinLeer(): Int = _conversaciones.value.values.sumOf { l -> l.count { !it.mio && !it.leido } }

    fun marcarLeida(usuario: String) = scope.launch {
        candado.withLock {
            val c = _chats.value
            val l = c.conversaciones[usuario] ?: return@withLock
            if (l.none { !it.leido }) return@withLock
            poner(c.copy(conversaciones = c.conversaciones + (usuario to l.map { it.copy(leido = true) })))
            guardar()
        }
    }

    fun borrarConversacion(usuario: String) = scope.launch {
        candado.withLock { val c = _chats.value; poner(c.copy(conversaciones = c.conversaciones - usuario)); guardar() }
    }

    private suspend fun borrarTodo() {
        candado.withLock { poner(Chats()); withContext(Dispatchers.IO) { archivo.delete() } }
        caja.chatLlaves = null
        llavesDe.clear()
    }
}

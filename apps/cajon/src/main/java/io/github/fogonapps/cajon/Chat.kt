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

/** A qué mensaje responde otro: su clave, quién lo escribió y un pedacito de lo que decía. */
@Serializable data class Respuesta(val k: String, val de: String, val texto: String, val sticker: Boolean = false)

/**
 * Un mensaje del chat, como se guarda en el teléfono (ya abierto). [k]: la clave que comparten los dos teléfonos (para
 * responder o borrar para todos). [obra]: una obra mandada como tarjeta. [sticker]: la imagen (WebP en base64).
 * [borrado]: se borró para todos (queda el aviso, sin el contenido).
 */
@Serializable
data class MensajeChat(
    val id: String, val mio: Boolean, val cuando: Long, val texto: String = "", val obra: JsonObject? = null,
    val noSeLee: Boolean = false, val leido: Boolean = false, val k: String = "", val responde: Respuesta? = null,
    val sticker: String? = null, val borrado: Boolean = false,
) { val clave get() = k.ifBlank { id } }

/** Un sticker guardado: la imagen (WebP en base64) y un nombre para buscarlo. */
@Serializable data class Sticker(val id: String, val imagen: String, val nombre: String = "", val cuando: Long = 0)

/** Cómo se ve una conversación: el color de las burbujas propias (0 = primera tinta, 1 = segunda), fondo de puntos y letra grande. */
@Serializable data class EstiloChat(val color: Int = 0, val puntos: Boolean = false, val letraGrande: Boolean = false)

/** Lo que va dentro del sobre cifrado: texto, obra, sticker, respuesta, su clave y, para borrar para todos, la clave a borrar. */
@Serializable private data class Contenido(
    val t: String = "", val o: JsonObject? = null, val c: Long = 0, val i: String = "", val r: Respuesta? = null,
    val s: String? = null, val b: String? = null,
)

@Serializable private data class Chats(
    val conversaciones: Map<String, List<MensajeChat>> = emptyMap(), val ultimo: Long = 0,
    val estilos: Map<String, EstiloChat> = emptyMap(), val stickers: List<Sticker> = emptyList(),
    /** El nombre y si tiene foto de cada persona con la que se habla (para mostrar el nombre y no el @usuario). */
    val personas: Map<String, io.github.fogonapps.cajon.datos.PersonaSocial> = emptyMap(),
)

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
    private val _estilos = MutableStateFlow<Map<String, EstiloChat>>(emptyMap())
    val estilos: StateFlow<Map<String, EstiloChat>> = _estilos
    private val _stickers = MutableStateFlow<List<Sticker>>(emptyList())
    val stickers: StateFlow<List<Sticker>> = _stickers
    private val _personas = MutableStateFlow<Map<String, io.github.fogonapps.cajon.datos.PersonaSocial>>(emptyMap())
    val personas: StateFlow<Map<String, io.github.fogonapps.cajon.datos.PersonaSocial>> = _personas
    private val _enviando = MutableStateFlow(false)
    val enviando: StateFlow<Boolean> = _enviando
    private val llavesDe = HashMap<String, LlavesPublicas>()

    init {
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { JsonCajon.decodeFromString(Chats.serializer(), archivo.readText()) }.getOrNull() }?.let { poner(it) }
            // Al cerrar o borrar la cuenta, el chat de este teléfono se va con ella.
            red.usuario.collect { u -> if (u == null && caja.chatLlaves != null) borrarTodo() else if (u != null) asegurarLlaves() }
        }
        // Mientras la app está abierta, los mensajes nuevos llegan solos (no solo con la conversación abierta).
        scope.launch {
            while (true) { if (red.usuario.value != null) recibir().join(); kotlinx.coroutines.delay(30_000) }
        }
    }

    private fun poner(c: Chats) {
        _chats.value = c; _conversaciones.value = c.conversaciones; _estilos.value = c.estilos; _stickers.value = c.stickers; _personas.value = c.personas
    }

    /** Cambia algo de los chats guardados (de a uno a la vez) y lo escribe. */
    private suspend fun cambiar(f: (Chats) -> Chats) = candado.withLock { poner(f(_chats.value)); guardar() }

    private suspend fun guardar() = withContext(Dispatchers.IO) { runCatching { archivo.writeText(JsonCajon.encodeToString(Chats.serializer(), _chats.value)) } }

    private fun propias(): Cifrado.Propias? = caja.chatLlaves?.let { runCatching { JsonCajon.decodeFromString(Cifrado.Propias.serializer(), it) }.getOrNull() }

    /** Que este teléfono tenga sus llaves y que el servidor tenga las públicas. */
    private suspend fun asegurarLlaves() {
        val p = propias() ?: withContext(Dispatchers.Default) { Cifrado.nuevas() }.also { caja.chatLlaves = JsonCajon.encodeToString(Cifrado.Propias.serializer(), it) }
        runCatching { ClienteSocial(caja.tokenSocial).ponerLlaves(withContext(Dispatchers.Default) { Cifrado.publicas(p) }) }
    }

    /** Las llaves públicas de [usuario]; [frescas] las pide de nuevo (cambian si la persona entró en otro teléfono). */
    private suspend fun llaves(usuario: String, frescas: Boolean = false): LlavesPublicas =
        (if (frescas) null else llavesDe[usuario]) ?: ClienteSocial(caja.tokenSocial).llaves(usuario).also { llavesDe[usuario] = it }

    /** El número de seguridad con [usuario] (null si todavía no se puede saber). */
    suspend fun numeroSeguridad(usuario: String): String? = runCatching {
        val p = propias() ?: return null
        Cifrado.numeroSeguridad(Cifrado.publicas(p), llaves(usuario))
    }.getOrNull()

    /** Manda unas palabras, una obra (como tarjeta) o un sticker a [usuario]; con [responde], como respuesta a ese mensaje. */
    fun enviar(usuario: String, texto: String, obra: Obra? = null, responde: MensajeChat? = null, sticker: String? = null) = scope.launch {
        val yo = red.usuario.value ?: return@launch
        val ahora = System.currentTimeMillis()
        val o = obra?.let { buildJsonObject {
            put("id", it.id); put("titulo", it.titulo); put("tipoObra", it.tipo)
            put("portada", it.portada.takeIf { u -> u.startsWith("https://") }.orEmpty()); it.anio?.let { a -> put("anio", a) }
            if (it.fuente.isNotBlank()) put("fuente", it.fuente)
        } }
        val r = responde?.let { Respuesta(it.clave, if (it.mio) yo else usuario, resumenCorto(it), sticker = it.sticker != null) }
        val id = "m${ahora.toString(36)}"
        val c = Contenido(texto.trim().take(4000), o, ahora, i = id, r = r, s = sticker)
        if (mandar(usuario, yo, c)) agregar(usuario, MensajeChat(id, true, ahora, texto.trim(), o, leido = true, k = id, responde = r, sticker = sticker))
    }

    /** Un pedacito de un mensaje, para citarlo al responder. */
    private fun resumenCorto(m: MensajeChat): String = when {
        m.sticker != null -> ""
        m.obra != null -> "\u25B8 " + ((m.obra["titulo"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "")
        else -> m.texto.take(120)
    }

    /** Cierra y manda un contenido; false si no se pudo (ya avisó por qué). */
    private suspend fun mandar(usuario: String, yo: String, c: Contenido): Boolean {
        val p = propias() ?: run { asegurarLlaves(); propias() } ?: return false
        _enviando.value = true
        try {
            val suyas = try { llaves(usuario, frescas = true) } catch (x: io.github.fogonapps.cajon.datos.ErrorSocial) {
                if (x.codigo == 404) { decir(R.string.chat_sin_llaves); return false } else throw x
            }
            val contenido = JsonCajon.encodeToString(Contenido.serializer(), c).toByteArray()
            val (sobre, firma) = withContext(Dispatchers.Default) { Cifrado.cerrar(contenido, yo, usuario, suyas, p) }
            ClienteSocial(caja.tokenSocial).mandar(usuario, sobre, firma)
            return true
        } catch (x: io.github.fogonapps.cajon.datos.ErrorSocial) {
            decir(when (x.error) { "no_existe" -> R.string.chat_no_se_puede; "muy_grande" -> R.string.chat_muy_grande; else -> R.string.red_error }); return false
        } catch (x: IOException) { decir(R.string.red_sin_conexion); return false }
        finally { _enviando.value = false }
    }

    /** Borra un mensaje solo de este teléfono. */
    fun borrarParaMi(usuario: String, m: MensajeChat) = scope.launch {
        cambiar { c -> c.copy(conversaciones = c.conversaciones + (usuario to c.conversaciones[usuario].orEmpty().filter { it.id != m.id })) }
    }

    /** Borra un mensaje propio también en el teléfono de la otra persona (le manda la orden, cifrada como todo). */
    fun borrarParaTodos(usuario: String, m: MensajeChat) = scope.launch {
        if (!m.mio || m.borrado) return@launch
        val yo = red.usuario.value ?: return@launch
        if (mandar(usuario, yo, Contenido(c = System.currentTimeMillis(), b = m.clave))) marcarBorrado(usuario, m.clave, mio = true)
    }

    private suspend fun marcarBorrado(usuario: String, clave: String, mio: Boolean) = cambiar { c ->
        c.copy(conversaciones = c.conversaciones + (usuario to c.conversaciones[usuario].orEmpty().map {
            if (it.clave == clave && it.mio == mio) it.copy(texto = "", obra = null, sticker = null, responde = null, borrado = true) else it
        }))
    }

    /** Deja la conversación vacía (sigue en la lista). */
    fun vaciar(usuario: String) = scope.launch { cambiar { c -> c.copy(conversaciones = c.conversaciones + (usuario to emptyList())) } }

    fun ponerEstilo(usuario: String, e: EstiloChat) = scope.launch { cambiar { c -> c.copy(estilos = c.estilos + (usuario to e)) } }

    /** Guarda un sticker (si ya estaba la misma imagen, solo le cambia el nombre). */
    fun guardarSticker(imagen: String, nombre: String = "") = scope.launch {
        cambiar { c ->
            val ya = c.stickers.firstOrNull { it.imagen == imagen }
            val nuevo = (ya ?: Sticker("k${System.currentTimeMillis().toString(36)}", imagen, cuando = System.currentTimeMillis())).copy(nombre = nombre.trim().take(40).ifBlank { ya?.nombre.orEmpty() })
            c.copy(stickers = listOf(nuevo) + c.stickers.filter { it.id != nuevo.id }.take(199))
        }
        decir(R.string.chat_sticker_guardado)
    }

    /**
     * Hace un sticker con una foto: la recorta cuadrada al centro, la deja en 320 px y la guarda en WebP, bajando la
     * calidad hasta que quepa en un sobre. Devuelve la imagen en base64 (o null si no se pudo leer).
     */
    suspend fun crearSticker(uri: android.net.Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
            val medidas = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, medidas)
            var muestra = 1
            while (minOf(medidas.outWidth, medidas.outHeight) / (muestra * 2) >= 320) muestra *= 2
            aSticker(android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = muestra }) ?: return@runCatching null)
        }.getOrNull()
    }

    /** Un sticker hecho con una imagen de la app (los objetos de Fogón). */
    suspend fun stickerDe(recurso: Int): String? = withContext(Dispatchers.IO) {
        runCatching { aSticker(android.graphics.BitmapFactory.decodeResource(app.resources, recurso)) }.getOrNull()
    }

    private fun aSticker(original: android.graphics.Bitmap): String? {
        val lado = minOf(original.width, original.height)
        val cuadrada = android.graphics.Bitmap.createBitmap(original, (original.width - lado) / 2, (original.height - lado) / 2, lado, lado)
        val chica = android.graphics.Bitmap.createScaledBitmap(cuadrada, 320, 320, true)
        for (calidad in listOf(80, 65, 50, 35)) {
            val salida = java.io.ByteArrayOutputStream()
            @Suppress("DEPRECATION")
            val formato = if (android.os.Build.VERSION.SDK_INT >= 30) android.graphics.Bitmap.CompressFormat.WEBP_LOSSY else android.graphics.Bitmap.CompressFormat.WEBP
            chica.compress(formato, calidad, salida)
            val b64 = java.util.Base64.getEncoder().encodeToString(salida.toByteArray())
            if (b64.length <= STICKER_MAX) return b64
        }
        return null
    }

    fun quitarSticker(id: String) = scope.launch { cambiar { c -> c.copy(stickers = c.stickers.filter { it.id != id }) } }

    /** El nombre de [usuario] (lo trae del servidor una vez y lo recuerda). */
    fun conocer(usuario: String) = scope.launch {
        val p = runCatching { ClienteSocial(caja.tokenSocial).perfil(usuario).persona }.getOrNull() ?: return@launch
        if (_personas.value[usuario] != p) cambiar { c -> c.copy(personas = c.personas + (usuario to p)) }
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
            // Si no abre con las llaves que se tenían, se piden otra vez (la persona pudo cambiar de teléfono) y se reintenta.
            fun abrirCon(l: LlavesPublicas) = JsonCajon.decodeFromString(Contenido.serializer(), String(Cifrado.abrir(s.sobre, s.firma, de, yo, l, p)))
            val c = withContext(Dispatchers.Default) {
                runCatching { abrirCon(llaves(de)) }.getOrNull() ?: runCatching { abrirCon(llaves(de, frescas = true)) }.getOrNull()
            }
            when {
                c == null -> agregar(de, MensajeChat("s${s.id}", false, s.cuando, noSeLee = true), s.id)
                // La otra persona borró un mensaje suyo para todos.
                c.b != null -> { marcarBorrado(de, c.b, mio = false); cambiar { x -> x.copy(ultimo = maxOf(x.ultimo, s.id)) } }
                else -> agregar(de, MensajeChat("s${s.id}", false, s.cuando, c.t, c.o, k = c.i, responde = c.r, sticker = c.s?.takeIf { it.length <= STICKER_MAX }), s.id)
            }
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

    companion object {
        /** El tamaño máximo de un sticker en base64 (cabe en un sobre del servidor con lo demás). */
        const val STICKER_MAX = 60_000
    }

    private suspend fun borrarTodo() {
        candado.withLock { poner(Chats()); withContext(Dispatchers.IO) { archivo.delete() } }
        caja.chatLlaves = null
        llavesDe.clear()
    }
}

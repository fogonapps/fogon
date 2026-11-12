package io.github.fogonapps.social

import android.content.Context
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
    /** El tamaño del sticker: 0 chico, 1 normal, 2 grande. */
    val tamano: Int = 1,
    /** Las reacciones (un emoji) de cada quien. */
    val reaccionMia: String = "", val reaccionSuya: String = "",
    /** Una foto (JPEG en base64) o una nota de voz (AAC en base64) de [segundos]. */
    val foto: String? = null, val audio: String? = null, val segundos: Int = 0,
    /** La otra persona ya lo vio (si los dos tienen las confirmaciones de lectura prendidas). */
    val visto: Boolean = false,
) { val clave get() = k.ifBlank { id } }

/** Un sticker guardado: la imagen (WebP en base64) y un nombre para buscarlo. */
@Serializable data class Sticker(val id: String, val imagen: String, val nombre: String = "", val cuando: Long = 0)

/** Cómo se ve una conversación: el color de las burbujas propias (0 = primera tinta, 1 = segunda), fondo de puntos y letra grande. */
/**
 * Cómo se ve una conversación: [color] de las burbujas propias (0 a 5, tintas del tema), fondo de puntos o con el
 * póster de una obra ([poster]), letra ([letra]: 0 chica, 1 normal, 2 grande; [letraGrande] es la de antes) y un
 * [apodo] para la otra persona que solo ve quien lo pone.
 */
@Serializable data class EstiloChat(val color: Int = 0, val puntos: Boolean = false, val letraGrande: Boolean = false,
    val poster: String = "", val letra: Int = -1, val apodo: String = "",
    /** Silenciado: sus mensajes no avisan. */
    val silenciado: Boolean = false) {
    val tamanoLetra get() = if (letra >= 0) letra else if (letraGrande) 2 else 1
}

/** Lo que va dentro del sobre cifrado: texto, obra, sticker, respuesta, su clave y, para borrar para todos, la clave a borrar. */
@Serializable private data class Contenido(
    val t: String = "", val o: JsonObject? = null, val c: Long = 0, val i: String = "", val r: Respuesta? = null,
    val s: String? = null, val b: String? = null,
    /** El tamaño del sticker (0 a 2; sin él, normal). */
    val z: Int? = null,
    /** Una reacción: [x] (un emoji; vacío la quita) al mensaje de clave [rk]. */
    val rk: String? = null, val x: String? = null,
    /** Una foto [f] (JPEG en base64) o una nota de voz [a] (AAC en base64) de [d] segundos. */
    val f: String? = null, val a: String? = null, val d: Int? = null,
    /** «Visto»: la otra persona leyó hasta el mensaje de clave [v]. */
    val v: String? = null,
)

@Serializable private data class Chats(
    val conversaciones: Map<String, List<MensajeChat>> = emptyMap(), val ultimo: Long = 0,
    val estilos: Map<String, EstiloChat> = emptyMap(), val stickers: List<Sticker> = emptyList(),
    /** El nombre y si tiene foto de cada persona con la que se habla (para mostrar el nombre y no el @usuario). */
    val personas: Map<String, PersonaSocial> = emptyMap(),
    /** Sin confirmaciones de lectura: no se manda el «Visto» ni se muestra el de los demás. */
    val sinVisto: Boolean = false,
)

fun claveSobre(s: Sobre): String = "s${s.id}:${s.cuando}:${s.de.usuario}"

/** Reconoce entregas repetidas, incluidos mensajes guardados antes de usar claves compuestas. */
fun decidirSobres(
    ultimo: Long, sobres: List<Sobre>, conversaciones: Map<String, List<MensajeChat>>,
): Pair<List<Sobre>, Long> {
    fun conocido(s: Sobre) = conversaciones[s.de.usuario].orEmpty().any {
        it.id == claveSobre(s) || (it.id == "s${s.id}" && it.cuando == s.cuando && !it.mio)
    }
    val reinicio = sobres.any { it.id < ultimo && !conocido(it) } ||
        (sobres.isNotEmpty() && sobres.all { it.id <= ultimo } && sobres.any { !conocido(it) })
    val nuevos = sobres.filter { (reinicio || it.id > ultimo) && !conocido(it) }.distinctBy(::claveSobre).sortedBy { it.id }
    val cursor = if (reinicio) sobres.maxOf { it.id } else maxOf(ultimo, sobres.maxOfOrNull { it.id } ?: ultimo)
    return nuevos to cursor
}

/** La lista pertenece a un solo remitente; solo el contenido abierto permite reconocer una entrega renumerada. */
fun agregarMensajeChat(lista: List<MensajeChat>, mensaje: MensajeChat): List<MensajeChat> {
    val repetido = !mensaje.mio && !mensaje.noSeLee && mensaje.k.isNotBlank() &&
        lista.any { !it.mio && it.k == mensaje.k }
    return (if (repetido) lista else (lista + mensaje).distinctBy { it.id }).takeLast(2000)
}

/**
 * El chat de la red, cifrado de extremo a extremo (ver [Cifrado]). Las conversaciones viven solo en este teléfono
 * (`files/chats.json`): no van en el respaldo, ni en el Gist, ni quedan en el servidor, que solo pasa los sobres.
 */
/**
 * Dónde guarda cada app lo del chat de un perfil: sus llaves privadas (cifradas en el teléfono) y la llave de la
 * cuenta (la de la contraseña, D-033) para compartirlas con las otras apps sin que el servidor las pueda abrir.
 */
interface AlmacenChat {
    var chatLlaves: String?
    val llaveCuenta: String?
    val permisoRed: kotlinx.coroutines.flow.Flow<Boolean>
}

/**
 * El chat de la red, cifrado de extremo a extremo (ver [Cifrado]), igual en las cuatro apps. Las conversaciones
 * viven solo en este teléfono ([archivo]): no van en el respaldo ni quedan en el servidor, que solo pasa los sobres.
 * [decir] recibe un código: "sin_llaves", "no_se_puede", "muy_grande", "error", "sin_conexion", "sticker_guardado",
 * "abre_cajon".
 */
class ChatSocial(
    private val app: Context, private val scope: CoroutineScope, private val archivo: File, private val caja: AlmacenChat,
    private val cliente: () -> ClienteSocial, private val usuario: StateFlow<String?>,
    /** Si esta app puede crear llaves nuevas aunque la cuenta ya tenga (El Cajón, como siempre: un teléfono nuevo). */
    private val creaLlaves: Boolean = false,
    private val decir: (String) -> Unit,
) {
    private var privadasSubidas = false
    private var avisoAbreCajon = false
    private val candado = Mutex()
    private val _chats = MutableStateFlow(Chats())
    /** Conversación (el @usuario) → sus mensajes, del más viejo al más nuevo. */
    val conversaciones: StateFlow<Map<String, List<MensajeChat>>> get() = _conversaciones
    private val _conversaciones = MutableStateFlow<Map<String, List<MensajeChat>>>(emptyMap())
    private val _estilos = MutableStateFlow<Map<String, EstiloChat>>(emptyMap())
    val estilos: StateFlow<Map<String, EstiloChat>> = _estilos
    private val _stickers = MutableStateFlow<List<Sticker>>(emptyList())
    val stickers: StateFlow<List<Sticker>> = _stickers
    private val _personas = MutableStateFlow<Map<String, PersonaSocial>>(emptyMap())
    val personas: StateFlow<Map<String, PersonaSocial>> = _personas
    private val _confirmaLectura = MutableStateFlow(true)
    /** Si se manda y se muestra el «Visto» (se puede apagar en la información de cualquier chat). */
    val confirmaLectura: StateFlow<Boolean> = _confirmaLectura
    private val _enviando = MutableStateFlow(false)
    val enviando: StateFlow<Boolean> = _enviando
    private val llavesDe = HashMap<String, LlavesPublicas>()

    init {
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { JsonSocial.decodeFromString(Chats.serializer(), archivo.readText()) }.getOrNull() }?.let { poner(it) }
            // Al cerrar o borrar la cuenta, el chat de este teléfono se va con ella.
            kotlinx.coroutines.flow.combine(usuario, caja.permisoRed) { usuario, permiso -> usuario to permiso }
                .collect { (u, permiso) ->
                    if (u == null && caja.chatLlaves != null) borrarTodo()
                    else if (u != null && permiso) asegurarLlaves()
                }
        }
        // Mientras la app está abierta, la pregunta queda hecha en el servidor y los mensajes llegan al instante.
        scope.launch {
            while (true) {
                if (usuario.value == null) { kotlinx.coroutines.delay(5_000); continue }
                // Sin llaves todavía (otra app aún no las compartió): se vuelve a intentar cada tanto.
                if (propias() == null) { runCatching { asegurarLlaves() }; kotlinx.coroutines.delay(30_000); continue }
                if (!privadasSubidas && caja.llaveCuenta != null) runCatching { asegurarLlaves() }
                val sobres = runCatching { cliente().esperarMensajes(_chats.value.ultimo) }.getOrNull()
                if (sobres == null) kotlinx.coroutines.delay(5_000) else procesar(sobres)
            }
        }
    }

    private fun poner(c: Chats) {
        _chats.value = c; _confirmaLectura.value = !c.sinVisto; _conversaciones.value = c.conversaciones; _estilos.value = c.estilos; _stickers.value = c.stickers; _personas.value = c.personas
    }

    /** Cambia algo de los chats guardados (de a uno a la vez) y lo escribe. */
    private suspend fun cambiar(f: (Chats) -> Chats) = candado.withLock { poner(f(_chats.value)); guardar() }

    private suspend fun guardar() = withContext(Dispatchers.IO) { runCatching { archivo.writeText(JsonSocial.encodeToString(Chats.serializer(), _chats.value)) } }

    private fun propias(): Cifrado.Propias? = caja.chatLlaves?.let { runCatching { JsonSocial.decodeFromString(Cifrado.Propias.serializer(), it) }.getOrNull() }

    /**
     * Que este teléfono tenga las llaves de la cuenta y el servidor las públicas. Las privadas se comparten entre las
     * apps cifradas con la llave de la contraseña: si otra app ya las subió, se usan esas; si la cuenta ya tiene
     * llaves pero no se pueden traer, no se crean otras (romperían el chat de la otra app).
     */
    private suspend fun asegurarLlaves() {
        val llave = caja.llaveCuenta
        val locales = propias()
        val remotas = runCatching { cliente().llavesChat() }.getOrNull()
        if (locales != null) {
            if (llave != null && remotas == "") runCatching { cliente().ponerLlavesChat(withContext(Dispatchers.Default) { NubeCifrada.cerrar(JsonSocial.encodeToString(Cifrado.Propias.serializer(), locales), llave) }) }.onSuccess { privadasSubidas = true }
            if (llave != null && !remotas.isNullOrBlank()) privadasSubidas = true
            runCatching { cliente().ponerLlaves(withContext(Dispatchers.Default) { Cifrado.publicas(locales) }) }
            return
        }
        if (llave != null && !remotas.isNullOrBlank()) {
            val texto = runCatching { withContext(Dispatchers.Default) { NubeCifrada.abrir(remotas, llave) } }.getOrNull()
            if (texto != null && runCatching { JsonSocial.decodeFromString(Cifrado.Propias.serializer(), texto) }.isSuccess) { caja.chatLlaves = texto; return }
        }
        if (remotas == null) return
        val yo = usuario.value ?: return
        val yaTiene = runCatching { cliente().llaves(yo) }.isSuccess
        if (yaTiene && remotas.isBlank() && !creaLlaves) { if (!avisoAbreCajon) { avisoAbreCajon = true; decir("abre_cajon") }; return }
        val nuevas = withContext(Dispatchers.Default) { Cifrado.nuevas() }
        caja.chatLlaves = JsonSocial.encodeToString(Cifrado.Propias.serializer(), nuevas)
        runCatching { cliente().ponerLlaves(withContext(Dispatchers.Default) { Cifrado.publicas(nuevas) }) }
        if (llave != null) runCatching { cliente().ponerLlavesChat(withContext(Dispatchers.Default) { NubeCifrada.cerrar(JsonSocial.encodeToString(Cifrado.Propias.serializer(), nuevas), llave) }) }.onSuccess { privadasSubidas = true }
    }

    /** Si este teléfono ya puede chatear (tiene las llaves). */
    fun listo(): Boolean = propias() != null

    /** Las llaves públicas de [usuario]; [frescas] las pide de nuevo (cambian si la persona entró en otro teléfono). */
    private suspend fun llaves(usuario: String, frescas: Boolean = false): LlavesPublicas =
        (if (frescas) null else llavesDe[usuario]) ?: cliente().llaves(usuario).also { llavesDe[usuario] = it }

    /** El número de seguridad con [usuario] (null si todavía no se puede saber). */
    suspend fun numeroSeguridad(usuario: String): String? = runCatching {
        val p = propias() ?: return null
        Cifrado.numeroSeguridad(Cifrado.publicas(p), llaves(usuario))
    }.getOrNull()

    /** Manda unas palabras, una obra (como tarjeta) o un sticker a [usuario]; con [responde], como respuesta a ese mensaje. */
    fun enviar(usuario: String, texto: String, obra: JsonObject? = null, responde: MensajeChat? = null, sticker: String? = null, tamano: Int = 1,
               foto: String? = null, audio: String? = null, segundos: Int = 0) = scope.launch {
        val yo = this@ChatSocial.usuario.value ?: return@launch
        val ahora = System.currentTimeMillis()
        val o = obra
        val r = responde?.let { Respuesta(it.clave, if (it.mio) yo else usuario, resumenCorto(it), sticker = it.sticker != null) }
        val id = "m${ahora.toString(36)}"
        val z = tamano.coerceIn(0, 2).takeIf { sticker != null && it != 1 }
        val d = segundos.takeIf { audio != null }
        val c = Contenido(texto.trim().take(4000), o, ahora, i = id, r = r, s = sticker, z = z, f = foto, a = audio, d = d)
        if (mandar(usuario, yo, c)) agregar(usuario, MensajeChat(id, true, ahora, texto.trim(), o, leido = true, k = id, responde = r, sticker = sticker, tamano = z ?: 1,
            foto = foto, audio = audio, segundos = d ?: 0))
    }

    /** Reaccionar a un mensaje con un emoji (o quitar la reacción, con ""), como en Instagram. */
    fun reaccion(usuario: String, m: MensajeChat, emoji: String) = scope.launch {
        val yo = this@ChatSocial.usuario.value ?: return@launch
        val ahora = System.currentTimeMillis()
        if (mandar(usuario, yo, Contenido(c = ahora, i = "r${ahora.toString(36)}", rk = m.clave, x = emoji))) reaccionar(usuario, m.clave, emoji, mia = true)
    }

    private suspend fun reaccionar(usuario: String, clave: String, emoji: String, mia: Boolean) = cambiar { c ->
        c.copy(conversaciones = c.conversaciones + (usuario to c.conversaciones[usuario].orEmpty().map {
            if (it.clave == clave) (if (mia) it.copy(reaccionMia = emoji) else it.copy(reaccionSuya = emoji)) else it
        }))
    }

    /** Un pedacito de un mensaje, para citarlo al responder. */
    private fun resumenCorto(m: MensajeChat): String = when {
        m.sticker != null -> ""
        m.foto != null -> "\uD83D\uDCF7"
        m.audio != null -> "\uD83C\uDFA4"
        m.obra != null -> "\u25B8 " + ((m.obra["titulo"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "")
        else -> m.texto.take(120)
    }

    /** Cierra y manda un contenido; false si no se pudo (ya avisó por qué). */
    private suspend fun mandar(usuario: String, yo: String, c: Contenido, silencioso: Boolean = false): Boolean {
        val p = propias() ?: run { asegurarLlaves(); propias() } ?: return false
        _enviando.value = true
        try {
            val suyas = try { llaves(usuario, frescas = true) } catch (x: ErrorSocial) {
                if (x.codigo == 404) { decir("sin_llaves"); return false } else throw x
            }
            val contenido = JsonSocial.encodeToString(Contenido.serializer(), c).toByteArray()
            val (sobre, firma) = withContext(Dispatchers.Default) { Cifrado.cerrar(contenido, yo, usuario, suyas, p) }
            cliente().mandar(usuario, sobre, firma, silencioso)
            return true
        } catch (x: ErrorSocial) {
            decir(when (x.error) { "no_existe" -> "no_se_puede"; "muy_grande" -> "muy_grande"; else -> "error" }); return false
        } catch (x: IOException) { decir("sin_conexion"); return false }
        finally { _enviando.value = false }
    }

    /** Borra un mensaje solo de este teléfono. */
    fun borrarParaMi(usuario: String, m: MensajeChat) = scope.launch {
        cambiar { c -> c.copy(conversaciones = c.conversaciones + (usuario to c.conversaciones[usuario].orEmpty().filter { it.id != m.id })) }
    }

    /** Borra un mensaje propio también en el teléfono de la otra persona (le manda la orden, cifrada como todo). */
    fun borrarParaTodos(usuario: String, m: MensajeChat) = scope.launch {
        if (!m.mio || m.borrado) return@launch
        val yo = this@ChatSocial.usuario.value ?: return@launch
        if (mandar(usuario, yo, Contenido(c = System.currentTimeMillis(), b = m.clave))) marcarBorrado(usuario, m.clave, mio = true)
    }

    private suspend fun marcarBorrado(usuario: String, clave: String, mio: Boolean) = cambiar { c ->
        c.copy(conversaciones = c.conversaciones + (usuario to c.conversaciones[usuario].orEmpty().map {
            if (it.clave == clave && it.mio == mio) it.copy(texto = "", obra = null, sticker = null, foto = null, audio = null, responde = null, borrado = true) else it
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
        decir("sticker_guardado")
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

    /** La foto para el editor de stickers (lado mayor de hasta 1600 px). */
    suspend fun fotoParaSticker(uri: android.net.Uri): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
            val medidas = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, medidas)
            var muestra = 1
            while (maxOf(medidas.outWidth, medidas.outHeight) / (muestra * 2) >= 1600) muestra *= 2
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = muestra })
        }.getOrNull()
    }

    /** El sticker con el recorte que se eligió en el editor ([recorte], en píxeles de la foto) y, si [redondo], en círculo. */
    suspend fun stickerRecortado(foto: android.graphics.Bitmap, recorte: android.graphics.Rect, redondo: Boolean): String? = withContext(Dispatchers.Default) {
        runCatching {
            val r = android.graphics.Rect(recorte.left.coerceIn(0, foto.width - 1), recorte.top.coerceIn(0, foto.height - 1), 0, 0).apply {
                val lado = minOf(recorte.width(), foto.width - left, foto.height - top).coerceAtLeast(1)
                right = left + lado; bottom = top + lado
            }
            val cuadrada = android.graphics.Bitmap.createBitmap(foto, r.left, r.top, r.width(), r.height())
            if (!redondo) return@runCatching aSticker(cuadrada)
            val salida = android.graphics.Bitmap.createBitmap(cuadrada.width, cuadrada.height, android.graphics.Bitmap.Config.ARGB_8888)
            val lienzo = android.graphics.Canvas(salida)
            val pincel = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            lienzo.drawCircle(cuadrada.width / 2f, cuadrada.height / 2f, cuadrada.width / 2f, pincel)
            pincel.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
            lienzo.drawBitmap(cuadrada, 0f, 0f, pincel)
            aSticker(salida)
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
        val p = runCatching { cliente().perfil(usuario).persona }.getOrNull() ?: return@launch
        if (_personas.value[usuario] != p) cambiar { c -> c.copy(personas = c.personas + (usuario to p)) }
    }

    private suspend fun agregar(usuario: String, m: MensajeChat, ultimo: Long? = null) = candado.withLock {
        val c = _chats.value
        val lista = agregarMensajeChat(c.conversaciones[usuario].orEmpty(), m)
        poner(c.copy(conversaciones = c.conversaciones + (usuario to lista), ultimo = ultimo ?: c.ultimo))
        guardar()
    }

    /** Trae los sobres nuevos, los abre y los guarda; al pedir los siguientes, el servidor borra los ya recibidos. */
    fun recibir() = scope.launch { procesar(runCatching { cliente().recibir(_chats.value.ultimo) }.getOrNull() ?: return@launch) }

    /** Abre y guarda los sobres que llegaron, y confirma (el servidor borra hasta el último). */
    private suspend fun procesar(sobres: List<Sobre>) = recibiendo.withLock {
        val yo = usuario.value ?: return@withLock
        val p = propias() ?: return@withLock
        val (nuevos, cursor) = decidirSobres(_chats.value.ultimo, sobres, _chats.value.conversaciones)
        for (s in nuevos) {
            val de = s.de.usuario
            // Si no abre con las llaves que se tenían, se piden otra vez (la persona pudo cambiar de teléfono) y se reintenta.
            fun abrirCon(l: LlavesPublicas) = JsonSocial.decodeFromString(Contenido.serializer(), String(Cifrado.abrir(s.sobre, s.firma, de, yo, l, p)))
            val c = withContext(Dispatchers.Default) {
                runCatching { abrirCon(llaves(de)) }.getOrNull() ?: runCatching { abrirCon(llaves(de, frescas = true)) }.getOrNull()
            }
            when {
                c == null -> agregar(de, MensajeChat(claveSobre(s), false, s.cuando, noSeLee = true), s.id)
                // La otra persona borró un mensaje suyo para todos.
                c.b != null -> { marcarBorrado(de, c.b, mio = false); cambiar { x -> x.copy(ultimo = maxOf(x.ultimo, s.id)) } }
                c.v != null -> { marcarVisto(de, c.v); cambiar { x -> x.copy(ultimo = maxOf(x.ultimo, s.id)) } }
                c.rk != null -> { reaccionar(de, c.rk, c.x.orEmpty().take(8), mia = false); cambiar { x -> x.copy(ultimo = maxOf(x.ultimo, s.id)) } }
                else -> agregar(de, MensajeChat(claveSobre(s), false, s.cuando, c.t, c.o, k = c.i, responde = c.r, sticker = c.s?.takeIf { it.length <= STICKER_MAX }, tamano = (c.z ?: 1).coerceIn(0, 2),
                    foto = c.f?.takeIf { it.length <= ADJUNTO_MAX }, audio = c.a?.takeIf { it.length <= ADJUNTO_MAX }, segundos = (c.d ?: 0).coerceIn(0, 60)), s.id)
            }
        }
        if (sobres.isNotEmpty()) cambiar { it.copy(ultimo = cursor) }
        // Confirmar lo recibido (el servidor borra hasta el último).
        if (nuevos.isNotEmpty()) runCatching { cliente().recibir(_chats.value.ultimo) }
    }
    private val recibiendo = Mutex()

    fun sinLeer(): Int = _conversaciones.value.values.sumOf { l -> l.count { !it.mio && !it.leido } }

    fun marcarLeida(usuario: String) = scope.launch {
        val visto = candado.withLock {
            val c = _chats.value
            val l = c.conversaciones[usuario] ?: return@withLock null
            if (l.none { !it.leido }) return@withLock null
            poner(c.copy(conversaciones = c.conversaciones + (usuario to l.map { it.copy(leido = true) })))
            guardar()
            l.lastOrNull { !it.mio && !it.noSeLee && it.k.isNotBlank() }?.k
        }
        // El «Visto», cifrado como todo y sin avisar.
        if (visto != null && _confirmaLectura.value) {
            val yo = this@ChatSocial.usuario.value ?: return@launch
            val ahora = System.currentTimeMillis()
            mandar(usuario, yo, Contenido(c = ahora, i = "v${ahora.toString(36)}", v = visto), silencioso = true)
        }
    }

    /** La otra persona leyó hasta [clave]: los mensajes propios hasta ese quedan vistos. */
    private suspend fun marcarVisto(usuario: String, clave: String) = cambiar { c ->
        val l = c.conversaciones[usuario].orEmpty()
        val hasta = l.indexOfLast { it.mio && it.clave == clave }
        if (hasta < 0) c else c.copy(conversaciones = c.conversaciones + (usuario to l.mapIndexed { i, m -> if (m.mio && i <= hasta && !m.visto) m.copy(visto = true) else m }))
    }

    fun ponerConfirmaLectura(si: Boolean) = scope.launch { cambiar { it.copy(sinVisto = !si) } }

    /** Una foto para mandar: lado mayor de hasta 1080 px, en JPEG, bajando tamaño y calidad hasta que quepa en un sobre. */
    suspend fun fotoParaMensaje(uri: android.net.Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
            val medidas = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, medidas)
            var muestra = 1
            while (maxOf(medidas.outWidth, medidas.outHeight) / (muestra * 2) >= 1080) muestra *= 2
            var foto = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = muestra }) ?: return@runCatching null
            // Derecha según la cámara.
            val giro = runCatching { app.contentResolver.openInputStream(uri)?.use { when (android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90; android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270; else -> 0 } } }.getOrNull() ?: 0
            if (giro != 0) foto = android.graphics.Bitmap.createBitmap(foto, 0, 0, foto.width, foto.height, android.graphics.Matrix().apply { postRotate(giro.toFloat()) }, true)
            for (lado in listOf(1080, 860, 680, 520)) {
                val e = minOf(1f, lado.toFloat() / maxOf(foto.width, foto.height))
                val chica = if (e < 1f) android.graphics.Bitmap.createScaledBitmap(foto, (foto.width * e).toInt().coerceAtLeast(1), (foto.height * e).toInt().coerceAtLeast(1), true) else foto
                for (calidad in listOf(70, 55, 40)) {
                    val salida = java.io.ByteArrayOutputStream()
                    chica.compress(android.graphics.Bitmap.CompressFormat.JPEG, calidad, salida)
                    val b64 = java.util.Base64.getEncoder().encodeToString(salida.toByteArray())
                    if (b64.length <= ADJUNTO_MAX) return@runCatching b64
                }
            }
            null
        }.getOrNull()
    }

    fun borrarConversacion(usuario: String) = scope.launch {
        candado.withLock { val c = _chats.value; poner(c.copy(conversaciones = c.conversaciones - usuario)); guardar() }
    }

    companion object {
        /** El tamaño máximo de un sticker en base64 (cabe en un sobre del servidor con lo demás). */
        const val STICKER_MAX = 60_000
        /** Lo mismo para una foto o una nota de voz (unos 45 KB: una foto chica o medio minuto de voz). */
        const val ADJUNTO_MAX = 62_000
        /** La nota de voz más larga, en segundos. */
        const val VOZ_MAX = 30
    }

    private suspend fun borrarTodo() {
        candado.withLock { poner(Chats()); withContext(Dispatchers.IO) { archivo.delete() } }
        caja.chatLlaves = null
        llavesDe.clear()
    }
}

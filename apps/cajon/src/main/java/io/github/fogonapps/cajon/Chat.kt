package io.github.fogonapps.cajon

/*
 * El chat cifrado vive en lo común (`core/social/ChatSocial.kt`) y es el mismo en las cuatro apps. Aquí quedan los
 * nombres de siempre de El Cajón (mismo archivo `chats.json` y mismas llaves de la caja fuerte).
 */
typealias Chat = io.github.fogonapps.social.ChatSocial
typealias MensajeChat = io.github.fogonapps.social.MensajeChat
typealias Respuesta = io.github.fogonapps.social.Respuesta
typealias Sticker = io.github.fogonapps.social.Sticker
typealias EstiloChat = io.github.fogonapps.social.EstiloChat

internal fun claveSobre(s: io.github.fogonapps.social.Sobre) = io.github.fogonapps.social.claveSobre(s)
internal fun decidirSobres(ultimo: Long, sobres: List<io.github.fogonapps.social.Sobre>, conversaciones: Map<String, List<MensajeChat>>) =
    io.github.fogonapps.social.decidirSobres(ultimo, sobres, conversaciones)
internal fun agregarMensajeChat(lista: List<MensajeChat>, mensaje: MensajeChat) = io.github.fogonapps.social.agregarMensajeChat(lista, mensaje)

/** El chat de un perfil de El Cajón: su archivo, sus llaves en la caja fuerte y la llave de la cuenta para compartirlas. */
fun chatDeCajon(app: CajonApp, scope: kotlinx.coroutines.CoroutineScope, caja: io.github.fogonapps.cajon.datos.CajaFuerte, red: RedSocial, decir: (Int) -> Unit) =
    Chat(app, scope, java.io.File(app.recursos(caja.perfilId).directorio, "chats.json"), object : io.github.fogonapps.social.AlmacenChat {
        override var chatLlaves: String?
            get() = caja.chatLlaves
            set(v) { caja.chatLlaves = v }
        override val llaveCuenta: String? get() = caja.llaveCajon
        override val permisoRed = caja.permisoRed
    }, { caja.clienteSocial() }, red.usuario, creaLlaves = true) { codigo ->
        decir(when (codigo) {
            "sin_llaves" -> R.string.chat_sin_llaves; "no_se_puede" -> R.string.chat_no_se_puede; "muy_grande" -> R.string.chat_muy_grande
            "sin_conexion" -> R.string.red_sin_conexion; "sticker_guardado" -> R.string.chat_sticker_guardado
            else -> R.string.red_error
        })
    }

/** Mandar una obra del cajón como tarjeta. */
fun Chat.enviarObra(usuario: String, texto: String, obra: io.github.fogonapps.cajon.datos.Obra) = enviar(usuario, texto, obraJson(obra))

/** Una obra como tarjeta del chat (lo que ve la otra persona). */
fun obraJson(obra: io.github.fogonapps.cajon.datos.Obra) = kotlinx.serialization.json.buildJsonObject {
    put("id", kotlinx.serialization.json.JsonPrimitive(obra.id)); put("titulo", kotlinx.serialization.json.JsonPrimitive(obra.titulo)); put("tipoObra", kotlinx.serialization.json.JsonPrimitive(obra.tipo))
    put("portada", kotlinx.serialization.json.JsonPrimitive(obra.portada.takeIf { u -> u.startsWith("https://") }.orEmpty()))
    obra.anio?.let { put("anio", kotlinx.serialization.json.JsonPrimitive(it)) }
    if (obra.fuente.isNotBlank()) put("fuente", kotlinx.serialization.json.JsonPrimitive(obra.fuente))
}

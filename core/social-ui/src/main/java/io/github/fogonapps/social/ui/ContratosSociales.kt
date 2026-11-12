package io.github.fogonapps.social.ui

import androidx.compose.runtime.Composable
import io.github.fogonapps.social.*
import kotlinx.coroutines.flow.StateFlow

/** Destinos resueltos por la app, sin transportar su árbol de pantallas. */
data class NavegacionSocial(
    val perfil: (String) -> Unit,
    val publicacion: (Long) -> Unit,
    val etiqueta: (String) -> Unit,
    val cuenta: () -> Unit,
    val ajustes: (AjusteSocial) -> Unit,
    val adjunto: (AdjuntoSocial) -> Unit,
    val volver: () -> Unit,
    val gente: (String, Boolean) -> Unit,
)
data class AdjuntoSocial(val tipo: String, val id: String)

enum class AjusteSocial { SESIONES, ESTADO_CUENTA, BLOQUEADOS, BORRAR_CUENTA }

/** La app conserva sus compositores y la interpretación de sus datos. */
interface ContenidoSocialApp {
    val tiposAdmitidos: Set<String>
    fun multimediaEnCuerpo(tipo: String): Boolean
    @Composable fun Compositor(tipo: String, adjunto: AdjuntoSocial?)
    @Composable fun NombreTipo(tipo: String): String
    @Composable fun Cuerpo(publicacion: PublicacionSocial, destapado: Boolean)
    @Composable fun SeccionesPerfil(perfil: PerfilSocial, propio: Boolean, contenido: @Composable (Int) -> Unit): List<io.github.fogonapps.perfil.SeccionPerfil>
    @Composable fun CifrasPerfil(perfil: PerfilSocial)
}

/** Operaciones que necesitan almacenamiento o reglas locales de la app. */
interface InterfazSocial {
    val textosApp: TextosSocialApp
    val red: ControladorCuentaSocial
    val navegacion: NavegacionSocial
    val contenido: ContenidoSocialApp
    val confirmar: (String, String, () -> Unit) -> Unit
    fun tx(recurso: Int): String
    @Composable fun palabrasOcultas(): List<String>
    @Composable fun cantidadModeracionApp(): Int
    @Composable fun tituloModeracionApp(): String
    val correoDisponible: StateFlow<Boolean?>
    fun salir()
    fun interesa(publicacion: PublicacionSocial, si: Boolean)
    fun silenciar(usuario: String)
    fun mostrarComentario(palabras: List<String>, texto: String): Boolean
    fun crearConCorreo(usuario: String, nombre: String, clave: String, foto: String?)
    fun ponerClave(nueva: String, actual: String, listo: () -> Unit)
    fun cambiarUsuario(nuevo: String, listo: () -> Unit)
    fun vincularCorreo(codigo: String)
    fun vista(id: Long)
    fun borrarPublicacion(id: Long, clave: String)
    fun quitarDeLaRed(clave: String)
    fun cabeceras(): Map<String, String>
    @Composable fun menu(publicacion: PublicacionSocial, cerrar: () -> Unit)
    @Composable fun moderacionApp()
    @Composable fun contenidoReporte(contenido: String)
    /**
     * Lo que la app ofrece para adjuntar a una historia o a una nota (además de una canción): sus obras, entrenos,
     * récords, platos o metas, como adjuntos ({"tipo", "titulo", "detalle", "imagen", "cifra"…}; ver `Adjuntos` en el servidor).
     */
    fun adjuntosApp(): List<kotlinx.serialization.json.JsonObject> = emptyList()
    /** Completa un adjunto antes de usarlo (por ejemplo, la escena del tráiler de una película). */
    suspend fun completarAdjunto(a: kotlinx.serialization.json.JsonObject): kotlinx.serialization.json.JsonObject = a
    /** Los stickers propios de la app (WebP en base64), para ponerlos en una historia. */
    fun stickersApp(): List<String> = emptyList()
    /** Buscar en las fuentes de la app algo para adjuntar (El Cajón: películas, series, animes, libros y álbumes de cualquier lado). */
    suspend fun buscarAdjuntos(q: String): List<kotlinx.serialization.json.JsonObject> = emptyList()
    /** Lo que dice el buscador de adjuntos, si la app busca más que canciones. */
    @Composable fun textoBuscarAdjunto(): String? = null
    /** Si la app ofrece fotos de obras o personas para el fondo de una historia (El Cajón: pósteres, escenas, portadas, retratos). */
    val fotosDeObras: Boolean get() = false
    /** Responder una historia por mensaje (solo si la app tiene chat, como El Cajón). */
    val respondeHistorias: Boolean get() = false
    fun responderHistoria(usuario: String, texto: String) {}
    /** Lo que hay dentro de un adjunto para elegir uno (las canciones de un álbum o de un artista). */
    suspend fun detallarAdjunto(a: kotlinx.serialization.json.JsonObject): List<kotlinx.serialization.json.JsonObject> = emptyList()
    /** Buscar obras o personas con fotos ({"titulo", "detalle", "imagen", "fuente"}). */
    suspend fun buscarConFotos(q: String): List<kotlinx.serialization.json.JsonObject> = emptyList()
    /** Las fotos para elegir de una obra o persona ([fuente], como "tmdb:movie:603"). */
    suspend fun fotosDe(fuente: String): List<String> = emptyList()
}

/** Explicaciones de la app sobre contenido público y recuperación privada. */
data class TextosSocialApp(
    val introduccion: String,
    val contenido: String,
    val compartido: String,
    val recuperacion: String,
    val borradoExplicacion: String,
    val borradoSeguro: String,
    val sinClave: String,
    val pausaPolitica: String,
    val resumenPolitica: String,
    val explicacionOrden: String,
)

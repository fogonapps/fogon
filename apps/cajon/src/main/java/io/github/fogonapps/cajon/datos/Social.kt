package io.github.fogonapps.cajon.datos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Un error del servidor social: el código HTTP y la palabra que manda ("usuario_tomado", "privada", "despacio"…). */
class ErrorSocial(val codigo: Int, val error: String) : IOException("HTTP $codigo $error")

/* Lo que devuelve Fogón Social (los mismos campos que el servidor). */

@Serializable data class PersonaSocial(val usuario: String, val nombre: String = "", val foto: Boolean = false, val privada: Boolean = false)

@Serializable
data class PublicacionSocial(
    val id: Long, val autor: PersonaSocial, val tipo: String, val cuando: Long, val visibilidad: String, val datos: JsonObject,
    val megusta: Int = 0, val comentarios: Int = 0, val yoMeGusta: Boolean = false, val yoGuardo: Boolean = false, val vistas: Int = 0,
    /** Qué tan bien le fue comparada con lo normal (1 = normal), la misma para todos: la calcula el servidor. */
    val calidad: Double = 1.0, val guardados: Int = 0,
)

@Serializable data class ComentarioSocial(val id: Long, val autor: PersonaSocial, val texto: String, val cuando: Long, val puedoBorrar: Boolean = false)

@Serializable
data class PerfilSocial(
    val persona: PersonaSocial, val bio: String = "", val portada: String = "", val enlaces: List<String> = emptyList(),
    val partes: Map<String, JsonElement> = emptyMap(), val publicaciones: List<PublicacionSocial> = emptyList(),
    /** "yo", "sigo", "pendiente" o "nada". */
    val relacion: String = "nada", val meSigue: Boolean = false,
    val seguidores: Int = 0, val siguiendo: Int = 0, val cuantas: Int = 0, val cerrada: Boolean = false,
    val megusta: Int = 0, val guardadasVisibles: Boolean = false,
)

@Serializable data class AvisoSocial(val id: Long, val tipo: String, val de: PersonaSocial, val objeto: String = "", val cuando: Long, val leido: Boolean = false)

@Serializable data class ParteSubida(val visibilidad: String, val datos: JsonElement)

@Serializable
data class PerfilSubido(
    val nombre: String = "", val bio: String = "", val portada: String = "", val enlaces: List<String> = emptyList(),
    val partes: Map<String, ParteSubida> = emptyMap(),
)

@Serializable data class PublicacionSubida(val clave: String, val visibilidad: String, val cuando: Long, val tipo: String, val datos: JsonObject)

@Serializable private data class Entrada(
    val correo: String = "", val codigo: String = "", val usuario: String = "", val nombre: String = "",
    val acepta: String = "", val mayorDeEdad: Boolean = false, val clave: String = "",
)
@Serializable private data class Correo(val correo: String)
/** Si el correo tiene cuenta y contraseña (para registrarse o entrar). */
@Serializable data class EstadoCorreo(val existe: Boolean = false, val conClave: Boolean = false)
/** La sesión que abre el servidor: [nueva] si se acaba de crear la cuenta. */
@Serializable data class Acceso(val usuario: String, val token: String, val nueva: Boolean = false)
@Serializable data class MiCuenta(val usuario: String, val nombre: String = "", val correo: String = "", val privada: Boolean = false,
    val conClave: Boolean = false, val usuarioDesde: Long = 0, val admin: Boolean = false)
@Serializable data class CasoModeracion(val que: String, val objeto: String, val motivos: Map<String, Int> = emptyMap(), val personas: Int = 0, val oculto: Boolean = false,
    val contenido: String = "", val autor: PersonaSocial? = null, val faltasAutor: Int = 0)
@Serializable private data class Decision(val que: String, val objeto: String, val decision: String, val regla: String = "")
@Serializable data class FaltaSocial(val regla: String, val puntos: Int, val cuando: Long, val vence: Long)
@Serializable data class EstadoCuentaSocial(val faltas: List<FaltaSocial> = emptyList(), val restringidaHasta: Long = 0, val suspendidaHasta: Long = 0,
    val cerrada: Boolean = false, val admin: Boolean = false)
@Serializable private data class EntradaClave(val correo: String, val clave: String)
@Serializable private data class CambioClave(val nueva: String, val actual: String = "")
@Serializable private data class CambioUsuario(val usuario: String)
/** Lo que dice el servidor de sí mismo: si puede mandar códigos por correo y la versión de la política. */
@Serializable data class InfoServidor(val version: String = "", val correo: String = "false", val politica: String = "")
@Serializable private data class Texto(val texto: String)
@Serializable private data class Reporte(val que: String, val objeto: String, val motivo: String)
@Serializable private data class Privada(val privada: Boolean)
@Serializable data class CajonCifrado(val sobre: String = "", val cambio: Long = 0, val llave: String = "")
/** Lo que la gente está buscando en Wikipedia (lo calcula el servidor cada 12 horas), ver `Momento` en el servidor. */
@Serializable data class Buscado(val titulo: String, val vistas: Long = 0, val wikidata: String = "", val tmdb: String = "")
@Serializable data class DelMomentoServidor(val dia: String = "", val peliculas: List<Buscado> = emptyList(), val series: List<Buscado> = emptyList(), val anime: List<Buscado> = emptyList())
@Serializable data class ConteoFrase(val si: Int = 0, val no: Int = 0)
@Serializable data class VotoFrase(val valor: Int)
@Serializable data class FraseNueva(val texto: String, val idioma: String)
/** Una frase propuesta por la gente (aprobada; con [de] cuando es para moderar). */
@Serializable data class FraseComunidad(val clave: String, val texto: String, val idioma: String = "", val de: String = "")
@Serializable data class DecisionFrase(val clave: String, val aprobar: Boolean)
@Serializable data class LlavesPublicas(val cifrar: String, val firmar: String)
@Serializable data class NotaNueva(val texto: String, val datos: JsonObject = JsonObject(emptyMap()))
@Serializable data class NotaSocial(val persona: PersonaSocial, val texto: String, val datos: JsonObject = JsonObject(emptyMap()), val cuando: Long)
@Serializable private data class SobreNuevo(val sobre: String, val firma: String)
@Serializable data class Sobre(val id: Long, val de: PersonaSocial, val cuando: Long, val sobre: String, val firma: String)

/**
 * El cliente de Fogón Social, el servidor de Fogón (perfiles, seguir, feed, me gusta, comentarios, avisos). Todo es
 * opcional: sin cuenta, la app funciona igual y nada sale del teléfono. [token] es el de la cuenta (en la caja fuerte).
 */
class ClienteSocial(private val token: String? = null) {

    private suspend fun pedir(ruta: String, metodo: String = "GET", cuerpo: okhttp3.RequestBody? = null): String? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(SERVIDOR + "/v1" + ruta).method(metodo, cuerpo ?: if (metodo in setOf("POST", "PUT")) ByteArray(0).toRequestBody() else null)
            .header("User-Agent", "ElCajon-Fogon")
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .build()
        http.newCall(req).execute().use { r ->
            val texto = r.body.string()
            if (!r.isSuccessful) {
                val error = runCatching { JsonCajon.parseToJsonElement(texto).jsonObject["error"]!!.jsonPrimitive.content }.getOrDefault("")
                throw ErrorSocial(r.code, error)
            }
            texto.ifEmpty { null }
        }
    }

    private fun <T> json(s: KSerializer<T>, v: T) = JsonCajon.encodeToString(s, v).toRequestBody(JSON)
    private fun <T> leer(s: KSerializer<T>, texto: String?) = JsonCajon.decodeFromString(s, texto ?: "null")
    private fun cod(s: String) = URLEncoder.encode(s, "UTF-8")

    /* Cuenta */
    suspend fun info(): InfoServidor = leer(InfoServidor.serializer(), pedir("/estado"))
    suspend fun usuarioLibre(usuario: String): Boolean =
        JsonCajon.parseToJsonElement(pedir("/usuarios/${cod(usuario)}/libre")!!).jsonObject["libre"]!!.jsonPrimitive.boolean
    /** Crea la cuenta sin correo (cuando el servidor no manda códigos): queda en este teléfono. */
    suspend fun crearSinCorreo(usuario: String, nombre: String, politica: String): Acceso =
        leer(Acceso.serializer(), pedir("/cuentas", "POST", json(Entrada.serializer(), Entrada(usuario = usuario, nombre = nombre, acepta = politica, mayorDeEdad = true))))
    suspend fun pedirCodigo(correo: String) { pedir("/acceso/codigo", "POST", json(Correo.serializer(), Correo(correo))) }
    /** Entra con el código. Para una cuenta nueva el servidor responde "cuenta_nueva" hasta que lleguen [usuario] y la aceptación. */
    suspend fun entrar(correo: String, codigo: String, usuario: String = "", nombre: String = "", politica: String = "", clave: String = ""): Acceso =
        leer(Acceso.serializer(), pedir("/acceso", "POST", json(Entrada.serializer(),
            Entrada(correo, codigo, usuario, nombre, politica, mayorDeEdad = politica.isNotEmpty(), clave = clave))))
    suspend fun estadoCorreo(correo: String) = leer(EstadoCorreo.serializer(), pedir("/acceso/correo", "POST", json(Correo.serializer(), Correo(correo))))
    suspend fun vincularCorreo(correo: String, codigo: String) { pedir("/cuenta/correo", "POST", json(Entrada.serializer(), Entrada(correo, codigo))) }
    suspend fun salir() { pedir("/salir", "POST") }
    suspend fun cajon() = leer(CajonCifrado.serializer(), pedir("/cajon"))
    /** Guarda el cajón cifrado; [antes] es la versión que se trajo (si hay otra más nueva, ErrorSocial 409 "cambio"). */
    suspend fun guardarCajon(sobre: String, antes: Long): Long =
        JsonCajon.parseToJsonElement(pedir("/cajon?antes=$antes", "PUT", json(CajonCifrado.serializer(), CajonCifrado(sobre)))!!).jsonObject["cambio"]!!.jsonPrimitive.content.toLong()
    suspend fun guardarLlaveCajon(envuelta: String) { pedir("/cajon/llave", "PUT", json(CajonCifrado.serializer(), CajonCifrado(llave = envuelta))) }
    suspend fun estadoCuenta() = leer(EstadoCuentaSocial.serializer(), pedir("/cuenta/estado"))
    suspend fun casos() = leer(ListSerializer(CasoModeracion.serializer()), pedir("/admin/casos"))
    suspend fun decidir(que: String, objeto: String, decision: String, regla: String) { pedir("/admin/decidir", "POST", json(Decision.serializer(), Decision(que, objeto, decision, regla))) }
    suspend fun entrarConClave(correo: String, clave: String): Acceso = leer(Acceso.serializer(), pedir("/acceso/clave", "POST", json(EntradaClave.serializer(), EntradaClave(correo, clave))))
    suspend fun ponerClave(nueva: String, actual: String = "") { pedir("/cuenta/clave", "PUT", json(CambioClave.serializer(), CambioClave(nueva, actual))) }
    suspend fun cambiarUsuario(u: String): String =
        JsonCajon.parseToJsonElement(pedir("/cuenta/usuario", "PUT", json(CambioUsuario.serializer(), CambioUsuario(u)))!!).jsonObject["usuario"]!!.jsonPrimitive.content
    suspend fun miCuenta(): MiCuenta = leer(MiCuenta.serializer(), pedir("/cuenta"))
    suspend fun borrarCuenta() { pedir("/cuenta", "DELETE") }
    suspend fun privada(si: Boolean) { pedir("/cuenta", "PUT", json(Privada.serializer(), Privada(si))) }

    /* Perfil */
    suspend fun subirPerfil(p: PerfilSubido) { pedir("/perfil", "PUT", json(PerfilSubido.serializer(), p)) }
    suspend fun subirFoto(jpeg: ByteArray) { pedir("/foto", "PUT", jpeg.toRequestBody(JPEG)) }
    suspend fun quitarFoto() { pedir("/foto", "DELETE") }
    suspend fun perfil(usuario: String): PerfilSocial = leer(PerfilSocial.serializer(), pedir("/perfiles/${cod(usuario)}"))
    suspend fun buscar(q: String): List<PersonaSocial> = leer(ListSerializer(PersonaSocial.serializer()), pedir("/buscar?q=${cod(q)}"))

    /* Seguir */
    suspend fun seguir(usuario: String): String =
        JsonCajon.parseToJsonElement(pedir("/seguir/${cod(usuario)}", "POST")!!).jsonObject["relacion"]!!.jsonPrimitive.content
    suspend fun dejarDeSeguir(usuario: String) { pedir("/seguir/${cod(usuario)}", "DELETE") }
    suspend fun quitarSeguidor(usuario: String) { pedir("/seguidores/${cod(usuario)}", "DELETE") }
    suspend fun seguidores(usuario: String) = leer(ListSerializer(PersonaSocial.serializer()), pedir("/seguidores/${cod(usuario)}"))
    suspend fun siguiendo(usuario: String) = leer(ListSerializer(PersonaSocial.serializer()), pedir("/siguiendo/${cod(usuario)}"))
    suspend fun solicitudes() = leer(ListSerializer(PersonaSocial.serializer()), pedir("/solicitudes"))
    suspend fun aceptar(usuario: String) { pedir("/solicitudes/${cod(usuario)}", "POST") }
    suspend fun rechazar(usuario: String) { pedir("/solicitudes/${cod(usuario)}", "DELETE") }
    suspend fun bloquear(usuario: String, si: Boolean) { pedir("/bloqueos/${cod(usuario)}", if (si) "POST" else "DELETE") }
    suspend fun bloqueados() = leer(ListSerializer(PersonaSocial.serializer()), pedir("/bloqueos"))

    /* Publicaciones */
    suspend fun subirPublicaciones(lista: List<PublicacionSubida>) { pedir("/publicaciones", "PUT", json(ListSerializer(PublicacionSubida.serializer()), lista)) }
    suspend fun feed(antes: Long? = null) = leer(ListSerializer(PublicacionSocial.serializer()), pedir("/actividad" + (antes?.let { "?antes=$it" } ?: "")))
    /** Las reseñas de una obra que puede ver esta persona (las públicas y las de quienes sigue). */
    suspend fun resenas(fuente: String) = leer(ListSerializer(PublicacionSocial.serializer()), pedir("/resenas?fuente=${cod(fuente)}"))
    suspend fun explorar(antes: Long? = null) = leer(ListSerializer(PublicacionSocial.serializer()), pedir("/explorar" + (antes?.let { "?antes=$it" } ?: "")))
    suspend fun publicacion(id: Long) = leer(PublicacionSocial.serializer(), pedir("/publicaciones/$id"))
    suspend fun guardadasDe(usuario: String) = leer(ListSerializer(PublicacionSocial.serializer()), pedir("/perfiles/${cod(usuario)}/guardadas"))
    suspend fun conEtiqueta(etiqueta: String) = leer(ListSerializer(PublicacionSocial.serializer()), pedir("/etiquetas/${cod(etiqueta.removePrefix("#"))}"))
    suspend fun guardados() = leer(ListSerializer(PublicacionSocial.serializer()), pedir("/guardados"))
    suspend fun meGusta(id: Long, si: Boolean) { pedir("/publicaciones/$id/megusta", if (si) "POST" else "DELETE") }
    suspend fun guardar(id: Long, si: Boolean) { pedir("/publicaciones/$id/guardar", if (si) "POST" else "DELETE") }
    suspend fun comentarios(id: Long) = leer(ListSerializer(ComentarioSocial.serializer()), pedir("/publicaciones/$id/comentarios"))
    suspend fun comentar(id: Long, texto: String) { pedir("/publicaciones/$id/comentarios", "POST", json(Texto.serializer(), Texto(texto))) }
    suspend fun borrarComentario(id: Long) { pedir("/comentarios/$id", "DELETE") }
    /** Sube una imagen (JPEG ya achicado) y devuelve su nombre, para ponerlo en la publicación ("imagen"). */
    suspend fun subirImagen(jpeg: ByteArray): String =
        JsonCajon.parseToJsonElement(pedir("/imagenes", "PUT", jpeg.toRequestBody(JPEG))!!).jsonObject["nombre"]!!.jsonPrimitive.content

    /* Chat: solo pasan sobres cerrados (ver `Chat`) */
    suspend fun ponerLlaves(l: LlavesPublicas) { pedir("/llaves", "PUT", json(LlavesPublicas.serializer(), l)) }
    suspend fun llaves(usuario: String) = leer(LlavesPublicas.serializer(), pedir("/llaves/${cod(usuario)}"))
    suspend fun momento(idioma: String): DelMomentoServidor = leer(DelMomentoServidor.serializer(), pedir("/momento?idioma=${cod(idioma)}"))

    /** Los votos de la comunidad a las frases del saludo (clave → sí y no). */
    suspend fun frases(): Map<String, ConteoFrase> = leer(kotlinx.serialization.builtins.MapSerializer(kotlinx.serialization.serializer<String>(), ConteoFrase.serializer()), pedir("/frases"))
    suspend fun frasesComunidad(idioma: String) = leer(ListSerializer(FraseComunidad.serializer()), pedir("/frases/comunidad?idioma=${cod(idioma)}"))
    suspend fun proponerFrase(texto: String, idioma: String) { pedir("/frases/propuestas", "POST", json(FraseNueva.serializer(), FraseNueva(texto, idioma))) }
    suspend fun frasesOcultas() = leer(ListSerializer(kotlinx.serialization.serializer<String>()), pedir("/frases/ocultas"))
    suspend fun agregarFrase(texto: String, idioma: String) = leer(FraseComunidad.serializer(), pedir("/admin/frases", "PUT", json(FraseNueva.serializer(), FraseNueva(texto, idioma))))
    suspend fun quitarFrase(clave: String) { pedir("/admin/frases/${cod(clave)}", "DELETE") }
    suspend fun frasesPendientes() = leer(ListSerializer(FraseComunidad.serializer()), pedir("/admin/frases"))
    suspend fun decidirFrase(clave: String, aprobar: Boolean) { pedir("/admin/frases", "POST", json(DecisionFrase.serializer(), DecisionFrase(clave, aprobar))) }
    suspend fun votarFrase(clave: String, valor: Int) { pedir("/frases/${cod(clave)}", "PUT", json(VotoFrase.serializer(), VotoFrase(valor))) }

    suspend fun mandar(usuario: String, sobre: String, firma: String) { pedir("/mensajes/${cod(usuario)}", "POST", json(SobreNuevo.serializer(), SobreNuevo(sobre, firma))) }
    suspend fun mensajesSinLeer(): Int = JsonCajon.parseToJsonElement(pedir("/mensajes/sin-leer")!!).jsonObject["n"]!!.jsonPrimitive.int
    suspend fun recibir(desde: Long) = leer(ListSerializer(Sobre.serializer()), pedir("/mensajes?desde=$desde"))

    suspend fun vio(ids: List<Long>) { pedir("/vistas", "POST", json(ListSerializer(Long.serializer()), ids)) }
    suspend fun ponerNota(n: NotaNueva) { pedir("/nota", "PUT", json(NotaNueva.serializer(), n)) }
    suspend fun notas() = leer(ListSerializer(NotaSocial.serializer()), pedir("/notas"))
    suspend fun borrarAvisos(id: Long? = null) { pedir("/avisos" + (id?.let { "/$it" } ?: ""), "DELETE") }

    /* Avisos y reportes */
    suspend fun avisos(antes: Long? = null) = leer(ListSerializer(AvisoSocial.serializer()), pedir("/avisos" + (antes?.let { "?antes=$it" } ?: "")))
    suspend fun sinLeer(): Int = JsonCajon.parseToJsonElement(pedir("/avisos/sin-leer")!!).jsonObject["n"]!!.jsonPrimitive.int
    suspend fun leidos() { pedir("/avisos/leidos", "POST") }
    /** [que]: "cuenta", "publicacion", "comentario" o "mensaje". */
    suspend fun reportar(que: String, objeto: String, motivo: String) { pedir("/reportes", "POST", json(Reporte.serializer(), Reporte(que, objeto, motivo))) }

    companion object {
        /** El servidor de Fogón (uno solo para todas las apps). */
        const val SERVIDOR = "https://fogonapps.duckdns.org"
        fun enlacePerfil(usuario: String) = "$SERVIDOR/@$usuario"
        fun enlacePublicacion(id: Long) = "$SERVIDOR/p/$id"
        fun foto(usuario: String) = "$SERVIDOR/v1/fotos/$usuario"
        fun imagen(nombre: String) = "$SERVIDOR/v1/imagenes/$nombre"
        private val JSON = "application/json".toMediaType()
        private val JPEG = "image/jpeg".toMediaType()
        private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    }
}

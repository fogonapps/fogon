package io.github.fogonapps.cajon.datos

import io.github.fogonapps.cajon.R
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
class ErrorSocial(val codigo: Int, val error: String, val desde: Long = 0) : IOException("HTTP $codigo $error")

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
/** La sesión que abre el servidor: [nueva] si se acaba de crear la cuenta. */
@Serializable data class Acceso(val usuario: String, val token: String, val nueva: Boolean = false, val borrarEl: Long = 0)
@Serializable data class MiCuenta(val usuario: String, val nombre: String = "", val correo: String = "", val privada: Boolean = false,
    val conClave: Boolean = false, val apodoDesde: Long = 0, val usuarioDesde: Long = 0, val admin: Boolean = false, val borrarEl: Long = 0,
    val rol: String = "", val permisos: Set<String> = emptySet(), val mfa: Boolean = false, val elevadaHasta: Long = 0)
@Serializable data class CasoModeracion(val que: String, val objeto: String, val motivos: Map<String, Int> = emptyMap(), val personas: Int = 0, val oculto: Boolean = false,
    val contenido: String = "", val autor: PersonaSocial? = null, val faltasAutor: Int = 0, val estado: String = "visible", val retiradoHasta: Long? = null)
@Serializable private data class Decision(val que: String, val objeto: String, val decision: String, val regla: String = "")
@Serializable data class FaltaSocial(val regla: String, val puntos: Int, val cuando: Long, val vence: Long)
@Serializable data class EstadoCuentaSocial(val faltas: List<FaltaSocial> = emptyList(), val restringidaHasta: Long = 0, val suspendidaHasta: Long = 0,
    val cerrada: Boolean = false, val admin: Boolean = false)
@Serializable private data class EntradaClave(val correo: String, val clave: String)
@Serializable private data class Borrado(val clave: String, val codigo: String, val motivo: String, val motivoTexto: String)
@Serializable private data class CambioCorreo(val nuevo: String, val clave: String = "", val codigo: String = "")
@Serializable private data class CambioClave(val nueva: String, val actual: String = "")
@Serializable private data class CambioApodo(val nombre: String)
@Serializable data class SesionSocial(val id: String, val dispositivo: String = "", val creada: Long = 0, @kotlinx.serialization.SerialName("ultimo_uso") val ultimoUso: Long = 0, val actual: Boolean = false)

/** Nombre declarado por Android, apto para una cabecera HTTP. */
fun nombreDispositivo(fabricante: String, modelo: String): String {
    fun limpiar(valor: String) = valor.filter { it.code in 32..126 }.trim().replace(Regex("\\s+"), " ")
    val marca = limpiar(fabricante)
    val telefono = limpiar(modelo)
    return (if (telefono.startsWith(marca, ignoreCase = true)) telefono else "$marca $telefono").trim().take(60)
}

data class SesionMostrada(val id: String, val dispositivo: String?, val creada: Long, val ultimoUso: Long, val actual: Boolean)
fun SesionSocial.paraPantalla() = SesionMostrada(id, dispositivo.trim().takeIf { it.isNotEmpty() }, creada, ultimoUso, actual)

fun esCuentaNueva(error: ErrorSocial) = error.codigo == 409 && error.error == "cuenta_nueva"

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
@Serializable data class CambiosPublicaciones(val lista: List<PublicacionSubida> = emptyList(), val quitar: List<String> = emptyList())
@Serializable data class VotoFrase(val valor: Int)
@Serializable data class FraseNueva(val texto: String, val idioma: String, val franja: String = "todo")
/** Una frase propuesta por la gente (aprobada; con [de] cuando es para moderar). */
@Serializable data class FraseComunidad(val clave: String, val texto: String, val idioma: String = "", val de: String = "", val franja: String = "todo")
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

    private suspend fun pedir(ruta: String, metodo: String = "GET", cuerpo: okhttp3.RequestBody? = null, cliente: OkHttpClient = http): String? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(SERVIDOR + "/v1" + ruta).method(metodo, cuerpo ?: if (metodo in setOf("POST", "PUT")) ByteArray(0).toRequestBody() else null)
            .header("User-Agent", "ElCajon-Fogon")
            .header("X-Fogon-Dispositivo", nombreDispositivo(android.os.Build.MANUFACTURER, android.os.Build.MODEL))
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .build()
        cliente.newCall(req).execute().use { r ->
            val texto = r.body.string()
            if (!r.isSuccessful) {
                val error = runCatching { JsonCajon.parseToJsonElement(texto).jsonObject["error"]!!.jsonPrimitive.content }.getOrDefault("")
                val desde = runCatching { JsonCajon.parseToJsonElement(texto).jsonObject["desde"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L }.getOrDefault(0L)
                if (r.code == 403 && error == "por_borrar" && token != null) cuentasPorBorrar.emit(token to desde)
                throw ErrorSocial(r.code, error, desde)
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
    suspend fun pedirCodigo(correo: String) { pedir("/acceso/codigo", "POST", json(Correo.serializer(), Correo(correo))) }
    /** Entra con el código. Para una cuenta nueva el servidor responde "cuenta_nueva" hasta que lleguen [usuario] y la aceptación. */
    suspend fun entrar(correo: String, codigo: String, usuario: String = "", nombre: String = "", politica: String = "", clave: String = ""): Acceso =
        leer(Acceso.serializer(), pedir("/acceso", "POST", json(Entrada.serializer(),
            Entrada(correo, codigo, usuario, nombre, politica, mayorDeEdad = politica.isNotEmpty(), clave = clave))))
    suspend fun vincularCorreo(correo: String, codigo: String) { pedir("/cuenta/correo", "POST", json(Entrada.serializer(), Entrada(correo, codigo))) }
    suspend fun salir() { pedir("/salir", "POST") }
    suspend fun cajon() = leer(CajonCifrado.serializer(), pedir("/cajon"))
    /** Guarda el cajón cifrado; [antes] es la versión que se trajo (si hay otra más nueva, ErrorSocial 409 "cambio"). */
    suspend fun guardarCajon(sobre: String, antes: Long): Long =
        JsonCajon.parseToJsonElement(pedir("/cajon?antes=$antes", "PUT", json(CajonCifrado.serializer(), CajonCifrado(sobre)))!!).jsonObject["cambio"]!!.jsonPrimitive.content.toLong()
    suspend fun guardarLlaveCajon(envuelta: String) { pedir("/cajon/llave", "PUT", json(CajonCifrado.serializer(), CajonCifrado(llave = envuelta))) }
    suspend fun estadoCuenta() = leer(EstadoCuentaSocial.serializer(), pedir("/cuenta/estado"))
    suspend fun activarMfa(factor: FactorMfa = FactorMfa()) = leer(InscripcionMfa.serializer(), pedir("/mfa/activar", "POST", json(FactorMfa.serializer(), factor)))
    suspend fun confirmarMfa(codigo: String) = leer(CodigosMfa.serializer(), pedir("/mfa/confirmar", "POST", json(FactorMfa.serializer(), FactorMfa(codigo))))
    suspend fun elevarMfa(factor: FactorMfa) = leer(ElevacionMfa.serializer(), pedir("/mfa/elevar", "POST", json(FactorMfa.serializer(), factor)))
    suspend fun desactivarMfa(codigo: String) { pedir("/mfa/desactivar", "POST", json(FactorMfa.serializer(), FactorMfa(codigo))) }
    suspend fun renovarMfa(codigo: String) = leer(CodigosMfa.serializer(), pedir("/mfa/recuperacion", "POST", json(FactorMfa.serializer(), FactorMfa(codigo))))
    suspend fun equipo() = leer(ListSerializer(MiembroEquipo.serializer()), pedir("/admin/equipo"))
    suspend fun cambiarEquipo(cambio: CambioEquipo, quitar: Boolean) { pedir("/admin/equipo", if (quitar) "DELETE" else "PUT", json(CambioEquipo.serializer(), cambio)) }
    suspend fun registro(actor: String, accion: String, despues: Long): RegistroEquipo = convertirRegistro(pedir("/admin/registro?actor=${cod(actor)}&accion=${cod(accion)}&despues=$despues") ?: "[]")
    suspend fun restaurar(caso: CasoModeracion) { pedir("/admin/restaurar", "POST", json(ContenidoEquipo.serializer(), ContenidoEquipo(caso.que, caso.objeto))) }
    suspend fun retirarPublicacion(objeto: String, motivo: String = "") { pedir("/admin/publicaciones/retirar", "POST", json(ContenidoEquipo.serializer(), ContenidoEquipo("publicacion", objeto, motivo))) }
    suspend fun retirarComentario(objeto: String, motivo: String = "") { pedir("/admin/comentarios/retirar", "POST", json(ContenidoEquipo.serializer(), ContenidoEquipo("comentario", objeto, motivo))) }
    suspend fun levantarSanciones(usuario: String, motivo: String) { pedir("/admin/sanciones/levantar", "POST", json(SancionEquipo.serializer(), SancionEquipo(usuario, motivo))) }
    suspend fun casos() = leer(ListSerializer(CasoModeracion.serializer()), pedir("/admin/casos"))
    suspend fun decidir(que: String, objeto: String, decision: String, regla: String) { pedir("/admin/decidir", "POST", json(Decision.serializer(), Decision(que, objeto, decision, regla))) }
    suspend fun entrarConClave(correo: String, clave: String): Acceso = leer(Acceso.serializer(), pedir("/acceso/clave", "POST", json(EntradaClave.serializer(), EntradaClave(correo, clave))))
    suspend fun ponerClave(nueva: String, actual: String = "") { pedir("/cuenta/clave", "PUT", json(CambioClave.serializer(), CambioClave(nueva, actual))) }
    suspend fun cambiarUsuario(u: String): String =
        JsonCajon.parseToJsonElement(pedir("/cuenta/usuario", "PUT", json(CambioUsuario.serializer(), CambioUsuario(u)))!!).jsonObject["usuario"]!!.jsonPrimitive.content
    suspend fun cambiarApodo(nombre: String) { pedir("/cuenta/apodo", "PUT", json(CambioApodo.serializer(), CambioApodo(nombre))) }
    suspend fun sesiones() = leer(ListSerializer(SesionSocial.serializer()), pedir("/sesiones"))
    suspend fun cerrarSesion(id: String) { pedir("/sesiones/${cod(id)}", "DELETE") }
    suspend fun cerrarOtrasSesiones() { pedir("/sesiones/otras", "DELETE") }
    suspend fun miCuenta(): MiCuenta = leer(MiCuenta.serializer(), pedir("/cuenta"))
    suspend fun borrarCuenta(clave: String, codigo: String, motivo: String, texto: String) {
        pedir("/cuenta/borrar", "POST", json(Borrado.serializer(), Borrado(clave, codigo, motivo, texto)))
    }
    suspend fun conservarCuenta() { pedir("/cuenta/conservar", "POST") }
    suspend fun cambiarCorreo(nuevo: String, clave: String, codigo: String) {
        pedir("/cuenta/correo/cambiar", "POST", json(CambioCorreo.serializer(), CambioCorreo(nuevo, clave, codigo)))
    }
    suspend fun confirmarCorreo(nuevo: String, codigo: String) {
        pedir("/cuenta/correo/confirmar", "POST", json(CambioCorreo.serializer(), CambioCorreo(nuevo, codigo = codigo)))
    }
    suspend fun privada(si: Boolean) { pedir("/cuenta", "PUT", json(Privada.serializer(), Privada(si))) }

    /* Perfil */
    suspend fun subirPerfil(p: PerfilSubido) { pedir("/perfil", "PUT", json(PerfilSubido.serializer(), p)) }
    suspend fun subirFoto(jpeg: ByteArray) { pedir("/foto", "PUT", jpeg.toRequestBody(JPEG)) }
    suspend fun descargarFoto(usuario: String): ByteArray = withContext(Dispatchers.IO) {
        val solicitud = Request.Builder().url(foto(usuario))
            .header("X-Fogon-Dispositivo", nombreDispositivo(android.os.Build.MANUFACTURER, android.os.Build.MODEL))
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .build()
        http.newCall(solicitud).execute().use { r ->
            if (r.code == 401) throw ErrorSocial(r.code, "")
            if (!r.isSuccessful) throw java.io.IOException("foto: ${r.code}")
            r.body.bytes()
        }
    }
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
    /** Sube publicaciones y quita solo las de [quitar] (lo que falta no se borra: un teléfono vacío no se lleva nada). */
    suspend fun subirCambios(lista: List<PublicacionSubida>, quitar: List<String>) {
        pedir("/publicaciones/cambios", "PUT", json(CambiosPublicaciones.serializer(), CambiosPublicaciones(lista, quitar)))
    }
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
    suspend fun agregarFrase(texto: String, idioma: String, franja: String) = leer(FraseComunidad.serializer(), pedir("/admin/frases", "PUT", json(FraseNueva.serializer(), FraseNueva(texto, idioma, franja))))
    suspend fun quitarFrase(clave: String) { pedir("/admin/frases/${cod(clave)}", "DELETE") }
    suspend fun frasesPendientes() = leer(ListSerializer(FraseComunidad.serializer()), pedir("/admin/frases"))
    suspend fun decidirFrase(clave: String, aprobar: Boolean) { pedir("/admin/frases", "POST", json(DecisionFrase.serializer(), DecisionFrase(clave, aprobar))) }
    suspend fun votarFrase(clave: String, valor: Int) { pedir("/frases/${cod(clave)}", "PUT", json(VotoFrase.serializer(), VotoFrase(valor))) }

    suspend fun mandar(usuario: String, sobre: String, firma: String) { pedir("/mensajes/${cod(usuario)}", "POST", json(SobreNuevo.serializer(), SobreNuevo(sobre, firma))) }
    suspend fun mensajesSinLeer(): Int = JsonCajon.parseToJsonElement(pedir("/mensajes/sin-leer")!!).jsonObject["n"]!!.jsonPrimitive.int
    suspend fun recibir(desde: Long) = leer(ListSerializer(Sobre.serializer()), pedir("/mensajes?desde=$desde"))
    /** Como [recibir], pero el servidor espera hasta 25 s a que llegue algo: con la app abierta, el mensaje llega al instante. */
    suspend fun esperarMensajes(desde: Long) = leer(ListSerializer(Sobre.serializer()), pedir("/mensajes/esperar?desde=$desde", cliente = httpLargo))

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
        val cuentasPorBorrar = kotlinx.coroutines.flow.MutableSharedFlow<Pair<String, Long>>(extraBufferCapacity = 16)
        /** El servidor de Fogón (uno solo para todas las apps). */
        const val SERVIDOR = "https://fogonapps.duckdns.org"
        fun enlacePerfil(usuario: String) = "$SERVIDOR/@$usuario"
        fun enlacePublicacion(id: Long) = "$SERVIDOR/p/$id"
        fun foto(usuario: String) = "$SERVIDOR/v1/fotos/$usuario"
        fun imagen(nombre: String) = "$SERVIDOR/v1/imagenes/$nombre"
        private val JSON = "application/json".toMediaType()
        private val JPEG = "image/jpeg".toMediaType()
        private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
        /** Para esperar mensajes (el servidor tiene la pregunta abierta hasta 25 s). */
        private val httpLargo = http.newBuilder().readTimeout(40, TimeUnit.SECONDS).build()
    }
}

/** Campos legibles del contenido reportado, sin acciones ni efectos de la publicación. */
data class ContenidoReporte(
    val titulo: String = "", val portada: String = "", val tipoObra: String = "",
    val anio: String = "", val creador: String = "", val texto: String = "",
)

fun leerContenidoReporte(contenido: String): ContenidoReporte {
    val elemento = try {
        kotlinx.serialization.json.Json.parseToJsonElement(contenido)
    } catch (_: IllegalArgumentException) {
        return ContenidoReporte(texto = contenido.take(600))
    }
    fun campo(nombre: String): String = ((elemento as? JsonObject)?.get(nombre) as? kotlinx.serialization.json.JsonPrimitive)
        ?.takeUnless { it is kotlinx.serialization.json.JsonNull }?.content.orEmpty()
    return ContenidoReporte(
        titulo = campo("titulo"), portada = campo("portada"), tipoObra = campo("tipoObra"),
        anio = campo("anio"), creador = campo("creador"),
        texto = campo("texto").ifBlank {
            (elemento as? kotlinx.serialization.json.JsonPrimitive)
                ?.takeUnless { it is kotlinx.serialization.json.JsonNull }?.content.orEmpty()
        }.take(600),
    )
}

val MOTIVOS_REPORTE = listOf(
    "menor_en_riesgo" to R.string.rep_menor, "desnudos" to R.string.rep_desnudos, "acoso" to R.string.rep_acoso, "odio" to R.string.rep_odio,
    "autolesion" to R.string.rep_autolesion, "spam" to R.string.rep_spam, "menor_de_edad" to R.string.rep_menor_de_edad,
    "derechos" to R.string.rep_autor, "falso" to R.string.rep_falso, "otro" to R.string.rep_otro,
)

/** Presentación traducida de una falta y los puntos que suma. */
fun textoFalta(falta: FaltaSocial, traducir: (Int) -> String): String {
    val regla = MOTIVOS_REPORTE.firstOrNull { it.first == falta.regla }?.second ?: when (falta.regla) {
        "evadir_sancion" -> R.string.mod_evadir_sancion
        "amenaza_real" -> R.string.rep_violencia
        else -> R.string.rep_otro
    }
    val puntos = if (falta.puntos == 2) traducir(R.string.mod_grave_puntos)
        else if (falta.puntos == 1) traducir(R.string.mod_un_punto)
        else if (falta.puntos >= 4) traducir(R.string.mod_cierre)
        else String.format(traducir(R.string.mod_puntos), falta.puntos)
    return "${traducir(regla)} · $puntos"
}

/** El servidor une los reportes de mensajes con un separador; cada uno lleva su motivo. */
fun limpiarReporteMensaje(contenido: String): String = contenido.split("\n---\n").joinToString("\n---\n") { reporte ->
    val cabecera = reporte.substringBefore('\n')
    if ('\n' in reporte && MOTIVOS_REPORTE.any { cabecera.startsWith("${it.first}:") })
        reporte.substringAfter('\n')
    else reporte
}

/** Retira la cuenta de las listas de seguidores y seguidos que estén cargadas. */
fun quitarCuentaBloqueada(gente: Map<String, List<PersonaSocial>>, usuario: String): Map<String, List<PersonaSocial>> =
    gente.mapValues { (clave, personas) ->
        if (clave.startsWith("seguidores:") || clave.startsWith("siguiendo:")) personas.filterNot { it.usuario == usuario }
        else personas
    }

fun quitarPublicacionesBloqueadas(feed: List<PublicacionSocial>?, usuario: String): List<PublicacionSocial>? =
    feed?.filterNot { it.autor.usuario == usuario }

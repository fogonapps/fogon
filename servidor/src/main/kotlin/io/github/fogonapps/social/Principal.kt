package io.github.fogonapps.social

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.response.header
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Fogón Social. Escucha solo en 127.0.0.1 (delante va Caddy, que pone el HTTPS). Se configura con variables de
 * entorno: `PUERTO` (8080), `DATOS` (la carpeta de la base y las fotos, ./datos).
 */
fun main(args: Array<String>) {
    val datos = File(System.getenv("DATOS") ?: "datos").apply { mkdirs() }
    val usuario = System.getenv("SMTP_USUARIO").orEmpty()
    val clave = System.getenv("SMTP_CLAVE").orEmpty()
    val cartero = if (usuario.isNotBlank() && clave.isNotBlank()) CarteroSmtp(usuario, clave,
        servidor = System.getenv("SMTP_SERVIDOR")?.takeIf { it.isNotBlank() } ?: "smtp.gmail.com",
        puerto = System.getenv("SMTP_PUERTO")?.toIntOrNull() ?: 587,
        remitente = System.getenv("SMTP_REMITENTE")?.takeIf { it.isNotBlank() } ?: usuario) else null
    val base = Base(File(datos, "social.db").path)
    val avisos = cartero ?: Cartero { _, _, _ -> throw Problema(503, "sin_correo") }
    if (args.isNotEmpty()) {
        try { Equipo(base, datos, System::currentTimeMillis, avisos, null, configurarPrincipal = false).ordenLocal(args) }
        finally { base.cerrar() }
        return
    }
    val social = Social(base, File(datos, "fotos"), cartero = avisos, principal = System.getenv("PRINCIPAL"))
    correoListo = cartero != null
    Momento.empezar()
    embeddedServer(CIO, port = System.getenv("PUERTO")?.toIntOrNull() ?: 8080, host = "127.0.0.1") { fogonSocial(social) }.start(wait = true)
}

/** Cuántas peticiones por minuto deja hacer a cada quien (por token o, sin él, por dirección). */
private class Limite(private val porMinuto: Int) {
    private val cuentas = ConcurrentHashMap<String, Pair<Long, Int>>()
    fun pasa(clave: String): Boolean {
        val minuto = System.currentTimeMillis() / 60_000
        val (m, n) = cuentas.compute(clave) { _, v -> if (v == null || v.first != minuto) minuto to 1 else v.first to v.second + 1 }!!
        if (cuentas.size > 50_000) cuentas.entries.removeIf { it.value.first != minuto }
        return m == minuto && n <= porMinuto
    }
}

const val VERSION = "0.33"

/** Si el servidor puede mandar los códigos de acceso por correo. */
var correoListo = false

@kotlinx.serialization.Serializable
data class ErrorConFecha(val error: String, val desde: Long)

fun Application.fogonSocial(social: Social, cine: Cine = Cine(System.getenv("TMDB_API_KEY")), lastfm: Lastfm = Lastfm(System.getenv("LASTFM_API_KEY"))) {
    launch {
        while (isActive) {
            try { social.limpiarSeguridad() } catch (_: Exception) {
                environment.log.error("No se pudo completar la limpieza de seguridad")
            }
            kotlinx.coroutines.delay(24 * 3600_000L)
        }
    }
    install(ContentNegotiation) { json(JsonSocial) }
    install(StatusPages) {
        exception<Problema> { call, p -> call.respond(HttpStatusCode.fromValue(p.codigo), if (p.desde > 0) ErrorConFecha(p.mensaje, p.desde) else Error(p.mensaje)) }
        exception<kotlinx.serialization.SerializationException> { call, _ -> call.respond(HttpStatusCode.BadRequest, Error("json")) }
        exception<io.ktor.server.plugins.BadRequestException> { call, _ -> call.respond(HttpStatusCode.BadRequest, Error("json")) }
    }
    val general = Limite(120)
    val registros = Limite(5)
    val pedidosCine = Limite(240)
    val pedidosLastfm = Limite(60)
    install(createApplicationPlugin("Limite") {
        onCall { call ->
            val token = call.request.header("Authorization")?.trim()?.removePrefix("Bearer ")?.trim()
            val ruta = call.request.local.uri.substringBefore('?')
            if (ruta.startsWith("/v1/admin/") || ruta.startsWith("/v1/mfa/")) call.response.header("Cache-Control", "no-store")
            if ((ruta.startsWith("/v1/") || ruta.startsWith("/v2/")) && ruta !in setOf("/v1/cuenta", "/v1/cuenta/conservar", "/v1/cuenta/borrar", "/v1/salir") && !ruta.startsWith("/v1/acceso"))
                social.cuentaDe(token)?.let { social.revisarBorrado(it) }
            val appDeclarada = call.request.header("X-Fogon-App")?.let(AppsSocial::validar)
            val esChat = ruta == "/v1/llaves" || ruta.startsWith("/v1/llaves/") || ruta == "/v1/mensajes" || ruta.startsWith("/v1/mensajes/") || ruta == "/v1/cajon" || ruta.startsWith("/v1/cajon/")
            if (esChat && (social.appSesion(token) != "cajon" || (appDeclarada != null && appDeclarada != "cajon"))) throw Problema(403, "solo_cajon")
            val quien = token?.takeIf { it.isNotEmpty() } ?: call.request.origin.remoteAddress
            if (!general.pasa(quien)) call.respond(HttpStatusCode.TooManyRequests, Error("despacio"))
        }
    })

    fun ApplicationCall.token() = request.header("Authorization")?.trim()?.removePrefix("Bearer ")?.trim()
    // Toda escritura pasa por aquí: una cuenta suspendida o cerrada no puede usar la red, y una restringida no puede
    // publicar, comentar ni mandar mensajes (sí leer, ver su estado y borrar su cuenta).
    fun ApplicationCall.yo(): Long {
        val id = social.cuentaDe(token()) ?: throw Problema(401, "sin_cuenta")
        val soloMirar = request.local.method == io.ktor.http.HttpMethod.Get || request.local.uri.startsWith("/v1/cuenta") || request.local.uri.startsWith("/v1/salir")
        if (!request.local.uri.startsWith("/v1/cuenta") && !request.local.uri.startsWith("/v1/sesiones")) social.revisarSancion(id, escribir = !soloMirar)
        return id
    }
    fun ApplicationCall.requiere(permiso: String): Long {
        val yo = yo()
        social.equipo.requiere(yo, permiso, token())
        return yo
    }
    fun ApplicationCall.lector(): Long? = social.cuentaDe(token())
    fun ApplicationCall.id(): Long = parameters["id"]?.toLongOrNull() ?: throw Problema(400, "id")
    fun ApplicationCall.usuario(): String = parameters["usuario"] ?: throw Problema(400, "usuario")

    routing {
        route("/v1") {
            get("/estado") { call.respond(mapOf("fogon" to "social", "version" to VERSION, "correo" to correoListo.toString(), "politica" to Social.POLITICA)) }

            // La dirección real llega de Caddy en X-Forwarded-For; sin él, la de la conexión.
            fun ApplicationCall.ip() = request.header("X-Forwarded-For")?.substringBefore(',')?.trim() ?: request.origin.remoteAddress
            post("/cuentas") {
                throw Problema(503, "sin_correo")
            }
            post("/acceso/codigo") {
                if (!registros.pasa("codigo:${call.ip()}")) throw Problema(429, "despacio")
                val pedido = call.receive<PedirCodigo>()
                social.pedirCodigo(pedido.correo, pedido.app); call.respond(HttpStatusCode.NoContent)
            }
            post("/acceso") {
                if (!registros.pasa("acceso:${call.ip()}")) throw Problema(429, "despacio")
                call.respond(social.entrar(call.receive(), call.request.header("X-Fogon-Dispositivo").orEmpty()))
            }
            get("/lastfm") {
                if (!pedidosLastfm.pasa("lastfm:${call.ip()}")) throw Problema(429, "despacio")
                val params = call.request.queryParameters.entries().associate { it.key to it.value.firstOrNull().orEmpty() }
                val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { lastfm.pedir(params) }
                call.respondText(r.cuerpo, ContentType.Application.Json, HttpStatusCode.fromValue(r.codigo))
            }
            // TMDB por el servidor (la clave solo vive aquí), ver Cine.
            get("/tmdb/{ruta...}") {
                if (!pedidosCine.pasa("cine:${call.ip()}")) throw Problema(429, "despacio")
                val ruta = call.parameters.getAll("ruta").orEmpty().joinToString("/")
                val params = call.request.queryParameters.entries().associate { it.key to it.value.firstOrNull().orEmpty() }
                val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { cine.pedir(ruta, params) }
                call.respondText(r.cuerpo, ContentType.Application.Json, HttpStatusCode.fromValue(r.codigo))
            }
            // Obsoleta: se conserva sin cambios para El Cajón 1.47; se retira con C-08b.
            post("/acceso/correo") {
                if (!registros.pasa("correo:${call.ip()}")) throw Problema(429, "despacio")
                call.respond(social.estadoCorreo(call.receive<PedirCodigo>().correo))
            }
            post("/acceso/clave") {
                if (!registros.pasa("clave:${call.ip()}")) throw Problema(429, "despacio")
                call.respond(social.entrarConClave(call.receive(), call.request.header("X-Fogon-Dispositivo").orEmpty()))
            }
            put("/cuenta/clave") { social.ponerClave(call.yo(), call.receive(), token = call.token()); call.respond(HttpStatusCode.NoContent) }
            get("/sesiones") { call.respond(social.sesiones(call.yo(), call.token())) }
            delete("/sesiones/otras") { social.cerrarOtras(call.yo(), call.token()); call.respond(HttpStatusCode.NoContent) }
            delete("/sesiones/{id}") {
                social.cerrarSesion(call.yo(), call.parameters["id"].orEmpty())
                call.respond(HttpStatusCode.NoContent)
            }
            put("/cuenta/apodo") { social.cambiarApodo(call.yo(), call.receive()); call.respond(HttpStatusCode.NoContent) }
            put("/cuenta/usuario") { call.respond(mapOf("usuario" to social.cambiarUsuario(call.yo(), call.receive()))) }
            get("/mensajes/sin-leer") { call.respond(mapOf("n" to social.mensajesSinLeer(call.yo()))) }
            post("/cuenta/correo") { social.vincularCorreo(call.yo(), call.receive()); call.respond(HttpStatusCode.NoContent) }
            post("/salir") { social.salir(call.token()); call.respond(HttpStatusCode.NoContent) }
            get("/cuenta") { call.respond(social.miCuenta(call.yo(), call.token())) }
            post("/cuenta/politica") { social.aceptarPolitica(call.yo(), call.receive<Aceptacion>().politica); call.respond(HttpStatusCode.NoContent) }
            delete("/cuenta") { call.yo(); throw Problema(403, "actualiza_app") }
            post("/cuenta/borrar") { social.programarBorrado(call.yo(), call.token(), call.receive<Borrado>()); call.respond(HttpStatusCode.NoContent) }
            post("/cuenta/conservar") { social.conservarCuenta(call.yo()); call.respond(HttpStatusCode.NoContent) }
            post("/cuenta/correo/cambiar") {
                if (!registros.pasa("codigo:${call.ip()}")) throw Problema(429, "despacio")
                social.cambiarCorreo(call.yo(), call.receive(), social.appSesion(call.token())); call.respond(HttpStatusCode.NoContent)
            }
            post("/cuenta/correo/confirmar") { social.confirmarCorreo(call.yo(), call.receive()); call.respond(HttpStatusCode.NoContent) }
            put("/cuenta") { social.ajustar(call.yo(), call.receive()); call.respond(HttpStatusCode.NoContent) }
            get("/usuarios/{usuario}/libre") { call.respond(mapOf("libre" to social.usuarioLibre(call.usuario()))) }

            put("/perfil") { social.subirPerfil(call.yo(), call.receive()); call.respond(HttpStatusCode.NoContent) }
            put("/foto") {
                val yo = call.yo()
                social.subirFoto(yo, call.receiveChannel().readRemaining(400_001).readByteArray())
                call.respond(HttpStatusCode.NoContent)
            }
            delete("/foto") { social.quitarFoto(call.yo()); call.respond(HttpStatusCode.NoContent) }
            get("/fotos/{usuario}") { call.respondBytes(social.foto(call.lector(), call.usuario()), ContentType.Image.JPEG) }
            get("/perfiles/{usuario}") { call.respond(social.verPerfil(call.lector(), call.usuario())) }
            get("/buscar") { call.respond(social.buscar(call.lector(), call.request.queryParameters["q"] ?: "")) }

            put("/publicaciones") { social.subirPublicaciones(call.yo(), call.receive()); call.respond(HttpStatusCode.NoContent) }
            put("/publicaciones/cambios") { social.subirCambios(call.yo(), call.receive()); call.respond(HttpStatusCode.NoContent) }
            get("/actividad") { call.respond(social.actividad(call.yo(), call.request.queryParameters["antes"]?.toLongOrNull())) }
            get("/explorar") { call.respond(social.explorar(call.lector(), call.request.queryParameters["antes"]?.toLongOrNull())) }
            get("/guardados") { call.respond(social.guardados(call.yo())) }
            get("/perfiles/{usuario}/guardadas") { call.respond(social.guardadasDe(call.lector(), call.usuario())) }
            get("/etiquetas/{etiqueta}") { call.respond(social.conEtiqueta(call.lector(), call.parameters["etiqueta"] ?: "", call.request.queryParameters["antes"]?.toLongOrNull())) }
            put("/imagenes") {
                val yo = call.yo()
                call.respond(HttpStatusCode.Created, social.subirImagen(yo, call.receiveChannel().readRemaining(900_001).readByteArray()))
            }
            put("/videos") {
                val yo = call.yo()
                call.respond(HttpStatusCode.Created, social.subirVideo(yo, call.receiveChannel().readRemaining((MAX_VIDEO + 1).toLong()).readByteArray()))
            }
            get("/videos/{nombre}") {
                val archivo = social.video(call.parameters["nombre"].orEmpty(), call.lector(), call.token())
                val largo = archivo.length()
                call.response.header("Cache-Control", "no-store")
                call.response.header("Accept-Ranges", "bytes")
                val cabecera = call.request.header("Range")
                val rango = cabecera?.let { rangoVideo(it, largo) }
                if (cabecera != null && rango == null) {
                    call.response.header("Content-Range", "bytes */$largo")
                    call.respond(HttpStatusCode.RequestedRangeNotSatisfiable)
                } else {
                    val inicio = rango?.first ?: 0L
                    val cantidad = rango?.let { it.last - it.first + 1 } ?: largo
                    if (rango != null) call.response.header("Content-Range", "bytes ${rango.first}-${rango.last}/$largo")
                    call.respondOutputStream(ContentType.Video.MP4, if (rango == null) HttpStatusCode.OK else HttpStatusCode.PartialContent, cantidad) {
                        java.io.RandomAccessFile(archivo, "r").use { entrada ->
                            entrada.seek(inicio)
                            val bloque = ByteArray(64 * 1024)
                            var quedan = cantidad
                            while (quedan > 0) {
                                val n = entrada.read(bloque, 0, minOf(bloque.size.toLong(), quedan).toInt())
                                if (n < 0) break
                                write(bloque, 0, n); quedan -= n
                            }
                        }
                    }
                }
            }
            get("/imagenes/{nombre}") {
                call.response.header("Cache-Control", "no-store")
                call.respondBytes(social.imagen(call.parameters["nombre"] ?: "", call.lector(), call.token()), ContentType.Image.JPEG)
            }
            get("/publicaciones/{id}") { call.respond(social.publicacion(call.lector(), call.id()) ?: throw Problema(404, "no_existe")) }
            delete("/publicaciones/{id}") { social.borrarPublicacion(call.yo(), call.id()); call.respond(HttpStatusCode.NoContent) }
            post("/publicaciones/{id}/util") { social.util(call.yo(), call.id(), true); call.respond(HttpStatusCode.NoContent) }
            delete("/publicaciones/{id}/util") { social.util(call.yo(), call.id(), false); call.respond(HttpStatusCode.NoContent) }
            post("/publicaciones/{id}/megusta") { social.meGusta(call.yo(), call.id(), true); call.respond(HttpStatusCode.NoContent) }
            delete("/publicaciones/{id}/megusta") { social.meGusta(call.yo(), call.id(), false); call.respond(HttpStatusCode.NoContent) }
            post("/publicaciones/{id}/guardar") { social.guardar(call.yo(), call.id(), true); call.respond(HttpStatusCode.NoContent) }
            delete("/publicaciones/{id}/guardar") { social.guardar(call.yo(), call.id(), false); call.respond(HttpStatusCode.NoContent) }
            get("/publicaciones/{id}/comentarios") { call.respond(social.comentarios(call.lector(), call.id())) }
            post("/publicaciones/{id}/comentarios") {
                val c = call.receive<NuevoComentario>()
                call.respond(HttpStatusCode.Created, mapOf("id" to social.comentar(call.yo(), call.id(), c.texto, c.padre)))
            }
            post("/comentarios/{id}/megusta") { social.meGustaComentario(call.yo(), call.id(), true); call.respond(HttpStatusCode.NoContent) }
            delete("/comentarios/{id}/megusta") { social.meGustaComentario(call.yo(), call.id(), false); call.respond(HttpStatusCode.NoContent) }
            delete("/comentarios/{id}") { social.borrarComentario(call.yo(), call.id()); call.respond(HttpStatusCode.NoContent) }

            post("/seguir/{usuario}") { call.respond(mapOf("relacion" to social.seguir(call.yo(), call.usuario()))) }
            delete("/seguir/{usuario}") { social.dejarDeSeguir(call.yo(), call.usuario()); call.respond(HttpStatusCode.NoContent) }
            delete("/seguidores/{usuario}") { social.quitarSeguidor(call.yo(), call.usuario()); call.respond(HttpStatusCode.NoContent) }
            get("/seguidores/{usuario}") { call.respond(social.lista(call.lector(), call.usuario(), seguidores = true)) }
            get("/siguiendo/{usuario}") { call.respond(social.lista(call.lector(), call.usuario(), seguidores = false)) }
            get("/solicitudes") { call.respond(social.solicitudes(call.yo())) }
            post("/solicitudes/{usuario}") { social.aceptar(call.yo(), call.usuario()); call.respond(HttpStatusCode.NoContent) }
            delete("/solicitudes/{usuario}") { social.quitarSeguidor(call.yo(), call.usuario()); call.respond(HttpStatusCode.NoContent) }
            get("/avisos") { call.respond(social.avisos(call.yo(), call.request.queryParameters["antes"]?.toLongOrNull())) }
            get("/avisos/sin-leer") { call.respond(mapOf("n" to social.sinLeer(call.yo()))) }
            post("/avisos/leidos") { social.leidos(call.yo()); call.respond(HttpStatusCode.NoContent) }
            put("/llaves") { social.ponerLlaves(call.yo(), call.receive()); call.respond(HttpStatusCode.NoContent) }
            get("/llaves/{usuario}") { call.respond(social.llaves(call.lector(), call.usuario())) }
            post("/mensajes/{usuario}") { call.respond(HttpStatusCode.Created, mapOf("id" to social.mandar(call.yo(), call.usuario(), call.receive()))) }
            get("/mensajes") { call.respond(social.recibir(call.yo(), call.request.queryParameters["desde"]?.toLongOrNull() ?: 0)) }
            get("/mensajes/esperar") { call.respond(social.esperarMensajes(call.yo(), call.request.queryParameters["desde"]?.toLongOrNull() ?: 0)) }
            post("/vistas") { social.vio(call.yo(), call.receive()); call.respond(HttpStatusCode.NoContent) }
            put("/nota") { social.ponerNota(call.yo(), call.receive()); call.respond(HttpStatusCode.NoContent) }
            get("/notas") { call.respond(social.notas(call.yo())) }
            get("/momento") { call.respond(Momento.de(call.request.queryParameters["idioma"].orEmpty())) }
            get("/obras/imagen") { call.respond(social.imagenObra(call.lector(), call.request.queryParameters["fuente"].orEmpty())) }
            put("/obras/imagen") { social.votarImagen(call.yo(), call.receive()); call.respond(HttpStatusCode.NoContent) }
            delete("/obras/imagen") { social.quitarVotoImagen(call.yo(), call.request.queryParameters["fuente"].orEmpty()); call.respond(HttpStatusCode.NoContent) }
            post("/admin/imagenes/bloquear") { social.bloquearImagen(call.requiere("gestionar_publicaciones"), call.receive(), true, call.token()); call.respond(HttpStatusCode.NoContent) }
            post("/admin/imagenes/permitir") { social.bloquearImagen(call.requiere("gestionar_publicaciones"), call.receive(), false, call.token()); call.respond(HttpStatusCode.NoContent) }
            get("/resenas") {
                val q = call.request.queryParameters
                call.respond(social.resenas(call.lector(), q["fuente"].orEmpty(), q["parte"].orEmpty(), q["orden"].orEmpty(), q["antes"]?.toLongOrNull()))
            }
            get("/frases/comunidad") { call.respond(social.frasesComunidad(call.request.queryParameters["idioma"] ?: "es")) }
            post("/frases/propuestas") { social.proponerFrase(call.yo(), call.receive()); call.respond(HttpStatusCode.Created) }
            get("/frases/ocultas") { call.respond(social.frasesOcultas()) }
            put("/admin/frases") { call.respond(HttpStatusCode.Created, social.agregarFrase(call.requiere("gestionar_frases"), call.receive(), call.token())) }
            delete("/admin/frases/{clave}") { social.quitarFrase(call.requiere("gestionar_frases"), call.parameters["clave"].orEmpty(), call.token()); call.respond(HttpStatusCode.NoContent) }
            get("/admin/frases") { call.respond(social.frasesPendientes(call.requiere("gestionar_frases"), call.token())) }
            post("/admin/frases") { social.decidirFrase(call.requiere("gestionar_frases"), call.receive(), call.token()); call.respond(HttpStatusCode.NoContent) }
            get("/frases") { call.respond(social.frases()) }
            put("/frases/{clave}") { social.votarFrase(call.yo(), call.parameters["clave"].orEmpty(), call.receive()); call.respond(HttpStatusCode.NoContent) }
            delete("/avisos") { social.borrarAvisos(call.yo(), null); call.respond(HttpStatusCode.NoContent) }
            delete("/avisos/{id}") { social.borrarAvisos(call.yo(), call.id()); call.respond(HttpStatusCode.NoContent) }
            get("/cajon") { call.respond(social.cajon(call.yo())) }
            put("/cajon") {
                val antes = call.request.queryParameters["antes"]?.toLongOrNull() ?: 0
                call.respond(mapOf("cambio" to social.guardarCajon(call.yo(), call.receive(), antes)))
            }
            put("/cajon/llave") { social.guardarLlave(call.yo(), call.receive<CajonCifrado>().llave); call.respond(HttpStatusCode.NoContent) }
            get("/cuenta/estado") { call.respond(social.estadoCuenta(call.yo())) }
            post("/mfa/activar") { call.respond(social.equipo.activar(call.yo(), call.receiveText().let { if (it.isBlank()) Factor() else JsonSocial.decodeFromString<Factor>(it) }, call.token())) }
            post("/mfa/confirmar") { call.respond(social.equipo.confirmar(call.yo(), call.receive(), call.token())) }
            post("/mfa/elevar") { call.respond(social.equipo.elevar(call.yo(), call.receive(), call.token())) }
            post("/mfa/desactivar") { social.equipo.desactivar(call.yo(), call.receive(), call.token()); call.respond(HttpStatusCode.NoContent) }
            post("/mfa/recuperacion") { call.respond(social.equipo.regenerar(call.yo(), call.receive(), call.token())) }
            get("/admin/equipo") { call.respond(social.equipo.listar(call.requiere("asignar_equipo"), call.token())) }
            put("/admin/equipo") { social.equipo.cambiarEquipo(call.requiere("asignar_equipo"), call.receive(), false, call.token()); call.respond(HttpStatusCode.NoContent) }
            delete("/admin/equipo") { social.equipo.cambiarEquipo(call.requiere("asignar_equipo"), call.receive(), true, call.token()); call.respond(HttpStatusCode.NoContent) }
            get("/admin/registro") { call.respond(social.equipo.registroPaginado(call.requiere("ver_registro"), call.token(), call.request.queryParameters["actor"].orEmpty(), call.request.queryParameters["accion"].orEmpty(), call.request.queryParameters["despues"]?.toLongOrNull() ?: 0)) }
            post("/admin/publicaciones/retirar") { social.retirar(call.requiere("gestionar_publicaciones"), call.receive<ContenidoEquipo>().copy(que = "publicacion"), call.token()); call.respond(HttpStatusCode.NoContent) }
            post("/admin/comentarios/retirar") { social.retirar(call.requiere("moderar_comentarios"), call.receive<ContenidoEquipo>().copy(que = "comentario"), call.token()); call.respond(HttpStatusCode.NoContent) }
            post("/admin/restaurar") { social.restaurar(call.requiere("gestionar_publicaciones"), call.receive(), call.token()); call.respond(HttpStatusCode.NoContent) }
            post("/admin/sanciones/levantar") { social.levantarSancion(call.requiere("gestionar_usuarios"), call.receive(), call.token()); call.respond(HttpStatusCode.NoContent) }
            get("/admin/casos") { call.respond(social.casos(call.requiere("ver_reportes"), call.token())) }
            post("/admin/decidir") { social.decidir(call.requiere("sancionar"), call.receive(), call.token()); call.respond(HttpStatusCode.NoContent) }
            get("/bloqueos") { call.respond(social.bloqueados(call.yo())) }
            post("/bloqueos/{usuario}") { social.bloquear(call.yo(), call.usuario(), true); call.respond(HttpStatusCode.NoContent) }
            delete("/bloqueos/{usuario}") { social.bloquear(call.yo(), call.usuario(), false); call.respond(HttpStatusCode.NoContent) }

            post("/reportes") { social.reportar(call.lector(), call.receive()); call.respond(HttpStatusCode.Created) }
        }
        route("/v2/apps/{app}") {
            fun ApplicationCall.app() = AppsSocial.validar(parameters["app"].orEmpty())
            fun ApplicationCall.cucha() { if (app() != "cucha") throw Problema(400, "tipo_app") }
            post("/lugares") { call.cucha(); call.respond(HttpStatusCode.Created, social.crearLugar(call.yo(), call.receive())) }
            get("/lugares") { call.cucha(); call.respond(social.buscarLugares(call.request.queryParameters["nombre"].orEmpty(), call.request.queryParameters["ciudad"].orEmpty())) }
            patch("/lugares/{id}") { call.cucha(); call.respond(social.corregirLugar(call.yo(), call.id(), call.receive(), call.token())) }
            post("/publicaciones/{id}/util") { call.cucha(); social.util(call.yo(), call.id(), true); call.respond(HttpStatusCode.NoContent) }
            delete("/publicaciones/{id}/util") { call.cucha(); social.util(call.yo(), call.id(), false); call.respond(HttpStatusCode.NoContent) }
            put("/publicaciones/cambios") { social.subirCambios(call.yo(), call.receive(), call.app(), estricto = true); call.respond(HttpStatusCode.NoContent) }
            put("/perfil") { social.subirPerfilApp(call.yo(), call.app(), call.receive()); call.respond(mapOf("revision" to social.revisionPerfil(call.yo()))) }
            get("/perfil/revision") { call.app(); call.respond(mapOf("revision" to social.revisionPerfil(call.yo()))) }
            // Las frases del saludo de cada app: las de la gente aprobadas, proponer una y los votos (las claves propias
            // de cada app llevan su prefijo, así que votos y ocultas siguen siendo las rutas comunes de v1).
            get("/frases/comunidad") { call.respond(social.frasesComunidad(call.request.queryParameters["idioma"] ?: "es", call.app())) }
            post("/frases/propuestas") { social.proponerFrase(call.yo(), call.receive(), call.app()); call.respond(HttpStatusCode.Created) }
            get("/perfiles/{usuario}") { call.respond(social.verPerfil(call.lector(), call.usuario(), call.app())) }
            get("/actividad") { call.respond(social.actividad(call.yo(), call.request.queryParameters["antes"]?.toLongOrNull(), app = call.app())) }
            get("/explorar") { call.respond(social.explorar(call.lector(), call.request.queryParameters["antes"]?.toLongOrNull(), app = call.app())) }
            get("/guardados") { call.respond(social.guardados(call.yo(), call.app())) }
            get("/perfiles/{usuario}/guardadas") { call.respond(social.guardadasDe(call.lector(), call.usuario(), call.app())) }
            get("/etiquetas/{etiqueta}") { call.respond(social.conEtiqueta(call.lector(), call.parameters["etiqueta"].orEmpty(), call.request.queryParameters["antes"]?.toLongOrNull(), call.app())) }
            get("/avisos") { call.respond(social.avisos(call.yo(), call.request.queryParameters["antes"]?.toLongOrNull(), call.app())) }
            get("/avisos/sin-leer") { call.respond(mapOf("n" to social.sinLeer(call.yo(), call.app()))) }
            post("/avisos/leidos") { social.leidos(call.yo(), call.app()); call.respond(HttpStatusCode.NoContent) }
            delete("/avisos") { social.borrarAvisos(call.yo(), null, call.app()); call.respond(HttpStatusCode.NoContent) }
            delete("/avisos/{id}") { social.borrarAvisos(call.yo(), call.id(), call.app()); call.respond(HttpStatusCode.NoContent) }
        }
        paginasWeb(social)
    }
}

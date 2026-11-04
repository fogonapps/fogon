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
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Fogón Social. Escucha solo en 127.0.0.1 (delante va Caddy, que pone el HTTPS). Se configura con variables de
 * entorno: `PUERTO` (8080), `DATOS` (la carpeta de la base y las fotos, ./datos).
 */
fun main() {
    val datos = File(System.getenv("DATOS") ?: "datos").apply { mkdirs() }
    val usuario = System.getenv("SMTP_USUARIO").orEmpty()
    val clave = System.getenv("SMTP_CLAVE").orEmpty()
    val cartero = if (usuario.isNotBlank() && clave.isNotBlank()) CarteroSmtp(usuario, clave) else null
    val social = Social(Base(File(datos, "social.db").path), File(datos, "fotos"), cartero = cartero ?: Cartero { _, _, _ -> throw Problema(503, "sin_correo") })
    correoListo = cartero != null
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

const val VERSION = "0.10"

/** Si el servidor puede mandar los códigos de acceso por correo (si no, la app crea la cuenta sin correo). */
var correoListo = false

fun Application.fogonSocial(social: Social) {
    install(ContentNegotiation) { json(JsonSocial) }
    install(StatusPages) {
        exception<Problema> { call, p -> call.respond(HttpStatusCode.fromValue(p.codigo), Error(p.mensaje)) }
        exception<kotlinx.serialization.SerializationException> { call, _ -> call.respond(HttpStatusCode.BadRequest, Error("json")) }
        exception<io.ktor.server.plugins.BadRequestException> { call, _ -> call.respond(HttpStatusCode.BadRequest, Error("json")) }
    }
    val general = Limite(120)
    val registros = Limite(5)
    install(createApplicationPlugin("Limite") {
        onCall { call ->
            val quien = call.request.header("Authorization") ?: call.request.origin.remoteAddress
            if (!general.pasa(quien)) call.respond(HttpStatusCode.TooManyRequests, Error("despacio"))
        }
    })

    fun ApplicationCall.token() = request.header("Authorization")?.removePrefix("Bearer ")?.trim()
    // Toda escritura pasa por aquí: una cuenta suspendida o cerrada no puede usar la red, y una restringida no puede
    // publicar, comentar ni mandar mensajes (sí leer, ver su estado y borrar su cuenta).
    fun ApplicationCall.yo(): Long {
        val id = social.cuentaDe(token()) ?: throw Problema(401, "sin_cuenta")
        val soloMirar = request.local.method == io.ktor.http.HttpMethod.Get || request.local.uri.startsWith("/v1/cuenta") || request.local.uri.startsWith("/v1/salir")
        if (!request.local.uri.startsWith("/v1/cuenta")) social.revisarSancion(id, escribir = !soloMirar)
        return id
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
                if (!registros.pasa("registro:${call.ip()}")) throw Problema(429, "despacio")
                call.respond(HttpStatusCode.Created, social.crearSinCorreo(call.receive()))
            }
            post("/acceso/codigo") {
                if (!registros.pasa("codigo:${call.ip()}")) throw Problema(429, "despacio")
                social.pedirCodigo(call.receive<PedirCodigo>().correo); call.respond(HttpStatusCode.NoContent)
            }
            post("/acceso") {
                if (!registros.pasa("acceso:${call.ip()}")) throw Problema(429, "despacio")
                call.respond(social.entrar(call.receive()))
            }
            post("/acceso/clave") {
                if (!registros.pasa("clave:${call.ip()}")) throw Problema(429, "despacio")
                call.respond(social.entrarConClave(call.receive()))
            }
            put("/cuenta/clave") { social.ponerClave(call.yo(), call.receive()); call.respond(HttpStatusCode.NoContent) }
            put("/cuenta/usuario") { call.respond(mapOf("usuario" to social.cambiarUsuario(call.yo(), call.receive()))) }
            get("/mensajes/sin-leer") { call.respond(mapOf("n" to social.mensajesSinLeer(call.yo()))) }
            post("/cuenta/correo") { social.vincularCorreo(call.yo(), call.receive()); call.respond(HttpStatusCode.NoContent) }
            post("/salir") { social.salir(call.token()); call.respond(HttpStatusCode.NoContent) }
            get("/cuenta") { call.respond(social.miCuenta(call.yo())) }
            delete("/cuenta") { social.borrarCuenta(call.yo()); call.respond(HttpStatusCode.NoContent) }
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
            get("/actividad") { call.respond(social.actividad(call.yo(), call.request.queryParameters["antes"]?.toLongOrNull())) }
            get("/explorar") { call.respond(social.explorar(call.lector(), call.request.queryParameters["antes"]?.toLongOrNull())) }
            get("/guardados") { call.respond(social.guardados(call.yo())) }
            get("/perfiles/{usuario}/guardadas") { call.respond(social.guardadasDe(call.lector(), call.usuario())) }
            get("/etiquetas/{etiqueta}") { call.respond(social.conEtiqueta(call.lector(), call.parameters["etiqueta"] ?: "", call.request.queryParameters["antes"]?.toLongOrNull())) }
            put("/imagenes") {
                val yo = call.yo()
                call.respond(HttpStatusCode.Created, social.subirImagen(yo, call.receiveChannel().readRemaining(900_001).readByteArray()))
            }
            get("/imagenes/{nombre}") {
                call.response.header("Cache-Control", "public, max-age=31536000, immutable")
                call.respondBytes(social.imagen(call.parameters["nombre"] ?: ""), ContentType.Image.JPEG)
            }
            get("/publicaciones/{id}") { call.respond(social.publicacion(call.lector(), call.id()) ?: throw Problema(404, "no_existe")) }
            post("/publicaciones/{id}/megusta") { social.meGusta(call.yo(), call.id(), true); call.respond(HttpStatusCode.NoContent) }
            delete("/publicaciones/{id}/megusta") { social.meGusta(call.yo(), call.id(), false); call.respond(HttpStatusCode.NoContent) }
            post("/publicaciones/{id}/guardar") { social.guardar(call.yo(), call.id(), true); call.respond(HttpStatusCode.NoContent) }
            delete("/publicaciones/{id}/guardar") { social.guardar(call.yo(), call.id(), false); call.respond(HttpStatusCode.NoContent) }
            get("/publicaciones/{id}/comentarios") { call.respond(social.comentarios(call.lector(), call.id())) }
            post("/publicaciones/{id}/comentarios") {
                val c = call.receive<NuevoComentario>()
                call.respond(HttpStatusCode.Created, mapOf("id" to social.comentar(call.yo(), call.id(), c.texto)))
            }
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
            post("/vistas") { social.vio(call.yo(), call.receive()); call.respond(HttpStatusCode.NoContent) }
            put("/nota") { social.ponerNota(call.yo(), call.receive()); call.respond(HttpStatusCode.NoContent) }
            get("/notas") { call.respond(social.notas(call.yo())) }
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
            get("/admin/casos") { call.respond(social.casos(call.yo())) }
            post("/admin/decidir") { social.decidir(call.yo(), call.receive()); call.respond(HttpStatusCode.NoContent) }
            get("/bloqueos") { call.respond(social.bloqueados(call.yo())) }
            post("/bloqueos/{usuario}") { social.bloquear(call.yo(), call.usuario(), true); call.respond(HttpStatusCode.NoContent) }
            delete("/bloqueos/{usuario}") { social.bloquear(call.yo(), call.usuario(), false); call.respond(HttpStatusCode.NoContent) }

            post("/reportes") { social.reportar(call.lector(), call.receive()); call.respond(HttpStatusCode.Created) }
        }
        paginasWeb(social)
    }
}

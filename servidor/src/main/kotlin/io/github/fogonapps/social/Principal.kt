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
    val social = Social(Base(File(datos, "social.db").path), File(datos, "fotos"))
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
    fun ApplicationCall.yo(): Long = social.cuentaDe(token()) ?: throw Problema(401, "sin_cuenta")
    fun ApplicationCall.lector(): Long? = social.cuentaDe(token())
    fun ApplicationCall.id(): Long = parameters["id"]?.toLongOrNull() ?: throw Problema(400, "id")
    fun ApplicationCall.usuario(): String = parameters["usuario"] ?: throw Problema(400, "usuario")

    routing {
        route("/v1") {
            get("/estado") { call.respond(mapOf("fogon" to "social", "version" to "0.1")) }

            post("/cuentas") {
                // La dirección real llega de Caddy en X-Forwarded-For; sin él, la de la conexión.
                val ip = call.request.header("X-Forwarded-For")?.substringBefore(',')?.trim() ?: call.request.origin.remoteAddress
                if (!registros.pasa("registro:$ip")) throw Problema(429, "despacio")
                call.respond(HttpStatusCode.Created, social.crearCuenta(call.receive()))
            }
            delete("/cuenta") { social.borrarCuenta(call.yo()); call.respond(HttpStatusCode.NoContent) }

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
            get("/guardados") { call.respond(social.guardados(call.yo())) }
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

            get("/amigos") { call.respond(social.amigos(call.yo())) }
            post("/amigos/{usuario}") { social.pedirAmistad(call.yo(), call.usuario()); call.respond(HttpStatusCode.NoContent) }
            delete("/amigos/{usuario}") { social.quitarAmistad(call.yo(), call.usuario()); call.respond(HttpStatusCode.NoContent) }
            post("/bloqueos/{usuario}") { social.bloquear(call.yo(), call.usuario(), true); call.respond(HttpStatusCode.NoContent) }
            delete("/bloqueos/{usuario}") { social.bloquear(call.yo(), call.usuario(), false); call.respond(HttpStatusCode.NoContent) }

            post("/reportes") { social.reportar(call.lector(), call.receive()); call.respond(HttpStatusCode.Created) }
        }
    }
}

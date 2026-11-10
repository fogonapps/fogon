package io.github.fogonapps.social

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Las páginas web: lo que ve quien abre un enlace compartido sin tener El Cajón (con El Cajón instalado, Android abre
 * la app directo gracias a `assetlinks.json`), y la política de privacidad y los términos. Solo muestran lo público.
 */
fun Routing.paginasWeb(social: Social) {
    get("/.well-known/assetlinks.json") {
        call.respondText(ASSETLINKS, ContentType.Application.Json)
    }
    get("/") { call.respondText(pagina("Fogón", "<h1>Fogón</h1><p class=\"suave\">Tuya desde el primer día.</p>$BOTONES"), ContentType.Text.Html) }
    get("/privacidad") { call.respondText(pagina("Privacidad", recurso("privacidad.html")), ContentType.Text.Html) }
    get("/terminos") { call.respondText(pagina("Términos", recurso("terminos.html")), ContentType.Text.Html) }

    get("/{arroba}") {
        val arroba = call.parameters["arroba"] ?: ""
        if (!arroba.startsWith("@")) return@get call.respondText(pagina("No está", "<p>Esta página no existe.</p>"), ContentType.Text.Html, HttpStatusCode.NotFound)
        val p = runCatching { social.verPerfilGlobal(null, arroba.removePrefix("@")) }.getOrNull()
            ?: return@get call.respondText(pagina("No está", "<p>Este perfil no existe.</p>$BOTONES"), ContentType.Text.Html, HttpStatusCode.NotFound)
        val u = p.persona.usuario
        val nombre = p.persona.nombre.ifBlank { u }
        val cuerpo = buildString {
            append("<header class=\"perfil\">")
            if (p.persona.foto) append("<img class=\"avatar\" src=\"/v1/fotos/${e(u)}\" alt=\"\">")
            append("<h1>${e(nombre)}</h1><p class=\"suave\">@${e(u)}</p>")
            if (p.bio.isNotBlank()) append("<p>${e(p.bio)}</p>")
            append("<p class=\"cifras\"><b>${p.cuantas}</b> publicaciones · <b>${p.seguidores}</b> seguidores · <b>${p.siguiendo}</b> siguiendo</p>")
            append("</header>")
            if (p.cerrada) append("<p class=\"suave\">Esta cuenta es privada.</p>")
            p.publicaciones.take(30).forEach { append(tarjeta(it)) }
            append(BOTONES)
        }
        call.respondText(pagina("$nombre (@$u)", cuerpo, descripcion = p.bio.ifBlank { "Mira el perfil de @$u en Fogón Social." }), ContentType.Text.Html)
    }

    get("/p/{id}") {
        val pub = call.parameters["id"]?.toLongOrNull()?.let { social.publicacion(null, it) }
            ?: return@get call.respondText(pagina("No está", "<p>Esta publicación no existe o no es pública.</p>$BOTONES"), ContentType.Text.Html, HttpStatusCode.NotFound)
        val titulo = texto(pub.datos, "titulo").ifBlank { "Una publicación" }
        call.respondText(pagina("$titulo · @${pub.autor.usuario}", tarjeta(pub) + BOTONES, descripcion = texto(pub.datos, "texto").take(160)), ContentType.Text.Html)
    }
}

private fun tarjeta(p: Publicacion): String = buildString {
    val d = p.datos
    append("<article>")
    append("<p class=\"suave\"><a href=\"/@${e(p.autor.usuario)}\">@${e(p.autor.usuario)}</a></p>")
    texto(d, "imagen").takeIf { Regex("[A-Za-z0-9_-]{22}").matches(it) }?.let { append("<img class=\"foto\" src=\"/v1/imagenes/$it\" alt=\"\">") }
    texto(d, "portada").takeIf { it.startsWith("https://") }?.let { append("<img class=\"portada\" src=\"${e(it)}\" alt=\"\">") }
    texto(d, "titulo").takeIf { it.isNotBlank() }?.let { append("<h2>${e(it)}</h2>") }
    (d["estrellas"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it > 0 }?.let { append("<p class=\"estrellas\">${"★".repeat(it.toInt())}${if (it % 1 >= 0.5) "½" else ""}</p>") }
    if (p.app != "cajon") {
        append("<p class=\"suave\">${e(AppsSocial.nombre(p.app))} · ${e(texto(d, "fecha"))}</p>")
        append("<p>Duración: ${e(texto(d, "duracion"))} s</p>")
        (d["ejercicios"] as? kotlinx.serialization.json.JsonArray)?.forEach { elemento ->
            val ejercicio = elemento as? JsonObject ?: return@forEach
            val series = ejercicio["series"] as? kotlinx.serialization.json.JsonArray
            append("<p>${e(texto(ejercicio, "nombre"))}: ${series?.size ?: 0} series</p>")
        }
    }
    val spoiler = (d["spoiler"] as? JsonPrimitive)?.content == "true"
    texto(d, "texto").takeIf { it.isNotBlank() }?.let { append(if (spoiler) "<details><summary>Tiene spoilers</summary><p>${e(it)}</p></details>" else "<p>${e(it)}</p>") }
    append("<p class=\"suave\">♥ ${p.megusta} · ${p.comentarios} comentarios</p>")
    append("</article>")
}

private fun texto(d: JsonObject, clave: String) = (d[clave] as? JsonPrimitive)?.content ?: ""

private fun recurso(nombre: String): String =
    object {}.javaClass.getResource("/web/$nombre")?.readText() ?: "<p>Pronto.</p>"

/** Escapa el texto para HTML. */
private fun e(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

private const val DESCARGA = "https://github.com/fogonapps/el-cajon/releases/latest"
private val BOTONES = "<p class=\"botones\"><a class=\"boton\" href=\"$DESCARGA\">Descargar El Cajón</a></p>" +
    "<p class=\"pie\"><a href=\"/privacidad\">Privacidad</a> · <a href=\"/terminos\">Términos</a></p>"

private fun pagina(titulo: String, cuerpo: String, descripcion: String = "Tuya desde el primer día."): String = """<!doctype html>
<html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>${e(titulo)}</title><meta name="description" content="${e(descripcion)}">
<meta property="og:title" content="${e(titulo)}"><meta property="og:description" content="${e(descripcion)}"><meta property="og:site_name" content="Fogón Social">
<style>
:root{--papel:#151316;--tarjeta:#201d22;--texto:#f2ede6;--suave:#a79fa8;--acento:#9b7bff}
*{box-sizing:border-box}body{margin:0;background:var(--papel);color:var(--texto);font:16px/1.5 system-ui,sans-serif}
main{max-width:560px;margin:0 auto;padding:24px 16px}a{color:var(--acento)}h1{margin:.2em 0 0}h2{margin:.3em 0}
.suave{color:var(--suave)}.perfil{text-align:center;margin-bottom:24px}.avatar{width:112px;height:112px;border-radius:50%;object-fit:cover}
.cifras b{color:var(--texto)}article{background:var(--tarjeta);border-radius:20px;padding:16px;margin:12px 0}
.foto{width:100%;border-radius:14px}.portada{width:96px;border-radius:10px;float:right;margin-left:12px}.estrellas{color:var(--acento);margin:0}
article::after{content:"";display:block;clear:both}.botones{text-align:center;margin:28px 0}
.boton{display:inline-block;background:var(--acento);color:#140f1f;padding:12px 22px;border-radius:999px;text-decoration:none;font-weight:600}
.pie{text-align:center;font-size:14px}.legal h2{font-size:1.1em;margin-top:1.4em}
</style></head><body><main>$cuerpo</main></body></html>"""

/** Para que Android abra los enlaces (/@usuario, /p/…) directo en El Cajón: el paquete y la huella del certificado de firma. */
private const val ASSETLINKS = """[{"relation":["delegate_permission/common.handle_all_urls"],"target":{"namespace":"android_app","package_name":"io.github.fogonapps.cajon","sha256_cert_fingerprints":["CE:54:06:88:2E:36:1C:F1:8E:3E:CE:18:FA:34:87:69:E3:4D:8F:A6:CC:8D:D5:3F:74:A7:72:89:A9:93:D8:E5"]}}]"""

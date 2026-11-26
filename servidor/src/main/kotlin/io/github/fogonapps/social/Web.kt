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
 * Las páginas web: lo que ve quien abre un enlace compartido sin tener Fabs (con Fabs instalado, Android abre
 * la app directo gracias a `assetlinks.json`), y la política de privacidad, los términos, los estándares de seguridad infantil y cómo borrar la cuenta. Solo muestran lo público.
 */
fun Routing.paginasWeb(social: Social) {
    get("/.well-known/assetlinks.json") {
        call.respondText(ASSETLINKS, ContentType.Application.Json)
    }
    get("/") { call.respondText(pagina("Fogón", "<h1>Fogón</h1><p class=\"suave\">Tuya desde el primer día.</p>$BOTONES"), ContentType.Text.Html) }
    get("/privacidad") { call.respondText(pagina("Privacidad", recurso("privacidad.html") + PIE), ContentType.Text.Html) }
    get("/terminos") { call.respondText(pagina("Términos", recurso("terminos.html") + PIE), ContentType.Text.Html) }
    get("/privacy") { call.respondText(pagina("Privacy", recurso("privacy.html") + PIE_EN), ContentType.Text.Html) }
    get("/terms") { call.respondText(pagina("Terms", recurso("terms.html") + PIE_EN), ContentType.Text.Html) }
    get("/seguridad-infantil") { call.respondText(pagina("Seguridad infantil", recurso("seguridad-infantil.html") + PIE), ContentType.Text.Html) }
    get("/child-safety") { call.respondText(pagina("Child safety", recurso("child-safety.html") + PIE_EN), ContentType.Text.Html) }
    get("/borrar-cuenta") { call.respondText(pagina("Borrar tu cuenta", recurso("borrar-cuenta.html") + PIE), ContentType.Text.Html) }
    get("/delete-account") { call.respondText(pagina("Delete your account", recurso("delete-account.html") + PIE_EN), ContentType.Text.Html) }

    get("/{arroba}") {
        val arroba = call.parameters["arroba"] ?: ""
        if (!arroba.startsWith("@")) return@get call.respondText(pagina("No está", "<p>Esta página no existe.</p>"), ContentType.Text.Html, HttpStatusCode.NotFound)
        val p = runCatching { social.verPerfilGlobal(null, arroba.removePrefix("@")) }.getOrNull()
            ?: return@get call.respondText(pagina("No está", "<p>Este perfil no existe.</p>$BOTONES"), ContentType.Text.Html, HttpStatusCode.NotFound)
        val u = p.persona.usuario
        val nombre = p.persona.nombre.ifBlank { u }
        val cuerpo = buildString {
            // La portada subida (foto, GIF/WebP o video corto); los banners del catálogo viven en la app.
            if (!p.cerrada && Regex("[A-Za-z0-9_-]{22}").matches(p.portada)) {
                val ruta = "/v1/imagenes/${p.portada}"
                if (social.esVideoPortada(p.portada)) append("<video class=\"banner\" src=\"$ruta\" autoplay muted loop playsinline></video>")
                else append("<img class=\"banner\" src=\"$ruta\" alt=\"\">")
            }
            append("<header class=\"perfil\">")
            if (p.persona.foto) append("<img class=\"avatar\" src=\"/v1/fotos/${e(u)}\" alt=\"\">")
            append("<h1>${e(nombre)}")
            if (p.nivel > 0) append(" <span class=\"nivel\">${p.nivel}</span>")
            append("</h1><p class=\"suave\">@${e(u)}</p>")
            if (p.bio.isNotBlank()) append("<p>${e(p.bio)}</p>")
            append("<p class=\"cifras\"><b>${p.cuantas}</b> publicaciones · <b>${p.seguidores}</b> seguidores · <b>${p.siguiendo}</b> siguiendo</p>")
            append("</header>")
            if (p.cerrada) append("<p class=\"suave\">Esta cuenta es privada.</p>")
            else {
                (p.partes["cancion"] as? JsonObject)?.let { c ->
                    append("<article class=\"cancion\">")
                    texto(c, "portada").takeIf(Social::portadaSegura)?.let { append("<img class=\"portada\" src=\"${e(it)}\" alt=\"\">") }
                    append("<p class=\"suave\">Su canción</p><h2>${e(texto(c, "cancion"))}</h2><p>${e(texto(c, "creador"))}</p></article>")
                }
                runCatching { social.escaparates.ver(null, u) }.getOrDefault(emptyList()).forEach { append(repisa(it)) }
            }
            p.publicaciones.take(30).forEach { append(tarjeta(it)) }
            append(BOTONES)
        }
        call.respondText(pagina("$nombre (@$u)", cuerpo, descripcion = p.bio.ifBlank { "Mira el perfil de @$u en Fogón Social." },
            imagen = if (p.persona.foto) "$SERVIDOR_WEB/v1/fotos/${e(u)}" else ""), ContentType.Text.Html)
    }

    get("/p/{id}") {
        val pub = call.parameters["id"]?.toLongOrNull()?.let { social.publicacion(null, it) }
            ?: return@get call.respondText(pagina("No está", "<p>Esta publicación no existe o no es pública.</p>$BOTONES"), ContentType.Text.Html, HttpStatusCode.NotFound)
        val titulo = texto(pub.datos, "titulo").ifBlank { texto(pub.datos, "nombre").ifBlank { "Reseña de restaurante" } }
        call.respondText(pagina("$titulo · @${pub.autor.usuario}", tarjeta(pub) + BOTONES, descripcion = texto(pub.datos, "texto").take(160)), ContentType.Text.Html)
    }
}

private fun tarjeta(p: Publicacion): String = buildString {
    val d = p.datos
    append("<article>")
    append("<p class=\"suave\"><a href=\"/@${e(p.autor.usuario)}\">@${e(p.autor.usuario)}</a></p>")
    texto(d, "imagen").takeIf { Regex("[A-Za-z0-9_-]{22}").matches(it) }?.let { append("<img class=\"foto\" src=\"/v1/imagenes/$it\" alt=\"\">") }
    texto(d, "portada").takeIf(Social::portadaSegura)?.let { append("<img class=\"portada\" src=\"${e(it)}\" alt=\"\">") }
    texto(d, "titulo").takeIf { it.isNotBlank() }?.let { append("<h2>${e(it)}</h2>") }
    (d["estrellas"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it > 0 }?.let { append("<p class=\"estrellas\">${"★".repeat(it.toInt())}${if (it % 1 >= 0.5) "½" else ""}</p>") }
    if (p.app == "forzudo") {
        append("<p class=\"suave\">${e(AppsSocial.nombre(p.app))} · ${e(texto(d, "fecha"))}</p>")
        append("<p>Duración: ${e(texto(d, "duracion"))} s</p>")
        (d["ejercicios"] as? kotlinx.serialization.json.JsonArray)?.forEach { elemento ->
            val ejercicio = elemento as? JsonObject ?: return@forEach
            val series = ejercicio["series"] as? kotlinx.serialization.json.JsonArray
            append("<p>${e(texto(ejercicio, "nombre"))}: ${series?.size ?: 0} series</p>")
        }
    }
    if (p.app == "alcancia") {
        val meta = texto(d, "meta")
        val logro = when (texto(d, "logro")) {
            "meta_cumplida" -> "Cumplió su meta «$meta»"
            "meta_avance" -> "Va en el ${texto(d, "porcentaje")} % de su meta «$meta»"
            "primera_anotacion" -> "Empezó a llevar sus cuentas"
            else -> "Cerró ${texto(d, "cantidad")} meses"
        }
        append("<h2>${e(logro)}</h2><p class=\"suave\">La Alcancía</p>")
    }
    if (p.app == "cucha") {
        append("<h2>${e(texto(d, "nombre").ifBlank { "Reseña de restaurante" })}</h2>")
        texto(d, "nota").takeIf { it.isNotBlank() }?.let { append("<p>Nota: ${e(it)}/5</p>") }
        texto(d, "plato").takeIf { it.isNotBlank() }?.let { append("<p>Plato: ${e(it)}</p>") }
        if ("lugar" in d) {
            val lugar = d["lugarDetalle"] as? JsonObject
            append(if (lugar == null) "<p>Lugar no disponible</p>" else "<p>${e(texto(lugar, "nombre"))} · ${e(texto(lugar, "ciudad"))}</p>")
            lugar?.let { texto(it, "direccion").takeIf { v -> v.isNotBlank() && v != "null" }?.let { v -> append("<p>${e(v)}</p>") } }
        }
        if (p.tipo == "receta") {
            append("<p>Porciones: ${e(texto(d, "porciones"))}</p>")
            texto(d, "tiempo").takeIf { it.isNotBlank() }?.let { append("<p>Tiempo: ${e(it)} min</p>") }
            append("<ul>")
            (d["ingredientes"] as? kotlinx.serialization.json.JsonArray)?.forEach { elemento ->
                val i = elemento as? JsonObject ?: return@forEach
                append("<li>${e(texto(i, "nombre"))}: ${e(texto(i, "cantidad"))} ${e(texto(i, "unidad"))}</li>")
            }
            append("</ul><ol>")
            (d["pasos"] as? kotlinx.serialization.json.JsonArray)?.forEach { append("<li>${e((it as? JsonPrimitive)?.content.orEmpty())}</li>") }
            append("</ol>")
        }
        (d["nutricion"] as? JsonObject)?.let { n ->
            append("<p>Nutrición${if (p.tipo == "receta") " por porción" else ""}: ${e(texto(n, "kcal"))} kcal · Proteína ${e(texto(n, "proteina"))} g · Carbohidratos ${e(texto(n, "carbohidratos"))} g · Grasa ${e(texto(n, "grasa"))} g</p>")
        }
        val video = texto(d, "video")
        val poster = texto(d, "poster")
        if (Regex("[A-Za-z0-9_-]{22}").matches(video) && Regex("[A-Za-z0-9_-]{22}").matches(poster)) {
            append("<video class=\"foto\" controls preload=\"none\" poster=\"/v1/imagenes/$poster\" src=\"/v1/videos/$video\"></video>")
        }
        (d["imagenes"] as? kotlinx.serialization.json.JsonArray)?.forEach {
            val nombre = (it as? JsonPrimitive)?.content.orEmpty()
            if (Regex("[A-Za-z0-9_-]{22}").matches(nombre)) append("<img class=\"foto\" src=\"/v1/imagenes/$nombre\" alt=\"\">")
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

private const val DESCARGA = "https://play.google.com/store/apps/details?id=io.github.fogonapps.cajon"
/** El pie con las páginas legales, en español y en inglés. */
private const val PIE = "<p class=\"pie\"><a href=\"/privacidad\">Privacidad</a> · <a href=\"/terminos\">Términos</a> · <a href=\"/seguridad-infantil\">Seguridad infantil</a> · <a href=\"/borrar-cuenta\">Borrar tu cuenta</a></p>"
private const val PIE_EN = "<p class=\"pie\"><a href=\"/privacy\">Privacy</a> · <a href=\"/terms\">Terms</a> · <a href=\"/child-safety\">Child safety</a> · <a href=\"/delete-account\">Delete your account</a></p>"
private val BOTONES = "<p class=\"botones\"><a class=\"boton\" href=\"$DESCARGA\">Descargar Fabs</a></p>" + PIE

private const val SERVIDOR_WEB = "https://fogonapps.duckdns.org"

/** Una repisa del perfil en la página pública: texto, número, fotos, álbumes e insignias. */
private fun repisa(r: Escaparate): String = buildString {
    val d = r.datos
    fun lista(clave: String) = (d[clave] as? kotlinx.serialization.json.JsonArray).orEmpty()
    when (r.tipo) {
        "texto" -> append("<article><p class=\"frase\">“${e(texto(d, "texto"))}”</p></article>")
        "numero", "forzudo:racha", "cucha:racha" -> append("<article><p class=\"numero\">${e(texto(d, "numero"))}</p><p class=\"suave\">${e(texto(d, "etiqueta"))}</p></article>")
        "galeria" -> {
            val fotos = lista("fotos").mapNotNull { (it as? JsonObject)?.let { f -> texto(f, "nombre") } }.filter { Regex("[A-Za-z0-9_-]{22}").matches(it) }
            if (fotos.isNotEmpty()) append("<article class=\"galeria\">" + fotos.joinToString("") { "<img src=\"/v1/imagenes/$it\" alt=\"\">" } + "</article>")
        }
        "insignias" -> append("<article><p class=\"suave\">Insignias</p><p>" + lista("insignias").joinToString(" · ") { e((it as? JsonPrimitive)?.content.orEmpty()) } + "</p></article>")
        else -> lista("elementos").mapNotNull { it as? JsonObject }.takeIf { it.isNotEmpty() }?.let { elementos ->
            append("<article class=\"elementos\">")
            elementos.take(6).forEach { el ->
                append("<div>")
                texto(el, "portada").takeIf(Social::portadaSegura)?.let { append("<img src=\"${e(it)}\" alt=\"\">") }
                append("<b>${e(texto(el, "titulo"))}</b><span class=\"suave\">${e(texto(el, "texto").take(80))}</span></div>")
            }
            append("</article>")
        }
    }
}

private fun pagina(titulo: String, cuerpo: String, descripcion: String = "Tuya desde el primer día.", imagen: String = ""): String = """<!doctype html>
<html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>${e(titulo)}</title><meta name="description" content="${e(descripcion)}">
<meta property="og:title" content="${e(titulo)}"><meta property="og:description" content="${e(descripcion)}"><meta property="og:site_name" content="Fogón Social">${if (imagen.isNotBlank()) "<meta property=\"og:image\" content=\"$imagen\">" else ""}
<style>
:root{--papel:#151316;--tarjeta:#201d22;--texto:#f2ede6;--suave:#a79fa8;--acento:#9b7bff}
*{box-sizing:border-box}body{margin:0;background:var(--papel);color:var(--texto);font:16px/1.5 system-ui,sans-serif}
main{max-width:560px;margin:0 auto;padding:24px 16px}a{color:var(--acento)}h1{margin:.2em 0 0}h2{margin:.3em 0}
.suave{color:var(--suave)}.perfil{text-align:center;margin-bottom:24px}.avatar{width:112px;height:112px;border-radius:50%;object-fit:cover}
.cifras b{color:var(--texto)}article{background:var(--tarjeta);border-radius:20px;padding:16px;margin:12px 0}
.foto{width:100%;border-radius:14px}.portada{width:96px;border-radius:10px;float:right;margin-left:12px}.estrellas{color:var(--acento);margin:0}
article::after{content:"";display:block;clear:both}.botones{text-align:center;margin:28px 0}
.boton{display:inline-block;background:var(--acento);color:#140f1f;padding:12px 22px;border-radius:999px;text-decoration:none;font-weight:600}
.pie{text-align:center;font-size:14px}.banner{width:100%;aspect-ratio:3.4;object-fit:cover;border-radius:24px;display:block;margin-bottom:-48px}
.nivel{font-size:.55em;vertical-align:middle;border:2px solid var(--suave);border-radius:999px;padding:1px 9px}.frase{font-size:1.3em;margin:0}
.numero{font-size:2.4em;font-weight:700;margin:0}.galeria{display:flex;gap:8px;padding:8px}.galeria img{flex:1;min-width:0;aspect-ratio:4/3;object-fit:cover;border-radius:14px}
.elementos{display:flex;gap:10px;overflow-x:auto}.elementos div{width:112px;flex:none;display:flex;flex-direction:column;gap:4px;font-size:14px}.elementos img{width:112px;aspect-ratio:1;object-fit:cover;border-radius:12px}.legal h2{font-size:1.1em;margin-top:1.4em}
</style></head><body><main>$cuerpo</main></body></html>"""

/** Para que Android abra los enlaces (/@usuario, /p/…) directo en Fabs: el paquete y las huellas de firma: la de subida y la de Google Play, que re-firma lo que se instala desde la tienda. */
private const val ASSETLINKS = """[{"relation":["delegate_permission/common.handle_all_urls"],"target":{"namespace":"android_app","package_name":"io.github.fogonapps.cajon","sha256_cert_fingerprints":["CE:54:06:88:2E:36:1C:F1:8E:3E:CE:18:FA:34:87:69:E3:4D:8F:A6:CC:8D:D5:3F:74:A7:72:89:A9:93:D8:E5","A2:BB:DA:64:05:82:54:1E:FD:F0:2B:8F:58:76:1E:33:F4:81:22:41:B4:3A:4C:11:66:AD:2A:71:74:BF:40:A2"]}}]"""

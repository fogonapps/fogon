package io.github.fogonapps.social

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Caché de portadas de Open Library y Cover Art Archive (tardan unos 2 s porque redirigen a archive.org; TMDB es
 * rápido y no pasa por aquí). La primera vez el servidor la trae y la guarda; después la entrega al instante, y la
 * fuente no ve la IP de quien mira. Solo esos dominios (y archive.org en las redirecciones), solo imágenes, hasta
 * [MAXIMO] bytes cada una y [TOPE] en total (se borran las menos usadas).
 */
class Portadas(private val dir: File, private val ahora: () -> Long = System::currentTimeMillis,
    private val traer: (URI) -> Pair<Int, Triple<String?, String?, ByteArray?>> = ::pedirSinSeguir) {
    companion object {
        const val MAXIMO = 3_000_000
        const val TOPE = 2_000_000_000L
        private val ORIGENES = setOf("covers.openlibrary.org", "coverartarchive.org")
        private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build()

        /** Una dirección que se puede pedir: https, sin usuario ni puerto, de uno de los orígenes. */
        fun permitida(u: String): Boolean = runCatching {
            val x = URI(u)
            x.scheme == "https" && x.userInfo == null && x.port == -1 && x.host in ORIGENES && u.length <= 300
        }.getOrDefault(false)

        /** A dónde puede llevar una redirección: los orígenes, archive.org y sus subdominios. */
        fun destinoValido(x: URI): Boolean = x.scheme == "https" && x.userInfo == null && x.port == -1 &&
            (x.host in ORIGENES || x.host == "archive.org" || x.host.endsWith(".archive.org"))

        private fun pedirSinSeguir(u: URI): Pair<Int, Triple<String?, String?, ByteArray?>> {
            val r = http.send(HttpRequest.newBuilder(u).timeout(Duration.ofSeconds(20)).header("User-Agent", "FogonSocial/$VERSION (+https://fogonapps.duckdns.org)").GET().build(),
                HttpResponse.BodyHandlers.ofInputStream())
            val tipo = r.headers().firstValue("content-type").orElse(null)
            val destino = r.headers().firstValue("location").orElse(null)
            val cuerpo = if (r.statusCode() == 200) r.body().use { it.readNBytes(MAXIMO + 1) } else { r.body().close(); null }
            return r.statusCode() to Triple(tipo, destino, cuerpo)
        }

        /** El tipo por los primeros bytes (no se confía en la cabecera): JPEG, PNG, GIF o WebP. */
        fun tipoImagen(b: ByteArray): String? = when {
            b.size > 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() -> "image/jpeg"
            b.size > 8 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte() -> "image/png"
            b.size > 6 && String(b, 0, 3) == "GIF" -> "image/gif"
            b.size > 12 && String(b, 0, 4) == "RIFF" && String(b, 8, 4) == "WEBP" -> "image/webp"
            else -> null
        }
    }

    private val cerrojos = ConcurrentHashMap<String, Any>()

    /** La portada (sus bytes y su tipo), de la caché o traída ahora; null si no existe o no es una imagen. */
    fun obtener(u: String): Pair<ByteArray, String>? {
        if (!permitida(u)) throw Problema(400, "portada")
        val clave = MessageDigest.getInstance("SHA-256").digest(u.toByteArray()).joinToString("") { "%02x".format(it) }
        val archivo = File(dir, "${clave.take(2)}/$clave")
        synchronized(cerrojos.computeIfAbsent(clave) { Any() }) {
            try {
                if (archivo.isFile) {
                    archivo.setLastModified(ahora())
                    val b = archivo.readBytes()
                    return tipoImagen(b)?.let { b to it }
                }
                var actual = URI(u)
                repeat(5) {
                    val (codigo, r) = traer(actual)
                    val (_, destino, cuerpo) = r
                    when {
                        codigo == 200 && cuerpo != null -> {
                            if (cuerpo.size > MAXIMO) return null
                            val tipo = tipoImagen(cuerpo) ?: return null
                            archivo.parentFile.mkdirs()
                            val tmp = File(archivo.parentFile, archivo.name + ".tmp")
                            tmp.writeBytes(cuerpo); tmp.renameTo(archivo)
                            return cuerpo to tipo
                        }
                        codigo in 300..399 && destino != null -> {
                            val siguiente = actual.resolve(destino)
                            if (!destinoValido(siguiente)) return null
                            actual = siguiente
                        }
                        else -> return null
                    }
                }
                return null
            } finally { cerrojos.remove(clave) }
        }
    }

    /** Si la caché pasa de [TOPE], borra las portadas menos usadas hasta quedar en el 80 %. */
    fun limpiar() {
        val archivos = dir.walkTopDown().filter { it.isFile }.toList()
        var total = archivos.sumOf { it.length() }
        if (total <= TOPE) return
        for (f in archivos.sortedBy { it.lastModified() }) {
            if (total <= TOPE * 8 / 10) break
            total -= f.length(); f.delete()
        }
    }
}

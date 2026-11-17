package io.github.fogonapps.cajon.datos

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Lo que la persona escucha, agrupado por álbum: de lo que suena en cualquier app (`EscuchaMusica`, con el acceso a
 * notificaciones), de Spotify (si lo conectó) o de su historial descargado de Spotify. Solo en este teléfono
 * (`files/escuchas.json`): no va en el respaldo ni en la sincronización. De aquí salen "Escuchado hace poco" y los
 * álbumes que se agregan solos.
 */
@Serializable
data class AlbumOido(
    /** "artista|álbum", sin tildes ni mayúsculas. */
    val clave: String,
    val artista: String,
    val album: String,
    /** Las canciones distintas que sonaron (sin tildes ni mayúsculas). */
    val canciones: List<String> = emptyList(),
    val primera: Long = 0,
    val ultima: Long = 0,
    val veces: Int = 0,
    /** De qué app vino lo último ("Spotify", "YouTube Music"…). */
    val app: String = "",
    /** Ya está en el cajón (su id) o la persona lo descartó: no se vuelve a ofrecer. */
    val obra: String? = null,
    val descartado: Boolean = false,
    val portada: String = "",
)

@Serializable
data class Oidos(
    val albumes: List<AlbumOido> = emptyList(),
    /** Hasta cuándo se trajo lo escuchado de Spotify (milisegundos), para no repetir. */
    val spotifyHasta: Long = 0,
    /** Huellas de escuchas importadas para que repetir un historial sea idempotente. */
    val huellas: Set<String> = emptySet(),
    /** Apps de las que no se anota nada (nombres de paquete), elegidas por la persona. */
    val sinAnotar: List<String> = emptyList(),
    /** Apps de las que llegó algo (paquete → nombre), para poder excluirlas. */
    val apps: Map<String, String> = emptyMap(),
    val intentosPortada: Map<String, Long> = emptyMap(),
)

/** Una canción escuchada (de cualquier fuente), antes de sumarla a su álbum. */
data class Escucha(val artista: String, val album: String, val cancion: String, val cuando: Long, val app: String, val paquete: String = "", val portada: String = "")

/** El registro de escuchas: uno por proceso (lo usan la app y el servicio que escucha). */
class RegistroEscuchas(context: Context, directorio: File = context.filesDir) {
    private val portadas = File(directorio, "oidos")
    private val archivo = File(directorio, "escuchas.json")
    private val _oidos = MutableStateFlow(leer())
    val oidos: StateFlow<Oidos> = _oidos

    private fun leer(): Oidos = runCatching { JsonCajon.decodeFromString(Oidos.serializer(), archivo.readText()) }.getOrDefault(Oidos())

    @Synchronized
    fun cambiar(f: (Oidos) -> Oidos) {
        val nuevo = f(_oidos.value)
        _oidos.value = nuevo
        runCatching {
            val tmp = File(archivo.parentFile, "escuchas.json.tmp")
            tmp.writeText(JsonCajon.encodeToString(Oidos.serializer(), nuevo))
            tmp.renameTo(archivo)
        }
    }

    /**
     * Une lo escuchado que llega de otro teléfono o de un respaldo: por álbum, las canciones de los dos, la primera y
     * la última vez, y si ya está en el cajón o se descartó en alguno. Nada se pierde.
     */
    fun unir(otro: Oidos?) {
        if (otro == null) return
        cambiar { unirA(it, otro) }
    }

    /** Suma escuchas a sus álbumes (sin repetir la misma canción del mismo álbum en el mismo minuto). */
    fun sumar(lista: List<Escucha>) {
        if (lista.isEmpty()) return
        cambiar { o ->
            sumarA(o, lista)
        }
    }

    /** Copia privada de la sesión musical, sin descargar ni subir imágenes. */
    @Synchronized
    fun guardarPortada(clave: String, bitmap: android.graphics.Bitmap): String = runCatching {
        portadas.mkdirs()
        val nombre = java.security.MessageDigest.getInstance("SHA-256").digest(clave.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val destino = File(portadas, "$nombre.jpg")
        if (!destino.exists()) {
            val escala = minOf(1.0, 300.0 / maxOf(bitmap.width, bitmap.height))
            val pequena = android.graphics.Bitmap.createScaledBitmap(bitmap,
                maxOf(1, (bitmap.width * escala).toInt()), maxOf(1, (bitmap.height * escala).toInt()), true)
            val tmp = File(portadas, "$nombre.tmp")
            try {
                tmp.outputStream().use { check(pequena.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it)) }
                check(tmp.renameTo(destino))
            } finally { tmp.delete(); if (pequena !== bitmap) pequena.recycle() }
        }
        android.net.Uri.fromFile(destino).toString()
    }.getOrDefault("")

    companion object {
        fun portadaDisponible(url: String) = url.startsWith("https://") ||
            (url.startsWith("file://") && runCatching { File(java.net.URI(url)).isFile }.getOrDefault(false))

        fun preferirPortada(actual: String, nueva: String): String = when {
            nueva.startsWith("file://") && portadaDisponible(nueva) -> nueva
            portadaDisponible(actual) -> actual
            nueva.startsWith("https://") -> nueva
            else -> ""
        }

        fun debeIntentar(o: Oidos, clave: String, ahora: Long): Boolean =
            o.intentosPortada[clave]?.let { ahora - it >= 86_400_000L } ?: true

        fun paraExportar(o: Oidos) = o.copy(albumes = o.albumes.map {
            it.copy(portada = it.portada.takeIf { p -> p.startsWith("https://") }.orEmpty())
        }, intentosPortada = emptyMap())

        private val edicion = Regex("(?i)\\b(deluxe|remaster(?:ed)?|edition|edición|expanded|anniversary|single|ep|live|en vivo|version|versión)\\b")
        fun artistaTolerante(s: String) = s.split(Regex("(?i)\\s+(?:feat\\.?|ft\\.?|con)\\s+")).first().trim()
        fun albumTolerante(s: String): String {
            val sinGrupos = s.replace(Regex("\\s*[（(\\[][^)\\]）]*[)\\]）]")) { if (edicion.containsMatchIn(it.value)) "" else it.value }
            return sinGrupos.replace(Regex("\\s+[-–—]\\s+(.+)$")) { if (edicion.containsMatchIn(it.groupValues[1])) "" else it.value }.trim()
        }
        fun claveTolerante(artista: String, album: String) =
            "${Logica.tituloLimpio(artistaTolerante(artista))}|${Logica.tituloLimpio(albumTolerante(album))}"

        fun pendientes(o: Oidos) = o.albumes.filter { it.obra == null && !it.descartado }.sortedByDescending { it.ultima }

        fun unirA(o: Oidos, otro: Oidos): Oidos {
            val m = o.albumes.associateBy { it.clave }.toMutableMap()
            for (b in otro.albumes) {
                val a = m[b.clave]
                m[b.clave] = if (a == null) b else a.copy(
                    canciones = (a.canciones + b.canciones).distinct().takeLast(200),
                    primera = listOf(a.primera, b.primera).filter { it > 0 }.minOrNull() ?: 0,
                    ultima = maxOf(a.ultima, b.ultima), veces = maxOf(a.veces, b.veces),
                    app = if (b.ultima > a.ultima) b.app.ifBlank { a.app } else a.app,
                    portada = preferirPortada(a.portada, b.portada),
                    obra = a.obra ?: b.obra, descartado = a.descartado || b.descartado,
                )
            }
            return o.copy(albumes = m.values.sortedByDescending { it.ultima }.take(2000), spotifyHasta = maxOf(o.spotifyHasta, otro.spotifyHasta),
                huellas = o.huellas + otro.huellas,
                sinAnotar = (o.sinAnotar + otro.sinAnotar).distinct(), apps = otro.apps + o.apps)
        }

        fun sumarA(o: Oidos, lista: List<Escucha>): Oidos {
            val huellas = o.huellas.toMutableSet()
            val porClave = o.albumes.associateBy { it.clave }.toMutableMap()
            val apps = o.apps.toMutableMap()
            for (x in lista) {
                if (x.paquete in o.sinAnotar) continue
                if (x.paquete.isNotEmpty()) apps[x.paquete] = x.app
                val artista = x.artista.trim(); val album = x.album.trim()
                if (artista.isEmpty() || album.isEmpty()) continue
                val clave = clave(artista, album)
                val huella = java.security.MessageDigest.getInstance("SHA-256").digest(
                    kotlinx.serialization.json.JsonArray(listOf(clave, Logica.tituloLimpio(x.cancion), (x.cuando / 60_000).toString()).map { kotlinx.serialization.json.JsonPrimitive(it) }).toString().toByteArray()
                ).joinToString("") { "%02x".format(it) }
                if (!huellas.add(huella)) {
                    porClave[clave]?.let { porClave[clave] = it.copy(portada = preferirPortada(it.portada, x.portada)) }
                    continue
                }
                val viejo = porClave[clave] ?: AlbumOido(clave, artista, album, primera = x.cuando)
                val cancion = Logica.tituloLimpio(x.cancion)
                porClave[clave] = viejo.copy(
                    portada = preferirPortada(viejo.portada, x.portada),
                    canciones = if (cancion.isEmpty() || cancion in viejo.canciones) viejo.canciones else (viejo.canciones + cancion).takeLast(200),
                    primera = minOf(viejo.primera.takeIf { it > 0 } ?: x.cuando, x.cuando),
                    ultima = maxOf(viejo.ultima, x.cuando), veces = viejo.veces + 1, app = x.app.ifBlank { viejo.app },
                )
            }
            // Hasta 2000 álbumes: los más recientes.
            return o.copy(albumes = porClave.values.sortedByDescending { it.ultima }.take(2000), apps = apps, huellas = huellas)
        }

        /** La clave de un álbum: artista principal y título, sin tildes, mayúsculas ni "(Deluxe)" y parecidos. */
        fun clave(artista: String, album: String): String {
            val a = Logica.tituloLimpio(artista.split(",", " & ", " feat", " ft.", " x ").first())
            val t = Logica.tituloLimpio(album.replace(Regex("\\s*[(\\[][^)\\]]*(deluxe|edition|edición|remaster|expanded|version|versión)[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE), ""))
            return "$a|$t"
        }
    }
}

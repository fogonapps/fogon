package io.github.fogonapps.cajon.datos

import kotlinx.serialization.Serializable
import java.net.URI

@Serializable
data class ImagenElegida(val fuente: String, val url: String, val autor: String? = null,
    val licencia: String? = null, val edicion: String? = null, val cuando: Long = 0)

@Serializable
data class EdicionLibro(val id: String, val portada: String, val editorial: String = "",
    val anio: Int? = null, val idiomas: List<String> = emptyList(), val paginas: Int? = null)

@Serializable
data class PortadasComunidad(val comunidad: String? = null, val votos: Map<String, Int> = emptyMap(),
    val miVoto: String? = null, val bloqueadas: List<String> = emptyList())

/** Reglas compartidas por las fuentes y todas las superficies que muestran portadas. */
object Imagenes {
    enum class Lugar { LISTA, FICHA, VISOR }
    fun fuente(url: String): String = when (runCatching { URI(url).host }.getOrNull()) {
        "image.tmdb.org" -> "tmdb"
        "covers.openlibrary.org" -> "ol"
        "coverartarchive.org", "archive.org" -> "caa"
        "upload.wikimedia.org", "commons.wikimedia.org" -> "wikimedia"
        else -> "local"
    }
    fun autorizada(url: String) = runCatching {
        val u = URI(url)
        u.scheme == "https" && u.host in setOf("image.tmdb.org", "covers.openlibrary.org", "coverartarchive.org", "upload.wikimedia.org") && u.userInfo == null
    }.getOrDefault(false)
    fun tamano(fuente: String, lugar: Lugar, ahorro: Boolean = false): String {
        val normal = when (lugar) { Lugar.LISTA -> 1; Lugar.FICHA -> 2; Lugar.VISOR -> if (fuente == "tmdb") 3 else 2 }
        val nivel = (normal - if (ahorro) 1 else 0).coerceAtLeast(0)
        return when (fuente) {
            "tmdb" -> listOf("w185", "w342", "w780", "original")[nivel]
            "ol" -> listOf("M", "L", "L")[nivel]   // La "M" de Open Library (180 px) se ve borrosa en pantallas densas.
            "caa" -> listOf("250", "500", "1200")[nivel]
            else -> ""
        }
    }
    fun url(url: String, lugar: Lugar, ahorro: Boolean = false): String {
        val t = tamano(fuente(url), lugar, ahorro)
        return when (fuente(url)) {
            "tmdb" -> url.replace(Regex("/t/p/(w\\d+|original)/"), "/t/p/$t/")
            "ol" -> url.replace(Regex("-[SML]\\.jpg"), "-$t.jpg")
            "caa" -> url.replace(Regex("-(250|500|1200)(?=\\.|$|\\?)"), "-$t")
            else -> url
        }
    }
    /**
     * La forma de comparar imágenes y de votarlas: el tamaño grande y, de Commons (FilePath, miniatura u original), una
     * sola por archivo: su original en upload.wikimedia.org (así los votos de una misma imagen no se reparten).
     */
    fun clave(url: String) = originalCommons(url) ?: url(url, Lugar.VISOR)
    fun descripcion(ed: EdicionLibro, idioma: String = "es", conId: Boolean = true): String {
        val codigos = mapOf("spa" to "es", "eng" to "en", "fra" to "fr", "fre" to "fr", "deu" to "de", "ger" to "de", "ita" to "it", "por" to "pt")
        val idiomas = ed.idiomas.joinToString { java.util.Locale.forLanguageTag(codigos[it] ?: it).getDisplayLanguage(java.util.Locale.forLanguageTag(idioma)) }
        return listOf(ed.editorial, ed.anio?.toString().orEmpty(), idiomas, if (conId) ed.id else "").filter { it.isNotBlank() }.joinToString(" · ")
    }
    fun bloqueada(url: String, bloqueadas: List<String>) = bloqueadas.any { clave(it) == clave(url) }
    fun filtrar(urls: List<String>, bloqueadas: List<String>) = urls.filterNot { bloqueada(it, bloqueadas) }.distinctBy(::clave)
    fun elegir(o: Obra, comunidad: PortadasComunidad, recomendada: String): String {
        val propia = o.imagen?.url ?: o.portada
        return listOfNotNull(propia.takeIf { o.portadaPropia }, comunidad.comunidad, recomendada)
            .firstOrNull { it.isNotBlank() && !bloqueada(it, comunidad.bloqueadas) }.orEmpty()
    }
    fun edicion(eds: List<EdicionLibro>, idioma: String): EdicionLibro? {
        val codigo = when (idioma.substringBefore('-')) { "es" -> "spa"; "en" -> "eng"; else -> idioma }
        val conPortada = eds.filter { it.portada.isNotBlank() }
        return conPortada.filter { codigo in it.idiomas }.maxByOrNull { it.anio ?: 0 }
            ?: conPortada.filter { "eng" in it.idiomas }.maxByOrNull { it.anio ?: 0 }
    }
    /**
     * La procedencia de la [portada] que queda al renovar los datos de [v] con los nuevos [d] («Actualizar datos»): la
     * elegida por la persona no se toca; si no, la de esa misma imagen (por su [clave]) entre la nueva, la que tenía y
     * las [conocidas] (la de la imagen de la comunidad). Así una imagen de Commons no pierde su autor y su licencia por
     * un fallo de red, ni la de la comunidad se queda con el crédito de otra.
     */
    fun alRenovar(v: Obra, d: Obra, portada: String = d.portada.ifBlank { v.portadaRecomendada ?: v.portada }, vararg conocidas: ImagenElegida?): ImagenElegida? {
        if (v.portadaPropia) return v.imagen
        if (portada.isBlank()) return d.imagen
        return (listOf(d.imagen, v.imagen) + conocidas).firstOrNull { it != null && it.url.isNotBlank() && clave(it.url) == clave(portada) }
            ?.let { if (it.url == portada) it else it.copy(url = portada) } ?: d.imagen
    }
    fun migrar(o: Obra) = if (o.imagen == null && o.portada.isNotBlank())
        o.copy(imagen = ImagenElegida(fuente(o.portada), o.portada, edicion = o.edicion, cuando = o.traida)) else o
    fun enlace(i: ImagenElegida, obra: String): String = when (i.fuente) {
        "ol" -> "https://openlibrary.org/" + (i.edicion?.let { "books/$it" } ?: "works/${obra.removePrefix("ol:")}")
        "tmdb" -> "https://www.themoviedb.org/" + obra.removePrefix("tmdb:").replace(':', '/')
        "wikimedia" -> "https://commons.wikimedia.org/wiki/File:" + archivoCommons(i.url)
        else -> i.url
    }

    /**
     * El nombre del archivo de Commons (codificado como en la dirección) de una imagen: de «Special:FilePath/…», de
     * «upload.wikimedia.org/…/F.jpg» o de su miniatura «…/thumb/a/ab/F.svg/500px-F.svg.png» (la carpeta, no la miniatura).
     */
    fun archivoCommons(url: String): String {
        if ("Special:FilePath/" in url) return url.substringAfter("Special:FilePath/").substringBefore('?')
        val ruta = url.substringBefore('?').substringAfter("://").substringAfter('/').split('/')
        val i = ruta.indexOf("thumb")
        return if (i >= 0 && ruta.size > i + 3) ruta[i + 3] else ruta.last().replace(Regex("^\\d+px-"), "")
    }

    /**
     * El nombre de un archivo de Commons como lo guarda MediaWiki: espacios y guiones bajos seguidos en un solo «_», sin
     * «_» en los extremos y con la primera letra en mayúscula solo si es una minúscula ASCII (lo demás ya viene canónico).
     */
    internal fun nombreCommons(nombre: String): String =
        nombre.replace(Regex("[ _]+"), "_").trim('_').let { n -> if (n.firstOrNull() in 'a'..'z') n.replaceFirstChar { it.uppercaseChar() } else n }

    /** El nombre codificado como en las direcciones de MediaWiki: estos signos quedan tal cual. */
    private fun codificarCommons(nombre: String) = java.net.URLEncoder.encode(nombre, "UTF-8").replace("+", "%20")
        .replace(Regex("%(3B|40|24|21|2A|28|29|2C|7E|3A)")) { java.net.URLDecoder.decode(it.value, "UTF-8") }

    /** El nombre canónico de la imagen de Commons de [url] (FilePath o upload), o null si no es de Commons. */
    private fun nombreDe(url: String): String? {
        val host = runCatching { URI(url).host }.getOrNull()
        val esCommons = (host == "commons.wikimedia.org" && "/wiki/Special:FilePath/" in url) ||
            (host == "upload.wikimedia.org" && "/wikipedia/commons/" in url)
        if (!esCommons) return null
        return runCatching { java.net.URLDecoder.decode(archivoCommons(url).replace("+", "%2B"), "UTF-8") }.getOrNull()
            ?.let(::nombreCommons)?.takeIf { it.isNotEmpty() && '/' !in it }
    }

    /** La carpeta de un archivo en upload.wikimedia.org: «h/hh», por el MD5 de su nombre. */
    private fun carpetaCommons(nombre: String): String {
        val md5 = java.security.MessageDigest.getInstance("MD5").digest(nombre.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return "${md5[0]}/${md5.take(2)}"
    }

    /** El original de una imagen de Commons en upload.wikimedia.org, o null si [url] no es de Commons. */
    private fun originalCommons(url: String): String? = nombreDe(url)?.let { n ->
        "https://upload.wikimedia.org/wikipedia/commons/${carpetaCommons(n)}/${codificarCommons(n)}"
    }

    /**
     * Una imagen de Commons pedida como «commons.wikimedia.org/wiki/Special:FilePath/…» pasa a su dirección en
     * «upload.wikimedia.org», la que aceptan las portadas de la red: el original, que siempre existe (una miniatura de N
     * px no existe si el original es más chico); solo un SVG con «?width=N» pasa a su miniatura en PNG, porque un SVG se
     * puede agrandar y su original no se dibuja como portada. Las demás direcciones quedan igual.
     */
    fun deCommons(url: String): String {
        if (runCatching { URI(url).host }.getOrNull() != "commons.wikimedia.org" || "/wiki/Special:FilePath/" !in url) return url
        val n = nombreDe(url) ?: return url
        val ancho = Regex("[?&]width=(\\d{1,5})").find(url)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }
        val ext = n.substringAfterLast('.', "").lowercase()
        val cod = codificarCommons(n)
        if (ancho == null || ext != "svg") return originalCommons(url)!!
        return "https://upload.wikimedia.org/wikipedia/commons/thumb/${carpetaCommons(n)}/$cod/${ancho}px-$cod.png"
    }

    /** La portada que va a la red (perfil, publicaciones, historias, chat): solo una de internet y, si es de Commons, la de upload.wikimedia.org. */
    fun paraRed(url: String): String = deCommons(url).takeIf { it.startsWith("https://") }.orEmpty()

    /**
     * Si se puede votar [url] como portada de todos: de una fuente autorizada y, en un juego («wd:»), solo la imagen
     * cuyo crédito (autor y licencia) se conoce: [credito] es la procedencia que se tiene de esa imagen.
     */
    fun votable(fuente: String, url: String, credito: ImagenElegida?): Boolean {
        if (!autorizada(clave(url))) return false
        if (!fuente.startsWith("wd:")) return true
        return credito != null && clave(credito.url) == clave(url) && WikidataJuegos.conCredito(credito) != null
    }

    /**
     * De las procedencias que ya se conocen ([conocidas]: la guardada en la obra, la de la imagen de la comunidad), la de
     * [url] (la misma imagen por su [clave]) solo si trae autor y licencia; null si hay que pedirla.
     */
    fun creditoConocido(url: String, vararg conocidas: ImagenElegida?): ImagenElegida? =
        conocidas.firstOrNull { it != null && clave(it.url) == clave(url) && WikidataJuegos.conCredito(it) != null }?.copy(url = url)

    /** Una miniatura de Commons en upload.wikimedia.org («…/thumb/…»): la que da la API de Commons, no el original. */
    fun esMiniaturaCommons(url: String) = autorizada(url) && "/wikipedia/commons/thumb/" in url

    /**
     * De las procedencias que ya se conocen, la de la misma imagen que [url] que es una miniatura de Commons (con su
     * dirección, no la de [url]) y trae autor y licencia; null si no se conoce ninguna.
     */
    fun miniaturaConocida(url: String, vararg conocidas: ImagenElegida?): ImagenElegida? =
        conocidas.firstOrNull { it != null && esMiniaturaCommons(it.url) && clave(it.url) == clave(url) && WikidataJuegos.conCredito(it) != null }

    /**
     * La procedencia de la imagen de la comunidad [url] (de Commons), en este orden: la miniatura ya conocida con
     * crédito; la ya pedida antes ([yaPedida]) de esa misma imagen con crédito (aunque sea el original, si la imagen
     * mide menos de 500 px), para no consultar a Commons en cada apertura; la que da [pedir] con crédito; un crédito
     * conocido de esa imagen (no se pierde porque Commons vuelva sin autor ni licencia); y, si no, lo que dio [pedir].
     */
    suspend fun creditoDeComunidad(url: String, deLaObra: ImagenElegida?, yaPedida: ImagenElegida?, pedir: suspend () -> ImagenElegida?): ImagenElegida? {
        miniaturaConocida(url, deLaObra, yaPedida)?.let { return it }
        yaPedida?.takeIf { clave(it.url) == clave(url) && WikidataJuegos.conCredito(it) != null }?.let { return it }
        val pedida = pedir()?.takeIf { clave(it.url) == clave(url) }
        return pedida?.takeIf { WikidataJuegos.conCredito(it) != null } ?: creditoConocido(url, deLaObra, yaPedida) ?: pedida
    }

    /**
     * La imagen de la comunidad como se usa: si es de Commons (se vota por su original, que puede pesar varios MB) y
     * [credito] es una miniatura de esa misma imagen, la miniatura.
     */
    fun comunidadLigera(comunidad: PortadasComunidad, credito: ImagenElegida?): PortadasComunidad {
        val url = comunidad.comunidad ?: return comunidad
        val mini = credito?.url?.takeIf { esMiniaturaCommons(it) && clave(it) == clave(url) } ?: return comunidad
        return comunidad.copy(comunidad = mini)
    }

    /** Lo que llegó de la comunidad para una obra: en un juego, su imagen solo se usa si se conoce su crédito. */
    fun comunidadUsable(fuente: String, comunidad: PortadasComunidad, credito: ImagenElegida?): PortadasComunidad {
        val url = comunidad.comunidad ?: return comunidad
        return if (!fuente.startsWith("wd:") || votable(fuente, url, credito)) comunidad else comunidad.copy(comunidad = null)
    }
}

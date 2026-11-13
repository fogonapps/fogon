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
    fun clave(url: String) = url(url, Lugar.VISOR)
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
    fun migrar(o: Obra) = if (o.imagen == null && o.portada.isNotBlank())
        o.copy(imagen = ImagenElegida(fuente(o.portada), o.portada, edicion = o.edicion, cuando = o.traida)) else o
    fun enlace(i: ImagenElegida, obra: String): String = when (i.fuente) {
        "ol" -> "https://openlibrary.org/" + (i.edicion?.let { "books/$it" } ?: "works/${obra.removePrefix("ol:")}")
        "tmdb" -> "https://www.themoviedb.org/" + obra.removePrefix("tmdb:").replace(':', '/')
        "wikimedia" -> "https://commons.wikimedia.org/wiki/File:" + i.url.substringAfter("Special:FilePath/", i.url.substringAfterLast('/').replace(Regex("^\\d+px-"), "")).substringBefore('?')
        else -> i.url
    }
}

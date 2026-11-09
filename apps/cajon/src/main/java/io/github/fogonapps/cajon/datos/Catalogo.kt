package io.github.fogonapps.cajon.datos

import kotlinx.serialization.Serializable

/**
 * Una obra o artista conocido para elegir gustos (`assets/datos/catalogos.json`): la lista la arma Fogón, con ids de
 * Wikidata, TMDB, Open Library y MusicBrainz; [imagen] es una portada de su fuente, resuelta al ver la tarjeta. [parecidos] son ids de la misma lista que salen al lado cuando se elige esta.
 */
@Serializable
data class ObraInicial(
    val id: String, val tipo: String, val titulo: String, val anio: Int? = null, val fuente: String = "",
    val premio: String = "", val anioPremio: Int? = null, val creador: String = "", val imagen: String = "", val parecidos: List<String> = emptyList(), val tituloEn: String = titulo,
)

enum class SeccionCatalogo(val enLinea: Boolean = false) {
    ICONICAS, TENDENCIAS(true), PREMIADAS, QUERIDAS(true), PARA_TI(true),
}

@Serializable
data class ListasCatalogo(
    val iconicas: Map<String, List<ObraInicial>> = emptyMap(),
    val premiadas: Map<String, List<ObraInicial>> = emptyMap(),
) {
    fun todas() = (iconicas.values.flatten() + premiadas.values.flatten()).distinctBy { it.fuente }
}

@Serializable
data class PaginaCatalogo(
    val obras: List<ObraInicial>, val fin: Boolean = false,
    val semilla: String = "", val finSemilla: Boolean = false,
)

/** Cada semilla tiene su propio final; una lista corta no agota las demás. */
class FinalesCatalogo {
    private var version = ""
    private val agotadas = mutableSetOf<String>()
    fun registrar(semillas: List<String>, pagina: PaginaCatalogo): Boolean {
        val actual = semillas.sorted().joinToString("|")
        if (actual != version) { version = actual; agotadas.clear() }
        if (pagina.finSemilla) agotadas += pagina.semilla
        return semillas.isEmpty() || semillas.all { it in agotadas }
    }
}

@Serializable
private data class CacheCatalogo(val guardada: Long, val pagina: PaginaCatalogo)

/** Páginas originales por perfil; las exclusiones se aplican después de leer la caché. */
class Catalogo(
    private val listas: ListasCatalogo,
    private val directorio: java.io.File,
    private val reloj: () -> Long = System::currentTimeMillis,
    private val version: (String) -> String = { "" },
    private val pedir: suspend (SeccionCatalogo, String, String, Int) -> PaginaCatalogo,
) {
    private var ultimoBarrido: Long? = null

    private fun limpiarVencidas() {
        val ahora = reloj()
        if (ultimoBarrido?.let { ahora - it in 0 until DIA } == true) return
        directorio.listFiles()?.filter { it.extension == "json" }?.forEach { archivo ->
            val guardada = runCatching { JsonCajon.decodeFromString<CacheCatalogo>(archivo.readText()).guardada }.getOrNull()
            if (guardada == null || ahora - guardada !in 0 until DIA) archivo.delete()
        }
        ultimoBarrido = ahora
    }

    suspend fun pagina(seccion: SeccionCatalogo, tipo: String, idioma: String, pagina: Int,
                       excluidas: Set<String> = emptySet()): PaginaCatalogo {
        require(pagina > 0 && tipo.matches(Regex("[a-z]+")) && idioma in listOf("es", "en"))
        val original = if (!seccion.enLinea) {
            val todas = (if (seccion == SeccionCatalogo.ICONICAS) listas.iconicas else listas.premiadas)[tipo].orEmpty()
            PaginaCatalogo(todas.drop((pagina - 1) * TAMANO).take(TAMANO).map { enIdioma(it, idioma) }, pagina * TAMANO >= todas.size)
        } else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            limpiarVencidas()
            val variante = if (seccion == SeccionCatalogo.PARA_TI) "-${version(tipo)}" else ""
            val archivo = java.io.File(directorio, "${seccion.name.lowercase()}-$tipo-$idioma-$pagina$variante.json")
            val cache = runCatching { JsonCajon.decodeFromString<CacheCatalogo>(archivo.readText()) }.getOrNull()
            if (cache != null && reloj() - cache.guardada in 0 until DIA) cache.pagina
            else pedir(seccion, tipo, idioma, pagina).also { datos ->
                // La caché es prescindible; un fallo de disco no debe ocultar una respuesta válida.
                runCatching {
                    directorio.mkdirs()
                    val temporal = java.io.File(directorio, archivo.name + ".tmp")
                    temporal.writeText(JsonCajon.encodeToString(CacheCatalogo.serializer(), CacheCatalogo(reloj(), datos)))
                    if (!temporal.renameTo(archivo)) temporal.delete()
                }
            }
        }
        return original.copy(obras = sinRepetir(original.obras, excluidas))
    }

    /** Sin red se conservan siempre las listas incluidas. No se sirve caché vencida. */
    suspend fun conRespaldo(seccion: SeccionCatalogo, tipo: String, idioma: String, pagina: Int,
                           excluidas: Set<String> = emptySet()): Pair<PaginaCatalogo, Boolean> = try {
        pagina(seccion, tipo, idioma, pagina, excluidas) to false
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
      catch (_: java.io.IOException) {
        pagina(SeccionCatalogo.ICONICAS, tipo, idioma, 1, excluidas) to true
    }

    companion object {
        const val TAMANO = 20
        const val DIA = 86_400_000L
        fun leer(json: String): ListasCatalogo = JsonCajon.decodeFromString(json)
        fun enIdioma(obra: ObraInicial, idioma: String) = if (idioma == "en") obra.copy(titulo = obra.tituloEn) else obra
        fun sinRepetir(obras: List<ObraInicial>, excluidas: Set<String>) = obras.distinctBy { it.fuente }.filter { it.fuente !in excluidas }
        /** Las claves antiguas de artistas quedan como borradas para que la sincronización no las resucite. */
        fun elegir(e: Estado, obra: ObraInicial, si: Boolean, imagen: String, ahora: Long): Estado {
            val clave = "obra:${obra.fuente}"
            val anteriores = e.gustos.map { if (it.fuente == obra.fuente) it.copy(borrada = true, cambio = ahora) else it }
            val gustos = if (si) anteriores.filter { it.clave != clave } +
                Gusto(clave, obra.titulo.take(120), Senales.GUSTA, obra.tipo, imagen, obra.fuente, obra.creador, cambio = ahora)
                else anteriores
            return e.copy(gustos = gustos)
        }
        fun minimoCumplido(cantidad: Int) = cantidad >= 3
        fun cantidadElegida(e: Estado) = e.gustosVivos.filter { it.senal == Senales.GUSTA && it.fuente.isNotBlank() }.distinctBy { it.fuente }.size
        fun excluidas(e: Estado): Set<String> = (e.vivas.map { it.fuente } + e.artistasVivos.map { it.fuente } +
            e.gustosVivos.filter { it.senal == Senales.GUSTA }.map { it.fuente }).filter { it.isNotBlank() }.toSet()
        fun insertarParecidas(fila: List<ObraInicial>, elegida: ObraInicial, nuevas: List<ObraInicial>, excluidas: Set<String>): List<ObraInicial> {
            val i = fila.indexOfFirst { it.fuente == elegida.fuente }
            val sumar = sinRepetir(nuevas, excluidas + fila.map { it.fuente }).take(3)
            return if (i < 0) fila + sumar else fila.take(i + 1) + sumar + fila.drop(i + 1)
        }
    }
}

fun Encontrado.paraCatalogo() = ObraInicial(fuente, tipo, titulo, anio, fuente, creador = creador,
    imagen = if (fuente.startsWith("mb:rg:")) "" else Imagenes.url(portada, Imagenes.Lugar.LISTA))
fun ArtistaEncontrado.paraCatalogo() = ObraInicial(fuente, "artista", nombre, fuente = fuente, imagen = foto)

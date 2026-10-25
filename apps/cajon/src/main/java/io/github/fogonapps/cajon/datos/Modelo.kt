package io.github.fogonapps.cajon.datos

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * Modelo de datos de El Cajón. Todo se guarda en un JSON (datastore/estado.json) y es el mismo que va al
 * Gist para sincronizar con el PC.
 * Reglas:
 *  - Las migraciones son solo aditivas (campos nuevos con valor por defecto).
 *  - Cada obra, artista y lista lleva [cambio] (milisegundos): al sincronizar gana el más reciente.
 *  - Borrar no quita la fila: la deja con `borrada = true`, para que el otro aparato también la borre.
 *  - Nada de lógica de Android aquí: el PC usa este mismo archivo.
 */

/** Los tipos de obra. Son los ids que se guardan: no se cambian. */
object Tipos {
    const val PELICULA = "pelicula"
    const val SERIE = "serie"
    const val ANIME = "anime"
    const val LIBRO = "libro"
    const val ALBUM = "album"
    val TODOS = listOf(PELICULA, SERIE, ANIME, LIBRO, ALBUM)
    /** Los que se siguen por episodios. */
    fun porEpisodios(t: String) = t == SERIE || t == ANIME
}

/** Dónde está la persona con una obra. */
object Estados {
    /** Por ver, por leer, por escuchar (la lista de pendientes). */
    const val QUIERO = "quiero"
    const val EN_CURSO = "en_curso"
    const val TERMINADO = "terminado"
    const val ABANDONADO = "abandonado"
    val TODOS = listOf(QUIERO, EN_CURSO, TERMINADO, ABANDONADO)
}

/** Una temporada de una serie o un anime: cuántos episodios tiene. */
@Serializable
data class Temporada(val numero: Int, val episodios: Int, val nombre: String = "")

/** Una canción de un álbum, con lo que la persona opina de ella. */
@Serializable
data class Cancion(
    val titulo: String,
    /** Duración en segundos (0 si no se sabe). */
    val segundos: Int = 0,
    /** Media estrellas: 0 = sin nota, 1 = ½ … 10 = 5 estrellas. */
    val nota: Int = 0,
    val favorita: Boolean = false,
)

/**
 * Una vez que se vio, leyó o escuchó algo: el diario. Una obra puede tener varios (volver a verla).
 * [fecha] es AAAA-MM-DD.
 */
@Serializable
data class Registro(
    val id: String,
    val fecha: String,
    val nota: Int = 0,
    /** Ya la había visto/leído/escuchado antes. */
    val otraVez: Boolean = false,
    val texto: String = "",
)

/**
 * Una película, serie, anime, libro o álbum.
 * Los datos "de afuera" (título, sinopsis, portada…) vienen de TMDB, Open Library o MusicBrainz, o se
 * escriben a mano. Lo "de la persona" (estado, nota, reseña, diario, progreso) es solo suyo.
 */
@Serializable
data class Obra(
    val id: String,
    val tipo: String,
    val titulo: String,
    val original: String = "",
    val anio: Int? = null,
    /** Director, creador, autor o artista. */
    val creador: String = "",
    val generos: List<String> = emptyList(),
    val sinopsis: String = "",
    /** URL de la portada (póster, carátula) y del fondo horizontal (solo cine). */
    val portada: String = "",
    val fondo: String = "",
    /** De dónde salió: "tmdb:movie:603", "tmdb:tv:1396", "ol:OL274505W", "mb:rg:<mbid>"; vacío si es a mano. */
    val fuente: String = "",
    /** Minutos (películas), páginas (libros). */
    val minutos: Int? = null,
    val paginas: Int? = null,
    val temporadas: List<Temporada> = emptyList(),
    val canciones: List<Cancion> = emptyList(),
    /** Álbum, EP, sencillo, en vivo… (como lo dice MusicBrainz o la persona). */
    val formato: String = "",
    /** El id del artista en El Cajón (álbumes), si está. */
    val artista: String? = null,

    // Lo de la persona
    val estado: String = Estados.QUIERO,
    val nota: Int = 0,
    val meGusta: Boolean = false,
    /** La reseña o el análisis: solo de la persona, nunca lo escribe la app. */
    val resena: String = "",
    /** Datos curiosos, notas sueltas. */
    val notas: String = "",
    val etiquetas: List<String> = emptyList(),
    /** Episodios vistos, como "1x3" (temporada x episodio). */
    val vistos: List<String> = emptyList(),
    /** Página por la que va (libros). */
    val pagina: Int = 0,
    val registros: List<Registro> = emptyList(),
    /** Cuándo empezó y terminó (AAAA-MM-DD), para libros y series. */
    val empezada: String? = null,
    val terminada: String? = null,

    val agregada: Long = 0,
    val cambio: Long = 0,
    /** Cuándo se trajeron los datos de afuera (TMDB pide no guardarlos más de 6 meses sin renovarlos). */
    val traida: Long = 0,
    val borrada: Boolean = false,
)

/** Un artista o banda (de MusicBrainz o a mano), con lo que la persona sabe y opina. */
@Serializable
data class Artista(
    val id: String,
    val nombre: String,
    val fuente: String = "",
    val pais: String = "",
    /** Año en que empezó o nació. */
    val desde: Int? = null,
    val tipo: String = "",
    val generos: List<String> = emptyList(),
    val notas: String = "",
    val nota: Int = 0,
    val favorito: Boolean = false,
    val cambio: Long = 0,
    val borrada: Boolean = false,
)

/** Una lista propia ("Ghibli en orden", "Mis 10 de terror"), con obras de cualquier tipo. */
@Serializable
data class Lista(
    val id: String,
    val nombre: String,
    val descripcion: String = "",
    val obras: List<String> = emptyList(),
    /** Si el orden importa (ranking): se muestran con número. */
    val numerada: Boolean = false,
    val cambio: Long = 0,
    val borrada: Boolean = false,
)

/** Lo que va en el perfil y los retos del año. */
@Serializable
data class Ajustes(
    val nombre: String = "",
    /** Las cuatro favoritas del perfil (ids de obras). */
    val favoritas: List<String> = emptyList(),
    /** Reto del año: cuántas obras de cada tipo ("libro" → 24). */
    val retos: Map<String, Int> = emptyMap(),
    val cambio: Long = 0,
)

@Serializable
data class Estado(
    val v: Int = 1,
    /** Ya pasó por el tutorial. */
    val listo: Boolean = false,
    val ajustes: Ajustes = Ajustes(),
    val obras: List<Obra> = emptyList(),
    val artistas: List<Artista> = emptyList(),
    val listas: List<Lista> = emptyList(),
) {
    /** Lo que se ve (sin lo borrado). */
    val vivas get() = obras.filter { !it.borrada }
    val artistasVivos get() = artistas.filter { !it.borrada }
    val listasVivas get() = listas.filter { !it.borrada }
}

val JsonCajon = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
}
